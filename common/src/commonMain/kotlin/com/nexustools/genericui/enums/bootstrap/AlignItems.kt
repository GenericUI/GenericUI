package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 cross-axis alignment utilities (`align-items-start`, `end`, `center`, `baseline`, `stretch`).
 */
enum class AlignItems(val cssClass: String) {
    START("align-items-start"),
    END("align-items-end"),
    CENTER("align-items-center"),
    BASELINE("align-items-baseline"),
    STRETCH("align-items-stretch");

    companion object {
        fun fromCssClass(cssClass: String): AlignItems? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
