package com.nexustools.genericui.component.layout

/**
 * Default responsive implementation of [AdaptiveCardLayoutImpl] used on Mobile, Web, and headless environments.
 */
open class ResponsiveAdaptiveCardLayoutImpl(
    override val layout: AdaptiveCardLayout
) : AdaptiveCardLayoutImpl {

    override var isCardActive: Boolean = true
        protected set

    override var isDisposed: Boolean = false
        protected set

    override fun evaluateIsCardActive(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    ): Boolean {
        val threshold = layout.cardBreakpointDp
        val isSmallScreen = viewportWidthDp < threshold ||
            (smallestScreenWidthDp in 1 until threshold.toInt())
        return when (layout.cardMode) {
            AdaptiveCardMode.CARD_ON_LARGE_SCREENS -> !isSmallScreen
            AdaptiveCardMode.CARD_ON_SMALL_SCREENS -> isSmallScreen
            AdaptiveCardMode.ALWAYS_CARD -> true
        }
    }

    override fun syncLayout(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    ) {
        val active = evaluateIsCardActive(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)
        isCardActive = active
        layout.applyCardState(active)
    }

    override fun dispose() {
        isDisposed = true
    }
}
