package org.fossify.phone.helpers

import android.content.Context

/**
 * A breadcrumb trail for the car-call screen, because on this phone nothing else can be read: EMUI
 * throttles `logcat` to error-level system lines, so what happened during a drive cannot be found out
 * any other way.
 *
 * It is the "car" kind of [Diag] — written to `diag/denwa-car_<day>.txt` and only while its switch
 * under UI → Diagnostics is on (until 2026-11-19 it was an always-on `carcall.log` in the files root,
 * which EMUI's nightly clean-up deletes; [Diag] moves that file in). It records decisions, never call
 * content beyond the number already visible in the call log.
 */
object CarCallLog {
    @Suppress("UNUSED_PARAMETER") // the callers all have a context at hand, and Diag keeps its own
    fun write(context: Context, line: String) = Diag.log(Diag.Kind.CAR, line)
}
