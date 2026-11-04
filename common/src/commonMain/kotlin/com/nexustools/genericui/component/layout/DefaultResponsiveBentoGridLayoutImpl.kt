package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

/**
 * Default responsive [ResponsiveBentoGridLayoutImpl] reflowing bento cards across Bootstrap 12-grid breakpoints.
 */
open class DefaultResponsiveBentoGridLayoutImpl(
    override val layout: ResponsiveBentoGridLayout
) : ResponsiveBentoGridLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val supportsDynamicViewportSwitching: Boolean = true
    override val minRequiredWindowSize: Size? = null

    override fun syncGrid(viewportWidthDp: Float, viewportHeightDp: Float) {
        layout.applyGridLayout(viewportWidthDp.toInt(), viewportHeightDp.toInt())
    }

    override fun dispose() {
        isDisposed = true
    }
}
