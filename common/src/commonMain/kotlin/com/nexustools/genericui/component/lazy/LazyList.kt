package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.component.shell.Application
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
    private val cachedItemHolders = linkedMapOf<Int, ViewHolder>()
    private var isRefreshingViewport: Boolean = false

    var adapter: LazyAdapter<ViewHolder>? = null
        set(value) {
            field?.unregisterDataSetObserver(adapterObserver)
            unloadAllVisibleItems()
            clearCachedItemHolders()
            field = value
            value?.registerDataSetObserver(adapterObserver)
            refreshLazyViewport()
        }

    var totalItemCount: Int = 0
        private set
    private var itemBuilder: ((index: Int) -> AbstractComponent)? = null
    private var itemUnloader: ((index: Int, component: AbstractComponent) -> Unit)? = null

    var scrollOffsetPx: Float = 0f
        private set

    var viewportExtentPx: Float = 840f
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

    private fun clearCachedItemHolders() {
        for (holder in cachedItemHolders.values) {
            holder.itemView.dispose()
        }
        cachedItemHolders.clear()
    }

    /**
     * Configures the lazy list using a declarative item count and builder/unloader callbacks.
     */
    fun setItems(
        count: Int,
        onLoadItem: (index: Int) -> AbstractComponent,
        onUnloadItem: ((index: Int, component: AbstractComponent) -> Unit)? = null,
        initialScrollIndex: Int? = null
    ): LazyList = apply {
        unloadAllVisibleItems()
        clearCachedItemHolders()
        this.totalItemCount = count.coerceAtLeast(0)
        this.itemBuilder = onLoadItem
        this.itemUnloader = onUnloadItem
        if (initialScrollIndex != null && initialScrollIndex >= 0) {
            scrollToIndex(initialScrollIndex)
        } else {
            refreshLazyViewport()
        }
    }

    /**
     * Updates the current scroll position and viewport size, immediately unloading items that scrolled
     * out of view and loading items that entered the visible viewport window.
     */
    fun onViewportChanged(scrollOffsetPx: Float, viewportExtentPx: Float): LazyViewportWindow {
        val clampedScroll = scrollOffsetPx.coerceAtLeast(0f)
        val clampedExtent = viewportExtentPx.coerceAtLeast(1f)
        val pending = pendingScrollIndex
        if (pending != null && clampedScroll == 0f) {
            pendingScrollIndex = null
            val total = adapter?.getItemCount() ?: totalItemCount
            val targetIdx = if (total > 0) pending.coerceIn(0, total - 1) else pending.coerceAtLeast(0)
            val contextIdx = (targetIdx - 1).coerceAtLeast(0)
            this.scrollOffsetPx = (contextIdx * estimatedItemExtentPx).coerceAtLeast(0f)
            this.viewportExtentPx = clampedExtent
            val window = refreshLazyViewport()
            typedNative<LazyListNativeImpl<*>>()?.scrollToIndex(targetIdx)
            return window
        }
        if (clampedScroll > 0f) {
            pendingScrollIndex = null
        }
        this.scrollOffsetPx = clampedScroll
        this.viewportExtentPx = clampedExtent
        return refreshLazyViewport()
    }

    fun scrollToIndex(index: Int) {
        val total = adapter?.getItemCount() ?: totalItemCount
        val targetIdx = if (total > 0) index.coerceIn(0, total - 1) else index.coerceAtLeast(0)
        pendingScrollIndex = targetIdx
        val contextIdx = (targetIdx - 1).coerceAtLeast(0)
        val targetOffset = (contextIdx * estimatedItemExtentPx).coerceAtLeast(0f)
        this.scrollOffsetPx = targetOffset
        refreshLazyViewport()
        typedNative<LazyListNativeImpl<*>>()?.scrollToIndex(targetIdx)
    }

    fun refreshLazyViewport(): LazyViewportWindow {
        if (isRefreshingViewport) return activeViewportWindow
        isRefreshingViewport = true
        try {
            val total = adapter?.getItemCount() ?: totalItemCount
            val window = viewportTracker.updateViewport(
                scrollOffsetPx = scrollOffsetPx,
                viewportExtentPx = viewportExtentPx,
                estimatedItemExtentPx = estimatedItemExtentPx,
                totalItems = total,
                columns = 1,
                onUnloadIndex = { idx -> unloadItemAtIndex(idx) },
                onLoadIndex = { idx -> loadItemAtIndex(idx) }
            )
            activeViewportWindow = window

            // Ensure activeHolders remain ordered by ascending item index without detaching/re-attaching
            // already-mounted child views (which would cause DOM churn and scroll jumps on :web).
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

            val impl = typedNative<LazyListNativeImpl<*>>()
            if (impl != null) {
                impl.setEstimatedItemExtent(estimatedItemExtentPx, viewportTracker.overscanItems)
                impl.updateViewportMetrics(
                    scrollOffsetPx = scrollOffsetPx,
                    viewportExtentPx = viewportExtentPx,
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
                if (scrollOffsetPx == 0f && adapter == null && itemUnloader == null && cachedItemHolders.isEmpty() && total > window.lastActiveIndex + 1) {
                    val prewarmEnd = minOf(total - 1, window.lastActiveIndex + 28)
                    prewarmOverscanHolders(window.lastActiveIndex + 1, prewarmEnd)
                }
            }
            return window
        } finally {
            isRefreshingViewport = false
        }
    }

    private fun prewarmOverscanHolders(startIdx: Int, endIdxInclusive: Int) {
        val applyStyles = (resolveActiveFactory() ?: com.nexustools.genericui.core.GenericUI.activeFactory)?.isStyleSheetEngineEnabled != false
        val sheets = if (applyStyles) Application.current?.getStyleSheets() else null
        for (index in startIdx..endIdxInclusive) {
            if (activeHolders.containsKey(index) || cachedItemHolders.containsKey(index)) continue
            val view = itemBuilder?.invoke(index) ?: continue
            val holder = ViewHolder(view, 0).also {
                it.boundPosition = index
                it.isBound = true
            }
            view.setAttribute("data-lazy-index", index.toString())
            view.parent = this
            if (applyStyles && sheets != null) {
                for (sheet in sheets) {
                    sheet.styleSheet.applyToTree(view)
                }
            }
            view.ensureNativeImpl()
            if (cachedItemHolders.size < 64) {
                cachedItemHolders[index] = holder
            }
        }
    }

    private fun loadItemAtIndex(index: Int) {
        if (activeHolders.containsKey(index)) return
        bindHolderForIndex(index)
    }

    private fun bindHolderForIndex(index: Int): ViewHolder {
        val currentAdapter = adapter
        val cached = if (currentAdapter == null && itemUnloader == null) cachedItemHolders.remove(index) else null
        if (cached != null) {
            val insertPos = activeHolders.keys.count { it < index }
            activeHolders[index] = cached
            addChild(cached.itemView, insertPos)
            return cached
        }
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
        holder.itemView.parent = this
        if ((resolveActiveFactory() ?: com.nexustools.genericui.core.GenericUI.activeFactory)?.isStyleSheetEngineEnabled != false) {
            Application.current?.getStyleSheets()?.forEach { sheet ->
                sheet.styleSheet.applyToTree(holder.itemView)
            }
        }
        addChild(holder.itemView, insertPos)
        return holder
    }

    private fun unloadItemAtIndex(index: Int) {
        val holder = activeHolders.remove(index) ?: return
        adapter?.onUnbindViewHolder(holder, index)
        itemUnloader?.invoke(index, holder.itemView)
        removeChild(holder.itemView)
        if (adapter != null) {
            viewHolderPool.release(holder)
        } else if (itemUnloader == null) {
            if (cachedItemHolders.size >= 64) {
                val eldestKey = cachedItemHolders.keys.firstOrNull()
                if (eldestKey != null) {
                    cachedItemHolders.remove(eldestKey)?.itemView?.dispose()
                }
            }
            cachedItemHolders[index] = holder
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

    protected override fun createNativeImpl(factory: NativeFactory): LazyListNativeImpl<*> =
        factory.createLazyList()

    override fun resolveActiveFactory(): NativeFactory? {
        if (!isVisible) return null
        return super.resolveActiveFactory()
            ?: if (nativeImpl != null) com.nexustools.genericui.core.GenericUI.activeFactory else null
    }

    internal override fun ensureNativeImpl(): com.nexustools.genericui.core.NativeImpl<*>? {
        val wasAlreadyCreated = (nativeImpl != null)
        val impl = super.ensureNativeImpl() ?: return null
        if (wasAlreadyCreated && activeHolders.isEmpty() && (adapter?.getItemCount() ?: totalItemCount) > 0) {
            refreshLazyViewport()
        }
        val pending = pendingScrollIndex
        if (pending != null) {
            val total = adapter?.getItemCount() ?: totalItemCount
            val targetIdx = if (total > 0) pending.coerceIn(0, total - 1) else pending.coerceAtLeast(0)
            (impl as? LazyListNativeImpl<*>)?.scrollToIndex(targetIdx)
        }
        return impl
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<LazyListNativeImpl<*>>() ?: return
        impl.setOrientation(orientation)
        impl.setEstimatedItemExtent(estimatedItemExtentPx, viewportTracker.overscanItems)
        impl.setOnViewportScrollChanged { scrollPx, extentPx ->
            if (!isRefreshingViewport) {
                onViewportChanged(scrollPx, extentPx)
            }
        }
        refreshLazyViewport()
        val pending = pendingScrollIndex
        if (pending != null) {
            val total = adapter?.getItemCount() ?: totalItemCount
            val targetIdx = if (total > 0) pending.coerceIn(0, total - 1) else pending.coerceAtLeast(0)
            impl.scrollToIndex(targetIdx)
        }
    }

    override fun dispose() {
        unloadAllVisibleItems()
        clearCachedItemHolders()
        viewHolderPool.clear()
        super.dispose()
    }
}
