package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import org.fossify.phone.R
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.themeColor

/**
 * What a menu row needs to know to be drawn as a pill (see views/PillMenuItemView, and the Menus section
 * of the black-yellow-theme skill). A menu item carries no "opens something" flag and no meaning of its
 * own, so this table supplies them by item id: the icon for an item that has none, the role whose colour
 * the icon takes — the frame and the label stay in the accent, or in the warning colour for a
 * destructive row — and whether the row ends in a ›.
 *
 * An item that is not listed keeps the icon it brought, in the view/open colour, with no ›.
 */
object MenuPills {
    const val DISABLED_ALPHA = 97 // 38 %
    const val TOGGLE_HEIGHT_DP = 36 // a checkable row: the switch and little more
    const val PILL_MARGIN_DP = 3 // above and below a pill
    const val SIDE_MARGIN_DP = 8 // between a pill and the menu's edge

    private const val SWITCH_OFF_BLEND = 0.55f
    private const val MENU_CHROME_DP = 24
    private const val GROUP_DIVIDER_DP = 9

    class Spec(@DrawableRes val icon: Int, val role: ThemeSlot, val opens: Boolean = false)

    private val specs: Map<Int, Spec> = mapOf(
        // a call-log row's ⋮, and the bars over selected calls and contacts
        R.id.cab_call to Spec(R.drawable.ic_phone_vector, ThemeSlot.MENU_ROLE_CALL),
        R.id.cab_call_sim_1 to Spec(R.drawable.ic_phone_one_vector, ThemeSlot.MENU_ROLE_CALL),
        R.id.cab_call_sim_2 to Spec(R.drawable.ic_phone_two_vector, ThemeSlot.MENU_ROLE_CALL),
        R.id.cab_remove_default_sim to Spec(R.drawable.ic_sim_vector, ThemeSlot.MENU_ROLE_CALL),
        R.id.cab_send_sms to Spec(R.drawable.ic_sms_vector, ThemeSlot.MENU_ROLE_MESSAGE, opens = true),
        R.id.cab_decline_messages to Spec(R.drawable.ic_sms_vector, ThemeSlot.MENU_ROLE_MESSAGE, opens = true),
        R.id.cab_view_details to
            Spec(R.drawable.ic_view_contact_details_vector, ThemeSlot.MENU_ROLE_CONTACT, opens = true),
        R.id.cab_edit_contact to Spec(R.drawable.ic_edit_vector, ThemeSlot.MENU_ROLE_CONTACT, opens = true),
        R.id.cab_add_number to Spec(R.drawable.ic_add_person_vector, ThemeSlot.MENU_ROLE_CONTACT, opens = true),
        R.id.cab_toggle_favorite to Spec(R.drawable.ic_star_vector, ThemeSlot.MENU_ROLE_CONTACT),
        R.id.cab_copy_number to Spec(R.drawable.ic_copy_vector, ThemeSlot.MENU_ROLE_SHARE),
        R.id.cab_create_shortcut to Spec(R.drawable.ic_plus_vector, ThemeSlot.MENU_ROLE_SHARE),
        R.id.cab_show_call_details to Spec(R.drawable.ic_info_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.cab_select_all to Spec(R.drawable.ic_select_all_vector, ThemeSlot.MENU_ROLE_VIEW),
        R.id.cab_block_number to Spec(R.drawable.ic_minus_circle_vector, ThemeSlot.MENU_ROLE_BLOCK),
        R.id.cab_block_unblock_contact to Spec(R.drawable.ic_minus_circle_vector, ThemeSlot.MENU_ROLE_BLOCK),
        R.id.cab_remove to Spec(R.drawable.ic_delete_vector, ThemeSlot.MENU_ROLE_DELETE),
        R.id.cab_delete to Spec(R.drawable.ic_delete_vector, ThemeSlot.MENU_ROLE_DELETE),

        // the top bar's ⋮
        R.id.sort to Spec(R.drawable.ic_sort_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.filter to Spec(R.drawable.ic_filter_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.change_view_type to Spec(R.drawable.ic_change_view_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.column_count to Spec(R.drawable.ic_column_count_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.settings to Spec(R.drawable.ic_settings_cog_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.about to Spec(R.drawable.ic_info_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.more_apps_from_us to Spec(R.drawable.ic_google_play_vector, ThemeSlot.MENU_ROLE_VIEW, opens = true),
        R.id.create_new_contact to Spec(R.drawable.ic_plus_vector, ThemeSlot.MENU_ROLE_CONTACT, opens = true),
        R.id.clear_call_history to Spec(R.drawable.ic_delete_vector, ThemeSlot.MENU_ROLE_DELETE),

        // Settings, the dialpad and commons' blocked-numbers screen
        R.id.calling_accounts to Spec(R.drawable.ic_sim_vector, ThemeSlot.MENU_ROLE_CALL, opens = true),
        R.id.add_number_to_contact to Spec(R.drawable.ic_plus_vector, ThemeSlot.MENU_ROLE_CONTACT, opens = true),
        R.id.add_blocked_number to Spec(R.drawable.ic_plus_vector, ThemeSlot.MENU_ROLE_BLOCK, opens = true),
        R.id.import_blocked_numbers to Spec(R.drawable.ic_copy_vector, ThemeSlot.MENU_ROLE_SHARE, opens = true),
        R.id.export_blocked_numbers to Spec(R.drawable.ic_copy_vector, ThemeSlot.MENU_ROLE_SHARE, opens = true),
    )

    fun spec(id: Int): Spec? = specs[id]

    /** The colour of the row's icon: its role's, or the view/open one for an item nobody listed. */
    fun iconColor(context: Context, id: Int): Int =
        context.themeColor(specs[id]?.role ?: ThemeSlot.MENU_ROLE_VIEW)

    fun isDestructive(id: Int) = specs[id]?.role == ThemeSlot.MENU_ROLE_DELETE

    /** The frame and label of a row: the warning colour for a destructive one, else the row-frame and label roles. */
    fun frameColor(context: Context, id: Int): Int = if (isDestructive(id)) {
        context.themeColor(ThemeSlot.MENU_ROLE_DELETE)
    } else {
        context.uiColor(UiRole.MENU_ROW_FRAME)
    }

    fun labelColor(context: Context, id: Int): Int =
        context.themeColor(if (isDestructive(id)) ThemeSlot.MENU_ROLE_DELETE else ThemeSlot.MENU_TEXT)

    /** A disabled row is the same row at 38 %. */
    fun fade(item: MenuItem, color: Int): Int =
        if (item.isEnabled) color else ColorUtils.setAlphaComponent(color, DISABLED_ALPHA)

    // The line between two groups of a menu: an opaque darker accent, bright enough to be seen on the
    // background, quieter than a pill frame
    fun dividerColor(context: Context): Int = context.uiColor(UiRole.MENU_DIVIDER)

    /** The disabled title line a menu opens with. */
    fun isHeader(item: MenuItem) = !item.isEnabled && item.itemId == 0 && !item.hasSubMenu()

    /**
     * Shows [popup] so that it never scrolls while the screen has room. A stock popup is given only the
     * larger of the room above and the room below its anchor, so a long menu opened from a row low on
     * the screen scrolled with half the screen free; shifted up by what is missing below, it can use the
     * whole height. Clicks still reach the popup's own listener, which lives on its menu.
     */
    @SuppressLint("RestrictedApi")
    fun show(popup: PopupMenu, themed: Context, anchor: View, gravity: Int) {
        val menu = popup.menu
        val offset = verticalOffset(themed, anchor, menu)
        if (offset == null || menu !is MenuBuilder) {
            popup.show()
            return
        }

        MenuPopupHelper(themed, menu, anchor).apply {
            setGravity(gravity)
            show(0, offset)
        }
    }

    // The vertical offset the menu needs to get its full height, or null when it fits below its anchor
    // anyway. The height is estimated from the rows as PillMenuItemView draws them, a little generously:
    // an estimate that is too large only moves the menu slightly higher.
    @SuppressLint("RestrictedApi")
    private fun verticalOffset(themed: Context, anchor: View, menu: Menu): Int? {
        if (menu !is MenuBuilder) return null
        val metrics = themed.resources.displayMetrics
        val rowHeight = TypedValue()
        val rowAttr = androidx.appcompat.R.attr.dropdownListPreferredItemHeight
        if (!themed.theme.resolveAttribute(rowAttr, rowHeight, true)) return null

        val row = rowHeight.getDimension(metrics)
        val density = metrics.density
        var needed = MENU_CHROME_DP * density
        var group: Int? = null
        for (item in menu.visibleItems) {
            needed += when {
                item.isCheckable -> TOGGLE_HEIGHT_DP * density
                isHeader(item) -> row
                else -> row + 2 * PILL_MARGIN_DP * density
            }
            if (group != null && group != item.groupId && menu.isGroupDividerEnabled) {
                needed += GROUP_DIVIDER_DP * density
            }
            group = item.groupId
        }

        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val position = IntArray(2)
        anchor.getLocationOnScreen(position)
        val base = position[1] + anchor.height
        val below = frame.bottom - base
        if (needed <= below) return null
        return maxOf((frame.top - base).toFloat(), below - needed).toInt()
    }
}
