package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.style.RgbaColor

/**
 * High-level static utility entry points for opening native alert, confirmation, prompt,
 * choice, color, file, and folder dialogs on any platform.
 */
object Dialogs {
    private fun resolveOwnerNative(ownerWindow: Window?): WindowNativeImpl<*>? =
        ownerWindow?.nativeImpl as? WindowNativeImpl<*>

    fun alert(
        title: String,
        message: String,
        type: BootstrapType = BootstrapType.INFO,
        ownerWindow: Window? = null,
        onDismiss: () -> Unit = {}
    ) {
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            factory.createStandardDialogs().showAlert(
                owner = resolveOwnerNative(ownerWindow),
                title = title,
                message = message,
                type = type,
                onDismissed = onDismiss
            )
        } else {
            onDismiss()
        }
    }

    fun confirm(
        title: String,
        message: String,
        confirmLabel: String = "OK",
        cancelLabel: String = "Cancel",
        ownerWindow: Window? = null,
        onResult: (Boolean) -> Unit
    ) {
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            factory.createStandardDialogs().showConfirm(
                owner = resolveOwnerNative(ownerWindow),
                title = title,
                message = message,
                confirmText = confirmLabel,
                cancelText = cancelLabel,
                onResult = onResult
            )
        } else {
            onResult(false)
        }
    }

    fun prompt(
        title: String,
        message: String,
        defaultValue: String = "",
        placeholder: String = "",
        ownerWindow: Window? = null,
        onResult: (String?) -> Unit
    ) {
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            factory.createStandardDialogs().showPrompt(
                owner = resolveOwnerNative(ownerWindow),
                title = title,
                message = message,
                defaultValue = defaultValue,
                placeholder = placeholder,
                onResult = onResult
            )
        } else {
            onResult(null)
        }
    }

    fun choice(
        title: String,
        message: String,
        choices: List<String>,
        selectedIndex: Int = 0,
        ownerWindow: Window? = null,
        onResult: (Int?) -> Unit
    ) {
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            factory.createStandardDialogs().showChoice(
                owner = resolveOwnerNative(ownerWindow),
                title = title,
                message = message,
                options = choices,
                selectedIndex = selectedIndex,
                onResult = onResult
            )
        } else {
            onResult(null)
        }
    }

    fun chooseColor(
        title: String = "Select Color",
        initialColor: RgbaColor = RgbaColor.WHITE,
        allowAlpha: Boolean = true,
        ownerWindow: Window? = null,
        onResult: (RgbaColor?) -> Unit
    ) {
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            factory.createStandardDialogs().showColorChooser(
                owner = resolveOwnerNative(ownerWindow),
                title = title,
                initialColor = initialColor,
                onResult = onResult
            )
        } else {
            onResult(null)
        }
    }

    fun openFile(
        title: String = "Open File",
        initialDirectory: String? = null,
        filters: List<FileTypeFilter> = emptyList(),
        ownerWindow: Window? = null,
        onSelected: (SelectedFile) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        FileOpenDialog(
            title = title,
            allowMultiple = false,
            initialDirectory = initialDirectory,
            filters = filters
        ).show(
            ownerWindow = ownerWindow,
            onSelected = { files ->
                val first = files.firstOrNull()
                if (first != null) onSelected(first) else onCancelled()
            },
            onCancelled = onCancelled
        )
    }

    fun openMultipleFiles(
        title: String = "Open Files",
        initialDirectory: String? = null,
        filters: List<FileTypeFilter> = emptyList(),
        ownerWindow: Window? = null,
        onSelected: (List<SelectedFile>) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        FileOpenDialog(
            title = title,
            allowMultiple = true,
            initialDirectory = initialDirectory,
            filters = filters
        ).show(
            ownerWindow = ownerWindow,
            onSelected = onSelected,
            onCancelled = onCancelled
        )
    }

    fun saveFile(
        title: String = "Save File",
        suggestedFileName: String? = "untitled",
        initialDirectory: String? = null,
        filters: List<FileTypeFilter> = emptyList(),
        ownerWindow: Window? = null,
        onSelected: (SelectedFile) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        FileSaveDialog(
            title = title,
            suggestedFileName = suggestedFileName,
            initialDirectory = initialDirectory,
            filters = filters
        ).show(
            ownerWindow = ownerWindow,
            onSelected = { files ->
                val first = files.firstOrNull()
                if (first != null) onSelected(first) else onCancelled()
            },
            onCancelled = onCancelled
        )
    }

    fun selectFolder(
        title: String = "Select Folder",
        initialDirectory: String? = null,
        ownerWindow: Window? = null,
        onSelected: (SelectedFile) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        DirectoryChooserDialog(
            title = title,
            initialDirectory = initialDirectory
        ).show(
            ownerWindow = ownerWindow,
            onSelected = { files ->
                val first = files.firstOrNull()
                if (first != null) onSelected(first) else onCancelled()
            },
            onCancelled = onCancelled
        )
    }
}
