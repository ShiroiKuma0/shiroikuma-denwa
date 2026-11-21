package org.fossify.phone.helpers

import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.themeColor
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.CallLog.Calls
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.phone.extensions.buildSIMAccountLookupMap
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.simColorOrSystem
import org.fossify.phone.extensions.simTextColor
import org.fossify.phone.models.CallStamp
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.TreeMap
import kotlin.math.abs

/** A SIM card in the phone right now. */
data class SimCard(val slot: Int, val subId: Int, val number: String, val operator: String)

/**
 * The store of [CallStamp]s: one per call, under the call's date plus number.
 *
 * ## Where a stamp comes from
 *
 * - **The call itself** ([CallStampRecorder]), with everything the in-call service can see.
 * - **The log, afterwards** ([late]): a call we never saw live — Android Auto takes the in-call
 *   service from us while it projects — is stamped the first time the log is read, from the slot it
 *   was logged under and the badge of that moment. The card's number is added only when the card now
 *   in that slot was already there when the call was made ([cardAt]); a card put in later says
 *   nothing about an older call.
 * - **A seed**: a file the user (or adb) drops into the app's external files directory, saying which
 *   number and operator stood behind each account id of the calls already in the log. It exists for
 *   the day the stamps were introduced, when a year of history had only slots — and it is a file, not
 *   code, because those numbers are private and this repository is public.
 * - **A backup**: `call_history.json` carries each call's stamp ([CallHistoryBackup]).
 *
 * Everything is kept in memory after the first read (a couple of thousand small rows) and written
 * through to a one-table database.
 */
@Suppress("TooManyFunctions") // one small store: its reads, its writes, and the three ways a stamp is made
object CallStamps {
    const val SEED_FILE = "sim-history-seed.json"
    private const val SEED_RESULT_FILE = "sim-history-seed.result.txt"

    private const val DB_NAME = "call_stamps.db"
    private const val TABLE = "stamps"
    private const val COL_DATE = "date"
    private const val COL_NUMBER = "number"
    private const val COL_JSON = "json"

    private const val PREFS = "call_stamps"
    private const val KEY_BACKFILLED = "backfilled"
    private const val KEY_CARD = "card_"
    private const val KEY_SEED_APPLIED = "seed_applied"

    // Telecom logs a call under its creation time, which is what the recorder keys on too; this only
    // has to absorb a ROM that rounds or re-reads the clock.
    private const val MATCH_WINDOW_MS = 5_000L
    private const val TELEPHONY_PACKAGE = "com.android.phone"

    class Item(val date: Long, val number: String, val stamp: CallStamp)

    private class Entry(val number: String, var stamp: CallStamp)

    private class SeenCard(val card: SimCard, val since: Long)

    private var cache: TreeMap<Long, MutableList<Entry>>? = null
    private var helper: SQLiteOpenHelper? = null
    private var seenCards: Map<Int, SeenCard> = emptyMap()

    // ---- reading ----

    /**
     * The stamp of the call logged at [date] to or from [number]. A stamp the recorder filed under a
     * slightly different time is found too, and moved to the log's own key so that a backup and the
     * next read match it exactly.
     */
    @Synchronized
    fun find(context: Context, date: Long, number: String): CallStamp? {
        exactEntry(context, date, number)?.let { return it.stamp }

        var bestDate = 0L
        var best: Entry? = null
        entries(context).subMap(date - MATCH_WINDOW_MS, true, date + MATCH_WINDOW_MS, true).forEach { (at, list) ->
            list.filter { it.stamp.isFromCall && sameNumber(it.number, number) }.forEach { entry ->
                if (best == null || abs(at - date) < abs(bestDate - date)) {
                    bestDate = at
                    best = entry
                }
            }
        }

        val found = best ?: return null
        remove(context, bestDate, found.number)
        write(context, date, number, found.stamp)
        return found.stamp
    }

    /** Every stamp, for the backup. */
    @Synchronized
    fun all(context: Context): List<Item> =
        entries(context).flatMap { (date, list) -> list.map { Item(date, it.number, it.stamp) } }

    // ---- writing ----

