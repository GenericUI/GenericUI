package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.layout.AdaptiveCardLayout
import com.nexustools.genericui.component.layout.AdaptiveCardLayoutImpl
import com.nexustools.genericui.component.layout.AdaptiveCardMode
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Helper [LayoutFragment] backed by [AdaptiveCardLayout] that renders its content inside a centered
 * [Card] on large screens (`CARD_ON_LARGE_SCREENS`) and inline without card chrome on small screens
 * (or vice-versa according to [cardMode]).
 */
open class CardLayoutFragment(
    cardTitle: String? = null,
    cardSubtitle: String? = null,
    cardMode: AdaptiveCardMode = AdaptiveCardMode.CARD_ON_LARGE_SCREENS,
    cardMaxWidthPx: Int = 520,
    cardBreakpointDp: Float = 600f,
    outerPadding: SpacingScale = SpacingScale.S4,
    cardPadding: SpacingScale = SpacingScale.S3,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    id: String? = null
) : LayoutFragment<AdaptiveCardLayoutImpl, AdaptiveCardLayout>(id = id) {

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
    ).also { it.ensureMounted() }

    val cardLayout: AdaptiveCardLayout
        get() = layout

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

    fun addCardContent(component: AbstractComponent): CardLayoutFragment = apply {
        layout.addContent(component)
    }

    fun removeCardContent(component: AbstractComponent): Boolean =
        layout.removeContent(component)

    fun clearCardContent() {
        layout.clearContent()
    }
}
