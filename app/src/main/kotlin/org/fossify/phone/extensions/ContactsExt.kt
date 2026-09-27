package org.fossify.phone.extensions

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import org.fossify.commons.models.contacts.Contact

/**
 * Marks one of a contact's numbers as the one to call — the platform's own "default number", stored
 * as `IS_SUPER_PRIMARY` on that data row.
 *
 * Deliberately the standard flag rather than a preference of our own: every app honours it, this app
 * and our Contacts fork included, and so does Android Auto's dialer. (The per-contact SIM had to be
 * invented in renrakusaki because Android has no concept of one; a default number it does have.)
 * The provider clears super-primary from the contact's other numbers by itself.
 *
 * It matters for the Favorites grid, which dials on a tap without asking which number — a question
 * that must not be put to somebody who is driving.
 */
fun Context.setDefaultPhoneNumber(contact: Contact, number: String): Boolean {
    return try {
        val values = ContentValues().apply {
            put(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
            put(ContactsContract.Data.IS_PRIMARY, 1)
        }

        val mimeType = CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        val byRawContact = "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
            "${ContactsContract.Data.MIMETYPE} = ? AND ${CommonDataKinds.Phone.NUMBER} = ?"
        var updated = contentResolver.update(
            ContactsContract.Data.CONTENT_URI,
            values,
            byRawContact,
            arrayOf(contact.id.toString(), mimeType, number)
        )

        if (updated == 0) {
            // the contact may be an aggregate whose raw id is not the one we hold
            val byContact = "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ? AND ${CommonDataKinds.Phone.NUMBER} = ?"
            updated = contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                byContact,
                arrayOf(contact.contactId.toString(), mimeType, number)
            )
        }

        updated > 0
    } catch (ignored: SecurityException) {
        false
    } catch (ignored: IllegalArgumentException) {
        false
    }
}
