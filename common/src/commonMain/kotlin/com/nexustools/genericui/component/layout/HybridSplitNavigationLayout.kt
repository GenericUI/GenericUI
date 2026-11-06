package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.tabs.BadgeTab
import com.nexustools.genericui.component.tabs.ButtonTab
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * Responsive split navigation layout generalizing `Episode-Caster`'s `LibraryViewController` across all apps.
 *
 * - **Column / Wide Mode ([inColumnMode] == `true`)**: Renders section navigation as a top [TabContainer]
 *   ([topTabLayout] visible, [bottomNavigationBar] hidden) above the optional [headerSlot] and [contentHost].
 * - **Compact / Single Mode ([inColumnMode] == `false`)**: Hides [topTabLayout] and renders a persistent
 *   bottom [BottomNavigationBar] ([bottomNavigationBar] visible) anchored below [contentHost].
 * - Switching between modes or sections preserves the active section ID and swaps section views inside
 *   [contentHost] without recreating child components.
 */
open class HybridSplitNavigationLayout(
    host: AbstractContainer = Container("genericui:hybridsplitnavigationlayout", "gui-hybrid-split-navigation"),
    inColumnMode: Boolean = false,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<HybridSplitNavigationLayoutImpl>(host, breakpointSpec) {

    constructor(
        inColumnMode: Boolean,
        breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
    ) : this(
        host = Container("genericui:hybridsplitnavigationlayout", "gui-hybrid-split-navigation"),
        inColumnMode = inColumnMode,
        breakpointSpec = breakpointSpec
    )

    private class SectionEntry(
        val id: String,
        var title: String,
        var icon: String?,
        var badge: String?,
        val content: AbstractComponent?,
        val tab: Tab
    )

    private val sections = linkedMapOf<String, SectionEntry>()
    private var suppressNavCallbacks: Boolean = false
    private var inColumnModeState: Boolean = inColumnMode
    private var hasSyncedNavigationChrome: Boolean = false

    val topTabLayout: TabContainer = TabContainer()
    val headerSlot: Container = object : Container("div", "gui-split-nav-header") {
        override fun onAttachNative(impl: com.nexustools.genericui.core.NativeImpl<*>) {
            super.onAttachNative(impl)
            prewarmHeaderChildren()
        }

        override fun applyStyleSheet(
            styleSheet: com.nexustools.genericui.style.css.CssStyleSheet,
            viewportWidthPx: Int
        ) {
            super.applyStyleSheet(styleSheet, viewportWidthPx)
            prewarmHeaderChildren()
        }
    }
    val contentHost: Container = object : Container("div", "gui-split-nav-content") {
        override fun onAttachNative(impl: com.nexustools.genericui.core.NativeImpl<*>) {
            super.onAttachNative(impl)
            prewarmInactiveSections()
        }

        override fun applyStyleSheet(
            styleSheet: com.nexustools.genericui.style.css.CssStyleSheet,
            viewportWidthPx: Int
        ) {
            super.applyStyleSheet(styleSheet, viewportWidthPx)
            for (entry in sections.values) {
                val sectionContent = entry.content ?: continue
                if (!children.contains(sectionContent)) {
                    sectionContent.parent = this
                    sectionContent.applyStyleSheet(styleSheet, viewportWidthPx)
                }
            }
            prewarmInactiveSections()
        }
    }
    val bottomNavigationBar: BottomNavigationBar = BottomNavigationBar()

    var selectedSectionId: String? = null
        private set

    var onSectionSelected: ((String) -> Unit)? = null

    var inColumnMode: Boolean
        get() = inColumnModeState
        set(value) {
            if (inColumnModeState == value && hasSyncedNavigationChrome) return
            impl.syncNavigationChrome(value)
        }

    val sectionIds: List<String>
        get() = sections.keys.toList()

    val sectionCount: Int
        get() = sections.size

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): HybridSplitNavigationLayoutImpl =
        factory.createHybridSplitNavigationLayout(this)

    override fun onMount() {
        host.addClass("gui-hybrid-split-navigation")
        host.setDisplay(DisplayMode.FLEX)
        host.setFlexDirection(FlexDirection.COLUMN)
        host.setStyleProperty("display", "flex")
        host.setStyleProperty("flex-direction", "column")
        host.setStyleProperty("width", "100%")
        host.setStyleProperty("height", "100%")
        host.setStyleProperty("min-height", "0")
        host.setStyleProperty("flex", "1 1 0%")
        host.setStyleProperty("overflow", "hidden")

        topTabLayout.setStyleProperty("flex-shrink", "0")
        topTabLayout.setStyleProperty("width", "100%")

        headerSlot.setStyleProperty("flex-shrink", "0")
        headerSlot.setStyleProperty("width", "100%")

        contentHost.setDisplay(DisplayMode.FLEX)
        contentHost.setFlexDirection(FlexDirection.COLUMN)
        contentHost.setStyleProperty("display", "flex")
        contentHost.setStyleProperty("flex-direction", "column")
        contentHost.setStyleProperty("flex", "1 1 0%")
        contentHost.setStyleProperty("flex-grow", "1")
        contentHost.setStyleProperty("flex-shrink", "1")
        contentHost.setStyleProperty("flex-basis", "0%")
        contentHost.setStyleProperty("min-height", "0")
        contentHost.setStyleProperty("width", "100%")
        contentHost.setStyleProperty("height", "100%")
        contentHost.setStyleProperty("overflow", "hidden")
        contentHost.setStyleProperty("overflow-x", "hidden")
        contentHost.setStyleProperty("overflow-y", "hidden")
        contentHost.addClass("flex-grow-1 overflow-hidden")

        bottomNavigationBar.setStyleProperty("flex-shrink", "0")
        bottomNavigationBar.setStyleProperty("width", "100%")
        bottomNavigationBar.addClass("mt-auto w-100 flex-shrink-0")

        headerSlot.isVisible = false
        val stackHost = host as? VStack
        if (!host.children.contains(topTabLayout)) {
            if (stackHost != null) stackHost.addChild(topTabLayout, StackConstraint.FIXED) else host.addChild(topTabLayout)
        }
        if (!host.children.contains(headerSlot)) {
            if (stackHost != null) stackHost.addChild(headerSlot, StackConstraint.FIXED) else host.addChild(headerSlot)
        }
        if (!host.children.contains(contentHost)) {
            if (stackHost != null) stackHost.addChild(contentHost, StackConstraint.EXPAND) else host.addChild(contentHost)
        }
        if (!host.children.contains(bottomNavigationBar)) {
            if (stackHost != null) stackHost.addChild(bottomNavigationBar, StackConstraint.FIXED) else host.addChild(bottomNavigationBar)
        }

        topTabLayout.setOnTabSelected { tabId ->
            if (!suppressNavCallbacks && tabId != selectedSectionId && sections.containsKey(tabId)) {
                selectSection(tabId)
            }
        }
        bottomNavigationBar.onItemSelected = { itemId ->
            if (!suppressNavCallbacks && itemId != selectedSectionId && sections.containsKey(itemId)) {
                selectSection(itemId)
            }
        }

        impl.syncNavigationChrome(inColumnModeState)
    }

    /**
     * Registers or replaces a navigable section with [id], [title], optional [icon], optional [badge], and [content].
     * Automatically selects the first added section if none is currently selected.
     */
    fun addSection(
        id: String,
        title: String,
        icon: String? = null,
        badge: String? = null,
        content: AbstractComponent
    ): HybridSplitNavigationLayout = apply {
        if (sections.containsKey(id)) {
            removeSection(id)
        }
        val tab: Tab = if (badge != null) {
            BadgeTab(tabId = id, title = title, badgeText = badge, icon = icon)
        } else {
            Tab(tabId = id, title = title, icon = icon)
        }
        val entry = SectionEntry(
            id = id,
            title = title,
            icon = icon,
            badge = badge,
            content = content,
            tab = tab
        )
        sections[id] = entry

        suppressNavCallbacks = true
        try {
            // Use an isolated placeholder for the tab header container so contentHost exclusively owns `content`.
            topTabLayout.addTab(tab, Container("div", "gui-split-nav-tab-slot"))
            syncBottomNavigationItems()
        } finally {
            suppressNavCallbacks = false
        }

        if (selectedSectionId == null) {
            selectSection(id)
        } else {
            syncActiveSelectionToBars()
            if (contentHost.nativeImpl != null) {
                prewarmSectionEntry(entry)
            }
        }
    }

    /**
     * Registers an action-only [ButtonTab] section with [id], [title], optional [icon], and [onClick].
     * Clicking this item in either [topTabLayout] or [bottomNavigationBar] invokes [onClick] without
     * changing [selectedSectionId] or swapping [contentHost].
     */
    fun addButtonSection(
        id: String,
        title: String,
        icon: String? = null,
        onClick: () -> Unit = {}
    ): ButtonTab {
        val buttonTab = ButtonTab(
            title = title,
            id = id,
            icon = icon,
            onClick = onClick
        )
        return addButtonSection(buttonTab)
    }

    /**
     * Registers an existing [ButtonTab] as an action-only section in both [topTabLayout] and [bottomNavigationBar].
     */
    fun addButtonSection(tab: ButtonTab): ButtonTab {
        val sectionId = tab.id?.takeIf { it.isNotEmpty() } ?: tab.tabId
        if (sections.containsKey(sectionId)) {
            removeSection(sectionId)
        }
        val entry = SectionEntry(
            id = sectionId,
            title = tab.title,
            icon = tab.icon,
            badge = null,
            content = null,
            tab = tab
        )
        sections[sectionId] = entry

        suppressNavCallbacks = true
        try {
            topTabLayout.addButtonTab(tab)
            syncBottomNavigationItems()
        } finally {
            suppressNavCallbacks = false
        }
        syncActiveSelectionToBars()
        return tab
    }

    private fun prewarmHeaderChildren() {
        if (headerSlot.nativeImpl == null) return
        val sheet = headerSlot.lastAppliedStyleSheet ?: host.lastAppliedStyleSheet
        val vw = headerSlot.lastStyleSheetViewportWidthPx
        for (child in headerSlot.children) {
            if (sheet != null && child.lastAppliedStyleSheet !== sheet) {
                child.applyStyleSheet(sheet, vw)
            }
            if (!child.isVisible && child.nativeImpl == null) {
                child.isVisible = true
                child.ensureNativeImpl()
                child.isVisible = false
            }
        }
    }

    private fun prewarmSectionEntry(entry: SectionEntry) {
        if (contentHost.nativeImpl == null) return
        val content = entry.content ?: return
        if (content.parent == null) {
            content.parent = contentHost
        }
        val sheet = contentHost.lastAppliedStyleSheet ?: host.lastAppliedStyleSheet
        val vw = contentHost.lastStyleSheetViewportWidthPx
        if (sheet != null && content.lastAppliedStyleSheet !== sheet) {
            content.applyStyleSheet(sheet, vw)
        }
        if (content.nativeImpl == null) {
            if (!content.isVisible) {
                content.isVisible = true
                content.ensureNativeImpl()
                content.isVisible = false
            } else {
                content.ensureNativeImpl()
            }
        }
        refreshLazyListsInSubtree(content)
    }

    private fun prewarmInactiveSections() {
        if (contentHost.nativeImpl == null) return
        for (entry in sections.values) {
            val sectionContent = entry.content ?: continue
            if (!contentHost.children.contains(sectionContent)) {
                prewarmSectionEntry(entry)
            }
        }
    }

    /**
     * Removes the section identified by [id]. If it was the active section, selects the first remaining section.
     */
    fun removeSection(id: String): Boolean {
        val removed = sections.remove(id) ?: return false
        removed.content?.let { sectionContent ->
            if (contentHost.children.contains(sectionContent)) {
                contentHost.removeChild(sectionContent)
            }
        }
        suppressNavCallbacks = true
        try {
            topTabLayout.removeTab(id)
            syncBottomNavigationItems()
        } finally {
            suppressNavCallbacks = false
        }

        if (selectedSectionId == id) {
            val nextId = sections.values.firstOrNull { it.tab.isSelectable && it.content != null }?.id
            if (nextId != null) {
                selectSection(nextId)
            } else {
                selectedSectionId = null
                contentHost.clearChildren()
            }
        } else {
            syncActiveSelectionToBars()
        }
        return true
    }

    /**
     * Selects the section identified by [id], mounting its [AbstractComponent] into [contentHost]
     * and synchronizing both [topTabLayout] and [bottomNavigationBar].
     */
    fun selectSection(id: String): Boolean {
        val entry = sections[id] ?: return false
        if (!entry.tab.isSelectable || entry.content == null) {
            (entry.tab as? ButtonTab)?.performClick()
            syncActiveSelectionToBars()
            return false
        }
        val sectionContent = entry.content
        val changed = (selectedSectionId != id)
        selectedSectionId = id

        if (contentHost.children.size != 1 || contentHost.children.firstOrNull() !== sectionContent) {
            val previousChildren = contentHost.children.toList()
            contentHost.clearChildren()
            for (prev in previousChildren) {
                if (prev !== sectionContent && sections.values.any { it.content === prev }) {
                    prev.parent = contentHost
                }
            }
            contentHost.addChild(sectionContent)
        }
        refreshLazyListsInSubtree(sectionContent)

        syncActiveSelectionToBars()
        if (changed) {
            onSectionSelected?.invoke(id)
        }
        return true
    }

    private fun refreshLazyListsInSubtree(component: AbstractComponent) {
        when (component) {
            is com.nexustools.genericui.component.lazy.LazyList -> {
                val total = component.adapter?.getItemCount() ?: component.totalItemCount
                if (component.loadedItemCount == 0 && total > 0) {
                    component.refreshLazyViewport()
                }
            }
            is com.nexustools.genericui.component.lazy.LazyGrid -> component.refreshLazyViewport()
            is com.nexustools.genericui.component.lazy.TreeView<*> -> component.refreshLazyViewport()
            is AbstractContainer -> {
                for (child in component.children) {
                    refreshLazyListsInSubtree(child)
                }
            }
        }
    }

    /**
     * Returns the [AbstractComponent] registered for section [id], or `null` if not found.
     */
    fun getSectionComponent(id: String): AbstractComponent? = sections[id]?.content

    /**
     * Returns the display title for section [id], or `null` if not found.
     */
    fun getSectionTitle(id: String): String? = sections[id]?.title

    /**
     * Returns the badge text for section [id], or `null` if none is set.
     */
    fun getSectionBadge(id: String): String? = sections[id]?.badge

    /**
     * Updates the badge text for section [id] if it was created as a [BadgeTab].
     */
    fun setSectionBadge(id: String, badge: String?) {
        val entry = sections[id] ?: return
        entry.badge = badge
        (entry.tab as? BadgeTab)?.badgeText = badge
    }

    /**
     * Mounts an optional header component (such as a search bar or filter toolbar) into [headerSlot]
     * above [contentHost], or hides [headerSlot] when [component] is `null`.
     */
    fun setHeaderComponent(component: AbstractComponent?) {
        headerSlot.clearChildren()
        if (component != null) {
            headerSlot.addChild(component)
            headerSlot.isVisible = true
            prewarmHeaderChildren()
        } else {
            headerSlot.isVisible = false
        }
    }

    /**
     * Returns the currently mounted header component in [headerSlot], or `null`.
     */
    fun getHeaderComponent(): AbstractComponent? = headerSlot.children.firstOrNull()

    /**
     * Evaluates [breakpointSpec] for the given viewport dimensions (in dp) and updates [inColumnMode].
     */
    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) {
        inColumnMode = breakpointSpec.shouldUseWideColumns(
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            smallestScreenWidthDp = smallestScreenWidthDp,
            backendTarget = host.resolveActiveFactory()?.backendTarget
        )
    }

    /**
     * Applies a [WindowConfiguration] snapshot to update [inColumnMode] via [breakpointSpec].
     */
    fun applyWindowConfiguration(config: WindowConfiguration) {
        val widthDp = breakpointSpec.toDp(config.widthPx, config.densityScale)
        val heightDp = breakpointSpec.toDp(config.heightPx, config.densityScale)
        updateViewport(
            viewportWidthDp = widthDp,
            viewportHeightDp = heightDp,
            smallestScreenWidthDp = config.smallestScreenWidthDp
        )
    }

    override fun onConfigurationChanged(config: WindowConfiguration) {
        super.onConfigurationChanged(config)
        applyWindowConfiguration(config)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        updateViewport(viewportWidth, viewportHeight)
    }

    /**
     * Applies the evaluated [inColumnMode] navigation chrome to [topTabLayout], [bottomNavigationBar], and [host].
     */
    fun applyNavigationChrome(inColumnMode: Boolean) {
        val changed = !hasSyncedNavigationChrome || inColumnModeState != inColumnMode
        inColumnModeState = inColumnMode
        hasSyncedNavigationChrome = true
        if (changed) {
            if (inColumnMode) {
                topTabLayout.tabPosition = TabPosition.TOP
                topTabLayout.isVisible = true
                bottomNavigationBar.isVisible = false
                host.addClass("gui-split-nav-column-mode")
                host.removeClass("gui-split-nav-compact-mode")
            } else {
                topTabLayout.tabPosition = TabPosition.BOTTOM
                topTabLayout.isVisible = false
                bottomNavigationBar.isVisible = true
                host.addClass("gui-split-nav-compact-mode")
                host.removeClass("gui-split-nav-column-mode")
            }
            syncActiveSelectionToBars()
        }
    }

    private fun syncBottomNavigationItems() {
        val actionMap = linkedMapOf<String, () -> Unit>()
        for (entry in sections.values) {
            val btnTab = entry.tab as? ButtonTab
            if (btnTab != null || !entry.tab.isSelectable) {
                actionMap[entry.id] = {
                    (entry.tab as? ButtonTab)?.performClick()
                }
            }
        }
        bottomNavigationBar.setActionCallbacks(actionMap)
        bottomNavigationBar.setItems(
            sections.values.map { entry ->
                Triple(entry.id, entry.title, entry.icon)
            }
        )
    }

    private fun syncActiveSelectionToBars() {
        val activeId = selectedSectionId ?: return
        suppressNavCallbacks = true
        try {
            topTabLayout.setActiveTab(activeId)
            if (bottomNavigationBar.selectedItemId != activeId) {
                bottomNavigationBar.selectedItemId = activeId
            }
        } finally {
            suppressNavCallbacks = false
        }
    }
}
