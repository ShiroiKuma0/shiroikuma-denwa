package org.fossify.phone.extensions

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import androidx.annotation.StringRes
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.darkenColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.lightenColor
import org.fossify.commons.helpers.resetAllUiColors
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.isUiColorSet
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.uiColor
import org.fossify.phone.R
import org.fossify.phone.helpers.LEGACY_PALETTE_YELLOW
import org.fossify.phone.helpers.PALETTE_ACCEPT_GREEN
import org.fossify.phone.helpers.PALETTE_BLUE
import org.fossify.phone.helpers.PALETTE_CYAN
import org.fossify.phone.helpers.PALETTE_DECLINE_RED
import org.fossify.phone.helpers.PALETTE_DIM_GOLD
import org.fossify.phone.helpers.PALETTE_DISC_GLYPH
import org.fossify.phone.helpers.PALETTE_DISC_GREEN
import org.fossify.phone.helpers.PALETTE_DISC_RED
import org.fossify.phone.helpers.PALETTE_HELD_SHADE
import org.fossify.phone.helpers.PALETTE_NOTIFICATION_GREY
import org.fossify.phone.helpers.PALETTE_WARNING
import org.fossify.phone.helpers.PALETTE_DIM_YELLOW
import org.fossify.phone.helpers.PALETTE_GREEN
import org.fossify.phone.helpers.PALETTE_INCOMING_BLUE
import org.fossify.phone.helpers.PALETTE_INCOMING_BLUE_NIGHT
import org.fossify.phone.helpers.PALETTE_MESSAGE_BLUE
import org.fossify.phone.helpers.PALETTE_PALE_YELLOW
import org.fossify.phone.helpers.PALETTE_RED
import org.fossify.phone.helpers.PALETTE_YELLOW
import org.fossify.phone.helpers.SIM_COLOR_UNSET
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
    // The UI page itself and the stock Settings page: headings, labels, values, chips, explanations
    PAGE(R.string.theme_group_page),
    SEARCH(R.string.theme_group_search),
    CHROME(R.string.theme_group_chrome),
    TABS(R.string.theme_group_tabs),
    CALL_LOG(R.string.theme_group_call_log),
    DIALPAD(R.string.theme_group_dialpad),
    IN_CALL(R.string.theme_group_in_call),
    CAR(R.string.theme_group_car),
    NOTIFICATION(R.string.theme_group_notification),
    LISTS(R.string.theme_group_lists),
    // The caller block's fields — enumerated by the order box in ThemeActivity, never by group
    CALL_FIELDS(R.string.theme_subgroup_caller_details),
    CONTACTS(R.string.theme_group_contacts),
    FAVORITES(R.string.theme_group_favorites),
    SIM(R.string.theme_group_sim),
}

