package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 float utilities (`float-start`, `float-end`, `float-none`).
 */
enum class BootstrapFloat(val cssClass: String) {
    START("float-start"),
    END("float-end"),
    NONE("float-none");

    companion object {
        fun fromCssClass(cssClass: String): BootstrapFloat? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
