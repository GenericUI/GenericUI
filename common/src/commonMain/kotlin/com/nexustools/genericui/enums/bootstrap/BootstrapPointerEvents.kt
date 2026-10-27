package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 pointer-events utilities (`pe-none`, `pe-auto`).
 */
enum class BootstrapPointerEvents(val cssClass: String) {
    NONE("pe-none"),
    AUTO("pe-auto");

    companion object {
        fun fromCssClass(cssClass: String): BootstrapPointerEvents? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
