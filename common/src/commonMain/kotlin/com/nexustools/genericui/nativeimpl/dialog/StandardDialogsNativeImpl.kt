package com.nexustools.genericui.nativeimpl.dialog

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.style.RgbaColor

/**
 * Native implementation contract for standard OS/platform dialogs (Alert, Confirm, Prompt, Choice, ColorChooser, FontChooser).
 */
interface StandardDialogsNativeImpl : Disposable {
    fun showAlert(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        type: BootstrapType = BootstrapType.PRIMARY,
        okButtonText: String = "OK",
        onDismissed: () -> Unit
    )

    fun showConfirm(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        confirmText: String = "OK",
        cancelText: String = "Cancel",
        type: BootstrapType = BootstrapType.PRIMARY,
        onResult: (Boolean) -> Unit
    )

    fun showPrompt(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        defaultValue: String = "",
        placeholder: String = "",
        confirmText: String = "OK",
        cancelText: String = "Cancel",
        onResult: (String?) -> Unit
    )

    fun showChoice(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        options: List<String>,
        selectedIndex: Int = 0,
        onResult: (Int?) -> Unit
    )

    fun showColorChooser(
        owner: WindowNativeImpl<*>?,
        title: String,
        initialColor: RgbaColor,
        onResult: (RgbaColor?) -> Unit
    )
}
