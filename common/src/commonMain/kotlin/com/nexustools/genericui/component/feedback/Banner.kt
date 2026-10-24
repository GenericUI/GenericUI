package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Full-width top or bottom status/announcement banner.
 */
class Banner(
    var message: String = "",
    type: BootstrapType = BootstrapType.WARNING
) : AbstractContainer() {
    init {
        this.type = type
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("banner")
}
