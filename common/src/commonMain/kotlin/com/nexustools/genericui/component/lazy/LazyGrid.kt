package com.nexustools.genericui.component.lazy

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

    val viewHolderPool: ViewHolderPool = ViewHolderPool()
    val viewportTracker: LazyViewportTracker = LazyViewportTracker(overscanRows)

    private val activeHolders = linkedMapOf<Int, ViewHolder>()

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
        onUnloadItem: ((index: Int, component: AbstractComponent) -> Unit)? = null
    ): LazyGrid = apply {
        unloadAllVisibleItems()
        this.itemCount = count.coerceAtLeast(0)
        this.itemBuilder = onLoadItem
        this.itemUnloader = onUnloadItem
        refreshLazyViewport()
    }

    fun onViewportChanged(scrollOffsetPx: Float, viewportHeightPx: Float): LazyViewportWindow {
        this.scrollOffsetPx = scrollOffsetPx.coerceAtLeast(0f)
        this.viewportHeightPx = viewportHeightPx.coerceAtLeast(1f)
        return refreshLazyViewport()
    }

    fun scrollToIndex(index: Int) {
        val row = index.coerceAtLeast(0) / columns
        onViewportChanged(row * estimatedRowHeightPx, viewportHeightPx)
        typedNative<LazyGridNativeImpl<*>>()?.scrollToIndex(index)
    }

    fun refreshLazyViewport(): LazyViewportWindow {
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

        val impl = typedNative<LazyGridNativeImpl<*>>()
        if (impl != null) {
            impl.setItemCount(total) { index ->
                val holder = activeHolders[index] ?: bindHolderForIndex(index)
                holder.itemView.ensureNativeImpl() ?: resolveActiveFactory()!!.createContainer("lazy-item")
            }
        }
        return window
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
        activeHolders[index] = holder
        addChild(holder.itemView)
        return holder
    }

    private fun unloadItemAtIndex(index: Int) {
        val holder = activeHolders.remove(index) ?: return
        adapter?.onUnbindViewHolder(holder, index)
        itemUnloader?.invoke(index, holder.itemView)
        removeChild(holder.itemView)
        viewHolderPool.release(holder)
    }

    fun unloadAllVisibleItems() {
        viewportTracker.unloadAll { idx -> unloadItemAtIndex(idx) }
        val remaining = activeHolders.keys.toList()
        for (idx in remaining) {
            unloadItemAtIndex(idx)
        }
    }

    override fun createNativeImpl(factory: NativeFactory): LazyGridNativeImpl<*> =
        factory.createLazyGrid()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<LazyGridNativeImpl<*>>() ?: return
        impl.setColumns(columns)
        refreshLazyViewport()
    }

    override fun dispose() {
        unloadAllVisibleItems()
        viewHolderPool.clear()
        super.dispose()
    }
}
