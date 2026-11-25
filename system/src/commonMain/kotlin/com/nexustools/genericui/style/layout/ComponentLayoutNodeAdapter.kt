package com.nexustools.genericui.style.layout

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer

/**
 * [LayoutNodeAdapter] implementation that operates directly on GenericUI [AbstractComponent] hierarchies.
 */
class ComponentLayoutNodeAdapter : LayoutNodeAdapter<AbstractComponent> {

    override fun childrenOf(node: AbstractComponent): List<AbstractComponent> = when (node) {
        is AbstractContainer -> node.children.filter { !it.mountedInTitleBar }
        is Composite -> node.internalCompositeChildren().filter { !it.mountedInTitleBar }
        else -> emptyList()
    }

    override fun layoutStyleOf(
        node: AbstractComponent,
        viewportWidthPx: Int,
        viewportHeightPx: Int
    ): LayoutStyleSnapshot = LayoutStyleSnapshot.fromComponent(
        component = node,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx
    )

    override fun tagNameOf(node: AbstractComponent): String = node.htmlTag
}
