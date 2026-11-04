package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

/**
 * Default responsive [ResponsiveMasterDetailLayoutImpl] switching between side-by-side panes and
 * compact single-pane drill-down.
 */
open class DefaultResponsiveMasterDetailLayoutImpl(
    override val layout: ResponsiveMasterDetailLayout
) : ResponsiveMasterDetailLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val supportsDynamicViewportSwitching: Boolean = true
    override val minRequiredWindowSize: Size? = null

    override val isSplitMode: Boolean
        get() = layout.isSideBySideMode

    override val isThreePaneMode: Boolean
        get() = layout.isInspectorSideBySide

    override fun syncPanes(viewportWidthDp: Float, viewportHeightDp: Float) {
        layout.applyPaneLayout(viewportWidthDp.toInt(), viewportHeightDp.toInt())
    }

    override fun dispose() {
        isDisposed = true
    }
}
