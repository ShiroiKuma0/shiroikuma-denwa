package org.fossify.phone.extensions

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.pillDrawable

private const val PILL_PADDING_HORIZONTAL_DP = 18
private const val PILL_PADDING_VERTICAL_DP = 9
private const val RING_STROKE_DP = 3f
private const val RIPPLE_ALPHA = 0.3f

/**
 * Turns a tappable word into the family's pill (black-yellow-theme skill: no bare-text buttons) — an
 * outlined pill as wide as its label, thick and bold when it is the [main] action of its block. The
 * text keeps the colour it has, or takes the accent.
 */
fun TextView.asPill(main: Boolean = false, color: Int = context.themeColor(ThemeSlot.PRIMARY)) {
    val density = resources.displayMetrics.density
    val h = (PILL_PADDING_HORIZONTAL_DP * density).toInt()
    val v = (PILL_PADDING_VERTICAL_DP * density).toInt()
    setTextColor(color)
    setTypeface(Typeface.create(typeface, if (main) Typeface.BOLD else Typeface.NORMAL))
    background = context.pillDrawable(color, context.themeColor(ThemeSlot.BACKGROUND), main)
    setPadding(h, v, h, v)
    layoutParams = layoutParams?.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
}

/** The one thing an empty list offers ("Request access", "Create new contact"): its main pill. */
fun TextView.asPlaceholderPill() = asPill(main = true)

/**
 * A round button as a ring, never a filled disc: the background showing through, a 3 dp frame and the
 * glyph in [color].
 */
fun ImageView.asRingButton(color: Int, strokeDp: Float = RING_STROKE_DP) {
    val density = resources.displayMetrics.density
    val ring = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(context.themeColor(ThemeSlot.BACKGROUND))
        setStroke((strokeDp * density).toInt().coerceAtLeast(1), color)
    }
    background = RippleDrawable(ColorStateList.valueOf(color.adjustAlpha(RIPPLE_ALPHA)), ring, null)
    setColorFilter(color)
}
