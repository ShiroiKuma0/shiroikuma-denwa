package org.fossify.phone.extensions

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import org.fossify.commons.helpers.isQPlus

// The CALL_REDIRECTION role is what gets services/SimRedirectionService bound, and with it the
// per-contact SIM applied to calls other apps place (Android Auto above all). At most one app on the
// device may hold it, the user grants it, and it only exists on Q+.
fun Context.isCallRedirectionRoleHeld(): Boolean {
    if (!isQPlus()) {
        return false
    }

    return try {
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION) == true
    } catch (ignored: Exception) {
        false
    }
}

// The system's own grant dialog for that role. Null when the role is unavailable, which is also how a
// caller learns not to offer it.
fun Context.getRequestCallRedirectionRoleIntent(): Intent? {
    if (!isQPlus()) {
        return null
    }

    return try {
        getSystemService(RoleManager::class.java)
            ?.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
    } catch (ignored: Exception) {
        null
    }
}
