package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.res.ResourceType
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Strongly-typed custom cursor specification (`url(...) hotspotX hotspotY, fallback`) supporting
 * PNG/bitmap [ResourceID]s, SVG vector [ResourceID]s, inline [svgMarkup], in-memory [ImageBuffer]s,
 * and URL/path references.
 *
 * Provides [resolveToImageBuffer] so platform backends always receive a valid 32-bit RGBA
 * [ImageBuffer] regardless of whether the cursor was supplied as a PNG resource, SVG resource,
 * inline SVG markup, or raw [ImageBuffer].
 */
data class CssCustomCursor(
    val resourceId: ResourceID? = null,
    val imageBuffer: ImageBuffer? = null,
    val svgMarkup: String? = null,
    val urlOrPath: String? = null,
    val widthPx: Int = 32,
    val heightPx: Int = 32,
    val hotspotX: Int = 0,
    val hotspotY: Int = 0,
    val fallback: CssCursor = CssCursor.AUTO
) : CssCursorValue {

    /**
     * Resolves or rasterizes this custom cursor into an RGBA_8888 [ImageBuffer] of size
     * `[widthPx] x [heightPx]` (or the source image's intrinsic dimensions when backed by a raster bitmap).
     */
    fun resolveToImageBuffer(): ImageBuffer? {
        imageBuffer?.let { return it.asRgba8888() }

        val targetW = widthPx.coerceIn(1, 256)
        val targetH = heightPx.coerceIn(1, 256)

        if (resourceId != null) {
            ResourceResolver.getImageBuffer(resourceId)?.let { return it.asRgba8888() }
            ResourceResolver.getDrawable(resourceId)?.let { drawable ->
                rasterizeDrawable(drawable, targetW, targetH)?.let { return it }
            }
            ResourceResolver.getBytes(resourceId)?.let { rawBytes ->
                if (rawBytes.isNotEmpty()) {
                    ImageLoader.loadFromBytes(rawBytes)?.let { return it.asRgba8888() }
                    val text = rawBytes.decodeToString()
                    if (text.contains("<svg", ignoreCase = true)) {
                        return rasterizeSvgMarkup(text, targetW, targetH)
                    }
                }
            }
        }

        if (!svgMarkup.isNullOrBlank()) {
            return rasterizeSvgMarkup(svgMarkup, targetW, targetH)
        }

        if (!urlOrPath.isNullOrBlank()) {
            val trimmed = urlOrPath.trim()
            if (trimmed.contains("<svg", ignoreCase = true)) {
                return rasterizeSvgMarkup(trimmed, targetW, targetH)
            }
            Resources.getDrawable(trimmed)?.let { drawable ->
                rasterizeDrawable(drawable, targetW, targetH)?.let { return it }
            }
            val normalizedName = trimmed
                .removePrefix("res://drawable/")
                .removePrefix("@drawable/")
                .substringAfterLast('/')
                .substringBeforeLast('.')
            Resources.activeTable.findResourceId(ResourceType.DRAWABLE, normalizedName)?.let { foundId ->
                ResourceResolver.getImageBuffer(foundId)?.let { return it.asRgba8888() }
                ResourceResolver.getDrawable(foundId)?.let { drawable ->
                    rasterizeDrawable(drawable, targetW, targetH)?.let { return it }
                }
            }
        }

        return null
    }

    override fun toCssString(): String {
        val resolvedUrl = when {
            !urlOrPath.isNullOrBlank() -> urlOrPath.trim()
            resourceId != null -> ResourceResolver.getUrl(resourceId)
            !svgMarkup.isNullOrBlank() -> "data:image/svg+xml;utf8,${svgMarkup.trim().replace("\n", " ")}"
            else -> "cursor.png"
        }
        val clampedHotX = hotspotX.coerceAtLeast(0)
        val clampedHotY = hotspotY.coerceAtLeast(0)
        return "url('$resolvedUrl') $clampedHotX $clampedHotY, ${fallback.toCssString()}"
    }

    companion object {
        fun fromResource(
            resourceId: ResourceID,
            hotspotX: Int = 0,
            hotspotY: Int = 0,
            widthPx: Int = 32,
            heightPx: Int = 32,
            fallback: CssCursor = CssCursor.AUTO
        ): CssCustomCursor = CssCustomCursor(
            resourceId = resourceId,
            widthPx = widthPx,
            heightPx = heightPx,
            hotspotX = hotspotX,
            hotspotY = hotspotY,
            fallback = fallback
        )

        fun fromImageBuffer(
            imageBuffer: ImageBuffer,
            hotspotX: Int = 0,
            hotspotY: Int = 0,
            fallback: CssCursor = CssCursor.AUTO
        ): CssCustomCursor = CssCustomCursor(
            imageBuffer = imageBuffer,
            widthPx = imageBuffer.width,
            heightPx = imageBuffer.height,
            hotspotX = hotspotX,
            hotspotY = hotspotY,
            fallback = fallback
        )

        fun fromSvg(
            svgMarkup: String,
            widthPx: Int = 32,
            heightPx: Int = 32,
            hotspotX: Int = 0,
            hotspotY: Int = 0,
            fallback: CssCursor = CssCursor.AUTO
        ): CssCustomCursor = CssCustomCursor(
            svgMarkup = svgMarkup,
            widthPx = widthPx,
            heightPx = heightPx,
            hotspotX = hotspotX,
            hotspotY = hotspotY,
            fallback = fallback
        )

        fun fromUrl(
            urlOrPath: String,
            hotspotX: Int = 0,
            hotspotY: Int = 0,
            widthPx: Int = 32,
            heightPx: Int = 32,
            fallback: CssCursor = CssCursor.AUTO
        ): CssCustomCursor = CssCustomCursor(
            urlOrPath = urlOrPath,
            widthPx = widthPx,
            heightPx = heightPx,
            hotspotX = hotspotX,
            hotspotY = hotspotY,
            fallback = fallback
        )

        /**
         * Parses a CSS `cursor` declaration into either a standard [CssCursor] keyword or a [CssCustomCursor].
         */
        fun parse(rawCss: String?): CssCursorValue? {
            val cleaned = rawCss
                ?.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            CssCursor.fromCss(cleaned)?.let { return it }

            val fallbackPart = cleaned.substringAfterLast(',', "").trim()
            val fallbackCursor = CssCursor.fromCss(fallbackPart) ?: CssCursor.AUTO
            val primaryPart = if (cleaned.contains(',')) cleaned.substringBeforeLast(',').trim() else cleaned

            val urlMatch = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)(.*)", RegexOption.IGNORE_CASE)
                .find(primaryPart)
            if (urlMatch != null) {
                val extractedUrl = urlMatch.groupValues[1].trim()
                val restCoords = urlMatch.groupValues[2].trim()
                    .split(Regex("\\s+"))
                    .mapNotNull { it.toIntOrNull() }
                val hotX = restCoords.getOrElse(0) { 0 }
                val hotY = restCoords.getOrElse(1) { 0 }
                val resId = resolveDrawableResourceId(extractedUrl)
                return CssCustomCursor(
                    resourceId = resId,
                    urlOrPath = extractedUrl,
                    hotspotX = hotX,
                    hotspotY = hotY,
                    fallback = fallbackCursor
                )
            }

            if (primaryPart.startsWith("@drawable/") || primaryPart.startsWith("res://drawable/")) {
                val tokens = primaryPart.split(Regex("\\s+"))
                val ref = tokens[0]
                val hotX = tokens.getOrNull(1)?.toIntOrNull() ?: 0
                val hotY = tokens.getOrNull(2)?.toIntOrNull() ?: 0
                val resId = resolveDrawableResourceId(ref)
                return CssCustomCursor(
                    resourceId = resId,
                    urlOrPath = ref,
                    hotspotX = hotX,
                    hotspotY = hotY,
                    fallback = fallbackCursor
                )
            }

            return null
        }

        private fun resolveDrawableResourceId(ref: String): ResourceID? {
            val trimmed = ref.trim()
            Resources.getDrawable(trimmed)?.let { return it.resId }
            val name = trimmed
                .removePrefix("res://drawable/")
                .removePrefix("@drawable/")
                .substringAfterLast('/')
                .substringBeforeLast('.')
            return Resources.activeTable.findResourceId(ResourceType.DRAWABLE, name)
        }

        private fun rasterizeDrawable(drawable: DrawableResource, widthPx: Int, heightPx: Int): ImageBuffer? {
            Resources.activeTable.getImageBuffer(drawable.resId)?.let { return it.asRgba8888() }
            if (!drawable.svgSource.isNullOrBlank()) {
                return rasterizeSvgMarkup(
                    svgMarkup = drawable.svgSource,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    defaultViewBoxW = drawable.viewBoxWidth.takeIf { it > 0f } ?: 24f,
                    defaultViewBoxH = drawable.viewBoxHeight.takeIf { it > 0f } ?: 24f
                )
            }
            if (drawable.svgPathDataList.isNotEmpty()) {
                val syntheticSvg = buildString {
                    append("<svg viewBox=\"0 0 ${drawable.viewBoxWidth} ${drawable.viewBoxHeight}\">")
                    for (pathD in drawable.svgPathDataList) {
                        append("<path d=\"$pathD\" fill=\"#ffffff\" stroke=\"#000000\" stroke-width=\"1.5\"/>")
                    }
                    append("</svg>")
                }
                return rasterizeSvgMarkup(
                    svgMarkup = syntheticSvg,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    defaultViewBoxW = drawable.viewBoxWidth.takeIf { it > 0f } ?: 24f,
                    defaultViewBoxH = drawable.viewBoxHeight.takeIf { it > 0f } ?: 24f
                )
            }
            return null
        }

        internal fun rasterizeSvgMarkup(
            svgMarkup: String,
            widthPx: Int,
            heightPx: Int,
            defaultViewBoxW: Float = 24f,
            defaultViewBoxH: Float = 24f
        ): ImageBuffer {
            val w = widthPx.coerceIn(1, 256)
            val h = heightPx.coerceIn(1, 256)
            val buffer = ImageBuffer(w, h, PixelFormat.RGBA_8888)

            var vbW = defaultViewBoxW
            var vbH = defaultViewBoxH
            Regex("viewBox\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                .find(svgMarkup)?.groupValues?.get(1)?.let { vbStr ->
                    val nums = vbStr.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                    if (nums.size >= 4 && nums[2] > 0f && nums[3] > 0f) {
                        vbW = nums[2]
                        vbH = nums[3]
                    }
                }

            val scaleX = w.toFloat() / vbW
            val scaleY = h.toFloat() / vbH
            val defaultColor = RgbaColor(255, 255, 255, 255)
            val outlineColor = RgbaColor(0, 0, 0, 255)
            var drewAnyShape = false

            // 1. Rasterize <circle cx="..." cy="..." r="..." .../>
            for (match in Regex("<circle\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(svgMarkup)) {
                val attrs = match.groupValues[1]
                val cx = extractFloatAttr(attrs, "cx") ?: (vbW * 0.5f)
                val cy = extractFloatAttr(attrs, "cy") ?: (vbH * 0.5f)
                val r = extractFloatAttr(attrs, "r") ?: continue
                val fill = extractColorAttr(attrs, "fill")
                val stroke = extractColorAttr(attrs, "stroke") ?: defaultColor
                val strokeWidth = (extractFloatAttr(attrs, "stroke-width") ?: 1.5f) * ((scaleX + scaleY) * 0.5f)
                val pxCx = cx * scaleX
                val pxCy = cy * scaleY
                val pxR = r * ((scaleX + scaleY) * 0.5f)
                drawCircle(buffer, pxCx, pxCy, pxR, fill, stroke, strokeWidth.coerceAtLeast(1f))
                drewAnyShape = true
            }

            // 2. Rasterize <rect x="..." y="..." width="..." height="..." .../>
            for (match in Regex("<rect\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(svgMarkup)) {
                val attrs = match.groupValues[1]
                val rx = (extractFloatAttr(attrs, "x") ?: 0f) * scaleX
                val ry = (extractFloatAttr(attrs, "y") ?: 0f) * scaleY
                val rw = (extractFloatAttr(attrs, "width") ?: continue) * scaleX
                val rh = (extractFloatAttr(attrs, "height") ?: continue) * scaleY
                val fill = extractColorAttr(attrs, "fill") ?: defaultColor
                val stroke = extractColorAttr(attrs, "stroke")
                drawRect(buffer, rx, ry, rw, rh, fill, stroke)
                drewAnyShape = true
            }

            // 3. Rasterize <line x1="..." y1="..." x2="..." y2="..." .../>
            for (match in Regex("<line\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(svgMarkup)) {
                val attrs = match.groupValues[1]
                val x1 = (extractFloatAttr(attrs, "x1") ?: 0f) * scaleX
                val y1 = (extractFloatAttr(attrs, "y1") ?: 0f) * scaleY
                val x2 = (extractFloatAttr(attrs, "x2") ?: 0f) * scaleX
                val y2 = (extractFloatAttr(attrs, "y2") ?: 0f) * scaleY
                val stroke = extractColorAttr(attrs, "stroke") ?: extractColorAttr(attrs, "fill") ?: defaultColor
                val strokeW = (extractFloatAttr(attrs, "stroke-width") ?: 1.5f) * ((scaleX + scaleY) * 0.5f)
                drawThickLine(buffer, x1, y1, x2, y2, stroke, strokeW.coerceAtLeast(1.2f))
                drewAnyShape = true
            }

            // 4. Rasterize <polygon points="..." /> and <polyline points="..." />
            for (match in Regex("<poly(gon|line)\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(svgMarkup)) {
                val isPolygon = match.groupValues[1].equals("gon", ignoreCase = true)
                val attrs = match.groupValues[2]
                val ptsRaw = extractStringAttr(attrs, "points") ?: continue
                val coords = Regex("-?\\d+(?:\\.\\d+)?").findAll(ptsRaw).mapNotNull { it.value.toFloatOrNull() }.toList()
                if (coords.size >= 4) {
                    val stroke = extractColorAttr(attrs, "stroke") ?: extractColorAttr(attrs, "fill") ?: defaultColor
                    val strokeW = (extractFloatAttr(attrs, "stroke-width") ?: 1.5f) * ((scaleX + scaleY) * 0.5f)
                    var i = 0
                    while (i + 3 < coords.size) {
                        drawThickLine(
                            buffer,
                            coords[i] * scaleX,
                            coords[i + 1] * scaleY,
                            coords[i + 2] * scaleX,
                            coords[i + 3] * scaleY,
                            stroke,
                            strokeW.coerceAtLeast(1.2f)
                        )
                        i += 2
                    }
                    if (isPolygon && coords.size >= 6) {
                        drawThickLine(
                            buffer,
                            coords[coords.size - 2] * scaleX,
                            coords[coords.size - 1] * scaleY,
                            coords[0] * scaleX,
                            coords[1] * scaleY,
                            stroke,
                            strokeW.coerceAtLeast(1.2f)
                        )
                    }
                    drewAnyShape = true
                }
            }

            // 5. Rasterize <path d="..." .../>
            for (match in Regex("<path\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(svgMarkup)) {
                val attrs = match.groupValues[1]
                val d = extractStringAttr(attrs, "d") ?: continue
                val color = extractColorAttr(attrs, "stroke")
                    ?: extractColorAttr(attrs, "fill")
                    ?: defaultColor
                val strokeW = (extractFloatAttr(attrs, "stroke-width") ?: 1.75f) * ((scaleX + scaleY) * 0.5f)
                if (rasterizeSvgPathD(buffer, d, scaleX, scaleY, color, strokeW.coerceAtLeast(1.25f))) {
                    drewAnyShape = true
                }
            }

            if (!drewAnyShape || buffer.isFullyTransparent()) {
                // Fallback crosshair/pointer glyph so a valid SVG cursor is never completely blank
                val cx = w / 2f
                val cy = h / 2f
                drawThickLine(buffer, 2f, cy, (w - 3).toFloat(), cy, outlineColor, 2.5f)
                drawThickLine(buffer, cx, 2f, cx, (h - 3).toFloat(), outlineColor, 2.5f)
                drawThickLine(buffer, 3f, cy, (w - 4).toFloat(), cy, defaultColor, 1.2f)
                drawThickLine(buffer, cx, 3f, cx, (h - 4).toFloat(), defaultColor, 1.2f)
            }

            return buffer
        }

        private fun rasterizeSvgPathD(
            buffer: ImageBuffer,
            d: String,
            scaleX: Float,
            scaleY: Float,
            color: RgbaColor,
            strokeWidthPx: Float
        ): Boolean {
            val tokens = Regex("([MmLlHhVvCcQqZz])|(-?\\d+(?:\\.\\d+)?)")
                .findAll(d)
                .map { it.value }
                .toList()
            if (tokens.isEmpty()) return false

            var idx = 0
            var cmd = 'M'
            var curX = 0f
            var curY = 0f
            var startX = 0f
            var startY = 0f
            var drew = false

            fun nextFloat(): Float? {
                if (idx >= tokens.size) return null
                val v = tokens[idx].toFloatOrNull() ?: return null
                idx++
                return v
            }

            while (idx < tokens.size) {
                val tok = tokens[idx]
                if (tok.length == 1 && tok[0].isLetter()) {
                    cmd = tok[0]
                    idx++
                    if (cmd == 'Z' || cmd == 'z') {
                        drawThickLine(buffer, curX * scaleX, curY * scaleY, startX * scaleX, startY * scaleY, color, strokeWidthPx)
                        curX = startX
                        curY = startY
                        drew = true
                        continue
                    }
                }

                when (cmd) {
                    'M' -> {
                        val x = nextFloat() ?: break
                        val y = nextFloat() ?: break
                        curX = x
                        curY = y
                        startX = curX
                        startY = curY
                        cmd = 'L'
                    }
                    'm' -> {
                        val dx = nextFloat() ?: break
                        val dy = nextFloat() ?: break
                        curX += dx
                        curY += dy
                        startX = curX
                        startY = curY
                        cmd = 'l'
                    }
                    'L' -> {
                        val x = nextFloat() ?: break
                        val y = nextFloat() ?: break
                        drawThickLine(buffer, curX * scaleX, curY * scaleY, x * scaleX, y * scaleY, color, strokeWidthPx)
                        curX = x
                        curY = y
                        drew = true
                    }
                    'l' -> {
                        val dx = nextFloat() ?: break
                        val dy = nextFloat() ?: break
                        val nx = curX + dx
                        val ny = curY + dy
                        drawThickLine(buffer, curX * scaleX, curY * scaleY, nx * scaleX, ny * scaleY, color, strokeWidthPx)
                        curX = nx
                        curY = ny
                        drew = true
                    }
                    'H' -> {
                        val x = nextFloat() ?: break
                        drawThickLine(buffer, curX * scaleX, curY * scaleY, x * scaleX, curY * scaleY, color, strokeWidthPx)
                        curX = x
                        drew = true
                    }
                    'h' -> {
                        val dx = nextFloat() ?: break
                        val nx = curX + dx
                        drawThickLine(buffer, curX * scaleX, curY * scaleY, nx * scaleX, curY * scaleY, color, strokeWidthPx)
                        curX = nx
                        drew = true
                    }
                    'V' -> {
                        val y = nextFloat() ?: break
                        drawThickLine(buffer, curX * scaleX, curY * scaleY, curX * scaleX, y * scaleY, color, strokeWidthPx)
                        curY = y
                        drew = true
                    }
                    'v' -> {
                        val dy = nextFloat() ?: break
                        val ny = curY + dy
                        drawThickLine(buffer, curX * scaleX, curY * scaleY, curX * scaleX, ny * scaleY, color, strokeWidthPx)
                        curY = ny
                        drew = true
                    }
                    'C', 'c' -> {
                        val rel = cmd == 'c'
                        val x1 = (nextFloat() ?: break) + if (rel) curX else 0f
                        val y1 = (nextFloat() ?: break) + if (rel) curY else 0f
                        val x2 = (nextFloat() ?: break) + if (rel) curX else 0f
                        val y2 = (nextFloat() ?: break) + if (rel) curY else 0f
                        val x = (nextFloat() ?: break) + if (rel) curX else 0f
                        val y = (nextFloat() ?: break) + if (rel) curY else 0f
                        var px = curX
                        var py = curY
                        val steps = 8
                        for (s in 1..steps) {
                            val t = s.toFloat() / steps
                            val u = 1f - t
                            val bx = u * u * u * curX + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x
                            val by = u * u * u * curY + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y
                            drawThickLine(buffer, px * scaleX, py * scaleY, bx * scaleX, by * scaleY, color, strokeWidthPx)
                            px = bx
                            py = by
                        }
                        curX = x
                        curY = y
                        drew = true
                    }
                    'Q', 'q' -> {
                        val rel = cmd == 'q'
                        val x1 = (nextFloat() ?: break) + if (rel) curX else 0f
                        val y1 = (nextFloat() ?: break) + if (rel) curY else 0f
                        val x = (nextFloat() ?: break) + if (rel) curX else 0f
                        val y = (nextFloat() ?: break) + if (rel) curY else 0f
                        var px = curX
                        var py = curY
                        val steps = 6
                        for (s in 1..steps) {
                            val t = s.toFloat() / steps
                            val u = 1f - t
                            val bx = u * u * curX + 2f * u * t * x1 + t * t * x
                            val by = u * u * curY + 2f * u * t * y1 + t * t * y
                            drawThickLine(buffer, px * scaleX, py * scaleY, bx * scaleX, by * scaleY, color, strokeWidthPx)
                            px = bx
                            py = by
                        }
                        curX = x
                        curY = y
                        drew = true
                    }
                    else -> {
                        idx++
                    }
                }
            }
            return drew
        }

        private fun drawCircle(
            buffer: ImageBuffer,
            cx: Float,
            cy: Float,
            radius: Float,
            fill: RgbaColor?,
            stroke: RgbaColor?,
            strokeWidth: Float
        ) {
            val halfStroke = strokeWidth * 0.5f
            val outerR = radius + halfStroke + 1f
            val minX = max(0, (cx - outerR).toInt())
            val maxX = min(buffer.width - 1, (cx + outerR).toInt())
            val minY = max(0, (cy - outerR).toInt())
            val maxY = min(buffer.height - 1, (cy + outerR).toInt())
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    if (fill != null && dist <= radius) {
                        buffer.setPixel(x, y, fill)
                    }
                    if (stroke != null && abs(dist - radius) <= max(0.75f, halfStroke)) {
                        buffer.setPixel(x, y, stroke)
                    }
                }
            }
        }

        private fun drawRect(
            buffer: ImageBuffer,
            rx: Float,
            ry: Float,
            rw: Float,
            rh: Float,
            fill: RgbaColor?,
            stroke: RgbaColor?
        ) {
            val x0 = rx.roundToInt().coerceIn(0, buffer.width - 1)
            val y0 = ry.roundToInt().coerceIn(0, buffer.height - 1)
            val x1 = (rx + rw).roundToInt().coerceIn(0, buffer.width - 1)
            val y1 = (ry + rh).roundToInt().coerceIn(0, buffer.height - 1)
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val isEdge = x == x0 || x == x1 || y == y0 || y == y1
                    if (isEdge && stroke != null) {
                        buffer.setPixel(x, y, stroke)
                    } else if (fill != null) {
                        buffer.setPixel(x, y, fill)
                    }
                }
            }
        }

        private fun drawThickLine(
            buffer: ImageBuffer,
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            color: RgbaColor,
            thickness: Float
        ) {
            val dx = x1 - x0
            val dy = y1 - y0
            val steps = max(abs(dx), abs(dy)).toInt().coerceAtLeast(1) * 2
            val radius = (thickness * 0.5f).coerceAtLeast(0.6f)
            val rCeil = (radius + 0.5f).toInt()
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val px = x0 + dx * t
                val py = y0 + dy * t
                val cx = px.roundToInt()
                val cy = py.roundToInt()
                for (oy in -rCeil..rCeil) {
                    for (ox in -rCeil..rCeil) {
                        if (ox * ox + oy * oy <= (radius + 0.5f) * (radius + 0.5f)) {
                            buffer.setPixel(cx + ox, cy + oy, color)
                        }
                    }
                }
            }
        }

        private fun extractStringAttr(attrs: String, name: String): String? =
            Regex("\\b$name\\s*=\\s*['\"]([^'\"]*)['\"]", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)

        private fun extractFloatAttr(attrs: String, name: String): Float? =
            extractStringAttr(attrs, name)?.trim()?.removeSuffix("px")?.toFloatOrNull()

        private fun extractColorAttr(attrs: String, name: String): RgbaColor? {
            val raw = extractStringAttr(attrs, name)?.trim() ?: return null
            if (raw.equals("none", ignoreCase = true) || raw.equals("transparent", ignoreCase = true)) {
                return null
            }
            if (raw.equals("currentColor", ignoreCase = true)) {
                return RgbaColor.WHITE
            }
            return RgbaColor.fromCss(raw)
        }
    }
}
