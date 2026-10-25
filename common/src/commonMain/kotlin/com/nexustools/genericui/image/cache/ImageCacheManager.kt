package com.nexustools.genericui.image.cache

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.util.CacheDirectory

/**
 * Cross-platform URL and resource image cache manager backed by the OS [CacheDirectory]
 * (`<userCacheDir>/<appName>/images`) and an in-memory decoded [ImageBuffer] LRU cache.
 *
 * Automatically caches remote (`http://`, `https://`), `data:image/...;base64,...`, and resolved
 * resource images on disk in the user's OS cache directory and provides [clearCache], [clearDiskCache],
 * [clearMemoryCache], and [evict] so applications can expose a "Clear Cache" user action.
 */
object ImageCacheManager {

    const val IMAGE_CACHE_SUBDIRECTORY: String = "images"

    var maxMemoryEntries: Int = 128

    /**
     * Optional platform or application hook for downloading `http://` and `https://` image URLs.
     */
    var urlFetcherHook: ((url: String) -> ByteArray?)? = null

    /**
     * Optional platform hook for reading local file paths or relative resource files (e.g. `src/commonMain/resources/<name>`).
     */
    var localResourceReaderHook: ((uriOrPath: String) -> ByteArray?)? = null

    private var activeAppName: String = GenericUI.applicationName
    private var backingCacheDirectory: CacheDirectory =
        CacheDirectory(appName = activeAppName, subdirectory = IMAGE_CACHE_SUBDIRECTORY)

    private val memoryBufferCache = LinkedHashMap<String, ImageBuffer>()
    private val cacheEntries = LinkedHashMap<String, ImageCacheEntry>()

    /**
     * The OS [CacheDirectory] (`<userCacheDir>/<appName>/images`) where fetched images are stored.
     */
    val cacheDirectory: CacheDirectory
        get() {
            val currentApp = GenericUI.applicationName
            if (currentApp != activeAppName) {
                activeAppName = currentApp
                backingCacheDirectory = CacheDirectory(
                    appName = currentApp,
                    subdirectory = IMAGE_CACHE_SUBDIRECTORY
                )
            }
            return backingCacheDirectory
        }

    /**
     * The resolved OS path of the image cache directory.
     */
    val cacheDirectoryPath: String
        get() = cacheDirectory.directoryPath

    /**
     * Total number of cached image files currently stored in the OS [CacheDirectory].
     */
    val cachedFileCount: Int
        get() = cacheDirectory.fileCount

    /**
     * Total byte size of all cached image files currently stored in the OS [CacheDirectory].
     */
    val cachedDiskByteSize: Long
        get() = cacheDirectory.totalByteSize()

    /**
     * Number of decoded [ImageBuffer] instances currently held in memory.
     */
    val cachedMemoryEntryCount: Int
        get() = memoryBufferCache.size

    /**
     * Returns `true` if [url] is currently cached either in memory or in the OS [CacheDirectory].
     */
    fun isCached(url: String): Boolean {
        val key = url.trim()
        if (key.isEmpty()) return false
        return memoryBufferCache.containsKey(key) || cacheDirectory.hasCachedEntry(key)
    }

    /**
     * Returns the [ImageCacheEntry] metadata for [url] if cached, or `null` otherwise.
     */
    fun getCacheEntry(url: String): ImageCacheEntry? = cacheEntries[url.trim()]

    /**
     * Stores [encodedBytes] (and optional [decodedBuffer]) in the OS [CacheDirectory] and memory cache under [url].
     */
    fun putInCache(
        url: String,
        encodedBytes: ByteArray,
        decodedBuffer: ImageBuffer? = null
    ): ImageCacheEntry {
        val cleanUrl = url.trim()
        val format = ImageFormat.detectFromHeader(encodedBytes)
        val ext = if (format != ImageFormat.UNKNOWN) ".${format.defaultExtension}" else ".bin"
        val fileName = cacheDirectory.cacheFileNameForKey(cleanUrl, ext)
        val savedPath = cacheDirectory.putCachedBytes(cleanUrl, encodedBytes, ext)

        val buffer = decodedBuffer ?: ImageLoader.loadFromBytes(encodedBytes)
        putMemoryBuffer(cleanUrl, buffer)

        val entry = ImageCacheEntry(
            url = cleanUrl,
            cacheFileName = fileName,
            cachedFilePath = savedPath,
            byteSize = encodedBytes.size,
            width = buffer.width,
            height = buffer.height,
            format = format
        )
        cacheEntries[cleanUrl] = entry
        return entry
    }

