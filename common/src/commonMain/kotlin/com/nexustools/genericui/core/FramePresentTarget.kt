package com.nexustools.genericui.core

/**
 * Implemented by components that present sequential visual frames (such as 2D/3D canvases,
 * media surfaces, or procedural renderers) so attached layers, overlays, or synchronizers
 * can execute immediate pre-present updates before the frame is composited onscreen.
 */
interface FramePresentTarget {
    val presentedFrameCount: Long
    fun addBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit)
    fun removeBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit)
}
