package com.nexustools.genericui.image

/**
 * Memory pixel layout formats supported by [ImageBuffer] and `:extensions:imageprocessing`.
 */
enum class PixelFormat(
    val bytesPerPixel: Int,
    val channels: Int,
    val hasAlpha: Boolean,
    val isFloatingPoint: Boolean = false
) {
    RGBA_8888(bytesPerPixel = 4, channels = 4, hasAlpha = true),
    BGRA_8888(bytesPerPixel = 4, channels = 4, hasAlpha = true),
    RGB_888(bytesPerPixel = 3, channels = 3, hasAlpha = false),
    GRAY_8(bytesPerPixel = 1, channels = 1, hasAlpha = false),
    GRAY_ALPHA_88(bytesPerPixel = 2, channels = 2, hasAlpha = true),
    RGBA_F16(bytesPerPixel = 8, channels = 4, hasAlpha = true, isFloatingPoint = true),
    RGBA_F32(bytesPerPixel = 16, channels = 4, hasAlpha = true, isFloatingPoint = true)
}
