package org.fossify.phone.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.fossify.commons.extensions.telecomManager
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isCarProjectionActive
import org.fossify.phone.services.CarCallService

/**
 * Puts our own call screen back on the phone when Telecom hands the in-call UI to somebody else.
 *
 * In practice that somebody is Android Auto: while it projects it calls
 * `UiModeManager.setAutomotiveProjection`, Telecom's CarModeTracker then binds *its* car-mode
 * service (`CarProjectionInCallService`) as the in-call UI and unbinds ours. The consequence is
 * total silence on the handset — [org.fossify.phone.services.CallService.onCallAdded] never fires,
 * so no CallActivity opens, no notification is posted, and [CallManager] holds no Call at all. 白い熊
 * dials, the app disappears, and nothing short of the car's own screen can hang up.
 *
 * The screen goes up **at dial time, not afterwards**. That is not a preference, it is the only
 * moment it can be done: while the tap that placed the call is still being handled we are the
 * foreground app and may start anything, whereas seconds later the car's screen owns the foreground
 * and a background app may start neither a foreground service nor an activity — both attempts are
 * refused, silently. An earlier build waited for the evidence and got its screen up once in five
 * calls for exactly that reason.
 *
 * So the question "does the car have this call?" is answered up front by asking Android Auto whether
 * it is projecting ([isCarProjectionActive]), and the screen withdraws later if it turns out to be
 * unnecessary — [CarCallService] stands down the moment [CallManager] reports Telecom handed the
 * call to us after all. Show first, withdraw if somebody else claims it.
 *
 * The old after-the-fact watch remains as a fallback for the day Android Auto stops publishing that
 * provider: as soon as Telecom reports a call, it gives it [SETTLE_MS] to arrive, and concludes from
 * its absence. It works only when we happen to still be foreground, which is why it is the fallback
 * and not the mechanism.
 *
 * Note what this cannot do: with no Call object there is nothing to `disconnect()`, so the screen
 * drives the call through TelecomManager instead (see [CarCallService.endCall]), and only the
 * controls that has — end, and answer — can be offered.
 */
object CarCallMonitor {
    // How long Telecom may take to bind our InCallService and deliver onCallAdded once the call
    // itself exists. Deliberately generous: being late merely delays the car screen by a couple of
    // seconds, while being early puts a second call screen on top of the normal one.
    private const val SETTLE_MS = 3000L

    // Arming happens as the call is placed, which is before Telecom has one. Give the call this long
    // to appear before concluding that there is nothing to watch.
    private const val WINDOW_MS = 12000L
    private const val TICK_MS = 500L

    private val handler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var pendingNumber: String? = null
    private var pendingSimSlot = 0
    private var pendingIncoming = false
    private var deadline = 0L
    private var inCallSince = 0L
    private var isTicking = false

    fun armOutgoing(context: Context, number: String?, simSlot: Int = 0) =
        arm(context, number, simSlot, incoming = false)

    fun armIncoming(context: Context, number: String?) =
        arm(context, number, simSlot = 0, incoming = true)

    private fun arm(context: Context, number: String?, simSlot: Int, incoming: Boolean) {
        val app = context.applicationContext
        if (!app.config.carCallScreen || CarCallService.isRunning) {
            return
        }

        // The whole point: commit now, while the caller is still foreground and allowed to start
        // things. Waiting to be sure would mean being sure from the background, where we can act on
        // nothing.
        if (app.isCarProjectionActive()) {
            CarCallLog.write(app, "projecting at dial time -> starting car call screen for ${number.orEmpty()}")
            CarCallService.start(app, number, simSlot, incoming)
            return
        }

        CarCallLog.write(app, "not projecting -> fallback watch armed for ${number.orEmpty()}")
        appContext = app
        pendingNumber = number
        pendingSimSlot = simSlot
        pendingIncoming = incoming
        deadline = SystemClock.elapsedRealtime() + WINDOW_MS
        inCallSince = 0L

        if (!isTicking) {
            isTicking = true
            handler.postDelayed(tick, TICK_MS)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val app = appContext
            if (app == null) {
                stopTicking()
                return
            }

            // Telecom bound us after all — the normal call screen is up and owns everything
            if (CallManager.getPhoneState() != NoCall) {
                stopTicking()
                return
            }

            val now = SystemClock.elapsedRealtime()
            if (isInCall(app)) {
                if (inCallSince == 0L) {
                    inCallSince = now
                }

                if (now - inCallSince >= SETTLE_MS) {
                    CarCallService.start(app, pendingNumber, pendingSimSlot, pendingIncoming)
                    stopTicking()
                    return
                }
            } else {
                inCallSince = 0L
            }

            if (now > deadline) {
                stopTicking()
            } else {
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    /**
     * Telecom gave us the call, so the normal screen takes over: drop the watch and tear down a car
     * screen that is already up. The second half matters when projection ends mid-call — unplugging
     * in the driveway rebinds us, and two call screens must not survive it.
     */
    fun standDown(context: Context) {
        stopTicking()
        CarCallService.stop(context.applicationContext)
    }

    private fun stopTicking() {
        handler.removeCallbacks(tick)
        isTicking = false
        inCallSince = 0L
    }

    fun isInCall(context: Context): Boolean = try {
        context.telecomManager.isInCall
    } catch (ignored: Exception) {
        false
    }
}
