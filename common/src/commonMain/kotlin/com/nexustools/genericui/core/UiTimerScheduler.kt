package com.nexustools.genericui.core

import com.nexustools.genericui.nativeimpl.system.DefaultUiTimerNativeImpl
import com.nexustools.genericui.nativeimpl.system.UiTimerNativeImpl

/**
 * Global UI timer scheduler delegating [setTimeout], [setInterval], [clearTimer], and [clearAllTimers]
 * to the active [NativeFactory.createUiTimerScheduler] implementation (or [fallbackScheduler] when no
 * platform factory has been initialized).
 */
object UiTimerScheduler {

    val fallbackScheduler: DefaultUiTimerNativeImpl = DefaultUiTimerNativeImpl()

    private var cachedFactory: NativeFactory? = null
    private var cachedFactoryScheduler: UiTimerNativeImpl? = null

    /** Optional override scheduler for tests. */
    var customScheduler: UiTimerNativeImpl? = null

    /**
     * Resolves the active [UiTimerNativeImpl] for the current [GenericUI.activeFactory].
     */
    val activeScheduler: UiTimerNativeImpl
        get() {
            customScheduler?.let { return it }
            val factory = GenericUI.activeFactory ?: return fallbackScheduler
            val existing = cachedFactoryScheduler
            if (cachedFactory === factory && existing != null && !existing.isDisposed) {
                return existing
            }
            val created = factory.createUiTimerScheduler()
            cachedFactory = factory
            cachedFactoryScheduler = created
            return created
        }

    fun setTimeout(delayMs: Int, callback: () -> Unit): Int =
        activeScheduler.setTimeout(delayMs, callback)

    fun setInterval(intervalMs: Int, callback: () -> Unit): Int =
        activeScheduler.setInterval(intervalMs, callback)

    fun clearTimer(timerId: Int) {
        activeScheduler.clearTimer(timerId)
        fallbackScheduler.clearTimer(timerId)
    }

    fun clearTimeout(timerId: Int) = clearTimer(timerId)

    fun clearInterval(timerId: Int) = clearTimer(timerId)

    fun clearAllTimers() {
        activeScheduler.clearAllTimers()
        fallbackScheduler.clearAllTimers()
    }

    /**
     * Advances timers by [deltaMs] milliseconds in unit tests and returns the number of timer callbacks invoked.
     */
    fun advanceTimeBy(deltaMs: Int): Int =
        activeScheduler.advanceTimeBy(deltaMs)

    /**
     * Flushes pending timers in unit tests and returns the number of timer callbacks invoked.
     */
    fun flushAll(): Int =
        activeScheduler.flushAll()
}
