package org.fossify.phone.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.ActionMenuView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import me.grantland.widget.AutofitHelper
import org.fossify.commons.dialogs.ChangeViewTypeDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.BuildConfig
import org.fossify.phone.R
import org.fossify.phone.adapters.ViewPagerAdapter
import org.fossify.phone.databinding.ActivityMainBinding
import org.fossify.phone.dialogs.ChangeSortingDialog
import org.fossify.phone.dialogs.FilterContactSourcesDialog
import org.fossify.phone.dialogs.PendingRestoreDialog
import org.fossify.phone.extensions.clearMissedCalls
import org.fossify.phone.extensions.ThemeSlot
import org.fossify.phone.extensions.applyThemeFont
import org.fossify.phone.extensions.colorItemTitles
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getInstalledContactsAppPackage
import org.fossify.phone.extensions.handleFullScreenNotificationsPermission
import org.fossify.phone.extensions.holdsDialerRole
import org.fossify.phone.extensions.launchContactsApp
import org.fossify.phone.extensions.themeColor
import org.fossify.phone.extensions.launchCreateNewContactIntent
import org.fossify.phone.fragments.ContactsFragment
import org.fossify.phone.fragments.FavoritesFragment
import org.fossify.phone.fragments.MyViewPagerFragment
import org.fossify.phone.fragments.RecentsFragment
import org.fossify.phone.helpers.CONTACTS_APP_OPEN_TAB_EXTRA
import org.fossify.phone.helpers.DialpadPanel
import org.fossify.phone.helpers.OPEN_DIAL_PAD_AT_LAUNCH
import org.fossify.phone.helpers.PendingRestore
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.SettingsExport
import org.fossify.phone.helpers.tabsList
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true
    
    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var launchedDialer = false

    private val dialpadPanel by lazy {
        DialpadPanel(
            activity = this,
            binding = binding.dialpadPanel,
            dialpadButton = binding.mainDialpadButton,
            onQueryChanged = { getRecentsFragment()?.applyDialpadQuery(it) },
            onNotDefaultDialer = { launchSetDefaultDialerIntent() }
        )
    }
    private var pendingRestoreDialog: PendingRestoreDialog? = null
    // A tab renrakusaki's bottom bar asked us to open, as a page position; consumed by the first tab
    // selection that runs after the launch, so a cold start lands on it instead of the default tab.
    private var requestedTab: Int? = null
    private var storedShowTabs = 0
    private var storedFontSize = 0
    private var storedStartNameWithSurname = false
    var cachedContacts = ArrayList<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        requestedTab = takeRequestedTab()
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.mainTabsHolder))

        EventBus.getDefault().register(this)
        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(
                    binding.mainHolder,
                    R.string.allow_displaying_over_other_apps,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(getProperBackgroundColor().darkenColor())
                snackbar.setTextColor(getProperTextColor())
                snackbar.setActionTextColor(getProperTextColor())
                snackbar.show()
            }

            handleFullScreenNotificationsPermission { granted ->
                if (!granted) {
                    toast(org.fossify.commons.R.string.notifications_disabled)
                }
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        if (isQPlus() && (config.blockUnknownNumbers || config.blockHiddenNumbers)) {
            setDefaultCallerIdApp()
        }

        setupTabs()
        Contact.sorting = config.sorting
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        updateMenuColors()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)

        updateTextColors(binding.mainHolder)
        setupTabColors()

        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), getProperPrimaryColor(), getProperPrimaryColor())
        }

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            getFavoritesFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            storedStartNameWithSurname = config.startNameWithSurname
        }

        if (!binding.mainMenu.isSearchOpen) {
            refreshItems(true)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        // if the Contacts-app hand-off got enabled while we sat on a hand-off page, move off it
        if (binding.viewPager.adapter != null && shouldOpenContactsAppForTab(binding.viewPager.currentItem)) {
            binding.viewPager.currentItem = handOffTabCount()
        }

        checkShortcuts()
        checkPendingRestore()
        Handler().postDelayed({
            getRecentsFragment()?.refreshItems()
        }, 2000)
    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    // How we are handed back: renrakusaki's Recents tab relaunches this activity with CLEAR_TOP or
    // SINGLE_TOP, so the running instance is brought forward with the wanted tab rather than recreated.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeRequestedTab()?.let { binding.mainTabsHolder.getTabAt(it)?.select() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
            // The role is the one thing a held restore was waiting for — do not make 白い熊 wait for
            // the next launch to find out it went in.
            checkPendingRestore()
        } else if (requestCode == REQUEST_CODE_SET_DEFAULT_CALLER_ID && resultCode != Activity.RESULT_OK) {
            toast(R.string.must_make_default_caller_id_app, length = Toast.LENGTH_LONG)
            baseConfig.blockUnknownNumbers = false
            baseConfig.blockHiddenNumbers = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    override fun onBackPressedCompat(): Boolean {
        if (dialpadPanel.hide()) {
            return true
        }

        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            return true
        }

        val recentsFragment = getRecentsFragment()
        if (recentsFragment != null && getCurrentFragment() == recentsFragment && recentsFragment.clearFilterIfActive()) {
            return true
        }

        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.clear_call_history).isVisible = currentFragment == getRecentsFragment()
            findItem(R.id.sort).isVisible = currentFragment != getRecentsFragment()
            findItem(R.id.filter).isVisible = currentFragment != getRecentsFragment()
            findItem(R.id.create_new_contact).isVisible = currentFragment == getContactsFragment()
            findItem(R.id.change_view_type).isVisible = currentFragment == getFavoritesFragment()
            findItem(R.id.column_count).isVisible = currentFragment == getFavoritesFragment() && config.viewType == VIEW_TYPE_GRID
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            requireToolbar().inflateMenu(R.menu.menu)
            toggleHideOnScroll(false)
            setupMenu()

            onSearchOpenListener = {
                post { styleSearchBar() }
            }

            onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.onSearchQueryChanged("")
                }
                post { styleSearchBar() }
            }

            onSearchTextChangedListener = { text ->
                getCurrentFragment()?.onSearchQueryChanged(text)
            }

            requireToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.clear_call_history -> clearCallHistory()
                    R.id.create_new_contact -> launchCreateNewContactIntent()
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                    R.id.settings -> launchSettings()
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }

        setupSettingsShortcut()
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, ArrayList(items), currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            refreshMenuItems()
            getFavoritesFragment()?.refreshItems()
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
        styleSearchBar()
        styleTopBarMenu()
        setupOverflowLongPress()
    }

    // Long-press on the overflow ("hamburger") button opens the 白い熊 UI settings page. The button is
    // created lazily by the ActionMenuPresenter during layout, so attach via post; re-running each
    // resume is harmless (the listener is simply replaced).
    private fun setupOverflowLongPress() {
        val toolbar = binding.mainMenu.requireToolbar()
        toolbar.post {
            val actionMenuView = (0 until toolbar.childCount)
                .map { toolbar.getChildAt(it) }
                .filterIsInstance<ActionMenuView>()
                .firstOrNull() ?: return@post
            val overflowButton = (0 until actionMenuView.childCount)
                .map { actionMenuView.getChildAt(it) }
                .filterIsInstance<ImageView>()
                .firstOrNull() ?: return@post
            overflowButton.setOnLongClickListener {
                startActivity(Intent(this, ThemeActivity::class.java))
                true
            }
        }
    }

    // Tint the top bar's action + overflow ("hamburger") icons, and colour the overflow-menu item text.
    private fun styleTopBarMenu() {
        val iconColor = themeColor(ThemeSlot.MENU_ICON)
        val menuTextColor = themeColor(ThemeSlot.MENU_TEXT)
        val toolbar = binding.mainMenu.requireToolbar()
        toolbar.overflowIcon?.applyColorFilter(iconColor)
        val toolbarMenu = toolbar.menu
        for (i in 0 until toolbarMenu.size()) {
            toolbarMenu.getItem(i).icon?.applyColorFilter(iconColor)
        }
        toolbarMenu.colorItemTitles(menuTextColor)
        styleSettingsShortcut()
    }

    private fun settingsShortcutView(): View? =
        binding.mainMenu.requireToolbar().menu.findItem(R.id.settings_shortcut)?.actionView

    // 設 opens the 白い熊 UI page, 定 opens the regular Settings; the two characters tap independently.
    private fun setupSettingsShortcut() {
        val view = settingsShortcutView() ?: return
        view.findViewById<View>(R.id.settings_shortcut_left)?.setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
        }
        view.findViewById<View>(R.id.settings_shortcut_right)?.setOnClickListener {
            launchSettings()
        }
    }

    // Colour + font the 設定 characters from the SETTINGS_BUTTON slot.
    private fun styleSettingsShortcut() {
        val view = settingsShortcutView() ?: return
        val color = themeColor(ThemeSlot.SETTINGS_BUTTON)
        listOf(R.id.settings_shortcut_left, R.id.settings_shortcut_right).forEach { id ->
            view.findViewById<TextView>(id)?.apply {
                setTextColor(color)
                applyThemeFont(ThemeSlot.SETTINGS_BUTTON)
            }
        }
    }

    // Apply the granular search-bar theme on top of the commons defaults (must run after updateColors).
    private fun styleSearchBar() {
        val menu = binding.mainMenu
        val radiusPx = 16f * resources.displayMetrics.density
        val strokePx = (2 * resources.displayMetrics.density).toInt()

        menu.findViewById<View>(org.fossify.commons.R.id.toolbar_container)?.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
                setColor(themeColor(ThemeSlot.SEARCH_FILL))
                setStroke(strokePx, themeColor(ThemeSlot.SEARCH_BORDER))
            }

        menu.findViewById<EditText>(org.fossify.commons.R.id.top_toolbar_search)?.apply {
            setTextColor(themeColor(ThemeSlot.SEARCH_TEXT))
            setHintTextColor(themeColor(ThemeSlot.SEARCH_HINT))
            applyThemeFont(ThemeSlot.SEARCH_TEXT)
        }

        menu.findViewById<ImageView>(org.fossify.commons.R.id.top_toolbar_search_icon)
            ?.applyColorFilter(themeColor(ThemeSlot.SEARCH_ICON))
    }

    /**
     * Apply anything a restore had to hold, and keep asking until there is nothing left.
     *
     * Runs on every resume, which is what makes the guarantee real: whatever order 白い熊 does things
     * in on a new phone — restore first, dialer role later, or the other way round — the held data
     * goes in the first time the app is looked at with the role in place. The directory check is one
     * `list()` and the common case is an empty directory, so this costs nothing on a normal launch.
     */
    private fun checkPendingRestore() {
        if (pendingRestoreDialog != null || !PendingRestore.hasAny(this)) {
            return
        }
        ensureBackgroundThread {
            val outcome = PendingRestore.applyPending(this)
            runOnUiThread { onPendingRestoreChecked(outcome) }
        }
    }

    private fun onPendingRestoreChecked(outcome: PendingRestore.Outcome) {
        if (isFinishing || isDestroyed) {
            return
        }
        if (outcome.appliedAnything) {
            val summary = outcome.applied.entries.joinToString("・") { (item, count) ->
                "${getString(item.shortLabelRes)}: $count"
            }
            toast(getString(R.string.pending_restore_done, summary), Toast.LENGTH_LONG)
        }
        if (outcome.stillHeld.isEmpty()) {
            return
        }

        val lines = outcome.stillHeld.map { (item, held) ->
            getString(R.string.pending_restore_item, getString(item.shortLabelRes), held.count, held.reason)
        }
        val (label, action) = pendingRestorePrimary(outcome.stillHeld.keys)
        pendingRestoreDialog = PendingRestoreDialog(
            activity = this,
            lines = lines,
            primaryLabel = label,
            onPrimary = { pendingRestoreDialog = null; action() },
            onLater = { pendingRestoreDialog = null },
        ).apply { show() }
    }

    /**
     * The one button that would actually unblock the held data, in the order the blocks appear: the
     * dialer role gates the blocked numbers AND brings call-log access with it, so it is always the
     * first ask; the explicit call-log grant only matters when the role is somehow held without it.
     */
    private fun pendingRestorePrimary(held: Set<SettingsExport.Item>): Pair<String, () -> Unit> {
        if (!holdsDialerRole()) {
            return getString(R.string.pending_restore_set_dialer) to { launchSetDefaultDialerIntent() }
        }
        if (SettingsExport.Item.CALL_HISTORY in held && !hasPermission(PERMISSION_WRITE_CALL_LOG)) {
            return getString(R.string.pending_restore_grant) to {
                handlePermission(PERMISSION_WRITE_CALL_LOG) { checkPendingRestore() }
            }
        }
        return getString(R.string.pending_restore_retry) to { checkPendingRestore() }
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems(invalidate = true)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])
        colorTabItem(activeView, themeColor(ThemeSlot.TAB_SELECTED))

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
            colorTabItem(inactiveView, themeColor(ThemeSlot.TAB_UNSELECTED))
        }

        binding.mainTabsHolder.setBackgroundColor(themeColor(ThemeSlot.TAB_BACKGROUND))
    }

    private fun colorTabItem(view: View?, color: Int) {
        view?.findViewById<ImageView>(org.fossify.commons.R.id.tab_item_icon)?.applyColorFilter(color)
        view?.findViewById<TextView>(org.fossify.commons.R.id.tab_item_label)?.setTextColor(color)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): List<Int> {
        val showTabs = config.showTabs
        val icons = mutableListOf<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_vector)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_outline_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_outline_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_vector)
        }

        return icons
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTabsHolder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = binding.mainTabsHolder.tabCount - 1
                }

                // a tab asked for by renrakusaki's bar outranks both — that is 白い熊 tapping one of our
                // own tabs, only from the other app
                requestedTab?.let { wantedTab = it }
                requestedTab = null

                binding.mainTabsHolder.getTabAt(wantedTab)?.select()
                refreshMenuItems()
            }, 100L)
        }

        binding.mainDialpadButton.setOnClickListener {
            if (canShowDialpadPanel()) {
                dialpadPanel.show()
            } else {
                launchDialpad()
            }
        }

        binding.viewPager.onGlobalLayout {
            refreshMenuItems()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun setupTabs() {
        binding.viewPager.adapter = null
        binding.mainTabsHolder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.text = getTabLabel(index)
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    binding.mainTabsHolder.addTab(this)
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                if (shouldOpenContactsAppForTab(it.position)) {
                    launchContactsApp(tabMaskAt(it.position), config.showTabs)
                    // bounce the selection back to the page we are actually staying on
                    Handler().post {
                        binding.mainTabsHolder.getTabAt(sanitizeWantedTab(binding.viewPager.currentItem))?.select()
                    }
                    return@onTabSelectionChanged
                }

                dialpadPanel.hide()
                getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])

                val lastPosition = binding.mainTabsHolder.tabCount - 1
                if (it.position == lastPosition && config.showTabs and TAB_CALL_HISTORY > 0) {
                    clearMissedCalls()
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.contacts_tab
            1 -> R.string.favorites_tab
            else -> R.string.call_history_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.apply {
            if (viewPager.adapter == null) {
                viewPager.adapter = ViewPagerAdapter(this@MainActivity)
                viewPager.currentItem = requestedTab
                    ?: if (openLastTab) sanitizeWantedTab(config.lastUsedViewPagerPage) else getDefaultTab()
                viewPager.onGlobalLayout {
                    refreshFragments()
                }
            } else {
                refreshFragments()
            }
        }
    }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    // The keypad only makes sense over the call log, which is what it filters. Anywhere else — and
    // that is only reachable with the Contacts-app hand-off turned off — the separate screen opens.
    private fun canShowDialpadPanel(): Boolean {
        return getRecentsFragment() != null &&
                config.showTabs and TAB_CALL_HISTORY > 0 &&
                binding.viewPager.currentItem == binding.mainTabsHolder.tabCount - 1
    }

    fun refreshFragments() {
        cacheContacts()
        getContactsFragment()?.refreshItems()
        getFavoritesFragment()?.refreshItems()
        getRecentsFragment()?.refreshItems()
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>?>()

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(getContactsFragment())
        }

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(getFavoritesFragment())
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(getRecentsFragment())
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getAllFragments().getOrNull(binding.viewPager.currentItem)

    private fun getContactsFragment(): ContactsFragment? = findViewById(R.id.contacts_fragment)

    private fun getFavoritesFragment(): FavoritesFragment? = findViewById(R.id.favorites_fragment)

    private fun getRecentsFragment(): RecentsFragment? = findViewById(R.id.recents_fragment)

    // Contacts and Favorites are always the first tabs, so the hand-off tabs occupy positions 0 until this count.
    private fun handOffTabCount(): Int {
        var count = 0
        if (config.showTabs and TAB_CONTACTS != 0) count++
        if (config.showTabs and TAB_FAVORITES != 0) count++
        return count
    }

    // When on, tapping (or swiping to) the Contacts or Favorites tab opens the Contacts app on the matching
    // tab instead of the built-in list. Requires a tab that stays in the dialer (i.e. Recents shown).
    private fun shouldOpenContactsAppForTab(position: Int): Boolean {
        return config.openContactsAppForTab
                && position < handOffTabCount()
                && visibleTabs().size > handOffTabCount()
                && getInstalledContactsAppPackage() != null
    }

    // The TAB_* masks of the tabs actually shown, in bar order — the source of both tab positions and count.
    // Read from the config rather than the tab bar, so it also answers before the bar has been built.
    private fun visibleTabs() = tabsList.filter { config.showTabs and it != 0 }

    // The TAB_* mask of the tab shown at the given position.
    private fun tabMaskAt(position: Int) = visibleTabs().getOrNull(position) ?: 0

    // A tab renrakusaki asked us to open, as a page position: the mirror of the extra we send it, arriving
    // when the Recents tab on the bar it wears for us is tapped. Consumed on read, and honored only for a
    // tab that stays in the dialer — a request for Contacts or Favorites would bounce us straight back out.
    private fun takeRequestedTab(): Int? {
        val wantedMask = intent.getIntExtra(CONTACTS_APP_OPEN_TAB_EXTRA, 0)
        if (wantedMask == 0) {
            return null
        }

        intent.removeExtra(CONTACTS_APP_OPEN_TAB_EXTRA)
        val position = visibleTabs().indexOf(wantedMask)
        return position.takeIf { it >= 0 && !shouldOpenContactsAppForTab(it) }
    }

    // Programmatic selections (default tab, last used page) must not land on a hand-off page,
    // otherwise the dialer would bounce into the Contacts app right at launch.
    private fun sanitizeWantedTab(position: Int) = if (shouldOpenContactsAppForTab(position)) handOffTabCount() else position

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        val wantedTab = when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < binding.mainTabsHolder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabsMask and TAB_CONTACTS > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CALL_HISTORY > 0) {
                    if (showTabsMask and TAB_CONTACTS > 0) {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }

        return sanitizeWantedTab(wantedTab)
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    private fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getRecentsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    fun cacheContacts() {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(this).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            if (SMT_PRIVATE !in config.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                    contacts.sort()
                }
            }

            try {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
            } catch (ignored: Exception) {
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshCallLog(event: Events.RefreshCallLog) {
        getRecentsFragment()?.refreshItems()
    }

}
