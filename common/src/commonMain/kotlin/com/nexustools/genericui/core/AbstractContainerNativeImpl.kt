package com.nexustools.genericui.core

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.transition.ContainerAnimationFrameState

/**
 * Abstract container native implementation (`AbstractContainerNativeImpl<W>`) supporting child widget attachment,
 * insertion, removal, clearing, and window-manager-style pre-rendered buffer / hardware layer caching during animations.
 */
interface AbstractContainerNativeImpl<out W> : NativeImpl<W> {
    fun addChildNative(childImpl: NativeImpl<*>, index: Int = -1)
    fun removeChildNative(childImpl: NativeImpl<*>)
    fun clearChildrenNative()

    /**
     * Enables or disables native hardware layer rasterization caching (`will-change` on Web,
     * `CALayer.shouldRasterize` on macOS/iOS, `LAYER_TYPE_HARDWARE` on Android) during transitions.
     */
    fun enableHardwareLayerCache(enabled: Boolean) {}

    /**
     * Captures the container's current rendered contents into an offscreen [ImageBuffer] if supported
     * by the native backend; returns `null` to use GenericUI's container buffer rasterizer.
     */
    fun captureOffscreenBuffer(width: Int, height: Int): ImageBuffer? = null

    /**
     * Presents a window-manager-style animated [buffer] with the interpolated [frameState]
     * without re-rendering interior child widgets.
     */
    fun presentAnimatedBuffer(buffer: ImageBuffer, frameState: ContainerAnimationFrameState) {}

    /**
     * Releases any temporary animated buffer presentation overlay when a transition completes.
     */
    fun releaseAnimatedBuffer() {}
}
