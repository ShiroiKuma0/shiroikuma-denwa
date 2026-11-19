package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.helpers.Diag

/** The two buttons of the recording notification: Mark, and Turn all off. */
class DiagReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Diag.ACTION_MARK -> {
                val number = Diag.mark("the notification")
                if (number > 0) context.toast(context.getString(R.string.diag_marked, number))
            }

            Diag.ACTION_ALL_OFF -> Diag.allOff(context)
        }
    }
}
