package org.fossify.phone.fragments

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.util.AttributeSet
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.trimToComparableNumber
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.RecentCallsAdapter
import org.fossify.phone.databinding.FragmentRecentsBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.findByNumber
import org.fossify.phone.extensions.hasSameTailAs
import org.fossify.phone.extensions.runAfterAnimations
import org.fossify.phone.extensions.startAddContactIntent
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.T9Helper
import org.fossify.phone.interfaces.RefreshItemsListener
import org.fossify.phone.models.CallLogItem
import org.fossify.phone.models.RecentCall

class RecentsFragment(
    context: Context, attributeSet: AttributeSet,
) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attributeSet), RefreshItemsListener {

    private lateinit var binding: FragmentRecentsBinding
    private var allRecentCalls = listOf<CallLogItem>()
    private var recentsAdapter: RecentCallsAdapter? = null

    private var searchQuery: String? = null
    // The numbers the list is narrowed to: one, after a call is tapped, or all of a contact's, when
    // renrakusaki's "全履歴" asks for its history. Null = the whole log.
    private var phoneNumberFilter: List<String>? = null
    private var dialpadQuery: String? = null
    private var recentsHelper = RecentsHelper(context)

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
    }

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        binding.recentsPlaceholder.text = context.getString(placeholderResId)
        binding.recentsPlaceholder2.apply {
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }

        binding.filterBannerClose.setOnClickListener {
            clearPhoneNumberFilter()
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.recentsPlaceholder2.setTextColor(properPrimaryColor)

        recentsAdapter?.apply {
            updateTextColor(textColor)
            initDrawables()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        if (invalidate) {
            allRecentCalls = emptyList()
        }

        refreshCallLog(loadAll = false) {
            binding.recentsList.runAfterAnimations {
                refreshCallLog(loadAll = true)
            }
        }
    }

    override fun onSearchClosed() {
        searchQuery = null
        dialpadQuery = null
        clearPhoneNumberFilter()
        showOrHidePlaceholder(allRecentCalls.isEmpty())
        recentsAdapter?.updateItems(allRecentCalls)
    }

    override fun onSearchQueryChanged(text: String) {
        searchQuery = text
        updateSearchResult()
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateSearchResult() {
        ensureBackgroundThread {
            val fixedText = searchQuery!!.trim().replace("\\s+".toRegex(), " ")
            val recentCalls = allRecentCalls
                .filterIsInstance<RecentCall>()
                .filter {
                    it.name.contains(fixedText, true) || it.doesContainPhoneNumber(fixedText)
                }
                .sortedWith(
                    compareByDescending<RecentCall> { it.dayCode }
                        .thenByDescending { it.name.startsWith(fixedText, true) }
                        .thenByDescending { it.startTS }
                )

            prepareCallLog(recentCalls) {
                activity?.runOnUiThread {
                    showOrHidePlaceholder(recentCalls.isEmpty())
                    recentsAdapter?.updateItems(it, fixedText)
                }
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                binding.recentsPlaceholder.text = context.getString(R.string.no_previous_calls)
                binding.recentsPlaceholder2.beGone()
                refreshCallLog()
            }
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        if (show && !binding.progressIndicator.isVisible()) {
            binding.recentsPlaceholder.beVisible()
        } else {
            binding.recentsPlaceholder.beGone()
        }
    }

    private fun gotRecents(recents: List<CallLogItem>) {
        binding.progressIndicator.hide()
        if (recents.isEmpty()) {
            binding.apply {
                showOrHidePlaceholder(true)
                recentsPlaceholder2.beGoneIf(context.hasPermission(PERMISSION_READ_CALL_LOG))
                recentsList.beGone()
            }
        } else {
            binding.apply {
                showOrHidePlaceholder(false)
                recentsPlaceholder2.beGone()
                recentsList.beVisible()
            }

            if (binding.recentsList.adapter == null) {
                recentsAdapter = RecentCallsAdapter(
                    activity = activity as SimpleActivity,
                    recyclerView = binding.recentsList,
                    refreshItemsListener = this,
                    showOverflowMenu = true,
                    itemDelete = { deleted ->
                        allRecentCalls = allRecentCalls.filter { it !in deleted }
                    },
                    enableSwipeToCall = true,
                    itemClick = { item ->
                        when (item) {
                            is CallLogItem.ContactSuggestion -> activity?.startCallWithConfirmationCheck(
                                item.number, item.contact.getNameToDisplay()
                            )

                            is RecentCall -> if (searchQuery.isNullOrEmpty() && dialpadQuery.isNullOrEmpty()) {
                                applyPhoneNumberFilter(item.phoneNumber, item.name)
                            } else {
                                activity?.startCallWithConfirmationCheck(item.phoneNumber, item.name)
                            }

                            else -> {}
                        }
                    },
                    profileIconClick = {
                        val recentCall = it as RecentCall
                        val contact = findContactByCall(recentCall)
                        if (contact != null) {
                            activity?.startContactDetailsIntent(contact)
                        } else {
                            activity?.startAddContactIntent(recentCall.phoneNumber)
                        }
                    }
                )

                binding.recentsList.adapter = recentsAdapter
                recentsAdapter?.updateItems(recents)
            } else {
                recentsAdapter?.updateItems(recents)
            }
        }
    }

    private fun refreshCallLog(loadAll: Boolean = false, callback: (() -> Unit)? = null) {
        getRecentCalls(loadAll) {
            allRecentCalls = it
            if (!dialpadQuery.isNullOrEmpty()) {
                applyDialpadQuery(dialpadQuery!!)
            } else if (!searchQuery.isNullOrEmpty()) {
                updateSearchResult()
            } else if (phoneNumberFilter != null) {
                activity?.runOnUiThread { applyCurrentFilter() }
            } else {
                activity?.runOnUiThread { gotRecents(it) }
            }

            callback?.invoke()
        }
    }

    private fun getRecentCalls(loadAll: Boolean, callback: (List<CallLogItem>) -> Unit) {
        val queryCount = if (loadAll) Int.MAX_VALUE else RecentsHelper.QUERY_LIMIT
        val existingRecentCalls = allRecentCalls.filterIsInstance<RecentCall>()

        with(recentsHelper) {
            if (context.config.groupSubsequentCalls) {
                getGroupedRecentCalls(existingRecentCalls, queryCount) {
                    prepareCallLog(it, callback)
                }
            } else {
                getRecentCalls(existingRecentCalls, queryCount) {
                    prepareCallLog(it, callback)
                }
            }
        }
    }

    private fun prepareCallLog(calls: List<RecentCall>, callback: (List<CallLogItem>) -> Unit) {
        if (calls.isEmpty()) {
            callback(emptyList())
            return
        }

        ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            ensureBackgroundThread {
                val privateContacts = getPrivateContacts()
                val updatedCalls = updateNamesIfEmpty(
                    calls = maybeFilterPrivateCalls(calls, privateContacts),
                    contacts = contacts,
                    privateContacts = privateContacts
                )

                callback(
                    groupCallsByDate(updatedCalls)
                )
            }
        }
    }

    private fun getPrivateContacts(): ArrayList<Contact> {
        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        return MyContactsContentProvider.getContacts(context, privateCursor)
    }

    private fun maybeFilterPrivateCalls(calls: List<RecentCall>, privateContacts: List<Contact>): List<RecentCall> {
        val ignoredSources = context.baseConfig.ignoredContactSources
        return if (SMT_PRIVATE in ignoredSources) {
            val privateNumbers = privateContacts.flatMap { it.phoneNumbers }.map { it.value }
            calls.filterNot { it.phoneNumber in privateNumbers }
        } else {
            calls
        }
    }

    private fun updateNamesIfEmpty(calls: List<RecentCall>, contacts: List<Contact>, privateContacts: List<Contact>): List<RecentCall> {
        if (calls.isEmpty()) return mutableListOf()

        val contactsWithNumbers = contacts.filter { it.phoneNumbers.isNotEmpty() }
        return calls.map { call ->
            if (call.phoneNumber == call.name) {
                val privateContact = privateContacts.firstOrNull { it.doesContainPhoneNumber(call.phoneNumber) }
                    ?: privateContacts.findByNumber(call.phoneNumber)
                val contact = contactsWithNumbers.firstOrNull { it.phoneNumbers.first().normalizedNumber == call.phoneNumber }

                when {
                    privateContact != null -> withUpdatedName(call = call, name = privateContact.getNameToDisplay())
                    contact != null -> withUpdatedName(call = call, name = contact.getNameToDisplay())
                    else -> call
                }
            } else {
                call
            }
        }
    }

    private fun withUpdatedName(call: RecentCall, name: String): RecentCall {
        return call.copy(
            name = name,
            groupedCalls = call.groupedCalls
                ?.map { it.copy(name = name) }
                ?.toMutableList()
                ?.ifEmpty { null }
        )
    }

    private fun groupCallsByDate(recentCalls: List<RecentCall>): MutableList<CallLogItem> {
        val callLog = mutableListOf<CallLogItem>()
        var lastDayCode = ""
        for (call in recentCalls) {
            val currentDayCode = call.dayCode
            if (currentDayCode != lastDayCode) {
                callLog += CallLogItem.Date(timestamp = call.startTS, dayCode = currentDayCode)
                lastDayCode = currentDayCode
            }

            callLog += call
        }

        return callLog
    }

    private fun findContactByCall(recentCall: RecentCall): Contact? {
        return (activity as MainActivity).cachedContacts.find { it.name == recentCall.name && it.doesHavePhoneNumber(recentCall.phoneNumber) }
    }

    private fun applyPhoneNumberFilter(phoneNumber: String, contactName: String) =
        applyNumbersFilter(listOf(phoneNumber), contactName)

    /**
     * Narrows the list to calls with any of [numbers], under the "Calls with <name>" banner whose cross
     * (or Back) puts the whole log back. Safe before the log has loaded: the load applies it.
     */
    fun applyNumbersFilter(numbers: List<String>, contactName: String) {
        val wanted = numbers.filter { it.isNotBlank() }
        if (wanted.isEmpty()) return
        phoneNumberFilter = wanted
        binding.filterBannerLabel.text = context.getString(R.string.calls_with, contactName.ifBlank { wanted.first() })
        binding.filterBanner.beVisible()
        if (allRecentCalls.isNotEmpty()) applyCurrentFilter()
    }

    private fun applyCurrentFilter() {
        val filter = phoneNumberFilter ?: return
        ensureBackgroundThread {
            val filtered = allRecentCalls
                .filterIsInstance<RecentCall>()
                .filter { call -> filter.any { call.isWithNumber(it) } }

            val grouped = groupCallsByDate(filtered)
            activity?.runOnUiThread {
                // gotRecents, not just updateItems: on a cold start the filter is the first thing the
                // list ever shows, so the adapter may not exist yet
                gotRecents(grouped)
            }
        }
    }

    // The tapped-row match the filter always used, plus a whole-number comparison for numbers handed
    // in from outside, which arrive as stored ("605 282 446", "+420 605…") rather than as digits
    private fun RecentCall.isWithNumber(number: String): Boolean {
        if (doesContainPhoneNumber(number)) return true
        val normalized = number.normalizePhoneNumber()
        if (normalized.isEmpty()) return false
        return PhoneNumberUtils.compare(phoneNumber.normalizePhoneNumber(), normalized) ||
            PhoneNumber(number, 0, "", normalized).hasSameTailAs(phoneNumber)
    }

    /**
     * Filters the call log by what is typed on the dialpad panel over this tab: matching calls first,
     * then, under a heading, the contacts that match but have not called. Passing an empty query puts
     * the full history back.
     */
    fun applyDialpadQuery(text: String) {
        dialpadQuery = text
        if (text.isEmpty()) {
            showOrHidePlaceholder(allRecentCalls.isEmpty())
            recentsAdapter?.updateItems(allRecentCalls)
            return
        }

        ensureBackgroundThread {
            val matchingCalls = allRecentCalls
                .filterIsInstance<RecentCall>()
                .filter { it.doesContainPhoneNumber(text) || T9Helper.toDigits(it.name).contains(text, true) }

            val calledNumbers = matchingCalls.map { it.phoneNumber.trimToComparableNumber() }.toSet()
            val suggestions = matchingContacts(text)
                .filterNot { it.number.trimToComparableNumber() in calledNumbers }

            val items = mutableListOf<CallLogItem>()
            items += groupCallsByDate(matchingCalls)
            if (suggestions.isNotEmpty()) {
                items += CallLogItem.ContactsHeader
                items += suggestions
            }

            activity?.runOnUiThread {
                gotRecents(items)
                recentsAdapter?.updateItems(items, text)
            }
        }
    }

    // Contacts matching the typed digits, either by number or by the name typed on the keypad, each
    // paired with the number that matched (or its first, when the name is what matched).
    private fun matchingContacts(text: String): List<CallLogItem.ContactSuggestion> {
        val contacts = (activity as? MainActivity)?.cachedContacts.orEmpty()
        return contacts
            .filter { it.doesContainPhoneNumber(text) || T9Helper.toDigits(it.getNameToDisplay()).contains(text, true) }
            .mapNotNull { contact ->
                val number = contact.phoneNumbers
                    .firstOrNull { it.value.contains(text) || it.normalizedNumber?.contains(text) == true }
                    ?: contact.phoneNumbers.firstOrNull()

                number?.let { CallLogItem.ContactSuggestion(contact = contact, number = it.value) }
            }
    }

    /** Clears an active contact filter, restoring the full call history. Returns true if a filter was cleared. */
    fun clearFilterIfActive(): Boolean {
        if (phoneNumberFilter == null) {
            return false
        }

        clearPhoneNumberFilter()
        return true
    }

    private fun clearPhoneNumberFilter() {
        if (phoneNumberFilter == null) {
            return
        }

        phoneNumberFilter = null
        binding.filterBanner.beGone()
        // gotRecents rather than updateItems: a filter that matched nothing left the list hidden
        gotRecents(allRecentCalls)
    }
}
