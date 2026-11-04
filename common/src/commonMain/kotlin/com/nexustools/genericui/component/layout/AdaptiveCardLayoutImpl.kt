package com.nexustools.genericui.component.layout

/**
 * Target-group layout strategy for [AdaptiveCardLayout] created by [LayoutFactory.createAdaptiveCardLayout].
 */
interface AdaptiveCardLayoutImpl : LayoutImpl {
    val layout: AdaptiveCardLayout
    val isCardActive: Boolean

    fun evaluateIsCardActive(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int = 0
    ): Boolean

    fun syncLayout(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int = 0
    )
}
