package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.io.FileSystem
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Unified cross-platform native file and folder browser dialog with 100% asynchronous
 * [Readable] and [Writable] stream openers ([pickReadable], [pickReadables], [pickWritable]).
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

    /**
     * Asynchronously displays the file/folder browser and returns the selected files (or an empty list if cancelled).
     */
    suspend fun pickFiles(ownerWindow: Window? = null): List<SelectedFile> =
        suspendCoroutine { cont ->
            show(
                ownerWindow = ownerWindow,
                onSelected = { files -> cont.resume(files) },
                onCancelled = { cont.resume(emptyList()) }
            )
        }

    /**
     * Asynchronously prompts the user to select a single file and opens a non-blocking [Readable] stream for it.
     */
    suspend fun pickReadable(ownerWindow: Window? = null): Pair<SelectedFile, Readable>? {
        mode = FileBrowserMode.OPEN_SINGLE_FILE
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            val selected = pickFiles(ownerWindow).firstOrNull() ?: return null
            val stream = selected.openReadable() ?: return null
            return selected to stream
        }
        val (path, readable) = FileSystem.openReadableFromFileDialog(
            title = title,
            initialDirectory = initialDirectory,
            filters = _filters
        ) ?: return null
        val leafName = path.substringAfterLast('/').substringAfterLast('\\')
        val file = SelectedFile(
            name = leafName,
            pathOrUri = path,
            readableOpener = { readable }
        )
        return file to readable
    }

    /**
     * Asynchronously prompts the user to select one or more files and opens non-blocking [Readable] streams for them.
     */
    suspend fun pickReadables(ownerWindow: Window? = null): List<Pair<SelectedFile, Readable>> {
        mode = FileBrowserMode.OPEN_MULTIPLE_FILES
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            val selectedList = pickFiles(ownerWindow)
            val out = mutableListOf<Pair<SelectedFile, Readable>>()
            for (sel in selectedList) {
                val stream = sel.openReadable()
                if (stream != null) {
                    out.add(sel to stream)
                }
            }
            return out
        }
        return FileSystem.openReadablesFromFileDialog(
            title = title,
            initialDirectory = initialDirectory,
            filters = _filters
        ).map { (path, readable) ->
            val leafName = path.substringAfterLast('/').substringAfterLast('\\')
            SelectedFile(
                name = leafName,
                pathOrUri = path,
                readableOpener = { readable }
            ) to readable
        }
    }

    /**
     * Asynchronously prompts the user to choose a save destination and opens a non-blocking [Writable] stream for it.
     */
    suspend fun pickWritable(
        suggestedFileName: String = this.suggestedFileName ?: "",
        ownerWindow: Window? = null
    ): Pair<SelectedFile, Writable>? {
        mode = FileBrowserMode.SAVE_FILE
        if (suggestedFileName.isNotEmpty()) {
            this.suggestedFileName = suggestedFileName
        }
        val factory = ownerWindow?.activeFactory ?: GenericUI.activeFactory
        if (factory != null) {
            val selected = pickFiles(ownerWindow).firstOrNull() ?: return null
            val writable = selected.openWritable(append = false)
            return selected to writable
        }
        val (path, writable) = FileSystem.openWritableFromFileDialog(
            title = title,
            initialDirectory = initialDirectory,
            suggestedFileName = this.suggestedFileName ?: "",
            filters = _filters
        ) ?: return null
        val leafName = path.substringAfterLast('/').substringAfterLast('\\')
        val file = SelectedFile(
            name = leafName,
            pathOrUri = path,
            writableOpener = { writable }
        )
        return file to writable
    }
}
