package com.nexustools.genericui.style.css

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.PixelFormat
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    fun applyToRgbaBytes(rgbaBytes: ByteArray, width: Int, height: Int): ByteArray =
        FastBackdropBlurKernel.applyColorOpsToRgbaBytes(this, rgbaBytes, width, height)

    val hasBlur: Boolean
        get() = !isNone && (blur?.resolvePx()?.let { it > 0f } == true)

    val hasPixelOps: Boolean
        get() = !isNone && (
            (grayscale != null && grayscale != 0f) ||
                (sepia != null && sepia != 0f) ||
                (saturate != null && saturate != 1f) ||
                (hueRotateDeg != null && hueRotateDeg != 0f) ||
                (invert != null && invert != 0f) ||
                (brightness != null && brightness != 1f) ||
                (contrast != null && contrast != 1f) ||
                (opacity != null && opacity != 1f)
            )

    val hasActiveEffects: Boolean
        get() = !isNone && (hasBlur || hasPixelOps)

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

    /**
     * Applies a fast 3-pass separable box blur approximating a CSS Gaussian `blur(radiusPx)`
     * to an `RGBA_8888` byte array, using clamp-to-edge sampling and alpha-weighted RGB mixing.
     */
    fun applyBlurToRgbaBytes(
        rgbaBytes: ByteArray,
        width: Int,
        height: Int,
        radiusPx: Float = blur?.resolvePx() ?: 0f
    ): ByteArray = FastBackdropBlurKernel.applyBlurToRgbaBytes(this, rgbaBytes, width, height, radiusPx)

    /**
     * Applies full `backdrop-filter` processing (Gaussian-approximated blur followed by pixel color/opacity
     * adjustments) to an `RGBA_8888` byte array.
     */
    fun applyBackdropToRgbaBytes(rgbaBytes: ByteArray, width: Int, height: Int): ByteArray =
        FastBackdropBlurKernel.applyBackdropToRgbaBytes(this, rgbaBytes, width, height)

    /**
     * Applies this [CssFilter] in-place as a `backdrop-filter` on a rectangular (optionally rounded)
     * region of an `0xAARRGGBB` `IntArray` framebuffer, capturing padded neighbor context around the
     * region so blurred edges sample real surrounding pixels without edge darkening.
     */
    fun applyBackdropToArgbRegion(
        argbPixels: IntArray,
        bufferWidth: Int,
        bufferHeight: Int,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        cornerRadiusPx: Float = 0f
    ): Boolean = FastBackdropBlurKernel.applyBackdropToArgbRegion(
        filter = this,
        argbPixels = argbPixels,
        bufferWidth = bufferWidth,
        bufferHeight = bufferHeight,
        regionX = regionX,
        regionY = regionY,
        regionWidth = regionWidth,
        regionHeight = regionHeight,
        cornerRadiusPx = cornerRadiusPx
    )

    /**
     * Applies this [CssFilter] as a `backdrop-filter` on a rectangular (optionally rounded) region
     * of an [ImageBuffer], returning a new `RGBA_8888` [ImageBuffer] (or [buffer] if inactive/empty).
     */
    fun applyBackdropToImageBufferRegion(
        buffer: ImageBuffer,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int,
        cornerRadiusPx: Float = 0f
    ): ImageBuffer {
        if (!hasActiveEffects) return buffer
        val rgba = buffer.asRgba8888()
        val w = rgba.width
        val h = rgba.height
        if (w <= 0 || h <= 0) return buffer
        val argb = IntArray(w * h)
        var sIdx = 0
        for (p in argb.indices) {
            val r = rgba.bytes[sIdx].toInt() and 0xFF
            val g = rgba.bytes[sIdx + 1].toInt() and 0xFF
            val b = rgba.bytes[sIdx + 2].toInt() and 0xFF
            val a = rgba.bytes[sIdx + 3].toInt() and 0xFF
            argb[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
            sIdx += 4
        }
        if (!applyBackdropToArgbRegion(argb, w, h, regionX, regionY, regionWidth, regionHeight, cornerRadiusPx)) {
            return buffer
        }
        val outBytes = ByteArray(w * h * 4)
        var dIdx = 0
        for (px in argb) {
            outBytes[dIdx] = ((px ushr 16) and 0xFF).toByte()
            outBytes[dIdx + 1] = ((px ushr 8) and 0xFF).toByte()
            outBytes[dIdx + 2] = (px and 0xFF).toByte()
            outBytes[dIdx + 3] = ((px ushr 24) and 0xFF).toByte()
            dIdx += 4
        }
        return ImageBuffer(
            width = w,
            height = h,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = rgba.colorSpace,
            bytes = outBytes
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

        /**
         * Resolves an effective [CssFilter] from legacy `backdropBlurPx` and `backdropFilter`.
         */
        fun backdropBlurOrFilter(backdropBlurPx: Float?, backdropFilter: CssFilter?): CssFilter? {
            if (backdropFilter?.isNone == true) return null
            val blurPx = backdropBlurPx?.takeIf { it > 0f }
            val resolved = when {
                backdropFilter != null && backdropFilter.blur == null && blurPx != null ->
                    backdropFilter.copy(blur = CssLength.px(blurPx))
                backdropFilter != null -> backdropFilter
                blurPx != null -> blur(CssLength.px(blurPx))
                else -> null
            }
            return resolved?.takeIf { it.hasActiveEffects }
        }

        /**
         * Computes 3 box-blur half-radii approximating a Gaussian kernel with standard deviation [sigmaPx].
         */
        fun boxBlurRadiiForGaussianSigma(sigmaPx: Float, passes: Int = 3): IntArray {
            val sigma = sigmaPx.coerceIn(0f, 48f)
            if (sigma <= 0f || passes <= 0) return IntArray(0)
            val n = passes
            val wIdeal = sqrt((12f * sigma * sigma / n.toFloat()) + 1f)
            var wl = floor(wIdeal).toInt()
            if (wl % 2 == 0) wl--
            if (wl < 1) wl = 1
            val wu = wl + 2
            val mIdeal = (12f * sigma * sigma - n * wl * wl - 4f * n * wl - 3f * n) / (-4f * wl - 4f)
            val m = mIdeal.roundToInt().coerceIn(0, n)
            val result = IntArray(n) { idx ->
                val width = if (idx < m) wl else wu
                ((width - 1) / 2).coerceAtLeast(0)
            }
            if (result.all { it == 0 }) {
                result[result.lastIndex] = 1
            }
            return result
        }

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
