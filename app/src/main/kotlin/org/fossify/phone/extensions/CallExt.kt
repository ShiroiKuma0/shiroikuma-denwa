package org.fossify.phone.extensions

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustForContrast
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.initiateCall
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.openFullScreenIntentSettings
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.helpers.PERMISSION_CALL_PHONE
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.BuildConfig
import org.fossify.phone.activities.DialerActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.dialogs.SelectSIMDialog

// Commons' own launchCallIntent hard-codes the call intent's target package to
// "org.fossify.phone[.debug]", which doesn't exist for our renamed app id
// (shiroikuma.denwa) — so every call died with "No valid app found". This mirrors
// commons but targets our real package. The DialerActivity class name is unchanged
// because the code namespace is still org.fossify.phone.
fun BaseSimpleActivity.launchCallIntent(recipient: String, handle: PhoneAccountHandle? = null) {
    val appPackageName = packageName
    handlePermission(PERMISSION_CALL_PHONE) { granted ->
        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", recipient, null)
            if (handle != null) {
                putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }

            if (isDefaultDialer()) {
                setClassName(appPackageName, "org.fossify.phone.activities.DialerActivity")
            }

            launchActivityIntent(this)
        }
    }
}

fun SimpleActivity.startCallIntent(
    recipient: String,
    forceSimSelector: Boolean = false
) {
    if (isDefaultDialer()) {
        getHandleToUse(
            intent = null,
            phoneNumber = recipient,
            forceSimSelector = forceSimSelector
        ) { handle ->
            launchCallIntent(recipient, handle)
        }
    } else {
        launchCallIntent(recipient, null)
    }
}

fun SimpleActivity.startCallWithConfirmationCheck(
    recipient: String,
    name: String,
    forceSimSelector: Boolean = false
) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            startCallIntent(recipient, forceSimSelector)
        }
    } else {
        startCallIntent(recipient, forceSimSelector)
    }
}

fun SimpleActivity.startCallWithConfirmationCheck(contact: Contact) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(
            activity = this,
            callee = contact.getNameToDisplay()
        ) {
            initiateCall(contact) { launchCallIntent(it) }
        }
    } else {
        initiateCall(contact) { launchCallIntent(it) }
    }
}

fun BaseSimpleActivity.callContactWithSim(
    recipient: String,
    useMainSIM: Boolean
) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        val wantedSimIndex = if (useMainSIM) 0 else 1
        val handle = getAvailableSIMCardLabels()
            .sortedBy { it.id }
            .getOrNull(wantedSimIndex)?.handle
        launchCallIntent(recipient, handle)
    }
}

fun BaseSimpleActivity.callContactWithSimWithConfirmationCheck(
    recipient: String,
    name: String,
    useMainSIM: Boolean
) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            callContactWithSim(recipient, useMainSIM)
        }
    } else {
        callContactWithSim(recipient, useMainSIM)
    }
}

// SIM1/SIM2 colors used as the swipe-to-call background, mirroring how the recents list
// colors the SIM badge: prefer the user override, fall back to the system highlight color.
fun BaseSimpleActivity.getSimSwipeColors(): Pair<Int, Int> {
    val sims = getAvailableSIMCardLabels().sortedBy { it.id }
    val backgroundColor = getProperBackgroundColor()
    val fallback = getProperPrimaryColor()
    val sim1 = config.sim1Color.takeIf { it != -1 } ?: sims.getOrNull(0)?.color ?: fallback
    val sim2 = config.sim2Color.takeIf { it != -1 } ?: sims.getOrNull(1)?.color ?: fallback
    return sim1.adjustForContrast(backgroundColor) to sim2.adjustForContrast(backgroundColor)
}

// used at devices with multiple SIM cards
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(
    intent: Intent?,
    phoneNumber: String,
    forceSimSelector: Boolean = false,
    callback: (handle: PhoneAccountHandle?) -> Unit
) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        if (it) {
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            when {
                forceSimSelector -> showSelectSimDialog(phoneNumber, callback)
                intent?.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true -> {
                    callback(intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)!!)
                }

                else -> {
                    // Honor a per-contact SIM set in our Contacts fork (renrakusaki), then this app's own
                    // saved SIM — and then ask, rather than falling through to the system default.
                    // A default calling SIM now has to be set system-wide, because Android Auto refuses
                    // to dial at all without one; deferring to it here would silently retire the picker
                    // on the phone, where picking per call is the point. With a single SIM there is
                    // nothing to ask about, so the default stands.
                    val handle = getRenrakusakiSimHandle(phoneNumber) ?: config.getCustomSIM(phoneNumber)
                    when {
                        handle != null -> callback(handle)
                        areMultipleSIMsAvailable() -> showSelectSimDialog(phoneNumber, callback)
                        else -> callback(defaultHandle)
                    }
                }
            }
        }
    }
}

fun SimpleActivity.showSelectSimDialog(
    phoneNumber: String,
    callback: (handle: PhoneAccountHandle?) -> Unit
) = SelectSIMDialog(
    activity = this,
    phoneNumber = phoneNumber,
    onDismiss = {
        if (this is DialerActivity) {
            finish()
        }
    }
) { handle ->
    callback(handle)
}

fun SimpleActivity.handleFullScreenNotificationsPermission(callback: (granted: Boolean) -> Unit) {
    handleNotificationPermission { granted ->
        if (granted) {
            if (canUseFullScreenIntent()) {
                callback(true)
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = R.string.allow_full_screen_notifications_incoming_calls,
                    positiveActionCallback = {
                        @SuppressLint("NewApi")
                        openFullScreenIntentSettings(BuildConfig.APPLICATION_ID)
                    },
                    negativeActionCallback = {
                        callback(false)
                    }
                )
            }
        } else {
            PermissionRequiredDialog(
                activity = this,
                textId = R.string.allow_notifications_incoming_calls,
                positiveActionCallback = {
                    openNotificationSettings()
                },
                negativeActionCallback = {
                    callback(false)
                }
            )
        }
    }
}