    @Synchronized
    fun putAll(context: Context, items: List<Item>) {
        if (items.isEmpty()) return
        val db = db(context)
        db.beginTransaction()
        try {
            items.forEach { write(context, it.date, it.number, it.stamp) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Stamps from a backup: they fill what is missing and never replace what a live call recorded. */
    @Synchronized
    fun putRestored(context: Context, items: List<Item>) {
        putAll(context, items.filter { exactEntry(context, it.date, it.number)?.stamp?.isFromCall != true })
    }

    /**
     * File what the recorder saw of a call created at [created]. If the log was read before this
     * arrived, the row already carries a weaker stamp under the log's own time — that one is replaced
     * in place rather than left to shadow this.
     */
    @Synchronized
    fun putFromCall(context: Context, created: Long, number: String, stamp: CallStamp) {
        val window = entries(context).subMap(created - MATCH_WINDOW_MS, true, created + MATCH_WINDOW_MS, true)
        val weaker = window.entries.firstNotNullOfOrNull { (at, list) ->
            list.firstOrNull { !it.stamp.isFromCall && sameNumber(it.number, number) }?.let { at to it.number }
        }
        if (weaker != null) {
            write(context, weaker.first, weaker.second, stamp)
        } else {
            write(context, created, number, stamp)
        }
    }

    @Synchronized
    fun clear(context: Context) {
        db(context).delete(TABLE, null, null)
        cache = TreeMap()
    }

    // ---- stamping after the fact ----

    /**
     * Before the log is read: note which cards are in the phone, apply a seed if one was dropped, and
     * on the very first run stamp the whole log — not just the page about to be shown — so that the
     * badges of today are frozen for all of it at once, before anyone changes a colour.
     */
    @Synchronized
    fun prepare(context: Context) {
        noteCards(context)
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) return

        val prefs = prefs(context)
        // A seed that could not be deleted is remembered by its time, so it is applied once all the
        // same — a second pass would re-freeze the old calls in whatever the colours are by then.
        val seedFile = context.getExternalFilesDir(null)?.let { File(it, SEED_FILE) }
            ?.takeIf { it.isFile && it.lastModified() != prefs.getLong(KEY_SEED_APPLIED, -1L) }
        if (seedFile != null) {
            prefs.edit().putLong(KEY_SEED_APPLIED, seedFile.lastModified()).apply()
            val result = runCatching { "stamped ${sweep(context, readSeed(seedFile))} calls" }
                .getOrElse { "failed: $it" }
            runCatching { File(seedFile.parentFile, SEED_RESULT_FILE).writeText("${Date()}  $result\n") }
            seedFile.delete()
            prefs.edit().putBoolean(KEY_BACKFILLED, true).apply()
        } else if (!prefs.getBoolean(KEY_BACKFILLED, false)) {
            runCatching { sweep(context, emptyMap()) }
            prefs.edit().putBoolean(KEY_BACKFILLED, true).apply()
        }
    }

    /**
     * The stamp of a call nobody recorded: SIM [digit] with the badge as it is set now, and the card's
     * number if that card was already in the slot at [date]. Null when the SIM is unknown.
     */
    fun late(context: Context, date: Long, digit: Int, color: Int): CallStamp? {
        if (digit <= 0) return null
        val card = cardAt(digit - 1, date)
        return CallStamp(
            source = CallStamp.SOURCE_LOG,
            digit = digit,
            color = color,
            textColor = context.simTextColor(),
            bold = context.config.simTextBold,
            number = card?.number.orEmpty(),
            operator = card?.operator.orEmpty(),
            slot = digit - 1,
        )
    }

    /**
     * The SIM (1 or 2) of a call whose account matches no SIM in the phone now — both are out, or the
     * one it used is. EMUI logs a cellular call's SIM as its slot index, "0" or "1", so that much can
     * be read back without the card; any other id (a subscription id of a card that is gone, another
     * app's account) stays unknown, -1.
     */
    fun slotSimId(accountId: String?, component: String?): Int {
        if (component?.startsWith("$TELEPHONY_PACKAGE/") != true) return -1
        return when (accountId) {
            "0" -> 1
            "1" -> 2
            else -> -1
        }
    }

    private class SeedCard(val digit: Int, val number: String, val operator: String)

    // {"accounts": {"<account id as logged>": {"sim": 1, "number": "+420…", "operator": "…"}, …}}
    private fun readSeed(file: File): Map<String, SeedCard> {
        val accounts = JSONObject(file.readText()).getJSONObject("accounts")
        return accounts.keys().asSequence().associateWith { id ->
            val card = accounts.getJSONObject(id)
            SeedCard(card.getInt("sim"), card.optString("number"), card.optString("operator"))
        }
    }

    /** Walk the whole call log and stamp what [seed] names or what has no stamp yet; returns how many. */
    private fun sweep(context: Context, seed: Map<String, SeedCard>): Int {
        val lookup = context.buildSIMAccountLookupMap()
        val items = ArrayList<Item>()
        // No projection: naming a column this device's call log lacks is a crash, not a null.
        context.contentResolver.query(Calls.CONTENT_URI, null, null, null, null)?.use { cursor ->
            val dateAt = cursor.getColumnIndex(Calls.DATE)
            val numberAt = cursor.getColumnIndex(Calls.NUMBER)
            val accountAt = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID)
            val componentAt = cursor.getColumnIndex(Calls.PHONE_ACCOUNT_COMPONENT_NAME)
            while (dateAt >= 0 && cursor.moveToNext()) {
                val date = cursor.getLong(dateAt)
                val number = if (numberAt >= 0) cursor.getString(numberAt).orEmpty() else ""
                val accountId = if (accountAt >= 0) cursor.getString(accountAt) else null
                val component = if (componentAt >= 0) cursor.getString(componentAt) else null
                val existing = find(context, date, number)
                val seeded = seed[accountId]?.takeIf { component?.startsWith("$TELEPHONY_PACKAGE/") == true }
                val stamp = when {
                    existing?.isFromCall == true -> null
                    seeded != null -> seedStamp(context, seeded)
                    existing != null -> null
                    else -> {
                        val account = lookup[accountId]
                        val digit = account?.id ?: slotSimId(accountId, component)
                        late(context, date, digit, account?.color ?: badgeColor(context, digit))
                    }
                }
                stamp?.let { items += Item(date, number, it) }
            }
        }
        putAll(context, items)
        Diag.log(Diag.Kind.SIM) { "swept the call log: ${items.size} calls stamped, seed ids ${seed.keys}" }
        return items.size
    }

    private fun seedStamp(context: Context, card: SeedCard) = CallStamp(
        source = CallStamp.SOURCE_SEED,
        digit = card.digit,
        color = badgeColor(context, card.digit),
        textColor = context.simTextColor(),
        bold = context.config.simTextBold,
        number = card.number,
        operator = card.operator,
        slot = card.digit - 1,
    )

    /** The badge fill SIM [digit] has right now. */
    fun badgeColor(context: Context, digit: Int): Int =
        context.simColorOrSystem(digit, context.themeColor(ThemeSlot.PRIMARY))

    // ---- the cards in the phone ----

    /** The cards in the phone now, each with its own number where the card gives one. */
    @SuppressLint("MissingPermission")
    fun activeCards(context: Context): List<SimCard> {
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val infos = runCatching { manager.activeSubscriptionInfoList }.getOrNull().orEmpty()
        return infos.map { info ->
            SimCard(
                slot = info.simSlotIndex,
                subId = info.subscriptionId,
                number = numberOf(manager, info),
                operator = (info.displayName ?: info.carrierName)?.toString().orEmpty(),
            )
        }
    }

    // Asks for the newer API by trying it rather than by SDK_INT, which EMUI under-reports; the same
    // two calls 自由作業盤 reads the cards' numbers with.
    @SuppressLint("MissingPermission", "NewApi")
    @Suppress("DEPRECATION")
    private fun numberOf(manager: SubscriptionManager, info: SubscriptionInfo): String {
        val modern = runCatching { manager.getPhoneNumber(info.subscriptionId) }.getOrNull()
        if (!modern.isNullOrBlank()) return modern
        return runCatching { info.number }.getOrNull().orEmpty()
    }

    /** The card behind a call's phone account, null when it is not a SIM's or the card is gone. */
    @SuppressLint("NewApi")
    fun cardFor(context: Context, handle: PhoneAccountHandle?): SimCard? {
        if (handle == null) return null
        val cards = activeCards(context)
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val subId = runCatching { telephony?.getSubscriptionId(handle) }.getOrNull()
        return cards.firstOrNull { it.subId == subId }
            ?: cards.firstOrNull { it.subId.toString() == handle.id }
            ?: handle.id.toIntOrNull()?.let { slot -> cards.firstOrNull { it.slot == slot } }
    }

    /**
     * Remember each card and when it was first seen in its slot. A card that differs from the one
     * remembered — another number, another operator, or with no number to go by another subscription —
     * starts counting from now.
     */
    @Synchronized
    fun noteCards(context: Context) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val seen = HashMap<Int, SeenCard>()
        activeCards(context).forEach { card ->
            val stored = prefs.getString(KEY_CARD + card.slot, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val same = stored != null &&
                    stored.optString("num") == card.number &&
                    stored.optString("op") == card.operator &&
                    (card.number.isNotEmpty() || stored.optInt("sub", -1) == card.subId)
            val since = if (same) stored!!.optLong("since", now) else now
            if (!same) {
                val json = JSONObject().put("num", card.number).put("op", card.operator).put("sub", card.subId).put("since", since)
                prefs.edit().putString(KEY_CARD + card.slot, json.toString()).apply()
                Diag.log(Diag.Kind.SIM) {
                    "new card in slot ${card.slot}: sub=${card.subId} op=${card.operator} number=${if (card.number.isEmpty()) "none" else "known"}"
                }
            }
            seen[card.slot] = SeenCard(card, since)
        }
        seenCards = seen
    }

    private fun cardAt(slot: Int, date: Long): SimCard? = seenCards[slot]?.takeIf { date >= it.since }?.card

    // ---- storage ----

    private fun sameNumber(a: String, b: String): Boolean =
        a == b || (a.isBlank() && b.isBlank()) || (a.isNotBlank() && b.isNotBlank() && PhoneNumberUtils.compare(a, b))

    private fun exactEntry(context: Context, date: Long, number: String): Entry? =
        entries(context)[date]?.firstOrNull { sameNumber(it.number, number) }

    private fun write(context: Context, date: Long, number: String, stamp: CallStamp) {
        val list = entries(context).getOrPut(date) { ArrayList(1) }
        val entry = list.firstOrNull { it.number == number }
        if (entry != null) entry.stamp = stamp else list += Entry(number, stamp)
        val values = ContentValues().apply {
            put(COL_DATE, date)
            put(COL_NUMBER, number)
            put(COL_JSON, stamp.toJson().toString())
        }
        db(context).insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun remove(context: Context, date: Long, number: String) {
        val map = entries(context)
        map[date]?.let { list ->
            list.removeAll { it.number == number }
            if (list.isEmpty()) map.remove(date)
        }
        db(context).delete(TABLE, "$COL_DATE = ? AND $COL_NUMBER = ?", arrayOf(date.toString(), number))
    }

    private fun entries(context: Context): TreeMap<Long, MutableList<Entry>> {
        cache?.let { return it }
        val map = TreeMap<Long, MutableList<Entry>>()
        db(context).query(TABLE, arrayOf(COL_DATE, COL_NUMBER, COL_JSON), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val stamp = runCatching { CallStamp.fromJson(JSONObject(cursor.getString(2))) }.getOrNull() ?: continue
                map.getOrPut(cursor.getLong(0)) { ArrayList(1) } += Entry(cursor.getString(1), stamp)
            }
        }
        cache = map
        return map
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun db(context: Context): SQLiteDatabase {
        val open = helper ?: object : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE $TABLE ($COL_DATE INTEGER NOT NULL, $COL_NUMBER TEXT NOT NULL, " +
                            "$COL_JSON TEXT NOT NULL, PRIMARY KEY ($COL_DATE, $COL_NUMBER))"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }.also { helper = it }
        return open.writableDatabase
    }
}
