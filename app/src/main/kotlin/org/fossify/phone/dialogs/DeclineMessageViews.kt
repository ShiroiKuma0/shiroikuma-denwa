package org.fossify.phone.dialogs

import android.view.ViewGroup
import org.fossify.commons.extensions.pillDrawable
import android.app.Activity
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DimenRes
import org.fossify.phone.R
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.themeColor

/**
 * The views the decline-message dialogs are built from — shared by the call screen's picker and the
 * editor dialogs, so both look the same. Plain TextViews in explicit
 * theme colors: commons' updateTextColors leaves them alone, so a row keeps the primary color.
 */
private const val PILL_ROW_GAP_DP = 3
private const val PILL_ROW_SIDE_DP = 8

object DeclineMessageViews {
    private const val MAX_INPUT_LINES = 4

    fun px(activity: Activity, @DimenRes id: Int) = activity.resources.getDimensionPixelSize(id)

    private fun TextView.textSize(@DimenRes id: Int) =
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(id))

    private fun View.selectableBackground() {
        val tv = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        setBackgroundResource(tv.resourceId)
    }

    /** One tappable line per item, inside a scroll view capped at [maxHeightFraction] of the screen. */
    fun rowsView(activity: Activity, items: List<String>, maxHeightFraction: Float, onClick: (Int) -> Unit): View {
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { index, text ->
            rows.addView(rowView(activity, text) { onClick(index) })
        }
        return cappedScroll(activity, rows, maxHeightFraction)
    }

    /**
     * A row to tap — a message to send, an action to run: a full-width outlined pill, as the menus
     * draw their rows, so it never passes for plain text. [color] is the accent unless the row is
     * destructive, which lends its red to frame and label alike.
     */
    fun rowView(
        activity: Activity,
        text: String,
        color: Int = activity.themeColor(ThemeSlot.PRIMARY),
        onClick: () -> Unit,
    ) = TextView(activity).apply {
        this.text = text
        setTextColor(color)
        textSize(R.dimen.decline_row_text_size)
        val h = px(activity, R.dimen.decline_row_padding_horizontal)
        val v = px(activity, R.dimen.decline_row_padding_vertical)
        setPadding(h, v, h, v)
        background = activity.pillDrawable(color, activity.themeColor(ThemeSlot.BACKGROUND), main = false)
        val density = activity.resources.displayMetrics.density
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            val gap = (PILL_ROW_GAP_DP * density).toInt()
            val side = (PILL_ROW_SIDE_DP * density).toInt()
            setMargins(side, gap, side, gap)
        }
        setOnClickListener { onClick() }
    }

    /** "For <caller>" / "General" above a group of rows. */
    fun headerView(activity: Activity, text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(activity.themeColor(ThemeSlot.TEXT_SECONDARY))
        textSize(R.dimen.decline_header_text_size)
        val h = px(activity, R.dimen.decline_row_padding_horizontal)
        val top = px(activity, R.dimen.decline_header_padding_top)
        setPadding(h, top, h, px(activity, R.dimen.decline_header_padding_bottom))
    }

    fun messageView(activity: Activity, text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(activity.themeColor(ThemeSlot.TEXT))
        textSize(R.dimen.decline_body_text_size)
        val h = px(activity, R.dimen.decline_row_padding_horizontal)
        val v = px(activity, R.dimen.decline_row_padding_vertical)
        setPadding(h, v, h, v)
    }

    fun messageInput(activity: Activity, minLines: Int = 1) = EditText(activity).apply {
        hint = activity.getString(R.string.sk_decline_hint)
        setTextColor(activity.themeColor(ThemeSlot.PRIMARY))
        setHintTextColor(activity.themeColor(ThemeSlot.TEXT_SECONDARY))
        textSize(R.dimen.decline_body_text_size)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        this.minLines = minLines
        maxLines = MAX_INPUT_LINES
    }

    fun cappedScroll(activity: Activity, content: View, maxHeightFraction: Float): ScrollView {
        val maxHeight = (activity.resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
        return object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
            }
        }.apply { addView(content) }
    }
}
