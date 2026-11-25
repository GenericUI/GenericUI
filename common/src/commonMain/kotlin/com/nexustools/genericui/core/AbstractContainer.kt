package com.nexustools.genericui.core

import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.transition.ContainerRenderCache
import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock
import kotlin.concurrent.Volatile

/**
 * Abstract base class for all components that can contain child components (`AbstractContainer`).
 *
 * Includes a Window-Manager-style [renderCache] ([ContainerRenderCache]) that pre-renders the
 * container's contents into an offscreen [ImageBuffer] (or native hardware layer) during CSS
 * transitions and animates the cached buffer across frames so child components only re-render
 * when their contents actually change during animation.
 */
abstract class AbstractContainer(
    componentClassPrefix: String = ""
) : AbstractComponent(componentClassPrefix) {

    val renderCache: ContainerRenderCache = ContainerRenderCache()

    private val containerChildrenLock: PlatformReentrantLock = PlatformReentrantLock()

    @Volatile
    private var _children: List<AbstractComponent> = emptyList()
    val children: List<AbstractComponent>
        get() = _children

    private val unattachedRealizedChildren: HashSet<AbstractComponent> = HashSet()
    private val attachedNativeChildren: MutableList<NativeImpl<*>> = mutableListOf()
    private var attachedContainerNativeImpl: AbstractContainerNativeImpl<*>? = null
    @Volatile
    private var childrenReconcileDirty: Boolean = true
    private var containerChildrenFocusableState: Boolean = true

    internal override fun isFocusableStateInternal(): Boolean =
        containerChildrenFocusableState && inheritedFocusableState

    override fun setFocusable(focusable: Boolean) {
        if (containerChildrenFocusableState == focusable) return
        val prevEffective = containerChildrenFocusableState && inheritedFocusableState
        containerChildrenFocusableState = focusable
        val nextEffective = containerChildrenFocusableState && inheritedFocusableState
        if (prevEffective != nextEffective) {
            if (supportsFocus) {
                onEffectiveFocusableChanged(nextEffective)
            }
            propagateInheritedFocusableToChildren(nextEffective)
        }
    }

    internal override fun effectiveDescendantFocusAllowed(): Boolean =
        containerChildrenFocusableState && inheritedFocusableState

    internal override fun updateInheritedFocusable(ancestorAllowsFocus: Boolean) {
        val prevEffective = containerChildrenFocusableState && inheritedFocusableState
        inheritedFocusableState = ancestorAllowsFocus
        val nextEffective = containerChildrenFocusableState && inheritedFocusableState
        if (prevEffective != nextEffective) {
            if (supportsFocus) {
                onEffectiveFocusableChanged(nextEffective)
            }
            propagateInheritedFocusableToChildren(nextEffective)
        }
    }

    private fun propagateInheritedFocusableToChildren(allow: Boolean) {
        val snapshot = containerChildrenLock.withLock { _children }
        for (child in snapshot) {
            child.updateInheritedFocusable(allow)
        }
        propagateInheritedFocusableToExtraChildren(allow)
    }

    protected open fun propagateInheritedFocusableToExtraChildren(ancestorAllowsFocus: Boolean) {}

    internal fun markChildrenReconcileDirty() {
        var cur: AbstractComponent? = this
        while (cur != null) {
            if (cur is AbstractContainer) {
                if (cur.childrenReconcileDirty) break
                cur.childrenReconcileDirty = true
            }
            cur = cur.parent
        }
    }

    internal fun reorderChildrenInMemory(orderedChildren: List<AbstractComponent>) {
        containerChildrenLock.withLock {
            val deduped = ArrayList<AbstractComponent>(orderedChildren.size)
            for (child in orderedChildren) {
                if (deduped.none { it === child }) {
                    deduped.add(child)
                }
            }
            _children = deduped
        }
        markChildrenReconcileDirty()
        markRenderCacheDirty()
    }

    fun markRenderCacheDirty() {
        renderCache.markDirty()
        (parent as? AbstractContainer)?.markRenderCacheDirty()
    }

    fun prerenderToBuffer(): ImageBuffer =
        renderCache.ensurePrerenderedBuffer(this)

    open fun addChild(child: AbstractComponent, index: Int = -1) {
        if (child.parent != null && child.parent !== this) {
            (child.parent as? AbstractContainer)?.removeChild(child)
        }
        var movedExisting = false
        containerChildrenLock.withLock {
            child.parent = this
            val current = _children
            val next = ArrayList<AbstractComponent>(current.size + 1)
            var oldIndex = -1
            for ((i, item) in current.withIndex()) {
                if (item === child) {
                    oldIndex = i
                } else {
                    next.add(item)
                }
            }
            val targetIndex = if (index < 0 || index >= next.size) next.size else index
            next.add(targetIndex, child)
            _children = next
            if (oldIndex >= 0 && oldIndex != targetIndex) {
                movedExisting = true
            }
        }
        markChildrenReconcileDirty()
        markRenderCacheDirty()
        child.dispatchAncestorHierarchyChanged()
        val parentImpl = typedNative<AbstractContainerNativeImpl<*>>()
        if (parentImpl != null) {
            if (child.isVisible) {
                val childImpl = child.ensureNativeImpl()
                if (childImpl != null) {
                    if (movedExisting) {
                        containerChildrenLock.withLock {
                            attachedNativeChildren.removeAll { it === childImpl }
                        }
                    }
                    attachRealizedChildToNative(child, childImpl)
                }
            } else if (child.nativeImpl != null) {
                containerChildrenLock.withLock {
                    unattachedRealizedChildren.add(child)
                }
            }
        } else if (child.nativeImpl != null) {
            containerChildrenLock.withLock {
                unattachedRealizedChildren.add(child)
            }
        }
    }

    open fun removeChild(child: AbstractComponent): Boolean {
        var removed = false
        var detachedImpl: NativeImpl<*>? = null
        containerChildrenLock.withLock {
            val current = _children
            if (!current.contains(child)) return false
            val next = ArrayList<AbstractComponent>(current.size)
            for (item in current) {
                if (item === child) {
                    removed = true
                } else {
                    next.add(item)
                }
            }
            if (removed) {
                _children = next
                child.parent = null
                unattachedRealizedChildren.remove(child)
                detachedImpl = child.nativeImpl
                val impl = detachedImpl
                if (impl != null) {
                    attachedNativeChildren.removeAll { it === impl }
                }
            }
        }
        if (removed) {
            markChildrenReconcileDirty()
            markRenderCacheDirty()
            child.dispatchAncestorHierarchyChanged()
            val childImpl = detachedImpl
            if (childImpl != null) {
                typedNative<AbstractContainerNativeImpl<*>>()?.removeChildNative(childImpl)
            }
        }
        return removed
    }

    open fun clearChildren() {
        containerChildrenLock.withLock {
            val current = _children
            _children = emptyList()
            unattachedRealizedChildren.clear()
            attachedNativeChildren.clear()
            for (child in current) {
                child.parent = null
            }
        }
        markChildrenReconcileDirty()
        markRenderCacheDirty()
        typedNative<AbstractContainerNativeImpl<*>>()?.clearChildrenNative()
    }

    private fun computeNativeInsertionIndexLocked(child: AbstractComponent): Int {
        var precedingAttachedCount = 0
        for (sibling in _children) {
            if (sibling === child) break
            val siblingImpl = sibling.nativeImpl
            if (siblingImpl != null && attachedNativeChildren.any { it === siblingImpl }) {
                precedingAttachedCount++
            }
        }
        return precedingAttachedCount
    }

    internal fun attachRealizedChildToNative(child: AbstractComponent, childImpl: NativeImpl<*>) {
        val containerImpl = typedNative<AbstractContainerNativeImpl<*>>() ?: return
        containerChildrenLock.withLock {
            if (child.parent !== this || !child.visibleStateOrIsVisible() || child.nativeImpl !== childImpl) return
            if (attachedContainerNativeImpl !== containerImpl) {
                attachedNativeChildren.clear()
                attachedContainerNativeImpl = containerImpl
            }
            unattachedRealizedChildren.remove(child)
            if (attachedNativeChildren.any { it === childImpl }) return
            val nativeIndex = computeNativeInsertionIndexLocked(child)
            attachedNativeChildren.add(nativeIndex.coerceIn(0, attachedNativeChildren.size), childImpl)
            containerImpl.addChildNative(childImpl, nativeIndex)
        }
    }

    private fun AbstractComponent.visibleStateOrIsVisible(): Boolean = isVisible

    internal fun reconcileVisibleChildrenNative(containerImpl: AbstractContainerNativeImpl<*>) {
        childrenReconcileDirty = false
        val snapshot = containerChildrenLock.withLock { _children }
        val realizedVisible = ArrayList<Pair<AbstractComponent, NativeImpl<*>>>(snapshot.size)
        var anyPendingDescendant = false
        for (child in snapshot) {
            if (child.isVisible) {
                val childImpl = child.ensureNativeImpl()
                if (childImpl != null) {
                    realizedVisible.add(child to childImpl)
                    if ((child as? AbstractContainer)?.childrenReconcileDirty == true) {
                        anyPendingDescendant = true
                    }
                } else {
                    anyPendingDescendant = true
                }
            }
        }
        if (anyPendingDescendant) {
            childrenReconcileDirty = true
        }
        containerChildrenLock.withLock {
            if (typedNative<AbstractContainerNativeImpl<*>>() !== containerImpl) {
                childrenReconcileDirty = true
                return
            }
            if (attachedContainerNativeImpl !== containerImpl) {
                attachedNativeChildren.clear()
                attachedContainerNativeImpl = containerImpl
            }
            val liveChildren = _children
            val staleIt = attachedNativeChildren.iterator()
            while (staleIt.hasNext()) {
                val existingImpl = staleIt.next()
                if (liveChildren.none { it.nativeImpl === existingImpl }) {
                    staleIt.remove()
                    containerImpl.removeChildNative(existingImpl)
                }
            }
            for ((child, childImpl) in realizedVisible) {
                if (child.parent !== this || !child.isVisible || child.nativeImpl !== childImpl) continue
                unattachedRealizedChildren.remove(child)
                if (attachedNativeChildren.none { it === childImpl }) {
                    val nativeIdx = computeNativeInsertionIndexLocked(child)
                    attachedNativeChildren.add(nativeIdx.coerceIn(0, attachedNativeChildren.size), childImpl)
                    containerImpl.addChildNative(childImpl, nativeIdx)
                }
            }
        }
    }

    override fun ensureNativeImpl(): NativeImpl<*>? {
        val wasAlreadyInitialized = (nativeImpl != null && nativeImplInitialized)
        val impl = super.ensureNativeImpl() ?: return null
        if (wasAlreadyInitialized) {
            val containerImpl = impl as? AbstractContainerNativeImpl<*>
            if (containerImpl != null && (childrenReconcileDirty || unattachedRealizedChildren.isNotEmpty() || attachedContainerNativeImpl !== containerImpl)) {
                reconcileVisibleChildrenNative(containerImpl)
            }
        }
        return impl
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val containerImpl = impl as? AbstractContainerNativeImpl<*> ?: return
        reconcileVisibleChildrenNative(containerImpl)
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val containerImpl = typedNative<AbstractContainerNativeImpl<*>>() ?: return
        reconcileVisibleChildrenNative(containerImpl)
    }

    override var isVisible: Boolean
        get() = super.isVisible
        set(value) {
            super.isVisible = value
            val containerImpl = typedNative<AbstractContainerNativeImpl<*>>()
            if (value && containerImpl != null && (childrenReconcileDirty || unattachedRealizedChildren.isNotEmpty() || attachedContainerNativeImpl !== containerImpl)) {
                reconcileVisibleChildrenNative(containerImpl)
            }
        }

    /**
     * `kotlinx.html`-style unary plus operator to append inline text directly to this container.
     */
    operator fun String.unaryPlus(): Text {
        val textNode = Text(this, paragraph = false)
        addChild(textNode)
        return textNode
    }

    /**
     * `kotlinx.html`-style unary plus operator to append an existing [AbstractComponent] to this container.
     */
    operator fun <T : AbstractComponent> T.unaryPlus(): T {
        addChild(this)
        return this
    }

    override fun dispose() {
        val current: List<AbstractComponent> = containerChildrenLock.withLock {
            val snapshot = _children
            _children = emptyList()
            unattachedRealizedChildren.clear()
            attachedNativeChildren.clear()
            attachedContainerNativeImpl = null
            childrenReconcileDirty = true
            snapshot
        }
        for (child in current) {
            child.dispose()
        }
        super.dispose()
    }
}
