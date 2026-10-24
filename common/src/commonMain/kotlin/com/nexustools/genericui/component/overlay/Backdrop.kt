package com.nexustools.genericui.component.overlay

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Dimmed modal/drawer backdrop scrim (`modal-backdrop`).
 */
class Backdrop(
    var dismissOnClick: Boolean = true,
    var onDismissed: (() -> Unit)? = null
) : AbstractContainer() {
    init {
        setOnClick {
            if (dismissOnClick) onDismissed?.invoke()
        }
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("modal-backdrop fade show")
}
