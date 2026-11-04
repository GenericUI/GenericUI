@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.nativeimpl.system

import kotlin.concurrent.atomics.AtomicInt

/**
 * Cross-platform in-memory fallback implementation of [UserDirectoryNativeImpl] used when no
 * platform backend [com.nexustools.genericui.core.NativeFactory] is active yet.
 */
class DefaultUserDirectoryNativeImpl(
    override val appName: String = "GenericUI",
    override val subdirectory: String = "",
    val scheme: String = "config"
) : UserDirectoryNativeImpl {

    override val fileSeparator: Char = '/'

    override val directoryPath: String = buildString {
        append(scheme.ifBlank { "config" })
        append("://")
        append(appName.trim().ifEmpty { "GenericUI" })
        val cleanSub = subdirectory.trim().trim('/', '\\')
        if (cleanSub.isNotEmpty()) {
            append('/')
            append(cleanSub.replace('\\', '/'))
        }
    }

    override var isDisposed: Boolean = false
        private set

    private val lockState = AtomicInt(0)
    private val entries = LinkedHashMap<String, ByteArray>()

    private inline fun <T> withLock(block: () -> T): T {
        while (!lockState.compareAndSet(0, 1)) {
            // Short spin for cross-platform non-blocking synchronization
        }
        try {
            return block()
        } finally {
            lockState.store(0)
        }
    }

    override fun ensureDirectoryExists(): Boolean = !isDisposed

    override fun resolveFilePath(fileName: String): String {
        val clean = sanitizeFileName(fileName)
        return "$directoryPath/$clean"
    }

    override fun writeBytes(fileName: String, bytes: ByteArray): Boolean = withLock {
        if (isDisposed) return@withLock false
        val clean = sanitizeFileName(fileName)
        entries[clean] = bytes.copyOf()
        true
    }

    override fun readBytes(fileName: String): ByteArray? = withLock {
        if (isDisposed) return@withLock null
        val clean = sanitizeFileName(fileName)
        entries[clean]?.copyOf()
    }

    override fun exists(fileName: String): Boolean = withLock {
        if (isDisposed) return@withLock false
        val clean = sanitizeFileName(fileName)
        entries.containsKey(clean)
    }

    override fun fileSize(fileName: String): Long = withLock {
        if (isDisposed) return@withLock 0L
        val clean = sanitizeFileName(fileName)
        entries[clean]?.size?.toLong() ?: 0L
    }

    override fun delete(fileName: String): Boolean = withLock {
        if (isDisposed) return@withLock false
        val clean = sanitizeFileName(fileName)
        entries.remove(clean) != null
    }

    override fun listFiles(): List<String> = withLock { entries.keys.toList() }

    override fun clearAll(): Int = withLock {
        val count = entries.size
        entries.clear()
        count
    }

    override fun totalByteSize(): Long = withLock {
        var total = 0L
        for (bytes in entries.values) {
            total += bytes.size.toLong()
        }
        total
    }

    override fun dispose() = withLock {
        isDisposed = true
        entries.clear()
    }

    private fun sanitizeFileName(raw: String): String {
        var stripped = raw.trim()
        val prefixSlash = "$directoryPath/"
        if (stripped.startsWith(prefixSlash)) {
            stripped = stripped.removePrefix(prefixSlash)
        } else if (stripped == directoryPath) {
            stripped = "unnamed"
        }
        val trimmed = stripped.trim('/', '\\').ifEmpty { "unnamed" }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
