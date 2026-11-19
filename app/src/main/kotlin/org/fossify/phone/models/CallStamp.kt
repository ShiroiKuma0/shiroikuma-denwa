package org.fossify.phone.models

import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * What a call was made through, frozen when it was made.
 *
 * The system call log keeps only the slot a call used, and the badge used to be worked out from that
 * and today's settings on every read — so a new card in slot 1 took over the whole history of the old
 * one, and re-colouring SIM 1 repainted calls made a year ago. A stamp is the answer (白い熊,
 * 2026-11-18): the SIM's own number, its operator and the badge as it looked then, plus what only the
 * in-call service knows and Telecom throws away (how the call ended, how long it rang, the network,
 * roaming, the audio route, the signal, caller ID). A row with a stamp is drawn from it and from
 * nothing else.
 *
 * Kept by [org.fossify.phone.helpers.CallStamps] under the call's date plus number — the key a
 * restored backup still matches, which a call-log row id would not.
 */
@Serializable
data class CallStamp(
    val source: String = SOURCE_LOG,
    /** The number on the badge, 1 or 2; 0 when the stamp carries no badge. */
    val digit: Int = 0,
    val color: Int = 0,
    val textColor: Int = 0,
    val bold: Boolean = true,
    /** The SIM's own number as the card or the user gave it, "" when the card reports none. */
    val number: String = "",
    val operator: String = "",
    /** Slot index, 0 or 1; -1 unknown. */
    val slot: Int = -1,
    /** `DisconnectCause` code, -1 when the call was not seen ending. */
    val endCode: Int = -1,
    /** How long it rang before it was answered or given up, -1 unknown. */
    val ringMs: Long = -1,
    /** One of the NETWORK_* codes, "" unknown. */
    val network: String = "",
    val roaming: Boolean = false,
    val roamingOperator: String = "",
    val roamingCountry: String = "",
    /** `CallAudioState` route the call ended on, -1 unknown. */
    val route: Int = -1,
    val dbm: Int = NO_DBM,
    /** 0–4, -1 unknown. */
    val level: Int = -1,
    /** One of the CALLER_ID_* values. */
    val callerId: Int = CALLER_ID_UNKNOWN,
) {
    /** True when the stamp was taken while the call was live, so nothing weaker may replace it. */
    val isFromCall get() = source == SOURCE_CALL

    fun toJson(): JSONObject = JSONObject().apply {
        put("src", source)
        put("digit", digit)
        put("color", color)
        put("text", textColor)
        put("bold", bold)
        if (number.isNotEmpty()) put("num", number)
        if (operator.isNotEmpty()) put("op", operator)
        if (slot >= 0) put("slot", slot)
        if (endCode >= 0) put("end", endCode)
        if (ringMs >= 0) put("ring", ringMs)
        if (network.isNotEmpty()) put("net", network)
        if (roaming) put("roam", true)
        if (roamingOperator.isNotEmpty()) put("roam_op", roamingOperator)
        if (roamingCountry.isNotEmpty()) put("roam_iso", roamingCountry)
        if (route >= 0) put("route", route)
        if (dbm != NO_DBM) put("dbm", dbm)
        if (level >= 0) put("lvl", level)
        if (callerId != CALLER_ID_UNKNOWN) put("cid", callerId)
    }

    companion object {
        /** Taken by the in-call service while the call was live. */
        const val SOURCE_CALL = "call"

        /** Worked out afterwards from the call log's slot and the cards and colours of that moment. */
        const val SOURCE_LOG = "log"

        /** Written by a history seed: what the user says the old cards were. */
        const val SOURCE_SEED = "seed"

        const val NO_DBM = Int.MIN_VALUE

        const val NETWORK_WIFI = "wifi"
        const val NETWORK_VOLTE = "volte"
        const val NETWORK_VONR = "vonr"
        const val NETWORK_3G = "3g"
        const val NETWORK_2G = "2g"

        const val CALLER_ID_UNKNOWN = -1

        /** We called without a prefix: the network's own setting decided. */
        const val CALLER_ID_DEFAULT = 0
        const val CALLER_ID_SHOWN = 1
        const val CALLER_ID_HIDDEN = 2

        fun fromJson(json: JSONObject) = CallStamp(
            source = json.optString("src", SOURCE_LOG),
            digit = json.optInt("digit", 0),
            color = json.optInt("color", 0),
            textColor = json.optInt("text", 0),
            bold = json.optBoolean("bold", true),
            number = json.optString("num"),
            operator = json.optString("op"),
            slot = json.optInt("slot", -1),
            endCode = json.optInt("end", -1),
            ringMs = json.optLong("ring", -1),
            network = json.optString("net"),
            roaming = json.optBoolean("roam", false),
            roamingOperator = json.optString("roam_op"),
            roamingCountry = json.optString("roam_iso"),
            route = json.optInt("route", -1),
            dbm = json.optInt("dbm", NO_DBM),
            level = json.optInt("lvl", -1),
            callerId = json.optInt("cid", CALLER_ID_UNKNOWN),
        )
    }
}
