@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.io.FileStat
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileCreationDate
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeSymbolicLink
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.lastPathComponent
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeData
import platform.Foundation.writeToFile
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import kotlin.coroutines.resume

/**
 * Shared Apple POSIX/Foundation file system backend.
 * Platform modules (`:desktop:macos` using `NSOpenPanel`, `:mobile:ios` using `UIDocumentPickerViewController`)
 * extend this class to attach native file/folder pickers while reusing real disk I/O and security-scoped NSURLs.
 */
open class AppleFileSystemBase : FileSystemNativeImpl {
    private val lock = AppleReentrantLock()
    private val ioQueue = dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0uL)
    private val securityScopedUrlsByPath = LinkedHashMap<String, NSURL>()
    private var disposedState = false

    override val fileSeparator: Char = '/'

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    override fun isAvailable(): FileSystemAvailability = FileSystemAvailability.Always

    override fun supports(): Boolean = true

    override fun supportsReading(): Boolean = true

    override fun supportsWriting(): Boolean = true

    override fun supportsDirectoryAccess(): Boolean = true

    override fun hasPermission(path: String?, write: Boolean): FilePermissionStatus {
        if (isDisposed) return FilePermissionStatus.UNSUPPORTED
        if (path.isNullOrBlank()) return FilePermissionStatus.GRANTED
        return withSecurityScope(path) {
            val fm = NSFileManager.defaultManager
            if (!write) {
                if (!fm.fileExistsAtPath(path) || fm.isReadableFileAtPath(path)) {
                    FilePermissionStatus.GRANTED
                } else {
                    FilePermissionStatus.DENIED
                }
            } else {
                if (fm.fileExistsAtPath(path)) {
                    if (fm.isWritableFileAtPath(path)) FilePermissionStatus.GRANTED else FilePermissionStatus.DENIED
                } else {
                    val parent = NSString.create(string = path).stringByDeletingLastPathComponent
                    if (parent.isEmpty() || !fm.fileExistsAtPath(parent) || fm.isWritableFileAtPath(parent)) {
                        FilePermissionStatus.GRANTED
                    } else {
                        FilePermissionStatus.DENIED
                    }
                }
            }
        }
    }

    override fun resolve(vararg segments: String): String {
        if (segments.isEmpty()) return ""
        val firstNonEmpty = segments.firstOrNull { it.isNotEmpty() } ?: return ""
        val leadingSlash = firstNonEmpty.startsWith('/')
        val parts = ArrayList<String>()
        for (segment in segments) {
            for (part in segment.split('/')) {
                if (part.isNotEmpty() && part != ".") {
                    parts.add(part)
                }
            }
        }
        val joined = parts.joinToString("/")
        return if (leadingSlash) "/$joined" else joined
    }

    override suspend fun requestPermission(path: String?, write: Boolean): FilePermissionStatus {
        return hasPermission(path, write)
    }

    override suspend fun requestDirectoryAccess(initialPath: String?): String? {
        return null
    }

    override suspend fun read(path: String): ByteBuffer? {
        val bytes = readFile(path) ?: return null
        return ByteBuffer.wrap(bytes)
    }

    override suspend fun write(path: String, buffer: ByteBuffer, append: Boolean): Boolean {
        val bytes = buffer.remainingByteArray()
        return if (append) appendFile(path, bytes) else writeFile(path, bytes)
    }

    override suspend fun openReadable(path: String): FileReadableNativeImpl? {
        return createReadStream(path)
    }

    override suspend fun openWritable(path: String, append: Boolean): FileWritableNativeImpl {
        val mode = if (append) FileOpenMode.WRITE_APPEND else FileOpenMode.WRITE_TRUNCATE
        return createWriteStream(path, mode)
    }

    override suspend fun createReadStream(path: String): FileReadableNativeImpl? {
        if (isDisposed || !exists(path)) return null
        val scopedUrl = resolveSecurityScopedUrl(path)
        return AppleFileReadableNativeImpl(path, securityScopedUrl = scopedUrl)
    }

    override suspend fun createWriteStream(path: String, mode: FileOpenMode): FileWritableNativeImpl {
        require(mode != FileOpenMode.READ) { "createWriteStream cannot open in READ-only mode for $path" }
        val append = mode == FileOpenMode.WRITE_APPEND || mode == FileOpenMode.READ_WRITE
        val scopedUrl = resolveSecurityScopedUrl(path)
        return AppleFileWritableNativeImpl(path, appendMode = append, securityScopedUrl = scopedUrl)
    }

    override suspend fun openReadableFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): Pair<String, FileReadableNativeImpl>? = null

    override suspend fun openReadablesFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): List<Pair<String, FileReadableNativeImpl>> = emptyList()

    override suspend fun openWritableFromFileDialog(
        title: String,
        initialDirectory: String?,
        suggestedFileName: String,
        filters: List<FileTypeFilter>
    ): Pair<String, FileWritableNativeImpl>? = null

    override suspend fun readFile(path: String): ByteArray? {
        if (isDisposed || path.isBlank()) return null
        return runOnIoQueue {
            withSecurityScope(path) {
                val data = NSData.dataWithContentsOfFile(path) ?: return@withSecurityScope null
                AppleDataBridge.nsDataToByteArray(data)
            }
        }
    }

    override suspend fun writeFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed || path.isBlank()) return false
        val copy = bytes.copyOf()
        return runOnIoQueue {
            withSecurityScope(path) {
                ensureParentDirExists(path)
                AppleDataBridge.byteArrayToNSData(copy).writeToFile(path, atomically = true)
            }
        }
    }

    override suspend fun appendFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed || path.isBlank()) return false
        val copy = bytes.copyOf()
        return runOnIoQueue {
            withSecurityScope(path) {
                val fm = NSFileManager.defaultManager
                ensureParentDirExists(path)
                if (!fm.fileExistsAtPath(path)) {
                    return@withSecurityScope AppleDataBridge.byteArrayToNSData(copy).writeToFile(path, atomically = true)
                }
                val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: return@withSecurityScope false
                try {
                    memScoped {
                        val err = alloc<ObjCObjectVar<NSError?>>()
                        val endOff = alloc<kotlinx.cinterop.ULongVar>()
                        handle.seekToEndReturningOffset(endOff.ptr, error = err.ptr)
                        val ok = handle.writeData(AppleDataBridge.byteArrayToNSData(copy), error = err.ptr)
                        handle.synchronizeAndReturnError(err.ptr)
                        ok
                    }
                } finally {
                    memScoped {
                        val err = alloc<ObjCObjectVar<NSError?>>()
                        handle.closeAndReturnError(err.ptr)
                    }
                }
            }
        }
    }

    override suspend fun exists(path: String): Boolean {
        if (isDisposed || path.isBlank()) return false
        return runOnIoQueue {
            withSecurityScope(path) {
                NSFileManager.defaultManager.fileExistsAtPath(path)
            }
        }
    }

    override suspend fun stat(path: String): FileStat? {
        if (isDisposed || path.isBlank()) return null
        return runOnIoQueue {
            withSecurityScope(path) {
                val fm = NSFileManager.defaultManager
                if (!fm.fileExistsAtPath(path)) return@withSecurityScope null
                val attrs = memScoped {
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    fm.attributesOfItemAtPath(path, error = err.ptr)
                } ?: return@withSecurityScope null

                val nsType = attrs[NSFileType]?.toString()
                val isDir = nsType == NSFileTypeDirectory
                val isSymlink = nsType == NSFileTypeSymbolicLink
                val size = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
                val modDate = attrs[NSFileModificationDate] as? NSDate
                val createDate = attrs[NSFileCreationDate] as? NSDate
                val modMs = ((modDate?.timeIntervalSince1970 ?: 0.0) * 1000.0).toLong()
                val createMs = ((createDate?.timeIntervalSince1970 ?: 0.0) * 1000.0).toLong()
                val name = NSString.create(string = path).lastPathComponent

                FileStat(
                    path = path,
                    name = name,
                    size = if (isDir) 0L else size,
                    isFile = !isDir && !isSymlink,
                    isDirectory = isDir,
                    isSymbolicLink = isSymlink,
                    lastModifiedEpochMs = modMs,
                    createdEpochMs = createMs,
                    isReadable = fm.isReadableFileAtPath(path),
                    isWritable = fm.isWritableFileAtPath(path)
                )
            }
        }
    }

    override suspend fun readdir(path: String): List<String> {
        if (isDisposed || path.isBlank()) return emptyList()
        return runOnIoQueue {
            withSecurityScope(path) {
                val items = memScoped {
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error = err.ptr)
                } ?: return@withSecurityScope emptyList()
                items.mapNotNull { it?.toString() }
            }
        }
    }

    override suspend fun mkdir(path: String, recursive: Boolean): Boolean {
        if (isDisposed || path.isBlank()) return false
        return runOnIoQueue {
            withSecurityScope(path) {
                memScoped {
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    NSFileManager.defaultManager.createDirectoryAtPath(
                        path,
                        withIntermediateDirectories = recursive,
                        attributes = null,
                        error = err.ptr
                    )
                }
            }
        }
    }

    override suspend fun unlink(path: String): Boolean {
        if (isDisposed || path.isBlank()) return false
        return runOnIoQueue {
            withSecurityScope(path) {
                val fm = NSFileManager.defaultManager
                memScoped {
                    val isDir = alloc<BooleanVar>()
                    if (!fm.fileExistsAtPath(path, isDirectory = isDir.ptr) || isDir.value) {
                        return@withSecurityScope false
                    }
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    fm.removeItemAtPath(path, error = err.ptr)
                }
            }
        }
    }

    override suspend fun rmdir(path: String, recursive: Boolean): Boolean {
        if (isDisposed || path.isBlank()) return false
        return runOnIoQueue {
            withSecurityScope(path) {
                val fm = NSFileManager.defaultManager
                memScoped {
                    val isDir = alloc<BooleanVar>()
                    if (!fm.fileExistsAtPath(path, isDirectory = isDir.ptr) || !isDir.value) {
                        return@withSecurityScope false
                    }
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    if (!recursive) {
                        val children = fm.contentsOfDirectoryAtPath(path, error = err.ptr)
                        if (children != null && children.isNotEmpty()) {
                            return@withSecurityScope false
                        }
                    }
                    fm.removeItemAtPath(path, error = err.ptr)
                }
            }
        }
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean {
        if (isDisposed || oldPath.isBlank() || newPath.isBlank()) return false
        return runOnIoQueue {
            withSecurityScope(oldPath) {
                withSecurityScope(newPath) {
                    val fm = NSFileManager.defaultManager
                    if (!fm.fileExistsAtPath(oldPath)) return@withSecurityScope false
                    ensureParentDirExists(newPath)
                    memScoped {
                        val isDir = alloc<BooleanVar>()
                        val err = alloc<ObjCObjectVar<NSError?>>()
                        if (fm.fileExistsAtPath(newPath, isDirectory = isDir.ptr) && !isDir.value) {
                            fm.removeItemAtPath(newPath, error = err.ptr)
                        }
                        fm.moveItemAtPath(oldPath, toPath = newPath, error = err.ptr)
                    }
                }
            }
        }
    }

    override suspend fun copyFile(srcPath: String, dstPath: String, overwrite: Boolean): Boolean {
        if (isDisposed || srcPath.isBlank() || dstPath.isBlank()) return false
        return runOnIoQueue {
            withSecurityScope(srcPath) {
                withSecurityScope(dstPath) {
                    val fm = NSFileManager.defaultManager
                    if (!fm.fileExistsAtPath(srcPath)) return@withSecurityScope false
                    if (fm.fileExistsAtPath(dstPath)) {
                        if (!overwrite) return@withSecurityScope false
                        memScoped {
                            val err = alloc<ObjCObjectVar<NSError?>>()
                            fm.removeItemAtPath(dstPath, error = err.ptr)
                        }
                    }
                    ensureParentDirExists(dstPath)
                    memScoped {
                        val err = alloc<ObjCObjectVar<NSError?>>()
                        fm.copyItemAtPath(srcPath, toPath = dstPath, error = err.ptr)
                    }
                }
            }
        }
    }

    override fun dispose() {
        lock.withLock {
            if (disposedState) return
            disposedState = true
            securityScopedUrlsByPath.clear()
        }
    }

    protected fun registerSecurityScopedUrl(url: NSURL): String? {
        val path = url.path ?: return null
        lock.withLock {
            securityScopedUrlsByPath[path] = url
        }
        return path
    }

    protected fun unregisterSecurityScopedPath(path: String) {
        lock.withLock {
            securityScopedUrlsByPath.remove(path)
        }
    }

    protected fun createSecurityScopedReadable(url: NSURL): Pair<String, FileReadableNativeImpl>? {
        val path = registerSecurityScopedUrl(url) ?: return null
        return path to AppleFileReadableNativeImpl(path, securityScopedUrl = url)
    }

    protected fun createSecurityScopedWritable(url: NSURL, append: Boolean = false): Pair<String, FileWritableNativeImpl>? {
        val path = registerSecurityScopedUrl(url) ?: return null
        return path to AppleFileWritableNativeImpl(path, appendMode = append, securityScopedUrl = url)
    }

    private fun resolveSecurityScopedUrl(path: String): NSURL? {
        return lock.withLock {
            securityScopedUrlsByPath[path] ?: securityScopedUrlsByPath.entries
                .firstOrNull { (dirPath, _) -> path == dirPath || path.startsWith("$dirPath/") }
                ?.value
        }
    }

    private inline fun <T> withSecurityScope(path: String, block: () -> T): T {
        val scopedUrl = resolveSecurityScopedUrl(path)
        val started = scopedUrl?.startAccessingSecurityScopedResource() == true
        return try {
            block()
        } finally {
            if (started) {
                scopedUrl.stopAccessingSecurityScopedResource()
            }
        }
    }

    private fun ensureParentDirExists(path: String) {
        val parentDir = NSString.create(string = path).stringByDeletingLastPathComponent
        if (parentDir.isNotEmpty() && !NSFileManager.defaultManager.fileExistsAtPath(parentDir)) {
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                NSFileManager.defaultManager.createDirectoryAtPath(
                    parentDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = err.ptr
                )
            }
        }
    }

    private suspend fun <T> runOnIoQueue(block: () -> T): T {
        return suspendCancellableCoroutine { continuation ->
            dispatch_async(ioQueue) {
                continuation.resume(block())
            }
        }
    }
}
