package org.fossify.phone.models

import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.models.contacts.Contact

sealed class CallLogItem {
    data class Date(
        val timestamp: Long,
        val dayCode: String,
    ) : CallLogItem()

    /** Section label above the contacts the dialpad filter appends below the matching calls. */
    data object ContactsHeader : CallLogItem()

    /** A contact matching what is typed on the dialpad, shown below the calls it did not appear in. */
    data class ContactSuggestion(
        val contact: Contact,
        val number: String,
    ) : CallLogItem()

    fun getItemId(): Int {
        return when (this) {
            is Date -> -(timestamp / (DAY_SECONDS * 1000L)).toInt()
            is ContactsHeader -> CONTACTS_HEADER_ID
            // Namespaced away from the call log's own row ids, which is all this has to be: contact
            // rows are never selectable, so nothing but DiffUtil ever compares them.
            is ContactSuggestion -> "contact-${contact.id}-$number".hashCode()
            is RecentCall -> id
        }
    }

    private companion object {
        const val CONTACTS_HEADER_ID = Int.MIN_VALUE
    }
}
