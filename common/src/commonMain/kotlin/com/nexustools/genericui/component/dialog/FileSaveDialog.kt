package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.enums.dialog.FileBrowserMode

/**
 * Native file save dialog supporting suggested filenames and [FileTypeFilter]s.
 */
class FileSaveDialog(
    title: String = "Save File",
    suggestedFileName: String? = "untitled",
    initialDirectory: String? = null,
    filters: List<FileTypeFilter> = emptyList()
) : FileBrowserDialog(
    mode = FileBrowserMode.SAVE_FILE,
    title = title,
    initialDirectory = initialDirectory,
    suggestedFileName = suggestedFileName,
    filters = filters
)
