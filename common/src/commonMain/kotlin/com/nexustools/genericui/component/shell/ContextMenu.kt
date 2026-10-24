package com.nexustools.genericui.component.shell

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl

class ContextMenu : AbstractContainer("dropdown-menu") {

    override fun createNativeImpl(factory: NativeFactory): ContextMenuNativeImpl<*> =
        factory.createContextMenu()

    fun showAt(anchor: AbstractComponent?, x: Int, y: Int) {
        val anchorFactory = anchor?.resolveActiveFactory()
        if (parent == null && anchor != null) {
            parent = anchor
        }
        if (anchorFactory != null) {
            val impl = ensureNativeImpl() as? ContextMenuNativeImpl<*>
            impl?.showAt(anchor.nativeImpl, x, y)
        }
    }

    fun hide() {
        typedNative<ContextMenuNativeImpl<*>>()?.hide()
    }
}
