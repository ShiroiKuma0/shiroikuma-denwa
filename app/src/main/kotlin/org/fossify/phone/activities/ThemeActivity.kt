package org.fossify.phone.activities

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.phone.databinding.ActivityThemeBinding
import org.fossify.phone.databinding.ItemThemeColorBinding
import org.fossify.phone.databinding.ItemThemeDimenBinding
import org.fossify.phone.databinding.ItemThemeSectionBinding
import org.fossify.phone.R
import org.fossify.phone.extensions.ThemeGroup
import org.fossify.phone.extensions.ThemeDimen
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.resetThemeColor
import org.fossify.phone.extensions.setThemeColor
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.extensions.themeDimenDp
import org.fossify.phone.extensions.setThemeDimenDp

class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.themeNestedScrollview))
        setupMaterialScrollListener(binding.themeNestedScrollview, binding.themeAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.themeAppbar, NavigationIcon.Arrow)
        buildRows()
    }

    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()

        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()

        ThemeGroup.entries.forEach { group ->
            val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
            section.themeSectionLabel.text = getString(group.labelRes)
            section.themeSectionLabel.setTextColor(primaryColor)
            binding.themeHolder.addView(section.root)

            ThemeSlot.entries.filter { it.group == group }.forEach { slot ->
                val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
                row.themeColorLabel.text = getString(slot.labelRes)
                row.themeColorLabel.setTextColor(textColor)
                row.themeColorPreview.background.setTint(themeColor(slot))
                row.root.setOnClickListener { openPicker(slot) }
                previews[slot] = row.themeColorPreview
                binding.themeHolder.addView(row.root)
            }

            ThemeDimen.entries.filter { it.group == group }.forEach { dimen ->
                val dimenRow = ItemThemeDimenBinding.inflate(layoutInflater, binding.themeHolder, false)
                dimenRow.themeDimenLabel.text = getString(dimen.labelRes)
                dimenRow.themeDimenLabel.setTextColor(textColor)
                dimenRow.themeDimenValue.setTextColor(textColor)
                dimenRow.themeDimenValue.text = dpLabel(themeDimenDp(dimen))
                dimenRow.root.setOnClickListener { openDimenPicker(dimen, dimenRow.themeDimenValue) }
                binding.themeHolder.addView(dimenRow.root)
            }
        }
    }

    private fun dpLabel(dp: Int) =
        if (dp <= 0) getString(R.string.theme_dp_none) else getString(R.string.theme_dp_value, dp)

    private fun openDimenPicker(dimen: ThemeDimen, valueView: TextView) {
        val options = intArrayOf(0, 1, 2, 3, 4, 5, 6, 8, 10, 12)
        val items = ArrayList<RadioItem>()
        options.forEach { items.add(RadioItem(it, dpLabel(it))) }
        RadioGroupDialog(this, items, themeDimenDp(dimen)) {
            setThemeDimenDp(dimen, it as Int)
            valueView.text = dpLabel(themeDimenDp(dimen))
        }
    }

    private fun openPicker(slot: ThemeSlot) {
        ColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) {
                setThemeColor(slot, color)
            } else {
                resetThemeColor(slot)
            }

            if (slot.isFoundation) {
                // foundation cascades into the chrome + every inheriting preview
                recreate()
            } else {
                previews[slot]?.background?.setTint(themeColor(slot))
            }
        }
    }
}
