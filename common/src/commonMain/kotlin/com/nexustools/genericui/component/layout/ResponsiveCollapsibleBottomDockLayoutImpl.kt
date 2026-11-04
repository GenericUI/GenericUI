package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

/**
 * Default responsive [CollapsibleBottomDockLayoutImpl] managing peek/expanded bottom dock state.
 */
open class ResponsiveCollapsibleBottomDockLayoutImpl(
    override val layout: CollapsibleBottomDockLayout
) : CollapsibleBottomDockLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val supportsDynamicViewportSwitching: Boolean = true
    override val minRequiredWindowSize: Size? = null

    override val isDockExpanded: Boolean
        get() = layout.isDockExpanded

    override fun syncDockState() {
        layout.applyDockState()
    }

    override fun dispose() {
        isDisposed = true
    }
}
