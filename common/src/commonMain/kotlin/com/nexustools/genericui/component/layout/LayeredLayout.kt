package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.PositionMode

/**
 * Composite layout helper (`LayeredLayout`) that stacks child components in layers inside [host]
 * with per-child [LayerConstraint] rules ([LayerSizingMode.FILL] vs [LayerSizingMode.FLOAT]).
 */
open class LayeredLayout(
    host: AbstractContainer = Container("genericui:layered-container", "gui-layered-container"),
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<LayeredLayoutImpl>(host, breakpointSpec) {

    private val constraintsByChild = linkedMapOf<AbstractComponent, LayerConstraint>()

    val layers: List<AbstractComponent>
        get() = host.children.toList()

    val layerEntries: List<Pair<AbstractComponent, LayerConstraint>>
        get() = host.children.map { it to (constraintsByChild[it] ?: LayerConstraint.fill()) }

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): LayeredLayoutImpl =
        factory.createLayeredLayout(this)

    override fun onMount() {
        syncHostStyle()
        impl.syncLayers()
    }

    internal fun syncHostStyle() {
        host.addClass("gui-layered-container")
        val parent = host.parent
        val isAspectRatioChild = parent is AspectRatioContainer || parent?.hasClass("ratio") == true
        val isLayeredChild = parent is LayeredContainer || host.hasClass("gui-layer-item")
        val currentPos = host.getStyleProperty("position")?.trim()?.lowercase()
        if (isAspectRatioChild) {
            if (host.positionMode == PositionMode.RELATIVE) {
                host.positionMode = null
            }
            host.setStyleProperty("position", "absolute")
            host.setStyleProperty("top", "0")
            host.setStyleProperty("left", "0")
            host.setStyleProperty("width", "100%")
            host.setStyleProperty("height", "100%")
        } else if (
            isLayeredChild ||
            currentPos == "absolute" ||
            currentPos == "fixed" ||
            host.positionMode == PositionMode.ABSOLUTE ||
            host.positionMode == PositionMode.FIXED
        ) {
            if (host.positionMode == PositionMode.RELATIVE) {
                host.positionMode = null
            }
            if (currentPos == null || currentPos == "static") {
                host.setStyleProperty("position", "absolute")
            }
            if (host.getStyleProperty("width") == null) {
                host.setStyleProperty("width", "100%")
            }
            if (host.getStyleProperty("height") == null) {
                host.setStyleProperty("height", "100%")
            }
        } else {
            if (currentPos == null || currentPos == "static") {
                host.setStyleProperty("position", "relative")
            }
            if (host.getStyleProperty("width") == null) {
                host.setStyleProperty("width", "100%")
            }
            if (host.getStyleProperty("height") == null) {
                host.setStyleProperty("height", "100%")
            }
        }
    }

    fun addLayer(
        component: AbstractComponent,
        constraint: LayerConstraint = LayerConstraint.fill()
    ): AbstractComponent {
        constraintsByChild[component] = constraint
        applyConstraintToComponent(component, constraint)
        if (!host.children.contains(component)) {
            host.addChild(component)
        }
        impl.syncLayers()
        return component
    }

    fun addFillLayer(
        component: AbstractComponent,
        zIndex: Int = 0,
        passThroughPointerEvents: Boolean = false
    ): AbstractComponent = addLayer(
        component = component,
        constraint = LayerConstraint.fill(
            zIndex = zIndex,
            passThroughPointerEvents = passThroughPointerEvents
        )
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
    ): AbstractComponent = addLayer(
        component = component,
        constraint = LayerConstraint.float(
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
    )

    fun setLayerConstraint(component: AbstractComponent, constraint: LayerConstraint) {
        constraintsByChild[component] = constraint
        applyConstraintToComponent(component, constraint)
        if (isMounted) {
            impl.syncLayers()
        }
    }

    fun getLayerConstraint(component: AbstractComponent): LayerConstraint? =
        constraintsByChild[component]

    fun removeLayer(component: AbstractComponent) {
        constraintsByChild.remove(component)
        host.removeChild(component)
        if (isMounted) {
            impl.syncLayers()
        }
    }

    fun clearLayers() {
        constraintsByChild.clear()
        host.clearChildren()
        if (isMounted) {
            impl.syncLayers()
        }
    }

    internal fun registerDefaultConstraintIfAbsent(child: AbstractComponent) {
        if (!constraintsByChild.containsKey(child)) {
            val defaultConstraint = LayerConstraint.fill()
            constraintsByChild[child] = defaultConstraint
            applyConstraintToComponent(child, defaultConstraint)
        }
    }

    internal fun applyConstraintToComponent(child: AbstractComponent, constraint: LayerConstraint) {
        child.setAttribute("layerSizingMode", constraint.sizingMode.name)
        child.setAttribute("layerAlignment", constraint.alignment.name)
        child.setAttribute("layerOffsetXPx", constraint.offsetXPx.toString())
        child.setAttribute("layerOffsetYPx", constraint.offsetYPx.toString())
        child.setAttribute("layerLeftPercent", constraint.leftPercent?.toString() ?: "")
        child.setAttribute("layerTopPercent", constraint.topPercent?.toString() ?: "")
        child.setAttribute("layerRightPx", constraint.rightPx?.toString() ?: "")
        child.setAttribute("layerBottomPx", constraint.bottomPx?.toString() ?: "")
        child.setAttribute("layerZIndex", constraint.zIndex.toString())
        child.setAttribute("layerPassThrough", constraint.passThroughPointerEvents.toString())

        child.addClass("gui-layer-item")
        child.setStyleProperty("position", "absolute")
        child.zIndex = constraint.zIndex
        child.setStyleProperty("z-index", constraint.zIndex.toString())
        if (constraint.passThroughPointerEvents) {
            child.addClass("gui-layer-pass-through")
            child.pointerEventsEnabled = false
            child.setStyleProperty("pointer-events", "none")
        } else {
            child.removeClass("gui-layer-pass-through")
            child.pointerEventsEnabled = true
            child.setStyleProperty("pointer-events", "auto")
        }

        if (constraint.sizingMode == LayerSizingMode.FILL) {
            val wasFloat = child.hasClass("gui-layer-float")
            child.addClass("gui-layer-fill")
            child.removeClass("gui-layer-float")
            child.setStyleProperty("inset", "0")
            child.setStyleProperty("top", "${constraint.offsetYPx}px")
            child.setStyleProperty("left", "${constraint.offsetXPx}px")
            child.setStyleProperty("width", "100%")
            child.setStyleProperty("height", "100%")
            child.removeStyleProperty("right")
            child.removeStyleProperty("bottom")
            if (wasFloat) {
                child.removeStyleProperty("transform")
            }
        } else {
            child.addClass("gui-layer-float")
            child.removeClass("gui-layer-fill")
            child.removeStyleProperty("inset")

            if (constraint.leftPercent != null) {
                val pct = constraint.leftPercent.coerceIn(0f, 100f)
                val leftExpr = if (constraint.offsetXPx != 0) {
                    "calc(${pct}% + ${constraint.offsetXPx}px)"
                } else {
                    "${pct}%"
                }
                child.setStyleProperty("left", leftExpr)
                child.removeStyleProperty("right")
            } else if (constraint.rightPx != null) {
                child.setStyleProperty("right", "${constraint.rightPx - constraint.offsetXPx}px")
                child.removeStyleProperty("left")
            } else {
                when (constraint.alignment) {
                    LayerAlignment.TOP_START,
                    LayerAlignment.CENTER_START,
                    LayerAlignment.BOTTOM_START -> {
                        child.setStyleProperty("left", "${constraint.offsetXPx}px")
                        child.removeStyleProperty("right")
                    }
                    LayerAlignment.TOP_CENTER,
                    LayerAlignment.CENTER,
                    LayerAlignment.BOTTOM_CENTER -> {
                        val leftExpr = if (constraint.offsetXPx != 0) {
                            "calc(50% + ${constraint.offsetXPx}px)"
                        } else {
                            "50%"
                        }
                        child.setStyleProperty("left", leftExpr)
                        child.removeStyleProperty("right")
                    }
                    LayerAlignment.TOP_END,
                    LayerAlignment.CENTER_END,
                    LayerAlignment.BOTTOM_END -> {
                        child.setStyleProperty("right", "${-constraint.offsetXPx}px")
                        child.removeStyleProperty("left")
                    }
                }
            }

            if (constraint.topPercent != null) {
                val pct = constraint.topPercent.coerceIn(0f, 100f)
                val topExpr = if (constraint.offsetYPx != 0) {
                    "calc(${pct}% + ${constraint.offsetYPx}px)"
                } else {
                    "${pct}%"
                }
                child.setStyleProperty("top", topExpr)
                child.removeStyleProperty("bottom")
            } else if (constraint.bottomPx != null) {
                child.setStyleProperty("bottom", "${constraint.bottomPx - constraint.offsetYPx}px")
                child.removeStyleProperty("top")
            } else {
                when (constraint.alignment) {
                    LayerAlignment.TOP_START,
                    LayerAlignment.TOP_CENTER,
                    LayerAlignment.TOP_END -> {
                        child.setStyleProperty("top", "${constraint.offsetYPx}px")
                        child.removeStyleProperty("bottom")
                    }
                    LayerAlignment.CENTER_START,
                    LayerAlignment.CENTER,
                    LayerAlignment.CENTER_END -> {
                        val topExpr = if (constraint.offsetYPx != 0) {
                            "calc(50% + ${constraint.offsetYPx}px)"
                        } else {
                            "50%"
                        }
                        child.setStyleProperty("top", topExpr)
                        child.removeStyleProperty("bottom")
                    }
                    LayerAlignment.BOTTOM_START,
                    LayerAlignment.BOTTOM_CENTER,
                    LayerAlignment.BOTTOM_END -> {
                        child.setStyleProperty("bottom", "${-constraint.offsetYPx}px")
                        child.removeStyleProperty("top")
                    }
                }
            }

            val tx = when (constraint.alignment) {
                LayerAlignment.TOP_CENTER,
                LayerAlignment.CENTER,
                LayerAlignment.BOTTOM_CENTER -> "-50%"
                LayerAlignment.TOP_END,
                LayerAlignment.CENTER_END,
                LayerAlignment.BOTTOM_END -> if (constraint.leftPercent != null) "-100%" else "0%"
                else -> "0%"
            }
            val ty = when (constraint.alignment) {
                LayerAlignment.CENTER_START,
                LayerAlignment.CENTER,
                LayerAlignment.CENTER_END -> "-50%"
                LayerAlignment.BOTTOM_START,
                LayerAlignment.BOTTOM_CENTER,
                LayerAlignment.BOTTOM_END -> if (constraint.topPercent != null) "-100%" else "0%"
                else -> "0%"
            }
            if (tx != "0%" || ty != "0%") {
                child.setStyleProperty("transform", "translate($tx, $ty)")
            } else {
                child.removeStyleProperty("transform")
            }
        }
    }
}
