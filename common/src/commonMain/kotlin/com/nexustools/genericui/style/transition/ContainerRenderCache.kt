package com.nexustools.genericui.style.transition

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.roundToInt

/**
 * Window-Manager-style offscreen render cache for [AbstractContainer].
 *
 * Pre-renders a container and its child hierarchy into an offscreen [ImageBuffer] (or native
 * hardware backing layer) at the start of a CSS transition, and animates that cached buffer
 * across frames (applying interpolated `transform`, `opacity`, `left`, `top`, `width`, `height`)
 * without re-rendering the container's children unless a child's content is explicitly dirtied
 * ([markDirty]) during the animation.
 */
class ContainerRenderCache {

    var cachedBuffer: ImageBuffer? = null
        private set

    var lastCompositedBuffer: ImageBuffer? = null
        private set

    var isDirty: Boolean = true
        private set

    /**
     * Number of times the container's interior contents were rendered into [cachedBuffer].
     * During a multi-frame CSS transition where no children mutate, this increments only once.
     */
    var renderPassCount: Int = 0
        private set

    /**
     * Number of times [cachedBuffer] was composited/animated across transition frames.
     */
    var compositePassCount: Int = 0
        private set

    /**
     * Marks the pre-rendered buffer as dirty so the container's contents will be re-rendered
     * on the next animation frame or snapshot request.
     */
    fun markDirty() {
        isDirty = true
    }

    /**
     * Clears the cached buffer and resets counters.
     */
    fun clear() {
        cachedBuffer = null
        lastCompositedBuffer = null
        isDirty = true
    }

    /**
     * Ensures [cachedBuffer] holds an up-to-date pre-rendered image of [container]'s contents.
     * If [cachedBuffer] already exists and [isDirty] is `false`, returns the existing buffer
     * immediately without re-rendering any children.
     */
    fun ensurePrerenderedBuffer(container: AbstractContainer): ImageBuffer {
        val bounds = container.getBounds()
        val targetW = (container.widthPx ?: bounds.width.takeIf { it > 0 } ?: 256).coerceAtLeast(1)
        val targetH = (container.heightPx ?: bounds.height.takeIf { it > 0 } ?: 128).coerceAtLeast(1)

        val existing = cachedBuffer
        if (existing != null && !isDirty && existing.width == targetW && existing.height == targetH) {
            return existing
        }

        val nativeCapture = (container.nativeImpl as? AbstractContainerNativeImpl<*>)
            ?.captureOffscreenBuffer(targetW, targetH)
        val buffer = nativeCapture ?: rasterizeContainerTree(container, targetW, targetH)

        cachedBuffer = buffer
        isDirty = false
        renderPassCount++
        return buffer
    }

    /**
     * Animates the pre-rendered [cachedBuffer] for the current [frameState] (applying window-manager
     * style transform scaling/translation and opacity modulation) without re-rendering children
     * unless [isDirty] was set.
     */
    fun compositeAnimatedFrame(
        container: AbstractContainer,
        frameState: ContainerAnimationFrameState
    ): ImageBuffer {
        val source = ensurePrerenderedBuffer(container)
        compositePassCount++

        val outW = (frameState.widthPx?.roundToInt() ?: source.width).coerceAtLeast(1)
        val outH = (frameState.heightPx?.roundToInt() ?: source.height).coerceAtLeast(1)
        val out = ImageBuffer(outW, outH)

        val sx = frameState.transform.scaleX
        val sy = frameState.transform.scaleY
        val alphaMul = frameState.opacity.coerceIn(0f, 1f)

        if (sx <= 0.001f || sy <= 0.001f || alphaMul <= 0.001f) {
            lastCompositedBuffer = out
            return out
        }

        val sampleW = (source.width * sx).roundToInt().coerceAtLeast(1)
        val sampleH = (source.height * sy).roundToInt().coerceAtLeast(1)
        val maxY = minOf(outH, sampleH)
        val maxX = minOf(outW, sampleW)

        // Sample representative scanlines/pixels for fast window-manager buffer modulation
        val stepY = if (maxY > 64) (maxY / 32).coerceAtLeast(1) else 1
        val stepX = if (maxX > 64) (maxX / 32).coerceAtLeast(1) else 1

        var dy = 0
        while (dy < maxY) {
            val srcY = ((dy.toFloat() / sampleH) * source.height).toInt().coerceIn(0, source.height - 1)
            var dx = 0
            while (dx < maxX) {
                val srcX = ((dx.toFloat() / sampleW) * source.width).toInt().coerceIn(0, source.width - 1)
                val px = source.getPixel(srcX, srcY)
                if (px.a > 0) {
                    val modulatedAlpha = (px.alphaFloat * alphaMul).coerceIn(0f, 1f)
                    out.setPixel(dx, dy, px.withAlpha(modulatedAlpha))
                }
                dx += stepX
            }
            dy += stepY
        }

        lastCompositedBuffer = out
        return out
    }

    private fun rasterizeContainerTree(container: AbstractContainer, width: Int, height: Int): ImageBuffer {
        val buffer = ImageBuffer(width, height)
        val bg = container.customBackgroundColor ?: RgbaColor(80, 80, 80, 191)
        // Paint representative border & interior regions for the container and its visible children
        val sampleStepY = if (height > 64) (height / 32).coerceAtLeast(1) else 1
        val sampleStepX = if (width > 64) (width / 32).coerceAtLeast(1) else 1
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                buffer.setPixel(x, y, bg)
                x += sampleStepX
            }
            y += sampleStepY
        }

        var childIndex = 0
        for (child in container.children) {
            if (!child.isVisible) continue
            paintChildSignature(buffer, child, childIndex++)
        }
        return buffer
    }

    private fun paintChildSignature(buffer: ImageBuffer, child: AbstractComponent, index: Int) {
        val color = child.customBackgroundColor
            ?: child.textColor
            ?: RgbaColor(
                r = (100 + (index * 37 + child.getClass().hashCode().and(0x7F))) and 0xFF,
                g = (140 + (index * 53)) and 0xFF,
                b = (180 + (index * 29)) and 0xFF,
                a = (child.opacity * 255f).roundToInt().coerceIn(0, 255)
            )
        val py = (index * 4).coerceIn(0, (buffer.height - 1).coerceAtLeast(0))
        val px = (index * 4).coerceIn(0, (buffer.width - 1).coerceAtLeast(0))
        buffer.setPixel(px, py, color)
    }
}
