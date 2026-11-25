package com.nexustools.genericui.component.surface

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Vertical list group container (`<ul class="list-group">`).
 */
open class ListGroup(
    tagName: String = "genericui:listgroup",
    val classPrefix: String = "list-group",
    type: BootstrapType = BootstrapType.NONE
) : AbstractContainer(classPrefix) {
    constructor(type: BootstrapType) : this(
        tagName = "genericui:listgroup",
        classPrefix = "list-group",
        type = type
    )

    init {
        htmlTag = tagName
        if (classPrefix.isNotBlank() && classPrefix != "list-group") {
            setClass(classPrefix)
        }
        if (type != BootstrapType.NONE) {
            this.type = type
        }
    }

    protected override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer(classPrefix, htmlTag)
}
