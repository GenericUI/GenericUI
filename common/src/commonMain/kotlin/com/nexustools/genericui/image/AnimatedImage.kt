package com.nexustools.genericui.image

/**
 * Multi-frame decoded animated image (e.g., animated GIF, animated WebP, APNG).
 */
class AnimatedImage(
    val width: Int,
    val height: Int,
    val frames: List<ImageFrame>,
    val loopCount: Int = 0
) {
    val totalDurationMs: Long = frames.sumOf { it.durationMs.coerceAtLeast(10).toLong() }

    /**
     * Returns the [ImageFrame] that should be displayed at [elapsedMs] into playback.
     */
    fun frameAtTimeMs(elapsedMs: Long): ImageFrame {
        if (frames.isEmpty()) {
            return ImageFrame(0, ImageBuffer(width.coerceAtLeast(1), height.coerceAtLeast(1)))
        }
        if (frames.size == 1 || totalDurationMs <= 0L) return frames.first()
        val cycleTime = elapsedMs.coerceAtLeast(0L) % totalDurationMs
        var acc = 0L
        for (frame in frames) {
            acc += frame.durationMs.coerceAtLeast(10)
            if (cycleTime < acc) return frame
        }
        return frames.last()
    }
}
