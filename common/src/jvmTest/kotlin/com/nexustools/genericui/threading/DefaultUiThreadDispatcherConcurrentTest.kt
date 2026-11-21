package com.nexustools.genericui.threading

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultUiThreadDispatcherConcurrentTest {

    private class ExposedDispatcher : DefaultUiThreadDispatcherNativeImpl(isSplitThreadingEnabled = true) {
        fun enqueueUi(block: () -> Unit) = enqueueUiTask(block)
        fun pollUi(): (() -> Unit)? = pollUiTask()
        fun hasUi(): Boolean = hasPendingUiTasks()
        fun enqueueLogic(block: () -> Unit) = enqueueLogicTask(block)
        fun pollLogic(): (() -> Unit)? = pollLogicTask()
        fun hasLogic(): Boolean = hasPendingLogicTasks()
    }

    @Test
    fun concurrentQueueMutationsAndReadsDoNotCorruptArrayDeque() {
        val dispatcher = ExposedDispatcher()
        val workerCount = 8
        val tasksPerWorker = 1000
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(workerCount)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val uiExecuted = AtomicInteger(0)
        val logicExecuted = AtomicInteger(0)

        for (w in 0 until workerCount) {
            Thread({
                try {
                    startGate.await()
                    for (i in 0 until tasksPerWorker) {
                        if (w % 2 == 0) {
                            dispatcher.enqueueUi { uiExecuted.incrementAndGet() }
                            dispatcher.hasUi()
                            if (i % 3 == 0) {
                                dispatcher.pollUi()?.invoke()
                            }
                        } else {
                            dispatcher.enqueueLogic { logicExecuted.incrementAndGet() }
                            dispatcher.hasLogic()
                            if (i % 3 == 0) {
                                dispatcher.pollLogic()?.invoke()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    failures.add(t)
                } finally {
                    doneGate.countDown()
                }
            }, "DispatcherQueueStress-$w").start()
        }

        startGate.countDown()
        assertTrue(doneGate.await(15, TimeUnit.SECONDS))
        assertNull(failures.peek(), "Expected zero queue corruption exceptions, got: ${failures.peek()}")

        dispatcher.flushAllQueues()
        assertFalse(dispatcher.hasUi())
        assertFalse(dispatcher.hasLogic())
        val expectedPerQueue = (workerCount / 2) * tasksPerWorker
        assertEquals(expectedPerQueue, uiExecuted.get())
        assertEquals(expectedPerQueue, logicExecuted.get())
    }
}
