package org.fossify.phone.extensions

import android.net.Uri
import android.telecom.Call
import android.telecom.Call.STATE_CONNECTING
import android.telecom.Call.STATE_DIALING
import android.telecom.Call.STATE_SELECT_PHONE_ACCOUNT
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isSPlus

private val OUTGOING_CALL_STATES = arrayOf(STATE_CONNECTING, STATE_DIALING, STATE_SELECT_PHONE_ACCOUNT)

@Suppress("DEPRECATION")
fun Call?.getStateCompat(): Int {
    return when {
        this == null -> Call.STATE_DISCONNECTED
        isSPlus() -> details.state
        else -> state
    }
}

fun Call?.getCallDuration(): Int {
    return if (this != null) {
        val connectTimeMillis = details.connectTimeMillis
        if (connectTimeMillis == 0L) {
            return 0
        }
        ((System.currentTimeMillis() - connectTimeMillis) / 1000).toInt()
    } else {
        0
    }
}

fun Call.isOutgoing(): Boolean {
    return if (isQPlus()) {
        details.callDirection == Call.Details.DIRECTION_OUTGOING
    } else {
        OUTGOING_CALL_STATES.contains(getStateCompat())
    }
}

fun Call.hasCapability(capability: Int): Boolean = (details.callCapabilities and capability) != 0

fun Call?.isConference(): Boolean = this?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true

/**
 * The caller's number exactly as the telecom stack handed it over — unformatted, so it can be fed
 * straight to the blocked-numbers provider. Empty for a withheld caller ID or a conference.
 */
fun Call?.getRawNumber(): String {
    if (this.isConference()) {
        return ""
    }

    // some ROMs throw out of Call.getDetails() rather than returning null
    val handle = runCatching { this?.details?.handle?.toString() }.getOrNull() ?: return ""

    val uri = Uri.decode(handle)
    return if (uri.startsWith("tel:")) uri.substringAfter("tel:") else ""
}
