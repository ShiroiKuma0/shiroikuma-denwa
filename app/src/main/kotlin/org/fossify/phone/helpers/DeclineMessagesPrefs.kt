package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.telephony.PhoneNumberUtils
import androidx.core.content.edit
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.phone.R
import org.fossify.phone.extensions.getCallLogRegion
import org.json.JSONArray

/**
 * The canned replies offered when an incoming call is declined with a message — the denwa twin of
 * shiroikuma-jami's DeclineMessagesPrefs, kept to the same shape so the two apps behave alike.
 *
 * Two kinds of list, both ORDERED (hence JSON arrays rather than a StringSet):
 *  · the general list, offered for every caller;
 *  · a per-caller list, keyed by the caller's number in E.164 — read in the SIM's country, never the
 *    UI locale (see getCallLogRegion) — so "735 759 605" and "+420 735 759 605" are one caller.
 *
 * The picker shows the caller's own list first, then the general one ([listFor]). The caller's
 * last-known display name is stored beside the list only so the settings page can label it without a
 * contacts lookup.
 *
 * The general list is seeded once, on first read, with four defaults — always in English, whatever the
 * UI language, since they are what callers receive; after that they are ordinary items. Deleting every
 * item leaves an empty list and is never re-seeded.
 *
 * Everything lives in the app's own preferences file, so the settings export carries it with the
 * behaviour settings, untouched by SettingsExport.
 */
@Suppress("TooManyFunctions") // a preferences wrapper: one accessor per list and per direction, by design
object DeclineMessagesPrefs {
    private const val KEY_GLOBAL = "decline_messages_global"
    private const val NUMBER_PREFIX = "decline_messages_number."
    private const val NAME_PREFIX = "decline_messages_name."

    // Builds +086 and +087 seeded the general list in the UI language. A list still exactly equal to
    // one of those seeds was never edited, so it is swapped for the English seed; any edit keeps it.
    private val LEGACY_LOCALIZED_SEEDS = listOf(
        listOf("今は出られません。後で折り返します。", "会議中です。", "SMSでご連絡ください。", "後で電話します。"),
        listOf("Teď nemůžu mluvit — zavolám zpátky.", "Jsem na schůzce.", "Napište mi prosím SMS.", "Zavolám později.")
    )

    private fun p(c: Context) = c.getSharedPrefs()

    /** The storage key of a caller's number; empty for a withheld one. */
    fun norm(c: Context, number: String): String {
        val decoded = Uri.decode(number).removePrefix("tel:").trim()
        if (decoded.isEmpty()) {
            return ""
        }

        return PhoneNumberUtils.formatNumberToE164(decoded, c.getCallLogRegion())
            ?: PhoneNumberUtils.normalizeNumber(decoded)
    }

    private fun read(c: Context, key: String): MutableList<String> {
        val raw = p(c).getString(key, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(raw)
            MutableList(a.length()) { a.optString(it) }.filter { it.isNotBlank() }.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun write(c: Context, key: String, list: List<String>) {
        p(c).edit { putString(key, JSONArray(list).toString()) }
    }

    // --- general list ------------------------------------------------------------------------

    private fun seed(c: Context) = listOf(
        c.getString(R.string.sk_decline_default_1),
        c.getString(R.string.sk_decline_default_2),
        c.getString(R.string.sk_decline_default_3),
        c.getString(R.string.sk_decline_default_4)
    )

    fun global(c: Context): List<String> {
        val stored = if (p(c).contains(KEY_GLOBAL)) read(c, KEY_GLOBAL) else null
        // never seeded, or still holding an untouched localized seed from build +086/+087
        if (stored == null || stored in LEGACY_LOCALIZED_SEEDS) {
            return seed(c).also { write(c, KEY_GLOBAL, it) }
        }
        return stored
    }

    fun setGlobal(c: Context, list: List<String>) = write(c, KEY_GLOBAL, list.filter { it.isNotBlank() })

    // --- per-caller lists --------------------------------------------------------------------

    /**
     * The stored key [number] belongs to: its own E.164 form, or else an existing key the telephony
     * comparison calls the same number (a key written before the SIM's country was known).
     */
    private fun keyFor(c: Context, number: String): String {
        val id = norm(c, number)
        if (id.isEmpty() || p(c).contains(NUMBER_PREFIX + id)) {
            return id
        }

        @Suppress("DEPRECATION")
        return storedNumbers(c).firstOrNull { PhoneNumberUtils.compare(it, id) } ?: id
    }

    private fun storedNumbers(c: Context): List<String> =
        p(c).all.keys.filter { it.startsWith(NUMBER_PREFIX) }.map { it.removePrefix(NUMBER_PREFIX) }

    fun forNumber(c: Context, number: String): List<String> {
        val id = keyFor(c, number)
        return if (id.isEmpty()) emptyList() else read(c, NUMBER_PREFIX + id)
    }

    /** Writes the caller's list; an empty list removes the caller (and its stored name). */
    fun setForNumber(c: Context, number: String, list: List<String>) {
        val id = keyFor(c, number)
        if (id.isEmpty()) {
            return
        }

        val clean = list.filter { it.isNotBlank() }
        if (clean.isEmpty()) {
            p(c).edit { remove(NUMBER_PREFIX + id).remove(NAME_PREFIX + id) }
        } else {
            write(c, NUMBER_PREFIX + id, clean)
        }
    }

    fun callerName(c: Context, number: String): String? =
        p(c).getString(NAME_PREFIX + keyFor(c, number), null)?.takeIf { it.isNotBlank() }

    fun setCallerName(c: Context, number: String, name: String) {
        val id = keyFor(c, number)
        if (id.isNotEmpty() && name.isNotBlank()) {
            p(c).edit { putString(NAME_PREFIX + id, name) }
        }
    }

    /** The numbers of every caller with a list of their own, sorted by their stored name. */
    fun callersWithOwn(c: Context): List<String> =
        storedNumbers(c)
            .filter { read(c, NUMBER_PREFIX + it).isNotEmpty() }
            .sortedBy { (callerName(c, it) ?: it).lowercase() }

    /** What the picker offers: the caller's own replies, then the general ones not already shown. */
    fun listFor(c: Context, number: String): Pair<List<String>, List<String>> {
        val own = forNumber(c, number)
        return own to global(c).filter { it !in own }
    }

    /** Appends [text] to the general list or to the caller's list, unless it is already there. */
    fun addGlobal(c: Context, text: String) {
        val l = global(c).toMutableList()
        if (text.isNotBlank() && text !in l) {
            l.add(text)
            setGlobal(c, l)
        }
    }

    fun addForNumber(c: Context, number: String, text: String) {
        val l = forNumber(c, number).toMutableList()
        if (text.isNotBlank() && text !in l) {
            l.add(text)
            setForNumber(c, number, l)
        }
    }
}
