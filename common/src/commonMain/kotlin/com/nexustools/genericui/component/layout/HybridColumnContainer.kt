package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * [LayoutContainer] hosting a [HybridColumnLayout].
 */
open class HybridColumnContainer(
    foldMode: HybridFoldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP,
    tabPlacement: HybridTabPlacement = HybridTabPlacement.ADAPTIVE_TOP_IN_COLUMNS_BOTTOM_IN_SINGLE,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    fullscreenPolicy: HybridFullscreenPolicy = HybridFullscreenPolicy.DISABLED,
    fullscreenTargetColumnIndex: Int = 0
) : LayoutContainer<HybridColumnLayoutImpl, HybridColumnLayout>(
    "genericui:hybridcolumnlayout",
    "hybrid-column-layout"
) {
    override val layout: HybridColumnLayout = HybridColumnLayout(
        host = this,
        foldMode = foldMode,
        tabPlacement = tabPlacement,
        breakpointSpec = breakpointSpec,
        fullscreenPolicy = fullscreenPolicy,
        fullscreenTargetColumnIndex = fullscreenTargetColumnIndex
    )

    var foldMode: HybridFoldMode
        get() = layout.foldMode
        set(value) {
            layout.foldMode = value
        }

    var tabPlacement: HybridTabPlacement
        get() = layout.tabPlacement
        set(value) {
            layout.tabPlacement = value
        }

    var breakpointSpec: HybridBreakpointSpec
        get() = layout.breakpointSpec
        set(value) {
            layout.breakpointSpec = value
        }

    var fullscreenPolicy: HybridFullscreenPolicy
        get() = layout.fullscreenPolicy
        set(value) {
            layout.fullscreenPolicy = value
        }

    var fullscreenTargetColumnIndex: Int
        get() = layout.fullscreenTargetColumnIndex
        set(value) {
            layout.fullscreenTargetColumnIndex = value
        }

    var fullscreenController: DynamicFullscreenController?
        get() = layout.fullscreenController
        set(value) {
            layout.fullscreenController = value
        }

    val viewportWidthDp: Int
        get() = layout.viewportWidthDp

    val viewportHeightDp: Int
        get() = layout.viewportHeightDp

    val smallestScreenWidthDp: Int
        get() = layout.smallestScreenWidthDp

    val isTablet: Boolean
        get() = layout.isTablet

    val isSmallScreenMobile: Boolean
        get() = layout.isSmallScreenMobile

    val isWideColumnMode: Boolean
        get() = layout.isWideColumnMode

    val layoutMode: HybridLayoutMode
        get() = layout.layoutMode

    val inColumnMode: Boolean
        get() = layout.inColumnMode

    @get:kotlin.jvm.JvmName("isFullscreenProperty")
    @set:kotlin.jvm.JvmName("setFullscreenProperty")
    var isFullscreen: Boolean
        get() = layout.isFullscreen
        set(value) {
            layout.isFullscreen = value
        }

    override fun isFullscreen(): Boolean = layout.isFullscreen

    override fun setFullscreen(fullscreen: Boolean) {
        layout.isFullscreen = fullscreen
        super.setFullscreen(fullscreen)
    }

    var isInPictureInPictureMode: Boolean
        get() = layout.isInPictureInPictureMode
        set(value) {
            layout.isInPictureInPictureMode = value
        }

    val activeTabColumnId: String?
        get() = layout.activeTabColumnId

    var onLayoutModeChanged: ((HybridLayoutMode) -> Unit)?
        get() = layout.onLayoutModeChanged
        set(value) {
            layout.onLayoutModeChanged = value
        }

    var onActiveTabChanged: ((String) -> Unit)?
        get() = layout.onActiveTabChanged
        set(value) {
            layout.onActiveTabChanged = value
        }

    val headerSlot: Container
        get() = layout.headerSlot

    val columnsRow: HStack
        get() = layout.columnsRow

    val singleStack: VStack
        get() = layout.singleStack

    val rightTabbedColumn: VStack
        get() = layout.rightTabbedColumn

    val topTabBar: TabContainer
        get() = layout.topTabBar

    val bottomNavBar: BottomNavigationBar
        get() = layout.bottomNavBar

    val bottomDockSlot: Container
        get() = layout.bottomDockSlot

    val columnSpecs: List<HybridColumnSpec>
        get() = layout.columnSpecs

    val visibleColumnSpecs: List<HybridColumnSpec>
        get() = layout.visibleColumnSpecs

    init {
        htmlTag = "genericui:hybridcolumnlayout"
        initializeLayout()
    }

    fun enableDynamicFullscreen(
        policy: HybridFullscreenPolicy = HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED,
        window: IWindow? = null
    ): DynamicFullscreenController = layout.enableDynamicFullscreen(policy, window)

    fun disableDynamicFullscreen() = layout.disableDynamicFullscreen()

    fun toggleFullscreen(): Boolean = layout.toggleFullscreen()

    fun addColumn(spec: HybridColumnSpec, component: AbstractComponent): HybridColumnContainer = apply {
        layout.addColumn(spec, component)
    }

    fun addColumn(
        id: String,
        title: String = id,
        weight: Float = 1.0f,
        colMd: Int? = null,
        colLg: Int? = null,
        colXl: Int? = null,
        icon: String? = null,
        compactHeightPx: Int? = null,
        compactAspectRatio: Float? = null,
        component: AbstractComponent
    ): HybridColumnContainer = apply {
        layout.addColumn(
            id = id,
            title = title,
            weight = weight,
            colMd = colMd,
            colLg = colLg,
            colXl = colXl,
            icon = icon,
            compactHeightPx = compactHeightPx,
            compactAspectRatio = compactAspectRatio,
            component = component
        )
    }

    fun removeColumn(id: String): Boolean = layout.removeColumn(id)

    fun clearColumns() = layout.clearColumns()

    fun setColumnVisible(id: String, visible: Boolean) = layout.setColumnVisible(id, visible)

    fun getColumnSpec(id: String): HybridColumnSpec? = layout.getColumnSpec(id)

    fun getColumnComponent(id: String): AbstractComponent? = layout.getColumnComponent(id)

    fun selectTab(columnId: String): Boolean = layout.selectTab(columnId)

    fun setHeaderComponent(component: AbstractComponent?) = layout.setHeaderComponent(component)

    fun setBottomDockComponent(component: AbstractComponent?) = layout.setBottomDockComponent(component)

    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp),
        isMobileTarget: Boolean? = null
    ) = layout.updateViewport(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp, isMobileTarget)

    fun applyWindowConfiguration(config: WindowConfiguration) = layout.applyWindowConfiguration(config)

    fun rebuildLayout() = layout.rebuildLayout()
}
