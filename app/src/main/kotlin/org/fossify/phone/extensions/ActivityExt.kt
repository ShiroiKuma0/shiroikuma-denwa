package org.fossify.phone.extensions

import android.app.Activity
import android.app.ActivityOptions
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
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.FIRST_CONTACT_ID
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.helpers.ON_CLICK_CALL_CONTACT
import org.fossify.commons.helpers.ON_CLICK_VIEW_CONTACT
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.helpers.CONTACTS_APP_DIALER_TABS_EXTRA
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

/**
 * Opens our Contacts fork on the given tab (a commons TAB_* mask), and tells it to wear our bottom bar
 * while it is there: `visibleTabs` is our own `config.showTabs`, so renrakusaki comes up with the dialer's
 * tabs — Recents included — instead of its own, and its Recents tab hands straight back here. Without it
 * the third tab would be renrakusaki's Groups, and getting back to the call log would mean leaving the app.
 *
 * Targets its MainActivity directly: the launcher intent would not deliver the extras when the app is
 * already running. Started with a zero-length animation so the swap reads as a tab change rather than an
 * app switch — `overridePendingTransition` is deprecated and ignored on API 34+, hence ActivityOptions.
 */
fun Activity.launchContactsApp(tab: Int, visibleTabs: Int) {
    val contactsAppPackage = getInstalledContactsAppPackage() ?: return
    val intent = Intent().apply {
        setClassName(contactsAppPackage, CONTACTS_APP_MAIN_ACTIVITY)
        putExtra(CONTACTS_APP_OPEN_TAB_EXTRA, tab)
        putExtra(CONTACTS_APP_DIALER_TABS_EXTRA, visibleTabs)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    // the package was confirmed installed a line ago, so an unresolvable target means renrakusaki went
    // away mid-tap — say what commons' own launcher would have said rather than crashing the dialer
    if (intent.resolveActivity(packageManager) == null) {
        toast(org.fossify.commons.R.string.no_app_found)
        return
    }

    startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
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
