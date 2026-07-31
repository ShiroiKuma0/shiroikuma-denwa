package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.ACTION_CANCEL_EXPORT
import org.fossify.phone.helpers.ACTION_EXPORT_STATE
import org.fossify.phone.helpers.ACTION_LIST_CATEGORIES
import org.fossify.phone.helpers.EXTRA_AUTOMATION_TOKEN
import org.fossify.phone.helpers.EXTRA_BACKUP_PATH
import org.fossify.phone.helpers.EXTRA_EXPORT_ITEMS
import org.fossify.phone.helpers.EXTRA_PROGRESS_ACTION
import org.fossify.phone.helpers.EXTRA_PROGRESS_APP
import org.fossify.phone.helpers.EXTRA_PROGRESS_CURRENT
import org.fossify.phone.helpers.EXTRA_PROGRESS_TEXT
import org.fossify.phone.helpers.EXTRA_PROGRESS_TOTAL
import org.fossify.phone.helpers.EXTRA_PROGRESS_UNIT
import org.fossify.phone.helpers.EXTRA_REPLY_ACTION
import org.fossify.phone.helpers.EXTRA_REPLY_ID
import org.fossify.phone.helpers.EXTRA_REPLY_PACKAGE
import org.fossify.phone.helpers.EXTRA_REPLY_RESULT
import org.fossify.phone.helpers.ExportCancelledException
import org.fossify.phone.helpers.PROGRESS_THROTTLE_MS
import org.fossify.phone.helpers.ProgressReporter
import org.fossify.phone.helpers.SettingsExport
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The 保存復元 state-export contract, for 白い熊 自由作業盤's one-run backup of every sister app.
 *
 * Three exported, token-gated actions:
 *  - [ACTION_LIST_CATEGORIES] — instant; replies "OK:" plus one `id<TAB>label<TAB>parent<TAB>on|off`
 *    line per selectable item. The third field is the parent id, empty for a top-level item, so a
 *    sub-option ("appearance.fonts" under "appearance") can be rendered indented and follow its
 *    parent's toggle; the fourth is whether the item starts ticked, which is this app's answer to
 *    state rather than the caller's to guess.
 *  - [ACTION_EXPORT_STATE] — runs the same category ZIP export as the Export/Import panel, headlessly
 *    (no Activity, no interaction), and replies with the written path and its real size. Extras:
 *    "token", optional "path" (an absolute directory that OVERRIDES the configured export folder),
 *    optional "items" (comma-separated category ids; absent = the default set), optional
 *    "progress_action", plus "reply_action"/"reply_package"/"reply_id".
 *  - [ACTION_CANCEL_EXPORT] — stops the export in flight. Extras: "token" and an optional "reply_id"
 *    (absent = whatever is running, which is unambiguous since two exports at once are forbidden). It
 *    is fire-and-forget: it never replies, and it is a SILENT no-op when nothing is running or the
 *    export already finished. The cancelled run answers its own request with "ERROR:cancelled" and
 *    deletes the half-written archive, so a cancelled export leaves the backup directory exactly as it
 *    found it. It is routed through this receiver rather than a service precisely because a
 *    third-party caller can reach an exported receiver and cannot start a private service.
 *
 * Directory precedence: the "path" extra → the app's configured export folder → ERROR:no-directory.
 *
 * The reply is a FRESH plain broadcast carrying "reply_id" + "result" — the only channel that works on
 * this EMUI: it severs the ordered-broadcast result between third-party apps and may drop a broadcast
 * carrying a Binder, so no ResultReceiver / PendingIntent / Messenger is ever used (verified on 白い熊's
 * Mate XT, 2026-07-23). [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] so a backgrounded caller still hears us.
 * Exactly one terminal reply per request, guarded by an [AtomicBoolean] so an async success and a
 * synchronous error can never both fire.
 *
 * Progress is reported as real counts, never a percentage — "区分 3/5 — 外観", throttled to one
 * broadcast per [PROGRESS_THROTTLE_MS] with an unthrottled final one at completion.
 *
 * Exported with no android:permission — the caller cannot hold one, so the master switch plus the token
 * are the gate. Both live on the 白い熊 電話 UI page, under Export / Import.
 */
