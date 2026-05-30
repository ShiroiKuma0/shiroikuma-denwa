package org.fossify.phone.helpers

import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import org.fossify.commons.extensions.notificationManager
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.extensions.formatCallTime

// Posts 白い熊 電話's own missed-call notification so the time honours the call-time format setting
// (Japanese kanji by default). Used only while we are the default dialer — Telecom then routes the
// missed-call broadcast to us instead of drawing its own notification (see MissedCallReceiver).
class MissedCallNotifier(private val context: Context) {
    companion object {
        const val NOTIFICATION_ID = 43
        private const val CHANNEL_ID = "shiroikuma_missed_calls"
    }

    private val notificationManager = context.notificationManager

    fun cancel() = notificationManager.cancel(NOTIFICATION_ID)

    fun show(count: Int, fallbackNumber: String?) {
        val latest = latestMissedCall()
        val number = (latest?.first ?: fallbackNumber).orEmpty()
        val timeText = latest?.second?.formatCallTime(context).orEmpty()
        val name = resolveName(number)
        createChannel()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_missed_vector)
            .setSubText(context.getString(R.string.notification_missed_call))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setContentIntent(openRecentsIntent())

        if (count > 1) {
            builder.setContentTitle(context.getString(R.string.notification_missed_calls, count))
            builder.setContentText(if (timeText.isEmpty()) name else "$name ・ $timeText")
        } else {
            builder.setContentTitle(name)
            builder.setContentText(timeText)
        }

        if (number.isNotBlank()) {
            builder.addAction(0, context.getString(R.string.notification_call_back), callBackIntent(number))
            builder.addAction(0, context.getString(R.string.notification_message), messageIntent(number))
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun openRecentsIntent(): PendingIntent {
        // ACTION_VIEW makes MainActivity land on the Recents tab, which also clears missed calls.
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent, pendingIntentFlags())
    }

    private fun callBackIntent(number: String): PendingIntent {
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return PendingIntent.getActivity(context, 1, intent, pendingIntentFlags())
    }

    private fun messageIntent(number: String): PendingIntent {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        return PendingIntent.getActivity(context, 2, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_missed_calls),
            IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    // The newest missed call (number to date-millis), used for the caller and the formatted time.
    private fun latestMissedCall(): Pair<String, Long>? {
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() to cursor.getLong(1) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveName(number: String): String {
        if (number.isBlank()) {
            return context.getString(R.string.unknown_caller)
        }
        val name = try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        return name?.takeIf { it.isNotBlank() } ?: number
    }
}
