package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.IWindow

/**
 * Target-group layout strategy created by [LayoutFactory] for a [Layout] helper.
 */
interface LayoutImpl : Disposable {
    val supportsDynamicViewportSwitching: Boolean
        get() = true

    val minRequiredWindowSize: Size?
        get() = null

    fun onAttachWindow(window: IWindow) {
        minRequiredWindowSize?.let { minSize ->
            val existing = window.getMinWindowSize()
            val merged = if (existing != null) {
                Size(maxOf(existing.width, minSize.width), maxOf(existing.height, minSize.height))
            } else {
                minSize
            }
            window.setMinWindowSize(merged)
        }
    }
}
