package com.nexustools.genericui.nativeimpl.dialog

import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Native implementation contract for the cross-platform file and folder browser.
 *
 * - On `:web`, creates an invisible, un-interactable `<input type="file">` element (`pointer-events: none; opacity: 0; position: fixed; left: -9999px`),
 *   configures `webkitdirectory` (in `SELECT_FOLDER` mode), `multiple` (in `OPEN_MULTIPLE_FILES` mode), and `accept`
 *   (translated via [com.nexustools.genericui.component.dialog.FileTypeFilter.toWebAcceptAttribute]), triggers `.click()`,
 *   and invokes [FileBrowserCallback.onFilesSelected] or [FileBrowserCallback.onCancelled].
 * - On `:android`, launches the external system file browser app (`Intent.ACTION_OPEN_DOCUMENT`,
 *   `Intent.ACTION_CREATE_DOCUMENT`, or `Intent.ACTION_OPEN_DOCUMENT_TREE`) via Android's Activity result API
 *   and routes the returned `Uri`(s) or `Activity.RESULT_CANCELED` to [callback].
 * - On `:windows`, `:macos`, `:gtk`, `:qt`, `:swing`, `:ios`, opens the genuine native OS file/folder dialog.
 */
interface FileBrowserDialogNativeImpl : Disposable {
    fun showFileBrowser(
        parentWindow: WindowNativeImpl<*>?,
        request: FileBrowserRequest,
        callback: FileBrowserCallback
    )
}
