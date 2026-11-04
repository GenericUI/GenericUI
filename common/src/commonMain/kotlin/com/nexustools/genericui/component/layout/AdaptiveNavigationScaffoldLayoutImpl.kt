package com.nexustools.genericui.component.layout

/**
 * Platform/viewport strategy contract for [AdaptiveNavigationScaffoldLayout].
 */
interface AdaptiveNavigationScaffoldLayoutImpl : LayoutImpl {
    val layout: AdaptiveNavigationScaffoldLayout
    val activeNavigationKind: String
        get() = layout.activeNavigationKind

    fun syncScaffold(viewportWidthDp: Float, viewportHeightDp: Float)
}
