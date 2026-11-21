@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.threading

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Default multiplatform implementation of [UiThreadDispatcherNativeImpl] providing strict dual-queue
 * separation between Logic/event-handler execution and UI widget mutation/rendering when
 * [isSplitThreadingEnabled] is `true`.
 *
 * While a callback is executing inside [dispatchToLogic] / [drainLogicQueue]:
 * - [isOnLogicThread] returns `true` and [isOnUiThread] returns `false`.
 * - Any UI updates scheduled via [dispatchToUi] (`GenericUiThreading.runOnUi`) are enqueued onto the
 *   UI task queue instead of running inline on the Logic context, and are subsequently drained on the
 *   UI context where [isOnUiThread] is `true` and [isOnLogicThread] is `false`.
 */
open class DefaultUiThreadDispatcherNativeImpl(
    override val isSplitThreadingEnabled: Boolean = true
) : UiThreadDispatcherNativeImpl {

    private val uiTaskQueue: ArrayDeque<() -> Unit> = ArrayDeque()
    private val logicTaskQueue: ArrayDeque<() -> Unit> = ArrayDeque()
    private val uiQueueLock: AtomicBoolean = AtomicBoolean(false)
    private val logicQueueLock: AtomicBoolean = AtomicBoolean(false)
    private val uiDrainActive: AtomicBoolean = AtomicBoolean(false)
    private val logicDrainActive: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    protected var logicExecutionDepth: Int = 0
    @Volatile
    protected var uiExecutionDepth: Int = 0
    @Volatile
    protected var uiBlockingOverrideDepth: Int = 0

    @Volatile
    private var isDrainingLogicQueue: Boolean = false
    @Volatile
    private var isDrainingUiQueue: Boolean = false

    private inline fun <T> withUiQueueLock(block: () -> T): T {
        while (!uiQueueLock.compareAndSet(expectedValue = false, newValue = true)) {
            // Short spin strictly around queue mutations/reads (never user callbacks).
        }
        return try {
            block()
        } finally {
            uiQueueLock.store(false)
        }
    }

    private inline fun <T> withLogicQueueLock(block: () -> T): T {
        while (!logicQueueLock.compareAndSet(expectedValue = false, newValue = true)) {
            // Short spin strictly around queue mutations/reads (never user callbacks).
        }
        return try {
            block()
        } finally {
            logicQueueLock.store(false)
        }
    }

    override val uiDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchToUi { block.run() }
        }
    }

    override val logicDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchToLogic { block.run() }
        }
    }

    override val logicScope: CoroutineScope = CoroutineScope(SupervisorJob() + logicDispatcher)

    override fun isOnUiThread(): Boolean {
        if (!isSplitThreadingEnabled) return true
        if (uiBlockingOverrideDepth > 0) return true
        if (logicExecutionDepth > 0) return false
        return true
    }

    override fun isOnLogicThread(): Boolean {
        if (!isSplitThreadingEnabled) return true
        if (uiBlockingOverrideDepth > 0) return false
        return logicExecutionDepth > 0
    }

    protected fun enqueueUiTask(block: () -> Unit) {
        withUiQueueLock {
            uiTaskQueue.addLast(block)
        }
    }

    protected fun pollUiTask(): (() -> Unit)? =
        withUiQueueLock {
            if (uiTaskQueue.isEmpty()) null else uiTaskQueue.removeFirst()
        }

    protected fun hasPendingUiTasks(): Boolean =
        withUiQueueLock {
            uiTaskQueue.isNotEmpty()
        }

    protected fun enqueueLogicTask(block: () -> Unit) {
        withLogicQueueLock {
            logicTaskQueue.addLast(block)
        }
    }

    protected fun pollLogicTask(): (() -> Unit)? =
        withLogicQueueLock {
            if (logicTaskQueue.isEmpty()) null else logicTaskQueue.removeFirst()
        }

    protected fun hasPendingLogicTasks(): Boolean =
        withLogicQueueLock {
            logicTaskQueue.isNotEmpty()
        }

    override fun dispatchToUi(block: () -> Unit) {
        if (!isSplitThreadingEnabled) {
            block()
            return
        }
        if (isOnLogicThread() || isDrainingUiQueue) {
            enqueueUiTask(block)
            onUiTaskQueuedFromLogic()
            return
        }
        enqueueUiTask(block)
        drainUiQueue()
    }

    protected open fun onUiTaskQueuedFromLogic() {
        // Subclasses with asynchronous OS message loops can post a wakeup message here.
    }

    override fun <T> runOnUiBlocking(block: () -> T): T {
        if (!isSplitThreadingEnabled) {
            return block()
        }
        uiBlockingOverrideDepth++
        uiExecutionDepth++
        return try {
            block()
        } finally {
            uiExecutionDepth--
            uiBlockingOverrideDepth--
        }
    }

    override fun dispatchToLogic(block: () -> Unit) {
        if (!isSplitThreadingEnabled || isOnLogicThread()) {
            block()
            return
        }
        enqueueLogicTask(block)
        if (!isDrainingLogicQueue) {
            val savedUiBlockingDepth = uiBlockingOverrideDepth
            uiBlockingOverrideDepth = 0
            try {
                drainLogicQueue()
            } finally {
                uiBlockingOverrideDepth = savedUiBlockingDepth
            }
            if (logicExecutionDepth == 0 && !isDrainingUiQueue && uiBlockingOverrideDepth == 0) {
                drainUiQueue()
            }
        }
    }

    override fun dispatchToLogicAsync(block: suspend CoroutineScope.() -> Unit) {
        logicScope.launch {
            block()
        }
    }

    override fun drainLogicQueue(): Int {
        if (!isSplitThreadingEnabled) {
            var executed = 0
            while (true) {
                val next = pollLogicTask() ?: break
                next()
                executed++
            }
            return executed
        }
        if (!logicDrainActive.compareAndSet(expectedValue = false, newValue = true)) return 0
        isDrainingLogicQueue = true
        logicExecutionDepth++
        var executed = 0
        try {
            while (executed < MAX_QUEUE_DRAIN_STEPS) {
                val next = pollLogicTask() ?: break
                next()
                executed++
            }
        } finally {
            logicExecutionDepth--
            isDrainingLogicQueue = false
            logicDrainActive.store(false)
        }
        return executed
    }

    override fun drainUiQueue(): Int {
        if (!isSplitThreadingEnabled) {
            var executed = 0
            while (true) {
                val next = pollUiTask() ?: break
                next()
                executed++
            }
            return executed
        }
        if (isOnLogicThread()) return 0
        if (!uiDrainActive.compareAndSet(expectedValue = false, newValue = true)) return 0
        isDrainingUiQueue = true
        uiExecutionDepth++
        var executed = 0
        try {
            while (executed < MAX_QUEUE_DRAIN_STEPS) {
                val next = pollUiTask() ?: break
                next()
                executed++
            }
        } finally {
            uiExecutionDepth--
            isDrainingUiQueue = false
            uiDrainActive.store(false)
        }
        return executed
    }

    override fun flushAllQueues() {
        var pass = 0
        while (pass < MAX_FLUSH_PASSES && (hasPendingLogicTasks() || hasPendingUiTasks())) {
            pass++
            if (hasPendingLogicTasks()) {
                val savedUiBlockingDepth = uiBlockingOverrideDepth
                uiBlockingOverrideDepth = 0
                try {
                    drainLogicQueue()
                } finally {
                    uiBlockingOverrideDepth = savedUiBlockingDepth
                }
            }
            if (hasPendingUiTasks()) {
                val savedLogicDepth = logicExecutionDepth
                logicExecutionDepth = 0
                try {
                    drainUiQueue()
                } finally {
                    logicExecutionDepth = savedLogicDepth
                }
            }
        }
    }

    companion object {
        private const val MAX_QUEUE_DRAIN_STEPS: Int = 100_000
        private const val MAX_FLUSH_PASSES: Int = 64
    }
}