enum class ThemeSlot(
    val key: String,
    val group: ThemeGroup,
    @StringRes val labelRes: Int,
    val isFoundation: Boolean = false,
    // hasFont = true for concrete text views (family / weight / size are configurable per element)
    val hasFont: Boolean = false,
    // A slot with a role IS that commons role (helpers/UiRoles.kt) under our label and in our section:
    // it is stored in the role's own key and the commons' views read the same colour.
    val role: UiRole? = null,
) {
    // Foundation — reuse the stock commons colors (editing these repaints the whole app)
    BACKGROUND(
        "theme_background", ThemeGroup.FOUNDATION, R.string.theme_background, isFoundation = true,
        role = UiRole.BACKGROUND,
    ),
    PRIMARY("theme_primary", ThemeGroup.FOUNDATION, R.string.theme_primary, isFoundation = true, role = UiRole.ACCENT),
    TEXT("theme_text", ThemeGroup.FOUNDATION, R.string.theme_text, isFoundation = true, role = UiRole.TEXT),
    TEXT_SECONDARY("theme_text_secondary", ThemeGroup.FOUNDATION, R.string.theme_text_secondary),
    // Something is missing or was refused: no backup directory, a permission, a restore held back
    WARNING("theme_warning", ThemeGroup.FOUNDATION, R.string.theme_warning),

    // The UI page and the stock Settings page, item by item. The pills on them are the commons' button roles.
    // The explanations and the thin rule beside them — the family's dim gold
    SETTINGS_NOTE("theme_settings_note", ThemeGroup.PAGE, R.string.theme_settings_note),
    PAGE_HEADING("theme_page_heading", ThemeGroup.PAGE, R.string.theme_page_heading),
    PAGE_CHEVRON("theme_page_chevron", ThemeGroup.PAGE, R.string.theme_page_chevron),
    PAGE_SUBHEADING("theme_page_subheading", ThemeGroup.PAGE, R.string.theme_page_subheading),
    PAGE_LABEL("theme_page_label", ThemeGroup.PAGE, R.string.theme_page_label),
    PAGE_VALUE("theme_page_value", ThemeGroup.PAGE, R.string.theme_page_value),
    PAGE_CHIP("theme_page_chip", ThemeGroup.PAGE, R.string.theme_page_chip),
    PAGE_DIVIDER("theme_page_divider", ThemeGroup.PAGE, R.string.theme_page_divider),

    // A switch that is off: the outline and dot in a darker, opaque yellow (the on state is the accent)
    SWITCH_OFF("theme_switch_off", ThemeGroup.FOUNDATION, R.string.theme_switch_off, role = UiRole.SWITCH_OFF_OUTLINE),

    // Search bar
    SEARCH_FILL("theme_search_fill", ThemeGroup.SEARCH, R.string.theme_search_fill, role = UiRole.SEARCH_GROUND),
    SEARCH_TEXT(
        "theme_search_text", ThemeGroup.SEARCH, R.string.theme_search_text, hasFont = true,
        role = UiRole.SEARCH_TEXT,
    ),
    SEARCH_HINT("theme_search_hint", ThemeGroup.SEARCH, R.string.theme_search_hint, role = UiRole.SEARCH_HINT),
    SEARCH_ICON("theme_search_icon", ThemeGroup.SEARCH, R.string.theme_search_icon, role = UiRole.SEARCH_ICON),
    SEARCH_BORDER("theme_search_border", ThemeGroup.SEARCH, R.string.theme_search_border, role = UiRole.SEARCH_FRAME),

    // Top bar & overflow ("hamburger") menu
    MENU_ICON("theme_menu_icon", ThemeGroup.CHROME, R.string.theme_menu_icon),
    MENU_TEXT("theme_menu_text", ThemeGroup.CHROME, R.string.theme_menu_text, role = UiRole.MENU_LABEL),
    HEADER_TITLE("theme_header_title", ThemeGroup.CHROME, R.string.theme_header_title, role = UiRole.TOOLBAR_TITLE),
    HEADER_ARROW("theme_header_arrow", ThemeGroup.CHROME, R.string.theme_header_arrow, role = UiRole.TOOLBAR_ICON),

    // The bar that asks for "display over other apps": its ground and its words (its action is a pill)
    SNACKBAR_GROUND("theme_snackbar_ground", ThemeGroup.CHROME, R.string.theme_snackbar_ground),
    SNACKBAR_TEXT("theme_snackbar_text", ThemeGroup.CHROME, R.string.theme_snackbar_text),

    // Menu icon roles: every menu row's icon is coloured by what the row does, while its frame and
    // label stay in the accent (or, for a destructive row, in MENU_ROLE_DELETE — the warning colour).
    // Served to the sister apps as color.role.* so all three menus speak one colour language.
    // Share, view, block and delete are commons roles since 7.1.3-sk3 (the commons' own menus draw
    // them too), so each is set in one place; call, message and contact are ours alone.
    MENU_ROLE_CALL("theme_menu_role_call", ThemeGroup.CHROME, R.string.theme_menu_role_call),
    MENU_ROLE_MESSAGE("theme_menu_role_message", ThemeGroup.CHROME, R.string.theme_menu_role_message),
    MENU_ROLE_CONTACT("theme_menu_role_contact", ThemeGroup.CHROME, R.string.theme_menu_role_contact),
    MENU_ROLE_SHARE(
        "theme_menu_role_share", ThemeGroup.CHROME, R.string.theme_menu_role_share,
        role = UiRole.MENU_ICON_SHARE,
    ),
    MENU_ROLE_VIEW(
        "theme_menu_role_view", ThemeGroup.CHROME, R.string.theme_menu_role_view,
        role = UiRole.MENU_ICON_VIEW,
    ),
    MENU_ROLE_BLOCK(
        "theme_menu_role_block", ThemeGroup.CHROME, R.string.theme_menu_role_block,
        role = UiRole.MENU_ICON_BLOCK,
    ),
    MENU_ROLE_DELETE(
        "theme_menu_role_delete", ThemeGroup.CHROME, R.string.theme_menu_role_delete,
        role = UiRole.MENU_DESTRUCTIVE,
    ),

    // Tabs
    TAB_BACKGROUND(
        "theme_tab_background", ThemeGroup.TABS, R.string.theme_tab_background,
        role = UiRole.BOTTOM_BAR_GROUND,
    ),
    TAB_SELECTED("theme_tab_selected", ThemeGroup.TABS, R.string.theme_tab_selected, role = UiRole.BOTTOM_TAB_ACTIVE),
    TAB_UNSELECTED(
        "theme_tab_unselected", ThemeGroup.TABS, R.string.theme_tab_unselected,
        role = UiRole.BOTTOM_TAB_IDLE,
    ),

    // Call log (Recents)
    CALL_LOG_NAME("theme_call_log_name", ThemeGroup.CALL_LOG, R.string.theme_call_log_name, hasFont = true),
    CALL_LOG_SUBTITLE("theme_call_log_subtitle", ThemeGroup.CALL_LOG, R.string.theme_call_log_subtitle, hasFont = true),
    CALL_LOG_DATE("theme_call_log_date", ThemeGroup.CALL_LOG, R.string.theme_call_log_date, hasFont = true),
    CALL_LOG_DAY_DATE("theme_call_log_day_date", ThemeGroup.CALL_LOG, R.string.theme_call_log_day_date, hasFont = true),
    CALL_LOG_MISSED("theme_call_log_missed", ThemeGroup.CALL_LOG, R.string.theme_call_log_missed),
    CALL_LOG_INCOMING("theme_call_log_incoming", ThemeGroup.CALL_LOG, R.string.theme_call_log_incoming),
    CALL_LOG_OUTGOING("theme_call_log_outgoing", ThemeGroup.CALL_LOG, R.string.theme_call_log_outgoing),
    // A call we placed that nobody picked up: its arrow and its time
    CALL_LOG_UNANSWERED("theme_call_log_unanswered", ThemeGroup.CALL_LOG, R.string.theme_call_log_unanswered),
    CALL_LOG_DIVIDER("theme_call_log_divider", ThemeGroup.CALL_LOG, R.string.theme_call_log_divider),
    CALL_LOG_DAY_DIVIDER("theme_call_log_day_divider", ThemeGroup.CALL_LOG, R.string.theme_call_log_day_divider),
    CALL_LOG_DATE_UNDERLINE("theme_call_log_date_underline", ThemeGroup.CALL_LOG, R.string.theme_call_log_date_underline),
    // The three-line row: line 2 (number · label), the country at the right of line 3, and the two
    // actions before the row's ⋮. CALL_LOG_SUBTITLE keeps the duration.
    CALL_LOG_DURATION(
        "theme_call_log_duration", ThemeGroup.CALL_LOG, R.string.theme_call_log_duration, hasFont = true,
    ),
    // The contact's label for the number (携帯, 自宅 …), after the “·” on the same line
    CALL_LOG_NUMBER_LABEL(
        "theme_call_log_number_label", ThemeGroup.CALL_LOG, R.string.theme_call_log_number_label,
    ),
    CALL_LOG_NUMBER(
        "theme_call_log_number", ThemeGroup.CALL_LOG, R.string.theme_call_log_number, hasFont = true,
    ),
    CALL_LOG_COUNTRY(
        "theme_call_log_country", ThemeGroup.CALL_LOG, R.string.theme_call_log_country, hasFont = true,
    ),
    CALL_LOG_ACTION_MESSAGE(
        "theme_call_log_action_message", ThemeGroup.CALL_LOG, R.string.theme_call_log_action_message,
    ),
    CALL_LOG_ACTION_CALL(
        "theme_call_log_action_call", ThemeGroup.CALL_LOG, R.string.theme_call_log_action_call,
    ),
    // Drawn by 臨電話 only (we have no failure reason and no 臨N badge), but set here so its rows follow
    // this page like ours do — see providers/CallLogStyleProvider
    CALL_LOG_FAILURE("theme_call_log_failure", ThemeGroup.CALL_LOG, R.string.theme_call_log_failure),
    RIN_BADGE("theme_rin_badge", ThemeGroup.CALL_LOG, R.string.theme_rin_badge),
    RIN_BADGE_TEXT("theme_rin_badge_text", ThemeGroup.CALL_LOG, R.string.theme_rin_badge_text),

    // The row's ⋮, and the "Calls with …" banner of a filtered log (its words and its cross)
    CALL_LOG_OVERFLOW("theme_call_log_overflow", ThemeGroup.CALL_LOG, R.string.theme_call_log_overflow),
    CALL_LOG_FILTER_BANNER("theme_call_log_filter_banner", ThemeGroup.CALL_LOG, R.string.theme_call_log_filter_banner),

    // Dialpad — every part of a key and of the line above the keys
    DIALPAD_GROUND("theme_dialpad_ground", ThemeGroup.DIALPAD, R.string.theme_dialpad_ground),
    DIALPAD_KEY_RING("theme_dialpad_key_ring", ThemeGroup.DIALPAD, R.string.theme_dialpad_key_ring),
    DIALPAD_DIGIT("theme_dialpad_digit", ThemeGroup.DIALPAD, R.string.theme_dialpad_digit),
    DIALPAD_LETTERS("theme_dialpad_letters", ThemeGroup.DIALPAD, R.string.theme_dialpad_letters),
    DIALPAD_INPUT("theme_dialpad_input", ThemeGroup.DIALPAD, R.string.theme_dialpad_input),
    DIALPAD_ICON("theme_dialpad_icon", ThemeGroup.DIALPAD, R.string.theme_dialpad_icon),
    DIALPAD_CALL_BUTTON("theme_dialpad_call_button", ThemeGroup.DIALPAD, R.string.theme_dialpad_call_button),

    // In-call. The ringing screen: the two rings, the handle dragged between them and its arrows, the
    // three buttons above them. The call itself: the control rings, the status line, the held call's bar.
    CALL_ACCEPT_RING("theme_call_accept_ring", ThemeGroup.IN_CALL, R.string.theme_call_accept_ring),
    CALL_END_RING("theme_call_end_ring", ThemeGroup.IN_CALL, R.string.theme_call_end_ring),
    CALL_ACCEPT("theme_call_accept", ThemeGroup.IN_CALL, R.string.theme_call_accept),
    CALL_DECLINE("theme_call_decline", ThemeGroup.IN_CALL, R.string.theme_call_decline),
    CALL_DRAG_HANDLE("theme_call_drag_handle", ThemeGroup.IN_CALL, R.string.theme_call_drag_handle),
    CALL_BUTTON_LABEL("theme_call_button_label", ThemeGroup.IN_CALL, R.string.theme_call_button_label),
    CALL_SILENCE("theme_call_silence", ThemeGroup.IN_CALL, R.string.theme_call_silence),
    CALL_MESSAGE("theme_call_message", ThemeGroup.IN_CALL, R.string.theme_call_message),
    CALL_BLOCK("theme_call_block", ThemeGroup.IN_CALL, R.string.theme_call_block),
    CALL_BLOCK_LABEL("theme_call_block_label", ThemeGroup.IN_CALL, R.string.theme_call_block_label),
    CALL_UNDO_GROUND("theme_call_undo_ground", ThemeGroup.IN_CALL, R.string.theme_call_undo_ground),
    CALL_UNDO_FRAME("theme_call_undo_frame", ThemeGroup.IN_CALL, R.string.theme_call_undo_frame),
    CALL_UNDO_TEXT("theme_call_undo_text", ThemeGroup.IN_CALL, R.string.theme_call_undo_text),
    CALL_CONTROL_ACTIVE("theme_call_control_active", ThemeGroup.IN_CALL, R.string.theme_call_control_active),
    CALL_CONTROL_INACTIVE("theme_call_control_inactive", ThemeGroup.IN_CALL, R.string.theme_call_control_inactive),
    CALL_STATUS("theme_call_status", ThemeGroup.IN_CALL, R.string.theme_call_status),
    CALL_ON_HOLD("theme_call_on_hold", ThemeGroup.IN_CALL, R.string.theme_call_on_hold),
    CALL_HELD_GROUND("theme_call_held_ground", ThemeGroup.IN_CALL, R.string.theme_call_held_ground),
    CALL_HELD_TEXT("theme_call_held_text", ThemeGroup.IN_CALL, R.string.theme_call_held_text),
    CALL_AVATAR_GROUND("theme_call_avatar_ground", ThemeGroup.IN_CALL, R.string.theme_call_avatar_ground),
    CALL_AVATAR_ICON("theme_call_avatar_icon", ThemeGroup.IN_CALL, R.string.theme_call_avatar_icon),
    CALL_ICON("theme_call_icon", ThemeGroup.IN_CALL, R.string.theme_call_icon),
    CALL_DIALPAD_GROUND("theme_call_dialpad_ground", ThemeGroup.IN_CALL, R.string.theme_call_dialpad_ground),
    CALL_SEND("theme_call_send", ThemeGroup.IN_CALL, R.string.theme_call_send),

    // The car call screen — the one place whose two buttons are filled discs (白い熊, 2026-11-19)
    CAR_GROUND("theme_car_ground", ThemeGroup.CAR, R.string.theme_car_ground),
    CAR_TEXT("theme_car_text", ThemeGroup.CAR, R.string.theme_car_text),
    CAR_ACCEPT("theme_car_accept", ThemeGroup.CAR, R.string.theme_car_accept),
    CAR_END("theme_car_end", ThemeGroup.CAR, R.string.theme_car_end),
    CAR_BUTTON_ICON("theme_car_button_icon", ThemeGroup.CAR, R.string.theme_car_button_icon),

    // The call notification's three buttons (its ground and its text are the system's)
    NOTIFICATION_ACCEPT("theme_notification_accept", ThemeGroup.NOTIFICATION, R.string.theme_notification_accept),
    NOTIFICATION_DECLINE("theme_notification_decline", ThemeGroup.NOTIFICATION, R.string.theme_notification_decline),
    NOTIFICATION_MESSAGE("theme_notification_message", ThemeGroup.NOTIFICATION, R.string.theme_notification_message),

    // Lists in general: what an empty one says, the letters beside one, the handle that drags a row
    EMPTY_LIST_TEXT("theme_empty_list_text", ThemeGroup.LISTS, R.string.theme_empty_list_text),
    FASTSCROLLER_LETTERS("theme_fastscroller_letters", ThemeGroup.LISTS, R.string.theme_fastscroller_letters),
    LIST_HANDLE("theme_list_handle", ThemeGroup.LISTS, R.string.theme_list_handle),
    CONFERENCE_NAME("theme_conference_name", ThemeGroup.LISTS, R.string.theme_conference_name),

    // The caller block's fields (helpers/CallScreenFields) — one styling slot each, so every line of
    // the block carries its own font, weight, size and colour. Only the ticked ones get a row in the
    // Theme screen, right under the order box that ticks them.
    CALL_DISPLAY_NAME(
        "theme_call_field_display_name", ThemeGroup.CALL_FIELDS, R.string.field_display_name, hasFont = true,
    ),
    CALL_SURNAME_FIRST(
        "theme_call_field_surname_first", ThemeGroup.CALL_FIELDS, R.string.field_surname_first, hasFont = true,
    ),
    CALL_FIRST_SURNAME(
        "theme_call_field_first_surname", ThemeGroup.CALL_FIELDS, R.string.field_first_surname, hasFont = true,
    ),
    CALL_FIRST_SURNAME_CAPS(
        "theme_call_field_first_surname_caps", ThemeGroup.CALL_FIELDS,
        R.string.field_first_surname_caps, hasFont = true,
    ),
    CALL_SURNAME_CAPS_FIRST(
        "theme_call_field_surname_caps_first", ThemeGroup.CALL_FIELDS,
        R.string.field_surname_caps_first, hasFont = true,
    ),
    CALL_PREFIX(
        "theme_call_field_prefix", ThemeGroup.CALL_FIELDS, R.string.field_prefix, hasFont = true,
    ),
    CALL_FIRST_NAME(
        "theme_call_field_first_name", ThemeGroup.CALL_FIELDS, R.string.field_first_name, hasFont = true,
    ),
    CALL_MIDDLE_NAME(
        "theme_call_field_middle_name", ThemeGroup.CALL_FIELDS, R.string.field_middle_name, hasFont = true,
    ),
    CALL_SURNAME(
        "theme_call_field_surname", ThemeGroup.CALL_FIELDS, R.string.field_surname, hasFont = true,
    ),
    CALL_SUFFIX(
        "theme_call_field_suffix", ThemeGroup.CALL_FIELDS, R.string.field_suffix, hasFont = true,
    ),
    CALL_NICKNAME(
        "theme_call_field_nickname", ThemeGroup.CALL_FIELDS, R.string.field_nickname, hasFont = true,
    ),
    CALL_NUMBER(
        "theme_call_field_number", ThemeGroup.CALL_FIELDS, R.string.field_phone, hasFont = true,
    ),
    CALL_NUMBER_LABEL(
        "theme_call_field_number_label", ThemeGroup.CALL_FIELDS, R.string.field_number_label, hasFont = true,
    ),
    CALL_COMPANY(
        "theme_call_field_company", ThemeGroup.CALL_FIELDS, R.string.field_company, hasFont = true,
    ),
    CALL_POSITION(
        "theme_call_field_position", ThemeGroup.CALL_FIELDS, R.string.field_position, hasFont = true,
    ),
    CALL_NOTE(
        "theme_call_field_note", ThemeGroup.CALL_FIELDS, R.string.field_note, hasFont = true,
    ),

    // Contacts
    CONTACT_NAME("theme_contact_name", ThemeGroup.CONTACTS, R.string.theme_contact_name, hasFont = true),
    CONTACT_FASTSCROLLER("theme_contact_fastscroller", ThemeGroup.CONTACTS, R.string.theme_contact_fastscroller),

    // Favorites
    FAVORITE_NAME("theme_favorite_name", ThemeGroup.FAVORITES, R.string.theme_favorite_name, hasFont = true),
    FAVORITE_FASTSCROLLER("theme_favorite_fastscroller", ThemeGroup.FAVORITES, R.string.theme_favorite_fastscroller),

    // SIM cards — the number drawn on the badge (the badge fill itself is SIM_1_COLOR / SIM_2_COLOR,
    // which map onto the system's SIM accounts and so live outside the slot machinery)
    SIM_TEXT("theme_sim_text", ThemeGroup.SIM, R.string.sim_text_color),
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
    slot.role?.let { return uiColor(it) }
    val override = config.getThemeOverride(slot.key)
    return if (override != THEME_UNSET) override else themeDefault(slot)
}

