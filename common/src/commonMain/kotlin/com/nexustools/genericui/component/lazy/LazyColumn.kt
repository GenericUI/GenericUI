package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.enums.ui.Orientation

/**
 * Vertically scrolling virtualized [LazyList] that dynamically loads items as they enter
 * the visible viewport and unloads/recycles them as they scroll out of view.
 */
class LazyColumn(
    estimatedItemHeightPx: Float = 56f,
    overscanItems: Int = 3
) : LazyList(
    orientation = Orientation.VERTICAL,
    estimatedItemExtentPx = estimatedItemHeightPx,
    overscanItems = overscanItems
)
