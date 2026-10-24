package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Horizontal or vertical navigation link bar (`<ul class="nav">`).
 */
class Nav(
    val pills: Boolean = false
) : AbstractContainer() {
    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer(if (pills) "nav nav-pills" else "nav")
}
