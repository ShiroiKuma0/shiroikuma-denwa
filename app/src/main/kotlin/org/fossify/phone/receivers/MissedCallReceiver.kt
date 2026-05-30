package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.helpers.MissedCallNotifier

// While 白い熊 電話 is the default dialer, Telecom delegates missed-call notifications to this receiver
// instead of posting its own, letting us render the time via the call-time format setting.
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) {
            return
        }

        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                val notifier = MissedCallNotifier(appContext)
                if (count <= 0) {
                    notifier.cancel()
                } else {
                    notifier.show(count, number)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
