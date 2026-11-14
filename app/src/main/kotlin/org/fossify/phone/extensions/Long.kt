package org.fossify.phone.extensions

import org.fossify.commons.extensions.toDayCode

// The Japanese clock and duration readings, the imperial date and the kanji numerals moved to
// shiroikuma-commons (sk13, extensions/CallFormats.kt), shared with renrakusaki's contact call history.

fun Long.getDayCode(): String {
    return toDayCode("yyyy-MM-dd") // format helps with sorting in call log
}
