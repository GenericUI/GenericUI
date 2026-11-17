package com.nexustools.genericui.jvm

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.io.FileStat
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.nativeimpl.io.DefaultFileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl
import java.io.File
import java.io.FileOutputStream

/**
 * Shared JVM and Android (`minSdk >= 24`, API-1 `java.io.File`) implementation of
 * [com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl] with [JvmStreamFileReadableNativeImpl],
 * [JvmStreamFileWritableNativeImpl], automatic in-memory fallback ([DefaultFileSystemNativeImpl]) for
 * virtual test paths, and extension hooks for Android `content://`/`asset://` URIs and desktop
 * filesystem watchers.
 */
open class JvmFileSystemBase(
    protected val isOnMainThread: () -> Boolean = { false }
) : DefaultFileSystemNativeImpl() {

    override fun isAvailable(): FileSystemAvailability = FileSystemAvailability.Always

    override fun hasPermission(path: String?, write: Boolean): FilePermissionStatus {
        if (path != null) {
            hasUriPermission(path.trim(), write)?.let { return it }
        }
        return FilePermissionStatus.GRANTED
    }

    protected open fun hasUriPermission(path: String, write: Boolean): FilePermissionStatus? = null

    protected open suspend fun openUriReadStream(path: String): JvmStreamFileReadableNativeImpl? = null

    protected open suspend fun openUriWriteStream(path: String, append: Boolean): JvmStreamFileWritableNativeImpl? = null

    protected open fun readUriBytes(path: String): ByteArray? = null

    protected open fun writeUriBytes(path: String, bytes: ByteArray, append: Boolean): Boolean? = null

    protected open fun existsUri(path: String): Boolean? = null

    protected open fun statUri(path: String): FileStat? = null

    protected open fun unlinkUri(path: String): Boolean? = null

    protected open fun onDiskFileMutated(file: File, eventKind: String) {}

    protected open fun resolveNativeStat(file: File): FileStat = FileStat(
        path = file.absolutePath,
        name = file.name.ifEmpty { file.path },
        size = if (file.isFile) file.length() else 0L,
        isFile = file.isFile,
        isDirectory = file.isDirectory,
        isSymbolicLink = false,
        lastModifiedEpochMs = file.lastModified(),
        createdEpochMs = file.lastModified(),
        isReadable = file.canRead(),
        isWritable = file.canWrite()
    )

    protected open fun createDiskReadStream(file: File): JvmStreamFileReadableNativeImpl =
        JvmStreamFileReadableNativeImpl(file = file, isOnMainThread = isOnMainThread)

    protected open fun createDiskWriteStream(file: File, append: Boolean): JvmStreamFileWritableNativeImpl =
        JvmStreamFileWritableNativeImpl(file = file, append = append, isOnMainThread = isOnMainThread)

    override suspend fun openReadable(path: String): JvmStreamFileReadableNativeImpl? =
        createReadStream(path)

    override suspend fun openWritable(path: String, append: Boolean): JvmStreamFileWritableNativeImpl =
        createWriteStream(path, if (append) FileOpenMode.WRITE_APPEND else FileOpenMode.WRITE_TRUNCATE)

    override suspend fun createReadStream(path: String): JvmStreamFileReadableNativeImpl? {
        if (isDisposed) return null
        val trimmed = path.trim()
        openUriReadStream(trimmed)?.let { return it }
        val localFile = resolveLocalFile(trimmed, mustExist = true)
        if (localFile != null) {
            return createDiskReadStream(localFile)
        }
        val fallbackBytes = super.readFile(path) ?: return null
        return JvmStreamFileReadableNativeImpl(initialBytes = fallbackBytes, filePath = normalizeKey(path))
    }

    override suspend fun createWriteStream(path: String, mode: FileOpenMode): JvmStreamFileWritableNativeImpl {
        val append = mode == FileOpenMode.WRITE_APPEND
        val trimmed = path.trim()
        openUriWriteStream(trimmed, append)?.let { return it }
        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            return createDiskWriteStream(localFile, append)
        }
        val norm = normalizeKey(path)
        val initial = if (append) files[norm] ?: ByteArray(0) else ByteArray(0)
        return JvmStreamFileWritableNativeImpl(
            initialBytes = initial,
            filePath = norm,
            appendMode = append,
            onCommit = { committed ->
                super.writeFile(norm, committed)
            }
        )
    }

    override suspend fun openReadableFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): Pair<String, FileReadableNativeImpl>? {
        val fallback = super.openReadableFromFileDialog(title, initialDirectory, filters) ?: return null
        val bytes = super.readFile(fallback.first) ?: return fallback
        return fallback.first to JvmStreamFileReadableNativeImpl(initialBytes = bytes, filePath = fallback.first)
    }

    override suspend fun openWritableFromFileDialog(
        title: String,
        initialDirectory: String?,
        suggestedFileName: String,
        filters: List<FileTypeFilter>
    ): Pair<String, FileWritableNativeImpl>? {
        val target = resolve(initialDirectory ?: "/", suggestedFileName.ifEmpty { "untitled.bin" })
        return target to createWriteStream(target, FileOpenMode.WRITE_TRUNCATE)
    }

    override suspend fun readFile(path: String): ByteArray? {
        if (isDisposed) return null
        val norm = normalizeKey(path)
        if (files.containsKey(norm)) {
            return super.readFile(path)
        }
        val trimmed = path.trim()
        val uriBytes = JvmIoExecutor.runOnIo(isOnMainThread) { readUriBytes(trimmed) }
        if (uriBytes != null) return uriBytes

        val localFile = resolveLocalFile(trimmed, mustExist = true)
        if (localFile != null) {
            return JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    if (localFile.isFile) localFile.readBytes() else null
                }.getOrNull()
            }
        }
        return super.readFile(path)
    }

    override suspend fun writeFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val trimmed = path.trim()
        val uriResult = JvmIoExecutor.runOnIo(isOnMainThread) { writeUriBytes(trimmed, bytes, append = false) }
        if (uriResult == true) return true

        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            val written = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    localFile.parentFile?.mkdirs()
                    localFile.writeBytes(bytes)
                    true
                }.getOrDefault(false)
            }
            if (written) {
                onDiskFileMutated(localFile, "write")
                return true
            }
        }
        return super.writeFile(path, bytes)
    }

    override suspend fun appendFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val trimmed = path.trim()
        val uriResult = JvmIoExecutor.runOnIo(isOnMainThread) { writeUriBytes(trimmed, bytes, append = true) }
        if (uriResult == true) return true

        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            val appended = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    localFile.parentFile?.mkdirs()
                    FileOutputStream(localFile, true).use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    true
                }.getOrDefault(false)
            }
            if (appended) {
                onDiskFileMutated(localFile, "append")
                return true
            }
        }
        return super.appendFile(path, bytes)
    }

    override suspend fun exists(path: String): Boolean {
        if (isDisposed) return false
        if (super.exists(path)) return true
        val trimmed = path.trim()
        val uriExists = JvmIoExecutor.runOnIo(isOnMainThread) { existsUri(trimmed) }
        if (uriExists != null) return uriExists
        val localFile = resolveLocalFile(trimmed, mustExist = true)
        return localFile != null
    }

    override suspend fun stat(path: String): FileStat? {
        if (isDisposed) return null
        super.stat(path)?.let { return it }
        val trimmed = path.trim()
        val uriStat = JvmIoExecutor.runOnIo(isOnMainThread) { statUri(trimmed) }
        if (uriStat != null) return uriStat
        val localFile = resolveLocalFile(trimmed, mustExist = true) ?: return null
        return JvmIoExecutor.runOnIo(isOnMainThread) {
            runCatching { resolveNativeStat(localFile) }.getOrNull()
        }
    }

    override suspend fun readdir(path: String): List<String> {
        if (isDisposed) return emptyList()
        val combined = LinkedHashSet<String>()
        combined.addAll(super.readdir(path))
        val localFile = resolveLocalFile(path.trim(), mustExist = true)
        if (localFile != null) {
            val diskEntries = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    if (localFile.isDirectory) localFile.list()?.sorted() ?: emptyList() else emptyList()
                }.getOrDefault(emptyList())
            }
            combined.addAll(diskEntries)
        }
        return combined.toList()
    }

    override suspend fun mkdir(path: String, recursive: Boolean): Boolean {
        if (isDisposed) return false
        val trimmed = path.trim()
        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            val created = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    if (recursive) localFile.mkdirs() || localFile.isDirectory else localFile.mkdir() || localFile.isDirectory
                }.getOrDefault(false)
            }
            if (created) {
                onDiskFileMutated(localFile, "mkdir")
                return true
            }
        }
        return super.mkdir(path, recursive)
    }

    override suspend fun unlink(path: String): Boolean {
        if (isDisposed) return false
        val removedMem = super.unlink(path)
        val trimmed = path.trim()
        val uriDeleted = JvmIoExecutor.runOnIo(isOnMainThread) { unlinkUri(trimmed) }
        if (uriDeleted != null) return removedMem || uriDeleted

        val localFile = resolveLocalFile(trimmed, mustExist = true)
        if (localFile != null) {
            val deletedDisk = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching { localFile.isFile && localFile.delete() }.getOrDefault(false)
            }
            if (deletedDisk) {
                onDiskFileMutated(localFile, "unlink")
            }
            return removedMem || deletedDisk
        }
        return removedMem
    }

    override suspend fun rmdir(path: String, recursive: Boolean): Boolean {
        if (isDisposed) return false
        val removedMem = super.rmdir(path, recursive)
        val localFile = resolveLocalFile(path.trim(), mustExist = true)
        if (localFile != null) {
            val deletedDisk = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    if (!localFile.isDirectory) return@runCatching false
                    if (recursive) localFile.deleteRecursively() else localFile.delete()
                }.getOrDefault(false)
            }
            if (deletedDisk) {
                onDiskFileMutated(localFile, "rmdir")
            }
            return removedMem || deletedDisk
        }
        return removedMem
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean {
        if (isDisposed) return false
        val oldFile = resolveLocalFile(oldPath.trim(), mustExist = true)
        val newFile = resolveLocalFile(newPath.trim(), mustExist = false)
        if (oldFile != null && newFile != null && !isVirtualPath(newPath)) {
            val moved = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    newFile.parentFile?.mkdirs()
                    if (oldFile.renameTo(newFile)) {
                        true
                    } else if (oldFile.isFile) {
                        oldFile.copyTo(newFile, overwrite = true)
                        oldFile.delete()
                    } else {
                        false
                    }
                }.getOrDefault(false)
            }
            if (moved) {
                onDiskFileMutated(newFile, "rename")
                return true
            }
        }
        return super.rename(oldPath, newPath)
    }

    override suspend fun copyFile(srcPath: String, dstPath: String, overwrite: Boolean): Boolean {
        if (isDisposed) return false
        val srcFile = resolveLocalFile(srcPath.trim(), mustExist = true)
        val dstFile = resolveLocalFile(dstPath.trim(), mustExist = false)
        if (srcFile != null && dstFile != null && !isVirtualPath(dstPath)) {
            val copied = JvmIoExecutor.runOnIo(isOnMainThread) {
                runCatching {
                    if (!overwrite && dstFile.exists()) return@runCatching false
                    dstFile.parentFile?.mkdirs()
                    srcFile.copyTo(dstFile, overwrite = overwrite)
                    true
                }.getOrDefault(false)
            }
            if (copied) {
                onDiskFileMutated(dstFile, "copy")
                return true
            }
        }
        return super.copyFile(srcPath, dstPath, overwrite)
    }

    protected open fun isVirtualPath(rawPath: String): Boolean {
        val trimmed = rawPath.trim()
        if (trimmed.contains("://") && !trimmed.startsWith("file:", ignoreCase = true)) return true
        val norm = normalizeKey(trimmed)
        if (files.containsKey(norm) || (norm != "/" && directories.contains(norm))) {
            return true
        }
        if (File.separatorChar == '\\') {
            if (trimmed.startsWith("/") && !(trimmed.length >= 3 && trimmed[2] == ':')) {
                val candidate = File(trimmed)
                return !candidate.exists()
            }
        } else if (trimmed.startsWith("/projects/") || trimmed == "/projects") {
            val candidate = File(trimmed)
            return !candidate.exists()
        }
        return false
    }

    protected open fun resolveLocalFile(rawPath: String, mustExist: Boolean): File? {
        if (rawPath.isEmpty()) return null
        if (rawPath.contains("://") && !rawPath.startsWith("file:", ignoreCase = true)) return null
        val file = JvmImageSourceResolver.resolveFileUri(rawPath)
            ?: runCatching { File(rawPath) }.getOrNull()
            ?: return null
        return if (mustExist) {
            if (file.exists()) file else null
        } else {
            file
        }
    }
}
