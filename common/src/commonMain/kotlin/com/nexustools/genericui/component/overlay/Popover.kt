package com.nexustools.genericui.component.overlay

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.exception.NativeUninitializedException
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl

/**
 * Anchored floating popover container (`<div class="popover">`).
 */
open class Popover(
    title: String? = null,
    placement: Placement = Placement.BOTTOM
) : AbstractContainer() {
    init {
        htmlTag = "genericui:popover"
    }

    private var boundFactory: NativeFactory? = null

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

    override fun resolveActiveFactory(): NativeFactory? =
        super.resolveActiveFactory() ?: boundFactory

    fun showAnchored(anchor: AbstractComponent?, x: Int = 0, y: Int = 0) {
        val anchorFactory = anchor?.resolveActiveFactory()
        if (parent == null && anchor != null) {
            parent = anchor
        }
        val resolvedFactory = anchorFactory ?: resolveActiveFactory() ?: GenericUI.activeFactory
            ?: throw NativeUninitializedException("Popover", "showAnchored")
        boundFactory = resolvedFactory
        isVisible = true
        val impl = ensureNativeImpl() as? PopoverNativeImpl<*>
            ?: throw NativeUninitializedException("Popover", "showAnchored")
        impl.showAnchored(anchor?.nativeImpl, x, y)
    }

    fun hide() {
        typedNative<PopoverNativeImpl<*>>()?.hide()
        isVisible = false
    }

    protected override fun createNativeImpl(factory: NativeFactory): PopoverNativeImpl<*> =
        factory.createPopover()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<PopoverNativeImpl<*>>() ?: return
        impl.setTitle(title)
        impl.setPlacement(placement)
    }
}
