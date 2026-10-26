package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.AdaptiveNavigationScaffold
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] hosting an [AdaptiveNavigationScaffold] that automatically morphs navigation chrome
 * between `"BOTTOM_NAV"` (`< 600dp`), `"NAVIGATION_RAIL"` (`600..839dp`), and `"SIDEBAR"` (`>= 840dp`)
 * while preserving child destination [Fragment] and [AbstractComponent] state.
 */
open class AdaptiveNavigationScaffoldFragment(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Fragment() {

    val scaffold: AdaptiveNavigationScaffold = AdaptiveNavigationScaffold(
        breakpointSpec = breakpointSpec
    )

    private val destinationFragments = linkedMapOf<String, Fragment>()
    private val destinationFragmentContainers = linkedMapOf<String, FragmentContainer>()

    val activeNavigationKind: String
        get() = scaffold.activeNavigationKind

    val selectedDestinationId: String?
        get() = scaffold.selectedDestinationId

    val destinationIds: List<String>
        get() = scaffold.destinationIds

    var onDestinationSelected: ((String) -> Unit)?
        get() = scaffold.onDestinationSelected
        set(value) {
            scaffold.onDestinationSelected = value
        }

    init {
        addChild(scaffold)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (!children.contains(scaffold)) {
            addChild(scaffold)
        }
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            scaffold.updateViewport(winSize.width, winSize.height)
        }
    }

    /**
     * Registers a destination backed by an [AbstractComponent].
     */
    fun addDestination(
        id: String,
        label: String,
        icon: String? = null,
        badge: String? = null,
        content: AbstractComponent
    ): AdaptiveNavigationScaffoldFragment = apply {
        destinationFragments.remove(id)
        destinationFragmentContainers.remove(id)?.dispose()
        scaffold.addDestination(
            id = id,
            label = label,
            icon = icon,
            badge = badge,
            content = content
        )
    }

    /**
     * Registers a destination backed by a child [Fragment].
     */
    fun addFragmentDestination(
        id: String,
        label: String,
        icon: String? = null,
        badge: String? = null,
        fragment: Fragment
    ): AdaptiveNavigationScaffoldFragment = apply {
        destinationFragments.remove(id)
        destinationFragmentContainers.remove(id)?.dispose()
        val host = FragmentContainer(
            initialFragment = fragment,
            hostWindow = attachedWindow
        ).apply {
            hostingIWindow = hostingWindow
        }
        destinationFragments[id] = fragment
        destinationFragmentContainers[id] = host
        scaffold.addDestination(
            id = id,
            label = label,
            icon = icon,
            badge = badge,
            content = host
        )
    }

    fun removeDestination(id: String): Boolean {
        destinationFragments.remove(id)
        destinationFragmentContainers.remove(id)?.dispose()
        return scaffold.removeDestination(id)
    }

    fun selectDestination(id: String): Boolean = scaffold.selectDestination(id)

    fun getDestinationComponent(id: String): AbstractComponent? =
        scaffold.getDestinationComponent(id)

    fun getDestinationFragment(id: String): Fragment? = destinationFragments[id]

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        scaffold.updateViewport(viewportWidth, viewportHeight)
        for (fragment in destinationFragments.values) {
            fragment.onResize(viewportWidth, viewportHeight)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        scaffold.applyWindowConfiguration(newConfig)
        for (fragment in destinationFragments.values) {
            fragment.onConfigurationChanged(newConfig)
        }
    }

    override fun onBackPressed(): Boolean {
        val activeId = selectedDestinationId
        val activeFragment = if (activeId != null) destinationFragments[activeId] else null
        if (activeFragment != null && activeFragment.onBackPressed()) {
            return true
        }
        return super.onBackPressed()
    }
}
