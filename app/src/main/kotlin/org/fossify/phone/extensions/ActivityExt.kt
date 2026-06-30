package org.fossify.phone.extensions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.launchViewContactIntent
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.FIRST_CONTACT_ID
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.helpers.CONTACTS_APP_MAIN_ACTIVITY
import org.fossify.phone.helpers.CONTACTS_APP_OPEN_TAB_EXTRA
import org.fossify.phone.helpers.contactsAppPackages

fun SimpleActivity.handleGenericContactClick(contact: Contact) {
    when (config.onContactClick) {
        ON_CLICK_CALL_CONTACT -> startCallWithConfirmationCheck(contact)
        ON_CLICK_VIEW_CONTACT -> startContactDetailsIntent(contact)
    }
}

fun Context.getInstalledContactsAppPackage() = contactsAppPackages.firstOrNull { isPackageInstalled(it) }

// Opens our Contacts fork on the given tab (a commons TAB_* mask). Targets its MainActivity directly:
// the launcher intent would not deliver the extra when the app is already running.
fun Activity.launchContactsApp(tab: Int) {
    val contactsAppPackage = getInstalledContactsAppPackage() ?: return
    Intent().apply {
        setClassName(contactsAppPackage, CONTACTS_APP_MAIN_ACTIVITY)
        putExtra(CONTACTS_APP_OPEN_TAB_EXTRA, tab)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        launchActivityIntent(this)
    }
}

fun SimpleActivity.launchCreateNewContactIntent() {
    Intent().apply {
        action = Intent.ACTION_INSERT
        data = ContactsContract.Contacts.CONTENT_URI
        launchActivityIntent(this)
    }
}

// handle private contacts differently, only Simple Contacts Pro can open them
fun Activity.startContactDetailsIntent(contact: Contact) {
    val simpleContacts = "org.fossify.contacts"
    val simpleContactsDebug = "org.fossify.contacts.debug"
    val isPrivateContact = contact.rawId > FIRST_CONTACT_ID
            && contact.contactId > FIRST_CONTACT_ID
            && contact.rawId == contact.contactId
            && (isPackageInstalled(simpleContacts) || isPackageInstalled(simpleContactsDebug))
    if (isPrivateContact) {
        Intent().apply {
            action = Intent.ACTION_VIEW
            putExtra(CONTACT_ID, contact.rawId)
            putExtra(IS_PRIVATE, true)
            `package` =
                if (isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug
            setDataAndType(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                "vnd.android.cursor.dir/person"
            )
            launchActivityIntent(this)
        }
    } else {
        ensureBackgroundThread {
            val lookupKey =
                SimpleContactsHelper(this).getContactLookupKey((contact).rawId.toString())
            val publicUri =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            runOnUiThread {
                launchViewContactIntent(publicUri)
            }
        }
    }
}
