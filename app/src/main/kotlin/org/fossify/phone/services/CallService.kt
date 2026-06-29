package org.fossify.phone.services

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.activities.CallActivity
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
    private val callNotificationManager by lazy { CallNotificationManager(this) }

    // calls we've already auto-resolved a SIM for, so we never call phoneAccountSelected twice
    private val handledAccountSelection = CopyOnWriteArraySet<Call>()

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            resolvePhoneAccountIfNeeded(call)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)
        resolvePhoneAccountIfNeeded(call)

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
    // saved in this app ➜ (only when we can't prompt, i.e. car mode) the default SIM. The first two
    // apply everywhere; the car fallback is what makes Android Auto calls go through.
    private fun resolveSimHandle(number: String?): PhoneAccountHandle? {
        if (number != null) {
            val slot = getRenrakusakiSimSlot(number)
            if (slot == 1 || slot == 2) {
                getSimHandleForSlot(slot)?.let { return it }
            }
            config.getCustomSIM(number)?.let { return it }
        }
        return if (isInCarMode()) getDefaultSimHandle() else null
    }

    private fun getSimHandleForSlot(slot: Int): PhoneAccountHandle? =
        getAvailableSIMCardLabels().firstOrNull { it.id == slot }?.handle

    private fun isInCarMode(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }

    // Prefer SIM 2 as the default when no per-number SIM is set; fall back to whatever SIM exists.
    private fun getDefaultSimHandle(): PhoneAccountHandle? {
        val sims = getAvailableSIMCardLabels()
        return (sims.firstOrNull { it.id == 2 } ?: sims.firstOrNull())?.handle
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
    }
}
