package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.FlexBox
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent

/**
 * Adaptive browser-style and mobile-switcher tabbed [FragmentHostFragment].
 *
 * Supports two responsive chrome modes via [TabbedChromeMode]:
 * - [TabbedChromeMode.DESKTOP_BROWSER_TABS]: Horizontal Chrome/Firefox-style tab strip with per-tab
 *   close (`×`) buttons, badge/pinned state, and a `+` new-tab button.
 * - [TabbedChromeMode.MOBILE_SWITCHER_PILL]: Compact mobile top bar displaying the active tab's title
 *   alongside a `[N]` tab-count pill button that toggles an interactive card-grid tab overview.
 *
 * When [TabbedFragmentConfig.persistLastActiveTabKey] is configured, [TabbedFragment] automatically
 * persists and restores the active `tabId` using [getPreferenceStore].
 */
open class TabbedFragment(
    val config: TabbedFragmentConfig = TabbedFragmentConfig(),
    id: String? = null
) : FragmentHostFragment() {

    private val tabSpecs = mutableListOf<TabItemSpec>()
    private val tabFragments = linkedMapOf<String, Fragment>()
    private var pendingPersistedTabId: String? = null
    private var hasInitializedPersistence: Boolean = false

    /** Ordered list of currently open [TabItemSpec] entries. */
    val tabs: List<TabItemSpec>
        get() = tabSpecs.toList()

    /** Total number of open tabs. */
    val tabCount: Int
        get() = tabSpecs.size

    /** Currently active tab identifier, or `null` if no tab is open. */
    var activeTabId: String? = null
        private set

    /** Zero-based index of the currently active tab, or `-1` if no tab is active. */
    val activeTabIndex: Int
        get() = activeTabId?.let { id -> tabSpecs.indexOfFirst { it.tabId == id } } ?: -1

    /** [TabItemSpec] of the currently active tab, or `null` if no tab is active. */
    val activeTabSpec: TabItemSpec?
        get() = activeTabId?.let { id -> tabSpecs.firstOrNull { it.tabId == id } }

    /** `true` when the mobile tab overview card grid is currently open. */
    var isMobileTabOverviewOpen: Boolean = false
        private set

    /** Optional runtime override for [TabbedFragmentConfig.chromeMode]. */
    var chromeModeOverride: TabbedChromeMode? = null
        set(value) {
            field = value
            syncChromeLayout()
        }

    /** Most recently reported viewport width in pixels (`1024` by default). */
    var currentViewportWidthPx: Int = 1024
        private set

    /**
     * Resolved [TabbedChromeMode] after evaluating [chromeModeOverride], [TabbedFragmentConfig.chromeMode],
     * and [currentViewportWidthPx].
     */
    val resolvedChromeMode: TabbedChromeMode
        get() {
            val effective = chromeModeOverride ?: config.chromeMode
            return when (effective) {
                TabbedChromeMode.DESKTOP_BROWSER_TABS -> TabbedChromeMode.DESKTOP_BROWSER_TABS
                TabbedChromeMode.MOBILE_SWITCHER_PILL -> TabbedChromeMode.MOBILE_SWITCHER_PILL
                TabbedChromeMode.AUTO -> {
                    if (GenericUI.activeFactory?.backendTarget?.isMobile == true) {
                        TabbedChromeMode.MOBILE_SWITCHER_PILL
                    } else if (currentViewportWidthPx in 1 until config.mobileBreakpointWidthPx) {
                        TabbedChromeMode.MOBILE_SWITCHER_PILL
                    } else {
                        TabbedChromeMode.DESKTOP_BROWSER_TABS
                    }
                }
            }
        }

    /** Top slot container holding both the desktop tab strip and the mobile header bar. */
    val headerSlotContainer: FlexBox = FlexBox().apply {
        flexDirection = FlexDirection.COLUMN
        addClass("genericui-tabbed-header-slot")
    }

    /** Desktop horizontal browser-style tab strip container. */
    val desktopTabStripContainer: FlexBox = FlexBox().apply {
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.START
        addClass("genericui-tabbed-desktop-strip", "gap-1")
    }

    /** Mobile compact header bar with active tab title and `[N]` tab switcher pill button. */
    val mobileHeaderBarContainer: FlexBox = FlexBox().apply {
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.BETWEEN
        addClass("genericui-tabbed-mobile-bar", "gap-2")
    }

    /** Mobile `[N]` pill button that toggles the mobile tab switcher overview. */
    val mobileTabSwitcherPillButton: Button = Button(
        text = "0",
        type = BootstrapType.SECONDARY,
        size = BootstrapSize.SM,
        outline = true
    ).apply {
        addClass("genericui-tabbed-mobile-pill")
        setOnClick {
            toggleMobileTabOverview()
        }
    }

    /** Label in the mobile header bar displaying the active tab's title. */
    val mobileActiveTabTitleLabel: Label = Label("").apply {
        addClass("genericui-tabbed-mobile-title")
    }

    /** Card-grid container displayed when [isMobileTabOverviewOpen] is `true`. */
    val mobileTabOverviewContainer: Container = Container("div", "genericui-tabbed-mobile-overview").apply {
        isVisible = false
    }

    /** `+` button for requesting a new tab when [TabbedFragmentConfig.showNewTabButton] is enabled. */
    val newTabButton: Button = Button(
        text = "+",
        type = BootstrapType.LIGHT,
        size = BootstrapSize.SM
    ).apply {
        addClass("genericui-tabbed-new-tab-btn")
        setOnClick {
            requestNewTab()
        }
    }

    /** Invoked whenever the active tab changes (`tabId`, `spec`, and active `fragment`). */
    var onActiveTabChanged: ((tabId: String?, spec: TabItemSpec?, fragment: Fragment?) -> Unit)? = null

    /** Invoked when a new tab is opened. */
    var onTabOpened: ((spec: TabItemSpec) -> Unit)? = null

    /** Invoked when a tab is closed. */
    var onTabClosed: ((spec: TabItemSpec) -> Unit)? = null

    /** Optional factory callback invoked by [requestNewTab] (and the `+` button) to create a new [TabItemSpec]. */
    var onNewTabRequested: (() -> TabItemSpec?)? = null

    /** Invoked whenever [isMobileTabOverviewOpen] changes. */
    var onMobileOverviewStateChanged: ((isOpen: Boolean) -> Unit)? = null

    init {
        if (id != null) {
            this.id = id
        }
        addClass("genericui-tabbed-fragment")
        headerSlotContainer.addChild(desktopTabStripContainer)
        headerSlotContainer.addChild(mobileHeaderBarContainer)
        addChild(headerSlotContainer, 0)
        addChild(mobileTabOverviewContainer)
        syncChromeLayout()
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        ensurePersistenceInitialized()
        val targetTabId = pendingPersistedTabId ?: readPersistedActiveTabId()
        if (!targetTabId.isNullOrBlank() && tabSpecs.any { it.tabId == targetTabId }) {
            pendingPersistedTabId = null
            selectTabInternal(targetTabId, persistSelection = true)
        }
        syncChromeLayout()
    }

    private fun ensurePersistenceInitialized() {
        if (!hasInitializedPersistence) {
            hasInitializedPersistence = true
            pendingPersistedTabId = readPersistedActiveTabId()
        }
    }

    private fun readPersistedActiveTabId(): String? {
        val key = config.persistLastActiveTabKey?.takeIf { it.isNotBlank() } ?: return null
        return getPreferenceStore().getString(key, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Opens a tab described by [spec] (or updates the existing tab if `spec.tabId` is already open).
     *
     * @param spec Tab specification to register.
     * @param selectImmediately Whether to immediately activate this tab (`true` by default when no tab is active).
     */
    fun openTab(spec: TabItemSpec, selectImmediately: Boolean = activeTabId == null): TabItemSpec {
        ensurePersistenceInitialized()
        val existingIdx = tabSpecs.indexOfFirst { it.tabId == spec.tabId }
        if (existingIdx >= 0) {
            tabSpecs[existingIdx] = spec
            if (selectImmediately || spec.tabId == pendingPersistedTabId) {
                val matchesPersisted = spec.tabId == pendingPersistedTabId
                if (matchesPersisted) {
                    pendingPersistedTabId = null
                }
                selectTabInternal(spec.tabId, persistSelection = true)
            } else {
                rebuildChrome()
            }
            return spec
        }

        val wasEmpty = tabSpecs.isEmpty()
        tabSpecs.add(spec)
        onTabOpened?.invoke(spec)

        val matchesPersisted = spec.tabId == pendingPersistedTabId
        if (matchesPersisted) {
            pendingPersistedTabId = null
            selectTabInternal(spec.tabId, persistSelection = true)
        } else if (selectImmediately) {
            val shouldPersist = !(wasEmpty && !pendingPersistedTabId.isNullOrBlank())
            if (shouldPersist) {
                pendingPersistedTabId = null
            }
            selectTabInternal(spec.tabId, persistSelection = shouldPersist)
        } else {
            rebuildChrome()
        }
        return spec
    }

    /** Opens [spec] and immediately selects it as the active tab. */
    fun openAndSelectTab(spec: TabItemSpec): TabItemSpec =
        openTab(spec, selectImmediately = true)

    /** Convenience overload that constructs a [TabItemSpec] and opens it. */
    fun openTab(
        tabId: String,
        title: String,
        subtitle: String = "",
        iconName: String? = null,
        badgeText: String? = null,
        closable: Boolean = true,
        pinned: Boolean = false,
        selectImmediately: Boolean = activeTabId == null,
        fragmentFactory: () -> Fragment
    ): TabItemSpec = openTab(
        spec = TabItemSpec(
            tabId = tabId,
            title = title,
            subtitle = subtitle,
            iconName = iconName,
            badgeText = badgeText,
            closable = closable,
            pinned = pinned,
            fragmentFactory = fragmentFactory
        ),
        selectImmediately = selectImmediately
    )

    /**
     * Activates the tab identified by [tabId], lazily instantiating its [Fragment] on first selection
     * and reusing the cached [Fragment] instance on subsequent selections.
     *
     * @return `true` if [tabId] exists and was selected.
     */
    fun selectTab(tabId: String): Boolean {
        ensurePersistenceInitialized()
        pendingPersistedTabId = null
        return selectTabInternal(tabId, persistSelection = true)
    }

    private fun selectTabInternal(tabId: String, persistSelection: Boolean): Boolean {
        val spec = tabSpecs.firstOrNull { it.tabId == tabId } ?: return false
        val fragment = getOrCreateTabFragment(spec)
        val previousTabId = activeTabId
        activeTabId = tabId

        if (currentFragment !== fragment) {
            if (currentFragment == null) {
                setRootFragment(fragment, route = tabId)
            } else {
                pushFragment(fragment, route = tabId)
            }
        }

        if (isMobileTabOverviewOpen) {
            isMobileTabOverviewOpen = false
            onMobileOverviewStateChanged?.invoke(false)
        }

        if (persistSelection) {
            val persistKey = config.persistLastActiveTabKey
            if (!persistKey.isNullOrBlank()) {
                getPreferenceStore().putString(persistKey, tabId)
            }
        }

        rebuildChrome()
        syncOverviewVisibility()

        if (previousTabId != tabId) {
            onActiveTabChanged?.invoke(tabId, spec, fragment)
        }
        return true
    }

    /** Selects the tab at [index] (`0 until tabCount`), returning `true` if [index] is in bounds. */
    fun selectTabAt(index: Int): Boolean {
        val spec = tabSpecs.getOrNull(index) ?: return false
        return selectTab(spec.tabId)
    }

    /**
     * Closes the tab with [tabId] and destroys its cached [Fragment] if one was instantiated.
     *
     * Respects [TabItemSpec.closable], [TabItemSpec.pinned] (unless [forceClosePinned] is `true`),
     * and [TabbedFragmentConfig.allowCloseLastTab].
     */
    fun closeTab(tabId: String, forceClosePinned: Boolean = false): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        val spec = tabSpecs[idx]
        if (!spec.closable) return false
        if (spec.pinned && !forceClosePinned) return false
        if (tabSpecs.size <= 1 && !config.allowCloseLastTab) return false

        tabSpecs.removeAt(idx)
        val removedFragment = tabFragments.remove(tabId)
        val wasActive = activeTabId == tabId

        if (wasActive) {
            if (tabSpecs.isNotEmpty()) {
                val nextIdx = idx.coerceAtMost(tabSpecs.lastIndex)
                val nextSpec = tabSpecs[nextIdx]
                selectTab(nextSpec.tabId)
            } else {
                activeTabId = null
                if (removedFragment != null && stageContainer.children.contains(removedFragment)) {
                    stageContainer.removeChild(removedFragment)
                }
                val persistKey = config.persistLastActiveTabKey
                if (!persistKey.isNullOrBlank()) {
                    getPreferenceStore().remove(persistKey)
                }
                rebuildChrome()
                syncOverviewVisibility()
                onActiveTabChanged?.invoke(null, null, null)
            }
        } else {
            rebuildChrome()
        }

        removedFragment?.performDestroy()
        onTabClosed?.invoke(spec)
        return true
    }

    /** Updates the display title of [tabId], returning `true` if the tab was found. */
    fun updateTabTitle(tabId: String, newTitle: String): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        tabSpecs[idx] = tabSpecs[idx].copy(title = newTitle)
        rebuildChrome()
        return true
    }

    /** Updates the subtitle of [tabId], returning `true` if the tab was found. */
    fun updateTabSubtitle(tabId: String, newSubtitle: String): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        tabSpecs[idx] = tabSpecs[idx].copy(subtitle = newSubtitle)
        rebuildChrome()
        return true
    }

    /** Updates the badge text of [tabId], returning `true` if the tab was found. */
    fun updateTabBadge(tabId: String, badgeText: String?): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        tabSpecs[idx] = tabSpecs[idx].copy(badgeText = badgeText)
        rebuildChrome()
        return true
    }

    /** Reorders a tab from [fromIndex] to [toIndex], returning `true` if both indices are valid. */
    fun moveTab(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in tabSpecs.indices || toIndex !in tabSpecs.indices) return false
        if (fromIndex == toIndex) return true
        val item = tabSpecs.removeAt(fromIndex)
        tabSpecs.add(toIndex, item)
        rebuildChrome()
        return true
    }

    /** Returns the already-instantiated [Fragment] for [tabId], or `null` if not yet created. */
    fun getTabFragment(tabId: String): Fragment? = tabFragments[tabId]

    /** Returns the [Fragment] for [tabId], instantiating and caching it if the tab exists. */
    fun getOrCreateTabFragment(tabId: String): Fragment? {
        val spec = tabSpecs.firstOrNull { it.tabId == tabId } ?: return null
        return getOrCreateTabFragment(spec)
    }

    private fun getOrCreateTabFragment(spec: TabItemSpec): Fragment {
        return tabFragments.getOrPut(spec.tabId) {
            spec.fragmentFactory().also { created ->
                if (created.routeKey.isBlank()) {
                    created.routeKey = spec.tabId
                }
                if (created.pageTitle.isBlank()) {
                    created.pageTitle = spec.title
                }
            }
        }
    }

    /**
     * Invokes [onNewTabRequested] to create, open, and select a new tab.
     *
     * @return The newly opened [TabItemSpec], or `null` if [onNewTabRequested] is unset or returned `null`.
     */
    fun requestNewTab(): TabItemSpec? {
        val created = onNewTabRequested?.invoke() ?: return null
        return openAndSelectTab(created)
    }

    /** Opens the mobile tab switcher card overview. */
    fun openMobileTabOverview() {
        if (!isMobileTabOverviewOpen) {
            isMobileTabOverviewOpen = true
            rebuildMobileOverviewGrid()
            syncOverviewVisibility()
            onMobileOverviewStateChanged?.invoke(true)
        }
    }

    /** Closes the mobile tab switcher card overview and returns to the active tab's content. */
    fun closeMobileTabOverview() {
        if (isMobileTabOverviewOpen) {
            isMobileTabOverviewOpen = false
            syncOverviewVisibility()
            onMobileOverviewStateChanged?.invoke(false)
        }
    }

    /** Toggles the mobile tab switcher card overview between open and closed states. */
    fun toggleMobileTabOverview() {
        if (isMobileTabOverviewOpen) {
            closeMobileTabOverview()
        } else {
            openMobileTabOverview()
        }
    }

    /** Updates the tracked viewport dimensions and re-evaluates [resolvedChromeMode]. */
    fun updateViewportSize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0) {
            currentViewportWidthPx = widthPx
        }
        syncChromeLayout()
        activeFragment?.onResize(widthPx, heightPx)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        if (viewportWidth > 0) {
            currentViewportWidthPx = viewportWidth
        }
        syncChromeLayout()
    }

    private fun syncChromeLayout() {
        val mode = resolvedChromeMode
        desktopTabStripContainer.isVisible = (mode == TabbedChromeMode.DESKTOP_BROWSER_TABS)
        mobileHeaderBarContainer.isVisible = (mode == TabbedChromeMode.MOBILE_SWITCHER_PILL)
        if (mode == TabbedChromeMode.DESKTOP_BROWSER_TABS && isMobileTabOverviewOpen) {
            isMobileTabOverviewOpen = false
            onMobileOverviewStateChanged?.invoke(false)
        }
        rebuildChrome()
        syncOverviewVisibility()
    }

    private fun syncOverviewVisibility() {
        val showOverview = isMobileTabOverviewOpen &&
            resolvedChromeMode == TabbedChromeMode.MOBILE_SWITCHER_PILL
        mobileTabOverviewContainer.isVisible = showOverview
        stageContainer.isVisible = !showOverview
    }

    private fun rebuildChrome() {
        rebuildDesktopTabStrip()
        rebuildMobileHeaderBar()
        if (isMobileTabOverviewOpen) {
            rebuildMobileOverviewGrid()
        }
    }

    private fun rebuildDesktopTabStrip() {
        desktopTabStripContainer.clearChildren()
        for (spec in tabSpecs) {
            val isActive = spec.tabId == activeTabId
            val tabChip = FlexBox().apply {
                flexDirection = FlexDirection.ROW
                alignItems = AlignItems.CENTER
                addClass("genericui-tabbed-chip", "gap-1")
                if (isActive) addClass("active")
                if (spec.pinned) addClass("pinned")
            }

            val caption = buildString {
                if (!spec.iconName.isNullOrBlank()) {
                    append(spec.iconName).append(' ')
                }
                append(spec.title)
                if (!spec.badgeText.isNullOrBlank()) {
                    append(" (").append(spec.badgeText).append(')')
                }
            }

            val selectBtn = Button(
                text = caption,
                type = if (isActive) BootstrapType.PRIMARY else BootstrapType.LIGHT,
                size = BootstrapSize.SM
            ).apply {
                addClass("genericui-tabbed-select-btn")
                setOnClick {
                    selectTab(spec.tabId)
                }
            }
            tabChip.addChild(selectBtn)

            val canClose = spec.closable && !spec.pinned && (tabSpecs.size > 1 || config.allowCloseLastTab)
            if (canClose) {
                val closeBtn = Button(
                    text = "×",
                    type = BootstrapType.LINK,
                    size = BootstrapSize.SM
                ).apply {
                    addClass("genericui-tabbed-close-btn")
                    setOnClick {
                        closeTab(spec.tabId)
                    }
                }
                tabChip.addChild(closeBtn)
            }

            desktopTabStripContainer.addChild(tabChip)
        }

        if (config.showNewTabButton) {
            desktopTabStripContainer.addChild(newTabButton)
        }
    }

    private fun rebuildMobileHeaderBar() {
        mobileHeaderBarContainer.clearChildren()
        val active = activeTabSpec
        mobileActiveTabTitleLabel.text = active?.title ?: ""
        mobileTabSwitcherPillButton.text = tabSpecs.size.toString()

        mobileHeaderBarContainer.addChild(mobileActiveTabTitleLabel)

        val trailingActions = FlexBox().apply {
            flexDirection = FlexDirection.ROW
            alignItems = AlignItems.CENTER
            addClass("gap-2")
        }
        if (config.showNewTabButton) {
            val mobileNewBtn = Button(
                text = "+",
                type = BootstrapType.LIGHT,
                size = BootstrapSize.SM
            ).apply {
                addClass("genericui-tabbed-mobile-new-btn")
                setOnClick {
                    requestNewTab()
                }
            }
            trailingActions.addChild(mobileNewBtn)
        }
        trailingActions.addChild(mobileTabSwitcherPillButton)
        mobileHeaderBarContainer.addChild(trailingActions)
    }

    private fun rebuildMobileOverviewGrid() {
        mobileTabOverviewContainer.clearChildren()
        for (spec in tabSpecs) {
            val isActive = spec.tabId == activeTabId
            val card = FlexBox().apply {
                flexDirection = FlexDirection.COLUMN
                addClass("genericui-tabbed-overview-card", "gap-2")
                if (isActive) addClass("active")
            }

            val cardHeader = FlexBox().apply {
                flexDirection = FlexDirection.ROW
                alignItems = AlignItems.CENTER
                justifyContent = JustifyContent.BETWEEN
                addClass("gap-1")
            }
            val titleBtn = Button(
                text = spec.title,
                type = if (isActive) BootstrapType.PRIMARY else BootstrapType.SECONDARY,
                size = BootstrapSize.SM
            ).apply {
                addClass("genericui-tabbed-overview-select-btn")
                setOnClick {
                    selectTab(spec.tabId)
                }
            }
            cardHeader.addChild(titleBtn)

            val canClose = spec.closable && !spec.pinned && (tabSpecs.size > 1 || config.allowCloseLastTab)
            if (canClose) {
                val closeBtn = Button(
                    text = "×",
                    type = BootstrapType.DANGER,
                    size = BootstrapSize.SM,
                    outline = true
                ).apply {
                    addClass("genericui-tabbed-overview-close-btn")
                    setOnClick {
                        closeTab(spec.tabId)
                    }
                }
                cardHeader.addChild(closeBtn)
            }
            card.addChild(cardHeader)

            if (spec.subtitle.isNotBlank()) {
                card.addChild(Label(spec.subtitle).apply {
                    addClass("genericui-tabbed-overview-subtitle")
                })
            }
            if (!spec.badgeText.isNullOrBlank()) {
                card.addChild(Label(spec.badgeText).apply {
                    addClass("genericui-tabbed-overview-badge")
                })
            }
            mobileTabOverviewContainer.addChild(card)
        }
    }

    override fun onBackPressed(): Boolean {
        if (isMobileTabOverviewOpen) {
            closeMobileTabOverview()
            return true
        }
        return super.onBackPressed()
    }

    override fun onDestroy() {
        for (fragment in tabFragments.values.toList()) {
            if (fragment !== currentFragment) {
                fragment.performDestroy()
            }
        }
        tabFragments.clear()
        super.onDestroy()
    }
}
