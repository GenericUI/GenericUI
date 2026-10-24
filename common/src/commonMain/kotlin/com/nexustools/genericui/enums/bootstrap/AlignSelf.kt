package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 individual item cross-axis alignment (`align-self-auto`, `start`, `end`, `center`, `baseline`, `stretch`).
 */
enum class AlignSelf(val cssClass: String) {
    AUTO("align-self-auto"),
    START("align-self-start"),
    END("align-self-end"),
    CENTER("align-self-center"),
    BASELINE("align-self-baseline"),
    STRETCH("align-self-stretch");

    companion object {
        fun fromCssClass(cssClass: String): AlignSelf? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
