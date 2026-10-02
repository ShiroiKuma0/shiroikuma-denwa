package org.fossify.phone.activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.bumptech.glide.Glide
import android.view.WindowManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityCarCallBinding
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.simColor
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.extensions.simTextColor
import org.fossify.phone.extensions.showCallerDetails
import org.fossify.phone.extensions.simTextStyle
import org.fossify.phone.models.CallContact
import org.fossify.phone.services.CarCallService

/**
 * The call screen for a call this app can see but does not hold — Android Auto's projection has the
 * real in-call UI, and Telecom has unbound ours (see
 * [org.fossify.phone.helpers.CarCallMonitor] for the whole story).
 *
 * Deliberately smaller than [CallActivity]: without a [android.telecom.Call] there is no hold, no
 * DTMF, no audio routing and no swap to offer. What the platform still allows an unbound default
 * dialer is ending the call and answering a ringing one, and that is exactly what this shows — big
 * enough to hit without looking, which is the entire point of it existing.
 */
class CarCallActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context): Intent {
            return Intent(context, CarCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            }
        }
    }

    private val binding by viewBinding(ActivityCarCallBinding::inflate)

    private val sessionListener = Runnable {
        runOnUiThread {
            if (CarCallService.isRunning) {
                refresh()
            } else {
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (!CarCallService.isRunning) {
            finish()
            return
        }

        setupEdgeToEdge(
            padTopSystem = listOf(binding.carCallHolder),
            padBottomSystem = listOf(binding.carCallHolder),
        )

        updateTextColors(binding.carCallHolder)
        // The fork's own background (pure black by default), not commons' dark grey — and the
        // chronometer by hand, because it is a Chronometer rather than a MyTextView and so is the
        // one view updateTextColors above leaves untouched.
        binding.carCallHolder.setBackgroundColor(themeColor(ThemeSlot.BACKGROUND))
        binding.carCallDuration.setTextColor(getProperTextColor())
        addLockScreenFlags()
        initButtons()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!CarCallService.isRunning) {
            finish()
            return
        }

        CarCallService.addListener(sessionListener)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        CarCallService.removeListener(sessionListener)
    }

    private fun initButtons() {
        binding.carCallEnd.setOnClickListener {
            if (!CarCallService.endCall(this)) {
                toast(R.string.could_not_end_call)
            }
        }

        binding.carCallAccept.setOnClickListener {
            if (!CarCallService.answerCall(this)) {
                toast(R.string.could_not_answer_call)
            }
        }
    }

    private fun refresh() {
        binding.apply {
            // the app-formatted number once the lookup has run, the raw one until then
            val number = CarCallService.displayNumber ?: CarCallService.number.orEmpty()

            // The caller block, exactly as the phone's own call screen is configured to draw it. Until
            // the lookup has answered there is only the raw number to go on, which the block reads as an
            // unknown caller: the number in the large slot, with nothing repeated underneath it.
            carCallerDetails.showCallerDetails(
                CarCallService.callContact ?: CallContact("", "", number, ""),
                getString(R.string.unknown_caller),
            )
            loadPhoto(CarCallService.contactPhotoUri)

            val ringing = CarCallService.isRinging
            carCallStatus.text = getString(if (ringing) R.string.is_calling else R.string.ongoing_call)
            carCallAccept.beVisibleIf(ringing)

            if (ringing || CarCallService.connectedAt == 0L) {
                carCallDuration.beGone()
                carCallDuration.stop()
            } else {
                carCallDuration.beVisible()
                carCallDuration.base = CarCallService.connectedAt
                carCallDuration.start()
            }

            val slot = CarCallService.simSlot
            carSimHolder.beVisibleIf(slot == 1 || slot == 2)
            if (slot == 1 || slot == 2) {
                carSimImage.applyColorFilter(simColor(slot))
                carSimId.text = slot.toString()
                carSimId.setTextColor(simTextColor())
                carSimId.setTypeface(null, simTextStyle())
            }
        }
    }

    /**
     * The contact photo, filling everything above the caller block. Centre-cropped rather than fitted
     * — a face recognised without looking properly is the whole value of it in a car. No photo means
     * the view stays gone and the name rises to fill the screen.
     */
    private fun loadPhoto(photoUri: String?) {
        if (photoUri.isNullOrEmpty() || isFinishing || isDestroyed) {
            binding.carCallerPhoto.beGone()
            return
        }

        binding.carCallerPhoto.beVisible()
        Glide.with(this)
            .load(photoUri)
            .centerCrop()
            .into(binding.carCallerPhoto)
    }

    private fun addLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
