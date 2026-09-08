package org.fossify.phone.dialogs

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity

private const val BOX_CORNER_DP = 16
private const val BOX_STROKE_DP = 2
private const val PILL_CORNER_DP = 50
private const val PILL_STROKE_DP = 1.5f

/**
 * The held-restore warning — the loudest thing this app draws.
 *
 * It exists because of one requirement (白い熊, 2026-09-08): a restore onto a new phone may not be
 * able to put the blocked numbers back at the moment it runs, and it must **never** be possible for
 * that to pass unnoticed. So this is not a toast and not a snackbar. It is a red-framed box that
 * cannot be dismissed by tapping outside or pressing back, carries the exact counts of what is
 * waiting, and comes back on every single launch until the last held row is in.
 *
 * The frame and heading are [EXPORT_WARN_COLOR] rather than the theme accent for the same reason the
 * unset-backup-folder row is: this is a warning about data, and it should not look like the rest of
 * the app until it stops being true.
 */
@Suppress("MagicNumber") // one bordered surface built in code, as the Export/Import panel is
class PendingRestoreDialog(
    private val activity: SimpleActivity,
    private val lines: List<String>,
    private val primaryLabel: String,
    private val onPrimary: () -> Unit,
    private val onLater: () -> Unit,
) {
    private val warn = EXPORT_WARN_COLOR
    private val background = activity.getProperBackgroundColor()
    private val textColor = activity.getProperTextColor()

    private var dialog: AlertDialog? = null

    fun show() {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            background = borderedBox()
        }

        box.addView(heading(activity.getString(R.string.pending_restore_title)))
        box.addView(body(activity.getString(R.string.pending_restore_body, lines.joinToString("\n"))))
        box.addView(buttonBar())

        dialog = activity.getAlertDialogBuilder()
            .setView(ScrollView(activity).apply { setPadding(dp(10), dp(10), dp(10), dp(10)); addView(box) })
            .setCancelable(false)
            .create()
            .apply {
                show()
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun buttonBar(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        clipChildren = false
        setPadding(0, dp(18), 0, 0)
        addView(pill(activity.getString(R.string.pending_restore_later)) { dismiss(); onLater() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(10)
        })
        addView(pill(primaryLabel) { dismiss(); onPrimary() })
    }

    private fun borderedBox() = GradientDrawable().apply {
        setColor(background)
        setStroke(dp(BOX_STROKE_DP), warn)
        cornerRadius = dp(BOX_CORNER_DP).toFloat()
    }

    private fun heading(text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(warn)
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        gravity = Gravity.CENTER
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun body(text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, dp(8), 0, 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun pill(label: String, onClick: () -> Unit) = Button(activity).apply {
        text = label
        isAllCaps = false
        setTextColor(warn)
        background = RippleDrawable(
            ColorStateList.valueOf((warn and 0x00FFFFFF) or 0x33000000),
            GradientDrawable().apply {
                setColor(this@PendingRestoreDialog.background)
                setStroke((PILL_STROKE_DP * activity.resources.displayMetrics.density).toInt(), warn)
                cornerRadius = dp(PILL_CORNER_DP).toFloat()
            },
            null
        )
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
