package org.fossify.phone.helpers

import android.view.View
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.updateTextColors
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialpadPanelBinding
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.themeColor

/**
 * The keypad shown over a list rather than on its own screen: it slides up in front of the Recents
 * tab and hands every keystroke to [onQueryChanged], which is what filters the list underneath.
 * [DialpadController] still owns what the keys do, so both keypads behave the same.
 *
 * The pad can be pulled down out of the way, leaving only the dial line at the bottom. What has been
 * typed stays there and keeps filtering; a pull back up, or the button on the dial line, returns it.
 */
class DialpadPanel(
    private val activity: SimpleActivity,
    private val binding: DialpadPanelBinding,
    private val dialpadButton: View,
    private val onQueryChanged: (String) -> Unit,
    onNotDefaultDialer: () -> Unit,
) {
    private val controller = DialpadController(
        activity = activity,
        binding = binding,
        onTextChanged = onQueryChanged,
        onCallPlaced = { hide() },
        onNotDefaultDialer = onNotDefaultDialer
    )

    private var isSetUp = false
    private var isCollapsed = false

    val isOpen: Boolean get() = binding.root.isVisible()

    fun show() {
        if (isOpen) {
            return
        }

        if (!isSetUp) {
            controller.setup()
            setupCollapsing()
            isSetUp = true
        }

        controller.refreshSpeedDialValues()
        style()
        isCollapsed = false
        applyCollapsedState()
        dialpadButton.beGone()
        binding.root.apply {
            alpha = 0f
            beVisible()
            // The panel has never been laid out on the first show, so its height is only known once
            // this pass lands — start the slide from there rather than from a guess.
            post {
                translationY = height.toFloat()
                animate().translationY(0f).alpha(1f).setDuration(ANIMATION_MS).start()
            }
        }
    }

    /** Closes the panel and puts the unfiltered list back. Returns true if it was open. */
    fun hide(): Boolean {
        if (!isOpen) {
            return false
        }

        controller.clearInput()
        onQueryChanged("")
        binding.root.beGone()
        dialpadButton.beVisible()
        return true
    }

    private fun setupCollapsing() {
        binding.dialpadKeypadToggle.apply {
            beVisible()
            setOnClickListener { if (isCollapsed) expand() else collapse() }
        }

        // With the keypad gone the dial line is all that is left to aim at, so let it be the target
        // too — the field included, which a tap would otherwise only put a caret in.
        binding.dialpadLine.setOnClickListener { expand() }
        binding.dialpadInput.setOnClickListener { expand() }
    }

    private fun collapse() {
        if (isCollapsed) {
            return
        }

        isCollapsed = true
        applyCollapsedState()
    }

    private fun expand() {
        if (!isCollapsed) {
            return
        }

        isCollapsed = false
        applyCollapsedState()
    }

    private fun applyCollapsedState() {
        binding.dialpadWrapper.root.beGoneIf(isCollapsed)
        binding.dialpadCallButtonHolder.beGoneIf(isCollapsed)
        // The dial line only needs a call button of its own once the big one has folded away.
        binding.dialpadLineCall.beVisibleIf(isCollapsed)

        // Only the meaningful direction is armed, so a drag the panel has no answer for is left to
        // whatever is underneath instead of being swallowed.
        binding.root.onPullDown = if (isCollapsed) null else ({ collapse() })
        binding.root.onPullUp = if (isCollapsed) ({ expand() }) else null

        binding.dialpadKeypadToggle.apply {
            setImageResource(if (isCollapsed) R.drawable.ic_dialpad_vector else R.drawable.ic_chevron_down_vector)
            contentDescription = activity.getString(
                if (isCollapsed) R.string.dialpad_show_keypad else R.string.dialpad_hide_keypad
            )
            applyColorFilter(activity.getProperTextColor())
        }
    }

    // The panel lies over the list, so unlike the Dialpad screen it needs a background of its own;
    // the rest is the theming that screen applies to the same views.
    private fun style() {
        binding.apply {
            root.setBackgroundColor(activity.getProperBackgroundColor())
            val callIcon = activity.resources.getColoredDrawableWithColor(
                drawableId = R.drawable.ic_phone_vector,
                color = activity.themeColor(ThemeSlot.DIALPAD_CALL_ICON)
            )
            listOf(dialpadCallButton, dialpadLineCall).forEach {
                it.setImageDrawable(callIcon)
                it.background.applyColorFilter(activity.themeColor(ThemeSlot.DIALPAD_CALL_BUTTON))
            }
            activity.updateTextColors(root)
            dialpadClearChar.applyColorFilter(activity.getProperTextColor())
        }
    }

    companion object {
        private const val ANIMATION_MS = 180L
    }
}
