package org.fossify.phone.dialogs

import org.fossify.commons.helpers.UiRole
import org.fossify.commons.helpers.uiColor
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.telecom.DisconnectCause
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.pillDrawable
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toImperialDateString
import org.fossify.phone.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.formatCallDuration
import org.fossify.phone.extensions.formatCallTime
import org.fossify.phone.extensions.simTextColor
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.models.CallStamp
import org.fossify.phone.models.RecentCall
import org.fossify.phone.views.SimBadgeView
import java.util.Locale

private const val BADGE_DP = 64
private const val ICON_DP = 20
private const val CAPTION_WIDTH_DP = 96
private const val NUMBER_MAX_SP = 22
private const val NUMBER_MIN_SP = 12
private const val MILLIS = 1000L
private const val GROUP = 3

/**
 * What a call was made through — opened by a tap on the SIM badge of its row in Recents.
 *
 * The head is the badge drawn large beside the SIM's own number and its operator; below it, in the
 * order the call happened, one line per fact its [CallStamp] holds, each with an icon coloured by
 * what the line is about. A line whose fact is not known is left out rather than shown empty, so a
 * call from before the stamps existed shows its head and its "Call" line and nothing more.
 *
 * Everything shown is the stamp's: the number, operator and badge are those of the card the call
 * used, however many cards the slot has held since.
 */
@Suppress("MagicNumber", "TooManyFunctions") // one surface built in code, a small builder per part
class SimBadgeDialog(private val activity: BaseSimpleActivity, private val call: RecentCall) {
    private val stamp = call.stamp ?: CallStamp()
    // A dialog of our own: its text and ground are the dialog roles, its plain icons the view role
    private val accent = activity.uiColor(UiRole.DIALOG_TEXT)
    private val background = activity.uiColor(UiRole.DIALOG_GROUND)
    private val plainIcon = activity.themeColor(ThemeSlot.MENU_ROLE_VIEW)
    private val noteColor = activity.themeColor(ThemeSlot.SETTINGS_NOTE)
    private val valueColor = activity.themeColor(ThemeSlot.CALL_LOG_NUMBER)
    private var dialog: AlertDialog? = null

