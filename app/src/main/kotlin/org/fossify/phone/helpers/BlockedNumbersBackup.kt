package org.fossify.phone.helpers

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import org.fossify.commons.extensions.getStringValue
import org.fossify.phone.extensions.holdsDialerRole
import org.json.JSONArray
import org.json.JSONObject

/**
 * The blocked numbers as backup data — read from and written to `BlockedNumberContract` directly.
 *
 * ## Why not commons' helpers
 *
 * `Context.getBlockedNumbers()` opens with `if (!isNougatPlus() || !isDefaultDialer()) return
 * arrayListOf()`, and its cursor helper swallows a `SecurityException` by default. For a screen that
 * is right: no role, no list, no crash. For a backup it is the worst possible shape — an export taken
 * without the dialer role would write `blocked_numbers.json` holding zero numbers and report success,
 * and 白い熊 would find out on the day he restored it. Commons also cannot tell us whether we hold the
 * role at all (see [holdsDialerRole]). So this reads the provider itself and **fails loudly** rather
 * than returning a plausible empty list.
 *
 * ## The pattern entries
 *
 * Commons lets a blocked entry be a wildcard pattern ("+49*"), stored in the original-number column
 * like any other. Nothing here inspects the string, so patterns round-trip as themselves; the E164
 * column is carried only when the archive has one, exactly as commons writes it.
 */
object BlockedNumbersBackup {

    private const val VERSION = 1
    private const val KEY_NUMBERS = "numbers"
    private const val KEY_NUMBER = "number"
    private const val KEY_NORMALIZED = "normalized"

    /**
     * Every blocked number, as the bytes of `blocked_numbers.json`.
     *
     * Throws when the role is missing rather than writing an empty list — the export fails, names the
     * reason, and 白い熊 keeps the previous backup instead of a new one that quietly says "none".
     */
    fun exportJson(context: Context): ByteArray {
        require(BlockedNumberContract.canCurrentUserBlockNumbers(context)) { "this user cannot block numbers" }
        require(context.holdsDialerRole()) { "not the default dialer — the blocked numbers cannot be read" }

        val numbers = JSONArray()
        val projection = arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, BlockedNumbers.COLUMN_E164_NUMBER)
        val cursor = context.contentResolver.query(BlockedNumbers.CONTENT_URI, projection, null, null, null)
            ?: error("the blocked-numbers provider returned nothing")
        cursor.use {
            while (it.moveToNext()) {
                val number = it.getStringValue(BlockedNumbers.COLUMN_ORIGINAL_NUMBER).orEmpty()
                if (number.isEmpty()) continue
                val entry = JSONObject().put(KEY_NUMBER, number)
                it.getStringValue(BlockedNumbers.COLUMN_E164_NUMBER)
                    ?.takeIf { e164 -> e164.isNotEmpty() }
                    ?.let { e164 -> entry.put(KEY_NORMALIZED, e164) }
                numbers.put(entry)
            }
        }
        return JSONObject().put("version", VERSION).put(KEY_NUMBERS, numbers).toString().toByteArray()
    }

    /** How many entries an archived payload carries — what the held-data dialog counts. */
    fun countIn(bytes: ByteArray): Int = runCatching { parse(bytes).size }.getOrDefault(0)

    /**
     * Put the archived numbers back, skipping the ones already blocked. Returns how many were added.
     *
     * Throws [RestoreDeferredException] when the role is not held — including when an insert is
     * refused despite the role looking right, since the platform's answer beats ours. The caller keeps
     * the payload and comes back to it.
     */
    fun importJson(context: Context, bytes: ByteArray): Int {
        val blocker = when {
            !BlockedNumberContract.canCurrentUserBlockNumbers(context) -> "this user cannot block numbers"
            !context.holdsDialerRole() -> "not the default dialer"
            else -> null
        }
        if (blocker != null) {
            throw RestoreDeferredException(blocker)
        }

        val existing = existingNumbers(context)
        var added = 0
        for ((number, normalized) in parse(bytes)) {
            if (number in existing) continue
            val values = ContentValues().apply {
                put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                normalized?.let { put(BlockedNumbers.COLUMN_E164_NUMBER, it) }
            }
            try {
                context.contentResolver.insert(BlockedNumbers.CONTENT_URI, values)
            } catch (e: SecurityException) {
                // Mid-list: the role went away, or was never really ours. Everything already inserted
                // stays, and the payload is kept whole — a second pass skips what is now present.
                throw RestoreDeferredException("the blocked-numbers provider refused the write", e)
            }
            added++
        }
        return added
    }

    /** The original-number column of what is blocked right now — the dedup key, as stored. */
    private fun existingNumbers(context: Context): Set<String> {
        val found = HashSet<String>()
        val projection = arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
        val cursor = try {
            context.contentResolver.query(BlockedNumbers.CONTENT_URI, projection, null, null, null)
        } catch (e: SecurityException) {
            throw RestoreDeferredException("the blocked numbers cannot be read", e)
        }
        cursor?.use {
            while (it.moveToNext()) {
                it.getStringValue(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)?.let(found::add)
            }
        }
        return found
    }

    private fun parse(bytes: ByteArray): List<Pair<String, String?>> {
        val array = JSONObject(bytes.decodeToString()).optJSONArray(KEY_NUMBERS) ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val number = entry.optString(KEY_NUMBER).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            number to entry.optString(KEY_NORMALIZED).takeIf { it.isNotEmpty() }
        }
    }
}
