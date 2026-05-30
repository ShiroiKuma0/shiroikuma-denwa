package org.fossify.phone.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
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
        try {
            // dismiss our own missed-call notification (posted by MissedCallNotifier)…
            MissedCallNotifier(this).cancel()
            // …and tell Telecom to clear its missed-call state. notification cancellation triggers
            // MissedCallNotifier.clearMissedCalls() which, in turn, should update the database and
            // reset the cached missed call count in MissedCallNotifier.java
            // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
            telecomManager.cancelMissedCallsNotification()
        } catch (ignored: Exception) {
        }
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
