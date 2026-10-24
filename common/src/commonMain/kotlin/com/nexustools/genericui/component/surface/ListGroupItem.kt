package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Item inside a [ListGroup] (`<li class="list-group-item">`).
 */
class ListGroupItem(
    override var id: String? = null,
    var isActive: Boolean = false
) : AbstractContainer() {
    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer(if (isActive) "list-group-item active" else "list-group-item")
}
