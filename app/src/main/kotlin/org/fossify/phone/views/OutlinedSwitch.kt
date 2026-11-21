package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.materialswitch.MaterialSwitch
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import kotlin.math.roundToInt

/**
 * The theme's switch: an outlined pill with the background showing through and a solid dot inside it —
 * right when on, left when off, outline and dot each in its own role (the commons' four switch roles). Nothing is
 * ever filled, not even the on state, and the dot travels between the two ends.
 *
 * It reads the checked state from the view it is drawn for, so it serves as a SwitchCompat's track and
 * as a CheckBox's button alike. Commons and Material re-tint switches whenever a screen refreshes its
 * colours; this drawable ignores every tint, so the two colours it was given are the only ones it shows.
 */
class OutlinedSwitchDrawable(
    private val on: Int,
    private val off: Int,
    private val onDot: Int,
    private val offDot: Int,
    private val width: Int,
    private val height: Int,
    strokeWidth: Float,
) : Drawable() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var checked: Boolean? = null
    private var progress = 0f // 0 = off (dot left), 1 = on (dot right)
    private var animator: ValueAnimator? = null

    override fun draw(canvas: Canvas) {
        val color = ColorUtils.blendARGB(off, on, progress)
        val inset = stroke.strokeWidth / 2f
        val h = minOf(bounds.height().toFloat(), height.toFloat())
        val top = bounds.exactCenterY() - h / 2f
        rect.set(bounds.left + inset, top + inset, bounds.right - inset, top + h - inset)
        stroke.color = color
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, stroke)

        fill.color = ColorUtils.blendARGB(offDot, onDot, progress)
        val half = h / 2f
        val startX = bounds.left + half
        val endX = bounds.right - half
        canvas.drawCircle(startX + (endX - startX) * progress, bounds.exactCenterY(), h * DOT_RADIUS, fill)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val nowChecked = android.R.attr.state_checked in state
        val was = checked
        if (was == nowChecked) return false
        checked = nowChecked
        val target = if (nowChecked) 1f else 0f
        animator?.cancel()
        // The first state a fresh drawable sees is where it starts, not a change to animate
        if (was == null || callback == null) {
            progress = target
        } else {
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = TRAVEL_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
        }
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth() = width

    override fun getIntrinsicHeight() = height

    // Drawn in the two colours it was given, nothing else
    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun setTintList(tint: ColorStateList?) = Unit

    override fun setTintMode(tintMode: PorterDuff.Mode?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        const val WIDTH_DP = 52
        const val HEIGHT_DP = 28
        const val SMALL_WIDTH_DP = 44 // in a compact menu row
        const val SMALL_HEIGHT_DP = 24
        private const val STROKE_DP = 2f
        private const val DOT_RADIUS = 0.23f // a dot 46 % of the track height
        private const val TRAVEL_MS = 180L

        fun create(context: Context, small: Boolean = false): OutlinedSwitchDrawable {
            val density = context.resources.displayMetrics.density
            val w = if (small) SMALL_WIDTH_DP else WIDTH_DP
            val h = if (small) SMALL_HEIGHT_DP else HEIGHT_DP
            return OutlinedSwitchDrawable(
                on = context.uiColor(UiRole.SWITCH_ON_OUTLINE),
                off = context.uiColor(UiRole.SWITCH_OFF_OUTLINE),
                onDot = context.uiColor(UiRole.SWITCH_ON_DOT),
                offDot = context.uiColor(UiRole.SWITCH_OFF_DOT),
                width = (w * density).roundToInt(),
                height = (h * density).roundToInt(),
                strokeWidth = STROKE_DP * density,
            )
        }
    }
}

/**
 * Gives this switch the theme's outlined look — or a check box that is a lone on/off setting. A check
 * box that marks which entries of a list are chosen is a selection mark and never comes here; nor does
 * a radio button. A SwitchCompat (MaterialSwitch included)
 * keeps its own thumb logic but draws nothing for it: a transparent thumb half the track wide sets the
 * width, and the track drawable draws the frame and the travelling dot. Safe to call repeatedly.
 */
fun CompoundButton.applyOutlinedSwitch(small: Boolean = false) {
    if (getTag(TAG_KEY) == true) return
    val drawable = OutlinedSwitchDrawable.create(context, small)
    when (this) {
        is SwitchCompat -> {
            if (this is MaterialSwitch) {
                thumbIconDrawable = null
                trackDecorationDrawable = null
            }
            thumbTintList = null
            trackTintList = null
            thumbDrawable = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setSize(drawable.intrinsicWidth / 2, drawable.intrinsicHeight)
            }
            trackDrawable = drawable
            switchMinWidth = drawable.intrinsicWidth
            minHeight = 0
            minimumHeight = 0
        }

        is CheckBox -> {
            buttonTintList = null
            buttonDrawable = drawable
        }

        else -> return
    }
    setTag(TAG_KEY, true)
    refreshDrawableState()
}

/** Every switch under this view, outlined. */
fun View.outlineAllSwitches() {
    if (this is SwitchCompat) {
        applyOutlinedSwitch()
    } else if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).outlineAllSwitches()
        }
    }
}

private val TAG_KEY = org.fossify.phone.R.id.outlined_switch_tag
