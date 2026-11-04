package com.nexustools.genericui.component.surface

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.CollapsiblePanelLayout
import com.nexustools.genericui.component.layout.CollapsiblePanelLayoutImpl
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayoutContainer
import com.nexustools.genericui.core.AbstractComponent

/**
 * Expandable/collapsible surface panel (`<genericui:collapsiblepanel class="collapse">`) backed by [CollapsiblePanelLayout].
 */
open class CollapsiblePanel(
    title: String = "",
    expanded: Boolean = true
) : LayoutContainer<CollapsiblePanelLayoutImpl, CollapsiblePanelLayout>(
    "genericui:collapsiblepanel",
    "collapse"
) {
    override val layout: CollapsiblePanelLayout = CollapsiblePanelLayout(
        host = this,
        title = title,
        expanded = expanded
    )

    var title: String
        get() = layout.title
        set(value) {
            layout.title = value
        }

    var isExpanded: Boolean
        get() = layout.isExpanded
        set(value) {
            layout.isExpanded = value
        }

    var onExpandedChanged: ((Boolean) -> Unit)?
        get() = layout.onExpandedChanged
        set(value) {
            layout.onExpandedChanged = value
        }

    val headerButton: Button
        get() = layout.headerButton

    val bodyContainer: Container
        get() = layout.bodyContainer

    init {
        htmlTag = "genericui:collapsiblepanel"
        initializeLayout()
    }

    override fun addChild(child: AbstractComponent, index: Int) {
        val currentLayout: CollapsiblePanelLayout? = layout
        if (currentLayout == null || currentLayout.isStructuralChild(child)) {
            super.addChild(child, index)
        } else {
            currentLayout.addBodyChild(child)
        }
    }

    override fun removeChild(child: AbstractComponent): Boolean {
        val currentLayout: CollapsiblePanelLayout? = layout
        if (currentLayout == null || currentLayout.isStructuralChild(child)) {
            return super.removeChild(child)
        }
        return currentLayout.removeBodyChild(child)
    }

    override fun clearChildren() {
        layout.clearBodyChildren()
    }

    fun toggle() {
        layout.toggle()
    }
}
