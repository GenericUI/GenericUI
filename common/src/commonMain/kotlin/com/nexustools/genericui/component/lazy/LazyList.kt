package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl

/**
 * True virtualized lazy list component ([Orientation.VERTICAL] or [Orientation.HORIZONTAL]).
 *
 * Guarantees genuine lazy loading and unloading across all 8 platforms:
 * - Only items within the active visible viewport window (`[firstActiveIndex .. lastActiveIndex]`)
 *   are materialized, bound (`onBindViewHolder` / `onLoadItem`), and attached.
 * - As soon as an item scrolls out of the visible viewport window, it is unbound (`onUnbindViewHolder` /
 *   `onUnloadItem`), its native widget is detached/recycled into [viewHolderPool], and memory is reclaimed.
 */
open class LazyList(
    orientation: Orientation = Orientation.VERTICAL,
    var estimatedItemExtentPx: Float = 56f,
    overscanItems: Int = 3
) : AbstractContainer() {
    init {
        htmlTag = "genericui:lazylist"
    }

    var orientation: Orientation = orientation
        set(value) {
            field = value
            typedNative<LazyListNativeImpl<*>>()?.setOrientation(value)
        }

    val viewHolderPool: ViewHolderPool = ViewHolderPool()
    val viewportTracker: LazyViewportTracker = LazyViewportTracker(overscanItems)

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

    var viewportExtentPx: Float = 600f
        private set

    val loadedItemIndices: Set<Int>
        get() = activeHolders.keys.toSet()

    val loadedItemCount: Int
        get() = activeHolders.size

    private val adapterObserver: () -> Unit = {
        refreshLazyViewport()
    }

    /**
     * Configures the lazy list using a declarative item count and builder/unloader callbacks.
     */
    fun setItems(
        count: Int,
        onLoadItem: (index: Int) -> AbstractComponent,
        onUnloadItem: ((index: Int, component: AbstractComponent) -> Unit)? = null
    ): LazyList = apply {
        unloadAllVisibleItems()
        this.itemCount = count.coerceAtLeast(0)
        this.itemBuilder = onLoadItem
        this.itemUnloader = onUnloadItem
        refreshLazyViewport()
    }

    /**
     * Updates the current scroll position and viewport size, immediately unloading items that scrolled
     * out of view and loading items that entered the visible viewport window.
     */
    fun onViewportChanged(scrollOffsetPx: Float, viewportExtentPx: Float): LazyViewportWindow {
        this.scrollOffsetPx = scrollOffsetPx.coerceAtLeast(0f)
        this.viewportExtentPx = viewportExtentPx.coerceAtLeast(1f)
        return refreshLazyViewport()
    }

    fun scrollToIndex(index: Int) {
        val targetOffset = (index.coerceAtLeast(0) * estimatedItemExtentPx)
        onViewportChanged(targetOffset, viewportExtentPx)
        typedNative<LazyListNativeImpl<*>>()?.scrollToIndex(index)
    }

    fun refreshLazyViewport(): LazyViewportWindow {
        val total = adapter?.getItemCount() ?: itemCount
        val window = viewportTracker.updateViewport(
            scrollOffsetPx = scrollOffsetPx,
            viewportExtentPx = viewportExtentPx,
            estimatedItemExtentPx = estimatedItemExtentPx,
            totalItems = total,
            columns = 1,
            onUnloadIndex = { idx -> unloadItemAtIndex(idx) },
            onLoadIndex = { idx -> loadItemAtIndex(idx) }
        )

        val impl = typedNative<LazyListNativeImpl<*>>()
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

    override fun createNativeImpl(factory: NativeFactory): LazyListNativeImpl<*> =
        factory.createLazyList()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<LazyListNativeImpl<*>>() ?: return
        impl.setOrientation(orientation)
        refreshLazyViewport()
    }

    override fun dispose() {
        unloadAllVisibleItems()
        viewHolderPool.clear()
        super.dispose()
    }
}