// One arm per slot — the count is inherent to an exhaustive map, not real complexity.
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun Context.themeDefault(slot: ThemeSlot): Int = when (slot) {
    // A slot that is a commons role never gets here (themeColor returns the role); the arm keeps the map total.
    ThemeSlot.BACKGROUND, ThemeSlot.PRIMARY, ThemeSlot.TEXT, ThemeSlot.SWITCH_OFF,
        ThemeSlot.SEARCH_FILL, ThemeSlot.SEARCH_TEXT, ThemeSlot.SEARCH_HINT, ThemeSlot.SEARCH_ICON,
        ThemeSlot.SEARCH_BORDER, ThemeSlot.MENU_TEXT, ThemeSlot.HEADER_TITLE, ThemeSlot.HEADER_ARROW,
        ThemeSlot.TAB_BACKGROUND, ThemeSlot.TAB_SELECTED, ThemeSlot.TAB_UNSELECTED,
        ThemeSlot.MENU_ROLE_SHARE, ThemeSlot.MENU_ROLE_VIEW, ThemeSlot.MENU_ROLE_BLOCK,
        ThemeSlot.MENU_ROLE_DELETE -> uiColor(slot.role!!)

    ThemeSlot.TEXT_SECONDARY -> themeColor(ThemeSlot.TEXT).adjustAlpha(0.6f)
    ThemeSlot.WARNING -> PALETTE_WARNING
    ThemeSlot.SETTINGS_NOTE -> PALETTE_DIM_GOLD
    ThemeSlot.PAGE_HEADING, ThemeSlot.PAGE_CHEVRON, ThemeSlot.PAGE_SUBHEADING, ThemeSlot.PAGE_CHIP ->
        themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.PAGE_LABEL, ThemeSlot.PAGE_VALUE -> themeColor(ThemeSlot.TEXT)
    // an opaque darker accent, as the menus' group divider
    ThemeSlot.PAGE_DIVIDER -> uiColor(UiRole.MENU_DIVIDER)
    ThemeSlot.SNACKBAR_GROUND -> themeColor(ThemeSlot.BACKGROUND).darkenColor()
    ThemeSlot.SNACKBAR_TEXT -> themeColor(ThemeSlot.TEXT)

    // The search bar's own ⋮ and the icons beside it follow the accent
    ThemeSlot.MENU_ICON -> themeColor(ThemeSlot.PRIMARY)

    ThemeSlot.MENU_ROLE_CALL -> PALETTE_GREEN
    ThemeSlot.MENU_ROLE_MESSAGE -> PALETTE_MESSAGE_BLUE
    ThemeSlot.MENU_ROLE_CONTACT -> PALETTE_CYAN

    // Call log
    ThemeSlot.CALL_LOG_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CALL_LOG_SUBTITLE -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.CALL_LOG_DATE -> themeColor(ThemeSlot.TEXT_SECONDARY)
    ThemeSlot.CALL_LOG_DAY_DATE -> themeColor(ThemeSlot.TEXT_SECONDARY)
    // Semantic call-type colors keep their meaning by default
    ThemeSlot.CALL_LOG_MISSED -> PALETTE_RED
    ThemeSlot.CALL_LOG_INCOMING -> if (isSystemNight()) PALETTE_INCOMING_BLUE_NIGHT else PALETTE_INCOMING_BLUE
    // A call we placed and that was answered wears the accent, arrow and time alike; one that was not
    // answered wears pure blue
    ThemeSlot.CALL_LOG_OUTGOING -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_LOG_UNANSWERED -> PALETTE_BLUE
    // Between two calls of one day: thinner and paler than the day's line, so the days still read first
    ThemeSlot.CALL_LOG_DIVIDER -> PALETTE_DIM_YELLOW
    ThemeSlot.CALL_LOG_DAY_DIVIDER -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_LOG_DATE_UNDERLINE -> themeColor(ThemeSlot.PRIMARY)
    // The row's own lines are accent by default, each its own slot — not the secondary text colour
    ThemeSlot.CALL_LOG_NUMBER -> PALETTE_PALE_YELLOW
    ThemeSlot.CALL_LOG_NUMBER_LABEL -> PALETTE_YELLOW
    ThemeSlot.CALL_LOG_DURATION -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_LOG_COUNTRY -> themeColor(ThemeSlot.PRIMARY)
    // The message bubble is an accent outline; the call-back handset wears its menu row's colour
    ThemeSlot.CALL_LOG_ACTION_MESSAGE -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_LOG_ACTION_CALL -> themeColor(ThemeSlot.MENU_ROLE_CALL)
    ThemeSlot.CALL_LOG_FAILURE -> themeColor(ThemeSlot.CALL_LOG_MISSED)
    ThemeSlot.RIN_BADGE -> PALETTE_YELLOW
    ThemeSlot.RIN_BADGE_TEXT -> PALETTE_BLUE

    ThemeSlot.CALL_LOG_OVERFLOW, ThemeSlot.CALL_LOG_FILTER_BANNER -> themeColor(ThemeSlot.TEXT)

    // Dialpad: keys ringed in the accent, everything written in the text colour
    ThemeSlot.DIALPAD_GROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.DIALPAD_KEY_RING, ThemeSlot.DIALPAD_CALL_BUTTON -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.DIALPAD_DIGIT, ThemeSlot.DIALPAD_LETTERS, ThemeSlot.DIALPAD_INPUT, ThemeSlot.DIALPAD_ICON ->
        themeColor(ThemeSlot.TEXT)

    // In-call: the rings are pure green and red; the arrows, "on hold" and the block octagon the softer
    // pair; the rest follows accent, text and background
    ThemeSlot.CALL_ACCEPT_RING -> PALETTE_GREEN
    ThemeSlot.CALL_END_RING -> PALETTE_RED
    ThemeSlot.CALL_ACCEPT -> PALETTE_ACCEPT_GREEN
    ThemeSlot.CALL_DECLINE -> PALETTE_DECLINE_RED
    // both wore the decline colour before they had roles of their own, and still follow it
    ThemeSlot.CALL_ON_HOLD, ThemeSlot.CALL_BLOCK -> themeColor(ThemeSlot.CALL_DECLINE)
    ThemeSlot.CALL_DRAG_HANDLE, ThemeSlot.CALL_BUTTON_LABEL, ThemeSlot.CALL_STATUS, ThemeSlot.CALL_HELD_TEXT,
        ThemeSlot.CALL_ICON -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CALL_SILENCE, ThemeSlot.CALL_MESSAGE, ThemeSlot.CALL_BLOCK_LABEL, ThemeSlot.CALL_UNDO_FRAME,
        ThemeSlot.CALL_UNDO_TEXT, ThemeSlot.CALL_AVATAR_GROUND, ThemeSlot.CALL_SEND -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_UNDO_GROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.CALL_HELD_GROUND -> PALETTE_HELD_SHADE
    ThemeSlot.CALL_AVATAR_ICON -> themeColor(ThemeSlot.CALL_AVATAR_GROUND).getContrastColor()
    ThemeSlot.CALL_DIALPAD_GROUND ->
        if (isSystemNight()) themeColor(ThemeSlot.BACKGROUND).lightenColor(2) else themeColor(ThemeSlot.BACKGROUND)

    // The car call screen: filled discs in the settled green and red, a white handset on them
    ThemeSlot.CAR_GROUND -> themeColor(ThemeSlot.BACKGROUND)
    ThemeSlot.CAR_TEXT -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CAR_ACCEPT, ThemeSlot.NOTIFICATION_ACCEPT -> PALETTE_DISC_GREEN
    ThemeSlot.CAR_END, ThemeSlot.NOTIFICATION_DECLINE -> PALETTE_DISC_RED
    ThemeSlot.CAR_BUTTON_ICON -> PALETTE_DISC_GLYPH
    ThemeSlot.NOTIFICATION_MESSAGE -> PALETTE_NOTIFICATION_GREY

    ThemeSlot.EMPTY_LIST_TEXT, ThemeSlot.FASTSCROLLER_LETTERS, ThemeSlot.LIST_HANDLE, ThemeSlot.CONFERENCE_NAME ->
        themeColor(ThemeSlot.TEXT)
    ThemeSlot.CALL_CONTROL_ACTIVE -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.CALL_CONTROL_INACTIVE -> themeColor(ThemeSlot.SWITCH_OFF) // a ring's colour now, not a fill

    // The caller block: the name shapes read as the headline, everything under them as its detail
    ThemeSlot.CALL_DISPLAY_NAME, ThemeSlot.CALL_SURNAME_FIRST, ThemeSlot.CALL_FIRST_SURNAME,
        ThemeSlot.CALL_FIRST_SURNAME_CAPS, ThemeSlot.CALL_SURNAME_CAPS_FIRST ->
        themeColor(ThemeSlot.TEXT)

    ThemeSlot.CALL_PREFIX, ThemeSlot.CALL_FIRST_NAME, ThemeSlot.CALL_MIDDLE_NAME, ThemeSlot.CALL_SURNAME,
        ThemeSlot.CALL_SUFFIX, ThemeSlot.CALL_NICKNAME, ThemeSlot.CALL_NUMBER, ThemeSlot.CALL_NUMBER_LABEL,
        ThemeSlot.CALL_COMPANY, ThemeSlot.CALL_POSITION, ThemeSlot.CALL_NOTE ->
        themeColor(ThemeSlot.TEXT_SECONDARY)

    ThemeSlot.CONTACT_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.CONTACT_FASTSCROLLER -> themeColor(ThemeSlot.PRIMARY)
    ThemeSlot.FAVORITE_NAME -> themeColor(ThemeSlot.TEXT)
    ThemeSlot.FAVORITE_FASTSCROLLER -> themeColor(ThemeSlot.PRIMARY)

    // The SIM number sits on the badge, not on the app background, so it keeps its own yellow rather
    // than inheriting the foundation text colour
    ThemeSlot.SIM_TEXT -> PALETTE_YELLOW
}

