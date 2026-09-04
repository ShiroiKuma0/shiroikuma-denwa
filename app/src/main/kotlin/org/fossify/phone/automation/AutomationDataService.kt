package org.fossify.phone.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isOreoPlus
import org.fossify.phone.R
import org.fossify.phone.helpers.EXTRA_PROGRESS_APP
import org.fossify.phone.helpers.EXTRA_PROGRESS_CURRENT
import org.fossify.phone.helpers.EXTRA_PROGRESS_TEXT
import org.fossify.phone.helpers.EXTRA_PROGRESS_TOTAL
import org.fossify.phone.helpers.EXTRA_PROGRESS_UNIT
import org.fossify.phone.helpers.EXTRA_REPLY_ID
import org.fossify.phone.helpers.ExportCancelledException
import org.fossify.phone.helpers.PROGRESS_THROTTLE_MS
import org.fossify.phone.helpers.ProgressReporter
import org.fossify.phone.helpers.SettingsExport
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a data export or import started at [AutomationProvider] actually runs.
 *
 * ## Why a foreground service and not the provider call
 *
 * The call returns in milliseconds; this can run for minutes. Two hard reasons it cannot be done
 * anywhere cheaper:
 *
 * - **A binder call holds the caller.** 応用管理 is drawing a list; a multi-minute synchronous call
 *   would freeze its UI, report no progress and refuse cancellation.
 * - **A backgrounded app writing for minutes is frozen mid-stream on this phone**, which yields a
 *   truncated archive underneath a success reply — the worst possible failure, because it is
 *   indistinguishable from a good backup until the day it is restored (応用管理, 2026-09-04).
 *
 * ## The descriptor
 *
 * Already duplicated by [AutomationProvider] before it got here, because the original belongs to the
 * binder transaction and is closed the moment `call()` returns. This service owns the copy and closes
 * it in a `finally` — leaking one would hold the caller's file open indefinitely, and the caller
 * cannot checksum or encrypt a file that is still open.
 */
