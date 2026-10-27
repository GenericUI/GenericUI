package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssCursorValue

/**
 * Standard W3C CSS `cursor` keyword values.
 */
enum class CssCursor(val cssValue: String) : CssCursorValue {
    AUTO("auto"),
    DEFAULT("default"),
    NONE("none"),
    POINTER("pointer"),
    TEXT("text"),
    WAIT("wait"),
    PROGRESS("progress"),
    HELP("help"),
    CROSSHAIR("crosshair"),
    MOVE("move"),
    GRAB("grab"),
    GRABBING("grabbing"),
    NOT_ALLOWED("not-allowed"),
    NO_DROP("no-drop"),
    COL_RESIZE("col-resize"),
    ROW_RESIZE("row-resize"),
    N_RESIZE("n-resize"),
    E_RESIZE("e-resize"),
    S_RESIZE("s-resize"),
    W_RESIZE("w-resize"),
    NE_RESIZE("ne-resize"),
    NW_RESIZE("nw-resize"),
    SE_RESIZE("se-resize"),
    SW_RESIZE("sw-resize"),
    EW_RESIZE("ew-resize"),
    NS_RESIZE("ns-resize"),
    NESW_RESIZE("nesw-resize"),
    NWSE_RESIZE("nwse-resize"),
    ZOOM_IN("zoom-in"),
    ZOOM_OUT("zoom-out");

    override fun toCssString(): String = cssValue

    companion object {
        fun fromCss(raw: String?): CssCursor? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.cssValue == cleaned }
        }
    }
}
