package com.nexustools.genericui.component.layout

/**
 * Active display mode of [HybridColumnLayout] and [com.nexustools.genericui.component.fragment.helper.HybridColumnFragment].
 */
enum class HybridLayoutMode {
    /**
     * Multi-column side-by-side Bootstrap-style layout (tablet landscape, wide web viewport, and desktop).
     */
    COLUMNS,

    /**
     * Folded single-interface layout (small-screen mobile, tablet portrait, and narrow web/desktop viewport).
     */
    SINGLE,

    /**
     * Fullscreen expansion of the target column (when optional fullscreen mode is enabled and active).
     */
    FULLSCREEN,

    /**
     * Picture-in-Picture expansion of the target column (when optional PiP mode is active).
     */
    PIP;

    val isExpandedMediaMode: Boolean
        get() = this == FULLSCREEN || this == PIP
}
