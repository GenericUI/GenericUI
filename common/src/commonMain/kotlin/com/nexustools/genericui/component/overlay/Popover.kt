package com.nexustools.genericui.component.overlay

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl

/**
 * Anchored floating popover container (`<div class="popover">`).
 */
open class Popover(
    title: String? = null,
    placement: Placement = Placement.BOTTOM
) : AbstractContainer() {
    var title: String? = title
        set(value) {
            field = value
            typedNative<PopoverNativeImpl<*>>()?.setTitle(value)
        }

    var placement: Placement = placement
        set(value) {
            field = value
            typedNative<PopoverNativeImpl<*>>()?.setPlacement(value)
        }

    fun showAnchored(anchor: AbstractComponent?, x: Int = 0, y: Int = 0) {
        isVisible = true
        ensureNativeImpl()
        typedNative<PopoverNativeImpl<*>>()?.showAnchored(anchor?.nativeImpl, x, y)
    }

    fun hide() {
        typedNative<PopoverNativeImpl<*>>()?.hide()
        isVisible = false
    }

    override fun createNativeImpl(factory: NativeFactory): PopoverNativeImpl<*> =
        factory.createPopover()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<PopoverNativeImpl<*>>() ?: return
        impl.setTitle(title)
        impl.setPlacement(placement)
    }
}
