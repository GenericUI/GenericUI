package com.nexustools.genericui.style.transition

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Snapshot of the interpolated compositor state for an animated container at a specific frame.
 *
 * Used by both native transition layers and the window-manager-style [ContainerRenderCache]
 * compositor to animate pre-rendered container buffers without re-rendering interior children.
 */
data class ContainerAnimationFrameState(
    val opacity: Float = 1.0f,
    val transform: CssTransform2D = CssTransform2D.IDENTITY,
    val leftPx: Float? = null,
    val topPx: Float? = null,
    val widthPx: Float? = null,
    val heightPx: Float? = null,
    val backgroundColor: RgbaColor? = null,
    val progress: Float = 1.0f,
    val isAnimating: Boolean = false,
    val usingNativeTransition: Boolean = false
)
