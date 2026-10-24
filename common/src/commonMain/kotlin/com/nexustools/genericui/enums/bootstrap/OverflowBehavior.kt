package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 overflow behavior (`overflow-auto`, `overflow-hidden`, `overflow-visible`, `overflow-scroll`).
 */
enum class OverflowBehavior(val cssClass: String) {
    AUTO("overflow-auto"),
    HIDDEN("overflow-hidden"),
    VISIBLE("overflow-visible"),
    SCROLL("overflow-scroll");

    companion object {
        fun fromCssClass(cssClass: String): OverflowBehavior? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