    init {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
        }
        box.addView(head())
        box.addView(divider())
        addRows(box)
        box.addView(buttonBar())

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(ScrollView(activity).apply { addView(box) }, this) { dialog = it }
        }
    }

    // ---- head: the badge, the number, the operator ----

    private fun head(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(14))

        addView(
            SimBadgeView(activity).apply {
                setBadge(
                    digit = stamp.digit.takeIf { it > 0 } ?: call.simID,
                    color = if (stamp.digit > 0) stamp.color else call.simColor,
                    textColor = if (stamp.digit > 0) stamp.textColor else activity.simTextColor(),
                    bold = stamp.bold,
                )
            },
            LinearLayout.LayoutParams(dp(BADGE_DP), dp(BADGE_DP)).apply { marginEnd = dp(16) }
        )

        val lines = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        lines.addView(
            TextView(activity).apply {
                text = stamp.number.takeIf { it.isNotEmpty() }?.let(::formatSimNumber)
                    ?: activity.getString(R.string.sim_dialog_number_unknown)
                setTextColor(accent)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                maxLines = 1
                // one line whatever the width: the number shrinks before it breaks
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, NUMBER_MIN_SP, NUMBER_MAX_SP, 1, TypedValue.COMPLEX_UNIT_SP
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32))
        )
        lines.addView(
            TextView(activity).apply {
                val slot = stamp.slot.takeIf { it >= 0 }?.let { activity.getString(R.string.sim_dialog_slot, it + 1) }
                text = listOfNotNull(stamp.operator.takeIf { it.isNotEmpty() }, slot).joinToString(" · ")
                setTextColor(valueColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
        )
        addView(lines, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    // The menus' divider: an opaque darker accent, 70 % of the way from the background to the accent.
    private fun divider(): View = View(activity).apply {
        setBackgroundColor(activity.uiColor(UiRole.LIST_DIVIDER))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { bottomMargin = dp(8) }
    }

    // ---- the lines ----

    private fun addRows(box: LinearLayout) {
        box.addView(row(typeIcon(), typeColor(), R.string.sim_dialog_call, "${typeLabel()} · ${dateAndTime()}"))

        val answered = call.duration > 0
        if (stamp.ringMs >= 0) {
            val rang = (stamp.ringMs / MILLIS).toInt().formatCallDuration(activity)
            val text = when {
                answered -> activity.getString(R.string.sim_dialog_rang_answered, rang)
                call.type == Calls.OUTGOING_TYPE -> activity.getString(R.string.sim_dialog_rang_unanswered, rang)
                else -> rang
            }
            box.addView(row(icon(org.fossify.commons.R.drawable.ic_clock_vector), plainIcon, R.string.sim_dialog_rang, text))
        }
        if (answered) {
            val green = activity.themeColor(ThemeSlot.MENU_ROLE_CALL)
            box.addView(row(icon(R.drawable.ic_sim_dialog_timer), green, R.string.sim_dialog_talked, call.duration.formatCallDuration(activity)))
        }
        endLabel()?.let { box.addView(row(icon(R.drawable.ic_phone_down_vector), endColor(), R.string.sim_dialog_ended, it)) }
        addCircumstances(box)
    }

    private fun addCircumstances(box: LinearLayout) {
        networkLabel()?.let {
            val blue = activity.themeColor(ThemeSlot.MENU_ROLE_MESSAGE)
            box.addView(row(icon(R.drawable.ic_sim_dialog_network), blue, R.string.sim_dialog_network, it))
        }
        if (stamp.roaming) {
            val country = stamp.roamingCountry.takeIf { it.isNotEmpty() }?.let { Locale("", it.uppercase()).displayCountry }
            val where = listOfNotNull(stamp.roamingOperator.takeIf { it.isNotEmpty() }, country).joinToString(" · ")
                .ifEmpty { activity.getString(R.string.sim_dialog_roaming_yes) }
            val orange = activity.themeColor(ThemeSlot.MENU_ROLE_SHARE)
            box.addView(row(icon(R.drawable.ic_sim_dialog_globe), orange, R.string.sim_dialog_roaming, where))
        }
        AudioRoute.fromRoute(stamp.route)?.let {
            val cyan = activity.themeColor(ThemeSlot.MENU_ROLE_CONTACT)
            box.addView(row(icon(it.iconRes), cyan, R.string.sim_dialog_audio, activity.getString(it.stringRes)))
        }
        if (stamp.dbm != CallStamp.NO_DBM) {
            val bars = SignalBarsDrawable(stamp.level, accent, blend(background, accent, 0.25f))
            box.addView(row(bars, null, R.string.sim_dialog_signal, activity.getString(R.string.sim_dialog_signal_dbm, stamp.dbm)))
        }
        callerIdLabel()?.let {
            val hidden = stamp.callerId == CallStamp.CALLER_ID_HIDDEN
            val color = if (hidden) activity.themeColor(ThemeSlot.MENU_ROLE_BLOCK) else plainIcon
            val glyph = if (hidden) org.fossify.commons.R.drawable.ic_hide_vector else R.drawable.ic_sim_dialog_caller_id
            box.addView(row(icon(glyph), color, R.string.sim_dialog_caller_id, it))
        }
    }

    private fun row(icon: Drawable?, tint: Int?, captionRes: Int, value: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(5), 0, dp(5))
        addView(
            ImageView(activity).apply {
                setImageDrawable(icon?.mutate())
                tint?.let { setColorFilter(it) }
            },
            LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply { marginEnd = dp(12) }
        )
        addView(
            TextView(activity).apply {
                setText(captionRes)
                setTextColor(noteColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            },
            LinearLayout.LayoutParams(dp(CAPTION_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        addView(
            TextView(activity).apply {
                text = value
                setTextColor(valueColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun icon(res: Int): Drawable? = ContextCompat.getDrawable(activity, res)

    // ---- what each line says ----

    private fun typeLabel(): String = activity.getString(
        when (call.type) {
            Calls.OUTGOING_TYPE -> R.string.sim_dialog_outgoing
            Calls.MISSED_TYPE -> R.string.sim_dialog_missed
            Calls.REJECTED_TYPE -> R.string.sim_dialog_rejected
            Calls.BLOCKED_TYPE -> R.string.sim_dialog_blocked
            else -> R.string.sim_dialog_incoming
        }
    )

    private fun typeIcon(): Drawable? = icon(
        when (call.type) {
            Calls.OUTGOING_TYPE -> R.drawable.ic_call_made_vector
            Calls.MISSED_TYPE -> R.drawable.ic_call_missed_vector
            else -> R.drawable.ic_call_received_vector
        }
    )

    // the colours the row's own arrow has
    private fun typeColor(): Int = activity.themeColor(
        when {
            call.type == Calls.MISSED_TYPE -> ThemeSlot.CALL_LOG_MISSED
            call.type != Calls.OUTGOING_TYPE -> ThemeSlot.CALL_LOG_INCOMING
            call.duration > 0 -> ThemeSlot.CALL_LOG_OUTGOING
            else -> ThemeSlot.CALL_LOG_UNANSWERED
        }
    )

    private fun dateAndTime(): String {
        val date = if (activity.config.useImperialDate) {
            call.startTS.toImperialDateString()
        } else {
            call.startTS.formatDateOrTime(activity, hideTimeOnOtherDays = true, showCurrentYear = true, hideTodaysDate = false)
        }
        return "$date ${call.startTS.formatCallTime(activity)}"
    }

    private fun endLabel(): String? = when (stamp.endCode) {
        DisconnectCause.LOCAL -> R.string.sim_dialog_end_local
        DisconnectCause.REMOTE -> R.string.sim_dialog_end_remote
        DisconnectCause.BUSY -> R.string.sim_dialog_end_busy
        DisconnectCause.REJECTED -> R.string.sim_dialog_end_rejected
        DisconnectCause.MISSED -> R.string.sim_dialog_end_missed
        DisconnectCause.CANCELED -> R.string.sim_dialog_end_canceled
        DisconnectCause.ERROR -> R.string.sim_dialog_end_error
        DisconnectCause.RESTRICTED -> R.string.sim_dialog_end_restricted
        DisconnectCause.ANSWERED_ELSEWHERE -> R.string.sim_dialog_end_elsewhere
        else -> null
    }?.let(activity::getString)

    // green for a call that simply ended, the warning red for one that did not come about
    private fun endColor(): Int = when (stamp.endCode) {
        DisconnectCause.LOCAL, DisconnectCause.REMOTE, DisconnectCause.ANSWERED_ELSEWHERE ->
            activity.themeColor(ThemeSlot.MENU_ROLE_CALL)

        DisconnectCause.CANCELED -> accent
        else -> activity.themeColor(ThemeSlot.MENU_ROLE_DELETE)
    }

    private fun networkLabel(): String? = when (stamp.network) {
        CallStamp.NETWORK_WIFI -> activity.getString(R.string.sim_dialog_network_wifi)
        CallStamp.NETWORK_VOLTE -> "VoLTE"
        CallStamp.NETWORK_VONR -> "VoNR"
        CallStamp.NETWORK_3G -> "3G"
        CallStamp.NETWORK_2G -> "2G"
        else -> null
    }

    private fun callerIdLabel(): String? = when (stamp.callerId) {
        CallStamp.CALLER_ID_DEFAULT -> R.string.sim_dialog_caller_id_default
        CallStamp.CALLER_ID_SHOWN -> R.string.sim_dialog_caller_id_shown
        CallStamp.CALLER_ID_HIDDEN -> R.string.sim_dialog_caller_id_hidden
        else -> null
    }?.let(activity::getString)

    // ---- buttons ----

    private fun buttonBar(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(16), 0, 0)
        if (stamp.number.isNotEmpty()) {
            addView(pill(R.string.sim_dialog_copy, main = false) { activity.copyToClipboard(stamp.number) }.also {
                (it.layoutParams as LinearLayout.LayoutParams).marginEnd = dp(10)
            })
        }
        addView(pill(R.string.sim_dialog_close, main = true) { dialog?.dismiss() })
    }

    private fun pill(labelRes: Int, main: Boolean, onClick: () -> Unit) = TextView(activity).apply {
        setText(labelRes)
        setTextColor(activity.uiColor(UiRole.DIALOG_BUTTON_TEXT))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        if (main) setTypeface(typeface, Typeface.BOLD)
        background = activity.pillDrawable(
            activity.uiColor(UiRole.DIALOG_BUTTON_FRAME), activity.uiColor(UiRole.DIALOG_BUTTON_GROUND), main,
        )
        setPadding(dp(18), dp(9), dp(18), dp(9))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return ((a + (b - a) * t).toInt() and 0xFF) shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }

    companion object {
        /**
         * A SIM's own number for reading: "+420-123-456-789" — the country code, then the national
         * number in threes (a lone last digit joins the group before it). A number that is not
         * international is shown as stored.
         */
        fun formatSimNumber(raw: String): String {
            val parsed = runCatching { PhoneNumberUtil.getInstance().parse(raw, null) }.getOrNull()
            if (!raw.startsWith("+") || parsed == null) return raw
            val groups = parsed.nationalNumber.toString().chunked(GROUP).toMutableList()
            if (groups.size > 1 && groups.last().length == 1) {
                val last = groups.removeAt(groups.lastIndex)
                groups[groups.lastIndex] = groups.last() + last
            }
            return "+${parsed.countryCode}-${groups.joinToString("-")}"
        }
    }
}

/** Four rising bars, the first [level] of them lit — the signal as a status bar draws it. */
private class SignalBarsDrawable(private val level: Int, private val lit: Int, private val unlit: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Suppress("MagicNumber")
    override fun draw(canvas: Canvas) {
        val bars = 4
        val slot = bounds.width() / bars.toFloat()
        val barWidth = slot * 0.62f
        for (i in 0 until bars) {
            paint.color = if (i < level) lit else unlit
            val height = bounds.height() * (i + 1) / bars.toFloat()
            val left = bounds.left + i * slot
            canvas.drawRect(left, bounds.bottom - height, left + barWidth, bounds.bottom.toFloat(), paint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
