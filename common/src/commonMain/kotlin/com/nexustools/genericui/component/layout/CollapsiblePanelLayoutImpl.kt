package com.nexustools.genericui.component.layout

/**
 * Platform/viewport strategy contract for [CollapsiblePanelLayout].
 */
interface CollapsiblePanelLayoutImpl : LayoutImpl {
    val layout: CollapsiblePanelLayout

    fun syncExpandedState()
}
