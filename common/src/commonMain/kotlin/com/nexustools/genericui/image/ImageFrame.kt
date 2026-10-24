package com.nexustools.genericui.image

/**
 * Single frame of a decoded static or animated image ([AnimatedImage]).
 */
data class ImageFrame(
    val frameIndex: Int,
    val buffer: ImageBuffer,
    val durationMs: Int = 100,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)
