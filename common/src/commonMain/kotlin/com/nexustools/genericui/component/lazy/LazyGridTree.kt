package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Box
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.geometry.EdgeInsets

/**
 * Virtualized multi-column collapsible tree-grid component extending [LazyGrid].
 *
 * Renders a hierarchical tree of [LazyGridTreeNode]s across configurable [treeColumns] where
 * each cell can hold either a plain text value or an arbitrary [AbstractComponent] (such as an
 * inline editable `TextField`, `Select`, `CheckBox`, or action `Button` bar).
 *
 * Only expanded branches are flattened into visible rows, and only cells inside the active
 * [LazyGrid] scroll viewport (+ overscan rows) are materialized.
 */
open class LazyGridTree<T>(
    treeColumns: List<LazyGridTreeColumn> = defaultTreeColumns(),
    estimatedRowHeightPx: Float = 38f,
    overscanRows: Int = 4
) : LazyGrid(
    columns = treeColumns.size.coerceAtLeast(1),
    estimatedRowHeightPx = estimatedRowHeightPx,
    overscanRows = overscanRows
) {
    init {
        htmlTag = "genericui:lazygridtree"
        addClass("lazy-grid-tree")
    }

    private val _treeColumns = mutableListOf<LazyGridTreeColumn>().apply {
        if (treeColumns.isEmpty()) {
            addAll(defaultTreeColumns())
        } else {
            addAll(treeColumns)
        }
    }

    /**
     * Column definitions for this [LazyGridTree].
     */
    val treeColumns: List<LazyGridTreeColumn>
        get() = _treeColumns

    private val _rootNodes = mutableListOf<LazyGridTreeNode<T>>()

    /**
     * Root-level [LazyGridTreeNode]s in the tree hierarchy.
     */
    val rootNodes: List<LazyGridTreeNode<T>>
        get() = _rootNodes

    private val _flattenedRows = mutableListOf<LazyGridTreeFlattenedRow<T>>()

    /**
     * Currently visible flattened rows produced by traversing expanded branches of [rootNodes].
     */
    val flattenedRows: List<LazyGridTreeFlattenedRow<T>>
        get() = _flattenedRows

    /**
     * Horizontal indentation in pixels per tree depth level in the tree column.
     */
    var indentStepPx: Int = 20
        set(value) {
            val clamped = value.coerceAtLeast(0)
            if (field != clamped) {
                field = clamped
                rebuildFlattenedTree()
            }
        }

    /**
     * Whether to render a column header row at row `0` of the grid.
     */
    var showHeaderRow: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                rebuildFlattenedTree()
            }
        }

    /**
     * Currently selected node ID, or `null` if no node is selected.
     */
    var selectedNodeId: String? = null
        set(value) {
            if (field != value) {
                field = value
                rebuildFlattenedTree()
            }
        }

    /**
     * Optional case-insensitive text filter query. When non-blank, nodes whose `keyText`, `id`,
     * cell texts, or descendant nodes match the query are shown.
     */
    var filterQuery: String = ""
        set(value) {
            val trimmed = value.trim()
            if (field != trimmed) {
                field = trimmed
                rebuildFlattenedTree()
            }
        }

    /**
     * Callback invoked when a node row is selected.
     */
    var onNodeSelected: ((LazyGridTreeNode<T>) -> Unit)? = null

    /**
     * Callback invoked when a branch node is expanded or collapsed.
     */
    var onNodeExpansionChanged: ((LazyGridTreeNode<T>, Boolean) -> Unit)? = null

    /**
     * Total number of visible tree rows (excluding the optional header row).
     */
    val visibleRowCount: Int
        get() = _flattenedRows.size

    /**
     * Replaces the column definitions of this [LazyGridTree] and rebuilds the virtualized grid.
     */
    fun setTreeColumns(newColumns: List<LazyGridTreeColumn>): LazyGridTree<T> = apply {
        _treeColumns.clear()
        if (newColumns.isEmpty()) {
            _treeColumns.addAll(defaultTreeColumns())
        } else {
            _treeColumns.addAll(newColumns)
        }
        this.columns = _treeColumns.size.coerceAtLeast(1)
        rebuildFlattenedTree()
    }

    /**
     * Replaces the root nodes of this [LazyGridTree] and rebuilds the flattened row list.
     */
    fun setRootNodes(roots: List<LazyGridTreeNode<T>>): LazyGridTree<T> = apply {
        _rootNodes.clear()
        _rootNodes.addAll(roots)
        rebuildFlattenedTree()
    }

    /**
     * Finds a [LazyGridTreeNode] by its [nodeId] across the entire tree (including collapsed branches).
     */
    fun findNodeById(nodeId: String): LazyGridTreeNode<T>? {
        fun search(nodes: List<LazyGridTreeNode<T>>): LazyGridTreeNode<T>? {
            for (node in nodes) {
                if (node.id == nodeId) return node
                node.ensureChildrenLoaded()
                val childMatch = search(node.children)
                if (childMatch != null) return childMatch
            }
            return null
        }
        return search(_rootNodes)
    }

    /**
     * Toggles the expanded state of the branch node with [nodeId].
     */
    fun toggleExpanded(nodeId: String): Boolean {
        val node = findNodeById(nodeId) ?: return false
        if (!node.hasBranchDisclosure) return false
        node.isExpanded = !node.isExpanded
        if (node.isExpanded) {
            node.ensureChildrenLoaded()
        }
        onNodeExpansionChanged?.invoke(node, node.isExpanded)
        rebuildFlattenedTree()
        return node.isExpanded
    }

    /**
     * Explicitly sets the expanded state of the branch node with [nodeId].
     */
    fun setExpanded(nodeId: String, expanded: Boolean): Boolean {
        val node = findNodeById(nodeId) ?: return false
        if (!node.hasBranchDisclosure) return false
        if (node.isExpanded != expanded) {
            node.isExpanded = expanded
            if (expanded) {
                node.ensureChildrenLoaded()
            }
            onNodeExpansionChanged?.invoke(node, expanded)
            rebuildFlattenedTree()
        }
        return true
    }

    /**
     * Expands all branch nodes in the tree recursively.
     */
    fun expandAll(): LazyGridTree<T> = apply {
        fun expandRecursive(nodes: List<LazyGridTreeNode<T>>) {
            for (node in nodes) {
                if (node.hasBranchDisclosure) {
                    node.isExpanded = true
                    node.ensureChildrenLoaded()
                    expandRecursive(node.children)
                }
            }
        }
        expandRecursive(_rootNodes)
        rebuildFlattenedTree()
    }

    /**
     * Collapses all branch nodes in the tree (optionally keeping top-level root nodes expanded when [keepRootsExpanded] is `true`).
     */
    fun collapseAll(keepRootsExpanded: Boolean = true): LazyGridTree<T> = apply {
        fun collapseRecursive(nodes: List<LazyGridTreeNode<T>>, depth: Int) {
            for (node in nodes) {
                if (node.hasBranchDisclosure) {
                    node.isExpanded = (depth == 0 && keepRootsExpanded)
                    collapseRecursive(node.children, depth + 1)
                }
            }
        }
        collapseRecursive(_rootNodes, 0)
        rebuildFlattenedTree()
    }

    /**
     * Selects the node with [nodeId] and triggers [onNodeSelected].
     */
    fun selectNode(nodeId: String): Boolean {
        val found = findNodeById(nodeId) ?: return false
        selectedNodeId = found.id
        onNodeSelected?.invoke(found)
        return true
    }

    /**
     * Recomputes [flattenedRows] from [rootNodes] and updates the underlying [LazyGrid] item count and cell builder.
     */
    fun rebuildFlattenedTree() {
        _flattenedRows.clear()
        val colCount = _treeColumns.size.coerceAtLeast(1)
        val activeQuery = filterQuery.lowercase()

        fun nodeMatchesOrHasMatchingDescendant(node: LazyGridTreeNode<T>): Boolean {
            if (activeQuery.isEmpty()) return true
            if (node.id.lowercase().contains(activeQuery) || node.keyText.lowercase().contains(activeQuery)) {
                return true
            }
            for (cell in node.cells) {
                if (cell.text?.lowercase()?.contains(activeQuery) == true) return true
                if (cell.badgeText?.lowercase()?.contains(activeQuery) == true) return true
            }
            if (node.hasBranchDisclosure) {
                node.ensureChildrenLoaded()
                for (child in node.children) {
                    if (nodeMatchesOrHasMatchingDescendant(child)) return true
                }
            }
            return false
        }

        fun traverse(
            siblings: List<LazyGridTreeNode<T>>,
            parent: LazyGridTreeNode<T>?,
            depth: Int
        ) {
            val filteredSiblings = if (activeQuery.isEmpty()) {
                siblings
            } else {
                siblings.filter { nodeMatchesOrHasMatchingDescendant(it) }
            }
            val count = filteredSiblings.size
            for (idx in filteredSiblings.indices) {
                val node = filteredSiblings[idx]
                _flattenedRows.add(
                    LazyGridTreeFlattenedRow(
                        rowIndex = _flattenedRows.size,
                        depth = depth,
                        node = node,
                        parentNode = parent,
                        siblingIndex = idx,
                        siblingCount = count
                    )
                )
                val shouldTraverseChildren = node.hasBranchDisclosure &&
                    (node.isExpanded || activeQuery.isNotEmpty())
                if (shouldTraverseChildren) {
                    node.ensureChildrenLoaded()
                    traverse(node.children, node, depth + 1)
                }
            }
        }

        traverse(_rootNodes, parent = null, depth = 0)

        val headerRowOffset = if (showHeaderRow) 1 else 0
        val totalRows = headerRowOffset + _flattenedRows.size
        val totalCells = totalRows * colCount

        setItems(
            count = totalCells,
            onLoadItem = { cellIndex ->
                buildGridCellComponent(cellIndex, colCount, headerRowOffset)
            }
        )
    }

    private fun buildGridCellComponent(
        cellIndex: Int,
        colCount: Int,
        headerRowOffset: Int
    ): AbstractComponent {
        val gridRowIndex = cellIndex / colCount
        val colIndex = cellIndex % colCount
        val column = _treeColumns.getOrNull(colIndex)
            ?: LazyGridTreeColumn(id = "col_$colIndex", headerText = "Column $colIndex", isTreeColumn = (colIndex == 0))

        // 1. Header Row
        if (showHeaderRow && gridRowIndex == 0) {
            val headerCell = HStack(gap = SpacingScale.S1).apply {
                addClass("lazy-grid-tree-header-cell")
                minWidthPx = column.minWidthPx
            }
            val headerLabel = Label(column.headerText).apply {
                fontWeight = FontWeight.BOLD
                addClass("lazy-grid-tree-header-label")
            }
            headerCell.addChild(headerLabel)
            return headerCell
        }

        // 2. Data Row
        val dataRowIndex = gridRowIndex - headerRowOffset
        val row = _flattenedRows.getOrNull(dataRowIndex) ?: return Box()
        val node = row.node
        val cell = node.cells.getOrNull(colIndex)
        val isSelected = (node.id == selectedNodeId)
        val isTreeCol = column.isTreeColumn || (colIndex == 0 && _treeColumns.none { it.isTreeColumn })

        val cellContainer = HStack(gap = SpacingScale.S1).apply {
            addClass("lazy-grid-tree-cell")
            if (isTreeCol) addClass("lazy-grid-tree-hierarchy-cell")
            if (node.hasBranchDisclosure) addClass("lazy-grid-tree-branch-cell") else addClass("lazy-grid-tree-leaf-cell")
            if (isSelected) addClass("selected")
            minWidthPx = column.minWidthPx
            cell?.tooltipText?.let { tooltipText = it }
        }

        if (isTreeCol) {
            val indentPx = (row.depth * indentStepPx).coerceAtLeast(0)
            cellContainer.setAttribute("data-tree-depth", row.depth.toString())
            cellContainer.setAttribute("data-tree-indent-px", indentPx.toString())
            if (indentPx > 0) {
                cellContainer.setPadding(EdgeInsets(left = indentPx))
                cellContainer.setStyleProperty("padding-left", "${indentPx}px")
            }

            if (node.hasBranchDisclosure) {
                val toggleSymbol = if (node.isExpanded) "▼" else "▶"
                val toggleBtn = Button(
                    text = toggleSymbol,
                    type = if (isSelected) BootstrapType.PRIMARY else BootstrapType.SECONDARY,
                    size = BootstrapSize.SM,
                    outline = true
                ).apply {
                    addClass("lazy-grid-tree-toggle-btn")
                    tooltipText = if (node.isExpanded) "Collapse '${node.keyText}'" else "Expand '${node.keyText}'"
                    setOnClick {
                        toggleExpanded(node.id)
                    }
                }
                cellContainer.addChild(toggleBtn)
            } else {
                val leafBadge = Badge(
                    text = node.icon ?: "•",
                    type = BootstrapType.SECONDARY,
                    pill = true
                ).apply {
                    addClass("lazy-grid-tree-leaf-indicator")
                }
                cellContainer.addChild(leafBadge)
            }
        }

        if (cell != null) {
            cell.badgeText?.takeIf { it.isNotEmpty() }?.let { badgeStr ->
                cellContainer.addChild(
                    Badge(
                        text = badgeStr,
                        type = if (node.hasBranchDisclosure) BootstrapType.PRIMARY else BootstrapType.INFO
                    )
                )
            }
            val comp = cell.resolveComponent()
            if (comp != null) {
                if (comp.parent != null && comp.parent !== cellContainer) {
                    (comp.parent as? AbstractContainer)?.removeChild(comp)
                }
                cellContainer.addChild(comp)
            } else {
                val displayStr = cell.text ?: if (isTreeCol) node.keyText else ""
                val textLabel = Label(displayStr).apply {
                    addClass("lazy-grid-tree-cell-label")
                    if (node.hasBranchDisclosure && isTreeCol) {
                        fontWeight = FontWeight.BOLD
                    }
                    setOnClick {
                        selectNode(node.id)
                    }
                }
                cellContainer.addChild(textLabel)
            }
        } else if (isTreeCol) {
            val fallbackLabel = Label(node.keyText).apply {
                addClass("lazy-grid-tree-cell-label")
                if (node.hasBranchDisclosure) {
                    fontWeight = FontWeight.BOLD
                }
                setOnClick {
                    selectNode(node.id)
                }
            }
            cellContainer.addChild(fallbackLabel)
        }

        return cellContainer
    }

    companion object {
        /**
         * Default 4-column schema (`Key`, `Value`, `Type`, `Actions`) used when no custom columns are specified.
         */
        fun defaultTreeColumns(): List<LazyGridTreeColumn> = listOf(
            LazyGridTreeColumn(id = "key", headerText = "Key / Node", widthWeight = 1.6f, minWidthPx = 180, isTreeColumn = true),
            LazyGridTreeColumn(id = "value", headerText = "Value", widthWeight = 1.8f, minWidthPx = 180),
            LazyGridTreeColumn(id = "type", headerText = "Type", widthWeight = 1.0f, minWidthPx = 120),
            LazyGridTreeColumn(id = "actions", headerText = "Actions", widthWeight = 1.4f, minWidthPx = 180)
        )
    }
}
