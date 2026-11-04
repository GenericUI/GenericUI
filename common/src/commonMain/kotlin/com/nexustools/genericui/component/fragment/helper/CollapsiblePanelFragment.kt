package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.CollapsiblePanelLayout
import com.nexustools.genericui.component.layout.CollapsiblePanelLayoutImpl
import com.nexustools.genericui.component.surface.CollapsiblePanel
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [LayoutFragment] hosting a [CollapsiblePanel] / [CollapsiblePanelLayout].
 */
open class CollapsiblePanelFragment(
    title: String = "",
    expanded: Boolean = true,
    id: String? = null
) : LayoutFragment<CollapsiblePanelLayoutImpl, CollapsiblePanelLayout>(id = id) {

    val collapsiblePanel: CollapsiblePanel = CollapsiblePanel(
        title = title,
        expanded = expanded
    )

    override val layout: CollapsiblePanelLayout = collapsiblePanel.layout

    var contentFragment: Fragment? = null
        private set

    private var contentFragmentContainer: FragmentContainer? = null

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

    init {
        addChild(collapsiblePanel)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        if (!children.contains(collapsiblePanel)) {
            addChild(collapsiblePanel)
        }
        super.onCreate(savedState)
    }

    fun setContent(component: AbstractComponent?): CollapsiblePanelFragment = apply {
        contentFragment = null
        contentFragmentContainer?.dispose()
        contentFragmentContainer = null
        layout.clearBodyChildren()
        if (component != null) {
            layout.addBodyChild(component)
        }
    }

    fun setContentFragment(fragment: Fragment?): CollapsiblePanelFragment = apply {
        contentFragmentContainer?.dispose()
        val host = if (fragment != null) {
            FragmentContainer(
                initialFragment = fragment,
                hostWindow = attachedWindow
            ).apply {
                hostingIWindow = hostingWindow
            }
        } else {
            null
        }
        contentFragment = fragment
        contentFragmentContainer = host
        layout.clearBodyChildren()
        if (host != null) {
            layout.addBodyChild(host)
        }
    }

    fun toggle() {
        layout.toggle()
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        contentFragment?.onResize(viewportWidth, viewportHeight)
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        contentFragment?.onConfigurationChanged(newConfig)
    }
}
