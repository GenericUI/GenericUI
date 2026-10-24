package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Vertical list group container (`<ul class="list-group">`).
 */
class ListGroup : AbstractContainer() {
    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("list-group")
}
