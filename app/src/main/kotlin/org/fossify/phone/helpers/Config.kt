package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.helpers.BaseConfig
import org.fossify.phone.extensions.getPhoneAccountHandleModel
import org.fossify.phone.extensions.putPhoneAccountHandle
import org.fossify.phone.models.SpeedDial
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

@Suppress("TooManyFunctions") // a preferences wrapper: one accessor per setting, by design
class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    private val regionHint: String by lazy {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        listOf(
            telephonyManager?.simCountryIso,
            telephonyManager?.networkCountryIso,
            Locale.getDefault().country
        )
            .firstOrNull { !it.isNullOrBlank() }
            ?.uppercase(Locale.US)
            .orEmpty()
    }

    fun getSpeedDialValues(): ArrayList<SpeedDial> {
        val speedDialType = object : TypeToken<List<SpeedDial>>() {}.type
        val speedDialValues = Gson().fromJson<ArrayList<SpeedDial>>(speedDial, speedDialType) ?: ArrayList(1)

        for (i in 1..9) {
            val speedDial = SpeedDial(i, "", "")
            if (speedDialValues.firstOrNull { it.id == i } == null) {
                speedDialValues.add(speedDial)
            }
        }

        return speedDialValues
    }

    fun saveCustomSIM(number: String, handle: PhoneAccountHandle) {
        prefs.edit().putPhoneAccountHandle(
            key = getKeyForCustomSIM(number),
            parcelable = handle
        ).apply()
    }

    fun getCustomSIM(number: String): PhoneAccountHandle? {
        val key = getKeyForCustomSIM(number)
        prefs.getPhoneAccountHandleModel(key, null)?.let {
            return it.toPhoneAccountHandle()
        }

        // fallback for old unstable keys. should be removed in future versions
        val migratedHandle = prefs.all.keys
            .filterIsInstance<String>()
            .filter { it.startsWith(REMEMBER_SIM_PREFIX) }
            .firstOrNull {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.compare(
                    it.removePrefix(REMEMBER_SIM_PREFIX),
                    normalizeCustomSIMNumber(number)
                )
            }?.let { legacyKey ->
                prefs.getPhoneAccountHandleModel(legacyKey, null)?.let {
                    val handle = it.toPhoneAccountHandle()
                    prefs.edit {
                        remove(legacyKey)
                        putPhoneAccountHandle(key, handle)
                    }
                    handle
                }
            }

        return migratedHandle
    }

    fun removeCustomSIM(number: String) {
        prefs.edit().remove(getKeyForCustomSIM(number)).apply()
    }

    private fun getKeyForCustomSIM(number: String): String {
        return REMEMBER_SIM_PREFIX + normalizeCustomSIMNumber(number)
    }

    private fun normalizeCustomSIMNumber(number: String): String {
        val decoded = Uri.decode(number).removePrefix("tel:")
        val formatted = PhoneNumberUtils.formatNumberToE164(decoded, regionHint)
        return formatted ?: PhoneNumberUtils.normalizeNumber(decoded)
    }

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var groupSubsequentCalls: Boolean
        get() = prefs.getBoolean(GROUP_SUBSEQUENT_CALLS, true)
        set(groupSubsequentCalls) = prefs.edit().putBoolean(GROUP_SUBSEQUENT_CALLS, groupSubsequentCalls).apply()

    var openDialPadAtLaunch: Boolean
        get() = prefs.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH, false)
        set(openDialPad) = prefs.edit().putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, openDialPad).apply()

    var openContactsAppForTab: Boolean
        get() = prefs.getBoolean(OPEN_CONTACTS_APP_FOR_TAB, true)
        set(openContactsAppForTab) = prefs.edit().putBoolean(OPEN_CONTACTS_APP_FOR_TAB, openContactsAppForTab).apply()

    var disableProximitySensor: Boolean
        get() = prefs.getBoolean(DISABLE_PROXIMITY_SENSOR, false)
        set(disableProximitySensor) = prefs.edit().putBoolean(DISABLE_PROXIMITY_SENSOR, disableProximitySensor).apply()

    var disableSwipeToAnswer: Boolean
        get() = prefs.getBoolean(DISABLE_SWIPE_TO_ANSWER, false)
        set(disableSwipeToAnswer) = prefs.edit().putBoolean(DISABLE_SWIPE_TO_ANSWER, disableSwipeToAnswer).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

    var dialpadVibration: Boolean
        get() = prefs.getBoolean(DIALPAD_VIBRATION, true)
        set(dialpadVibration) = prefs.edit().putBoolean(DIALPAD_VIBRATION, dialpadVibration).apply()

    var hideDialpadNumbers: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_NUMBERS, false)
        set(hideDialpadNumbers) = prefs.edit().putBoolean(HIDE_DIALPAD_NUMBERS, hideDialpadNumbers).apply()

    var dialpadBeeps: Boolean
        get() = prefs.getBoolean(DIALPAD_BEEPS, true)
        set(dialpadBeeps) = prefs.edit().putBoolean(DIALPAD_BEEPS, dialpadBeeps).apply()

    var alwaysShowFullscreen: Boolean
        get() = prefs.getBoolean(ALWAYS_SHOW_FULLSCREEN, false)
        set(alwaysShowFullscreen) = prefs.edit().putBoolean(ALWAYS_SHOW_FULLSCREEN, alwaysShowFullscreen).apply()

    var sim1Color: Int
        get() = prefs.getInt(SIM_1_COLOR, -1)
        set(sim1Color) = prefs.edit().putInt(SIM_1_COLOR, sim1Color).apply()

    var sim2Color: Int
        get() = prefs.getInt(SIM_2_COLOR, -1)
        set(sim2Color) = prefs.edit().putInt(SIM_2_COLOR, sim2Color).apply()

    var swipeToCall: Boolean
        get() = prefs.getBoolean(SWIPE_TO_CALL, true)
        set(swipeToCall) = prefs.edit().putBoolean(SWIPE_TO_CALL, swipeToCall).apply()

    var useImperialDate: Boolean
        get() = prefs.getBoolean(USE_IMPERIAL_DATE, false)
        set(useImperialDate) = prefs.edit().putBoolean(USE_IMPERIAL_DATE, useImperialDate).apply()

    // Index into CallTimeFormat / CallDurationFormat; 0 (Japanese kanji) is the default.
    var callTimeFormat: Int
        get() = prefs.getInt(CALL_TIME_FORMAT, 0)
        set(value) = prefs.edit().putInt(CALL_TIME_FORMAT, value).apply()

    var callDurationFormat: Int
        get() = prefs.getInt(CALL_DURATION_FORMAT, 0)
        set(value) = prefs.edit().putInt(CALL_DURATION_FORMAT, value).apply()

    // Granular theming: one Int override per color slot, THEME_UNSET means "follow the default".
    var themeV1Seeded: Boolean
        get() = prefs.getBoolean(THEME_V1_SEEDED, false)
        set(value) = prefs.edit().putBoolean(THEME_V1_SEEDED, value).apply()

    var pureYellowMigrated: Boolean
        get() = prefs.getBoolean(PURE_YELLOW_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(PURE_YELLOW_MIGRATED, value).apply()

    fun getThemeOverride(key: String): Int = prefs.getInt(key, THEME_UNSET)

    fun setThemeOverride(key: String, color: Int) = prefs.edit().putInt(key, color).apply()

    fun clearThemeOverride(key: String) = prefs.edit().remove(key).apply()

    fun getThemeDimen(key: String, default: Int): Int = prefs.getInt(key, default)

    fun setThemeDimen(key: String, dp: Int) = prefs.edit().putInt(key, dp).apply()

    // Per-element fonts: family (filename, "" = default), weight (0 = default), size (sp, 0 = default).
    fun getFontFamily(slotKey: String): String = prefs.getString(FONT_FAMILY_PREFIX + slotKey, "")!!

    fun setFontFamily(slotKey: String, value: String) =
        prefs.edit().putString(FONT_FAMILY_PREFIX + slotKey, value).apply()

    fun getFontWeight(slotKey: String): Int = prefs.getInt(FONT_WEIGHT_PREFIX + slotKey, 0)

    fun setFontWeight(slotKey: String, value: Int) =
        prefs.edit().putInt(FONT_WEIGHT_PREFIX + slotKey, value).apply()

    fun getFontSize(slotKey: String): Int = prefs.getInt(FONT_SIZE_PREFIX + slotKey, 0)

    fun setFontSize(slotKey: String, value: Int) =
        prefs.edit().putInt(FONT_SIZE_PREFIX + slotKey, value).apply()

    // External-automation surface (receivers/StateExportReceiver and automation/AutomationProvider): a
    // master switch, a separate opt-in for the token, and the shared secret itself. The same model the
    // sister apps use — one token per app, never a second one. All three are device-local: SettingsExport
    // never carries them in a backup, so a restore can neither flip automation nor overwrite the token.
    //
    // Default ON since contract v2 (2026-09-04): the case this exists for is 応用管理 restoring apps and
    // their data onto a wiped phone, where nothing has been configured yet — a door that has to be opened
    // by hand first is no door for setting a phone up. The switch stays because closing one app off is
    // still 白い熊's to do. The read gate lives in helpers/AutomationAuth, never written out per caller.
    //
    // These three write with commit(), not apply(), and that is not fussiness. apply() is asynchronous,
    // and v2 flipped the default: a LOST write of `automationEnabled = false` does not fall back to
    // "off", it falls back to ON, because the key is then absent and absent now means open. The same
    // asymmetry runs the other way for the token — a lost regenerate leaves 白い熊 holding a copied
    // string the app will never accept. These are written once, from a settings row tap, so the cost
    // of a synchronous write is nothing and the failure mode it removes is a door quietly reopening.
    var automationEnabled: Boolean
        get() = prefs.getBoolean(AUTOMATION_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(AUTOMATION_ENABLED, value).commit()
        }

    // Whether a caller must also present the token. Default OFF: a pasted secret cannot survive a wipe,
    // so it is an extra a caller may be asked for rather than the gate itself.
    var automationRequireToken: Boolean
        get() = prefs.getBoolean(AUTOMATION_REQUIRE_TOKEN, false)
        set(value) {
            prefs.edit().putBoolean(AUTOMATION_REQUIRE_TOKEN, value).commit()
        }

    // The shared secret; generated on first read so the settings row always shows a value.
    val automationToken: String
        get() = prefs.getString(AUTOMATION_TOKEN, null)?.takeIf { it.isNotEmpty() } ?: regenerateAutomationToken()

    fun regenerateAutomationToken(): String {
        val bytes = ByteArray(AUTOMATION_TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(AUTOMATION_TOKEN, token).commit()
        return token
    }

    // True when the caller's token matches the stored secret (constant-time — never `==` on a secret).
    // The enabled check is kept separate so callers report "disabled" and "bad token" distinctly.
    fun isAutomationTokenValid(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        return MessageDigest.isEqual(token.toByteArray(), automationToken.toByteArray())
    }
}

// 24 random bytes, hex-encoded — the sister-app convention.
private const val AUTOMATION_TOKEN_BYTES = 24
