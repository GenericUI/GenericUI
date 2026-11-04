package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FileStat

/**
 * Cross-platform in-memory fallback implementation of [FileSystemNativeImpl] used when no native
 * backend factory is active or during headless unit tests.
 */
open class DefaultFileSystemNativeImpl : FileSystemNativeImpl {

    protected val files = LinkedHashMap<String, ByteArray>()
    protected val directories = LinkedHashSet<String>().apply {
        add("/")
    }
    protected val modifiedTimes = LinkedHashMap<String, Long>()

    override var isDisposed: Boolean = false
        protected set

    override fun resolve(vararg segments: String): String {
        val cleaned = mutableListOf<String>()
        var schemePrefix: String? = null
        var hasWindowsDrive = false
        for (raw in segments) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val schemeIdx = trimmed.indexOf("://")
            if (schemeIdx > 0) {
                schemePrefix = trimmed.substring(0, schemeIdx + 3)
                cleaned.clear()
                hasWindowsDrive = false
                val rest = trimmed.substring(schemeIdx + 3)
                for (part in rest.split('/', '\\')) {
                    if (part.isNotEmpty() && part != ".") {
                        if (part == "..") {
                            if (cleaned.isNotEmpty()) cleaned.removeAt(cleaned.lastIndex)
                        } else {
                            cleaned.add(part)
                        }
                    }
                }
                continue
            }
            val normalized = trimmed.replace('\\', '/')
            if (normalized.startsWith("/") && schemePrefix == null && !hasWindowsDrive) {
                cleaned.clear()
            } else if (normalized.length >= 2 && normalized[1] == ':') {
                schemePrefix = null
                hasWindowsDrive = true
                cleaned.clear()
            }
            for (part in normalized.split('/')) {
                if (part.isEmpty() || part == ".") continue
                if (part == "..") {
                    if (cleaned.size > (if (hasWindowsDrive) 1 else 0)) {
                        cleaned.removeAt(cleaned.lastIndex)
                    }
                } else {
                    cleaned.add(part)
                }
            }
        }
        return when {
            schemePrefix != null -> schemePrefix + cleaned.joinToString("/")
            hasWindowsDrive -> cleaned.joinToString("/")
            cleaned.isEmpty() -> "/"
            else -> "/" + cleaned.joinToString("/")
        }
    }

    override suspend fun read(path: String): ByteBuffer? =
        readFile(path)?.let { ByteBuffer.wrap(it) }

    override suspend fun write(path: String, buffer: ByteBuffer, append: Boolean): Boolean =
        if (append) appendFile(path, buffer.toByteArray()) else writeFile(path, buffer.toByteArray())

    override suspend fun createReadStream(path: String): FileReadableNativeImpl? {
        val bytes = readFile(path) ?: return null
        return DefaultFileReadableNativeImpl(bytes, filePath = normalizeKey(path))
    }

    override suspend fun createWriteStream(path: String, mode: FileOpenMode): FileWritableNativeImpl {
        val norm = normalizeKey(path)
        val append = mode == FileOpenMode.WRITE_APPEND
        val initial = if (append) files[norm] ?: ByteArray(0) else ByteArray(0)
        return DefaultFileWritableNativeImpl(
            initialBytes = initial,
            filePath = norm,
            appendMode = append
        ) { committed ->
            ensureParentDirs(norm)
            files[norm] = committed.copyOf()
            modifiedTimes[norm] = currentEpochMs()
        }
    }

    override suspend fun openReadableFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): Pair<String, FileReadableNativeImpl>? {
        val first = files.entries.firstOrNull() ?: return null
        return first.key to DefaultFileReadableNativeImpl(first.value.copyOf(), filePath = first.key)
    }

    override suspend fun openReadablesFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): List<Pair<String, FileReadableNativeImpl>> =
        openReadableFromFileDialog(title, initialDirectory, filters)?.let { listOf(it) } ?: emptyList()

    override suspend fun openWritableFromFileDialog(
        title: String,
        initialDirectory: String?,
        suggestedFileName: String,
        filters: List<FileTypeFilter>
    ): Pair<String, FileWritableNativeImpl>? {
        val target = resolve(initialDirectory ?: "/", suggestedFileName.ifEmpty { "untitled.bin" })
        return target to createWriteStream(target, FileOpenMode.WRITE_TRUNCATE)
    }

    override suspend fun readFile(path: String): ByteArray? =
        files[normalizeKey(path)]?.copyOf()

    override suspend fun writeFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val norm = normalizeKey(path)
        ensureParentDirs(norm)
        files[norm] = bytes.copyOf()
        modifiedTimes[norm] = currentEpochMs()
        return true
    }

    override suspend fun appendFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val norm = normalizeKey(path)
        ensureParentDirs(norm)
        val existing = files[norm]
        files[norm] = if (existing == null || existing.isEmpty()) {
            bytes.copyOf()
        } else {
            existing + bytes
        }
        modifiedTimes[norm] = currentEpochMs()
        return true
    }

    override suspend fun exists(path: String): Boolean {
        val norm = normalizeKey(path)
        return files.containsKey(norm) || directories.contains(norm)
    }

    override suspend fun stat(path: String): FileStat? {
        val norm = normalizeKey(path)
        files[norm]?.let { bytes ->
            return FileStat(
                path = norm,
                size = bytes.size.toLong(),
                isFile = true,
                isDirectory = false,
                lastModifiedEpochMs = modifiedTimes[norm] ?: 0L,
                createdEpochMs = modifiedTimes[norm] ?: 0L
            )
        }
        if (directories.contains(norm)) {
            return FileStat(
                path = norm,
                size = 0L,
                isFile = false,
                isDirectory = true,
                lastModifiedEpochMs = modifiedTimes[norm] ?: 0L,
                createdEpochMs = modifiedTimes[norm] ?: 0L
            )
        }
        return null
    }

    override suspend fun readdir(path: String): List<String> {
        val norm = normalizeKey(path).trimEnd('/')
        val prefix = if (norm.isEmpty() || norm == "/") "/" else "$norm/"
        val children = LinkedHashSet<String>()
        for (k in files.keys) {
            if (k.startsWith(prefix) && k.length > prefix.length) {
                val remainder = k.substring(prefix.length)
                val child = remainder.substringBefore('/')
                if (child.isNotEmpty()) children.add(child)
            }
        }
        for (d in directories) {
            if (d.startsWith(prefix) && d.length > prefix.length) {
                val remainder = d.substring(prefix.length)
                val child = remainder.substringBefore('/')
                if (child.isNotEmpty()) children.add(child)
            }
        }
        return children.toList()
    }

    override suspend fun mkdir(path: String, recursive: Boolean): Boolean {
        if (isDisposed) return false
        val norm = normalizeKey(path)
        if (recursive) {
            ensureParentDirs("$norm/placeholder")
        }
        directories.add(norm)
        modifiedTimes[norm] = currentEpochMs()
        return true
    }

    override suspend fun unlink(path: String): Boolean {
        val norm = normalizeKey(path)
        modifiedTimes.remove(norm)
        return files.remove(norm) != null
    }

    override suspend fun rmdir(path: String, recursive: Boolean): Boolean {
        val norm = normalizeKey(path).trimEnd('/').ifEmpty { "/" }
        if (norm == "/") return false
        val prefix = "$norm/"
        if (!recursive) {
            val hasChildren = files.keys.any { it.startsWith(prefix) } ||
                directories.any { it != norm && it.startsWith(prefix) }
            if (hasChildren) return false
            return directories.remove(norm)
        }
        val toRemoveFiles = files.keys.filter { it == norm || it.startsWith(prefix) }
        for (f in toRemoveFiles) {
            files.remove(f)
            modifiedTimes.remove(f)
        }
        val toRemoveDirs = directories.filter { it == norm || it.startsWith(prefix) }
        for (d in toRemoveDirs) {
            directories.remove(d)
            modifiedTimes.remove(d)
        }
        return toRemoveFiles.isNotEmpty() || toRemoveDirs.isNotEmpty()
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean {
        val oldNorm = normalizeKey(oldPath)
        val newNorm = normalizeKey(newPath)
        val bytes = files.remove(oldNorm) ?: return false
        ensureParentDirs(newNorm)
        files[newNorm] = bytes
        modifiedTimes[newNorm] = currentEpochMs()
        return true
    }

    override suspend fun copyFile(srcPath: String, dstPath: String, overwrite: Boolean): Boolean {
        val srcNorm = normalizeKey(srcPath)
        val dstNorm = normalizeKey(dstPath)
        val bytes = files[srcNorm] ?: return false
        if (!overwrite && files.containsKey(dstNorm)) return false
        ensureParentDirs(dstNorm)
        files[dstNorm] = bytes.copyOf()
        modifiedTimes[dstNorm] = currentEpochMs()
        return true
    }

    override fun dispose() {
        isDisposed = true
        files.clear()
        directories.clear()
        modifiedTimes.clear()
    }

    protected open fun normalizeKey(path: String): String = resolve(path)

    private fun ensureParentDirs(normalizedFilePath: String) {
        val schemeIdx = normalizedFilePath.indexOf("://")
        if (schemeIdx > 0) {
            val schemePrefix = normalizedFilePath.substring(0, schemeIdx + 3)
            val rest = normalizedFilePath.substring(schemeIdx + 3)
            val parts = rest.split('/').filter { it.isNotEmpty() }
            var acc = schemePrefix.trimEnd('/')
            for (i in 0 until parts.size - 1) {
                acc = if (acc.endsWith("://")) "$acc${parts[i]}" else "$acc/${parts[i]}"
                directories.add(acc)
            }
            return
        }
        val parts = normalizedFilePath.split('/').filter { it.isNotEmpty() }
        var acc = ""
        for (i in 0 until parts.size - 1) {
            acc = "$acc/${parts[i]}"
            directories.add(acc)
        }
    }

    private fun currentEpochMs(): Long = 1700000000000L + files.size.toLong()
}
