package org.fossify.phone.extensions

import org.fossify.commons.extensions.toDayCode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Sino-Japanese clock readings for the call log: e.g. 14:53 -> 午後二時五十三分, 9:30 -> 午前九時半.
// :00 drops the minute part, :30 becomes 半; noon/midnight get the special words 正午 / 正子.
fun Long.toJapaneseClockString(): String {
    val time = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    val hour = time.hour
    val minute = time.minute
    when {
        hour == 12 && minute == 0 -> return "正午"
        hour == 12 && minute == 30 -> return "正午半"
        hour == 0 && minute == 0 -> return "正子"
        hour == 0 && minute == 30 -> return "正子半"
    }
    val period = if (hour < 12) "午前" else "午後"
    val hour12 = when {
        hour == 0 -> 12
        hour <= 12 -> hour
        else -> hour - 12
    }
    val minutePart = when (minute) {
        0 -> ""
        30 -> "半"
        else -> "${minute.toKanjiNumeral()}分"
    }
    return "$period${hour12.toKanjiNumeral()}時$minutePart"
}

// A call duration in seconds as kanji wrapped in full-width parentheses: 6 -> （六秒）,
// 210 -> （三分半）, 403 -> （六分四十三秒）. A trailing 30s/30m becomes 半 of the unit above it.
fun Int.toJapaneseDurationString(): String {
    val core = if (this <= 0) {
        "零秒"
    } else {
        val hours = this / 3600
        val minutes = this % 3600 / 60
        val seconds = this % 60
        buildString {
            if (hours > 0) append("${hours.toKanjiNumeral()}時間")
            if (hours > 0 && minutes == 30 && seconds == 0) {
                append("半")
            } else {
                if (minutes > 0) append("${minutes.toKanjiNumeral()}分")
                if (minutes > 0 && seconds == 30) {
                    append("半")
                } else if (seconds > 0) {
                    append("${seconds.toKanjiNumeral()}秒")
                }
            }
        }
    }
    return "（$core）"
}

fun Long.getDayCode(): String {
    return toDayCode("yyyy-MM-dd") // format helps with sorting in call log
}

// Formats this epoch-millis timestamp as a Japanese imperial-era (和暦) date,
// e.g. 令和八年五月二十九日（金曜日）. Era is resolved from fixed boundaries so the
// output is fully controlled and does not depend on JapaneseEra API availability.
fun Long.toImperialDateString(): String {
    val date = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    val (era, baseYear) = when {
        !date.isBefore(LocalDate.of(2019, 5, 1)) -> "令和" to 2018
        !date.isBefore(LocalDate.of(1989, 1, 8)) -> "平成" to 1988
        !date.isBefore(LocalDate.of(1926, 12, 25)) -> "昭和" to 1925
        !date.isBefore(LocalDate.of(1912, 7, 30)) -> "大正" to 1911
        else -> "明治" to 1867
    }
    val eraYear = date.year - baseYear
    val year = if (eraYear == 1) "元" else eraYear.toKanjiNumeral() // first year is written 元年
    val weekday = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "月"
        DayOfWeek.TUESDAY -> "火"
        DayOfWeek.WEDNESDAY -> "水"
        DayOfWeek.THURSDAY -> "木"
        DayOfWeek.FRIDAY -> "金"
        DayOfWeek.SATURDAY -> "土"
        DayOfWeek.SUNDAY -> "日"
    }
    return "$era${year}年${date.monthValue.toKanjiNumeral()}月${date.dayOfMonth.toKanjiNumeral()}日（${weekday}曜日）"
}

// Converts 1..99 to everyday kanji numerals (e.g. 29 -> 二十九). Covers all era
// years (≤64), months (≤12) and days (≤31) that appear in a date.
internal fun Int.toKanjiNumeral(): String {
    if (this <= 0) return "〇"
    val digits = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    val tens = this / 10
    val ones = this % 10
    return buildString {
        when (tens) {
            0 -> {}
            1 -> append("十")
            else -> append(digits[tens]).append("十")
        }
        if (ones != 0) append(digits[ones])
    }
}
