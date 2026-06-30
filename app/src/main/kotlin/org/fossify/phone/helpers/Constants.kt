package org.fossify.phone.helpers

import org.fossify.commons.helpers.TAB_CALL_HISTORY
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val GROUP_SUBSEQUENT_CALLS = "group_subsequent_calls"
const val OPEN_DIAL_PAD_AT_LAUNCH = "open_dial_pad_at_launch"
const val DISABLE_PROXIMITY_SENSOR = "disable_proximity_sensor"
const val DISABLE_SWIPE_TO_ANSWER = "disable_swipe_to_answer"
const val SHOW_TABS = "show_tabs"
const val FAVORITES_CONTACTS_ORDER = "favorites_contacts_order"
const val FAVORITES_CUSTOM_ORDER_SELECTED = "favorites_custom_order_selected"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_VIBRATION = "dialpad_vibration"
const val DIALPAD_BEEPS = "dialpad_beeps"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val ALWAYS_SHOW_FULLSCREEN = "always_show_fullscreen"
const val SIM_1_COLOR = "sim_1_color"
const val SIM_2_COLOR = "sim_2_color"
const val SWIPE_TO_CALL = "swipe_to_call"
const val USE_IMPERIAL_DATE = "use_imperial_date"
const val CALL_TIME_FORMAT = "call_time_format"
const val CALL_DURATION_FORMAT = "call_duration_format"
const val THEME_V1_SEEDED = "theme_v1_seeded"
const val PURE_YELLOW_MIGRATED = "pure_yellow_migrated"
const val OPEN_CONTACTS_APP_FOR_TAB = "open_contacts_app_for_tab"
const val FONT_FAMILY_PREFIX = "font_family_" // String, "" = system/global default
const val FONT_WEIGHT_PREFIX = "font_weight_" // Int, 0 = default, else 100..900
const val FONT_SIZE_PREFIX = "font_size_"     // Int sp, 0 = default
const val MAX_FONT_SIZE_SP = 40

// Granular theming
const val THEME_UNSET = Int.MIN_VALUE // a slot with this stored value follows its inherited default
const val PALETTE_BLACK = 0xFF000000.toInt()
const val PALETTE_YELLOW = 0xFFFFFF00.toInt()
// The material yellow PALETTE_YELLOW used to be; persisted colors carrying its RGB are rewritten
// once at startup (see migrateToPureYellowIfNeeded)
const val LEGACY_PALETTE_YELLOW = 0xFFFFEB3B.toInt()

const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_CALL_HISTORY

// Our Contacts fork (shiroikuma-renrakusaki); when installed, the Contacts and Favorites tabs hand off to it
val contactsAppPackages = listOf("shiroikuma.renrakusaki", "shiroikuma.renrakusaki.debug")

// Int extra (a commons TAB_* mask) telling renrakusaki's MainActivity which tab to open;
// must match OPEN_TAB_INTENT_EXTRA in the renrakusaki repo
const val CONTACTS_APP_OPEN_TAB_EXTRA = "shiroikuma_open_tab"
const val CONTACTS_APP_MAIN_ACTIVITY = "org.fossify.contacts.activities.MainActivity"

val tabsList = arrayListOf(TAB_CONTACTS, TAB_FAVORITES, TAB_CALL_HISTORY)

private const val PATH = "org.fossify.phone.action."
const val ACCEPT_CALL = PATH + "ACCEPT_CALL"
const val DECLINE_CALL = PATH + "DECLINE_CALL"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds
