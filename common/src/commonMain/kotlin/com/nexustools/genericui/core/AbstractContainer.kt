package com.nexustools.genericui.core

import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.transition.ContainerRenderCache

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

    private var _children: List<AbstractComponent> = emptyList()
    val children: List<AbstractComponent>
        get() = _children

    internal fun reorderChildrenInMemory(orderedChildren: List<AbstractComponent>) {
        _children = orderedChildren.toList()
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
        child.parent = this
        val current = _children
        val next = ArrayList<AbstractComponent>(current.size + 1)
        next.addAll(current)
        if (index < 0 || index >= next.size) {
            next.add(child)
        } else {
            next.add(index, child)
        }
        _children = next
        markRenderCacheDirty()
        val parentImpl = typedNative<AbstractContainerNativeImpl<*>>()
        if (parentImpl != null && child.isVisible) {
            val childImpl = child.ensureNativeImpl()
            if (childImpl != null) {
                parentImpl.addChildNative(childImpl, index)
            }
        }
    }

    open fun removeChild(child: AbstractComponent): Boolean {
        val current = _children
        if (!current.contains(child)) return false
        val next = ArrayList<AbstractComponent>(current.size)
        var removed = false
        for (item in current) {
            if (!removed && item === child) {
                removed = true
            } else {
                next.add(item)
            }
        }
        if (removed) {
            _children = next
            child.parent = null
            markRenderCacheDirty()
            val childImpl = child.nativeImpl
            if (childImpl != null) {
                typedNative<AbstractContainerNativeImpl<*>>()?.removeChildNative(childImpl)
            }
        }
        return removed
    }

    open fun clearChildren() {
        val current = _children
        _children = emptyList()
        for (child in current) {
            child.parent = null
        }
        markRenderCacheDirty()
        typedNative<AbstractContainerNativeImpl<*>>()?.clearChildrenNative()
    }

    internal override fun ensureNativeImpl(): NativeImpl<*>? {
        val wasAlreadyCreated = (nativeImpl != null)
        val impl = super.ensureNativeImpl() ?: return null
        if (wasAlreadyCreated) {
            val containerImpl = impl as? AbstractContainerNativeImpl<*>
            if (containerImpl != null) {
                for ((idx, child) in _children.withIndex()) {
                    if (child.isVisible) {
                        val childImpl = child.ensureNativeImpl()
                        if (childImpl != null) {
                            containerImpl.addChildNative(childImpl, idx)
                        }
                    }
                }
            }
        }
        return impl
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        val containerImpl = impl as? AbstractContainerNativeImpl<*> ?: return
        for (child in _children) {
            if (child.isVisible) {
                val childImpl = child.ensureNativeImpl()
                if (childImpl != null) {
                    containerImpl.addChildNative(childImpl, -1)
                }
            }
        }
    }

    override var isVisible: Boolean
        get() = super.isVisible
        set(value) {
            super.isVisible = value
            val containerImpl = typedNative<AbstractContainerNativeImpl<*>>()
            if (value && containerImpl != null) {
                for (child in _children) {
                    if (child.isVisible && child.nativeImpl == null) {
                        val childImpl = child.ensureNativeImpl()
                        if (childImpl != null) {
                            containerImpl.addChildNative(childImpl, -1)
                        }
                    }
                }
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
        val current = _children
        _children = emptyList()
        for (child in current) {
            child.dispose()
        }
        super.dispose()
    }
}
