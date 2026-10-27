package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 object-fit utilities (`object-fit-contain`, `object-fit-cover`, `object-fit-fill`, `object-fit-scale`, `object-fit-none`).
 */
enum class BootstrapObjectFit(val cssClass: String) {
    CONTAIN("object-fit-contain"),
    COVER("object-fit-cover"),
    FILL("object-fit-fill"),
    SCALE("object-fit-scale"),
    NONE("object-fit-none");

    companion object {
        fun fromCssClass(cssClass: String): BootstrapObjectFit? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
