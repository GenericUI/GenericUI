package com.nexustools.genericui.threading

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

    protected var logicExecutionDepth: Int = 0
    protected var uiExecutionDepth: Int = 0
    protected var uiBlockingOverrideDepth: Int = 0

    private var isDrainingLogicQueue: Boolean = false
    private var isDrainingUiQueue: Boolean = false

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
        uiTaskQueue.addLast(block)
    }

    protected fun pollUiTask(): (() -> Unit)? =
        if (uiTaskQueue.isEmpty()) null else uiTaskQueue.removeFirst()

    protected fun hasPendingUiTasks(): Boolean = uiTaskQueue.isNotEmpty()

    protected fun enqueueLogicTask(block: () -> Unit) {
        logicTaskQueue.addLast(block)
    }

    protected fun pollLogicTask(): (() -> Unit)? =
        if (logicTaskQueue.isEmpty()) null else logicTaskQueue.removeFirst()

    protected fun hasPendingLogicTasks(): Boolean = logicTaskQueue.isNotEmpty()

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
        if (isDrainingLogicQueue) return 0
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
        if (isDrainingUiQueue || isOnLogicThread()) return 0
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
        }
        if (hasPendingLogicTasks() && !isDrainingLogicQueue && logicExecutionDepth == 0) {
            drainLogicQueue()
            if (hasPendingUiTasks() && !isDrainingUiQueue) {
                executed += drainUiQueue()
            }
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
