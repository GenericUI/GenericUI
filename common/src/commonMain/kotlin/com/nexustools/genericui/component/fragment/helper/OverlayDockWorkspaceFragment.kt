package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.dock.DockPanel
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode

/**
 * Specialized [DockWorkspaceFragment] pre-configured with `overlayPanelsOverBackground = true`
 * so dockable/collapsible [DockPanel]s are overlaid on top of a full-viewport [backgroundComponent].
 */
open class OverlayDockWorkspaceFragment(
    backgroundComponent: AbstractComponent? = null,
    initialMode: StudioWindowMode = StudioWindowMode.SINGLE_WINDOW_MODE
) : DockWorkspaceFragment(
    initialMode = initialMode,
    initialBackgroundComponent = backgroundComponent,
    overlayPanelsOverBackground = true
) {
    fun registerOverlayPanel(
        panel: DockPanel,
        position: DockPosition = DockPosition.LEFT,
        xOffsetPx: Int? = null,
        yOffsetPx: Int? = null
    ): OverlayDockWorkspaceFragment = apply {
        val isFreePositioned = position == DockPosition.FLOATING || position == DockPosition.CENTER
        if (!isFreePositioned) {
            panel.leftOffset = null
            panel.topOffset = null
        }
        addOverlayPanel(
            panel = panel,
            position = position,
            xOffsetPx = if (isFreePositioned) xOffsetPx else null,
            yOffsetPx = if (isFreePositioned) yOffsetPx else null
        )
    }
}
