package org.fossify.phone.helpers

import android.content.Intent
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.TelephonyManager
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.isOreoPlus
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialpadPanelBinding
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.disableKeyboard
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.models.SpeedDial

/**
 * What the typed number is used for: placing the call, speed dial, and firing secret codes. The keys
 * that produce it belong to [DialpadKeypad]. Between them these cover the whole shared panel, so the
 * Dialpad screen and the panel over the Recents tab behave identically.
 *
 * [onTextChanged] is how a host learns what has been typed, and is what it filters its list on.
 */
class DialpadController(
    private val activity: SimpleActivity,
    private val binding: DialpadPanelBinding,
    private val onTextChanged: (String) -> Unit,
    private val onCallPlaced: () -> Unit = {},
    // launchSetDefaultDialerIntent() is protected on the activity, so the host has to offer it.
    private val onNotDefaultDialer: () -> Unit = {},
) {
    private val keypad = DialpadKeypad(
        activity = activity,
        binding = binding,
        onSpeedDial = { speedDial(it) },
        onClearAll = { clearInput() }
    )

    private var speedDialValues = listOf<SpeedDial>()

    val value: String get() = binding.dialpadInput.value

    fun setup() {
        speedDialValues = activity.config.getSpeedDialValues()
        keypad.setup()

        binding.apply {
            // The big button under the keypad and the small one on the dial line, which takes over
            // while the pad is folded away, are the same action down to the SIM-selector long press.
            listOf(dialpadCallButton, dialpadLineCall).forEach { button ->
                button.setOnClickListener { initCall(dialpadInput.value) }
                button.setOnLongClickListener { initCallWithSimSelector() }
            }

            dialpadInput.onTextChangeListener { onTextChanged(it) }
            dialpadInput.requestFocus()
            dialpadInput.disableKeyboard()
        }
    }

    /** Speed dial entries can be edited while a host is alive, so re-read them whenever it reappears. */
    fun refreshSpeedDialValues() {
        speedDialValues = activity.config.getSpeedDialValues()
    }

    fun setText(text: String) {
        binding.dialpadInput.setText(text)
        binding.dialpadInput.setSelection(text.length)
    }

    fun clearInput() {
        binding.dialpadInput.setText("")
    }

    fun clearInputWithDelay() {
        activity.lifecycleScope.launch {
            delay(CLEAR_INPUT_DELAY_MS)
            clearInput()
        }
    }

    fun initCall(number: String = binding.dialpadInput.value, name: String? = null) {
        if (maybeHandleSecretCode(number)) {
            return
        }

        if (number.isNotEmpty()) {
            activity.startCallWithConfirmationCheck(number, name ?: number)
            clearInputWithDelay()
            onCallPlaced()
        } else {
            RecentsHelper(activity).getRecentCalls(queryLimit = 1) {
                val mostRecentNumber = it.firstOrNull()?.phoneNumber
                if (!mostRecentNumber.isNullOrEmpty()) {
                    activity.runOnUiThread { setText(mostRecentNumber) }
                }
            }
        }
    }

    // Secret codes (*#*#<code>#*#*) are actions, not phone numbers — dialling one only ever earns an
    // operator's "this number does not exist". Telephony broadcasts them to whatever app registered
    // the code (microG's check-in, a vendor engineering menu…) and only the default dialer may ask it
    // to. We fire them from the text watcher the moment the code is complete, like the stock dialer,
    // and again from the call button, so pressing it can never turn a code into a real call.
    fun maybeHandleSecretCode(text: String): Boolean {
        val secretCode = secretCodeOf(text) ?: return false

        if (isOreoPlus()) {
            if (!activity.isDefaultDialer()) {
                onNotDefaultDialer()
                return true
            }

            activity.getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
        } else {
            activity.sendBroadcast(Intent(SECRET_CODE_ACTION, "android_secret_code://$secretCode".toUri()))
        }

        // Nothing about a secret code is visible on its own — whether anything acts on it is entirely
        // the receiver's business — so confirm that it went out, then empty the field so the call
        // button is left with nothing to dial.
        activity.toast(activity.getString(R.string.secret_code_sent, secretCode))
        clearInputWithDelay()
        return true
    }

    private fun initCallWithSimSelector(): Boolean {
        val number = binding.dialpadInput.value
        if (maybeHandleSecretCode(number)) {
            return true
        }

        return if (activity.areMultipleSIMsAvailable() && number.isNotEmpty()) {
            activity.startCallWithConfirmationCheck(
                recipient = number,
                name = number,
                forceSimSelector = true
            )
            onCallPlaced()
            true
        } else {
            false
        }
    }

    // The *#*# … #*#* wrapper is 8 characters, so anything longer than that carries a code.
    private fun secretCodeOf(text: String): String? {
        return if (text.length > SECRET_CODE_WRAPPER_LENGTH && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            text.substring(SECRET_CODE_AFFIX_LENGTH, text.length - SECRET_CODE_AFFIX_LENGTH)
        } else {
            null
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (binding.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, speedDial.getName(activity))
                return true
            }
        }
        return false
    }

    companion object {
        private const val CLEAR_INPUT_DELAY_MS = 1000L
        private const val SECRET_CODE_AFFIX_LENGTH = 4
        private const val SECRET_CODE_WRAPPER_LENGTH = SECRET_CODE_AFFIX_LENGTH * 2
    }
}
