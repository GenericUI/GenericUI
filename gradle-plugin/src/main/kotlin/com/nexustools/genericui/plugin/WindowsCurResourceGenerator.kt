package com.nexustools.genericui.plugin

import org.w3c.dom.Element
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Build-time generator (`1 Class Per File`) that converts `.png` or `.svg` cursor assets into
 * valid Windows `.cur` binary files (`ICONDIR` with `idType = 2`, `ICONDIRENTRY` with `wXHotSpot`
 * and `wYHotSpot`, 32-bit BGRA DIB + 1-bpp AND mask) under `backends/windows/cursors/<name>.cur`.
 */
object WindowsCurResourceGenerator {

    const val DEFAULT_CURSOR_SIZE: Int = 32

    /**
     * Checks whether [bytes] is already a valid Windows `.cur` binary (`idReserved = 0`, `idType = 2`, `idCount >= 1`).
     */
    fun isCurBinary(bytes: ByteArray): Boolean =
        bytes.size >= 22 &&
            bytes[0] == 0.toByte() &&
            bytes[1] == 0.toByte() &&
            bytes[2] == 2.toByte() &&
            bytes[3] == 0.toByte() &&
            ((bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)) >= 1

    /**
     * Converts a `.cur`, `.png`, or `.svg` cursor file into a valid Windows `.cur` file at [outputFile].
     */
    fun generateCurFile(
        sourceFile: File,
        outputFile: File,
        hotspotX: Int? = null,
        hotspotY: Int? = null,
        targetWidth: Int = DEFAULT_CURSOR_SIZE,
        targetHeight: Int = DEFAULT_CURSOR_SIZE
    ): File {
        outputFile.parentFile?.mkdirs()
        val curBytes = convertFileToCurBytes(
            sourceFile = sourceFile,
            hotspotX = hotspotX,
            hotspotY = hotspotY,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
        outputFile.writeBytes(curBytes)
        return outputFile
    }

    /**
     * Converts a `.cur`, `.png`, or `.svg` [sourceFile] into Windows `.cur` binary bytes.
     */
    fun convertFileToCurBytes(
        sourceFile: File,
        hotspotX: Int? = null,
        hotspotY: Int? = null,
        targetWidth: Int = DEFAULT_CURSOR_SIZE,
        targetHeight: Int = DEFAULT_CURSOR_SIZE
    ): ByteArray {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return encodeBufferedImageToCurBytes(
                image = createFallbackCursorImage(targetWidth, targetHeight),
                hotspotX = hotspotX ?: 0,
                hotspotY = hotspotY ?: 0
            )
        }
        val ext = sourceFile.extension.lowercase()
        val (inferredHx, inferredHy) = extractHotspotFromFileName(sourceFile.nameWithoutExtension)
        val effectiveHx = hotspotX ?: inferredHx
        val effectiveHy = hotspotY ?: inferredHy

        return when (ext) {
            "cur" -> {
                val raw = sourceFile.readBytes()
                if (isCurBinary(raw)) {
                    raw
                } else {
                    encodeBufferedImageToCurBytes(
                        image = createFallbackCursorImage(targetWidth, targetHeight),
                        hotspotX = effectiveHx ?: 0,
                        hotspotY = effectiveHy ?: 0
                    )
                }
            }
            "svg" -> convertSvgToCurBytes(
                svgText = sourceFile.readText(Charsets.UTF_8),
                hotspotX = effectiveHx,
                hotspotY = effectiveHy,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
            else -> convertPngBytesToCurBytes(
                pngBytes = sourceFile.readBytes(),
                hotspotX = effectiveHx ?: 0,
                hotspotY = effectiveHy ?: 0,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        }
    }

    /**
     * Converts PNG (or other raster image) bytes into a Windows `.cur` binary (`ByteArray`).
     */
    fun convertPngBytesToCurBytes(
        pngBytes: ByteArray,
        hotspotX: Int = 0,
        hotspotY: Int = 0,
        targetWidth: Int = DEFAULT_CURSOR_SIZE,
        targetHeight: Int = DEFAULT_CURSOR_SIZE
    ): ByteArray {
        val decoded = runCatching {
            if (pngBytes.isNotEmpty()) ImageIO.read(ByteArrayInputStream(pngBytes)) else null
        }.getOrNull()
        val finalImage = if (decoded != null && decoded.width > 0 && decoded.height > 0) {
            val w = decoded.width.coerceIn(1, 256)
            val h = decoded.height.coerceIn(1, 256)
            if (w == decoded.width && h == decoded.height) decoded else scaleImage(decoded, w, h)
        } else {
            createFallbackCursorImage(targetWidth.coerceIn(1, 256), targetHeight.coerceIn(1, 256))
        }
        return encodeBufferedImageToCurBytes(
            image = finalImage,
            hotspotX = hotspotX,
            hotspotY = hotspotY
        )
    }

    /**
     * Rasterizes an SVG cursor string into a `targetWidth x targetHeight` RGBA image and encodes it
     * as a Windows `.cur` binary (`ByteArray`).
     */
    fun convertSvgToCurBytes(
        svgText: String,
        hotspotX: Int? = null,
        hotspotY: Int? = null,
        targetWidth: Int = DEFAULT_CURSOR_SIZE,
        targetHeight: Int = DEFAULT_CURSOR_SIZE
    ): ByteArray {
        val w = targetWidth.coerceIn(1, 256)
        val h = targetHeight.coerceIn(1, 256)
        val (svgHx, svgHy) = extractHotspotFromSvgMarkup(svgText)
        val resolvedHx = (hotspotX ?: svgHx ?: 0).coerceIn(0, w - 1)
        val resolvedHy = (hotspotY ?: svgHy ?: 0).coerceIn(0, h - 1)
        val rendered = rasterizeSvgCursor(svgText, w, h)
        return encodeBufferedImageToCurBytes(
            image = rendered,
            hotspotX = resolvedHx,
            hotspotY = resolvedHy
        )
    }

    /**
     * Encodes an ARGB [BufferedImage] into a single-entry Windows `.cur` binary (`ByteArray`):
     * - `ICONDIR` (6 bytes): `idReserved = 0`, `idType = 2` (`.cur`), `idCount = 1`
     * - `ICONDIRENTRY` (16 bytes): `bWidth`, `bHeight`, `bColorCount = 0`, `bReserved = 0`,
     *   `wXHotSpot`, `wYHotSpot`, `dwBytesInRes`, `dwImageOffset = 22`
     * - `BITMAPINFOHEADER` (40 bytes) + bottom-up 32-bit BGRA XOR mask + bottom-up 1-bpp AND mask.
     */
    fun encodeBufferedImageToCurBytes(
        image: BufferedImage,
        hotspotX: Int = 0,
        hotspotY: Int = 0
    ): ByteArray {
        val w = image.width.coerceIn(1, 256)
        val h = image.height.coerceIn(1, 256)
        val src = if (w == image.width && h == image.height) image else scaleImage(image, w, h)
        val clampedHx = hotspotX.coerceIn(0, w - 1)
        val clampedHy = hotspotY.coerceIn(0, h - 1)

        val xorSize = w * h * 4
        val andRowBytes = ((w + 31) / 32) * 4
        val andSize = andRowBytes * h
        val dibSize = 40 + xorSize + andSize
        val headerAndDirSize = 6 + 16
        val totalSize = headerAndDirSize + dibSize

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // ICONDIR (6 bytes)
        buf.putShort(0) // idReserved = 0
        buf.putShort(2) // idType = 2 (CUR)
        buf.putShort(1) // idCount = 1

        // ICONDIRENTRY (16 bytes)
        buf.put((if (w >= 256) 0 else w).toByte()) // bWidth
        buf.put((if (h >= 256) 0 else h).toByte()) // bHeight
        buf.put(0) // bColorCount
        buf.put(0) // bReserved
        buf.putShort(clampedHx.toShort()) // wXHotSpot
        buf.putShort(clampedHy.toShort()) // wYHotSpot
        buf.putInt(dibSize) // dwBytesInRes
        buf.putInt(headerAndDirSize) // dwImageOffset = 22

        // BITMAPINFOHEADER (40 bytes)
        buf.putInt(40) // biSize
        buf.putInt(w) // biWidth
        buf.putInt(h * 2) // biHeight (doubled for XOR + AND masks)
        buf.putShort(1) // biPlanes
        buf.putShort(32) // biBitCount = 32-bit BGRA
        buf.putInt(0) // biCompression = BI_RGB
        buf.putInt(xorSize + andSize) // biSizeImage
        buf.putInt(0) // biXPelsPerMeter
        buf.putInt(0) // biYPelsPerMeter
        buf.putInt(0) // biClrUsed
        buf.putInt(0) // biClrImportant

        // Bottom-up 32-bit BGRA XOR mask
        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val argb = src.getRGB(x, y)
                buf.put((argb and 0xFF).toByte())         // B
                buf.put(((argb ushr 8) and 0xFF).toByte())  // G
                buf.put(((argb ushr 16) and 0xFF).toByte()) // R
                buf.put(((argb ushr 24) and 0xFF).toByte()) // A
            }
        }

        // Bottom-up 1-bpp AND mask (padded to 32-bit row boundaries)
        for (y in h - 1 downTo 0) {
            for (byteIdx in 0 until andRowBytes) {
                var maskByte = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x >= w) {
                        maskByte = maskByte or (1 shl (7 - bit))
                    } else {
                        val alpha = (src.getRGB(x, y) ushr 24) and 0xFF
                        if (alpha == 0) {
                            maskByte = maskByte or (1 shl (7 - bit))
                        }
                    }
                }
                buf.put(maskByte.toByte())
            }
        }

        return buf.array()
    }

    private fun extractHotspotFromFileName(nameWithoutExtension: String): Pair<Int?, Int?> {
        val m = Regex("(?:hotspot[_-]|[_-])(\\d+)[_-](\\d+)$", RegexOption.IGNORE_CASE).find(nameWithoutExtension)
            ?: return null to null
        return m.groupValues[1].toIntOrNull() to m.groupValues[2].toIntOrNull()
    }

    private fun extractHotspotFromSvgMarkup(svgText: String): Pair<Int?, Int?> {
        val hx = Regex("(?:data-hotspot-x|hotspotX|hotspot-x)\\s*=\\s*\"(\\d+)\"", RegexOption.IGNORE_CASE)
            .find(svgText)?.groupValues?.get(1)?.toIntOrNull()
        val hy = Regex("(?:data-hotspot-y|hotspotY|hotspot-y)\\s*=\\s*\"(\\d+)\"", RegexOption.IGNORE_CASE)
            .find(svgText)?.groupValues?.get(1)?.toIntOrNull()
        return hx to hy
    }

    private fun scaleImage(source: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        val dst = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g2 = dst.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        g2.dispose()
        return dst
    }

    private fun createFallbackCursorImage(width: Int, height: Int): BufferedImage {
        val img = BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val poly = GeneralPath()
        poly.moveTo(1f, 1f)
        poly.lineTo(1f, (height * 0.75f).coerceAtLeast(2f))
        poly.lineTo((width * 0.28f).coerceAtLeast(2f), (height * 0.56f).coerceAtLeast(2f))
        poly.lineTo((width * 0.52f).coerceAtLeast(2f), (height * 0.56f).coerceAtLeast(2f))
        poly.closePath()
        g2.color = Color(255, 255, 255, 255)
        g2.fill(poly)
        g2.color = Color(24, 25, 32, 255)
        g2.stroke = BasicStroke(1.5f)
        g2.draw(poly)
        g2.dispose()
        return img
    }

    private fun rasterizeSvgCursor(svgText: String, width: Int, height: Int): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        var drewAny = false
        runCatching {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)))
            val root = doc.documentElement
            val (vbW, vbH) = parseViewBox(root, width.toFloat(), height.toFloat())
            g2.scale(width.toDouble() / vbW.toDouble(), height.toDouble() / vbH.toDouble())
            drewAny = renderSvgChildren(g2, root)
        }
        g2.dispose()
        return if (drewAny) img else createFallbackCursorImage(width, height)
    }

    private fun parseViewBox(root: Element, fallbackW: Float, fallbackH: Float): Pair<Float, Float> {
        val vb = root.getAttribute("viewBox").trim()
        if (vb.isNotEmpty()) {
            val parts = vb.split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (parts.size >= 4 && parts[2] > 0f && parts[3] > 0f) {
                return parts[2] to parts[3]
            }
        }
        val w = root.getAttribute("width").removeSuffix("px").trim().toFloatOrNull() ?: fallbackW
        val h = root.getAttribute("height").removeSuffix("px").trim().toFloatOrNull() ?: fallbackH
        return w.coerceAtLeast(1f) to h.coerceAtLeast(1f)
    }

    private fun renderSvgChildren(g2: Graphics2D, parent: Element): Boolean {
        var rendered = false
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.tagName.equals("defs", ignoreCase = true)) continue
            if (el.tagName.equals("g", ignoreCase = true)) {
                val sub = g2.create() as Graphics2D
                if (renderSvgChildren(sub, el)) rendered = true
                sub.dispose()
                continue
            }
            val shape: Shape? = when (el.tagName.lowercase()) {
                "rect" -> {
                    val x = el.getAttribute("x").toFloatOrNull() ?: 0f
                    val y = el.getAttribute("y").toFloatOrNull() ?: 0f
                    val w = el.getAttribute("width").toFloatOrNull() ?: 0f
                    val h = el.getAttribute("height").toFloatOrNull() ?: 0f
                    val rx = el.getAttribute("rx").toFloatOrNull() ?: 0f
                    val ry = el.getAttribute("ry").toFloatOrNull() ?: rx
                    if (rx > 0f || ry > 0f) RoundRectangle2D.Float(x, y, w, h, rx * 2f, ry * 2f)
                    else Rectangle2D.Float(x, y, w, h)
                }
                "circle" -> {
                    val cx = el.getAttribute("cx").toFloatOrNull() ?: 0f
                    val cy = el.getAttribute("cy").toFloatOrNull() ?: 0f
                    val r = el.getAttribute("r").toFloatOrNull() ?: 0f
                    Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f)
                }
                "ellipse" -> {
                    val cx = el.getAttribute("cx").toFloatOrNull() ?: 0f
                    val cy = el.getAttribute("cy").toFloatOrNull() ?: 0f
                    val rx = el.getAttribute("rx").toFloatOrNull() ?: 0f
                    val ry = el.getAttribute("ry").toFloatOrNull() ?: 0f
                    Ellipse2D.Float(cx - rx, cy - ry, rx * 2f, ry * 2f)
                }
                "line" -> {
                    val x1 = el.getAttribute("x1").toFloatOrNull() ?: 0f
                    val y1 = el.getAttribute("y1").toFloatOrNull() ?: 0f
                    val x2 = el.getAttribute("x2").toFloatOrNull() ?: 0f
                    val y2 = el.getAttribute("y2").toFloatOrNull() ?: 0f
                    Line2D.Float(x1, y1, x2, y2)
                }
                "polygon", "polyline" -> {
                    val path = GeneralPath()
                    val nums = el.getAttribute("points").trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                    var idx = 0
                    while (idx + 1 < nums.size) {
                        if (idx == 0) path.moveTo(nums[idx], nums[idx + 1])
                        else path.lineTo(nums[idx], nums[idx + 1])
                        idx += 2
                    }
                    if (el.tagName.equals("polygon", ignoreCase = true)) path.closePath()
                    path
                }
                "path" -> parseSvgPathData(el.getAttribute("d"))
                else -> null
            }
            if (shape != null) {
                val opacity = el.getAttribute("opacity").toFloatOrNull() ?: 1f
                val fillAttr = el.getAttribute("fill").trim()
                val strokeAttr = el.getAttribute("stroke").trim()
                if (!fillAttr.equals("none", ignoreCase = true)) {
                    val fillColor = parseCssColor(fillAttr.ifEmpty { "#FFFFFF" }, opacity)
                    if (fillColor != null) {
                        g2.color = fillColor
                        g2.fill(shape)
                        rendered = true
                    }
                }
                if (strokeAttr.isNotEmpty() && !strokeAttr.equals("none", ignoreCase = true)) {
                    val strokeColor = parseCssColor(strokeAttr, opacity)
                    if (strokeColor != null) {
                        val sw = el.getAttribute("stroke-width").removeSuffix("px").trim().toFloatOrNull() ?: 1.5f
                        g2.color = strokeColor
                        g2.stroke = BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                        g2.draw(shape)
                        rendered = true
                    }
                }
            }
        }
        return rendered
    }

    private fun parseSvgPathData(d: String): Shape {
        val path = GeneralPath()
        val tokens = Regex("[a-zA-Z]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)").findAll(d).map { it.value }.toList()
        var i = 0
        var cx = 0f
        var cy = 0f
        var cmd = ""
        while (i < tokens.size) {
            val tok = tokens[i]
            if (tok.length == 1 && tok[0].isLetter()) {
                cmd = tok
                i++
            }
            when (cmd) {
                "M" -> if (i + 1 < tokens.size) {
                    cx = tokens[i++].toFloatOrNull() ?: cx
                    cy = tokens[i++].toFloatOrNull() ?: cy
                    path.moveTo(cx, cy)
                    cmd = "L"
                } else break
                "m" -> if (i + 1 < tokens.size) {
                    cx += tokens[i++].toFloatOrNull() ?: 0f
                    cy += tokens[i++].toFloatOrNull() ?: 0f
                    path.moveTo(cx, cy)
                    cmd = "l"
                } else break
                "L" -> if (i + 1 < tokens.size) {
                    cx = tokens[i++].toFloatOrNull() ?: cx
                    cy = tokens[i++].toFloatOrNull() ?: cy
                    path.lineTo(cx, cy)
                } else break
                "l" -> if (i + 1 < tokens.size) {
                    cx += tokens[i++].toFloatOrNull() ?: 0f
                    cy += tokens[i++].toFloatOrNull() ?: 0f
                    path.lineTo(cx, cy)
                } else break
                "H" -> if (i < tokens.size) {
                    cx = tokens[i++].toFloatOrNull() ?: cx
                    path.lineTo(cx, cy)
                } else break
                "h" -> if (i < tokens.size) {
                    cx += tokens[i++].toFloatOrNull() ?: 0f
                    path.lineTo(cx, cy)
                } else break
                "V" -> if (i < tokens.size) {
                    cy = tokens[i++].toFloatOrNull() ?: cy
                    path.lineTo(cx, cy)
                } else break
                "v" -> if (i < tokens.size) {
                    cy += tokens[i++].toFloatOrNull() ?: 0f
                    path.lineTo(cx, cy)
                } else break
                "Q" -> if (i + 3 < tokens.size) {
                    val x1 = tokens[i++].toFloatOrNull() ?: cx
                    val y1 = tokens[i++].toFloatOrNull() ?: cy
                    cx = tokens[i++].toFloatOrNull() ?: cx
                    cy = tokens[i++].toFloatOrNull() ?: cy
                    path.quadTo(x1, y1, cx, cy)
                } else break
                "C" -> if (i + 5 < tokens.size) {
                    val x1 = tokens[i++].toFloatOrNull() ?: cx
                    val y1 = tokens[i++].toFloatOrNull() ?: cy
                    val x2 = tokens[i++].toFloatOrNull() ?: cx
                    val y2 = tokens[i++].toFloatOrNull() ?: cy
                    cx = tokens[i++].toFloatOrNull() ?: cx
                    cy = tokens[i++].toFloatOrNull() ?: cy
                    path.curveTo(x1, y1, x2, y2, cx, cy)
                } else break
                "Z", "z" -> {
                    path.closePath()
                    cmd = ""
                }
                else -> i++
            }
        }
        return path
    }

    private fun parseCssColor(raw: String, opacity: Float): Color? {
        val clean = raw.trim()
        if (clean.isEmpty() || clean.equals("none", ignoreCase = true)) return null
        if (clean.equals("currentColor", ignoreCase = true) || clean.equals("white", ignoreCase = true)) {
            return Color(255, 255, 255, (opacity * 255f).toInt().coerceIn(0, 255))
        }
        if (clean.equals("black", ignoreCase = true)) {
            return Color(0, 0, 0, (opacity * 255f).toInt().coerceIn(0, 255))
        }
        val hex = clean.removePrefix("#")
        val expanded = when (hex.length) {
            3 -> "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}"
            6, 8 -> hex
            else -> return Color(255, 255, 255, (opacity * 255f).toInt().coerceIn(0, 255))
        }
        val rgb = expanded.substring(0, 6).toIntOrNull(16) ?: return null
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        val a = if (expanded.length == 8) {
            expanded.substring(6, 8).toIntOrNull(16) ?: 255
        } else {
            (opacity * 255f).toInt().coerceIn(0, 255)
        }
        return Color(r, g, b, a)
    }
}
