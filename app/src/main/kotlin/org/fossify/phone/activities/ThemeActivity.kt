package org.fossify.phone.activities

import org.fossify.commons.extensions.circleDrawable
import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.UiRoleSection
import org.fossify.commons.helpers.resetUiColor
import org.fossify.commons.helpers.setUiColor
import org.fossify.commons.helpers.uiColor
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.rippleDrawable
import org.fossify.phone.extensions.resetAllThemeColors
import org.fossify.phone.extensions.rolesShownAsSlots
import com.google.android.material.chip.ChipGroup
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.provider.DocumentsContract
import android.util.TypedValue
import android.view.Gravity
import org.fossify.commons.extensions.formatSize
import org.fossify.phone.helpers.Diag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.RadioItem
import org.fossify.phone.R
import org.fossify.phone.adapters.CallScreenFieldsAdapter
import org.fossify.phone.databinding.ActivityThemeBinding
import org.fossify.phone.databinding.ItemThemeActionBinding
import org.fossify.phone.databinding.ItemThemeColorBinding
import org.fossify.phone.databinding.ItemThemeDimenBinding
import org.fossify.phone.databinding.ItemThemeFieldsOrderBinding
import org.fossify.phone.databinding.ItemThemeSubgroupBinding
import org.fossify.phone.databinding.ItemThemeSwitchBinding
import org.fossify.phone.databinding.ItemThemeTextBinding
import org.fossify.phone.databinding.ItemThemeTokenBinding
import org.fossify.phone.databinding.ItemThemeValueBinding
import org.fossify.phone.dialogs.AlphaColorPickerDialog
import org.fossify.phone.dialogs.DeclineMessagesEditor
import org.fossify.phone.dialogs.ExportImportDialog
import org.fossify.phone.dialogs.FontPickerDialog
import org.fossify.phone.dialogs.SelectContactDialog
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
import org.fossify.phone.extensions.dropRenrakusakiCopies
import org.fossify.phone.extensions.fontDisplayName
import org.fossify.phone.extensions.importFont
import org.fossify.phone.extensions.resetThemeColor
import org.fossify.phone.extensions.setThemeColor
import org.fossify.phone.extensions.setThemeDimenDp
import org.fossify.phone.extensions.setSimColor
import org.fossify.phone.extensions.simColor
import org.fossify.phone.extensions.showFontSample
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.extensions.themeDimenDp
import org.fossify.phone.helpers.CallFieldEntry
import org.fossify.phone.helpers.DeclineMessagesPrefs
import org.fossify.phone.helpers.CallScreenConfig
import org.fossify.phone.helpers.MAX_FONT_SIZE_SP
import org.fossify.phone.helpers.SIM_COLOR_UNSET
import org.fossify.phone.helpers.SettingsExport
import java.io.OutputStream

// Every customizable setting for 白い熊 電話, as the black-yellow-theme skill lays a UI page out: the way to
// the app's own settings, a draggable chip index, then the sections — each a folding block, folded
// unless 白い熊 opened it, parted from the one above by a 1 px darker-accent line, its heading underlined
// only as wide as its words. Inside a section the cascade steps in one notch per level: rows at 72dp, a
// subgroup's heading at 54, its rows at 90. Every row carries an icon coloured by what the row does, and
// every colour on the page is a role of its own, set in the section of its item.

private const val SECTION_GAP_DP = 10
private const val SECTION_HEADER_PAD_DP = 8
private const val SECTION_UNDERLINE_DP = 2
private const val SECTION_UNDERLINE_GAP_DP = 2
private const val SECTION_CHEVRON_SP = 22f
private const val SECTION_TITLE_SP = 20f

// The chip index under the app-settings pill.
private const val INDEX_GAP_DP = 6
private const val INDEX_TEXT_SP = 13f
private const val INDEX_PAD_H_DP = 10
private const val INDEX_PAD_V_DP = 5
private const val INDEX_DRAGGED_ALPHA = 0.3f

// A row's icon, before its label.
private const val ROW_ICON_DP = 20
private const val ROW_ICON_GAP_DP = 10

/** A top-level section of the page: its stored [key] (never rename one), its heading, and what builds its rows. */
private class UiSection(val key: String, val title: String, val fill: () -> Unit)

/**
 * The icon a row of the page carries, coloured by what the row does (black-yellow theme: "Every row has an
 * icon"). The colours are the menu icon roles, so a row and a menu line that do the same wear the same colour.
 */
private enum class RowIcon(val res: Int, val role: ThemeSlot) {
    SETTINGS(org.fossify.commons.R.drawable.ic_settings_cog_vector, ThemeSlot.MENU_ROLE_VIEW),
    TOGGLE(org.fossify.commons.R.drawable.ic_check_circle_vector, ThemeSlot.MENU_ROLE_VIEW),
    VALUE(org.fossify.commons.R.drawable.ic_edit_vector, ThemeSlot.MENU_ROLE_VIEW),
    COLOR(R.drawable.ic_palette_vector, ThemeSlot.MENU_ROLE_VIEW),
    TEXT(R.drawable.ic_text_fields_vector, ThemeSlot.MENU_ROLE_VIEW),
    WEIGHT(R.drawable.ic_format_bold_vector, ThemeSlot.MENU_ROLE_VIEW),
    SIZE(R.drawable.ic_tune_vector, ThemeSlot.MENU_ROLE_VIEW),
    TIME(org.fossify.commons.R.drawable.ic_clock_vector, ThemeSlot.MENU_ROLE_VIEW),
    RESET(org.fossify.commons.R.drawable.ic_reset_vector, ThemeSlot.MENU_ROLE_VIEW),
    DIAG(org.fossify.commons.R.drawable.ic_info_vector, ThemeSlot.MENU_ROLE_VIEW),
    SECRET(org.fossify.commons.R.drawable.ic_lock_vector, ThemeSlot.MENU_ROLE_BLOCK),
    SHARE(org.fossify.commons.R.drawable.ic_share_vector, ThemeSlot.MENU_ROLE_SHARE),
    DIRECTORY(org.fossify.commons.R.drawable.ic_folder_vector, ThemeSlot.MENU_ROLE_SHARE),
    AUTOMATION(org.fossify.commons.R.drawable.ic_code_vector, ThemeSlot.MENU_ROLE_SHARE),
    CALL(org.fossify.commons.R.drawable.ic_phone_vector, ThemeSlot.MENU_ROLE_CALL),
    SIM(org.fossify.commons.R.drawable.ic_sim_vector, ThemeSlot.MENU_ROLE_CALL),
    MESSAGE(org.fossify.commons.R.drawable.ic_sms_vector, ThemeSlot.MENU_ROLE_MESSAGE),
    ADD(org.fossify.commons.R.drawable.ic_plus_vector, ThemeSlot.MENU_ROLE_MESSAGE),
    CONTACT(org.fossify.commons.R.drawable.ic_person_vector, ThemeSlot.MENU_ROLE_CONTACT),
    DELETE(org.fossify.commons.R.drawable.ic_delete_vector, ThemeSlot.MENU_ROLE_DELETE),
}

// A row's vertical padding, replacing the commons style's 20dp: every row is only as tall as its content
// (black-yellow-theme skill, "Row padding"), so a page of one-liners never reads double-spaced.
private const val ROW_PADDING_VERTICAL_DP = 2
private const val NOTE_TEXT_SP = 13f


