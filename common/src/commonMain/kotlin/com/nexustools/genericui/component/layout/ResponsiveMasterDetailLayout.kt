package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * 2-pane or 3-pane (`Master` + `Detail` + optional `Inspector`) responsive layout that displays
 * panes side-by-side on wide screens (`viewportWidthDp >= breakpointSpec.mediumBreakpointDp` on desktop/web
 * or tablet on mobile; 3rd `Inspector` pane inline when `viewportWidthDp >= breakpointSpec.extraWideBreakpointDp`)
 * and folds into a single-pane drill-down stack on small-screen mobile / narrow web (`< 600dp`).
 */
open class ResponsiveMasterDetailLayout(
    var breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Container("genericui:responsivemasterdetaillayout", "gui-responsive-master-detail") {

    val paneRow: Row = Row()
    val masterHost: Col = Col(span = 4, weight = 1.0f)
    val detailHost: Col = Col(span = 8, weight = 2.0f)
    val inspectorHost: Col = Col(span = 3, weight = 1.0f)

    private var masterComponent: AbstractComponent? = null
    private var detailComponent: AbstractComponent? = null
    private var inspectorComponent: AbstractComponent? = null

    private var masterWidthPx: Int = 320
    private var masterColMd: Int = 4
    private var detailColMd: Int = 8
    private var inspectorWidthPx: Int = 300
    private var inspectorColXl: Int = 3

    private var lastViewportWidthDp: Int = 1280
    private var lastViewportHeightDp: Int = 800
    private var lastSmallestScreenWidthDp: Int = 800

    var isSideBySideMode: Boolean = true
        private set

    val isInspectorSideBySide: Boolean
        get() = isSideBySideMode &&
            inspectorComponent != null &&
            isInspectorOpen &&
            lastViewportWidthDp >= breakpointSpec.extraWideBreakpointDp

    var isDetailShowingInCompact: Boolean = false
        private set

    var isInspectorOpen: Boolean = false
        set(value) {
            field = value
            syncPaneLayout()
        }

    init {
        htmlTag = "genericui:responsivemasterdetaillayout"
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.COLUMN)

        masterHost.addClass("gui-master-pane")
        detailHost.addClass("gui-detail-pane")
        inspectorHost.addClass("gui-inspector-pane")
        inspectorHost.isVisible = false

        paneRow.addChild(masterHost)
        paneRow.addChild(detailHost)
        paneRow.addChild(inspectorHost)
        addChild(paneRow)

        syncPaneLayout()
    }

    /**
     * Mounts the Master list/navigation pane component.
     */
    fun setMasterPane(
        component: AbstractComponent,
        widthPx: Int = 320,
        colMd: Int = 4
    ): ResponsiveMasterDetailLayout = apply {
        masterComponent = component
        masterWidthPx = widthPx
        masterColMd = colMd.coerceIn(1, 12)
        masterHost.widthPx = widthPx
        masterHost.clearChildren()
        masterHost.addChild(component)
        syncPaneLayout()
    }

    /**
     * Mounts the Detail content pane component.
     */
    fun setDetailPane(
        component: AbstractComponent,
        colMd: Int = 8
    ): ResponsiveMasterDetailLayout = apply {
        detailComponent = component
        detailColMd = colMd.coerceIn(1, 12)
        detailHost.clearChildren()
        detailHost.addChild(component)
        syncPaneLayout()
    }

    /**
     * Mounts or clears the optional 3rd Inspector pane component.
     * When a non-null [component] is provided, [isInspectorOpen] defaults to `true`.
     */
    fun setInspectorPane(
        component: AbstractComponent?,
        widthPx: Int = 300,
        colXl: Int = 3
    ): ResponsiveMasterDetailLayout = apply {
        inspectorComponent = component
        inspectorWidthPx = widthPx
        inspectorColXl = colXl.coerceIn(1, 12)
        inspectorHost.widthPx = widthPx
        inspectorHost.clearChildren()
        if (component != null) {
            inspectorHost.addChild(component)
            isInspectorOpen = true
        } else {
            isInspectorOpen = false
        }
        syncPaneLayout()
    }

    fun getMasterPane(): AbstractComponent? = masterComponent

    fun getDetailPane(): AbstractComponent? = detailComponent

    fun getInspectorPane(): AbstractComponent? = inspectorComponent

    /**
     * Switches the compact single-pane view (`!isSideBySideMode`) from the Master pane to the Detail pane.
     * Also marks [isDetailShowingInCompact] = `true` so folding to compact mode preserves Detail visibility.
     */
    fun openDetailInCompact(): Boolean {
        if (detailComponent == null) return false
        isDetailShowingInCompact = true
        syncPaneLayout()
        return true
    }

    /**
     * When in compact single-pane mode (`!isSideBySideMode`) and [isDetailShowingInCompact] is `true`,
     * navigates back to the Master pane and returns `true`. Otherwise returns `false`.
     */
    fun navigateBackToMaster(): Boolean {
        if (!isSideBySideMode && isDetailShowingInCompact) {
            isDetailShowingInCompact = false
            syncPaneLayout()
            return true
        }
        return false
    }

    /**
     * Updates the responsive pane layout for the given viewport dimensions in dp.
     */
    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) {
        lastViewportWidthDp = viewportWidthDp
        lastViewportHeightDp = viewportHeightDp
        lastSmallestScreenWidthDp = smallestScreenWidthDp

        val backend = resolveActiveFactory()?.backendTarget
        val smallMobilePhone = smallestScreenWidthDp < breakpointSpec.tabletSmallestWidthDp &&
            viewportHeightDp < breakpointSpec.tabletSmallestWidthDp
        isSideBySideMode = if (backend != null && backend.isMobile) {
            breakpointSpec.isTablet(smallestScreenWidthDp)
        } else {
            viewportWidthDp >= breakpointSpec.mediumBreakpointDp && !smallMobilePhone
        }

        syncPaneLayout()
    }

    /**
     * Applies a [WindowConfiguration] snapshot to update [isSideBySideMode] and [isInspectorSideBySide].
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

    private fun syncPaneLayout() {
        if (isSideBySideMode) {
            addClass("gui-master-detail-side-by-side")
            removeClass("gui-master-detail-compact")

            masterHost.isVisible = true
            detailHost.isVisible = true
            val showInlineInspector = isInspectorSideBySide
            inspectorHost.isVisible = showInlineInspector

            masterHost.setClass("col-$masterColMd gui-master-pane")
            val effectiveDetailSpan = if (showInlineInspector) {
                (12 - masterColMd - inspectorColXl).coerceAtLeast(1)
            } else {
                detailColMd
            }
            detailHost.setClass("col-$effectiveDetailSpan gui-detail-pane")
            inspectorHost.setClass("col-$inspectorColXl gui-inspector-pane")
        } else {
            addClass("gui-master-detail-compact")
            removeClass("gui-master-detail-side-by-side")

            val showDetail = isDetailShowingInCompact && detailComponent != null
            masterHost.isVisible = !showDetail
            detailHost.isVisible = showDetail
            inspectorHost.isVisible = false

            masterHost.setClass("col-12 gui-master-pane")
            detailHost.setClass("col-12 gui-detail-pane")
            inspectorHost.setClass("col-12 gui-inspector-pane")
        }
    }
}
