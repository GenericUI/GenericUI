package com.nexustools.genericui.enums.bootstrap

/**
 * Bootstrap 5 spacing scale (`0` through `5`, plus `auto`) used for margin (`m-*`) and padding (`p-*`) helpers.
 */
enum class SpacingScale(val token: String, val remMultiplier: Float) {
    ZERO("0", 0f),
    ONE("1", 0.25f),
    TWO("2", 0.5f),
    THREE("3", 1.0f),
    FOUR("4", 1.5f),
    FIVE("5", 3.0f),
    AUTO("auto", 0f);

    fun toPixels(baseFontSizePx: Float = 16f): Int = (remMultiplier * baseFontSizePx).toInt()

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
