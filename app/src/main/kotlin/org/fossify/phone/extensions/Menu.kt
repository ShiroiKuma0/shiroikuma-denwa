package org.fossify.phone.extensions

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu

/**
 * Paint every item title in [color]. Menu popups render their titles with the platform theme's text
 * color, which no runtime theming reaches — a span on the title is what carries our color into them.
 * Call this after the titles are final, right before the menu is shown.
 *
 * Any color span left by an earlier pass is dropped first, so repainting the same menu (a contextual
 * action bar is re-prepared on every selection change) neither stacks spans nor keeps the old color.
 */
fun Menu.colorItemTitles(color: Int) {
    for (index in 0 until size()) {
        val item = getItem(index)
        val title = item.title ?: continue
        item.title = SpannableString(title).apply {
            getSpans(0, length, ForegroundColorSpan::class.java).forEach { removeSpan(it) }
            setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
