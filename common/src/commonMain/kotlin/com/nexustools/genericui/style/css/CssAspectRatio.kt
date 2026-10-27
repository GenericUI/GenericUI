package com.nexustools.genericui.style.css

/**
 * Strongly-typed CSS `aspect-ratio` value (`auto`, `16 / 9`, `1 / 1`, etc.).
 */
data class CssAspectRatio(
    val widthRatio: Float = 1f,
    val heightRatio: Float = 1f,
    val isAuto: Boolean = false
) : CssValue {

    val numericRatio: Float?
        get() = if (isAuto || heightRatio == 0f) null else widthRatio / heightRatio

    override fun toCssString(): String {
        if (isAuto) return "auto"
        val wStr = formatNumber(widthRatio)
        val hStr = formatNumber(heightRatio)
        return "$wStr / $hStr"
    }

    private fun formatNumber(v: Float): String {
        val intPart = v.toInt()
        return if (v == intPart.toFloat()) intPart.toString() else v.toString()
    }

    companion object {
        val AUTO = CssAspectRatio(1f, 1f, isAuto = true)
        val RATIO_1_1 = of(1f, 1f)
        val RATIO_4_3 = of(4f, 3f)
        val RATIO_16_9 = of(16f, 9f)
        val RATIO_21_9 = of(21f, 9f)

        fun of(widthRatio: Float, heightRatio: Float = 1f): CssAspectRatio =
            CssAspectRatio(widthRatio = widthRatio, heightRatio = heightRatio, isAuto = false)

        fun parse(raw: String?): CssAspectRatio? {
            val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (cleaned.equals("auto", ignoreCase = true)) return AUTO
            if (cleaned.contains("/")) {
                val parts = cleaned.split("/").map { it.trim() }
                if (parts.size == 2) {
                    val w = parts[0].toFloatOrNull() ?: return null
                    val h = parts[1].toFloatOrNull() ?: return null
                    return of(w, h)
                }
            }
            val single = cleaned.toFloatOrNull() ?: return null
            return of(single, 1f)
        }
    }
}
