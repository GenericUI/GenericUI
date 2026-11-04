package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

/**
 * Default responsive [AdaptiveNavigationScaffoldLayoutImpl] morphing between `"BOTTOM_NAV"`,
 * `"NAVIGATION_RAIL"`, and `"SIDEBAR"`.
 */
open class DefaultAdaptiveNavigationScaffoldLayoutImpl(
    override val layout: AdaptiveNavigationScaffoldLayout
) : AdaptiveNavigationScaffoldLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val supportsDynamicViewportSwitching: Boolean = true
    override val minRequiredWindowSize: Size? = null

    override fun syncScaffold(viewportWidthDp: Float, viewportHeightDp: Float) {
        layout.applyScaffoldLayout(viewportWidthDp.toInt(), viewportHeightDp.toInt())
    }

    override fun dispose() {
        isDisposed = true
    }
}
