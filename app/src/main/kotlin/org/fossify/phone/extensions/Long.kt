package org.fossify.phone.extensions

import org.fossify.commons.extensions.toDayCode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
private fun Int.toKanjiNumeral(): String {
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