private fun Context.isSystemNight() =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** Set an explicit colour for a slot — in the commons role's key when the slot is one. */
fun Context.setThemeColor(slot: ThemeSlot, color: Int) {
    val role = slot.role
    if (role != null) setUiColor(role, color) else config.setThemeOverride(slot.key, color)
}

/** Revert a slot to its default (the role's, or the inherited one). */
fun Context.resetThemeColor(slot: ThemeSlot) {
    val role = slot.role
    if (role != null) resetUiColor(role) else config.clearThemeOverride(slot.key)
}

/** The commons roles a slot stands for — the UI page lists every other role by itself. */
val rolesShownAsSlots: Set<UiRole> by lazy {
    ThemeSlot.entries.filter { it != ThemeSlot.SWITCH_OFF }.mapNotNull { it.role }.toSet()
}

/**
 * Every colour back on its default: the commons roles, this app's own slots and the two SIM fills.
 * Fonts, sizes and line thicknesses are not colours and stay.
 */
fun Context.resetAllThemeColors() {
    resetAllUiColors()
    ThemeSlot.entries.forEach { config.clearThemeOverride(it.key) }
    setSimColor(1, SIM_COLOR_UNSET)
    setSimColor(2, SIM_COLOR_UNSET)
}

// -------------------------------------------------------------------------------------------------
// SIM badge palette
//
// A SIM badge is the little card glyph with the SIM's number drawn on it — in the call log, on the
// call screen, and (as the 1/2 phone glyph) on a swipe-to-call row. Its fill is per-SIM and its
// number is one colour for both, all three settable under "SIM cards" in the Theme screen.
// Nothing is contrast-adjusted on the way out: every one of these colours is an explicit choice, so
// the badge wears exactly what the picker showed.
// -------------------------------------------------------------------------------------------------

