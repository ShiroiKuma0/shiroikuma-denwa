package org.fossify.phone.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityThemeBinding
import org.fossify.phone.databinding.ItemThemeColorBinding
import org.fossify.phone.databinding.ItemThemeDimenBinding
import org.fossify.phone.databinding.ItemThemeSectionBinding
import org.fossify.phone.databinding.ItemThemeSubgroupBinding
import org.fossify.phone.databinding.ItemThemeSwitchBinding
import org.fossify.phone.databinding.ItemThemeTextBinding
import org.fossify.phone.databinding.ItemThemeTokenBinding
import org.fossify.phone.databinding.ItemThemeValueBinding
import org.fossify.phone.dialogs.AlphaColorPickerDialog
import org.fossify.phone.dialogs.EXPORT_WARN_COLOR
import org.fossify.phone.dialogs.ExportImportDialog
import org.fossify.phone.dialogs.FontPickerDialog
import org.fossify.phone.extensions.CallDurationFormat
import org.fossify.phone.extensions.CallTimeFormat
import org.fossify.phone.extensions.FontWeightOption
import org.fossify.phone.extensions.ThemeDimen
import org.fossify.phone.extensions.ThemeGroup
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.callDurationFormatOf
import org.fossify.phone.extensions.callTimeFormatOf
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.fontDisplayName
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.extensions.importFont
import org.fossify.phone.extensions.resetThemeColor
import org.fossify.phone.extensions.setThemeColor
import org.fossify.phone.extensions.setThemeDimenDp
import org.fossify.phone.extensions.showFontSample
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.extensions.themeDimenDp
import org.fossify.phone.helpers.MAX_FONT_SIZE_SP
import org.fossify.phone.helpers.SettingsExport
import java.io.OutputStream

// Every customizable setting for 白い熊 電話, laid out as a section > subgroup > controls cascade in the
// kxkb settings house style: a group is parted from the one above by a thin full-width hairline, its
// heading is underlined only as wide as its words, and each cascade level steps in one notch — a
// section's heading at 36dp, its rows at 72, a subgroup's heading at 54, its rows at 90.

// A row's vertical padding, replacing the commons style's 20dp: the page is a long list of one-liners,
// so it is packed tight rather than spaced like a handful of preference rows.
private const val ROW_PADDING_VERTICAL_DP = 5

// A row description renders at this share of the title's size, dimmed to this alpha.
private const val DESCRIPTION_TEXT_SCALE = 0.85f
private const val DESCRIPTION_ALPHA = 0.6f

// How many hex characters of the automation token stay visible at each end.
private const val TOKEN_ABBREVIATION_EDGE = 8

