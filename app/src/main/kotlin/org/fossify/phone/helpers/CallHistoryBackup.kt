package org.fossify.phone.helpers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CallLog.Calls
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.PERMISSION_WRITE_CALL_LOG
import org.json.JSONArray
import org.json.JSONObject

/**
 * The call log as backup data — a raw dump of the `CallLog.Calls` provider.
 *
 * ## Why not [RecentsHelper]
 *
 * The app already had call-history export/import, on two manual rows in Settings, going through
 * [RecentsHelper.getRecentCalls]. That path is built for the Recents *list* and is wrong for an
 * archive in three ways: it **drops every call from a blocked number** (the display list filters them
 * out, so the backup would silently lose exactly the calls from the people 白い熊 blocked), it walks
 * the contacts provider to bake today's names into rows that already carry their own, and its restore
 * blind-inserts with no deduplication, so importing onto a phone that still has its log doubles every
 * entry. This reads the columns and writes them back, and does nothing else.
 *
 * ## What a restore is careful about
 *
 * - **Deduplicated** on date-plus-number, so restoring twice, or onto a phone that already has some
 *   of the log, adds only what is missing.
 * - **Marked read.** A restored row goes in with `NEW = 0` and `IS_READ = 1`; inserting a few hundred
 *   missed calls as unread would hand the platform a few hundred missed-call notifications.
 * - **Read with no projection**, because `subscription_id` is a Huawei column and naming a column the
 *   device does not have is a crash rather than a null.
 */
@Suppress("TooManyFunctions") // one tiny reader per column type and per direction, by design
object CallHistoryBackup {

    private const val VERSION = 1
    private const val KEY_CALLS = "calls"
    private const val INSERT_CHUNK = 200

    // The columns worth carrying, as their JSON names. PHONE_ACCOUNT_ID / _COMPONENT_NAME and Huawei's
    // subscription_id identify the SIM a call went through; on a new phone they will not match a live
    // account and the SIM colour simply falls back, which is still better than dropping which SIM it
    // was on a restore onto the same phone.
    private val TEXT_COLUMNS = mapOf(
        "number" to Calls.NUMBER,
        "name" to Calls.CACHED_NAME,
        "account_id" to Calls.PHONE_ACCOUNT_ID,
        "account_component" to Calls.PHONE_ACCOUNT_COMPONENT_NAME,
        "geocoded" to Calls.GEOCODED_LOCATION,
    )
    private val INT_COLUMNS = mapOf(
        "type" to Calls.TYPE,
        "duration" to Calls.DURATION,
        "presentation" to Calls.NUMBER_PRESENTATION,
        "subscription_id" to "subscription_id",
    )

    /** The whole call log, oldest first, as the bytes of `call_history.json`. */
    fun exportJson(context: Context): ByteArray {
        require(context.hasPermission(PERMISSION_READ_CALL_LOG)) { "no call-log access" }

        val calls = JSONArray()
        val cursor = context.contentResolver.query(Calls.CONTENT_URI, null, null, null, "${Calls.DATE} ASC")
            ?: error("the call log is unavailable")
        cursor.use {
            while (it.moveToNext()) {
                rowOf(it)?.let(calls::put)
            }
        }
        return JSONObject().put("version", VERSION).put(KEY_CALLS, calls).toString().toByteArray()
    }

    /** How many calls an archived payload carries — what the held-data dialog counts. */
    fun countIn(bytes: ByteArray): Int = runCatching { callsIn(bytes).length() }.getOrDefault(0)

    /**
     * Put the archived calls back, skipping the ones already in the log. Returns how many were added.
     *
     * Throws [RestoreDeferredException] without call-log access, which on a fresh phone is simply not
     * granted yet — it arrives with the dialer role. The caller keeps the payload for then.
     */
    fun importJson(context: Context, bytes: ByteArray): Int {
        if (!context.hasPermission(PERMISSION_WRITE_CALL_LOG) || !context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            throw RestoreDeferredException("no call-log access")
        }

        val calls = callsIn(bytes)
        val existing = existingKeys(context)
        val writable = writableColumns(context)
        val pending = ArrayList<ContentValues>(INSERT_CHUNK)
        var added = 0

        for (index in 0 until calls.length()) {
            val row = calls.optJSONObject(index)
            val date = row?.optLong("date", 0L) ?: 0L
            val number = row?.optString("number").orEmpty()
            if (row != null && date > 0L && "$date|$number" !in existing) {
                pending += valuesOf(row, date, number, writable)
                if (pending.size >= INSERT_CHUNK) {
                    added += flush(context, pending)
                }
            }
        }
        return added + flush(context, pending)
    }

