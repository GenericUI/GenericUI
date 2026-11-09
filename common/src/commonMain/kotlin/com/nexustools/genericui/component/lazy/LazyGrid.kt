package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl

/**
 * Multi-column virtualized lazy grid component.
 *
 * Guarantees genuine lazy loading and unloading across all 8 platforms:
 * only cells within the visible rows (+ overscan rows) are loaded and bound, while cells scrolling
 * out of view are immediately unbound and recycled into [viewHolderPool].
 */
open class LazyGrid(
    columns: Int = 3,
    var estimatedRowHeightPx: Float = 180f,
    overscanRows: Int = 2
) : AbstractContainer() {
    init {
        htmlTag = "genericui:lazygrid"
    }

    var columns: Int = columns.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
            typedNative<LazyGridNativeImpl<*>>()?.setColumns(field)
            refreshLazyViewport()
        }

    var minColumnWidthPx: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            typedNative<LazyGridNativeImpl<*>>()?.setMinColumnWidthPx(field)
            refreshLazyViewport()
        }

    fun resolveEffectiveColumns(availableWidthPx: Int): Int {
        if (minColumnWidthPx > 0 && availableWidthPx > 0) {
            return maxOf(1, minOf(columns, availableWidthPx / minColumnWidthPx))
        }
        return columns
    }

    val viewHolderPool: ViewHolderPool = ViewHolderPool()
    val viewportTracker: LazyViewportTracker = LazyViewportTracker(overscanRows)

    private val activeHolders = linkedMapOf<Int, ViewHolder>()
    private var isRefreshingViewport: Boolean = false

    var adapter: LazyAdapter<ViewHolder>? = null
        set(value) {
            field?.unregisterDataSetObserver(adapterObserver)
            unloadAllVisibleItems()
            field = value
            value?.registerDataSetObserver(adapterObserver)
            refreshLazyViewport()
        }

    private var itemCount: Int = 0
    private var itemBuilder: ((index: Int) -> AbstractComponent)? = null
    private var itemUnloader: ((index: Int, component: AbstractComponent) -> Unit)? = null

    var scrollOffsetPx: Float = 0f
        private set

    var viewportHeightPx: Float = 600f
        private set

    internal var pendingScrollIndex: Int? = null

    var activeViewportWindow: LazyViewportWindow = LazyViewportWindow.EMPTY
        private set

    val loadedItemIndices: Set<Int>
        get() = activeHolders.keys.toSet()

    val loadedItemCount: Int
        get() = activeHolders.size

    private val adapterObserver: () -> Unit = {
        refreshLazyViewport()
    }

    fun setItems(
        count: Int,
        onLoadItem: (index: Int) -> AbstractComponent,
        onUnloadItem: ((index: Int, component: AbstractComponent) -> Unit)? = null,
        initialScrollIndex: Int? = null
    ): LazyGrid = apply {
        unloadAllVisibleItems()
        this.itemCount = count.coerceAtLeast(0)
        this.itemBuilder = onLoadItem
        this.itemUnloader = onUnloadItem
        if (initialScrollIndex != null && initialScrollIndex >= 0) {
            scrollToIndex(initialScrollIndex)
        } else {
            refreshLazyViewport()
        }
    }

    fun onViewportChanged(scrollOffsetPx: Float, viewportHeightPx: Float): LazyViewportWindow {
        val clampedScroll = scrollOffsetPx.coerceAtLeast(0f)
        val clampedExtent = viewportHeightPx.coerceAtLeast(1f)
        val pending = pendingScrollIndex
        if (pending != null && clampedScroll == 0f) {
            pendingScrollIndex = null
            val total = adapter?.getItemCount() ?: itemCount
            val targetIdx = if (total > 0) pending.coerceIn(0, total - 1) else pending.coerceAtLeast(0)
            val contextRow = ((targetIdx / columns) - 1).coerceAtLeast(0)
            this.scrollOffsetPx = (contextRow * estimatedRowHeightPx).coerceAtLeast(0f)
            this.viewportHeightPx = clampedExtent
            val window = refreshLazyViewport()
            typedNative<LazyGridNativeImpl<*>>()?.scrollToIndex(targetIdx)
            return window
        }
        if (clampedScroll > 0f) {
            pendingScrollIndex = null
        }
        this.scrollOffsetPx = clampedScroll
        this.viewportHeightPx = clampedExtent
        return refreshLazyViewport()
    }

    fun scrollToIndex(index: Int) {
        val total = adapter?.getItemCount() ?: itemCount
        val targetIdx = if (total > 0) index.coerceIn(0, total - 1) else index.coerceAtLeast(0)
        pendingScrollIndex = targetIdx
        val contextRow = ((targetIdx / columns) - 1).coerceAtLeast(0)
        this.scrollOffsetPx = (contextRow * estimatedRowHeightPx).coerceAtLeast(0f)
        refreshLazyViewport()
        typedNative<LazyGridNativeImpl<*>>()?.scrollToIndex(targetIdx)
    }

    fun refreshLazyViewport(): LazyViewportWindow {
        if (isRefreshingViewport) return activeViewportWindow
        isRefreshingViewport = true
        try {
            val total = adapter?.getItemCount() ?: itemCount
            val window = viewportTracker.updateViewport(
                scrollOffsetPx = scrollOffsetPx,
                viewportExtentPx = viewportHeightPx,
                estimatedItemExtentPx = estimatedRowHeightPx,
                totalItems = total,
                columns = columns,
                onUnloadIndex = { idx -> unloadItemAtIndex(idx) },
                onLoadIndex = { idx -> loadItemAtIndex(idx) }
            )
            activeViewportWindow = window

            if (activeHolders.size > 1) {
                val sortedEntries = activeHolders.entries.sortedBy { it.key }.map { it.key to it.value }
                var orderChanged = false
                var idx = 0
                for (key in activeHolders.keys) {
                    if (key != sortedEntries[idx].first) {
                        orderChanged = true
                        break
                    }
                    idx++
                }
                if (orderChanged) {
                    activeHolders.clear()
                    for ((k, v) in sortedEntries) {
                        activeHolders[k] = v
                    }
                    reorderChildrenInMemory(sortedEntries.map { it.second.itemView })
                }
            }

            val impl = typedNative<LazyGridNativeImpl<*>>()
            if (impl != null) {
                impl.setEstimatedRowExtent(estimatedRowHeightPx, viewportTracker.overscanItems)
                impl.updateViewportMetrics(
                    scrollOffsetPx = scrollOffsetPx,
                    viewportExtentPx = viewportHeightPx,
                    firstActiveIndex = window.firstActiveIndex,
                    lastActiveIndex = window.lastActiveIndex,
                    leadingSpacerPx = window.leadingSpacerPx,
                    trailingSpacerPx = window.trailingSpacerPx,
                    totalContentExtentPx = window.totalContentExtentPx
                )
                impl.setItemCount(total) { index ->
                    val holder = activeHolders[index] ?: bindHolderForIndex(index)
                    holder.itemView.ensureNativeImpl()
                        ?: (resolveActiveFactory() ?: com.nexustools.genericui.core.GenericUI.activeFactory)!!.createContainer("lazy-item")
                }
            }
            return window
        } finally {
            isRefreshingViewport = false
        }
    }

    private fun loadItemAtIndex(index: Int) {
        if (activeHolders.containsKey(index)) return
        bindHolderForIndex(index)
    }

    private fun bindHolderForIndex(index: Int): ViewHolder {
        val currentAdapter = adapter
        val holder = if (currentAdapter != null) {
            val viewType = currentAdapter.getItemViewType(index)
            val pooled = viewHolderPool.acquire(viewType) ?: currentAdapter.onCreateViewHolder(viewType)
            pooled.boundPosition = index
            pooled.isBound = true
            currentAdapter.onBindViewHolder(pooled, index)
            pooled
        } else {
            val view = itemBuilder?.invoke(index) ?: com.nexustools.genericui.component.layout.Box()
            ViewHolder(view, 0).also {
                it.boundPosition = index
                it.isBound = true
            }
        }
        holder.itemView.setAttribute("data-lazy-index", index.toString())
        val insertPos = activeHolders.keys.count { it < index }
        activeHolders[index] = holder
        addChild(holder.itemView, insertPos)
        if ((resolveActiveFactory() ?: com.nexustools.genericui.core.GenericUI.activeFactory)?.isStyleSheetEngineEnabled != false) {
            Application.current?.getStyleSheets()?.forEach { sheet ->
                sheet.styleSheet.applyToTree(holder.itemView)
            }
        }
        return holder
    }

    private fun unloadItemAtIndex(index: Int) {
        val holder = activeHolders.remove(index) ?: return
        adapter?.onUnbindViewHolder(holder, index)
        itemUnloader?.invoke(index, holder.itemView)
        removeChild(holder.itemView)
        if (adapter != null) {
            viewHolderPool.release(holder)
        } else {
            holder.itemView.dispose()
        }
    }

    fun unloadAllVisibleItems() {
        viewportTracker.unloadAll { idx -> unloadItemAtIndex(idx) }
        val remaining = activeHolders.keys.toList()
        for (idx in remaining) {
            unloadItemAtIndex(idx)
        }
    }

    protected override fun createNativeImpl(factory: NativeFactory): LazyGridNativeImpl<*> =
        factory.createLazyGrid()

    override fun resolveActiveFactory(): NativeFactory? {
        if (!isVisible) return null
        return super.resolveActiveFactory()
            ?: if (nativeImpl != null) com.nexustools.genericui.core.GenericUI.activeFactory else null
    }

    internal override fun ensureNativeImpl(): com.nexustools.genericui.core.NativeImpl<*>? {
        val wasAlreadyCreated = (nativeImpl != null)
        val impl = super.ensureNativeImpl() ?: return null
        if (wasAlreadyCreated) {
            refreshLazyViewport()
        }
        val pending = pendingScrollIndex
        if (pending != null) {
            val total = adapter?.getItemCount() ?: itemCount
            val targetIdx = if (total > 0) pending.coerceIn(0, total - 1) else pending.coerceAtLeast(0)
            (impl as? LazyGridNativeImpl<*>)?.scrollToIndex(targetIdx)
        }
        return impl
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<LazyGridNativeImpl<*>>() ?: return
        impl.setColumns(columns)
        impl.setMinColumnWidthPx(minColumnWidthPx)
        impl.setEstimatedRowExtent(estimatedRowHeightPx, viewportTracker.overscanItems)
        impl.setOnViewportScrollChanged { scrollPx, extentPx ->
            if (!isRefreshingViewport) {
                onViewportChanged(scrollPx, extentPx)
            }
        }
        refreshLazyViewport()
        val pending = pendingScrollIndex
        if (pending != null) {
            val total = adapter?.getItemCount() ?: itemCount
            val targetIdx = if (total > 0) pending.coerceIn(0, total - 1) else pending.coerceAtLeast(0)
            impl.scrollToIndex(targetIdx)
        }
    }

    override fun dispose() {
        unloadAllVisibleItems()
        viewHolderPool.clear()
        super.dispose()
    }
}
