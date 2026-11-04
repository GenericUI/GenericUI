package com.nexustools.genericui.component.layout

/**
 * Controls when [AdaptiveCardLayout] wraps its content inside a centered [com.nexustools.genericui.component.surface.Card]
 * versus rendering the content inline as a full-width stack.
 */
enum class AdaptiveCardMode {
    /**
     * Wraps content in a centered card on large screens (`>= cardBreakpointDp`),
     * and renders content inline without card chrome on small screens (`< cardBreakpointDp`).
     */
    CARD_ON_LARGE_SCREENS,

    /**
     * Wraps content in a card on small screens (`< cardBreakpointDp`),
     * and renders content inline on large screens (`>= cardBreakpointDp`).
     */
    CARD_ON_SMALL_SCREENS,

    /**
     * Always wraps content in a centered card on all screen sizes.
     */
    ALWAYS_CARD
}
