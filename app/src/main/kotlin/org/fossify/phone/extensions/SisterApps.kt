package org.fossify.phone.extensions

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.launchSendSMSIntent
import org.fossify.commons.extensions.toast
import org.fossify.phone.helpers.MESSEJI_PACKAGE
import org.fossify.phone.helpers.RINDENWA_MAIN_ACTIVITY
import org.fossify.phone.helpers.RINDENWA_OPEN_HISTORY_EXTRA
import org.fossify.phone.helpers.RINDENWA_PACKAGE

// The sister apps we open besides renrakusaki (whose hand-off lives in ActivityExt.kt): 臨電話 from the
// bar's fourth tab, メッセージ from a call-log row's ✉.

/** Whether 臨電話 is installed — what decides if the bar's fourth tab is drawn. */
fun Context.isRindenwaInstalled() = isPackageInstalled(RINDENWA_PACKAGE)

/**
 * Opens 臨電話 on its SIP call history: the fourth tab of the bar the three apps share. Same flags and
 * zero-length animation as [launchContactsApp], so it too reads as a tab change; neither side finishes
 * the other.
 */
fun Activity.launchRindenwa() {
    val intent = Intent().apply {
        setClassName(RINDENWA_PACKAGE, RINDENWA_MAIN_ACTIVITY)
        putExtra(RINDENWA_OPEN_HISTORY_EXTRA, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    if (intent.resolveActivity(packageManager) == null) {
        toast(org.fossify.commons.R.string.no_app_found)
        return
    }

    startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
}

/** The conversation with [number] in 白い熊 メッセージ, or in whatever handles SMS when it is not installed. */
fun Activity.openMessages(number: String) {
    if (!isPackageInstalled(MESSEJI_PACKAGE)) {
        launchSendSMSIntent(number)
        return
    }

    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).setPackage(MESSEJI_PACKAGE)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        launchSendSMSIntent(number)
    }
}
