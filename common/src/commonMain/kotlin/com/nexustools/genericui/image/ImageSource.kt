package com.nexustools.genericui.image

/**
 * Describes the origin of an image to be loaded by [ImageLoader] or displayed in an `ImageView`.
 */
class ImageSource private constructor(
    val uriOrPath: String?,
    val encodedBytes: ByteArray?,
    val memoryBuffer: ImageBuffer?,
    val formatHint: ImageFormat = ImageFormat.UNKNOWN,
    val isResource: Boolean = false
) {
    companion object {
        fun fromFile(filePath: String): ImageSource =
            ImageSource(
                uriOrPath = filePath,
                encodedBytes = null,
                memoryBuffer = null,
                formatHint = ImageFormat.fromExtension(filePath)
            )

        fun fromUrl(url: String): ImageSource =
            ImageSource(
                uriOrPath = url,
                encodedBytes = null,
                memoryBuffer = null,
                formatHint = ImageFormat.fromExtension(url.substringBefore('?'))
            )

        fun fromResource(resourcePath: String): ImageSource =
            ImageSource(
                uriOrPath = resourcePath,
                encodedBytes = null,
                memoryBuffer = null,
                formatHint = ImageFormat.fromExtension(resourcePath),
                isResource = true
            )

        fun fromBytes(bytes: ByteArray, formatHint: ImageFormat = ImageFormat.detectFromHeader(bytes)): ImageSource =
            ImageSource(
                uriOrPath = null,
                encodedBytes = bytes,
                memoryBuffer = null,
                formatHint = formatHint
            )

        fun fromBuffer(buffer: ImageBuffer): ImageSource =
            ImageSource(
                uriOrPath = null,
                encodedBytes = null,
                memoryBuffer = buffer,
                formatHint = ImageFormat.RAW_RGBA
            )
    }
}
