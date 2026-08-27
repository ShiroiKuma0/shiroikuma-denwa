package org.fossify.phone.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * The dialpad panel, which can be pulled down out of the way — leaving only the dial line — and
 * pulled back up. The keys swallow their own touches, so the drag has to be taken from them here,
 * before they ever see it; a tap never travels far enough to qualify, so pressing a key still works.
 *
 * Both callbacks stay null on the Dialpad screen, where the panel is the screen and never collapses.
 */
class DialpadDragLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onPullDown: (() -> Unit)? = null
    var onPullUp: (() -> Unit)? = null

    private val dragThreshold = ViewConfiguration.get(context).scaledTouchSlop * DRAG_SLOP_FACTOR
    private var downX = 0f
    private var downY = 0f
    private var handled = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                handled = false
            }

            MotionEvent.ACTION_MOVE -> return takeOverDrag(ev)
        }

        return false
    }

    // Only a clearly vertical drag counts, so a finger sliding across the keys never collapses the
    // panel by accident, and neither does a drag the panel has no answer for in that direction.
    private fun takeOverDrag(ev: MotionEvent): Boolean {
        if (handled) {
            return false
        }

        val dragY = ev.y - downY
        if (abs(dragY) <= dragThreshold || abs(dragY) <= abs(ev.x - downX)) {
            return false
        }

        val callback = (if (dragY > 0) onPullDown else onPullUp) ?: return false
        handled = true
        callback()
        return true
    }

    // Reached only once a drag has been intercepted; swallow the rest of that gesture.
    override fun onTouchEvent(event: MotionEvent) = true

    companion object {
        private const val DRAG_SLOP_FACTOR = 2
    }
}
