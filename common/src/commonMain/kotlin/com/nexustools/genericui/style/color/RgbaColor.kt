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

    constructor(r: Int, g: Int, b: Int, a: Double) : this(
        r = r.coerceIn(0, 255),
        g = g.coerceIn(0, 255),
        b = b.coerceIn(0, 255),
        a = (a.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt().coerceIn(0, 255)
    )

    /**
     * Blends this color as a native-control tint over [baseSurfaceColor] by [strength] (`0.0..1.0`),
     * preserving the native control's luminance/bevel while tinting its hue/saturation.
     */
    fun blendOverSurface(baseSurfaceColor: RgbaColor, strength: Float = 0.45f): RgbaColor {
        val t = (strength * alphaFloat).coerceIn(0f, 1f)
        val inv = 1f - t
        return RgbaColor(
            r = (baseSurfaceColor.r * inv + r * t).toInt().coerceIn(0, 255),
            g = (baseSurfaceColor.g * inv + g * t).toInt().coerceIn(0, 255),
            b = (baseSurfaceColor.b * inv + b * t).toInt().coerceIn(0, 255),
            a = baseSurfaceColor.a
        )
    }

    /**
     * Lightens (`amount > 0`) or darkens (`amount < 0`) this color for hover/active state feedback.
     */
    fun adjustBrightness(amount: Int): RgbaColor = RgbaColor(
        r = (r + amount).coerceIn(0, 255),
        g = (g + amount).coerceIn(0, 255),
        b = (b + amount).coerceIn(0, 255),
        a = a
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

    fun toCssRgbaString(): String =
        if (a >= 255) "rgb($r, $g, $b)" else "rgba($r, $g, $b, ${(alphaFloat * 1000f).toInt() / 1000f})"

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
            "aqua" to RgbaColor(0, 255, 255, 255),
            "magenta" to RgbaColor(255, 0, 255, 255),
            "fuchsia" to RgbaColor(255, 0, 255, 255),
            "silver" to SILVER,
            "gold" to GOLD,
            "copper" to COPPER,
            "gray" to RgbaColor(128, 128, 128, 255),
            "grey" to RgbaColor(128, 128, 128, 255),
            "orange" to RgbaColor(255, 165, 0, 255),
            "purple" to RgbaColor(128, 0, 128, 255),
            "aliceblue" to RgbaColor(240, 248, 255, 255),
            "antiquewhite" to RgbaColor(250, 235, 215, 255),
            "aquamarine" to RgbaColor(127, 255, 212, 255),
            "azure" to RgbaColor(240, 255, 255, 255),
            "beige" to RgbaColor(245, 245, 220, 255),
            "bisque" to RgbaColor(255, 228, 196, 255),
            "blanchedalmond" to RgbaColor(255, 235, 205, 255),
            "blueviolet" to RgbaColor(138, 43, 226, 255),
            "brown" to RgbaColor(165, 42, 42, 255),
            "burlywood" to RgbaColor(222, 184, 135, 255),
            "cadetblue" to RgbaColor(95, 158, 160, 255),
            "chartreuse" to RgbaColor(127, 255, 0, 255),
            "chocolate" to RgbaColor(210, 105, 30, 255),
            "coral" to RgbaColor(255, 127, 80, 255),
            "cornflowerblue" to RgbaColor(100, 149, 237, 255),
            "cornsilk" to RgbaColor(255, 248, 220, 255),
            "crimson" to RgbaColor(220, 20, 60, 255),
            "darkblue" to RgbaColor(0, 0, 139, 255),
            "darkcyan" to RgbaColor(0, 139, 139, 255),
            "darkgoldenrod" to RgbaColor(184, 134, 11, 255),
            "darkgray" to RgbaColor(169, 169, 169, 255),
            "darkgrey" to RgbaColor(169, 169, 169, 255),
            "darkgreen" to RgbaColor(0, 100, 0, 255),
            "darkkhaki" to RgbaColor(189, 183, 107, 255),
            "darkmagenta" to RgbaColor(139, 0, 139, 255),
            "darkolivegreen" to RgbaColor(85, 107, 47, 255),
            "darkorange" to RgbaColor(255, 140, 0, 255),
            "darkorchid" to RgbaColor(153, 50, 204, 255),
            "darkred" to RgbaColor(139, 0, 0, 255),
            "darksalmon" to RgbaColor(233, 150, 122, 255),
            "darkseagreen" to RgbaColor(143, 188, 143, 255),
            "darkslateblue" to RgbaColor(72, 61, 139, 255),
            "darkslategray" to RgbaColor(47, 79, 79, 255),
            "darkslategrey" to RgbaColor(47, 79, 79, 255),
            "darkturquoise" to RgbaColor(0, 206, 209, 255),
            "darkviolet" to RgbaColor(148, 0, 211, 255),
            "deeppink" to RgbaColor(255, 20, 147, 255),
            "deepskyblue" to RgbaColor(0, 191, 255, 255),
            "dimgray" to RgbaColor(105, 105, 105, 255),
            "dimgrey" to RgbaColor(105, 105, 105, 255),
            "dodgerblue" to RgbaColor(30, 144, 255, 255),
            "firebrick" to RgbaColor(178, 34, 34, 255),
            "floralwhite" to RgbaColor(255, 250, 240, 255),
            "forestgreen" to RgbaColor(34, 139, 34, 255),
            "gainsboro" to RgbaColor(220, 220, 220, 255),
            "ghostwhite" to RgbaColor(248, 248, 255, 255),
            "goldenrod" to RgbaColor(218, 165, 32, 255),
            "greenyellow" to RgbaColor(173, 255, 47, 255),
            "honeydew" to RgbaColor(240, 255, 240, 255),
            "hotpink" to RgbaColor(255, 105, 180, 255),
            "indianred" to RgbaColor(205, 92, 92, 255),
            "indigo" to RgbaColor(75, 0, 130, 255),
            "ivory" to RgbaColor(255, 255, 240, 255),
            "khaki" to RgbaColor(240, 230, 140, 255),
            "lavender" to RgbaColor(230, 230, 250, 255),
            "lavenderblush" to RgbaColor(255, 240, 245, 255),
            "lawngreen" to RgbaColor(124, 252, 0, 255),
            "lemonchiffon" to RgbaColor(255, 250, 205, 255),
            "lightblue" to RgbaColor(173, 216, 230, 255),
            "lightcoral" to RgbaColor(240, 128, 128, 255),
            "lightcyan" to RgbaColor(224, 255, 255, 255),
            "lightgoldenrodyellow" to RgbaColor(250, 250, 210, 255),
            "lightgray" to RgbaColor(211, 211, 211, 255),
            "lightgrey" to RgbaColor(211, 211, 211, 255),
            "lightgreen" to RgbaColor(144, 238, 144, 255),
            "lightpink" to RgbaColor(255, 182, 193, 255),
            "lightsalmon" to RgbaColor(255, 160, 122, 255),
            "lightseagreen" to RgbaColor(32, 178, 170, 255),
            "lightskyblue" to RgbaColor(135, 206, 250, 255),
            "lightslategray" to RgbaColor(119, 136, 153, 255),
            "lightslategrey" to RgbaColor(119, 136, 153, 255),
            "lightsteelblue" to RgbaColor(176, 196, 222, 255),
            "lightyellow" to RgbaColor(255, 255, 224, 255),
            "lime" to RgbaColor(0, 255, 0, 255),
            "limegreen" to RgbaColor(50, 205, 50, 255),
            "linen" to RgbaColor(250, 240, 230, 255),
            "maroon" to RgbaColor(128, 0, 0, 255),
            "mediumaquamarine" to RgbaColor(102, 205, 170, 255),
            "mediumblue" to RgbaColor(0, 0, 205, 255),
            "mediumorchid" to RgbaColor(186, 85, 211, 255),
            "mediumpurple" to RgbaColor(147, 112, 219, 255),
            "mediumseagreen" to RgbaColor(60, 179, 113, 255),
            "mediumslateblue" to RgbaColor(123, 104, 238, 255),
            "mediumspringgreen" to RgbaColor(0, 250, 154, 255),
            "mediumturquoise" to RgbaColor(72, 209, 204, 255),
            "mediumvioletred" to RgbaColor(199, 21, 133, 255),
            "midnightblue" to RgbaColor(25, 25, 112, 255),
            "mintcream" to RgbaColor(245, 255, 250, 255),
            "mistyrose" to RgbaColor(255, 228, 225, 255),
            "moccasin" to RgbaColor(255, 228, 181, 255),
            "navajowhite" to RgbaColor(255, 222, 173, 255),
            "navy" to RgbaColor(0, 0, 128, 255),
            "oldlace" to RgbaColor(253, 245, 230, 255),
            "olive" to RgbaColor(128, 128, 0, 255),
            "olivedrab" to RgbaColor(107, 142, 35, 255),
            "orangered" to RgbaColor(255, 69, 0, 255),
            "orchid" to RgbaColor(218, 112, 214, 255),
            "palegoldenrod" to RgbaColor(238, 232, 170, 255),
            "palegreen" to RgbaColor(152, 251, 152, 255),
            "paleturquoise" to RgbaColor(175, 238, 238, 255),
            "palevioletred" to RgbaColor(219, 112, 147, 255),
            "papayawhip" to RgbaColor(255, 239, 213, 255),
            "peachpuff" to RgbaColor(255, 218, 185, 255),
            "peru" to RgbaColor(205, 133, 63, 255),
            "pink" to RgbaColor(255, 192, 203, 255),
            "plum" to RgbaColor(221, 160, 221, 255),
            "powderblue" to RgbaColor(176, 224, 230, 255),
            "rebeccapurple" to RgbaColor(102, 51, 153, 255),
            "rosybrown" to RgbaColor(188, 143, 143, 255),
            "royalblue" to RgbaColor(65, 105, 225, 255),
            "saddlebrown" to RgbaColor(139, 69, 19, 255),
            "salmon" to RgbaColor(250, 128, 114, 255),
            "sandybrown" to RgbaColor(244, 164, 96, 255),
            "seagreen" to RgbaColor(46, 139, 87, 255),
            "seashell" to RgbaColor(255, 245, 238, 255),
            "sienna" to RgbaColor(160, 82, 45, 255),
            "skyblue" to RgbaColor(135, 206, 235, 255),
            "slateblue" to RgbaColor(106, 90, 205, 255),
            "slategray" to RgbaColor(112, 128, 144, 255),
            "slategrey" to RgbaColor(112, 128, 144, 255),
            "snow" to RgbaColor(255, 250, 250, 255),
            "springgreen" to RgbaColor(0, 255, 127, 255),
            "steelblue" to RgbaColor(70, 130, 180, 255),
            "tan" to RgbaColor(210, 180, 140, 255),
            "teal" to RgbaColor(0, 128, 128, 255),
            "thistle" to RgbaColor(216, 191, 216, 255),
            "tomato" to RgbaColor(255, 99, 71, 255),
            "turquoise" to RgbaColor(64, 224, 208, 255),
            "violet" to RgbaColor(238, 130, 238, 255),
            "wheat" to RgbaColor(245, 222, 179, 255),
            "whitesmoke" to RgbaColor(245, 245, 245, 255),
            "yellowgreen" to RgbaColor(154, 205, 50, 255)
        )

        /**
         * Parses any CSS color expression (`#rgb`, `#rrggbb`, `#rgba`, `#rrggbbaa`, `rgb()`, `rgba()`,
         * `hsl()`, `hsla()`, CSS Color 4 space syntax, `currentColor`, or W3C named color).
         */
        fun fromCss(cssColor: String): RgbaColor? = fromCss(cssColor, currentColor = null)

        fun fromCss(cssColor: String, currentColor: RgbaColor?): RgbaColor? {
            val cleaned = cssColor
                .replace("!important", "", ignoreCase = true)
                .trim()
            if (cleaned.isEmpty()) return null

            val lower = cleaned.lowercase()
            if (lower == "currentcolor") return currentColor
            NAMED_COLORS[lower]?.let { return it }

            if ((lower.startsWith("rgba(") || lower.startsWith("rgb(")) && lower.endsWith(")")) {
                val inner = lower.substringAfter("(").removeSuffix(")").trim()
                return parseRgbFunction(inner)
            }

            if ((lower.startsWith("hsla(") || lower.startsWith("hsl(")) && lower.endsWith(")")) {
                val inner = lower.substringAfter("(").removeSuffix(")").trim()
                return parseHslFunction(inner)
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

        private fun parseAlphaChannel(raw: String): Float? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            return if (s.endsWith("%")) {
                val pct = s.removeSuffix("%").trim().toFloatOrNull() ?: return null
                (pct / 100f).coerceIn(0f, 1f)
            } else {
                s.toFloatOrNull()?.coerceIn(0f, 1f)
            }
        }

        private fun parseRgbChannel(raw: String): Int? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            return if (s.endsWith("%")) {
                val pct = s.removeSuffix("%").trim().toFloatOrNull() ?: return null
                ((pct / 100f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            } else {
                val v = s.toFloatOrNull() ?: return null
                (v + 0.5f).toInt().coerceIn(0, 255)
            }
        }

        private fun splitCssFunctionalArgs(inner: String): Pair<List<String>, String?>? {
            if (',' in inner) {
                val commaParts = inner.split(',').map { it.trim() }
                if (commaParts.size == 3) return commaParts to null
                if (commaParts.size == 4) return commaParts.subList(0, 3) to commaParts[3]
                return null
            }
            val slashIdx = inner.indexOf('/')
            val mainPart = if (slashIdx >= 0) inner.substring(0, slashIdx).trim() else inner.trim()
            val alphaPart = if (slashIdx >= 0) inner.substring(slashIdx + 1).trim() else null
            val spaceParts = mainPart.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (spaceParts.size != 3) return null
            return spaceParts to alphaPart
        }

        private fun parseRgbFunction(inner: String): RgbaColor? {
            val (channels, alphaRaw) = splitCssFunctionalArgs(inner) ?: return null
            val r = parseRgbChannel(channels[0]) ?: return null
            val g = parseRgbChannel(channels[1]) ?: return null
            val b = parseRgbChannel(channels[2]) ?: return null
            val a = if (alphaRaw != null) (parseAlphaChannel(alphaRaw) ?: return null) else 1f
            return RgbaColor(r, g, b, a)
        }

        private fun parseHueDegrees(raw: String): Float? {
            val s = raw.trim().lowercase()
            val deg = when {
                s.endsWith("deg") -> s.removeSuffix("deg").trim().toFloatOrNull()
                s.endsWith("rad") -> s.removeSuffix("rad").trim().toFloatOrNull()?.let { it * 57.29578f }
                s.endsWith("turn") -> s.removeSuffix("turn").trim().toFloatOrNull()?.let { it * 360f }
                s.endsWith("grad") -> s.removeSuffix("grad").trim().toFloatOrNull()?.let { it * 0.9f }
                else -> s.toFloatOrNull()
            } ?: return null
            var norm = deg % 360f
            if (norm < 0f) norm += 360f
            return norm
        }

        private fun parseHslFunction(inner: String): RgbaColor? {
            val (channels, alphaRaw) = splitCssFunctionalArgs(inner) ?: return null
            val h = parseHueDegrees(channels[0]) ?: return null
            val sRaw = channels[1].trim()
            val lRaw = channels[2].trim()
            val s = ((sRaw.removeSuffix("%").trim().toFloatOrNull() ?: return null) / 100f).coerceIn(0f, 1f)
            val l = ((lRaw.removeSuffix("%").trim().toFloatOrNull() ?: return null) / 100f).coerceIn(0f, 1f)
            val a = if (alphaRaw != null) (parseAlphaChannel(alphaRaw) ?: return null) else 1f
            val hNorm = h / 360f
            val rF: Float
            val gF: Float
            val bF: Float
            if (s == 0f) {
                rF = l
                gF = l
                bF = l
            } else {
                val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
                val p = 2f * l - q
                rF = hueToRgb(p, q, hNorm + 1f / 3f)
                gF = hueToRgb(p, q, hNorm)
                bF = hueToRgb(p, q, hNorm - 1f / 3f)
            }
            return RgbaColor(
                r = (rF * 255f + 0.5f).toInt().coerceIn(0, 255),
                g = (gF * 255f + 0.5f).toInt().coerceIn(0, 255),
                b = (bF * 255f + 0.5f).toInt().coerceIn(0, 255),
                a = a
            )
        }

        private fun hueToRgb(p: Float, q: Float, tRaw: Float): Float {
            var t = tRaw
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 0.5f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }

        fun fromHex(hex: String): RgbaColor {
            val trimmed = hex.trim()
            val lower = trimmed.lowercase()
            val withHash = if (
                trimmed.startsWith("#") ||
                lower.startsWith("rgb") ||
                lower.startsWith("hsl")
            ) {
                trimmed
            } else {
                NAMED_COLORS[lower]?.let { return it }
                "#$trimmed"
            }
            return fromCss(withHash) ?: BLACK
        }
    }
}
