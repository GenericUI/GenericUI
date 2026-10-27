package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 line-height utilities (`lh-1`, `lh-sm`, `lh-base`, `lh-lg`).
 */
enum class BootstrapLineHeight(val cssClass: String, val multiplier: Float) {
    LH_1("lh-1", 1.0f),
    LH_SM("lh-sm", 1.25f),
    LH_BASE("lh-base", 1.5f),
    LH_LG("lh-lg", 2.0f);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapLineHeight? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
