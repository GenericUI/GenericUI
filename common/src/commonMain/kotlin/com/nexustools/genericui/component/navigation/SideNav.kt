package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Vertical sidebar navigation menu.
 */
class SideNav : AbstractContainer() {
    init {
        htmlTag = "genericui:sidenav"
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("side-nav", htmlTag)
}
