package com.nexustools.genericui.image

/**
 * Decoded or header-probed metadata for an image asset.
 */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val pixelFormat: PixelFormat = PixelFormat.RGBA_8888,
    val colorSpace: ImageColorSpace = ImageColorSpace.SRGB,
    val bitDepthPerChannel: Int = 8,
    val hasAlpha: Boolean = true,
    val isAnimated: Boolean = false,
    val frameCount: Int = 1,
    val exifOrientation: Int = 1,
    val dpiX: Float = 72f,
    val dpiY: Float = 72f,
    val attributes: Map<String, String> = emptyMap()
)
