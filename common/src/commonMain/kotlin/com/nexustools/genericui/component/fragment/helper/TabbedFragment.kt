package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.FlexBox
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.styled.StyledComponent
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssWhiteSpace
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.res.DataUrlCodec
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.TitleBar

private data class ChromiumThemePalette(
    val isDark: Boolean,
    val stripSurface: RgbaColor,
    val activeTabSurface: RgbaColor,
    val navbarSurface: RgbaColor,
    val inactiveTabHoverSurface: RgbaColor,
    val controlSubtleSurface: RgbaColor,
    val controlHoverSurface: RgbaColor,
    val controlActiveSurface: RgbaColor,
    val activeText: RgbaColor,
    val inactiveText: RgbaColor,
    val dividerColor: RgbaColor,
    val navbarBottomBorder: RgbaColor
)

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

    private class TabHeaderViewRef(
        val tabId: String,
        val itemContainer: AbstractContainer,
        val iconSlotContainer: AbstractContainer,
        var ownedIconImage: Image?,
        var activeIconImage: Image?,
        val fallbackBadgeLabel: Label,
        val titleLabel: Label?,
        val selectButton: Button?,
        var badgeLabel: Label?,
        val closeButton: AbstractComponent?,
        var isActive: Boolean,
        val isChromium: Boolean
    )

    private val tabSpecs = mutableListOf<TabItemSpec>()
    private val tabFragments = linkedMapOf<String, Fragment>()
    private val tabHeaderRefs = linkedMapOf<String, TabHeaderViewRef>()
    private var pendingPersistedTabId: String? = null
    private var hasInitializedPersistence: Boolean = false
    private var lastSyncedChromeMode: TabbedChromeMode? = null

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

    private var chromeInitialized: Boolean = false
    private var isSyncingChrome: Boolean = false
    private var stoppedForTitleBar: Boolean = false
    private var disposedForTitleBar: Boolean = false
    private var updatingManagedTitleBar: Boolean = false
    private var ownedManagedTitleBar: TitleBar? = null
    private var mountedTitleBarRef: TitleBar? = null
    private var mountedTitleBarWindowRef: IWindow? = null
    private var ownedTitleBarStripAttached: Boolean = false
    private var observedTitleBarWindowRef: Window? = null
    private var observedThemeAppRef: Application? = null

    private val titleBarChangedListener: (TitleBar?) -> Unit = { _ ->
        if (!updatingManagedTitleBar && chromeInitialized) {
            syncChromeLayout()
        }
    }

    private val themeChangeListener: (ThemeMode) -> Unit = { _ ->
        if (chromeInitialized && resolvedChromeStyle == TabbedChromeStyle.CHROMIUM) {
            syncChromeLayout()
        }
    }

    /** Optional runtime override for [TabbedFragmentConfig.chromeStyle]. */
    var chromeStyleOverride: TabbedChromeStyle? = null
        set(value) {
            if (field != value) {
                field = value
                if (chromeInitialized) {
                    syncChromeLayout()
                }
            }
        }

    /** Resolved [TabbedChromeStyle] after evaluating [chromeStyleOverride] and [TabbedFragmentConfig.chromeStyle]. */
    val resolvedChromeStyle: TabbedChromeStyle
        get() = chromeStyleOverride ?: config.chromeStyle

    /** `true` when responsive layout currently resolves to [TabbedChromeMode.MOBILE_SWITCHER_PILL]. */
    val isCompactMode: Boolean
        get() = resolvedChromeMode == TabbedChromeMode.MOBILE_SWITCHER_PILL

    /** `true` when [desktopTabStripContainer] is currently embedded into the host [TitleBar]. */
    val isEmbeddedInTitleBar: Boolean
        get() = ownedTitleBarStripAttached

    /** Top slot container holding both the desktop tab strip and the mobile header bar. */
    val headerSlotContainer: FlexBox = FlexBox().apply {
        flexDirection = FlexDirection.COLUMN
        addClass("genericui-tabbed-header-slot")
    }

    /** Desktop horizontal browser-style tab strip container. */
    val desktopTabStripContainer: StyledContainer = object : StyledContainer(
        classPrefix = "styled-container",
        tagName = "genericui:styled-container"
    ) {
        override fun onThemeChanged() {
            super.onThemeChanged()
            if (chromeInitialized && resolvedChromeStyle == TabbedChromeStyle.CHROMIUM) {
                syncChromeLayout()
            }
        }
    }.apply {
        setDisplay(DisplayMode.FLEX)
        getStyle().apply {
            setDisplay(CssDisplay.FLEX)
            setFlexDirection(FlexDirection.ROW)
            setAlignItems(AlignItems.CENTER)
            setJustifyContent(JustifyContent.START)
        }
        addClass("genericui-tabbed-desktop-strip", "gap-1")
    }

    /** Persistent navbar slot directly below the tab strip (or at the top of content when embedded in `TitleBar`). */
    val tabNavbarSlotContainer: StyledContainer = object : StyledContainer(
        classPrefix = "styled-container",
        tagName = "genericui:styled-container"
    ) {
        override fun onThemeChanged() {
            super.onThemeChanged()
            if (chromeInitialized && resolvedChromeStyle == TabbedChromeStyle.CHROMIUM) {
                syncChromeLayout()
            }
        }
    }.apply {
        isVisible = false
        addClass("gui-tab-navbar", "genericui-tabbed-navbar-slot")
        setAttribute("data-gui-tab-navbar", "true")
        setDisplay(DisplayMode.FLEX)
        getStyle().apply {
            setDisplay(CssDisplay.FLEX)
            setFlexDirection(FlexDirection.ROW)
            setAlignItems(AlignItems.CENTER)
            setJustifyContent(JustifyContent.START)
            setMargin(CssEdgeLengths.ZERO)
            setPadding(CssEdgeLengths.px(4, 10, 4, 10))
        }
    }

    /** Currently installed navbar component inside [tabNavbarSlotContainer], if any. */
    var currentTabNavbar: AbstractComponent? = null
        private set

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

    /** Far-left circular Chromium tab-search (`⌄`) button displayed when [TabbedFragmentConfig.showTabSearchButton] is `true`. */
    val tabSearchButton: StyledButton = StyledButton(
        text = "⌄",
        icon = null,
        type = null,
        size = BootstrapSize.SM
    ).apply {
        addClass("gui-tab-search-btn", "genericui-tabbed-search-btn")
        setAttribute("data-gui-tab-search", "true")
        getStyle().apply {
            setWidthPx(28)
            setHeightPx(28)
            setMinWidthPx(28)
            setMinHeightPx(28)
            setPadding(CssEdgeLengths.ZERO)
            setMargin(CssEdgeLengths.px(0, 2, 3, 0))
            setBorderRadius(CssCornerRadii.px(999))
            setBorderWidth(CssEdgeLengths.ZERO)
            setBorderStyle(CssBorderStyle.NONE)
            setFontSizePx(13f)
            setCursor(CssCursor.POINTER)
        }
        setOnClick {
            openTabSearchPicker()
        }
    }

    /** Lightweight popup menu listing open tabs for [openTabSearchPicker]. */
    val tabSearchContextMenu: ContextMenu = ContextMenu()

    /** Circular Chromium `+` new-tab button placed immediately after the tab items. */
    val chromiumNewTabButton: StyledButton = StyledButton(
        text = "+",
        icon = null,
        type = null,
        size = BootstrapSize.SM
    ).apply {
        addClass("gui-tab-add-btn", "genericui-tabbed-new-tab-btn")
        setAttribute("data-gui-tab-add", "true")
        getStyle().apply {
            setWidthPx(28)
            setHeightPx(28)
            setMinWidthPx(28)
            setMinHeightPx(28)
            setPadding(CssEdgeLengths.ZERO)
            setMargin(CssEdgeLengths.px(0, 0, 3, 4))
            setBorderRadius(CssCornerRadii.px(999))
            setBorderWidth(CssEdgeLengths.ZERO)
            setBorderStyle(CssBorderStyle.NONE)
            setFontSizePx(16f)
            setCursor(CssCursor.POINTER)
        }
        setOnClick {
            requestNewTab()
        }
    }

    /** Non-interactive flex spacer filling empty strip space so desktop titlebar drag/maximize works smoothly. */
    val chromiumDragSpacer: StyledComponent = StyledComponent().apply {
        addClass("gui-tab-drag-spacer")
        setAttribute("data-gui-drag-spacer", "true")
        setAttribute("data-win32-htcaption", "true")
        backgroundColor = RgbaColor.TRANSPARENT
        pointerEventsEnabled = false
        getStyle().apply {
            setFlexGrow(1f)
            setMinWidthPx(16)
            setHeightPx(32)
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

    /** Optional override callback invoked by [openTabSearchPicker] instead of the default tab-picker popup/cycle. */
    var onTabSearchRequested: (() -> Unit)? = null

    /** Invoked whenever [isMobileTabOverviewOpen] changes. */
    var onMobileOverviewStateChanged: ((isOpen: Boolean) -> Unit)? = null

    /** Invoked whenever responsive chrome layout is synchronized (e.g. viewport resize or chrome mode change). */
    var onChromeLayoutSynced: ((mode: TabbedChromeMode) -> Unit)? = null

    init {
        if (id != null) {
            this.id = id
        }
        addClass("genericui-tabbed-fragment")
        headerSlotContainer.addChild(desktopTabStripContainer)
        headerSlotContainer.addChild(mobileHeaderBarContainer)
        addChild(headerSlotContainer, 0)
        addChild(tabNavbarSlotContainer, 1)
        addChild(mobileTabOverviewContainer)
        chromeInitialized = true
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
        iconUrl: String? = null,
        iconRes: ResourceID? = null,
        iconImage: Image? = null,
        tintIconToTheme: Boolean = false,
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
            fragmentFactory = fragmentFactory,
            iconUrl = iconUrl,
            iconRes = iconRes,
            iconImage = iconImage,
            tintIconToTheme = tintIconToTheme
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

    /** Updates the display title (and optional subtitle) of [tabId] in place, returning `true` if the tab was found. */
    fun updateTabTitle(tabId: String, newTitle: String, newSubtitle: String? = null): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        val current = tabSpecs[idx]
        val updated = current.copy(
            title = newTitle,
            subtitle = newSubtitle ?: current.subtitle
        )
        tabSpecs[idx] = updated
        val ref = tabHeaderRefs[tabId]
        if (ref != null) {
            if (ref.isChromium) {
                ref.titleLabel?.text = newTitle
            } else {
                ref.selectButton?.text = buildClassicTabCaption(updated, showInlineIconText = !ref.iconSlotContainer.isVisible)
            }
            if (tabId == activeTabId) {
                mobileActiveTabTitleLabel.text = newTitle
            }
            if (isMobileTabOverviewOpen) {
                rebuildMobileOverviewGrid()
            }
        } else {
            rebuildChrome()
        }
        return true
    }

    /** Updates the subtitle of [tabId], returning `true` if the tab was found. */
    fun updateTabSubtitle(tabId: String, newSubtitle: String): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        tabSpecs[idx] = tabSpecs[idx].copy(subtitle = newSubtitle)
        if (isMobileTabOverviewOpen) {
            rebuildMobileOverviewGrid()
        } else if (tabHeaderRefs[tabId] == null) {
            rebuildChrome()
        }
        return true
    }

    /** Updates the badge text of [tabId], returning `true` if the tab was found. */
    fun updateTabBadge(tabId: String, badgeText: String?): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        val updated = tabSpecs[idx].copy(badgeText = badgeText)
        tabSpecs[idx] = updated
        val ref = tabHeaderRefs[tabId]
        if (ref != null) {
            if (!ref.isChromium) {
                ref.selectButton?.text = buildClassicTabCaption(updated, showInlineIconText = !ref.iconSlotContainer.isVisible)
                if (isMobileTabOverviewOpen) {
                    rebuildMobileOverviewGrid()
                }
                return true
            }
            val existingBadge = ref.badgeLabel
            if (existingBadge != null && !badgeText.isNullOrBlank()) {
                existingBadge.text = badgeText
                if (isMobileTabOverviewOpen) {
                    rebuildMobileOverviewGrid()
                }
                return true
            }
        }
        rebuildChrome()
        return true
    }

    /**
     * Updates the icon/favicon of [tabId] in place without tearing down or rebuilding the tab strip,
     * returning `true` if the tab was found.
     */
    fun updateTabIcon(
        tabId: String,
        iconUrl: String? = null,
        iconRes: ResourceID? = null,
        iconImage: Image? = null,
        tintIconToTheme: Boolean = false
    ): Boolean {
        val idx = tabSpecs.indexOfFirst { it.tabId == tabId }
        if (idx < 0) return false
        val updated = tabSpecs[idx].copy(
            iconUrl = iconUrl,
            iconRes = iconRes,
            iconImage = iconImage,
            tintIconToTheme = tintIconToTheme
        )
        tabSpecs[idx] = updated
        val ref = tabHeaderRefs[tabId]
        if (ref != null) {
            applySpecIconToHeaderRef(updated, ref)
            if (!ref.isChromium) {
                ref.selectButton?.text = buildClassicTabCaption(updated, showInlineIconText = !ref.iconSlotContainer.isVisible)
            }
        } else {
            rebuildChrome()
        }
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
        val previousMode = resolvedChromeMode
        if (widthPx > 0) {
            currentViewportWidthPx = widthPx
        }
        val newMode = resolvedChromeMode
        if (!chromeInitialized || lastSyncedChromeMode != newMode || previousMode != newMode) {
            syncChromeLayout()
        }
        activeFragment?.onResize(widthPx, heightPx)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        val previousMode = resolvedChromeMode
        if (viewportWidth > 0) {
            currentViewportWidthPx = viewportWidth
        }
        val newMode = resolvedChromeMode
        if (!chromeInitialized || lastSyncedChromeMode != newMode || previousMode != newMode) {
            syncChromeLayout()
        }
    }

    /**
     * Installs or clears the persistent navbar rendered directly beneath the tab strip (or at the top of
     * [TabbedFragment]'s content root when tabs are embedded inside the host window's [TitleBar]).
     */
    fun setTabNavbar(navbar: AbstractComponent?) {
        if (currentTabNavbar === navbar) {
            syncTabNavbarSlot(if (resolvedChromeStyle == TabbedChromeStyle.CHROMIUM) resolveChromiumPalette() else null)
            return
        }
        currentTabNavbar = navbar
        tabNavbarSlotContainer.clearChildren()
        if (navbar != null) {
            val oldParent = navbar.parent
            if (oldParent is AbstractContainer && oldParent !== tabNavbarSlotContainer) {
                oldParent.removeChild(navbar)
            }
            tabNavbarSlotContainer.addChild(navbar)
        }
        syncTabNavbarSlot(if (resolvedChromeStyle == TabbedChromeStyle.CHROMIUM) resolveChromiumPalette() else null)
    }

    /**
     * Opens a lightweight popup tab-switcher menu listing all open tabs when a native factory is available,
     * invokes [onTabSearchRequested] if registered, or cycles cleanly to the next tab in headless mode.
     */
    fun openTabSearchPicker() {
        populateTabSearchContextMenu()
        val customHandler = onTabSearchRequested
        if (customHandler != null) {
            customHandler.invoke()
            return
        }
        if (tabSpecs.isEmpty()) return
        val factory = tabSearchButton.resolveActiveFactory()
            ?: resolveActiveFactory()
            ?: GenericUI.activeFactory
        if (factory != null) {
            tabSearchContextMenu.showAt(tabSearchButton, 0, 32)
        } else {
            val nextIdx = (activeTabIndex + 1).coerceAtLeast(0) % tabSpecs.size
            selectTab(tabSpecs[nextIdx].tabId)
        }
    }

    private fun populateTabSearchContextMenu() {
        tabSearchContextMenu.clearChildren()
        for (spec in tabSpecs) {
            val isActive = spec.tabId == activeTabId
            val label = if (isActive) "✓ ${spec.title}" else spec.title
            tabSearchContextMenu.addItem(
                label = label,
                icon = spec.iconName,
                onClick = {
                    selectTab(spec.tabId)
                }
            )
        }
    }

    private fun resolveHostIWindow(): IWindow? {
        hostingWindow?.let { return it }
        attachedIWindow?.let { return it }
        attachedWindow?.let { return it }
        var cur: AbstractComponent? = parent
        while (cur != null) {
            if (cur is IWindow) return cur
            cur = cur.parent
        }
        return null
    }

    private fun resolveThemeApplication(): Application? {
        val hostWindow = resolveHostIWindow()
        if (hostWindow != null) {
            (Application.findApplicationForWindow(hostWindow) as? Application)?.let { return it }
        }
        return (application as? Application) ?: Application.current
    }

    private fun syncThemeAndWindowObservers() {
        val nextApp = resolveThemeApplication()
        if (observedThemeAppRef !== nextApp) {
            observedThemeAppRef?.removeThemeChangeListener(themeChangeListener)
            observedThemeAppRef = nextApp
            if (!disposedForTitleBar && !isDisposed) {
                nextApp?.addThemeChangeListener(themeChangeListener)
            }
        }
        val nextWindow = resolveHostIWindow() as? Window
        if (observedTitleBarWindowRef !== nextWindow) {
            observedTitleBarWindowRef?.removeOnTitleBarChangedListener(titleBarChangedListener)
            observedTitleBarWindowRef = nextWindow
            if (!disposedForTitleBar && !isDisposed) {
                nextWindow?.addOnTitleBarChangedListener(titleBarChangedListener)
            }
        }
    }

    private fun clearThemeAndWindowObservers() {
        observedThemeAppRef?.removeThemeChangeListener(themeChangeListener)
        observedThemeAppRef = null
        observedTitleBarWindowRef?.removeOnTitleBarChangedListener(titleBarChangedListener)
        observedTitleBarWindowRef = null
    }

    private fun blendColors(a: RgbaColor, b: RgbaColor, weightB: Float): RgbaColor {
        val w = weightB.coerceIn(0f, 1f)
        val inv = 1f - w
        return RgbaColor(
            r = (a.r * inv + b.r * w).toInt().coerceIn(0, 255),
            g = (a.g * inv + b.g * w).toInt().coerceIn(0, 255),
            b = (a.b * inv + b.b * w).toInt().coerceIn(0, 255),
            a = (a.a * inv + b.a * w).toInt().coerceIn(0, 255)
        )
    }

    private fun resolveChromiumPalette(): ChromiumThemePalette {
        val app = resolveThemeApplication()
        val themeMode = app?.getResolvedTheme() ?: SystemThemeDetector.resolveTheme(GenericUI.defaultThemeMode)
        val isDark = themeMode == ThemeMode.DARK
        val themeConfig = app?.getThemeConfig() ?: GenericUI.defaultThemeConfig
        val colorSet = themeConfig.resolveEffectiveColors(isDark)

        return if (isDark) {
            val baseSurface = colorSet.surface ?: RgbaColor(48, 50, 56, 255)
            val baseBg = colorSet.background ?: RgbaColor(28, 30, 34, 255)
            val activeSurface = blendColors(baseSurface, RgbaColor(60, 64, 72, 255), 0.35f)
            val stripSurface = blendColors(baseBg, RgbaColor(26, 28, 32, 255), 0.5f)
            val onText = colorSet.onSurface ?: colorSet.onDark ?: RgbaColor(241, 243, 244, 255)
            ChromiumThemePalette(
                isDark = true,
                stripSurface = stripSurface,
                activeTabSurface = activeSurface,
                navbarSurface = activeSurface,
                inactiveTabHoverSurface = RgbaColor(255, 255, 255, 26),
                controlSubtleSurface = RgbaColor(255, 255, 255, 20),
                controlHoverSurface = RgbaColor(255, 255, 255, 34),
                controlActiveSurface = RgbaColor(255, 255, 255, 52),
                activeText = onText,
                inactiveText = RgbaColor(onText.r, onText.g, onText.b, 198),
                dividerColor = RgbaColor(255, 255, 255, 38),
                navbarBottomBorder = RgbaColor(255, 255, 255, 28)
            )
        } else {
            val baseSurface = colorSet.surface ?: RgbaColor(255, 255, 255, 255)
            val baseBg = colorSet.background ?: RgbaColor(222, 225, 230, 255)
            val activeSurface = blendColors(baseSurface, RgbaColor(255, 255, 255, 255), 0.7f)
            val stripSurface = blendColors(baseBg, RgbaColor(222, 225, 230, 255), 0.65f)
            val onText = colorSet.onSurface ?: colorSet.onLight ?: RgbaColor(32, 33, 36, 255)
            ChromiumThemePalette(
                isDark = false,
                stripSurface = stripSurface,
                activeTabSurface = activeSurface,
                navbarSurface = activeSurface,
                inactiveTabHoverSurface = RgbaColor(0, 0, 0, 18),
                controlSubtleSurface = RgbaColor(0, 0, 0, 14),
                controlHoverSurface = RgbaColor(0, 0, 0, 22),
                controlActiveSurface = RgbaColor(0, 0, 0, 36),
                activeText = onText,
                inactiveText = RgbaColor(onText.r, onText.g, onText.b, 190),
                dividerColor = RgbaColor(0, 0, 0, 42),
                navbarBottomBorder = RgbaColor(0, 0, 0, 24)
            )
        }
    }

    private fun resolveActiveEmbeddingTitleBar(): TitleBar? {
        if (!ownedTitleBarStripAttached) return null
        return mountedTitleBarRef
    }

    private fun resolveBestAvailableStyleSheet(): CssStyleSheet? {
        return lastAppliedStyleSheet
            ?: parent?.lastAppliedStyleSheet
            ?: (resolveHostIWindow() as? AbstractComponent)?.lastAppliedStyleSheet
            ?: resolveThemeApplication()?.buildCombinedActiveStyleSheet()
    }

    private fun getOrCreateManagedTitleBar(): TitleBar {
        return ownedManagedTitleBar ?: TitleBar(
            showWindowTitle = false,
            showWindowIcon = config.showTitleBarWindowIcon
        ).also { ownedManagedTitleBar = it }
    }

    private fun syncOwnedTitleBarEmbedding(
        shouldEmbed: Boolean,
        hostWindow: IWindow?
    ) {
        if (!shouldEmbed || hostWindow == null) {
            detachOwnedTitleBarStrip()
            ensureStripMountedInHeaderSlot()
            return
        }

        val existingTb = hostWindow.getTitleBar()
        val targetTb = existingTb ?: getOrCreateManagedTitleBar()

        if (mountedTitleBarRef !== targetTb || mountedTitleBarWindowRef !== hostWindow) {
            detachOwnedTitleBarStrip()
        }

        if (desktopTabStripContainer.parent === headerSlotContainer) {
            headerSlotContainer.removeChild(desktopTabStripContainer)
        }

        if (existingTb == null && hostWindow.getTitleBar() !== targetTb) {
            updatingManagedTitleBar = true
            try {
                hostWindow.setTitleBar(targetTb)
            } finally {
                updatingManagedTitleBar = false
            }
        }

        desktopTabStripContainer.isVisible = true
        if (!targetTb.containsComponent(desktopTabStripContainer)) {
            targetTb.addLeading(desktopTabStripContainer)
        }
        mountedTitleBarRef = targetTb
        mountedTitleBarWindowRef = hostWindow
        ownedTitleBarStripAttached = true
    }

    private fun detachOwnedTitleBarStrip() {
        val tb = mountedTitleBarRef
        val win = mountedTitleBarWindowRef
        if (tb != null) {
            tb.removeLeading(desktopTabStripContainer)
            if (win != null &&
                tb === ownedManagedTitleBar &&
                win.getTitleBar() === tb &&
                tb.leadingComponents.isEmpty() &&
                tb.centerComponents.isEmpty() &&
                tb.trailingComponents.isEmpty() &&
                !tb.embedMenuBar
            ) {
                updatingManagedTitleBar = true
                try {
                    win.setTitleBar(null)
                } finally {
                    updatingManagedTitleBar = false
                }
            }
        }
        desktopTabStripContainer.mountedInTitleBar = false
        mountedTitleBarRef = null
        mountedTitleBarWindowRef = null
        ownedTitleBarStripAttached = false
    }

    private fun ensureStripMountedInHeaderSlot() {
        if (desktopTabStripContainer.parent !== headerSlotContainer) {
            val prev = desktopTabStripContainer.parent
            if (prev is AbstractContainer) {
                prev.removeChild(desktopTabStripContainer)
            } else {
                desktopTabStripContainer.parent = null
            }
            headerSlotContainer.addChild(desktopTabStripContainer, 0)
        }
    }

    private fun syncTabNavbarSlot(chromiumPalette: ChromiumThemePalette?) {
        val visible = currentTabNavbar != null
        tabNavbarSlotContainer.isVisible = visible
        tabNavbarSlotContainer.getStyle().apply {
            setMargin(CssEdgeLengths.ZERO)
            setMarginTop(CssLength.ZERO)
        }
        if (chromiumPalette != null && resolvedChromeStyle == TabbedChromeStyle.CHROMIUM) {
            tabNavbarSlotContainer.addClass("gui-tab-navbar", "gui-tab-navbar--chromium")
            tabNavbarSlotContainer.setAttribute("data-gui-chrome-style", "chromium")
            tabNavbarSlotContainer.setAttribute("data-gui-theme", if (chromiumPalette.isDark) "dark" else "light")
            tabNavbarSlotContainer.backgroundColor = chromiumPalette.navbarSurface
            tabNavbarSlotContainer.getStyle().apply {
                setBorderWidth(CssEdgeLengths.px(0, 0, 1, 0))
                setBorderStyle(CssBorderStyle.SOLID)
                setBorderBottomColor(chromiumPalette.navbarBottomBorder)
                setBorderRadius(CssCornerRadii.ZERO)
            }
        } else {
            tabNavbarSlotContainer.removeClass("gui-tab-navbar--chromium")
            tabNavbarSlotContainer.removeAttribute("data-gui-chrome-style")
            tabNavbarSlotContainer.removeAttribute("data-gui-theme")
            tabNavbarSlotContainer.backgroundColor = null
            tabNavbarSlotContainer.getStyle().apply {
                setBorderWidth(null)
                setBorderStyle(null)
                setBorderBottomColor(null)
            }
        }
    }

    private fun syncChromeLayout() {
        if (!chromeInitialized || isSyncingChrome) return
        isSyncingChrome = true
        try {
            syncThemeAndWindowObservers()
            val mode = resolvedChromeMode
            val style = resolvedChromeStyle
            val hostWindow = resolveHostIWindow()
            val caps = hostWindow?.titleCapabilities
            val isHostActiveForTitleBar = !stoppedForTitleBar && !disposedForTitleBar && !isDisposed
            val shouldEmbedInTitleBar = mode == TabbedChromeMode.DESKTOP_BROWSER_TABS &&
                style == TabbedChromeStyle.CHROMIUM &&
                isHostActiveForTitleBar &&
                hostWindow != null &&
                caps != null &&
                caps.canEmbedComponentsInTitleBar &&
                caps.supportsCustomTitleBar &&
                !caps.titleBarRendersAsNavbar

            val prevTb = mountedTitleBarRef
            val expectedTb = if (shouldEmbedInTitleBar) {
                hostWindow.getTitleBar() ?: getOrCreateManagedTitleBar()
            } else {
                prevTb
            }
            expectedTb?.beginBatchUpdate()
            try {
                syncOwnedTitleBarEmbedding(shouldEmbedInTitleBar, hostWindow)
                if (ownedTitleBarStripAttached) {
                    headerSlotContainer.isVisible = false
                    desktopTabStripContainer.isVisible = true
                    mobileHeaderBarContainer.isVisible = false
                } else {
                    headerSlotContainer.isVisible = true
                    desktopTabStripContainer.isVisible = (mode == TabbedChromeMode.DESKTOP_BROWSER_TABS)
                    mobileHeaderBarContainer.isVisible = (mode == TabbedChromeMode.MOBILE_SWITCHER_PILL)
                }
                if (style == TabbedChromeStyle.CHROMIUM) {
                    headerSlotContainer.getStyle().apply {
                        setPadding(CssEdgeLengths.ZERO)
                        setMargin(CssEdgeLengths.ZERO)
                        setGap(CssLength.ZERO)
                    }
                } else {
                    headerSlotContainer.getStyle().apply {
                        setPadding(null)
                        setMargin(null)
                        setGap(null)
                    }
                }
                if (mode == TabbedChromeMode.DESKTOP_BROWSER_TABS && isMobileTabOverviewOpen) {
                    isMobileTabOverviewOpen = false
                    onMobileOverviewStateChanged?.invoke(false)
                }
                lastSyncedChromeMode = mode
                rebuildChrome()
                syncOverviewVisibility()
                onChromeLayoutSynced?.invoke(mode)
            } finally {
                expectedTb?.endBatchUpdate()
            }
        } finally {
            isSyncingChrome = false
        }
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
        if (resolvedChromeStyle == TabbedChromeStyle.CHROMIUM) {
            rebuildChromiumDesktopTabStrip()
        } else {
            rebuildClassicDesktopTabStrip()
        }
    }

    private fun isIconUriOrPath(value: String?): Boolean {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("data:") ||
            lower.startsWith("res://") ||
            lower.startsWith("memory://") ||
            lower.startsWith("file://") ||
            lower.startsWith("/")
        ) {
            return true
        }
        val path = lower.substringBefore('?').substringBefore('#')
        return path.endsWith(".svg") ||
            path.endsWith(".svgz") ||
            path.endsWith(".png") ||
            path.endsWith(".ico") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".webp") ||
            path.endsWith(".gif") ||
            path.endsWith(".bmp")
    }

    private fun buildClassicTabCaption(spec: TabItemSpec, showInlineIconText: Boolean): String = buildString {
        if (showInlineIconText && !spec.iconName.isNullOrBlank()) {
            append(spec.iconName).append(' ')
        }
        append(spec.title)
        if (!spec.badgeText.isNullOrBlank()) {
            append(" (").append(spec.badgeText).append(')')
        }
    }

    private fun createTabIconSlotContainer(): FlexBox {
        return FlexBox().apply {
            flexDirection = FlexDirection.ROW
            alignItems = AlignItems.CENTER
            justifyContent = JustifyContent.CENTER
            addClass("gui-tab-icon-slot")
            setAttribute("data-gui-tab-icon-slot", "true")
            pointerEventsEnabled = false
            widthPx = 16
            heightPx = 16
            minWidthPx = 16
            minHeightPx = 16
            maxWidthPx = 16
            maxHeightPx = 16
            getStyle().apply {
                setDisplay(CssDisplay.FLEX)
                setFlexDirection(FlexDirection.ROW)
                setAlignItems(AlignItems.CENTER)
                setJustifyContent(JustifyContent.CENTER)
                setWidthPx(16)
                setHeightPx(16)
                setMinWidthPx(16)
                setMinHeightPx(16)
                setMaxWidthPx(16)
                setMaxHeightPx(16)
                setFlexShrink(0f)
                setPointerEvents(CssPointerEvents.NONE)
            }
        }
    }

    private fun configureSlotImageView(
        image: Image,
        sizePx: Int,
        tintToTheme: Boolean,
        opacity: Float = 1f,
        clearPlaceholder: Boolean = false
    ) {
        image.addClass("gui-tab-favicon")
        image.widthPx = sizePx
        image.heightPx = sizePx
        image.objectFit = ObjectFit.CONTAIN
        image.pointerEventsEnabled = false
        image.opacity = opacity
        if (clearPlaceholder) {
            image.placeholderUrl = null
        }
        image.setMonocolorThemeRecolor(tintToTheme)
        image.getStyle().apply {
            setWidthPx(sizePx)
            setHeightPx(sizePx)
            setMinWidthPx(sizePx)
            setMinHeightPx(sizePx)
            setMaxWidthPx(sizePx)
            setMaxHeightPx(sizePx)
            setFlexShrink(0f)
            setPointerEvents(CssPointerEvents.NONE)
        }
    }

    private fun ensureOwnedSlotImage(ref: TabHeaderViewRef, initialSrc: String = ""): Image {
        val existingOwned = ref.ownedIconImage
        val target = existingOwned ?: Image(src = initialSrc, alt = "", objectFit = ObjectFit.CONTAIN).also {
            ref.ownedIconImage = it
        }
        if (ref.activeIconImage !== target) {
            ref.activeIconImage?.let { prev ->
                if (prev.parent === ref.iconSlotContainer) {
                    ref.iconSlotContainer.removeChild(prev)
                }
            }
            if (target.parent !== ref.iconSlotContainer) {
                ref.iconSlotContainer.addChild(target)
            }
            ref.activeIconImage = target
        }
        return target
    }

    private fun applySpecIconToHeaderRef(spec: TabItemSpec, ref: TabHeaderViewRef) {
        val explicitImage = spec.iconImage
        val iconRes = spec.iconRes
        val uriFromField = spec.iconUrl?.trim()?.takeIf { it.isNotEmpty() }
        val uriFromName = spec.iconName?.trim()?.takeIf { isIconUriOrPath(it) }
        val resolvedUri = uriFromField ?: uriFromName

        when {
            explicitImage != null -> {
                if (ref.activeIconImage !== explicitImage) {
                    ref.activeIconImage?.let { prev ->
                        if (prev.parent === ref.iconSlotContainer) {
                            ref.iconSlotContainer.removeChild(prev)
                        }
                    }
                    if (explicitImage.parent !== ref.iconSlotContainer) {
                        ref.iconSlotContainer.addChild(explicitImage)
                    }
                    ref.activeIconImage = explicitImage
                }
                configureSlotImageView(
                    image = explicitImage,
                    sizePx = 16,
                    tintToTheme = spec.tintIconToTheme,
                    opacity = 1f
                )
                explicitImage.isVisible = true
                ref.iconSlotContainer.isVisible = true
                ref.fallbackBadgeLabel.isVisible = false
            }
            iconRes != null -> {
                val img = ensureOwnedSlotImage(ref)
                configureSlotImageView(
                    image = img,
                    sizePx = 16,
                    tintToTheme = spec.tintIconToTheme,
                    opacity = 1f
                )
                img.setSource(iconRes)
                img.isVisible = true
                ref.iconSlotContainer.isVisible = true
                ref.fallbackBadgeLabel.isVisible = false
            }
            resolvedUri != null -> {
                val img = ensureOwnedSlotImage(ref, resolvedUri)
                val isInlineSvgData = resolvedUri.startsWith("data:image/svg", ignoreCase = true)
                configureSlotImageView(
                    image = img,
                    sizePx = 16,
                    tintToTheme = spec.tintIconToTheme,
                    opacity = 1f,
                    clearPlaceholder = isInlineSvgData
                )
                if (img.sourceUrl != resolvedUri || img.resourceId != null) {
                    img.setSource(resolvedUri)
                }
                img.isVisible = true
                ref.iconSlotContainer.isVisible = true
                ref.fallbackBadgeLabel.isVisible = false
            }
            !spec.iconName.isNullOrBlank() -> {
                ref.activeIconImage?.let { prev ->
                    if (prev !== ref.ownedIconImage && prev.parent === ref.iconSlotContainer) {
                        ref.iconSlotContainer.removeChild(prev)
                    } else {
                        prev.isVisible = false
                    }
                }
                ref.activeIconImage = null
                ref.iconSlotContainer.isVisible = false
                if (ref.isChromium) {
                    ref.fallbackBadgeLabel.text = spec.iconName
                    ref.fallbackBadgeLabel.isVisible = true
                } else {
                    ref.fallbackBadgeLabel.isVisible = false
                }
            }
            ref.isChromium -> {
                val img = ensureOwnedSlotImage(ref, CHROMIUM_FALLBACK_GLOBE_DATA_URL)
                configureSlotImageView(
                    image = img,
                    sizePx = 14,
                    tintToTheme = true,
                    opacity = 0.75f,
                    clearPlaceholder = true
                )
                if (img.sourceUrl != CHROMIUM_FALLBACK_GLOBE_DATA_URL || img.resourceId != null) {
                    img.setSource(CHROMIUM_FALLBACK_GLOBE_DATA_URL)
                }
                img.isVisible = true
                ref.iconSlotContainer.isVisible = true
                ref.fallbackBadgeLabel.isVisible = false
            }
            else -> {
                ref.activeIconImage?.let { prev ->
                    if (prev !== ref.ownedIconImage && prev.parent === ref.iconSlotContainer) {
                        ref.iconSlotContainer.removeChild(prev)
                    } else {
                        prev.isVisible = false
                    }
                }
                ref.activeIconImage = null
                ref.iconSlotContainer.isVisible = false
                ref.fallbackBadgeLabel.isVisible = false
            }
        }
    }

    private fun rebuildClassicDesktopTabStrip() {
        tabHeaderRefs.clear()
        desktopTabStripContainer.clearChildren()
        desktopTabStripContainer.removeClass(
            "gui-tab-strip",
            "gui-tab-strip--chromium",
            "gui-tab-strip--titlebar"
        )
        desktopTabStripContainer.addClass("genericui-tabbed-desktop-strip", "gap-1")
        desktopTabStripContainer.removeAttribute("data-gui-tab-strip")
        desktopTabStripContainer.removeAttribute("data-gui-chrome-style")
        desktopTabStripContainer.removeAttribute("data-gui-embedded-titlebar")
        desktopTabStripContainer.removeAttribute("data-gui-tab-connect")
        desktopTabStripContainer.removeAttribute("data-gui-theme")
        desktopTabStripContainer.backgroundColor = null
        desktopTabStripContainer.getStyle().apply {
            setAlignItems(AlignItems.CENTER)
            setGap(null)
            setPadding(null)
            setMargin(null)
            setMinHeight(null)
            setFlexGrow(null)
        }
        syncTabNavbarSlot(chromiumPalette = null)

        for (spec in tabSpecs) {
            val isActive = spec.tabId == activeTabId
            val tabChip = FlexBox().apply {
                flexDirection = FlexDirection.ROW
                alignItems = AlignItems.CENTER
                addClass("genericui-tabbed-chip", "gap-1")
                if (isActive) addClass("active")
                if (spec.pinned) addClass("pinned")
            }

            val iconSlotContainer = createTabIconSlotContainer().apply {
                isVisible = false
            }
            val fallbackBadgeLabel = Label("").apply {
                isVisible = false
                maxLines = 1
                addClass("gui-tab-icon")
            }
            tabChip.addChild(iconSlotContainer)

            val selectBtn = Button(
                text = "",
                type = if (isActive) BootstrapType.PRIMARY else BootstrapType.LIGHT,
                size = BootstrapSize.SM
            ).apply {
                addClass("genericui-tabbed-select-btn")
                setOnClick {
                    selectTab(spec.tabId)
                }
            }
            tabChip.addChild(selectBtn)

            var closeBtn: Button? = null
            val canClose = spec.closable && !spec.pinned && (tabSpecs.size > 1 || config.allowCloseLastTab)
            if (canClose) {
                closeBtn = Button(
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

            val ref = TabHeaderViewRef(
                tabId = spec.tabId,
                itemContainer = tabChip,
                iconSlotContainer = iconSlotContainer,
                ownedIconImage = null,
                activeIconImage = null,
                fallbackBadgeLabel = fallbackBadgeLabel,
                titleLabel = null,
                selectButton = selectBtn,
                badgeLabel = null,
                closeButton = closeBtn,
                isActive = isActive,
                isChromium = false
            )
            applySpecIconToHeaderRef(spec, ref)
            selectBtn.text = buildClassicTabCaption(spec, showInlineIconText = !iconSlotContainer.isVisible)
            tabHeaderRefs[spec.tabId] = ref
            desktopTabStripContainer.addChild(tabChip)
        }

        if (config.showNewTabButton) {
            desktopTabStripContainer.addChild(newTabButton)
        }
    }

    private fun rebuildChromiumDesktopTabStrip() {
        val palette = resolveChromiumPalette()
        val embeddedInTitleBar = ownedTitleBarStripAttached
        val connectToNavbar = config.connectActiveTabToNavbar

        tabHeaderRefs.clear()
        desktopTabStripContainer.clearChildren()
        desktopTabStripContainer.removeClass("gap-1")
        desktopTabStripContainer.addClass("genericui-tabbed-desktop-strip", "gui-tab-strip", "gui-tab-strip--chromium")
        if (embeddedInTitleBar) {
            desktopTabStripContainer.addClass("gui-tab-strip--titlebar")
        } else {
            desktopTabStripContainer.removeClass("gui-tab-strip--titlebar")
        }
        desktopTabStripContainer.setAttribute("data-gui-tab-strip", "true")
        desktopTabStripContainer.setAttribute("data-gui-chrome-style", "chromium")
        desktopTabStripContainer.setAttribute("data-gui-embedded-titlebar", embeddedInTitleBar.toString())
        desktopTabStripContainer.setAttribute("data-gui-tab-connect", connectToNavbar.toString())
        desktopTabStripContainer.setAttribute("data-gui-theme", if (palette.isDark) "dark" else "light")
        desktopTabStripContainer.backgroundColor = if (embeddedInTitleBar) RgbaColor.TRANSPARENT else palette.stripSurface
        desktopTabStripContainer.getStyle().apply {
            setDisplay(CssDisplay.FLEX)
            setFlexDirection(FlexDirection.ROW)
            setAlignItems(AlignItems.END)
            setJustifyContent(JustifyContent.START)
            setGap(CssLength.px(2))
            setMinHeightPx(38)
            setPadding(if (embeddedInTitleBar) CssEdgeLengths.px(4, 6, 0, 6) else CssEdgeLengths.px(4, 8, 0, 8))
            setMargin(CssEdgeLengths.ZERO)
            setFlexGrow(if (embeddedInTitleBar) 1f else null)
        }

        syncTabNavbarSlot(palette)

        if (config.showTabSearchButton) {
            tabSearchButton.backgroundColor = palette.controlSubtleSurface
            tabSearchButton.hoverBackgroundColor = palette.controlHoverSurface
            tabSearchButton.activeBackgroundColor = palette.controlActiveSurface
            tabSearchButton.hoverTextColor = palette.activeText
            tabSearchButton.activeTextColor = palette.activeText
            tabSearchButton.getStyle().setColor(palette.inactiveText)
            desktopTabStripContainer.addChild(tabSearchButton)
        }

        var prevWasActive = false
        for (index in tabSpecs.indices) {
            val spec = tabSpecs[index]
            val isActive = spec.tabId == activeTabId

            if (index > 0 && !isActive && !prevWasActive) {
                desktopTabStripContainer.addChild(createChromiumDivider(palette))
            }

            val canClose = spec.closable && !spec.pinned && (tabSpecs.size > 1 || config.allowCloseLastTab)
            val tabItem = createChromiumTabItem(
                spec = spec,
                isActive = isActive,
                canClose = canClose,
                connectToNavbar = connectToNavbar,
                palette = palette
            )
            desktopTabStripContainer.addChild(tabItem)
            prevWasActive = isActive
        }

        if (config.showNewTabButton) {
            chromiumNewTabButton.backgroundColor = RgbaColor.TRANSPARENT
            chromiumNewTabButton.hoverBackgroundColor = palette.controlHoverSurface
            chromiumNewTabButton.activeBackgroundColor = palette.controlActiveSurface
            chromiumNewTabButton.hoverTextColor = palette.activeText
            chromiumNewTabButton.activeTextColor = palette.activeText
            chromiumNewTabButton.getStyle().setColor(palette.inactiveText)
            desktopTabStripContainer.addChild(chromiumNewTabButton)
        }

        desktopTabStripContainer.addChild(chromiumDragSpacer)

        if (embeddedInTitleBar) {
            val sheet = resolveBestAvailableStyleSheet()
            if (sheet != null) {
                val viewportW = currentViewportWidthPx.takeIf { it > 0 } ?: 1280
                desktopTabStripContainer.applyStyleSheet(sheet, viewportW)
            }
            mountedTitleBarRef?.notifyComponentsUpdated()
        }
    }

    private fun createChromiumDivider(palette: ChromiumThemePalette): StyledComponent {
        return StyledComponent().apply {
            addClass("gui-tab-divider")
            setAttribute("data-gui-tab-divider", "true")
            backgroundColor = palette.dividerColor
            pointerEventsEnabled = false
            getStyle().apply {
                setWidthPx(1)
                setMinWidthPx(1)
                setMaxWidthPx(1)
                setHeightPx(16)
                setMargin(CssEdgeLengths.px(0, 2, 7, 2))
                setFlexShrink(0f)
            }
        }
    }

    private fun createChromiumTabItem(
        spec: TabItemSpec,
        isActive: Boolean,
        canClose: Boolean,
        connectToNavbar: Boolean,
        palette: ChromiumThemePalette
    ): StyledContainer {
        val textColor = if (isActive) palette.activeText else palette.inactiveText
        val connectBottom = isActive && connectToNavbar

        val tabItem = StyledContainer().apply {
            addClass("genericui-tabbed-chip", "gui-tab-item", "gui-tab-item--chromium")
            if (isActive) {
                addClass("active", "gui-tab-item--active")
            } else {
                addClass("gui-tab-item--inactive")
            }
            if (spec.pinned) {
                addClass("pinned")
            }
            setAttribute("data-tab-id", spec.tabId)
            setAttribute("data-gui-tab-item", "true")
            setAttribute("data-gui-tab-active", isActive.toString())
            setAttribute("data-gui-tab-connect", connectBottom.toString())
            setAttribute("data-gui-chrome-style", "chromium")

            backgroundColor = if (isActive) palette.activeTabSurface else RgbaColor.TRANSPARENT
            setDisplay(DisplayMode.FLEX)
            getStyle().apply {
                setDisplay(CssDisplay.FLEX)
                setFlexDirection(FlexDirection.ROW)
                setAlignItems(AlignItems.CENTER)
                setJustifyContent(JustifyContent.BETWEEN)
                setGap(CssLength.px(7))
                setMinWidthPx(if (spec.pinned) 56 else 108)
                setMaxWidthPx(if (spec.pinned) 140 else 224)
                setHeightPx(if (connectBottom) 34 else 30)
                setPadding(CssEdgeLengths.px(0, if (canClose) 8 else 12, 0, 10))
                setMargin(CssEdgeLengths.ZERO)
                setMarginBottom(if (connectBottom) CssLength.ZERO else CssLength.px(3))
                setBorderRadius(
                    if (connectBottom) {
                        CssCornerRadii.px(
                            topLeftPx = 10f,
                            topRightPx = 10f,
                            bottomRightPx = 0f,
                            bottomLeftPx = 0f
                        )
                    } else {
                        CssCornerRadii.px(8f)
                    }
                )
                setBorderWidth(CssEdgeLengths.ZERO)
                setBorderStyle(CssBorderStyle.NONE)
                setCursor(CssCursor.POINTER)
            }
            setOnClick {
                selectTab(spec.tabId)
            }
        }

        if (!isActive) {
            tabItem.setOnPointerEnter {
                tabItem.backgroundColor = palette.inactiveTabHoverSurface
            }
            tabItem.setOnPointerLeave {
                tabItem.backgroundColor = RgbaColor.TRANSPARENT
            }
        }

        val iconSlotContainer = createTabIconSlotContainer()
        tabItem.addChild(iconSlotContainer)

        val fallbackBadgeLabel = Label("").apply {
            isVisible = false
            maxLines = 1
            addClass("gui-tab-icon")
            getStyle().apply {
                setColor(textColor)
                setFontSizePx(12f)
                setPointerEvents(CssPointerEvents.NONE)
            }
        }
        tabItem.addChild(fallbackBadgeLabel)

        val titleLabel = Label(spec.title).apply {
            maxLines = 1
            addClass("gui-tab-title", "genericui-tabbed-title")
            getStyle().apply {
                setColor(textColor)
                setFontSizePx(12.5f)
                setFlexGrow(1f)
                setMinWidthPx(0)
                setWhiteSpace(CssWhiteSpace.NOWRAP)
                setOverflow(OverflowBehavior.HIDDEN)
                setTextOverflow(TextOverflow.ELLIPSIS)
                setPointerEvents(CssPointerEvents.NONE)
            }
        }
        tabItem.addChild(titleLabel)

        var badgeLabel: Label? = null
        if (!spec.badgeText.isNullOrBlank()) {
            badgeLabel = Label(spec.badgeText).apply {
                maxLines = 1
                addClass("gui-tab-badge")
                getStyle().apply {
                    setColor(textColor)
                    setFontSizePx(11f)
                    setPointerEvents(CssPointerEvents.NONE)
                }
            }
            tabItem.addChild(badgeLabel)
        }

        var closeBtn: StyledButton? = null
        if (canClose) {
            closeBtn = StyledButton(
                text = "×",
                icon = null,
                type = null,
                size = BootstrapSize.SM
            ).apply {
                addClass("gui-tab-close-btn", "genericui-tabbed-close-btn")
                backgroundColor = RgbaColor.TRANSPARENT
                hoverBackgroundColor = palette.controlHoverSurface
                activeBackgroundColor = palette.controlActiveSurface
                hoverTextColor = palette.activeText
                activeTextColor = palette.activeText
                getStyle().apply {
                    setColor(textColor)
                    setWidthPx(18)
                    setHeightPx(18)
                    setMinWidthPx(18)
                    setMinHeightPx(18)
                    setPadding(CssEdgeLengths.ZERO)
                    setBorderRadius(CssCornerRadii.px(999))
                    setBorderWidth(CssEdgeLengths.ZERO)
                    setBorderStyle(CssBorderStyle.NONE)
                    setFontSizePx(12f)
                    setCursor(CssCursor.POINTER)
                }
                setOnClick {
                    closeTab(spec.tabId)
                }
                if (!isActive) {
                    setOnPointerEnter {
                        tabItem.backgroundColor = palette.inactiveTabHoverSurface
                    }
                }
            }
            tabItem.addChild(closeBtn)
        }

        val ref = TabHeaderViewRef(
            tabId = spec.tabId,
            itemContainer = tabItem,
            iconSlotContainer = iconSlotContainer,
            ownedIconImage = null,
            activeIconImage = null,
            fallbackBadgeLabel = fallbackBadgeLabel,
            titleLabel = titleLabel,
            selectButton = null,
            badgeLabel = badgeLabel,
            closeButton = closeBtn,
            isActive = isActive,
            isChromium = true
        )
        applySpecIconToHeaderRef(spec, ref)
        tabHeaderRefs[spec.tabId] = ref

        return tabItem
    }

    override fun refreshNavigationChrome() {
        val tb = if (chromeInitialized) resolveActiveEmbeddingTitleBar() else null
        if (tb != null && navigationChrome.hasNavigationItems) {
            tb.beginBatchUpdate()
            try {
                super.refreshNavigationChrome()
                syncChromeLayout()
            } finally {
                tb.endBatchUpdate()
            }
        } else {
            super.refreshNavigationChrome()
            if (chromeInitialized) {
                syncChromeLayout()
            }
        }
    }

    override fun onStart() {
        stoppedForTitleBar = false
        super.onStart()
        syncChromeLayout()
    }

    override fun onResume() {
        stoppedForTitleBar = false
        super.onResume()
        syncChromeLayout()
    }

    override fun onStop() {
        stoppedForTitleBar = true
        syncChromeLayout()
        super.onStop()
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        if (chromeInitialized) {
            syncChromeLayout()
        }
    }

    protected override fun onAncestorHierarchyChanged() {
        super.onAncestorHierarchyChanged()
        if (chromeInitialized) {
            syncChromeLayout()
        }
    }

    override fun applyStyleSheetWalk(
        styleSheet: CssStyleSheet,
        viewportWidthPx: Int,
        walkSheet: CssStyleSheet,
        activeFactory: NativeFactory?,
        isDark: Boolean,
        mediaBucket: Int,
        refOldWidthPx: Int,
        mediaMode: Int,
        targetClasses: Set<String>,
        targetIds: Set<String>,
        targetTags: Set<String>,
        targetAncestorClasses: Set<String>,
        targetAncestorIds: Set<String>,
        targetAncestorTags: Set<String>,
        inInvalidatedAncestorSubtree: Boolean
    ) {
        super.applyStyleSheetWalk(
            styleSheet, viewportWidthPx, walkSheet, activeFactory, isDark,
            mediaBucket, refOldWidthPx, mediaMode, targetClasses, targetIds, targetTags,
            targetAncestorClasses, targetAncestorIds, targetAncestorTags, inInvalidatedAncestorSubtree
        )
        if (chromeInitialized && ownedTitleBarStripAttached) {
            desktopTabStripContainer.applyStyleSheetWalk(
                styleSheet, viewportWidthPx, walkSheet, activeFactory, isDark,
                mediaBucket, refOldWidthPx, mediaMode, targetClasses, targetIds, targetTags,
                targetAncestorClasses, targetAncestorIds, targetAncestorTags, inInvalidatedAncestorSubtree
            )
            mountedTitleBarRef?.notifyComponentsUpdated()
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
        disposedForTitleBar = true
        stoppedForTitleBar = true
        clearThemeAndWindowObservers()
        detachOwnedTitleBarStrip()
        ensureStripMountedInHeaderSlot()
        tabHeaderRefs.clear()
        for (fragment in tabFragments.values.toList()) {
            if (fragment !== currentFragment) {
                fragment.performDestroy()
            }
        }
        tabFragments.clear()
        super.onDestroy()
    }

    override fun dispose() {
        disposedForTitleBar = true
        stoppedForTitleBar = true
        clearThemeAndWindowObservers()
        detachOwnedTitleBarStrip()
        tabHeaderRefs.clear()
        super.dispose()
    }

    private companion object {
        private const val CHROMIUM_FALLBACK_GLOBE_SVG: String =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"14\" height=\"14\" viewBox=\"0 0 16 16\" fill=\"none\" stroke=\"#000000\" stroke-width=\"1.35\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" +
                "<circle cx=\"8\" cy=\"8\" r=\"6\"/>" +
                "<path d=\"M 8 2 C 10.6 4.5 10.6 11.5 8 14\"/>" +
                "<path d=\"M 8 2 C 5.4 4.5 5.4 11.5 8 14\"/>" +
                "<line x1=\"2.2\" y1=\"8\" x2=\"13.8\" y2=\"8\"/>" +
                "<line x1=\"3.0\" y1=\"5.2\" x2=\"13.0\" y2=\"5.2\"/>" +
                "<line x1=\"3.0\" y1=\"10.8\" x2=\"13.0\" y2=\"10.8\"/>" +
                "</svg>"

        private val CHROMIUM_FALLBACK_GLOBE_DATA_URL: String =
            DataUrlCodec.encodeUtf8Text(CHROMIUM_FALLBACK_GLOBE_SVG, "image/svg+xml")
    }
}
