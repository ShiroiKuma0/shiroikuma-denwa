package org.fossify.phone.extensions

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * The region a call-log number stored without a country code should be read in.
 *
 * Emphatically NOT the UI locale, which is what upstream used (`Locale.getDefault().country`): this
 * phone runs a Japanese locale on Czech SIMs, and the Czech mobile number 735759605 is also a
 * perfectly good Japanese landline (0735-75-9605, Wakayama) — so the recents list labelled calls
 * placed in Prague "Kushimoto, Wakayama".
 *
 * A bare national number belongs to the country the phone was dialing in, and the SIM's own country
 * is the closest record of that. The registered network stands in when the SIM will not say (no SIM,
 * or an operator that leaves it blank), and the locale only when neither does.
 */
fun Context.getCallLogRegion(): String {
    val telephony = try {
        getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    } catch (ignored: Exception) {
        null
    }

    val fromSim = try {
        telephony?.simCountryIso
    } catch (ignored: Exception) {
        null
    }

    val fromNetwork = try {
        telephony?.networkCountryIso
    } catch (ignored: Exception) {
        null
    }

    return listOfNotNull(fromSim, fromNetwork)
        .firstOrNull { it.isNotEmpty() }
        ?.uppercase(Locale.US)
        ?: Locale.getDefault().country
}

/**
 * Which SIM slot a PhoneAccountHandle belongs to (1 or 2), or 0 when it is unknown or the device has
 * a single SIM. Used to badge a call we do not hold (see helpers/CarCallMonitor), where the handle
 * noted as the call was placed is the only SIM information we ever get.
 */
fun Context.getSimSlotForHandle(handle: PhoneAccountHandle?): Int {
    if (handle == null) {
        return 0
    }

    return try {
        getAvailableSIMCardLabels().firstOrNull { it.handle == handle }?.id ?: 0
    } catch (ignored: Exception) {
        0
    }
}
