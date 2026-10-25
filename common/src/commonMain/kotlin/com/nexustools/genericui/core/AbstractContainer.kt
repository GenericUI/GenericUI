package com.nexustools.genericui.core

import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.state.ComponentRuleScope
import com.nexustools.genericui.style.state.ComponentStyleBlock
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

    private val _children = mutableListOf<AbstractComponent>()
    val children: List<AbstractComponent>
        get() = _children

    private val selectorStyleRules = mutableListOf<Pair<String, ComponentStyleBlock>>()

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
        if (index < 0 || index >= _children.size) {
            _children.add(child)
        } else {
            _children.add(index, child)
        }
        applyRegisteredSelectorRulesToSubtree(child)
        markRenderCacheDirty()
        val parentImpl = typedNative<AbstractContainerNativeImpl<*>>()
        if (parentImpl != null && isVisible && child.isVisible) {
            val childImpl = child.ensureNativeImpl()
            if (childImpl != null) {
                parentImpl.addChildNative(childImpl, index)
            }
        }
    }

    open fun removeChild(child: AbstractComponent): Boolean {
        val removed = _children.remove(child)
        if (removed) {
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
        for (child in _children) {
            child.parent = null
        }
        _children.clear()
        markRenderCacheDirty()
        typedNative<AbstractContainerNativeImpl<*>>()?.clearChildrenNative()
    }

    override fun onAttachNative(impl: NativeImpl<*>) {
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

    /**
     * Selector-scoped DSL styling on this container (e.g., `".button" { style { tint = ColorRgba(0, 255, 0, 1.0); hover { tint = ColorRgba(255, 0, 0, 1.0) } } }`).
     */
    operator fun String.invoke(block: ComponentRuleScope.() -> Unit) {
        val scope = ComponentRuleScope(this.trim())
        scope.block()
        val styleBlock = scope.styleBlock ?: return
        selectorStyleRules.add(scope.selector to styleBlock)
        for (child in _children) {
            applySelectorRuleToSubtree(child, scope.selector, styleBlock)
        }
    }

    private fun applyRegisteredSelectorRulesToSubtree(target: AbstractComponent) {
        var anc: AbstractContainer? = this
        val inheritedRules = mutableListOf<Pair<String, ComponentStyleBlock>>()
        while (anc != null) {
            inheritedRules.addAll(0, anc.selectorStyleRules)
            anc = anc.parent as? AbstractContainer
        }
        for ((selector, block) in inheritedRules) {
            applySelectorRuleToSubtree(target, selector, block)
        }
    }

    private fun applySelectorRuleToSubtree(
        target: AbstractComponent,
        selector: String,
        styleBlock: ComponentStyleBlock
    ) {
        if (matchesSelectorRule(target, selector)) {
            val merged = styleBlock.toComputedStyle(target.computedStyle)
            target.applyComputedStyle(merged)
            target.nativeImpl?.setTint(merged.tint)
        }
        if (target is AbstractContainer) {
            for (c in target.children) {
                applySelectorRuleToSubtree(c, selector, styleBlock)
            }
        }
    }

    private fun matchesSelectorRule(target: AbstractComponent, rawSelector: String): Boolean {
        val sel = rawSelector.trim()
        if (sel.isEmpty() || sel == "*") return true
        val classTokens = target.getClass().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        val simpleClassName = target::class.simpleName?.lowercase().orEmpty()
        val prefix = target.componentClassPrefix.lowercase()
        return when {
            sel.startsWith("#") -> target.id == sel.removePrefix("#")
            sel.startsWith(".") -> {
                val cls = sel.removePrefix(".").lowercase()
                cls in classTokens.map { it.lowercase() } ||
                    cls == simpleClassName ||
                    cls == prefix ||
                    (cls == "button" && (prefix == "btn" || simpleClassName.contains("button")))
            }
            else -> {
                val lower = sel.lowercase().replace("\\:", ":")
                val targetTag = target.htmlTag.lowercase().replace("\\:", ":")
                val rootMatch = (lower == "root" || lower == "genericui:root") &&
                    (targetTag == "root" || targetTag == "genericui:root")
                rootMatch ||
                    lower == targetTag ||
                    lower == simpleClassName ||
                    lower == prefix ||
                    (lower == "button" && (prefix == "btn" || simpleClassName.contains("button")))
            }
        }
    }

    override fun dispose() {
        for (child in _children) {
            child.dispose()
        }
        _children.clear()
        super.dispose()
    }
}
