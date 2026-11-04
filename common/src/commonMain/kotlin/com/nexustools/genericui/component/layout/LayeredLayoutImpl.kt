package com.nexustools.genericui.component.layout

/**
 * Target-group layout strategy for [LayeredLayout].
 */
interface LayeredLayoutImpl : LayoutImpl {
    val layout: LayeredLayout
    fun syncLayers()
}
