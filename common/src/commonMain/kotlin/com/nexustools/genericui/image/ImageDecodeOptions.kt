package com.nexustools.genericui.image

/**
 * Options controlling how [ImageLoader] decodes and optionally downscales or orientates an image.
 */
data class ImageDecodeOptions(
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val preserveAspectRatio: Boolean = true,
    val preferredPixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    val applyExifOrientation: Boolean = true,
    val premultiplyAlpha: Boolean = false
)