// Every catch here is deliberately broad: a request from another app must always be answered with a
// single ERROR line rather than take the receiver down, and a reply or progress broadcast that the
// system refuses must never abort the export it is reporting on. The function count is one small named
// step per stage of a request — parse, answer, run, cancel, clean up, format — which is what makes the
// contract readable against the document it implements.
@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
class StateExportReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "DenwaStateExport"
        private const val KILO = 1024.0

        // The export in flight, if any. Process-static because a cancel arrives on a FRESH receiver
        // instance and must find the run some earlier instance started; at most one is ever meaningful,
        // since the contract forbids two exports at once.
        private val running = AtomicReference<RunningExport?>(null)
    }

    /** One in-flight export: the request it answers, and the flag that unwinds it at an entry boundary. */
    private class RunningExport(val replyId: String) {
        @Volatile
        var cancelled = false
    }

    /** What a parsed request turned out to be: already answerable, or an export to run. */
    private sealed class Request {
        class Done(val result: String) : Request()
        class Export(val cats: Set<SettingsExport.Item>, val path: String) : Request()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_EXPORT_STATE && action != ACTION_LIST_CATEGORIES && action != ACTION_CANCEL_EXPORT) {
            return
        }

        // The cancel action answers nothing at all — no reply, no result data, so it never touches the
        // reply machinery below and is handled before any of it is set up.
        if (action == ACTION_CANCEL_EXPORT) {
            cancelExport(context.applicationContext, intent)
            return
        }

        // goAsync() holds the broadcast open until finish(); the guard makes finishWith idempotent so
        // the async success path and any synchronous error path can't double-finish (and a dropped path
        // can't leave the caller waiting forever).
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val appContext = context.applicationContext
        val replyAction = intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        val progressAction = intent.getStringExtra(EXTRA_PROGRESS_ACTION)?.trim().orEmpty()

        fun finishWith(result: String) {
            if (!finished.compareAndSet(false, true)) return
            // Logged either way: the reply is invisible on this side, and this is what 白い熊 reads back
            // with `adb logcat` during acceptance testing.
            Log.i(TAG, "result → $result")
            if (replyAction.isNotEmpty() && replyId.isNotEmpty()) {
                try {
                    appContext.sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage.ifEmpty { null })
                            .putExtra(EXTRA_REPLY_ID, replyId)
                            .putExtra(EXTRA_REPLY_RESULT, result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                    Log.i(TAG, "reply broadcast sent → $replyAction ($replyPackage, id=$replyId)")
                } catch (e: Exception) {
                    Log.w(TAG, "reply broadcast failed: $e")
                }
            }
            // Correct AOSP behaviour and harmless, but never our only channel (see the class comment).
            pending.setResultData(result)
            pending.finish()
        }

        val request = try {
            parse(appContext, intent, action)
        } catch (e: Exception) {
            Request.Done("ERROR:${reason(e)}")
        }

        when (request) {
            is Request.Done -> finishWith(request.result)
            is Request.Export -> {
                val progress = throttledProgress(appContext, progressAction, replyPackage, replyId)
                val run = RunningExport(replyId)
                running.set(run)
                ensureBackgroundThread {
                    try {
                        finishWith(export(appContext, request.cats, request.path, progress, run))
                    } finally {
                        // Only if it is still ours: a later export owns the slot from the moment it starts.
                        running.compareAndSet(run, null)
                    }
                }
            }
        }
    }

    /**
     * [ACTION_CANCEL_EXPORT] — stop the export in flight, if there is one. Gated exactly like the other
     * actions, and deliberately silent: no reply of its own (the cancelled run sends the terminal
     * "ERROR:cancelled" for the request it was answering), and arriving when nothing is running — or
     * after the export already finished — is a no-op, never an error and never a crash.
     *
     * Cancelling is only ever a raised flag: the export unwinds itself at the next entry boundary, so
     * no thread is interrupted mid-write and nothing kills the process.
     */
    private fun cancelExport(context: Context, intent: Intent) {
        val config = context.config
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
        val replyId = intent.getStringExtra(EXTRA_REPLY_ID)?.trim().orEmpty()
        Log.i(
            TAG,
            "received $ACTION_CANCEL_EXPORT: enabled=${config.automationEnabled}, " +
                "tokenLen=${token?.length ?: 0}, reply_id=$replyId"
        )
        if (!config.automationEnabled || !config.isAutomationTokenValid(token)) {
            Log.i(TAG, "cancel refused by the automation gate")
            return
        }

        // An absent reply_id means "whatever you are running"; a given one that names another request is
        // not ours to stop.
        val run = running.get()
        if (run == null || (replyId.isNotEmpty() && replyId != run.replyId)) {
            Log.i(TAG, "cancel: nothing to stop")
            return
        }
        run.cancelled = true
        Log.i(TAG, "cancel: signalled the export answering reply_id=${run.replyId}")
    }

    /**
     * Decide the request without doing any work: the gate first (the switch and the token report
     * distinctly, since they debug differently), then the instant category list, then the export's own
     * validation — so a malformed request is answered before anything is written.
     */
    private fun parse(context: Context, intent: Intent, action: String?): Request {
        val config = context.config
        val token = intent.getStringExtra(EXTRA_AUTOMATION_TOKEN)
        val itemsRaw = intent.getStringExtra(EXTRA_EXPORT_ITEMS)?.trim().orEmpty()
        val path = intent.getStringExtra(EXTRA_BACKUP_PATH)?.trim().orEmpty()
        val cats = parseItems(itemsRaw)
        Log.i(
            TAG,
            "received $action: enabled=${config.automationEnabled}, tokenLen=${token?.length ?: 0}, " +
                "items=$itemsRaw, path=$path"
        )

        return when {
            !config.automationEnabled -> Request.Done("ERROR:automation disabled")
            !config.isAutomationTokenValid(token) -> Request.Done("ERROR:bad token")
            action == ACTION_LIST_CATEGORIES -> Request.Done(categoryList(context))
            cats == null -> Request.Done("ERROR:unknown category in items: $itemsRaw")
            path.isNotEmpty() && !path.startsWith("/") ->
                Request.Done("ERROR:$EXTRA_BACKUP_PATH must be an absolute directory")

            else -> Request.Export(cats, path)
        }
    }

    /**
     * "OK:" plus one `id<TAB>label<TAB>parent<TAB>on|off` line per selectable item — the ids are exactly
     * the ones "items" accepts. The third field is the parent id and is EMPTY for a top-level item (the
     * fields are positional), so a sub-option follows its parent's line and can be rendered indented
     * under it; the fourth says whether the item starts ticked in the caller's picker.
     */
    private fun categoryList(context: Context): String =
        SettingsExport.Item.listed.joinToString(separator = "\n", prefix = "OK:") {
            val default = if (it.defaultOn) "on" else "off"
            "${it.id}\t${context.getString(it.labelRes)}\t${it.parentId.orEmpty()}\t$default"
        }

    /**
     * The requested items, or null when [itemsRaw] names an id we do not export. Absent or empty means
     * the default set — the items this app reports as starting ticked. A parent id on its own selects
     * that category's own data only — its parts are separate ids, so they are included only when asked
     * for.
     */
    private fun parseItems(itemsRaw: String): Set<SettingsExport.Item>? {
        val ids = itemsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SettingsExport.Item.defaultSelected
        val items = ids.mapNotNull { SettingsExport.Item.byId(it) }.toSet()
        return items.takeIf { it.size == ids.distinct().size }
    }

    /** Runs on a background thread; returns the single result line and never throws. */
    private fun export(
        context: Context,
        cats: Set<SettingsExport.Item>,
        path: String,
        progress: ThrottledProgress,
        run: RunningExport,
    ): String {
        val target = try {
            SettingsExport.headlessTarget(context, path) ?: return "ERROR:no-directory"
        } catch (e: Exception) {
            return storageError(path, e)
        }

        return try {
            // The count is a fallback for a destination we cannot stat; it is final once exportBlocking
            // returns, which is after the ZIP's central directory has been flushed.
            val counting = CountingOutputStream(target.open())
            counting.use {
                SettingsExport.exportBlocking(context, cats, it, progress.reporter) { run.cancelled }
            }
            val bytes = target.size().takeIf { it > 0 } ?: counting.count
            progress.final(cats.size.toLong())
            "OK:${target.displayPath}|$bytes|${humanSize(bytes)}|${cats.size} categories"
        } catch (ignored: ExportCancelledException) {
            discard(target)
            "ERROR:cancelled"
        } catch (e: Exception) {
            discard(target)
            storageError(path, e)
        }
    }

    /**
     * Take away the archive a cancelled or failed run left half-written — the whole point of the cancel
     * action is that the backup directory ends up exactly as it was found, with no short ZIP in it. This
     * app writes straight to the final name, so that file IS the partial; there is never a stray ".part".
     */
    private fun discard(target: SettingsExport.Target) {
        try {
            target.delete()
        } catch (e: Exception) {
            Log.w(TAG, "could not delete the partial export: $e")
        }
    }

    // An absolute path we were told to write but cannot needs All-files access; name that specifically,
    // since it is the one failure 白い熊 fixes with a toggle rather than a code change.
    private fun storageError(path: String, e: Exception): String {
        val noAllFiles = isRPlus() && !Environment.isExternalStorageManager()
        return if (path.isNotEmpty() && noAllFiles) "ERROR:no-storage-access" else "ERROR:${reason(e)}"
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    /** Display size for the reply line — the caller cannot stat the file, so we compute both forms. */
    private fun humanSize(bytes: Long): String = when {
        bytes < KILO -> "$bytes B"
        bytes < KILO * KILO -> "%.1f KB".format(Locale.ROOT, bytes / KILO)
        bytes < KILO * KILO * KILO -> "%.1f MB".format(Locale.ROOT, bytes / (KILO * KILO))
        else -> "%.2f GB".format(Locale.ROOT, bytes / (KILO * KILO * KILO))
    }

    private fun throttledProgress(
        context: Context,
        progressAction: String,
        replyPackage: String,
        replyId: String,
    ): ThrottledProgress {
        val appLabel = context.getString(R.string.app_launcher_name)
        val unit = context.getString(R.string.eim_progress_unit)

        fun send(current: Long, total: Long, unitName: String, text: String) {
            try {
                context.sendBroadcast(
                    Intent(progressAction)
                        .setPackage(replyPackage.ifEmpty { null })
                        .putExtra(EXTRA_REPLY_ID, replyId)
                        .putExtra(EXTRA_PROGRESS_APP, appLabel)
                        .putExtra(EXTRA_PROGRESS_TEXT, text)
                        .putExtra(EXTRA_PROGRESS_CURRENT, current)
                        .putExtra(EXTRA_PROGRESS_TOTAL, total)
                        .putExtra(EXTRA_PROGRESS_UNIT, unitName)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                )
            } catch (e: Exception) {
                Log.w(TAG, "progress broadcast failed: $e")
            }
        }

        var lastSent = 0L
        return ThrottledProgress(
            reporter = { current, total, unitName, text ->
                val now = System.currentTimeMillis()
                if (progressAction.isNotEmpty() && now - lastSent >= PROGRESS_THROTTLE_MS) {
                    lastSent = now
                    send(current, total, unitName, text)
                }
            },
            final = { categories ->
                if (progressAction.isNotEmpty()) {
                    send(categories, categories, unit, "$unit $categories/$categories")
                }
            },
        )
    }

    /** The throttled progress channel plus the unthrottled completion broadcast. */
    private class ThrottledProgress(val reporter: ProgressReporter, val final: (Long) -> Unit)

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }
}
