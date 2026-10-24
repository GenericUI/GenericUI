package com.nexustools.genericui.style.transition

/**
 * Parsed 2D CSS transform (`translate`, `scale`, `rotate`) supporting interpolation
 * during CSS transitions and window-manager-style container buffer compositing.
 */
data class CssTransform2D(
    val translateX: Float = 0f,
    val translateXIsPercent: Boolean = false,
    val translateY: Float = 0f,
    val translateYIsPercent: Boolean = false,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotateDeg: Float = 0f
) {
    fun interpolateTo(target: CssTransform2D, progress: Float): CssTransform2D {
        val t = progress.coerceIn(0f, 1f)
        return CssTransform2D(
            translateX = lerp(translateX, target.translateX, t),
            translateXIsPercent = if (t < 0.5f) translateXIsPercent else target.translateXIsPercent,
            translateY = lerp(translateY, target.translateY, t),
            translateYIsPercent = if (t < 0.5f) translateYIsPercent else target.translateYIsPercent,
            scaleX = lerp(scaleX, target.scaleX, t),
            scaleY = lerp(scaleY, target.scaleY, t),
            rotateDeg = lerp(rotateDeg, target.rotateDeg, t)
        )
    }

    fun resolveTranslationPx(widthPx: Int, heightPx: Int): Pair<Float, Float> {
        val dx = if (translateXIsPercent) (translateX / 100f) * widthPx else translateX
        val dy = if (translateYIsPercent) (translateY / 100f) * heightPx else translateY
        return dx to dy
    }

    fun toCssString(): String {
        val parts = mutableListOf<String>()
        if (translateX != 0f || translateY != 0f || translateXIsPercent || translateYIsPercent) {
            val xUnit = if (translateXIsPercent) "%" else "px"
            val yUnit = if (translateYIsPercent) "%" else "px"
            parts.add("translate(${formatFloat(translateX)}$xUnit, ${formatFloat(translateY)}$yUnit)")
        }
        if (scaleX != 1f || scaleY != 1f) {
            parts.add("scale(${formatFloat(scaleX)}, ${formatFloat(scaleY)})")
        }
        if (rotateDeg != 0f) {
            parts.add("rotate(${formatFloat(rotateDeg)}deg)")
        }
        return if (parts.isEmpty()) "none" else parts.joinToString(" ")
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun formatFloat(v: Float): String {
        val asInt = v.toInt()
        return if (v == asInt.toFloat()) asInt.toString() else v.toString()
    }

    companion object {
        val IDENTITY = CssTransform2D()

        fun parse(raw: String?): CssTransform2D {
            if (raw.isNullOrBlank()) return IDENTITY
            val cleaned = raw.replace("!important", "", ignoreCase = true).trim()
            if (cleaned.isEmpty() || cleaned.equals("none", ignoreCase = true)) return IDENTITY

            var tx = 0f
            var txPct = false
            var ty = 0f
            var tyPct = false
            var sx = 1f
            var sy = 1f
            var rot = 0f

            val fnRegex = Regex("""([a-zA-Z0-9]+)\(([^)]*)\)""")
            for (match in fnRegex.findAll(cleaned)) {
                val fnName = match.groupValues[1].lowercase()
                val args = match.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
                when (fnName) {
                    "translate", "translate3d" -> {
                        if (args.isNotEmpty()) {
                            val (v, pct) = parseOffset(args[0])
                            tx = v
                            txPct = pct
                        }
                        if (args.size >= 2) {
                            val (v, pct) = parseOffset(args[1])
                            ty = v
                            tyPct = pct
                        }
                    }
                    "translatex" -> if (args.isNotEmpty()) {
                        val (v, pct) = parseOffset(args[0])
                        tx = v
                        txPct = pct
                    }
                    "translatey" -> if (args.isNotEmpty()) {
                        val (v, pct) = parseOffset(args[0])
                        ty = v
                        tyPct = pct
                    }
                    "scale", "scale3d" -> {
                        if (args.isNotEmpty()) {
                            sx = args[0].toFloatOrNull() ?: 1f
                            sy = if (args.size >= 2) (args[1].toFloatOrNull() ?: sx) else sx
                        }
                    }
                    "scalex" -> if (args.isNotEmpty()) {
                        sx = args[0].toFloatOrNull() ?: 1f
                    }
                    "scaley" -> if (args.isNotEmpty()) {
                        sy = args[0].toFloatOrNull() ?: 1f
                    }
                    "rotate", "rotatez" -> if (args.isNotEmpty()) {
                        val a = args[0].lowercase()
                        rot = when {
                            a.endsWith("deg") -> a.removeSuffix("deg").trim().toFloatOrNull() ?: 0f
                            a.endsWith("rad") -> ((a.removeSuffix("rad").trim().toFloatOrNull() ?: 0f) * 180f / 3.1415927f)
                            a.endsWith("turn") -> (a.removeSuffix("turn").trim().toFloatOrNull() ?: 0f) * 360f
                            else -> a.toFloatOrNull() ?: 0f
                        }
                    }
                }
            }

            return CssTransform2D(
                translateX = tx,
                translateXIsPercent = txPct,
                translateY = ty,
                translateYIsPercent = tyPct,
                scaleX = sx,
                scaleY = sy,
                rotateDeg = rot
            )
        }

        private fun parseOffset(token: String): Pair<Float, Boolean> {
            val t = token.trim().lowercase()
            return when {
                t.endsWith("%") -> (t.removeSuffix("%").trim().toFloatOrNull() ?: 0f) to true
                t.endsWith("px") -> (t.removeSuffix("px").trim().toFloatOrNull() ?: 0f) to false
                else -> (t.toFloatOrNull() ?: 0f) to false
            }
        }
    }
}
