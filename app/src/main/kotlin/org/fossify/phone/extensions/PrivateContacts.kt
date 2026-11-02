package org.fossify.phone.extensions

import android.content.Context
import android.database.Cursor
import android.net.Uri

// Our Contacts fork's provider (release, then debug build). It serves the private contacts and the
// per-contact SIM; reading needs its signature-level READ_PRIVATE_DATA, which we hold.
internal val RENRAKUSAKI_PROVIDER_AUTHORITIES = arrayOf(
    "shiroikuma.renrakusaki.contactsprovider",
    "shiroikuma.renrakusaki.debug.contactsprovider"
)

/**
 * The private ("Phone storage (not visible by other apps)") contacts of our Contacts fork, as the
 * cursor [org.fossify.commons.helpers.MyContactsContentProvider.getContacts] parses.
 *
 * Use this, never commons' `getMyContactsCursor`: that one queries the fixed authority
 * `org.fossify.commons.contactsprovider`, which only stock Fossify Contacts serves. renrakusaki serves
 * `<applicationId>.contactsprovider`, so through commons the dialer saw no private contact at all — no
 * name on the call screen or in the call log, none in search, and "block unknown numbers" treated
 * every one of them as unknown.
 */
fun Context.getPrivateContactsCursor(favoritesOnly: Boolean, withPhoneNumbersOnly: Boolean): Cursor? {
    val args = arrayOf(if (favoritesOnly) "1" else "0", if (withPhoneNumbersOnly) "1" else "0")
    for (authority in RENRAKUSAKI_PROVIDER_AUTHORITIES) {
        val cursor = try {
            contentResolver.query(Uri.parse("content://$authority/contacts"), null, null, args, null)
        } catch (ignored: Exception) {
            null
        }
        if (cursor != null) {
            return cursor
        }
    }
    return null
}