/** The badge fill for SIM [simId] — the user's own colour if they picked one, else the fork default. */
fun Context.simColor(simId: Int): Int {
    val stored = if (simId == 1) config.sim1Color else config.sim2Color
    return if (stored != SIM_COLOR_UNSET) stored else simDefaultColor(simId)
}

/** Store (or, with [SIM_COLOR_UNSET], forget) an explicit fill for SIM [simId]. */
fun Context.setSimColor(simId: Int, color: Int) {
    if (simId == 1) config.sim1Color = color else config.sim2Color = color
}

/**
 * The fill a SIM account should be built with: ours for SIM 1 and SIM 2, the system's highlight colour
 * for any beyond them (rare, and with nowhere in the UI to set one).
 */
fun Context.simColorOrSystem(simId: Int, systemColor: Int): Int =
    if (simId == 1 || simId == 2) simColor(simId) else systemColor

/** The fork's own SIM fills — what the picker's "Default" button restores. */
fun simDefaultColor(simId: Int): Int = if (simId == 1) PALETTE_RED else PALETTE_BLUE

/** The colour of the number on a SIM badge, shared by both SIMs. */
fun Context.simTextColor(): Int = themeColor(ThemeSlot.SIM_TEXT)

/** That number's [Typeface] style — bold unless turned off in the Theme screen. */
fun Context.simTextStyle(): Int = if (config.simTextBold) Typeface.BOLD else Typeface.NORMAL

