package org.fossify.phone.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityThemeBinding
import org.fossify.phone.databinding.ItemThemeColorBinding
import org.fossify.phone.databinding.ItemThemeDimenBinding
import org.fossify.phone.databinding.ItemThemeSectionBinding
import org.fossify.phone.databinding.ItemThemeSubgroupBinding
import org.fossify.phone.databinding.ItemThemeSwitchBinding
import org.fossify.phone.databinding.ItemThemeValueBinding
import org.fossify.phone.extensions.CallDurationFormat
import org.fossify.phone.extensions.CallTimeFormat
import org.fossify.phone.extensions.ThemeDimen
import org.fossify.phone.extensions.ThemeGroup
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.callDurationFormatOf
import org.fossify.phone.extensions.callTimeFormatOf
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.extensions.resetThemeColor
import org.fossify.phone.extensions.setThemeColor
import org.fossify.phone.extensions.setThemeDimenDp
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.extensions.themeDimenDp

// Every customizable setting for 白い熊 電話, laid out as a section > subgroup > controls cascade.
// Each cascade level is indented one more step: a section's contents one step in, a subgroup's two.
private const val INDENT_STEP_DP = 72

@Suppress("TooManyFunctions")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()
    private var stepPx = 0

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

        val primaryColor = getProperPrimaryColor()
        stepPx = (INDENT_STEP_DP * resources.displayMetrics.density).toInt()

        // Foundation
        addSection(R.string.theme_group_foundation, primaryColor)
        colorRowsFor(ThemeGroup.FOUNDATION, stepPx)

        // Search bar
        addSection(R.string.theme_group_search, primaryColor)
        colorRowsFor(ThemeGroup.SEARCH, stepPx)

        // Top bar & overflow ("hamburger") menu
        addSection(R.string.theme_group_chrome, primaryColor)
        addSubgroup(R.string.theme_subgroup_chrome_menu, primaryColor)
        addColorRow(ThemeSlot.MENU_ICON, stepPx * 2)
        addColorRow(ThemeSlot.MENU_TEXT, stepPx * 2)
        addSubgroup(R.string.theme_subgroup_chrome_header, primaryColor)
        addColorRow(ThemeSlot.HEADER_TITLE, stepPx * 2)
        addColorRow(ThemeSlot.HEADER_ARROW, stepPx * 2)

        // Tabs
        addSection(R.string.theme_group_tabs, primaryColor)
        colorRowsFor(ThemeGroup.TABS, stepPx)

        // Call log — rich enough to warrant subgroups; each line's colour sits next to its thickness
        addSection(R.string.theme_group_call_log, primaryColor)
        addSubgroup(R.string.theme_subgroup_call_log_text, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_NAME, stepPx * 2)
        addColorRow(ThemeSlot.CALL_LOG_SUBTITLE, stepPx * 2)
        addColorRow(ThemeSlot.CALL_LOG_DATE, stepPx * 2)
        addSubgroup(R.string.theme_subgroup_call_log_types, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_MISSED, stepPx * 2)
        addColorRow(ThemeSlot.CALL_LOG_INCOMING, stepPx * 2)
        addColorRow(ThemeSlot.CALL_LOG_OUTGOING, stepPx * 2)
        addSubgroup(R.string.theme_subgroup_call_log_dividers, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_DIVIDER, stepPx * 2)
        addDimenRow(ThemeDimen.CALL_LOG_DIVIDER_THICKNESS, stepPx * 2)
        addColorRow(ThemeSlot.CALL_LOG_DAY_DIVIDER, stepPx * 2)
        addDimenRow(ThemeDimen.CALL_LOG_DAY_DIVIDER_THICKNESS, stepPx * 2)
        addColorRow(ThemeSlot.CALL_LOG_DATE_UNDERLINE, stepPx * 2)
        addDimenRow(ThemeDimen.CALL_LOG_DATE_UNDERLINE_THICKNESS, stepPx * 2)
        addSubgroup(R.string.theme_subgroup_call_log_date_format, primaryColor)
        addSwitchRow(R.string.use_imperial_date, config.useImperialDate, stepPx * 2) { config.useImperialDate = it }
        addValueRow(R.string.call_time_format, getString(callTimeFormatOf(config.callTimeFormat).labelRes), stepPx * 2) { valueView ->
            val items = ArrayList(CallTimeFormat.entries.map { RadioItem(it.ordinal, getString(it.labelRes)) })
            RadioGroupDialog(this, items, config.callTimeFormat) {
                config.callTimeFormat = it as Int
                valueView.text = getString(callTimeFormatOf(config.callTimeFormat).labelRes)
            }
        }
        addValueRow(R.string.call_duration_format, getString(callDurationFormatOf(config.callDurationFormat).labelRes), stepPx * 2) { valueView ->
            val items = ArrayList(CallDurationFormat.entries.map { RadioItem(it.ordinal, getString(it.labelRes)) })
            RadioGroupDialog(this, items, config.callDurationFormat) {
                config.callDurationFormat = it as Int
                valueView.text = getString(callDurationFormatOf(config.callDurationFormat).labelRes)
            }
        }

        // Dialpad
        addSection(R.string.theme_group_dialpad, primaryColor)
        colorRowsFor(ThemeGroup.DIALPAD, stepPx)

        // In-call screen
        addSection(R.string.theme_group_in_call, primaryColor)
        colorRowsFor(ThemeGroup.IN_CALL, stepPx)

        // Contacts
        addSection(R.string.theme_group_contacts, primaryColor)
        colorRowsFor(ThemeGroup.CONTACTS, stepPx)

        // Favorites
        addSection(R.string.theme_group_favorites, primaryColor)
        colorRowsFor(ThemeGroup.FAVORITES, stepPx)

        // SIM cards — colours + swipe-to-dial only make sense with more than one SIM
        if (areMultipleSIMsAvailable()) {
            addSection(R.string.theme_group_sim, primaryColor)
            addSimColorRow(simId = 1, indent = stepPx)
            addSimColorRow(simId = 2, indent = stepPx)
            addSwitchRow(R.string.swipe_to_call, config.swipeToCall, stepPx) { config.swipeToCall = it }
        }
    }

    private fun colorRowsFor(group: ThemeGroup, indent: Int) {
        ThemeSlot.entries.filter { it.group == group }.forEach { addColorRow(it, indent) }
    }

    private fun addSection(@StringRes labelRes: Int, primaryColor: Int) {
        val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        section.themeSectionLabel.text = getString(labelRes)
        section.themeSectionLabel.setTextColor(primaryColor)
        section.themeSectionRule.setBackgroundColor(primaryColor)
        binding.themeHolder.addView(section.root)
    }

    private fun addSubgroup(@StringRes labelRes: Int, primaryColor: Int) {
        val sub = ItemThemeSubgroupBinding.inflate(layoutInflater, binding.themeHolder, false)
        sub.themeSubgroupLabel.text = getString(labelRes)
        sub.themeSubgroupLabel.setTextColor(primaryColor)
        sub.themeSubgroupRule.setBackgroundColor(primaryColor)
        // a subgroup header is part of the section's contents → one step in
        indentRow(sub.root, stepPx)
        binding.themeHolder.addView(sub.root)
    }

    private fun addColorRow(slot: ThemeSlot, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorPreview.background.setTint(themeColor(slot))
        row.root.setOnClickListener { openPicker(slot) }
        indentRow(row.root, indent)
        previews[slot] = row.themeColorPreview
        binding.themeHolder.addView(row.root)
    }

    private fun addDimenRow(dimen: ThemeDimen, indent: Int) {
        val row = ItemThemeDimenBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeDimenLabel.text = getString(dimen.labelRes)
        row.themeDimenLabel.setTextColor(getProperTextColor())
        row.themeDimenValue.setTextColor(getProperTextColor())
        row.themeDimenValue.text = dpLabel(themeDimenDp(dimen))
        row.root.setOnClickListener { openDimenPicker(dimen, row.themeDimenValue) }
        indentRow(row.root, indent)
        binding.themeHolder.addView(row.root)
    }

    private fun addValueRow(@StringRes labelRes: Int, value: String, indent: Int, onClick: (TextView) -> Unit) {
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = getString(labelRes)
        row.themeValueLabel.setTextColor(getProperTextColor())
        row.themeValue.setTextColor(getProperTextColor())
        row.themeValue.text = value
        row.root.setOnClickListener { onClick(row.themeValue) }
        indentRow(row.root, indent)
        binding.themeHolder.addView(row.root)
    }

    private fun addSwitchRow(@StringRes labelRes: Int, checked: Boolean, indent: Int, onToggle: (Boolean) -> Unit) {
        val row = ItemThemeSwitchBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeSwitchLabel.text = getString(labelRes)
        row.themeSwitchLabel.setTextColor(getProperTextColor())
        row.themeSwitch.isChecked = checked
        row.root.setOnClickListener {
            row.themeSwitch.toggle()
            onToggle(row.themeSwitch.isChecked)
        }
        indentRow(row.root, indent)
        binding.themeHolder.addView(row.root)
    }

    private fun addSimColorRow(simId: Int, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        val labelRes = if (simId == 1) R.string.sim_1_color else R.string.sim_2_color
        row.themeColorLabel.text = getString(labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorPreview.background.setTint(simColor(simId))
        row.root.setOnClickListener {
            ColorPickerDialog(this, simColor(simId), addDefaultColorButton = true) { wasPositive, color ->
                setSimColor(simId, if (wasPositive) color else -1)
                row.themeColorPreview.background.setTint(simColor(simId))
            }
        }
        indentRow(row.root, indent)
        binding.themeHolder.addView(row.root)
    }

    private fun simColor(simId: Int): Int {
        val stored = if (simId == 1) config.sim1Color else config.sim2Color
        return if (stored != -1) stored else getSimDefaultColor(simId)
    }

    private fun setSimColor(simId: Int, color: Int) {
        if (simId == 1) config.sim1Color = color else config.sim2Color = color
    }

    private fun getSimDefaultColor(simId: Int): Int {
        return getAvailableSIMCardLabels().firstOrNull { it.id == simId }?.color ?: getProperPrimaryColor()
    }

    private fun indentRow(view: View, indent: Int) {
        if (indent > 0) {
            view.setPaddingRelative(view.paddingStart + indent, view.paddingTop, view.paddingEnd, view.paddingBottom)
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
