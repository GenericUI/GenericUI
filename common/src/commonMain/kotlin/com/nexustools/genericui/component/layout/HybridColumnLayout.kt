package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min
import kotlin.math.roundToInt

typealias TabLayout = TabContainer

/**
 * Bootstrap-style responsive multi-column layout container (`HybridColumnLayout`) that displays columns
 * side-by-side in [HybridLayoutMode.COLUMNS] on tablets in landscape, wide web viewports, and desktops,
 * and folds into a single interface ([HybridLayoutMode.SINGLE]) on small-screen mobile phones, tablet portrait,
 * and narrow desktop/web viewports according to [foldMode] and [tabPlacement].
 *
 * Fullscreen and rotation-driven fullscreen are **disabled by default** ([HybridFullscreenPolicy.DISABLED])
 * and can be opted into via [fullscreenPolicy] or [enableDynamicFullscreen].
 */
open class HybridColumnLayout(
    foldMode: HybridFoldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP,
    tabPlacement: HybridTabPlacement = HybridTabPlacement.TOP_TABS,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    fullscreenPolicy: HybridFullscreenPolicy = HybridFullscreenPolicy.DISABLED,
    var fullscreenTargetColumnIndex: Int = 0
) : Container("hybrid-column-layout") {

    private class ColumnRegistration(
        var spec: HybridColumnSpec,
        val component: AbstractComponent,
        val originalHeightPx: Int? = component.heightPx,
        val originalAspectRatio: Float? = component.aspectRatio,
        val appliedGridClasses: MutableSet<String> = linkedSetOf(),
        var compactSizingApplied: Boolean = false
    )

    private val registrations = mutableListOf<ColumnRegistration>()
    private var isRebuildingLayout: Boolean = false
    private var internalFullscreenState: Boolean = false
    private var internalPipState: Boolean = false

    var foldMode: HybridFoldMode = foldMode
        set(value) {
            if (field != value) {
                field = value
                rebuildLayout()
            }
        }

    var tabPlacement: HybridTabPlacement = tabPlacement
        set(value) {
            if (field != value) {
                field = value
                rebuildLayout()
            }
        }

    var breakpointSpec: HybridBreakpointSpec = breakpointSpec
        set(value) {
            if (field != value) {
                field = value
                fullscreenController?.breakpointSpec = value
                rebuildLayout()
            }
        }

    var fullscreenPolicy: HybridFullscreenPolicy = fullscreenPolicy
        set(value) {
            field = value
            if (value == HybridFullscreenPolicy.DISABLED) {
                internalFullscreenState = false
                fullscreenController?.policy = HybridFullscreenPolicy.DISABLED
                fullscreenController = null
                rebuildLayout()
            } else {
                val existing = fullscreenController
                if (existing == null) {
                    val controller = DynamicFullscreenController(
                        policy = value,
                        breakpointSpec = breakpointSpec
                    )
                    wireFullscreenController(controller)
                    fullscreenController = controller
                } else {
                    existing.policy = value
                    existing.breakpointSpec = breakpointSpec
                }
                rebuildLayout()
            }
        }

    var fullscreenController: DynamicFullscreenController? =
        if (fullscreenPolicy != HybridFullscreenPolicy.DISABLED) {
            DynamicFullscreenController(
                policy = fullscreenPolicy,
                breakpointSpec = breakpointSpec
            )
        } else {
            null
        }
        set(value) {
            field = value
            if (value != null) {
                wireFullscreenController(value)
            }
        }

    var viewportWidthDp: Int = 1280
        private set

    var viewportHeightDp: Int = 800
        private set

    var smallestScreenWidthDp: Int = 800
        private set

    val isTablet: Boolean
        get() = breakpointSpec.isTablet(smallestScreenWidthDp)

    val isSmallScreenMobile: Boolean
        get() = breakpointSpec.isSmallScreenMobile(smallestScreenWidthDp)

    val isWideColumnMode: Boolean
        get() {
            if (isFullscreen || isInPictureInPictureMode) return false
            return breakpointSpec.shouldUseWideColumns(
                viewportWidthDp = viewportWidthDp,
                viewportHeightDp = viewportHeightDp,
                smallestScreenWidthDp = smallestScreenWidthDp,
                backendTarget = resolveActiveFactory()?.backendTarget
            )
        }

    var layoutMode: HybridLayoutMode = HybridLayoutMode.COLUMNS
        private set

    val inColumnMode: Boolean
        get() = layoutMode == HybridLayoutMode.COLUMNS

    var isFullscreen: Boolean
        get() = fullscreenController?.isFullscreen ?: internalFullscreenState
        set(value) {
            if (value && fullscreenPolicy == HybridFullscreenPolicy.DISABLED) {
                fullscreenPolicy = HybridFullscreenPolicy.MANUAL_ONLY
            }
            internalFullscreenState = value
            val controller = fullscreenController
            if (controller != null && controller.isEnabled) {
                if (controller.isFullscreen != value) {
                    controller.setFullscreen(value, userInitiated = true)
                } else {
                    rebuildLayout()
                }
            } else {
                rebuildLayout()
            }
        }

    var isInPictureInPictureMode: Boolean
        get() = fullscreenController?.isInPictureInPictureMode ?: internalPipState
        set(value) {
            internalPipState = value
            val controller = fullscreenController
            if (controller != null) {
                controller.setPictureInPictureMode(value)
            }
            rebuildLayout()
        }

    var activeTabColumnId: String? = null
        private set

    var onLayoutModeChanged: ((HybridLayoutMode) -> Unit)? = null
    var onActiveTabChanged: ((String) -> Unit)? = null

    val headerSlot: Container = Container("div", "gui-hybrid-header-slot").apply { isVisible = false }
    val columnsRow: HStack = HStack().apply { addClass("row gui-hybrid-columns-row") }
    val singleStack: VStack = VStack().apply { addClass("gui-hybrid-single-stack") }
    val rightTabbedColumn: VStack = VStack().apply { addClass("col gui-hybrid-tabbed-column") }
    val topTabBar: TabLayout = TabLayout().apply { addClass("gui-hybrid-top-tabs") }
    val bottomNavBar: BottomNavigationBar = BottomNavigationBar().apply { addClass("gui-hybrid-bottom-nav") }
    val bottomDockSlot: Container = Container("div", "gui-hybrid-bottom-dock-slot").apply { isVisible = false }

    val columnSpecs: List<HybridColumnSpec>
        get() = registrations.map { it.spec }

    val visibleColumnSpecs: List<HybridColumnSpec>
        get() = registrations.filter { it.spec.visible }.map { it.spec }

    init {
        htmlTag = "genericui:hybridcolumnlayout"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)
        fullscreenController?.let { wireFullscreenController(it) }

        topTabBar.setOnTabSelected { tabId ->
            if (!isRebuildingLayout) {
                selectTab(tabId)
            }
        }
        bottomNavBar.onItemSelected = { itemId ->
            if (!isRebuildingLayout) {
                selectTab(itemId)
            }
        }

        rebuildLayout()
    }

    private fun wireFullscreenController(controller: DynamicFullscreenController) {
        controller.breakpointSpec = breakpointSpec
        controller.onFullscreenChanged = { enabled ->
            internalFullscreenState = enabled
            rebuildLayout()
        }
        controller.onPictureInPictureChanged = { inPip ->
            internalPipState = inPip
            rebuildLayout()
        }
    }

    /**
     * Explicitly enables dynamic fullscreen behavior with the specified [policy] and optional [window].
     */
    fun enableDynamicFullscreen(
        policy: HybridFullscreenPolicy = HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED,
        window: IWindow? = null
    ): DynamicFullscreenController {
        val effectivePolicy = if (policy == HybridFullscreenPolicy.DISABLED) {
            HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
        } else {
            policy
        }
        this.fullscreenPolicy = effectivePolicy
        val controller = fullscreenController ?: DynamicFullscreenController(
            policy = effectivePolicy,
            breakpointSpec = breakpointSpec
        ).also {
            wireFullscreenController(it)
            fullscreenController = it
        }
        if (window != null) {
            controller.targetWindow = window
        }
        controller.onViewportOrOrientationChanged(
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp,
            smallestScreenWidthDp = smallestScreenWidthDp,
            isMobileTarget = resolveActiveFactory()?.backendTarget?.isMobile ?: true
        )
        rebuildLayout()
        return controller
    }

    /**
     * Disables dynamic fullscreen and clears [fullscreenController].
     */
    fun disableDynamicFullscreen() {
        fullscreenController?.targetWindow?.setFullscreen(false)
        internalFullscreenState = false
        fullscreenPolicy = HybridFullscreenPolicy.DISABLED
    }

    /**
     * Toggles fullscreen mode (automatically enabling [HybridFullscreenPolicy.MANUAL_ONLY] if currently disabled).
     */
    fun toggleFullscreen(): Boolean {
        isFullscreen = !isFullscreen
        return isFullscreen
    }

    /**
     * Registers a column with its [spec] and [component].
     */
    fun addColumn(spec: HybridColumnSpec, component: AbstractComponent): HybridColumnLayout = apply {
        val existingIdx = registrations.indexOfFirst { it.spec.id == spec.id }
        val entry = ColumnRegistration(
            spec = spec,
            component = component,
            originalHeightPx = component.heightPx,
            originalAspectRatio = component.aspectRatio
        )
        if (existingIdx >= 0) {
            val old = registrations[existingIdx]
            detachFromParent(old.component)
            registrations[existingIdx] = entry
        } else {
            registrations.add(entry)
        }
        rebuildLayout()
    }

    /**
     * Convenience overload for registering a column by parameters.
     */
    fun addColumn(
        id: String,
        title: String = id,
        weight: Float = 1.0f,
        colMd: Int? = null,
        colLg: Int? = null,
        colXl: Int? = null,
        icon: String? = null,
        compactHeightPx: Int? = null,
        compactAspectRatio: Float? = null,
        component: AbstractComponent
    ): HybridColumnLayout = addColumn(
        spec = HybridColumnSpec(
            id = id,
            title = title,
            icon = icon,
            weight = weight,
            colMd = colMd,
            colLg = colLg,
            colXl = colXl,
            compactHeightPx = compactHeightPx,
            compactAspectRatio = compactAspectRatio
        ),
        component = component
    )

    /**
     * Removes the column with the given [id].
     */
    fun removeColumn(id: String): Boolean {
        val idx = registrations.indexOfFirst { it.spec.id == id }
        if (idx < 0) return false
        val removed = registrations.removeAt(idx)
        resetColumnFormatting(removed)
        detachFromParent(removed.component)
        if (activeTabColumnId == id) {
            activeTabColumnId = null
        }
        rebuildLayout()
        return true
    }

    /**
     * Removes all columns from this layout.
     */
    fun clearColumns() {
        for (reg in registrations) {
            resetColumnFormatting(reg)
            detachFromParent(reg.component)
        }
        registrations.clear()
        activeTabColumnId = null
        rebuildLayout()
    }

    /**
     * Shows or hides a registered column by [id] and rebuilds the layout.
     */
    fun setColumnVisible(id: String, visible: Boolean) {
        val reg = registrations.firstOrNull { it.spec.id == id } ?: return
        if (reg.spec.visible != visible) {
            reg.spec = reg.spec.copy(visible = visible)
            rebuildLayout()
        }
    }

    fun getColumnSpec(id: String): HybridColumnSpec? =
        registrations.firstOrNull { it.spec.id == id }?.spec

    fun getColumnComponent(id: String): AbstractComponent? =
        registrations.firstOrNull { it.spec.id == id }?.component

    /**
     * Selects the tab for [columnId] when columns are collapsed into tabs.
     */
    fun selectTab(columnId: String): Boolean {
        val reg = registrations.firstOrNull { it.spec.id == columnId && it.spec.visible } ?: return false
        val changed = activeTabColumnId != reg.spec.id
        activeTabColumnId = reg.spec.id
        rebuildLayout()
        if (changed) {
            onActiveTabChanged?.invoke(reg.spec.id)
        }
        return true
    }

    /**
     * Sets an optional header component above the columns/stack (automatically hidden in fullscreen/PiP).
     */
    fun setHeaderComponent(component: AbstractComponent?) {
        headerSlot.clearChildren()
        if (component != null) {
            headerSlot.addChild(component)
        }
        rebuildLayout()
    }

    /**
     * Sets an optional bottom dock component below the columns/stack (automatically hidden in fullscreen/PiP).
     */
    fun setBottomDockComponent(component: AbstractComponent?) {
        bottomDockSlot.clearChildren()
        if (component != null) {
            bottomDockSlot.addChild(component)
        }
        rebuildLayout()
    }

    /**
     * Updates viewport dimensions in `dp`, notifies [fullscreenController] if enabled, and rebuilds the layout.
     */
    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp),
        isMobileTarget: Boolean? = null
    ) {
        this.viewportWidthDp = viewportWidthDp.coerceAtLeast(1)
        this.viewportHeightDp = viewportHeightDp.coerceAtLeast(1)
        this.smallestScreenWidthDp = smallestScreenWidthDp.coerceAtLeast(1)

        val controller = fullscreenController
        if (controller != null && controller.isEnabled) {
            val mobileFlag = isMobileTarget ?: (resolveActiveFactory()?.backendTarget?.isMobile ?: true)
            controller.onViewportOrOrientationChanged(
                viewportWidthDp = this.viewportWidthDp,
                viewportHeightDp = this.viewportHeightDp,
                smallestScreenWidthDp = this.smallestScreenWidthDp,
                isMobileTarget = mobileFlag
            )
        }

        rebuildLayout()
    }

    /**
     * Applies a [WindowConfiguration] snapshot by converting pixel dimensions to `dp` via [breakpointSpec].
     */
    fun applyWindowConfiguration(config: WindowConfiguration) {
        val widthDp = breakpointSpec.toDp(config.widthPx, config.densityScale)
        val heightDp = breakpointSpec.toDp(config.heightPx, config.densityScale)
        val rawSmallest = config.smallestScreenWidthDp
        val smallestDp = if (config.densityScale != 1.0f && rawSmallest == min(config.widthPx, config.heightPx)) {
            min(widthDp, heightDp)
        } else {
            rawSmallest
        }
        if (config.isInPictureInPicture != isInPictureInPictureMode) {
            isInPictureInPictureMode = config.isInPictureInPicture
        }
        if (config.isFullscreen && !isFullscreen) {
            isFullscreen = true
        }
        updateViewport(
            viewportWidthDp = widthDp,
            viewportHeightDp = heightDp,
            smallestScreenWidthDp = smallestDp
        )
    }

    /**
     * Reconstructs the active hierarchy ([HybridLayoutMode.COLUMNS], [HybridLayoutMode.SINGLE],
     * [HybridLayoutMode.FULLSCREEN], or [HybridLayoutMode.PIP]) based on current viewport, fold mode, and tab state.
     */
    fun rebuildLayout() {
        if (isRebuildingLayout) return
        isRebuildingLayout = true
        try {
            val previousMode = layoutMode
            val nextMode = when {
                isInPictureInPictureMode -> HybridLayoutMode.PIP
                isFullscreen -> HybridLayoutMode.FULLSCREEN
                isWideColumnMode -> HybridLayoutMode.COLUMNS
                else -> HybridLayoutMode.SINGLE
            }
            layoutMode = nextMode

            // Detach all structural containers and column components before reassembling
            columnsRow.clearChildren()
            singleStack.clearChildren()
            rightTabbedColumn.clearChildren()
            detachFromParent(headerSlot)
            detachFromParent(columnsRow)
            detachFromParent(singleStack)
            detachFromParent(rightTabbedColumn)
            detachFromParent(topTabBar)
            detachFromParent(bottomNavBar)
            detachFromParent(bottomDockSlot)

            val visibleRegs = registrations.filter { it.spec.visible }
            for (reg in registrations) {
                resetColumnFormatting(reg)
                detachFromParent(reg.component)
                reg.component.isVisible = false
            }

            if (nextMode == HybridLayoutMode.FULLSCREEN || nextMode == HybridLayoutMode.PIP) {
                headerSlot.isVisible = false
                columnsRow.isVisible = false
                singleStack.isVisible = false
                topTabBar.isVisible = false
                bottomNavBar.isVisible = false
                bottomDockSlot.isVisible = false

                val targetReg = visibleRegs.getOrNull(fullscreenTargetColumnIndex) ?: visibleRegs.firstOrNull()
                if (targetReg != null) {
                    targetReg.component.isVisible = true
                    targetReg.component.setStyleProperty("width", "100%")
                    targetReg.component.setStyleProperty("height", "100%")
                    targetReg.component.setFlexGrow(1f)
                    addChild(targetReg.component)
                }
            } else if (nextMode == HybridLayoutMode.COLUMNS) {
                mountHeaderIfPresent()
                singleStack.isVisible = false
                columnsRow.isVisible = true
                columnsRow.setFlexGrow(1f)
                addChild(columnsRow)

                val shouldCollapseSecondaryInColumns =
                    visibleRegs.size > 2 &&
                        (foldMode == HybridFoldMode.COLLAPSE_ALL_COLUMNS_TO_TABS ||
                            foldMode == HybridFoldMode.COLLAPSE_SECONDARY_COLUMNS_TO_TABS)

                if (shouldCollapseSecondaryInColumns) {
                    val primaryReg = visibleRegs.first()
                    val secondaryRegs = visibleRegs.drop(1)

                    applyColumnModeFormatting(primaryReg)
                    primaryReg.component.isVisible = true
                    columnsRow.addChild(primaryReg.component)

                    // Promote Column 0 out of tabs and ensure activeTabColumnId selects a secondary column (1..N)
                    if (activeTabColumnId == null || secondaryRegs.none { it.spec.id == activeTabColumnId }) {
                        activeTabColumnId = secondaryRegs.first().spec.id
                    }

                    val useTopTabs = tabPlacement != HybridTabPlacement.BOTTOM_NAV
                    populateTabBars(secondaryRegs.map { it.spec }, useTopTabs = useTopTabs)

                    rightTabbedColumn.isVisible = true
                    rightTabbedColumn.setFlexGrow(1f)
                    if (useTopTabs) {
                        rightTabbedColumn.addChild(topTabBar)
                    }
                    val activeSecReg = secondaryRegs.firstOrNull { it.spec.id == activeTabColumnId } ?: secondaryRegs.first()
                    activeSecReg.component.isVisible = true
                    activeSecReg.component.setFlexGrow(1f)
                    if (activeSecReg.spec.scrollableInColumnMode) {
                        activeSecReg.component.addClass("overflow-auto")
                        activeSecReg.appliedGridClasses.add("overflow-auto")
                    }
                    rightTabbedColumn.addChild(activeSecReg.component)
                    if (!useTopTabs) {
                        rightTabbedColumn.addChild(bottomNavBar)
                    }
                    columnsRow.addChild(rightTabbedColumn)
                } else {
                    topTabBar.isVisible = false
                    bottomNavBar.isVisible = false
                    for (reg in visibleRegs) {
                        applyColumnModeFormatting(reg)
                        reg.component.isVisible = true
                        columnsRow.addChild(reg.component)
                    }
                }

                mountBottomDockIfPresent()
            } else {
                // HybridLayoutMode.SINGLE
                mountHeaderIfPresent()
                columnsRow.isVisible = false
                singleStack.isVisible = true
                singleStack.setFlexGrow(1f)
                addChild(singleStack)

                val useTopTabs = tabPlacement == HybridTabPlacement.TOP_TABS

                when (foldMode) {
                    HybridFoldMode.PIN_FIRST_COLUMN_TOP -> {
                        if (visibleRegs.size <= 2) {
                            topTabBar.isVisible = false
                            bottomNavBar.isVisible = false
                            visibleRegs.firstOrNull()?.let { firstReg ->
                                applyCompactPinnedFormatting(firstReg)
                                firstReg.component.isVisible = true
                                singleStack.addChild(firstReg.component)
                            }
                            if (visibleRegs.size == 2) {
                                val secondReg = visibleRegs[1]
                                applySingleModeBodyFormatting(secondReg)
                                secondReg.component.isVisible = true
                                singleStack.addChild(secondReg.component)
                            }
                        } else {
                            val firstReg = visibleRegs.first()
                            val secondaryRegs = visibleRegs.drop(1)
                            applyCompactPinnedFormatting(firstReg)
                            firstReg.component.isVisible = true
                            singleStack.addChild(firstReg.component)

                            if (activeTabColumnId == null || secondaryRegs.none { it.spec.id == activeTabColumnId }) {
                                activeTabColumnId = secondaryRegs.first().spec.id
                            }
                            populateTabBars(secondaryRegs.map { it.spec }, useTopTabs = useTopTabs)

                            if (useTopTabs) {
                                singleStack.addChild(topTabBar)
                            }
                            val activeSecReg = secondaryRegs.firstOrNull { it.spec.id == activeTabColumnId } ?: secondaryRegs.first()
                            applySingleModeBodyFormatting(activeSecReg)
                            activeSecReg.component.isVisible = true
                            singleStack.addChild(activeSecReg.component)
                            if (!useTopTabs) {
                                singleStack.addChild(bottomNavBar)
                            }
                        }
                    }

                    HybridFoldMode.COLLAPSE_SECONDARY_COLUMNS_TO_TABS -> {
                        if (visibleRegs.size <= 1) {
                            topTabBar.isVisible = false
                            bottomNavBar.isVisible = false
                            visibleRegs.firstOrNull()?.let { firstReg ->
                                applySingleModeBodyFormatting(firstReg)
                                firstReg.component.isVisible = true
                                singleStack.addChild(firstReg.component)
                            }
                        } else {
                            val firstReg = visibleRegs.first()
                            val secondaryRegs = visibleRegs.drop(1)
                            applyCompactPinnedFormatting(firstReg)
                            firstReg.component.isVisible = true
                            singleStack.addChild(firstReg.component)

                            if (activeTabColumnId == null || secondaryRegs.none { it.spec.id == activeTabColumnId }) {
                                activeTabColumnId = secondaryRegs.first().spec.id
                            }
                            populateTabBars(secondaryRegs.map { it.spec }, useTopTabs = useTopTabs)

                            if (useTopTabs) {
                                singleStack.addChild(topTabBar)
                            }
                            val activeSecReg = secondaryRegs.firstOrNull { it.spec.id == activeTabColumnId } ?: secondaryRegs.first()
                            applySingleModeBodyFormatting(activeSecReg)
                            activeSecReg.component.isVisible = true
                            singleStack.addChild(activeSecReg.component)
                            if (!useTopTabs) {
                                singleStack.addChild(bottomNavBar)
                            }
                        }
                    }

                    HybridFoldMode.COLLAPSE_ALL_COLUMNS_TO_TABS -> {
                        if (visibleRegs.isEmpty()) {
                            topTabBar.isVisible = false
                            bottomNavBar.isVisible = false
                        } else {
                            if (activeTabColumnId == null || visibleRegs.none { it.spec.id == activeTabColumnId }) {
                                activeTabColumnId = visibleRegs.first().spec.id
                            }
                            populateTabBars(visibleRegs.map { it.spec }, useTopTabs = useTopTabs)

                            if (useTopTabs) {
                                singleStack.addChild(topTabBar)
                            }
                            val activeReg = visibleRegs.firstOrNull { it.spec.id == activeTabColumnId } ?: visibleRegs.first()
                            applySingleModeBodyFormatting(activeReg)
                            activeReg.component.isVisible = true
                            singleStack.addChild(activeReg.component)
                            if (!useTopTabs) {
                                singleStack.addChild(bottomNavBar)
                            }
                        }
                    }

                    HybridFoldMode.STACK_VERTICAL -> {
                        topTabBar.isVisible = false
                        bottomNavBar.isVisible = false
                        for ((idx, reg) in visibleRegs.withIndex()) {
                            if (idx == 0 && (reg.spec.compactHeightPx != null || reg.spec.compactAspectRatio != null)) {
                                applyCompactPinnedFormatting(reg)
                            } else {
                                applySingleModeBodyFormatting(reg)
                            }
                            reg.component.isVisible = true
                            singleStack.addChild(reg.component)
                        }
                    }
                }

                mountBottomDockIfPresent()
            }

            if (previousMode != nextMode) {
                onLayoutModeChanged?.invoke(nextMode)
            }
        } finally {
            isRebuildingLayout = false
        }
    }

    private fun mountHeaderIfPresent() {
        if (headerSlot.children.isNotEmpty()) {
            headerSlot.isVisible = true
            addChild(headerSlot)
        } else {
            headerSlot.isVisible = false
        }
    }

    private fun mountBottomDockIfPresent() {
        if (bottomDockSlot.children.isNotEmpty()) {
            bottomDockSlot.isVisible = true
            addChild(bottomDockSlot)
        } else {
            bottomDockSlot.isVisible = false
        }
    }

    private fun populateTabBars(specs: List<HybridColumnSpec>, useTopTabs: Boolean) {
        while (topTabBar.entries.isNotEmpty()) {
            topTabBar.removeTabAt(0)
        }
        for (spec in specs) {
            topTabBar.addTab(
                title = spec.title,
                content = Container("div", "gui-hybrid-tab-placeholder"),
                id = spec.id,
                icon = spec.icon,
                closable = false
            )
        }
        val selectedIdx = specs.indexOfFirst { it.id == activeTabColumnId }.coerceAtLeast(0)
        if (specs.isNotEmpty()) {
            topTabBar.selectedIndex = selectedIdx
        }

        bottomNavBar.setItems(specs.map { Triple(it.id, it.title, it.icon) })
        if (activeTabColumnId != null) {
            bottomNavBar.selectedItemId = activeTabColumnId
        }

        topTabBar.isVisible = useTopTabs && specs.isNotEmpty()
        bottomNavBar.isVisible = !useTopTabs && specs.isNotEmpty()
    }

    private fun applyColumnModeFormatting(reg: ColumnRegistration) {
        val spec = reg.spec
        val comp = reg.component
        val classesToAdd = linkedSetOf<String>()

        spec.colSm?.let { classesToAdd.add("col-sm-${it.coerceIn(1, 12)}") }
        spec.colMd?.let { classesToAdd.add("col-md-${it.coerceIn(1, 12)}") }
        spec.colLg?.let { classesToAdd.add("col-lg-${it.coerceIn(1, 12)}") }
        spec.colXl?.let { classesToAdd.add("col-xl-${it.coerceIn(1, 12)}") }

        val resolvedSpan = spec.resolveBootstrapColSpan(viewportWidthDp)
        if (resolvedSpan != null) {
            classesToAdd.add("col-$resolvedSpan")
        } else {
            classesToAdd.add("col")
        }

        if (spec.scrollableInColumnMode) {
            classesToAdd.add("overflow-auto")
        }

        for (cls in classesToAdd) {
            comp.addClass(cls)
            reg.appliedGridClasses.add(cls)
        }

        comp.setFlexGrow(spec.weight)
        spec.minWidthPx?.let { comp.minWidthPx = it }
        spec.maxWidthPx?.let { comp.maxWidthPx = it }
    }

    private fun applyCompactPinnedFormatting(reg: ColumnRegistration) {
        val spec = reg.spec
        val comp = reg.component
        if (spec.compactHeightPx != null) {
            comp.heightPx = spec.compactHeightPx
            reg.compactSizingApplied = true
        } else if (spec.compactAspectRatio != null && spec.compactAspectRatio > 0f) {
            comp.aspectRatio = spec.compactAspectRatio
            comp.heightPx = (viewportWidthDp / spec.compactAspectRatio).roundToInt()
            reg.compactSizingApplied = true
        }
    }

    private fun applySingleModeBodyFormatting(reg: ColumnRegistration) {
        val spec = reg.spec
        val comp = reg.component
        comp.setFlexGrow(spec.weight.coerceAtLeast(1f))
        if (spec.scrollableInSingleMode) {
            comp.addClass("overflow-auto")
            reg.appliedGridClasses.add("overflow-auto")
        }
    }

    private fun resetColumnFormatting(reg: ColumnRegistration) {
        val comp = reg.component
        for (cls in reg.appliedGridClasses) {
            comp.removeClass(cls)
        }
        reg.appliedGridClasses.clear()
        if (reg.compactSizingApplied) {
            comp.heightPx = reg.originalHeightPx
            comp.aspectRatio = reg.originalAspectRatio
            reg.compactSizingApplied = false
        }
        comp.setStyleProperty("width", "")
        comp.setStyleProperty("height", "")
        comp.setStyleProperty("flex-grow", "")
    }

    private fun detachFromParent(component: AbstractComponent) {
        (component.parent as? AbstractContainer)?.removeChild(component)
    }

    private fun AbstractComponent.setFlexGrow(weight: Float) {
        setStyleProperty("flex-grow", weight.toString())
    }
}
