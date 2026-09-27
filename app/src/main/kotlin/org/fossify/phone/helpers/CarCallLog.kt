package org.fossify.phone.helpers

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A breadcrumb trail for the car-call screen, because on this phone nothing else can be read.
 *
 * EMUI throttles `logcat` to error-level system lines — a capture across every buffer shows not one
 * line from an ordinary app — so the usual way of finding out what happened during a drive does not
 * exist. This writes a handful of one-line facts to
 * `/sdcard/Android/data/<package>/files/carcall.log`, which `adb pull` reaches without root.
 *
 * Deliberately tiny and self-limiting: a few dozen bytes per call, truncated at [MAX_BYTES], and
 * silent about anything it cannot do. It records decisions, never call content beyond the number
 * already visible in the call log.
 */
object CarCallLog {
    private const val FILE_NAME = "carcall.log"
    private const val MAX_BYTES = 64 * 1024L

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun write(context: Context, line: String) {
        try {
            val dir = context.getExternalFilesDir(null) ?: return
            val file = File(dir, FILE_NAME)
            if (file.length() > MAX_BYTES) {
                file.delete()
            }

            file.appendText("${stamp.format(Date())}  $line\n")
        } catch (ignored: Exception) {
            // diagnostics must never be the reason a call screen fails to appear
        }
    }
}
