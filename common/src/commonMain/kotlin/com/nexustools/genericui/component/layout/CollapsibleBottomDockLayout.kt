package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
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
    var breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Container("genericui:collapsiblebottomdocklayout", "gui-collapsible-bottom-dock") {

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
            syncDockState()
            if (changed) {
                onDockCollapsedChanged?.invoke(value)
            }
        }

    val isDockExpanded: Boolean
        get() = !isDockCollapsed

    var isDockHidden: Boolean = false
        set(value) {
            field = value
            syncDockState()
        }

    var isFullscreen: Boolean = false
        set(value) {
            field = value
            syncDockState()
        }

    var isInPictureInPictureMode: Boolean = false
        set(value) {
            field = value
            syncDockState()
        }

    var hideWhenPrimaryColumnVisible: Boolean = false
        set(value) {
            field = value
            syncDockState()
        }

    var isPrimaryColumnVisible: Boolean = false
        set(value) {
            field = value
            syncDockState()
        }

    var collapsedHeightPx: Int = 60
        set(value) {
            field = value.coerceAtLeast(0)
            syncDockState()
        }

    var expandedHeightPx: Int = 320
        set(value) {
            field = value.coerceAtLeast(0)
            syncDockState()
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
        htmlTag = "genericui:collapsiblebottomdocklayout"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)

        bottomDockContainer.setDisplay(DisplayMode.FLEX)
        bottomDockContainer.setFlexDirection(FlexDirection.COLUMN)
        bottomDockContainer.addChild(collapsedContentSlot)
        bottomDockContainer.addChild(expandedSheetSlot)

        addChild(mainContentHost)
        addChild(bottomDockContainer)

        syncDockState()
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
        syncDockState()
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
            backendTarget = resolveActiveFactory()?.backendTarget
        )
        if (hideWhenPrimaryColumnVisible) {
            isPrimaryColumnVisible = wideColumns
        } else {
            syncDockState()
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

    private fun syncDockState() {
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
