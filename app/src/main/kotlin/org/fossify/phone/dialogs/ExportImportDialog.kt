package org.fossify.phone.dialogs

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.helpers.SettingsExport
import java.util.Date

// The panel's own metrics. It is a dialog, not the page, so it steps in tighter than the 18dp page
// cascade — enough to read a sub-option as nested under its category, no more.
private const val BOX_CORNER_DP = 16
private const val BOX_STROKE_DP = 2
private const val CHILD_INDENT_DP = 24
private const val PILL_CORNER_DP = 50
private const val PILL_STROKE_DP = 1.5f

// Unset folder / no backup yet is a warning, not a value: it reads red until 白い熊 fixes it, and the
// same red is used for the matching row on the UI page.
const val EXPORT_WARN_COLOR = 0xFFFF5252.toInt()

/**
 * The Export / Import panel — the single place this app is backed up and restored from.
 *
 * The layout is the sister apps' (Kōjiki's export sheet, kxkb's export page): one bordered rounded box
 * carrying a centred title, a short description, a bordered tappable folder box, the last-export line,
 * then the category checklist with sub-options indented under their parent, and finally an ArcaneChat
 * button bar — round pills, Cancel alone on the left, Import and Export grouped on the right.
 *
 * The folder and the checklist are read on every [refresh]: opening the panel scans the configured
 * folder for the newest backup, so 白い熊 sees when this app was last saved before deciding to save it
 * again. An unset folder shows red here and on the page; once set, it reads in the accent colour.
 *
 * The panel holds no export logic — [SettingsExport] is the one engine, and the activity drives it (it
 * owns the storage-access launchers). What lands back here is only the outcome: [showResultDialog]
 * draws the bordered result box, and a successful run closes the whole chain.
 */
