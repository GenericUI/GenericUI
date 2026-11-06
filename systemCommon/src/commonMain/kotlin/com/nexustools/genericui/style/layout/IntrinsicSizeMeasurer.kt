package com.nexustools.genericui.style.layout

import com.nexustools.genericui.geometry.Size

/**
 * Callback interface used by [BrowserLayoutEngine] to measure the natural/intrinsic content size
 * of a leaf or unconstrained node given [availableWidthPx] and [availableHeightPx].
 */
fun interface IntrinsicSizeMeasurer<N> {
    fun measureIntrinsicSize(node: N, availableWidthPx: Int, availableHeightPx: Int): Size
}
