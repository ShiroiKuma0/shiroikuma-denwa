package org.fossify.phone.extensions

import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.helpers.SwipeToCallCallback

fun MyRecyclerView.setupSwipeToCall(callback: SwipeToCallCallback) {
    val itemTouchHelper = ItemTouchHelper(callback)
    itemTouchHelper.attachToRecyclerView(this)

    // Detaching forces ItemTouchHelper to clearView() every pending recover animation, dropping the
    // per-frame off-screen translation it would otherwise keep re-applying to a swiped row; the
    // callback triggers this right after a committed swipe, before the call screen pauses the list.
    callback.resetItemTouchHelper = {
        itemTouchHelper.attachToRecyclerView(null)
        itemTouchHelper.attachToRecyclerView(this)
    }

    // The list lives inside a horizontally-paging ViewPager. On touch-down over a swipeable row
    // we tell the parent not to intercept, so the row swipe wins instead of switching tabs.
    addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                val child = rv.findChildViewUnder(e.x, e.y)
                val position = child?.let { rv.getChildAdapterPosition(it) } ?: RecyclerView.NO_POSITION
                if (position != RecyclerView.NO_POSITION && callback.canSwipe(position)) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            return false
        }
    })
}

fun RecyclerView.runAfterAnimations(callback: () -> Unit) {
    if (isComputingLayout) {
        post { runAfterAnimations(callback) }
        return
    }

    val animator = itemAnimator
    if (animator == null) {
        post(callback)
    } else {
        animator.isRunning {
            post(callback)
        }
    }
}
