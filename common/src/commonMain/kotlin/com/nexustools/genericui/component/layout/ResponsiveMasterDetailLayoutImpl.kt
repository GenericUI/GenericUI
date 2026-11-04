package com.nexustools.genericui.component.layout

/**
 * Platform/viewport strategy contract for [ResponsiveMasterDetailLayout].
 */
interface ResponsiveMasterDetailLayoutImpl : LayoutImpl {
    val layout: ResponsiveMasterDetailLayout
    val isSplitMode: Boolean
    val isThreePaneMode: Boolean

    fun syncPanes(viewportWidthDp: Float, viewportHeightDp: Float)
}
