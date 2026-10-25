package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Transient notification toast component (`<div class="toast">`).
 */
class Toast(
    var title: String = "",
    var message: String = "",
    type: BootstrapType = BootstrapType.INFO,
    var autoHideDurationMs: Long = 4000L
) : AbstractContainer() {
    init {
        htmlTag = "genericui:toast"
        this.type = type
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("toast", htmlTag)
}
