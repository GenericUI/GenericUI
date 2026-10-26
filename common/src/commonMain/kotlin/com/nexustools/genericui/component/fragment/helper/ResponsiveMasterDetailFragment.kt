package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.layout.ResponsiveMasterDetailLayout
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] hosting a [ResponsiveMasterDetailLayout] (`Master` + `Detail` + optional `Inspector`).
 *
 * - Supports both [AbstractComponent] and [Fragment] panes ([setMasterFragment], [setDetailFragment], [setInspectorFragment]).
 * - Forwards [onResize] and [onConfigurationChanged] to [masterDetailLayout] and hosted pane fragments.
 * - Overrides [onBackPressed] to invoke [ResponsiveMasterDetailLayout.navigateBackToMaster] when showing
 *   the Detail pane in compact single-pane mode.
 */
open class ResponsiveMasterDetailFragment(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Fragment() {

    val masterDetailLayout: ResponsiveMasterDetailLayout = ResponsiveMasterDetailLayout(
        breakpointSpec = breakpointSpec
    )

    var masterFragment: Fragment? = null
        private set
    var detailFragment: Fragment? = null
        private set
    var inspectorFragment: Fragment? = null
        private set

    private var masterFragmentContainer: FragmentContainer? = null
    private var detailFragmentContainer: FragmentContainer? = null
    private var inspectorFragmentContainer: FragmentContainer? = null

    val isSideBySideMode: Boolean
        get() = masterDetailLayout.isSideBySideMode

    val isInspectorSideBySide: Boolean
        get() = masterDetailLayout.isInspectorSideBySide

    val isDetailShowingInCompact: Boolean
        get() = masterDetailLayout.isDetailShowingInCompact

    var isInspectorOpen: Boolean
        get() = masterDetailLayout.isInspectorOpen
        set(value) {
            masterDetailLayout.isInspectorOpen = value
        }

    init {
        addChild(masterDetailLayout)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (!children.contains(masterDetailLayout)) {
            addChild(masterDetailLayout)
        }
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            masterDetailLayout.updateViewport(winSize.width, winSize.height)
        }
    }

    fun setMasterPane(
        component: AbstractComponent,
        widthPx: Int = 320,
        colMd: Int = 4
    ): ResponsiveMasterDetailFragment = apply {
        masterFragment = null
        masterFragmentContainer?.dispose()
        masterFragmentContainer = null
        masterDetailLayout.setMasterPane(component, widthPx, colMd)
    }

    fun setDetailPane(
        component: AbstractComponent,
        colMd: Int = 8
    ): ResponsiveMasterDetailFragment = apply {
        detailFragment = null
        detailFragmentContainer?.dispose()
        detailFragmentContainer = null
        masterDetailLayout.setDetailPane(component, colMd)
    }

    fun setInspectorPane(
        component: AbstractComponent?,
        widthPx: Int = 300,
        colXl: Int = 3
    ): ResponsiveMasterDetailFragment = apply {
        inspectorFragment = null
        inspectorFragmentContainer?.dispose()
        inspectorFragmentContainer = null
        masterDetailLayout.setInspectorPane(component, widthPx, colXl)
    }

    fun setMasterFragment(
        fragment: Fragment,
        widthPx: Int = 320,
        colMd: Int = 4
    ): ResponsiveMasterDetailFragment = apply {
        masterFragmentContainer?.dispose()
        val host = FragmentContainer(
            initialFragment = fragment,
            hostWindow = attachedWindow
        ).apply {
            hostingIWindow = hostingWindow
        }
        masterFragment = fragment
        masterFragmentContainer = host
        masterDetailLayout.setMasterPane(host, widthPx, colMd)
    }

    fun setDetailFragment(
        fragment: Fragment,
        colMd: Int = 8
    ): ResponsiveMasterDetailFragment = apply {
        detailFragmentContainer?.dispose()
        val host = FragmentContainer(
            initialFragment = fragment,
            hostWindow = attachedWindow
        ).apply {
            hostingIWindow = hostingWindow
        }
        detailFragment = fragment
        detailFragmentContainer = host
        masterDetailLayout.setDetailPane(host, colMd)
    }

    fun setInspectorFragment(
        fragment: Fragment?,
        widthPx: Int = 300,
        colXl: Int = 3
    ): ResponsiveMasterDetailFragment = apply {
        inspectorFragmentContainer?.dispose()
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
        inspectorFragment = fragment
        inspectorFragmentContainer = host
        masterDetailLayout.setInspectorPane(host, widthPx, colXl)
    }

    fun openDetailInCompact(): Boolean = masterDetailLayout.openDetailInCompact()

    fun navigateBackToMaster(): Boolean = masterDetailLayout.navigateBackToMaster()

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        masterDetailLayout.updateViewport(viewportWidth, viewportHeight)
        masterFragment?.onResize(viewportWidth, viewportHeight)
        detailFragment?.onResize(viewportWidth, viewportHeight)
        inspectorFragment?.onResize(viewportWidth, viewportHeight)
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        masterDetailLayout.applyWindowConfiguration(newConfig)
        masterFragment?.onConfigurationChanged(newConfig)
        detailFragment?.onConfigurationChanged(newConfig)
        inspectorFragment?.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed(): Boolean {
        if (masterDetailLayout.isDetailShowingInCompact && !masterDetailLayout.isSideBySideMode) {
            if (detailFragment?.onBackPressed() == true) {
                return true
            }
            if (masterDetailLayout.navigateBackToMaster()) {
                return true
            }
        }
        if (detailFragment?.onBackPressed() == true) {
            return true
        }
        if (masterFragment?.onBackPressed() == true) {
            return true
        }
        return super.onBackPressed()
    }
}
