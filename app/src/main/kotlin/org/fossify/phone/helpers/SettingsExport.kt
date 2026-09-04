package org.fossify.phone.helpers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.commons.helpers.ACCENT_COLOR
import org.fossify.commons.helpers.APP_ICON_COLOR
import org.fossify.commons.helpers.BACKGROUND_COLOR
import org.fossify.commons.helpers.CUSTOM_ACCENT_COLOR
import org.fossify.commons.helpers.CUSTOM_APP_ICON_COLOR
import org.fossify.commons.helpers.CUSTOM_BACKGROUND_COLOR
import org.fossify.commons.helpers.CUSTOM_PRIMARY_COLOR
import org.fossify.commons.helpers.CUSTOM_TEXT_COLOR
import org.fossify.commons.helpers.FONT_SIZE
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.IS_SYSTEM_THEME_ENABLED
import org.fossify.commons.helpers.LAST_VERSION
import org.fossify.commons.helpers.PRIMARY_COLOR
import org.fossify.commons.helpers.TEXT_COLOR
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.phone.BuildConfig
import org.fossify.phone.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * (current, total, unit, text) — the progress channel of a headless export. Real counts only: [text] is
 * the numbers-first line a caller displays ("区分 3/5 — 外観"), never a percentage.
 */
typealias ProgressReporter = (current: Long, total: Long, unit: String, text: String) -> Unit

/**
 * Thrown out of [SettingsExport.exportBlocking] when the caller's cancel signal has gone true — the
 * export unwound at an entry boundary rather than finishing. The caller answers `ERROR:cancelled` and
 * removes what was half-written.
 */
class ExportCancelledException : Exception("cancelled")

/**
 * The category-based settings export/import — everything this app can be set to, in one ZIP.
 *
 * The archive holds plain JSON files, one per category, plus the imported fonts as real files under
 * `fonts/`, and a `manifest.json` naming the format, version and the categories present. **One ZIP per
 * export, always**: however many categories are picked, they are entries inside a single archive, named
 * by the 白い熊 family convention — `shiroikuma-denwa_<yyyy-MM-dd_HH-mm-ss>.zip`, no version and no
 * suffix, so every sister app's backups sort and read uniformly in one shared directory.
 *
 * Everything the app stores lives in one SharedPreferences file, so the categories are a partition of
 * its keys ([itemForKey]) rather than separate stores: the appearance keys (the granular theme slots,
 * dimensions and per-element fonts) go one way, speed dial and the per-contact SIM choices each get
 * their own file, and everything else — the behaviour settings — is the remainder. An unknown key
 * therefore always lands somewhere, so a setting added later is exported without touching this file.
 *
 * Future-proof by construction: import applies only the selected categories, skips absent files, and
 * merges prefs per key (a missing key keeps its current value; unknown keys are ignored), so exports
 * and app versions can drift apart without breaking a restore.
 */
@Suppress("TooManyFunctions") // one small helper per ZIP entry and per direction, by design
object SettingsExport {

    const val FORMAT = "denwa-export"
    const val VERSION = 1

    // The app's English dash-separated name — the repo / APK basename, never the Japanese display name.
    // Deliberately version-free: a backup is identified by when it was taken, not by the build that
    // wrote it (the build is recorded inside, as manifest.json's appVersion).
    const val EXPORT_PREFIX = "shiroikuma-denwa"

