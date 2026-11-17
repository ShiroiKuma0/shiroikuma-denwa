package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.material.tabs.TabLayout
import kotlin.math.roundToInt

/**
 * The bottom bar drawn as card-folder tabs — the look 臨電話 gave it first (its SkFolderTabsView /
 * SkFolderTabPainter, whose measures these are), so the bar the three apps share is one object.
 *
 * One bold, opaque line runs the full width of the bar's top edge; at the tab in the foreground it
 * leaves that baseline, curls outward, runs down both sides and around the two far corners — the tab
 * you are looking at, open towards the page above because it *is* that page. Every other tab is the
 * same box drawn thinner and fainter behind the line. A selection change is interpolated, so a tab
 * switch reads as one continuous movement.
 *
 * It sits behind a [TabLayout] with a transparent background and takes its geometry from that
 * layout's tab views at draw time, so it follows whatever tabs are shown.
 */
class FolderTabsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val flare = FLARE_DP * density
    private val shoulder = SHOULDER_DP * density
    private val corner = CORNER_DP * density
    private val sideInset = TAB_SIDE_INSET_DP * density
    private val barInset = BAR_INSET_DP * density
    private val path = Path()

    private val frontPaint = strokePaint(FRONT_STROKE_DP)
    private val backPaint = strokePaint(BACK_STROKE_DP)

    private var tabs: TabLayout? = null
    private var weights = FloatArray(0)
    private var weightsFrom = FloatArray(0)
    private var animator: ValueAnimator? = null

    private val layoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }

    private val selectionListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) = moveTo(tab.position)

        override fun onTabUnselected(tab: TabLayout.Tab) = Unit

        override fun onTabReselected(tab: TabLayout.Tab) = Unit
    }

    private fun strokePaint(widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthDp * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Draw behind [tabLayout], following its tabs and its selection. Call again after rebuilding the tabs. */
    fun attach(tabLayout: TabLayout) {
        tabs?.removeOnLayoutChangeListener(layoutListener)
        tabs?.removeOnTabSelectedListener(selectionListener)
        animator?.cancel()
        tabs = tabLayout
        weights = FloatArray(tabLayout.tabCount) { if (it == tabLayout.selectedTabPosition) 1f else 0f }
        weightsFrom = FloatArray(weights.size)
        tabLayout.addOnLayoutChangeListener(layoutListener)
        tabLayout.addOnTabSelectedListener(selectionListener)
        invalidate()
    }

    /** The line and the tab in front take [color]; the tabs behind take it at 30 %. */
    fun setColors(color: Int, background: Int) {
        frontPaint.color = color
        backPaint.color = color
        setBackgroundColor(background)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun moveTo(position: Int) {
        if (position !in weights.indices) return
        weights.copyInto(weightsFrom)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                for (index in weights.indices) {
                    val target = if (index == position) 1f else 0f
                    weights[index] = weightsFrom[index] + (target - weightsFrom[index]) * fraction
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val tabLayout = tabs ?: return
        val strip = tabLayout.getChildAt(0) as? ViewGroup ?: return
        val baseline = frontPaint.strokeWidth / 2f
        // the bar is padded at the bottom for the system navigation; the tabs end above that
        val depth = height - tabLayout.paddingBottom - baseline - barInset
        if (width == 0 || depth <= 0f || strip.childCount != weights.size) return

        val offset = (tabLayout.left - left + strip.left - tabLayout.scrollX).toFloat()
        val spans = (0 until strip.childCount).map { index ->
            val tab = strip.getChildAt(index)
            Triple(offset + tab.left + sideInset, offset + tab.right - sideInset, weights[index])
        }

        // the tabs filed away, behind the line
        for ((start, end, weight) in spans) {
            val fade = 1f - weight
            if (fade <= VISIBLE_WEIGHT || end <= start) continue
            backPaint.alpha = (FULL_ALPHA * BACK_ALPHA * fade).roundToInt()
            path.reset()
            path.moveTo(start - shoulder, baseline)
            outline(start, end, baseline, depth)
            canvas.drawPath(path, backPaint)
        }

        // the page's edge, diving around whichever tab is in front
        path.reset()
        path.moveTo(0f, baseline)
        for ((start, end, weight) in spans) {
            if (weight <= VISIBLE_WEIGHT || end <= start) continue
            path.lineTo(start - shoulder, baseline)
            outline(start, end, baseline, depth * weight)
        }
        path.lineTo(width.toFloat(), baseline)
        canvas.drawPath(path, frontPaint)
    }

    // Leaves the baseline with an outward curl at [start], runs down that side, around the two far
    // corners and back up the [end] side, then curls back onto the baseline — entered at
    // (start - shoulder, baseline) and left at (end + shoulder, baseline), so tabs chain onto one line.
    private fun outline(start: Float, end: Float, baseline: Float, depth: Float) {
        val curl = minOf(flare, depth / 2f)
        val far = baseline + depth
        val radius = minOf(corner, (end - start) / 2f, depth - curl).coerceAtLeast(0f)
        val nearSide = baseline + curl
        val farSide = far - radius

        path.cubicTo(start - shoulder / 2f, baseline, start, baseline, start, nearSide)
        path.lineTo(start, farSide)
        path.quadTo(start, far, start + radius, far)
        path.lineTo(end - radius, far)
        path.quadTo(end, far, end, farSide)
        path.lineTo(end, nearSide)
        path.cubicTo(end, baseline, end + shoulder / 2f, baseline, end + shoulder, baseline)
    }

    private companion object {
        const val FLARE_DP = 6f
        const val SHOULDER_DP = 10f
        const val CORNER_DP = 12f
        const val FRONT_STROKE_DP = 3f
        const val BACK_STROKE_DP = 1.5f
        const val BACK_ALPHA = 0.3f
        const val FULL_ALPHA = 255
        const val VISIBLE_WEIGHT = 0.01f
        const val TAB_SIDE_INSET_DP = 4f
        const val BAR_INSET_DP = 3f
        const val ANIMATION_DURATION_MS = 260L
    }
}
