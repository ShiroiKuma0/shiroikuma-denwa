package org.fossify.phone.extensions

import android.content.Context
import android.provider.ContactsContract.RawContacts
import org.fossify.commons.models.contacts.Contact

/** `RawContacts.SYNC1` prefix renrakusaki puts on the system copy of a private contact, before its local id. */
private const val RENRAKUSAKI_COPY_TAG = "shiroikuma.renrakusaki:"

/**
 * Drops the system copies of private contacts that renrakusaki marks "visible to other apps".
 *
 * Such a contact keeps its private original (served by renrakusaki's provider) and gains a copy in a
 * system account — name, numbers, maybe the photo — whose raw contact carries
 * `SYNC1 = "shiroikuma.renrakusaki:<private local id>"`. Where we merge system contacts with private
 * ones, that person would be listed twice. A copy is dropped only when its private original is among
 * [privateContacts]; if the provider gave us nothing (setting off, app not allowed, renrakusaki gone),
 * the copy is all we have and stays.
 */
fun MutableList<Contact>.dropRenrakusakiCopies(context: Context, privateContacts: List<Contact>) {
    if (privateContacts.isEmpty()) return
    val privateIds = privateContacts.map { it.id.toString() }.toSet()
    val copyRawIds = context.renrakusakiCopies().filterValues { it in privateIds }.keys
    if (copyRawIds.isNotEmpty()) {
        removeAll { it.id in copyRawIds }
    }
}

/** Raw contact id → private local id, for every live system copy renrakusaki has made. */
private fun Context.renrakusakiCopies(): Map<Int, String> {
    val copies = HashMap<Int, String>()
    try {
        contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.SYNC1),
            "${RawContacts.SYNC1} LIKE ? AND ${RawContacts.DELETED} = 0",
            arrayOf("$RENRAKUSAKI_COPY_TAG%"),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                copies[cursor.getInt(0)] = cursor.getString(1).orEmpty().removePrefix(RENRAKUSAKI_COPY_TAG)
            }
        }
    } catch (ignored: Exception) {
        return emptyMap()
    }
    return copies
}
