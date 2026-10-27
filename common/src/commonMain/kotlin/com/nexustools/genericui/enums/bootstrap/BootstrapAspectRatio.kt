package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 aspect-ratio utilities (`ratio-1x1`, `ratio-4x3`, `ratio-16x9`, `ratio-21x9`).
 */
enum class BootstrapAspectRatio(val cssClass: String, val widthRatio: Float, val heightRatio: Float) {
    RATIO_1X1("ratio-1x1", 1f, 1f),
    RATIO_4X3("ratio-4x3", 4f, 3f),
    RATIO_16X9("ratio-16x9", 16f, 9f),
    RATIO_21X9("ratio-21x9", 21f, 9f);

    val numericRatio: Float get() = widthRatio / heightRatio

    companion object {
        fun fromCssClass(cssClass: String): BootstrapAspectRatio? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
