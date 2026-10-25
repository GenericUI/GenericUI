package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl

/**
 * Virtualized hierarchical tree view component that flattens only expanded nodes and lazily
 * loads/unloads visible tree rows via [LazyViewportTracker].
 */
class TreeView<T>(
    var estimatedRowHeightPx: Float = 32f,
    overscanRows: Int = 4
) : AbstractComponent(htmlTag = "genericui:treeview") {
    val viewportTracker: LazyViewportTracker = LazyViewportTracker(overscanRows)

    private val _rootNodes = mutableListOf<TreeNode<T>>()
    val rootNodes: List<TreeNode<T>> get() = _rootNodes

    private val flattenedVisibleNodes = mutableListOf<Pair<Int, TreeNode<T>>>()
    private val activeRowNodes = linkedMapOf<Int, TreeNode<T>>()

    var selectedNodeId: String? = null
        set(value) {
            field = value
            typedNative<TreeViewNativeImpl<*>>()?.setSelectedNodeId(value)
        }

    var scrollOffsetPx: Float = 0f
        private set

    var viewportHeightPx: Float = 500f
        private set

    var onNodeSelected: ((TreeNode<T>) -> Unit)? = null
    var onNodeDoubleClicked: ((TreeNode<T>) -> Unit)? = null
    var onNodeContextMenu: ((TreeNode<T>, Float, Float) -> Unit)? = null

    val loadedRowIndices: Set<Int>
        get() = activeRowNodes.keys.toSet()

    fun setRootNodes(roots: List<TreeNode<T>>): TreeView<T> = apply {
        _rootNodes.clear()
        _rootNodes.addAll(roots)
        rebuildFlattenedNodes()
        typedNative<TreeViewNativeImpl<*>>()?.setRootNodes(_rootNodes)
    }

    fun toggleExpanded(node: TreeNode<T>) {
        node.isExpanded = !node.isExpanded
        if (node.isExpanded) {
            node.ensureChildrenLoaded()
        }
        rebuildFlattenedNodes()
        typedNative<TreeViewNativeImpl<*>>()?.setRootNodes(_rootNodes)
    }

    fun onViewportChanged(scrollOffsetPx: Float, viewportHeightPx: Float): LazyViewportWindow {
        this.scrollOffsetPx = scrollOffsetPx.coerceAtLeast(0f)
        this.viewportHeightPx = viewportHeightPx.coerceAtLeast(1f)
        return refreshLazyViewport()
    }

    fun rebuildFlattenedNodes() {
        flattenedVisibleNodes.clear()
        fun traverse(nodes: List<TreeNode<T>>, depth: Int) {
            for (n in nodes) {
                flattenedVisibleNodes.add(depth to n)
                if (n.isExpanded) {
                    n.ensureChildrenLoaded()
                    traverse(n.children, depth + 1)
                }
            }
        }
        traverse(_rootNodes, 0)
        refreshLazyViewport()
    }

    private fun refreshLazyViewport(): LazyViewportWindow =
        viewportTracker.updateViewport(
            scrollOffsetPx = scrollOffsetPx,
            viewportExtentPx = viewportHeightPx,
            estimatedItemExtentPx = estimatedRowHeightPx,
            totalItems = flattenedVisibleNodes.size,
            columns = 1,
            onUnloadIndex = { idx -> activeRowNodes.remove(idx) },
            onLoadIndex = { idx ->
                flattenedVisibleNodes.getOrNull(idx)?.second?.let { activeRowNodes[idx] = it }
            }
        )

    override fun createNativeImpl(factory: NativeFactory): TreeViewNativeImpl<*> =
        factory.createTreeView()

    @Suppress("UNCHECKED_CAST")
    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TreeViewNativeImpl<*>>() ?: return
        impl.setRootNodes(_rootNodes)
        impl.setSelectedNodeId(selectedNodeId)
        impl.setOnNodeSelected { raw ->
            (raw as? TreeNode<T>)?.let {
                selectedNodeId = it.id
                onNodeSelected?.invoke(it)
            }
        }
        impl.setOnNodeDoubleClicked { raw ->
            (raw as? TreeNode<T>)?.let { onNodeDoubleClicked?.invoke(it) }
        }
        impl.setOnNodeContextMenu { raw, x, y ->
            (raw as? TreeNode<T>)?.let { onNodeContextMenu?.invoke(it, x, y) }
        }
    }
}
