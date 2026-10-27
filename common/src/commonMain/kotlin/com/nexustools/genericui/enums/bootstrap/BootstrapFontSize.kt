package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 font size scale utilities (`fs-1` .. `fs-6`).
 */
enum class BootstrapFontSize(val cssClass: String, val sizePx: Float) {
    FS_1("fs-1", 40f),
    FS_2("fs-2", 32f),
    FS_3("fs-3", 28f),
    FS_4("fs-4", 24f),
    FS_5("fs-5", 20f),
    FS_6("fs-6", 16f);

    companion object {
        fun fromCssClass(cssClass: String): BootstrapFontSize? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