    /**
     * Everything independently selectable in an export or import: the top-level categories plus their
     * parts (sub-options). `id` is what the automation contract accepts in its "items" extra and what
     * `LIST_CATEGORIES` reports; for a top-level item it is also the stable name its data carries inside
     * the ZIP. A part names its parent through [parentId] and is dotted after it ("settings.speed_dial")
     * — selecting a parent WITHOUT its parts means that category's own data only. [labelRes] is the
     * descriptive label shown in the pickers (in-app and in 自由作業盤), [shortLabelRes] the bare noun
     * used in progress lines and summaries.
     *
     * [defaultOn] is the fourth `LIST_CATEGORIES` field: whether the item starts TICKED in a picker,
     * and what an absent "items" extra therefore means. It is this app's answer to state rather than
     * the picker's to guess — everything here is small and not re-creatable from anything else, so
     * every item is `on`; the `off` case is for bulk data a restore could regenerate (downloaded media,
     * a thumbnail cache), which this app has none of.
     */
    enum class Item(
        val id: String,
        val parentId: String?,
        @StringRes val labelRes: Int,
        @StringRes val shortLabelRes: Int,
        val defaultOn: Boolean = true,
    ) {
        SETTINGS("settings", null, R.string.eim_cat_settings, R.string.eim_cat_settings_short),
        SETTINGS_SPEED_DIAL(
            "settings.speed_dial", "settings", R.string.eim_cat_speed_dial, R.string.eim_cat_speed_dial_short
        ),
        SETTINGS_SIM("settings.sim", "settings", R.string.eim_cat_sim, R.string.eim_cat_sim_short),
        APPEARANCE("appearance", null, R.string.eim_cat_appearance, R.string.eim_cat_appearance_short),
        APPEARANCE_FONTS("appearance.fonts", "appearance", R.string.eim_cat_fonts, R.string.eim_cat_fonts_short);

        val isTopLevel: Boolean get() = parentId == null

        /** The parts of this item, in declaration order — empty for a leaf. */
        val children: List<Item> get() = entries.filter { it.parentId == id }

        /** The ZIP entry this item's data is written to; the fonts are files, not one entry. */
        val entryName: String get() = id.substringAfterLast('.') + ".json"

        companion object {
            fun byId(id: String): Item? = entries.firstOrNull { it.id == id }

            /** Parents first, each followed by its own parts — the order both pickers render. */
            val listed: List<Item> get() = entries.filter { it.isTopLevel }.flatMap { listOf(it) + it.children }

            /**
             * The set every picker starts on, and what an absent "items" extra means: each [defaultOn]
             * item. Both the in-app panel and the automation reply seed from here, so the two agree.
             */
            val defaultSelected: Set<Item> get() = entries.filter { it.defaultOn }.toSet()
        }
    }

    // The configured export folder: a persisted SAF tree grant kept in a device-local prefs file that is
    // itself never exported. Shared by the Export/Import panel and the headless automation export, so
    // both always resolve the same folder.
    private const val EXIM_PREFS = "denwa_eximport"
    private const val EXIM_DIR_URI = "dir_uri"

    // Device-local keys never carried across an export: the automation shared secret AND both of its
    // switches (each device owns its own security state — a restore must never silently open or close
    // the automation door, demand a token the caller has not got, or overwrite the token itself), plus
    // the one-time upgrade marker.
    private val PREFS_EXCLUDE =
        setOf(AUTOMATION_TOKEN, AUTOMATION_ENABLED, AUTOMATION_REQUIRE_TOKEN, LAST_VERSION)

    // Appearance = the granular theme slots and dimensions, the per-element fonts, and the commons
    // colour/theme keys the fork's palette sits on top of. Everything else in the prefs file is a
    // behaviour setting, so no list of "settings keys" has to be kept in step with the settings page.
    private val APPEARANCE_PREFIXES = listOf("theme_", FONT_FAMILY_PREFIX, FONT_WEIGHT_PREFIX, FONT_SIZE_PREFIX)
    private val APPEARANCE_KEYS = setOf(
        TEXT_COLOR, BACKGROUND_COLOR, PRIMARY_COLOR, ACCENT_COLOR, APP_ICON_COLOR,
        CUSTOM_TEXT_COLOR, CUSTOM_BACKGROUND_COLOR, CUSTOM_PRIMARY_COLOR, CUSTOM_ACCENT_COLOR,
        CUSTOM_APP_ICON_COLOR, IS_SYSTEM_THEME_ENABLED, FONT_SIZE,
        SIM_1_COLOR, SIM_2_COLOR, PURE_YELLOW_MIGRATED,
    )

    /** "shiroikuma-denwa_2026-07-25_18-58-23.zip" — app name, then when it was taken. */
    fun exportFileName(): String =
        EXPORT_PREFIX + "_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".zip"

