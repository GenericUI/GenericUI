package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.surface.CollapseNativeImpl

/**
 * Expandable/collapsible surface panel (`<div class="collapse">`).
 */
open class CollapsiblePanel(
    title: String = "",
    expanded: Boolean = true
) : AbstractContainer() {
    var title: String = title
        set(value) {
            field = value
            typedNative<CollapseNativeImpl<*>>()?.setTitle(value)
        }

    var isExpanded: Boolean = expanded
        set(value) {
            field = value
            typedNative<CollapseNativeImpl<*>>()?.setExpanded(value)
        }

    var onExpandedChanged: ((Boolean) -> Unit)? = null

    fun toggle() {
        isExpanded = !isExpanded
    }

    override fun createNativeImpl(factory: NativeFactory): CollapseNativeImpl<*> =
        factory.createCollapse()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<CollapseNativeImpl<*>>() ?: return
        impl.setTitle(title)
        impl.setExpanded(isExpanded)
        impl.setOnExpandedChanged { updated ->
            isExpanded = updated
            onExpandedChanged?.invoke(updated)
        }
    }
}
