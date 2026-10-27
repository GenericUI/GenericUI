package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 visibility utilities (`visible`, `invisible`).
 */
enum class BootstrapVisibility(val cssClass: String) {
    VISIBLE("visible"),
    INVISIBLE("invisible");

    companion object {
        fun fromCssClass(cssClass: String): BootstrapVisibility? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
