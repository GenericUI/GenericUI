package com.nexustools.genericui.event

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeImpl

/**
 * Global popup mouse capture manager that tracks active popups, context menus,
 * dropdowns, and modal overlays to intercept outside pointer clicks, dismiss the
 * active popup, and swallow the outside click so it does not trigger underlying UI controls.
 */
object PopupMouseCaptureManager {
    private val entries = mutableListOf<PopupCaptureEntry>()
    private var nextId: Int = 1
    private var swallowActivePressSequence: Boolean = false
    private var pointerDownActive: Boolean = false
    private var approvedClickDispatchDepth: Int = 0

    val hasActivePopup: Boolean
        get() = entries.isNotEmpty()

    val activePopupCount: Int
        get() = entries.size

    var onActivePopupCountChanged: ((Int) -> Unit)? = null

    fun registerPopup(
        popup: AbstractComponent,
        hitTester: ((Float, Float, Any?) -> Boolean)? = null,
        onDismiss: () -> Unit
    ): Int {
        entries.removeAll { it.popupComponent === popup }
        val id = nextId++
        entries.add(
            PopupCaptureEntry(
                id = id,
                popupComponent = popup,
                nativePopup = null,
                hitTester = hitTester,
                onDismiss = onDismiss,
                openedDuringCurrentPointerGesture = pointerDownActive
            )
        )
        onActivePopupCountChanged?.invoke(entries.size)
        return id
    }

    fun registerNativePopup(
        nativePopup: NativeImpl<*>,
        hitTester: ((Float, Float, Any?) -> Boolean)? = null,
        onDismiss: () -> Unit
    ): Int {
        entries.removeAll { it.nativePopup === nativePopup }
        val id = nextId++
        entries.add(
            PopupCaptureEntry(
                id = id,
                popupComponent = null,
                nativePopup = nativePopup,
                hitTester = hitTester,
                onDismiss = onDismiss,
                openedDuringCurrentPointerGesture = pointerDownActive
            )
        )
        onActivePopupCountChanged?.invoke(entries.size)
        return id
    }

    fun unregisterPopup(popup: AbstractComponent) {
        val removed = entries.removeAll { it.popupComponent === popup }
        if (removed) {
            onActivePopupCountChanged?.invoke(entries.size)
        }
    }

    fun unregisterNativePopup(nativePopup: NativeImpl<*>) {
        val removed = entries.removeAll { it.nativePopup === nativePopup }
        if (removed) {
            onActivePopupCountChanged?.invoke(entries.size)
        }
    }

    fun unregisterById(id: Int) {
        val removed = entries.removeAll { it.id == id }
        if (removed) {
            onActivePopupCountChanged?.invoke(entries.size)
        }
    }

    fun clear() {
        val hadEntries = entries.isNotEmpty()
        entries.clear()
        swallowActivePressSequence = false
        pointerDownActive = false
        approvedClickDispatchDepth = 0
        if (hadEntries) {
            onActivePopupCountChanged?.invoke(0)
        }
    }

    fun isPopupRoot(target: Any?): Boolean {
        if (target == null) return false
        for (entry in entries) {
            if (entry.nativePopup != null && (entry.nativePopup === target || entry.nativePopup.nativeWidget === target)) {
                return true
            }
            val popup = entry.popupComponent
            if (popup != null) {
                val ni = popup.nativeImpl
                if (popup === target || ni === target || ni?.nativeWidget === target) {
                    return true
                }
            }
        }
        return false
    }

    private fun pruneInvisiblePopups() {
        val removed = entries.removeAll { entry ->
            entry.popupComponent != null && !entry.popupComponent.isVisible
        }
        if (removed) {
            onActivePopupCountChanged?.invoke(entries.size)
        }
    }

    private fun containsTargetInSubtree(comp: AbstractComponent, target: Any): Boolean {
        val ni = comp.nativeImpl
        if (comp === target || ni === target || (ni != null && ni.nativeWidget === target)) {
            return true
        }
        if (comp is AbstractContainer) {
            for (child in comp.children) {
                if (containsTargetInSubtree(child, target)) return true
            }
        }
        return false
    }

    fun isTargetInsideEntry(entry: PopupCaptureEntry, x: Float, y: Float, target: Any?): Boolean {
        if (entry.hitTester != null) {
            val hit = entry.hitTester.invoke(x, y, target)
            if (hit) return true
            if (entry.popupComponent == null) return false
        }
        if (entry.nativePopup != null && target != null && (target === entry.nativePopup || target === entry.nativePopup.nativeWidget)) {
            return true
        }
        val popup = entry.popupComponent
        if (popup != null) {
            if (target is AbstractComponent) {
                var curr: AbstractComponent? = target
                while (curr != null) {
                    if (curr === popup) return true
                    curr = curr.parent
                }
            }
            if (target != null && containsTargetInSubtree(popup, target)) {
                return true
            }
            if (!x.isNaN() && !y.isNaN()) {
                val b = popup.bounds
                if (b.width > 0 && b.height > 0 && b.contains(x.toInt(), y.toInt())) {
                    return true
                }
            }
        }
        return false
    }

    fun interceptPointerDown(x: Float = Float.NaN, y: Float = Float.NaN, target: Any? = null): Boolean {
        for (entry in entries) {
            entry.openedDuringCurrentPointerGesture = false
        }
        pointerDownActive = true
        pruneInvisiblePopups()
        val top = entries.lastOrNull() ?: run {
            swallowActivePressSequence = false
            return false
        }
        if (isTargetInsideEntry(top, x, y, target)) {
            swallowActivePressSequence = false
            return false
        }
        swallowActivePressSequence = true
        entries.removeAt(entries.lastIndex)
        onActivePopupCountChanged?.invoke(entries.size)
        top.onDismiss.invoke()
        return true
    }

    fun interceptPointerUp(x: Float = Float.NaN, y: Float = Float.NaN, target: Any? = null): Boolean {
        pointerDownActive = false
        return swallowActivePressSequence
    }

    fun interceptClick(x: Float = Float.NaN, y: Float = Float.NaN, target: Any? = null): Boolean {
        if (approvedClickDispatchDepth > 0) return false
        pointerDownActive = false
        if (swallowActivePressSequence) {
            swallowActivePressSequence = false
            return true
        }
        pruneInvisiblePopups()
        val top = entries.lastOrNull() ?: return false
        if (top.openedDuringCurrentPointerGesture) {
            top.openedDuringCurrentPointerGesture = false
            return false
        }
        val intercepted = interceptPointerDown(x, y, target)
        pointerDownActive = false
        swallowActivePressSequence = false
        return intercepted
    }

    fun <T> withApprovedClickDispatch(block: () -> T): T {
        approvedClickDispatchDepth++
        return try {
            block()
        } finally {
            approvedClickDispatchDepth = (approvedClickDispatchDepth - 1).coerceAtLeast(0)
        }
    }
}
