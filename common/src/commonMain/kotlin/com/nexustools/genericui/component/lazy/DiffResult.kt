package com.nexustools.genericui.component.lazy

/**
 * Result of computing the minimal difference between two lists via [LazyDiffUtil.calculateDiff].
 */
data class DiffResult(
    val insertedIndices: List<Int>,
    val removedIndices: List<Int>,
    val changedIndices: List<Int>
) {
    val hasChanges: Boolean
        get() = insertedIndices.isNotEmpty() || removedIndices.isNotEmpty() || changedIndices.isNotEmpty()
}
