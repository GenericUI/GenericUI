package com.nexustools.genericui.animation

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.system.AnimationNativeImpl
import com.nexustools.genericui.nativeimpl.system.DefaultAnimationNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.threading.GenericUiThreading
import kotlin.math.roundToInt

/**
 * Handle for a running [Animator] animation, allowing cancellation or [Disposable] cleanup.
 */
interface AnimationHandle : Disposable {
    val id: Int
    val isRunning: Boolean
    fun cancel()
    override fun dispose() = cancel()
}

/**
 * Parameters describing a timed animation curve and delay.
 */
data class AnimationSpec(
    val durationMs: Int,
    val delayMs: Int = 0,
    val timingFunction: CssTimingFunction = CssTimingFunction.EASE_IN_OUT
)

/**
 * Standard cross-platform GenericUI animation and OS-VBlank frame scheduler.
 *
 * Delegates timed animations and frame synchronization to the active backend's
 * [NativeFactory.createAnimationScheduler] ([AnimationNativeImpl]):
 * - **macOS**: AppKit `NSAnimation` / `NSAnimationContext` + `NSWindow.displayLink` (`CADisplayLink` / `CVDisplayLink`)
 * - **iOS / iPadOS**: UIKit `UIViewPropertyAnimator` + `CADisplayLink` (with 120Hz ProMotion range)
 * - **Android**: `ValueAnimator` (`PathInterpolator`) + `Choreographer`
 * - **Web**: `Element.animate` + `window.requestAnimationFrame`
 * - **Linux GTK4**: `AdwTimedAnimation` + `gtk_widget_add_tick_callback` (`GdkFrameClock`)
 * - **Linux KDE (Qt6)**: `QVariantAnimation` / `QPauseAnimation` (`QUnifiedTimer`) + `QScreen::refreshRate`
 * - **Windows**: `UIAnimation` + `DwmFlush()` / per-monitor VBlank
 * - **JVM Swing**: Monitor `GraphicsDevice` refresh-rate VBlank pacer + `Toolkit.sync()`
 */
object Animator {

    internal val fallbackScheduler: DefaultAnimationNativeImpl = DefaultAnimationNativeImpl()

    private var cachedFactory: NativeFactory? = null
    private var cachedFactoryScheduler: AnimationNativeImpl? = null

    /** Optional override scheduler for tests. */
    internal var customScheduler: AnimationNativeImpl? = null

    internal val activeScheduler: AnimationNativeImpl
        get() {
            customScheduler?.let { return it }
            val factory = GenericUI.activeFactory ?: return fallbackScheduler
            val existing = cachedFactoryScheduler
            if (cachedFactory === factory && existing != null && !existing.isDisposed) {
                return existing
            }
            val created = factory.createAnimationScheduler()
            cachedFactory = factory
            cachedFactoryScheduler = created
            return created
        }

    private inline fun runOnLogicIfNeeded(crossinline block: () -> Unit) {
        if (!GenericUiThreading.isSplitThreadingEnabled || GenericUiThreading.isOnLogicThread()) {
            block()
        } else {
            GenericUiThreading.runOnLogic {
                block()
            }
        }
    }

    /**
     * Runs an eased `0f -> 1f` animation driven by the platform's native animation API or display VBlank.
     */
    fun animate(
        durationMs: Int,
        delayMs: Int = 0,
        timingFunction: CssTimingFunction = CssTimingFunction.EASE_IN_OUT,
        target: NativeImpl<*>? = null,
        onUpdate: (progress: Float) -> Unit,
        onComplete: ((cancelled: Boolean) -> Unit)? = null
    ): AnimationHandle = activeScheduler.startAnimation(
        spec = AnimationSpec(durationMs = durationMs, delayMs = delayMs, timingFunction = timingFunction),
        target = target,
        onUpdate = { p -> runOnLogicIfNeeded { onUpdate(p) } },
        onComplete = onComplete?.let { cb -> { cancelled -> runOnLogicIfNeeded { cb(cancelled) } } }
    )

