package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * [LayoutContainer] hosting a [ResponsiveMasterDetailLayout].
 */
open class ResponsiveMasterDetailContainer(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<ResponsiveMasterDetailLayoutImpl, ResponsiveMasterDetailLayout>(
    "genericui:responsivemasterdetaillayout",
    "gui-responsive-master-detail"
) {
    override val layout: ResponsiveMasterDetailLayout = ResponsiveMasterDetailLayout(
        host = this,
        breakpointSpec = breakpointSpec
    )

    var breakpointSpec: HybridBreakpointSpec
        get() = layout.breakpointSpec
        set(value) {
            layout.breakpointSpec = value
        }

    val paneRow: ColumnContainer
        get() = layout.paneRow

    val masterHost: Column
        get() = layout.masterHost

    val detailHost: Column
        get() = layout.detailHost

    val inspectorHost: Column
        get() = layout.inspectorHost

    val isSideBySideMode: Boolean
        get() = layout.isSideBySideMode

    val isInspectorSideBySide: Boolean
        get() = layout.isInspectorSideBySide

    val isDetailShowingInCompact: Boolean
        get() = layout.isDetailShowingInCompact

    var isInspectorOpen: Boolean
        get() = layout.isInspectorOpen
        set(value) {
            layout.isInspectorOpen = value
        }

    init {
        htmlTag = "genericui:responsivemasterdetaillayout"
        initializeLayout()
    }

    fun setMasterPane(
        component: AbstractComponent,
        widthPx: Int = 320,
        colMd: Int = 4
    ): ResponsiveMasterDetailContainer = apply {
        layout.setMasterPane(component, widthPx, colMd)
    }

    fun setDetailPane(
        component: AbstractComponent,
        colMd: Int = 8
    ): ResponsiveMasterDetailContainer = apply {
        layout.setDetailPane(component, colMd)
    }

    fun setInspectorPane(
        component: AbstractComponent?,
        widthPx: Int = 300,
        colXl: Int = 3
    ): ResponsiveMasterDetailContainer = apply {
        layout.setInspectorPane(component, widthPx, colXl)
    }

    fun getMasterPane(): AbstractComponent? = layout.getMasterPane()

    fun getDetailPane(): AbstractComponent? = layout.getDetailPane()

    fun getInspectorPane(): AbstractComponent? = layout.getInspectorPane()

    fun openDetailInCompact(): Boolean = layout.openDetailInCompact()

    fun navigateBackToMaster(): Boolean = layout.navigateBackToMaster()

    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) = layout.updateViewport(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)

    fun applyWindowConfiguration(config: WindowConfiguration) = layout.applyWindowConfiguration(config)
}
