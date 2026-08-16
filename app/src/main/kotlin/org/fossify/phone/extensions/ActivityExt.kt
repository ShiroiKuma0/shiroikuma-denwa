package org.fossify.phone.extensions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.view.ActionMode
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.isPackageInstalled
import org.fossify.commons.extensions.onGlobalLayout
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

/**
 * Paint the contextual action bar — the "N / M" bar that takes over the top of the screen while items
 * are selected — and its menu from our own slots: MENU_TEXT for the counter and the overflow item
 * titles, MENU_ICON for the action icons, the back arrow and the overflow dots, BACKGROUND behind it.
 *
 * Commons paints this bar in code for `MyRecyclerViewAdapter` only; `MyRecyclerViewListAdapter` (the
 * call log) still gets the stock dark-grey bar with a white counter and a grey back arrow. Neither of
 * them touches the item titles, which the popup draws in the platform theme's text color — white. So
 * every contextual bar in the app is repainted here instead.
 *
 * Call it at the end of `prepareActionMode`: that runs when the bar appears and again on every
 * selection change, after the adapter has set its own item titles.
 */
fun Activity.styleContextualActionBar(actionMode: ActionMode?, menu: Menu) {
    val iconColor = themeColor(ThemeSlot.MENU_ICON)
    val textColor = themeColor(ThemeSlot.MENU_TEXT)
    val barColor = themeColor(ThemeSlot.BACKGROUND)

    menu.colorItemTitles(textColor)
    for (index in 0 until menu.size()) {
        menu.getItem(index).icon?.applyColorFilter(iconColor)
    }

    // commons builds the "N / M" counter as the action mode's custom view
    val counter = actionMode?.customView
    (counter as? TextView)?.setTextColor(textColor)

    // The back arrow and the overflow button are only added to the bar as it lays itself out, so they
    // cannot be tinted before that — and painting from the layout pass also lands after commons' own.
    paintActionModeBar(barColor, iconColor)
    counter?.onGlobalLayout { paintActionModeBar(barColor, iconColor) }
}

private fun Activity.paintActionModeBar(barColor: Int, iconColor: Int) {
    val bar = findViewById<ViewGroup>(androidx.appcompat.R.id.action_mode_bar) ?: return
    bar.setBackgroundColor(barColor)
    bar.tintImageViews(iconColor)
}

// The back arrow and the overflow dots are plain image views, not menu items, so the whole bar is walked.
private fun View.tintImageViews(color: Int) {
    when (this) {
        is ImageView -> applyColorFilter(color)
        is ViewGroup -> for (index in 0 until childCount) getChildAt(index).tintImageViews(color)
    }
}

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
