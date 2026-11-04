package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
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
    host: AbstractContainer = Container("genericui:responsivemasterdetaillayout", "gui-responsive-master-detail"),
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<ResponsiveMasterDetailLayoutImpl>(host, breakpointSpec) {

    constructor(
        breakpointSpec: HybridBreakpointSpec
    ) : this(
        host = Container("genericui:responsivemasterdetaillayout", "gui-responsive-master-detail"),
        breakpointSpec = breakpointSpec
    )

    val paneRow: ColumnContainer = ColumnContainer()
    val masterHost: Column = Column(span = 4)
    val detailHost: Column = Column(span = 8)
    val inspectorHost: Column = Column(span = 3)

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
            impl.syncPanes(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
        }

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): ResponsiveMasterDetailLayoutImpl =
        factory.createResponsiveMasterDetailLayout(this)

    override fun onMount() {
        host.addClass("gui-responsive-master-detail")
        host.setDisplay(DisplayMode.FLEX)
        host.setFlexDirection(FlexDirection.COLUMN)

        masterHost.addClass("gui-master-pane")
        detailHost.addClass("gui-detail-pane")
        inspectorHost.addClass("gui-inspector-pane")
        inspectorHost.isVisible = false

        if (!paneRow.children.contains(masterHost)) {
            paneRow.addChild(masterHost)
        }
        if (!paneRow.children.contains(detailHost)) {
            paneRow.addChild(detailHost)
        }
        if (!paneRow.children.contains(inspectorHost)) {
            paneRow.addChild(inspectorHost)
        }
        if (!host.children.contains(paneRow)) {
            host.addChild(paneRow)
        }

        impl.syncPanes(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
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
        impl.syncPanes(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
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
        impl.syncPanes(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
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
        impl.syncPanes(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
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
        impl.syncPanes(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
        return true
    }

    /**
     * When in compact single-pane mode (`!isSideBySideMode`) and [isDetailShowingInCompact] is `true`,
     * navigates back to the Master pane and returns `true`. Otherwise returns `false`.
     */
    fun navigateBackToMaster(): Boolean {
        if (!isSideBySideMode && isDetailShowingInCompact) {
            isDetailShowingInCompact = false
            impl.syncPanes(lastViewportWidthDp.toFloat(), lastViewportHeightDp.toFloat())
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

        impl.syncPanes(viewportWidthDp.toFloat(), viewportHeightDp.toFloat())
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

    override fun onConfigurationChanged(config: WindowConfiguration) {
        super.onConfigurationChanged(config)
        applyWindowConfiguration(config)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        updateViewport(viewportWidth, viewportHeight)
    }

    /**
     * Applies the evaluated pane visibility and Bootstrap column classes to [masterHost], [detailHost], and [inspectorHost].
     */
    fun applyPaneLayout(
        viewportWidthDp: Int = lastViewportWidthDp,
        viewportHeightDp: Int = lastViewportHeightDp
    ) {
        lastViewportWidthDp = viewportWidthDp
        lastViewportHeightDp = viewportHeightDp

        val backend = host.resolveActiveFactory()?.backendTarget
        val smallMobilePhone = impl.supportsDynamicViewportSwitching &&
            lastSmallestScreenWidthDp < breakpointSpec.tabletSmallestWidthDp &&
            viewportHeightDp < breakpointSpec.tabletSmallestWidthDp
        isSideBySideMode = if (backend != null && backend.isMobile) {
            breakpointSpec.isTablet(lastSmallestScreenWidthDp)
        } else {
            viewportWidthDp >= breakpointSpec.mediumBreakpointDp && !smallMobilePhone
        }

        if (isSideBySideMode) {
            host.addClass("gui-master-detail-side-by-side")
            host.removeClass("gui-master-detail-compact")

            masterHost.isVisible = true
            detailHost.isVisible = true
            val showInlineInspector = isInspectorSideBySide
            inspectorHost.isVisible = showInlineInspector

            masterHost.span = masterColMd
            masterHost.setClass("col-$masterColMd gui-master-pane")
            val effectiveDetailSpan = if (showInlineInspector) {
                (12 - masterColMd - inspectorColXl).coerceAtLeast(1)
            } else {
                detailColMd
            }
            detailHost.span = effectiveDetailSpan
            detailHost.setClass("col-$effectiveDetailSpan gui-detail-pane")
            inspectorHost.span = inspectorColXl
            inspectorHost.setClass("col-$inspectorColXl gui-inspector-pane")
        } else {
            host.addClass("gui-master-detail-compact")
            host.removeClass("gui-master-detail-side-by-side")

            val showDetail = isDetailShowingInCompact && detailComponent != null
            masterHost.isVisible = !showDetail
            detailHost.isVisible = showDetail
            inspectorHost.isVisible = false

            masterHost.span = 12
            masterHost.setClass("col-12 gui-master-pane")
            detailHost.span = 12
            detailHost.setClass("col-12 gui-detail-pane")
            inspectorHost.span = 12
            inspectorHost.setClass("col-12 gui-inspector-pane")
        }
    }
}
