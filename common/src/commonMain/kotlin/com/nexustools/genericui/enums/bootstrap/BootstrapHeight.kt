package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 height utilities (`h-25`, `h-50`, `h-75`, `h-100`, `h-auto`, `mh-100`, `vh-100`, `min-vh-100`).
 */
enum class BootstrapHeight(val cssClass: String, val fraction: Float? = null) {
    H_25("h-25", 0.25f),
    H_50("h-50", 0.50f),
    H_75("h-75", 0.75f),
    H_100("h-100", 1.00f),
    H_AUTO("h-auto", null),
    MH_100("mh-100", null),
    VH_100("vh-100", 1.00f),
    MIN_VH_100("min-vh-100", 1.00f);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapHeight? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
