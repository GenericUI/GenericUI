package com.nexustools.genericui.image

/**
 * Options controlling how [ImageLoader.encode] serializes an [ImageBuffer] to an encoded format.
 */
data class ImageEncodeOptions(
    val format: ImageFormat = ImageFormat.PNG,
    val quality: Int = 90,
    val compressionLevel: Int = 6
)
