package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.helpers.ACCEPT_CALL
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.ACCEPT_CAR_CALL
import org.fossify.phone.helpers.DECLINE_CALL
import org.fossify.phone.helpers.END_CAR_CALL
import org.fossify.phone.services.CarCallService

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }

            DECLINE_CALL -> CallManager.reject()

            // A call we do not hold (Android Auto owns the in-call UI): no Call to disconnect, so
            // these go through TelecomManager. See helpers/CarCallMonitor.
            END_CAR_CALL -> CarCallService.endCall(context)

            ACCEPT_CAR_CALL -> CarCallService.answerCall(context)
        }
    }
}
