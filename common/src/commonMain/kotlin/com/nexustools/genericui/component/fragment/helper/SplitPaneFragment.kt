package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.SplitPaneLayout
import com.nexustools.genericui.component.layout.SplitPaneLayoutImpl
import com.nexustools.genericui.component.shell.SplitPane
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [LayoutFragment] hosting a [SplitPane] / [SplitPaneLayout] with component or child [Fragment] panes.
 */
open class SplitPaneFragment(
    orientation: Orientation = Orientation.HORIZONTAL,
    dividerPosition: Float = 0.5f,
    id: String? = null
) : LayoutFragment<SplitPaneLayoutImpl, SplitPaneLayout>(id = id) {

    val splitPane: SplitPane = SplitPane(
        orientation = orientation,
        dividerPosition = dividerPosition
    )

    override val layout: SplitPaneLayout = splitPane.layout

    var firstFragment: Fragment? = null
        private set
    var secondFragment: Fragment? = null
        private set

    private var firstFragmentContainer: FragmentContainer? = null
    private var secondFragmentContainer: FragmentContainer? = null

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

    init {
        addChild(splitPane)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        if (!children.contains(splitPane)) {
            addChild(splitPane)
        }
        super.onCreate(savedState)
    }

    fun setFirstPane(component: AbstractComponent?): SplitPaneFragment = apply {
        firstFragment = null
        firstFragmentContainer?.dispose()
        firstFragmentContainer = null
        layout.setFirstPane(component)
    }

    fun setSecondPane(component: AbstractComponent?): SplitPaneFragment = apply {
        secondFragment = null
        secondFragmentContainer?.dispose()
        secondFragmentContainer = null
        layout.setSecondPane(component)
    }

    fun setPanes(first: AbstractComponent, second: AbstractComponent): SplitPaneFragment = apply {
        setFirstPane(first)
        setSecondPane(second)
    }

    fun setFirstFragment(fragment: Fragment?): SplitPaneFragment = apply {
        firstFragmentContainer?.dispose()
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
        firstFragment = fragment
        firstFragmentContainer = host
        layout.setFirstPane(host)
    }

    fun setSecondFragment(fragment: Fragment?): SplitPaneFragment = apply {
        secondFragmentContainer?.dispose()
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
        secondFragment = fragment
        secondFragmentContainer = host
        layout.setSecondPane(host)
    }

    fun setFragments(first: Fragment, second: Fragment): SplitPaneFragment = apply {
        setFirstFragment(first)
        setSecondFragment(second)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        firstFragment?.onResize(viewportWidth, viewportHeight)
        secondFragment?.onResize(viewportWidth, viewportHeight)
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        firstFragment?.onConfigurationChanged(newConfig)
        secondFragment?.onConfigurationChanged(newConfig)
    }
}
