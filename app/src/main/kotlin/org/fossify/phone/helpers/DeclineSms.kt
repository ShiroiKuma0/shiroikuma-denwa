package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.receivers.DeclineSmsResultReceiver

/**
 * Sends a decline message as an SMS from the SIM the declined call came in on.
 *
 * The SIM is the whole point: the reply has to come from the number the caller dialled, so the
 * message goes through the SmsManager of that call's own subscription, never the default one. When
 * the subscription cannot be worked out from the call's phone account, [send] refuses rather than
 * guess, and the caller falls back to Telecom's own respond-via-SMS, which knows the account itself.
 */
object DeclineSms {
    const val EXTRA_NUMBER = "decline_sms_number"

    /** The subscription id behind [handle], or INVALID_SUBSCRIPTION_ID when it cannot be told. */
    fun subscriptionIdFor(context: Context, handle: PhoneAccountHandle?): Int {
        handle ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return telephonySubscriptionId(context, handle)
            ?: subscriptionIdFromAccount(context, handle)
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    /** API 30+: the framework's own answer. We hold READ_PHONE_STATE as the dialer; a refusal is caught. */
    @SuppressLint("MissingPermission")
    private fun telephonySubscriptionId(context: Context, handle: PhoneAccountHandle): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        return runCatching { context.getSystemService(TelephonyManager::class.java)?.getSubscriptionId(handle) }
            .getOrNull()
            ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }

    /**
     * Older systems, or a framework that would not say: the telephony connection service names its
     * accounts by subscription id or by ICCID, and failing both, the account's position among the
     * call-capable ones is its SIM slot.
     */
    @SuppressLint("MissingPermission")
    private fun subscriptionIdFromAccount(context: Context, handle: PhoneAccountHandle): Int? {
        val subscriptions = runCatching {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        }.getOrNull().orEmpty()

        val byId = subscriptions.firstOrNull {
            it.subscriptionId.toString() == handle.id || (!it.iccId.isNullOrEmpty() && it.iccId == handle.id)
        }

        val slot = context.getAvailableSIMCardLabels().firstOrNull { it.handle == handle }?.id
        val bySlot = slot?.let { s -> subscriptions.firstOrNull { it.simSlotIndex == s - 1 } }
        return (byId ?: bySlot)?.subscriptionId
    }

    /**
     * Hands [text] to the radio for [number], from the SIM of [handle]. Returns false when nothing
     * was sent (no subscription, no permission, or the framework refused); a failure after hand-off
     * is reported by [DeclineSmsResultReceiver].
     */
    @Suppress("DEPRECATION")
    fun send(context: Context, handle: PhoneAccountHandle?, number: String, text: String): Boolean {
        val subId = subscriptionIdFor(context, handle)
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || number.isEmpty() || text.isBlank()) {
            return false
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }

            val sentIntent = PendingIntent.getBroadcast(
                context,
                number.hashCode(),
                Intent(context, DeclineSmsResultReceiver::class.java).putExtra(EXTRA_NUMBER, number),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent?>(parts.map { null })
                sentIntents[parts.size - 1] = sentIntent
                smsManager.sendMultipartTextMessage(number, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(number, null, text, sentIntent, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
