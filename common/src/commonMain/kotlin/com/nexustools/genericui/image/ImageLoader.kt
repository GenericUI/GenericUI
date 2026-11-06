@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.image

import com.nexustools.genericui.image.cache.ImageCacheManager
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.image.codec.LibPngCodec
import com.nexustools.genericui.image.codec.LibWebpCodec
import com.nexustools.genericui.io.FileSystem
import com.nexustools.genericui.nativeimpl.image.ImageCodecNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.util.CacheDirectory
import com.nexustools.genericui.util.WeakReference
import kotlin.concurrent.atomics.AtomicInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Full cross-platform Image API in the base GenericUI project for probing metadata,
 * loading/decoding images (`PNG`, `JPEG`, `WEBP`, `GIF`, `BMP`, `ICO`, `TIFF`, `SVG`, `PPM`)
 * into [ImageBuffer] or [AnimatedImage], and encoding [ImageBuffer]s back to bytes.
 *
 * Uses the active platform's native image loader where possible:
 * - `:swing` (Java): `javax.imageio.ImageIO` & `BufferedImage` on coroutine worker threads.
 * - `:android`: `android.graphics.BitmapFactory`, `android.graphics.ImageDecoder`, & `Bitmap.compress` on coroutine worker threads.
 * - `:web`: Creates an invisible `HTMLImageElement` (`<img style="display: none">`), sets `src`, and watches
 *   `onload` / checks `complete` so `:web` properly simulates and executes asynchronous image loading.
 * - `:macos` / `:ios`: Apple `ImageIO` (`CGImageSource` / `CGImageDestination`) on coroutine worker threads.
 * - `:windows` / `:gtk` / `:qt` (`:desktop`): Native OS loaders (`WIC`, `GdkPixbuf`, `QImage`) +
 *   `libjpeg` ([LibJpegCodec]), `libpng` ([LibPngCodec]), and `libwebp` ([LibWebpCodec]) on coroutine worker threads.
 *
 * Resizing uses antialiased area-averaging + bilinear interpolation by default ([ImageDecodeOptions.antialiasing] == `true`).
 */
object ImageLoader {
    /**
     * Built-in default `loading.svg` resource URI used across all backends while target images are loading asynchronously.
     */
    const val DEFAULT_PLACEHOLDER_URI: String = "res://drawable/loading"

    /**
     * Built-in default `loading.svg` data-URI placeholder image used while target images are loading asynchronously.
     */
    val DEFAULT_PLACEHOLDER_DATA_URL: String
        get() = com.nexustools.genericui.res.Resources.DEFAULT_LOADING_SVG_DATA_URL

    /**
     * Optional platform-native [ImageCodecNativeImpl] registered by the active backend (`:swing`, `:android`, `:web`, `:desktop`, `:mobile`).
     */
    internal var activeNativeCodec: ImageCodecNativeImpl? = null

    /**
     * Optional lambda hooks for custom decoders/encoders.
     */
    var platformDecoderHook: ((ImageSource, ImageDecodeOptions) -> ImageBuffer?)? = null
    var platformEncoderHook: ((ImageBuffer, ImageEncodeOptions) -> ByteArray?)? = null

    /**
     * Maximum number of concurrent background coroutine image decodes/downloads on system backends.
     */
    const val MAX_CONCURRENT_ASYNC_LOADS: Int = 4

    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadSemaphore = Semaphore(MAX_CONCURRENT_ASYNC_LOADS)
    private val weakRefLock = AtomicInt(0)
    private val weakImageReferences = LinkedHashMap<String, WeakReference<ImageBuffer>>()

    private inline fun <T> withWeakRefLock(block: () -> T): T {
        while (!weakRefLock.compareAndSet(0, 1)) {
            // Short spin for thread-safe weak reference map access
        }
        try {
            return block()
        } finally {
            weakRefLock.store(0)
        }
    }

