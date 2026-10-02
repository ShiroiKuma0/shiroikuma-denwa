package org.fossify.phone.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.ItemCallScreenFieldBinding
import org.fossify.phone.helpers.CallFieldEntry
import java.util.Collections

// The "order box": tick which of the caller's fields the call screen shows, drag to reorder, and use the
// chevrons to merge a field onto the previous shown line (▶, a column to its right) or split it back to
// its own line (◀). Every change persists via [onChanged]; the editor lives embedded in ThemeActivity's
// "In-call screen" section. The twin of renrakusaki's contacts-list field editor.
class CallScreenFieldsAdapter(
    private val activity: SimpleActivity,
    private val entries: MutableList<CallFieldEntry>,
    private val onChanged: () -> Unit,
) : RecyclerView.Adapter<CallScreenFieldsAdapter.ViewHolder>() {

    private val textColor = activity.getProperTextColor()
    private val accentColor = activity.getProperPrimaryColor()
    private val backgroundColor = activity.getProperBackgroundColor()
    private val touchHelper = ItemTouchHelper(DragCallback())

    private companion object {
        const val DISABLED_ALPHA = 0.3f
    }

    fun attachTo(recyclerView: RecyclerView) {
        touchHelper.attachToRecyclerView(recyclerView)
    }

    inner class ViewHolder(val binding: ItemCallScreenFieldBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallScreenFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = entries.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val hasShownBefore = entries.take(position).any { it.checked }
        val canMoveRight = entry.checked && hasShownBefore && !entry.sameLine
        val canMoveLeft = entry.checked && entry.sameLine

        holder.binding.apply {
            fieldCheckbox.text = activity.getString(entry.field.labelRes)
            fieldCheckbox.setColors(textColor, accentColor, backgroundColor)
            fieldCheckbox.setOnCheckedChangeListener(null)
            fieldCheckbox.isChecked = entry.checked
            fieldCheckbox.setOnCheckedChangeListener { _, isChecked ->
                entry.checked = isChecked
                if (!isChecked) {
                    entry.sameLine = false
                }
                persist()
            }

            setupButton(fieldMoveLeft, canMoveLeft) {
                entry.sameLine = false
                persist()
            }
            setupButton(fieldMoveRight, canMoveRight) {
                entry.sameLine = true
                persist()
            }

            fieldDragHandle.applyColorFilter(textColor)
            fieldDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                }
                false
            }
        }
    }

    private fun setupButton(button: ImageView, enabled: Boolean, action: () -> Unit) {
        button.applyColorFilter(textColor)
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
        button.isEnabled = enabled
        button.setOnClickListener { if (enabled) action() }
    }

    // The first shown field always starts a line; keep the stored config consistent with that.
    private fun normalizeFirstShown() {
        entries.firstOrNull { it.checked }?.sameLine = false
    }

    // The button enabled-states depend on the whole list (which field is shown first, what precedes each),
    // so a single edit can change several rows — rebind them all.
    @SuppressLint("NotifyDataSetChanged")
    private fun persist() {
        normalizeFirstShown()
        onChanged()
        notifyDataSetChanged()
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled() = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) {
                return false
            }
            Collections.swap(entries, from, to)
            notifyItemMoved(from, to)
            return true
        }

        @Suppress("EmptyFunctionBlock") // swipe-to-dismiss is intentionally disabled (drag-only).
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            persist()
        }
    }
}
