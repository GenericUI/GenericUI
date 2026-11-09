package com.nexustools.genericui.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Backend-specific UI and Logic thread dispatcher contract (`*NativeImpl`).
 *
 * On all 7 non-Web backends (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`,
 * `:desktop:swing`, `:mobile:android`, `:mobile:ios`), [isSplitThreadingEnabled] is `true`:
 * - **UI Thread**: Exclusively performs UI tree mutations, layout passes, style application, and rendering.
 * - **Logic Thread**: Executes user event callbacks (`onClick`, pointer/key handlers, timers) and application logic,
 *   forwarding any UI updates back to the UI Thread via [dispatchToUi] / [runOnUiBlocking].
 *
 * On `:web`, [isSplitThreadingEnabled] is `false` so both UI and Logic operations execute directly on the
 * single browser event loop without Web Worker serialization overhead.
 */
interface UiThreadDispatcherNativeImpl {
    /**
     * `true` when UI rendering/updating and Logic/event-handler execution are separated into distinct threads/queues
     * (all 7 non-Web backends); `false` on `:web`.
     */
    val isSplitThreadingEnabled: Boolean

    /**
     * [CoroutineDispatcher] targeting the UI rendering and native widget mutation thread.
     */
    val uiDispatcher: CoroutineDispatcher

    /**
     * [CoroutineDispatcher] targeting the application logic and event-handler thread.
     */
    val logicDispatcher: CoroutineDispatcher

    /**
     * Lifecycle-managed [CoroutineScope] bound to [logicDispatcher].
     */
    val logicScope: CoroutineScope

    /**
     * Returns `true` when the caller is currently executing on the UI thread context.
     */
    fun isOnUiThread(): Boolean

    /**
     * Returns `true` when the caller is currently executing on the Logic thread context.
     */
    fun isOnLogicThread(): Boolean

    /**
     * Schedules [block] to run on the UI thread (or executes immediately if already on the UI thread
     * and not inside a Logic thread execution frame).
     */
    fun dispatchToUi(block: () -> Unit)

    /**
     * Executes [block] synchronously on the UI thread and returns its result [T].
     */
    fun <T> runOnUiBlocking(block: () -> T): T

    /**
     * Schedules [block] to run on the Logic thread and drains any resulting UI mutations on the UI thread.
     */
    fun dispatchToLogic(block: () -> Unit)

    /**
     * Launches an asynchronous coroutine [block] on [logicScope] ([logicDispatcher]).
     */
    fun dispatchToLogicAsync(block: suspend CoroutineScope.() -> Unit)

    /**
     * Drains all pending UI tasks on the UI thread and returns the number of tasks executed.
     */
    fun drainUiQueue(): Int

    /**
     * Drains all pending Logic tasks on the Logic thread and returns the number of tasks executed.
     */
    fun drainLogicQueue(): Int

    /**
     * Repeatedly drains both the Logic queue and the UI queue until all pending tasks have completed.
     */
    fun flushAllQueues()
}
