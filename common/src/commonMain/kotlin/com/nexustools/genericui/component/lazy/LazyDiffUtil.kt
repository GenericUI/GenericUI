package com.nexustools.genericui.component.lazy

/**
 * Computes incremental insertions, removals, and content updates between an old list and a new list
 * for efficient [LazyList] and [LazyGrid] updates.
 */
object LazyDiffUtil {
    fun <T> calculateDiff(
        oldList: List<T>,
        newList: List<T>,
        areItemsTheSame: (oldItem: T, newItem: T) -> Boolean = { a, b -> a == b },
        areContentsTheSame: (oldItem: T, newItem: T) -> Boolean = { a, b -> a == b }
    ): DiffResult {
        val inserted = mutableListOf<Int>()
        val removed = mutableListOf<Int>()
        val changed = mutableListOf<Int>()

        oldList.forEachIndexed { oldIdx, oldItem ->
            val matchNewIdx = newList.indexOfFirst { areItemsTheSame(oldItem, it) }
            if (matchNewIdx < 0) {
                removed.add(oldIdx)
            } else if (!areContentsTheSame(oldItem, newList[matchNewIdx])) {
                changed.add(matchNewIdx)
            }
        }

        newList.forEachIndexed { newIdx, newItem ->
            val existedInOld = oldList.any { areItemsTheSame(it, newItem) }
            if (!existedInOld) {
                inserted.add(newIdx)
            }
        }

        return DiffResult(
            insertedIndices = inserted,
            removedIndices = removed,
            changedIndices = changed
        )
    }
}
