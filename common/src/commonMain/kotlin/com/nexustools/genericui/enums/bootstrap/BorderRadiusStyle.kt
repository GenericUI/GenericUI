package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 border radius style classes (`rounded-0`..`rounded-5`, `rounded-circle`, `rounded-pill`).
 */
enum class BorderRadiusStyle(val cssClass: String, val radiusPx: Int) {
    NONE("rounded-0", 0),
    SMALL("rounded-1", 4),
    DEFAULT("rounded", 6),
    MEDIUM("rounded-2", 6),
    LARGE("rounded-3", 8),
    EXTRA_LARGE("rounded-4", 16),
    XXL("rounded-5", 32),
    CIRCLE("rounded-circle", 9999),
    PILL("rounded-pill", 9999);

    companion object {
        val ROUNDED get() = DEFAULT
        val ROUNDED_0 get() = NONE
        val ROUNDED_1 get() = SMALL
        val ROUNDED_2 get() = MEDIUM
        val ROUNDED_3 get() = LARGE
        val ROUNDED_4 get() = EXTRA_LARGE
        val ROUNDED_5 get() = XXL

        fun fromCssClass(cssClass: String): BorderRadiusStyle? =
            entries.firstOrNull { it.cssClass.equals(cssClass.trim(), ignoreCase = true) }
    }
}
