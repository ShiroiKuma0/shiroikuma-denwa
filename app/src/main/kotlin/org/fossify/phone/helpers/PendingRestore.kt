package org.fossify.phone.helpers

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Thrown by a restore that could not run *yet* — not one that failed.
 *
 * The two provider-backed categories both need something this app may not have at the moment an
 * archive arrives: the blocked numbers need the dialer role, the call log needs call-log access. On a
 * wiped phone — the case the whole restore contract exists for — neither is granted, because nothing
 * has been set up yet. That is a timing problem, not a data problem, so it must never surface as
 * "restored 0 numbers"; [PendingRestore] catches this exception, keeps the payload, and the app
 * applies it the moment the missing piece appears.
 */
class RestoreDeferredException(val reason: String, cause: Throwable? = null) : Exception(reason, cause)

/**
 * Data a restore could not apply yet, kept on disk until it can be.
 *
 * ## Why this exists at all
 *
 * The blocked numbers are the reason (白い熊, 2026-09-08). They are irreplaceable — there is no other
 * copy of them anywhere, no way to reconstruct the list from anything else, and the whole point of
 * restoring onto a new phone is that the nuisance callers do not start getting through again. But
 * `BlockedNumberContract` only accepts writes from the default dialer, and on a fresh phone denwa is
 * not the default dialer at the moment 応用管理 hands it the archive. The obvious implementations both
 * lose the data: writing and letting the `SecurityException` through fails the whole import, and
 * skipping the category quietly reports success over nothing.
 *
 * So the import never drops a category it cannot apply. It writes the payload here, reports it as
 * **held**, and the app retries on every launch and the moment the dialer role is granted — with a
 * dialog that will not stop appearing until the last held byte is in. A restore can be late. It
 * cannot be lost.
 *
 * ## On disk
 *
 * One file per held category under `files/pending_restore/`, named by the category's own ZIP entry
 * name, so the stash needs no format of its own — the bytes are exactly what came out of the archive
 * and go straight back into the same importer later.
 *
 * Written temp-then-rename with an explicit fsync, because 応用管理 **force-stops this app straight
 * after the import replies**. A file still sitting in the page cache behind a lazily-flushed rename is
 * precisely the data this class exists to not lose.
 */
object PendingRestore {

    private const val DIR = "pending_restore"

    private fun dir(context: Context) = File(context.filesDir, DIR)

    private fun fileFor(context: Context, item: SettingsExport.Item) = File(dir(context), item.entryName)

    /** The categories waiting for their moment, in the pickers' order. */
    fun pending(context: Context): List<SettingsExport.Item> {
        val held = dir(context).list()?.toSet() ?: return emptyList()
        return SettingsExport.Item.listed.filter { it.entryName in held }
    }

    /**
     * One directory listing — the cheap gate every launch runs before doing anything else.
     *
     * Counts recognised entries, not files: a leftover `.part` from a write that never finished must
     * not make this answer "yes" forever and hand the app a background pass on every resume.
     */
    fun hasAny(context: Context): Boolean = pending(context).isNotEmpty()

    /**
     * Keep [bytes] until [item] can be applied. Durable by the time it returns: the rename is fsynced
     * against the force-stop that follows an automation import.
     */
    fun stash(context: Context, item: SettingsExport.Item, bytes: ByteArray) {
        val dir = dir(context).apply { mkdirs() }
        val temp = File(dir, "${item.entryName}.part")
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.flush()
            // The force-stop that follows an automation import cannot lose written bytes — the kernel
            // keeps them across the process dying. This is against the phone losing power instead,
            // which during a restore onto a new phone is not a hypothetical.
            out.fd.sync()
        }
        if (!temp.renameTo(fileFor(context, item))) {
            temp.delete()
            error("cannot stash ${item.id} for a later restore")
        }
    }

    fun read(context: Context, item: SettingsExport.Item): ByteArray? =
        fileFor(context, item).takeIf { it.isFile }?.runCatching { readBytes() }?.getOrNull()

    fun clear(context: Context, item: SettingsExport.Item) {
        fileFor(context, item).delete()
    }

    /** A category still waiting: how many rows it holds, and what it is waiting for. */
    data class Held(val count: Int, val reason: String)

    /** What one pass of [applyPending] managed: what went in, and what is still waiting and why. */
    data class Outcome(
        val applied: Map<SettingsExport.Item, Int> = emptyMap(),
        val stillHeld: Map<SettingsExport.Item, Held> = emptyMap(),
    ) {
        val appliedAnything: Boolean get() = applied.values.any { it > 0 }
    }

    /**
     * Try every held category once. Blocking — call it off the main thread.
     *
     * A category that applies is deleted; one that still cannot be applied stays exactly as it was,
     * with the reason carried back for the dialog to show. A category that fails for any *other*
     * reason is also kept rather than discarded: a payload that cannot be parsed today is still the
     * only copy of those numbers, and quietly deleting it would be the one unrecoverable move.
     */
    @Suppress("TooGenericExceptionCaught") // one bad payload must not stop the other one going in
    fun applyPending(context: Context): Outcome {
        val applied = LinkedHashMap<SettingsExport.Item, Int>()
        val held = LinkedHashMap<SettingsExport.Item, Held>()
        for (item in pending(context)) {
            val bytes = read(context, item)
            if (bytes == null) {
                held[item] = Held(0, "unreadable")
                continue
            }
            try {
                applied[item] = SettingsExport.applyProviderData(context, item, bytes)
                clear(context, item)
            } catch (e: RestoreDeferredException) {
                held[item] = Held(SettingsExport.countIn(item, bytes), e.reason)
            } catch (e: Exception) {
                val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                held[item] = Held(SettingsExport.countIn(item, bytes), reason)
            }
        }
        return Outcome(applied, held)
    }
}
