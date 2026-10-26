package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.CollapsibleBottomDockLayout
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] hosting a [CollapsibleBottomDockLayout] with a primary content area and a persistent
 * collapsible bottom dock / mini-player bar.
 *
 * - Supports mounting either [AbstractComponent]s or child [Fragment]s for the main workspace and bottom dock.
 * - Intercepts [onBackPressed]: if the bottom dock is currently expanded (`!bottomDockLayout.isDockCollapsed`),
 *   collapses it to peek height first and returns `true`.
 * - Automatically forwards [onResize], [onConfigurationChanged], and [onPictureInPictureModeChanged]
 *   to [bottomDockLayout] and hosted child fragments.
 */
open class CollapsibleBottomDockFragment(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Fragment() {

    val bottomDockLayout: CollapsibleBottomDockLayout = CollapsibleBottomDockLayout(
        breakpointSpec = breakpointSpec
    )

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
        addChild(bottomDockLayout)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (!children.contains(bottomDockLayout)) {
            addChild(bottomDockLayout)
        }
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            bottomDockLayout.updateViewport(winSize.width, winSize.height)
        }
    }

    /**
     * Mounts an [AbstractComponent] as the primary workspace content.
     */
    fun setMainContent(component: AbstractComponent): CollapsibleBottomDockFragment = apply {
        mainFragment = null
        mainFragmentContainer?.dispose()
        mainFragmentContainer = null
        bottomDockLayout.setMainContent(component)
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
        bottomDockLayout.setMainContent(host)
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
        bottomDockLayout.setDockContent(component, expandedSheetContent)
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
        bottomDockLayout.setDockContent(dockHost, sheetHost)
    }

    fun expandDock() {
        bottomDockLayout.expandDock()
    }

    fun collapseDock() {
        bottomDockLayout.collapseDock()
    }

    fun toggleDockExpanded(): Boolean = bottomDockLayout.toggleDockExpanded()

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        bottomDockLayout.updateViewport(viewportWidth, viewportHeight)
        mainFragment?.onResize(viewportWidth, viewportHeight)
        dockFragment?.onResize(viewportWidth, viewportHeight)
        expandedSheetFragment?.onResize(viewportWidth, viewportHeight)
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        bottomDockLayout.applyWindowConfiguration(newConfig)
        mainFragment?.onConfigurationChanged(newConfig)
        dockFragment?.onConfigurationChanged(newConfig)
        expandedSheetFragment?.onConfigurationChanged(newConfig)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        bottomDockLayout.isInPictureInPictureMode = isInPictureInPictureMode
        mainFragment?.onPictureInPictureModeChanged(isInPictureInPictureMode)
        dockFragment?.onPictureInPictureModeChanged(isInPictureInPictureMode)
        expandedSheetFragment?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onBackPressed(): Boolean {
        if (bottomDockLayout.isDockCurrentlyVisible && !bottomDockLayout.isDockCollapsed) {
            bottomDockLayout.collapseDock()
            return true
        }
        if (mainFragment?.onBackPressed() == true) {
            return true
        }
        return super.onBackPressed()
    }
}