@Suppress("TooManyFunctions", "LargeClass")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    // The cascade's indents, in pixels; resolved once per rebuild. The section heading's own indent
    // lives in its layout, since it is the same for every section.
    private var subgroupIndent = 0
    private var rowIndent = 0
    private var stepPx = 0

    // Only the first section skips the hairline above it — it has nothing to be parted from.
    private var sectionCount = 0

    private var pendingFontSlot: ThemeSlot? = null
    private var pendingFontBinding: ItemThemeTextBinding? = null

    // Export / Import: the ticked categories survive a buildRows() rebuild and the panel being reopened,
    // so a choice made once is not silently forgotten. What starts ticked is each item's own declared
    // default — the same answer LIST_CATEGORIES gives 自由作業盤, so both pickers open on one state.
    private val eximSelected = SettingsExport.Item.defaultSelected.toMutableSet()
    private var eximPanel: ExportImportDialog? = null

    private val eximFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onEximFolderPicked(uri)
    }

    private val eximSaveAs =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) runEximExport(uri.lastPathSegment ?: SettingsExport.exportFileName()) {
                contentResolver.openOutputStream(uri)
            }
        }

    private val eximImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runEximImport(uri)
    }

    private val fontImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onFontImported(uri)
    }

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
        // Returning from the system's All-files-access screen: the panel's folder line may now resolve.
        eximPanel?.refresh()
    }

    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()
        sectionCount = 0

        val primaryColor = getProperPrimaryColor()
        subgroupIndent = resources.getDimensionPixelSize(R.dimen.theme_subgroup_indent)
        rowIndent = resources.getDimensionPixelSize(R.dimen.theme_row_indent)
        stepPx = resources.getDimensionPixelSize(R.dimen.theme_row_indent_step)
        val subRowIndent = rowIndent + stepPx

        // Export / Import — the first section on the page, as in every sister app: this is where the
        // app is saved and restored, so it comes before anything it can save.
        addExportImportSection(primaryColor)

        // Foundation
        addSection(R.string.theme_group_foundation, primaryColor)
        slotRowsFor(ThemeGroup.FOUNDATION, rowIndent)

        // Search bar
        addSection(R.string.theme_group_search, primaryColor)
        slotRowsFor(ThemeGroup.SEARCH, rowIndent)

        // Top bar & overflow ("hamburger") menu
        addSection(R.string.theme_group_chrome, primaryColor)
        addSubgroup(R.string.theme_subgroup_chrome_menu, primaryColor)
        addColorRow(ThemeSlot.MENU_ICON, subRowIndent)
        addColorRow(ThemeSlot.MENU_TEXT, subRowIndent)
        addSubgroup(R.string.theme_subgroup_chrome_header, primaryColor)
        addColorRow(ThemeSlot.HEADER_TITLE, subRowIndent)
        addColorRow(ThemeSlot.HEADER_ARROW, subRowIndent)
        addSubgroup(R.string.theme_subgroup_chrome_settings, primaryColor)
        addSlotRow(ThemeSlot.SETTINGS_BUTTON, subRowIndent)

        // Tabs
        addSection(R.string.theme_group_tabs, primaryColor)
        slotRowsFor(ThemeGroup.TABS, rowIndent)

        // Call log — rich enough to warrant subgroups; each line's colour sits next to its thickness
        addSection(R.string.theme_group_call_log, primaryColor)
        addSubgroup(R.string.theme_subgroup_call_log_text, primaryColor)
        addSlotRow(ThemeSlot.CALL_LOG_NAME, subRowIndent)
        addSlotRow(ThemeSlot.CALL_LOG_SUBTITLE, subRowIndent)
        addSlotRow(ThemeSlot.CALL_LOG_DATE, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_types, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_MISSED, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_INCOMING, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_OUTGOING, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_dividers, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_DIVIDER, subRowIndent)
        addDimenRow(ThemeDimen.CALL_LOG_DIVIDER_THICKNESS, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_DAY_DIVIDER, subRowIndent)
        addDimenRow(ThemeDimen.CALL_LOG_DAY_DIVIDER_THICKNESS, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_DATE_UNDERLINE, subRowIndent)
        addDimenRow(ThemeDimen.CALL_LOG_DATE_UNDERLINE_THICKNESS, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_date_format, primaryColor)
        addSlotRow(ThemeSlot.CALL_LOG_DAY_DATE, subRowIndent)
        addSwitchRow(R.string.use_imperial_date, config.useImperialDate, subRowIndent) { config.useImperialDate = it }
        val timeFormatLabel = getString(callTimeFormatOf(config.callTimeFormat).labelRes)
        addValueRow(R.string.call_time_format, timeFormatLabel, subRowIndent) {
            val items = ArrayList(CallTimeFormat.entries.map { RadioItem(it.ordinal, getString(it.labelRes)) })
            RadioGroupDialog(this, items, config.callTimeFormat) { picked ->
                config.callTimeFormat = picked as Int
                it.text = getString(callTimeFormatOf(config.callTimeFormat).labelRes)
            }
        }
        val durationFormatLabel = getString(callDurationFormatOf(config.callDurationFormat).labelRes)
        addValueRow(R.string.call_duration_format, durationFormatLabel, subRowIndent) {
            val items = ArrayList(CallDurationFormat.entries.map { RadioItem(it.ordinal, getString(it.labelRes)) })
            RadioGroupDialog(this, items, config.callDurationFormat) { picked ->
                config.callDurationFormat = picked as Int
                it.text = getString(callDurationFormatOf(config.callDurationFormat).labelRes)
            }
        }

        // Dialpad
        addSection(R.string.theme_group_dialpad, primaryColor)
        slotRowsFor(ThemeGroup.DIALPAD, rowIndent)

        // In-call screen
        addSection(R.string.theme_group_in_call, primaryColor)
        slotRowsFor(ThemeGroup.IN_CALL, rowIndent)

        // Contacts
        addSection(R.string.theme_group_contacts, primaryColor)
        slotRowsFor(ThemeGroup.CONTACTS, rowIndent)

        // Favorites
        addSection(R.string.theme_group_favorites, primaryColor)
        slotRowsFor(ThemeGroup.FAVORITES, rowIndent)

        // SIM cards — colours + swipe-to-dial only make sense with more than one SIM
        if (areMultipleSIMsAvailable()) {
            addSection(R.string.theme_group_sim, primaryColor)
            addSimColorRow(simId = 1, indent = rowIndent)
            addSimColorRow(simId = 2, indent = rowIndent)
            addSwitchRow(R.string.swipe_to_call, config.swipeToCall, rowIndent) { config.swipeToCall = it }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Export / Import
    // ---------------------------------------------------------------------------------------------

    /**
     * Two rows plus the automation subgroup, exactly as the sister apps lay it out: where backups go,
     * the panel that does the work, and — directly below the export rows it drives — the 保存復元
     * automation contract. The folder row reads red until a folder is chosen, since nothing can be
     * exported without one.
     */
    private fun addExportImportSection(primaryColor: Int) {
        addSection(R.string.eim_title, primaryColor)

        val dir = SettingsExport.configuredDir(this)
        addValueRow(
            title = getString(R.string.eim_dir),
            value = dir?.name ?: getString(R.string.eim_dir_unset),
            indent = rowIndent,
            // Red until a folder is chosen — nothing can be exported without one — then the accent.
            valueColor = if (dir == null) EXPORT_WARN_COLOR else primaryColor,
        ) { eximFolderPicker.launch(SettingsExport.configuredDirUri(this)) }

        addValueRow(
            title = getString(R.string.eim_open),
            value = lastExportSummary(),
            indent = rowIndent,
            valueColor = if (dir == null) EXPORT_WARN_COLOR else null,
        ) { showExportImportPanel() }

        addAutomationSubgroup(primaryColor)
    }

    /** The opener row's value: when the newest backup in the configured folder was written. */
    private fun lastExportSummary(): String {
        if (SettingsExport.configuredDir(this) == null) return getString(R.string.eim_last_no_dir)
        val last = SettingsExport.lastExportTime(this) ?: return getString(R.string.eim_last_never)
        val date = java.util.Date(last)
        val stamp = android.text.format.DateFormat.getDateFormat(this).format(date) + " " +
            android.text.format.DateFormat.getTimeFormat(this).format(date)
        return getString(R.string.eim_last_at, stamp)
    }

    private fun showExportImportPanel() {
        eximPanel = ExportImportDialog(
            activity = this,
            selected = eximSelected,
            onPickFolder = { eximFolderPicker.launch(SettingsExport.configuredDirUri(this)) },
            onExport = { onEximExport() },
            onImport = { onEximImport() },
        ).apply { show() }
    }

    private fun onEximFolderPicked(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        SettingsExport.setConfiguredDirUri(this, uri)
        buildRows()
        eximPanel?.refresh()
    }

    private fun onEximExport() {
        if (eximSelected.isEmpty()) {
            toast(R.string.eim_none_selected)
            return
        }
        val dir = SettingsExport.configuredDir(this)
        if (dir == null) {
            // No folder configured: fall back to a one-off save-as rather than refusing outright.
            runCatching { eximSaveAs.launch(SettingsExport.exportFileName()) }
                .onFailure { toast(org.fossify.commons.R.string.no_app_found) }
            return
        }
        val name = SettingsExport.exportFileName()
        val file = dir.createFile("application/zip", name)
        if (file == null) {
            toast(getString(R.string.eim_export_fail, dir.name))
            return
        }
        runEximExport(name) { contentResolver.openOutputStream(file.uri) }
    }

    private fun runEximExport(name: String, openOut: () -> OutputStream?) {
        toast(R.string.eim_exporting)
        SettingsExport.export(this, eximSelected.toSet(), openOut) { result ->
            runOnUiThread {
                result
                    .onSuccess { summary ->
                        val body = getString(R.string.eim_export_done, name, summary)
                        onEximFinished(R.string.eim_export_done_title, body, imported = false)
                    }
                    .onFailure { toast(getString(R.string.eim_export_fail, it.message ?: "")) }
            }
        }
    }

    private fun onEximImport() {
        if (eximSelected.isEmpty()) {
            toast(R.string.eim_none_selected)
            return
        }
        runCatching { eximImportPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            .onFailure { toast(org.fossify.commons.R.string.no_app_found) }
    }

    private fun runEximImport(uri: Uri) {
        toast(R.string.eim_importing)
        ensureBackgroundThread {
            val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                runOnUiThread { toast(getString(R.string.eim_import_fail, "no input stream")) }
                return@ensureBackgroundThread
            }
            SettingsExport.import(this, bytes, eximSelected.toSet()) { result ->
                runOnUiThread {
                    result
                        .onSuccess { summary ->
                            val body = getString(R.string.eim_import_done, summary)
                            onEximFinished(R.string.eim_import_done_title, body, imported = true)
                        }
                        .onFailure { toast(getString(R.string.eim_import_fail, it.message ?: "")) }
                }
            }
        }
    }

    /**
     * A run that succeeded: show the bordered result box, and let acknowledging it close the whole
     * chain — the box, the panel beneath it, and this page. Only success closes anything; a failure
     * toasts and leaves the panel open so the run can be retried.
     */
    private fun onEximFinished(@StringRes titleRes: Int, message: String, imported: Boolean) {
        val panel = eximPanel
        if (panel == null) {
            toast(message)
            return
        }
        val buttons = if (imported) {
            // Imported colours, fonts and settings only fully apply on a fresh process.
            listOf(
                getString(R.string.eim_restart_later) to { closeExportImportChain() },
                getString(R.string.eim_restart_now) to { restartApp() },
            )
        } else {
            listOf(getString(org.fossify.commons.R.string.ok) to { closeExportImportChain() })
        }
        panel.showResultDialog(getString(titleRes), message, buttons)
    }

    private fun closeExportImportChain() {
        eximPanel?.dismiss()
        eximPanel = null
        finish()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(Intent.makeRestartActivityTask(intent.component))
        Runtime.getRuntime().exit(0)
    }

    // ---------------------------------------------------------------------------------------------
    // 保存復元 automation — a subgroup of Export / Import, since every automation intent drives that
    // export (see receivers/StateExportReceiver)
    // ---------------------------------------------------------------------------------------------

    private fun addAutomationSubgroup(primaryColor: Int) {
        addSubgroup(R.string.automation, primaryColor)
        val indent = rowIndent + stepPx

        // Two rows, in the order every sister app uses: the master switch (default OFF), then the token.
        addSwitchRow(
            labelRes = R.string.enable_automation,
            checked = config.automationEnabled,
            indent = indent,
            description = getString(R.string.enable_automation_desc),
        ) { config.automationEnabled = it }

        addTokenRow(indent)

        // All-files access: needed only so an automation broadcast can name an absolute backup
        // directory outside Download/ and Documents/. API 30+ only.
        if (isRPlus()) {
            val granted = Environment.isExternalStorageManager()
            addValueRow(
                title = getString(R.string.all_files_access),
                value = getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed),
                indent = indent,
                valueColor = if (granted) null else EXPORT_WARN_COLOR,
            ) { openAllFilesAccess() }
        }
    }

    /** Tap anywhere to copy the full token; Regenerate on the right warns before invalidating copies. */
    private fun addTokenRow(indent: Int) {
        val b = ItemThemeTokenBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeTokenLabel.text = getString(R.string.automation_token)
        b.themeTokenLabel.setTextColor(getProperTextColor())
        b.themeTokenValue.text = abbreviateToken(config.automationToken)
        b.themeTokenValue.setTextColor(getProperTextColor())
        b.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        b.themeTokenRegenerate.setTextColor(getProperPrimaryColor())
        b.root.setOnClickListener { copyAutomationToken() }
        b.themeTokenRegenerate.setOnClickListener {
            ConfirmationDialog(
                activity = this,
                message = getString(R.string.automation_token_regenerate_warning),
                positive = R.string.automation_token_regenerate,
                negative = org.fossify.commons.R.string.cancel,
            ) {
                b.themeTokenValue.text = abbreviateToken(config.regenerateAutomationToken())
                toast(R.string.automation_token_regenerated)
            }
        }
        indentRow(b.root, indent)
        binding.themeHolder.addView(b.root)
    }

    private fun copyAutomationToken() {
        // Not commons' copyToClipboard: that one toasts the value itself, which would put the full
        // secret back on screen right after we deliberately abbreviated it.
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.automation_token), config.automationToken))
        toast(R.string.automation_token_copied)
    }

    // Shown abbreviated so the secret is not left on screen; the tap still copies it in full.
    private fun abbreviateToken(token: String): String =
        if (token.length <= TOKEN_ABBREVIATION_EDGE * 2) {
            token
        } else {
            token.take(TOKEN_ABBREVIATION_EDGE) + "…" + token.takeLast(TOKEN_ABBREVIATION_EDGE)
        }

    private fun openAllFilesAccess() {
        val direct = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { startActivity(direct) }.onFailure {
            // Some OEM builds refuse the per-app deep link; fall back to the full list.
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                .onFailure { e -> showErrorToast(e.toString()) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rows
    // ---------------------------------------------------------------------------------------------

    private fun slotRowsFor(group: ThemeGroup, indent: Int) {
        ThemeSlot.entries.filter { it.group == group }.forEach { addSlotRow(it, indent) }
    }

    // A text element gets the rich color+font+weight+size+sample block; everything else a colour swatch.
    private fun addSlotRow(slot: ThemeSlot, indent: Int) {
        if (slot.hasFont) addTextSlot(slot, indent) else addColorRow(slot, indent)
    }

    private fun addSection(@StringRes labelRes: Int, primaryColor: Int) {
        val section = ItemThemeSectionBinding.inflate(layoutInflater, binding.themeHolder, false)
        section.themeSectionLabel.text = getString(labelRes)
        section.themeSectionLabel.setTextColor(primaryColor)
        section.themeSectionRule.setBackgroundColor(primaryColor)
        // The hairline parts one group from the previous one, so the first section has none.
        if (sectionCount == 0) {
            section.themeSectionDivider.beGone()
        } else {
            section.themeSectionDivider.setBackgroundColor(primaryColor)
        }
        sectionCount++
        binding.themeHolder.addView(section.root)
    }

    private fun addSubgroup(@StringRes labelRes: Int, primaryColor: Int) {
        val sub = ItemThemeSubgroupBinding.inflate(layoutInflater, binding.themeHolder, false)
        sub.themeSubgroupLabel.text = getString(labelRes)
        sub.themeSubgroupLabel.setTextColor(primaryColor)
        sub.themeSubgroupRule.setBackgroundColor(primaryColor)
        sub.root.setPaddingRelative(subgroupIndent, sub.root.paddingTop, sub.root.paddingEnd, sub.root.paddingBottom)
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

    // A concrete text element: its colour, font family, weight, size and a live sample of all four.
    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addTextSlot(slot: ThemeSlot, indent: Int) {
        val textColor = getProperTextColor()
        val b = ItemThemeTextBinding.inflate(layoutInflater, binding.themeHolder, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(
            b.themeTextLabel, b.themeTextFontTitle, b.themeTextFontValue,
            b.themeTextWeightTitle, b.themeTextWeightValue, b.themeTextSizeTitle, b.themeTextSizeValue
        ).forEach { it.setTextColor(textColor) }

        b.themeTextColorPreview.background.setTint(themeColor(slot))
        b.themeTextFontValue.text = fontDisplayName(config.getFontFamily(slot.key))
        b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(config.getFontWeight(slot.key)).labelRes)
        b.themeTextSizeSeekbar.max = MAX_FONT_SIZE_SP
        b.themeTextSizeSeekbar.progress = config.getFontSize(slot.key)
        b.themeTextSizeValue.text = sizeLabel(config.getFontSize(slot.key))
        refreshSample(b, slot)

        b.themeTextColorRow.setOnClickListener { openTextColorPicker(slot, b) }
        b.themeTextFontRow.setOnClickListener { openFontPicker(slot, b) }
        b.themeTextWeightRow.setOnClickListener { openWeightPicker(slot, b) }
        b.themeTextSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                config.setFontSize(slot.key, progress)
                b.themeTextSizeValue.text = sizeLabel(progress)
                refreshSample(b, slot)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        indentRow(b.root, indent)
        // the element's font / weight / size / sample sit one step deeper than its label row
        indentRow(b.themeTextFontRow, indent + stepPx)
        indentRow(b.themeTextWeightRow, indent + stepPx)
        indentRow(b.themeTextSizeRow, indent + stepPx)
        indentRow(b.themeTextSample, indent + stepPx)
        binding.themeHolder.addView(b.root)
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

    private fun addValueRow(
        @StringRes labelRes: Int,
        value: String,
        indent: Int,
        onClick: (TextView) -> Unit,
    ) = addValueRow(getString(labelRes), value, indent, null, onClick)

    private fun addValueRow(
        title: String,
        value: String,
        indent: Int,
        valueColor: Int? = null,
        onClick: (TextView) -> Unit,
    ) {
        val row = ItemThemeValueBinding.inflate(layoutInflater, binding.themeHolder, false)
        row.themeValueLabel.text = title
        row.themeValueLabel.setTextColor(getProperTextColor())
        row.themeValue.text = value
        row.themeValue.setTextColor(valueColor ?: getProperTextColor())
        row.root.setOnClickListener { onClick(row.themeValue) }
        indentRow(row.root, indent)
        binding.themeHolder.addView(row.root)
    }

    private fun addSwitchRow(
        @StringRes labelRes: Int,
        checked: Boolean,
        indent: Int,
        description: String? = null,
        onToggle: (Boolean) -> Unit,
    ) {
        val row = ItemThemeSwitchBinding.inflate(layoutInflater, binding.themeHolder, false)
        val title = getString(labelRes)
        row.themeSwitchLabel.text = if (description == null) title else titleWithDescription(title, description)
        row.themeSwitchLabel.setTextColor(getProperTextColor())
        row.themeSwitch.isChecked = checked
        row.root.setOnClickListener {
            row.themeSwitch.toggle()
            onToggle(row.themeSwitch.isChecked)
        }
        indentRow(row.root, indent)
        binding.themeHolder.addView(row.root)
    }

    // A row's explanation, as a smaller dimmed line below its title — the value rows' styling, without
    // needing a second view in the switch layout.
    private fun titleWithDescription(title: String, description: String): CharSequence =
        SpannableStringBuilder(title).apply {
            append("\n")
            val start = length
            append(description)
            setSpan(RelativeSizeSpan(DESCRIPTION_TEXT_SCALE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(getProperTextColor().adjustAlpha(DESCRIPTION_ALPHA)),
                start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

    private fun addSimColorRow(simId: Int, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, binding.themeHolder, false)
        val labelRes = if (simId == 1) R.string.sim_1_color else R.string.sim_2_color
        row.themeColorLabel.text = getString(labelRes)
        row.themeColorLabel.setTextColor(getProperTextColor())
        row.themeColorPreview.background.setTint(simColor(simId))
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, simColor(simId), addDefaultColorButton = true) { wasPositive, color ->
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

    /**
     * Every row on the page passes through here, so this is also where the vertical rhythm is set: the
     * indent becomes the row's absolute start padding (the cascade level, not an offset from whatever
     * the commons style happened to use), and the style's 20dp above and below a single line of text —
     * 40dp per row, which on a page this long is screens of whitespace — is replaced with
     * [ROW_PADDING_VERTICAL_DP].
     */
    private fun indentRow(view: View, indent: Int) {
        val vertical = (ROW_PADDING_VERTICAL_DP * resources.displayMetrics.density).toInt()
        view.setPaddingRelative(indent, vertical, view.paddingEnd, vertical)
    }

    private fun refreshSample(b: ItemThemeTextBinding, slot: ThemeSlot) {
        b.themeTextSample.showFontSample(
            config.getFontFamily(slot.key),
            config.getFontWeight(slot.key),
            config.getFontSize(slot.key),
            themeColor(slot)
        )
    }

    private fun sizeLabel(sp: Int) = if (sp > 0) "$sp sp" else getString(R.string.theme_size_default)

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
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
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

    private fun openTextColorPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            b.themeTextColorPreview.background.setTint(themeColor(slot))
            refreshSample(b, slot)
        }
    }

    private fun openFontPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        FontPickerDialog(
            activity = this,
            onAddFont = {
                pendingFontSlot = slot
                pendingFontBinding = b
                fontImportLauncher.launch(arrayOf("*/*"))
            },
            onPick = { fileName ->
                config.setFontFamily(slot.key, fileName)
                b.themeTextFontValue.text = fontDisplayName(fileName)
                refreshSample(b, slot)
            }
        )
    }

    private fun openWeightPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        val items = ArrayList(FontWeightOption.entries.map { RadioItem(it.value, getString(it.labelRes)) })
        RadioGroupDialog(this, items, config.getFontWeight(slot.key)) {
            val weight = it as Int
            config.setFontWeight(slot.key, weight)
            b.themeTextWeightValue.text = getString(FontWeightOption.fromValue(weight).labelRes)
            refreshSample(b, slot)
        }
    }

    private fun onFontImported(uri: Uri?) {
        val slot = pendingFontSlot
        val b = pendingFontBinding
        pendingFontSlot = null
        pendingFontBinding = null
        if (uri == null || slot == null) {
            return
        }

        val fileName = importFont(uri)
        if (fileName == null) {
            toast(R.string.font_invalid)
            return
        }

        config.setFontFamily(slot.key, fileName)
        b?.themeTextFontValue?.text = fontDisplayName(fileName)
        if (b != null) {
            refreshSample(b, slot)
        }
    }
}