    /**
     * Stores a [WeakReference] to [buffer] under [key] so in-memory image references automatically
     * clear when no longer referenced by any active component.
     */
    fun trackWeakImageReference(key: String, buffer: ImageBuffer) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        withWeakRefLock {
            pruneClearedWeakReferencesUnlocked()
            weakImageReferences[clean] = WeakReference(buffer)
        }
    }

    /**
     * Resolves a live [ImageBuffer] from its [WeakReference] under [key], or returns `null` if it
     * has been garbage-collected.
     */
    fun getWeakImageReference(key: String): ImageBuffer? {
        val clean = key.trim()
        if (clean.isEmpty()) return null
        return withWeakRefLock {
            val ref = weakImageReferences[clean] ?: return@withWeakRefLock null
            val alive = ref.get()
            if (alive == null) {
                weakImageReferences.remove(clean)
            }
            alive
        }
    }

    /**
     * Registers an in-memory [ImageBuffer] under a synthetic `"memory://imagebuffer/..."` URI so
     * [load], [loadAsync], [launchLoad], and [probeMetadata] resolve it immediately across all backends.
     */
    fun registerMemoryBuffer(uri: String, buffer: ImageBuffer) {
        trackWeakImageReference(uri, buffer)
    }

    /**
     * Resolves an in-memory [ImageBuffer] previously registered via [registerMemoryBuffer] for a
     * `"memory://..."` URI, or returns `null` if not found or garbage-collected.
     */
    fun resolveMemoryBuffer(uri: String?): ImageBuffer? {
        val clean = uri?.trim() ?: return null
        if (!clean.startsWith("memory://", ignoreCase = true)) return null
        getWeakImageReference(clean)?.let { return it }
        val baseKey = clean.substringBefore("@_crop:")
        if (baseKey != clean) {
            getWeakImageReference(baseKey)?.let { return it }
        }
        return null
    }

    /**
     * Sweeps any [WeakReference] entries whose [ImageBuffer] has been garbage-collected.
     */
    fun pruneClearedWeakReferences(): Int = withWeakRefLock {
        pruneClearedWeakReferencesUnlocked() + ImageCacheManager.pruneCollectedMemoryReferences()
    }

    private fun pruneClearedWeakReferencesUnlocked(): Int {
        var removed = 0
        val iter = weakImageReferences.entries.iterator()
        while (iter.hasNext()) {
            if (iter.next().value.get() == null) {
                iter.remove()
                removed++
            }
        }
        return removed
    }

    /**
     * `true` when running on `:web` where the browser handles image loading and HTTP caching via `HTMLImageElement`.
     */
    val isWebBrowserManaged: Boolean
        get() = ImageCacheManager.isWebBrowserManaged

    /**
     * The persistent OS [CacheDirectory] (`<userCacheDir>/<appName>/images`) used by [ImageLoader]
     * and [ImageCacheManager] to store downloaded URL images and resolved assets on system targets.
     */
    val cacheDirectory: CacheDirectory
        get() = ImageCacheManager.cacheDirectory

    /**
     * Resolved OS directory path for cached images.
     */
    val cacheDirectoryPath: String
        get() = ImageCacheManager.cacheDirectoryPath

    /**
     * Total disk bytes currently used by cached images in [cacheDirectory].
     */
    val cachedDiskByteSize: Long
        get() = ImageCacheManager.cachedDiskByteSize

    /**
     * Clears both the in-memory decoded image cache and all cached image files in the OS [cacheDirectory].
     */
    fun clearCache(): Int {
        withWeakRefLock {
            for (ref in weakImageReferences.values) {
                ref.clear()
            }
            weakImageReferences.clear()
        }
        return ImageCacheManager.clearCache()
    }

    /**
     * Evicts a single cached URL or path from both memory and the OS [cacheDirectory].
     */
    fun evictFromCache(url: String): Boolean {
        val clean = url.trim()
        withWeakRefLock {
            weakImageReferences.remove(clean)?.clear()
        }
        return ImageCacheManager.evict(clean)
    }

    /**
     * Returns `true` if [url] is currently cached in memory or in the OS [cacheDirectory].
     */
    fun isCached(url: String): Boolean = ImageCacheManager.isCached(url)

    /**
     * Rasterizes the built-in `loading.svg` (`res://drawable/loading`) placeholder into an antialiased
     * [ImageBuffer] for display while an image is loading asynchronously.
     */
    fun createDefaultPlaceholderBuffer(width: Int = 48, height: Int = 48, darkTheme: Boolean = true): ImageBuffer {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val buf = ImageBuffer(w, h, PixelFormat.RGBA_8888)
        val bg = if (darkTheme) RgbaColor(30, 41, 59, 0.9f) else RgbaColor(226, 232, 240, 0.95f)
        val track = if (darkTheme) RgbaColor(71, 85, 105, 0.45f) else RgbaColor(148, 163, 184, 0.45f)
        val arc = RgbaColor(56, 189, 248, 0.95f)
        val centerDot = if (darkTheme) RgbaColor(148, 163, 184, 0.7f) else RgbaColor(100, 116, 139, 0.7f)
        buf.fill(bg)

        val cx = (w - 1) * 0.5f
        val cy = (h - 1) * 0.5f
        val outerR = min(w, h) * 0.29f
        val strokeHalf = (min(w, h) * 0.042f).coerceAtLeast(1.1f)
        val dotR = (min(w, h) * 0.065f).coerceAtLeast(1.2f)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = x - cx
                val dy = y - cy
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val ringDelta = kotlin.math.abs(dist - outerR)
                if (ringDelta <= strokeHalf + 0.8f) {
                    val coverage = (1f - (ringDelta - strokeHalf).coerceAtLeast(0f) / 0.8f).coerceIn(0f, 1f)
                    // Top-right 90-degree arc (`M24 10 A14 14 0 0 1 38 24` in loading.svg)
                    if (dx >= -0.5f && dy <= 0.5f) {
                        buf.setPixel(x, y, arc.withAlpha(arc.alphaFloat * coverage))
                    } else {
                        buf.setPixel(x, y, track.withAlpha(track.alphaFloat * coverage))
                    }
                } else if (dist <= dotR + 0.7f) {
                    val coverage = (1f - (dist - dotR).coerceAtLeast(0f) / 0.7f).coerceIn(0f, 1f)
                    buf.setPixel(x, y, centerDot.withAlpha(centerDot.alphaFloat * coverage))
                }
            }
        }
        return buf
    }

    /**
     * Probes the dimensions and [ImageMetadata] of an [ImageSource] without decoding the full pixel payload.
     */
    fun probeMetadata(source: ImageSource): ImageMetadata {
        val memBuf = source.memoryBuffer ?: resolveMemoryBuffer(source.uriOrPath)
        if (memBuf != null) {
            return ImageMetadata(
                width = memBuf.width,
                height = memBuf.height,
                format = ImageFormat.RAW_RGBA,
                pixelFormat = memBuf.pixelFormat,
                colorSpace = memBuf.colorSpace
            )
        }

        activeNativeCodec?.probeMetadata(source)?.let { return it }

        if (isWebBrowserManaged && source.encodedBytes == null) {
            return ImageMetadata(
                width = 64,
                height = 64,
                format = source.formatHint
            )
        }

        val bytes = source.encodedBytes
            ?: source.uriOrPath?.let { ImageCacheManager.fetchAndCacheBytes(it) }
            ?: ByteArray(0)
        val detectedFormat = if (bytes.isNotEmpty()) {
            ImageFormat.detectFromHeader(bytes)
        } else {
            source.formatHint
        }

        if (bytes.isNotEmpty()) {
            when (detectedFormat) {
                ImageFormat.JPEG -> return LibJpegCodec.probeJpegMetadata(bytes)
                ImageFormat.PNG -> return LibPngCodec.probePngMetadata(bytes)
                ImageFormat.WEBP -> return LibWebpCodec.probeWebpMetadata(bytes)
                else -> {}
            }
        }

        val (w, h) = parseDimensionsFromHeader(bytes, detectedFormat)
        return ImageMetadata(
            width = w,
            height = h,
            format = detectedFormat,
            isAnimated = detectedFormat == ImageFormat.GIF
        )
    }

    /**
     * Loads and decodes an [ImageSource] into an in-memory [ImageBuffer].
     */
    fun load(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer {
        source.memoryBuffer?.let { existing ->
            return applyPostDecodeOptions(existing.copy(), options)
        }
        resolveMemoryBuffer(source.uriOrPath)?.let { memBuf ->
            return applyPostDecodeOptions(memBuf, options)
        }

        val uri = source.uriOrPath?.trim()
        val hasCustomSize = options.targetWidth != null && options.targetHeight != null
        val weakCacheKey = if (!uri.isNullOrEmpty()) {
            if (hasCustomSize) "$uri@${options.targetWidth}x${options.targetHeight}:${options.antialiasing}" else uri
        } else null

        if (!isWebBrowserManaged && weakCacheKey != null && source.encodedBytes == null) {
            getWeakImageReference(weakCacheKey)?.let { return it }
            if (!hasCustomSize && !uri.isNullOrEmpty()) {
                ImageCacheManager.getMemoryCached(uri)?.let { memCached ->
                    val res = applyPostDecodeOptions(memCached, options)
                    trackWeakImageReference(weakCacheKey, res)
                    return res
                }
            }
        }

        activeNativeCodec?.decode(source, options)?.let { nativeDecoded ->
            val result = applyPostDecodeOptions(nativeDecoded, options)
            if (!isWebBrowserManaged) {
                if (weakCacheKey != null) trackWeakImageReference(weakCacheKey, result)
                if (!uri.isNullOrEmpty() && !hasCustomSize) {
                    ImageCacheManager.putInCache(uri, result)
                }
            }
            return result
        }

        platformDecoderHook?.invoke(source, options)?.let { hookDecoded ->
            val result = applyPostDecodeOptions(hookDecoded, options)
            if (!isWebBrowserManaged) {
                if (weakCacheKey != null) trackWeakImageReference(weakCacheKey, result)
                if (!uri.isNullOrEmpty() && !hasCustomSize) {
                    ImageCacheManager.putInCache(uri, result)
                }
            }
            return result
        }

        if (isWebBrowserManaged && source.encodedBytes == null) {
            var syncWebBuffer: ImageBuffer? = null
            activeNativeCodec?.decodeAsync(
                source = source,
                options = options,
                onLoaded = { buf -> syncWebBuffer = applyPostDecodeOptions(buf, options) },
                onError = {}
            )
            syncWebBuffer?.let { return it }
            val fallbackW = options.targetWidth ?: 64
            val fallbackH = options.targetHeight ?: 64
            return ImageBuffer(fallbackW, fallbackH, options.preferredPixelFormat)
        }

        val bytes = source.encodedBytes
            ?: uri?.let { ImageCacheManager.fetchAndCacheBytes(it) }
        if (bytes != null && bytes.isNotEmpty()) {
            val decoded = decodeBytesInternal(bytes, options)
            if (!isWebBrowserManaged) {
                if (weakCacheKey != null) trackWeakImageReference(weakCacheKey, decoded)
                if (!uri.isNullOrEmpty() && !hasCustomSize) {
                    ImageCacheManager.putInCache(uri, bytes, decoded)
                }
            }
            return decoded
        }

        val fallbackW = options.targetWidth ?: 64
        val fallbackH = options.targetHeight ?: 64
        return ImageBuffer(fallbackW, fallbackH, options.preferredPixelFormat)
    }

    /**
     * Asynchronously loads and decodes an [ImageSource] into an [ImageBuffer] using:
     * - On `:web`: an invisible `HTMLImageElement` with `src` and `onload` / `complete` check.
     * - On system backends: non-blocking Kotlin coroutines on [Dispatchers.Default] with bounded concurrency.
     */
    suspend fun loadAsync(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer {
        source.memoryBuffer?.let { existing ->
            return applyPostDecodeOptions(existing.copy(), options)
        }
        resolveMemoryBuffer(source.uriOrPath)?.let { memBuf ->
            return applyPostDecodeOptions(memBuf, options)
        }

        val uri = source.uriOrPath?.trim()
        val hasCustomSize = options.targetWidth != null && options.targetHeight != null
        val weakCacheKey = if (!uri.isNullOrEmpty()) {
            if (hasCustomSize) "$uri@${options.targetWidth}x${options.targetHeight}:${options.antialiasing}" else uri
        } else null

        if (!isWebBrowserManaged && weakCacheKey != null && source.encodedBytes == null) {
            getWeakImageReference(weakCacheKey)?.let { return it }
            if (!hasCustomSize && !uri.isNullOrEmpty()) {
                ImageCacheManager.getMemoryCached(uri)?.let { memCached ->
                    val res = applyPostDecodeOptions(memCached, options)
                    trackWeakImageReference(weakCacheKey, res)
                    return res
                }
            }
        }

        val codec = activeNativeCodec
        if (codec != null && codec.usesBrowserManagedImageLoading) {
            return suspendCancellableCoroutine { cont ->
                val handled = codec.decodeAsync(
                    source = source,
                    options = options,
                    onLoaded = { decoded ->
                        if (cont.isActive) {
                            cont.resume(applyPostDecodeOptions(decoded, options))
                        }
                    },
                    onError = { err ->
                        if (cont.isActive) {
                            cont.resumeWithException(err)
                        }
                    }
                )
                if (!handled && cont.isActive) {
                    val fallbackW = options.targetWidth ?: 64
                    val fallbackH = options.targetHeight ?: 64
                    cont.resume(ImageBuffer(fallbackW, fallbackH, options.preferredPixelFormat))
                }
            }
        }

        return withContext(Dispatchers.Default) {
            loadSemaphore.withPermit {
                if (!isWebBrowserManaged && weakCacheKey != null && source.encodedBytes == null) {
                    getWeakImageReference(weakCacheKey)?.let { return@withPermit it }
                    if (!hasCustomSize && !uri.isNullOrEmpty()) {
                        ImageCacheManager.getMemoryCached(uri)?.let { memCached ->
                            val res = applyPostDecodeOptions(memCached, options)
                            trackWeakImageReference(weakCacheKey, res)
                            return@withPermit res
                        }
                    }
                }

                activeNativeCodec?.decode(source, options)?.let { nativeDecoded ->
                    val result = applyPostDecodeOptions(nativeDecoded, options)
                    if (!isWebBrowserManaged) {
                        if (weakCacheKey != null) trackWeakImageReference(weakCacheKey, result)
                        if (!uri.isNullOrEmpty() && !hasCustomSize) {
                            ImageCacheManager.putInCache(uri, result)
                        }
                    }
                    return@withPermit result
                }

                platformDecoderHook?.invoke(source, options)?.let { hookDecoded ->
                    val result = applyPostDecodeOptions(hookDecoded, options)
                    if (!isWebBrowserManaged) {
                        if (weakCacheKey != null) trackWeakImageReference(weakCacheKey, result)
                        if (!uri.isNullOrEmpty() && !hasCustomSize) {
                            ImageCacheManager.putInCache(uri, result)
                        }
                    }
                    return@withPermit result
                }

                val bytes = source.encodedBytes
                    ?: uri?.let { pathOrUrl ->
                        ImageCacheManager.fetchAndCacheBuffer(pathOrUrl)?.toByteArray()
                            ?: ImageCacheManager.fetchAndCacheBytes(pathOrUrl)
                            ?: FileSystem.read(pathOrUrl)?.toByteArray()
                    }
                if (bytes != null && bytes.isNotEmpty()) {
                    val decoded = decodeBytesInternal(bytes, options)
                    if (!isWebBrowserManaged) {
                        if (weakCacheKey != null) trackWeakImageReference(weakCacheKey, decoded)
                        if (!uri.isNullOrEmpty() && !hasCustomSize) {
                            ImageCacheManager.putInCache(uri, bytes, decoded)
                        }
                    }
                    return@withPermit decoded
                }

                val fallbackW = options.targetWidth ?: 64
                val fallbackH = options.targetHeight ?: 64
                ImageBuffer(fallbackW, fallbackH, options.preferredPixelFormat)
            }
        }
    }

    /**
     * Convenience helper for loading an [ImageBuffer] directly from a file path.
     */
    fun loadFromFile(
        filePath: String,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer = load(ImageSource.fromFile(filePath), options)

    /**
     * Convenience helper for loading an [ImageBuffer] directly from a URL.
     */
    fun loadFromUrl(
        url: String,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer = load(ImageSource.fromUrl(url), options)

    /**
     * Convenience helper for loading an [ImageBuffer] from encoded bytes.
     */
    fun loadFromBytes(
        encodedBytes: ByteArray,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer = load(ImageSource.fromBytes(encodedBytes), options)

    /**
     * Asynchronously loads an [ImageSource] and delivers the decoded [ImageBuffer] to [onLoaded].
     *
     * - On `:web`: creates an invisible `HTMLImageElement`, sets `src`, and watches `onload` / checks `complete`.
     * - On system backends: executes on a background Kotlin coroutine ([Dispatchers.Default]) with bounded
     *   concurrency so the UI thread never freezes.
     */
    fun loadAsync(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions(),
        onLoaded: (ImageBuffer) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        launchLoad(source, options, onLoaded, onError)
    }

    /**
     * Launches an asynchronous image load and returns the backing coroutine [Job] (or `null` when
     * satisfied immediately from memory or handled by `:web`'s `HTMLImageElement`).
     */
    fun launchLoad(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions(),
        onLoaded: (ImageBuffer) -> Unit,
        onError: (Throwable) -> Unit = {}
    ): Job? {
        source.memoryBuffer?.let { existing ->
            try {
                onLoaded(applyPostDecodeOptions(existing.copy(), options))
            } catch (t: Throwable) {
                onError(t)
            }
            return null
        }
        resolveMemoryBuffer(source.uriOrPath)?.let { memBuf ->
            try {
                onLoaded(applyPostDecodeOptions(memBuf, options))
            } catch (t: Throwable) {
                onError(t)
            }
            return null
        }

        val codec = activeNativeCodec
        if (codec != null) {
            val handledByNative = codec.decodeAsync(
                source = source,
                options = options,
                onLoaded = { decoded ->
                    try {
                        onLoaded(applyPostDecodeOptions(decoded, options))
                    } catch (t: Throwable) {
                        onError(t)
                    }
                },
                onError = onError
            )
            if (handledByNative) {
                return null
            }
        }

        val uri = source.uriOrPath?.trim()
        val hasCustomSize = options.targetWidth != null && options.targetHeight != null
        val weakCacheKey = if (!uri.isNullOrEmpty()) {
            if (hasCustomSize) "$uri@${options.targetWidth}x${options.targetHeight}:${options.antialiasing}" else uri
        } else null
        if (!isWebBrowserManaged && weakCacheKey != null && source.encodedBytes == null) {
            getWeakImageReference(weakCacheKey)?.let { weakHit ->
                try {
                    onLoaded(weakHit)
                } catch (t: Throwable) {
                    onError(t)
                }
                return null
            }
            if (!hasCustomSize && !uri.isNullOrEmpty()) {
                ImageCacheManager.getMemoryCached(uri)?.let { memCached ->
                    try {
                        val res = applyPostDecodeOptions(memCached, options)
                        trackWeakImageReference(weakCacheKey, res)
                        onLoaded(res)
                    } catch (t: Throwable) {
                        onError(t)
                    }
                    return null
                }
            }
        }

        return loaderScope.launch {
            try {
                val decoded = loadAsync(source, options)
                onLoaded(decoded)
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    /**
     * Resizes [src] to ([dstWidth] x [dstHeight]) using antialiased area-averaging (when downscaling)
     * and bilinear interpolation (when upscaling) by default ([antialiasing] == `true`).
     */
    fun resize(
        src: ImageBuffer,
        dstWidth: Int,
        dstHeight: Int,
        antialiasing: Boolean = true
    ): ImageBuffer {
        val targetW = dstWidth.coerceAtLeast(1)
        val targetH = dstHeight.coerceAtLeast(1)
        if (src.width == targetW && src.height == targetH) return src
        return if (antialiasing) {
            resizeAntialiasedCpu(src, targetW, targetH)
        } else {
            resizeNearestCpu(src, targetW, targetH)
        }
    }

    /**
     * Loads a multi-frame [AnimatedImage] (e.g. GIF / WebP / APNG).
     */
    fun loadAnimated(
        source: ImageSource,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): AnimatedImage {
        activeNativeCodec?.decodeAnimated(source, options)?.let { return it }
        val firstFrameBuffer = load(source, options)
        return AnimatedImage(
            width = firstFrameBuffer.width,
            height = firstFrameBuffer.height,
            frames = listOf(ImageFrame(0, firstFrameBuffer, durationMs = 100))
        )
    }

    /**
     * Encodes an [ImageBuffer] into a serialized image format (`PNG`, `JPEG`, `WEBP`, `BMP`, `PPM`, `RAW_RGBA`).
     */
    fun encode(
        buffer: ImageBuffer,
        options: ImageEncodeOptions = ImageEncodeOptions()
    ): ByteArray {
        activeNativeCodec?.encode(buffer, options)?.let { return it }
        platformEncoderHook?.invoke(buffer, options)?.let { return it }

        return when (options.format) {
            ImageFormat.JPEG -> LibJpegCodec.encode(buffer, options.quality)
            ImageFormat.PNG -> LibPngCodec.encode(buffer, options.compressionLevel)
            ImageFormat.RAW_RGBA -> buffer.toRgba8888().bytes
            ImageFormat.PPM -> encodePpm(buffer)
            else -> encodeBmp(buffer)
        }
    }

    private fun decodeBytesInternal(bytes: ByteArray, options: ImageDecodeOptions): ImageBuffer {
        val fmt = ImageFormat.detectFromHeader(bytes)
        val decoded = when (fmt) {
            ImageFormat.JPEG -> LibJpegCodec.decode(bytes)
            ImageFormat.PNG -> LibPngCodec.decode(bytes)
            ImageFormat.WEBP -> LibWebpCodec.decode(bytes)
            ImageFormat.BMP -> decodeBmp(bytes)
            ImageFormat.PPM -> decodePpm(bytes)
            ImageFormat.SVG -> createDefaultPlaceholderBuffer(
                width = options.targetWidth ?: 48,
                height = options.targetHeight ?: 48
            )
            else -> {
                val headStr = bytes.take(64).toByteArray().decodeToString().trimStart()
                if (headStr.startsWith("<svg", ignoreCase = true) || headStr.startsWith("<?xml", ignoreCase = true)) {
                    createDefaultPlaceholderBuffer(
                        width = options.targetWidth ?: 48,
                        height = options.targetHeight ?: 48
                    )
                } else {
                    val (w, h) = parseDimensionsFromHeader(bytes, fmt)
                    ImageBuffer(w.coerceAtLeast(1), h.coerceAtLeast(1), options.preferredPixelFormat)
                }
            }
        }
        return applyPostDecodeOptions(decoded, options)
    }

    private fun applyPostDecodeOptions(buffer: ImageBuffer, options: ImageDecodeOptions): ImageBuffer {
        var result = if (buffer.pixelFormat != options.preferredPixelFormat &&
            options.preferredPixelFormat == PixelFormat.RGBA_8888
        ) {
            buffer.toRgba8888()
        } else {
            buffer
        }

        val targetW = options.targetWidth
        val targetH = options.targetHeight
        if (targetW != null && targetH != null && (targetW != result.width || targetH != result.height)) {
            result = resize(result, targetW.coerceAtLeast(1), targetH.coerceAtLeast(1), options.antialiasing)
        }
        return result
    }

    private fun resizeNearestCpu(src: ImageBuffer, dstWidth: Int, dstHeight: Int): ImageBuffer {
        val dst = ImageBuffer(dstWidth, dstHeight, src.pixelFormat, src.colorSpace)
        val xRatio = src.width.toFloat() / dstWidth.toFloat()
        val yRatio = src.height.toFloat() / dstHeight.toFloat()
        for (y in 0 until dstHeight) {
            val sy = (y * yRatio).toInt().coerceIn(0, src.height - 1)
            for (x in 0 until dstWidth) {
                val sx = (x * xRatio).toInt().coerceIn(0, src.width - 1)
                dst.setPixel(x, y, src.getPixel(sx, sy))
            }
        }
        return dst
    }

    /**
     * High-quality antialiased resampler:
     * - Uses area-averaging box integration with fractional edge weights and premultiplied alpha when downscaling
     *   (eliminating moiré and jagged edges on thumbnails/icons).
     * - Uses smooth bilinear interpolation with premultiplied alpha when upscaling.
     */
    private fun resizeAntialiasedCpu(src: ImageBuffer, dstWidth: Int, dstHeight: Int): ImageBuffer {
        val rgbaSrc = if (src.pixelFormat == PixelFormat.RGBA_8888) src else src.toRgba8888()
        val srcW = rgbaSrc.width.coerceAtLeast(1)
        val srcH = rgbaSrc.height.coerceAtLeast(1)
        val srcBytes = rgbaSrc.bytes
        val dst = ImageBuffer(dstWidth, dstHeight, PixelFormat.RGBA_8888, src.colorSpace)
        val dstBytes = dst.bytes

        if (dstWidth < srcW || dstHeight < srcH) {
            // Area-averaging box filter for crisp downscaling
            val scaleX = srcW.toFloat() / dstWidth.toFloat()
            val scaleY = srcH.toFloat() / dstHeight.toFloat()
            for (dy in 0 until dstHeight) {
                val sy0 = dy * scaleY
                val sy1 = min(srcH.toFloat(), (dy + 1) * scaleY)
                val iy0 = floor(sy0).toInt().coerceIn(0, srcH - 1)
                val iy1 = ceil(sy1).toInt().coerceIn(iy0 + 1, srcH)

                for (dx in 0 until dstWidth) {
                    val sx0 = dx * scaleX
                    val sx1 = min(srcW.toFloat(), (dx + 1) * scaleX)
                    val ix0 = floor(sx0).toInt().coerceIn(0, srcW - 1)
                    val ix1 = ceil(sx1).toInt().coerceIn(ix0 + 1, srcW)

                    var sumPremulR = 0f
                    var sumPremulG = 0f
                    var sumPremulB = 0f
                    var sumA = 0f
                    var totalWeight = 0f

                    for (sy in iy0 until iy1) {
                        val wy = min(sy + 1f, sy1) - max(sy.toFloat(), sy0)
                        if (wy <= 0f) continue
                        val rowBase = sy * srcW * 4
                        for (sx in ix0 until ix1) {
                            val wx = min(sx + 1f, sx1) - max(sx.toFloat(), sx0)
                            if (wx <= 0f) continue
                            val weight = wx * wy
                            val idx = rowBase + sx * 4
                            if (idx + 3 < srcBytes.size) {
                                val r = (srcBytes[idx].toInt() and 0xFF).toFloat()
                                val g = (srcBytes[idx + 1].toInt() and 0xFF).toFloat()
                                val b = (srcBytes[idx + 2].toInt() and 0xFF).toFloat()
                                val a = (srcBytes[idx + 3].toInt() and 0xFF).toFloat()
                                val aNorm = a / 255f
                                sumPremulR += r * aNorm * weight
                                sumPremulG += g * aNorm * weight
                                sumPremulB += b * aNorm * weight
                                sumA += a * weight
                                totalWeight += weight
                            }
                        }
                    }

                    val outIdx = (dy * dstWidth + dx) * 4
                    if (totalWeight > 0f && outIdx + 3 < dstBytes.size) {
                        val avgA = (sumA / totalWeight).coerceIn(0f, 255f)
                        val aNorm = avgA / 255f
                        if (aNorm > 0.0001f) {
                            val invPremul = 1f / (totalWeight * aNorm)
                            dstBytes[outIdx] = (sumPremulR * invPremul).roundToInt().coerceIn(0, 255).toByte()
                            dstBytes[outIdx + 1] = (sumPremulG * invPremul).roundToInt().coerceIn(0, 255).toByte()
                            dstBytes[outIdx + 2] = (sumPremulB * invPremul).roundToInt().coerceIn(0, 255).toByte()
                            dstBytes[outIdx + 3] = avgA.roundToInt().coerceIn(0, 255).toByte()
                        }
                    }
                }
            }
            return dst
        }

        // Smooth bilinear interpolation for upscaling
        val xScale = if (dstWidth > 1) (srcW - 1).toFloat() / (dstWidth - 1).toFloat() else 0f
        val yScale = if (dstHeight > 1) (srcH - 1).toFloat() / (dstHeight - 1).toFloat() else 0f
        for (dy in 0 until dstHeight) {
            val fy = dy * yScale
            val y0 = floor(fy).toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceIn(0, srcH - 1)
            val wy1 = fy - y0
            val wy0 = 1f - wy1

            for (dx in 0 until dstWidth) {
                val fx = dx * xScale
                val x0 = floor(fx).toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceIn(0, srcW - 1)
                val wx1 = fx - x0
                val wx0 = 1f - wx1

                val w00 = wx0 * wy0
                val w10 = wx1 * wy0
                val w01 = wx0 * wy1
                val w11 = wx1 * wy1

                val i00 = (y0 * srcW + x0) * 4
                val i10 = (y0 * srcW + x1) * 4
                val i01 = (y1 * srcW + x0) * 4
                val i11 = (y1 * srcW + x1) * 4

                val a00 = (srcBytes[i00 + 3].toInt() and 0xFF) / 255f
                val a10 = (srcBytes[i10 + 3].toInt() and 0xFF) / 255f
                val a01 = (srcBytes[i01 + 3].toInt() and 0xFF) / 255f
                val a11 = (srcBytes[i11 + 3].toInt() and 0xFF) / 255f

                val outANorm = a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11
                val outIdx = (dy * dstWidth + dx) * 4
                if (outANorm > 0.0001f && outIdx + 3 < dstBytes.size) {
                    val rPremul =
                        (srcBytes[i00].toInt() and 0xFF) * a00 * w00 +
                            (srcBytes[i10].toInt() and 0xFF) * a10 * w10 +
                            (srcBytes[i01].toInt() and 0xFF) * a01 * w01 +
                            (srcBytes[i11].toInt() and 0xFF) * a11 * w11
                    val gPremul =
                        (srcBytes[i00 + 1].toInt() and 0xFF) * a00 * w00 +
                            (srcBytes[i10 + 1].toInt() and 0xFF) * a10 * w10 +
                            (srcBytes[i01 + 1].toInt() and 0xFF) * a01 * w01 +
                            (srcBytes[i11 + 1].toInt() and 0xFF) * a11 * w11
                    val bPremul =
                        (srcBytes[i00 + 2].toInt() and 0xFF) * a00 * w00 +
                            (srcBytes[i10 + 2].toInt() and 0xFF) * a10 * w10 +
                            (srcBytes[i01 + 2].toInt() and 0xFF) * a01 * w01 +
                            (srcBytes[i11 + 2].toInt() and 0xFF) * a11 * w11

                    dstBytes[outIdx] = (rPremul / outANorm).roundToInt().coerceIn(0, 255).toByte()
                    dstBytes[outIdx + 1] = (gPremul / outANorm).roundToInt().coerceIn(0, 255).toByte()
                    dstBytes[outIdx + 2] = (bPremul / outANorm).roundToInt().coerceIn(0, 255).toByte()
                    dstBytes[outIdx + 3] = (outANorm * 255f).roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return dst
    }

    private fun parseDimensionsFromHeader(bytes: ByteArray, format: ImageFormat): Pair<Int, Int> {
        return when (format) {
            ImageFormat.PNG -> {
                if (bytes.size >= 24) {
                    val w = readInt32BE(bytes, 16)
                    val h = readInt32BE(bytes, 20)
                    w.coerceAtLeast(1) to h.coerceAtLeast(1)
                } else 1 to 1
            }
            ImageFormat.GIF -> {
                if (bytes.size >= 10) {
                    val w = readUInt16LE(bytes, 6)
                    val h = readUInt16LE(bytes, 8)
                    w.coerceAtLeast(1) to h.coerceAtLeast(1)
                } else 1 to 1
            }
            ImageFormat.BMP -> {
                if (bytes.size >= 26) {
                    val w = readInt32LE(bytes, 18)
                    val h = kotlin.math.abs(readInt32LE(bytes, 22))
                    w.coerceAtLeast(1) to h.coerceAtLeast(1)
                } else 1 to 1
            }
            else -> 64 to 64
        }
    }

    private fun decodeBmp(bytes: ByteArray): ImageBuffer {
        if (bytes.size < 54) return ImageBuffer(1, 1)
        val dataOffset = readInt32LE(bytes, 10)
        val width = readInt32LE(bytes, 18).coerceAtLeast(1)
        val rawHeight = readInt32LE(bytes, 22)
        val topDown = rawHeight < 0
        val height = kotlin.math.abs(rawHeight).coerceAtLeast(1)
        val bitsPerPixel = readUInt16LE(bytes, 28)
        val bytesPerPixel = (bitsPerPixel / 8).coerceAtLeast(3)
        val rowStride = ((width * bitsPerPixel + 31) / 32) * 4

        val buffer = ImageBuffer(width, height, PixelFormat.RGBA_8888)
        for (row in 0 until height) {
            val dstY = if (topDown) row else (height - 1 - row)
            val rowStart = dataOffset + row * rowStride
            for (col in 0 until width) {
                val p = rowStart + col * bytesPerPixel
                if (p + 2 < bytes.size) {
                    val b = bytes[p].toInt() and 0xFF
                    val g = bytes[p + 1].toInt() and 0xFF
                    val r = bytes[p + 2].toInt() and 0xFF
                    val a = if (bytesPerPixel >= 4 && p + 3 < bytes.size) {
                        (bytes[p + 3].toInt() and 0xFF) / 255f
                    } else 1f
                    buffer.setPixel(col, dstY, RgbaColor(r, g, b, a))
                }
            }
        }
        return buffer
    }

    private fun encodeBmp(buffer: ImageBuffer): ByteArray {
        val w = buffer.width
        val h = buffer.height
        val rowBytes = w * 4
        val pixelDataSize = rowBytes * h
        val fileSize = 54 + pixelDataSize
        val out = ByteArray(fileSize)

        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        writeInt32LE(out, 2, fileSize)
        writeInt32LE(out, 10, 54)
        writeInt32LE(out, 14, 40)
        writeInt32LE(out, 18, w)
        writeInt32LE(out, 22, -h)
        out[26] = 1
        out[28] = 32
        writeInt32LE(out, 34, pixelDataSize)

        var offset = 54
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = buffer.getPixel(x, y)
                out[offset++] = c.b.toByte()
                out[offset++] = c.g.toByte()
                out[offset++] = c.r.toByte()
                out[offset++] = c.a.coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    private fun decodePpm(bytes: ByteArray): ImageBuffer {
        val headerText = bytes.take(64).toByteArray().decodeToString()
        val tokens = headerText.split(Regex("\\s+")).filter { it.isNotEmpty() && !it.startsWith("#") }
        if (tokens.size < 4) return ImageBuffer(1, 1)
        val w = tokens[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val h = tokens[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val buf = ImageBuffer(w, h, PixelFormat.RGBA_8888)
        val pixelOffset = bytes.size - (w * h * 3)
        if (pixelOffset >= 0) {
            var p = pixelOffset
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (p + 2 < bytes.size) {
                        buf.setPixel(
                            x,
                            y,
                            RgbaColor(
                                bytes[p].toInt() and 0xFF,
                                bytes[p + 1].toInt() and 0xFF,
                                bytes[p + 2].toInt() and 0xFF,
                                1f
                            )
                        )
                        p += 3
                    }
                }
            }
        }
        return buf
    }

    private fun encodePpm(buffer: ImageBuffer): ByteArray {
        val header = "P6\n${buffer.width} ${buffer.height}\n255\n".encodeToByteArray()
        val out = ByteArray(header.size + buffer.width * buffer.height * 3)
        header.copyInto(out, 0)
        var offset = header.size
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val c = buffer.getPixel(x, y)
                out[offset++] = c.r.toByte()
                out[offset++] = c.g.toByte()
                out[offset++] = c.b.toByte()
            }
        }
        return out
    }

    private fun readUInt16LE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)

    private fun readInt32LE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)

    private fun readInt32BE(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun writeInt32LE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xFF).toByte()
        b[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        b[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        b[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
