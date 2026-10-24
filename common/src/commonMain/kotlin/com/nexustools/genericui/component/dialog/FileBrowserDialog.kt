package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Unified cross-platform native file and folder browser dialog.
 */
open class FileBrowserDialog(
    var mode: FileBrowserMode = FileBrowserMode.OPEN_SINGLE_FILE,
    var title: String = "Select File",
    var initialDirectory: String? = null,
    var suggestedFileName: String? = null,
    filters: List<FileTypeFilter> = emptyList(),
    var activeFilterIndex: Int = 0,
    var showHiddenFiles: Boolean = false,
    var createDirectoriesAllowed: Boolean = true,
    var persistAndroidUriPermissions: Boolean = true
) {
    private val _filters = filters.toMutableList()
    val filters: List<FileTypeFilter> get() = _filters

    fun addFilter(filter: FileTypeFilter): FileBrowserDialog = apply {
        _filters.add(filter)
    }

    fun clearFilters(): FileBrowserDialog = apply {
        _filters.clear()
    }

    /**
     * Builds an immutable [FileBrowserRequest] snapshot for the current configuration.
     */
    fun toRequest(): FileBrowserRequest = FileBrowserRequest(
        mode = mode,
        title = title,
        initialDirectory = initialDirectory,
        defaultFileName = suggestedFileName,
        suggestedFileName = suggestedFileName,
        filters = _filters.toList(),
        selectedFilterIndex = activeFilterIndex,
        activeFilterIndex = activeFilterIndex,
        showHiddenFiles = showHiddenFiles,
        createDirectoriesAllowed = createDirectoriesAllowed,
        persistAndroidUriPermissions = persistAndroidUriPermissions
    )

    /**
     * Opens the native file or folder browser and delivers the result or cancellation to [callback].
     */
    fun show(ownerWindow: Window? = null, callback: FileBrowserCallback) {
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        val request = toRequest()
        if (factory != null) {
            factory.createFileBrowserDialog().showFileBrowser(
                parentWindow = ownerWindow?.nativeImpl as? WindowNativeImpl<*>,
                request = request,
                callback = callback
            )
        } else {
            callback.onCancelled()
        }
    }

    /**
     * Convenience overload accepting lambda callbacks for selection and cancellation.
     */
    fun show(
        ownerWindow: Window? = null,
        onSelected: (List<SelectedFile>) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        show(
            ownerWindow = ownerWindow,
            callback = object : FileBrowserCallback {
                override fun onFilesSelected(files: List<SelectedFile>, selectedFilter: FileTypeFilter?) =
                    onSelected(files)
                override fun onCancelled() = onCancelled()
            }
        )
    }
}
