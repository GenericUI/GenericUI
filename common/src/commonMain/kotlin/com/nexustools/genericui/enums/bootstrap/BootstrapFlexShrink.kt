package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 flex-shrink utilities (`flex-shrink-0`, `flex-shrink-1`).
 */
enum class BootstrapFlexShrink(val cssClass: String, val factor: Int) {
    SHRINK_0("flex-shrink-0", 0),
    SHRINK_1("flex-shrink-1", 1);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapFlexShrink? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
