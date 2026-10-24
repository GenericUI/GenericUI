package com.nexustools.genericui.component.lazy

/**
 * Hierarchical tree node model used by [TreeView], supporting lazy child expansion.
 */
class TreeNode<T>(
    val id: String,
    var label: String,
    val data: T? = null,
    var iconSvgOrPath: String? = null,
    var isExpanded: Boolean = false,
    val children: MutableList<TreeNode<T>> = mutableListOf(),
    val lazyChildrenLoader: (() -> List<TreeNode<T>>)? = null
) {
    private var lazyLoaded = false

    fun ensureChildrenLoaded() {
        if (!lazyLoaded && lazyChildrenLoader != null) {
            lazyLoaded = true
            children.clear()
            children.addAll(lazyChildrenLoader.invoke())
        }
    }
}
