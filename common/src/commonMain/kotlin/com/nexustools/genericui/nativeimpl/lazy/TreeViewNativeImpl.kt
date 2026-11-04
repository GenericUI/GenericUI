package com.nexustools.genericui.nativeimpl.lazy

import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.core.NativeImpl

interface TreeViewNativeImpl<out W> : NativeImpl<W> {
    fun setRootNodes(roots: List<TreeNode<*>>)
    fun setSelectedNodeId(nodeId: String?)
    fun setOnNodeSelected(listener: ((TreeNode<*>) -> Unit)?)
    fun setOnNodeDoubleClicked(listener: ((TreeNode<*>) -> Unit)?)
    fun setOnNodeContextMenu(listener: ((TreeNode<*>, Float, Float) -> Unit)?)
    fun setEstimatedRowHeight(rowHeightPx: Float, overscanRows: Int = 4) {}
    fun updateViewportMetrics(
        scrollOffsetPx: Float,
        viewportHeightPx: Float,
        firstActiveIndex: Int,
        lastActiveIndex: Int,
        leadingSpacerPx: Float,
        trailingSpacerPx: Float,
        totalContentExtentPx: Float
    ) {}
    fun setOnViewportScrollChanged(listener: ((scrollOffsetPx: Float, viewportHeightPx: Float) -> Unit)?) {}
}
