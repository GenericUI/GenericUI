package com.nexustools.genericui.style.color

/**
 * RGBA color representation with hex, `rgb()`/`rgba()`, and CSS named color parsing and formatting utilities.
 */
data class RgbaColor(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int = 255
) {
    constructor(r: Int, g: Int, b: Int, a: Float) : this(
        r = r.coerceIn(0, 255),
        g = g.coerceIn(0, 255),
        b = b.coerceIn(0, 255),
        a = (a.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
    )

    val alphaFloat: Float
        get() = a.coerceIn(0, 255) / 255f

    fun toHex(includeAlpha: Boolean = a < 255): String {
        fun hex2(v: Int) = v.coerceIn(0, 255).toString(16).padStart(2, '0')
        return if (includeAlpha) {
            "#${hex2(r)}${hex2(g)}${hex2(b)}${hex2(a)}"
        } else {
            "#${hex2(r)}${hex2(g)}${hex2(b)}"
        }
    }

    fun toHexString(includeAlpha: Boolean = a < 255): String = toHex(includeAlpha)

    fun toCssRgbaString(): String =
        if (a >= 255) "rgb($r, $g, $b)" else "rgba($r, $g, $b, ${(alphaFloat * 1000f).toInt() / 1000f})"

    fun toCssRgba(): String = toCssRgbaString()

    fun withAlpha(alphaFloat: Float): RgbaColor =
        RgbaColor(r = r, g = g, b = b, a = alphaFloat)

    fun toArgbInt(): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    companion object {
        val TRANSPARENT = RgbaColor(0, 0, 0, 0)
        val BLACK = RgbaColor(0, 0, 0, 255)
        val WHITE = RgbaColor(255, 255, 255, 255)
        val PRIMARY_BLUE = RgbaColor(13, 110, 253, 255)
        val RED = RgbaColor(220, 53, 69, 255)
        val GREEN = RgbaColor(25, 135, 84, 255)
        val BLUE = RgbaColor(13, 110, 253, 255)
        val GOLD = RgbaColor(255, 215, 0, 255)
        val SILVER = RgbaColor(192, 192, 192, 255)
        val COPPER = RgbaColor(184, 115, 51, 255)

        private val NAMED_COLORS: Map<String, RgbaColor> = mapOf(
            "transparent" to TRANSPARENT,
            "black" to BLACK,
            "white" to WHITE,
            "red" to RgbaColor(255, 0, 0, 255),
            "green" to RgbaColor(0, 128, 0, 255),
            "blue" to RgbaColor(0, 0, 255, 255),
            "yellow" to RgbaColor(255, 255, 0, 255),
            "cyan" to RgbaColor(0, 255, 255, 255),
            "magenta" to RgbaColor(255, 0, 255, 255),
            "silver" to SILVER,
            "gold" to GOLD,
            "copper" to COPPER,
            "gray" to RgbaColor(128, 128, 128, 255),
            "grey" to RgbaColor(128, 128, 128, 255),
            "orange" to RgbaColor(255, 165, 0, 255),
            "purple" to RgbaColor(128, 0, 128, 255)
        )

        /**
         * Parses any CSS color expression (`#rgb`, `#rrggbb`, `#rrggbbaa`, `rgb(...)`, `rgba(...)`, or named color).
         */
        fun fromCss(cssColor: String): RgbaColor? {
            val cleaned = cssColor
                .replace("!important", "", ignoreCase = true)
                .trim()
            if (cleaned.isEmpty()) return null

            val lower = cleaned.lowercase()
            NAMED_COLORS[lower]?.let { return it }

            if (lower.startsWith("rgba(") && lower.endsWith(")")) {
                val parts = lower.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
                if (parts.size == 4) {
                    val r = parts[0].toIntOrNull() ?: return null
                    val g = parts[1].toIntOrNull() ?: return null
                    val b = parts[2].toIntOrNull() ?: return null
                    val aStr = parts[3]
                    val aFloat = if (aStr.endsWith("%")) {
                        (aStr.removeSuffix("%").toFloatOrNull() ?: 100f) / 100f
                    } else {
                        aStr.toFloatOrNull() ?: 1f
                    }
                    return RgbaColor(r, g, b, aFloat)
                }
            }

            if (lower.startsWith("rgb(") && lower.endsWith(")")) {
                val parts = lower.removePrefix("rgb(").removeSuffix(")").split(",").map { it.trim() }
                if (parts.size == 3) {
                    val r = parts[0].toIntOrNull() ?: return null
                    val g = parts[1].toIntOrNull() ?: return null
                    val b = parts[2].toIntOrNull() ?: return null
                    return RgbaColor(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), 255)
                }
            }

            if (cleaned.startsWith("#")) {
                val hex = cleaned.removePrefix("#")
                return when (hex.length) {
                    3 -> {
                        val r = hex.substring(0, 1).repeat(2).toIntOrNull(16) ?: return null
                        val g = hex.substring(1, 2).repeat(2).toIntOrNull(16) ?: return null
                        val b = hex.substring(2, 3).repeat(2).toIntOrNull(16) ?: return null
                        RgbaColor(r, g, b, 255)
                    }
                    4 -> {
                        val r = hex.substring(0, 1).repeat(2).toIntOrNull(16) ?: return null
                        val g = hex.substring(1, 2).repeat(2).toIntOrNull(16) ?: return null
                        val b = hex.substring(2, 3).repeat(2).toIntOrNull(16) ?: return null
                        val a = hex.substring(3, 4).repeat(2).toIntOrNull(16) ?: return null
                        RgbaColor(r, g, b, a)
                    }
                    6 -> {
                        val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                        val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                        val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                        RgbaColor(r, g, b, 255)
                    }
                    8 -> {
                        val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                        val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                        val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                        val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                        RgbaColor(r, g, b, a)
                    }
                    else -> null
                }
            }

            return null
        }

        fun fromHex(hex: String): RgbaColor {
            val trimmed = hex.trim()
            val withHash = if (trimmed.startsWith("#") || trimmed.startsWith("rgb", ignoreCase = true)) {
                trimmed
            } else {
                NAMED_COLORS[trimmed.lowercase()]?.let { return it }
                "#$trimmed"
            }
            return fromCss(withHash) ?: BLACK
        }
    }
}
