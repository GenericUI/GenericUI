package com.nexustools.genericui.component.layout

/**
 * Platform/viewport strategy contract for [SplitPaneLayout].
 */
interface SplitPaneLayoutImpl : LayoutImpl {
    val layout: SplitPaneLayout

    fun syncSplitPane()
}
