package com.nexustools.genericui.component.dock

import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.enums.dock.StudioWindowMode
import com.nexustools.genericui.geometry.Rect

/**
 * Serializable layout snapshot of a [DockWorkspace] or [com.nexustools.genericui.component.fragment.helper.DockWorkspaceFragment].
 */
data class DockLayoutState(
    val windowMode: StudioWindowMode = StudioWindowMode.SINGLE_WINDOW,
    val leftSplitRatio: Float = 0.2f,
    val rightSplitRatio: Float = 0.25f,
    val bottomSplitRatio: Float = 0.25f,
    val panelPositions: Map<String, DockPosition> = emptyMap(),
    val floatingBounds: Map<String, Rect> = emptyMap(),
    val collapsedPanels: Set<String> = emptySet(),
    val activeDocumentId: String? = null
)
