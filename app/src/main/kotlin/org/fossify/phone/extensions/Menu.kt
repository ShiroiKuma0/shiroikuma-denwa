package org.fossify.phone.extensions

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu

/**
 * Paint every item title in [color]. Menu popups render their titles with the platform theme's text
 * color, which no runtime theming reaches — a span on the title is what carries our color into them.
 * Call this after the titles are final, right before the menu is shown.
 */
fun Menu.colorItemTitles(color: Int) {
    for (index in 0 until size()) {
        val item = getItem(index)
        val title = item.title ?: continue
        item.title = SpannableString(title).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
