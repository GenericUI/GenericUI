package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileSystem
import com.nexustools.genericui.nativeimpl.io.DefaultFileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DefaultFileWritableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl

/**
 * Per-backend native contract for managing files inside an OS user directory
 * ([com.nexustools.genericui.util.UserDirectory], [com.nexustools.genericui.util.ConfigDirectory],
 * and [com.nexustools.genericui.util.CacheDirectory]).
 *
 * System backends where [directoryPath] is a real OS directory path can set [usesFileSystemBasePath]
 * to `true` to delegate async stream and buffer operations directly to [FileSystem], while `:web`
 * (`WebUserDirectoryNativeImpl`) overrides these async methods to use browser `window.indexedDB` directly.
 */
interface UserDirectoryNativeImpl : Disposable {
    val appName: String
    val subdirectory: String
    val directoryPath: String
    val fileSeparator: Char

    /**
     * When `true`, [readAsync], [writeAsync], [openReadableAsync], and [openWritableAsync] delegate
     * directly to [FileSystem] using [resolveFilePath].
     */
    val usesFileSystemBasePath: Boolean
        get() = false

    fun ensureDirectoryExists(): Boolean
    fun resolveFilePath(fileName: String): String
    fun writeBytes(fileName: String, bytes: ByteArray): Boolean
    fun readBytes(fileName: String): ByteArray?
    fun writeText(fileName: String, text: String): Boolean = writeBytes(fileName, text.encodeToByteArray())
    fun readText(fileName: String): String? = readBytes(fileName)?.decodeToString()
    fun exists(fileName: String): Boolean
    fun fileSize(fileName: String): Long
    fun delete(fileName: String): Boolean
    fun listFiles(): List<String>
    fun clearAll(): Int
    fun totalByteSize(): Long

    suspend fun readAsync(fileName: String): ByteBuffer? {
        if (usesFileSystemBasePath) {
            return FileSystem.read(resolveFilePath(fileName))
        }
        return readBytes(fileName)?.let { ByteBuffer.wrap(it) }
    }

    suspend fun writeAsync(fileName: String, buffer: ByteBuffer, append: Boolean = false): Boolean {
        if (usesFileSystemBasePath) {
            ensureDirectoryExists()
            return FileSystem.write(resolveFilePath(fileName), buffer, append)
        }
        ensureDirectoryExists()
        val incoming = buffer.toByteArray()
        return if (append) {
            val existing = readBytes(fileName)
            val merged = if (existing == null || existing.isEmpty()) incoming else existing + incoming
            writeBytes(fileName, merged)
        } else {
            writeBytes(fileName, incoming)
        }
    }

    suspend fun openReadableAsync(fileName: String): FileReadableNativeImpl? {
        if (usesFileSystemBasePath) {
            return FileSystem.nativeImpl.openReadable(resolveFilePath(fileName))
        }
        val buf = readAsync(fileName) ?: return null
        return DefaultFileReadableNativeImpl(
            initialBytes = buf.toByteArray(),
            filePath = resolveFilePath(fileName)
        )
    }

    suspend fun openWritableAsync(fileName: String, append: Boolean = false): FileWritableNativeImpl {
        if (usesFileSystemBasePath) {
            ensureDirectoryExists()
            return FileSystem.nativeImpl.openWritable(resolveFilePath(fileName), append)
        }
        val initial = if (append) readAsync(fileName)?.toByteArray() ?: ByteArray(0) else ByteArray(0)
        return DefaultFileWritableNativeImpl(
            initialBytes = initial,
            filePath = resolveFilePath(fileName),
            appendMode = append
        ) { committed ->
            writeAsync(fileName, ByteBuffer.wrap(committed), append = false)
        }
    }
}
