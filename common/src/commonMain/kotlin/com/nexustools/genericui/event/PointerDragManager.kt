package com.nexustools.genericui.event

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.style.css.CssCursorValue

/**
 * Global pointer drag coordinator for `GenericUI`.
 *
 * Ensures that once a component (such as `SeekBar`, `TrackSeekBar`, or `Slider`) or a window
 * move/resize interaction begins dragging, pointer movements ([notifyGlobalPointerMove]) continue
 * to be delivered to the active drag session even when the cursor leaves the element's bounds,
 * pointer release ([notifyGlobalPointerUp]) completes the session, and optional cursor locking
 * notifies host cursor listeners coherently across session transitions.
 */
object PointerDragManager {

    var activeSession: PointerDragSession? = null
        private set

    private var lastHandledMoveEvent: PointerEvent? = null
    private val cursorLockListeners =
        mutableListOf<(locked: Boolean, cursor: CssCursorValue?, kind: PointerDragKind?) -> Unit>()
    private var lastNotifiedCursorLocked: Boolean = false
    private var lastNotifiedCursor: CssCursorValue? = null
    private var lastNotifiedKind: PointerDragKind? = null

    val isDragging: Boolean
        get() = activeSession?.isActive == true

    val isCursorLocked: Boolean
        get() = activeSession?.let { it.isActive && it.lockCursor } == true

    val lockedCursor: CssCursorValue?
        get() = activeSession?.takeIf { it.isActive && it.lockCursor }?.lockedCursor

    val activeKind: PointerDragKind?
        get() = activeSession?.takeIf { it.isActive }?.kind

    fun isDragging(target: AbstractComponent): Boolean =
        activeSession?.let { it.isActive && it.target === target } == true

    fun addCursorLockListener(
        listener: (locked: Boolean, cursor: CssCursorValue?, kind: PointerDragKind?) -> Unit
    ) {
        if (!cursorLockListeners.contains(listener)) {
            cursorLockListeners.add(listener)
        }
    }

    fun removeCursorLockListener(
        listener: (locked: Boolean, cursor: CssCursorValue?, kind: PointerDragKind?) -> Unit
    ) {
        cursorLockListeners.remove(listener)
    }

    fun startDrag(
        target: AbstractComponent,
        initialEvent: PointerEvent = PointerEvent(),
        lockedCursor: CssCursorValue? = null,
        lockCursor: Boolean = true,
        onDragMove: (PointerEvent) -> Unit,
        onDragEnd: (event: PointerEvent, cancelled: Boolean) -> Unit = { _, _ -> }
    ): PointerDragSession = startSession(
        target = target,
        initialEvent = initialEvent,
        kind = PointerDragKind.COMPONENT,
        lockedCursor = lockedCursor,
        lockCursor = lockCursor,
        onDragMove = onDragMove,
        onDragEnd = onDragEnd
    )

    fun startWindowMoveDrag(
        windowTarget: AbstractComponent? = null,
        initialEvent: PointerEvent = PointerEvent(),
        lockedCursor: CssCursorValue? = CssCursor.DEFAULT,
        onDragEnd: (event: PointerEvent, cancelled: Boolean) -> Unit = { _, _ -> }
    ): PointerDragSession = startSession(
        target = windowTarget,
        initialEvent = initialEvent,
        kind = PointerDragKind.WINDOW_MOVE,
        lockedCursor = lockedCursor,
        lockCursor = true,
        onDragMove = {},
        onDragEnd = onDragEnd
    )

    fun startWindowResizeDrag(
        windowTarget: AbstractComponent? = null,
        initialEvent: PointerEvent = PointerEvent(),
        lockedCursor: CssCursorValue?,
        onDragEnd: (event: PointerEvent, cancelled: Boolean) -> Unit = { _, _ -> }
    ): PointerDragSession = startSession(
        target = windowTarget,
        initialEvent = initialEvent,
        kind = PointerDragKind.WINDOW_RESIZE,
        lockedCursor = lockedCursor,
        lockCursor = true,
        onDragMove = {},
        onDragEnd = onDragEnd
    )

    private fun startSession(
        target: AbstractComponent?,
        initialEvent: PointerEvent,
        kind: PointerDragKind,
        lockedCursor: CssCursorValue?,
        lockCursor: Boolean,
        onDragMove: (PointerEvent) -> Unit,
        onDragEnd: (event: PointerEvent, cancelled: Boolean) -> Unit
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
            kind = kind,
            lockedCursor = lockedCursor,
            lockCursor = lockCursor,
            onDragMove = onDragMove,
            onDragEnd = onDragEnd
        )
        activeSession = session
        dispatchCursorLockStateIfChanged()
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
            dispatchCursorLockStateIfChanged()
            return false
        }
        if (session.target?.isDisposed == true) {
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
            dispatchCursorLockStateIfChanged()
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
            dispatchCursorLockStateIfChanged()
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
        dispatchCursorLockStateIfChanged()
        session.onDragEnd(event, cancelled)
    }

    private fun dispatchCursorLockStateIfChanged() {
        val locked = isCursorLocked
        val cursor = lockedCursor
        val kind = activeKind
        if (locked == lastNotifiedCursorLocked &&
            cursor == lastNotifiedCursor &&
            kind == lastNotifiedKind
        ) {
            return
        }
        // Only emit unlocked transitions when leaving a previously locked state (not for non-locking drags).
        if (!locked && !lastNotifiedCursorLocked) {
            lastNotifiedCursor = null
            lastNotifiedKind = null
            return
        }
        lastNotifiedCursorLocked = locked
        lastNotifiedCursor = cursor
        lastNotifiedKind = kind
        val listeners = cursorLockListeners.toList()
        for (listener in listeners) {
            listener(locked, cursor, kind)
        }
    }

    private fun toTargetLocalEvent(target: AbstractComponent?, event: PointerEvent): PointerEvent {
        if (target == null) return event
        val bounds = target.getBounds()
        if ((bounds.width <= 0 && bounds.height <= 0) ||
            (event.screenX == 0f && event.screenY == 0f && (event.x != 0f || event.y != 0f))
        ) {
            return event
        }
        val localX = event.screenX - bounds.x.toFloat()
        val localY = event.screenY - bounds.y.toFloat()
        return event.copy(x = localX, y = localY)
    }
}