// How many hex characters of the automation token stay visible at each end.
private const val TOKEN_ABBREVIATION_EDGE = 8

// The family's action pill (black-yellow-theme skill; the same numbers as commons sk12's pillDrawable):
// always outlined, never filled — the main action of a section a thick frame, every other one a thin one.
private const val PILL_RADIUS_DP = 100f
private const val PILL_STROKE_MAIN_DP = 3f
private const val PILL_STROKE_DP = 1.25f
private const val PILL_RIPPLE_ALPHA = 0.3f
private const val PILL_ROW_PADDING_DP = 3

/** Set by SettingsActivity when it opens this page, so "App settings" goes back to it rather than stacking another. */
const val EXTRA_FROM_SETTINGS = "shiroikuma_from_settings"

@Suppress("TooManyFunctions", "LargeClass")
class ThemeActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThemeBinding::inflate)
    private val previews = HashMap<ThemeSlot, ImageView>()

    // The cascade's indents, in pixels; resolved once per rebuild. The section heading's own indent
    // lives in its layout, since it is the same for every section.
    private var subgroupIndent = 0
    private var rowIndent = 0
    private var stepPx = 0

    // Where the row builders add their rows: the page itself, or the body of the section being filled.
    private lateinit var body: ViewGroup

    // The page's sections in their shown order, and each one's views by its key.
    private var sections: List<UiSection> = emptyList()
    private val sectionWrappers = HashMap<String, View>()
    private val sectionBodies = HashMap<String, LinearLayout>()
    private val sectionChevrons = HashMap<String, TextView>()
    private val sectionsFilled = HashSet<String>()

    // Rebuilt whenever the caller-block order box changes: one styling row per ticked field.
    private var callFieldStylingContainer: LinearLayout? = null

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

    // Diagnostics: the export's directory picker, and whether "keep for" changed (clean up on leaving).
    private var diagCleanupDue = false
    private val diagExportPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runDiagExport(uri)
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

    override fun onPause() {
        super.onPause()
        if (diagCleanupDue) {
            diagCleanupDue = false
            ensureBackgroundThread { Diag.cleanup(applicationContext) }
        }
    }

    // A folded section's rows are not built until it is first opened: the page has several hundred of them.
    private fun buildRows() {
        binding.themeHolder.removeAllViews()
        previews.clear()
        sectionWrappers.clear()
        sectionBodies.clear()
        sectionChevrons.clear()
        sectionsFilled.clear()
        callFieldStylingContainer = null
        body = binding.themeHolder

        val primaryColor = themeColor(ThemeSlot.PAGE_SUBHEADING)
        subgroupIndent = resources.getDimensionPixelSize(R.dimen.theme_subgroup_indent)
        rowIndent = resources.getDimensionPixelSize(R.dimen.theme_row_indent)
        stepPx = resources.getDimensionPixelSize(R.dimen.theme_row_indent_step)
        val subRowIndent = rowIndent + stepPx

        // The page's link to the app's own settings: right under the title, the first and thick pill.
        // It is not a section.
        addActionRow(
            title = getString(R.string.theme_app_settings),
            summary = getString(R.string.theme_app_settings_desc),
            indent = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin),
            main = true,
            icon = RowIcon.SETTINGS,
        ) { openAppSettings() }

        // Your data first (backup, the decline replies), then the look surface by surface, the commons'
        // own items after ours, housekeeping and diagnostics last. The keys are stored: never rename one.
        val built = ArrayList<UiSection>()
        fun section(key: String, @StringRes titleRes: Int, fill: () -> Unit) =
            built.add(UiSection(key, getString(titleRes), fill))
        fun groupSection(key: String, group: ThemeGroup) =
            section(key, group.labelRes) { slotRowsFor(group, rowIndent) }
        fun roleSection(roles: UiRoleSection) =
            section("roles_" + roles.name.lowercase(Locale.ROOT), roles.labelRes) { addRoleRows(roles, rowIndent) }

        section("exim", R.string.eim_title) { addExportImportSection(primaryColor) }
        section("decline", R.string.sk_decline_settings) { addDeclineMessagesSection(primaryColor) }
        groupSection("foundation", ThemeGroup.FOUNDATION)
        groupSection("page", ThemeGroup.PAGE)
        groupSection("search", ThemeGroup.SEARCH)
        section("chrome", R.string.theme_group_chrome) { addChromeRows(primaryColor, subRowIndent) }
        section("menus", UiRoleSection.MENUS.labelRes) { addMenuRows(primaryColor, subRowIndent) }
        groupSection("tabs", ThemeGroup.TABS)
        section("call_log", R.string.theme_group_call_log) { addCallLogRows(primaryColor, subRowIndent) }
        groupSection("dialpad", ThemeGroup.DIALPAD)
        section("in_call", R.string.theme_group_in_call) {
            slotRowsFor(ThemeGroup.IN_CALL, rowIndent)
            addCallerFieldsSubgroup(primaryColor, subRowIndent)
        }
        groupSection("car", ThemeGroup.CAR)
        groupSection("notification", ThemeGroup.NOTIFICATION)
        groupSection("contacts", ThemeGroup.CONTACTS)
        groupSection("favorites", ThemeGroup.FAVORITES)
        section("lists", R.string.theme_group_lists) {
            slotRowsFor(ThemeGroup.LISTS, rowIndent)
            addRoleRows(UiRoleSection.LISTS, rowIndent)
        }
        // SIM cards — colours + swipe-to-dial only make sense with more than one SIM
        if (areMultipleSIMsAvailable()) {
            section("sim", R.string.theme_group_sim) { addSimRows() }
        }
        // Everything the commons draws that no row above stands for, each role in the section of its item
        listOf(
            UiRoleSection.BUTTONS, UiRoleSection.TEXT, UiRoleSection.FIELDS, UiRoleSection.DIALOGS,
            UiRoleSection.SWITCHES, UiRoleSection.MARKS, UiRoleSection.BARS, UiRoleSection.TOASTS,
        ).forEach(::roleSection)
        section("maintenance", R.string.maintenance_section) { addMaintenanceRows() }
        // Diagnostics — last, as in every sister app
        section("diag", R.string.diag_title) { addDiagnosticsSection(primaryColor, subRowIndent) }
        sections = orderedSections(built)

        addSectionIndex()
        sections.forEachIndexed { index, section -> addSection(section, index == 0) }
    }

    /** The stored order applied to [built]: stored keys first, then sections a later build added. */
    private fun orderedSections(built: List<UiSection>): List<UiSection> {
        val byKey = built.associateBy { it.key }
        val stored = config.uiSectionOrder.split(',').mapNotNull { byKey[it] }.distinct()
        return stored + built.filter { it !in stored }
    }

    // The jump index: a wrapping grid of small outlined chips, one per section. A tap unfolds the section
    // and scrolls to it; a long-press lifts the chip, the chips reorder live under the finger, and the
    // sections follow on drop.
    private fun addSectionIndex() {
        val density = resources.displayMetrics.density
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val gap = (INDEX_GAP_DP * density).toInt()
        val group = ChipGroup(this).apply {
            chipSpacingHorizontal = gap
            chipSpacingVertical = gap
            setPaddingRelative(margin, gap, margin, gap)
        }
        val chipColor = themeColor(ThemeSlot.PAGE_CHIP)
        sections.forEach { section ->
            group.addView(TextView(this).apply {
                text = section.title
                tag = section.key
                setTextColor(chipColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, INDEX_TEXT_SP)
                val h = (INDEX_PAD_H_DP * density).toInt()
                val v = (INDEX_PAD_V_DP * density).toInt()
                setPadding(h, v, h, v)
                background = pillDrawable(chipColor, uiColor(UiRole.BUTTON_GROUND), main = false)
                setOnClickListener { jumpToSection(section.key) }
                setOnLongClickListener { chip ->
                    chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    chip.startDragAndDrop(null, View.DragShadowBuilder(chip), chip, 0)
                    chip.alpha = INDEX_DRAGGED_ALPHA
                    true
                }
            })
        }

        makeChipsDraggable(group)
        binding.themeHolder.addView(group)
    }

    private fun makeChipsDraggable(group: ChipGroup) {
        val orderBefore = sections.map { it.key }
        // The chip last moved past: the same chip is not swapped again until the finger has been over the
        // dragged one, or two chips of unequal width would swap back and forth under a still finger.
        var lastTarget: View? = null
        group.setOnDragListener { _, event ->
            val dragged = event.localState as? View
            if (dragged == null || dragged.parent !== group) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    val target = (0 until group.childCount).map { group.getChildAt(it) }
                        .firstOrNull { x in it.left until it.right && y in it.top until it.bottom }
                    if (target === dragged) {
                        lastTarget = null
                    } else if (target != null && target !== lastTarget) {
                        val to = group.indexOfChild(target)
                        group.removeView(dragged)
                        group.addView(dragged, to)
                        lastTarget = target
                    }
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    dragged.alpha = 1f
                    val order = (0 until group.childCount).map { group.getChildAt(it).tag as String }
                    if (order != orderBefore) {
                        config.uiSectionOrder = order.joinToString(",")
                        // not from inside the drag dispatch: the rebuild removes the view being dispatched to
                        group.post { buildRows() }
                    }
                }
            }
            true
        }
    }

    // A folding section: the 1 px darker-accent line above it (every section but the first; it stays whether
    // the section is folded or open), its bold heading underlined as wide as its text with › folded / ⌄ open
    // at the right, and its rows in a body that folds away.
    private fun addSection(section: UiSection, first: Boolean) {
        val density = resources.displayMetrics.density
        val margin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val heading = themeColor(ThemeSlot.PAGE_HEADING)
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (!first) {
            val line = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            line.topMargin = (SECTION_GAP_DP * density).toInt()
            wrapper.addView(View(this).apply { setBackgroundColor(themeColor(ThemeSlot.PAGE_DIVIDER)) }, line)
        }

        val label = TextView(this).apply {
            text = section.title
            setTextColor(heading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_TITLE_SP)
            setTypeface(typeface, Typeface.BOLD)
        }
        val labelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label, wrapContent())
            // as wide as the column, which is as wide as the label's text
            addView(
                View(this@ThemeActivity).apply { setBackgroundColor(heading) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (SECTION_UNDERLINE_DP * density).toInt())
                    .apply { topMargin = (SECTION_UNDERLINE_GAP_DP * density).toInt() },
            )
        }
        val chevron = TextView(this).apply {
            setTextColor(themeColor(ThemeSlot.PAGE_CHEVRON))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, SECTION_CHEVRON_SP)
            includeFontPadding = false
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val v = (SECTION_HEADER_PAD_DP * density).toInt()
            setPaddingRelative(margin, v, margin, v)
            background = rippleDrawable()
            addView(labelColumn, wrapContent())
            addView(View(this@ThemeActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(chevron)
            setOnClickListener { setSectionOpen(section.key, section.key !in config.uiSectionsOpen) }
        }
        wrapper.addView(header)

        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(inner)
        binding.themeHolder.addView(wrapper)

        sectionWrappers[section.key] = wrapper
        sectionBodies[section.key] = inner
        sectionChevrons[section.key] = chevron
        showSectionState(section.key, section.key in config.uiSectionsOpen)
    }

    private fun wrapContent() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun setSectionOpen(key: String, open: Boolean) {
        val stored = config.uiSectionsOpen
        config.uiSectionsOpen = if (open) stored + key else stored - key
        showSectionState(key, open)
    }

    private fun showSectionState(key: String, open: Boolean) {
        val inner = sectionBodies[key] ?: return
        if (open && sectionsFilled.add(key)) {
            // rows are added to `body`; it points here only while this section's own rows are built
            body = inner
            sections.firstOrNull { it.key == key }?.fill?.invoke()
            body = binding.themeHolder
        }
        inner.beVisibleIf(open)
        sectionChevrons[key]?.text = if (open) "⌄" else "›"
    }

    /** A tap on an index chip: unfold the section and scroll to it. */
    private fun jumpToSection(key: String) {
        val wrapper = sectionWrappers[key] ?: return
        setSectionOpen(key, true)
        binding.themeHolder.post { binding.themeNestedScrollview.smoothScrollTo(0, wrapper.top) }
    }

    // Top bar & overflow ("hamburger") menu, and the bar that asks for a permission
    private fun addChromeRows(primaryColor: Int, subRowIndent: Int) {
        addSubgroup(R.string.theme_subgroup_chrome_menu, primaryColor)
        addColorRow(ThemeSlot.MENU_ICON, subRowIndent)
        addColorRow(ThemeSlot.MENU_TEXT, subRowIndent)
        addSubgroup(R.string.theme_subgroup_chrome_header, primaryColor)
        addColorRow(ThemeSlot.HEADER_TITLE, subRowIndent)
        addColorRow(ThemeSlot.HEADER_ARROW, subRowIndent)
        addSubgroup(R.string.theme_subgroup_snackbar, primaryColor)
        addColorRow(ThemeSlot.SNACKBAR_GROUND, subRowIndent)
        addColorRow(ThemeSlot.SNACKBAR_TEXT, subRowIndent)
    }

    // Menus: the icon of a row by what the row does, then the popup itself (the commons' menu roles)
    private fun addMenuRows(primaryColor: Int, subRowIndent: Int) {
        addSubgroup(R.string.theme_subgroup_menu_roles, primaryColor)
        listOf(
            ThemeSlot.MENU_ROLE_CALL, ThemeSlot.MENU_ROLE_MESSAGE, ThemeSlot.MENU_ROLE_CONTACT,
            ThemeSlot.MENU_ROLE_SHARE, ThemeSlot.MENU_ROLE_VIEW, ThemeSlot.MENU_ROLE_BLOCK,
            ThemeSlot.MENU_ROLE_DELETE,
        ).forEach { addColorRow(it, subRowIndent) }
        addSubgroup(R.string.theme_subgroup_menu_panel, primaryColor)
        addRoleRows(UiRoleSection.MENUS, subRowIndent)
    }

    // Call log — rich enough to warrant subgroups; each line's colour sits next to its thickness
    private fun addCallLogRows(primaryColor: Int, subRowIndent: Int) {
        addSubgroup(R.string.theme_subgroup_call_log_text, primaryColor)
        addSlotRow(ThemeSlot.CALL_LOG_NAME, subRowIndent)
        addSlotRow(ThemeSlot.CALL_LOG_SUBTITLE, subRowIndent)
        addSlotRow(ThemeSlot.CALL_LOG_DATE, subRowIndent)
        addSlotRow(ThemeSlot.CALL_LOG_NUMBER, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_NUMBER_LABEL, subRowIndent)
        addSlotRow(ThemeSlot.CALL_LOG_DURATION, subRowIndent)
        addSlotRow(ThemeSlot.CALL_LOG_COUNTRY, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_FILTER_BANNER, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_actions, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_ACTION_MESSAGE, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_ACTION_CALL, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_OVERFLOW, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_rindenwa, primaryColor)
        addColorRow(ThemeSlot.RIN_BADGE, subRowIndent)
        addColorRow(ThemeSlot.RIN_BADGE_TEXT, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_FAILURE, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_types, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_MISSED, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_INCOMING, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_OUTGOING, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_UNANSWERED, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_dividers, primaryColor)
        addColorRow(ThemeSlot.CALL_LOG_DIVIDER, subRowIndent)
        addDimenRow(ThemeDimen.CALL_LOG_DIVIDER_THICKNESS, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_DAY_DIVIDER, subRowIndent)
        addDimenRow(ThemeDimen.CALL_LOG_DAY_DIVIDER_THICKNESS, subRowIndent)
        addColorRow(ThemeSlot.CALL_LOG_DATE_UNDERLINE, subRowIndent)
        addDimenRow(ThemeDimen.CALL_LOG_DATE_UNDERLINE_THICKNESS, subRowIndent)
        addSubgroup(R.string.theme_subgroup_call_log_date_format, primaryColor)
        addSlotRow(ThemeSlot.CALL_LOG_DAY_DATE, subRowIndent)
        addSwitchRow(R.string.use_imperial_date, config.useImperialDate, subRowIndent, icon = RowIcon.TIME) {
            config.useImperialDate = it
        }
        val timeFormatLabel = getString(callTimeFormatOf(config.callTimeFormat).labelRes)
        addValueRow(R.string.call_time_format, timeFormatLabel, subRowIndent, RowIcon.TIME) {
            val items = ArrayList(CallTimeFormat.entries.map { RadioItem(it.ordinal, getString(it.labelRes)) })
            RadioGroupDialog(this, items, config.callTimeFormat) { picked ->
                config.callTimeFormat = picked as Int
                it.text = getString(callTimeFormatOf(config.callTimeFormat).labelRes)
            }
        }
        val durationFormatLabel = getString(callDurationFormatOf(config.callDurationFormat).labelRes)
        addValueRow(R.string.call_duration_format, durationFormatLabel, subRowIndent, RowIcon.TIME) {
            val items = ArrayList(CallDurationFormat.entries.map { RadioItem(it.ordinal, getString(it.labelRes)) })
            RadioGroupDialog(this, items, config.callDurationFormat) { picked ->
                config.callDurationFormat = picked as Int
                it.text = getString(callDurationFormatOf(config.callDurationFormat).labelRes)
            }
        }
    }

    private fun addSimRows() {
        addSimColorRow(simId = 1, indent = rowIndent)
        addSimColorRow(simId = 2, indent = rowIndent)
        addColorRow(ThemeSlot.SIM_TEXT, rowIndent)
        addSwitchRow(R.string.sim_text_bold, config.simTextBold, rowIndent, icon = RowIcon.SIM) {
            config.simTextBold = it
        }
        addSwitchRow(R.string.swipe_to_call, config.swipeToCall, rowIndent, icon = RowIcon.CALL) {
            config.swipeToCall = it
        }
    }

    // Housekeeping: both act in place, so neither pill has a ›.
    private fun addMaintenanceRows() {
        addActionRow(
            getString(R.string.ui_reset_section_order), getString(R.string.ui_reset_section_order_desc), rowIndent,
            main = false, opens = false, icon = RowIcon.RESET,
        ) {
            config.uiSectionOrder = ""
            buildRows()
        }

        // Every colour role back on its default, after a red confirmation: it cannot be undone.
        addActionRow(
            getString(R.string.reset_all_colours), getString(R.string.reset_all_colours_desc), rowIndent,
            main = false, opens = false, icon = RowIcon.RESET,
        ) {
            ConfirmationDialog(
                activity = this,
                message = getString(R.string.reset_all_colours_confirm),
                positive = org.fossify.commons.R.string.yes,
                negative = org.fossify.commons.R.string.no,
                destructive = true,
            ) {
                resetAllThemeColors()
                recreate()
            }
        }
    }

    /**
     * The commons' colour roles (helpers/UiRoles.kt) of one [section] that no [ThemeSlot] stands for:
     * since sk18 every colour the commons draws is a role, and a role with no row here could not be
     * set in this app at all. A new commons role appears here by itself.
     */
    private fun addRoleRows(section: UiRoleSection, indent: Int) {
        UiRole.entries.filter { it.section == section && it !in rolesShownAsSlots }.forEach { addRoleRow(it, indent) }
    }

    private fun addRoleRow(role: UiRole, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(role.labelRes)
        row.themeColorLabel.setTextColor(themeColor(ThemeSlot.PAGE_LABEL))
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = circleDrawable(uiColor(role))
        row.root.background = rippleDrawable()
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, uiColor(role), addDefaultColorButton = true) { wasPositive, color ->
                if (wasPositive) setUiColor(role, color) else resetUiColor(role)
                // a role may be the default of others, and the page itself is drawn with several of them
                recreate()
            }
        }
        indentRow(row.root, indent)
        body.addView(row.root)
    }

    // ---------------------------------------------------------------------------------------------
    // Diagnostics
    // ---------------------------------------------------------------------------------------------

    /**
     * Every diagnostic behind its own switch, off by default, in subsections by theme; then what is
     * recorded and what can be done with it — keep-for, export, share, purge. The layout the
     * diagnostics-ui-settings skill prescribes; the storage is [Diag].
     */
    private fun addDiagnosticsSection(primaryColor: Int, subRowIndent: Int) {
        addNoteRow(getString(R.string.diag_note, Diag.dir(this)?.absolutePath ?: "?"), rowIndent)
        addNoteRow(getString(R.string.diag_notification_note), rowIndent)

        addSubgroup(R.string.diag_sub_car, primaryColor)
        addDiagSwitch(Diag.Kind.CAR, subRowIndent)
        addSubgroup(R.string.diag_sub_calls, primaryColor)
        addDiagSwitch(Diag.Kind.CALLS, subRowIndent)
        addDiagSwitch(Diag.Kind.SIM, subRowIndent)

        addSubgroup(R.string.diag_sub_data, primaryColor)
        // counted off the main thread: reading every file's first line must not slow the page down
        val stored = addNoteRow(getString(R.string.diag_counting), subRowIndent)
        ensureBackgroundThread {
            val summary = Diag.summarize(applicationContext)
            val text = if (summary.files == 0) {
                getString(R.string.diag_stored_none)
            } else {
                getString(R.string.diag_stored, diagAmount(summary.files, summary.bytes), diagSpan(summary.first, summary.last))
            }
            runOnUiThread { stored.text = text }
        }
        addKeepDaysRow(subRowIndent)
        addNoteRow(getString(R.string.diag_keep_note), subRowIndent)

        val lastTree = Diag.getExportTree(this)?.lastPathSegment?.substringAfter(':')?.let { "/sdcard/$it" } ?: "/sdcard/tmp"
        addActionRow(
            getString(R.string.diag_export), getString(R.string.diag_export_descr, lastTree), subRowIndent,
            main = false, icon = RowIcon.DIRECTORY,
        ) {
            if (Diag.files(this).isEmpty()) {
                toast(R.string.diag_nothing)
            } else {
                // first offered at /sdcard/tmp, afterwards where the last export went
                val tree = Diag.getExportTree(this)
                diagExportPicker.launch(
                    if (tree != null) {
                        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    } else {
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:tmp")
                    }
                )
            }
        }
        addActionRow(
            getString(R.string.diag_share), getString(R.string.diag_share_descr), subRowIndent, main = false,
            icon = RowIcon.SHARE,
        ) {
            Diag.shareIntent(this)?.let(::startActivity) ?: toast(R.string.diag_nothing)
        }
        addActionRow(
            getString(R.string.diag_purge), getString(R.string.diag_purge_descr), subRowIndent,
            main = false, opens = false, labelColor = uiColor(UiRole.BUTTON_DESTRUCTIVE), icon = RowIcon.DELETE,
        ) {
            confirmDiagPurge()
        }
    }

    private fun addDiagSwitch(kind: Diag.Kind, indent: Int) {
        addSwitchRow(kind.titleRes, Diag.isOn(kind), indent, getString(kind.descrRes), RowIcon.DIAG) {
            Diag.set(this, kind, it)
        }
    }

    private fun runDiagExport(tree: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        ensureBackgroundThread {
            val name = runCatching { Diag.exportTo(applicationContext, tree) }.getOrNull()
            runOnUiThread {
                toast(if (name != null) getString(R.string.diag_export_done, name) else getString(R.string.diag_export_failed))
                buildRows()
            }
        }
    }

    // The warning says exactly what goes: how many files and how big, per kind, and each day's time span.
    private fun confirmDiagPurge() {
        ensureBackgroundThread {
            val summary = Diag.summarize(applicationContext)
            runOnUiThread {
                if (summary.files == 0) {
                    toast(R.string.diag_nothing)
                    return@runOnUiThread
                }
                val total = diagAmount(summary.files, summary.bytes)
                val kinds = summary.kinds.map { (kind, k) -> "$kind: ${diagAmount(k[0].toInt(), k[1])}, ${diagSpan(k[2], k[3])}" }
                val clock = SimpleDateFormat("HH:mm", Locale.US)
                val days = summary.days.map { (day, span) -> "$day  ${clock.format(Date(span[0]))} – ${clock.format(Date(span[1]))}" }
                val message = getString(R.string.diag_purge_warning, total, (kinds + "" + days).joinToString("\n"))
                ConfirmationDialog(
                    this, message, positive = R.string.diag_purge_do, negative = org.fossify.commons.R.string.cancel, destructive = true,
                ) {
                    ensureBackgroundThread {
                        Diag.purge(applicationContext)
                        runOnUiThread {
                            toast(getString(R.string.diag_purged, total))
                            buildRows()
                        }
                    }
                }
            }
        }
    }

    private fun diagAmount(files: Int, bytes: Long) =
        "${resources.getQuantityString(R.plurals.diag_files, files, files)}, ${bytes.formatSize()}"

    private fun diagSpan(first: Long, last: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return "${format.format(Date(first))} – ${format.format(Date(last))}"
    }

    /** A loose explanation that belongs to no single row: dim gold, small, no rule. */
    private fun addNoteRow(text: String, indent: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(themeColor(ThemeSlot.SETTINGS_NOTE))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, NOTE_TEXT_SP)
        val vertical = (ROW_PADDING_VERTICAL_DP * resources.displayMetrics.density).toInt()
        setPaddingRelative(indent, vertical, resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin), vertical)
        body.addView(this)
    }

    // "Keep for N days": a slider from 0 (everything) to Diag.MAX_KEEP_DAYS, its value beside it.
    private fun addKeepDaysRow(indent: Int) {
        fun label(days: Int) = if (days == 0) getString(R.string.diag_keep_all) else getString(R.string.diag_keep_days, days)
        val value = TextView(this).apply {
            text = label(Diag.getKeepDays(this@ThemeActivity))
            setTextColor(themeColor(ThemeSlot.PAGE_VALUE))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ThemeActivity).apply {
                setText(R.string.diag_keep)
                setTextColor(themeColor(ThemeSlot.PAGE_LABEL))
                setRowIcon(RowIcon.TIME)
            })
            addView(
                SeekBar(this@ThemeActivity).apply {
                    max = Diag.MAX_KEEP_DAYS
                    progress = Diag.getKeepDays(this@ThemeActivity)
                    progressTintList = ColorStateList.valueOf(uiColor(UiRole.SLIDER_TRACK))
                    thumbTintList = ColorStateList.valueOf(uiColor(UiRole.SLIDER_THUMB))
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                            if (!fromUser) return
                            Diag.setKeepDays(this@ThemeActivity, progress)
                            diagCleanupDue = true
                            value.text = label(progress)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar) {}
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(value)
        }
        indentRow(row, indent)
        row.setPaddingRelative(indent, row.paddingTop, resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin), row.paddingBottom)
        body.addView(row)
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
        val dir = SettingsExport.configuredDir(this)
        addValueRow(
            title = getString(R.string.eim_dir),
            value = dir?.name ?: getString(R.string.eim_dir_unset),
            indent = rowIndent,
            // Red until a folder is chosen — nothing can be exported without one — then the accent.
            valueColor = if (dir == null) themeColor(ThemeSlot.WARNING) else null,
            icon = RowIcon.DIRECTORY,
        ) { eximFolderPicker.launch(SettingsExport.configuredDirUri(this)) }

        addActionRow(
            title = getString(R.string.eim_open),
            summary = getString(R.string.eim_open_desc) + "\n" + lastExportSummary(),
            indent = rowIndent,
            main = true,
            summaryColor = if (dir == null) themeColor(ThemeSlot.WARNING) else null,
            icon = RowIcon.SHARE,
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
    // Call decline messages
    // ---------------------------------------------------------------------------------------------

    /**
     * The replies offered by the incoming-call screen's Message button. The general list is shown
     * inline; each caller with replies of their own gets one row that opens the shared editor
     * (DeclineMessagesEditor — the call log's per-entry action opens the very same dialogs). Laid out
     * like the section of the same name on shiroikuma-jami's UI page.
     */
    private fun addDeclineMessagesSection(primaryColor: Int) {
        val indent = rowIndent + stepPx

        // without SEND_SMS the reply still goes out through Telecom, so this is a warning, not a gate
        if (!hasPermission(PERMISSION_SEND_SMS)) {
            addActionRow(
                title = getString(R.string.sk_decline_settings_sms_permission),
                summary = null,
                indent = rowIndent,
                main = false,
                labelColor = themeColor(ThemeSlot.WARNING),
                icon = RowIcon.MESSAGE,
            ) { handlePermission(PERMISSION_SEND_SMS) { buildRows() } }
        }

        addSubgroup(R.string.sk_decline_general, primaryColor)
        DeclineMessagesPrefs.global(this).forEachIndexed { index, message ->
            // a message opens its actions (edit, move, delete), so it is a pill with a ›
            addActionRow(message, null, indent, main = false, icon = RowIcon.MESSAGE) {
                DeclineMessagesEditor.showItemActions(this, null, index, { buildRows() })
            }
        }
        addActionRow(getString(R.string.sk_decline_add), null, indent, main = false, icon = RowIcon.ADD) {
            DeclineMessagesEditor.showEdit(this, getString(R.string.sk_decline_add), "") { text ->
                DeclineMessagesPrefs.addGlobal(this, text)
                buildRows()
            }
        }

        addSubgroup(R.string.sk_decline_settings_callers, primaryColor)
        for (number in DeclineMessagesPrefs.callersWithOwn(this)) {
            val name = DeclineMessagesPrefs.callerName(this, number)
            val count = DeclineMessagesPrefs.forNumber(this, number).size
            addActionRow(
                title = name?.let { "$it ($number)" } ?: number,
                summary = resources.getQuantityString(R.plurals.sk_decline_message_count, count, count),
                indent = indent,
                main = false,
                icon = RowIcon.CONTACT,
            ) { DeclineMessagesEditor.showList(this, number, name) { buildRows() } }
        }
        val addCaller = getString(R.string.sk_decline_settings_add_caller)
        addActionRow(addCaller, null, indent, main = false, icon = RowIcon.CONTACT) { pickDeclineCaller() }
    }

    /** A contact, then — when it has several — which of its numbers; the lists are kept per number. */
    private fun pickDeclineCaller() {
        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            val all = contacts.toMutableList()
            val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
            all.dropRenrakusakiCopies(this, privateContacts)
            all.addAll(privateContacts)
            all.sort()
            if (all.isEmpty()) {
                return@getContacts
            }

            SelectContactDialog(this, all) { contact ->
                val name = contact.getNameToDisplay()
                val numbers = contact.phoneNumbers
                if (numbers.size > 1) {
                    val items = numbers.mapIndexed { index, item ->
                        RadioItem(index, "${item.value} (${getPhoneNumberTypeText(item.type, item.label)})", item)
                    }
                    RadioGroupDialog(this, ArrayList(items)) { picked ->
                        DeclineMessagesEditor.showList(this, (picked as PhoneNumber).value, name) { buildRows() }
                    }
                } else {
                    numbers.firstOrNull()?.let {
                        DeclineMessagesEditor.showList(this, it.value, name) { buildRows() }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 保存復元 automation — a subgroup of Export / Import, since every automation intent drives that
    // export (see receivers/StateExportReceiver)
    // ---------------------------------------------------------------------------------------------

    private fun addAutomationSubgroup(primaryColor: Int) {
        addSubgroup(R.string.automation, primaryColor)
        val indent = rowIndent + stepPx

        // Three rows, in the order every sister app uses: the master switch (default ON since contract
        // v2), then whether a token is demanded at all (default OFF), then — only when it is — the token
        // itself. A 48-character secret sitting under an off switch only invites 白い熊 to paste it
        // somewhere it would do nothing, so the row is hidden rather than dimmed.
        addSwitchRow(
            labelRes = R.string.enable_automation,
            checked = config.automationEnabled,
            indent = indent,
            description = getString(R.string.enable_automation_desc),
            icon = RowIcon.AUTOMATION,
        ) { config.automationEnabled = it }

        addSwitchRow(
            labelRes = R.string.automation_require_token,
            checked = config.automationRequireToken,
            indent = indent,
            description = getString(R.string.automation_require_token_desc),
            icon = RowIcon.SECRET,
        ) {
            config.automationRequireToken = it
            // Rebuild so the token row appears or goes away with the switch that asks for it.
            buildRows()
        }

        if (config.automationRequireToken) {
            addTokenRow(indent)
        }

        // All-files access: needed only so an automation broadcast can name an absolute backup
        // directory outside Download/ and Documents/. API 30+ only.
        if (isRPlus()) {
            val granted = Environment.isExternalStorageManager()
            // opens the system's own screen: a pill with a ›, its state beneath (red while missing)
            addActionRow(
                title = getString(R.string.all_files_access),
                summary = getString(if (granted) R.string.all_files_access_granted else R.string.all_files_access_needed),
                indent = indent,
                main = false,
                summaryColor = if (granted) null else themeColor(ThemeSlot.WARNING),
                icon = RowIcon.DIRECTORY,
            ) { openAllFilesAccess() }
        }
    }

    /** Tap anywhere to copy the full token; Regenerate on the right warns before invalidating copies. */
    private fun addTokenRow(indent: Int) {
        val b = ItemThemeTokenBinding.inflate(layoutInflater, body, false)
        b.themeTokenLabel.text = getString(R.string.automation_token)
        b.themeTokenLabel.setTextColor(themeColor(ThemeSlot.PAGE_LABEL))
        b.themeTokenLabel.setRowIcon(RowIcon.SECRET)
        b.themeTokenValue.text = abbreviateToken(config.automationToken)
        b.themeTokenValue.setTextColor(themeColor(ThemeSlot.PAGE_VALUE))
        b.themeTokenValue.alpha = 1f // the stock value style dims by alpha; nothing here is faded
        b.themeTokenRegenerate.text = getString(R.string.automation_token_regenerate)
        b.themeTokenRegenerate.setTextColor(uiColor(UiRole.BUTTON_TEXT))
        // an action, so a (thin) pill — never a bare clickable word
        b.themeTokenRegenerate.background =
            pillDrawable(uiColor(UiRole.BUTTON_FRAME), uiColor(UiRole.BUTTON_GROUND), main = false)
        b.root.setOnClickListener { copyAutomationToken() }
        b.themeTokenRegenerate.setOnClickListener {
            ConfirmationDialog(
                activity = this,
                message = getString(R.string.automation_token_regenerate_warning),
                positive = R.string.automation_token_regenerate,
                negative = org.fossify.commons.R.string.cancel,
                destructive = true,
            ) {
                b.themeTokenValue.text = abbreviateToken(config.regenerateAutomationToken())
                toast(R.string.automation_token_regenerated)
            }
        }
        indentRow(b.root, indent)
        body.addView(b.root)
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

    // The off switch is a commons role and is listed with the other switch roles, in Switches.
    private fun slotRowsFor(group: ThemeGroup, indent: Int) {
        ThemeSlot.entries.filter { it.group == group && it != ThemeSlot.SWITCH_OFF }.forEach { addSlotRow(it, indent) }
    }

    // The row's icon, before its label and in the colour of what the row does.
    private fun TextView.setRowIcon(icon: RowIcon) {
        val density = resources.displayMetrics.density
        val size = (ROW_ICON_DP * density).toInt()
        val drawable = resources.getColoredDrawableWithColor(icon.res, themeColor(icon.role))
        drawable.setBounds(0, 0, size, size)
        setCompoundDrawablesRelative(drawable, null, null, null)
        compoundDrawablePadding = (ROW_ICON_GAP_DP * density).toInt()
    }

    // How far a line beneath a label is moved in, so that it starts under the label's text and not under its icon.
    private fun rowIconInset() = ((ROW_ICON_DP + ROW_ICON_GAP_DP) * resources.displayMetrics.density).toInt()

    // A text element gets the rich color+font+weight+size+sample block; everything else a colour swatch.
    private fun addSlotRow(slot: ThemeSlot, indent: Int) {
        if (slot.hasFont) addTextSlot(slot, indent) else addColorRow(slot, indent)
    }

    /**
     * The caller block on the call screen (and on the car one): the order box that ticks which of the
     * caller's fields it shows, in what order and on which lines; the nickname switch; and — rebuilt as
     * the box is ticked — the font / weight / size / colour of each field that is actually shown. The
     * same editor renrakusaki carries under "Contacts' list", on this app's own field catalog.
     */
    private fun addCallerFieldsSubgroup(primaryColor: Int, indent: Int) {
        addSubgroup(R.string.theme_subgroup_caller_details, primaryColor)
        addSwitchRow(
            R.string.call_prefer_nickname,
            config.callScreenPreferNickname,
            indent,
            getString(R.string.call_prefer_nickname_description),
            RowIcon.CONTACT,
        ) { config.callScreenPreferNickname = it }

        val entries = CallScreenConfig.parse(config.callScreenFields).toMutableList()
        val orderList = ItemThemeFieldsOrderBinding.inflate(layoutInflater, body, false).root
        orderList.setPaddingRelative(indent, orderList.paddingTop, orderList.paddingEnd, orderList.paddingBottom)
        orderList.layoutManager = LinearLayoutManager(this)
        val adapter = CallScreenFieldsAdapter(this, entries) {
            config.callScreenFields = CallScreenConfig.serialize(entries)
            rebuildCallFieldStyling(entries, indent)
        }
        orderList.adapter = adapter
        adapter.attachTo(orderList)
        body.addView(orderList)

        callFieldStylingContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        body.addView(callFieldStylingContainer)
        rebuildCallFieldStyling(entries, indent)
    }

    // One styling block per ticked field, in the order the caller block draws them.
    private fun rebuildCallFieldStyling(entries: List<CallFieldEntry>, indent: Int) {
        val container = callFieldStylingContainer ?: return
        container.removeAllViews()
        val shown = entries.filter { it.checked }
        if (shown.isEmpty()) {
            return
        }

        addSubgroup(R.string.theme_subgroup_caller_styling, themeColor(ThemeSlot.PAGE_SUBHEADING), container)
        shown.forEach { addTextSlot(it.field.slot, indent, container) }
    }

    private fun addSubgroup(@StringRes labelRes: Int, primaryColor: Int, parent: ViewGroup = body) {
        val sub = ItemThemeSubgroupBinding.inflate(layoutInflater, parent, false)
        sub.themeSubgroupLabel.text = getString(labelRes)
        sub.themeSubgroupLabel.setTextColor(primaryColor)
        sub.themeSubgroupRule.setBackgroundColor(primaryColor)
        sub.root.setPaddingRelative(subgroupIndent, sub.root.paddingTop, sub.root.paddingEnd, sub.root.paddingBottom)
        parent.addView(sub.root)
    }

    private fun addColorRow(slot: ThemeSlot, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        row.themeColorLabel.text = getString(slot.labelRes)
        row.themeColorLabel.setTextColor(themeColor(ThemeSlot.PAGE_LABEL))
        row.themeColorLabel.setRowIcon(RowIcon.COLOR)
        row.themeColorPreview.background = circleDrawable(themeColor(slot))
        row.root.background = rippleDrawable()
        row.root.setOnClickListener { openPicker(slot) }
        indentRow(row.root, indent)
        previews[slot] = row.themeColorPreview
        body.addView(row.root)
    }

    // A concrete text element: its colour, font family, weight, size and a live sample of all four.
    @Suppress("EmptyFunctionBlock") // SeekBar's start/stop-tracking callbacks are intentionally no-ops
    private fun addTextSlot(slot: ThemeSlot, indent: Int, parent: ViewGroup = body) {
        val b = ItemThemeTextBinding.inflate(layoutInflater, parent, false)
        b.themeTextLabel.text = getString(slot.labelRes)
        listOf(b.themeTextLabel, b.themeTextFontTitle, b.themeTextWeightTitle, b.themeTextSizeTitle)
            .forEach { it.setTextColor(themeColor(ThemeSlot.PAGE_LABEL)) }
        listOf(b.themeTextFontValue, b.themeTextWeightValue, b.themeTextSizeValue)
            .forEach { it.setTextColor(themeColor(ThemeSlot.PAGE_VALUE)) }
        // the element's own line sets its colour; the lines beneath set its font, weight and size
        b.themeTextLabel.setRowIcon(RowIcon.COLOR)
        b.themeTextFontTitle.setRowIcon(RowIcon.TEXT)
        b.themeTextWeightTitle.setRowIcon(RowIcon.WEIGHT)
        b.themeTextSizeTitle.setRowIcon(RowIcon.SIZE)
        b.themeTextSizeSeekbar.progressTintList = ColorStateList.valueOf(uiColor(UiRole.SLIDER_TRACK))
        b.themeTextSizeSeekbar.thumbTintList = ColorStateList.valueOf(uiColor(UiRole.SLIDER_THUMB))

        b.themeTextColorPreview.background = circleDrawable(themeColor(slot))
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
        tightenRow(b.themeTextColorRow)
        parent.addView(b.root)
    }

    private fun addDimenRow(dimen: ThemeDimen, indent: Int) {
        val row = ItemThemeDimenBinding.inflate(layoutInflater, body, false)
        row.themeDimenLabel.text = getString(dimen.labelRes)
        row.themeDimenLabel.setTextColor(themeColor(ThemeSlot.PAGE_LABEL))
        row.themeDimenLabel.setRowIcon(RowIcon.SIZE)
        row.themeDimenValue.setTextColor(themeColor(ThemeSlot.PAGE_VALUE))
        row.root.background = rippleDrawable()
        row.themeDimenValue.text = dpLabel(themeDimenDp(dimen))
        row.root.setOnClickListener { openDimenPicker(dimen, row.themeDimenValue) }
        indentRow(row.root, indent)
        body.addView(row.root)
    }

    private fun addValueRow(
        @StringRes labelRes: Int,
        value: String,
        indent: Int,
        icon: RowIcon = RowIcon.VALUE,
        onClick: (TextView) -> Unit,
    ) = addValueRow(getString(labelRes), value, indent, null, icon, onClick)

    private fun addValueRow(
        title: String,
        value: String,
        indent: Int,
        valueColor: Int? = null,
        icon: RowIcon = RowIcon.VALUE,
        onClick: (TextView) -> Unit,
    ) {
        val row = ItemThemeValueBinding.inflate(layoutInflater, body, false)
        row.themeValueLabel.text = title
        row.themeValueLabel.setTextColor(themeColor(ThemeSlot.PAGE_LABEL))
        row.themeValueLabel.setRowIcon(icon)
        row.themeValue.text = value
        row.themeValue.setTextColor(valueColor ?: themeColor(ThemeSlot.PAGE_VALUE))
        row.root.background = rippleDrawable()
        row.root.setOnClickListener { onClick(row.themeValue) }
        indentRow(row.root, indent)
        body.addView(row.root)
    }

    /**
     * An action / navigation row: the outlined pill, only as wide as [title], holding it — thick and bold when [main],
     * with a › when it [opens] something — and [summary] beneath it, aligned with the label. Only the pill
     * takes the tap; the explanation beneath does not. Rows with a control of their own (a switch, a
     * swatch, a slider, a value) stay plain rows: their control is the affordance already.
     */
    private fun addActionRow(
        title: String,
        summary: String?,
        indent: Int,
        main: Boolean,
        opens: Boolean = true,
        labelColor: Int? = null,
        summaryColor: Int? = null,
        icon: RowIcon = RowIcon.SETTINGS,
        onClick: () -> Unit,
    ) {
        // a coloured action lends its colour to the frame and the › as well as to the label; any other
        // pill is drawn from the button roles
        val frame = labelColor ?: uiColor(UiRole.BUTTON_FRAME)
        val row = ItemThemeActionBinding.inflate(layoutInflater, body, false)
        row.themeActionPill.background = pillDrawable(frame, uiColor(UiRole.BUTTON_GROUND), main)
        row.themeActionLabel.text = title
        row.themeActionLabel.setTextColor(labelColor ?: uiColor(UiRole.BUTTON_TEXT))
        row.themeActionLabel.setTypeface(row.themeActionLabel.typeface, if (main) Typeface.BOLD else Typeface.NORMAL)
        row.themeActionLabel.setRowIcon(icon)
        row.themeActionChevron.setTextColor(frame)
        // the explanation starts under the label's text, not under its icon
        row.themeActionSummary.setPaddingRelative(rowIconInset(), 0, 0, 0)
        if (!opens) row.themeActionChevron.beGone()
        val explained = !summary.isNullOrEmpty()
        if (explained) {
            row.themeActionSummary.text = summary
            row.themeActionSummary.setTextColor(summaryColor ?: themeColor(ThemeSlot.SETTINGS_NOTE))
            row.themeActionRule.setBackgroundColor(themeColor(ThemeSlot.SETTINGS_NOTE))
            row.themeActionRule.beVisible()
        } else {
            row.themeActionSummary.beGone()
        }
        row.themeActionPill.setOnClickListener { onClick() }
        // only a hair of space around the pill — its own inner padding already makes it easy to hit
        val vertical = (PILL_ROW_PADDING_DP * resources.displayMetrics.density).toInt()
        row.root.setPaddingRelative(ruledStart(indent, explained), vertical, row.root.paddingEnd, vertical)
        body.addView(row.root)
    }

    private fun pillDrawable(accent: Int, background: Int, main: Boolean): Drawable {
        val density = resources.displayMetrics.density
        val face = GradientDrawable().apply {
            cornerRadius = PILL_RADIUS_DP * density
            setColor(background)
            setStroke(((if (main) PILL_STROKE_MAIN_DP else PILL_STROKE_DP) * density).toInt().coerceAtLeast(1), accent)
        }
        return RippleDrawable(ColorStateList.valueOf(accent.adjustAlpha(PILL_RIPPLE_ALPHA)), face, null)
    }

    /** Opened from Settings: go back to it. Opened from the main screen's 設 or menu: open Settings over this page. */
    private fun openAppSettings() {
        if (intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)) {
            finish()
        } else {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun addSwitchRow(
        @StringRes labelRes: Int,
        checked: Boolean,
        indent: Int,
        description: String? = null,
        icon: RowIcon = RowIcon.TOGGLE,
        onToggle: (Boolean) -> Unit,
    ) {
        val row = ItemThemeSwitchBinding.inflate(layoutInflater, body, false)
        row.themeSwitchLabel.text = getString(labelRes)
        row.themeSwitchLabel.setTextColor(uiColor(UiRole.SWITCH_LABEL))
        row.themeSwitchLabel.setRowIcon(icon)
        row.themeSwitchDescription.setPaddingRelative(rowIconInset(), row.themeSwitchDescription.paddingTop, 0, 0)
        row.root.background = rippleDrawable()
        row.themeSwitch.isChecked = checked
        row.themeSwitch.minimumHeight = 0
        // An explained item is one block: bold label, dim-gold description, dim-gold rule on its left.
        val explained = !description.isNullOrEmpty()
        if (explained) {
            row.themeSwitchLabel.setTypeface(row.themeSwitchLabel.typeface, Typeface.BOLD)
            row.themeSwitchDescription.text = description
            row.themeSwitchDescription.setTextColor(themeColor(ThemeSlot.SETTINGS_NOTE))
            row.themeSwitchDescription.beVisible()
            row.themeSwitchRule.setBackgroundColor(themeColor(ThemeSlot.SETTINGS_NOTE))
            row.themeSwitchRule.beVisible()
        }
        row.root.setOnClickListener {
            row.themeSwitch.toggle()
            onToggle(row.themeSwitch.isChecked)
        }
        indentRow(row.root, indent)
        row.root.apply { setPaddingRelative(ruledStart(indent, explained), paddingTop, paddingEnd, paddingBottom) }
        body.addView(row.root)
    }

    /**
     * The start padding of a row that may carry the left rule: the rule and its gap hang in the indent,
     * so the row's text stays at the same depth as an unexplained neighbour's.
     */
    private fun ruledStart(indent: Int, ruled: Boolean): Int {
        if (!ruled) return indent
        val inset = resources.getDimensionPixelSize(R.dimen.theme_note_rule_width) +
            resources.getDimensionPixelSize(R.dimen.theme_note_rule_gap)
        return (indent - inset).coerceAtLeast(0)
    }

    // The badge fill for one SIM. "Default" in the picker drops the override, so the row falls back to
    // the fork colour for that SIM (1 red, 2 blue) rather than to whatever the system happens to use.
    private fun addSimColorRow(simId: Int, indent: Int) {
        val row = ItemThemeColorBinding.inflate(layoutInflater, body, false)
        val labelRes = if (simId == 1) R.string.sim_1_color else R.string.sim_2_color
        row.themeColorLabel.text = getString(labelRes)
        row.themeColorLabel.setTextColor(themeColor(ThemeSlot.PAGE_LABEL))
        row.themeColorLabel.setRowIcon(RowIcon.SIM)
        row.themeColorPreview.background = circleDrawable(simColor(simId))
        row.root.background = rippleDrawable()
        row.root.setOnClickListener {
            AlphaColorPickerDialog(this, simColor(simId), addDefaultColorButton = true) { wasPositive, color ->
                setSimColor(simId, if (wasPositive) color else SIM_COLOR_UNSET)
                row.themeColorPreview.background = circleDrawable(simColor(simId))
            }
        }
        indentRow(row.root, indent)
        body.addView(row.root)
    }

    /**
     * Every row on the page passes through here, so this is also where the vertical rhythm is set: the
     * indent becomes the row's absolute start padding (the cascade level, not an offset from whatever
     * the commons style happened to use), and the style's 20dp above and below a single line of text —
     * 40dp per row, which on a page this long is screens of whitespace — is replaced with
     * [ROW_PADDING_VERTICAL_DP].
     */
    private fun indentRow(view: View, indent: Int) {
        view.setPaddingRelative(indent, view.paddingTop, view.paddingEnd, view.paddingBottom)
        tightenRow(view)
    }

    /** Minimal vertical padding and no minimum height, keeping the row's horizontal geometry. */
    private fun tightenRow(view: View) {
        val vertical = (ROW_PADDING_VERTICAL_DP * resources.displayMetrics.density).toInt()
        view.setPaddingRelative(view.paddingStart, vertical, view.paddingEnd, vertical)
        view.minimumHeight = 0
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

            if (slot.repaintsPage()) {
                // these cascade into the chrome, the page itself and every inheriting preview
                recreate()
            } else {
                previews[slot]?.background = circleDrawable(themeColor(slot))
            }
        }
    }

    // A role, a colour the page itself is drawn with, or the colour of its row icons
    private fun ThemeSlot.repaintsPage() = isFoundation || role != null || group == ThemeGroup.PAGE ||
        this == ThemeSlot.WARNING || name.startsWith("MENU_ROLE")

    private fun openTextColorPicker(slot: ThemeSlot, b: ItemThemeTextBinding) {
        AlphaColorPickerDialog(this, themeColor(slot), addDefaultColorButton = true) { wasPositive, color ->
            if (wasPositive) setThemeColor(slot, color) else resetThemeColor(slot)
            b.themeTextColorPreview.background = circleDrawable(themeColor(slot))
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
