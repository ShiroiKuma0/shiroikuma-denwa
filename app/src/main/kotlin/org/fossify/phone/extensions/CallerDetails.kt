package org.fossify.phone.extensions

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.phone.R
import org.fossify.phone.helpers.CallField
import org.fossify.phone.helpers.CallScreenConfig
import org.fossify.phone.helpers.valueFor
import org.fossify.phone.models.CallContact

// The caller block — the name, number and whatever else of the caller the Theme screen's order box has
// been told to show, on both call screens (CallActivity and the car one). Built in code rather than in
// the layout, because which fields appear, in what order, and which of them share a line is a setting.

// Between two fields merged onto one line.
private const val COLUMN_SEPARATOR = " · "

// A field wraps to a second line before it is ellipsised; a long note should not push the buttons away.
private const val FIELD_MAX_LINES = 2

private data class ShownField(val field: CallField, val value: String, val sameLine: Boolean)

/**
 * Fill this container with [contact]'s caller block, as configured. Every call replaces what was there,
 * so it is safe to call on each refresh of the screen.
 *
 * [unknownLabel] is what a name field falls back to when the call carries no caller ID at all.
 */
fun LinearLayout.showCallerDetails(contact: CallContact?, unknownLabel: String) {
    removeAllViews()
    if (contact == null) {
        return
    }

    val lines = ArrayList<MutableList<ShownField>>()
    shownFields(context, contact, unknownLabel).forEach { item ->
        if (item.sameLine && lines.isNotEmpty()) lines.last().add(item) else lines.add(mutableListOf(item))
    }

    val lineSpacing = resources.getDimensionPixelSize(R.dimen.normal_margin)
    lines.forEachIndexed { index, line ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        line.forEachIndexed { column, item ->
            val separator = if (column < line.lastIndex) COLUMN_SEPARATOR else ""
            row.addView(context.callFieldView(item.field, item.value + separator))
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        if (index > 0) {
            params.topMargin = lineSpacing
        }
        addView(row, params)
    }
}

/**
 * The ticked fields that have something to say about this caller, in their configured order.
 *
 * A field whose value is empty is skipped rather than drawn blank, and a value already shown by an
 * earlier field is skipped too — so a contact known only by a surname does not say it twice, and two
 * name shapes ticked at once do not both print "unknown caller".
 */
private fun shownFields(context: Context, contact: CallContact, unknownLabel: String): List<ShownField> {
    val preferNickname = context.config.callScreenPreferNickname
    val result = ArrayList<ShownField>()
    val alreadyShown = HashSet<String>()
    CallScreenConfig.parse(context.config.callScreenFields).filter { it.checked }.forEach { entry ->
        // A number with no contact behind it: the lookup leaves the name as the number itself (and
        // empty for a withheld caller ID), so a name field carries the number and the number's own
        // field is then dropped as a duplicate rather than printed twice.
        val value = if (entry.field.isName) {
            entry.field.valueFor(contact, preferNickname).ifEmpty { contact.number }.ifEmpty { unknownLabel }
        } else {
            entry.field.valueFor(contact, preferNickname)
        }

        if (value.isNotEmpty() && alreadyShown.add(value)) {
            result.add(ShownField(entry.field, value, entry.sameLine))
        }
    }
    return result
}

// One field's view, wearing that field's own colour, font, weight and size — the size falling back to
// the one the call screen has always used for a headline (a name) or for a detail line under it.
private fun Context.callFieldView(field: CallField, text: String): TextView = TextView(this).apply {
    this.text = text
    gravity = Gravity.CENTER_HORIZONTAL
    maxLines = FIELD_MAX_LINES
    ellipsize = TextUtils.TruncateAt.END
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
    setTextColor(themeColor(field.slot))
    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(field.defaultSizeRes))
    applyThemeFont(field.slot)
}
