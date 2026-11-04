package com.nexustools.genericui.event

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable

/**
 * Represents an active global pointer drag operation initiated on [target] via [PointerDragManager].
 * While active, pointer movements and pointer release events anywhere inside or outside [target]'s
 * bounds are routed to [onDragMove] and [onDragEnd].
 */
class PointerDragSession internal constructor(
    val target: AbstractComponent,
    val initialEvent: PointerEvent,
    val pointerId: Int = initialEvent.pointerId,
    internal val onDragMove: (PointerEvent) -> Unit,
    internal val onDragEnd: (event: PointerEvent, cancelled: Boolean) -> Unit
) : Disposable {

    var lastEvent: PointerEvent = initialEvent
        internal set

    var isActive: Boolean = true
        internal set

    override val isDisposed: Boolean
        get() = !isActive

    fun cancel(event: PointerEvent = lastEvent) {
        PointerDragManager.endSession(this, event, cancelled = true)
    }

    override fun dispose() {
        cancel(lastEvent)
    }
}
