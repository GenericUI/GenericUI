package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.layout.HybridSplitNavigationContainer
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayout
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayoutImpl
import com.nexustools.genericui.component.tabs.ButtonTab
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [LayoutFragment] wrapping [HybridSplitNavigationLayout] to support both [AbstractComponent]
 * and child [Fragment] sections with automatic top-tabs (`inColumnMode == true`) vs. bottom-navigation
 * (`inColumnMode == false`) switching across mobile, tablet, web, and desktop viewports.
 */
open class HybridSplitNavigationFragment(
    initialInColumnMode: Boolean = false,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    id: String? = null
) : LayoutFragment<HybridSplitNavigationLayoutImpl, HybridSplitNavigationLayout>(id = id) {

    val splitNavigationContainer: HybridSplitNavigationContainer = HybridSplitNavigationContainer(
        inColumnMode = initialInColumnMode,
        breakpointSpec = breakpointSpec
    )

    override val layout: HybridSplitNavigationLayout = splitNavigationContainer.layout

    val splitNavigationLayout: HybridSplitNavigationLayout
        get() = layout

    private val sectionFragments = linkedMapOf<String, Fragment>()
    private val sectionFragmentContainers = linkedMapOf<String, FragmentContainer>()

    var inColumnMode: Boolean
        get() = layout.inColumnMode
        set(value) {
            layout.inColumnMode = value
        }

    var breakpointSpec: HybridBreakpointSpec
        get() = layout.breakpointSpec
        set(value) {
            layout.breakpointSpec = value
        }

    val selectedSectionId: String?
        get() = layout.selectedSectionId

    val sectionIds: List<String>
        get() = layout.sectionIds

    var onSectionSelected: ((String) -> Unit)?
        get() = layout.onSectionSelected
        set(value) {
            layout.onSectionSelected = value
        }

    init {
        addChild(splitNavigationContainer)
    }

    override fun onCreate(savedState: FragmentBundle?) {
        if (!children.contains(splitNavigationContainer)) {
            addChild(splitNavigationContainer)
        }
        super.onCreate(savedState)
        val winSize = hostingWindow?.getWindowSize() ?: attachedWindow?.getWindowSize()
        if (winSize != null && winSize.width > 0 && winSize.height > 0) {
            layout.updateViewport(winSize.width, winSize.height)
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
        layout.addSection(
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
        layout.addSection(
            id = id,
            title = title,
            icon = icon,
            badge = badge,
            content = host
        )
    }

    /**
     * Registers an action-only [ButtonTab] section with [id], [title], optional [icon], and [onClick].
     */
    fun addButtonSection(
        id: String,
        title: String,
        icon: String? = null,
        onClick: () -> Unit = {}
    ): ButtonTab {
        sectionFragments.remove(id)
        sectionFragmentContainers.remove(id)?.dispose()
        return layout.addButtonSection(
            id = id,
            title = title,
            icon = icon,
            onClick = onClick
        )
    }

    /**
     * Registers an existing [ButtonTab] as an action-only section.
     */
    fun addButtonSection(tab: ButtonTab): ButtonTab {
        val sectionId = tab.id?.takeIf { it.isNotEmpty() } ?: tab.tabId
        sectionFragments.remove(sectionId)
        sectionFragmentContainers.remove(sectionId)?.dispose()
        return layout.addButtonSection(tab)
    }

    /**
     * Removes the section identified by [id].
     */
    fun removeSection(id: String): Boolean {
        sectionFragments.remove(id)
        sectionFragmentContainers.remove(id)?.dispose()
        return layout.removeSection(id)
    }

    /**
     * Selects the section identified by [id].
     */
    fun selectSection(id: String): Boolean = layout.selectSection(id)

    /**
     * Returns the [AbstractComponent] for section [id], or `null`.
     */
    fun getSectionComponent(id: String): AbstractComponent? =
        layout.getSectionComponent(id)

    /**
     * Returns the child [Fragment] registered via [addFragmentSection] for [id], or `null`.
     */
    fun getSectionFragment(id: String): Fragment? = sectionFragments[id]

    /**
     * Mounts an optional header component above the active section content.
     */
    fun setHeaderComponent(component: AbstractComponent?): HybridSplitNavigationFragment = apply {
        layout.setHeaderComponent(component)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        for (fragment in sectionFragments.values) {
            fragment.onResize(viewportWidth, viewportHeight)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
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
