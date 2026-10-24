package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.enums.dialog.FileBrowserMode

/**
 * Configuration options for launching a native file or folder browser via [FileBrowserDialog].
 */
data class FileBrowserRequest(
    val mode: FileBrowserMode = FileBrowserMode.OPEN_SINGLE_FILE,
    val title: String = "Select File",
    val initialDirectory: String? = null,
    val defaultFileName: String? = null,
    val suggestedFileName: String? = defaultFileName,
    val filters: List<FileTypeFilter> = emptyList(),
    val selectedFilterIndex: Int = 0,
    val activeFilterIndex: Int = selectedFilterIndex,
    val showHiddenFiles: Boolean = false,
    val createDirectoriesAllowed: Boolean = true,
    val persistAndroidUriPermissions: Boolean = true
)
