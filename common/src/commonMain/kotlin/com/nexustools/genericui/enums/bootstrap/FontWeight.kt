package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 font weight utilities (`fw-light`, `fw-normal`, `fw-medium`, `fw-semibold`, `fw-bold`, `fw-bolder`).
 */
enum class FontWeight(val cssClass: String, val numericWeight: Int) {
    LIGHTER("fw-lighter", 200),
    LIGHT("fw-light", 300),
    NORMAL("fw-normal", 400),
    MEDIUM("fw-medium", 500),
    SEMIBOLD("fw-semibold", 600),
    BOLD("fw-bold", 700),
    BOLDER("fw-bolder", 800);

    companion object {
        fun fromCssClass(cssClass: String): FontWeight? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
