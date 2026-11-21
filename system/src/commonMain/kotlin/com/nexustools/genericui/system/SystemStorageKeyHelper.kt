package com.nexustools.genericui.system

import com.nexustools.genericui.util.UserDirectory

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
     * Sanitizes a storage or cache key into a single-segment file name by replacing invalid
     * filesystem characters with `_`, defaulting to [fallback] when blank.
     */
    fun sanitizeKey(key: String, fallback: String = "default"): String {
        val trimmed = key.trim().ifEmpty { fallback }
        return trimmed.replace(INVALID_FILENAME_CHARS_REGEX, "_")
    }

    /**
     * Bijectively encodes a preference [key] into a valid, non-dotfile filename stem.
     */
    fun encodePreferenceKeyToFileName(key: String): String =
        UserDirectory.encodePreferenceKeyToFileName(key)

    /**
     * Losslessly decodes an encoded preference filename stem back into the original preference key.
     */
    fun decodePreferenceKeyFromFileName(encodedStem: String): String =
        UserDirectory.decodePreferenceKeyFromFileName(encodedStem)

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
