package com.nexustools.genericui.nativeimpl.system

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

    private val entries = LinkedHashMap<String, ByteArray>()

    override fun ensureDirectoryExists(): Boolean = !isDisposed

    override fun resolveFilePath(fileName: String): String {
        val clean = sanitizeFileName(fileName)
        return "$directoryPath/$clean"
    }

    override fun writeBytes(fileName: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val clean = sanitizeFileName(fileName)
        entries[clean] = bytes.copyOf()
        return true
    }

    override fun readBytes(fileName: String): ByteArray? {
        if (isDisposed) return null
        val clean = sanitizeFileName(fileName)
        return entries[clean]?.copyOf()
    }

    override fun exists(fileName: String): Boolean {
        if (isDisposed) return false
        val clean = sanitizeFileName(fileName)
        return entries.containsKey(clean)
    }

    override fun fileSize(fileName: String): Long {
        if (isDisposed) return 0L
        val clean = sanitizeFileName(fileName)
        return entries[clean]?.size?.toLong() ?: 0L
    }

    override fun delete(fileName: String): Boolean {
        if (isDisposed) return false
        val clean = sanitizeFileName(fileName)
        return entries.remove(clean) != null
    }

    override fun listFiles(): List<String> = entries.keys.toList()

    override fun clearAll(): Int {
        val count = entries.size
        entries.clear()
        return count
    }

    override fun totalByteSize(): Long {
        var total = 0L
        for (bytes in entries.values) {
            total += bytes.size.toLong()
        }
        return total
    }

    override fun dispose() {
        isDisposed = true
        entries.clear()
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.trim().trim('/', '\\').ifEmpty { "unnamed" }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
