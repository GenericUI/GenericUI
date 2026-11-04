package com.nexustools.genericui.component.layout

/**
 * Target-group layout strategy for [ColumnLayout] created by [LayoutFactory.createColumnLayout].
 */
interface ColumnLayoutImpl : LayoutImpl {
    val layout: ColumnLayout
    fun syncLayout(viewportWidthDp: Float, viewportHeightDp: Float)
}
