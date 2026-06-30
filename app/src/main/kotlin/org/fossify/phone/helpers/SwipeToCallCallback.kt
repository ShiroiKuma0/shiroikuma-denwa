package org.fossify.phone.helpers

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor

/**
 * Swipe-only [ItemTouchHelper.SimpleCallback] used to place a call with a specific SIM:
 * swiping a row left dials with SIM1, swiping right dials with SIM2. The row is never removed –
 * after a committed swipe it springs back via [RecyclerView.Adapter.notifyItemChanged].
 *
 * [canSwipe] decides per position whether the row reacts (it is also read by the touch listener
 * that lets the swipe win over the surrounding ViewPager) and [onSwipe] performs the call.
 */
class SwipeToCallCallback(
    activity: BaseSimpleActivity,
    private val sim1Color: Int,
    private val sim2Color: Int,
    val canSwipe: (position: Int) -> Boolean,
    private val onSwipe: (position: Int, useSim1: Boolean) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val background = ColorDrawable()
    private val sim1Icon = activity.resources
        .getColoredDrawableWithColor(R.drawable.ic_phone_one_vector, sim1Color.getContrastColor())
    private val sim2Icon = activity.resources
        .getColoredDrawableWithColor(R.drawable.ic_phone_two_vector, sim2Color.getContrastColor())

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

    // require a clear, deliberate drag so accidental nudges spring back instead of dialing
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.4f

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }

        // we don't remove the row, so redraw it back into place
        viewHolder.bindingAdapter?.notifyItemChanged(position)

        if (canSwipe(position)) {
            val useSim1 = direction == ItemTouchHelper.LEFT
            // Defer the call until ItemTouchHelper has settled this swipe. Launching the call screen
            // synchronously from onSwiped() interrupts the spring-back (recover) animation, leaving the
            // row frozen mid-swipe with the colored SIM background stuck on screen until the app is killed.
            viewHolder.itemView.post { onSwipe(position, useSim1) }
        }
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
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            val itemView = viewHolder.itemView
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

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
