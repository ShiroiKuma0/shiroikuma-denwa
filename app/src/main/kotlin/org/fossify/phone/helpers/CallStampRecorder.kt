package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telephony.TelephonyManager
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.extensions.simTextColor
import org.fossify.phone.models.CallStamp
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Takes a call's [CallStamp] while the call is live — the only moment most of it can be known.
 *
 * [org.fossify.phone.services.CallService] reports each call's arrival, state changes and removal,
 * and the audio route; everything slow (the subscription and telephony services are binder calls)
 * runs on one worker thread, in the order it was reported, so the stamp is written after its last
 * sample. The stamp is filed when the call disconnects and the log refreshed, so the row shows it at
 * once.
 *
 * Nothing here may ever disturb a call: every step is wrapped, and a failure only costs the stamp.
 */
object CallStampRecorder {
    private const val CLIR_HIDE = "#31#"
    private const val CLIR_SHOW = "*31#"

    private class Live(val created: Long, val number: String, val outgoing: Boolean, val startedAt: Long) {
        var card: SimCard? = null
        var ringMs = -1L
        var wasActive = false
        var endCode = -1
        var network = ""
        var roaming = false
        var roamingOperator = ""
        var roamingCountry = ""
        var route = -1
        var dbm = CallStamp.NO_DBM
        var level = -1
        var wifi = false
        var saved = false
    }

    private val live = ConcurrentHashMap<Call, Live>()
    private val worker = Executors.newSingleThreadExecutor()

    fun onCallAdded(context: Context, call: Call) = guarded {
        val details = call.details ?: return@guarded
        val entry = Live(
            created = details.creationTimeMillis,
            number = details.handle?.schemeSpecificPart.orEmpty(),
            outgoing = call.isOutgoing(),
            startedAt = SystemClock.elapsedRealtime(),
        )
        live[call] = entry
        Diag.log(Diag.Kind.CALLS) { "added ${if (entry.outgoing) "outgoing" else "incoming"} ${entry.number} created=${entry.created}" }
        val app = context.applicationContext
        val handle = details.accountHandle
        val wifi = details.hasProperty(Call.Details.PROPERTY_WIFI)
        worker.execute {
            guarded {
                CallStamps.noteCards(app)
                entry.card = CallStamps.cardFor(app, handle)
                entry.wifi = wifi
                sampleRadio(app, entry, withSignal = true)
                Diag.log(Diag.Kind.CALLS) { "account ${handle?.id} -> ${describe(entry)}" }
            }
        }
    }

    fun onStateChanged(context: Context, call: Call, state: Int) = guarded {
        val entry = live[call] ?: return@guarded
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        val details = call.details
        val handle = details?.accountHandle
        val wifi = details?.hasProperty(Call.Details.PROPERTY_WIFI) == true
        val endCode = details?.disconnectCause?.code ?: -1
        val route = CallManager.getCallAudioRoute()?.route ?: -1
        worker.execute {
            guarded {
                // A call placed without a SIM gets its account only once one is picked.
                if (entry.card == null) entry.card = CallStamps.cardFor(app, handle)
                entry.wifi = entry.wifi || wifi
                Diag.log(Diag.Kind.CALLS) { "state $state route=$route end=$endCode ${describe(entry)}" }
                when (state) {
                    Call.STATE_ACTIVE -> if (!entry.wasActive) {
                        entry.wasActive = true
                        entry.ringMs = now - entry.startedAt
                        entry.route = route
                        // only now is the voice bearer the one the call really runs on
                        sampleRadio(app, entry, withSignal = false)
                    }

                    Call.STATE_DISCONNECTED -> {
                        if (!entry.wasActive) entry.ringMs = now - entry.startedAt
                        entry.endCode = endCode
                        save(app, entry)
                    }
                }
            }
        }
    }

    /** The route the calls now play through; the last one before a call ends is the one kept. */
    fun onAudioRoute(route: Int) = guarded {
        worker.execute { live.values.filter { it.wasActive && !it.saved }.forEach { it.route = route } }
    }

