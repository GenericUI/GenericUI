package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min

/**
 * [LayoutContainer] hosting a [CollapsibleBottomDockLayout].
 */
open class CollapsibleBottomDockContainer(
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : LayoutContainer<CollapsibleBottomDockLayoutImpl, CollapsibleBottomDockLayout>(
    "genericui:collapsiblebottomdocklayout",
    "gui-collapsible-bottom-dock"
) {
    override val layout: CollapsibleBottomDockLayout = CollapsibleBottomDockLayout(
        host = this,
        breakpointSpec = breakpointSpec
    )

    var breakpointSpec: HybridBreakpointSpec
        get() = layout.breakpointSpec
        set(value) {
            layout.breakpointSpec = value
        }

    val mainContentHost: Container
        get() = layout.mainContentHost

    val bottomDockContainer: Container
        get() = layout.bottomDockContainer

    val collapsedContentSlot: Container
        get() = layout.collapsedContentSlot

    val expandedSheetSlot: Container
        get() = layout.expandedSheetSlot

    var isDockCollapsed: Boolean
        get() = layout.isDockCollapsed
        set(value) {
            layout.isDockCollapsed = value
        }

    val isDockExpanded: Boolean
        get() = layout.isDockExpanded

    var isDockHidden: Boolean
        get() = layout.isDockHidden
        set(value) {
            layout.isDockHidden = value
        }

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

    var hideWhenPrimaryColumnVisible: Boolean
        get() = layout.hideWhenPrimaryColumnVisible
        set(value) {
            layout.hideWhenPrimaryColumnVisible = value
        }

    var isPrimaryColumnVisible: Boolean
        get() = layout.isPrimaryColumnVisible
        set(value) {
            layout.isPrimaryColumnVisible = value
        }

    var collapsedHeightPx: Int
        get() = layout.collapsedHeightPx
        set(value) {
            layout.collapsedHeightPx = value
        }

    var expandedHeightPx: Int
        get() = layout.expandedHeightPx
        set(value) {
            layout.expandedHeightPx = value
        }

    val isDockCurrentlyVisible: Boolean
        get() = layout.isDockCurrentlyVisible

    var onDockCollapsedChanged: ((Boolean) -> Unit)?
        get() = layout.onDockCollapsedChanged
        set(value) {
            layout.onDockCollapsedChanged = value
        }

    var onDockVisibilityChanged: ((Boolean) -> Unit)?
        get() = layout.onDockVisibilityChanged
        set(value) {
            layout.onDockVisibilityChanged = value
        }

    init {
        htmlTag = "genericui:collapsiblebottomdocklayout"
        initializeLayout()
    }

    fun setMainContent(component: AbstractComponent): CollapsibleBottomDockContainer = apply {
        layout.setMainContent(component)
    }

    fun getMainContent(): AbstractComponent? = layout.getMainContent()

    fun setDockContent(
        component: AbstractComponent?,
        expandedSheetContent: AbstractComponent? = null
    ): CollapsibleBottomDockContainer = apply {
        layout.setDockContent(component, expandedSheetContent)
    }

    fun getDockContent(): AbstractComponent? = layout.getDockContent()

    fun getExpandedSheetContent(): AbstractComponent? = layout.getExpandedSheetContent()

    fun expandDock() = layout.expandDock()

    fun collapseDock() = layout.collapseDock()

    fun toggleDockExpanded(): Boolean = layout.toggleDockExpanded()

    fun updateViewport(
        viewportWidthDp: Int,
        viewportHeightDp: Int,
        smallestScreenWidthDp: Int = min(viewportWidthDp, viewportHeightDp)
    ) = layout.updateViewport(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)

    fun applyWindowConfiguration(config: WindowConfiguration) = layout.applyWindowConfiguration(config)
}
