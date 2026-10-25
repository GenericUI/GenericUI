package com.nexustools.genericui.component.overlay

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Slide-out offcanvas drawer overlay (`<div class="offcanvas">`).
 */
class Drawer(
    var placement: Placement = Placement.START,
    var isOpen: Boolean = false
) : AbstractContainer() {
    init {
        htmlTag = "genericui:drawer"
    }

    fun open() {
        isOpen = true
        isVisible = true
    }

    fun close() {
        isOpen = false
        isVisible = false
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("offcanvas", htmlTag)
}
