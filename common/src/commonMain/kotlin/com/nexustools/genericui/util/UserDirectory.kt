package com.nexustools.genericui.util

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.io.IFileSystem
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.io.StreamUtil
import com.nexustools.genericui.io.Writable
import com.nexustools.genericui.nativeimpl.system.DefaultUserDirectoryNativeImpl
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl

/**
 * Abstract base class for OS-specific user application directories ([ConfigDirectory] and [CacheDirectory])
 * implementing the shared 100% asynchronous [IFileSystem] contract.
 *
 * Automatically resolves its platform-specific [UserDirectoryNativeImpl] from the active [NativeFactory]
 * (or [factory] when explicitly provided), and transparently migrates any files written to the in-memory
 * fallback when [GenericUI.activeFactory] is initialized later in the application lifecycle.
 *
 * - [isAvailable] is always [FileSystemAvailability.Always] across all 8 backends (`:windows`, `:macos`, `:gtk`,
 *   `:qt`, `:swing`, `:android`, `:ios`, `:web`).
 * - System backends store files in the OS application config/cache directory and delegate async streams to [com.nexustools.genericui.io.FileSystem].
 * - `:web` (`WebUserDirectoryNativeImpl`) stores files directly in browser `window.indexedDB` (`"config"` and `"cache"`
 *   stores) without using [Database].
 */
