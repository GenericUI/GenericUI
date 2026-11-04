package com.nexustools.genericui.component.layout

/**
 * Platform/viewport strategy contract for [CollapsibleBottomDockLayout].
 */
interface CollapsibleBottomDockLayoutImpl : LayoutImpl {
    val layout: CollapsibleBottomDockLayout
    val isDockExpanded: Boolean

    fun syncDockState()
}
