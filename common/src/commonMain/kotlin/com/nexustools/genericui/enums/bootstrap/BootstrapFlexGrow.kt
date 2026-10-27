package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 flex-grow utilities (`flex-grow-0`, `flex-grow-1`).
 */
enum class BootstrapFlexGrow(val cssClass: String, val factor: Int) {
    GROW_0("flex-grow-0", 0),
    GROW_1("flex-grow-1", 1);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapFlexGrow? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
