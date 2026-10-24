package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 box shadow elevations (`shadow-none`, `shadow-sm`, `shadow`, `shadow-lg`).
 */
enum class ShadowElevation(val cssClass: String, val elevationDp: Float) {
    NONE("shadow-none", 0f),
    SMALL("shadow-sm", 2f),
    DEFAULT("shadow", 6f),
    LARGE("shadow-lg", 16f);

    companion object {
        val SM: ShadowElevation = SMALL
        val MD: ShadowElevation = DEFAULT
        val LG: ShadowElevation = LARGE

        fun fromCssClass(cssClass: String): ShadowElevation? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