    /** True if [name] is one of this app's backups — what the "last export" line scans a folder for. */
    fun isExportFileName(name: String?): Boolean =
        name != null && name.startsWith(EXPORT_PREFIX) && name.endsWith(".zip")

    /** Which category owns a preference key. Unknown keys fall through to the behaviour settings. */
    private fun itemForKey(key: String): Item = when {
        key == SPEED_DIAL -> Item.SETTINGS_SPEED_DIAL
        key.startsWith(REMEMBER_SIM_PREFIX) -> Item.SETTINGS_SIM
        key in APPEARANCE_KEYS || APPEARANCE_PREFIXES.any { key.startsWith(it) } -> Item.APPEARANCE
        else -> Item.SETTINGS
    }

    // ---------------------------------------------------------------------------------------------
    // EXPORT
    // ---------------------------------------------------------------------------------------------

    /**
     * Write a ZIP of the selected categories. [openOut] runs on a background thread; [done] reports a
     * short human summary or the failure, on that background thread. A thin wrapper over
     * [exportBlocking] — the Export/Import panel and the automation receiver share one export core.
     */
    fun export(context: Context, items: Set<Item>, openOut: () -> OutputStream?, done: (Result<String>) -> Unit) {
        ensureBackgroundThread {
            done(runCatching { (openOut() ?: error("no output stream")).use { exportBlocking(context, items, it) } })
        }
    }

    /**
     * The export core, callable headlessly — no Activity, no user interaction. Gathers the selected
     * categories, writes the ZIP into [out] and reports real counts through [onProgress] (unthrottled;
     * the caller decides how often to surface them). Blocking, so call it on a background thread, and it
     * throws on every failure so both callers get a single error path. Returns a short human summary.
     *
     * [isCancelled] is polled at every entry boundary — never mid-write — and unwinds the export with
     * [ExportCancelledException] when it goes true. The caller owns what is left behind: the ZIP it was
     * writing is short and must be deleted, never kept.
     */
    fun exportBlocking(
        context: Context,
        items: Set<Item>,
        out: OutputStream,
        onProgress: ProgressReporter = { _, _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): String {
        // Declaration order, not the caller's, so a ZIP's contents don't depend on how the set was built.
        val ordered = Item.listed.filter { it in items }
        require(ordered.isNotEmpty()) { "nothing selected" }
        val total = ordered.size.toLong()
        val unit = context.getString(R.string.eim_progress_unit)
        val prefs = context.getSharedPrefs().all
        val parts = mutableListOf<String>()

        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("app", context.packageName)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdTs", System.currentTimeMillis())
                .put("categories", JSONArray(ordered.map { it.id }))
            writeEntry(zip, "manifest.json", manifest.toString(2).toByteArray())

            ordered.forEachIndexed { index, item ->
                if (isCancelled()) throw ExportCancelledException()
                val done = index + 1L
                onProgress(done, total, unit, "$unit $done/$total — ${context.getString(item.shortLabelRes)}")
                val count = if (item == Item.APPEARANCE_FONTS) {
                    exportFonts(context, zip, isCancelled)
                } else {
                    val slice = prefs.filterKeys { itemForKey(it) == item && it !in PREFS_EXCLUDE }
                    writeEntry(zip, item.entryName, encodePrefs(slice).toByteArray())
                    slice.size
                }
                parts += "${context.getString(item.shortLabelRes)}: $count"
            }
        }
        return parts.joinToString("・")
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }

    /** Every pref key as a typed JSON entry, so import can round-trip it exactly. */
    private fun encodePrefs(values: Map<String, Any?>): String {
        val obj = JSONObject()
        for ((k, v) in values) {
            val e = JSONObject()
            when (v) {
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is String -> { e.put("t", "s"); e.put("v", v) }
                is Set<*> -> { e.put("t", "ss"); e.put("v", JSONArray(v.map { it.toString() })) }
                else -> continue
            }
            obj.put(k, e)
        }
        return obj.toString(2)
    }

    // The one category with an unbounded number of entries, so it polls the cancel signal per file too.
    private fun exportFonts(context: Context, zip: ZipOutputStream, isCancelled: () -> Boolean): Int {
        var count = 0
        FontHelper.getFontsDir(context).listFiles()?.forEach { f ->
            if (isCancelled()) throw ExportCancelledException()
            if (f.isFile) {
                writeEntry(zip, "fonts/${f.name}", f.readBytes())
                count++
            }
        }
        return count
    }

