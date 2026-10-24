package com.nexustools.genericui.component.lazy

/**
 * Type-keyed recycling pool of detached [ViewHolder] instances used by [LazyList], [LazyGrid],
 * and [TreeView] so views scrolling out of the viewport are unloaded and reused for newly
 * visible items without re-allocating component trees.
 */
class ViewHolderPool(
    private val maxScrapPerViewType: Int = 16
) {
    private val scrapByType = mutableMapOf<Int, ArrayDeque<ViewHolder>>()

    fun acquire(viewType: Int): ViewHolder? =
        scrapByType[viewType]?.removeLastOrNull()

    fun release(holder: ViewHolder) {
        holder.boundPosition = -1
        holder.isBound = false
        val queue = scrapByType.getOrPut(holder.viewType) { ArrayDeque() }
        if (queue.size < maxScrapPerViewType) {
            queue.addLast(holder)
        } else {
            holder.itemView.dispose()
        }
    }

    fun pooledCount(viewType: Int): Int =
        scrapByType[viewType]?.size ?: 0

    fun totalPooledCount(): Int =
        scrapByType.values.sumOf { it.size }

    fun clear() {
        for (queue in scrapByType.values) {
            while (queue.isNotEmpty()) {
                queue.removeLast().itemView.dispose()
            }
        }
        scrapByType.clear()
    }
}