/** One-time seed of the default black/yellow look across the whole app (via the stock colors). */
fun Context.seedBlackYellowThemeIfNeeded() {
    if (config.themeV1Seeded) {
        return
    }

    // Black and yellow are the commons' own defaults since sk18 (helpers/UiRoles.kt), so nothing is
    // written any more: an unset foundation role is the family's colour.
    config.themeV1Seeded = true
}

// Fork: the accent frame commons draws around every dialog, in dp.
private const val DIALOG_FRAME_WIDTH_DP = 2

/**
 * The two switches of the family's look that are not colours: the dialog frame's width (0 = none, what
 * a stock consumer gets) and pill dialog buttons. The frame's colour, the destructive button's and a
 * switch's off colour are commons roles since sk18 (`UiRole.DIALOG_FRAME`, `BUTTON_DESTRUCTIVE`,
 * `SWITCH_OFF_OUTLINE`), set on the UI page — nothing is mirrored into the commons any more.
 */
fun Context.syncDialogFrame() {
    if (config.dialogBorderWidth != DIALOG_FRAME_WIDTH_DP) {
        config.dialogBorderWidth = DIALOG_FRAME_WIDTH_DP
    }

    if (!config.pillDialogButtons) {
        config.pillDialogButtons = true
    }
}

/**
 * One-time per hand-over: a slot that is now a commons role hands its stored colour over to the role's
 * key, so what was picked before the move is kept. Two hand-overs so far — commons 6.1.6-sk18 (the
 * search, bar, header and tab slots) and 7.1.3-sk3 (the menu icons for share, view and block, and the
 * destructive menu row). The loop is the same for both: a slot handed over earlier has no stored
 * colour left. Foundation slots always lived in the stock keys the roles read.
 *
 * At sk18 the delete colour, until then mirrored into the commons' destructive dialog button, also
 * seeded that button's own role.
 */
