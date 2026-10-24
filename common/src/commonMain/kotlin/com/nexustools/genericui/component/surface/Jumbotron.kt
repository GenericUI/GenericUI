package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Hero callout container (`jumbotron` / `p-5 mb-4 bg-body-tertiary rounded-3`).
 */
class Jumbotron : AbstractContainer() {
    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("jumbotron p-5 mb-4 rounded-3")
}
