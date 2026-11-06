package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.tabs.ButtonTab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * [LayoutContainer] hosting a [HybridSplitNavigationLayout].
 */
open class HybridSplitNavigationContainer(
    inColumnMode: Boolean = false,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<HybridSplitNavigationLayoutImpl, HybridSplitNavigationLayout>(
    "genericui:hybridsplitnavigationlayout",
    "gui-hybrid-split-navigation"
) {
    override val layout: HybridSplitNavigationLayout = HybridSplitNavigationLayout(
        host = this,
        inColumnMode = inColumnMode,
        breakpointSpec = breakpointSpec
    )

    var breakpointSpec: HybridBreakpointSpec
        get() = layout.breakpointSpec
        set(value) {
            layout.breakpointSpec = value
        }

    val topTabLayout: TabContainer
        get() = layout.topTabLayout

    val headerSlot: Container
        get() = layout.headerSlot

    val contentHost: Container
        get() = layout.contentHost

    val bottomNavigationBar: BottomNavigationBar
        get() = layout.bottomNavigationBar

    val selectedSectionId: String?
        get() = layout.selectedSectionId

    var onSectionSelected: ((String) -> Unit)?
        get() = layout.onSectionSelected
        set(value) {
            layout.onSectionSelected = value
        }

    var inColumnMode: Boolean
        get() = layout.inColumnMode
        set(value) {
            layout.inColumnMode = value
        }

    val sectionIds: List<String>
        get() = layout.sectionIds

    val sectionCount: Int
        get() = layout.sectionCount

    init {
        htmlTag = "genericui:hybridsplitnavigationlayout"
        initializeLayout()
    }

    fun addSection(
        id: String,
        title: String,
        icon: String? = null,
        badge: String? = null,
        content: AbstractComponent
    ): HybridSplitNavigationContainer = apply {
        layout.addSection(id, title, icon, badge, content)
    }

    fun addButtonSection(
        id: String,
        title: String,
        icon: String? = null,
        onClick: () -> Unit = {}
    ): ButtonTab = layout.addButtonSection(id, title, icon, onClick)

    fun addButtonSection(tab: ButtonTab): ButtonTab = layout.addButtonSection(tab)

    fun removeSection(id: String): Boolean = layout.removeSection(id)

    fun selectSection(id: String): Boolean = layout.selectSection(id)

    fun getSectionComponent(id: String): AbstractComponent? = layout.getSectionComponent(id)

    fun getSectionTitle(id: String): String? = layout.getSectionTitle(id)

    fun getSectionBadge(id: String): String? = layout.getSectionBadge(id)

    fun setSectionBadge(id: String, badge: String?) = layout.setSectionBadge(id, badge)

    fun setHeaderComponent(component: AbstractComponent?) = layout.setHeaderComponent(component)

    fun getHeaderComponent(): AbstractComponent? = layout.getHeaderComponent()

    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) = layout.updateViewport(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)

    fun applyWindowConfiguration(config: WindowConfiguration) = layout.applyWindowConfiguration(config)
}
