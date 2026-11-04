package com.nexustools.genericui.component.layout

import kotlin.math.roundToInt

/**
 * Default [AspectRatioLayoutImpl] shared across Desktop, Mobile, and headless targets.
 */
open class DefaultAspectRatioLayoutImpl(
    protected val layout: AspectRatioLayout
) : AspectRatioLayoutImpl {

    override var isDisposed: Boolean = false

    override fun dispose() {
        isDisposed = true
    }

    override fun syncAspectRatio() {
        layout.syncHostAspectRatioClassesAndStyles()
    }

    override fun syncLayout(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    ) {
        layout.syncHostAspectRatioClassesAndStyles()
        val ratio = layout.effectiveAspectRatio
        if (ratio > 0f) {
            val hostWidth = layout.host.widthPx?.takeIf { it > 0 } ?: viewportWidthDp.roundToInt()
            if (hostWidth > 0) {
                layout.computedHeightPx = (hostWidth / ratio).roundToInt().coerceAtLeast(1)
            }
        }
    }
}
