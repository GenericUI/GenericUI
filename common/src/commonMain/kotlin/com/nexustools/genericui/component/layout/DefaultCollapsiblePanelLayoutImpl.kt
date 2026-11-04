package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

/**
 * Default [CollapsiblePanelLayoutImpl] synchronizing header button and collapsible body visibility.
 */
open class DefaultCollapsiblePanelLayoutImpl(
    override val layout: CollapsiblePanelLayout
) : CollapsiblePanelLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val supportsDynamicViewportSwitching: Boolean = true
    override val minRequiredWindowSize: Size? = null

    override fun syncExpandedState() {
        layout.applyExpandedState()
    }

    override fun dispose() {
        isDisposed = true
    }
}
