package com.nexustools.genericui.window

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Serializable snapshot of main-window geometry and presentation state persisted in `PreferenceStore`.
 */
data class MainWindowSavedState(
    val x: Int? = null,
    val y: Int? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fullscreen: Boolean = false,
    val windowState: WindowState = WindowState.NORMAL
) {
    val hasValidSize: Boolean
        get() = width > 0 && height > 0

    val hasPosition: Boolean
        get() = x != null && y != null

    fun toBounds(): Rect? {
        if (!hasValidSize) return null
        val posX = x ?: return null
        val posY = y ?: return null
        return Rect(posX, posY, width, height)
    }

    fun toPreferenceElement(): PreferenceElement {
        val entries = linkedMapOf<String, PreferenceElement>(
            FIELD_WIDTH to PreferenceElement.ofInt(width),
            FIELD_HEIGHT to PreferenceElement.ofInt(height),
            FIELD_FULLSCREEN to PreferenceElement.ofBoolean(fullscreen),
            FIELD_WINDOW_STATE to PreferenceElement.ofString(windowState.name)
        )
        if (x != null && y != null) {
            entries[FIELD_X] = PreferenceElement.ofInt(x)
            entries[FIELD_Y] = PreferenceElement.ofInt(y)
        }
        return PreferenceElement.ofMap(entries)
    }

    companion object {
        const val PREF_KEY: String = "genericui.main_window_state"

        private const val FIELD_X = "x"
        private const val FIELD_Y = "y"
        private const val FIELD_WIDTH = "width"
        private const val FIELD_HEIGHT = "height"
        private const val FIELD_FULLSCREEN = "fullscreen"
        private const val FIELD_WINDOW_STATE = "windowState"
        private const val MIN_VISIBLE_OVERLAP_PX = 48

        fun fromPreferenceElement(element: PreferenceElement?): MainWindowSavedState? {
            if (element == null || (!element.isMap && !element.isObject)) {
                return null
            }
            val hasKnownField = element[FIELD_WIDTH] != null ||
                element[FIELD_HEIGHT] != null ||
                element[FIELD_FULLSCREEN] != null ||
                element[FIELD_WINDOW_STATE] != null ||
                element[FIELD_X] != null ||
                element[FIELD_Y] != null
            if (!hasKnownField) {
                return null
            }
            val width = (element[FIELD_WIDTH]?.asInt(0) ?: 0).coerceAtLeast(0)
            val height = (element[FIELD_HEIGHT]?.asInt(0) ?: 0).coerceAtLeast(0)
            val validSize = width > 0 && height > 0
            val xElem = element[FIELD_X]
            val yElem = element[FIELD_Y]
            val x = if (validSize && xElem != null && !xElem.isNull) xElem.asInt() else null
            val y = if (validSize && yElem != null && !yElem.isNull) yElem.asInt() else null
            val fullscreen = element[FIELD_FULLSCREEN]?.asBoolean(false) ?: false
            val stateName = element[FIELD_WINDOW_STATE]?.asString()
            val parsedState = WindowState.entries.firstOrNull { it.name == stateName } ?: WindowState.NORMAL
            val normalizedState = when (parsedState) {
                WindowState.FULLSCREEN,
                WindowState.MINIMIZED,
                WindowState.PICTURE_IN_PICTURE -> WindowState.NORMAL
                WindowState.NORMAL,
                WindowState.MAXIMIZED -> parsedState
            }
            return MainWindowSavedState(
                x = if (x != null && y != null) x else null,
                y = if (x != null && y != null) y else null,
                width = if (validSize) width else 0,
                height = if (validSize) height else 0,
                fullscreen = fullscreen,
                windowState = normalizedState
            )
        }

        /**
         * Returns `true` when [windowRect] overlaps [monitorBounds] by at least `48px` on each axis
         * (or the full smaller dimension when smaller than `48px`).
         */
        fun intersectsVisibleArea(windowRect: Rect, monitorBounds: Rect): Boolean {
            if (windowRect.width <= 0 || windowRect.height <= 0 ||
                monitorBounds.width <= 0 || monitorBounds.height <= 0
            ) {
                return false
            }
            val overlapW = min(windowRect.right, monitorBounds.right) - max(windowRect.x, monitorBounds.x)
            val overlapH = min(windowRect.bottom, monitorBounds.bottom) - max(windowRect.y, monitorBounds.y)
            if (overlapW <= 0 || overlapH <= 0) {
                return false
            }
            val minReqW = min(MIN_VISIBLE_OVERLAP_PX, min(windowRect.width, monitorBounds.width))
            val minReqH = min(MIN_VISIBLE_OVERLAP_PX, min(windowRect.height, monitorBounds.height))
            return overlapW >= minReqW && overlapH >= minReqH
        }
    }
}
