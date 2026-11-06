package com.nexustools.genericui.style.layout

/**
 * Adapter allowing [BrowserLayoutEngine] to traverse and inspect either GenericUI [com.nexustools.genericui.core.AbstractComponent]
 * trees or backend-specific native widget trees (`Win32WidgetNode`, `MacWidgetNode`, `GtkWidgetNode`,
 * `QtWidgetNode`, `SwingWidgetNode`, `AndroidViewHandle`, `IosNativeView`).
 */
interface LayoutNodeAdapter<N> {
    fun childrenOf(node: N): List<N>
    fun layoutStyleOf(node: N, viewportWidthPx: Int, viewportHeightPx: Int): LayoutStyleSnapshot
    fun tagNameOf(node: N): String
}
