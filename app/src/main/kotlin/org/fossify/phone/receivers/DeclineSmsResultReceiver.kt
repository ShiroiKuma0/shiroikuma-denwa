package org.fossify.phone.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.fossify.phone.R
import org.fossify.phone.helpers.DeclineSms

/**
 * The radio's verdict on a decline message. The call screen is long gone by then, so a failure is the
 * only thing worth saying — "sent" was already shown when the message was handed off.
 */
class DeclineSmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            return
        }

        val number = intent.getStringExtra(DeclineSms.EXTRA_NUMBER).orEmpty()
        Toast.makeText(context, context.getString(R.string.sk_decline_failed, number), Toast.LENGTH_LONG).show()
    }
}
