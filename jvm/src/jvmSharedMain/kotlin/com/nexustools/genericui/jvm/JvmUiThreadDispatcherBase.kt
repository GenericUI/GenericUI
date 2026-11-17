package com.nexustools.genericui.jvm

import com.nexustools.genericui.threading.DefaultUiThreadDispatcherNativeImpl
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Shared JVM and Android (`minSdk >= 24`) implementation of
 * [com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl].
 *
 * Separates UI rendering/widget mutation onto the platform UI thread ([isOnToolkitMainThread] /
 * [postToToolkitUiThread]) and executes event handlers and application logic on a dedicated
 * single-thread daemon [java.util.concurrent.ExecutorService], supporting synchronous UI round-trips
 * from logic callbacks via [runOnUiBlocking] and [CountDownLatch].
 */
abstract class JvmUiThreadDispatcherBase(
    logicThreadName: String = "GenericUI-JVM-Logic"
) : DefaultUiThreadDispatcherNativeImpl(
    isSplitThreadingEnabled = true
) {

    @Volatile
    private var logicWorkerThread: Thread? = null

    @Volatile
    private var pumpingUiThread: Thread? = null

    private val blockingUiRequests = ConcurrentLinkedQueue<() -> Unit>()

    private val logicExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, logicThreadName).apply {
            isDaemon = true
            logicWorkerThread = this
        }
    }

    /**
     * Returns `true` if the current thread is the underlying UI toolkit's main thread
     * (e.g. Swing EDT or Android Main `Looper` thread).
     */
    abstract fun isOnToolkitMainThread(): Boolean

    /**
     * Posts [task] asynchronously onto the underlying UI toolkit's main thread.
     */
    abstract fun postToToolkitUiThread(task: () -> Unit)

    protected open fun canPostToToolkitUiThread(): Boolean = true

    protected open fun runOnToolkitUiThreadSync(action: () -> Unit): Boolean = false

    protected val logicThread: Thread?
        get() = logicWorkerThread

    override fun isOnLogicThread(): Boolean {
        if (uiBlockingOverrideDepth > 0) return false
        return Thread.currentThread() === logicWorkerThread || super.isOnLogicThread()
    }

    override fun isOnUiThread(): Boolean {
        if (uiBlockingOverrideDepth > 0) return true
        if (Thread.currentThread() === logicWorkerThread || super.isOnLogicThread()) return false
        return isOnToolkitMainThread() || super.isOnUiThread()
    }

    override fun onUiTaskQueuedFromLogic() {
        if (canPostToToolkitUiThread() && pumpingUiThread == null && !isOnToolkitMainThread()) {
            postToToolkitUiThread {
                drainUiQueue()
            }
        }
    }

    override fun <T> runOnUiBlocking(block: () -> T): T {
        if (Thread.currentThread() === logicWorkerThread) {
            val activePumper = pumpingUiThread
            if (activePumper != null && activePumper !== Thread.currentThread()) {
                var result: Any? = null
                var failure: Throwable? = null
                val latch = CountDownLatch(1)
                blockingUiRequests.add {
                    try {
                        result = super.runOnUiBlocking(block)
                    } catch (t: Throwable) {
                        failure = t
                    } finally {
                        latch.countDown()
                    }
                }
                latch.await()
                failure?.let { throw it }
                @Suppress("UNCHECKED_CAST")
                return result as T
            }
            if (canPostToToolkitUiThread() && !isOnToolkitMainThread()) {
                var result: Any? = null
                var failure: Throwable? = null
                val handled = runOnToolkitUiThreadSync {
                    try {
                        result = super.runOnUiBlocking(block)
                    } catch (t: Throwable) {
                        failure = t
                    }
                }
                if (handled) {
                    failure?.let { throw it }
                    @Suppress("UNCHECKED_CAST")
                    return result as T
                }
            }
        }
        return super.runOnUiBlocking(block)
    }

    override fun dispatchToLogic(block: () -> Unit) {
        if (Thread.currentThread() === logicWorkerThread) {
            super.dispatchToLogic(block)
            return
        }
        enqueueLogicTask(block)
        val latch = CountDownLatch(1)
        var logicFailure: Throwable? = null
        val previousPumper = pumpingUiThread
        pumpingUiThread = Thread.currentThread()
        try {
            logicExecutor.execute {
                try {
                    drainLogicQueue()
                } catch (t: Throwable) {
                    logicFailure = t
                } finally {
                    latch.countDown()
                }
            }
            while (!latch.await(1, TimeUnit.MILLISECONDS)) {
                while (true) {
                    val req = blockingUiRequests.poll() ?: break
                    req()
                }
            }
            while (true) {
                val req = blockingUiRequests.poll() ?: break
                req()
            }
        } finally {
            pumpingUiThread = previousPumper
        }
        if (logicExecutionDepth == 0 && uiBlockingOverrideDepth == 0) {
            drainUiQueue()
        }
        logicFailure?.let { throw it }
    }
}
