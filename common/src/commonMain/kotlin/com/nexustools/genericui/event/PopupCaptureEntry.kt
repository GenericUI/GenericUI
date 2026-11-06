package com.nexustools.genericui.event

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeImpl

/**
 * Represents an active popup registered with [PopupMouseCaptureManager] for outside-click interception.
 */
class PopupCaptureEntry(
    val id: Int,
    val popupComponent: AbstractComponent? = null,
    val nativePopup: NativeImpl<*>? = null,
    val hitTester: ((x: Float, y: Float, target: Any?) -> Boolean)? = null,
    val onDismiss: () -> Unit,
    internal var openedDuringCurrentPointerGesture: Boolean = false
)
