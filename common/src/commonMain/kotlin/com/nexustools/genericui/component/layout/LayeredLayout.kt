package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.transition.CssTransform2D

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
        val currentPos = host.getStyle().getPosition()
        if (isAspectRatioChild) {
            if (host.positionMode == PositionMode.RELATIVE) {
                host.positionMode = null
            }
            host.getStyle().apply {
                setPosition(CssPosition.ABSOLUTE)
                setTop(CssLength.ZERO)
                setLeft(CssLength.ZERO)
                setWidth(CssLength.PERCENT_100)
                setHeight(CssLength.PERCENT_100)
            }
        } else if (
            isLayeredChild ||
            currentPos == CssPosition.ABSOLUTE ||
            currentPos == CssPosition.FIXED ||
            host.positionMode == PositionMode.ABSOLUTE ||
            host.positionMode == PositionMode.FIXED
        ) {
            if (host.positionMode == PositionMode.RELATIVE) {
                host.positionMode = null
            }
            if (currentPos == null || currentPos == CssPosition.STATIC) {
                host.getStyle().setPosition(CssPosition.ABSOLUTE)
            }
            if (host.getStyle().getWidth() == null) {
                host.getStyle().setWidth(CssLength.PERCENT_100)
            }
            if (host.getStyle().getHeight() == null) {
                host.getStyle().setHeight(CssLength.PERCENT_100)
            }
        } else {
            if (currentPos == null || currentPos == CssPosition.STATIC) {
                host.getStyle().setPosition(CssPosition.RELATIVE)
            }
            if (host.getStyle().getWidth() == null) {
                host.getStyle().setWidth(CssLength.PERCENT_100)
            }
            if (host.getStyle().getHeight() == null) {
                host.getStyle().setHeight(CssLength.PERCENT_100)
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
        child.getStyle().setPosition(CssPosition.ABSOLUTE)
        child.zIndex = constraint.zIndex
        if (constraint.passThroughPointerEvents) {
            child.addClass("gui-layer-pass-through")
            child.pointerEventsEnabled = false
            child.getStyle().setPointerEvents(CssPointerEvents.NONE)
        } else {
            child.removeClass("gui-layer-pass-through")
            child.pointerEventsEnabled = true
            child.getStyle().setPointerEvents(CssPointerEvents.AUTO)
        }

        if (constraint.sizingMode == LayerSizingMode.FILL) {
            val wasFloat = child.hasClass("gui-layer-fill").not() && child.hasClass("gui-layer-float")
            child.addClass("gui-layer-fill")
            child.removeClass("gui-layer-float")
            child.getStyle().apply {
                setTop(CssLength.px(constraint.offsetYPx))
                setLeft(CssLength.px(constraint.offsetXPx))
                setWidth(CssLength.PERCENT_100)
                setHeight(CssLength.PERCENT_100)
                setRight(null)
                setBottom(null)
                if (wasFloat) {
                    setTransform(null)
                }
            }
        } else {
            child.addClass("gui-layer-float")
            child.removeClass("gui-layer-fill")

            if (constraint.leftPercent != null) {
                val pct = constraint.leftPercent.coerceIn(0f, 100f)
                val leftLen = if (constraint.offsetXPx != 0) {
                    CssLength.calcPercentPx(pct, constraint.offsetXPx.toFloat())
                } else {
                    CssLength.percent(pct)
                }
                child.getStyle().setLeft(leftLen)
                child.getStyle().setRight(null)
            } else if (constraint.rightPx != null) {
                child.getStyle().setRight(CssLength.px(constraint.rightPx - constraint.offsetXPx))
                child.getStyle().setLeft(null)
            } else {
                when (constraint.alignment) {
                    LayerAlignment.TOP_START,
                    LayerAlignment.CENTER_START,
                    LayerAlignment.BOTTOM_START -> {
                        child.getStyle().setLeft(CssLength.px(constraint.offsetXPx))
                        child.getStyle().setRight(null)
                    }
                    LayerAlignment.TOP_CENTER,
                    LayerAlignment.CENTER,
                    LayerAlignment.BOTTOM_CENTER -> {
                        val leftLen = if (constraint.offsetXPx != 0) {
                            CssLength.calcPercentPx(50f, constraint.offsetXPx.toFloat())
                        } else {
                            CssLength.PERCENT_50
                        }
                        child.getStyle().setLeft(leftLen)
                        child.getStyle().setRight(null)
                    }
                    LayerAlignment.TOP_END,
                    LayerAlignment.CENTER_END,
                    LayerAlignment.BOTTOM_END -> {
                        child.getStyle().setRight(CssLength.px(-constraint.offsetXPx))
                        child.getStyle().setLeft(null)
                    }
                }
            }

            if (constraint.topPercent != null) {
                val pct = constraint.topPercent.coerceIn(0f, 100f)
                val topLen = if (constraint.offsetYPx != 0) {
                    CssLength.calcPercentPx(pct, constraint.offsetYPx.toFloat())
                } else {
                    CssLength.percent(pct)
                }
                child.getStyle().setTop(topLen)
                child.getStyle().setBottom(null)
            } else if (constraint.bottomPx != null) {
                child.getStyle().setBottom(CssLength.px(constraint.bottomPx - constraint.offsetYPx))
                child.getStyle().setTop(null)
            } else {
                when (constraint.alignment) {
                    LayerAlignment.TOP_START,
                    LayerAlignment.TOP_CENTER,
                    LayerAlignment.TOP_END -> {
                        child.getStyle().setTop(CssLength.px(constraint.offsetYPx))
                        child.getStyle().setBottom(null)
                    }
                    LayerAlignment.CENTER_START,
                    LayerAlignment.CENTER,
                    LayerAlignment.CENTER_END -> {
                        val topLen = if (constraint.offsetYPx != 0) {
                            CssLength.calcPercentPx(50f, constraint.offsetYPx.toFloat())
                        } else {
                            CssLength.PERCENT_50
                        }
                        child.getStyle().setTop(topLen)
                        child.getStyle().setBottom(null)
                    }
                    LayerAlignment.BOTTOM_START,
                    LayerAlignment.BOTTOM_CENTER,
                    LayerAlignment.BOTTOM_END -> {
                        child.getStyle().setBottom(CssLength.px(-constraint.offsetYPx))
                        child.getStyle().setTop(null)
                    }
                }
            }

            val txPct = when (constraint.alignment) {
                LayerAlignment.TOP_CENTER,
                LayerAlignment.CENTER,
                LayerAlignment.BOTTOM_CENTER -> -50f
                LayerAlignment.TOP_END,
                LayerAlignment.CENTER_END,
                LayerAlignment.BOTTOM_END -> if (constraint.leftPercent != null) -100f else 0f
                else -> 0f
            }
            val tyPct = when (constraint.alignment) {
                LayerAlignment.CENTER_START,
                LayerAlignment.CENTER,
                LayerAlignment.CENTER_END -> -50f
                LayerAlignment.BOTTOM_START,
                LayerAlignment.BOTTOM_CENTER,
                LayerAlignment.BOTTOM_END -> if (constraint.topPercent != null) -100f else 0f
                else -> 0f
            }
            if (txPct != 0f || tyPct != 0f) {
                child.getStyle().setTransform(
                    CssTransform2D(
                        translateX = txPct,
                        translateXIsPercent = txPct != 0f,
                        translateY = tyPct,
                        translateYIsPercent = tyPct != 0f
                    )
                )
            } else {
                child.getStyle().setTransform(null)
            }
        }
    }
}
