package com.nexustools.genericui.component.layout

/**
 * Platform/viewport strategy contract for [ResponsiveBentoGridLayout].
 */
interface ResponsiveBentoGridLayoutImpl : LayoutImpl {
    val layout: ResponsiveBentoGridLayout

    fun syncGrid(viewportWidthDp: Float, viewportHeightDp: Float)
}
