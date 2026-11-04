package com.nexustools.genericui.io

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.io.DefaultFileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl
import com.nexustools.genericui.util.Database

/**
 * NodeJS `fs.promises`-like 100% asynchronous, non-blocking cross-platform OS [IFileSystem].
 *
 * Delegates all operations to the active backend's [FileSystemNativeImpl]:
 * - **Desktop (`:qt`, `:gtk`, `:windows`, `:macos`, `:swing`)**: [isAvailable] is [FileSystemAvailability.Always];
 *   reads and writes OS file paths asynchronously without blocking the UI thread.
 * - **Mobile (`:android`, `:ios`)**: [isAvailable] is [FileSystemAvailability.WithPermission] for external storage /
 *   Storage Access Framework / UIDocumentPicker, and [FileSystemAvailability.Always] for app-sandbox paths.
 * - **Web (`:web`)**: [isAvailable] is [FileSystemAvailability.WithPermission] via the File System Access API
 *   (`window.showOpenFilePicker`, `window.showSaveFilePicker`, `window.showDirectoryPicker`) and `<input type="file">`.
 */
object FileSystem : IFileSystem {

    private var fallbackImpl: DefaultFileSystemNativeImpl = DefaultFileSystemNativeImpl()
    private var boundFactory: NativeFactory? = null
    private var factoryImpl: FileSystemNativeImpl? = null

    /**
     * Optional explicit override [FileSystemNativeImpl] for tests or custom environments.
     */
    internal var customNativeImpl: FileSystemNativeImpl? = null

    internal val nativeImpl: FileSystemNativeImpl
        get() {
            customNativeImpl?.let { return it }
            val active = GenericUI.activeFactory
            if (active != null) {
                if (active !== boundFactory || factoryImpl == null) {
                    boundFactory = active
                    factoryImpl = active.createFileSystem()
                }
                return factoryImpl!!
            }
            return fallbackImpl
        }

    val fileSeparator: Char
        get() = nativeImpl.fileSeparator

    override fun isAvailable(): FileSystemAvailability =
        nativeImpl.isAvailable()

    override fun resolve(vararg segments: String): String =
        nativeImpl.resolve(*segments)

    override fun openDatabase(name: String, version: Int): Database {
        val dbFileName = if (name.contains('.')) name else "$name.db"
        val dbFilePath = resolve(dbFileName)
        val cleanName = name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').ifEmpty { "database" }
        return Database(
            databaseName = cleanName,
            filePath = dbFilePath,
            version = version
        )
    }

    override suspend fun read(path: String): ByteBuffer? =
        nativeImpl.read(path)

    override suspend fun write(path: String, buffer: ByteBuffer, append: Boolean): Boolean =
        nativeImpl.write(path, buffer, append)

    override suspend fun write(path: String, bytes: ByteArray, append: Boolean): Boolean =
        nativeImpl.write(path, ByteBuffer.wrap(bytes), append)

    override suspend fun write(path: String, readable: Readable, append: Boolean): Boolean {
        val writable = openWritable(path, append)
        StreamUtil.pipeline(readable, writable, closeBoth = true)
        return true
    }

    override suspend fun openReadable(path: String): Readable? =
        nativeImpl.openReadable(path)

    override suspend fun openWritable(path: String, append: Boolean): Writable =
        nativeImpl.openWritable(path, append)

    fun supports(): Boolean = nativeImpl.supports()

    fun supportsReading(): Boolean = nativeImpl.supportsReading()

    fun supportsWriting(): Boolean = nativeImpl.supportsWriting()

    fun supportsDirectoryAccess(): Boolean = nativeImpl.supportsDirectoryAccess()

    fun hasPermission(path: String? = null, write: Boolean = false): FilePermissionStatus =
        nativeImpl.hasPermission(path, write)

    suspend fun requestPermission(path: String? = null, write: Boolean = false): FilePermissionStatus =
        nativeImpl.requestPermission(path, write)

    suspend fun requestDirectoryAccess(initialPath: String? = null): String? =
        nativeImpl.requestDirectoryAccess(initialPath)

    suspend fun createReadStream(path: String): Readable? =
        openReadable(path)

    suspend fun createWriteStream(path: String, append: Boolean = false): Writable =
        openWritable(path, append)

    suspend fun createWriteStream(path: String, mode: FileOpenMode): Writable =
        nativeImpl.createWriteStream(path, mode)

    suspend fun openReadableFromFileDialog(
        title: String = "Open File",
        initialDirectory: String? = null,
        filters: List<FileTypeFilter> = emptyList()
    ): Pair<String, Readable>? =
        nativeImpl.openReadableFromFileDialog(title, initialDirectory, filters)

    suspend fun openReadablesFromFileDialog(
        title: String = "Open Files",
        initialDirectory: String? = null,
        filters: List<FileTypeFilter> = emptyList()
    ): List<Pair<String, Readable>> =
        nativeImpl.openReadablesFromFileDialog(title, initialDirectory, filters)

    suspend fun openWritableFromFileDialog(
        title: String = "Save File",
        initialDirectory: String? = null,
        suggestedFileName: String = "",
        filters: List<FileTypeFilter> = emptyList()
    ): Pair<String, Writable>? =
        nativeImpl.openWritableFromFileDialog(title, initialDirectory, suggestedFileName, filters)

    suspend fun readFile(path: String): ByteArray? =
        nativeImpl.readFile(path)

    suspend fun readTextFile(path: String): String? =
        readFile(path)?.decodeToString()

    suspend fun writeFile(path: String, bytes: ByteArray): Boolean =
        nativeImpl.writeFile(path, bytes)

    suspend fun writeTextFile(path: String, text: String): Boolean =
        writeFile(path, text.encodeToByteArray())

    suspend fun appendFile(path: String, bytes: ByteArray): Boolean =
        nativeImpl.appendFile(path, bytes)

    suspend fun appendTextFile(path: String, text: String): Boolean =
        appendFile(path, text.encodeToByteArray())

    suspend fun exists(path: String): Boolean =
        nativeImpl.exists(path)

    suspend fun stat(path: String): FileStat? =
        nativeImpl.stat(path)

    suspend fun readdir(path: String): List<String> =
        nativeImpl.readdir(path)

    suspend fun mkdir(path: String, recursive: Boolean = true): Boolean =
        nativeImpl.mkdir(path, recursive)

    suspend fun unlink(path: String): Boolean =
        nativeImpl.unlink(path)

    suspend fun rmdir(path: String, recursive: Boolean = false): Boolean =
        nativeImpl.rmdir(path, recursive)

    suspend fun rename(oldPath: String, newPath: String): Boolean =
        nativeImpl.rename(oldPath, newPath)

    suspend fun copyFile(srcPath: String, dstPath: String, overwrite: Boolean = true): Boolean =
        nativeImpl.copyFile(srcPath, dstPath, overwrite)

    fun resetFallback() {
        fallbackImpl.dispose()
        fallbackImpl = DefaultFileSystemNativeImpl()
    }
}
