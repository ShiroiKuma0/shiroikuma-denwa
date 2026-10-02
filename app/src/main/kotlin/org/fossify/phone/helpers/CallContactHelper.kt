package org.fossify.phone.helpers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.telecom.Call
import org.fossify.commons.extensions.formatPhoneNumber
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isConference
import org.fossify.phone.models.CallContact

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            lookUpNumber(context, privateCursor, uri.substringAfter("tel:"), callContact, callback)
        }
    }
}

/**
 * The same lookup starting from a bare number, for a call this app can see but does not hold — with
 * no [Call] there is no handle to read, only the number we noted as the call was placed. See
 * [CarCallMonitor].
 */
fun getCallContactByNumber(context: Context, number: String, callback: (CallContact) -> Unit) {
    val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
    ensureBackgroundThread {
        lookUpNumber(context, privateCursor, number, CallContact("", "", "", ""), callback)
    }
}

private fun lookUpNumber(
    context: Context,
    privateCursor: Cursor?,
    number: String,
    callContact: CallContact,
    callback: (CallContact) -> Unit,
) {
    ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
        val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
        if (privateContacts.isNotEmpty()) {
            contacts.addAll(privateContacts)
        }

        val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
        val numbersToContactIDMap = HashMap<String, Int>()
        contactsWithMultipleNumbers.forEach { contact ->
            contact.phoneNumbers.forEach { phoneNumber ->
                numbersToContactIDMap[phoneNumber.value] = contact.contactId
                numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
            }
        }

        callContact.number = if (context.config.formatPhoneNumbers) {
            number.formatPhoneNumber()
        } else {
            number
        }

        val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
        if (contact != null) {
            callContact.name = contact.getNameToDisplay()
            callContact.photoUri = contact.photoUri
            // The rest of what the caller block may be asked to show. The contacts were read whole
            // anyway, so this is only a matter of not throwing the fields away here.
            callContact.prefix = contact.prefix
            callContact.firstName = contact.firstName
            callContact.middleName = contact.middleName
            callContact.surname = contact.surname
            callContact.suffix = contact.suffix
            callContact.nickname = contact.nickname
            callContact.company = contact.organization.company
            callContact.jobPosition = contact.organization.jobPosition
            callContact.note = contact.notes

            if (contact.phoneNumbers.size > 1) {
                val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.value == number }
                if (specificPhoneNumber != null) {
                    callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                }
            }
        } else {
            callContact.name = callContact.number
        }

        callback(callContact)
    }
}
