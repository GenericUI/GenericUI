package com.nexustools.genericui.component.lazy

/**
 * Represents a single visible flattened row in a [LazyGridTree] after traversing expanded branches.
 *
 * @property rowIndex Zero-based visible row index (excluding the optional header row).
 * @property depth Nesting depth in the tree hierarchy (`0` for root nodes).
 * @property node The [LazyGridTreeNode] displayed on this row.
 * @property parentNode The parent branch [LazyGridTreeNode], or `null` if [node] is a root node.
 * @property siblingIndex Zero-based index of [node] within its parent's children (or root list).
 * @property siblingCount Total number of siblings in [node]'s parent container (or root list).
 */
data class LazyGridTreeFlattenedRow<T>(
    val rowIndex: Int,
    val depth: Int,
    val node: LazyGridTreeNode<T>,
    val parentNode: LazyGridTreeNode<T>?,
    val siblingIndex: Int,
    val siblingCount: Int
)
