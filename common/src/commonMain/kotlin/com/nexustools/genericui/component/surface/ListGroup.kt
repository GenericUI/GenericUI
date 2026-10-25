package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Vertical list group container (`<ul class="list-group">`).
 */
open class ListGroup(
    tagName: String = "genericui:listgroup",
    val classPrefix: String = "list-group"
) : AbstractContainer(classPrefix) {
    init {
        htmlTag = tagName
        if (classPrefix.isNotBlank()) {
            setClass(classPrefix)
        }
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer(classPrefix, htmlTag)
}
