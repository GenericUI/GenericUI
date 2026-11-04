package com.nexustools.genericui.component.shell

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayoutContainer
import com.nexustools.genericui.component.layout.SplitPaneLayout
import com.nexustools.genericui.component.layout.SplitPaneLayoutImpl
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.ui.Orientation

/**
 * Resizable 2-pane split container (`<genericui:splitpane>`) backed by [SplitPaneLayout].
 */
open class SplitPane(
    orientation: Orientation = Orientation.HORIZONTAL,
    dividerPosition: Float = 0.5f
) : LayoutContainer<SplitPaneLayoutImpl, SplitPaneLayout>("genericui:splitpane", "split-pane") {

    override val layout: SplitPaneLayout = SplitPaneLayout(
        host = this,
        orientation = orientation,
        dividerPosition = dividerPosition
    )

    var orientation: Orientation
        get() = layout.orientation
        set(value) {
            layout.orientation = value
        }

    var dividerPosition: Float
        get() = layout.dividerPosition
        set(value) {
            layout.dividerPosition = value
        }

    var onDividerPositionChanged: ((Float) -> Unit)?
        get() = layout.onDividerPositionChanged
        set(value) {
            layout.onDividerPositionChanged = value
        }

    val firstPaneHost: Container
        get() = layout.firstPaneHost

    val dividerBar: Container
        get() = layout.dividerBar

    val secondPaneHost: Container
        get() = layout.secondPaneHost

    val firstPane: AbstractComponent?
        get() = layout.firstPane

    val secondPane: AbstractComponent?
        get() = layout.secondPane

    init {
        htmlTag = "genericui:splitpane"
        initializeLayout()
    }

    override fun addChild(child: AbstractComponent, index: Int) {
        val currentLayout: SplitPaneLayout? = layout
        if (currentLayout == null || currentLayout.isStructuralChild(child)) {
            super.addChild(child, index)
            return
        }
        when {
            currentLayout.firstPane == null -> currentLayout.setFirstPane(child)
            currentLayout.secondPane == null -> currentLayout.setSecondPane(child)
            else -> currentLayout.secondPaneHost.addChild(child, index)
        }
    }

    override fun clearChildren() {
        layout.clearPanes()
    }

    fun setFirstPane(component: AbstractComponent?): SplitPane = apply {
        layout.setFirstPane(component)
    }

    fun setSecondPane(component: AbstractComponent?): SplitPane = apply {
        layout.setSecondPane(component)
    }

    fun setPanes(first: AbstractComponent, second: AbstractComponent): SplitPane = apply {
        layout.setPanes(first, second)
    }
}
