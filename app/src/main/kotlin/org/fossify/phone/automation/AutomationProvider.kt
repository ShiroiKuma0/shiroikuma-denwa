package org.fossify.phone.automation

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.fossify.phone.helpers.AutomationAuth
import org.fossify.phone.helpers.SettingsExport
import org.json.JSONArray
import org.json.JSONObject

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify.
 *
 * ## Why a provider and not the broadcast receiver next to it
 *
 * Two reasons, and the first is the whole point of the redesign.
 *
 * **A broadcast cannot tell you who sent it.** The old contract's answer to that was a shared secret,
 * which cannot survive the wipe this feature exists to recover from. A provider gets the caller's
 * identity from the framework for free — see [AutomationCallers] for what is actually checked and why
 * a package-name prefix would have been *weaker* than the token it replaced.
 *
 * **A list needs a synchronous answer.** 応用管理 draws a row per installed app before any export
 * exists; a broadcast round trip per app to fill a list is the wrong shape entirely.
 *
 * ## What does NOT happen here
 *
 * The payload. `call()` validates, starts a foreground service and returns — tens of megabytes over
 * minutes inside a binder call would block the caller, report no progress, refuse cancellation and
 * die silently if this process were killed. The bytes go through a file descriptor the caller opened,
 * and the terminal answer comes back on the broadcast the family already proved on EMUI
 * (see [org.fossify.phone.receivers.StateExportReceiver] for why no Binder ever rides a reply).
 *
 * ## Why a descriptor and not a path
 *
 * Because a backup is not a stable directory while it is being assembled. 応用管理 writes into a
 * temporary path and renames on commit; it encrypts and checksums **per file it knows about**. A file
 * this app dropped into that directory itself would be renamed out from under it, would sit in
 * plaintext inside an encrypted backup, and would be unverified rather than verified-and-failing
 * (応用管理, 2026-09-04). A descriptor is also a capability that **expires when it is closed**.
 *
 * It also means this app no longer needs `MANAGE_EXTERNAL_STORAGE` to be backed up — that permission
 * is still declared, but only for the receiver's absolute-`path` case, which predates this door.
 *
 * ## `import` lives ONLY here
 *
 * It never gets a broadcast action. An import overwrites this app's settings, and the receiver next
 * door is `exported="true"` with no permission — an import there would let any app on the phone wipe
 * any sister app.
 */
