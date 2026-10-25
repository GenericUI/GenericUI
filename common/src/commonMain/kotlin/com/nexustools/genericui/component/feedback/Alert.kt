package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Bootstrap contextual alert box (`<div class="alert alert-primary">`).
 */
class Alert(
    message: String = "",
    type: BootstrapType = BootstrapType.INFO,
    var dismissible: Boolean = false
) : AbstractContainer() {
    val messageText: Text = Text(message)

    init {
        htmlTag = "genericui:alert"
        this.type = type
        addChild(messageText)
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer(if (dismissible) "alert alert-dismissible" else "alert", htmlTag)
}
