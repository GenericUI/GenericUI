package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

/**
 * Default responsive [HybridSplitNavigationLayoutImpl] switching between top tabs in column mode
 * and a bottom navigation bar in compact mode.
 */
open class ResponsiveHybridSplitNavigationLayoutImpl(
    override val layout: HybridSplitNavigationLayout
) : HybridSplitNavigationLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val supportsDynamicViewportSwitching: Boolean = true
    override val minRequiredWindowSize: Size? = null

    override val inColumnMode: Boolean
        get() = layout.inColumnMode

    override fun syncNavigationChrome(inColumnMode: Boolean) {
        layout.applyNavigationChrome(inColumnMode)
    }

    override fun dispose() {
        isDisposed = true
    }
}
