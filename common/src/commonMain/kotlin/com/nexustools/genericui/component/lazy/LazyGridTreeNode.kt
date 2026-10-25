package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.core.AbstractComponent

/**
 * Hierarchical tree node model used by [LazyGridTree], supporting both collapsible branch nodes
 * and leaf nodes where each column cell in [cells] can contain either plain text or an [AbstractComponent].
 */
class LazyGridTreeNode<T>(
    val id: String,
    var keyText: String,
    var data: T? = null,
    var isBranch: Boolean = false,
    var isExpanded: Boolean = true,
    var icon: String? = null,
    val cells: MutableList<LazyGridTreeCell> = mutableListOf(),
    val children: MutableList<LazyGridTreeNode<T>> = mutableListOf(),
    val lazyChildrenLoader: (() -> List<LazyGridTreeNode<T>>)? = null
) {
    private var lazyLoaded: Boolean = false

    /**
     * Returns `true` when this node is a branch container (either explicitly marked via [isBranch],
     * has child nodes in [children], or has a [lazyChildrenLoader]).
     */
    val hasBranchDisclosure: Boolean
        get() = isBranch || children.isNotEmpty() || lazyChildrenLoader != null

    /**
     * Materializes lazy children on first expansion when [lazyChildrenLoader] is provided.
     */
    fun ensureChildrenLoaded() {
        if (!lazyLoaded && lazyChildrenLoader != null) {
            lazyLoaded = true
            children.clear()
            children.addAll(lazyChildrenLoader.invoke())
        }
    }

    /**
     * Sets a plain text value (and optional [badgeText]) at [columnIndex] in [cells].
     */
    fun setTextCell(
        columnIndex: Int,
        text: String,
        badgeText: String? = null,
        tooltipText: String? = null
    ): LazyGridTreeNode<T> = apply {
        ensureCellCapacity(columnIndex)
        cells[columnIndex] = LazyGridTreeCell.ofText(
            text = text,
            badgeText = badgeText,
            tooltipText = tooltipText
        )
    }

    /**
     * Sets an [AbstractComponent] value at [columnIndex] in [cells].
     */
    fun setComponentCell(
        columnIndex: Int,
        component: AbstractComponent,
        tooltipText: String? = null
    ): LazyGridTreeNode<T> = apply {
        ensureCellCapacity(columnIndex)
        cells[columnIndex] = LazyGridTreeCell.ofComponent(
            component = component,
            tooltipText = tooltipText
        )
    }

    /**
     * Sets a lazily-constructed [AbstractComponent] factory at [columnIndex] in [cells].
     */
    fun setComponentCellFactory(
        columnIndex: Int,
        tooltipText: String? = null,
        factory: () -> AbstractComponent
    ): LazyGridTreeNode<T> = apply {
        ensureCellCapacity(columnIndex)
        cells[columnIndex] = LazyGridTreeCell.ofComponentFactory(
            tooltipText = tooltipText,
            factory = factory
        )
    }

    /**
     * Appends a child [LazyGridTreeNode] to [children] and marks this node as a branch.
     */
    fun addChild(child: LazyGridTreeNode<T>): LazyGridTreeNode<T> = apply {
        isBranch = true
        children.add(child)
    }

    private fun ensureCellCapacity(columnIndex: Int) {
        val target = columnIndex.coerceAtLeast(0)
        while (cells.size <= target) {
            cells.add(LazyGridTreeCell.ofText(""))
        }
    }
}