// The function count is ContentProvider's, not ours: five abstract methods this door never uses have
// to be overridden anyway, and the four it does use are one small named step each.
@Suppress("TooManyFunctions")
class AutomationProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /**
     * Every method answers a [Bundle] with [KEY_RESULT] — `OK…` or `ERROR:…`, the same vocabulary the
     * broadcast contract uses, so a caller has one grammar to parse rather than two.
     *
     * A refusal is returned, never thrown: an exception across a binder reaches the caller as a
     * `RuntimeException` with our stack trace in it, which tells 白い熊 nothing and tells a
     * misbehaving caller rather more than it should.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return fail("ERROR:not ready")

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        when (val verdict = AutomationCallers.verify(ctx, callingPackage)) {
            is AutomationCallers.Verdict.Refused -> return fail(verdict.why)
            AutomationCallers.Verdict.Allowed -> Unit
        }
        // Then this app's own switches — a token is ignored unless this app asks for one.
        AutomationAuth.refuse(ctx, extras?.getString(KEY_TOKEN))?.let { return fail(it) }

        return when (method) {
            METHOD_DESCRIBE -> ok(describe(ctx))
            METHOD_EXPORT -> start(ctx, extras, importing = false)
            METHOD_IMPORT -> start(ctx, extras, importing = true)
            METHOD_CANCEL -> {
                AutomationJobs.cancel(extras?.getString(KEY_JOB_ID))
                ok("OK:cancelled")
            }

            else -> fail("ERROR:unknown method: $method")
        }
    }

    /**
     * What this app would export, answered without exporting anything.
     *
     * Returned from the call rather than written into the archive, deliberately: 応用管理 must draw a
     * row before an export exists, and at restore must judge compatibility **before** streaming into
     * an app that would reject it — which it cannot do if the header is buried inside an encrypted
     * archive (応用管理, 2026-09-04).
     *
     * Built with [JSONObject] rather than string interpolation because `contains` carries localized
     * labels: a quote or a backslash in a translation would otherwise hand the caller broken JSON.
     */
    private fun describe(ctx: Context): String {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION") // longVersionCode is API 28; this app's minSdk is 26
        val versionCode = info.versionCode
        val contains = SettingsExport.Item.listed
            .filter { it.defaultOn }
            .map { ctx.getString(it.shortLabelRes) }
        val header = JSONObject()
            .put("app_id", ctx.packageName)
            .put("version_code", versionCode)
            .put("version_name", info.versionName.orEmpty())
            .put("format", FORMAT)
            .put("min_format_readable", MIN_FORMAT_READABLE)
            // This app writes its own defaults lazily and imports by MERGING per key, so an archive
            // applied to a never-launched install lands exactly as it would on a launched one.
            .put("requires_launch_first", false)
            .put("contains", JSONArray(contains))
        return "OK:$header"
    }

    /**
     * Hand the descriptor to a foreground service and get out of the way.
     *
     * The descriptor is **duplicated** before it leaves this method. The one in [extras] belongs to
     * the binder transaction and is closed the moment `call()` returns; a service reading it
     * afterwards would find it shut. That is a bug you only see under load, so it is not left to the
     * service to remember.
     *
     * A service that will not start is answered, not thrown: on this platform a background
     * foreground-service start can be refused outright, and the caller needs a line it can show on a
     * failed row rather than our stack trace. The duplicate is closed on that path — a leaked
     * descriptor holds the caller's file open, and a caller cannot checksum or encrypt a file that is
     * still open.
     */
    private fun start(ctx: Context, extras: Bundle?, importing: Boolean): Bundle {
        @Suppress("DEPRECATION") // getParcelable(String, Class) is API 33; this app's minSdk is 26
        val fd = extras?.getParcelable<ParcelFileDescriptor>(KEY_FD)
            ?: return fail("ERROR:no descriptor")
        val dup = runCatching { fd.dup() }.getOrNull() ?: return fail("ERROR:descriptor unusable")
        val jobId = AutomationJobs.begin()
        return runCatching {
            AutomationDataService.start(ctx, jobId, dup, importing, extras)
            ok("OK:$jobId")
        }.getOrElse { t ->
            AutomationJobs.finish(jobId)
            runCatching { dup.close() }
            fail("ERROR:${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun ok(result: String) = Bundle().apply { putString(KEY_RESULT, result) }

    private fun fail(why: String) = Bundle().apply { putString(KEY_RESULT, why) }

    // A provider that is only ever call()ed still has to answer these. Refusing loudly beats returning
    // an empty cursor, which reads downstream as "there is no data" rather than "wrong door".
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? =
        throw UnsupportedOperationException("automation is call() only")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("automation is call() only")

    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int =
        throw UnsupportedOperationException("automation is call() only")

    companion object {
        const val METHOD_DESCRIBE = "describe"
        const val METHOD_EXPORT = "export"
        const val METHOD_IMPORT = "import"
        const val METHOD_CANCEL = "cancel"

        const val KEY_RESULT = "result"
        const val KEY_FD = "fd"
        const val KEY_TOKEN = "token"
        const val KEY_JOB_ID = "job_id"
        const val KEY_ITEMS = "items"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_REPLY_PACKAGE = "reply_package"
        const val KEY_PROGRESS_ACTION = "progress_action"

        /**
         * This app's archive format, which is [SettingsExport.VERSION] and must never be a second
         * number that can drift from it — the header describes the ZIP the export actually writes.
         * Bumped when an older build could no longer read what we write.
         */
        const val FORMAT = SettingsExport.VERSION

        /**
         * The oldest archive this build can still read.
         *
         * Version skew has a direction: old data into a newer app is normally fine, because an app
         * migrates its own storage; newer data into an older app is not. This field is what lets a
         * caller refuse the second case at discovery time, before anything is streamed. Import here
         * merges per key and skips categories it does not recognise, so every format this app has
         * ever written stays readable.
         */
        const val MIN_FORMAT_READABLE = 1
    }
}
