package org.fossify.phone.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.telecomManager
import org.fossify.phone.R
import org.fossify.phone.activities.CarCallActivity
import org.fossify.phone.helpers.ACCEPT_CAR_CALL
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CarCallLog
import org.fossify.phone.helpers.CarCallMonitor
import org.fossify.phone.helpers.END_CAR_CALL
import org.fossify.phone.helpers.NoCall
import org.fossify.phone.helpers.getCallContactByNumber
import org.fossify.phone.models.CallContact
import org.fossify.phone.receivers.CallActionReceiver
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owns a call that this app can see but does not hold — see [CarCallMonitor] for how we get here.
 *
 * It exists for three reasons. It keeps a foreground notification up for the whole call, which is
 * the one thing that is reachable no matter what covers the screen; it keeps the process alive so
 * [CarCallActivity] has something to observe; and it is the single place that knows how to drive a
 * call we have no [android.telecom.Call] for.
 *
 * Call state comes from the PHONE_STATE broadcast, with a slow poll behind it so a missed broadcast
 * cannot strand the screen on a call that has already ended.
 */
class CarCallService : Service() {
    companion object {
        private const val EXTRA_NUMBER = "car_call_number"
        private const val EXTRA_SIM_SLOT = "car_call_sim_slot"
        private const val EXTRA_INCOMING = "car_call_incoming"

        // 42 is the ongoing-call notification CallNotificationManager posts; ours must not collide,
        // because both can briefly exist while projection starts or ends mid-call.
        private const val NOTIFICATION_ID = 43
        private const val CHANNEL_ID = "car_call"

        // request codes 0 and 1 belong to CallNotificationManager's accept/decline intents
        private const val END_CALL_CODE = 2
        private const val ACCEPT_CALL_CODE = 3
        private const val POLL_MS = 2000L

        // We are started the instant the call is placed, so Telecom may not have it yet. Until we
        // have seen a call at least once, "no call" means "not yet", not "over".
        private const val STARTUP_GRACE_MS = 8000L

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var number: String? = null
            private set

        @Volatile
        var contactName: String? = null
            private set

        /**
         * Everything the lookup found about the caller, for the caller block on [CarCallActivity] to
         * draw as configured. Null until the lookup answers — the block then has only the raw number.
         */
        @Volatile
        var callContact: CallContact? = null
            private set

        /** The number as the app formats it for display, once the contact lookup has run. */
        @Volatile
        var displayNumber: String? = null
            private set

        @Volatile
        var contactPhotoUri: String? = null
            private set

        @Volatile
        var simSlot = 0
            private set

        @Volatile
        var isRinging = false
            private set

        /** SystemClock.elapsedRealtime() base for the chronometer; 0 while still ringing. */
        @Volatile
        var connectedAt = 0L
            private set

        private val listeners = CopyOnWriteArraySet<Runnable>()

        fun addListener(listener: Runnable) = listeners.add(listener)

        fun removeListener(listener: Runnable) = listeners.remove(listener)

        private fun notifyListeners() = listeners.forEach { it.run() }

        fun start(context: Context, number: String?, simSlot: Int, incoming: Boolean) {
            if (isRunning) {
                return
            }

            val intent = Intent(context, CarCallService::class.java).apply {
                putExtra(EXTRA_NUMBER, number)
                putExtra(EXTRA_SIM_SLOT, simSlot)
                putExtra(EXTRA_INCOMING, incoming)
            }

            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException on API 31+, i.e. we were not foreground
                CarCallLog.write(context, "startForegroundService REFUSED: ${e.javaClass.simpleName}")
            } catch (e: SecurityException) {
                CarCallLog.write(context, "startForegroundService REFUSED: ${e.javaClass.simpleName}")
            }
        }

        fun stop(context: Context) {
            if (!isRunning) {
                return
            }

            try {
                context.stopService(Intent(context, CarCallService::class.java))
            } catch (ignored: Exception) {
            }
        }

        /**
         * Ends the call the only way an app without a [android.telecom.Call] can. Deprecated since
         * API 29 and refused for anyone but the default dialer — which we are, or we would not be
         * bound as an InCallService in the first place. Returns false if the platform said no, and
         * the screen says so out loud rather than pretending the tap worked.
         */
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        fun endCall(context: Context): Boolean = try {
            context.telecomManager.endCall()
        } catch (ignored: Exception) {
            false
        }

