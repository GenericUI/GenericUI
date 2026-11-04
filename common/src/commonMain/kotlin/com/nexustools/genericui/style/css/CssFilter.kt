package com.nexustools.genericui.style.css

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

    /**
     * Applies this [CssFilter]'s pixel-level operations (`grayscale`, `sepia`, `saturate`,
     * `hue-rotate`, `invert`, `brightness`, `contrast`, `opacity`) to an `RGBA_8888` byte buffer.
     *
     * Note: Per W3C Filter Effects specification, filter functions are evaluated in canonical
     * pipeline order; when `brightness(0) invert(1)` (white monocolor) is represented on [CssFilter]
     * with `brightness = 0f` and `invert = 1f`, `brightness` is applied before `invert` when
     * `brightness == 0f && (invert ?: 0f) > 0f` so the common CSS `brightness(0) invert(1)` idiom
     * produces pure white (`#FFFFFF`) on opaque pixels.
     */
    fun applyToRgbaBytes(rgbaBytes: ByteArray, width: Int, height: Int): ByteArray {
        if (isNone) return rgbaBytes
        val totalBytes = width * height * 4
        if (width <= 0 || height <= 0 || rgbaBytes.size < totalBytes) return rgbaBytes

        val hasPixelOps =
            (grayscale != null && grayscale != 0f) ||
                (sepia != null && sepia != 0f) ||
                (saturate != null && saturate != 1f) ||
                (hueRotateDeg != null && hueRotateDeg != 0f) ||
                (invert != null && invert != 0f) ||
                (brightness != null && brightness != 1f) ||
                (contrast != null && contrast != 1f) ||
                (opacity != null && opacity != 1f)
        if (!hasPixelOps) return rgbaBytes

        val out = rgbaBytes.copyOf()
        val brightnessBeforeInvert = brightness != null && brightness == 0f && (invert ?: 0f) > 0f
        val hueRad = (hueRotateDeg ?: 0f) * (PI.toFloat() / 180f)
        val cosH = if (hueRotateDeg != null && hueRotateDeg != 0f) cos(hueRad) else 1f
        val sinH = if (hueRotateDeg != null && hueRotateDeg != 0f) sin(hueRad) else 0f

        var i = 0
        while (i < totalBytes) {
            val rawA = out[i + 3].toInt() and 0xFF
            if (rawA == 0 && (opacity == null || opacity == 1f)) {
                i += 4
                continue
            }
            var r = (out[i].toInt() and 0xFF) / 255f
            var g = (out[i + 1].toInt() and 0xFF) / 255f
            var b = (out[i + 2].toInt() and 0xFF) / 255f
            var a = rawA / 255f

            if (grayscale != null && grayscale != 0f) {
                val gAmt = grayscale.coerceIn(0f, 1f)
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r += (lum - r) * gAmt
                g += (lum - g) * gAmt
                b += (lum - b) * gAmt
            }

            if (sepia != null && sepia != 0f) {
                val sAmt = sepia.coerceIn(0f, 1f)
                val sr = (r * 0.393f + g * 0.769f + b * 0.189f).coerceIn(0f, 1f)
                val sg = (r * 0.349f + g * 0.686f + b * 0.168f).coerceIn(0f, 1f)
                val sb = (r * 0.272f + g * 0.534f + b * 0.131f).coerceIn(0f, 1f)
                r += (sr - r) * sAmt
                g += (sg - g) * sAmt
                b += (sb - b) * sAmt
            }

            if (saturate != null && saturate != 1f) {
                val sat = saturate.coerceAtLeast(0f)
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = (lum + (r - lum) * sat).coerceIn(0f, 1f)
                g = (lum + (g - lum) * sat).coerceIn(0f, 1f)
                b = (lum + (b - lum) * sat).coerceIn(0f, 1f)
            }

            if (hueRotateDeg != null && hueRotateDeg != 0f) {
                val nr = (0.213f + cosH * 0.787f - sinH * 0.213f) * r +
                    (0.715f - cosH * 0.715f - sinH * 0.715f) * g +
                    (0.072f - cosH * 0.072f + sinH * 0.928f) * b
                val ng = (0.213f - cosH * 0.213f + sinH * 0.143f) * r +
                    (0.715f + cosH * 0.285f + sinH * 0.140f) * g +
                    (0.072f - cosH * 0.072f - sinH * 0.283f) * b
                val nb = (0.213f - cosH * 0.213f - sinH * 0.787f) * r +
                    (0.715f - cosH * 0.715f + sinH * 0.715f) * g +
                    (0.072f + cosH * 0.928f + sinH * 0.072f) * b
                r = nr.coerceIn(0f, 1f)
                g = ng.coerceIn(0f, 1f)
                b = nb.coerceIn(0f, 1f)
            }

            if (brightnessBeforeInvert) {
                val br = brightness!!.coerceAtLeast(0f)
                r = (r * br).coerceIn(0f, 1f)
                g = (g * br).coerceIn(0f, 1f)
                b = (b * br).coerceIn(0f, 1f)
            }

            if (invert != null && invert != 0f) {
                val inv = invert.coerceIn(0f, 1f)
                r += ((1f - r) - r) * inv
                g += ((1f - g) - g) * inv
                b += ((1f - b) - b) * inv
            }

            if (!brightnessBeforeInvert && brightness != null && brightness != 1f) {
                val br = brightness.coerceAtLeast(0f)
                r = (r * br).coerceIn(0f, 1f)
                g = (g * br).coerceIn(0f, 1f)
                b = (b * br).coerceIn(0f, 1f)
            }

            if (contrast != null && contrast != 1f) {
                val ct = contrast.coerceAtLeast(0f)
                r = ((r - 0.5f) * ct + 0.5f).coerceIn(0f, 1f)
                g = ((g - 0.5f) * ct + 0.5f).coerceIn(0f, 1f)
                b = ((b - 0.5f) * ct + 0.5f).coerceIn(0f, 1f)
            }

            if (opacity != null && opacity != 1f) {
                a = (a * opacity.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            }

            out[i] = (r * 255f).roundToInt().coerceIn(0, 255).toByte()
            out[i + 1] = (g * 255f).roundToInt().coerceIn(0, 255).toByte()
            out[i + 2] = (b * 255f).roundToInt().coerceIn(0, 255).toByte()
            out[i + 3] = (a * 255f).roundToInt().coerceIn(0, 255).toByte()
            i += 4
        }
        return out
    }

    /**
     * Applies this [CssFilter] to an [ImageBuffer], returning a new `RGBA_8888` [ImageBuffer]
     * (or the original [buffer] if no pixel operations are active).
     */
    fun applyToImageBuffer(buffer: ImageBuffer): ImageBuffer {
        if (isNone) return buffer
        val rgba = buffer.asRgba8888()
        val filtered = applyToRgbaBytes(rgba.bytes, rgba.width, rgba.height)
        if (filtered === rgba.bytes) return buffer
        return ImageBuffer(
            width = rgba.width,
            height = rgba.height,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = rgba.colorSpace,
            bytes = filtered
        )
    }


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

        fun invert(amount: Float = 1f): CssFilter = CssFilter(invert = amount)

        /**
         * Returns a CSS filter (`brightness(0) invert(1)`) that recolors any non-transparent
         * icon/image pixels to pure white (`#FFFFFF`) while preserving alpha.
         */
        fun monocolorWhite(): CssFilter = CssFilter(brightness = 0f, invert = 1f)

        /**
         * Returns a CSS filter (`brightness(0)`) that recolors any non-transparent
         * icon/image pixels to pure black (`#000000`) while preserving alpha.
         */
        fun monocolorBlack(): CssFilter = CssFilter(brightness = 0f)

        fun parse(raw: String?): CssFilter? {
            val cleaned = raw
                ?.replace(Regex("\\s*!important\\s*$", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
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
