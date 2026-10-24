package com.nexustools.genericui.image

/**
 * Container / codec formats supported by the base GenericUI [ImageLoader] API.
 */
enum class ImageFormat(
    val mimeType: String,
    val defaultExtension: String
) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif"),
    BMP("image/bmp", "bmp"),
    ICO("image/x-icon", "ico"),
    TIFF("image/tiff", "tiff"),
    SVG("image/svg+xml", "svg"),
    PPM("image/x-portable-pixmap", "ppm"),
    RAW_RGBA("application/octet-stream", "raw"),
    UNKNOWN("application/octet-stream", "bin");

    companion object {
        /**
         * Detects the [ImageFormat] from the magic header bytes of an encoded stream.
         */
        fun detectFromHeader(bytes: ByteArray): ImageFormat {
            if (bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            ) {
                return PNG
            }
            if (bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()
            ) {
                return JPEG
            }
            if (bytes.size >= 6 &&
                bytes[0] == 'G'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == '8'.code.toByte()
            ) {
                return GIF
            }
            if (bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte()
            ) {
                return WEBP
            }
            if (bytes.size >= 2 &&
                bytes[0] == 'B'.code.toByte() &&
                bytes[1] == 'M'.code.toByte()
            ) {
                return BMP
            }
            if (bytes.size >= 4 &&
                bytes[0] == 0x00.toByte() &&
                bytes[1] == 0x00.toByte() &&
                bytes[2] == 0x01.toByte() &&
                bytes[3] == 0x00.toByte()
            ) {
                return ICO
            }
            if (bytes.size >= 4 &&
                ((bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 42.toByte()) ||
                    (bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() && bytes[3] == 42.toByte()))
            ) {
                return TIFF
            }
            if (bytes.size >= 2 &&
                bytes[0] == 'P'.code.toByte() &&
                (bytes[1] == '6'.code.toByte() || bytes[1] == '3'.code.toByte())
            ) {
                return PPM
            }
            val headStr = bytes.take(128).toByteArray().decodeToString().trimStart()
            if (headStr.startsWith("<svg", ignoreCase = true) || headStr.contains("<svg", ignoreCase = true)) {
                return SVG
            }
            return UNKNOWN
        }

        fun fromExtension(extensionOrPath: String): ImageFormat {
            val ext = extensionOrPath.substringAfterLast('.', extensionOrPath).lowercase().trim()
            return when (ext) {
                "png", "apng" -> PNG
                "jpg", "jpeg", "jpe", "jfif" -> JPEG
                "webp" -> WEBP
                "gif" -> GIF
                "bmp", "dib" -> BMP
                "ico", "cur" -> ICO
                "tif", "tiff" -> TIFF
                "svg", "svgz" -> SVG
                "ppm", "pgm", "pbm", "pnm" -> PPM
                "raw", "rgba" -> RAW_RGBA
                else -> UNKNOWN
            }
        }
    }
}
