package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 spacing scale (`0` through `5`, plus `auto`) used for margin (`m-*`, `mt-*`, `mb-*`,
 * `ms-*`, `me-*`, `mx-*`, `my-*`), padding (`p-*`, `pt-*`, `pb-*`, `ps-*`, `pe-*`, `px-*`, `py-*`),
 * and gap (`gap-*`, `row-gap-*`, `column-gap-*`) utilities.
 */
enum class SpacingScale(
    val level: Int,
    val token: String,
    val remMultiplier: Float
) {
    ZERO(0, "0", 0f),
    ONE(1, "1", 0.25f),
    TWO(2, "2", 0.5f),
    THREE(3, "3", 1.0f),
    FOUR(4, "4", 1.5f),
    FIVE(5, "5", 3.0f),
    AUTO(-1, "auto", 0f);

    val isAuto: Boolean
        get() = this == AUTO

    val scaleValue: String
        get() = token

    fun toPixels(baseFontSizePx: Float = 16f): Int =
        if (isAuto) 0 else (remMultiplier * baseFontSizePx).toInt()

    companion object {
        val S0 get() = ZERO
        val S1 get() = ONE
        val S2 get() = TWO
        val S3 get() = THREE
        val S4 get() = FOUR
        val S5 get() = FIVE

        fun fromToken(token: String): SpacingScale? {
            val normalized = token.trim().lowercase()
            if (normalized == "zero") return ZERO
            return entries.firstOrNull { it.token.equals(normalized, ignoreCase = true) }
        }
    }
}
