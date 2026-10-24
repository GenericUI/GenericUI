package com.nexustools.genericui.component.dialog

/**
 * Cross-platform representation of a file or folder returned by [FileBrowserDialog].
 *
 * - On Desktop (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`), wraps a native filesystem path.
 * - On `:android`, wraps a `content://` `Uri` (or tree `Uri` for folders) returned by the external
 *   Android system file browser (`Intent.ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`).
 * - On `:web`, wraps an HTML5 `File` / `FileSystemDirectoryHandle` selected via the invisible `<input type="file">`.
 */
class SelectedFile(
    val name: String,
    val pathOrUri: String,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val isDirectory: Boolean = false,
    private val bytesReader: (suspend () -> ByteArray)? = null,
    private val bytesWriter: (suspend (ByteArray) -> Unit)? = null,
    private val childrenLister: (suspend () -> List<SelectedFile>)? = null
) {
    val extension: String
        get() = name.substringAfterLast('.', "")

    suspend fun readBytes(): ByteArray =
        bytesReader?.invoke() ?: ByteArray(0)

    suspend fun readText(): String =
        readBytes().decodeToString()

    suspend fun writeBytes(bytes: ByteArray) {
        bytesWriter?.invoke(bytes)
    }

    suspend fun writeText(text: String) {
        writeBytes(text.encodeToByteArray())
    }

    suspend fun listChildren(): List<SelectedFile> =
        if (isDirectory) (childrenLister?.invoke() ?: emptyList()) else emptyList()
}
