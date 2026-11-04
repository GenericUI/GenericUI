package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileSystem
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.Writable

/**
 * Cross-platform representation of a file or folder returned by [FileBrowserDialog].
 *
 * - On Desktop (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`), wraps a native filesystem path.
 * - On `:android`, wraps a `content://` `Uri` (or tree `Uri` for folders) returned by the external
 *   Android system file browser (`Intent.ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`).
 * - On `:web`, wraps an HTML5 `File` / `FileSystemFileHandle` / `FileSystemDirectoryHandle`.
 */
class SelectedFile(
    val name: String,
    val pathOrUri: String,
    val sizeBytes: Long = 0L,
    val mimeType: String? = null,
    val isDirectory: Boolean = false,
    private val bytesReader: (suspend () -> ByteArray)? = null,
    private val bytesWriter: (suspend (ByteArray) -> Unit)? = null,
    private val childrenLister: (suspend () -> List<SelectedFile>)? = null,
    private val readableOpener: (suspend () -> Readable?)? = null,
    private val writableOpener: (suspend (Boolean) -> Writable)? = null
) {
    val extension: String
        get() = name.substringAfterLast('.', "")

    /**
     * Asynchronously opens a [Readable] byte stream for this selected file without loading the entire file into memory.
     */
    suspend fun openReadable(): Readable? {
        readableOpener?.let { return it() }
        if (bytesReader != null) {
            return Readable.fromBytes(bytesReader.invoke())
        }
        return FileSystem.openReadable(pathOrUri)
    }

    /**
     * Asynchronously opens a [Writable] byte stream for writing or appending to this selected file.
     */
    suspend fun openWritable(append: Boolean = false): Writable {
        writableOpener?.let { return it(append) }
        if (bytesWriter != null) {
            return Writable.toMemory { committed ->
                if (append && bytesReader != null) {
                    val existing = bytesReader.invoke()
                    bytesWriter.invoke(if (existing.isEmpty()) committed else existing + committed)
                } else {
                    bytesWriter.invoke(committed)
                }
            }
        }
        return FileSystem.openWritable(pathOrUri, append)
    }

    suspend fun readBuffer(): ByteBuffer =
        ByteBuffer.wrap(readBytes())

    suspend fun readBytes(): ByteArray =
        bytesReader?.invoke()
            ?: FileSystem.readFile(pathOrUri)
            ?: ByteArray(0)

    suspend fun readText(): String =
        readBytes().decodeToString()

    suspend fun writeBuffer(buffer: ByteBuffer) {
        writeBytes(buffer.toByteArray())
    }

    suspend fun writeBytes(bytes: ByteArray) {
        if (bytesWriter != null) {
            bytesWriter.invoke(bytes)
        } else {
            FileSystem.writeFile(pathOrUri, bytes)
        }
    }

    suspend fun writeText(text: String) {
        writeBytes(text.encodeToByteArray())
    }

    suspend fun listChildren(): List<SelectedFile> =
        if (isDirectory) (childrenLister?.invoke() ?: emptyList()) else emptyList()
}
