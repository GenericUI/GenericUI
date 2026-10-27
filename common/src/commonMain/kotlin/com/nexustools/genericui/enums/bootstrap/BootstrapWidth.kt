package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 width utilities (`w-25`, `w-50`, `w-75`, `w-100`, `w-auto`, `mw-100`, `vw-100`, `min-vw-100`).
 */
enum class BootstrapWidth(val cssClass: String, val fraction: Float? = null) {
    W_25("w-25", 0.25f),
    W_50("w-50", 0.50f),
    W_75("w-75", 0.75f),
    W_100("w-100", 1.00f),
    W_AUTO("w-auto", null),
    MW_100("mw-100", null),
    VW_100("vw-100", 1.00f),
    MIN_VW_100("min-vw-100", 1.00f);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapWidth? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
