package org.fossify.phone.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.phone.R
import org.fossify.phone.helpers.PALETTE_BLACK
import org.fossify.phone.helpers.PALETTE_YELLOW
import org.fossify.phone.helpers.THEME_UNSET

// Granular, per-element theming for 白い熊 電話.
//
// Each [ThemeSlot] is one customizable color. Foundation slots reuse the stock commons colors
// (background / primary / text); every other slot inherits from a foundation slot by default
// (two-tier), so the whole app stays coherent and a single foundation change cascades. A slot
// only diverges once the user gives it an explicit override (stored as an Int; THEME_UNSET means
// "follow the default"). The default look is seeded to black background + yellow text/accents.

enum class ThemeGroup(@StringRes val labelRes: Int) {
    FOUNDATION(R.string.theme_group_foundation),
    SEARCH(R.string.theme_group_search),
    CHROME(R.string.theme_group_chrome),
    TABS(R.string.theme_group_tabs),
    CALL_LOG(R.string.theme_group_call_log),
    DIALPAD(R.string.theme_group_dialpad),
    IN_CALL(R.string.theme_group_in_call),
    CONTACTS(R.string.theme_group_contacts),
    FAVORITES(R.string.theme_group_favorites),
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text views (family / weight / size are configurable per element)
    val hasFont: Boolean = false,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND("theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary),

    // Search bar
    SEARCH_FILL("theme_search_fill", ThemeGroup.SEARCH, R.string.theme_search_fill),
    SEARCH_TEXT("theme_search_text", ThemeGroup.SEARCH, R.string.theme_search_text, hasFont = true),
    SEARCH_HINT("theme_search_hint", ThemeGroup.SEARCH, R.string.theme_search_hint),
    SEARCH_ICON("theme_search_icon", ThemeGroup.SEARCH, R.string.theme_search_icon),
    SEARCH_BORDER("theme_search_border", ThemeGroup.SEARCH, R.string.theme_search_border),

    // Top bar & overflow ("hamburger") menu
    MENU_ICON("theme_menu_icon", ThemeGroup.CHROME, R.string.theme_menu_icon),
    MENU_TEXT("theme_menu_text", ThemeGroup.CHROME, R.string.theme_menu_text),
    HEADER_TITLE("theme_header_title", ThemeGroup.CHROME, R.string.theme_header_title),
    HEADER_ARROW("theme_header_arrow", ThemeGroup.CHROME, R.string.theme_header_arrow),
    SETTINGS_BUTTON("theme_settings_button", ThemeGroup.CHROME, R.string.theme_settings_button, hasFont = true),

    // Tabs
    TAB_BACKGROUND("theme_tab_background", ThemeGroup.TABS, R.string.theme_tab_background),
    TAB_SELECTED("theme_tab_selected", ThemeGroup.TABS, R.string.theme_tab_selected),
    TAB_UNSELECTED("theme_tab_unselected", ThemeGroup.TABS, R.string.theme_tab_unselected),

    // Call log (Recents)
    CALL_LOG_NAME("theme_call_log_name", ThemeGroup.CALL_LOG, R.string.theme_call_log_name, hasFont = true),
    CALL_LOG_SUBTITLE("theme_call_log_subtitle", ThemeGroup.CALL_LOG, R.string.theme_call_log_subtitle, hasFont = true),
    CALL_LOG_DATE("theme_call_log_date", ThemeGroup.CALL_LOG, R.string.theme_call_log_date, hasFont = true),
    CALL_LOG_DAY_DATE("theme_call_log_day_date", ThemeGroup.CALL_LOG, R.string.theme_call_log_day_date, hasFont = true),
    CALL_LOG_MISSED("theme_call_log_missed", ThemeGroup.CALL_LOG, R.string.theme_call_log_missed),
    CALL_LOG_INCOMING("theme_call_log_incoming", ThemeGroup.CALL_LOG, R.string.theme_call_log_incoming),
    CALL_LOG_OUTGOING("theme_call_log_outgoing", ThemeGroup.CALL_LOG, R.string.theme_call_log_outgoing),
    CALL_LOG_DIVIDER("theme_call_log_divider", ThemeGroup.CALL_LOG, R.string.theme_call_log_divider),
    CALL_LOG_DAY_DIVIDER("theme_call_log_day_divider", ThemeGroup.CALL_LOG, R.string.theme_call_log_day_divider),
    CALL_LOG_DATE_UNDERLINE("theme_call_log_date_underline", ThemeGroup.CALL_LOG, R.string.theme_call_log_date_underline),

    // Dialpad
    DIALPAD_CALL_BUTTON("theme_dialpad_call_button", ThemeGroup.DIALPAD, R.string.theme_dialpad_call_button),
    DIALPAD_CALL_ICON("theme_dialpad_call_icon", ThemeGroup.DIALPAD, R.string.theme_dialpad_call_icon),

    // In-call
    CALL_ACCEPT("theme_call_accept", ThemeGroup.IN_CALL, R.string.theme_call_accept),
    CALL_DECLINE("theme_call_decline", ThemeGroup.IN_CALL, R.string.theme_call_decline),
    CALL_CONTROL_ACTIVE("theme_call_control_active", ThemeGroup.IN_CALL, R.string.theme_call_control_active),
    CALL_CONTROL_INACTIVE("theme_call_control_inactive", ThemeGroup.IN_CALL, R.string.theme_call_control_inactive),

    // Contacts
    CONTACT_NAME("theme_contact_name", ThemeGroup.CONTACTS, R.string.theme_contact_name, hasFont = true),
    CONTACT_FASTSCROLLER("theme_contact_fastscroller", ThemeGroup.CONTACTS, R.string.theme_contact_fastscroller),

    // Favorites
    FAVORITE_NAME("theme_favorite_name", ThemeGroup.FAVORITES, R.string.theme_favorite_name, hasFont = true),
    FAVORITE_FASTSCROLLER("theme_favorite_fastscroller", ThemeGroup.FAVORITES, R.string.theme_favorite_fastscroller),
}

// A configurable line thickness (in dp), grouped alongside the color slots in the Theme screen.
enum class ThemeDimen(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    val defaultDp: Int,
) {
    CALL_LOG_DIVIDER_THICKNESS("theme_call_log_divider_thickness", ThemeGroup.CALL_LOG, R.string.theme_call_log_divider_thickness, 1),
    CALL_LOG_DAY_DIVIDER_THICKNESS("theme_call_log_day_divider_thickness", ThemeGroup.CALL_LOG, R.string.theme_call_log_day_divider_thickness, 4),
    CALL_LOG_DATE_UNDERLINE_THICKNESS("theme_call_log_date_underline_thickness", ThemeGroup.CALL_LOG, R.string.theme_call_log_date_underline_thickness, 2),
}

/** The effective thickness (dp) for a dimen: the user override, otherwise its default. */
fun Context.themeDimenDp(dimen: ThemeDimen): Int = config.getThemeDimen(dimen.key, dimen.defaultDp)

fun Context.setThemeDimenDp(dimen: ThemeDimen, dp: Int) = config.setThemeDimen(dimen.key, dp)

/** The effective color for a slot: the user's override if set, otherwise its inherited default. */
fun Context.themeColor(slot: ThemeSlot): Int {
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // Foundation reads the stock commons colors (seeded to black/yellow on first run)
    ThemeSlot.BACKGROUND -> getProperBackgroundColor()
    ThemeSlot.PRIMARY -> getProperPrimaryColor()
    ThemeSlot.TEXT -> getProperTextColor()
    ThemeSlot.TEXT_SECONDARY -> themeColor(ThemeSlot.TEXT).adjustAlpha(0.6f)

    // Search bar: black fill, yellow text/icon/border by inheriting foundation
    ThemeSlot.SEARCH_FILL -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.SEARCH_TEXT -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SEARCH_HINT -> themeColor(ThemeSlot.PRIMARY).adjustAlpha(0.5f)
    ThemeSlot.SEARCH_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.SEARCH_BORDER -> themeColor(ThemeSlot.PRIMARY)

    // Top bar & overflow menu: icons follow the accent; the Settings header title/arrow contrast the
    // primary-coloured toolbar (matching commons' default), and the overflow menu text follows the text.
    ThemeSlot.MENU_ICON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.MENU_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.HEADER_TITLE -> themeColor(ThemeSlot.PRIMARY).getContrastColor()
    ThemeSlot.HEADER_ARROW -> themeColor(ThemeSlot.PRIMARY).getContrastColor()
    // The 設定 shortcut sits in the search bar, so it follows the accent like the other top-bar icons
    ThemeSlot.SETTINGS_BUTTON -> themeColor(ThemeSlot.PRIMARY)

    // Tabs
    ThemeSlot.TAB_BACKGROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.TAB_SELECTED -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.TAB_UNSELECTED -> themeColor(ThemeSlot.TEXT).adjustAlpha(0.6f)

    // Call log
    ThemeSlot.CALL_LOG_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CALL_LOG_SUBTITLE -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.CALL_LOG_DATE -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.CALL_LOG_DAY_DATE -> themeColor(ThemeSlot.TEXT_SECONDARY)
    // Semantic call-type colors keep their meaning by default
    ThemeSlot.CALL_LOG_MISSED -> resources.getColor(R.color.color_missed_call, theme)
    ThemeSlot.CALL_LOG_INCOMING -> resources.getColor(R.color.color_incoming_call, theme)
    ThemeSlot.CALL_LOG_OUTGOING -> resources.getColor(R.color.color_outgoing_call, theme)
    ThemeSlot.CALL_LOG_DIVIDER -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_LOG_DAY_DIVIDER -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_LOG_DATE_UNDERLINE -> themeColor(ThemeSlot.PRIMARY)

    // Dialpad
    ThemeSlot.DIALPAD_CALL_BUTTON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.DIALPAD_CALL_ICON -> themeColor(ThemeSlot.DIALPAD_CALL_BUTTON).getContrastColor()

    // In-call: accept/decline keep their semantic green/red; controls follow primary/text
    ThemeSlot.CALL_ACCEPT -> resources.getColor(R.color.md_green_400, theme)
    ThemeSlot.CALL_DECLINE -> resources.getColor(R.color.md_red_400, theme)
    ThemeSlot.CALL_CONTROL_ACTIVE -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_CONTROL_INACTIVE -> themeColor(ThemeSlot.TEXT).adjustAlpha(0.10f)

    ThemeSlot.CONTACT_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CONTACT_FASTSCROLLER -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.FAVORITE_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.FAVORITE_FASTSCROLLER -> themeColor(ThemeSlot.PRIMARY)
}

/** Set an explicit override for a slot. Foundation slots write through to the stock commons colors. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    when (slot) {
        ThemeSlot.PRIMARY -> {
            config.isSystemThemeEnabled = false
            config.primaryColor = color
        }

        ThemeSlot.BACKGROUND -> {
            config.isSystemThemeEnabled = false
            config.backgroundColor = color
        }

        ThemeSlot.TEXT -> {
            config.isSystemThemeEnabled = false
            config.textColor = color
        }

        else -> config.setThemeOverride(slot.key, color)
    }
}

/** Revert a slot to its default (palette for the editable foundation colors, inherited otherwise). */
fun Context.resetThemeColor(slot: ThemeSlot) {
    when (slot) {
        ThemeSlot.BACKGROUND -> setThemeColor(slot, PALETTE_BLACK)
        ThemeSlot.PRIMARY, ThemeSlot.TEXT -> setThemeColor(slot, PALETTE_YELLOW)
        else -> config.clearThemeOverride(slot.key)
    }
}

/** One-time seed of the default black/yellow look across the whole app (via the stock colors). */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    config.isSystemThemeEnabled = false
    config.backgroundColor = PALETTE_BLACK
    config.textColor = PALETTE_YELLOW
    config.primaryColor = PALETTE_YELLOW
    config.accentColor = PALETTE_YELLOW
    config.themeV1Seeded = true
}
