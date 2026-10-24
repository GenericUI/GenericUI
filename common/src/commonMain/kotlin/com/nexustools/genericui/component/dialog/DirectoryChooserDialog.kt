package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.enums.dialog.FileBrowserMode

/**
 * Native folder/directory selection dialog ([FileBrowserMode.SELECT_FOLDER]).
 *
 * Uses `FOS_PICKFOLDERS` on Windows, `canChooseDirectories` on macOS, `gtk_file_dialog_select_folder`
 * on GTK, `QFileDialog::Directory` on Qt, `JFileChooser.DIRECTORIES_ONLY` on Swing,
 * invisible `<input type="file" webkitdirectory>` on Web, `Intent.ACTION_OPEN_DOCUMENT_TREE`
 * on Android, and `UIDocumentPickerViewController` (`UTTypeFolder`) on iOS.
 */
class DirectoryChooserDialog(
    title: String = "Select Folder",
    initialDirectory: String? = null,
    createDirectoriesAllowed: Boolean = true
) : FileBrowserDialog(
    mode = FileBrowserMode.SELECT_FOLDER,
    title = title,
    initialDirectory = initialDirectory,
    createDirectoriesAllowed = createDirectoriesAllowed
)
