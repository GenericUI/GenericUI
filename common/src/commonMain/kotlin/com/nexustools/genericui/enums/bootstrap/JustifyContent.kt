package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 main-axis alignment utilities (`justify-content-start`, `end`, `center`, `between`, `around`, `evenly`).
 */
enum class JustifyContent(val cssClass: String) {
    START("justify-content-start"),
    END("justify-content-end"),
    CENTER("justify-content-center"),
    BETWEEN("justify-content-between"),
    AROUND("justify-content-around"),
    EVENLY("justify-content-evenly");

    companion object {
        fun fromCssClass(cssClass: String): JustifyContent? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