fun Context.migrateSlotsToRolesIfNeeded() {
    if (config.slotsMovedToRoles && config.menuSlotsMovedToRoles) {
        return
    }

    if (!config.slotsMovedToRoles) {
        val warning = config.getThemeOverride(ThemeSlot.MENU_ROLE_DELETE.key)
        if (warning != THEME_UNSET && !isUiColorSet(UiRole.BUTTON_DESTRUCTIVE)) {
            setUiColor(UiRole.BUTTON_DESTRUCTIVE, warning)
        }
    }

    ThemeSlot.entries.forEach { slot ->
        val role = slot.role ?: return@forEach
        if (role.isFoundation) return@forEach
        val override = config.getThemeOverride(slot.key)
        if (override != THEME_UNSET) {
            setUiColor(role, override)
            config.clearThemeOverride(slot.key)
        }
    }
    config.slotsMovedToRoles = true
    config.menuSlotsMovedToRoles = true
}

/**
 * One-time: a call divider that was switched off (0 dp) comes back at its defaults — 1 dp in
 * [PALETTE_DIM_YELLOW] — the line 白い熊 asked for on 2026-11-17. A divider that was showing keeps
 * what was picked for it, and 0 can be set again afterwards.
 */
fun Context.migrateCallDividerIfNeeded() {
    if (config.callDividerMigrated) {
        return
    }

    if (themeDimenDp(ThemeDimen.CALL_LOG_DIVIDER_THICKNESS) <= 0) {
        setThemeDimenDp(ThemeDimen.CALL_LOG_DIVIDER_THICKNESS, ThemeDimen.CALL_LOG_DIVIDER_THICKNESS.defaultDp)
        config.clearThemeOverride(ThemeSlot.CALL_LOG_DIVIDER.key)
    }
    config.callDividerMigrated = true
}

/** One-time rewrite of every persisted legacy material-yellow color to pure [PALETTE_YELLOW]. */
fun Context.migrateToPureYellowIfNeeded() {
    if (config.pureYellowMigrated) {
        return
    }

    // Stored colors can carry alpha (AlphaColorPickerDialog), so only the RGB part is compared and replaced.
    fun fix(color: Int): Int = if (color and 0xFFFFFF == LEGACY_PALETTE_YELLOW and 0xFFFFFF) {
        (color and 0xFF000000.toInt()) or (PALETTE_YELLOW and 0xFFFFFF)
    } else {
        color
    }

    config.apply {
        backgroundColor = fix(backgroundColor)
        textColor = fix(textColor)
        primaryColor = fix(primaryColor)
        accentColor = fix(accentColor)
        sim1Color = fix(sim1Color)
        sim2Color = fix(sim2Color)
        ThemeSlot.entries
            .filter { getThemeOverride(it.key) != THEME_UNSET }
            .forEach { setThemeOverride(it.key, fix(getThemeOverride(it.key))) }
        pureYellowMigrated = true
    }
}
