package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.navigation.SideNav
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * [LayoutContainer] hosting an [AdaptiveNavigationScaffoldLayout].
 */
open class AdaptiveNavigationScaffold(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<AdaptiveNavigationScaffoldLayoutImpl, AdaptiveNavigationScaffoldLayout>(
    "genericui:adaptivenavigationscaffold",
    "gui-adaptive-navigation-scaffold"
) {
    override val layout: AdaptiveNavigationScaffoldLayout = AdaptiveNavigationScaffoldLayout(
        host = this,
        breakpointSpec = breakpointSpec
    )

    var breakpointSpec: HybridBreakpointSpec
        get() = layout.breakpointSpec
        set(value) {
            layout.breakpointSpec = value
        }

    val topNavbar: Navbar
        get() = layout.topNavbar

    val sidebar: SideNav
        get() = layout.sidebar

    val navigationRail: SideNav
        get() = layout.navigationRail

    val bottomNavigationBar: BottomNavigationBar
        get() = layout.bottomNavigationBar

    val bodyRow: ColumnContainer
        get() = layout.bodyRow

    val contentHost: Container
        get() = layout.contentHost

    val activeNavigationKind: String
        get() = layout.activeNavigationKind

    val selectedDestinationId: String?
        get() = layout.selectedDestinationId

    var onDestinationSelected: ((String) -> Unit)?
        get() = layout.onDestinationSelected
        set(value) {
            layout.onDestinationSelected = value
        }

    val destinationIds: List<String>
        get() = layout.destinationIds

    init {
        htmlTag = "genericui:adaptivenavigationscaffold"
        initializeLayout()
    }

    fun addDestination(
        id: String,
        label: String,
        icon: String? = null,
        badge: String? = null,
        content: AbstractComponent
    ): AdaptiveNavigationScaffold = apply {
        layout.addDestination(id, label, icon, badge, content)
    }

    fun removeDestination(id: String): Boolean = layout.removeDestination(id)

    fun selectDestination(id: String): Boolean = layout.selectDestination(id)

    fun getDestinationComponent(id: String): AbstractComponent? = layout.getDestinationComponent(id)

    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) = layout.updateViewport(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)

    fun applyWindowConfiguration(config: WindowConfiguration) = layout.applyWindowConfiguration(config)
}