    /**
     * Resolves or downloads the encoded image bytes for [url], reading from the OS [CacheDirectory]
     * when already cached and persisting newly fetched bytes into the OS [CacheDirectory].
     */
    fun fetchAndCacheBytes(url: String): ByteArray? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null

        // 1. Check OS CacheDirectory first
        cacheDirectory.getCachedBytes(cleanUrl)?.let { cached ->
            if (cached.isNotEmpty()) return cached
        }

        // 2. Check inline data:image/...;base64,... URI
        if (cleanUrl.startsWith("data:", ignoreCase = true)) {
            val commaIdx = cleanUrl.indexOf(',')
            if (commaIdx > 0 && commaIdx + 1 < cleanUrl.length) {
                val header = cleanUrl.substring(0, commaIdx)
                val payload = cleanUrl.substring(commaIdx + 1)
                val bytes = if (header.contains(";base64", ignoreCase = true)) {
                    decodeBase64(payload)
                } else {
                    payload.encodeToByteArray()
                }
                if (bytes.isNotEmpty()) {
                    cacheDirectory.putCachedBytes(cleanUrl, bytes)
                    return bytes
                }
            }
        }

        // 3. Check compiled Resources table (e.g. "res://drawable/logo" or "logo.png?2")
        resolveDrawableResourceBytes(cleanUrl)?.let { drawableBytes ->
            if (drawableBytes.isNotEmpty()) {
                cacheDirectory.putCachedBytes(cleanUrl, drawableBytes)
                return drawableBytes
            }
        }

        // 4. Remote HTTP / HTTPS URL via urlFetcherHook
        if (cleanUrl.startsWith("http://", ignoreCase = true) ||
            cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            val fetched = urlFetcherHook?.invoke(cleanUrl)
            if (fetched != null && fetched.isNotEmpty()) {
                cacheDirectory.putCachedBytes(cleanUrl, fetched)
                return fetched
            }
            return null
        }

        // 5. Local file or relative resource path via localResourceReaderHook or urlFetcherHook
        val localBytes = localResourceReaderHook?.invoke(cleanUrl)
            ?: urlFetcherHook?.invoke(cleanUrl)
        if (localBytes != null && localBytes.isNotEmpty()) {
            cacheDirectory.putCachedBytes(cleanUrl, localBytes)
            return localBytes
        }

