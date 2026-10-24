package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 position utilities (`position-static`, `position-relative`, `position-absolute`, `position-fixed`, `position-sticky`).
 */
enum class PositionMode(val cssClass: String) {
    STATIC("position-static"),
    RELATIVE("position-relative"),
    ABSOLUTE("position-absolute"),
    FIXED("position-fixed"),
    STICKY("position-sticky");

    companion object {
        fun fromCssClass(cssClass: String): PositionMode? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
