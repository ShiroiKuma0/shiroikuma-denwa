package org.fossify.phone.services

import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import androidx.annotation.RequiresApi
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getRenrakusakiSimHandle

/**
 * Puts the per-contact SIM back in charge of calls this app did not place — above all the ones
 * Android Auto places.
 *
 * Android Auto refuses to dial at all while the phone has no default calling SIM (it would have to
 * ask which SIM on the handset, which it will not do while driving), so a default has to be set in
 * the system. But once it is, Telecom resolves the SIM itself and the call is created with an
 * account already attached — it never parks in STATE_SELECT_PHONE_ACCOUNT, so
 * [CallService.resolvePhoneAccountIfNeeded] never runs and the per-contact SIM never gets a say.
 *
 * Telecom invokes a redirection service *before* placing every outgoing call, whoever placed it, and
 * lets it swap the [PhoneAccountHandle] — which is the one supported hook that survives that. The
 * resolution order deliberately matches the rest of the app: a per-contact SIM set in our Contacts
 * fork (renrakusaki), then a per-number SIM saved here. Neither one set means we leave the call
 * exactly as Telecom built it, so the system default stands.
 *
 * There is no prompting branch on purpose: under car mode Telecom passes allowInteractiveResponse =
 * false, and a SIM question that can only be answered on the handset is precisely what stopped
 * Android Auto dialing in the first place.
 *
 * Needs the CALL_REDIRECTION role, which the user grants from Settings (see
 * `setupSimRedirection`); without it the system never binds this service and calls are untouched.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SimRedirectionService : CallRedirectionService() {

    override fun onPlaceCall(
        handle: Uri,
        initialPhoneAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean,
    ) {
        val number = handle.schemeSpecificPart
        if (number.isNullOrEmpty()) {
            placeCallUnmodified()
            return
        }

        // The renrakusaki lookup is a content-provider query, so it stays off the main thread. Telecom
        // gives a redirection service only a few seconds before it gives up and places the call
        // unmodified, which is exactly the outcome we would pick anyway if the lookup were that slow.
        ensureBackgroundThread {
            val wanted = resolveSimHandle(number)
            Handler(Looper.getMainLooper()).post {
                try {
                    if (wanted == null || wanted == initialPhoneAccount) {
                        placeCallUnmodified()
                    } else {
                        redirectCall(handle, wanted, false)
                    }
                } catch (ignored: Exception) {
                    // a response that arrives after Telecom's timeout throws; the call is already
                    // on its way unmodified, so there is nothing left to do about it
                }
            }
        }
    }

    private fun resolveSimHandle(number: String): PhoneAccountHandle? {
        return getRenrakusakiSimHandle(number) ?: config.getCustomSIM(number)
    }
}
