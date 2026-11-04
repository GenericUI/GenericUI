package com.nexustools.genericui.component.layout

/**
 * Backend-group-specific implementation contract for [AspectRatioLayout].
 */
interface AspectRatioLayoutImpl : LayoutImpl {
    fun syncAspectRatio()
    fun syncLayout(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int
    )
}
