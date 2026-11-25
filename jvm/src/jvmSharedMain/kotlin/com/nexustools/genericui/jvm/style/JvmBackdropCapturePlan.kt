package com.nexustools.genericui.jvm.style

import com.nexustools.genericui.style.css.CssFilter

/**
 * Resolved backdrop capture dimensions, padding, downsample scale, and downsampled [CssFilter]
 * computed by [JvmBackdropCapturePlanner.planCapture] for Swing and Android backdrop renderers.
 */
data class JvmBackdropCapturePlan(
    val blurPx: Float,
    val downsampleScale: Int,
    val captureWidth: Int,
    val captureHeight: Int,
    val blurPaddingPx: Int,
    val scaledFilter: CssFilter,
    val supportsHardwareFastBlurOnly: Boolean,
    val hasPixelOps: Boolean,
    val hasDropShadow: Boolean
)
