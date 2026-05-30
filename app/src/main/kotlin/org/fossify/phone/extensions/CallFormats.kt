package org.fossify.phone.extensions

import android.content.Context
import androidx.annotation.StringRes
import org.fossify.commons.extensions.formatSecondsToShortTimeString
import org.fossify.commons.extensions.formatTime
import org.fossify.phone.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// How a call's time-of-day is shown in the call log. JAPANESE (the default) renders kanji clock
// readings; the rest are plain masks. The stored Config value is the entry's ordinal.
enum class CallTimeFormat(@StringRes val labelRes: Int) {
    JAPANESE(R.string.call_format_japanese),
    SYSTEM(R.string.call_time_system),
    HOUR_24(R.string.call_time_24h),
    HOUR_12(R.string.call_time_12h),
}

// How a call's duration is shown. JAPANESE (the default) renders kanji wrapped in full-width
// parentheses; DIGITAL is the stock m:ss / h:mm:ss form. The stored Config value is the ordinal.
enum class CallDurationFormat(@StringRes val labelRes: Int) {
    JAPANESE(R.string.call_format_japanese),
    DIGITAL(R.string.call_duration_digital),
}

fun callTimeFormatOf(index: Int) = CallTimeFormat.entries.getOrElse(index) { CallTimeFormat.JAPANESE }

fun callDurationFormatOf(index: Int) = CallDurationFormat.entries.getOrElse(index) { CallDurationFormat.JAPANESE }

fun Long.formatCallTime(context: Context): String = when (callTimeFormatOf(context.config.callTimeFormat)) {
    CallTimeFormat.JAPANESE -> toJapaneseClockString()
    CallTimeFormat.SYSTEM -> formatTime(context)
    CallTimeFormat.HOUR_24 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
    CallTimeFormat.HOUR_12 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(this))
}

fun Int.formatCallDuration(context: Context): String = when (callDurationFormatOf(context.config.callDurationFormat)) {
    CallDurationFormat.JAPANESE -> toJapaneseDurationString()
    CallDurationFormat.DIGITAL -> context.formatSecondsToShortTimeString(this)
}
