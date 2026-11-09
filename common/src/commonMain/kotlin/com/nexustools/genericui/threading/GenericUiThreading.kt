package com.nexustools.genericui.threading

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Global coordinator for GenericUI's Dual-Thread UI Rendering/Updating vs. Logic/Event-Handler
 * Thread Separation architecture.
 *
 * - On all 7 non-Web backends (`:desktop:windows`, `:desktop:macos`, `:desktop:gtk`, `:desktop:qt`,
 *   `:desktop:swing`, `:mobile:android`, `:mobile:ios`), [isSplitThreadingEnabled] is `true`:
 *   - User event handlers (`onClick`, pointer/key listeners, timers) execute on the **Logic Thread**
 *     ([isOnLogicThread] == `true`, [isOnUiThread] == `false`).
 *   - Native widget updates, layout passes, and rendering execute on the **UI Thread**
 *     ([isOnUiThread] == `true`, [isOnLogicThread] == `false`) via [runOnUi] and [runOnUiSync].
 * - On `:web`, [isSplitThreadingEnabled] is `false` so both UI and Logic operations execute directly
 *   on the browser's single event loop.
 */
object GenericUiThreading {

    internal val fallbackDispatcher: DefaultUiThreadDispatcherNativeImpl =
        DefaultUiThreadDispatcherNativeImpl(isSplitThreadingEnabled = true)

    private var cachedFactory: NativeFactory? = null
    private var cachedFactoryDispatcher: UiThreadDispatcherNativeImpl? = null

    internal var customDispatcherOverride: UiThreadDispatcherNativeImpl? = null

    internal val activeDispatcher: UiThreadDispatcherNativeImpl
        get() {
            customDispatcherOverride?.let { return it }
            val factory = GenericUI.activeFactory ?: return fallbackDispatcher
            val existing = cachedFactoryDispatcher
            if (cachedFactory === factory && existing != null) {
                return existing
            }
            val created = factory.createUiThreadDispatcher()
            cachedFactory = factory
            cachedFactoryDispatcher = created
            return created
        }

    internal fun onFactoryInitialized(factory: NativeFactory) {
        if (cachedFactory !== factory || cachedFactoryDispatcher == null) {
            cachedFactory = factory
            cachedFactoryDispatcher = factory.createUiThreadDispatcher()
        }
    }

    /**
     * `true` when the active backend separates UI rendering/updating and Logic/event-handler execution
     * (`true` on all 7 non-Web backends, `false` on `:web`).
     */
    val isSplitThreadingEnabled: Boolean
        get() = activeDispatcher.isSplitThreadingEnabled

    /**
     * [CoroutineDispatcher] bound to the UI rendering/updating thread.
     */
    val uiDispatcher: CoroutineDispatcher
        get() = activeDispatcher.uiDispatcher

    /**
     * [CoroutineDispatcher] bound to the application Logic/event-handler thread.
     */
    val logicDispatcher: CoroutineDispatcher
        get() = activeDispatcher.logicDispatcher

    /**
     * [CoroutineScope] bound to [logicDispatcher] for launching background logic coroutines.
     */
    val logicScope: CoroutineScope
        get() = activeDispatcher.logicScope

    /**
     * Returns `true` when the current execution frame is on the UI thread.
     */
    fun isOnUiThread(): Boolean = activeDispatcher.isOnUiThread()

    /**
     * Returns `true` when the current execution frame is on the Logic thread.
     */
    fun isOnLogicThread(): Boolean = activeDispatcher.isOnLogicThread()

    /**
     * Schedules [block] to execute on the UI thread.
     * When called from inside a Logic thread callback while [isSplitThreadingEnabled] is `true`,
     * [block] is queued onto the UI task queue and executed on the UI thread.
     */
    fun runOnUi(block: () -> Unit) {
        activeDispatcher.dispatchToUi(block)
    }

    /**
     * Synchronously executes [block] on the UI thread and returns its result [T].
     */
    fun <T> runOnUiSync(block: () -> T): T =
        activeDispatcher.runOnUiBlocking(block)

    /**
     * Executes [block] on the Logic thread and drains any resulting UI updates on the UI thread.
     */
    fun runOnLogic(block: () -> Unit) {
        activeDispatcher.dispatchToLogic(block)
    }

    /**
     * Launches an asynchronous coroutine [block] on [logicScope] ([logicDispatcher]).
     */
    fun runOnLogicAsync(block: suspend CoroutineScope.() -> Unit) {
        activeDispatcher.dispatchToLogicAsync(block)
    }

    /**
     * Flushes all queued Logic and UI tasks until both queues are empty.
     */
    fun flushAllQueues() {
        activeDispatcher.flushAllQueues()
    }
}
