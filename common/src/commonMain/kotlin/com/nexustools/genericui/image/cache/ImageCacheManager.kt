@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.image.cache

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.util.CacheDirectory
import com.nexustools.genericui.util.WeakReference
import kotlin.concurrent.atomics.AtomicInt

/**
 * Cross-platform URL and resource image cache manager backed by the OS [CacheDirectory]
 * (`<userCacheDir>/<appName>/images`) and a [WeakReference]-backed in-memory decoded [ImageBuffer]
 * map on system targets so unreferenced images are automatically reclaimed by the garbage collector.
 *
 * On `:web` ([isWebBrowserManaged] == `true`), the web browser manages image loading and HTTP caching
 * automatically via `HTMLImageElement`, so [ImageCacheManager] performs zero caching of its own.
 */
object ImageCacheManager {

    const val IMAGE_CACHE_SUBDIRECTORY: String = "images"

    var maxMemoryEntries: Int = 256

    /**
     * Optional platform or application hook for downloading `http://` and `https://` image URLs.
     */
    var urlFetcherHook: ((url: String) -> ByteArray?)? = null

    /**
     * Optional async coroutine hook for downloading `http://` and `https://` image URLs without blocking.
     */
    var asyncUrlFetcherHook: (suspend (url: String) -> ByteArray?)? = null

    /**
     * Optional platform hook for reading local file paths or relative resource files (e.g. `src/commonMain/resources/<name>`).
     */
    var localResourceReaderHook: ((uriOrPath: String) -> ByteArray?)? = null

    private val lockState = AtomicInt(0)
    private var activeAppName: String = GenericUI.applicationName
    private var backingCacheDirectory: CacheDirectory =
        CacheDirectory(appName = activeAppName, subdirectory = IMAGE_CACHE_SUBDIRECTORY)

    private val memoryBufferCache = LinkedHashMap<String, WeakReference<ImageBuffer>>()
    private val cacheEntries = LinkedHashMap<String, ImageCacheEntry>()

    private inline fun <T> withLock(block: () -> T): T {
        while (!lockState.compareAndSet(0, 1)) {
            // Short spin for thread-safe cache access across coroutine workers
        }
        try {
            return block()
        } finally {
            lockState.store(0)
        }
    }