        return null
    }

    /**
     * Loads and decodes the image for [url] from the in-memory cache, OS [CacheDirectory], or remote/local
     * source, automatically caching both the encoded file on disk and the decoded [ImageBuffer] in memory.
     */
    fun loadCachedOrFetch(
        url: String,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null

        val hasCustomSize = options.targetWidth != null && options.targetHeight != null
        if (!hasCustomSize) {
            memoryBufferCache[cleanUrl]?.let { return it.copy() }
        }

        val bytes = fetchAndCacheBytes(cleanUrl) ?: return null
        val decoded = ImageLoader.load(ImageSource.fromBytes(bytes), options)
        if (!hasCustomSize) {
            putMemoryBuffer(cleanUrl, decoded)
        }
        if (!cacheEntries.containsKey(cleanUrl)) {
            val fmt = ImageFormat.detectFromHeader(bytes)
            val ext = if (fmt != ImageFormat.UNKNOWN) ".${fmt.defaultExtension}" else ".bin"
            val fileName = cacheDirectory.cacheFileNameForKey(cleanUrl, ext)
            cacheEntries[cleanUrl] = ImageCacheEntry(
                url = cleanUrl,
                cacheFileName = fileName,
                cachedFilePath = cacheDirectory.resolvePath(fileName),
                byteSize = bytes.size,
                width = decoded.width,
                height = decoded.height,
                format = fmt
            )
        }
        return decoded
    }

    /**
     * Evicts [url] from both the in-memory decoded cache and the OS [CacheDirectory].
     */
    fun evict(url: String): Boolean {
        val cleanUrl = url.trim()
        val memRemoved = memoryBufferCache.remove(cleanUrl) != null
        cacheEntries.remove(cleanUrl)
        val diskRemoved = cacheDirectory.evictCachedEntry(cleanUrl)
        return memRemoved || diskRemoved
    }

    /**
     * Clears only the in-memory decoded [ImageBuffer] cache while retaining files in the OS [CacheDirectory].
     */
    fun clearMemoryCache() {
        memoryBufferCache.clear()
    }

    /**
     * Clears all cached image files from the OS [CacheDirectory] on disk and returns the number of deleted files.
     */
    fun clearDiskCache(): Int {
        cacheEntries.clear()
        return cacheDirectory.clearCache()
    }

    /**
     * Clears both the in-memory decoded image cache and all files in the OS [CacheDirectory] on disk.
     * Ideal for wiring to an application's "Clear Cache" button in Settings.
     */
    fun clearCache(): Int {
        memoryBufferCache.clear()
        cacheEntries.clear()
        return cacheDirectory.clearCache()
    }

    private fun putMemoryBuffer(url: String, buffer: ImageBuffer) {
        if (memoryBufferCache.size >= maxMemoryEntries && !memoryBufferCache.containsKey(url)) {
            val oldestKey = memoryBufferCache.keys.firstOrNull()
            if (oldestKey != null) {
                memoryBufferCache.remove(oldestKey)
            }
        }
        memoryBufferCache[url] = buffer.copy()
    }

    private fun resolveDrawableResourceBytes(urlOrName: String): ByteArray? {
        val clean = urlOrName.substringBefore('?').substringBefore('#').trim()
        val baseName = clean.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val drawable = Resources.getDrawable(clean)
            ?: (if (baseName.isNotEmpty()) Resources.getDrawable(baseName) else null)
            ?: return null

        drawable.base64Data?.takeIf { it.isNotBlank() }?.let { b64 ->
            val decoded = decodeBase64(b64)
            if (decoded.isNotEmpty()) return decoded
        }
        drawable.svgSource?.takeIf { it.isNotBlank() }?.let { svg ->
            return svg.encodeToByteArray()
        }
        return null
    }

    private fun decodeBase64(encoded: String): ByteArray {
        val clean = encoded.filter { !it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)
        val table = IntArray(128) { -1 }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (idx in chars.indices) {
            table[chars[idx].code] = idx
        }
        val out = ArrayList<Byte>((clean.length * 3) / 4)
        var i = 0
        while (i + 3 < clean.length) {
            val c0 = clean[i].code.let { if (it < 128) table[it] else -1 }
            val c1 = clean[i + 1].code.let { if (it < 128) table[it] else -1 }
            val c2 = if (clean[i + 2] == '=') -2 else clean[i + 2].code.let { if (it < 128) table[it] else -1 }
            val c3 = if (clean[i + 3] == '=') -2 else clean[i + 3].code.let { if (it < 128) table[it] else -1 }
            if (c0 < 0 || c1 < 0) break
            val b0 = (c0 shl 2) or (c1 ushr 4)
            out.add((b0 and 0xFF).toByte())
            if (c2 >= 0) {
                val b1 = ((c1 and 0x0F) shl 4) or (c2 ushr 2)
                out.add((b1 and 0xFF).toByte())
                if (c3 >= 0) {
                    val b2 = ((c2 and 0x03) shl 6) or c3
                    out.add((b2 and 0xFF).toByte())
                }
            }
            i += 4
        }
        return out.toByteArray()
    }
}
