package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Custom modal or modeless dialog window capable of hosting arbitrary GenericUI component trees.
 */
open class DialogWindow(
    title: String = "",
    modal: Boolean = true,
    presentationStyle: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
) : AbstractContainer() {
    var title: String = title
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setTitle(value)
        }

    var isModal: Boolean = modal
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setModal(value)
        }

    var presentationStyle: DialogPresentationStyle = presentationStyle
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setPresentationStyle(value)
        }

    var isFullscreen: Boolean = false
        set(value) {
            field = value
            typedNative<DialogWindowNativeImpl<*>>()?.setFullscreen(value)
        }

    var onCloseHandler: (() -> Unit)? = null

    fun open(ownerWindow: Window? = null) {
        if (ownerWindow != null && parent == null) {
            this.parent = ownerWindow
        }
        isVisible = true
        ensureNativeImpl()
        val ownerNative = ownerWindow?.nativeImpl as? WindowNativeImpl<*>
        typedNative<DialogWindowNativeImpl<*>>()?.showDialog(ownerNative)
    }

    fun close() {
        typedNative<DialogWindowNativeImpl<*>>()?.closeDialog()
        isVisible = false
        onCloseHandler?.invoke()
    }

    override fun createNativeImpl(factory: NativeFactory): DialogWindowNativeImpl<*> =
        factory.createDialogWindow()

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<DialogWindowNativeImpl<*>>() ?: return
        impl.setTitle(title)
        impl.setModal(isModal)
        impl.setPresentationStyle(presentationStyle)
        impl.setFullscreen(isFullscreen)
        impl.setOnDialogClosed {
            isVisible = false
            onCloseHandler?.invoke()
        }
    }
}
