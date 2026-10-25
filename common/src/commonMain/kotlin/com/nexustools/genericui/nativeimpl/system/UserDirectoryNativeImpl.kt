package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable

/**
 * Per-backend native contract for managing files inside an OS user directory
 * ([com.nexustools.genericui.util.UserDirectory], [com.nexustools.genericui.util.ConfigDirectory],
 * and [com.nexustools.genericui.util.CacheDirectory]).
 */
interface UserDirectoryNativeImpl : Disposable {
    val appName: String
    val subdirectory: String
    val directoryPath: String
    val fileSeparator: Char

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
}