    /**
     * Interpolates a [Float] from [from] to [to] over [durationMs].
     */
    fun animateFloat(
        from: Float,
        to: Float,
        durationMs: Int,
        delayMs: Int = 0,
        timingFunction: CssTimingFunction = CssTimingFunction.EASE_IN_OUT,
        target: NativeImpl<*>? = null,
        onUpdate: (value: Float) -> Unit,
        onComplete: ((cancelled: Boolean) -> Unit)? = null
    ): AnimationHandle = animate(
        durationMs = durationMs,
        delayMs = delayMs,
        timingFunction = timingFunction,
        target = target,
        onUpdate = { p -> onUpdate(from + (to - from) * p) },
        onComplete = onComplete
    )

    /**
     * Interpolates an [RgbaColor] from [from] to [to] over [durationMs].
     */
    fun animateColor(
        from: RgbaColor,
        to: RgbaColor,
        durationMs: Int,
        delayMs: Int = 0,
        timingFunction: CssTimingFunction = CssTimingFunction.EASE_IN_OUT,
        target: NativeImpl<*>? = null,
        onUpdate: (color: RgbaColor) -> Unit,
        onComplete: ((cancelled: Boolean) -> Unit)? = null
    ): AnimationHandle = animate(
        durationMs = durationMs,
        delayMs = delayMs,
        timingFunction = timingFunction,
        target = target,
        onUpdate = { p ->
            onUpdate(
                RgbaColor(
                    r = (from.r + (to.r - from.r) * p).roundToInt().coerceIn(0, 255),
                    g = (from.g + (to.g - from.g) * p).roundToInt().coerceIn(0, 255),
                    b = (from.b + (to.b - from.b) * p).roundToInt().coerceIn(0, 255),
                    a = (from.a + (to.a - from.a) * p).roundToInt().coerceIn(0, 255)
                )
            )
        },
        onComplete = onComplete
    )

    /**
     * Interpolates a [Rect] from [from] to [to] over [durationMs].
     */
    fun animateRect(
        from: Rect,
        to: Rect,
        durationMs: Int,
        delayMs: Int = 0,
        timingFunction: CssTimingFunction = CssTimingFunction.EASE_IN_OUT,
        target: NativeImpl<*>? = null,
        onUpdate: (rect: Rect) -> Unit,
        onComplete: ((cancelled: Boolean) -> Unit)? = null
    ): AnimationHandle = animate(
        durationMs = durationMs,
        delayMs = delayMs,
        timingFunction = timingFunction,
        target = target,
        onUpdate = { p ->
            onUpdate(
                Rect(
                    x = (from.x + (to.x - from.x) * p).roundToInt(),
                    y = (from.y + (to.y - from.y) * p).roundToInt(),
                    width = (from.width + (to.width - from.width) * p).roundToInt(),
                    height = (from.height + (to.height - from.height) * p).roundToInt()
                )
            )
        },
        onComplete = onComplete
    )

    /**
     * Requests a single callback synchronized to the next OS VBlank / `requestAnimationFrame`.
     */
    fun requestAnimationFrame(callback: (timestampMs: Double) -> Unit): Int =
        activeScheduler.requestAnimationFrame { ts ->
            runOnLogicIfNeeded { callback(ts) }
        }

    fun cancelAnimationFrame(handle: Int) {
        activeScheduler.cancelAnimationFrame(handle)
        fallbackScheduler.cancelAnimationFrame(handle)
    }

    /**
     * Registers a continuous VBlank frame listener synchronized to the display's refresh rate.
     */
    fun watchVBlank(listener: (deltaMs: Double, timestampMs: Double, frameNumber: Long) -> Unit): Int =
        activeScheduler.watchVBlank { deltaMs, timestampMs, frameNumber ->
            runOnLogicIfNeeded { listener(deltaMs, timestampMs, frameNumber) }
        }

    fun unwatchVBlank(watcherId: Int) {
        activeScheduler.unwatchVBlank(watcherId)
        fallbackScheduler.unwatchVBlank(watcherId)
    }

    fun cancelAllAnimations() {
        activeScheduler.cancelAllAnimations()
        fallbackScheduler.cancelAllAnimations()
    }

    fun getRefreshRateHz(): Double =
        activeScheduler.getRefreshRateHz()

    fun stepFrame(deltaMs: Double = 16.667): Int =
        activeScheduler.stepFrame(deltaMs)

    fun advanceTimeBy(deltaMs: Double): Int =
        activeScheduler.advanceTimeBy(deltaMs)

    fun flushAll(): Int =
        activeScheduler.flushAll()
}
