package com.nexustools.genericui.event

import com.nexustools.genericui.core.AbstractComponent

/**
 * Global pointer drag coordinator for `GenericUI`.
 *
 * Ensures that once a component (such as `SeekBar`, `TrackSeekBar`, or `Slider`) begins dragging,
 * pointer movements ([notifyGlobalPointerMove]) continue to be delivered to the active drag session
 * even when the cursor leaves the boundary of the element, and pointer release ([notifyGlobalPointerUp])
 * is handled even when released outside the boundary of the element.
 */
object PointerDragManager {

    var activeSession: PointerDragSession? = null
        private set

    private var lastHandledMoveEvent: PointerEvent? = null

    val isDragging: Boolean
        get() = activeSession?.isActive == true

    fun isDragging(target: AbstractComponent): Boolean =
        activeSession?.let { it.isActive && it.target === target } == true

    fun startDrag(
        target: AbstractComponent,
        initialEvent: PointerEvent = PointerEvent(),
        onDragMove: (PointerEvent) -> Unit,
        onDragEnd: (event: PointerEvent, cancelled: Boolean) -> Unit = { _, _ -> }
    ): PointerDragSession {
        activeSession?.let { existing ->
            if (existing.isActive) {
                endSession(existing, initialEvent, cancelled = true)
            }
        }
        val session = PointerDragSession(
            target = target,
            initialEvent = initialEvent,
            pointerId = initialEvent.pointerId,
            onDragMove = onDragMove,
            onDragEnd = onDragEnd
        )
        activeSession = session
        return session
    }

    /**
     * Dispatches a global pointer move event (in window/screen coordinates or target-relative coordinates)
     * to the active drag session, even when the pointer is outside the dragged element's bounds.
     */
    fun notifyGlobalPointerMove(
        event: PointerEvent,
        coordinatesAreTargetLocal: Boolean = false
    ): Boolean {
        val session = activeSession ?: return false
        if (!session.isActive) {
            activeSession = null
            return false
        }
        if (session.target.isDisposed) {
            endSession(session, event, cancelled = true)
            return false
        }
        if (lastHandledMoveEvent === event) {
            return true
        }
        lastHandledMoveEvent = event
        val localEvent = if (coordinatesAreTargetLocal) {
            event
        } else {
            toTargetLocalEvent(session.target, event)
        }
        session.lastEvent = localEvent
        session.onDragMove(localEvent)
        return true
    }

    /**
     * Dispatches a global pointer release (`pointerup` / `mouseup`) event to complete the active drag session,
     * even when the pointer is released outside the dragged element's bounds.
     */
    fun notifyGlobalPointerUp(
        event: PointerEvent = activeSession?.lastEvent ?: PointerEvent(),
        coordinatesAreTargetLocal: Boolean = false
    ): Boolean {
        val session = activeSession ?: return false
        if (!session.isActive) {
            activeSession = null
            return false
        }
        val localEvent = if (coordinatesAreTargetLocal) {
            event
        } else {
            toTargetLocalEvent(session.target, event)
        }
        endSession(session, localEvent, cancelled = false)
        return true
    }

    fun cancelActiveDrag(event: PointerEvent = activeSession?.lastEvent ?: PointerEvent()): Boolean {
        val session = activeSession ?: return false
        if (!session.isActive) {
            activeSession = null
            return false
        }
        endSession(session, event, cancelled = true)
        return true
    }

    internal fun endSession(
        session: PointerDragSession,
        event: PointerEvent,
        cancelled: Boolean
    ) {
        if (!session.isActive) return
        session.isActive = false
        session.lastEvent = event
        if (activeSession === session) {
            activeSession = null
        }
        session.onDragEnd(event, cancelled)
    }

    private fun toTargetLocalEvent(target: AbstractComponent, event: PointerEvent): PointerEvent {
        val bounds = target.getBounds()
        if ((bounds.width <= 0 && bounds.height <= 0) || (bounds.x == 0 && bounds.y == 0)) {
            return event
        }
        val localX = event.screenX - bounds.x.toFloat()
        val localY = event.screenY - bounds.y.toFloat()
        return event.copy(x = localX, y = localY)
    }
}
