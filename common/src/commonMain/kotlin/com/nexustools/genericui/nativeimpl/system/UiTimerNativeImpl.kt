package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable

/**
 * Platform-native UI timer scheduler (`setTimeout` / `setInterval` / `clearTimer` / `clearAllTimers`).
 *
 * Implementations run callbacks on the UI thread across all 8 backends (`:windows`, `:macos`, `:gtk`, `:qt`,
 * `:swing`, `:android`, `:ios`, `:web`), while also supporting deterministic time advancement
 * ([advanceTimeBy], [flushAll]) in headless unit tests.
 */
interface UiTimerNativeImpl : Disposable {
    /**
     * Schedules [callback] to execute once after [delayMs] milliseconds and returns a positive timer ID.
     */
    fun setTimeout(delayMs: Int, callback: () -> Unit): Int

    /**
     * Schedules [callback] to execute repeatedly every [intervalMs] milliseconds and returns a positive timer ID.
     */
    fun setInterval(intervalMs: Int, callback: () -> Unit): Int

    /**
     * Cancels a timer previously registered via [setTimeout] or [setInterval].
     */
    fun clearTimer(timerId: Int)

    /**
     * Convenience alias for [clearTimer] matching browser `clearTimeout`.
     */
    fun clearTimeout(timerId: Int) = clearTimer(timerId)

    /**
     * Convenience alias for [clearTimer] matching browser `clearInterval`.
     */
    fun clearInterval(timerId: Int) = clearTimer(timerId)

    /**
     * Cancels all active one-shot and repeating timers managed by this scheduler.
     */
    fun clearAllTimers()

    /**
     * Advances the timer clock by [deltaMs] milliseconds in unit tests and returns the number of timer callbacks invoked.
     */
    fun advanceTimeBy(deltaMs: Int): Int = 0

    /**
     * Executes all pending timers in unit tests and returns the number of timer callbacks invoked.
     */
    fun flushAll(): Int = 0
}
