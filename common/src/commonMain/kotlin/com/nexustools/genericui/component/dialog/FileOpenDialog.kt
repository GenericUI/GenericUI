package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.enums.dialog.FileBrowserMode

/**
 * Native file open dialog supporting single or multiple file selection and [FileTypeFilter]s.
 */
class FileOpenDialog(
    title: String = "Open File",
    allowMultiple: Boolean = false,
    initialDirectory: String? = null,
    filters: List<FileTypeFilter> = emptyList()
) : FileBrowserDialog(
    mode = if (allowMultiple) FileBrowserMode.OPEN_MULTIPLE_FILES else FileBrowserMode.OPEN_SINGLE_FILE,
    title = title,
    initialDirectory = initialDirectory,
    filters = filters
) {
    var allowMultiple: Boolean
        get() = mode == FileBrowserMode.OPEN_MULTIPLE_FILES
        set(value) {
            mode = if (value) FileBrowserMode.OPEN_MULTIPLE_FILES else FileBrowserMode.OPEN_SINGLE_FILE
        }
}
