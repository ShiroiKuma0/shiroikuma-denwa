package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.isVisible
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialpadPanelBinding
import org.fossify.phone.extensions.addCharacter
import org.fossify.phone.extensions.boundingBox
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getKeyEvent
import org.fossify.phone.views.applyDialpadKeyBackgrounds
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The keys themselves: what a press types, the tone and the haptic tick it makes, and what a long
 * press does. Owned by [DialpadController], which handles everything the typed number is then used
 * for. [onSpeedDial] returns true when a long press on that digit actually dialled someone.
 */
internal class DialpadKeypad(
    private val activity: SimpleActivity,
    private val binding: DialpadPanelBinding,
    private val onSpeedDial: (Int) -> Boolean,
    private val onClearAll: () -> Unit,
) {
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()
    private var toneGeneratorHelper: ToneGeneratorHelper? = null

    fun setup() {
        toneGeneratorHelper = ToneGeneratorHelper(activity, DIALPAD_TONE_LENGTH_MS)

        binding.dialpadWrapper.apply {
            if (activity.config.hideDialpadNumbers) {
                dialpad1Holder.isVisible = false
                dialpad2Holder.isVisible = false
                dialpad3Holder.isVisible = false
                dialpad4Holder.isVisible = false
                dialpad5Holder.isVisible = false
                dialpad6Holder.isVisible = false
                dialpad7Holder.isVisible = false
                dialpad8Holder.isVisible = false
                dialpad9Holder.isVisible = false
                dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }

            activity.applyDialpadKeyBackgrounds(
                arrayOf(
                    dialpad0Holder,
                    dialpad1Holder,
                    dialpad2Holder,
                    dialpad3Holder,
                    dialpad4Holder,
                    dialpad5Holder,
                    dialpad6Holder,
                    dialpad7Holder,
                    dialpad8Holder,
                    dialpad9Holder,
                    dialpadPlusHolder,
                    dialpadAsteriskHolder,
                    dialpadHashtagHolder
                )
            )

            if (Locale.getDefault().language == "ru") {
                dialpad2Letters.append("\nАБВГ")
                dialpad3Letters.append("\nДЕЁЖЗ")
                dialpad4Letters.append("\nИЙКЛ")
                dialpad5Letters.append("\nМНОП")
                dialpad6Letters.append("\nРСТУ")
                dialpad7Letters.append("\nФХЦЧ")
                dialpad8Letters.append("\nШЩЪЫ")
                dialpad9Letters.append("\nЬЭЮЯ")

                val fontSize = activity.resources.getDimension(R.dimen.small_text_size)
                arrayOf(
                    dialpad2Letters,
                    dialpad3Letters,
                    dialpad4Letters,
                    dialpad5Letters,
                    dialpad6Letters,
                    dialpad7Letters,
                    dialpad8Letters,
                    dialpad9Letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }
            }

            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*', longClickable = false)
            setupCharClick(dialpadHashtagHolder, '#', longClickable = false)
        }

        binding.dialpadClearChar.apply {
            setOnClickListener { clearChar(it) }
            setOnLongClickListener { onClearAll(); true }
        }
    }

    fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(binding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun startDialpadTone(char: Char) {
        if (activity.config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (activity.config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (activity.config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else {
            val result = onSpeedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }
}
