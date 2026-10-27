package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 border width utilities (`border-0` .. `border-5`).
 */
enum class BootstrapBorderWidth(val cssClass: String, val widthPx: Int) {
    BORDER_0("border-0", 0),
    BORDER_1("border-1", 1),
    BORDER_2("border-2", 2),
    BORDER_3("border-3", 3),
    BORDER_4("border-4", 4),
    BORDER_5("border-5", 5);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapBorderWidth? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
