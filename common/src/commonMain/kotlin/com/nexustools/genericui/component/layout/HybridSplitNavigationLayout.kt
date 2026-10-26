package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.tabs.BadgeTab
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
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
    inColumnMode: Boolean = false,
    var breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Container("genericui:hybridsplitnavigationlayout", "gui-hybrid-split-navigation") {

    private class SectionEntry(
        val id: String,
        var title: String,
        var icon: String?,
        var badge: String?,
        val content: AbstractComponent,
        val tab: Tab
    )

    private val sections = linkedMapOf<String, SectionEntry>()
    private var suppressNavCallbacks: Boolean = false

    val topTabLayout: TabContainer = TabContainer()
    val headerSlot: Container = Container("div", "gui-split-nav-header")
    val contentHost: Container = Container("div", "gui-split-nav-content")
    val bottomNavigationBar: BottomNavigationBar = BottomNavigationBar()

    var selectedSectionId: String? = null
        private set

    var onSectionSelected: ((String) -> Unit)? = null

    var inColumnMode: Boolean = inColumnMode
        set(value) {
            field = value
            syncNavigationModeVisibility()
        }

    val sectionIds: List<String>
        get() = sections.keys.toList()

    val sectionCount: Int
        get() = sections.size

    init {
        htmlTag = "genericui:hybridsplitnavigationlayout"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)

        headerSlot.isVisible = false
        addChild(topTabLayout)
        addChild(headerSlot)
        addChild(contentHost)
        addChild(bottomNavigationBar)

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

        syncNavigationModeVisibility()
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
        }
    }

    /**
     * Removes the section identified by [id]. If it was the active section, selects the first remaining section.
     */
    fun removeSection(id: String): Boolean {
        val removed = sections.remove(id) ?: return false
        if (contentHost.children.contains(removed.content)) {
            contentHost.removeChild(removed.content)
        }
        suppressNavCallbacks = true
        try {
            topTabLayout.removeTab(id)
            syncBottomNavigationItems()
        } finally {
            suppressNavCallbacks = false
        }

        if (selectedSectionId == id) {
            val nextId = sections.keys.firstOrNull()
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
        val changed = (selectedSectionId != id)
        selectedSectionId = id

        if (contentHost.children.size != 1 || contentHost.children.firstOrNull() !== entry.content) {
            contentHost.clearChildren()
            contentHost.addChild(entry.content)
        }

        syncActiveSelectionToBars()
        if (changed) {
            onSectionSelected?.invoke(id)
        }
        return true
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
            backendTarget = resolveActiveFactory()?.backendTarget
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

    private fun syncNavigationModeVisibility() {
        if (inColumnMode) {
            topTabLayout.isVisible = true
            bottomNavigationBar.isVisible = false
            addClass("gui-split-nav-column-mode")
            removeClass("gui-split-nav-compact-mode")
        } else {
            topTabLayout.isVisible = false
            bottomNavigationBar.isVisible = true
            addClass("gui-split-nav-compact-mode")
            removeClass("gui-split-nav-column-mode")
        }
        syncActiveSelectionToBars()
    }

    private fun syncBottomNavigationItems() {
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
