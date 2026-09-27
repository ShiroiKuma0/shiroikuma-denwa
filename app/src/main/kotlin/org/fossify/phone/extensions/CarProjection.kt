package org.fossify.phone.extensions

import android.content.Context
import android.net.Uri

/**
 * Whether the phone is projecting to a car right now — i.e. whether Android Auto, not this app, is
 * holding the in-call UI.
 *
 * Android Auto publishes this through the androidx CarConnection provider
 * (`com.google.android.apps.auto.components.projectionstate.ProjectionStateProvider`), which any app
 * may read: one row, one column, 0 = not connected, 1 = Android Automotive OS, 2 = projection. We
 * query it directly rather than pulling in `androidx.car.app:app` for a single integer. The manifest
 * `<queries>` entry for the gearhead package is what makes the provider visible to us on API 30+;
 * without it this silently reads "not connected".
 *
 * Why it matters that this is known at dial time: three seconds later the car's own screen owns the
 * foreground, and a backgrounded app may start neither a foreground service nor an activity. So the
 * decision to put our call screen up has to be taken while the tap that placed the call still makes
 * us the foreground app. See [org.fossify.phone.helpers.CarCallMonitor].
 */
fun Context.isCarProjectionActive(): Boolean {
    return try {
        contentResolver.query(CAR_CONNECTION_URI, arrayOf(CAR_CONNECTION_STATE), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(CAR_CONNECTION_STATE)
                cursor.moveToFirst() && column >= 0 && cursor.getInt(column) != CAR_NOT_CONNECTED
            } ?: false
    } catch (ignored: Exception) {
        // no Android Auto installed, provider not visible, or it threw — either way, not in a car
        false
    }
}

private val CAR_CONNECTION_URI: Uri = Uri.parse("content://androidx.car.app.connection")
private const val CAR_CONNECTION_STATE = "CarConnectionState"
private const val CAR_NOT_CONNECTED = 0
