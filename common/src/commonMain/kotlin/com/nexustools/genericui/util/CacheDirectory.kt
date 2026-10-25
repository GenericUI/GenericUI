package com.nexustools.genericui.util

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl

/**
 * Cross-platform user cache directory (`<userCacheDir>/<appName>[/<subdirectory>]`) extending [ConfigDirectory]
 * (which in turn extends [UserDirectory]).
 *
 * Used by [com.nexustools.genericui.image.cache.ImageCacheManager] and [com.nexustools.genericui.image.ImageLoader]
 * to cache downloaded URL images and decoded assets on disk in the OS cache location:
 * - **Windows (`:desktop:windows`)**: `%LOCALAPPDATA%\<appName>\Cache`
 * - **macOS (`:desktop:macos`)**: `~/Library/Caches/<appName>`
 * - **Linux (`:desktop:gtk`, `:desktop:qt`)**: `$XDG_CACHE_HOME/<appName>` (or `~/.cache/<appName>`)
 * - **Swing (`:desktop:swing`)**: Host OS user cache directory (`%LOCALAPPDATA%\<appName>\Cache`, `~/Library/Caches/<appName>`, or `~/.cache/<appName>`)
 * - **Android (`:mobile:android`)**: `<context.cacheDir>/<appName>`
 * - **iOS (`:mobile:ios`)**: `<Home>/Library/Caches/<appName>`
 * - **Web (`:web`)**: `window.localStorage` namespace `genericui:cache:<appName>`
 */
class CacheDirectory(
    appName: String = GenericUI.applicationName,
    subdirectory: String = "",
    factory: NativeFactory? = null,
    nativeImplOverride: UserDirectoryNativeImpl? = null
) : ConfigDirectory(appName, subdirectory, factory, nativeImplOverride) {

    override val fallbackScheme: String = "cache"

    override fun createBackendImpl(activeFactory: NativeFactory): UserDirectoryNativeImpl =
        activeFactory.createCacheDirectory(appName, subdirectory)

    /**
     * Computes a deterministic, collision-resistant cache filename for [urlOrKey].
     */
    fun cacheFileNameForKey(urlOrKey: String, defaultExtension: String = ".bin"): String {
        val cleanUrl = urlOrKey.trim()
        val pathPart = cleanUrl.substringBefore('?').substringBefore('#')
        val rawLeaf = pathPart.substringAfterLast('/').substringAfterLast('\\')
        val stemCandidate = rawLeaf.substringBeforeLast('.', rawLeaf)
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_')
            .take(32)
            .ifEmpty { "asset" }

        val extFromUrl = detectExtensionFromUrl(pathPart)
        val ext = when {
            extFromUrl != null -> extFromUrl
            defaultExtension.isBlank() -> ".bin"
            defaultExtension.startsWith(".") -> defaultExtension.lowercase()
            else -> ".${defaultExtension.lowercase()}"
        }
        val hashHex = fnv1a64Hex(cleanUrl)
        return "${stemCandidate}_$hashHex$ext"
    }

    /**
     * Writes [bytes] into the cache directory for [urlOrKey], automatically detecting the image extension
     * from the magic header when [defaultExtension] is `".bin"`, and returns the resolved file path.
     */
    fun putCachedBytes(urlOrKey: String, bytes: ByteArray, defaultExtension: String = ".bin"): String {
        val detectedExt = detectExtensionFromBytes(bytes) ?: defaultExtension
        val fileName = cacheFileNameForKey(urlOrKey, detectedExt)
        ensureExists()
        writeBytes(fileName, bytes)
        return resolvePath(fileName)
    }

    /**
     * Reads cached bytes for [urlOrKey], checking both the exact [defaultExtension] and common image extensions.
     */
    fun getCachedBytes(urlOrKey: String, defaultExtension: String = ".bin"): ByteArray? {
        for (candidate in candidateFileNames(urlOrKey, defaultExtension)) {
            val bytes = readBytes(candidate)
            if (bytes != null) return bytes
        }
        return null
    }

    /**
     * Returns `true` if [urlOrKey] is present in this cache directory.
     */
    fun hasCachedEntry(urlOrKey: String, defaultExtension: String = ".bin"): Boolean {
        for (candidate in candidateFileNames(urlOrKey, defaultExtension)) {
            if (exists(candidate)) return true
        }
        return false
    }

    /**
     * Evicts any cached file(s) matching [urlOrKey] from this cache directory.
     */
    fun evictCachedEntry(urlOrKey: String, defaultExtension: String = ".bin"): Boolean {
        var removedAny = false
        for (candidate in candidateFileNames(urlOrKey, defaultExtension)) {
            if (delete(candidate)) {
                removedAny = true
            }
        }
        return removedAny
    }

    /**
     * Clears all files in this cache directory and returns the number of deleted files.
     */
    fun clearCache(): Int = clear()

    private fun candidateFileNames(urlOrKey: String, defaultExtension: String): List<String> {
        val primary = cacheFileNameForKey(urlOrKey, defaultExtension)
        val out = ArrayList<String>(8)
        out.add(primary)
        for (ext in KNOWN_CACHE_EXTENSIONS) {
            val name = cacheFileNameForKey(urlOrKey, ext)
            if (name !in out) {
                out.add(name)
            }
        }
        return out
    }

    private fun detectExtensionFromUrl(pathPart: String): String? {
        val dotIdx = pathPart.lastIndexOf('.')
        if (dotIdx < 0 || dotIdx == pathPart.length - 1) return null
        val ext = pathPart.substring(dotIdx + 1).lowercase()
        return when (ext) {
            "png", "apng" -> ".png"
            "jpg", "jpeg", "jpe", "jfif" -> ".jpg"
            "webp" -> ".webp"
            "gif" -> ".gif"
            "bmp" -> ".bmp"
            "ico" -> ".ico"
            "tif", "tiff" -> ".tiff"
            "svg" -> ".svg"
            "ppm", "pgm", "pbm" -> ".ppm"
            else -> null
        }
    }

    private fun detectExtensionFromBytes(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return when (ImageFormat.detectFromHeader(bytes)) {
            ImageFormat.PNG -> ".png"
            ImageFormat.JPEG -> ".jpg"
            ImageFormat.WEBP -> ".webp"
            ImageFormat.GIF -> ".gif"
            ImageFormat.BMP -> ".bmp"
            ImageFormat.ICO -> ".ico"
            ImageFormat.TIFF -> ".tiff"
            ImageFormat.SVG -> ".svg"
            ImageFormat.PPM -> ".ppm"
            else -> null
        }
    }

    private fun fnv1a64Hex(input: String): String {
        var hash = -0x340d631b7bdddcdbL
        val prime = 0x100000001b3L
        for (i in input.indices) {
            hash = hash xor (input[i].code.toLong() and 0xFFFFL)
            hash *= prime
        }
        val hexChars = "0123456789abcdef"
        val chars = CharArray(16)
        var v = hash
        for (i in 15 downTo 0) {
            chars[i] = hexChars[(v and 0xFL).toInt()]
            v = v ushr 4
        }
        return chars.concatToString()
    }

    companion object {
        private val KNOWN_CACHE_EXTENSIONS = listOf(".png", ".jpg", ".webp", ".gif", ".bmp", ".svg", ".ico", ".ppm", ".bin")
    }
}
