package com.nexustools.genericui.apple

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleThreadingTimerAndSystemTest {

    @Test
    fun uiThreadDispatcherSeparatesUiAndLogicQueuesSafely() {
        runBlocking {
        var drainCount = 0
        val dispatcher = AppleUiThreadDispatcherNativeImpl(
            isSplitDispatchActive = { true },
            onUiDrainComplete = { drainCount++ }
        )

        var logicWasLogicThread = false
        var logicWasNotUiThread = false
        var blockingUiRanOnUiThread = false
        var asyncDispatchedRan = false

        dispatcher.dispatchToLogic {
            logicWasLogicThread = dispatcher.isOnLogicThread()
            logicWasNotUiThread = !dispatcher.isOnUiThread()
            val value = dispatcher.runOnUiBlocking {
                blockingUiRanOnUiThread = dispatcher.isOnUiThread() && !dispatcher.isOnLogicThread()
                123
            }
            assertEquals(123, value)
        }

        dispatcher.dispatchToLogicAsync {
            asyncDispatchedRan = dispatcher.isOnLogicThread()
        }

        val coroutineDone = CompletableDeferred<Boolean>()
        dispatcher.logicScope.launch {
            coroutineDone.complete(dispatcher.isOnLogicThread())
        }

        dispatcher.flushAllQueues()
        assertTrue(coroutineDone.await())
        assertTrue(logicWasLogicThread)
        assertTrue(logicWasNotUiThread)
        assertTrue(blockingUiRanOnUiThread)
        assertTrue(asyncDispatchedRan)
        assertTrue(drainCount >= 1)
        }
    }

    @Test
    fun gcdUiTimerFiresTimeoutsIntervalsAndCancelsCleanly() {
        val timer = AppleUiTimerNativeImpl()
        try {
            var timeoutFired = false
            var cancelledFired = false
            var intervalCount = 0

            timer.setTimeout(25) { timeoutFired = true }
            val toCancel = timer.setTimeout(25) { cancelledFired = true }
            val intervalId = timer.setInterval(20) { intervalCount++ }

            timer.clearTimer(toCancel)
            timer.advanceTimeBy(95)

            assertTrue(timeoutFired)
            assertFalse(cancelledFired)
            assertTrue(intervalCount >= 2, "Expected >=2 interval ticks, got $intervalCount")

            val beforeClear = intervalCount
            timer.clearInterval(intervalId)
            timer.advanceTimeBy(50)
            assertEquals(beforeClear, intervalCount)

            var flushedOneShot = false
            timer.setTimeout(5000) { flushedOneShot = true }
            assertEquals(1, timer.flushAll())
            assertTrue(flushedOneShot)
        } finally {
            timer.dispose()
        }
    }

    @Test
    fun networkMonitorAndNotificationManagerOperateSafely() {
        val monitor = AppleNetworkMonitorNativeImpl()
        try {
            monitor.setOnConnectivityChanged { _ -> }
            // Must return cleanly without crashing or blocking indefinitely in headless environments.
            monitor.isOnline()
            assertFalse(monitor.isDisposed)
        } finally {
            monitor.dispose()
            assertTrue(monitor.isDisposed)
        }

        val notifications = AppleNotificationManagerNativeImpl()
        try {
            assertFalse(notifications.isBundleNotificationCenterAvailable)
            notifications.registerChannel(
                com.nexustools.genericui.component.system.NotificationChannel("alerts", "Alerts", highImportance = true)
            )
            notifications.showNotification(
                com.nexustools.genericui.component.system.Notification(
                    id = "n1",
                    channelId = "alerts",
                    title = "Build Ready",
                    body = "GenericUI Apple tests running",
                    actions = listOf("open" to "Open")
                )
            )
            assertEquals(1, notifications.activeNotificationCount())
            notifications.cancelNotification("n1")
            assertEquals(0, notifications.activeNotificationCount())
        } finally {
            notifications.dispose()
            assertTrue(notifications.isDisposed)
        }
    }
}
