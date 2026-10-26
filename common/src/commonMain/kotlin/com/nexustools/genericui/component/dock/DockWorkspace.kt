package com.nexustools.genericui.component.dock

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.jvm.JvmName
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-featured docking workspace supporting detachable [DockPanel]s, central document tabs,
 * optional full-bleed [backgroundComponent] with overlaid dock panels (`overlayMode = true`),
 * dynamic switching between GIMP's Single-Window Mode and Multi-Window Mode, and responsive
 * off-canvas slide-out edge drawers on small-screen mobile and narrow Web/Desktop viewports.
 */
open class DockWorkspace(
    windowMode: StudioWindowMode = StudioWindowMode.SINGLE_WINDOW,
    overlayMode: Boolean = false
) : AbstractContainer() {
    var windowMode: StudioWindowMode = windowMode
        set(value) {
            if (field != value) {
                field = value
                typedNative<DockWorkspaceNativeImpl<*>>()?.setWindowModeNative(value.name)
                applyWindowModeTransition(value)
                onWindowModeChanged?.invoke(value)
            }
        }

    var mode: StudioWindowMode
        get() = windowMode
        set(value) {
            windowMode = value
        }

    var overlayMode: Boolean = overlayMode
        set(value) {
            if (field != value) {
                field = value
                syncOverlayStyling()
            }
        }

    @set:JvmName("setIsOverlayMode")
    var isOverlayMode: Boolean
        get() = overlayMode
        set(value) {
            overlayMode = value
        }

    private var isUpdatingViewportMetrics: Boolean = false
    private var densityScale: Float = 1.0f
    private var viewportWidthPx: Int = 1280
    private var viewportHeightPx: Int = 800

    var mobileDrawerPolicy: DockMobileDrawerPolicy = DockMobileDrawerPolicy.AUTO
        set(value) {
            if (field != value) {
                field = value
                syncCompactDrawerLayout()
            }
        }

    var compactDrawerBreakpointDp: Int = 600
        set(value) {
            if (field != value) {
                field = value
                syncCompactDrawerLayout()
            }
        }

    var breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
        set(value) {
            if (field != value) {
                field = value
                syncCompactDrawerLayout()
            }
        }

    var viewportWidthDp: Int = 1280
        set(value) {
            field = value
            if (!isUpdatingViewportMetrics) {
                viewportWidthPx = (value * densityScale).roundToInt().coerceAtLeast(0)
                syncCompactDrawerLayout()
            }
        }

    var viewportHeightDp: Int = 800
        set(value) {
            field = value
            if (!isUpdatingViewportMetrics) {
                viewportHeightPx = (value * densityScale).roundToInt().coerceAtLeast(0)
                syncCompactDrawerLayout()
            }
        }

    var smallestScreenWidthDp: Int = 800
        set(value) {
            field = value
            if (!isUpdatingViewportMetrics) {
                syncCompactDrawerLayout()
            }
        }

    val isCompactDrawerMode: Boolean
        get() = when (mobileDrawerPolicy) {
            DockMobileDrawerPolicy.DISABLED -> false
            DockMobileDrawerPolicy.ALWAYS_ENABLED -> true
            DockMobileDrawerPolicy.AUTO -> breakpointSpec
                .copy(compactDrawerBreakpointDp = compactDrawerBreakpointDp)
                .shouldUseCompactDockDrawers(
                    viewportWidthDp = viewportWidthDp,
                    viewportHeightDp = viewportHeightDp,
                    smallestScreenWidthDp = smallestScreenWidthDp,
                    backendTarget = resolveActiveFactory()?.backendTarget
                )
        }

    var activeMobileDrawerPosition: DockPosition? = null
        private set

    var activeMobileDrawerOpenFraction: Float = 0f
        private set

    var edgeSwipeDetectionZonePx: Int = 28

    var drawerOpenThresholdFraction: Float = 0.3f

    var showMobileEdgeHandles: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                syncCompactDrawerLayout()
            }
        }

    var onMobileDrawerStateChanged: ((DockPosition?, Float) -> Unit)? = null

    private var activeSwipePosition: DockPosition? = null
    private var isSwipingClose: Boolean = false
    private var swipeInitialOpenFraction: Float = 0f
    private var swipeStartX: Float = 0f
    private var swipeStartY: Float = 0f
    private var swipeLastX: Float = 0f
    private var swipeLastY: Float = 0f
    private var swipeMovedDistancePx: Float = 0f
    private var pointerDownInCompactMode: Boolean = false
    private var pointerStartX: Float = 0f
    private var pointerStartY: Float = 0f

    val backgroundContainer: Container = Container("div", "dock-workspace-background").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "0px"
        topOffset = "0px"
        setStyleProperty("width", "100%")
        setStyleProperty("height", "100%")
        zIndex = 0
        isVisible = false
    }

    var backgroundComponent: AbstractComponent? = null
        private set

    var leftArea: DockArea = DockArea(DockPosition.LEFT)
        private set
    var rightArea: DockArea = DockArea(DockPosition.RIGHT)
        private set
    var topArea: DockArea = DockArea(DockPosition.TOP)
        private set
    var bottomArea: DockArea = DockArea(DockPosition.BOTTOM)
        private set
    var centerArea: DockArea = DockArea(DockPosition.CENTER)
        private set

    val mobileDrawerScrim: Container = Container("div", "gui-dock-mobile-scrim").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "0px"
        topOffset = "0px"
        setStyleProperty("width", "100%")
        setStyleProperty("height", "100%")
        zIndex = 25
        isVisible = false
    }

    private val _leftEdgeHandle: Container = Container("div", "gui-dock-edge-handle gui-dock-edge-handle-left").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "0px"
        topOffset = "50%"
        zIndex = 20
        isVisible = false
    }
    val leftEdgeHandle: Container
        get() {
            syncEdgeHandlesVisibility()
            return _leftEdgeHandle
        }

    private val _rightEdgeHandle: Container = Container("div", "gui-dock-edge-handle gui-dock-edge-handle-right").apply {
        positionMode = PositionMode.ABSOLUTE
        setStyleProperty("right", "0px")
        topOffset = "50%"
        zIndex = 20
        isVisible = false
    }
    val rightEdgeHandle: Container
        get() {
            syncEdgeHandlesVisibility()
            return _rightEdgeHandle
        }

    private val _topEdgeHandle: Container = Container("div", "gui-dock-edge-handle gui-dock-edge-handle-top").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "50%"
        topOffset = "0px"
        zIndex = 20
        isVisible = false
    }
    val topEdgeHandle: Container
        get() {
            syncEdgeHandlesVisibility()
            return _topEdgeHandle
        }

    private val _bottomEdgeHandle: Container = Container("div", "gui-dock-edge-handle gui-dock-edge-handle-bottom").apply {
        positionMode = PositionMode.ABSOLUTE
        leftOffset = "50%"
        setStyleProperty("bottom", "0px")
        zIndex = 20
        isVisible = false
    }
    val bottomEdgeHandle: Container
        get() {
            syncEdgeHandlesVisibility()
            return _bottomEdgeHandle
        }

    val centralDocumentTabs: TabContainer = TabContainer(tabBarStyle = TabBarStyle.DOCUMENT_STRIP)
    var toolPalette: ToolPalette? = null
        private set

    private val _panels = mutableMapOf<String, DockPanel>()
    val panels: Map<String, DockPanel> get() = _panels

    private val _floatingWindows = mutableMapOf<String, PanelWindow>()
    val floatingWindows: Map<String, PanelWindow> get() = _floatingWindows
    val panelWindows: Map<String, PanelWindow> get() = _floatingWindows

    private val _documents = mutableListOf<StudioDocument>()
    val documents: List<StudioDocument> get() = _documents

    var activeDocumentId: String? = null
        private set

    var onWindowModeChanged: ((StudioWindowMode) -> Unit)? = null
    var onActiveDocumentChanged: ((StudioDocument?) -> Unit)? = null

    init {
        htmlTag = "genericui:dockworkspace"
        positionMode = PositionMode.RELATIVE
        addChild(backgroundContainer)
        addChild(topArea)
        addChild(leftArea)
        addChild(centerArea)
        addChild(centralDocumentTabs)
        addChild(rightArea)
        addChild(bottomArea)
        addChild(mobileDrawerScrim)
        addChild(_leftEdgeHandle)
        addChild(_rightEdgeHandle)
        addChild(_topEdgeHandle)
        addChild(_bottomEdgeHandle)
        wireMobileDrawerInteractions()
        syncOverlayStyling()
    }

    private fun syncEdgeHandlesVisibility() {
        val handlesVisible = isCompactDrawerMode && showMobileEdgeHandles && activeMobileDrawerPosition == null
        _leftEdgeHandle.isVisible = handlesVisible && hasDockedPanelsAt(DockPosition.LEFT)
        _rightEdgeHandle.isVisible = handlesVisible && hasDockedPanelsAt(DockPosition.RIGHT)
        _topEdgeHandle.isVisible = handlesVisible && hasDockedPanelsAt(DockPosition.TOP)
        _bottomEdgeHandle.isVisible = handlesVisible && hasDockedPanelsAt(DockPosition.BOTTOM)
    }

    private fun wireMobileDrawerInteractions() {
        mobileDrawerScrim.onClick = {
            closeMobileDockDrawer()
        }
        mobileDrawerScrim.onPointerDown = { event ->
            handleWorkspacePointerDown(event)
        }
        mobileDrawerScrim.onPointerMove = { event ->
            handleWorkspacePointerMove(event)
        }
        mobileDrawerScrim.onPointerUp = { event ->
            handleWorkspacePointerUp(event, isMainAreaTapCandidate = true)
        }

        wireMainAreaInteractions(centerArea)
        wireMainAreaInteractions(backgroundContainer)

        wireEdgeHandleInteractions(_leftEdgeHandle, DockPosition.LEFT)
        wireEdgeHandleInteractions(_rightEdgeHandle, DockPosition.RIGHT)
        wireEdgeHandleInteractions(_topEdgeHandle, DockPosition.TOP)
        wireEdgeHandleInteractions(_bottomEdgeHandle, DockPosition.BOTTOM)

        onPointerDown = { event ->
            handleWorkspacePointerDown(event)
        }
        onPointerMove = { event ->
            handleWorkspacePointerMove(event)
        }
        onPointerUp = { event ->
            handleWorkspacePointerUp(event, isMainAreaTapCandidate = false)
        }
    }

    private fun wireMainAreaInteractions(component: AbstractComponent) {
        component.onClick = {
            tapMainWorkspaceInCompactMode()
        }
        component.onPointerDown = { event ->
            handleWorkspacePointerDown(event)
        }
        component.onPointerMove = { event ->
            handleWorkspacePointerMove(event)
        }
        component.onPointerUp = { event ->
            handleWorkspacePointerUp(event, isMainAreaTapCandidate = true)
        }
    }

    private fun wireEdgeHandleInteractions(handle: Container, position: DockPosition) {
        handle.onClick = {
            openMobileDockDrawer(position)
        }
        handle.onPointerDown = { event ->
            if (isCompactDrawerMode && hasDockedPanelsAt(position)) {
                pointerDownInCompactMode = true
                pointerStartX = event.x
                pointerStartY = event.y
                swipeStartX = event.x
                swipeStartY = event.y
                swipeLastX = event.x
                swipeLastY = event.y
                swipeMovedDistancePx = 0f
                activeSwipePosition = position
                isSwipingClose = false
                swipeInitialOpenFraction = 0f
                activeMobileDrawerPosition = position
                activeMobileDrawerOpenFraction = 0f
                syncCompactDrawerLayout()
            }
        }
        handle.onPointerMove = { event ->
            if (isCompactDrawerMode && activeSwipePosition == position) {
                updateEdgeSwipe(event.x, event.y)
            }
        }
        handle.onPointerUp = { event ->
            if (isCompactDrawerMode) {
                pointerDownInCompactMode = false
                val dist = hypot(event.x - pointerStartX, event.y - pointerStartY)
                if (dist <= 8f) {
                    activeSwipePosition = null
                    isSwipingClose = false
                    openMobileDockDrawer(position)
                } else if (activeSwipePosition == position) {
                    endEdgeSwipe()
                }
            }
        }
    }

    private fun handleWorkspacePointerDown(event: PointerEvent) {
        if (!isCompactDrawerMode) return
        pointerDownInCompactMode = true
        pointerStartX = event.x
        pointerStartY = event.y
        swipeMovedDistancePx = 0f
        beginEdgeSwipe(event.x, event.y)
    }

    private fun handleWorkspacePointerMove(event: PointerEvent) {
        if (!isCompactDrawerMode || !pointerDownInCompactMode) return
        if (activeSwipePosition != null) {
            updateEdgeSwipe(event.x, event.y)
        }
    }

    private fun handleWorkspacePointerUp(event: PointerEvent, isMainAreaTapCandidate: Boolean) {
        if (!isCompactDrawerMode) {
            pointerDownInCompactMode = false
            return
        }
        pointerDownInCompactMode = false
        val moveDist = hypot(event.x - pointerStartX, event.y - pointerStartY)
        if (activeMobileDrawerPosition != null && isSwipingClose && moveDist <= 8f) {
            activeSwipePosition = null
            isSwipingClose = false
            tapMainWorkspaceInCompactMode()
        } else if (activeSwipePosition != null) {
            endEdgeSwipe()
        } else if (isMainAreaTapCandidate && activeMobileDrawerPosition != null && moveDist <= 8f) {
            tapMainWorkspaceInCompactMode()
        }
    }

    /**
     * Updates the workspace viewport dimensions in pixels and density scale, recalculating
     * [viewportWidthDp], [viewportHeightDp], and [smallestScreenWidthDp], and syncing compact drawer mode.
     */
    fun updateViewportMetrics(
        widthPx: Int,
        heightPx: Int,
        densityScale: Float = 1.0f,
        smallestWidthDp: Int? = null
    ) {
        val safeDensity = if (densityScale > 0f) densityScale else 1.0f
        isUpdatingViewportMetrics = true
        try {
            this.densityScale = safeDensity
            this.viewportWidthPx = widthPx.coerceAtLeast(0)
            this.viewportHeightPx = heightPx.coerceAtLeast(0)
            this.viewportWidthDp = breakpointSpec.toDp(widthPx, safeDensity)
            this.viewportHeightDp = breakpointSpec.toDp(heightPx, safeDensity)
            this.smallestScreenWidthDp = smallestWidthDp ?: min(this.viewportWidthDp, this.viewportHeightDp)
        } finally {
            isUpdatingViewportMetrics = false
        }
        syncCompactDrawerLayout()
    }

    /**
     * Applies a [WindowConfiguration] snapshot to update viewport metrics and compact drawer layout.
     */
    fun applyWindowConfiguration(config: WindowConfiguration) {
        updateViewportMetrics(
            widthPx = config.widthPx,
            heightPx = config.heightPx,
            densityScale = config.densityScale,
            smallestWidthDp = config.smallestScreenWidthDp
        )
    }

    /**
     * Returns `true` if there is at least one non-floating docked panel or tool palette at [position].
     */
    fun hasDockedPanelsAt(position: DockPosition): Boolean {
        if (position == DockPosition.FLOATING) {
            return _panels.values.any { it.isFloating }
        }
        val area = areaForPosition(position)
        val hasRegisteredDockedPanel = _panels.values.any { !it.isFloating && it.preferredPosition == position }
        val hasGroupPanel = area.groups.any { group ->
            group.panels.any { !it.isFloating } || group.children.any { it !is DockPanel }
        }
        val hasDirectChild = area.children.any { child ->
            when (child) {
                is DockGroup -> child.panels.any { !it.isFloating } || child.children.any { it !is DockPanel }
                is DockPanel -> !child.isFloating
                else -> true
            }
        }
        return hasRegisteredDockedPanel || hasGroupPanel || hasDirectChild
    }

    /**
     * Opens the off-canvas mobile dock drawer at [position] when [isCompactDrawerMode] is active.
     */
    fun openMobileDockDrawer(position: DockPosition): Boolean {
        if (!isCompactDrawerMode || position == DockPosition.CENTER || position == DockPosition.FLOATING) {
            return false
        }
        if (!hasDockedPanelsAt(position)) {
            return false
        }
        activeSwipePosition = null
        isSwipingClose = false
        activeMobileDrawerPosition = position
        activeMobileDrawerOpenFraction = 1f
        syncCompactDrawerLayout()
        onMobileDrawerStateChanged?.invoke(activeMobileDrawerPosition, activeMobileDrawerOpenFraction)
        return true
    }

    /**
     * Closes any currently open mobile dock drawer.
     */
    fun closeMobileDockDrawer(): Boolean {
        if (activeMobileDrawerPosition == null && activeMobileDrawerOpenFraction <= 0f) {
            activeSwipePosition = null
            isSwipingClose = false
            return false
        }
        activeSwipePosition = null
        isSwipingClose = false
        activeMobileDrawerPosition = null
        activeMobileDrawerOpenFraction = 0f
        syncCompactDrawerLayout()
        onMobileDrawerStateChanged?.invoke(null, 0f)
        return true
    }

    /**
     * Toggles the off-canvas mobile dock drawer at [position].
     */
    fun toggleMobileDockDrawer(position: DockPosition): Boolean {
        if (!isCompactDrawerMode || position == DockPosition.CENTER || position == DockPosition.FLOATING) {
            return false
        }
        return if (activeMobileDrawerPosition == position && activeMobileDrawerOpenFraction > 0f) {
            closeMobileDockDrawer()
        } else {
            openMobileDockDrawer(position)
        }
    }

    /**
     * Begins an edge-swipe gesture from `(startX, startY)` when in [isCompactDrawerMode].
     * - When no drawer is open, detects if `(startX, startY)` starts within [edgeSwipeDetectionZonePx]
     *   of a peripheral edge (`LEFT`, `RIGHT`, `TOP`, `BOTTOM`) that [hasDockedPanelsAt] that position.
     * - When a drawer is already open, begins tracking a swipe-to-close gesture on the active drawer.
     */
    fun beginEdgeSwipe(startX: Float, startY: Float): DockPosition? {
        if (!isCompactDrawerMode) return null
        swipeStartX = startX
        swipeStartY = startY
        swipeLastX = startX
        swipeLastY = startY
        swipeMovedDistancePx = 0f

        val currentOpen = activeMobileDrawerPosition
        if (currentOpen != null && activeMobileDrawerOpenFraction > 0f) {
            activeSwipePosition = currentOpen
            isSwipingClose = true
            swipeInitialOpenFraction = activeMobileDrawerOpenFraction
            return currentOpen
        }

        val zone = edgeSwipeDetectionZonePx.toFloat().coerceAtLeast(1f)
        val effectiveWidthPx = (if (viewportWidthPx > 0) viewportWidthPx else viewportWidthDp).toFloat()
        val effectiveHeightPx = (if (viewportHeightPx > 0) viewportHeightPx else viewportHeightDp).toFloat()

        val candidates = mutableListOf<Pair<DockPosition, Float>>()
        if (startX <= zone && hasDockedPanelsAt(DockPosition.LEFT)) {
            candidates.add(DockPosition.LEFT to startX.coerceAtLeast(0f))
        }
        if (startX >= effectiveWidthPx - zone && hasDockedPanelsAt(DockPosition.RIGHT)) {
            candidates.add(DockPosition.RIGHT to (effectiveWidthPx - startX).coerceAtLeast(0f))
        }
        if (startY <= zone && hasDockedPanelsAt(DockPosition.TOP)) {
            candidates.add(DockPosition.TOP to startY.coerceAtLeast(0f))
        }
        if (startY >= effectiveHeightPx - zone && hasDockedPanelsAt(DockPosition.BOTTOM)) {
            candidates.add(DockPosition.BOTTOM to (effectiveHeightPx - startY).coerceAtLeast(0f))
        }

        val matched = candidates.minByOrNull { it.second }?.first
        if (matched == null) {
            activeSwipePosition = null
            isSwipingClose = false
            return null
        }

        activeSwipePosition = matched
        isSwipingClose = false
        swipeInitialOpenFraction = 0f
        activeMobileDrawerPosition = matched
        activeMobileDrawerOpenFraction = 0f
        syncCompactDrawerLayout()
        return matched
    }

    /**
     * Updates [activeMobileDrawerOpenFraction] in `0f..1f` during an active edge-swipe gesture.
     */
    fun updateEdgeSwipe(currentX: Float, currentY: Float): Float {
        if (!isCompactDrawerMode) return 0f
        val pos = activeSwipePosition ?: activeMobileDrawerPosition ?: return 0f
        swipeLastX = currentX
        swipeLastY = currentY
        val dx = currentX - swipeStartX
        val dy = currentY - swipeStartY
        swipeMovedDistancePx = maxOf(swipeMovedDistancePx, hypot(dx, dy))

        val effectiveWidthPx = (if (viewportWidthPx > 0) viewportWidthPx else viewportWidthDp).toFloat()
        val effectiveHeightPx = (if (viewportHeightPx > 0) viewportHeightPx else viewportHeightDp).toFloat()
        val area = areaForPosition(pos)

        val drawerSpanPx = when (pos) {
            DockPosition.LEFT, DockPosition.RIGHT -> {
                area.widthPx?.takeIf { it > 0 }?.toFloat()
                    ?: minOf(240f, (effectiveWidthPx * 0.6f).coerceAtLeast(100f))
            }
            DockPosition.TOP, DockPosition.BOTTOM -> {
                area.heightPx?.takeIf { it > 0 }?.toFloat()
                    ?: minOf(240f, (effectiveHeightPx * 0.5f).coerceAtLeast(100f))
            }
            else -> 240f
        }.coerceAtLeast(1f)

        val inwardDeltaPx = when (pos) {
            DockPosition.LEFT -> dx
            DockPosition.RIGHT -> -dx
            DockPosition.TOP -> dy
            DockPosition.BOTTOM -> -dy
            else -> 0f
        }

        val nextFraction = (swipeInitialOpenFraction + (inwardDeltaPx / drawerSpanPx)).coerceIn(0f, 1f)
        activeMobileDrawerPosition = pos
        activeMobileDrawerOpenFraction = nextFraction
        syncCompactDrawerLayout()
        onMobileDrawerStateChanged?.invoke(activeMobileDrawerPosition, activeMobileDrawerOpenFraction)
        return activeMobileDrawerOpenFraction
    }

    /**
     * Completes an active edge-swipe gesture, snapping the drawer open if
     * `activeMobileDrawerOpenFraction >= drawerOpenThresholdFraction` or `velocityPxPerSec > 350f`,
     * and snapping it closed otherwise.
     */
    fun endEdgeSwipe(velocityPxPerSec: Float = 0f): Boolean {
        if (!isCompactDrawerMode) {
            activeSwipePosition = null
            isSwipingClose = false
            return false
        }
        val pos = activeSwipePosition ?: activeMobileDrawerPosition
        activeSwipePosition = null
        isSwipingClose = false
        if (pos == null) {
            return false
        }
        val shouldOpen = if (velocityPxPerSec < -350f) {
            false
        } else {
            velocityPxPerSec > 350f || activeMobileDrawerOpenFraction >= drawerOpenThresholdFraction
        }
        return if (shouldOpen) {
            openMobileDockDrawer(pos)
        } else {
            closeMobileDockDrawer()
            false
        }
    }

    /**
     * Handles a tap on the main workspace area while in compact drawer mode, immediately closing
     * any open mobile dock drawer.
     */
    fun tapMainWorkspaceInCompactMode(): Boolean {
        if (isCompactDrawerMode && activeMobileDrawerPosition != null) {
            return closeMobileDockDrawer()
        }
        return false
    }

    /**
     * Sets a full-bleed background component (e.g., a Three.js 3D `GLCanvas` / `VKCanvas` or 2D map)
     * underneath the workspace's dock areas and enables [overlayMode] so dock panels float on top
     * of the background component while passing pointer events through uncovered regions.
     */
    fun setBackgroundComponent(component: AbstractComponent?, enableOverlay: Boolean = component != null) {
        backgroundContainer.clearChildren()
        backgroundComponent = component
        if (component != null) {
            backgroundContainer.isVisible = true
            backgroundContainer.addChild(component)
        } else {
            backgroundContainer.isVisible = false
        }
        overlayMode = enableOverlay
        syncOverlayStyling()
    }

    private fun syncOverlayStyling() {
        syncCompactDrawerLayout()
    }

    /**
     * Synchronizes peripheral [DockArea] visibility, CSS classes, off-canvas transforms, backdrop scrim,
     * and edge handles for [isCompactDrawerMode] vs. normal onscreen / overlay dock layouts.
     */
    fun syncCompactDrawerLayout() {
        if (overlayMode) {
            if (!hasClass("dock-workspace-overlay")) addClass("dock-workspace-overlay")
            centralDocumentTabs.isVisible = _documents.isNotEmpty()
        } else {
            if (hasClass("dock-workspace-overlay")) removeClass("dock-workspace-overlay")
            centralDocumentTabs.isVisible = true
        }

        val compact = isCompactDrawerMode
        val peripheralAreas = listOf(
            DockPosition.LEFT to leftArea,
            DockPosition.RIGHT to rightArea,
            DockPosition.TOP to topArea,
            DockPosition.BOTTOM to bottomArea
        )

        if (compact) {
            if (!hasClass("gui-dock-workspace-compact-drawers")) {
                addClass("gui-dock-workspace-compact-drawers")
            }
            val currentActive = activeMobileDrawerPosition
            if (currentActive != null && !hasDockedPanelsAt(currentActive)) {
                activeMobileDrawerPosition = null
                activeMobileDrawerOpenFraction = 0f
            }

            for ((pos, area) in peripheralAreas) {
                val sideClass = "gui-dock-mobile-drawer-${pos.name.lowercase()}"
                if (!area.hasClass("gui-dock-mobile-drawer")) area.addClass("gui-dock-mobile-drawer")
                if (!area.hasClass(sideClass)) area.addClass(sideClass)

                area.positionMode = PositionMode.ABSOLUTE
                area.zIndex = 30
                area.pointerEventsEnabled = true
                when (pos) {
                    DockPosition.LEFT -> {
                        area.leftOffset = "0px"
                        area.topOffset = "0px"
                        area.setStyleProperty("right", "")
                        area.setStyleProperty("bottom", "")
                        area.setStyleProperty("height", "100%")
                    }
                    DockPosition.RIGHT -> {
                        area.leftOffset = null
                        area.setStyleProperty("right", "0px")
                        area.topOffset = "0px"
                        area.setStyleProperty("bottom", "")
                        area.setStyleProperty("height", "100%")
                    }
                    DockPosition.TOP -> {
                        area.leftOffset = "0px"
                        area.topOffset = "0px"
                        area.setStyleProperty("right", "")
                        area.setStyleProperty("bottom", "")
                        area.setStyleProperty("width", "100%")
                    }
                    DockPosition.BOTTOM -> {
                        area.leftOffset = "0px"
                        area.topOffset = null
                        area.setStyleProperty("right", "")
                        area.setStyleProperty("bottom", "0px")
                        area.setStyleProperty("width", "100%")
                    }
                    else -> {}
                }

                val isOpen = (activeMobileDrawerPosition == pos && activeMobileDrawerOpenFraction > 0f && hasDockedPanelsAt(pos))
                if (isOpen) {
                    if (area.hasClass("gui-dock-mobile-drawer-closed")) area.removeClass("gui-dock-mobile-drawer-closed")
                    if (!area.hasClass("gui-dock-mobile-drawer-open")) area.addClass("gui-dock-mobile-drawer-open")
                    area.isVisible = true
                    val fraction = activeMobileDrawerOpenFraction.coerceIn(0f, 1f)
                    area.transform = if (fraction >= 1f) {
                        "translate(0%, 0%)"
                    } else {
                        val offsetPct = ((1f - fraction) * 100f).toInt()
                        when (pos) {
                            DockPosition.LEFT -> "translateX(-${offsetPct}%)"
                            DockPosition.RIGHT -> "translateX(${offsetPct}%)"
                            DockPosition.TOP -> "translateY(-${offsetPct}%)"
                            DockPosition.BOTTOM -> "translateY(${offsetPct}%)"
                            else -> "translate(0%, 0%)"
                        }
                    }
                } else {
                    if (area.hasClass("gui-dock-mobile-drawer-open")) area.removeClass("gui-dock-mobile-drawer-open")
                    if (!area.hasClass("gui-dock-mobile-drawer-closed")) area.addClass("gui-dock-mobile-drawer-closed")
                    area.isVisible = false
                    area.transform = when (pos) {
                        DockPosition.LEFT -> "translateX(-100%)"
                        DockPosition.RIGHT -> "translateX(100%)"
                        DockPosition.TOP -> "translateY(-100%)"
                        DockPosition.BOTTOM -> "translateY(100%)"
                        else -> null
                    }
                }
            }

            val openDrawerActive = activeMobileDrawerPosition != null &&
                activeMobileDrawerOpenFraction > 0f &&
                hasDockedPanelsAt(activeMobileDrawerPosition!!)
            mobileDrawerScrim.isVisible = openDrawerActive
            mobileDrawerScrim.opacity = if (openDrawerActive) activeMobileDrawerOpenFraction.coerceIn(0f, 1f) else 0f

            syncEdgeHandlesVisibility()
        } else {
            if (hasClass("gui-dock-workspace-compact-drawers")) {
                removeClass("gui-dock-workspace-compact-drawers")
            }
            val hadOpenDrawer = activeMobileDrawerPosition != null || activeMobileDrawerOpenFraction > 0f
            activeMobileDrawerPosition = null
            activeMobileDrawerOpenFraction = 0f
            activeSwipePosition = null
            isSwipingClose = false
            mobileDrawerScrim.isVisible = false
            syncEdgeHandlesVisibility()

            for ((pos, area) in peripheralAreas) {
                val sideClass = "gui-dock-mobile-drawer-${pos.name.lowercase()}"
                if (area.hasClass("gui-dock-mobile-drawer")) area.removeClass("gui-dock-mobile-drawer")
                if (area.hasClass(sideClass)) area.removeClass(sideClass)
                if (area.hasClass("gui-dock-mobile-drawer-open")) area.removeClass("gui-dock-mobile-drawer-open")
                if (area.hasClass("gui-dock-mobile-drawer-closed")) area.removeClass("gui-dock-mobile-drawer-closed")
                area.transform = null
                area.isVisible = true
                area.setStyleProperty("width", "")
                area.setStyleProperty("height", "")
                area.setStyleProperty("right", "")
                area.setStyleProperty("bottom", "")
                area.leftOffset = null
                area.topOffset = null
            }

            if (overlayMode) {
                for ((_, area) in peripheralAreas) {
                    area.positionMode = PositionMode.ABSOLUTE
                    area.zIndex = 10
                    area.pointerEventsEnabled = true
                }
                topArea.leftOffset = "0px"
                topArea.topOffset = "0px"
                leftArea.leftOffset = "0px"
                leftArea.topOffset = "48px"
                rightArea.setStyleProperty("right", "0px")
                rightArea.topOffset = "48px"
                bottomArea.leftOffset = "0px"
                bottomArea.setStyleProperty("bottom", "0px")
            } else {
                for ((_, area) in peripheralAreas) {
                    area.positionMode = PositionMode.RELATIVE
                    area.zIndex = null
                }
            }

            if (hadOpenDrawer) {
                onMobileDrawerStateChanged?.invoke(null, 0f)
            }
        }
    }

    fun setToolPalette(palette: ToolPalette) {
        this.toolPalette = palette
        leftArea.addChild(palette, 0)
        syncCompactDrawerLayout()
    }

    fun addDockPanel(panel: DockPanel, position: DockPosition, groupId: String? = null): DockWorkspace =
        registerPanel(panel, position)

    fun dockPanel(panel: DockPanel, position: DockPosition = panel.preferredPosition, groupId: String? = null): DockWorkspace {
        if (panel.panelId !in _panels) {
            return registerPanel(panel, position)
        }
        panel.reattachToDock(position)
        syncCompactDrawerLayout()
        return this
    }

    fun floatPanel(panel: DockPanel, bounds: Rect = panel.floatingBounds): DockWorkspace = apply {
        if (panel.panelId !in _panels) {
            registerPanel(panel, panel.preferredPosition)
        }
        panel.detachToFloatingWindow(bounds)
        syncCompactDrawerLayout()
    }

    fun getDockArea(position: DockPosition): DockArea = areaForPosition(position)

    fun attachDockArea(position: DockPosition, area: DockArea) {
        when (position) {
            DockPosition.LEFT -> leftArea = area
            DockPosition.RIGHT -> rightArea = area
            DockPosition.TOP -> topArea = area
            DockPosition.BOTTOM -> bottomArea = area
            DockPosition.CENTER, DockPosition.FLOATING -> {
                centerArea = area
                wireMainAreaInteractions(area)
            }
        }
        addChild(area)
        syncCompactDrawerLayout()
    }

    fun registerPanel(panel: DockPanel, position: DockPosition = panel.preferredPosition): DockWorkspace = apply {
        panel.preferredPosition = position
        _panels[panel.panelId] = panel
        panel.onDetachHandler = { detached ->
            val ownerWin = findOwnerWindow()
            val panelWin = PanelWindow(
                title = detached.title,
                panel = detached,
                initialBounds = detached.floatingBounds,
                ownerWindow = ownerWin
            )
            detached.getTitleBar()?.let { panelWin.setTitleBar(it) }
            _floatingWindows[detached.panelId] = panelWin
            ownerWin?.bringAttachedPanelWindowsToFront()
            syncCompactDrawerLayout()
        }
        panel.onReattachHandler = { reattached, targetPos ->
            reattached.panelWindow = null
            _floatingWindows.remove(reattached.panelId)?.close()
            if (reattached.parent == null) {
                areaForPosition(targetPos).addPanel(reattached)
            }
            syncCompactDrawerLayout()
        }
        areaForPosition(position).addPanel(panel)
        if (windowMode == StudioWindowMode.MULTI_WINDOW && position != DockPosition.CENTER) {
            panel.detachToFloatingWindow()
        }
        syncCompactDrawerLayout()
    }

    fun findOwnerWindow(): Window? {
        var curr: AbstractComponent? = this
        while (curr != null) {
            if (curr is Window && curr !is PanelWindow) {
                return curr
            }
            if (curr is Fragment && curr.attachedWindow != null) {
                return curr.attachedWindow
            }
            if (curr is FragmentContainer && curr.hostWindow != null) {
                return curr.hostWindow
            }
            curr = curr.parent
        }
        return null
    }

    fun syncFloatingWindowsWithOwnerWindow(explicitOwner: Window? = null) {
        val ownerWin = explicitOwner ?: findOwnerWindow() ?: return
        for (panelWin in _floatingWindows.values) {
            panelWin.attachToMainWindow(ownerWin)
        }
        ownerWin.bringAttachedPanelWindowsToFront()
    }

    fun addDocument(document: StudioDocument): DockWorkspace = apply {
        _documents.add(document)
        if (activeDocumentId == null) {
            activeDocumentId = document.id
            onActiveDocumentChanged?.invoke(document)
        }
    }

    fun removeDocument(documentId: String): Boolean {
        val removed = _documents.removeAll { it.id == documentId }
        if (removed && activeDocumentId == documentId) {
            activeDocumentId = _documents.firstOrNull()?.id
            onActiveDocumentChanged?.invoke(_documents.firstOrNull())
        }
        return removed
    }

    fun selectDocument(documentId: String) {
        val doc = _documents.firstOrNull { it.id == documentId } ?: return
        activeDocumentId = doc.id
        onActiveDocumentChanged?.invoke(doc)
    }

    fun areaForPosition(position: DockPosition): DockArea = when (position) {
        DockPosition.LEFT -> leftArea
        DockPosition.RIGHT -> rightArea
        DockPosition.TOP -> topArea
        DockPosition.BOTTOM -> bottomArea
        DockPosition.CENTER, DockPosition.FLOATING -> centerArea
    }

    fun saveLayoutState(): DockLayoutState = DockLayoutState(
        windowMode = windowMode,
        panelPositions = _panels.mapValues { (_, p) -> if (p.isFloating) DockPosition.FLOATING else p.preferredPosition },
        floatingBounds = _panels.mapValues { (_, p) -> p.floatingBounds },
        collapsedPanels = _panels.filterValues { it.isCollapsed }.keys,
        activeDocumentId = activeDocumentId
    )

    fun restoreLayoutState(state: DockLayoutState) {
        for ((id, bounds) in state.floatingBounds) {
            _panels[id]?.floatingBounds = bounds
        }
        for ((id, panel) in _panels) {
            panel.isCollapsed = id in state.collapsedPanels
        }
        windowMode = state.windowMode
        state.activeDocumentId?.let { selectDocument(it) }
        syncCompactDrawerLayout()
    }

    private fun applyWindowModeTransition(targetMode: StudioWindowMode) {
        when (targetMode) {
            StudioWindowMode.SINGLE_WINDOW_MODE -> {
                for (panel in _panels.values) {
                    if (panel.isFloating) {
                        panel.reattachToDock(panel.preferredPosition)
                    }
                }
            }
            StudioWindowMode.MULTI_WINDOW_MODE -> {
                for (panel in _panels.values) {
                    if (panel.preferredPosition != DockPosition.CENTER && !panel.isFloating) {
                        panel.detachToFloatingWindow(panel.floatingBounds)
                    }
                }
                syncFloatingWindowsWithOwnerWindow()
            }
        }
        syncCompactDrawerLayout()
    }

    override fun createNativeImpl(factory: NativeFactory): DockWorkspaceNativeImpl<*> =
        factory.createDockWorkspace()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DockWorkspaceNativeImpl<*>>() ?: return
        impl.setWindowModeNative(windowMode.name)
        (leftArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.LEFT, it) }
        (rightArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.RIGHT, it) }
        (topArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.TOP, it) }
        (bottomArea.nativeImpl as? DockAreaNativeImpl<*>)?.let { impl.attachDockAreaNative(DockPosition.BOTTOM, it) }
        impl.setCentralDocumentNative(centerArea.nativeImpl)
    }
}
