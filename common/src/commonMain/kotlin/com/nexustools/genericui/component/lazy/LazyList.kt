package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock

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

    private val lazyStateLock: PlatformReentrantLock = PlatformReentrantLock()
    private val activeHolders = linkedMapOf<Int, ViewHolder>()
    private val cachedItemHolders = linkedMapOf<Int, ViewHolder>()
    private var isRefreshingViewport: Boolean = false
    private var lastSyncedImpl: LazyListNativeImpl<*>? = null
    private var lastSyncedItemCount: Int = -1

    internal val needsViewportSync: Boolean
        get() = lazyStateLock.withLock {
            val total = adapter?.getItemCount() ?: totalItemCount
            total > 0 && (activeHolders.isEmpty() || nativeImpl == null || nativeImpl !== lastSyncedImpl)
        }

    var adapter: LazyAdapter<ViewHolder>? = null
        set(value) {
            lazyStateLock.withLock {
                field?.unregisterDataSetObserver(adapterObserver)
                unloadAllVisibleItems()
                clearCachedItemHolders()
                lastSyncedItemCount = -1
                field = value
                value?.registerDataSetObserver(adapterObserver)
                refreshLazyViewport()
            }
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
        get() = lazyStateLock.withLock { activeHolders.keys.toSet() }

    val loadedItemCount: Int
        get() = lazyStateLock.withLock {
            if (activeHolders.isEmpty() && (adapter?.getItemCount() ?: totalItemCount) > 0 && !isRefreshingViewport) {
                refreshLazyViewport()
            }
            activeHolders.size
        }

    private val adapterObserver: () -> Unit = {
        refreshLazyViewport()
    }

    private fun clearCachedItemHolders() {
        val snapshot = runCatching { ArrayList(cachedItemHolders.values) }.getOrElse { emptyList() }
        cachedItemHolders.clear()
        for (holder in snapshot) {
            holder?.itemView?.dispose()
        }
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
        lazyStateLock.withLock {
            isRefreshingViewport = false
            unloadAllVisibleItems()
            clearCachedItemHolders()
            lastSyncedItemCount = -1
            this.totalItemCount = count.coerceAtLeast(0)
            this.itemBuilder = onLoadItem
            this.itemUnloader = onUnloadItem
            if (initialScrollIndex != null && initialScrollIndex >= 0) {
                scrollToIndex(initialScrollIndex)
            } else {
                refreshLazyViewport()
            }
        }
    }

    /**
     * Updates the current scroll position and viewport size, immediately unloading items that scrolled
     * out of view and loading items that entered the visible viewport window.
     */
    fun onViewportChanged(scrollOffsetPx: Float, viewportExtentPx: Float): LazyViewportWindow = lazyStateLock.withLock {
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
            return@withLock window
        }
        if (clampedScroll > 0f) {
            pendingScrollIndex = null
        }
        this.scrollOffsetPx = clampedScroll
        this.viewportExtentPx = clampedExtent
        val impl = typedNative<LazyListNativeImpl<*>>()
        impl?.beginViewportScrollScope()
        if (impl != null) {
            GenericUiThreading.runOnUi { (nativeImpl as? LazyListNativeImpl<*>)?.beginViewportScrollScope() }
        }
        return@withLock try {
            refreshLazyViewport()
        } finally {
            if (impl != null) {
                GenericUiThreading.runOnUi { (nativeImpl as? LazyListNativeImpl<*>)?.endViewportScrollScope() }
            }
            impl?.endViewportScrollScope()
        }
    }

    fun scrollToIndex(index: Int) {
        lazyStateLock.withLock {
            val total = adapter?.getItemCount() ?: totalItemCount
            val targetIdx = if (total > 0) index.coerceIn(0, total - 1) else index.coerceAtLeast(0)
            pendingScrollIndex = targetIdx
            val contextIdx = (targetIdx - 1).coerceAtLeast(0)
            val targetOffset = (contextIdx * estimatedItemExtentPx).coerceAtLeast(0f)
            this.scrollOffsetPx = targetOffset
            refreshLazyViewport()
            typedNative<LazyListNativeImpl<*>>()?.scrollToIndex(targetIdx)
        }
    }

    fun refreshLazyViewport(): LazyViewportWindow = lazyStateLock.withLock {
        if (isRefreshingViewport) return@withLock activeViewportWindow
        isRefreshingViewport = true
        try {
            val total = adapter?.getItemCount() ?: totalItemCount
            val prevWindow = activeViewportWindow
            var mutatedHolders = false
            val window = viewportTracker.updateViewport(
                scrollOffsetPx = scrollOffsetPx,
                viewportExtentPx = viewportExtentPx,
                estimatedItemExtentPx = estimatedItemExtentPx,
                totalItems = total,
                columns = 1,
                onUnloadIndex = { idx ->
                    unloadItemAtIndex(idx)
                    mutatedHolders = true
                },
                onLoadIndex = { idx ->
                    loadItemAtIndex(idx)
                    mutatedHolders = true
                }
            )
            activeViewportWindow = window
            if (total > 0 && window.activeCount > 0 && activeHolders.size < window.activeCount) {
                for (idx in window.firstActiveIndex..window.lastActiveIndex) {
                    if (!activeHolders.containsKey(idx)) {
                        loadItemAtIndex(idx)
                        mutatedHolders = true
                    }
                }
            }

            // Ensure activeHolders remain ordered by ascending item index without detaching/re-attaching
            // already-mounted child views (which would cause DOM churn and scroll jumps on :web).
            if (activeHolders.size > 1) {
                val entrySnapshot = runCatching { activeHolders.entries.map { it.key to it.value } }.getOrElse { emptyList() }
                val sortedEntries = entrySnapshot.sortedBy { it.first }
                var orderChanged = false
                var idx = 0
                for ((key, _) in entrySnapshot) {
                    if (idx >= sortedEntries.size || key != sortedEntries[idx].first) {
                        orderChanged = true
                        break
                    }
                    idx++
                }
                if (orderChanged) {
                    mutatedHolders = true
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
                val needsItemSync = impl !== lastSyncedImpl ||
                    total != lastSyncedItemCount ||
                    prevWindow.firstActiveIndex != window.firstActiveIndex ||
                    prevWindow.lastActiveIndex != window.lastActiveIndex ||
                    mutatedHolders
                if (needsItemSync) {
                    impl.setItemCount(total) { index ->
                        val holder = activeHolders[index] ?: bindHolderForIndex(index)
                        holder.itemView.ensureNativeImpl()
                            ?: (resolveActiveFactory() ?: com.nexustools.genericui.core.GenericUI.activeFactory)!!.createContainer("lazy-item")
                    }
                    lastSyncedImpl = impl
                    lastSyncedItemCount = total
                }
                if (scrollOffsetPx == 0f && adapter == null && itemUnloader == null && cachedItemHolders.isEmpty() && total > window.lastActiveIndex + 1) {
                    val prewarmEnd = minOf(total - 1, window.lastActiveIndex + 28)
                    prewarmOverscanHolders(window.lastActiveIndex + 1, prewarmEnd)
                }
            }
            return@withLock window
        } finally {
            isRefreshingViewport = false
        }
    }

    private fun resolveActiveItemCascadeSheet(): com.nexustools.genericui.style.css.CssStyleSheet? {
        if ((resolveActiveFactory() ?: com.nexustools.genericui.core.GenericUI.activeFactory)?.isStyleSheetEngineEnabled == false) {
            return null
        }
        return resolveEffectiveAncestorStyleSheet() ?: Application.current?.buildCombinedActiveStyleSheet()
    }

    protected override fun propagateInheritedFocusableToExtraChildren(ancestorAllowsFocus: Boolean) {
        val cachedSnapshot = lazyStateLock.withLock {
            runCatching { ArrayList(cachedItemHolders.values) }.getOrElse { emptyList() }
        }
        for (holder in cachedSnapshot) {
            holder.itemView.updateInheritedFocusable(ancestorAllowsFocus)
        }
    }

    private fun prewarmOverscanHolders(startIdx: Int, endIdxInclusive: Int) {
        val effectiveSheet = resolveActiveItemCascadeSheet()
        val vw = lastStyleSheetViewportWidthPx
        for (index in startIdx..endIdxInclusive) {
            if (activeHolders.containsKey(index) || cachedItemHolders.containsKey(index)) continue
            val view = itemBuilder?.invoke(index) ?: continue
            val holder = ViewHolder(view, 0).also {
                it.boundPosition = index
                it.isBound = true
            }
            view.setAttribute("data-lazy-index", index.toString())
            view.parent = this
            view.recomputeInheritedFocusableFromParent()
            effectiveSheet?.applyToTree(view, vw)
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
            val insertPos = runCatching {
                @Suppress("UNCHECKED_CAST")
                (activeHolders.keys.toTypedArray() as Array<Int?>).count { it != null && it < index }
            }.getOrElse { 0 }
            activeHolders[index] = cached
            addChild(cached.itemView, insertPos)
            cached.itemView.recomputeInheritedFocusableFromParent()
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
        val insertPos = runCatching {
            @Suppress("UNCHECKED_CAST")
            (activeHolders.keys.toTypedArray() as Array<Int?>).count { it != null && it < index }
        }.getOrElse { 0 }
        activeHolders[index] = holder
        holder.itemView.parent = this
        resolveActiveItemCascadeSheet()?.applyToTree(holder.itemView, lastStyleSheetViewportWidthPx)
        addChild(holder.itemView, insertPos)
        holder.itemView.recomputeInheritedFocusableFromParent()
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
        lazyStateLock.withLock {
            lastSyncedItemCount = -1
            viewportTracker.unloadAll { idx -> unloadItemAtIndex(idx) }
            val remaining = runCatching { ArrayList<Int?>(activeHolders.keys).filterNotNull() }.getOrElse { emptyList() }
            for (idx in remaining) {
                unloadItemAtIndex(idx)
            }
        }
    }

    protected override fun createNativeImpl(factory: NativeFactory): LazyListNativeImpl<*> =
        factory.createLazyList()

    protected override fun onAttachNative(impl: com.nexustools.genericui.core.NativeImpl<*>) {
        super.onAttachNative(impl)
        lazyStateLock.withLock {
            if ((adapter?.getItemCount() ?: totalItemCount) > 0 && impl !== lastSyncedImpl) {
                refreshLazyViewport()
            }
        }
    }

    override fun resolveActiveFactory(): NativeFactory? {
        if (!isVisible) return null
        return super.resolveActiveFactory()
            ?: if (nativeImpl != null) com.nexustools.genericui.core.GenericUI.activeFactory else null
    }

    override fun ensureNativeImpl(): com.nexustools.genericui.core.NativeImpl<*>? {
        val wasAlreadyCreated = (nativeImpl != null)
        val impl = super.ensureNativeImpl() ?: return null
        lazyStateLock.withLock {
            if (wasAlreadyCreated && activeHolders.isEmpty() && (adapter?.getItemCount() ?: totalItemCount) > 0) {
                refreshLazyViewport()
            }
            val pending = pendingScrollIndex
            if (pending != null) {
                val total = adapter?.getItemCount() ?: totalItemCount
                val targetIdx = if (total > 0) pending.coerceIn(0, total - 1) else pending.coerceAtLeast(0)
                (impl as? LazyListNativeImpl<*>)?.scrollToIndex(targetIdx)
            }
        }
        return impl
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<LazyListNativeImpl<*>>() ?: return
        lazyStateLock.withLock {
            lastSyncedItemCount = -1
            impl.setOrientation(orientation)
            impl.setEstimatedItemExtent(estimatedItemExtentPx, viewportTracker.overscanItems)
            impl.setOnViewportScrollChanged { scrollPx, extentPx ->
                if (GenericUI.activeFactory != null &&
                    GenericUiThreading.isSplitThreadingEnabled &&
                    !GenericUiThreading.isOnLogicThread()
                ) {
                    GenericUiThreading.runOnLogic {
                        if (!isDisposed && !isRefreshingViewport) {
                            onViewportChanged(scrollPx, extentPx)
                        }
                    }
                } else if (!isRefreshingViewport) {
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
    }

    override fun dispose() {
        lazyStateLock.withLock {
            unloadAllVisibleItems()
            clearCachedItemHolders()
            viewHolderPool.clear()
        }
        super.dispose()
    }
}
