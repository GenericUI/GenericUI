package com.nexustools.genericui.nativeimpl.image

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.image.AnimatedImage
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.ImageSource

/**
 * Native / platform image loader & codec contract used by [com.nexustools.genericui.image.ImageLoader].
 *
 * Platform implementations:
 * - `:swing` (Java): `javax.imageio.ImageIO` & `BufferedImage`.
 * - `:android`: `android.graphics.BitmapFactory`, `android.graphics.ImageDecoder`, and `Bitmap.compress`.
 * - `:web`: Browser `createImageBitmap` / `HTMLImageElement` + offscreen `<canvas>` `getImageData` / `toDataURL`
 *   ([usesBrowserManagedImageLoading] is `true` on `:web` so the browser handles image loading and HTTP caching directly).
 * - `:macos` / `:ios`: Apple `ImageIO` (`CGImageSource` / `CGImageDestination`).
 * - `:windows` / `:gtk` / `:qt` (`:desktop`): Native OS loaders (`WIC`, `GdkPixbuf`, `QImage`) + direct `libjpeg` (`libjpeg-turbo`), `libpng`, `libwebp`, and `giflib` codecs.
 */
interface ImageCodecNativeImpl : Disposable {
    val codecBackendName: String

    /**
     * When `true` (on `:web`), the browser manages image loading and HTTP caching directly, so
     * [com.nexustools.genericui.image.ImageLoader] and [com.nexustools.genericui.image.cache.ImageCacheManager]
     * bypass `CacheDirectory` disk caching.
     */
    val usesBrowserManagedImageLoading: Boolean
        get() = false

    fun probeMetadata(source: ImageSource): ImageMetadata?

    fun decode(source: ImageSource, options: ImageDecodeOptions): ImageBuffer?

    /**
     * Asynchronously loads and decodes [source] with [options], invoking [onLoaded] or [onError].
     *
     * Returns `true` if this codec handled the asynchronous load natively (for example, `:web`
     * creating an invisible `HTMLImageElement`, setting `src`, and watching `onload` / checking `complete`),
     * or `false` to fall back to [com.nexustools.genericui.image.ImageLoader]'s coroutine pipeline.
     */
    fun decodeAsync(
        source: ImageSource,
        options: ImageDecodeOptions,
        onLoaded: (ImageBuffer) -> Unit,
        onError: (Throwable) -> Unit
    ): Boolean = false

    fun decodeAnimated(source: ImageSource, options: ImageDecodeOptions): AnimatedImage?

    fun encode(buffer: ImageBuffer, options: ImageEncodeOptions): ByteArray?
}
