package com.nexustools.genericui.component.layout

/**
 * Target-group layout strategy for [HybridColumnLayout].
 */
interface HybridColumnLayoutImpl : LayoutImpl {
    val layout: HybridColumnLayout
    val layoutMode: HybridLayoutMode

    fun evaluateLayoutMode(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    ): HybridLayoutMode

    fun syncLayout(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    )
}
