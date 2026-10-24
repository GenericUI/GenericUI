package com.nexustools.genericui.component.lazy

/**
 * Represents the currently visible and active index range `[firstActiveIndex .. lastActiveIndex]`
 * in a [LazyList], [LazyGrid], or [TreeView].
 *
 * Items entering this range are dynamically loaded/bound; items leaving this range are immediately
 * unloaded/unbound and recycled.
 */
data class LazyViewportWindow(
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val firstActiveIndex: Int,
    val lastActiveIndex: Int,
    val totalItemCount: Int
) {
    val activeCount: Int
        get() = if (lastActiveIndex >= firstActiveIndex && firstActiveIndex >= 0) {
            lastActiveIndex - firstActiveIndex + 1
        } else {
            0
        }

    fun containsActive(index: Int): Boolean =
        activeCount > 0 && index in firstActiveIndex..lastActiveIndex

    companion object {
        val EMPTY = LazyViewportWindow(
            firstVisibleIndex = -1,
            lastVisibleIndex = -1,
            firstActiveIndex = -1,
            lastActiveIndex = -1,
            totalItemCount = 0
        )
    }
}
