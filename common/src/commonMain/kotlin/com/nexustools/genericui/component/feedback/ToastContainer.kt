package com.nexustools.genericui.component.feedback

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Viewport overlay stack for displaying active [Toast] messages (`toast-container`).
 */
class ToastContainer(
    var placement: Placement = Placement.BOTTOM_END
) : AbstractContainer() {
    init {
        htmlTag = "genericui:toastcontainer"
    }

    fun showToast(toast: Toast) {
        addChild(toast)
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("toast-container", htmlTag)
}
