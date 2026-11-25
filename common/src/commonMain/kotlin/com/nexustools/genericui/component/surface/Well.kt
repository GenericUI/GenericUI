package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Inset surface container (`well`).
 */
class Well(
    type: BootstrapType = BootstrapType.NONE
) : AbstractContainer() {
    init {
        htmlTag = "genericui:well"
        if (type != BootstrapType.NONE) {
            this.type = type
        }
    }

    protected override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("well p-3 border rounded", htmlTag)
}
