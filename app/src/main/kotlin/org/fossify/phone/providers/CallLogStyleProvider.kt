package org.fossify.phone.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.io.File

/**
 * Read-only view of the call log's look, for renrakusaki's per-contact call history, so it draws its
 * rows exactly like our Recents without copying any of our settings.
 *
 * - `content://<applicationId>.calllogstyle/style` — a `key` / `value` cursor (both strings): colours
 *   as decimal ARGB ints already resolved through their defaults, thicknesses in dp, per-slot fonts,
 *   the base text size in px and the format choices. [VERSION] goes up whenever a key changes meaning.
 * - `content://<applicationId>.calllogstyle/font/<file name>` — an imported font file, read-only.
 *
 * Only renrakusaki (release and debug) gets an answer; anyone else gets null. The contract is
 * documented in CLAUDE.md beside the tab hand-off.
 */
class CallLogStyleProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? {
        val ctx = context ?: return null
        if (!isAllowedCaller() || u.pathSegments.singleOrNull() != PATH_STYLE) return null
        val cursor = MatrixCursor(arrayOf(COLUMN_KEY, COLUMN_VALUE))
        styleRows(ctx).forEach { (key, value) -> cursor.addRow(arrayOf(key, value)) }
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
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
        const val VERSION = 1

        private val ALLOWED_CALLERS = setOf("shiroikuma.renrakusaki", "shiroikuma.renrakusaki.debug")

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
