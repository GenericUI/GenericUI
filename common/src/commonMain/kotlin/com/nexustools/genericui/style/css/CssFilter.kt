package com.nexustools.genericui.style.css

/**
 * Strongly-typed CSS `filter` and `backdrop-filter` pipeline (`blur()`, `brightness()`, `contrast()`,
 * `grayscale()`, `hue-rotate()`, `invert()`, `opacity()`, `saturate()`, `sepia()`, `drop-shadow()`).
 */
data class CssFilter(
    val blur: CssLength? = null,
    val brightness: Float? = null,
    val contrast: Float? = null,
    val grayscale: Float? = null,
    val hueRotateDeg: Float? = null,
    val invert: Float? = null,
    val opacity: Float? = null,
    val saturate: Float? = null,
    val sepia: Float? = null,
    val dropShadow: CssShadow? = null,
    val isNone: Boolean = false
) : CssValue {

    fun interpolateTo(other: CssFilter, t: Float): CssFilter {
        val clamped = t.coerceIn(0f, 1f)
        if (isNone && other.isNone) return NONE
        return CssFilter(
            blur = interpolateLength(blur, other.blur, clamped),
            brightness = interpolateScalar(brightness, other.brightness, 1f, clamped),
            contrast = interpolateScalar(contrast, other.contrast, 1f, clamped),
            grayscale = interpolateScalar(grayscale, other.grayscale, 0f, clamped),
            hueRotateDeg = interpolateScalar(hueRotateDeg, other.hueRotateDeg, 0f, clamped),
            invert = interpolateScalar(invert, other.invert, 0f, clamped),
            opacity = interpolateScalar(opacity, other.opacity, 1f, clamped),
            saturate = interpolateScalar(saturate, other.saturate, 1f, clamped),
            sepia = interpolateScalar(sepia, other.sepia, 0f, clamped),
            dropShadow = if (clamped < 0.5f) dropShadow else other.dropShadow,
            isNone = false
        )
    }

    private fun interpolateLength(a: CssLength?, b: CssLength?, t: Float): CssLength? {
        if (a == null && b == null) return null
        val av = a?.value ?: 0f
        val bv = b?.value ?: 0f
        val unit = b?.unit ?: a?.unit ?: com.nexustools.genericui.enums.css.CssUnit.PX
        return CssLength(av + (bv - av) * t, unit)
    }

    private fun interpolateScalar(a: Float?, b: Float?, identity: Float, t: Float): Float? {
        if (a == null && b == null) return null
        val av = a ?: identity
        val bv = b ?: identity
        return av + (bv - av) * t
    }

    override fun toCssString(): String {
        if (isNone) return "none"
        val parts = mutableListOf<String>()
        blur?.let { parts.add("blur(${it.toCssString()})") }
        brightness?.let { parts.add("brightness(${formatFloat(it)})") }
        contrast?.let { parts.add("contrast(${formatFloat(it)})") }
        grayscale?.let { parts.add("grayscale(${formatFloat(it)})") }
        hueRotateDeg?.let { parts.add("hue-rotate(${formatFloat(it)}deg)") }
        invert?.let { parts.add("invert(${formatFloat(it)})") }
        opacity?.let { parts.add("opacity(${formatFloat(it)})") }
        saturate?.let { parts.add("saturate(${formatFloat(it)})") }
        sepia?.let { parts.add("sepia(${formatFloat(it)})") }
        dropShadow?.let { parts.add("drop-shadow(${it.toCssString()})") }
        return if (parts.isEmpty()) "none" else parts.joinToString(" ")
    }

    private fun formatFloat(v: Float): String {
        val intPart = v.toInt()
        return if (v == intPart.toFloat()) intPart.toString() else v.toString()
    }

    companion object {
        val NONE = CssFilter(isNone = true)

        fun blur(radius: CssLength): CssFilter = CssFilter(blur = radius)

        fun brightness(factor: Float): CssFilter = CssFilter(brightness = factor)

        fun contrast(factor: Float): CssFilter = CssFilter(contrast = factor)

        fun grayscale(amount: Float): CssFilter = CssFilter(grayscale = amount)

        fun parse(raw: String?): CssFilter? {
            val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (cleaned.equals("none", ignoreCase = true)) return NONE

            var blur: CssLength? = null
            var brightness: Float? = null
            var contrast: Float? = null
            var grayscale: Float? = null
            var hueRotateDeg: Float? = null
            var invert: Float? = null
            var opacity: Float? = null
            var saturate: Float? = null
            var sepia: Float? = null
            var dropShadow: CssShadow? = null
            var anyMatched = false

            val fnRegex = Regex("([a-zA-Z-]+)\\(([^)]*)\\)")
            for (match in fnRegex.findAll(cleaned)) {
                val fnName = match.groupValues[1].trim().lowercase()
                val arg = match.groupValues[2].trim()
                when (fnName) {
                    "blur" -> {
                        blur = CssLength.parse(arg)
                        if (blur != null) anyMatched = true
                    }
                    "brightness" -> {
                        brightness = parseRatioOrPercent(arg)
                        if (brightness != null) anyMatched = true
                    }
                    "contrast" -> {
                        contrast = parseRatioOrPercent(arg)
                        if (contrast != null) anyMatched = true
                    }
                    "grayscale" -> {
                        grayscale = parseRatioOrPercent(arg)
                        if (grayscale != null) anyMatched = true
                    }
                    "hue-rotate" -> {
                        hueRotateDeg = parseAngleDeg(arg)
                        if (hueRotateDeg != null) anyMatched = true
                    }
                    "invert" -> {
                        invert = parseRatioOrPercent(arg)
                        if (invert != null) anyMatched = true
                    }
                    "opacity" -> {
                        opacity = parseRatioOrPercent(arg)
                        if (opacity != null) anyMatched = true
                    }
                    "saturate" -> {
                        saturate = parseRatioOrPercent(arg)
                        if (saturate != null) anyMatched = true
                    }
                    "sepia" -> {
                        sepia = parseRatioOrPercent(arg)
                        if (sepia != null) anyMatched = true
                    }
                    "drop-shadow" -> {
                        dropShadow = CssShadow.parse(arg)
                        if (dropShadow != null) anyMatched = true
                    }
                }
            }

            if (!anyMatched) return null
            return CssFilter(
                blur = blur,
                brightness = brightness,
                contrast = contrast,
                grayscale = grayscale,
                hueRotateDeg = hueRotateDeg,
                invert = invert,
                opacity = opacity,
                saturate = saturate,
                sepia = sepia,
                dropShadow = dropShadow,
                isNone = false
            )
        }

        private fun parseRatioOrPercent(raw: String): Float? {
            val clean = raw.trim()
            if (clean.endsWith("%")) {
                val pct = clean.removeSuffix("%").trim().toFloatOrNull() ?: return null
                return pct / 100f
            }
            return clean.toFloatOrNull()
        }

        private fun parseAngleDeg(raw: String): Float? {
            val clean = raw.trim().lowercase()
            return when {
                clean.endsWith("deg") -> clean.removeSuffix("deg").trim().toFloatOrNull()
                clean.endsWith("turn") -> clean.removeSuffix("turn").trim().toFloatOrNull()?.times(360f)
                clean.endsWith("rad") -> clean.removeSuffix("rad").trim().toFloatOrNull()?.times(57.29578f)
                else -> clean.toFloatOrNull()
            }
        }
    }
}
