@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSThread
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_get_specific
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_set_specific
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.usleep
import kotlin.coroutines.CoroutineContext

class AppleUiThreadDispatcherNativeImpl(
    private val isSplitDispatchActive: () -> Boolean = { true },
    private val onUiDrainComplete: (() -> Unit)? = null
) : UiThreadDispatcherNativeImpl {
    private val lock = AppleReentrantLock()
    private val uiPendingTasks = ArrayDeque<() -> Unit>()
    private var inFlightLogicCount = 0
    private var drainedLogicCounter = 0

    // Unique pointer key attached to the serial logic GCD queue for O(1) thread identity checks.
    private val logicSpecificStorage: IntVar = nativeHeap.alloc()
    private val logicSpecificKey: COpaquePointer = logicSpecificStorage.rawPtr.let {
        @Suppress("CAST_NEVER_SUCCEEDS")
        logicSpecificStorage.ptr
    }

    private val logicQueue = dispatch_queue_create("com.nexustools.genericui.apple.logic", null).also { queue ->
        dispatch_queue_set_specific(queue, logicSpecificKey, logicSpecificKey, null)
    }

    override val isSplitThreadingEnabled: Boolean
        get() = isSplitDispatchActive()

    override val uiDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean {
            return !isOnUiThread()
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchToUi { block.run() }
        }
    }

    override val logicDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean {
            return !isOnLogicThread()
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchToLogic { block.run() }
        }
    }

    override val logicScope: CoroutineScope = CoroutineScope(SupervisorJob() + logicDispatcher)

    override fun isOnUiThread(): Boolean {
        if (!isSplitThreadingEnabled) return true
        return NSThread.isMainThread
    }

    override fun isOnLogicThread(): Boolean {
        if (!isSplitThreadingEnabled) return true
        return dispatch_get_specific(logicSpecificKey) == logicSpecificKey
    }

    override fun dispatchToUi(block: () -> Unit) {
        if (!isSplitThreadingEnabled && NSThread.isMainThread) {
            block()
            onUiDrainComplete?.invoke()
            return
        }
        lock.withLock {
            uiPendingTasks.addLast(block)
        }
        dispatch_async(dispatch_get_main_queue()) {
            drainUiQueue()
        }
    }

    override fun <T> runOnUiBlocking(block: () -> T): T {
        if (NSThread.isMainThread || !isSplitThreadingEnabled) {
            return block()
        }

        val sem = dispatch_semaphore_create(0)
        var resultBox: Result<T>? = null
        dispatchToUi {
            try {
                resultBox = Result.success(block())
            } catch (t: Throwable) {
                resultBox = Result.failure(t)
            } finally {
                sem?.let { dispatch_semaphore_signal(it) }
            }
        }
        if (sem != null) {
            dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER)
        }
        return resultBox!!.getOrThrow()
    }

    override fun dispatchToLogic(block: () -> Unit) {
        if (!isSplitThreadingEnabled) {
            block()
            return
        }
        lock.withLock {
            inFlightLogicCount++
        }
        dispatch_async(logicQueue) {
            try {
                block()
            } finally {
                lock.withLock {
                    if (inFlightLogicCount > 0) inFlightLogicCount--
                    drainedLogicCounter++
                }
            }
        }
    }

    override fun dispatchToLogicAsync(block: suspend CoroutineScope.() -> Unit) {
        if (!isSplitThreadingEnabled) {
            logicScope.launch {
                block()
            }
            return
        }
        lock.withLock {
            inFlightLogicCount++
        }
        logicScope.launch {
            try {
                block()
            } finally {
                lock.withLock {
                    if (inFlightLogicCount > 0) inFlightLogicCount--
                    drainedLogicCounter++
                }
            }
        }
    }

    override fun drainUiQueue(): Int {
        if (!NSThread.isMainThread && isSplitThreadingEnabled) {
            // Drain on main thread synchronously only when safe; otherwise leave for main queue.
            return runOnUiBlocking { drainUiQueueOnMain() }
        }
        return drainUiQueueOnMain()
    }

    private fun drainUiQueueOnMain(): Int {
        var executed = 0
        while (true) {
            val next = lock.withLock {
                if (uiPendingTasks.isEmpty()) null else uiPendingTasks.removeFirst()
            } ?: break
            next.invoke()
            executed++
        }
        if (executed > 0) {
            onUiDrainComplete?.invoke()
        }
        return executed
    }

    override fun drainLogicQueue(): Int {
        if (isOnLogicThread()) {
            return 0
        }
        val startCount = lock.withLock { drainedLogicCounter }
        while (true) {
            if (NSThread.isMainThread) {
                drainUiQueueOnMain()
            }
            val remaining = lock.withLock { inFlightLogicCount }
            if (remaining <= 0) break
            usleep(50u)
        }
        val endCount = lock.withLock { drainedLogicCounter }
        return (endCount - startCount).coerceAtLeast(0)
    }

    override fun flushAllQueues() {
        while (true) {
            val drainedUi = if (NSThread.isMainThread) drainUiQueueOnMain() else 0
            val pending = lock.withLock { uiPendingTasks.size + inFlightLogicCount }
            if (pending == 0) {
                if (drainedUi == 0) break
                continue
            }
            usleep(50u)
        }
    }
}
