package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.navigation.SideNav
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * Material 3 / Bootstrap adaptive navigation shell layout (`AdaptiveNavigationScaffoldLayout`) that automatically morphs
 * its navigation chrome based on screen/viewport size while preserving destination state:
 *
 * - **Compact (`< 600dp` — Small-Screen Mobile & Narrow Web)**: Bottom [BottomNavigationBar]
 *   ([activeNavigationKind] == `"BOTTOM_NAV"`).
 * - **Medium (`600..839dp` — Tablet Portrait / Medium Web)**: Leading vertical [navigationRail]
 *   ([activeNavigationKind] == `"NAVIGATION_RAIL"`).
 * - **Expanded (`>= 840dp` — Tablet Landscape / Wide Web / Desktop)**: Persistent [sidebar]
 *   ([activeNavigationKind] == `"SIDEBAR"`).
 */
open class AdaptiveNavigationScaffoldLayout(
    host: AbstractContainer = Container("genericui:adaptivenavigationscaffold", "gui-adaptive-navigation-scaffold"),
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<AdaptiveNavigationScaffoldLayoutImpl>(host, breakpointSpec) {

    constructor(
        breakpointSpec: HybridBreakpointSpec
    ) : this(
        host = Container("genericui:adaptivenavigationscaffold", "gui-adaptive-navigation-scaffold"),
        breakpointSpec = breakpointSpec
    )

    private class DestinationEntry(
        val id: String,
        val label: String,
        val icon: String?,
        val badge: String?,
        val content: AbstractComponent,
        val sidebarButton: Button,
        val railButton: Button
    )

    private val destinations = linkedMapOf<String, DestinationEntry>()
    private var suppressNavCallbacks: Boolean = false
    private var lastViewportWidthDp: Int = 1280
    private var lastViewportHeightDp: Int = 800
    private var lastSmallestScreenWidthDp: Int = 800

    val topNavbar: Navbar = Navbar()
    val sidebar: SideNav = SideNav()
    val navigationRail: SideNav = SideNav()
    val bottomNavigationBar: BottomNavigationBar = BottomNavigationBar()
    val bodyRow: ColumnContainer = ColumnContainer()
    val contentHost: Container = Container("div", "gui-adaptive-scaffold-content")

    var activeNavigationKind: String = "SIDEBAR"
        private set

    var selectedDestinationId: String? = null
        private set

    var onDestinationSelected: ((String) -> Unit)? = null

    val destinationIds: List<String>
        get() = destinations.keys.toList()

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): AdaptiveNavigationScaffoldLayoutImpl =
        factory.createAdaptiveNavigationScaffoldLayout(this)

    override fun onMount() {
        host.addClass("gui-adaptive-navigation-scaffold")
        host.setDisplay(DisplayMode.FLEX)
        host.setFlexDirection(FlexDirection.COLUMN)

        sidebar.addClass("gui-adaptive-sidebar")
        navigationRail.addClass("gui-adaptive-navigation-rail")
        bottomNavigationBar.addClass("gui-adaptive-bottom-nav")

        if (!bodyRow.children.contains(sidebar)) {
            bodyRow.addChild(sidebar)
        }
        if (!bodyRow.children.contains(navigationRail)) {
            bodyRow.addChild(navigationRail)
        }
        if (!bodyRow.children.contains(contentHost)) {
            bodyRow.addChild(contentHost)
        }

        if (!host.children.contains(topNavbar)) {
            host.addChild(topNavbar)
        }
        if (!host.children.contains(bodyRow)) {
            host.addChild(bodyRow)
        }
        if (!host.children.contains(bottomNavigationBar)) {
            host.addChild(bottomNavigationBar)
        }

        bottomNavigationBar.onItemSelected = { id ->
            if (!suppressNavCallbacks && id != selectedDestinationId && destinations.containsKey(id)) {
                selectDestination(id)
            }
        }

        impl.syncScaffold(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
    }

    /**
     * Registers a top-level navigation destination with [id], [label], optional [icon], optional [badge],
     * and [content]. Automatically selects the first registered destination.
     */
    fun addDestination(
        id: String,
        label: String,
        icon: String? = null,
        badge: String? = null,
        content: AbstractComponent
    ): AdaptiveNavigationScaffoldLayout = apply {
        if (destinations.containsKey(id)) {
            removeDestination(id)
        }

        val sidebarLabel = if (!badge.isNullOrBlank()) "$label ($badge)" else label
        val sidebarBtn = Button(sidebarLabel).apply {
            addClass("gui-sidebar-item")
            onClick = { selectDestination(id) }
        }
        val railText = icon ?: label.take(2).uppercase()
        val railBtn = Button(railText).apply {
            addClass("gui-rail-item")
            onClick = { selectDestination(id) }
        }

        val entry = DestinationEntry(
            id = id,
            label = label,
            icon = icon,
            badge = badge,
            content = content,
            sidebarButton = sidebarBtn,
            railButton = railBtn
        )
        destinations[id] = entry

        sidebar.addChild(sidebarBtn)
        navigationRail.addChild(railBtn)
        syncBottomNavigationItems()

        if (selectedDestinationId == null) {
            selectDestination(id)
        } else {
            syncActiveSelectionMarkers()
        }
    }

    /**
     * Removes a destination by [id].
     */
    fun removeDestination(id: String): Boolean {
        val removed = destinations.remove(id) ?: return false
        sidebar.removeChild(removed.sidebarButton)
        navigationRail.removeChild(removed.railButton)
        if (contentHost.children.contains(removed.content)) {
            contentHost.removeChild(removed.content)
        }
        syncBottomNavigationItems()

        if (selectedDestinationId == id) {
            val nextId = destinations.keys.firstOrNull()
            if (nextId != null) {
                selectDestination(nextId)
            } else {
                selectedDestinationId = null
                contentHost.clearChildren()
            }
        } else {
            syncActiveSelectionMarkers()
        }
        return true
    }

    /**
     * Selects the destination with [id], mounting its content in [contentHost] without destroying state.
     */
    fun selectDestination(id: String): Boolean {
        val entry = destinations[id] ?: return false
        val changed = (selectedDestinationId != id)
        selectedDestinationId = id

        if (contentHost.children.size != 1 || contentHost.children.firstOrNull() !== entry.content) {
            contentHost.clearChildren()
            contentHost.addChild(entry.content)
        }

        syncActiveSelectionMarkers()
        if (changed) {
            onDestinationSelected?.invoke(id)
        }
        return true
    }

    /**
     * Returns the [AbstractComponent] registered for destination [id], or `null`.
     */
    fun getDestinationComponent(id: String): AbstractComponent? = destinations[id]?.content

    /**
     * Evaluates [breakpointSpec] for the given viewport dimensions (in dp) and morphs between
     * `"BOTTOM_NAV"`, `"NAVIGATION_RAIL"`, and `"SIDEBAR"`.
     */
    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) {
        lastViewportWidthDp = viewportWidthDp
        lastViewportHeightDp = viewportHeightDp
        lastSmallestScreenWidthDp = smallestScreenWidthDp
        impl.syncScaffold(viewportWidthDp.toFloat(), viewportHeightDp.toFloat())
    }

    /**
     * Applies a [WindowConfiguration] snapshot to morph navigation chrome via [breakpointSpec].
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
     * Evaluates and applies the navigation chrome kind (`"BOTTOM_NAV"`, `"NAVIGATION_RAIL"`, or `"SIDEBAR"`).
     */
    fun applyScaffoldLayout(
        viewportWidthDp: Int = lastViewportWidthDp,
        viewportHeightDp: Int = lastViewportHeightDp
    ) {
        lastViewportWidthDp = viewportWidthDp
        lastViewportHeightDp = viewportHeightDp

        val smallMobilePhone = impl.supportsDynamicViewportSwitching &&
            lastSmallestScreenWidthDp < breakpointSpec.tabletSmallestWidthDp &&
            viewportHeightDp < breakpointSpec.tabletSmallestWidthDp
        val kind = when {
            impl.supportsDynamicViewportSwitching &&
                (viewportWidthDp < breakpointSpec.mediumBreakpointDp || smallMobilePhone) -> "BOTTOM_NAV"
            viewportWidthDp < breakpointSpec.expandedNavigationBreakpointDp -> "NAVIGATION_RAIL"
            else -> "SIDEBAR"
        }
        applyNavigationKind(kind)
    }

    fun applyNavigationKind(kind: String) {
        activeNavigationKind = kind
        when (kind) {
            "BOTTOM_NAV" -> {
                sidebar.isVisible = false
                navigationRail.isVisible = false
                bottomNavigationBar.isVisible = true
            }
            "NAVIGATION_RAIL" -> {
                sidebar.isVisible = false
                navigationRail.isVisible = true
                bottomNavigationBar.isVisible = false
            }
            else -> {
                sidebar.isVisible = true
                navigationRail.isVisible = false
                bottomNavigationBar.isVisible = false
            }
        }
    }

    private fun syncBottomNavigationItems() {
        suppressNavCallbacks = true
        try {
            bottomNavigationBar.setItems(
                destinations.values.map { entry ->
                    Triple(entry.id, entry.label, entry.icon)
                }
            )
        } finally {
            suppressNavCallbacks = false
        }
    }

    private fun syncActiveSelectionMarkers() {
        val activeId = selectedDestinationId
        for ((id, entry) in destinations) {
            if (id == activeId) {
                entry.sidebarButton.addClass("active")
                entry.railButton.addClass("active")
            } else {
                entry.sidebarButton.removeClass("active")
                entry.railButton.removeClass("active")
            }
        }
        if (activeId != null) {
            suppressNavCallbacks = true
            try {
                if (bottomNavigationBar.selectedItemId != activeId) {
                    bottomNavigationBar.selectedItemId = activeId
                }
            } finally {
                suppressNavCallbacks = false
            }
        }
    }
}
