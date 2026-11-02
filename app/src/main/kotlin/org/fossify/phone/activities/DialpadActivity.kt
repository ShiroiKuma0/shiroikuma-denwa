package org.fossify.phone.activities

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.ActivityDialpadBinding
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.extensions.startAddContactIntent
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.helpers.DialpadController
import org.fossify.phone.helpers.T9Helper

class DialpadActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDialpadBinding::inflate)

    private var allContacts = ArrayList<Contact>()
    private var privateCursor: Cursor? = null

    private val dialpadController by lazy {
        DialpadController(
            activity = this,
            binding = binding.dialpadPanel,
            onTextChanged = { dialpadValueChanged(it) },
            onNotDefaultDialer = { launchSetDefaultDialerIntent() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(
                padBottomImeAndSystem = listOf(dialpadList, dialpadHolder)
            )
            setupMaterialScrollListener(binding.dialpadList, binding.dialpadAppbar)
        }

        setupOptionsMenu()
        privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        dialpadController.setup()

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            gotContacts(contacts)
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.dialpadPanel.apply {
            val callIcon = resources.getColoredDrawableWithColor(
                drawableId = R.drawable.ic_phone_vector,
                color = themeColor(ThemeSlot.DIALPAD_CALL_ICON)
            )
            dialpadCallButton.setImageDrawable(callIcon)
            dialpadCallButton.background.applyColorFilter(themeColor(ThemeSlot.DIALPAD_CALL_BUTTON))
        }

        binding.apply {
            letterFastscroller.textColor = getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(binding.dialpadHolder)
        binding.dialpadPanel.dialpadClearChar.applyColorFilter(getProperTextColor())
        setupTopAppBar(binding.dialpadAppbar, NavigationIcon.Arrow)
        dialpadController.refreshSpeedDialValues()
    }

    private fun setupOptionsMenu() {
        binding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun checkDialIntent(): Boolean {
        return if (
            (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW)
            && intent.data != null && intent.dataString?.contains("tel:") == true
        ) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            dialpadController.setText(number)
            true
        } else {
            false
        }
    }

    private fun addNumberToContact() {
        startAddContactIntent(dialpadController.value)
    }

    private fun gotContacts(newContacts: ArrayList<Contact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        runOnUiThread {
            if (!checkDialIntent() && dialpadController.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    private fun dialpadValueChanged(text: String) {
        if (dialpadController.maybeHandleSecretCode(text)) {
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter { contact ->
            contact.doesContainPhoneNumber(text) || T9Helper.toDigits(contact.name).contains(text, true)
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text)
        }).toMutableList() as ArrayList<Contact>

        binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

        ContactsAdapter(
            activity = this,
            contacts = filtered,
            recyclerView = binding.dialpadList,
            highlightText = text,
            itemClick = {
                startCallWithConfirmationCheck(it as Contact)
                dialpadController.clearInputWithDelay()
            },
            profileIconClick = {
                startContactDetailsIntent(it as Contact)
            }).apply {
            binding.dialpadList.adapter = this
        }

        binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(dialpadController.value)
        }
    }
}