    fun onCallRemoved(context: Context, call: Call) = guarded {
        val entry = live.remove(call) ?: return@guarded
        val app = context.applicationContext
        worker.execute { guarded { save(app, entry) } }
    }

    private fun save(context: Context, entry: Live) {
        if (entry.saved) return
        entry.saved = true
        val card = entry.card
        val digit = if (card != null) card.slot + 1 else 0
        val stamp = CallStamp(
            source = CallStamp.SOURCE_CALL,
            digit = digit,
            color = if (digit > 0) CallStamps.badgeColor(context, digit) else 0,
            textColor = context.simTextColor(),
            bold = context.config.simTextBold,
            number = card?.number.orEmpty(),
            operator = card?.operator.orEmpty(),
            slot = card?.slot ?: -1,
            endCode = entry.endCode,
            ringMs = entry.ringMs,
            network = if (entry.wifi) CallStamp.NETWORK_WIFI else entry.network,
            roaming = entry.roaming,
            roamingOperator = entry.roamingOperator,
            roamingCountry = entry.roamingCountry,
            route = entry.route,
            dbm = entry.dbm,
            level = entry.level,
            callerId = callerId(entry),
        )
        CallStamps.putFromCall(context, entry.created, entry.number, stamp)
        Diag.log(Diag.Kind.CALLS) { "stamp ${entry.created}|${entry.number} ${stamp.toJson()}" }
        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    private fun describe(entry: Live): String {
        val card = entry.card ?: return "no card"
        return "slot=${card.slot} sub=${card.subId} op=${card.operator} number=${if (card.number.isEmpty()) "none" else "known"}" +
                " net=${entry.network} roaming=${entry.roaming} dbm=${entry.dbm}"
    }

    // Whether our number went out with the call is only certain when the dialled string said so;
    // without a prefix the network's own setting decides, and no public API reads that.
    private fun callerId(entry: Live): Int = when {
        !entry.outgoing -> CallStamp.CALLER_ID_UNKNOWN
        entry.number.startsWith(CLIR_HIDE) -> CallStamp.CALLER_ID_HIDDEN
        entry.number.startsWith(CLIR_SHOW) -> CallStamp.CALLER_ID_SHOWN
        else -> CallStamp.CALLER_ID_DEFAULT
    }

    @SuppressLint("MissingPermission")
    private fun sampleRadio(context: Context, entry: Live, withSignal: Boolean) {
        val card = entry.card ?: return
        val telephony = context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(card.subId) ?: return
        runCatching { networkOf(telephony.voiceNetworkType) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { entry.network = it }
        runCatching {
            entry.roaming = telephony.isNetworkRoaming
            if (entry.roaming) {
                entry.roamingOperator = telephony.networkOperatorName.orEmpty()
                entry.roamingCountry = telephony.networkCountryIso.orEmpty()
            }
        }
        if (withSignal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val signal = telephony.signalStrength ?: return@runCatching
                entry.level = signal.level
                signal.cellSignalStrengths.firstOrNull()?.dbm
                    ?.takeIf { it != Int.MAX_VALUE && it < 0 }
                    ?.let { entry.dbm = it }
            }
        }
    }

    // Voice carried on LTE or NR is by definition VoLTE / VoNR: a phone without them drops to 3G or 2G
    // for the call, and the voice network type says so.
    @Suppress("DEPRECATION")
    private fun networkOf(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_IWLAN -> CallStamp.NETWORK_WIFI
        TelephonyManager.NETWORK_TYPE_NR -> CallStamp.NETWORK_VONR
        TelephonyManager.NETWORK_TYPE_LTE -> CallStamp.NETWORK_VOLTE
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        -> CallStamp.NETWORK_3G

        TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
        -> CallStamp.NETWORK_2G

        else -> ""
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (ignored: Exception) {
        }
    }
}
