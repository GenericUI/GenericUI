package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 text overflow & wrapping behavior (`text-wrap`, `text-nowrap`, `text-truncate`).
 */
enum class TextOverflow(val cssClass: String) {
    WRAP("text-wrap"),
    NOWRAP("text-nowrap"),
    ELLIPSIS("text-truncate");

    companion object {
        fun fromCssClass(cssClass: String): TextOverflow? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
