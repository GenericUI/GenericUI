package com.nexustools.genericui.system

/**
 * Pure-Kotlin storage key and path sanitization helper shared across all non-Web system platforms
 * (`:desktop:common`, `:desktop:nativeCommon`, `:desktop:swing`, `:mobile:common`, `:mobile:android`, `:mobile:ios`).
 */
object SystemStorageKeyHelper {

    private val INVALID_FILENAME_CHARS_REGEX = Regex("[\\\\/:*?\"<>|]")

    /**
     * Sanitizes an application directory name by replacing invalid filesystem characters with `_`,
     * defaulting to `"GenericUI"` when blank.
     */
    fun sanitizeAppDirectoryName(appName: String): String {
        val trimmed = appName.trim().ifEmpty { "GenericUI" }
        return trimmed.replace(INVALID_FILENAME_CHARS_REGEX, "_")
    }

    /**
     * Sanitizes a storage or preference key into a safe single-segment file name by replacing
     * invalid filesystem characters with `_`, defaulting to [fallback] when blank.
     */
    fun sanitizeKey(key: String, fallback: String = "default"): String {
        val trimmed = key.trim().ifEmpty { fallback }
        return trimmed.replace(INVALID_FILENAME_CHARS_REGEX, "_")
    }

    /**
     * Encodes a set of preference/storage keys into a newline-delimited UTF-8 [ByteArray].
     */
    fun encodeKeySet(keys: Set<String>): ByteArray =
        keys.joinToString("\n").encodeToByteArray()

    /**
     * Decodes a newline-delimited UTF-8 [ByteArray] into an ordered [LinkedHashSet] of non-empty keys.
     */
    fun decodeKeySet(bytes: ByteArray): LinkedHashSet<String> =
        decodeKeySet(bytes.decodeToString())

    /**
     * Decodes a newline-delimited string into an ordered [LinkedHashSet] of non-empty keys.
     */
    fun decodeKeySet(text: String): LinkedHashSet<String> {
        val set = LinkedHashSet<String>()
        for (line in text.lineSequence()) {
            val k = line.trim()
            if (k.isNotEmpty()) {
                set.add(k)
            }
        }
        return set
    }

    /**
     * Normalizes all path separators (`\` and `/`) in [path] to [separator].
     */
    fun normalizePath(path: String, separator: Char = '/'): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replace('\\', separator).replace('/', separator)
    }

    /**
     * Validates and sanitizes a relative path, stripping leading/trailing slashes and rejecting
     * any `..` parent-directory traversal segments.
     */
    fun validateRelativePath(relativePath: String, separator: Char = '/'): String {
        val clean = relativePath.trim().trim('\\', '/')
        if (clean.isEmpty()) return ""
        val segments = clean.split('\\', '/').filter { it.isNotEmpty() && it != "." }
        require(segments.none { it == ".." }) {
            "Relative path must not contain parent traversal ('..'): $relativePath"
        }
        return segments.joinToString(separator.toString())
    }

    /**
     * Joins [baseDir] and [childPath] using [separator], normalizing internal slashes.
     */
    fun joinPath(baseDir: String, childPath: String, separator: Char = '/'): String {
        val cleanBase = baseDir.trimEnd('\\', '/')
        val cleanSub = childPath.trim().trim('\\', '/')
        if (cleanSub.isEmpty()) return cleanBase
        val normalizedSub = normalizePath(cleanSub, separator)
        if (cleanBase.isEmpty()) return normalizedSub
        return "$cleanBase$separator$normalizedSub"
    }
}
