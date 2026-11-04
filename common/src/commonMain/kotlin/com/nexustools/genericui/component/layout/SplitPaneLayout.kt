package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.ui.Orientation
import kotlin.math.roundToInt

/**
 * Resizable 2-pane split layout helper (`SplitPaneLayout`) managing a primary pane, a divider bar,
 * and a secondary pane in either [Orientation.HORIZONTAL] or [Orientation.VERTICAL] orientation.
 */
open class SplitPaneLayout(
    host: AbstractContainer = Container("genericui:splitpane", "split-pane"),
    orientation: Orientation = Orientation.HORIZONTAL,
    dividerPosition: Float = 0.5f,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<SplitPaneLayoutImpl>(host, breakpointSpec) {

    constructor(
        orientation: Orientation,
        dividerPosition: Float = 0.5f,
        breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
    ) : this(
        host = Container("genericui:splitpane", "split-pane"),
        orientation = orientation,
        dividerPosition = dividerPosition,
        breakpointSpec = breakpointSpec
    )

    val firstPaneHost: Container = Container("div", "gui-split-pane-first")
    val dividerBar: Container = Container("div", "gui-split-pane-divider")
    val secondPaneHost: Container = Container("div", "gui-split-pane-second")

    var orientation: Orientation = orientation
        set(value) {
            if (field != value) {
                field = value
                if (isMounted) impl.syncSplitPane()
            }
        }

    var dividerPosition: Float = dividerPosition.coerceIn(0.05f, 0.95f)
        set(value) {
            val clamped = value.coerceIn(0.05f, 0.95f)
            val changed = (field != clamped)
            field = clamped
            if (isMounted) impl.syncSplitPane()
            if (changed) {
                onDividerPositionChanged?.invoke(clamped)
            }
        }

    var onDividerPositionChanged: ((Float) -> Unit)? = null

    val firstPane: AbstractComponent?
        get() = firstPaneHost.children.firstOrNull()

    val secondPane: AbstractComponent?
        get() = secondPaneHost.children.firstOrNull()

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): SplitPaneLayoutImpl =
        factory.createSplitPaneLayout(this)

    override fun onMount() {
        host.addClass("split-pane")
        host.setDisplay(DisplayMode.FLEX)
        if (!host.children.contains(firstPaneHost)) {
            host.addChild(firstPaneHost)
        }
        if (!host.children.contains(dividerBar)) {
            host.addChild(dividerBar)
        }
        if (!host.children.contains(secondPaneHost)) {
            host.addChild(secondPaneHost)
        }
        impl.syncSplitPane()
    }

    fun setFirstPane(component: AbstractComponent?): SplitPaneLayout = apply {
        firstPaneHost.clearChildren()
        if (component != null) {
            firstPaneHost.addChild(component)
        }
        impl.syncSplitPane()
    }

    fun setSecondPane(component: AbstractComponent?): SplitPaneLayout = apply {
        secondPaneHost.clearChildren()
        if (component != null) {
            secondPaneHost.addChild(component)
        }
        impl.syncSplitPane()
    }

    fun setPanes(first: AbstractComponent, second: AbstractComponent): SplitPaneLayout = apply {
        firstPaneHost.clearChildren()
        secondPaneHost.clearChildren()
        firstPaneHost.addChild(first)
        secondPaneHost.addChild(second)
        impl.syncSplitPane()
    }

    fun clearPanes() {
        firstPaneHost.clearChildren()
        secondPaneHost.clearChildren()
        impl.syncSplitPane()
    }

    internal fun isStructuralChild(child: AbstractComponent): Boolean =
        child === firstPaneHost || child === dividerBar || child === secondPaneHost

    /**
     * Synchronizes flex direction, classes, and percentage sizing for [firstPaneHost], [dividerBar], and [secondPaneHost].
     */
    fun applySplitPaneState() {
        val firstPct = (dividerPosition * 100f).roundToInt().coerceIn(5, 95)
        val secondPct = (100 - firstPct).coerceIn(5, 95)

        if (orientation == Orientation.HORIZONTAL) {
            host.setFlexDirection(FlexDirection.ROW)
            host.addClass("gui-split-pane-horizontal")
            host.removeClass("gui-split-pane-vertical")
            firstPaneHost.setStyleProperty("width", "$firstPct%")
            firstPaneHost.setStyleProperty("height", "100%")
            secondPaneHost.setStyleProperty("width", "$secondPct%")
            secondPaneHost.setStyleProperty("height", "100%")
        } else {
            host.setFlexDirection(FlexDirection.COLUMN)
            host.addClass("gui-split-pane-vertical")
            host.removeClass("gui-split-pane-horizontal")
            firstPaneHost.setStyleProperty("width", "100%")
            firstPaneHost.setStyleProperty("height", "$firstPct%")
            secondPaneHost.setStyleProperty("width", "100%")
            secondPaneHost.setStyleProperty("height", "$secondPct%")
        }
        firstPaneHost.setStyleProperty("flex-grow", dividerPosition.toString())
        secondPaneHost.setStyleProperty("flex-grow", (1f - dividerPosition).toString())
    }
}
