package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection

/**
 * Expandable/collapsible surface panel layout helper (`CollapsiblePanelLayout`) managing a header button
 * and collapsible body container.
 */
open class CollapsiblePanelLayout(
    host: AbstractContainer = Container("genericui:collapsiblepanel", "collapse"),
    title: String = "",
    expanded: Boolean = true,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<CollapsiblePanelLayoutImpl>(host, breakpointSpec) {

    constructor(
        title: String,
        expanded: Boolean = true,
        breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
    ) : this(
        host = Container("genericui:collapsiblepanel", "collapse"),
        title = title,
        expanded = expanded,
        breakpointSpec = breakpointSpec
    )

    val headerButton: Button = Button(title).apply {
        addClass("gui-collapsible-panel-header")
    }
    val bodyContainer: Container = Container("div", "gui-collapsible-panel-body")

    var title: String = title
        set(value) {
            field = value
            headerButton.text = value
            headerButton.isVisible = value.isNotEmpty()
        }

    var isExpanded: Boolean = expanded
        set(value) {
            val changed = (field != value)
            field = value
            if (isMounted) {
                impl.syncExpandedState()
            }
            if (changed) {
                onExpandedChanged?.invoke(value)
            }
        }

    var onExpandedChanged: ((Boolean) -> Unit)? = null

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): CollapsiblePanelLayoutImpl =
        factory.createCollapsiblePanelLayout(this)

    override fun onMount() {
        host.addClass("collapse")
        host.setDisplay(DisplayMode.FLEX)
        host.setFlexDirection(FlexDirection.COLUMN)

        headerButton.text = title
        headerButton.isVisible = title.isNotEmpty()
        headerButton.onClick = { toggle() }

        if (!host.children.contains(headerButton)) {
            host.addChild(headerButton)
        }
        if (!host.children.contains(bodyContainer)) {
            host.addChild(bodyContainer)
        }
        impl.syncExpandedState()
    }

    fun toggle() {
        isExpanded = !isExpanded
    }

    fun addBodyChild(child: AbstractComponent) {
        bodyContainer.addChild(child)
    }

    fun removeBodyChild(child: AbstractComponent): Boolean =
        bodyContainer.removeChild(child)

    fun clearBodyChildren() {
        bodyContainer.clearChildren()
    }

    internal fun isStructuralChild(child: AbstractComponent): Boolean =
        child === headerButton || child === bodyContainer

    /**
     * Synchronizes visibility and `.show` / `.collapsed` classes on [host], [headerButton], and [bodyContainer].
     */
    fun applyExpandedState() {
        bodyContainer.isVisible = isExpanded
        if (isExpanded) {
            host.addClass("show")
            host.removeClass("collapsed")
            headerButton.removeClass("collapsed")
            bodyContainer.addClass("show")
        } else {
            host.removeClass("show")
            host.addClass("collapsed")
            headerButton.addClass("collapsed")
            bodyContainer.removeClass("show")
        }
    }
}