// Broad catches on purpose: a request from another app must always come back as one ERROR line rather
// than take this service down with an exception nobody on the far side can read.
@Suppress("TooGenericExceptionCaught")
class AutomationDataService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB)
        val importing = intent?.getBooleanExtra(EXTRA_IMPORTING, false) == true

        // BEFORE any decision to stop, and unconditionally. Once startForegroundService has been
        // called the platform demands startForeground within 5 s WHATEVER this service then decides,
        // and enforces it by killing the process with ForegroundServiceDidNotStartInTimeException. So
        // returning early on a missing intent or a drained handover entry would not ignore a caller
        // retrying with a stale job id — it would kill 白い熊 電話 (contract v2, 2026-09-04).
        //
        // Guarded, because the start itself can be refused: on API 31+ a service started from the
        // background — which a provider call() always is — may be denied outright unless the app is
        // exempt from battery optimisation.
        val foreground = runCatching { startForeground(NOTIFICATION_ID, notification(importing)) }

        if (intent == null || jobId == null) return stop(startId)
        val fd = HANDOVER.remove(jobId) ?: return stop(startId)

        val reply = replyChannel(intent, jobId)
        foreground.exceptionOrNull()?.let { e ->
            // The descriptor has already left HANDOVER, so nothing else would ever close it, and the
            // caller is waiting for an answer it would otherwise never get.
            runCatching { fd.close() }
            AutomationJobs.finish(jobId)
            reply("ERROR:cannot go foreground: ${e.javaClass.simpleName}")
            return stop(startId)
        }

        ensureBackgroundThread {
            try {
                if (importing) runImport(fd, reply) else runExport(intent, jobId, fd, reply)
            } catch (ignored: ExportCancelledException) {
                reply("ERROR:cancelled")
            } catch (e: Exception) {
                reply("ERROR:${reason(e)}")
            } finally {
                // Idempotent: the stream wrappers below already closed it on every normal path.
                runCatching { fd.close() }
                AutomationJobs.finish(jobId)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Exactly one terminal answer per job, whatever path gets here — a synchronous failure and an
     * asynchronous success must never both fire. The same guard the broadcast contract has carried
     * since the first sister app.
     *
     * A fresh plain broadcast, never a Binder: EMUI will not reliably carry a live `ResultReceiver` /
     * `PendingIntent` / `Messenger` into another app's manifest receiver and may drop the broadcast
     * carrying one outright (verified on 白い熊's Mate XT, 2026-07-23).
     * [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] because a caller that has been backgrounded — or, on a
     * clean phone, never launched — otherwise never hears the answer.
     */
    private fun replyChannel(intent: Intent, jobId: String): (String) -> Unit {
        val replyAction = intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION).orEmpty()
        val replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE).orEmpty()
        val replied = AtomicBoolean(false)
        return { result ->
            // No package to aim at means nobody can hear it: since API 26 an implicit broadcast reaches
            // no manifest-declared receiver, so setPackage(null) is not a WIDER send, it is no send at
            // all. Skip it rather than pretend, and keep the one-reply guard honest either way.
            if (replied.compareAndSet(false, true) && replyAction.isNotEmpty() && replyPackage.isNotEmpty()) {
                runCatching {
                    sendBroadcast(
                        Intent(replyAction)
                            .setPackage(replyPackage)
                            .putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                            .putExtra(AutomationProvider.KEY_RESULT, result)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                }
            }
        }
    }

    /**
     * Write the selected categories straight into the caller's descriptor.
     *
     * The byte count is taken as it goes rather than stat'ed afterwards: the caller owns the file and
     * we may not be able to see it at all — it can be an anonymous pipe, or a descriptor into a
     * directory this app cannot list.
     */
    private fun runExport(intent: Intent, jobId: String, fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val items = resolve(intent.getStringExtra(AutomationProvider.KEY_ITEMS))
        if (items == null) {
            reply("ERROR:unknown category in items: ${intent.getStringExtra(AutomationProvider.KEY_ITEMS)}")
            return
        }
        val progress = throttledProgress(
            progressAction = intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION),
            replyPackage = intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE).orEmpty(),
            jobId = jobId,
        )
        val counting = CountingOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(fd))
        counting.use {
            SettingsExport.exportBlocking(this, items, it, progress) { AutomationJobs.isCancelled(jobId) }
        }
        reply("OK:${counting.count}|${items.size} categories")
    }

    /**
     * Read the whole archive before touching anything.
     *
     * [SettingsExport] wants the bytes, and that is the right shape here for a reason beyond
     * convenience: a partial read that failed halfway would import half an archive, and a
     * half-restored app is worse than one that refused.
     */
    private fun runImport(fd: ParcelFileDescriptor, reply: (String) -> Unit) {
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        if (bytes.isEmpty()) {
            reply("ERROR:empty archive")
            return
        }
        // Every category the archive actually carries, not every category we know about: asking for
        // one the archive lacks is how a restore ends up reporting success over nothing.
        val present = SettingsExport.categoriesIn(bytes)
        if (present.isEmpty()) {
            reply("ERROR:archive carries no categories")
            return
        }
        val summary = SettingsExport.importBlocking(this, bytes, present)
        // 応用管理 force-stops us straight after this, deliberately and on its side: a running process
        // writes its cached SharedPreferences back out at orderly shutdown and would silently undo the
        // import that just happened.
        reply("OK:$summary")
    }

    /**
     * The requested ids, or null when one of them is not ours. Absent or empty means this app's
     * default set — what [AutomationProvider]'s `describe` reports, not everything it could write.
     */
    private fun resolve(items: String?): Set<SettingsExport.Item>? {
        val ids = items.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) return SettingsExport.Item.defaultSelected
        val found = ids.mapNotNull { SettingsExport.Item.byId(it) }.toSet()
        return found.takeIf { it.size == ids.distinct().size }
    }

    /**
     * Real counts, never a percentage, throttled to one broadcast per [PROGRESS_THROTTLE_MS].
     *
     * `total` is the number of categories actually being written, and `current` the 1-based position
     * of the one being written now — which is what lets the panel read the pair as a walk through the
     * item list it drew and move the highlight, rather than printing a number beside nothing.
     * `reply_id` carries the job id as well as `job_id` does, so a caller keying on either finds it.
     *
     * [replyPackage] is not optional dressing: WITHOUT `setPackage` this is an implicit broadcast, and
     * since API 26 an implicit broadcast is not delivered to manifest-declared receivers at all. The
     * export would run, finish and report its terminal reply correctly while every progress line was
     * silently dropped — the panel would show a row that never moves and then jumps to done.
     */
    private fun throttledProgress(progressAction: String?, replyPackage: String, jobId: String): ProgressReporter {
        if (progressAction.isNullOrEmpty() || replyPackage.isEmpty()) return { _, _, _, _ -> }
        val appLabel = getString(R.string.app_launcher_name)
        var lastSent = 0L
        return { current, total, unit, text ->
            val now = System.currentTimeMillis()
            if (now - lastSent >= PROGRESS_THROTTLE_MS || current == total) {
                lastSent = now
                runCatching {
                    sendBroadcast(
                        Intent(progressAction)
                            .setPackage(replyPackage)
                            .putExtra(AutomationProvider.KEY_JOB_ID, jobId)
                            .putExtra(EXTRA_REPLY_ID, jobId)
                            .putExtra(EXTRA_PROGRESS_APP, appLabel)
                            .putExtra(EXTRA_PROGRESS_TEXT, text)
                            .putExtra(EXTRA_PROGRESS_CURRENT, current)
                            .putExtra(EXTRA_PROGRESS_TOTAL, total)
                            .putExtra(EXTRA_PROGRESS_UNIT, unit)
                            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    )
                }
            }
        }
    }

    private fun notification(importing: Boolean): Notification {
        if (isOreoPlus()) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.automation), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val title = if (importing) R.string.automation_importing else R.string.automation_exporting
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(title))
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setOngoing(true)
            .build()
    }

    private fun reason(e: Throwable): String =
        (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).replace('\n', ' ')

    /** Always paired with the unconditional startForeground above, so nothing is left in that state. */
    private fun stop(startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

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

    companion object {
        private const val CHANNEL = "automation_data"
        private const val NOTIFICATION_ID = 9714
        private const val EXTRA_JOB = "job"
        private const val EXTRA_IMPORTING = "importing"

        /**
         * The descriptor's way across, because an Intent is the wrong vehicle for one.
         *
         * A [ParcelFileDescriptor] in an Intent extra is duplicated by the system on delivery and the
         * copy's lifetime stops being ours to reason about. Handing it through a map keyed by the job
         * id keeps exactly one open descriptor with exactly one owner — this service, which closes it
         * in a `finally`.
         */
        private val HANDOVER = ConcurrentHashMap<String, ParcelFileDescriptor>()

        /**
         * Throws if the platform refuses the start; [AutomationProvider] answers that as an `ERROR:`
         * line and closes the descriptor, rather than letting it cross the binder as a stack trace.
         * The handover entry is removed on that path too, so a refused start leaks nothing.
         */
        fun start(context: Context, jobId: String, fd: ParcelFileDescriptor, importing: Boolean, extras: Bundle?) {
            HANDOVER[jobId] = fd
            try {
                context.startForegroundService(
                    Intent(context, AutomationDataService::class.java)
                        .putExtra(EXTRA_JOB, jobId)
                        .putExtra(EXTRA_IMPORTING, importing)
                        .putExtra(AutomationProvider.KEY_ITEMS, extras?.getString(AutomationProvider.KEY_ITEMS))
                        .putExtra(
                            AutomationProvider.KEY_PROGRESS_ACTION,
                            extras?.getString(AutomationProvider.KEY_PROGRESS_ACTION)
                        )
                        .putExtra(
                            AutomationProvider.KEY_REPLY_ACTION,
                            extras?.getString(AutomationProvider.KEY_REPLY_ACTION)
                        )
                        .putExtra(
                            AutomationProvider.KEY_REPLY_PACKAGE,
                            extras?.getString(AutomationProvider.KEY_REPLY_PACKAGE)
                        )
                )
            } catch (t: Throwable) {
                HANDOVER.remove(jobId)
                throw t
            }
        }
    }
}