    // ---------------------------------------------------------------------------------------------
    // HEADLESS DESTINATION (automation)
    // ---------------------------------------------------------------------------------------------

    /**
     * A resolved headless export destination: where to write, what to call it, how big it ended up —
     * and how to take it away again. [delete] removes the file this target names, which is how a
     * cancelled or failed run leaves the backup directory exactly as it found it: this app writes
     * straight to the final name rather than to a ".part", so the half-written archive IS that file.
     */
    class Target(
        val displayPath: String,
        val open: () -> OutputStream,
        val size: () -> Long,
        val delete: () -> Unit,
    )

    /** The configured export folder (a persisted SAF tree), or null when none was ever picked. */
    fun configuredDir(context: Context): DocumentFile? =
        configuredDirUri(context)
            ?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
            ?.takeIf { it.isDirectory }

    /** The raw persisted tree URI, for the Export/Import panel's folder picker. */
    fun configuredDirUri(context: Context): Uri? =
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE).getString(EXIM_DIR_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setConfiguredDirUri(context: Context, uri: Uri) {
        context.getSharedPreferences(EXIM_PREFS, Context.MODE_PRIVATE).edit()
            .putString(EXIM_DIR_URI, uri.toString())
            .apply()
    }

    /** The newest backup of ours in the configured folder, or null when there is no folder or none yet. */
    fun lastExportTime(context: Context): Long? {
        val dir = configuredDir(context) ?: return null
        return runCatching {
            dir.listFiles().filter { it.isFile && isExportFileName(it.name) }.maxOfOrNull { it.lastModified() }
        }.getOrNull()?.takeIf { it > 0 }
    }

    /**
     * Resolve where a headless export writes. Directory precedence, per the automation contract:
     * [pathOverride] (an absolute directory, created if missing) → the configured export folder →
     * null, which the caller reports as "no-directory".
     */
    fun headlessTarget(context: Context, pathOverride: String): Target? {
        val name = exportFileName()
        if (pathOverride.isNotEmpty()) {
            // /sdcard is a symlink; normalize it so the storage checks below see the real path.
            val primary = Environment.getExternalStorageDirectory().absolutePath
            val file = File(pathOverride.replaceFirst(Regex("^/sdcard"), primary), name)
            return Target(
                displayPath = file.absolutePath,
                open = { openAbsolute(file) },
                size = { file.length() },
                delete = { file.delete() },
            )
        }

        val dir = configuredDir(context) ?: return null
        val file = dir.createFile("application/zip", name) ?: error("cannot create a file in ${dir.name}")
        return Target(
            displayPath = displayPathOf(file.uri),
            open = { context.contentResolver.openOutputStream(file.uri) ?: error("cannot open ${file.uri}") },
            size = { file.length() },
            delete = { file.delete() },
        )
    }

    /**
     * Write to a caller-supplied absolute path. On API 30+ anything under primary storage needs
     * All-files access; say so distinctly, since that is the one failure 白い熊 fixes with a toggle
     * rather than a code change.
     */
    private fun openAbsolute(file: File): OutputStream {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        val underPrimary = file.absolutePath.startsWith("$primary/")
        if (isRPlus() && underPrimary && !Environment.isExternalStorageManager()) {
            error("no-storage-access")
        }
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    /**
     * Best-effort filesystem path for a SAF document ("primary:〇/x.zip" → "/storage/emulated/0/〇/x.zip"),
     * so the automation reply names a path 白い熊 can actually open. Falls back to the URI.
     */
    private fun displayPathOf(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return uri.toString()
        val volume = docId.substringBefore(':', "")
        val relative = docId.substringAfter(':', "")
        if (volume.isEmpty() || relative.isEmpty()) return uri.toString()
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return "$root/$relative"
    }

    // ---------------------------------------------------------------------------------------------
    // IMPORT
    // ---------------------------------------------------------------------------------------------

    /** Items present in a ZIP (from its manifest, falling back to the entries actually found). */
    fun categoriesIn(zip: ByteArray): Set<Item> {
        val files = readZip(zip)
        files["manifest.json"]?.let { mf ->
            val cats = runCatching { JSONObject(mf.decodeToString()).optJSONArray("categories") }.getOrNull()
            if (cats != null) {
                val set = (0 until cats.length()).mapNotNull { Item.byId(cats.optString(it)) }.toSet()
                if (set.isNotEmpty()) return set
            }
        }
        return Item.entries.filter { hasData(it, files) }.toSet()
    }

    private fun hasData(item: Item, files: Map<String, ByteArray>) = when (item) {
        Item.APPEARANCE_FONTS -> files.keys.any { it.startsWith("fonts/") }
        else -> files.containsKey(item.entryName)
    }

    /**
     * Apply the selected categories from a ZIP; absent ones are skipped. Runs on a background thread;
     * [done] reports a short per-category summary or the failure, on that background thread.
     */
    fun import(context: Context, zip: ByteArray, items: Set<Item>, done: (Result<String>) -> Unit) {
        ensureBackgroundThread {
            done(runCatching { importBlocking(context, zip, items) })
        }
    }

    /**
     * The import core, callable headlessly — the Export/Import panel reaches it through [import], and
     * the data door ([org.fossify.phone.automation.AutomationDataService]) calls it directly, because a
     * provider-started job already owns a background thread and needs the summary as a return value
     * rather than in a callback. Blocking, and it throws on every failure so both callers have one
     * error path.
     */
    fun importBlocking(context: Context, zip: ByteArray, items: Set<Item>): String {
        val files = readZip(zip)
        require(categoriesIn(zip).isNotEmpty()) { context.getString(R.string.eim_import_none) }
        val parts = mutableListOf<String>()

        for (item in Item.listed.filter { it in items }) {
            val count = if (item == Item.APPEARANCE_FONTS) {
                importFonts(context, files)
            } else {
                val data = files[item.entryName] ?: continue
                // Merge — never clear — so unrelated and device-local keys survive a partial restore.
                decodeInto(context.getSharedPrefs(), data.decodeToString())
            }
            if (count > 0 || item != Item.APPEARANCE_FONTS) {
                parts += "${context.getString(item.shortLabelRes)}: $count"
            }
        }
        return if (parts.isEmpty()) context.getString(R.string.eim_import_none) else parts.joinToString("・")
    }

    /** Apply a typed pref dump onto [sp]. Returns the applied-key count. */
    private fun decodeInto(sp: SharedPreferences, json: String): Int {
        val obj = JSONObject(json)
        val editor = sp.edit()
        val applied = obj.keys().asSequence()
            .filter { it !in PREFS_EXCLUDE }
            .count { key ->
                val entry = obj.optJSONObject(key)
                entry != null && putTyped(editor, key, entry)
            }
        editor.commit()
        return applied
    }

    /** Write one typed entry; false for a type this build does not know, which is simply skipped. */
    private fun putTyped(editor: SharedPreferences.Editor, key: String, entry: JSONObject): Boolean {
        when (entry.optString("t")) {
            "b" -> editor.putBoolean(key, entry.optBoolean("v"))
            "i" -> editor.putInt(key, entry.optInt("v"))
            "l" -> editor.putLong(key, entry.optLong("v"))
            "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
            "s" -> editor.putString(key, entry.optString("v"))
            "ss" -> {
                val arr = entry.optJSONArray("v") ?: JSONArray()
                editor.putStringSet(key, (0 until arr.length()).map { arr.optString(it) }.toHashSet())
            }

            else -> return false
        }
        return true
    }

    private fun importFonts(context: Context, files: Map<String, ByteArray>): Int {
        val dir = FontHelper.getFontsDir(context).apply { mkdirs() }
        // Basename only — an entry name must never escape the fonts directory.
        val fonts = files.filterKeys { it.startsWith("fonts/") }
            .mapKeys { it.key.removePrefix("fonts/").substringAfterLast('/') }
            .filterKeys { it.isNotEmpty() }
        fonts.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        return fonts.size
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return map
    }
}
