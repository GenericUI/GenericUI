package com.nexustools.genericui.image

/**
 * Options controlling how [ImageLoader] decodes and optionally downscales or orientates an image.
 *
 * [antialiasing] defaults to `true` across all platforms so resized images use smooth area-averaging
 * and bilinear/bicubic interpolation instead of nearest-neighbor pixelation.
 */
data class ImageDecodeOptions(
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val preserveAspectRatio: Boolean = true,
    val preferredPixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    val applyExifOrientation: Boolean = true,
    val premultiplyAlpha: Boolean = false,
    val antialiasing: Boolean = true
)
