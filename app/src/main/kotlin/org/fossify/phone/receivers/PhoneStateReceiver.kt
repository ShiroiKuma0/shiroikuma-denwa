package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import org.fossify.phone.helpers.CarCallMonitor

/**
 * The incoming half of the car-call fallback. An outgoing call announces itself through
 * [org.fossify.phone.services.SimRedirectionService], but nothing binds us for an incoming one while
 * Android Auto holds the in-call UI — this broadcast is all that is left, and it arrives even when
 * the app has no process of its own running.
 *
 * It fires for ordinary calls too, where Telecom binds our InCallService a moment later and
 * [CarCallMonitor] simply stands down again, so there is no effect off the car.
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) {
            return
        }

        // withheld on a hidden caller, and null unless we hold READ_CALL_LOG — which we do; the
        // screen falls back to "unknown caller" either way
        val number = try {
            @Suppress("DEPRECATION")
            intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        } catch (ignored: Exception) {
            null
        }

        CarCallMonitor.armIncoming(context, number)
    }
}
