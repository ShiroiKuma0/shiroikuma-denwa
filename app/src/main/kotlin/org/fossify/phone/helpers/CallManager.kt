package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import org.fossify.commons.extensions.telecomManager
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.extensions.hasCapability
import org.fossify.phone.extensions.isConference
import org.fossify.phone.models.AudioRoute
import java.util.concurrent.CopyOnWriteArraySet

// inspired by https://github.com/Chooloo/call_manage
class CallManager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        var inCallService: InCallService? = null
        private var call: Call? = null
        private val calls = mutableListOf<Call>()
        private val listeners = CopyOnWriteArraySet<CallManagerListener>()

        private val dtmfHandler = Handler(Looper.getMainLooper())
        private val pendingDtmf = ArrayDeque<Char>()
        private var dtmfStartedAtMs: Long? = null
        private var isDtmfBusy = false

        // the ringtone of the current incoming call was silenced (by us or by the system, e.g. the
        // hardware volume keys) — the call itself keeps ringing for the caller
        private var ringerSilenced = false

        fun onCallAdded(call: Call) {
            this.call = call
            calls.add(call)
            ringerSilenced = false
            for (listener in listeners) {
                listener.onPrimaryCallChanged(call)
            }
            call.registerCallback(object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    updateState()
                }

                override fun onDetailsChanged(call: Call, details: Call.Details) {
                    updateState()
                }

                override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
                    updateState()
                }
            })
        }

        fun onCallRemoved(call: Call) {
            calls.remove(call)
            updateState()
        }

        fun onAudioStateChanged(audioState: CallAudioState) {
            val route = AudioRoute.fromRoute(audioState.route) ?: return
            for (listener in listeners) {
                listener.onAudioStateChanged(route)
            }
        }

        fun getPhoneState(): PhoneState {
            return when (calls.size) {
                0 -> NoCall
                1 -> SingleCall(calls.first())
                2 -> {
                    val active = calls.find { it.getStateCompat() == Call.STATE_ACTIVE }
                    val newCall = calls.find { it.getStateCompat() == Call.STATE_CONNECTING || it.getStateCompat() == Call.STATE_DIALING }
                    val onHold = calls.find { it.getStateCompat() == Call.STATE_HOLDING }
                    if (active != null && newCall != null) {
                        TwoCalls(newCall, active)
                    } else if (newCall != null && onHold != null) {
                        TwoCalls(newCall, onHold)
                    } else if (active != null && onHold != null) {
                        TwoCalls(active, onHold)
                    } else {
                        TwoCalls(calls[0], calls[1])
                    }
                }

                else -> {
                    val conference = calls.find { it.isConference() } ?: return NoCall
                    val secondCall = if (conference.children.size + 1 != calls.size) {
                        calls.filter { !it.isConference() }
                            .subtract(conference.children.toSet())
                            .firstOrNull()
                    } else {
                        null
                    }
                    if (secondCall == null) {
                        SingleCall(conference)
                    } else {
                        val newCallState = secondCall.getStateCompat()
                        if (newCallState == Call.STATE_ACTIVE || newCallState == Call.STATE_CONNECTING || newCallState == Call.STATE_DIALING) {
                            TwoCalls(secondCall, conference)
                        } else {
                            TwoCalls(conference, secondCall)
                        }
                    }
                }
            }
        }

        private fun getCallAudioState() = inCallService?.callAudioState

        fun getSupportedAudioRoutes(): Array<AudioRoute> {
            return AudioRoute.values().filter {
                val supportedRouteMask = getCallAudioState()?.supportedRouteMask
                if (supportedRouteMask != null) {
                    supportedRouteMask and it.route == it.route
                } else {
                    false
                }
            }.toTypedArray()
        }

        fun getCallAudioRoute() = AudioRoute.fromRoute(getCallAudioState()?.route)

        fun setAudioRoute(newRoute: Int) {
            inCallService?.setAudioRoute(newRoute)
        }

        private fun updateState() {
            val primaryCall = when (val phoneState = getPhoneState()) {
                is NoCall -> null
                is SingleCall -> phoneState.call
                is TwoCalls -> phoneState.active
            }
            var notify = true
            if (primaryCall == null) {
                call = null
                resetDtmf()
            } else if (primaryCall != call) {
                call = primaryCall
                for (listener in listeners) {
                    listener.onPrimaryCallChanged(primaryCall)
                }
                notify = false
            }
            if (notify) {
                for (listener in listeners) {
                    listener.onStateChanged()
                }
            }

            // remove all disconnected calls manually in case they are still here
            calls.removeAll { it.getStateCompat() == Call.STATE_DISCONNECTED }
        }

        fun getPrimaryCall(): Call? {
            return call
        }

        fun getConferenceCalls(): List<Call> {
            return calls.find { it.isConference() }?.children ?: emptyList()
        }

        fun accept() {
            call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun reject() {
            if (call != null) {
                val state = getState()
                if (state == Call.STATE_RINGING) {
                    call!!.reject(false, null)
                } else if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING) {
                    call!!.disconnect()
                }
            }
        }

        fun toggleHold(): Boolean {
            val isOnHold = getState() == Call.STATE_HOLDING
            if (isOnHold) {
                call?.unhold()
            } else {
                call?.hold()
            }
            return !isOnHold
        }

        fun swap() {
            if (calls.size > 1) {
                calls.find { it.getStateCompat() == Call.STATE_HOLDING }?.unhold()
            }
        }

        fun merge() {
            val conferenceableCalls = call!!.conferenceableCalls
            if (conferenceableCalls.isNotEmpty()) {
                call!!.conference(conferenceableCalls.first())
            } else {
                if (call!!.hasCapability(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                    call!!.mergeConference()
                }
            }
        }

        fun isRingerSilenced() = ringerSilenced

        /**
         * Stops the ringtone and the ringing vibration of an incoming call without touching the
         * call itself — it keeps ringing for the caller, so it can still be answered or left to
         * voicemail. Telecom allows this for the default dialer, which we are, hence the
         * suppressed permission. Returns false when Telecom refused, i.e. nothing was silenced.
         */
        @SuppressLint("MissingPermission")
        fun silenceRinger(): Boolean {
            if (ringerSilenced) {
                return true
            }

            val service = inCallService ?: return false
            return try {
                service.telecomManager.silenceRinger()
                onRingerSilenced()
                true
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Telecom has stopped the ringtone of the incoming call — either because we asked it to, or
         * because the system did it on its own (the volume keys are handled by the window manager
         * before the app ever sees them, so this is our only signal in that case).
         */
        fun onRingerSilenced() {
            if (ringerSilenced) {
                return
            }

            ringerSilenced = true
            for (listener in listeners) {
                listener.onRingerSilenced()
            }
        }

        fun addListener(listener: CallManagerListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: CallManagerListener) {
            listeners.remove(listener)
        }

        fun getState() = getPrimaryCall()?.getStateCompat()

        /**
         * Starts transmitting [char] and keeps it on air until [releaseKeypad] is called, so a digit
         * lasts as long as the key is held. A digit pressed while another one is still on air is
         * queued rather than cutting it short — every digit gets at least [MIN_DTMF_TONE_LENGTH_MS].
         */
        fun pressKeypad(char: Char) {
            if (!isDtmfChar(char)) {
                return
            }

            if (isDtmfBusy) {
                pendingDtmf.addLast(char)
                releaseKeypad()
            } else {
                isDtmfBusy = true
                startDtmfTone(char)
            }
        }

        fun releaseKeypad() {
            // Queued digits release themselves once they go on air.
            val startedAtMs = dtmfStartedAtMs ?: return
            dtmfHandler.removeCallbacksAndMessages(null)
            val remainingMs = MIN_DTMF_TONE_LENGTH_MS - (SystemClock.elapsedRealtime() - startedAtMs)
            if (remainingMs > 0) {
                dtmfHandler.postDelayed({ finishDtmfTone() }, remainingMs)
            } else {
                finishDtmfTone()
            }
        }

        private fun startDtmfTone(char: Char) {
            dtmfStartedAtMs = SystemClock.elapsedRealtime()
            call?.playDtmfTone(char)
        }

        private fun finishDtmfTone() {
            dtmfStartedAtMs = null
            call?.stopDtmfTone()
            val next = pendingDtmf.removeFirstOrNull()
            if (next == null) {
                isDtmfBusy = false
                return
            }

            dtmfHandler.postDelayed({
                startDtmfTone(next)
                releaseKeypad()
            }, DTMF_GAP_MS)
        }

        private fun resetDtmf() {
            dtmfHandler.removeCallbacksAndMessages(null)
            pendingDtmf.clear()
            dtmfStartedAtMs = null
            isDtmfBusy = false
        }

        private fun isDtmfChar(char: Char) = char in '0'..'9' || char == '*' || char == '#'
    }
}

interface CallManagerListener {
    fun onStateChanged()
    fun onAudioStateChanged(audioState: AudioRoute)
    fun onPrimaryCallChanged(call: Call)
    fun onRingerSilenced() {}
}

sealed class PhoneState
object NoCall : PhoneState()
class SingleCall(val call: Call) : PhoneState()
class TwoCalls(val active: Call, val onHold: Call) : PhoneState()
