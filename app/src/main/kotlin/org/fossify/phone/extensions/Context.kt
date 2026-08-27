package org.fossify.phone.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.helpers.Config
import org.fossify.phone.helpers.MissedCallNotifier
import org.fossify.phone.models.SIMAccount

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.audioManager: AudioManager
    get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.powerManager: PowerManager
    get() = getSystemService(Context.POWER_SERVICE) as PowerManager

val Context.keyguardManager: KeyguardManager
    get() = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): List<SIMAccount> {
    val simAccounts = mutableListOf<SIMAccount>()
    try {
        telecomManager.callCapablePhoneAccounts.forEachIndexed { index, account ->
            val phoneAccount = telecomManager.getPhoneAccount(account)
            var label = phoneAccount.label.toString()
            var address = phoneAccount.address.toString()
            if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
                address = Uri.decode(address.substringAfter("tel:"))
                label += " ($address)"
            }

            simAccounts.add(
                SIMAccount(
                    id = index + 1,
                    handle = phoneAccount.accountHandle,
                    label = label,
                    phoneNumber = address.substringAfter("tel:"),
                    color = phoneAccount.highlightColor
                )
            )
        }
    } catch (ignored: Exception) {
    }

    return simAccounts
}

@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    try {
        if (telecomManager.callCapablePhoneAccounts.size > 1) {
            return true
        }
    } catch (_: Exception) {
    }

    return try {
        val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        (subscriptionManager?.activeSubscriptionInfoList?.size ?: 0) > 1
    } catch (_: Exception) {
        false
    }
}

@SuppressLint("MissingPermission")
fun Context.buildSIMAccountLookupMap(): HashMap<String, SIMAccount> {
    val map = HashMap<String, SIMAccount>()

    val simAccounts = getAvailableSIMCardLabels()
    for (account in simAccounts) {
        map[account.handle.id] = account
    }

    try {
        val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return map
        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList ?: return map

        for (subInfo in activeSubscriptions) {
            val subId = subInfo.subscriptionId.toString()
            val iccId = subInfo.iccId

            // Try to find the matching TelecomManager-based SIMAccount by subscription ID or slot index
            val matchingAccount = simAccounts.firstOrNull { it.handle.id == subId }
                ?: simAccounts.getOrNull(subInfo.simSlotIndex)

            if (matchingAccount != null) {
                if (subId !in map) {
                    map[subId] = matchingAccount
                }
                if (!iccId.isNullOrEmpty() && iccId !in map) {
                    map[iccId] = matchingAccount
                }
                // Huawei stores slot index (0-based) as subscription_id in the call log
                val slotKey = subInfo.simSlotIndex.toString()
                if (slotKey !in map) {
                    map[slotKey] = matchingAccount
                }
            } else if (simAccounts.isEmpty()) {
                // TelecomManager returned nothing; create accounts from SubscriptionManager
                val fallbackAccount = SIMAccount(
                    id = subInfo.simSlotIndex + 1,
                    handle = PhoneAccountHandle(
                        ComponentName(
                            "com.android.phone",
                            "com.android.services.telephony.TelephonyConnectionService"
                        ),
                        subId
                    ),
                    label = subInfo.displayName?.toString() ?: "SIM ${subInfo.simSlotIndex + 1}",
                    phoneNumber = "",
                    color = subInfo.iconTint
                )
                map[subId] = fallbackAccount
                if (!iccId.isNullOrEmpty()) {
                    map[iccId] = fallbackAccount
                }
                val slotKey = subInfo.simSlotIndex.toString()
                if (slotKey !in map) {
                    map[slotKey] = fallbackAccount
                }
            }
        }
    } catch (_: Exception) {
    }

    // Apply user-configured SIM color overrides
    val sim1Color = config.sim1Color
    val sim2Color = config.sim2Color
    if (sim1Color != -1 || sim2Color != -1) {
        for ((key, account) in map.entries.toList()) {
            if (account.id == 1 && sim1Color != -1) {
                map[key] = account.copy(color = sim1Color)
            } else if (account.id == 2 && sim2Color != -1) {
                map[key] = account.copy(color = sim2Color)
            }
        }
    }

    return map
}

fun Context.clearMissedCalls() {
    ensureBackgroundThread {
        // dismiss our own missed-call notification (posted by MissedCallNotifier)…
        try {
            MissedCallNotifier(this).cancel()
        } catch (ignored: Exception) {
        }

        // …mark the call log entries read ourselves. Telecom is *supposed* to do this from
        // cancelMissedCallsNotification() below — MissedCallNotifierImpl.clearMissedCalls() writes
        // NEW=0/IS_READ=1 before cancelling — but EMUI's version bails out first with
        // "Telecom-MissedCallNotifierImpl: missCallNumberCount should not be null.", because Huawei
        // only fills that per-number tally when its own notifier drew the notification, and we hold
        // the dialer role. The rows therefore kept NEW=1 forever, so every reboot re-derived the
        // missed-call broadcast from them and the count only ever grew. We hold WRITE_CALL_LOG, so
        // we do the bookkeeping ourselves and leave the Telecom call as a best-effort extra.
        // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
        try {
            val values = ContentValues().apply {
                put(CallLog.Calls.NEW, 0)
                put(CallLog.Calls.IS_READ, 1)
            }

            contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                values,
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            )
        } catch (ignored: Exception) {
        }

        // …and tell Telecom to reset its cached missed-call count as well.
        try {
            telecomManager.cancelMissedCallsNotification()
        } catch (ignored: Exception) {
        }
    }
}

// Per-contact default SIM chosen in our Contacts fork (renrakusaki), read from its content provider.
// Returns the SIM slot (1 or 2) saved for the given number, or 0 if none / unavailable. Reading is
// gated by renrakusaki's signature-level permission, which we hold (both apps share a signing key).
fun Context.getRenrakusakiSimSlot(number: String): Int {
    if (number.isEmpty()) {
        return 0
    }

    val authorities = arrayOf(
        "shiroikuma.renrakusaki.contactsprovider",
        "shiroikuma.renrakusaki.debug.contactsprovider"
    )
    for (authority in authorities) {
        val slot = querySimSlotFromProvider(authority, number)
        if (slot == 1 || slot == 2) {
            return slot
        }
    }

    return 0
}

private fun Context.querySimSlotFromProvider(authority: String, number: String): Int {
    return try {
        val uri = Uri.parse("content://$authority/sim_slot")
        contentResolver.query(uri, null, null, arrayOf(number), null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } ?: 0
    } catch (ignored: Exception) {
        0
    }
}

// The PhoneAccountHandle for a contact's renrakusaki-set SIM (slot 1/2), or null if none is set.
fun Context.getRenrakusakiSimHandle(number: String): PhoneAccountHandle? {
    val slot = getRenrakusakiSimSlot(number)
    return if (slot == 1 || slot == 2) {
        getAvailableSIMCardLabels().firstOrNull { it.id == slot }?.handle
    } else {
        null
    }
}

fun Context.canLaunchAccountsConfiguration(): Boolean {
    return Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
        .resolveActivity(packageManager) != null
}

fun Context.launchAccountsConfiguration() {
    startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
}

fun Activity.startAddContactIntent(phoneNumber: String) {
    Intent().apply {
        action = Intent.ACTION_INSERT_OR_EDIT
        type = "vnd.android.cursor.item/contact"
        putExtra(KEY_PHONE, phoneNumber)
        launchActivityIntent(this)
    }
}
