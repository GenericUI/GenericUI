package com.nexustools.genericui.threading

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.core.UiTimerScheduler
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DualThreadUiLogicSeparationTest {

    @BeforeTest
    fun setUp() {
        GenericUiThreading.customDispatcherOverride = DefaultUiThreadDispatcherNativeImpl(isSplitThreadingEnabled = true)
    }

    @AfterTest
    fun tearDown() {
        GenericUiThreading.customDispatcherOverride = null
    }

    @Test
    fun logicCallbackRunsOnLogicThreadAndQueuesUiUpdatesToUiThread() {
        assertTrue(GenericUiThreading.isSplitThreadingEnabled)
        assertTrue(GenericUiThreading.isOnUiThread())
        assertFalse(GenericUiThreading.isOnLogicThread())

        val executionOrder = mutableListOf<String>()

        GenericUiThreading.runOnLogic {
            assertTrue(GenericUiThreading.isOnLogicThread())
            assertFalse(GenericUiThreading.isOnUiThread())
            executionOrder.add("logic-start:ui=${GenericUiThreading.isOnUiThread()},logic=${GenericUiThreading.isOnLogicThread()}")

            GenericUiThreading.runOnUi {
                assertTrue(GenericUiThreading.isOnUiThread())
                assertFalse(GenericUiThreading.isOnLogicThread())
                executionOrder.add("ui-callback:ui=${GenericUiThreading.isOnUiThread()},logic=${GenericUiThreading.isOnLogicThread()}")
            }

            // Still on the Logic Thread; the UI callback must be queued until the logic frame finishes.
            assertTrue(GenericUiThreading.isOnLogicThread())
            assertFalse(GenericUiThreading.isOnUiThread())
            executionOrder.add("logic-end:ui=${GenericUiThreading.isOnUiThread()},logic=${GenericUiThreading.isOnLogicThread()}")
        }

        assertEquals(
            listOf(
                "logic-start:ui=false,logic=true",
                "logic-end:ui=false,logic=true",
                "ui-callback:ui=true,logic=false"
            ),
            executionOrder
        )
    }

    @Test
    fun runOnUiSyncExecutesWithUiThreadContextAndRestoresLogicThreadContext() {
        var syncResult = ""
        GenericUiThreading.runOnLogic {
            assertTrue(GenericUiThreading.isOnLogicThread())
            assertFalse(GenericUiThreading.isOnUiThread())

            syncResult = GenericUiThreading.runOnUiSync {
                assertTrue(GenericUiThreading.isOnUiThread())
                assertFalse(GenericUiThreading.isOnLogicThread())
                "measured-on-ui"
            }

            assertTrue(GenericUiThreading.isOnLogicThread())
            assertFalse(GenericUiThreading.isOnUiThread())
        }

        assertEquals("measured-on-ui", syncResult)
    }

    @Test
    fun componentEventsAndTimersRunOnLogicThreadAndFlushUiMutations() {
        val events = mutableListOf<String>()
        val button = Button("Run Action")
        button.onClick = {
            events.add("click-logic:ui=${GenericUiThreading.isOnUiThread()},logic=${GenericUiThreading.isOnLogicThread()}")
            GenericUiThreading.runOnUi {
                events.add("click-ui:ui=${GenericUiThreading.isOnUiThread()},logic=${GenericUiThreading.isOnLogicThread()}")
            }
        }

        button.triggerClick()

        button.setTimeout(50) {
            events.add("timer-logic:ui=${GenericUiThreading.isOnUiThread()},logic=${GenericUiThreading.isOnLogicThread()}")
            GenericUiThreading.runOnUi {
                events.add("timer-ui:ui=${GenericUiThreading.isOnUiThread()},logic=${GenericUiThreading.isOnLogicThread()}")
            }
        }
        UiTimerScheduler.advanceTimeBy(50)

        assertEquals(
            listOf(
                "click-logic:ui=false,logic=true",
                "click-ui:ui=true,logic=false",
                "timer-logic:ui=false,logic=true",
                "timer-ui:ui=true,logic=false"
            ),
            events
        )
    }

    @Test
    fun webSingleLoopModeExecutesDirectlyWhenSplitThreadingDisabled() {
        GenericUiThreading.customDispatcherOverride = DefaultUiThreadDispatcherNativeImpl(isSplitThreadingEnabled = false)
        assertFalse(GenericUiThreading.isSplitThreadingEnabled)

        val order = mutableListOf<String>()
        GenericUiThreading.runOnLogic {
            assertTrue(GenericUiThreading.isOnUiThread())
            assertTrue(GenericUiThreading.isOnLogicThread())
            order.add("logic-before")
            GenericUiThreading.runOnUi {
                order.add("ui-inline")
            }
            order.add("logic-after")
        }

        assertEquals(listOf("logic-before", "ui-inline", "logic-after"), order)
    }
}
