package com.nexustools.genericui.nativeimpl.dialog

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

interface DialogWindowNativeImpl<out W> : AbstractContainerNativeImpl<W> {
    fun setTitle(title: String)
    fun getTitle(): String
    fun setPresentationStyle(style: DialogPresentationStyle)
    fun getPresentationStyle(): DialogPresentationStyle
    fun setModal(modal: Boolean)
    fun setFullscreen(fullscreen: Boolean)
    fun showDialog(ownerWindow: WindowNativeImpl<*>?)
    fun closeDialog()
    fun setOnDialogClosed(listener: (() -> Unit)?)
}