abstract class UserDirectory internal constructor(
    val appName: String,
    val subdirectory: String,
    protected val factory: NativeFactory?,
    private val nativeImplOverride: UserDirectoryNativeImpl?
) : IFileSystem, Disposable {

    constructor(
        appName: String = GenericUI.applicationName,
        subdirectory: String = "",
        factory: NativeFactory? = null
    ) : this(appName, subdirectory, factory, null)

    protected abstract val fallbackScheme: String

    protected abstract fun createBackendImpl(activeFactory: NativeFactory): UserDirectoryNativeImpl

    protected open fun resolveCustomImpl(): UserDirectoryNativeImpl? = null

    private var boundFactory: NativeFactory? = null
    private var backingNativeImpl: UserDirectoryNativeImpl? = nativeImplOverride

    internal val nativeImpl: UserDirectoryNativeImpl
        get() {
            nativeImplOverride?.let { return it }
            resolveCustomImpl()?.let { return it }
            val currentImpl = backingNativeImpl
            if (currentImpl == null) {
                val initialFactory = factory ?: GenericUI.activeFactory
                boundFactory = initialFactory
                val created = initialFactory?.let { createBackendImpl(it) }
                    ?: DefaultUserDirectoryNativeImpl(appName, subdirectory, fallbackScheme)
                backingNativeImpl = created
                return created
            }
            if (factory == null) {
                val active = GenericUI.activeFactory
                if (active != null && active !== boundFactory) {
                    boundFactory = active
                    val upgraded = createBackendImpl(active)
                    if (currentImpl is DefaultUserDirectoryNativeImpl) {
                        for (name in currentImpl.listFiles()) {
                            if (!upgraded.exists(name)) {
                                currentImpl.readBytes(name)?.let { upgraded.writeBytes(name, it) }
                            }
                        }
                    }
                    backingNativeImpl = upgraded
                    return upgraded
                }
            }
            return currentImpl
        }

    val directoryPath: String
        get() = nativeImpl.directoryPath

    val fileSeparator: Char
        get() = nativeImpl.fileSeparator

    val fileCount: Int
        get() = listFiles().size

    override val isDisposed: Boolean
        get() = backingNativeImpl?.isDisposed == true

    override fun isAvailable(): FileSystemAvailability =
        FileSystemAvailability.Always

    override fun resolve(vararg segments: String): String {
        val cleaned = mutableListOf<String>()
        val base = directoryPath.trimEnd('/', '\\')
        for (raw in segments) {
            var part = raw.trim()
            if (part.isEmpty()) continue
            if (part == base) continue
            if (part.startsWith("$base/") || part.startsWith("$base\\")) {
                part = part.substring(base.length + 1)
            }
            for (sub in part.split('/', '\\')) {
                val token = sub.trim()
                if (token.isEmpty() || token == ".") continue
                if (token == "..") {
                    if (cleaned.isNotEmpty()) cleaned.removeAt(cleaned.lastIndex)
                } else {
                    cleaned.add(sanitizeFileName(token))
                }
            }
        }
        if (cleaned.isEmpty()) return directoryPath
        val sep = fileSeparator
        val joined = cleaned.joinToString(sep.toString())
        return if (cleaned.size == 1) {
            nativeImpl.resolveFilePath(cleaned[0])
        } else {
            "$base$sep$joined"
        }
    }

    override fun openDatabase(name: String, version: Int): Database {
        val dbFile = if (name.contains('.')) name else "$name.db"
        val resolvedDbPath = resolve(dbFile)
        val cleanDbName = "$fallbackScheme:$appName:${sanitizeFileName(dbFile)}"
        return Database(
            databaseName = cleanDbName,
            filePath = resolvedDbPath,
            version = version,
            factory = factory
        )
    }

    override suspend fun read(path: String): ByteBuffer? =
        nativeImpl.readAsync(relativeFileName(path))

    override suspend fun write(path: String, buffer: ByteBuffer, append: Boolean): Boolean =
        nativeImpl.writeAsync(relativeFileName(path), buffer, append)

    override suspend fun write(path: String, bytes: ByteArray, append: Boolean): Boolean =
        write(path, ByteBuffer.wrap(bytes), append)

    override suspend fun write(path: String, readable: Readable, append: Boolean): Boolean {
        val writable = openWritable(path, append)
        StreamUtil.pipeline(readable, writable, closeBoth = true)
        return true
    }

    override suspend fun openReadable(path: String): Readable? =
        nativeImpl.openReadableAsync(relativeFileName(path))

    override suspend fun openWritable(path: String, append: Boolean): Writable =
        nativeImpl.openWritableAsync(relativeFileName(path), append)

    suspend fun readTextAsync(path: String): String? =
        read(path)?.toString("utf8")

    suspend fun writeTextAsync(path: String, text: String, append: Boolean = false): Boolean =
        write(path, ByteBuffer.fromString(text), append)

    fun ensureExists(): Boolean = nativeImpl.ensureDirectoryExists()

    fun resolvePath(fileName: String): String = nativeImpl.resolveFilePath(relativeFileName(fileName))

    fun writeBytes(fileName: String, bytes: ByteArray): Boolean =
        nativeImpl.writeBytes(relativeFileName(fileName), bytes)

    fun readBytes(fileName: String): ByteArray? =
        nativeImpl.readBytes(relativeFileName(fileName))

    fun writeText(fileName: String, text: String): Boolean =
        nativeImpl.writeText(relativeFileName(fileName), text)

    fun readText(fileName: String): String? =
        nativeImpl.readText(relativeFileName(fileName))

    fun exists(fileName: String): Boolean =
        nativeImpl.exists(relativeFileName(fileName))

    fun fileSize(fileName: String): Long =
        nativeImpl.fileSize(relativeFileName(fileName))

    fun delete(fileName: String): Boolean =
        nativeImpl.delete(relativeFileName(fileName))

    fun listFiles(): List<String> = nativeImpl.listFiles()

    fun listFilesWithSuffix(suffix: String): List<String> = listFiles().filter { it.endsWith(suffix) }

    fun clear(): Int = nativeImpl.clearAll()

    fun totalByteSize(): Long = nativeImpl.totalByteSize()

    override fun dispose() {
        backingNativeImpl?.dispose()
    }

    protected fun relativeFileName(pathOrName: String): String {
        val trimmed = pathOrName.trim()
        val base = directoryPath.trimEnd('/', '\\')
        if (trimmed.startsWith("$base/")) {
            return trimmed.substring(base.length + 1)
        }
        if (trimmed.startsWith("$base\\")) {
            return trimmed.substring(base.length + 1)
        }
        return trimmed
    }

    companion object {
        fun sanitizeFileName(raw: String): String {
            val trimmed = raw.trim().trim('/', '\\').ifEmpty { "unnamed" }
            return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }
    }
}
