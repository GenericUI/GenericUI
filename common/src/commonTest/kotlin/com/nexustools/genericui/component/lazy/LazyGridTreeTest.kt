package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.TextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LazyGridTreeTest {

    @Test
    fun testLazyGridTreeHtmlTagAndColumns() {
        val tree = LazyGridTree<String>()
        assertEquals("genericui:lazygridtree", tree.htmlTag)
        assertEquals(4, tree.treeColumns.size)
        assertTrue(tree.treeColumns[0].isTreeColumn)
    }

    @Test
    fun testMultiColumnCollapsibleHierarchyWithTextAndComponentCells() {
        var lazyFactoryInvocations = 0
        val columns = listOf(
            LazyGridTreeColumn(id = "key", headerText = "Property", isTreeColumn = true),
            LazyGridTreeColumn(id = "val", headerText = "Value"),
            LazyGridTreeColumn(id = "action", headerText = "Action")
        )
        val tree = LazyGridTree<String>(treeColumns = columns)

        val rootBranch = LazyGridTreeNode<String>(
            id = "root.window",
            keyText = "window",
            isBranch = true,
            isExpanded = true
        ).apply {
            setTextCell(0, "window", badgeText = "Object")
            setTextCell(1, "{2 keys}")
            setComponentCell(2, Button("Reset"))
        }

        val childTitle = LazyGridTreeNode<String>(
            id = "root.window.title",
            keyText = "title",
            isBranch = false
        ).apply {
            setTextCell(0, "title")
            setComponentCell(1, TextField(value = "GenericUI Studio"))
            setComponentCellFactory(2, tooltipText = "Lazy Action") {
                lazyFactoryInvocations += 1
                Button(" Clear ")
            }
        }

        var lazyBranchLoadedCount = 0
        val lazySubBranch = LazyGridTreeNode<String>(
            id = "root.window.bounds",
            keyText = "bounds",
            isBranch = true,
            isExpanded = false,
            lazyChildrenLoader = {
                lazyBranchLoadedCount += 1
                listOf(
                    LazyGridTreeNode<String>(id = "root.window.bounds.width", keyText = "width").apply {
                        setTextCell(0, "width")
                        setTextCell(1, "1280")
                    },
                    LazyGridTreeNode<String>(id = "root.window.bounds.height", keyText = "height").apply {
                        setTextCell(0, "height")
                        setTextCell(1, "800")
                    }
                )
            }
        )

        rootBranch.addChild(childTitle)
        rootBranch.addChild(lazySubBranch)
        tree.setRootNodes(listOf(rootBranch))

        // Initially rootBranch is expanded and lazySubBranch is collapsed -> 3 visible rows (rootBranch, childTitle, lazySubBranch)
        assertEquals(3, tree.visibleRowCount)
        assertEquals(0, lazyBranchLoadedCount)

        // Verify cell types on childTitle
        assertFalse(childTitle.cells[0].isComponentCell)
        assertTrue(childTitle.cells[1].isComponentCell)
        assertTrue(childTitle.cells[2].isComponentCell)

        // Verify visible viewport rows are materialized and component factory is invoked lazily
        tree.onViewportChanged(scrollOffsetPx = 0f, viewportHeightPx = 300f)
        assertTrue(tree.loadedItemCount > 0)
        assertTrue(lazyFactoryInvocations >= 1)

        // Expand lazySubBranch via toggleExpanded -> loads 2 lazy children -> 5 visible rows
        assertTrue(tree.toggleExpanded("root.window.bounds"))
        assertEquals(1, lazyBranchLoadedCount)
        assertEquals(5, tree.visibleRowCount)

        // Collapse via setExpanded
        assertTrue(tree.setExpanded("root.window.bounds", false))
        assertEquals(3, tree.visibleRowCount)

        // Expand all -> 5 visible rows
        tree.expandAll()
        assertEquals(5, tree.visibleRowCount)

        // Collapse all including roots -> 1 visible row (rootBranch)
        tree.collapseAll(keepRootsExpanded = false)
        assertEquals(1, tree.visibleRowCount)

        // Filter query automatically traverses matching descendants even when collapsed
        tree.filterQuery = "1280"
        assertEquals(3, tree.visibleRowCount) // root.window -> root.window.bounds -> root.window.bounds.width
        assertEquals("root.window.bounds.width", tree.flattenedRows.last().node.id)

        // Clear filter query and expand all
        tree.filterQuery = ""
        tree.expandAll()
        assertEquals(5, tree.visibleRowCount)

        // Select node
        var selectedNode: LazyGridTreeNode<String>? = null
        tree.onNodeSelected = { selectedNode = it }
        assertTrue(tree.selectNode("root.window.title"))
        assertNotNull(selectedNode)
        assertEquals("root.window.title", tree.selectedNodeId)
    }
}
