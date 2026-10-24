package com.nexustools.genericui.component.dialog

/**
 * Callback interface for asynchronous native file/folder browser requests.
 * Explicitly reports both selection ([onFilesSelected]) and user cancellation ([onCancelled]),
 * which is essential on platforms like `:android` (where the request is passed to an external
 * system file-picker Activity) and `:web` (where an invisible `<input type="file">` is triggered).
 */
interface FileBrowserCallback {
    fun onFilesSelected(files: List<SelectedFile>, selectedFilter: FileTypeFilter? = null)
    fun onCancelled()
}
