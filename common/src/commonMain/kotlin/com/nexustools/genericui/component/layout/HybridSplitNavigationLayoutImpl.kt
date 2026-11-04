package com.nexustools.genericui.component.layout

/**
 * Platform/viewport strategy contract for [HybridSplitNavigationLayout].
 */
interface HybridSplitNavigationLayoutImpl : LayoutImpl {
    val layout: HybridSplitNavigationLayout
    val inColumnMode: Boolean

    fun syncNavigationChrome(inColumnMode: Boolean)
}
