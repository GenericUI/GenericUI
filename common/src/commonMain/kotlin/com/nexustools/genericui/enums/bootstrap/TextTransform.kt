package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 text transformation utilities (`text-lowercase`, `text-uppercase`, `text-capitalize`).
 */
enum class TextTransform(val cssClass: String) {
    NONE(""),
    LOWERCASE("text-lowercase"),
    UPPERCASE("text-uppercase"),
    CAPITALIZE("text-capitalize");

    companion object {
        fun fromCssClass(cssClass: String): TextTransform? =
            entries.firstOrNull { it.cssClass.isNotEmpty() && it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
