package com.nexustools.genericui.plugin

import org.w3c.dom.Element
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Paint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Provides the default GenericUI 6-platform `icon.svg`, `icon.png`, and `icon.ico`
 * assets across all backend targets, and converts `.svg` / `.png` icons to `.ico` or `.png`
 * whenever a backend requires a specific icon format (such as `icon.ico` for Windows `.exe` builds).
 */
object GenericUiDefaultIconProvider {

    const val DEFAULT_ICON_SVG_FILE_NAME: String = "icon.svg"
    const val DEFAULT_ICON_PNG_FILE_NAME: String = "icon.png"
    const val DEFAULT_ICON_ICO_FILE_NAME: String = "icon.ico"

    private const val CLASSPATH_ICON_SVG_RESOURCE = "/com/nexustools/genericui/plugin/icon.svg"
    private const val CLASSPATH_ICON_PNG_RESOURCE = "/com/nexustools/genericui/plugin/icon.png"
    private const val CLASSPATH_ICON_ICO_RESOURCE = "/com/nexustools/genericui/plugin/icon.ico"

    private val ICO_SIZES = intArrayOf(16, 24, 32, 48, 64, 128, 256)

    /**
     * Loads the default GenericUI `icon.svg` XML text, checking the GenericUI repository root first
     * (`icon.svg` or `lib/GenericUI/icon.svg`), then the bundled classpath resource.
     */
    fun loadDefaultIconSvgText(projectRootDir: File? = null): String {
        val repoFile = findRepositoryIconFile(projectRootDir, DEFAULT_ICON_SVG_FILE_NAME)
        if (repoFile != null) {
            val text = runCatching { repoFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!text.isNullOrBlank() && text.contains("<svg")) {
                return text
            }
        }
        val stream = GenericUiDefaultIconProvider::class.java.getResourceAsStream(CLASSPATH_ICON_SVG_RESOURCE)
        if (stream != null) {
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.isNotBlank()) {
                return text
            }
        }
        return FALLBACK_DEFAULT_ICON_SVG
    }

    /**
     * Loads the default GenericUI `icon.png` bytes, checking the GenericUI repository root first,
     * then the bundled `/com/nexustools/genericui/plugin/icon.png` classpath resource, and falling
     * back to rasterizing `icon.svg` at 512×512.
     */
    fun loadDefaultIconPngBytes(projectRootDir: File? = null): ByteArray {
        val repoFile = findRepositoryIconFile(projectRootDir, DEFAULT_ICON_PNG_FILE_NAME)
        if (repoFile != null) {
            val bytes = runCatching { repoFile.readBytes() }.getOrNull()
            if (bytes != null && bytes.size > 8 && isPngMagic(bytes)) {
                return bytes
            }
        }
        val stream = GenericUiDefaultIconProvider::class.java.getResourceAsStream(CLASSPATH_ICON_PNG_RESOURCE)
        if (stream != null) {
            val bytes = stream.use { it.readBytes() }
            if (bytes.size > 8 && isPngMagic(bytes)) {
                return bytes
            }
        }
        return renderSvgTextToPngBytes(loadDefaultIconSvgText(projectRootDir), 512)
    }

    /**
     * Loads the default GenericUI `icon.ico` bytes, checking the GenericUI repository root first,
     * then the bundled `/com/nexustools/genericui/plugin/icon.ico` classpath resource, and falling
     * back to building a multi-resolution `.ico` from `icon.svg`.
     */
    fun loadDefaultIconIcoBytes(projectRootDir: File? = null): ByteArray {
        val repoFile = findRepositoryIconFile(projectRootDir, DEFAULT_ICON_ICO_FILE_NAME)
        if (repoFile != null) {
            val bytes = runCatching { repoFile.readBytes() }.getOrNull()
            if (bytes != null && bytes.size > 22 && isIcoMagic(bytes)) {
                return bytes
            }
        }
        val stream = GenericUiDefaultIconProvider::class.java.getResourceAsStream(CLASSPATH_ICON_ICO_RESOURCE)
        if (stream != null) {
            val bytes = stream.use { it.readBytes() }
            if (bytes.size > 22 && isIcoMagic(bytes)) {
                return bytes
            }
        }
        val master = downscaleStepwise(renderSvgTextToImage(loadDefaultIconSvgText(projectRootDir), 1024), 512)
        return buildMultiResolutionIcoBytes(master, ICO_SIZES)
    }

    /**
     * Materializes the default GenericUI `icon.svg` into [outputDir] and returns the file.
     */
    fun ensureDefaultSvgIcon(projectRootDir: File?, outputDir: File, fileName: String = DEFAULT_ICON_SVG_FILE_NAME): File {
        outputDir.mkdirs()
        val svgFile = File(outputDir, fileName)
        svgFile.writeText(loadDefaultIconSvgText(projectRootDir), Charsets.UTF_8)
        return svgFile
    }

    /**
     * Materializes the default GenericUI `icon.png` (512×512) into [outputDir] and returns the file.
     */
    fun ensureDefaultPngIcon(projectRootDir: File?, outputDir: File, fileName: String = DEFAULT_ICON_PNG_FILE_NAME): File {
        outputDir.mkdirs()
        val pngFile = File(outputDir, fileName)
        pngFile.writeBytes(loadDefaultIconPngBytes(projectRootDir))
        return pngFile
    }

    /**
     * Materializes the default multi-resolution Windows `icon.ico` (16×16..256×256) into [outputDir]
     * so `windres.exe` can embed it in `.exe` binaries.
     */
    fun ensureDefaultIcoIcon(projectRootDir: File?, outputDir: File, fileName: String = DEFAULT_ICON_ICO_FILE_NAME): File {
        outputDir.mkdirs()
        val icoFile = File(outputDir, fileName)
        icoFile.writeBytes(loadDefaultIconIcoBytes(projectRootDir))
        return icoFile
    }

    /**
     * Ensures a valid `.ico` file for Windows `.exe` resource compilation and runtime icon loading:
     * - If [sourceIconFile] is already an `.ico` file, returns it directly.
     * - If [sourceIconFile] is a `.png` file, converts it into a multi-resolution `.ico` in [outputDir].
     * - If [sourceIconFile] is an `.svg` file, checks for a sibling `icon.ico` or converts/falls back to the default `icon.ico`.
     * - Otherwise materializes the default `icon.ico` into [outputDir].
     */
    fun ensureWindowsIcoFromSourceOrDefault(
        sourceIconFile: File?,
        projectRootDir: File?,
        outputDir: File,
        fileName: String = DEFAULT_ICON_ICO_FILE_NAME
    ): File {
        if (sourceIconFile != null && sourceIconFile.exists() && sourceIconFile.isFile) {
            when (sourceIconFile.extension.lowercase()) {
                "ico" -> return sourceIconFile
                "png" -> {
                    val siblingIco = File(sourceIconFile.parentFile, "${sourceIconFile.nameWithoutExtension}.ico")
                    if (siblingIco.exists() && siblingIco.isFile) {
                        return siblingIco
                    }
                    val img = runCatching { ImageIO.read(sourceIconFile) }.getOrNull()
                    if (img != null) {
                        outputDir.mkdirs()
                        val outFile = File(outputDir, fileName)
                        outFile.writeBytes(buildMultiResolutionIcoBytes(img, ICO_SIZES))
                        return outFile
                    }
                }
                "svg" -> {
                    val siblingIco = File(sourceIconFile.parentFile, "${sourceIconFile.nameWithoutExtension}.ico")
                    if (siblingIco.exists() && siblingIco.isFile) {
                        return siblingIco
                    }
                    val svgText = runCatching { sourceIconFile.readText(Charsets.UTF_8) }.getOrNull()
                    val defaultSvgText = loadDefaultIconSvgText(projectRootDir)
                    if (svgText != null && svgText.trim() != defaultSvgText.trim()) {
                        val rendered = runCatching {
                            downscaleStepwise(renderSvgTextToImage(svgText, 1024), 512)
                        }.getOrNull()
                        if (rendered != null) {
                            outputDir.mkdirs()
                            val outFile = File(outputDir, fileName)
                            outFile.writeBytes(buildMultiResolutionIcoBytes(rendered, ICO_SIZES))
                            return outFile
                        }
                    }
                }
            }
        }
        return ensureDefaultIcoIcon(projectRootDir, outputDir, fileName)
    }

    /**
     * Ensures a valid `.png` icon file from [sourceIconFile] or the default `icon.png`.
     */
    fun ensurePngFromSourceOrDefault(
        sourceIconFile: File?,
        projectRootDir: File?,
        outputDir: File,
        fileName: String = DEFAULT_ICON_PNG_FILE_NAME
    ): File {
        if (sourceIconFile != null && sourceIconFile.exists() && sourceIconFile.isFile) {
            when (sourceIconFile.extension.lowercase()) {
                "png" -> return sourceIconFile
                "svg" -> {
                    val siblingPng = File(sourceIconFile.parentFile, "${sourceIconFile.nameWithoutExtension}.png")
                    if (siblingPng.exists() && siblingPng.isFile) {
                        return siblingPng
                    }
                    val svgText = runCatching { sourceIconFile.readText(Charsets.UTF_8) }.getOrNull()
                    val defaultSvgText = loadDefaultIconSvgText(projectRootDir)
                    if (svgText != null && svgText.trim() != defaultSvgText.trim()) {
                        val pngBytes = runCatching { renderSvgTextToPngBytes(svgText, 512) }.getOrNull()
                        if (pngBytes != null && pngBytes.isNotEmpty()) {
                            outputDir.mkdirs()
                            val outFile = File(outputDir, fileName)
                            outFile.writeBytes(pngBytes)
                            return outFile
                        }
                    }
                }
            }
        }
        return ensureDefaultPngIcon(projectRootDir, outputDir, fileName)
    }

    private fun findRepositoryIconFile(projectRootDir: File?, fileName: String): File? {
        if (projectRootDir == null) return null
        val candidates = listOf(
            File(projectRootDir, fileName),
            File(projectRootDir, "lib/GenericUI/$fileName"),
            File(projectRootDir, "../$fileName"),
            File(projectRootDir, "../../$fileName"),
            File(projectRootDir, "../../../$fileName")
        )
        return candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }
    }

    private fun isPngMagic(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()

    private fun isIcoMagic(bytes: ByteArray): Boolean =
        bytes.size >= 6 &&
            bytes[0] == 0.toByte() &&
            bytes[1] == 0.toByte() &&
            bytes[2] == 1.toByte() &&
            bytes[3] == 0.toByte()

    private fun renderSvgTextToPngBytes(svgText: String, targetSize: Int): ByteArray {
        val supersampled = renderSvgTextToImage(svgText, (targetSize * 2).coerceAtLeast(512))
        val finalImg = downscaleStepwise(supersampled, targetSize)
        val out = ByteArrayOutputStream()
        ImageIO.write(finalImg, "PNG", out)
        return out.toByteArray()
    }

    private fun downscaleStepwise(source: BufferedImage, targetSize: Int): BufferedImage {
        var current = source
        var currentSize = source.width
        while (currentSize / 2 >= targetSize && currentSize > targetSize) {
            val nextSize = currentSize / 2
            val step = BufferedImage(nextSize, nextSize, BufferedImage.TYPE_INT_ARGB)
            val g2 = step.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.drawImage(current, 0, 0, nextSize, nextSize, null)
            g2.dispose()
            current = step
            currentSize = nextSize
        }
        if (currentSize != targetSize) {
            val step = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
            val g2 = step.createGraphics()
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.drawImage(current, 0, 0, targetSize, targetSize, null)
            g2.dispose()
            current = step
        }
        return current
    }

    private fun encodeBmpDibForIco(img: BufferedImage): ByteArray {
        val w = img.width
        val h = img.height
        val xorSize = w * h * 4
        val andRowBytes = ((w + 31) / 32) * 4
        val andSize = andRowBytes * h
        val dib = ByteBuffer.allocate(40 + xorSize + andSize).order(ByteOrder.LITTLE_ENDIAN)
        dib.putInt(40)
        dib.putInt(w)
        dib.putInt(h * 2)
        dib.putShort(1)
        dib.putShort(32)
        dib.putInt(0)
        dib.putInt(xorSize + andSize)
        dib.putInt(0)
        dib.putInt(0)
        dib.putInt(0)
        dib.putInt(0)

        for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val argb = img.getRGB(x, y)
                dib.put((argb and 0xFF).toByte())
                dib.put(((argb ushr 8) and 0xFF).toByte())
                dib.put(((argb ushr 16) and 0xFF).toByte())
                dib.put(((argb ushr 24) and 0xFF).toByte())
            }
        }
        for (y in h - 1 downTo 0) {
            for (byteIdx in 0 until andRowBytes) {
                var maskByte = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x < w) {
                        val alpha = (img.getRGB(x, y) ushr 24) and 0xFF
                        if (alpha == 0) {
                            maskByte = maskByte or (1 shl (7 - bit))
                        }
                    }
                }
                dib.put(maskByte.toByte())
            }
        }
        return dib.array()
    }

    private fun buildMultiResolutionIcoBytes(baseImage: BufferedImage, sizes: IntArray): ByteArray {
        val payloads = sizes.map { size ->
            val scaled = downscaleStepwise(baseImage, size)
            if (size <= 64) {
                encodeBmpDibForIco(scaled)
            } else {
                val baos = ByteArrayOutputStream()
                ImageIO.write(scaled, "PNG", baos)
                baos.toByteArray()
            }
        }
        val headerSize = 6
        val dirEntrySize = 16
        val dataOffset = headerSize + dirEntrySize * sizes.size
        val totalSize = dataOffset + payloads.sumOf { it.size }

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0)
        buf.putShort(1)
        buf.putShort(sizes.size.toShort())

        var currentOffset = dataOffset
        for (i in sizes.indices) {
            val dim = sizes[i]
            val payload = payloads[i]
            buf.put((if (dim >= 256) 0 else dim).toByte())
            buf.put((if (dim >= 256) 0 else dim).toByte())
            buf.put(0)
            buf.put(0)
            buf.putShort(1)
            buf.putShort(32)
            buf.putInt(payload.size)
            buf.putInt(currentOffset)
            currentOffset += payload.size
        }
        for (payload in payloads) {
            buf.put(payload)
        }
        return buf.array()
    }

    private fun renderSvgTextToImage(svgText: String, size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        runCatching {
            val dbf = DocumentBuilderFactory.newInstance()
            dbf.isNamespaceAware = false
            val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)))
            val root = doc.documentElement

            // Map of clipId -> clip Shape
            val clipPaths = LinkedHashMap<String, Shape>()
            // Map of gradId -> Pair(FloatArray(x1, y1, x2, y2), Pair(startColor, endColor))
            val gradients = LinkedHashMap<String, Pair<FloatArray, Pair<Color, Color>>>()

            val defsList = root.getElementsByTagName("defs")
            for (i in 0 until defsList.length) {
                val defs = defsList.item(i) as? Element ?: continue
                val children = defs.childNodes
                for (j in 0 until children.length) {
                    val el = children.item(j) as? Element ?: continue
                    when (el.tagName) {
                        "clipPath" -> {
                            val id = el.getAttribute("id")
                            val polys = el.getElementsByTagName("polygon")
                            if (polys.length > 0) {
                                val poly = polys.item(0) as Element
                                clipPaths[id] = parsePolygon(poly.getAttribute("points"))
                            }
                        }
                        "linearGradient" -> {
                            val id = el.getAttribute("id")
                            val coords = floatArrayOf(
                                parsePercent(el.getAttribute("x1"), 0f),
                                parsePercent(el.getAttribute("y1"), 0f),
                                parsePercent(el.getAttribute("x2"), 0f),
                                parsePercent(el.getAttribute("y2"), 1f)
                            )
                            val stops = el.getElementsByTagName("stop")
                            val c1 = if (stops.length > 0) {
                                parseColor((stops.item(0) as Element).getAttribute("stop-color"), 1f) ?: Color.BLACK
                            } else Color.BLACK
                            val c2 = if (stops.length > 1) {
                                parseColor((stops.item(stops.length - 1) as Element).getAttribute("stop-color"), 1f) ?: c1
                            } else c1
                            gradients[id] = coords to (c1 to c2)
                        }
                    }
                }
            }

            val scale = size / 512.0
            g2.scale(scale, scale)
            renderSvgElements(g2, root, clipPaths, gradients)
        }.onFailure {
            g2.color = Color(0x18, 0x19, 0x20)
            g2.fillRect(0, 0, size, size)
        }

        g2.dispose()
        return img
    }

    private fun renderSvgElements(
        g2: Graphics2D,
        parent: Element,
        clipPaths: Map<String, Shape>,
        gradients: Map<String, Pair<FloatArray, Pair<Color, Color>>>
    ) {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            val tag = el.tagName
            if (tag == "defs") continue
            if (tag == "g") {
                val sub = g2.create() as Graphics2D
                val clipAttr = el.getAttribute("clip-path")
                if (clipAttr.startsWith("url(#") && clipAttr.endsWith(")")) {
                    val clipId = clipAttr.substring(5, clipAttr.length - 1)
                    clipPaths[clipId]?.let { sub.clip(it) }
                }
                renderSvgElements(sub, el, clipPaths, gradients)
                sub.dispose()
            } else {
                drawSvgShapeElement(g2, el, gradients)
            }
        }
    }

    private fun drawSvgShapeElement(
        g2: Graphics2D,
        el: Element,
        gradients: Map<String, Pair<FloatArray, Pair<Color, Color>>>
    ) {
        val shapeAndBounds: Pair<Shape, Rectangle2D>? = when (el.tagName) {
            "rect" -> {
                val x = parseFloat(el.getAttribute("x"), 0f)
                val y = parseFloat(el.getAttribute("y"), 0f)
                val w = parseFloat(el.getAttribute("width"), 0f)
                val h = parseFloat(el.getAttribute("height"), 0f)
                val rx = parseFloat(el.getAttribute("rx"), 0f)
                val ry = parseFloat(el.getAttribute("ry"), rx)
                val s = if (rx > 0f || ry > 0f) {
                    RoundRectangle2D.Float(x, y, w, h, rx * 2f, ry * 2f)
                } else {
                    Rectangle2D.Float(x, y, w, h)
                }
                s to Rectangle2D.Float(x, y, w, h)
            }
            "circle" -> {
                val cx = parseFloat(el.getAttribute("cx"), 0f)
                val cy = parseFloat(el.getAttribute("cy"), 0f)
                val r = parseFloat(el.getAttribute("r"), 0f)
                val s = Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f)
                s to s.bounds2D
            }
            "line" -> {
                val x1 = parseFloat(el.getAttribute("x1"), 0f)
                val y1 = parseFloat(el.getAttribute("y1"), 0f)
                val x2 = parseFloat(el.getAttribute("x2"), 0f)
                val y2 = parseFloat(el.getAttribute("y2"), 0f)
                val s = Line2D.Float(x1, y1, x2, y2)
                s to s.bounds2D
            }
            "path" -> {
                val s = parseSvgPath(el.getAttribute("d"))
                s to s.bounds2D
            }
            "polygon" -> {
                val s = parsePolygon(el.getAttribute("points"))
                s to s.bounds2D
            }
            else -> null
        }
        val (shape, bounds) = shapeAndBounds ?: return

        val opacity = parseFloat(el.getAttribute("opacity"), 1f)
        val fill = el.getAttribute("fill")
        if (fill.isNotEmpty() && !fill.equals("none", ignoreCase = true)) {
            val fillOpacity = opacity * parseFloat(el.getAttribute("fill-opacity"), 1f)
            resolvePaint(fill, bounds, gradients, fillOpacity)?.let {
                g2.paint = it
                g2.fill(shape)
            }
        }

        val stroke = el.getAttribute("stroke")
        if (stroke.isNotEmpty() && !stroke.equals("none", ignoreCase = true)) {
            val strokeOpacity = opacity * parseFloat(el.getAttribute("stroke-opacity"), 1f)
            val strokeWidth = parseFloat(el.getAttribute("stroke-width"), 1f)
            val cap = when (el.getAttribute("stroke-linecap").lowercase()) {
                "round" -> BasicStroke.CAP_ROUND
                "square" -> BasicStroke.CAP_SQUARE
                else -> BasicStroke.CAP_BUTT
            }
            val join = when (el.getAttribute("stroke-linejoin").lowercase()) {
                "round" -> BasicStroke.JOIN_ROUND
                "bevel" -> BasicStroke.JOIN_BEVEL
                else -> BasicStroke.JOIN_MITER
            }
            resolvePaint(stroke, bounds, gradients, strokeOpacity)?.let {
                g2.paint = it
                g2.stroke = BasicStroke(strokeWidth, cap, join)
                g2.draw(shape)
            }
        }
    }

    private fun resolvePaint(
        paintSpec: String,
        bounds: Rectangle2D,
        gradients: Map<String, Pair<FloatArray, Pair<Color, Color>>>,
        opacity: Float
    ): Paint? {
        if (paintSpec.startsWith("url(#") && paintSpec.endsWith(")")) {
            val id = paintSpec.substring(5, paintSpec.length - 1)
            val grad = gradients[id] ?: return null
            val coords = grad.first
            val (c1, c2) = grad.second
            val x1 = (bounds.x + coords[0] * bounds.width).toFloat()
            val y1 = (bounds.y + coords[1] * bounds.height).toFloat()
            val x2 = (bounds.x + coords[2] * bounds.width).toFloat()
            val y2 = (bounds.y + coords[3] * bounds.height).toFloat()
            return GradientPaint(x1, y1, c1, x2, y2, c2)
        }
        return parseColor(paintSpec, opacity)
    }

    private fun parseColor(hex: String?, opacity: Float): Color? {
        if (hex.isNullOrBlank() || hex.equals("none", ignoreCase = true)) return null
        val clean = hex.trim().removePrefix("#")
        val rgb = clean.toIntOrNull(16) ?: return null
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        val a = (opacity * 255f).toInt().coerceIn(0, 255)
        return Color(r, g, b, a)
    }

    private fun parsePolygon(pointsAttr: String): Shape {
        val path = GeneralPath()
        val pairs = pointsAttr.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for ((idx, pair) in pairs.withIndex()) {
            val xy = pair.split(",")
            if (xy.size >= 2) {
                val x = xy[0].toFloatOrNull() ?: 0f
                val y = xy[1].toFloatOrNull() ?: 0f
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.closePath()
        return path
    }

    private fun parseSvgPath(d: String): Shape {
        val path = GeneralPath()
        val spaced = d.replace(Regex("([MLZmlz])"), " $1 ").replace(",", " ").trim()
        val tokens = spaced.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var i = 0
        while (i < tokens.size) {
            when (val cmd = tokens[i++]) {
                "M" -> if (i + 1 < tokens.size) {
                    val x = tokens[i++].toFloatOrNull() ?: 0f
                    val y = tokens[i++].toFloatOrNull() ?: 0f
                    path.moveTo(x, y)
                }
                "L" -> if (i + 1 < tokens.size) {
                    val x = tokens[i++].toFloatOrNull() ?: 0f
                    val y = tokens[i++].toFloatOrNull() ?: 0f
                    path.lineTo(x, y)
                }
                "Z", "z" -> path.closePath()
            }
        }
        return path
    }

    private fun parseFloat(value: String?, defaultVal: Float): Float =
        value?.trim()?.takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: defaultVal

    private fun parsePercent(value: String?, defaultVal: Float): Float {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return defaultVal
        return if (clean.endsWith("%")) {
            (clean.removeSuffix("%").toFloatOrNull() ?: (defaultVal * 100f)) / 100f
        } else {
            clean.toFloatOrNull() ?: defaultVal
        }
    }

    private const val FALLBACK_DEFAULT_ICON_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <rect x="0" y="0" width="512" height="512" fill="#181920"/>
</svg>"""
}
