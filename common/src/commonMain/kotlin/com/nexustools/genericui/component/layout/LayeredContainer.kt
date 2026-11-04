package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent

/**
 * [LayoutContainer] hosting a [LayeredLayout] that stacks child components in fill or floating layers.
 */
open class LayeredContainer(
    tagName: String = "genericui:layered-container",
    classPrefix: String = "gui-layered-container",
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<LayeredLayoutImpl, LayeredLayout>(tagName, classPrefix) {

    override val layout: LayeredLayout = LayeredLayout(
        host = this,
        breakpointSpec = breakpointSpec
    ).also { it.ensureMounted() }

    val layers: List<AbstractComponent>
        get() = layout.layers

    val layerEntries: List<Pair<AbstractComponent, LayerConstraint>>
        get() = layout.layerEntries

    init {
        htmlTag = tagName
        initializeLayout()
    }

    override fun addChild(child: AbstractComponent, index: Int) {
        if (layout.getLayerConstraint(child) == null) {
            layout.registerDefaultConstraintIfAbsent(child)
        }
        super.addChild(child, index)
    }

    fun addLayer(
        component: AbstractComponent,
        constraint: LayerConstraint = LayerConstraint.fill()
    ): AbstractComponent = layout.addLayer(component, constraint)

    fun addFillLayer(
        component: AbstractComponent,
        zIndex: Int = 0,
        passThroughPointerEvents: Boolean = false
    ): AbstractComponent = layout.addFillLayer(
        component = component,
        zIndex = zIndex,
        passThroughPointerEvents = passThroughPointerEvents
    )

    fun addFloatingLayer(
        component: AbstractComponent,
        alignment: LayerAlignment = LayerAlignment.TOP_START,
        offsetXPx: Int = 0,
        offsetYPx: Int = 0,
        leftPercent: Float? = null,
        topPercent: Float? = null,
        rightPx: Int? = null,
        bottomPx: Int? = null,
        zIndex: Int = 10,
        passThroughPointerEvents: Boolean = false
    ): AbstractComponent = layout.addFloatingLayer(
        component = component,
        alignment = alignment,
        offsetXPx = offsetXPx,
        offsetYPx = offsetYPx,
        leftPercent = leftPercent,
        topPercent = topPercent,
        rightPx = rightPx,
        bottomPx = bottomPx,
        zIndex = zIndex,
        passThroughPointerEvents = passThroughPointerEvents
    )

    fun setLayerConstraint(component: AbstractComponent, constraint: LayerConstraint) {
        layout.setLayerConstraint(component, constraint)
    }

    fun getLayerConstraint(component: AbstractComponent): LayerConstraint? =
        layout.getLayerConstraint(component)

    fun removeLayer(component: AbstractComponent) {
        layout.removeLayer(component)
    }

    fun clearLayers() {
        layout.clearLayers()
    }
}
