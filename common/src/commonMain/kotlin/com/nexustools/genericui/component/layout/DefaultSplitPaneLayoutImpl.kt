package com.nexustools.genericui.component.layout

import com.nexustools.genericui.geometry.Size

/**
 * Default [SplitPaneLayoutImpl] synchronizing horizontal/vertical flex panes and divider ratio.
 */
open class DefaultSplitPaneLayoutImpl(
    override val layout: SplitPaneLayout
) : SplitPaneLayoutImpl {

    override var isDisposed: Boolean = false
        protected set

    override val supportsDynamicViewportSwitching: Boolean = true
    override val minRequiredWindowSize: Size? = null

    override fun syncSplitPane() {
        layout.applySplitPaneState()
    }

    override fun dispose() {
        isDisposed = true
    }
}
