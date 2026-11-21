package org.fossify.phone.extensions

import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import org.fossify.commons.extensions.pillDrawable
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity

private const val ROW_PADDING_DP = 2
private const val PILL_ROW_PADDING_DP = 3
private const val PILL_PADDING_HORIZONTAL_DP = 18
private const val PILL_PADDING_VERTICAL_DP = 9
private const val HEADING_PADDING_TOP_DP = 12
private const val HEADING_PADDING_BOTTOM_DP = 2
private const val CHEVRON = "  ›"

/**
 * Brings the stock Fossify settings page (its layout is upstream's) into the family's look without
 * rewriting it, per the black-yellow-theme skill:
 *
 * - **every row only as tall as its content** — 2 dp above and below, no minimum height;
 * - **a row that opens something is a pill**: the rows in [navigationRows] get an outlined pill around
 *   their label, as wide as the label, with a › — [mainRow] (the link to our own UI page) thick and
 *   bold. The pill alone takes the tap; the row behind it no longer ripples across the screen;
 * - **headings bold and underlined**, as wide as their text;
 * - **nothing faded**: the stock value style dims by alpha, which reads as grey on black.
 *
 * Run at the end of the page's `onResume`, after its own set-up re-installs the row listeners. Safe to
 * run again: the pill keeps the label's own text in a tag.
 */
fun SimpleActivity.restyleSettingsPage(holder: ViewGroup, mainRow: ViewGroup, navigationRows: List<ViewGroup>) {
    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    holder.children.forEach { child ->
        when {
            child is TextView -> {
                child.setTextColor(themeColor(ThemeSlot.PAGE_HEADING))
                child.setTypeface(child.typeface, Typeface.BOLD)
                child.paintFlags = child.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                child.setPadding(child.paddingLeft, dp(HEADING_PADDING_TOP_DP), child.paddingRight, dp(HEADING_PADDING_BOTTOM_DP))
            }

            child is ViewGroup && child.id != View.NO_ID -> {
                val vertical = dp(if (child == mainRow || child in navigationRows) PILL_ROW_PADDING_DP else ROW_PADDING_DP)
                child.setPadding(child.paddingLeft, vertical, child.paddingRight, vertical)
                child.minimumHeight = 0
                child.children.filterIsInstance<TextView>().forEachIndexed { index, text ->
                    text.alpha = 1f
                    // a row's label, then (where it has one) its value
                    text.setTextColor(themeColor(if (index == 0) ThemeSlot.PAGE_LABEL else ThemeSlot.PAGE_VALUE))
                }
            }
        }
    }

    val frame = uiColor(UiRole.BUTTON_FRAME)
    val background = uiColor(UiRole.BUTTON_GROUND)
    (navigationRows + mainRow).forEach { row ->
        val label = row.children.filterIsInstance<TextView>().firstOrNull() ?: return@forEach
        val main = row == mainRow
        val own = (label.getTag(R.id.settings_pill_label_tag) as? CharSequence) ?: label.text.also {
            label.setTag(R.id.settings_pill_label_tag, it)
        }
        label.text = "$own$CHEVRON"
        label.setTypeface(Typeface.create(label.typeface, if (main) Typeface.BOLD else Typeface.NORMAL))
        label.setTextColor(uiColor(UiRole.BUTTON_TEXT))
        label.background = pillDrawable(frame, background, main)
        label.setPadding(dp(PILL_PADDING_HORIZONTAL_DP), dp(PILL_PADDING_VERTICAL_DP), dp(PILL_PADDING_HORIZONTAL_DP), dp(PILL_PADDING_VERTICAL_DP))
        label.layoutParams = label.layoutParams.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
        label.setOnClickListener { row.callOnClick() }
        row.isClickable = false
        row.background = null
    }
}
