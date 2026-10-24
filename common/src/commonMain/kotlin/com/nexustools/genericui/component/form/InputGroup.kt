package com.nexustools.genericui.component.form

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Bootstrap input group container (`<div class="input-group">`) for prepending/appending
 * addons, labels, or buttons to a [TextField] or [Select].
 */
class InputGroup : AbstractContainer() {
    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("input-group")
}
