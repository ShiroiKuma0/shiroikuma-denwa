package org.fossify.phone.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.helpers.FontHelper
import org.fossify.phone.adapters.RecentCallsAdapter
import org.fossify.phone.extensions.ThemeDimen
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.simColor
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.extensions.themeDimenDp
import org.fossify.phone.helpers.CallStamps
import java.io.File

/**
 * Read-only view of the call log's look, for renrakusaki's per-contact call history and rindenwa's SIP
 * history, so they draw their rows exactly like our Recents without copying any of our settings.
 *
 * - `content://<applicationId>.calllogstyle/style` — a `key` / `value` cursor (both strings): colours
 *   as decimal ARGB ints already resolved through their defaults, thicknesses in dp, per-slot fonts,
 *   the base text size in px and the format choices. [VERSION] goes up whenever a key changes meaning.
 * - `content://<applicationId>.calllogstyle/font/<file name>` — an imported font file, read-only.
 * - `content://<applicationId>.calllogstyle/stamps` — the call stamps (`date`, `number`, `stamp` as JSON),
 *   so a call drawn elsewhere keeps the SIM badge it was made with; selection arguments narrow it to
 *   the calls with those numbers.
 *
 * Only renrakusaki (release and debug) and rindenwa get an answer; anyone else gets null. The contract is
 * documented in CLAUDE.md beside the tab hand-off.
 */
class CallLogStyleProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? {
        val ctx = context ?: return null
        if (!isAllowedCaller()) return null
        return when (u.pathSegments.singleOrNull()) {
            PATH_STYLE -> styleCursor(ctx)
            PATH_STAMPS -> stampsCursor(ctx, a)
            else -> null
        }
    }

    private fun styleCursor(ctx: Context): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN_KEY, COLUMN_VALUE))
        styleRows(ctx).forEach { (key, value) -> cursor.addRow(arrayOf(key, value)) }
        return cursor
    }

    // One row per stamped call: when it was logged, with which number, and its CallStamp as JSON.
    // With selection arguments, only the calls with one of those numbers (compared as phone numbers).
    private fun stampsCursor(ctx: Context, numbers: Array<String>?): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN_DATE, COLUMN_NUMBER, COLUMN_STAMP))
        CallStamps.all(ctx)
            .filter { item -> numbers.isNullOrEmpty() || numbers.any { it == item.number || PhoneNumberUtils.compare(it, item.number) } }
            .forEach { cursor.addRow(arrayOf<Any>(it.date, it.number, it.stamp.toJson().toString())) }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        if (!isAllowedCaller() || mode != "r") return null
        val name = fontName(uri) ?: return null
        val file = File(FontHelper.getFontsDir(ctx), name)
        if (!file.isFile) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    // The file name in font/<name>, or null for any other path or a name that could leave the fonts dir
    private fun fontName(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != PATH_FONT) return null
        return segments[1].takeUnless { it.isEmpty() || it.contains('/') || it.contains("..") }
    }

    private fun isAllowedCaller() = callingPackage in ALLOWED_CALLERS

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    companion object {
        const val PATH_STYLE = "style"
        const val PATH_FONT = "font"
        const val PATH_STAMPS = "stamps"
        const val COLUMN_DATE = "date"
        const val COLUMN_NUMBER = "number"
        const val COLUMN_STAMP = "stamp"
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
        const val VERSION = 1

        private val ALLOWED_CALLERS = setOf(
            "shiroikuma.renrakusaki", "shiroikuma.renrakusaki.debug", "shiroikuma.rindenwa",
        )

        private val COLOR_SLOTS = listOf(
            "name" to ThemeSlot.CALL_LOG_NAME,
            "subtitle" to ThemeSlot.CALL_LOG_SUBTITLE,
            "date" to ThemeSlot.CALL_LOG_DATE,
            "day_date" to ThemeSlot.CALL_LOG_DAY_DATE,
            "missed" to ThemeSlot.CALL_LOG_MISSED,
            "incoming" to ThemeSlot.CALL_LOG_INCOMING,
            "outgoing" to ThemeSlot.CALL_LOG_OUTGOING,
            "divider" to ThemeSlot.CALL_LOG_DIVIDER,
            "day_divider" to ThemeSlot.CALL_LOG_DAY_DIVIDER,
            "date_underline" to ThemeSlot.CALL_LOG_DATE_UNDERLINE,
            "sim_text" to ThemeSlot.SIM_TEXT,
            "background" to ThemeSlot.BACKGROUND,
            // Added for the three-line row (2026-11-17). Pure additions: version stays 1.
            "number" to ThemeSlot.CALL_LOG_NUMBER,
            "number_label" to ThemeSlot.CALL_LOG_NUMBER_LABEL,
            "duration" to ThemeSlot.CALL_LOG_DURATION,
            "country" to ThemeSlot.CALL_LOG_COUNTRY,
            "action_message" to ThemeSlot.CALL_LOG_ACTION_MESSAGE,
            "action_call" to ThemeSlot.CALL_LOG_ACTION_CALL,
            "failure" to ThemeSlot.CALL_LOG_FAILURE,
            "unanswered" to ThemeSlot.CALL_LOG_UNANSWERED,
            "rin_badge" to ThemeSlot.RIN_BADGE,
            "rin_badge_text" to ThemeSlot.RIN_BADGE_TEXT,
            "role.call" to ThemeSlot.MENU_ROLE_CALL,
            "role.message" to ThemeSlot.MENU_ROLE_MESSAGE,
            "role.contact" to ThemeSlot.MENU_ROLE_CONTACT,
            "role.share" to ThemeSlot.MENU_ROLE_SHARE,
            "role.view" to ThemeSlot.MENU_ROLE_VIEW,
            "role.block" to ThemeSlot.MENU_ROLE_BLOCK,
            "role.delete" to ThemeSlot.MENU_ROLE_DELETE,
        )

        // The call log's text slots — the only ones with a font of their own
        private val FONT_SLOTS = COLOR_SLOTS.filter { it.second.hasFont }

        private val THICKNESSES = listOf(
            "divider" to ThemeDimen.CALL_LOG_DIVIDER_THICKNESS,
            "day_divider" to ThemeDimen.CALL_LOG_DAY_DIVIDER_THICKNESS,
            "date_underline" to ThemeDimen.CALL_LOG_DATE_UNDERLINE_THICKNESS,
        )

        private fun Boolean.flag() = if (this) "1" else "0"

        /** Every key the style path answers, in the order the contract lists them. */
        fun styleRows(ctx: Context): List<Pair<String, String>> {
            val config = ctx.config
            val rows = mutableListOf<Pair<String, String>>()
            COLOR_SLOTS.forEach { (name, slot) -> rows += "color.$name" to ctx.themeColor(slot).toString() }
            rows += "color.sim1" to ctx.simColor(1).toString()
            rows += "color.sim2" to ctx.simColor(2).toString()
            THICKNESSES.forEach { (name, dimen) -> rows += "thickness.$name" to ctx.themeDimenDp(dimen).toString() }
            FONT_SLOTS.forEach { (name, slot) ->
                rows += "font.$name.family" to config.getFontFamily(slot.key)
                rows += "font.$name.weight" to config.getFontWeight(slot.key).toString()
                rows += "font.$name.size" to config.getFontSize(slot.key).toString()
            }
            rows += "text_size_px" to ctx.getTextSize().toString()
            rows += "number_scale" to RecentCallsAdapter.NUMBER_LINE_FONT_SCALE.toString()
            rows += "day_header_scale" to RecentCallsAdapter.DAY_HEADER_FONT_SCALE.toString()
            rows += "sim_text_bold" to config.simTextBold.flag()
            rows += "time_format" to config.callTimeFormat.toString()
            rows += "duration_format" to config.callDurationFormat.toString()
            rows += "imperial_date" to config.useImperialDate.flag()
            rows += "format_numbers" to ctx.baseConfig.formatPhoneNumbers.flag()
            rows += "version" to VERSION.toString()
            return rows
        }
    }
}
