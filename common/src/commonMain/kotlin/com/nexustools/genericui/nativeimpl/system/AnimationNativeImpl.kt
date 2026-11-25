package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.animation.AnimationHandle
import com.nexustools.genericui.animation.AnimationSpec
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.NativeImpl

/**
 * Platform-native animation and display VBlank scheduler powering [com.nexustools.genericui.animation.Animator]
 * across all 9 backends (`:windows`, `:macos`, `:gtk`, `:kde`, `:swing`, `:android`, `:ios`, `:ipados`, `:web`),
 * with deterministic virtual-clock stepping ([stepFrame], [advanceTimeBy], [flushAll]) for headless unit tests.
 */
interface AnimationNativeImpl : Disposable {

    /**
     * Starts a timed/eased animation described by [spec], invoking [onUpdate] with eased progress
     * in `[0f..1f]` on each native frame and [onComplete] (`cancelled == true` if cancelled early)
     * when finished.
     */
    fun startAnimation(
        spec: AnimationSpec,
        target: NativeImpl<*>? = null,
        onUpdate: (progress: Float) -> Unit,
        onComplete: ((cancelled: Boolean) -> Unit)? = null
    ): AnimationHandle

    /**
     * Schedules a single callback on the next OS display VBlank / `requestAnimationFrame`.
     */
    fun requestAnimationFrame(callback: (timestampMs: Double) -> Unit): Int

    /**
     * Cancels a pending callback registered via [requestAnimationFrame].
     */
    fun cancelAnimationFrame(handle: Int)

    /**
     * Subscribes a continuous display-synced VBlank listener receiving `(deltaMs, timestampMs, frameNumber)`.
     */
    fun watchVBlank(listener: (deltaMs: Double, timestampMs: Double, frameNumber: Long) -> Unit): Int

    /**
     * Removes a continuous VBlank listener registered via [watchVBlank].
     */
    fun unwatchVBlank(watcherId: Int)

    /**
     * Cancels all active timed animations, pending [requestAnimationFrame] callbacks, and [watchVBlank] listeners.
     */
    fun cancelAllAnimations()

    /**
     * Returns the active display refresh rate in Hz (e.g., `60.0`, `120.0`, `144.0`), or `0.0` when headless/unknown.
     */
    fun getRefreshRateHz(): Double = 0.0

    /**
     * Steps a single frame of [deltaMs] milliseconds in unit tests and returns total callbacks dispatched.
     */
    fun stepFrame(deltaMs: Double = 16.667): Int = 0

    /**
     * Advances the virtual animation clock by [deltaMs] milliseconds in unit tests.
     */
    fun advanceTimeBy(deltaMs: Double): Int = 0

    /**
     * Advances and completes all currently active finite animations and one-shot frame callbacks in unit tests.
     */
    fun flushAll(): Int = 0
}
