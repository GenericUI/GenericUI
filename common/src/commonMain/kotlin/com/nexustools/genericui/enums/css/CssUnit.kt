package com.nexustools.genericui.enums.css

import com.nexustools.genericui.style.css.CssValue

/**
 * Standard CSS measurement units and keywords (`px`, `%`, `rem`, `em`, `vw`, `vh`, `vmin`, `vmax`, `pt`, `dp`, `auto`, `none`).
 */
enum class CssUnit(val symbol: String) : CssValue {
    PX("px"),
    PERCENT("%"),
    REM("rem"),
    EM("em"),
    VW("vw"),
    VH("vh"),
    VMIN("vmin"),
    VMAX("vmax"),
    PT("pt"),
    DP("px"),
    AUTO("auto"),
    NONE("none");

    val cssValue: String
        get() = symbol

    override fun toCssString(): String = symbol

    companion object {
        fun fromCss(raw: String?): CssUnit? {
            val cleaned = raw?.trim()?.lowercase() ?: return null
            if (cleaned == "dp") return DP
            return entries.firstOrNull { it.symbol.equals(cleaned, ignoreCase = true) }
        }
    }
}
