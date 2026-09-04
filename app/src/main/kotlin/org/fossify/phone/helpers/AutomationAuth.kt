package org.fossify.phone.helpers

import android.content.Context
import org.fossify.phone.extensions.config

/**
 * The one gate every automation entry point asks, in one place.
 *
 * ## Why it is one function
 *
 * The broadcast receiver and the data door both have to answer "may this caller drive the export?",
 * and the two checks behind that question — the master switch and the optional token — report as
 * *different* errors because they debug differently. Written out at each entry point they drift; the
 * family has forty-two apps that must all answer identically, so the check lives here and the entry
 * points only ever forward what it says.
 *
 * ## Why the token is now opt-in
 *
 * v1 shipped every app closed: the switch defaulted off, and a caller also had to present a
 * 48-character secret 白い熊 had pasted from this app's settings into the caller's. That is the wrong
 * shape for what the contract now serves — **a pasted secret cannot survive a wipe**, and the case
 * the family exists for is 応用管理 restoring apps *and their data* onto a clean phone, where nothing
 * has been configured and nobody has pasted anything. A gate that only works once the phone is
 * already set up is no gate for setting the phone up (contract v2, 2026-09-04).
 *
 * So the switch ships ON ([Config.automationEnabled]) and the token is OFF
 * ([Config.automationRequireToken]). The switch stays, because it is the only way to close one app
 * off and a feature that can be turned on but never off is one 白い熊 cannot retreat from.
 *
 * ## A token this app does not want is IGNORED, never refused
 *
 * Required, not a nicety. Tokens live in task arguments and workspace variables that outlive the
 * setting they were pasted for, so a caller still sending one — because it was configured last year,
 * or because another app on the batch does ask for one — must be served. Refusing it would turn
 * "白い熊 turned a switch off" into "half the batch mysteriously fails", which is precisely the
 * friction the switch exists to remove.
 *
 * The identity of a caller through the data door is a separate question, and a stronger one: see
 * [org.fossify.phone.automation.AutomationCallers].
 */
object AutomationAuth {

    /** The master switch. Default ON — see the class comment for why it is no longer a closed door. */
    fun enabled(context: Context): Boolean = context.config.automationEnabled

    /** Whether a caller must also present the token. Default OFF. */
    fun requiresToken(context: Context): Boolean = context.config.automationRequireToken

    /**
     * `null` = proceed. Otherwise the exact `ERROR:` line to answer with, verbatim — the caller shows
     * it to 白い熊 unchanged, so these two strings are wire format and not prose.
     */
    fun refuse(context: Context, candidate: String?): String? = when {
        !enabled(context) -> "ERROR:automation disabled"
        requiresToken(context) && !context.config.isAutomationTokenValid(candidate) -> "ERROR:bad token"
        else -> null
    }
}