// Built in code rather than XML because the panel is one bordered surface whose colours follow the
// live theme; that makes it dp-literal dense, and naming each padding would obscure rather than
// explain. The handful of numbers that carry meaning are the constants above.
@Suppress("MagicNumber", "TooManyFunctions")
class ExportImportDialog(
    private val activity: SimpleActivity,
    private val selected: MutableSet<SettingsExport.Item>,
    private val onPickFolder: () -> Unit,
    private val onExport: () -> Unit,
    private val onImport: () -> Unit,
) {
    private val accent = activity.getProperPrimaryColor()
    private val background = activity.getProperBackgroundColor()
    private val textColor = activity.getProperTextColor()

    private var dialog: AlertDialog? = null
    private var folderValue: TextView? = null
    private var statusLine: TextView? = null
    private val rows = HashMap<SettingsExport.Item, CheckBox>()

    fun show() {
        val view = buildView()
        dialog = activity.getAlertDialogBuilder().setView(view).create().apply {
            show()
            // The bordered box IS the surface; the platform window behind it must not draw its own.
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        refresh()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /** Re-read the configured folder and the newest backup in it. */
    fun refresh() {
        val dir = SettingsExport.configuredDir(activity)
        folderValue?.text = dir?.name ?: activity.getString(R.string.eim_dir_unset)
        folderValue?.setTextColor(if (dir == null) EXPORT_WARN_COLOR else accent)

        val last = if (dir == null) null else SettingsExport.lastExportTime(activity)
        val warn = last == null
        val message = when {
            dir == null -> activity.getString(R.string.eim_last_no_dir)
            last == null -> activity.getString(R.string.eim_last_never)
            else -> activity.getString(R.string.eim_last_at, formatTimestamp(last))
        }
        statusLine?.text = message
        statusLine?.setTextColor(if (warn) EXPORT_WARN_COLOR else textColor)
        statusLine?.alpha = if (warn) 1f else 0.8f
    }

    private fun formatTimestamp(time: Long): String {
        val date = Date(time)
        return DateFormat.getDateFormat(activity).format(date) + " " + DateFormat.getTimeFormat(activity).format(date)
    }

    // ---- the panel ------------------------------------------------------------------------------

    private fun buildView(): View {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            background = borderedBox(BOX_CORNER_DP)
        }

        box.addView(heading(activity.getString(R.string.eim_title)))
        box.addView(caption(activity.getString(R.string.eim_hint)).apply {
            alpha = 0.85f
            setPadding(0, 0, 0, dp(10))
        })
        box.addView(folderBox())
        statusLine = caption("", sizeSp = 14f).apply { setPadding(dp(2), 0, 0, dp(8)) }
        box.addView(statusLine)

        box.addView(divider())
        box.addView(selectAllRow())
        SettingsExport.Item.listed.forEach { box.addView(itemRow(it)) }
        box.addView(divider(topGap = 8))
        box.addView(buttonBar())

        return ScrollView(activity).apply {
            clipToPadding = false
            // Inset the box so its border shows on every side.
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(
                box,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    /** The folder box: a bordered, clearly tappable box — a small label over the bold value. */
    private fun folderBox(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = borderedBox(cornerDp = 10)
        setOnClickListener { onPickFolder() }
        addView(caption(activity.getString(R.string.eim_dir), sizeSp = 12f, color = accent))
        folderValue = TextView(activity).apply {
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        addView(folderValue)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6); bottomMargin = dp(6) }
    }

    private fun selectAllRow(): CheckBox = checkbox(activity.getString(R.string.eim_select_all), bold = true).apply {
        isChecked = selected.containsAll(SettingsExport.Item.entries.toSet())
        setOnClickListener {
            val on = isChecked
            SettingsExport.Item.entries.forEach { item ->
                if (on) selected.add(item) else selected.remove(item)
                rows[item]?.isChecked = on
            }
        }
    }

    /** A category, or one of its parts indented under it and following its parent's toggle. */
    private fun itemRow(item: SettingsExport.Item): CheckBox =
        checkbox(activity.getString(item.labelRes)).apply {
            isChecked = item in selected
            if (!item.isTopLevel) {
                setPadding(paddingStart + dp(CHILD_INDENT_DP), paddingTop, paddingEnd, paddingBottom)
            }
            setOnClickListener {
                val on = isChecked
                if (on) selected.add(item) else selected.remove(item)
                item.children.forEach { child ->
                    if (on) selected.add(child) else selected.remove(child)
                    rows[child]?.isChecked = on
                }
            }
            rows[item] = this
        }

    /** The ArcaneChat button bar: Cancel alone on the left, Import + Export grouped on the right. */
    private fun buttonBar(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, dp(14), 0, 0)
        addView(pill(activity.getString(org.fossify.commons.R.string.cancel)) { dismiss() })
        addView(View(activity), LinearLayout.LayoutParams(0, 0, 1f))
        addView(pill(activity.getString(R.string.eim_import)) { onImport() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
        })
        addView(pill(activity.getString(R.string.eim_export)) { onExport() })
    }

    // ---- the result box -------------------------------------------------------------------------

    /**
     * The "finished" box: the same bordered surface as the panel, in the accent colour, with its
     * actions as pills on the right. [buttons] are (label, action) pairs in left-to-right order; the
     * box is not cancellable, so the outcome is always acknowledged deliberately.
     */
    fun showResultDialog(title: String, message: String, buttons: List<Pair<String, () -> Unit>>) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = borderedBox(BOX_CORNER_DP)
        }
        box.addView(heading(title).apply { gravity = Gravity.START })
        box.addView(caption(message, sizeSp = 14f, color = accent).apply { setPadding(0, dp(10), 0, 0) })

        val result = activity.getAlertDialogBuilder()
            .setView(ScrollView(activity).apply { setPadding(dp(10), dp(10), dp(10), dp(10)); addView(box) })
            .setCancelable(false)
            .create()

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            clipChildren = false
            setPadding(0, dp(16), 0, 0)
        }
        buttons.forEachIndexed { index, (label, action) ->
            bar.addView(pill(label) { result.dismiss(); action() }.also {
                if (index < buttons.lastIndex) (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(10)
            })
        }
        box.addView(bar)

        result.show()
        result.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    // ---- view builders --------------------------------------------------------------------------

    private fun borderedBox(cornerDp: Int) = GradientDrawable().apply {
        setColor(background)
        setStroke(dp(BOX_STROKE_DP), accent)
        cornerRadius = dp(cornerDp).toFloat()
    }

    private fun heading(text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(accent)
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        gravity = Gravity.CENTER
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun caption(text: String, sizeSp: Float = 13f, color: Int = textColor) = TextView(activity).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun checkbox(label: String, bold: Boolean = false) = CheckBox(activity).apply {
        text = label
        setTextColor(textColor)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        buttonTintList = ColorStateList.valueOf(accent)
        setPadding(dp(8), dp(7), 0, dp(7))
    }

    private fun divider(topGap: Int = 0) = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            .apply { topMargin = dp(topGap) }
        setBackgroundColor(accent)
        alpha = 0.4f
    }

    /** An ArcaneChat-style round pill: background fill, a thin accent stroke, accent text and ripple. */
    private fun pill(label: String, onClick: () -> Unit) = Button(activity).apply {
        text = label
        isAllCaps = false
        setTextColor(accent)
        background = RippleDrawable(
            ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x33000000),
            GradientDrawable().apply {
                setColor(this@ExportImportDialog.background)
                setStroke((PILL_STROKE_DP * activity.resources.displayMetrics.density).toInt(), accent)
                cornerRadius = dp(PILL_CORNER_DP).toFloat()
            },
            null
        )
        // Explicit padding and zeroed minimums so the rounded stroke is never clipped at the view edge.
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        stateListAnimator = null
        setPadding(dp(20), dp(8), dp(20), dp(8))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
