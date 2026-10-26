package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayout
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] wrapping [HybridSplitNavigationLayout] to support both [AbstractComponent]
 * and child [Fragment] sections with automatic top-tabs (`inColumnMode == true`) vs. bottom-navigation
 * (`inColumnMode == false`) switching across mobile, tablet, web, and desktop viewports.
 */
open class HybridSplitNavigationFragment(
    initialInColumnMode: Boolean = false,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Fragment() {

    val splitNavigationLayout: HybridSplitNavigationLayout = HybridSplitNavigationLayout(
        inColumnMode = initialInColumnMode,
        breakpointSpec = breakpointSpec
    )

    private val sectionFragments = linkedMapOf<String, Fragment>()
    private val sectionFragmentContainers = linkedMapOf<String, FragmentContainer>()

    var inColumnMode: Boolean
        get() = splitNavigationLayout.inColumnMode
        set(value) {
            splitNavigationLayout.inColumnMode = value
        }

    var breakpointSpec: HybridBreakpointSpec
        get() = splitNavigationLayout.breakpointSpec
        set(value) {
            splitNavigationLayout.breakpointSpec = value
        }

    val selectedSectionId: String?
        get() = splitNavigationLayout.selectedSectionId

    val sectionIds: List<String>
        get() = splitNavigationLayout.sectionIds

    var onSectionSelected: ((String) -> Unit)?
        get() = splitNavigationLayout.onSectionSelected
        set(value) {
            splitNavigationLayout.onSectionSelected = value
        }

    init {
        addChild(splitNavigationLayout)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (!children.contains(splitNavigationLayout)) {
            addChild(splitNavigationLayout)
        }
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            splitNavigationLayout.updateViewport(winSize.width, winSize.height)
        }
    }

    /**
     * Registers a component section with [id], [title], optional [icon], optional [badge], and [content].
     */
    fun addSection(
        id: String,
        title: String,
        icon: String? = null,
        badge: String? = null,
        content: AbstractComponent
    ): HybridSplitNavigationFragment = apply {
        sectionFragments.remove(id)
        sectionFragmentContainers.remove(id)?.dispose()
        splitNavigationLayout.addSection(
            id = id,
            title = title,
            icon = icon,
            badge = badge,
            content = content
        )
    }

    /**
     * Registers a child [Fragment] section with [id], [title], optional [icon], optional [badge], and [fragment].
     */
    fun addFragmentSection(
        id: String,
        title: String,
        icon: String? = null,
        badge: String? = null,
        fragment: Fragment
    ): HybridSplitNavigationFragment = apply {
        sectionFragments.remove(id)
        sectionFragmentContainers.remove(id)?.dispose()
        val host = FragmentContainer(
            initialFragment = fragment,
            hostWindow = attachedWindow
        ).apply {
            hostingIWindow = hostingWindow
        }
        sectionFragments[id] = fragment
        sectionFragmentContainers[id] = host
        splitNavigationLayout.addSection(
            id = id,
            title = title,
            icon = icon,
            badge = badge,
            content = host
        )
    }

    /**
     * Removes the section identified by [id].
     */
    fun removeSection(id: String): Boolean {
        sectionFragments.remove(id)
        sectionFragmentContainers.remove(id)?.dispose()
        return splitNavigationLayout.removeSection(id)
    }

    /**
     * Selects the section identified by [id].
     */
    fun selectSection(id: String): Boolean = splitNavigationLayout.selectSection(id)

    /**
     * Returns the [AbstractComponent] for section [id], or `null`.
     */
    fun getSectionComponent(id: String): AbstractComponent? =
        splitNavigationLayout.getSectionComponent(id)

    /**
     * Returns the child [Fragment] registered via [addFragmentSection] for [id], or `null`.
     */
    fun getSectionFragment(id: String): Fragment? = sectionFragments[id]

    /**
     * Mounts an optional header component above the active section content.
     */
    fun setHeaderComponent(component: AbstractComponent?): HybridSplitNavigationFragment = apply {
        splitNavigationLayout.setHeaderComponent(component)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        splitNavigationLayout.updateViewport(viewportWidth, viewportHeight)
        for (fragment in sectionFragments.values) {
            fragment.onResize(viewportWidth, viewportHeight)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        splitNavigationLayout.applyWindowConfiguration(newConfig)
        for (fragment in sectionFragments.values) {
            fragment.onConfigurationChanged(newConfig)
        }
    }

    override fun onBackPressed(): Boolean {
        val activeId = selectedSectionId
        val activeFragment = if (activeId != null) sectionFragments[activeId] else null
        if (activeFragment != null && activeFragment.onBackPressed()) {
            return true
        }
        return super.onBackPressed()
    }
}
