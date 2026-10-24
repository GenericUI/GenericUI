package com.nexustools.genericui.component.lazy

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Computes the active [LazyViewportWindow] from scroll offset, viewport extent, item extent,
 * and overscan buffer count, and diffs previous vs current windows to emit exact `toLoad`
 * and `toUnload` item indices.
 */
class LazyViewportTracker(
    var overscanItems: Int = 3
) {
    var currentWindow: LazyViewportWindow = LazyViewportWindow.EMPTY
        private set

    /**
     * Updates the viewport state and invokes [onUnloadIndex] for every item that scrolled out of view
     * before invoking [onLoadIndex] for every item that newly entered the visible + overscan window.
     */
    fun updateViewport(
        scrollOffsetPx: Float,
        viewportExtentPx: Float,
        estimatedItemExtentPx: Float,
        totalItems: Int,
        columns: Int = 1,
        onUnloadIndex: (Int) -> Unit,
        onLoadIndex: (Int) -> Unit
    ): LazyViewportWindow {
        if (totalItems <= 0 || viewportExtentPx <= 0f || estimatedItemExtentPx <= 0f) {
            val prev = currentWindow
            if (prev.activeCount > 0) {
                for (i in prev.firstActiveIndex..prev.lastActiveIndex) {
                    onUnloadIndex(i)
                }
            }
            currentWindow = LazyViewportWindow.EMPTY
            return currentWindow
        }

        val cols = columns.coerceAtLeast(1)
        val firstRow = floor(scrollOffsetPx.coerceAtLeast(0f) / estimatedItemExtentPx).toInt()
        val visibleRows = ceil(viewportExtentPx / estimatedItemExtentPx).toInt().coerceAtLeast(1)
        val lastRow = firstRow + visibleRows

        val firstVis = (firstRow * cols).coerceIn(0, totalItems - 1)
        val lastVis = ((lastRow * cols) + (cols - 1)).coerceIn(firstVis, totalItems - 1)

        val overscanCount = overscanItems.coerceAtLeast(0) * cols
        val firstAct = (firstVis - overscanCount).coerceAtLeast(0)
        val lastAct = (lastVis + overscanCount).coerceAtMost(totalItems - 1)

        val nextWindow = LazyViewportWindow(
            firstVisibleIndex = firstVis,
            lastVisibleIndex = lastVis,
            firstActiveIndex = firstAct,
            lastActiveIndex = lastAct,
            totalItemCount = totalItems
        )

        val prev = currentWindow
        currentWindow = nextWindow

        if (prev.activeCount > 0) {
            for (idx in prev.firstActiveIndex..prev.lastActiveIndex) {
                if (!nextWindow.containsActive(idx)) {
                    onUnloadIndex(idx)
                }
            }
        }

        for (idx in nextWindow.firstActiveIndex..nextWindow.lastActiveIndex) {
            if (!prev.containsActive(idx)) {
                onLoadIndex(idx)
            }
        }

        return nextWindow
    }

    fun unloadAll(onUnloadIndex: (Int) -> Unit) {
        val prev = currentWindow
        if (prev.activeCount > 0) {
            for (idx in prev.firstActiveIndex..prev.lastActiveIndex) {
                onUnloadIndex(idx)
            }
        }
        currentWindow = LazyViewportWindow.EMPTY
    }
}