    private fun pruneClearedMemoryReferencesUnlocked(): Int {
        var removed = 0
        val iter = memoryBufferCache.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.get() == null) {
                iter.remove()
                removed++
            }
        }
        return removed
    }

    /**
     * Sweeps any [WeakReference] entries whose [ImageBuffer] has been garbage-collected.
     */
    fun pruneCollectedMemoryReferences(): Int =
        if (isWebBrowserManaged) 0 else withLock { pruneClearedMemoryReferencesUnlocked() }

    /**
     * `true` when running on `:web` where the browser handles image loading and caching natively.
     */
    val isWebBrowserManaged: Boolean
        get() = GenericUI.activeFactory?.usesBrowserManagedImageLoading == true ||
            ImageLoader.activeNativeCodec?.usesBrowserManagedImageLoading == true

    /**
     * The OS [CacheDirectory] (`<userCacheDir>/<appName>/images`) where fetched images are stored on system targets.
     */
    val cacheDirectory: CacheDirectory
        get() = withLock {
            val currentApp = GenericUI.applicationName
            if (currentApp != activeAppName) {
                activeAppName = currentApp
                backingCacheDirectory = CacheDirectory(
                    appName = currentApp,
                    subdirectory = IMAGE_CACHE_SUBDIRECTORY
                )
            }
            backingCacheDirectory
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
        get() = if (isWebBrowserManaged) 0 else cacheDirectory.fileCount

    /**
     * Total byte size of all cached image files currently stored in the OS [CacheDirectory].
     */
    val cachedDiskByteSize: Long
        get() = if (isWebBrowserManaged) 0L else cacheDirectory.totalByteSize()

    /**
     * Number of live weakly-referenced decoded [ImageBuffer] instances currently held in memory.
     */
    val cachedMemoryEntryCount: Int
        get() = if (isWebBrowserManaged) 0 else withLock {
            pruneClearedMemoryReferencesUnlocked()
            memoryBufferCache.size
        }

    /**
     * Returns the weakly-referenced in-memory [ImageBuffer] for [url] if still alive in RAM, or `null` otherwise.
     */
    fun getMemoryCached(url: String): ImageBuffer? {
        if (isWebBrowserManaged) return null
        val key = url.trim()
        if (key.isEmpty()) return null
        return withLock {
            val directRef = memoryBufferCache[key]
            if (directRef != null) {
                val alive = directRef.get()
                if (alive != null) return@withLock alive
                memoryBufferCache.remove(key)
            }
            if (!key.startsWith("data:", ignoreCase = true)) {
                val stripped = key.substringBefore('?').substringBefore('#').trim()
                if (stripped.isNotEmpty() && stripped != key) {
                    val strippedRef = memoryBufferCache[stripped]
                    if (strippedRef != null) {
                        val alive = strippedRef.get()
                        if (alive != null) return@withLock alive
                        memoryBufferCache.remove(stripped)
                    }
                }
            }
            null
        }
    }

    /**
     * Returns `true` if [url] is currently cached either in memory (via a live [WeakReference]) or in the OS [CacheDirectory].
     */
    fun isCached(url: String): Boolean {
        if (isWebBrowserManaged) return false
        val key = url.trim()
        if (key.isEmpty()) return false
        val inMemory = getMemoryCached(key) != null
        return inMemory || cacheDirectory.hasCachedEntry(key)
    }

    /**
     * Returns the [ImageCacheEntry] metadata for [url] if cached, or `null` otherwise.
     */
    fun getCacheEntry(url: String): ImageCacheEntry? =
        if (isWebBrowserManaged) null else withLock { cacheEntries[url.trim()] }

    /**
     * Stores [decodedBuffer] directly in the in-memory cache under [url].
     */
    fun putInCache(
        url: String,
        decodedBuffer: ImageBuffer
    ): ImageCacheEntry {
        val cleanUrl = url.trim()
        val entry = ImageCacheEntry(
            url = cleanUrl,
            cacheFileName = cleanUrl,
            cachedFilePath = cleanUrl,
            byteSize = decodedBuffer.bytes.size,
            width = decodedBuffer.width,
            height = decodedBuffer.height,
            format = ImageFormat.UNKNOWN
        )
        if (!isWebBrowserManaged) {
            withLock {
                putMemoryBufferUnlocked(cleanUrl, decodedBuffer)
                cacheEntries[cleanUrl] = entry
            }
        }
        return entry
    }

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
        val buffer = decodedBuffer ?: ImageLoader.loadFromBytes(encodedBytes)
        val savedPath = if (isWebBrowserManaged) {
            cleanUrl
        } else {
            val p = cacheDirectory.putCachedBytes(cleanUrl, encodedBytes, ext)
            withLock {
                putMemoryBufferUnlocked(cleanUrl, buffer)
            }
            p
        }

        val entry = ImageCacheEntry(
            url = cleanUrl,
            cacheFileName = fileName,
            cachedFilePath = savedPath,
            byteSize = encodedBytes.size,
            width = buffer.width,
            height = buffer.height,
            format = format
        )
        if (!isWebBrowserManaged) {
            withLock {
                cacheEntries[cleanUrl] = entry
            }
        }
        return entry
    }

    /**
     * Asynchronously resolves or downloads the encoded image [ByteBuffer] for [url], reading from and writing
     * to the OS [CacheDirectory] via its non-blocking `suspend` `IFileSystem` methods on system targets.
     */
    suspend fun fetchAndCacheBuffer(url: String): ByteBuffer? {
        if (isWebBrowserManaged) return null
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null

        cacheDirectory.getCached(cleanUrl)?.let { cached ->
            if (cached.isNotEmpty()) return cached
        }

        val resolvedBytes = resolveBytesWithoutDiskCache(cleanUrl, preferAsync = true) ?: return null
        if (resolvedBytes.isEmpty()) return null
        val buffer = ByteBuffer.wrap(resolvedBytes)
        cacheDirectory.putCached(cleanUrl, buffer)
        return buffer
    }

    /**
     * Resolves or downloads the encoded image bytes for [url], reading from the OS [CacheDirectory]
     * when already cached and persisting newly fetched bytes into the OS [CacheDirectory].
     */
    fun fetchAndCacheBytes(url: String): ByteArray? {
        if (isWebBrowserManaged) return null
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null

        // 1. Check OS CacheDirectory first
        cacheDirectory.getCachedBytes(cleanUrl)?.let { cached ->
            if (cached.isNotEmpty()) return cached
        }

        // 2. Resolve from data URI, Resources, or hooks
        val resolvedBytes = decodeOrFetchImmediate(cleanUrl) ?: return null
        if (resolvedBytes.isNotEmpty()) {
            cacheDirectory.putCachedBytes(cleanUrl, resolvedBytes)
            return resolvedBytes
        }
        return null
    }

    /**
     * Loads and decodes the image for [url] from the in-memory cache, OS [CacheDirectory], or remote/local
     * source, automatically caching both the encoded file on disk and the decoded [ImageBuffer] in memory
     * on system targets.
     */
    fun loadCachedOrFetch(
        url: String,
        options: ImageDecodeOptions = ImageDecodeOptions(),
        copyResult: Boolean = false
    ): ImageBuffer? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null

        if (isWebBrowserManaged) {
            return ImageLoader.activeNativeCodec?.decode(ImageSource.fromUrl(cleanUrl), options)
        }

        val hasCustomSize = options.targetWidth != null && options.targetHeight != null
        if (!hasCustomSize) {
            getMemoryCached(cleanUrl)?.let { cached ->
                return if (copyResult) cached.copy() else cached
            }
        }

        val bytes = fetchAndCacheBytes(cleanUrl) ?: return null
        val decoded = ImageLoader.load(ImageSource.fromBytes(bytes), options)
        val dir = cacheDirectory
        withLock {
            if (!hasCustomSize) {
                putMemoryBufferUnlocked(cleanUrl, decoded)
            }
            if (!cacheEntries.containsKey(cleanUrl)) {
                val fmt = ImageFormat.detectFromHeader(bytes)
                val ext = if (fmt != ImageFormat.UNKNOWN) ".${fmt.defaultExtension}" else ".bin"
                val fileName = dir.cacheFileNameForKey(cleanUrl, ext)
                cacheEntries[cleanUrl] = ImageCacheEntry(
                    url = cleanUrl,
                    cacheFileName = fileName,
                    cachedFilePath = dir.resolvePath(fileName),
                    byteSize = bytes.size,
                    width = decoded.width,
                    height = decoded.height,
                    format = fmt
                )
            }
        }
        return if (copyResult) decoded.copy() else decoded
    }

    /**
     * Asynchronously loads and decodes the image for [url] using Kotlin coroutines, consulting the
     * in-memory cache first and falling back to [fetchAndCacheBuffer].
     */
    suspend fun loadCachedOrFetchAsync(
        url: String,
        options: ImageDecodeOptions = ImageDecodeOptions(),
        copyResult: Boolean = false
    ): ImageBuffer? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null

        if (isWebBrowserManaged) {
            return ImageLoader.loadAsync(ImageSource.fromUrl(cleanUrl), options)
        }

        val hasCustomSize = options.targetWidth != null && options.targetHeight != null
        if (!hasCustomSize) {
            getMemoryCached(cleanUrl)?.let { cached ->
                return if (copyResult) cached.copy() else cached
            }
        }

        val bytes = fetchAndCacheBuffer(cleanUrl)?.toByteArray() ?: fetchAndCacheBytes(cleanUrl) ?: return null
        val decoded = ImageLoader.load(ImageSource.fromBytes(bytes), options)
        val dir = cacheDirectory
        withLock {
            if (!hasCustomSize) {
                putMemoryBufferUnlocked(cleanUrl, decoded)
            }
            if (!cacheEntries.containsKey(cleanUrl)) {
                val fmt = ImageFormat.detectFromHeader(bytes)
                val ext = if (fmt != ImageFormat.UNKNOWN) ".${fmt.defaultExtension}" else ".bin"
                val fileName = dir.cacheFileNameForKey(cleanUrl, ext)
                cacheEntries[cleanUrl] = ImageCacheEntry(
                    url = cleanUrl,
                    cacheFileName = fileName,
                    cachedFilePath = dir.resolvePath(fileName),
                    byteSize = bytes.size,
                    width = decoded.width,
                    height = decoded.height,
                    format = fmt
                )
            }
        }
        return if (copyResult) decoded.copy() else decoded
    }

    /**
     * Evicts [url] from both the in-memory decoded cache and the OS [CacheDirectory].
     */
    fun evict(url: String): Boolean {
        if (isWebBrowserManaged) return false
        val cleanUrl = url.trim()
        val memRemoved = withLock {
            val rem = memoryBufferCache.remove(cleanUrl)?.also { it.clear() } != null
            cacheEntries.remove(cleanUrl)
            rem
        }
        val diskRemoved = cacheDirectory.evictCachedEntry(cleanUrl)
        return memRemoved || diskRemoved
    }

    /**
     * Clears only the in-memory decoded [ImageBuffer] weak references while retaining files in the OS [CacheDirectory].
     */
    fun clearMemoryCache() = withLock {
        for (ref in memoryBufferCache.values) {
            ref.clear()
        }
        memoryBufferCache.clear()
    }

    /**
     * Clears all cached image files from the OS [CacheDirectory] on disk and returns the number of deleted files.
     */
    fun clearDiskCache(): Int {
        withLock { cacheEntries.clear() }
        if (isWebBrowserManaged) return 0
        return cacheDirectory.clear()
    }

    /**
     * Clears both the in-memory decoded image cache and all files in the OS [CacheDirectory] on disk.
     * Ideal for wiring to an application's "Clear Cache" button in Settings.
     */
    fun clearCache(): Int {
        withLock {
            for (ref in memoryBufferCache.values) {
                ref.clear()
            }
            memoryBufferCache.clear()
            cacheEntries.clear()
        }
        if (isWebBrowserManaged) return 0
        return cacheDirectory.clear()
    }

    private suspend fun resolveBytesWithoutDiskCache(cleanUrl: String, preferAsync: Boolean): ByteArray? {
        if (preferAsync && (cleanUrl.startsWith("http://", ignoreCase = true) ||
                cleanUrl.startsWith("https://", ignoreCase = true))
        ) {
            asyncUrlFetcherHook?.invoke(cleanUrl)?.let { if (it.isNotEmpty()) return it }
        }
        return decodeOrFetchImmediate(cleanUrl)
    }

    private fun decodeOrFetchImmediate(cleanUrl: String): ByteArray? {
        // Inline data:image/...;base64,... URI
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
                if (bytes.isNotEmpty()) return bytes
            }
        }

        // Compiled Resources table (e.g. "res://drawable/logo" or "logo.png?2")
        resolveDrawableResourceBytes(cleanUrl)?.let { drawableBytes ->
            if (drawableBytes.isNotEmpty()) return drawableBytes
        }

        // Remote HTTP / HTTPS URL via urlFetcherHook
        if (cleanUrl.startsWith("http://", ignoreCase = true) ||
            cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            val fetched = urlFetcherHook?.invoke(cleanUrl)
            if (fetched != null && fetched.isNotEmpty()) return fetched
            return null
        }

        // Local file or relative resource path via localResourceReaderHook or urlFetcherHook
        val localBytes = localResourceReaderHook?.invoke(cleanUrl)
            ?: urlFetcherHook?.invoke(cleanUrl)
        if (localBytes != null && localBytes.isNotEmpty()) return localBytes

        return null
    }

    private fun putMemoryBufferUnlocked(url: String, buffer: ImageBuffer) {
        pruneClearedMemoryReferencesUnlocked()
        if (memoryBufferCache.size >= maxMemoryEntries && !memoryBufferCache.containsKey(url)) {
            val oldestKey = memoryBufferCache.keys.firstOrNull()
            if (oldestKey != null) {
                memoryBufferCache.remove(oldestKey)?.clear()
            }
        }
        memoryBufferCache[url] = WeakReference(buffer)
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
