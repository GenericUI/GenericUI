package com.nexustools.genericui.component.action

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Horizontal action toolbar container (`btn-toolbar`).
 */
class Toolbar : AbstractContainer() {
    init {
        htmlTag = "genericui:toolbar"
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("btn-toolbar", htmlTag)
}
