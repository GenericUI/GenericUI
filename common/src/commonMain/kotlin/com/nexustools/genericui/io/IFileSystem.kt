package com.nexustools.genericui.io

import com.nexustools.genericui.util.Database

/**
 * Shared 100% asynchronous, non-blocking file system interface implemented by both
 * [FileSystem] and [com.nexustools.genericui.util.UserDirectory] ([com.nexustools.genericui.util.ConfigDirectory]
 * and [com.nexustools.genericui.util.CacheDirectory]).
 */
interface IFileSystem {
    /**
     * Reports the availability of file system operations on the current platform:
     * - [FileSystemAvailability.Always] when operations succeed without interactive prompts
     *   (e.g., Desktop `FileSystem` and all `UserDirectory` instances).
     * - [FileSystemAvailability.WithPermission] when OS permission or a file/directory picker grant is required.
     * - [FileSystemAvailability.Unavailable] when direct path operations are unavailable.
     */
    fun isAvailable(): FileSystemAvailability

    /**
     * Resolves a normalized file system path from one or more path [segments].
     */
    fun resolve(vararg segments: String): String

    /**
     * Opens a structured [Database] backed by a database file resolved within this [IFileSystem].
     */
    fun openDatabase(name: String = "database.db", version: Int = 1): Database

    /**
     * Asynchronously reads the entire file at [path] into a [ByteBuffer], or returns `null` if not found.
     */
    suspend fun read(path: String): ByteBuffer?

    /**
     * Asynchronously writes the bytes of [buffer] to [path], appending when [append] is `true`.
     */
    suspend fun write(path: String, buffer: ByteBuffer, append: Boolean = false): Boolean

    /**
     * Asynchronously writes [bytes] to [path], appending when [append] is `true`.
     */
    suspend fun write(path: String, bytes: ByteArray, append: Boolean = false): Boolean =
        write(path, ByteBuffer.wrap(bytes), append)

    /**
     * Asynchronously pipes all chunks from [readable] into [path], appending when [append] is `true`.
     */
    suspend fun write(path: String, readable: Readable, append: Boolean = false): Boolean

    /**
     * Asynchronously opens a [Readable] byte stream for [path], or returns `null` if the file does not exist.
     */
    suspend fun openReadable(path: String): Readable?

    /**
     * Asynchronously opens a [Writable] byte stream for [path], appending when [append] is `true`.
     */
    suspend fun openWritable(path: String, append: Boolean = false): Writable
}
