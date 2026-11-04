package com.nexustools.genericui.component.layout

import com.nexustools.genericui.enums.bootstrap.PositionMode

/**
 * Default [LayeredLayoutImpl] shared across Web, Desktop, Mobile, and headless targets.
 */
open class DefaultLayeredLayoutImpl(
    override val layout: LayeredLayout
) : LayeredLayoutImpl {

    override var isDisposed: Boolean = false

    override fun dispose() {
        isDisposed = true
    }

    override fun syncLayers() {
        layout.syncHostStyle()
        for ((child, constraint) in layout.layerEntries) {
            layout.applyConstraintToComponent(child, constraint)
        }
    }
}
