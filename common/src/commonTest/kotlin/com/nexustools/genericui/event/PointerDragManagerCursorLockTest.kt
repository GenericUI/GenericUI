package com.nexustools.genericui.event

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.style.css.CssCursorValue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointerDragManagerCursorLockTest {

    @AfterTest
    fun tearDown() {
        PointerDragManager.cancelActiveDrag()
    }

    @Test
    fun testStartDragDefaultAndOptOutCursorLocking() {
        val target = Container("drag-target")
        val events = mutableListOf<Triple<Boolean, CssCursorValue?, PointerDragKind?>>()
        val listener: (Boolean, CssCursorValue?, PointerDragKind?) -> Unit = { locked, cursor, kind ->
            events.add(Triple(locked, cursor, kind))
        }
        PointerDragManager.addCursorLockListener(listener)

        try {
            val defaultSession = PointerDragManager.startDrag(
                target = target,
                initialEvent = PointerEvent(10f, 20f),
                lockedCursor = CssCursor.POINTER,
                onDragMove = {}
            )
            assertTrue(defaultSession.lockCursor)
            assertEquals(PointerDragKind.COMPONENT, defaultSession.kind)
            assertEquals(CssCursor.POINTER, defaultSession.lockedCursor)
            assertTrue(PointerDragManager.isDragging(target))
            assertTrue(PointerDragManager.isCursorLocked)
            assertEquals(CssCursor.POINTER, PointerDragManager.lockedCursor)
            assertEquals(PointerDragKind.COMPONENT, PointerDragManager.activeKind)
            assertEquals(
                listOf(Triple<Boolean, CssCursorValue?, PointerDragKind?>(true, CssCursor.POINTER, PointerDragKind.COMPONENT)),
                events
            )

            assertTrue(PointerDragManager.notifyGlobalPointerUp(PointerEvent(30f, 40f)))
            assertFalse(PointerDragManager.isDragging)
            assertFalse(PointerDragManager.isCursorLocked)
            assertNull(PointerDragManager.lockedCursor)
            assertNull(PointerDragManager.activeKind)
            assertEquals(
                listOf(
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(true, CssCursor.POINTER, PointerDragKind.COMPONENT),
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(false, null, null)
                ),
                events
            )

            events.clear()
            val unlockedSession = PointerDragManager.startDrag(
                target = target,
                initialEvent = PointerEvent(5f, 5f),
                lockedCursor = CssCursor.GRABBING,
                lockCursor = false,
                onDragMove = {}
            )
            assertFalse(unlockedSession.lockCursor)
            assertTrue(PointerDragManager.isDragging(target))
            assertFalse(PointerDragManager.isCursorLocked)
            assertNull(PointerDragManager.lockedCursor)
            assertEquals(PointerDragKind.COMPONENT, PointerDragManager.activeKind)
            assertTrue(events.isEmpty())

            assertTrue(PointerDragManager.cancelActiveDrag())
            assertFalse(PointerDragManager.isDragging)
            assertTrue(events.isEmpty())
        } finally {
            PointerDragManager.removeCursorLockListener(listener)
            target.dispose()
        }
    }

    @Test
    fun testWindowMoveResizeSessionTransitionsAndListenerRemoval() {
        val target = Container("window-drag-target")
        val events = mutableListOf<Triple<Boolean, CssCursorValue?, PointerDragKind?>>()
        val listener: (Boolean, CssCursorValue?, PointerDragKind?) -> Unit = { locked, cursor, kind ->
            events.add(Triple(locked, cursor, kind))
        }
        PointerDragManager.addCursorLockListener(listener)

        try {
            var moveEndedCancelled: Boolean? = null
            val moveSession = PointerDragManager.startWindowMoveDrag(
                windowTarget = null,
                initialEvent = PointerEvent(100f, 12f),
                onDragEnd = { _, cancelled -> moveEndedCancelled = cancelled }
            )
            assertNull(moveSession.target)
            assertEquals(PointerDragKind.WINDOW_MOVE, moveSession.kind)
            assertTrue(moveSession.lockCursor)
            assertTrue(PointerDragManager.isDragging)
            assertFalse(PointerDragManager.isDragging(target))
            assertTrue(PointerDragManager.isCursorLocked)
            assertEquals(CssCursor.DEFAULT, PointerDragManager.lockedCursor)
            assertEquals(PointerDragKind.WINDOW_MOVE, PointerDragManager.activeKind)
            assertTrue(PointerDragManager.notifyGlobalPointerMove(PointerEvent(115f, 18f)))

            // Replacing active window move drag with window resize drag ends previous session cleanly
            var resizeEndedCancelled: Boolean? = null
            val resizeSession = PointerDragManager.startWindowResizeDrag(
                windowTarget = null,
                initialEvent = PointerEvent(800f, 600f),
                lockedCursor = CssCursor.NWSE_RESIZE,
                onDragEnd = { _, cancelled -> resizeEndedCancelled = cancelled }
            )
            assertEquals(true, moveEndedCancelled)
            assertFalse(moveSession.isActive)
            assertTrue(resizeSession.isActive)
            assertEquals(PointerDragKind.WINDOW_RESIZE, PointerDragManager.activeKind)
            assertEquals(CssCursor.NWSE_RESIZE, PointerDragManager.lockedCursor)
            assertTrue(PointerDragManager.isCursorLocked)

            // Cancelling active resize drag notifies unlock
            assertTrue(PointerDragManager.cancelActiveDrag())
            assertEquals(true, resizeEndedCancelled)
            assertFalse(PointerDragManager.isCursorLocked)
            assertNull(PointerDragManager.lockedCursor)
            assertNull(PointerDragManager.activeKind)

            assertEquals(
                listOf(
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(true, CssCursor.DEFAULT, PointerDragKind.WINDOW_MOVE),
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(false, null, null),
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(true, CssCursor.NWSE_RESIZE, PointerDragKind.WINDOW_RESIZE),
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(false, null, null)
                ),
                events
            )

            // Removing listener stops further notifications
            events.clear()
            PointerDragManager.removeCursorLockListener(listener)
            PointerDragManager.startDrag(
                target = target,
                lockedCursor = CssCursor.MOVE,
                onDragMove = {}
            )
            assertTrue(PointerDragManager.isCursorLocked)
            PointerDragManager.cancelActiveDrag()
            assertTrue(events.isEmpty())
        } finally {
            PointerDragManager.removeCursorLockListener(listener)
            target.dispose()
        }
    }

    @Test
    fun testDisposedTargetAutoCancelsAndUnlocksCursor() {
        val target = Container("disposable-target")
        val events = mutableListOf<Triple<Boolean, CssCursorValue?, PointerDragKind?>>()
        val listener: (Boolean, CssCursorValue?, PointerDragKind?) -> Unit = { locked, cursor, kind ->
            events.add(Triple(locked, cursor, kind))
        }
        PointerDragManager.addCursorLockListener(listener)

        try {
            var endedCancelled: Boolean? = null
            PointerDragManager.startDrag(
                target = target,
                lockedCursor = CssCursor.EW_RESIZE,
                onDragMove = {},
                onDragEnd = { _, cancelled -> endedCancelled = cancelled }
            )
            assertTrue(PointerDragManager.isCursorLocked)
            target.dispose()
            assertFalse(PointerDragManager.isDragging)
            assertEquals(true, endedCancelled)
            assertFalse(PointerDragManager.isCursorLocked)
            assertEquals(
                listOf(
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(true, CssCursor.EW_RESIZE, PointerDragKind.COMPONENT),
                    Triple<Boolean, CssCursorValue?, PointerDragKind?>(false, null, null)
                ),
                events
            )
        } finally {
            PointerDragManager.removeCursorLockListener(listener)
            if (!target.isDisposed) target.dispose()
        }
    }
}
