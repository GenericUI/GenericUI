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
 * - `:web`: Browser `createImageBitmap` / `HTMLImageElement` + offscreen `<canvas>` `getImageData` / `toDataURL`.
 * - `:macos` / `:ios`: Apple `ImageIO` (`CGImageSource` / `CGImageDestination`).
 * - `:windows` / `:gtk` / `:qt` (`:desktop`): Native OS loaders (`WIC`, `GdkPixbuf`, `QImage`) + direct `libjpeg` (`libjpeg-turbo`), `libpng`, `libwebp`, and `giflib` codecs.
 */
interface ImageCodecNativeImpl : Disposable {
    val codecBackendName: String

    fun probeMetadata(source: ImageSource): ImageMetadata?

    fun decode(source: ImageSource, options: ImageDecodeOptions): ImageBuffer?

    fun decodeAnimated(source: ImageSource, options: ImageDecodeOptions): AnimatedImage?

    fun encode(buffer: ImageBuffer, options: ImageEncodeOptions): ByteArray?
}
