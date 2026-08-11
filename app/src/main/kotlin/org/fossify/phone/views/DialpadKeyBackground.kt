package org.fossify.phone.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.phone.R
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.themeColor
import kotlin.math.min

/**
 * A dialpad key painted as a circle rather than the stock pill, so the pad reads like a telephone
 * keypad. The cells are far wider than they are tall, so the circle is inscribed against the cell
 * height and centred — the whole cell stays tappable, only the paint is round.
 */
class DialpadKeyBackground(
    fillColor: Int,
    strokeColor: Int,
    private val strokeWidthPx: Float,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = strokeWidthPx
    }

    override fun draw(canvas: Canvas) {
        // Keep the whole ring inside the cell: a stroke straddles the path, so back off by half of it.
        val radius = min(bounds.width(), bounds.height()) / 2f - strokeWidthPx / 2f
        if (radius <= 0f) {
            return
        }

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        if (strokeWidthPx > 0f) {
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * One circular key background, ringed in the theme's primary color and filled faintly with it, with
 * the touch ripple clipped to the same circle. Each key needs its own instance — the drawable sizes
 * itself from its own bounds.
 */
fun Context.dialpadKeyBackground(): Drawable {
    val accentColor = themeColor(ThemeSlot.PRIMARY)
    val strokeWidthPx = resources.getDimension(R.dimen.dialpad_key_border_width)
    val content = DialpadKeyBackground(
        fillColor = ColorUtils.setAlphaComponent(accentColor, LOWER_ALPHA_INT),
        strokeColor = accentColor,
        strokeWidthPx = strokeWidthPx,
    )

    val mask = DialpadKeyBackground(fillColor = Color.WHITE, strokeColor = Color.WHITE, strokeWidthPx = 0f)
    val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accentColor, RIPPLE_ALPHA_INT))
    return RippleDrawable(rippleColor, content, mask)
}

fun Context.applyDialpadKeyBackgrounds(keys: Array<View>) {
    keys.forEach { it.background = dialpadKeyBackground() }
}

private const val RIPPLE_ALPHA_INT = 80
