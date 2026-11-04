package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * Persistent collapsible bottom dock / mini-player layout (`CollapsibleBottomDockLayout`) generalizing
 * `Episode-Caster`'s `MainActivity` bottom mini-player bar across media, IDE, and productivity apps.
 *
 * - Hosts a primary [mainContentHost] (`flex-grow: 1`) above a persistent [bottomDockContainer].
 * - Supports collapsed peek height ([collapsedHeightPx], when [isDockCollapsed] is `true`) and expanded
 *   sheet height ([expandedHeightPx], when [isDockCollapsed] is `false`).
 * - Automatically hides the bottom dock when [isDockHidden], [isFullscreen], or [isInPictureInPictureMode]
 *   is `true`, or when [hideWhenPrimaryColumnVisible] and [isPrimaryColumnVisible] are both `true`.
 */
open class CollapsibleBottomDockLayout(
    host: AbstractContainer = Container("genericui:collapsiblebottomdocklayout", "gui-collapsible-bottom-dock"),
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<CollapsibleBottomDockLayoutImpl>(host, breakpointSpec) {

    constructor(
        breakpointSpec: HybridBreakpointSpec
    ) : this(
        host = Container("genericui:collapsiblebottomdocklayout", "gui-collapsible-bottom-dock"),
        breakpointSpec = breakpointSpec
    )

    val mainContentHost: Container = Container("div", "gui-bottom-dock-main-content")
    val bottomDockContainer: Container = Container("div", "gui-bottom-dock-bar")
    val collapsedContentSlot: Container = Container("div", "gui-bottom-dock-collapsed-slot")
    val expandedSheetSlot: Container = Container("div", "gui-bottom-dock-expanded-slot")

    private var hasDockContent: Boolean = false
    private var lastKnownVisibleState: Boolean = false

    var isDockCollapsed: Boolean = false
        set(value) {
            val changed = (field != value)
            field = value
            impl.syncDockState()
            if (changed) {
                onDockCollapsedChanged?.invoke(value)
            }
        }

    val isDockExpanded: Boolean
        get() = !isDockCollapsed

    var isDockHidden: Boolean = false
        set(value) {
            field = value
            impl.syncDockState()
        }

    var isFullscreen: Boolean = false
        set(value) {
            field = value
            impl.syncDockState()
        }

    var isInPictureInPictureMode: Boolean = false
        set(value) {
            field = value
            impl.syncDockState()
        }

    var hideWhenPrimaryColumnVisible: Boolean = false
        set(value) {
            field = value
            impl.syncDockState()
        }

    var isPrimaryColumnVisible: Boolean = false
        set(value) {
            field = value
            impl.syncDockState()
        }

    var collapsedHeightPx: Int = 60
        set(value) {
            field = value.coerceAtLeast(0)
            impl.syncDockState()
        }

    var expandedHeightPx: Int = 320
        set(value) {
            field = value.coerceAtLeast(0)
            impl.syncDockState()
        }

    val isDockCurrentlyVisible: Boolean
        get() = !isDockHidden &&
            !isFullscreen &&
            !isInPictureInPictureMode &&
            !(hideWhenPrimaryColumnVisible && isPrimaryColumnVisible) &&
            hasDockContent

    var onDockCollapsedChanged: ((Boolean) -> Unit)? = null
    var onDockVisibilityChanged: ((Boolean) -> Unit)? = null

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): CollapsibleBottomDockLayoutImpl =
        factory.createCollapsibleBottomDockLayout(this)

    override fun onMount() {
        host.addClass("gui-collapsible-bottom-dock")
        host.setDisplay(DisplayMode.FLEX)
        host.setFlexDirection(FlexDirection.COLUMN)
        host.setStyleProperty("width", "100%")
        host.setStyleProperty("height", "100%")
        host.setStyleProperty("min-height", "0")
        host.setStyleProperty("flex-grow", "1")
        host.setStyleProperty("overflow", "hidden")

        mainContentHost.setDisplay(DisplayMode.FLEX)
        mainContentHost.setFlexDirection(FlexDirection.COLUMN)
        mainContentHost.setStyleProperty("width", "100%")
        mainContentHost.setStyleProperty("height", "100%")
        mainContentHost.setStyleProperty("min-height", "0")
        mainContentHost.setStyleProperty("flex-grow", "1")
        mainContentHost.setStyleProperty("overflow", "hidden")

        bottomDockContainer.setDisplay(DisplayMode.FLEX)
        bottomDockContainer.setFlexDirection(FlexDirection.COLUMN)
        if (!bottomDockContainer.children.contains(collapsedContentSlot)) {
            bottomDockContainer.addChild(collapsedContentSlot)
        }
        if (!bottomDockContainer.children.contains(expandedSheetSlot)) {
            bottomDockContainer.addChild(expandedSheetSlot)
        }

        if (!host.children.contains(mainContentHost)) {
            host.addChild(mainContentHost)
        }
        if (!host.children.contains(bottomDockContainer)) {
            host.addChild(bottomDockContainer)
        }

        impl.syncDockState()
    }

    /**
     * Mounts the primary workspace or navigation content inside [mainContentHost].
     */
    fun setMainContent(component: AbstractComponent): CollapsibleBottomDockLayout = apply {
        mainContentHost.clearChildren()
        mainContentHost.addChild(component)
    }

    /**
     * Returns the component currently mounted inside [mainContentHost], or `null`.
     */
    fun getMainContent(): AbstractComponent? = mainContentHost.children.firstOrNull()

    /**
     * Mounts the persistent bottom dock bar content ([component]) and optional [expandedSheetContent]
     * shown when the dock is expanded ([isDockCollapsed] == `false`).
     */
    fun setDockContent(
        component: AbstractComponent?,
        expandedSheetContent: AbstractComponent? = null
    ): CollapsibleBottomDockLayout = apply {
        collapsedContentSlot.clearChildren()
        expandedSheetSlot.clearChildren()
        if (component != null) {
            collapsedContentSlot.addChild(component)
        }
        if (expandedSheetContent != null) {
            expandedSheetSlot.addChild(expandedSheetContent)
        }
        hasDockContent = (component != null || expandedSheetContent != null)
        impl.syncDockState()
    }

    /**
     * Returns the collapsed/peek bar component inside [collapsedContentSlot], or `null`.
     */
    fun getDockContent(): AbstractComponent? = collapsedContentSlot.children.firstOrNull()

    /**
     * Returns the optional expanded sheet component inside [expandedSheetSlot], or `null`.
     */
    fun getExpandedSheetContent(): AbstractComponent? = expandedSheetSlot.children.firstOrNull()

    /**
     * Expands the bottom dock to [expandedHeightPx] ([isDockCollapsed] = `false`).
     */
    fun expandDock() {
        isDockCollapsed = false
    }

    /**
     * Collapses the bottom dock to its peek bar height [collapsedHeightPx] ([isDockCollapsed] = `true`).
     */
    fun collapseDock() {
        isDockCollapsed = true
    }

    /**
     * Toggles between collapsed peek height and expanded height.
     * Returns `true` if the dock is now expanded (`!isDockCollapsed`), or `false` if now collapsed.
     */
    fun toggleDockExpanded(): Boolean {
        isDockCollapsed = !isDockCollapsed
        return !isDockCollapsed
    }

    /**
     * Evaluates [breakpointSpec] for the given viewport dimensions (in dp) and updates
     * [isPrimaryColumnVisible] when [hideWhenPrimaryColumnVisible] is enabled.
     */
    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) {
        val wideColumns = breakpointSpec.shouldUseWideColumns(
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            smallestScreenWidthDp = smallestScreenWidthDp,
            backendTarget = host.resolveActiveFactory()?.backendTarget
        )
        if (hideWhenPrimaryColumnVisible) {
            isPrimaryColumnVisible = wideColumns
        } else {
            impl.syncDockState()
        }
    }

    /**
     * Applies a [WindowConfiguration] snapshot, automatically syncing [isFullscreen],
     * [isInPictureInPictureMode], and viewport breakpoints.
     */
    fun applyWindowConfiguration(config: WindowConfiguration) {
        isFullscreen = config.isFullscreen
        isInPictureInPictureMode = config.isInPictureInPicture
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
     * Applies the current dock visibility and height state to [bottomDockContainer] and [expandedSheetSlot].
     */
    fun applyDockState() {
        val visible = isDockCurrentlyVisible
        bottomDockContainer.isVisible = visible
        bottomDockContainer.heightPx = if (isDockCollapsed) collapsedHeightPx else expandedHeightPx
        expandedSheetSlot.isVisible = !isDockCollapsed && expandedSheetSlot.children.isNotEmpty()

        if (isDockCollapsed) {
            bottomDockContainer.addClass("gui-bottom-dock-collapsed")
            bottomDockContainer.removeClass("gui-bottom-dock-expanded")
        } else {
            bottomDockContainer.addClass("gui-bottom-dock-expanded")
            bottomDockContainer.removeClass("gui-bottom-dock-collapsed")
        }

        if (visible != lastKnownVisibleState) {
            lastKnownVisibleState = visible
            onDockVisibilityChanged?.invoke(visible)
        }
    }
}
