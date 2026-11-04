package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.io.FileStat
import com.nexustools.genericui.io.FileSystemAvailability

/**
 * Per-backend native contract for 100% asynchronous, non-blocking OS file system operations.
 */
interface FileSystemNativeImpl : Disposable {
    val fileSeparator: Char
        get() = '/'

    fun isAvailable(): FileSystemAvailability =
        if (supports()) FileSystemAvailability.Always else FileSystemAvailability.Unavailable

    fun supports(): Boolean = true
    fun supportsReading(): Boolean = supports()
    fun supportsWriting(): Boolean = supports()
    fun supportsDirectoryAccess(): Boolean = supports()
    fun hasPermission(path: String? = null, write: Boolean = false): FilePermissionStatus =
        when (isAvailable()) {
            FileSystemAvailability.Always -> FilePermissionStatus.GRANTED
            FileSystemAvailability.WithPermission -> FilePermissionStatus.PROMPT_REQUIRED
            FileSystemAvailability.Unavailable -> FilePermissionStatus.UNSUPPORTED
        }

    /**
     * Resolves an absolute file system path from a sequence of [segments].
     */
    fun resolve(vararg segments: String): String

    suspend fun requestPermission(path: String? = null, write: Boolean = false): FilePermissionStatus =
        hasPermission(path, write)

    suspend fun requestDirectoryAccess(initialPath: String? = null): String? = initialPath

    suspend fun read(path: String): ByteBuffer? =
        readFile(path)?.let { ByteBuffer.wrap(it) }

    suspend fun write(path: String, buffer: ByteBuffer, append: Boolean = false): Boolean =
        if (append) appendFile(path, buffer.toByteArray()) else writeFile(path, buffer.toByteArray())

    suspend fun openReadable(path: String): FileReadableNativeImpl? =
        createReadStream(path)

    suspend fun openWritable(path: String, append: Boolean = false): FileWritableNativeImpl =
        createWriteStream(path, if (append) FileOpenMode.WRITE_APPEND else FileOpenMode.WRITE_TRUNCATE)

    suspend fun createReadStream(path: String): FileReadableNativeImpl?

    suspend fun createWriteStream(
        path: String,
        mode: FileOpenMode = FileOpenMode.WRITE_TRUNCATE
    ): FileWritableNativeImpl

    suspend fun openReadableFromFileDialog(
        title: String = "Open File",
        initialDirectory: String? = null,
        filters: List<FileTypeFilter> = emptyList()
    ): Pair<String, FileReadableNativeImpl>? = null

    suspend fun openReadablesFromFileDialog(
        title: String = "Open Files",
        initialDirectory: String? = null,
        filters: List<FileTypeFilter> = emptyList()
    ): List<Pair<String, FileReadableNativeImpl>> = emptyList()

    suspend fun openWritableFromFileDialog(
        title: String = "Save File",
        initialDirectory: String? = null,
        suggestedFileName: String = "",
        filters: List<FileTypeFilter> = emptyList()
    ): Pair<String, FileWritableNativeImpl>? = null

    suspend fun readFile(path: String): ByteArray?
    suspend fun writeFile(path: String, bytes: ByteArray): Boolean
    suspend fun appendFile(path: String, bytes: ByteArray): Boolean
    suspend fun exists(path: String): Boolean
    suspend fun stat(path: String): FileStat?
    suspend fun readdir(path: String): List<String>
    suspend fun mkdir(path: String, recursive: Boolean = true): Boolean
    suspend fun unlink(path: String): Boolean
    suspend fun rmdir(path: String, recursive: Boolean = false): Boolean
    suspend fun rename(oldPath: String, newPath: String): Boolean
    suspend fun copyFile(srcPath: String, dstPath: String, overwrite: Boolean = true): Boolean
}