    private fun flush(context: Context, pending: MutableList<ContentValues>): Int {
        if (pending.isEmpty()) return 0
        val written = try {
            context.contentResolver.bulkInsert(Calls.CONTENT_URI, pending.toTypedArray())
        } catch (e: SecurityException) {
            throw RestoreDeferredException("the call log refused the write", e)
        }
        pending.clear()
        return written
    }

    private fun valuesOf(
        row: JSONObject,
        date: Long,
        number: String,
        writable: Set<String>,
    ) = ContentValues().apply {
        put(Calls.DATE, date)
        put(Calls.NUMBER, number)
        TEXT_COLUMNS
            .filter { (_, column) -> column != Calls.NUMBER && column in writable }
            .mapNotNull { (key, column) -> row.optString(key).takeIf { it.isNotEmpty() }?.let { column to it } }
            .forEach { (column, value) -> put(column, value) }
        INT_COLUMNS
            .filter { (key, column) -> column in writable && row.has(key) }
            .forEach { (key, column) -> put(column, row.optInt(key)) }
        // Restored history is history, not news: unread missed calls would each raise a notification.
        put(Calls.NEW, 0)
        put(Calls.IS_READ, 1)
    }

    /**
     * What THIS device's call log will accept, asked rather than assumed.
     *
     * A backup taken on the Mate XT carries `subscription_id`, which is Huawei's own column; handing
     * that to a call log without it does not drop the value, it fails the entire `bulkInsert` and with
     * it the whole restore. So the archive is filtered against the destination's real column set — an
     * empty-result query, which costs one round trip and returns the column names regardless.
     */
    private fun writableColumns(context: Context): Set<String> {
        val cursor = context.contentResolver
            .query(Calls.CONTENT_URI, null, "${Calls._ID} = -1", null, null)
            ?: return TEXT_COLUMNS.values.toSet() + INT_COLUMNS.values.toSet()
        return cursor.use { it.columnNames.toSet() }
    }

    /** Date-plus-number for every call already logged — the dedup key both directions agree on. */
    private fun existingKeys(context: Context): Set<String> {
        val keys = HashSet<String>()
        val projection = arrayOf(Calls.DATE, Calls.NUMBER)
        val cursor = try {
            context.contentResolver.query(Calls.CONTENT_URI, projection, null, null, null)
        } catch (e: SecurityException) {
            throw RestoreDeferredException("the call log cannot be read", e)
        }
        cursor?.use {
            while (it.moveToNext()) {
                keys += "${it.longOrNull(Calls.DATE) ?: 0L}|${it.textOrNull(Calls.NUMBER).orEmpty()}"
            }
        }
        return keys
    }

    private fun rowOf(cursor: Cursor): JSONObject? {
        val date = cursor.longOrNull(Calls.DATE) ?: return null
        val row = JSONObject().put("date", date)
        TEXT_COLUMNS.forEach { (key, column) ->
            cursor.textOrNull(column)?.takeIf { it.isNotEmpty() }?.let { row.put(key, it) }
        }
        INT_COLUMNS.forEach { (key, column) ->
            cursor.intOrNull(column)?.let { row.put(key, it) }
        }
        return row
    }

    private fun callsIn(bytes: ByteArray): JSONArray =
        JSONObject(bytes.decodeToString()).optJSONArray(KEY_CALLS) ?: JSONArray()

    // Read by index and tolerate an absent column: the projection is deliberately null (see above), so
    // what a given OEM's call log actually carries is discovered here rather than assumed.
    private fun Cursor.textOrNull(column: String): String? = indexOf(column)?.let { getString(it) }

    private fun Cursor.intOrNull(column: String): Int? = indexOf(column)?.let { getInt(it) }

    private fun Cursor.longOrNull(column: String): Long? = indexOf(column)?.let { getLong(it) }

    private fun Cursor.indexOf(column: String): Int? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }
}
