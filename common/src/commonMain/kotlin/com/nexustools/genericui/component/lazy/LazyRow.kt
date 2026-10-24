package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.enums.ui.Orientation

/**
 * Horizontally scrolling virtualized [LazyList] that dynamically loads items as they enter
 * the visible viewport and unloads/recycles them as they scroll out of view.
 */
class LazyRow(
    estimatedItemWidthPx: Float = 140f,
    overscanItems: Int = 3
) : LazyList(
    orientation = Orientation.HORIZONTAL,
    estimatedItemExtentPx = estimatedItemWidthPx,
    overscanItems = overscanItems
)
