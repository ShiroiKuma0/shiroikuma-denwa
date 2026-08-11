package org.fossify.phone.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.audioManager
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.extensions.getRenrakusakiSimSlot
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.extensions.keyguardManager
import org.fossify.phone.extensions.powerManager
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallNotificationManager
import org.fossify.phone.helpers.NoCall
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.CopyOnWriteArraySet

class CallService : InCallService() {
    companion object {
        // AudioManager.VOLUME_CHANGED_ACTION and its extras are hidden from the SDK, so they are
        // spelled out here. It is a protected broadcast — only the system can send it.
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val EXTRA_PREV_VOLUME_STREAM_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"

        // Which stream a volume key moves while the phone rings is up to the ROM: the ring stream on
        // AOSP, but EMUI may well hand the press to the media stream instead. Any of them means the
        // same thing during a ringing call — the user reached for a volume key.
        private val WATCHED_VOLUME_STREAMS = setOf(
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_MUSIC,
        )
    }

    private val callNotificationManager by lazy { CallNotificationManager(this) }

    // calls we've already auto-resolved a SIM for, so we never call phoneAccountSelected twice
    private val handledAccountSelection = CopyOnWriteArraySet<Call>()

    private var isVolumeWatcherRegistered = false

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            resolvePhoneAccountIfNeeded(call)
            updateVolumeWatcher()
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
        }
    }

    // Pressing a volume key while the phone rings is supposed to silence the ringer, and on AOSP the
    // window manager does exactly that before the key is ever queued. Some ROMs (EMUI among them)
    // instead grab the key for their own volume panel: the ringtone keeps playing and CallActivity
    // never sees a key event, so it cannot help either. The one signal that still gets out is the
    // ring volume actually changing — so watch for that, silence the ringer, and put the volume back
    // where it was, since the user meant "shut up", not "ring quieter from now on".
    private val volumeWatcher = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stream = intent?.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) ?: return
            if (stream !in WATCHED_VOLUME_STREAMS) {
                return
            }

            if (CallManager.isRingerSilenced() || !hasRingingCall || !CallManager.silenceRinger()) {
                return
            }

            updateVolumeWatcher()

            val previousVolume = intent.getIntExtra(EXTRA_PREV_VOLUME_STREAM_VALUE, -1)
            val newVolume = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
            if (previousVolume > 0 && newVolume >= 0 && newVolume != previousVolume) {
                try {
                    audioManager.setStreamVolume(stream, previousVolume, 0)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private val hasRingingCall get() = calls.any { it.getStateCompat() == Call.STATE_RINGING }

    private fun updateVolumeWatcher() {
        val shouldWatch = !CallManager.isRingerSilenced() && hasRingingCall
        if (shouldWatch == isVolumeWatcherRegistered) {
            return
        }

        try {
            if (shouldWatch) {
                val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                // a protected broadcast — no other app can send it, so EXPORTED is safe here
                ContextCompat.registerReceiver(this, volumeWatcher, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                unregisterReceiver(volumeWatcher)
            }
            isVolumeWatcherRegistered = shouldWatch
        } catch (ignored: Exception) {
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)
        resolvePhoneAccountIfNeeded(call)
        updateVolumeWatcher()

        // Incoming/Outgoing (locked): high priority (FSI)
        // Incoming (unlocked): if user opted in, low priority ➜ manual activity start, otherwise high priority (FSI)
        // Outgoing (unlocked): low priority ➜ manual activity start
        val isIncoming = !call.isOutgoing()
        val isDeviceLocked = !powerManager.isInteractive || keyguardManager.isDeviceLocked
        val lowPriority = when {
            isIncoming && isDeviceLocked -> false
            !isIncoming && isDeviceLocked -> false
            isIncoming && !isDeviceLocked -> config.alwaysShowFullscreen
            else -> true
        }

        callNotificationManager.setupNotification(lowPriority)
        if (
            lowPriority
            || !hasPermission(PERMISSION_POST_NOTIFICATIONS)
            || !canUseFullScreenIntent()
        ) {
            try {
                startActivity(CallActivity.getStartIntent(this))
            } catch (_: Exception) {
                // seems like startActivity can throw AndroidRuntimeException and
                // ActivityNotFoundException, not yet sure when and why, lets show a notification
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        handledAccountSelection.remove(call)
        updateVolumeWatcher()
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    // Telecom tells every in-call service when the ringtone got silenced. This is how we learn about
    // a silence we did not trigger ourselves — most notably the hardware volume keys, which the
    // window manager swallows while a call is ringing, so CallActivity never sees the key event.
    override fun onSilenceRinger() {
        super.onSilenceRinger()
        CallManager.onRingerSilenced()
        updateVolumeWatcher()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    // When a call is placed without a SIM (most notably from Android Auto / car mode, which calls
    // TelecomManager.placeCall without a PhoneAccountHandle), a dual-SIM device with no system
    // default parks the call in STATE_SELECT_PHONE_ACCOUNT, waiting for the default dialer to pick.
    // Our only SIM picker lives in CallActivity, which can't be reached from the car, so the call
    // would silently hang. Resolve the SIM here instead:
    //   1) honor a per-number SIM saved in the app (config.getCustomSIM),
    //   2) otherwise, when we can't show the on-screen picker (car mode), fall back to the default SIM.
    // On the phone (not car mode) with no saved SIM we do nothing, so CallActivity's picker behaves
    // exactly as before.
    private fun resolvePhoneAccountIfNeeded(call: Call) {
        if (call.getStateCompat() != Call.STATE_SELECT_PHONE_ACCOUNT || !handledAccountSelection.add(call)) {
            return
        }

        val number = call.details?.handle?.schemeSpecificPart
        // resolve off the main thread (the renrakusaki lookup is a content-provider query); Telecom
        // just keeps the call parked in STATE_SELECT_PHONE_ACCOUNT until we pick an account.
        ensureBackgroundThread {
            val handle = resolveSimHandle(number)
            if (handle != null) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        call.phoneAccountSelected(handle, false)
                    } catch (ignored: Exception) {
                    }
                }
            } else {
                // nothing to auto-pick (e.g. on the phone, not in car) ➜ let CallActivity show the picker
                handledAccountSelection.remove(call)
            }
        }
    }

    // Resolution order: a per-contact SIM set in our Contacts fork (renrakusaki) ➜ a per-number SIM
    // saved in this app ➜ the default SIM. A call only reaches STATE_SELECT_PHONE_ACCOUNT when it was
    // placed without a SIM (Android Auto, external ACTION_CALL), where we can't show a picker — and AA
    // does NOT set UI_MODE_TYPE_CAR — so always resolve to a concrete SIM rather than leaving it unpicked.
    private fun resolveSimHandle(number: String?): PhoneAccountHandle? {
        if (number != null) {
            val slot = getRenrakusakiSimSlot(number)
            if (slot == 1 || slot == 2) {
                getSimHandleForSlot(slot)?.let { return it }
            }
            config.getCustomSIM(number)?.let { return it }
        }
        return getDefaultSimHandle()
    }

    private fun getSimHandleForSlot(slot: Int): PhoneAccountHandle? =
        getAvailableSIMCardLabels().firstOrNull { it.id == slot }?.handle

    // Prefer SIM 2 as the default when no per-number SIM is set; fall back to whatever SIM exists.
    private fun getDefaultSimHandle(): PhoneAccountHandle? {
        val sims = getAvailableSIMCardLabels()
        return (sims.firstOrNull { it.id == 2 } ?: sims.firstOrNull())?.handle
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
        if (isVolumeWatcherRegistered) {
            try {
                unregisterReceiver(volumeWatcher)
            } catch (ignored: Exception) {
            }
            isVolumeWatcherRegistered = false
        }
    }
}
