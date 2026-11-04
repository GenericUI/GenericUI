package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.CollapsibleBottomDockContainer
import com.nexustools.genericui.component.layout.CollapsibleBottomDockLayout
import com.nexustools.genericui.component.layout.CollapsibleBottomDockLayoutImpl
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [LayoutFragment] hosting a [CollapsibleBottomDockLayout] with a primary content area and a persistent
 * collapsible bottom dock / mini-player bar.
 *
 * - Supports mounting either [AbstractComponent]s or child [Fragment]s for the main workspace and bottom dock.
 * - Intercepts [onBackPressed]: if the bottom dock is currently expanded (`!bottomDockLayout.isDockCollapsed`),
 *   collapses it to peek height first and returns `true`.
 * - Automatically forwards [onResize], [onConfigurationChanged], and [onPictureInPictureModeChanged]
 *   to [bottomDockLayout] and hosted child fragments.
 */
open class CollapsibleBottomDockFragment(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    id: String? = null
) : LayoutFragment<CollapsibleBottomDockLayoutImpl, CollapsibleBottomDockLayout>(id = id) {

    val bottomDockContainer: CollapsibleBottomDockContainer = CollapsibleBottomDockContainer(
        breakpointSpec = breakpointSpec
    )

    override val layout: CollapsibleBottomDockLayout = bottomDockContainer.layout

    val bottomDockLayout: CollapsibleBottomDockLayout
        get() = layout

    var mainFragment: Fragment? = null
        private set
    var dockFragment: Fragment? = null
        private set
    var expandedSheetFragment: Fragment? = null
        private set

    private var mainFragmentContainer: FragmentContainer? = null
    private var dockFragmentContainer: FragmentContainer? = null
    private var expandedSheetFragmentContainer: FragmentContainer? = null

    init {
        addChild(bottomDockContainer)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        if (!children.contains(bottomDockContainer)) {
            addChild(bottomDockContainer)
        }
        super.onCreate(savedState)
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            layout.updateViewport(winSize.width, winSize.height)
        }
    }

    /**
     * Mounts an [AbstractComponent] as the primary workspace content.
     */
    fun setMainContent(component: AbstractComponent): CollapsibleBottomDockFragment = apply {
        mainFragment = null
        mainFragmentContainer?.dispose()
        mainFragmentContainer = null
        layout.setMainContent(component)
    }

    /**
     * Mounts a child [Fragment] as the primary workspace content.
     */
    fun setMainFragment(fragment: Fragment): CollapsibleBottomDockFragment = apply {
        mainFragmentContainer?.dispose()
        val host = FragmentContainer(
            initialFragment = fragment,
            hostWindow = attachedWindow
        ).apply {
            hostingIWindow = hostingWindow
        }
        mainFragment = fragment
        mainFragmentContainer = host
        layout.setMainContent(host)
    }

    /**
     * Mounts the persistent bottom dock bar content ([component]) and optional [expandedSheetContent].
     */
    fun setDockContent(
        component: AbstractComponent?,
        expandedSheetContent: AbstractComponent? = null
    ): CollapsibleBottomDockFragment = apply {
        dockFragment = null
        expandedSheetFragment = null
        dockFragmentContainer?.dispose()
        expandedSheetFragmentContainer?.dispose()
        dockFragmentContainer = null
        expandedSheetFragmentContainer = null
        layout.setDockContent(component, expandedSheetContent)
    }

    /**
     * Mounts a child [Fragment] in the bottom dock bar and an optional [expandedSheet] fragment
     * displayed when the dock is expanded.
     */
    fun setDockFragment(
        fragment: Fragment?,
        expandedSheet: Fragment? = null
    ): CollapsibleBottomDockFragment = apply {
        dockFragmentContainer?.dispose()
        expandedSheetFragmentContainer?.dispose()

        val dockHost = if (fragment != null) {
            FragmentContainer(
                initialFragment = fragment,
                hostWindow = attachedWindow
            ).apply {
                hostingIWindow = hostingWindow
            }
        } else {
            null
        }
        val sheetHost = if (expandedSheet != null) {
            FragmentContainer(
                initialFragment = expandedSheet,
                hostWindow = attachedWindow
            ).apply {
                hostingIWindow = hostingWindow
            }
        } else {
            null
        }

        dockFragment = fragment
        expandedSheetFragment = expandedSheet
        dockFragmentContainer = dockHost
        expandedSheetFragmentContainer = sheetHost
        layout.setDockContent(dockHost, sheetHost)
    }

    fun expandDock() {
        layout.expandDock()
    }

    fun collapseDock() {
        layout.collapseDock()
    }

    fun toggleDockExpanded(): Boolean = layout.toggleDockExpanded()

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        mainFragment?.onResize(viewportWidth, viewportHeight)
        dockFragment?.onResize(viewportWidth, viewportHeight)
        expandedSheetFragment?.onResize(viewportWidth, viewportHeight)
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        mainFragment?.onConfigurationChanged(newConfig)
        dockFragment?.onConfigurationChanged(newConfig)
        expandedSheetFragment?.onConfigurationChanged(newConfig)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        layout.isInPictureInPictureMode = isInPictureInPictureMode
        mainFragment?.onPictureInPictureModeChanged(isInPictureInPictureMode)
        dockFragment?.onPictureInPictureModeChanged(isInPictureInPictureMode)
        expandedSheetFragment?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onBackPressed(): Boolean {
        if (layout.isDockCurrentlyVisible && !layout.isDockCollapsed) {
            layout.collapseDock()
            return true
        }
        if (mainFragment?.onBackPressed() == true) {
            return true
        }
        return super.onBackPressed()
    }
}
