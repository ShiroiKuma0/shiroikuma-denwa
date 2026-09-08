package org.fossify.phone.extensions

import android.app.role.RoleManager
import android.content.Context
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.helpers.isQPlus

/**
 * Whether this app really holds the dialer role — the honest answer, which commons cannot give.
 *
 * Commons' `isDefaultDialer()` gates on the package name (`org.fossify.contacts` / `org.fossify.phone`)
 * and returns `true` for anything else, so for `shiroikuma.denwa` it answers "yes" whatever the truth
 * is. Harmless where it merely un-gates a screen, fatal where a backup asks "may I read the blocked
 * numbers?" — the answer decides between writing real data and writing an empty list that looks like
 * a clean export. Hence a check that asks the platform about *us*.
 */
fun Context.holdsDialerRole(): Boolean = if (isQPlus()) {
    val roleManager = getSystemService(RoleManager::class.java)
    roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
        roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
} else {
    telecomManager.defaultDialerPackage == packageName
}
