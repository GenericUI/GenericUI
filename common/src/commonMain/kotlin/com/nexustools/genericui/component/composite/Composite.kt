package com.nexustools.genericui.component.composite

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock
import kotlin.concurrent.Volatile

/**
 * Encapsulated composite widget base class extending [AbstractComponent] (NOT [AbstractContainer]).
 *
 * Manages sealed internal sub-components ([compositeChildren]) backed uniformly across all
 * 9 GenericUI backends by [StyledContainerNativeImpl] without exposing `Container` child
 * mutation/inspection APIs to consumers.
 *
 * Subtrees inside [Composite] participate in stylesheet cascading, layout, visibility/enablement
 * propagation, hover/focus/active transitions, keyboard dispatch, and attach/detach lifecycle
 * via package/friend `internal` traversal helpers while keeping [compositeChildren] encapsulated.
 */
abstract class Composite(
    classPrefix: String = "",
    tagName: String = DEFAULT_TAG_NAME
) : AbstractComponent(classPrefix, tagName) {

    private val compositeChildrenLock: PlatformReentrantLock = PlatformReentrantLock()

    @Volatile
    private var _compositeChildren: List<AbstractComponent> = emptyList()

    /**
     * Read-only snapshot of sealed internal children owned by this [Composite] subclass.
     */
    protected val compositeChildren: List<AbstractComponent>
        get() = _compositeChildren

    private val attachedNativeChildren: MutableList<NativeImpl<*>> = mutableListOf()
    private var attachedContainerNativeImpl: StyledContainerNativeImpl<*>? = null
    private var lastSyncedVisualSpec: StyledVisualSpec? = null
    private var compositeFocusableState: Boolean = true

    protected open val focusDelegateChild: AbstractComponent?
        get() = null

    override val supportsFocus: Boolean
        get() = focusDelegateChild != null

    internal override fun isFocusableStateInternal(): Boolean {
        val delegate = focusDelegateChild
        return if (delegate != null) {
            compositeFocusableState && inheritedFocusableState && delegate.isFocusable
        } else {
            compositeFocusableState && inheritedFocusableState
        }
    }

    override fun setFocusable(focusable: Boolean) {
        if (compositeFocusableState == focusable) return
        val prevEffective = compositeFocusableState && inheritedFocusableState
        compositeFocusableState = focusable
        val nextEffective = compositeFocusableState && inheritedFocusableState
        if (prevEffective != nextEffective) {
            propagateInheritedFocusableToCompositeChildren(nextEffective)
        }
    }

    internal override fun effectiveDescendantFocusAllowed(): Boolean =
        compositeFocusableState && inheritedFocusableState

    internal override fun updateInheritedFocusable(ancestorAllowsFocus: Boolean) {
        val prevEffective = compositeFocusableState && inheritedFocusableState
        inheritedFocusableState = ancestorAllowsFocus
        val nextEffective = compositeFocusableState && inheritedFocusableState
        if (prevEffective != nextEffective) {
            propagateInheritedFocusableToCompositeChildren(nextEffective)
        }
    }

    internal override fun onEffectiveFocusableChanged(effectiveFocusable: Boolean) {}

    private fun propagateInheritedFocusableToCompositeChildren(allow: Boolean) {
        val snapshot = compositeChildrenLock.withLock { _compositeChildren }
        for (child in snapshot) {
            child.updateInheritedFocusable(allow)
        }
    }

    override var isFocused: Boolean
        get() {
            val delegate = focusDelegateChild
            return if (delegate != null) {
                if (!isEnabled || !isFocusable) false else delegate.isFocused
            } else {
                super.isFocused
            }
        }
        set(value) {
            val delegate = focusDelegateChild
            if (delegate != null) {
                if (!isEnabled || !isFocusable) {
                    delegate.isFocused = false
                } else {
                    delegate.isFocused = value
                }
            } else {
                super.isFocused = value
            }
        }

    override val acceptsTypographyComponentStyles: Boolean
        get() = true

    override val acceptsAestheticSkinComponentStyles: Boolean
        get() = true

    internal fun internalCompositeChildren(): List<AbstractComponent> = _compositeChildren

    protected fun addCompositeChild(child: AbstractComponent) {
        insertCompositeChild(_compositeChildren.size, child)
    }

    protected fun insertCompositeChild(index: Int, child: AbstractComponent) {
        if (child === this) {
            throw com.nexustools.genericui.exception.GenericUiException(
                "Composite cannot add itself as an internal composite child."
            )
        }
        val prevParent = child.parent
        if (prevParent != null && prevParent !== this) {
            when (prevParent) {
                is AbstractContainer -> prevParent.removeChild(child)
                is Composite -> prevParent.removeCompositeChild(child)
            }
        }
        var movedExisting = false
        compositeChildrenLock.withLock {
            child.parent = this
            val current = _compositeChildren
            val next = ArrayList<AbstractComponent>(current.size + 1)
            var oldIndex = -1
            for ((i, item) in current.withIndex()) {
                if (item === child) {
                    oldIndex = i
                } else {
                    next.add(item)
                }
            }
            val targetIndex = index.coerceIn(0, next.size)
            next.add(targetIndex, child)
            _compositeChildren = next
            if (oldIndex >= 0 && oldIndex != targetIndex) {
                movedExisting = true
            }
        }
        if (!isEnabled && child.isEnabled) {
            child.isEnabled = false
        }
        notifyParentCacheDirty()
        val parentImpl = typedNative<StyledContainerNativeImpl<*>>()
        if (parentImpl != null && child.isVisible) {
            val childImpl = child.ensureNativeImpl()
            if (childImpl != null) {
                if (movedExisting) {
                    compositeChildrenLock.withLock {
                        attachedNativeChildren.removeAll { it === childImpl }
                    }
                }
                attachRealizedCompositeChildToNative(child, childImpl)
            }
        }
    }

    protected fun removeCompositeChild(child: AbstractComponent): Boolean {
        var removed = false
        var detachedImpl: NativeImpl<*>? = null
        compositeChildrenLock.withLock {
            val current = _compositeChildren
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
                _compositeChildren = next
                child.parent = null
                detachedImpl = child.nativeImpl
                val impl = detachedImpl
                if (impl != null) {
                    attachedNativeChildren.removeAll { it === impl }
                }
            }
        }
        if (removed) {
            notifyParentCacheDirty()
            detachedImpl?.let { impl ->
                typedNative<StyledContainerNativeImpl<*>>()?.removeChildNative(impl)
            }
        }
        return removed
    }

    protected fun clearCompositeChildren() {
        val removed = compositeChildrenLock.withLock {
            val current = _compositeChildren
            _compositeChildren = emptyList()
            attachedNativeChildren.clear()
            for (child in current) {
                child.parent = null
            }
            current
        }
        if (removed.isNotEmpty()) {
            notifyParentCacheDirty()
            typedNative<StyledContainerNativeImpl<*>>()?.clearChildrenNative()
        }
    }

    private fun computeNativeInsertionIndexLocked(child: AbstractComponent): Int {
        var precedingAttachedCount = 0
        for (sibling in _compositeChildren) {
            if (sibling === child) break
            val siblingImpl = sibling.nativeImpl
            if (siblingImpl != null && attachedNativeChildren.any { it === siblingImpl }) {
                precedingAttachedCount++
            }
        }
        return precedingAttachedCount
    }

    internal fun attachRealizedCompositeChildToNative(child: AbstractComponent, childImpl: NativeImpl<*>) {
        val containerImpl = typedNative<StyledContainerNativeImpl<*>>() ?: return
        compositeChildrenLock.withLock {
            if (child.parent !== this || !child.isVisible || child.nativeImpl !== childImpl) return
            if (attachedContainerNativeImpl !== containerImpl) {
                attachedNativeChildren.clear()
                attachedContainerNativeImpl = containerImpl
            }
            if (attachedNativeChildren.any { it === childImpl }) return
            val nativeIndex = computeNativeInsertionIndexLocked(child)
            attachedNativeChildren.add(nativeIndex.coerceIn(0, attachedNativeChildren.size), childImpl)
            containerImpl.addChildNative(childImpl, nativeIndex)
        }
    }

    internal fun reconcileVisibleCompositeChildrenNative(containerImpl: StyledContainerNativeImpl<*>) {
        val snapshot = compositeChildrenLock.withLock { _compositeChildren }
        val realizedVisible = ArrayList<Pair<AbstractComponent, NativeImpl<*>>>(snapshot.size)
        for (child in snapshot) {
            if (child.isVisible) {
                val childImpl = child.ensureNativeImpl()
                if (childImpl != null) {
                    realizedVisible.add(child to childImpl)
                }
            }
        }
        compositeChildrenLock.withLock {
            if (typedNative<StyledContainerNativeImpl<*>>() !== containerImpl) return
            if (attachedContainerNativeImpl !== containerImpl) {
                attachedNativeChildren.clear()
                attachedContainerNativeImpl = containerImpl
            }
            val liveChildren = _compositeChildren
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
                if (attachedNativeChildren.none { it === childImpl }) {
                    val nativeIdx = computeNativeInsertionIndexLocked(child)
                    attachedNativeChildren.add(nativeIdx.coerceIn(0, attachedNativeChildren.size), childImpl)
                    containerImpl.addChildNative(childImpl, nativeIdx)
                }
            }
        }
    }

    /**
     * Resolves the effective [StyledVisualSpec] for this [Composite]'s own backing container
     * without propagating/multiplying opacity down into [compositeChildren].
     */
    fun resolveVisualSpec(): StyledVisualSpec {
        val isDark = Application.current?.getResolvedTheme() != ThemeMode.LIGHT
        val snapshot = getBootstrap().applyToSnapshot(BootstrapStyleSnapshot(componentPrefix = componentClassPrefix))
        return StyledVisualSpec.fromComponentAndComputed(
            componentStyle = getStyle(),
            computedStyle = resolveEffectiveStyle(),
            snapshot = snapshot,
            darkTheme = isDark
        )
    }

    protected fun syncVisualsToNative() {
        val impl = typedNative<StyledContainerNativeImpl<*>>() ?: return
        val spec = resolveVisualSpec()
        if (spec == lastSyncedVisualSpec) return
        lastSyncedVisualSpec = spec
        impl.setStyledVisuals(spec)
    }

    override var isVisible: Boolean
        get() = super.isVisible
        set(value) {
            super.isVisible = value
            val containerImpl = typedNative<StyledContainerNativeImpl<*>>()
            if (value && containerImpl != null) {
                reconcileVisibleCompositeChildrenNative(containerImpl)
            }
        }

    override var isEnabled: Boolean
        get() = super.isEnabled
        set(value) {
            super.isEnabled = value
            for (child in _compositeChildren) {
                child.isEnabled = value
            }
            syncVisualsToNative()
        }

    override fun triggerKeyDown(ev: KeyEvent) {
        if (!isVisible || !isEnabled || isDisposed) return
        super.triggerKeyDown(ev)
        for (child in _compositeChildren.toList()) {
            if (!child.isVisible || !child.isEnabled || child.isDisposed) continue
            child.triggerKeyDown(ev)
        }
    }

    override fun triggerKeyUp(ev: KeyEvent) {
        if (!isVisible || !isEnabled || isDisposed) return
        super.triggerKeyUp(ev)
        for (child in _compositeChildren.toList()) {
            if (!child.isVisible || !child.isEnabled || child.isDisposed) continue
            child.triggerKeyUp(ev)
        }
    }

    override fun ensureNativeImpl(): NativeImpl<*>? {
        val wasAlreadyInitialized = (nativeImpl != null && nativeImplInitialized)
        val impl = super.ensureNativeImpl() ?: return null
        if (wasAlreadyInitialized) {
            val containerImpl = impl as? StyledContainerNativeImpl<*>
            if (containerImpl != null) {
                reconcileVisibleCompositeChildrenNative(containerImpl)
            }
        }
        return impl
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val containerImpl = impl as? StyledContainerNativeImpl<*> ?: return
        reconcileVisibleCompositeChildrenNative(containerImpl)
    }

    internal override fun onComputedStyleChanged(previous: ComputedStyle?, current: ComputedStyle) {
        syncVisualsToNative()
    }

    internal override fun onComponentOpacityChanged() {
        syncVisualsToNative()
    }

    internal override fun onComponentEnabledChanged(enabled: Boolean) {
        syncVisualsToNative()
    }

    internal override fun onAfterBootstrapChanged() {
        syncVisualsToNative()
    }

    internal open fun onThemeChanged() {
        syncVisualsToNative()
    }

    protected override fun createNativeImpl(factory: NativeFactory): StyledContainerNativeImpl<*> =
        factory.createStyledContainer(
            classPrefix = componentClassPrefix.ifBlank { DEFAULT_CLASS_PREFIX },
            tagName = htmlTag
        )

    override fun syncStateToNative() {
        lastSyncedVisualSpec = null
        super.syncStateToNative()
        syncVisualsToNative()
        typedNative<StyledContainerNativeImpl<*>>()?.let { reconcileVisibleCompositeChildrenNative(it) }
    }

    override fun dispose() {
        val current: List<AbstractComponent> = compositeChildrenLock.withLock {
            val snapshot = _compositeChildren
            _compositeChildren = emptyList()
            attachedNativeChildren.clear()
            attachedContainerNativeImpl = null
            snapshot
        }
        for (child in current) {
            child.dispose()
        }
        super.dispose()
    }

    companion object {
        const val DEFAULT_TAG_NAME: String = "genericui:composite"
        const val DEFAULT_CLASS_PREFIX: String = "composite"
    }
}