        @SuppressLint("MissingPermission")
        fun answerCall(context: Context): Boolean = try {
            context.telecomManager.acceptRingingCall()
            true
        } catch (ignored: Exception) {
            false
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false
    private var startedAt = 0L
    private var hasSeenCall = false

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_IDLE -> endSession()
                TelephonyManager.EXTRA_STATE_OFFHOOK -> markConnected()
            }
        }
    }

    // A missed broadcast would otherwise leave our screen sitting on a call that is long over, so
    // Telecom gets asked directly too. It also catches projection ending mid-call: Telecom rebinds
    // CallService, the real call screen takes over, and this one must get out of its way.
    private val poll = object : Runnable {
        override fun run() {
            val inCall = CarCallMonitor.isInCall(this@CarCallService)
            if (inCall) {
                hasSeenCall = true
            }

            when {
                // Telecom handed the call to us after all — the real call screen owns it now. This is
                // also how projection ending mid-call is noticed: unplug in the driveway, CallService
                // gets bound, and this screen gets out of the way.
                CallManager.getPhoneState() != NoCall -> {
                    CarCallLog.write(this@CarCallService, "stand down: CallService holds the call")
                    endSession()
                }

                inCall -> handler.postDelayed(this, POLL_MS)

                // no call, and we have had one: it ended
                hasSeenCall -> {
                    CarCallLog.write(this@CarCallService, "call ended")
                    endSession()
                }

                // no call yet: Telecom is still placing it, unless it never arrives at all
                SystemClock.elapsedRealtime() - startedAt > STARTUP_GRACE_MS -> {
                    CarCallLog.write(this@CarCallService, "no call arrived within the grace window")
                    endSession()
                }

                else -> handler.postDelayed(this, POLL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        number = intent.getStringExtra(EXTRA_NUMBER)
        simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, 0)
        isRinging = intent.getBooleanExtra(EXTRA_INCOMING, false)
        contactName = null
        displayNumber = null
        contactPhotoUri = null
        callContact = null
        connectedAt = if (isRinging) 0L else SystemClock.elapsedRealtime()
        startedAt = SystemClock.elapsedRealtime()
        hasSeenCall = false
        isRunning = true

        startInForeground()
        registerPhoneStateReceiver()
        handler.postDelayed(poll, POLL_MS)
        resolveContactName()

        // A service start is a background activity launch. Holding SYSTEM_ALERT_WINDOW exempts us,
        // and the notification carries a full-screen intent as the second route, so a refusal here
        // still leaves 白い熊 one tap from the screen.
        try {
            startActivity(CarCallActivity.getStartIntent(this))
            CarCallLog.write(this, "call screen started")
        } catch (e: SecurityException) {
            // a background activity launch the platform would not allow
            CarCallLog.write(this, "call screen REFUSED: ${e.javaClass.simpleName}")
        } catch (e: ActivityNotFoundException) {
            CarCallLog.write(this, "call screen REFUSED: ${e.javaClass.simpleName}")
        }

        notifyListeners()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(phoneStateReceiver)
            } catch (ignored: Exception) {
            }
            isReceiverRegistered = false
        }

        notificationManager.cancel(NOTIFICATION_ID)
        notifyListeners()
    }

    private fun registerPhoneStateReceiver() {
        if (isReceiverRegistered) {
            return
        }

        try {
            // a protected broadcast — only the system can send it, so EXPORTED is safe here
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            ContextCompat.registerReceiver(this, phoneStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            isReceiverRegistered = true
        } catch (ignored: Exception) {
        }
    }

    private fun markConnected() {
        if (!isRinging && connectedAt != 0L) {
            return
        }

        isRinging = false
        connectedAt = SystemClock.elapsedRealtime()
        updateNotification()
        notifyListeners()
    }

    private fun endSession() {
        if (!isRunning) {
            return
        }

        isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        notifyListeners()
    }

    private fun resolveContactName() {
        val wanted = number ?: return
        getCallContactByNumber(applicationContext, wanted) { contact ->
            if (number != wanted) {
                return@getCallContactByNumber
            }

            callContact = contact
            contactName = contact.name.takeIf { it.isNotEmpty() && it != contact.number }
            displayNumber = contact.number.takeIf { it.isNotEmpty() }
            contactPhotoUri = contact.photoUri.takeIf { it.isNotEmpty() }
            handler.post {
                if (isRunning) {
                    updateNotification()
                    notifyListeners()
                }
            }
        }
    }

    private fun startInForeground() {
        NotificationChannel(CHANNEL_ID, getString(R.string.car_call_notification_channel), IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }

        val notification = buildNotification()
        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: IllegalStateException) {
            // a phoneCall-typed foreground service is refused if the platform disagrees that a call
            // is up; the notification alone still gives a reachable hang-up
            CarCallLog.write(this, "startForeground REFUSED: ${e.javaClass.simpleName}")
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            CarCallLog.write(this, "startForeground REFUSED: ${e.javaClass.simpleName}")
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (!isRunning) {
            return
        }

        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (ignored: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, CarCallActivity.getStartIntent(this), PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = PendingIntent.getBroadcast(
            this,
            END_CALL_CODE,
            Intent(this, CallActionReceiver::class.java).setAction(END_CAR_CALL),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = contactName
            ?: displayNumber
            ?: number?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.unknown_caller)
        val status = when {
            isRinging -> R.string.is_calling
            else -> R.string.ongoing_call
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(title)
            .setContentText(getString(status))
            .setContentIntent(openIntent)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(!isRinging)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.end_call), endIntent).build()
            )

        if (isRinging) {
            val acceptIntent = PendingIntent.getBroadcast(
                this,
                ACCEPT_CALL_CODE,
                Intent(this, CallActionReceiver::class.java).setAction(ACCEPT_CAR_CALL),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.car_call_answer), acceptIntent).build()
            )
        }

        // The screen is what 白い熊 actually wants in the car, and a full-screen intent is the one
        // way a backgrounded app is allowed to raise it.
        builder.setFullScreenIntent(openIntent, true)
        return builder.build()
    }
}
