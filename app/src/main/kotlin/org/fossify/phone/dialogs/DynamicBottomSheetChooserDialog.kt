package org.fossify.phone.dialogs

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.fossify.commons.adapters.SimpleListItemAdapter
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.commons.models.SimpleListItem
import org.fossify.phone.databinding.LayoutSimpleRecyclerViewBinding
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.themeColor

// The accent frame around the sheet, in dp — the dialog frame's counterpart (see syncDialogFrame).
private const val SHEET_FRAME_WIDTH_DP = 2

// same as BottomSheetChooserDialog but with dynamic updates
class DynamicBottomSheetChooserDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: LayoutSimpleRecyclerViewBinding

    var onItemClick: ((SimpleListItem) -> Unit)? = null

    // Frame the sheet in the accent, for the same reason dialogs are framed: it is the same black as
    // the call screen behind it, so its rounded top edge is the only thing that would show.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val strokePx = (SHEET_FRAME_WIDTH_DP * resources.displayMetrics.density).toInt()
        val radius = resources.getDimension(org.fossify.commons.R.dimen.bottom_sheet_corner_radius)
        val sheet = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setColor(context.themeColor(ThemeSlot.BACKGROUND))
            setStroke(strokePx, context.themeColor(ThemeSlot.PRIMARY))
        }

        // The sheet sits flush with the bottom of the screen, so push that edge of the frame off it.
        view.background = LayerDrawable(arrayOf(sheet)).apply { setLayerInset(0, 0, 0, 0, -strokePx) }
    }

    override fun setupContentView(parent: ViewGroup) {
        binding = LayoutSimpleRecyclerViewBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        @Suppress("UNCHECKED_CAST")
        val listItems = arguments?.getParcelableArray(ITEMS) as Array<SimpleListItem>
        getRecyclerViewAdapter().submitList(listItems.toList())
    }

    private fun getRecyclerViewAdapter(): SimpleListItemAdapter {
        var adapter = binding.recyclerView.adapter as? SimpleListItemAdapter
        if (adapter == null) {
            adapter = SimpleListItemAdapter(requireActivity()) {
                onItemClick?.invoke(it)
                dismissAllowingStateLoss()
            }
            binding.recyclerView.adapter = adapter
        }
        return adapter
    }

    fun updateChooserItems(newItems: Array<SimpleListItem>) {
        if (isAdded) {
            getRecyclerViewAdapter().submitList(newItems.toList())
        }
    }

    companion object {
        private const val TAG = "BottomSheetChooserDialog"
        private const val ITEMS = "data"

        fun createChooser(
            fragmentManager: FragmentManager,
            title: Int?,
            items: Array<SimpleListItem>,
            callback: (SimpleListItem) -> Unit
        ): DynamicBottomSheetChooserDialog {
            val extras = Bundle().apply {
                if (title != null) {
                    putInt(BOTTOM_SHEET_TITLE, title)
                }
                putParcelableArray(ITEMS, items)
            }
            return DynamicBottomSheetChooserDialog().apply {
                arguments = extras
                onItemClick = callback
                show(fragmentManager, TAG)
            }
        }
    }
}
