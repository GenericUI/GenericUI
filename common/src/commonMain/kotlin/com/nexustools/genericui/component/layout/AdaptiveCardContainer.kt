package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Container component (`<genericui:adaptive-card-layout>`) backed by [AdaptiveCardLayout].
 */
open class AdaptiveCardContainer(
    cardTitle: String? = null,
    cardSubtitle: String? = null,
    cardMode: AdaptiveCardMode = AdaptiveCardMode.CARD_ON_LARGE_SCREENS,
    cardMaxWidthPx: Int = 520,
    cardBreakpointDp: Float = 600f,
    outerPadding: SpacingScale = SpacingScale.S4,
    cardPadding: SpacingScale = SpacingScale.S3,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<AdaptiveCardLayoutImpl, AdaptiveCardLayout>(
    "genericui:adaptive-card-layout",
    "gui-adaptive-card-layout"
) {

    override val layout: AdaptiveCardLayout = AdaptiveCardLayout(
        host = this,
        cardTitle = cardTitle,
        cardSubtitle = cardSubtitle,
        cardMode = cardMode,
        cardMaxWidthPx = cardMaxWidthPx,
        cardBreakpointDp = cardBreakpointDp,
        outerPadding = outerPadding,
        cardPadding = cardPadding,
        breakpointSpec = breakpointSpec
    )

    val cardContainer: Card
        get() = layout.cardContainer

    val inlineContentStack: VStack
        get() = layout.inlineContentStack

    val contentItems: List<AbstractComponent>
        get() = layout.contentItems

    val isCardActive: Boolean
        get() = layout.isCardActive

    var cardTitle: String?
        get() = layout.cardTitle
        set(value) {
            layout.cardTitle = value
        }

    var cardSubtitle: String?
        get() = layout.cardSubtitle
        set(value) {
            layout.cardSubtitle = value
        }

    var cardMode: AdaptiveCardMode
        get() = layout.cardMode
        set(value) {
            layout.cardMode = value
        }

    var cardMaxWidthPx: Int
        get() = layout.cardMaxWidthPx
        set(value) {
            layout.cardMaxWidthPx = value
        }

    var cardBreakpointDp: Float
        get() = layout.cardBreakpointDp
        set(value) {
            layout.cardBreakpointDp = value
        }

    var outerPadding: SpacingScale
        get() = layout.outerPadding
        set(value) {
            layout.outerPadding = value
        }

    var cardPadding: SpacingScale
        get() = layout.cardPadding
        set(value) {
            layout.cardPadding = value
        }

    var onCardModeChanged: ((Boolean) -> Unit)?
        get() = layout.onCardModeChanged
        set(value) {
            layout.onCardModeChanged = value
        }

    init {
        initializeLayout()
    }

    fun addContent(component: AbstractComponent): AdaptiveCardContainer = apply {
        layout.addContent(component)
    }

    fun removeContent(component: AbstractComponent): Boolean =
        layout.removeContent(component)

    fun clearContent() {
        layout.clearContent()
    }

    fun updateViewport(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int = 0
    ) {
        layout.updateViewport(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)
    }

    fun updateViewport(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        densityScale: Float = 1f
    ) {
        layout.updateViewport(viewportWidthPx, viewportHeightPx, densityScale)
    }

    fun applyWindowConfiguration(config: WindowConfiguration) {
        layout.applyWindowConfiguration(config)
    }
}
