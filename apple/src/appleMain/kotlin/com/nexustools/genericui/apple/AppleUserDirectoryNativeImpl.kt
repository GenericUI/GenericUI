@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl
import com.nexustools.genericui.util.UserDirectory
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.writeToFile
import platform.posix.getenv

class AppleUserDirectoryNativeImpl(
    override val appName: String = "GenericUI",
    override val subdirectory: String = "cache",
    val useCachesDirectory: Boolean = false,
    val useXdgStyleConfigDirectory: Boolean = false,
    private val customBaseDirectoryPath: String? = null
) : UserDirectoryNativeImpl {
    private val lock = AppleReentrantLock()
    private var disposedState = false

    override val fileSeparator: Char = '/'
    override val usesFileSystemBasePath: Boolean = true

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    override val directoryPath: String by lazy {
        computeDirectoryPath()
    }

    override fun ensureDirectoryExists(): Boolean {
        if (isDisposed) return false
        return lock.withLock {
            val fm = NSFileManager.defaultManager
            if (fm.fileExistsAtPath(directoryPath)) return@withLock true
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                fm.createDirectoryAtPath(
                    directoryPath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = err.ptr
                )
            }
        }
    }

    override fun resolveFilePath(fileName: String): String {
        val safeName = UserDirectory.sanitizeFileName(fileName)
        return if (directoryPath.endsWith('/')) "$directoryPath$safeName" else "$directoryPath/$safeName"
    }

    override fun writeBytes(fileName: String, bytes: ByteArray): Boolean {
        if (isDisposed || !ensureDirectoryExists()) return false
        val path = resolveFilePath(fileName)
        return lock.withLock {
            val parent = NSString.create(string = path).stringByDeletingLastPathComponent
            if (parent.isNotEmpty() && !NSFileManager.defaultManager.fileExistsAtPath(parent)) {
                memScoped {
                    val err = alloc<ObjCObjectVar<NSError?>>()
                    NSFileManager.defaultManager.createDirectoryAtPath(
                        parent,
                        withIntermediateDirectories = true,
                        attributes = null,
                        error = err.ptr
                    )
                }
            }
            AppleDataBridge.byteArrayToNSData(bytes).writeToFile(path, atomically = true)
        }
    }

    override fun readBytes(fileName: String): ByteArray? {
        if (isDisposed) return null
        val path = resolveFilePath(fileName)
        return lock.withLock {
            val data = NSData.dataWithContentsOfFile(path) ?: return@withLock null
            AppleDataBridge.nsDataToByteArray(data)
        }
    }

    override fun exists(fileName: String): Boolean {
        if (isDisposed) return false
        val path = resolveFilePath(fileName)
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    override fun fileSize(fileName: String): Long {
        if (isDisposed) return -1L
        val path = resolveFilePath(fileName)
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) return -1L
        val attrs = memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            fm.attributesOfItemAtPath(path, error = err.ptr)
        } ?: return -1L
        return (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: -1L
    }

    override fun delete(fileName: String): Boolean {
        if (isDisposed) return false
        val path = resolveFilePath(fileName)
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) return false
        return lock.withLock {
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                fm.removeItemAtPath(path, error = err.ptr)
            }
        }
    }

    override fun listFiles(): List<String> {
        if (isDisposed) return emptyList()
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(directoryPath)) return emptyList()
        val entries = memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            fm.contentsOfDirectoryAtPath(directoryPath, error = err.ptr)
        } ?: return emptyList()
        return entries.mapNotNull { it?.toString() }.sorted()
    }

    override fun clearAll(): Int {
        if (isDisposed) return 0
        val files = listFiles()
        var removed = 0
        for (file in files) {
            if (delete(file)) removed++
        }
        return removed
    }

    override fun totalByteSize(): Long {
        if (isDisposed) return 0L
        var total = 0L
        for (file in listFiles()) {
            val sz = fileSize(file)
            if (sz > 0L) total += sz
        }
        return total
    }

    override suspend fun readAsync(fileName: String): ByteBuffer? {
        val bytes = readBytes(fileName) ?: return null
        return ByteBuffer.wrap(bytes)
    }

    override suspend fun writeAsync(fileName: String, buffer: ByteBuffer, append: Boolean): Boolean {
        if (isDisposed || !ensureDirectoryExists()) return false
        if (!append) {
            return writeBytes(fileName, buffer.remainingByteArray())
        }
        val writable = openWritableAsync(fileName, append = true)
        return try {
            writable.write(buffer)
            writable.end(null)
            true
        } finally {
            writable.dispose()
        }
    }

    override suspend fun openReadableAsync(fileName: String): FileReadableNativeImpl? {
        if (isDisposed || !exists(fileName)) return null
        return AppleFileReadableNativeImpl(resolveFilePath(fileName))
    }

    override suspend fun openWritableAsync(fileName: String, append: Boolean): FileWritableNativeImpl {
        ensureDirectoryExists()
        return AppleFileWritableNativeImpl(resolveFilePath(fileName), appendMode = append)
    }

    override fun dispose() {
        lock.withLock {
            disposedState = true
        }
    }

    private fun computeDirectoryPath(): String {
        val safeApp = UserDirectory.sanitizeFileName(appName).ifBlank { "GenericUI" }
        val safeSub = if (subdirectory.isBlank()) "" else UserDirectory.sanitizeFileName(subdirectory)

        val base = when {
            !customBaseDirectoryPath.isNullOrBlank() -> customBaseDirectoryPath.trimEnd('/')
            useXdgStyleConfigDirectory -> {
                val xdg = getenv("XDG_CONFIG_HOME")?.toKString()?.takeIf { it.isNotBlank() }
                if (xdg != null) {
                    xdg.trimEnd('/')
                } else {
                    val home = getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() } ?: "/tmp"
                    "${home.trimEnd('/')}/.config"
                }
            }
            else -> {
                val searchDir = if (useCachesDirectory) NSCachesDirectory else NSApplicationSupportDirectory
                val urls = NSFileManager.defaultManager.URLsForDirectory(searchDir, inDomains = NSUserDomainMask)
                val firstUrl = urls.firstOrNull() as? NSURL
                firstUrl?.path?.trimEnd('/') ?: "/tmp"
            }
        }

        val appRoot = "$base/$safeApp"
        return if (safeSub.isEmpty()) appRoot else "$appRoot/$safeSub"
    }
}
