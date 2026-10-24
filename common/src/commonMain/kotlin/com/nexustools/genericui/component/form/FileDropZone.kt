package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.dialog.FileBrowserDialog
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Drag-and-drop file/folder drop zone that also opens a native [FileBrowserDialog] when clicked.
 */
class FileDropZone(
    var mode: FileBrowserMode = FileBrowserMode.OPEN_MULTIPLE_FILES,
    var filters: List<FileTypeFilter> = emptyList(),
    var promptText: String = "Drop files here or click to browse"
) : AbstractContainer() {
    var onFilesDroppedOrSelected: ((List<SelectedFile>) -> Unit)? = null
    var onSelectionCancelled: (() -> Unit)? = null

    init {
        setOnClick {
            openNativeFileBrowser()
        }
    }

    fun openNativeFileBrowser() {
        FileBrowserDialog(
            mode = mode,
            title = promptText,
            filters = filters
        ).show(
            onSelected = { files -> onFilesDroppedOrSelected?.invoke(files) },
            onCancelled = { onSelectionCancelled?.invoke() }
        )
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("file-drop-zone")
}
