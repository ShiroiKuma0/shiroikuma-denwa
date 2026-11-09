package org.fossify.phone.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.phone.extensions.simTextColor

/**
 * Swipe-only [ItemTouchHelper.SimpleCallback] used to place a call with a specific SIM:
 * swiping a row left dials with SIM1, swiping right dials with SIM2. The row is never removed –
 * after a committed swipe it springs back via [RecyclerView.Adapter.notifyItemChanged].
 *
 * [canSwipe] decides per position whether the row reacts (it is also read by the touch listener
 * that lets the swipe win over the surrounding ViewPager) and [onSwipe] performs the call.
 */
@Suppress("TooManyFunctions") // ItemTouchHelper overrides plus the slide-out it drives itself
class SwipeToCallCallback(
    activity: BaseSimpleActivity,
    private val sim1Color: Int,
    private val sim2Color: Int,
    val canSwipe: (position: Int) -> Boolean,
    private val onSwipe: (position: Int, useSim1: Boolean) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    // Set by setupSwipeToCall: detaches + re-attaches the ItemTouchHelper, forcing it to clearView()
    // its pending recover animations (see onSwiped for why that can't be left to the normal cleanup).
    var resetItemTouchHelper: (() -> Unit)? = null

    private val background = ColorDrawable()

    // The row the finger is swiping, from onSelectedChanged; read when ItemTouchHelper commits it.
    private var swiping: RecyclerView.ViewHolder? = null

    // The finger's last offset and its speed (px per ms), sampled on every drag frame, so the slide-out
    // can leave at exactly the speed the finger had.
    private var lastDx = 0f
    private var lastDxTime = 0L
    private var fingerSpeed = 0f

    // The committed row's own slide-out: from where the finger let go to the end of the line.
    private var slideHolder: RecyclerView.ViewHolder? = null
    private var slideFrom = 0f
    private var slideTo = 0f
    private var slideStart = 0L
    private var slideDuration = 1L

    // The 1/2 glyph is the same marking as the number on a SIM badge, so it wears the SIM text colour
    private val simTextColor = activity.simTextColor()
    private val sim1Icon = activity.resources
        .getColoredDrawableWithColor(R.drawable.ic_phone_one_vector, simTextColor)
    private val sim2Icon = activity.resources
        .getColoredDrawableWithColor(R.drawable.ic_phone_two_vector, simTextColor)

    // After the slide-out the row stays filled with its SIM colour and reads "Dialing…" until the call
    // screen is in front — placing a call takes the system a second or two, and a row that snapped
    // back to normal in the meantime looked as if the swipe had done nothing.
    private val dialingText = activity.getString(org.fossify.phone.R.string.dialing)
    private val dialingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = simTextColor
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, DIALING_TEXT_SP, activity.resources.displayMetrics,
        )
    }
    private var dialingPosition = RecyclerView.NO_POSITION
    private var dialingSim1 = true
    private var dialingFrom = 0L
    private var dialingList: RecyclerView? = null
    private val clearDialingRunnable = Runnable { clearDialing() }

    /** Draws the filled "Dialing…" row over the list; added beside the ItemTouchHelper by setupSwipeToCall. */
    val dialingDecoration = object : RecyclerView.ItemDecoration() {
        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (dialingPosition == RecyclerView.NO_POSITION) return
            if (AnimationUtils.currentAnimationTimeMillis() < dialingFrom) return
            val itemView = parent.findViewHolderForAdapterPosition(dialingPosition)?.itemView ?: return
            val width = itemView.width.toFloat()
            drawReveal(c, itemView, if (dialingSim1) -width else width)
            val baseline = (itemView.top + itemView.bottom) / 2f - (dialingPaint.ascent() + dialingPaint.descent()) / 2f
            c.drawText(dialingText, (itemView.left + itemView.right) / 2f, baseline, dialingPaint)
        }
    }

    init {
        // Cleared once the list is no longer visible — the call screen is in front by then.
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) clearDialing() }
        )
    }

    private fun clearDialing() {
        if (dialingPosition == RecyclerView.NO_POSITION) return
        dialingPosition = RecyclerView.NO_POSITION
        dialingList?.removeCallbacks(clearDialingRunnable)
        dialingList?.invalidate()
    }

    private fun showDialing(recyclerView: RecyclerView, position: Int, useSim1: Boolean, after: Long) {
        dialingList?.removeCallbacks(clearDialingRunnable)
        dialingList = recyclerView
        dialingPosition = position
        dialingSim1 = useSim1
        dialingFrom = AnimationUtils.currentAnimationTimeMillis() + after
        // a call that never comes up (cancelled confirmation, refused permission) must not leave it
        recyclerView.postDelayed(clearDialingRunnable, after + DIALING_TIMEOUT_MS)
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return if (canSwipe(viewHolder.bindingAdapterPosition)) {
            super.getMovementFlags(recyclerView, viewHolder)
        } else {
            0
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ) = false

    // a drag past this share of the row, or a flick, commits; a smaller nudge springs back
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = SWIPE_THRESHOLD

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            swiping = viewHolder
            clearDialing()
        }
    }

    /**
     * ItemTouchHelper asks for the release animation's duration at the very moment the finger lifts,
     * and the type tells whether the swipe was committed — the earliest point anything can know.
     *
     * A committed row then slides out in one motion: it leaves at the finger's own speed and eases to
     * a stop at the end of the line (onChildDraw drives it — ItemTouchHelper's own animation starts
     * slowly, which read as a halt on lift). The call is placed the moment the slide ends — not during
     * it, where starting the call screen made it stutter, and not in onSwiped, which also waits for
     * every list animation to settle while the screen looks as if nothing had happened.
     */
    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float,
    ): Long {
        val holder = swiping
        if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_SUCCESS && holder != null) {
            swiping = null
            val duration = startSlide(holder, animateDx)
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && canSwipe(position)) {
                // the row flies toward the side it was swiped to: left (negative) is SIM 1
                val useSim1 = animateDx < 0
                showDialing(recyclerView, position, useSim1, duration)
                recyclerView.postDelayed({
                    recyclerView.invalidate()
                    onSwipe(position, useSim1)
                }, duration)
            }
            return duration
        }
        return super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy)
    }

    /**
     * Plans the slide-out from the release point to the end of the line. With an ease-out curve
     * (1 − (1 − t)²) the starting speed is twice the average, so a duration of 2 × distance ÷ finger
     * speed leaves at exactly the finger's speed; clamped so a slow drag still finishes promptly.
     */
    private fun startSlide(holder: RecyclerView.ViewHolder, animateDx: Float): Long {
        val distance = abs(animateDx)
        val speed = abs(fingerSpeed)
        val duration = if (speed > 0f) (2f * distance / speed).toLong() else SLIDE_MAX_MS
        slideHolder = holder
        slideFrom = holder.itemView.translationX
        slideTo = slideFrom + animateDx
        slideStart = AnimationUtils.currentAnimationTimeMillis()
        slideDuration = duration.coerceIn(SLIDE_MIN_MS, SLIDE_MAX_MS)
        return slideDuration
    }

    /** Where the committed row is at this frame of its slide-out. */
    private fun slideOffset(): Float {
        val elapsed = AnimationUtils.currentAnimationTimeMillis() - slideStart
        val t = (elapsed.toFloat() / slideDuration).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t)
        return slideFrom + (slideTo - slideFrom) * eased
    }

    private fun trackFinger(dX: Float) {
        val now = AnimationUtils.currentAnimationTimeMillis()
        val elapsed = now - lastDxTime
        if (lastDxTime != 0L && elapsed in 1..SPEED_SAMPLE_MAX_MS) {
            // a light smoothing over the last frames, so one uneven frame does not set the speed
            fingerSpeed = fingerSpeed * SPEED_SMOOTHING + (dX - lastDx) / elapsed * (1f - SPEED_SMOOTHING)
        } else {
            fingerSpeed = 0f
        }
        lastDx = dX
        lastDxTime = now
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }

        // At this point ItemTouchHelper has animated the row fully off-screen and keeps re-applying
        // translationX = ±width on EVERY draw frame (via its ItemDecoration's recover-animation list)
        // until a cleanup layout pass calls clearView() — a pass that never runs, because launching
        // the call screen pauses the list first. Setting translationX back is therefore not enough;
        // detach/re-attach the helper to force that cleanup now, or the reused holder renders as a
        // blank row after the post-call refresh.
        viewHolder.itemView.translationX = 0f
        slideHolder = null
        resetItemTouchHelper?.invoke()

        // we don't remove the row, so redraw it back into place; the call itself was already placed
        // when the finger lifted (getAnimationDuration)
        viewHolder.bindingAdapter?.notifyItemChanged(position)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        // Draw the reveal whenever the row is off its place: while the finger drags, while a committed
        // swipe slides the row out (the band runs to the end of the line), and while an uncommitted one
        // springs back (it shrinks away with the row). A committed slide ends in onSwiped, which resets
        // the helper and drops its decoration, so the band cannot be left behind on the row.
        var offset = dX
        if (isCurrentlyActive) {
            trackFinger(dX)
            if (slideHolder === viewHolder) slideHolder = null
        } else if (slideHolder === viewHolder) {
            offset = slideOffset()
        }
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && offset != 0f) {
            drawReveal(c, viewHolder.itemView, offset)
        }

        super.onChildDraw(c, recyclerView, viewHolder, offset, dY, actionState, isCurrentlyActive)
    }

    private fun drawReveal(c: Canvas, itemView: View, dX: Float) {
        // swiping left reveals the right edge (SIM1), swiping right reveals the left edge (SIM2)
        val swipingLeft = dX < 0
        val icon = if (swipingLeft) sim1Icon else sim2Icon
        background.color = if (swipingLeft) sim1Color else sim2Color

        if (swipingLeft) {
            background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
        } else {
            background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
        }
        background.draw(c)

        val iconSize = icon.intrinsicHeight
        val iconWidth = icon.intrinsicWidth
        val margin = (itemView.height - iconSize) / 2
        val iconTop = itemView.top + margin
        val iconBottom = iconTop + iconSize
        if (swipingLeft) {
            val right = itemView.right - margin
            icon.setBounds(right - iconWidth, iconTop, right, iconBottom)
        } else {
            val left = itemView.left + margin
            icon.setBounds(left, iconTop, left + iconWidth, iconBottom)
        }
        icon.draw(c)
    }

    private companion object {
        const val SWIPE_THRESHOLD = 0.25f
        // the slide-out's bounds: a fast flick still shows its sweep, a slow drag still ends promptly
        const val SLIDE_MIN_MS = 90L
        const val SLIDE_MAX_MS = 220L
        // finger-speed sampling: frames further apart than this restart the measurement
        const val SPEED_SAMPLE_MAX_MS = 100L
        const val SPEED_SMOOTHING = 0.5f
        const val DIALING_TEXT_SP = 18f
        const val DIALING_TIMEOUT_MS = 15_000L
    }
}
