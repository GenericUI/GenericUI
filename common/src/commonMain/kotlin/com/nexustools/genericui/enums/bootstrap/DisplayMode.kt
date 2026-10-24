package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 display utilities (`d-none`, `d-inline`, `d-inline-block`, `d-block`, `d-grid`, `d-flex`, `d-inline-flex`).
 */
enum class DisplayMode(val cssClass: String) {
    NONE("d-none"),
    INLINE("d-inline"),
    INLINE_BLOCK("d-inline-block"),
    BLOCK("d-block"),
    GRID("d-grid"),
    FLEX("d-flex"),
    INLINE_FLEX("d-inline-flex");

    companion object {
        fun fromCssClass(cssClass: String): DisplayMode? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
