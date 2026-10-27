package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 opacity utilities (`opacity-0`, `opacity-25`, `opacity-50`, `opacity-75`, `opacity-100`).
 */
enum class BootstrapOpacity(val cssClass: String, val alpha: Float) {
    OPACITY_0("opacity-0", 0.0f),
    OPACITY_25("opacity-25", 0.25f),
    OPACITY_50("opacity-50", 0.50f),
    OPACITY_75("opacity-75", 0.75f),
    OPACITY_100("opacity-100", 1.0f);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapOpacity? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
