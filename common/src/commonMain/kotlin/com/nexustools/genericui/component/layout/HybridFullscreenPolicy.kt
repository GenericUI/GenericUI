package com.nexustools.genericui.component.layout

/**
 * Policy controlling optional dynamic fullscreen & rotation behavior on [HybridColumnLayout],
 * [com.nexustools.genericui.component.fragment.helper.HybridColumnFragment], and [DynamicFullscreenController].
 *
 * **Defaults to [DISABLED]** so fullscreen and rotation-triggered fullscreen are strictly opt-in.
 */
enum class HybridFullscreenPolicy {
    /**
     * Default policy: fullscreen auto-rotation and orientation locking are disabled.
     * [HybridColumnLayout] simply switches between [HybridLayoutMode.COLUMNS] and [HybridLayoutMode.SINGLE]
     * based on screen/viewport size.
     */
    DISABLED,

    /**
     * Opt-in manual fullscreen only: explicit calls to `setFullscreen(true/false)` or `toggleFullscreen()`
     * expand the target column to [HybridLayoutMode.FULLSCREEN], without automatic phone-landscape rotation triggering.
     */
    MANUAL_ONLY,

    /**
     * Opt-in phone auto-fullscreen on landscape rotation:
     * - On small-screen mobile (`smallestScreenWidthDp < 600`), rotating to landscape automatically enters fullscreen,
     *   and rotating back to portrait automatically exits fullscreen.
     */
    AUTO_FULLSCREEN_ON_PHONE_LANDSCAPE,

    /**
     * Opt-in tablet multi-column landscape + locked fullscreen:
     * - On tablet (`smallestScreenWidthDp >= 600`), rotating between landscape and portrait switches between
     *   [HybridLayoutMode.COLUMNS] and [HybridLayoutMode.SINGLE] without forcing fullscreen, while explicit
     *   fullscreen locks landscape orientation until exited.
     */
    TABLET_COLUMNS_AND_LOCKED_FULLSCREEN,

    /**
     * Full Episode-Caster (`AbstractPlayerScreen`) hybrid policy combining both
     * [AUTO_FULLSCREEN_ON_PHONE_LANDSCAPE] on phones (`< 600dp`) and
     * [TABLET_COLUMNS_AND_LOCKED_FULLSCREEN] on tablets (`>= 600dp`), plus desktop/web fullscreen sync.
     */
    AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
}
