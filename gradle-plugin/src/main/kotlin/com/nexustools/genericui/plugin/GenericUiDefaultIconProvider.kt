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
    const val DEFAULT_ICON_ICNS_FILE_NAME: String = "icon.icns"

    private const val CLASSPATH_ICON_SVG_RESOURCE = "/com/nexustools/genericui/plugin/icon.svg"
    private const val CLASSPATH_ICON_PNG_RESOURCE = "/com/nexustools/genericui/plugin/icon.png"
    private const val CLASSPATH_ICON_ICO_RESOURCE = "/com/nexustools/genericui/plugin/icon.ico"

    private val ICO_SIZES = intArrayOf(16, 24, 32, 48, 64, 128, 256)
    private val ICNS_ENTRIES = listOf(
        "icp4" to 16,
        "icp5" to 32,
        "icp6" to 64,
        "ic07" to 128,
        "ic08" to 256,
        "ic09" to 512,
        "ic10" to 1024
    )

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
     * Ensures a valid multi-resolution `.ico` file (`16×16..256×256`) for Windows `.exe` resource
     * compilation and runtime icon loading:
     * - If [sourceIconFile] is already a multi-resolution `.ico` file (`>= 5` entries), copies it into [outputDir].
     * - If [sourceIconFile] is a `.svg`, `.png`, or single-resolution `.ico` file, decodes/rasterizes it directly
     *   and builds a full 7-resolution `.ico` (`16, 24, 32, 48, 64, 128, 256`) in [outputDir].
     * - Otherwise materializes the default `icon.ico` into [outputDir].
     */
    fun ensureWindowsIcoFromSourceOrDefault(
        sourceIconFile: File?,
        projectRootDir: File?,
        outputDir: File,
        fileName: String = DEFAULT_ICON_ICO_FILE_NAME
    ): File {
        outputDir.mkdirs()
        val outFile = File(outputDir, fileName)
        if (sourceIconFile != null && sourceIconFile.exists() && sourceIconFile.isFile) {
            val ext = sourceIconFile.extension.lowercase()
            if (ext == "ico" && hasMultiResolutionIcoEntries(sourceIconFile, minEntries = 5)) {
                if (sourceIconFile.canonicalFile != outFile.canonicalFile) {
                    sourceIconFile.copyTo(outFile, overwrite = true)
                }
                return outFile
            }
            val decoded = decodeImageFromFile(sourceIconFile)
            if (decoded != null) {
                outFile.writeBytes(buildMultiResolutionIcoBytes(decoded, ICO_SIZES))
                return outFile
            }
        }
        return ensureDefaultIcoIcon(projectRootDir, outputDir, fileName)
    }

    /**
     * Materializes the default Apple `.icns` icon bundle (`16×16..1024×1024`) into [outputDir].
     */
    fun ensureDefaultIcnsIcon(
        projectRootDir: File?,
        outputDir: File,
        fileName: String = DEFAULT_ICON_ICNS_FILE_NAME
    ): File {
        outputDir.mkdirs()
        val icnsFile = File(outputDir, fileName)
        val master = renderSvgTextToImage(loadDefaultIconSvgText(projectRootDir), 1024)
        icnsFile.writeBytes(buildAppleIcnsBytes(master))
        return icnsFile
    }

    /**
     * Ensures a valid multi-resolution Apple `.icns` icon container (`icp4` 16×16 through `ic10` 1024×1024)
     * in [outputDir] generated from [sourceIconFile] (`.svg`, `.png`, `.ico`, or `.icns`) or the default GenericUI icon.
     */
    fun ensureAppleIcnsFromSourceOrDefault(
        sourceIconFile: File?,
        projectRootDir: File?,
        outputDir: File,
        fileName: String = DEFAULT_ICON_ICNS_FILE_NAME
    ): File {
        outputDir.mkdirs()
        val outFile = File(outputDir, fileName)
        if (sourceIconFile != null && sourceIconFile.exists() && sourceIconFile.isFile) {
            val ext = sourceIconFile.extension.lowercase()
            if (ext == "icns") {
                val bytes = runCatching { sourceIconFile.readBytes() }.getOrNull()
                if (bytes != null && isIcnsMagic(bytes)) {
                    if (sourceIconFile.canonicalFile != outFile.canonicalFile) {
                        sourceIconFile.copyTo(outFile, overwrite = true)
                    }
                    return outFile
                }
            }
            val decoded = if (ext == "svg") {
                val svgText = runCatching { sourceIconFile.readText(Charsets.UTF_8) }.getOrNull()
                svgText?.let { runCatching { renderSvgTextToImage(it, 1024) }.getOrNull() }
            } else {
                decodeImageFromFile(sourceIconFile)
            }
            if (decoded != null) {
                outFile.writeBytes(buildAppleIcnsBytes(decoded))
                return outFile
            }
        }
        return ensureDefaultIcnsIcon(projectRootDir, outputDir, fileName)
    }

    /**
     * Ensures a valid `.png` icon file in [outputDir] from [sourceIconFile] (supporting `.png`, `.ico`, and `.svg`
     * source icons) or the default `icon.png`.
     */
    fun ensurePngFromSourceOrDefault(
        sourceIconFile: File?,
        projectRootDir: File?,
        outputDir: File,
        fileName: String = DEFAULT_ICON_PNG_FILE_NAME
    ): File {
        outputDir.mkdirs()
        val outFile = File(outputDir, fileName)
        val requestedSize = when {
            fileName.contains("192") -> 192
            fileName.contains("512") -> 512
            else -> 512
        }
        if (sourceIconFile != null && sourceIconFile.exists() && sourceIconFile.isFile) {
            val ext = sourceIconFile.extension.lowercase()
            if (ext == "png" && fileName == DEFAULT_ICON_PNG_FILE_NAME) {
                if (sourceIconFile.canonicalFile != outFile.canonicalFile) {
                    sourceIconFile.copyTo(outFile, overwrite = true)
                }
                return outFile
            }
            val decoded = decodeImageFromFile(sourceIconFile)
            if (decoded != null) {
                val finalImg = if (decoded.width != requestedSize || decoded.height != requestedSize) {
                    scaleImageBicubic(decoded, requestedSize)
                } else {
                    decoded
                }
                val baos = ByteArrayOutputStream()
                ImageIO.write(finalImg, "PNG", baos)
                outFile.writeBytes(baos.toByteArray())
                return outFile
            }
        }
        return ensureDefaultPngIcon(projectRootDir, outputDir, fileName)
    }

    /**
     * Decodes any `.png`, `.ico`, `.svg`, `.jpg`, `.bmp`, or `.gif` icon file into an ARGB [BufferedImage].
     */
    fun decodeImageFromFile(file: File): BufferedImage? {
        if (!file.exists() || !file.isFile) return null
        return when (file.extension.lowercase()) {
            "svg" -> {
                val svgText = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
                runCatching { downscaleStepwise(renderSvgTextToImage(svgText, 1024), 512) }.getOrNull()
            }
            "ico" -> decodeLargestIcoEntry(file)
            else -> runCatching { ImageIO.read(file) }.getOrNull()
        }
    }

    private fun hasMultiResolutionIcoEntries(icoFile: File, minEntries: Int): Boolean {
        val bytes = runCatching { icoFile.readBytes() }.getOrNull() ?: return false
        if (!isIcoMagic(bytes) || bytes.size < 6) return false
        val count = ((bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8))
        return count >= minEntries
    }

    private fun decodeLargestIcoEntry(icoFile: File): BufferedImage? {
        val bytes = runCatching { icoFile.readBytes() }.getOrNull() ?: return null
        if (!isIcoMagic(bytes) || bytes.size < 22) {
            return runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
        }
        val count = ((bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8))
        if (count <= 0) return null

        var bestImg: BufferedImage? = null
        var bestArea = -1
        for (i in 0 until count) {
            val entryOff = 6 + i * 16
            if (entryOff + 16 > bytes.size) break
            val w = (bytes[entryOff].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val h = (bytes[entryOff + 1].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val size = ByteBuffer.wrap(bytes, entryOff + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val dataOff = ByteBuffer.wrap(bytes, entryOff + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (size <= 0 || dataOff < 0 || dataOff + size > bytes.size) continue
            val area = w * h
            if (area <= bestArea) continue

            val payload = bytes.copyOfRange(dataOff, dataOff + size)
            val decoded = if (isPngMagic(payload)) {
                runCatching { ImageIO.read(ByteArrayInputStream(payload)) }.getOrNull()
            } else {
                decodeBmpDibFromIcoPayload(payload, w, h)
            }
            if (decoded != null) {
                bestImg = decoded
                bestArea = decoded.width * decoded.height
            }
        }
        return bestImg
    }

    private fun decodeBmpDibFromIcoPayload(payload: ByteArray, fallbackW: Int, fallbackH: Int): BufferedImage? = runCatching {
        if (payload.size < 40) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val headerSize = buf.getInt(0)
        val w = buf.getInt(4).takeIf { it > 0 } ?: fallbackW
        val rawH = buf.getInt(8)
        val h = (if (rawH > 0) rawH / 2 else fallbackH).takeIf { it > 0 } ?: fallbackH
        val bpp = buf.getShort(14).toInt() and 0xFFFF
        if (bpp != 32 && bpp != 24) return null

        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val bytesPerPixel = bpp / 8
        val xorRowStride = ((w * bytesPerPixel + 3) / 4) * 4
        val xorStart = headerSize
        val andStart = xorStart + xorRowStride * h
        val andRowStride = ((w + 31) / 32) * 4

        for (row in 0 until h) {
            val y = h - 1 - row
            val rowOff = xorStart + row * xorRowStride
            val andRowOff = andStart + row * andRowStride
            for (x in 0 until w) {
                val pxOff = rowOff + x * bytesPerPixel
                if (pxOff + bytesPerPixel > payload.size) continue
                val b = payload[pxOff].toInt() and 0xFF
                val g = payload[pxOff + 1].toInt() and 0xFF
                val r = payload[pxOff + 2].toInt() and 0xFF
                val a = if (bpp == 32) {
                    payload[pxOff + 3].toInt() and 0xFF
                } else {
                    val byteIdx = x / 8
                    val bitIdx = 7 - (x % 8)
                    val maskByte = if (andRowOff + byteIdx < payload.size) payload[andRowOff + byteIdx].toInt() and 0xFF else 0
                    if (((maskByte ushr bitIdx) and 1) != 0) 0 else 255
                }
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                img.setRGB(x, y, argb)
            }
        }
        img
    }.getOrNull()

    private fun scaleImageBicubic(source: BufferedImage, targetSize: Int): BufferedImage {
        if (source.width >= targetSize * 2) {
            return downscaleStepwise(source, targetSize)
        }
        val out = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
        val g2 = out.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.drawImage(source, 0, 0, targetSize, targetSize, null)
        g2.dispose()
        return out
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

    private fun isIcnsMagic(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[0] == 'i'.code.toByte() &&
            bytes[1] == 'c'.code.toByte() &&
            bytes[2] == 'n'.code.toByte() &&
            bytes[3] == 's'.code.toByte()

    internal fun buildAppleIcnsBytes(baseImage: BufferedImage): ByteArray {
        val chunks = ICNS_ENTRIES.map { (osType, size) ->
            val scaled = scaleImageBicubic(baseImage, size)
            val baos = ByteArrayOutputStream()
            ImageIO.write(scaled, "PNG", baos)
            val pngBytes = baos.toByteArray()
            osType to pngBytes
        }
        val totalSize = 8 + chunks.sumOf { 8 + it.second.size }
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buf.put('i'.code.toByte())
        buf.put('c'.code.toByte())
        buf.put('n'.code.toByte())
        buf.put('s'.code.toByte())
        buf.putInt(totalSize)
        for ((osType, pngBytes) in chunks) {
            val tagBytes = osType.toByteArray(Charsets.US_ASCII)
            buf.put(tagBytes, 0, 4)
            buf.putInt(8 + pngBytes.size)
            buf.put(pngBytes)
        }
        return buf.array()
    }

    internal fun renderSvgTextToPngBytes(svgText: String, targetSize: Int): ByteArray {
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

            val (minX, minY, vbW, vbH) = parseSvgViewBox(root)
            val scaleX = size.toDouble() / vbW.coerceAtLeast(1.0)
            val scaleY = size.toDouble() / vbH.coerceAtLeast(1.0)
            val uniformScale = minOf(scaleX, scaleY)
            val offsetX = (size.toDouble() - vbW * uniformScale) / 2.0
            val offsetY = (size.toDouble() - vbH * uniformScale) / 2.0
            g2.translate(offsetX, offsetY)
            g2.scale(uniformScale, uniformScale)
            if (minX != 0.0 || minY != 0.0) {
                g2.translate(-minX, -minY)
            }
            renderSvgElements(g2, root, clipPaths, gradients)
        }.onFailure {
            g2.color = Color(0x18, 0x19, 0x20)
            g2.fillRect(0, 0, size, size)
        }

        g2.dispose()
        return img
    }

    private fun parseSvgViewBox(root: Element): DoubleArray {
        val vbAttr = root.getAttribute("viewBox")?.trim().orEmpty()
        if (vbAttr.isNotEmpty()) {
            val parts = vbAttr.split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
            if (parts.size >= 4 && parts[2] > 0.0 && parts[3] > 0.0) {
                return doubleArrayOf(parts[0], parts[1], parts[2], parts[3])
            }
        }
        val wAttr = root.getAttribute("width")?.replace("px", "")?.replace("pt", "")?.trim()?.toDoubleOrNull()
        val hAttr = root.getAttribute("height")?.replace("px", "")?.replace("pt", "")?.trim()?.toDoubleOrNull()
        val w = if (wAttr != null && wAttr > 0.0) wAttr else 512.0
        val h = if (hAttr != null && hAttr > 0.0) hAttr else w
        return doubleArrayOf(0.0, 0.0, w, h)
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
            "ellipse" -> {
                val cx = parseFloat(el.getAttribute("cx"), 0f)
                val cy = parseFloat(el.getAttribute("cy"), 0f)
                val rx = parseFloat(el.getAttribute("rx"), 0f)
                val ry = parseFloat(el.getAttribute("ry"), 0f)
                val s = Ellipse2D.Float(cx - rx, cy - ry, rx * 2f, ry * 2f)
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
                val s = parsePolygon(el.getAttribute("points"), close = true)
                s to s.bounds2D
            }
            "polyline" -> {
                val s = parsePolygon(el.getAttribute("points"), close = false)
                s to s.bounds2D
            }
            else -> null
        }
        val (shape, bounds) = shapeAndBounds ?: return

        val opacity = parseFloat(el.getAttribute("opacity"), 1f)
        val rawFill = el.getAttribute("fill")
        val inheritedFill = if (rawFill.isNotEmpty()) {
            rawFill
        } else {
            var p = el.parentNode
            var found = ""
            while (p is Element) {
                val pf = p.getAttribute("fill")
                if (pf.isNotEmpty()) {
                    found = pf
                    break
                }
                p = p.parentNode
            }
            found
        }
        if (!inheritedFill.equals("none", ignoreCase = true) && (inheritedFill.isNotEmpty() || el.getAttribute("stroke").isEmpty())) {
            val effectiveFill = inheritedFill.ifEmpty { "#000000" }
            val fillOpacity = opacity * parseFloat(el.getAttribute("fill-opacity"), 1f)
            resolvePaint(effectiveFill, bounds, gradients, fillOpacity)?.let {
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
        val rgb = when (clean.length) {
            3 -> {
                val r = clean[0].digitToIntOrNull(16) ?: return null
                val g = clean[1].digitToIntOrNull(16) ?: return null
                val b = clean[2].digitToIntOrNull(16) ?: return null
                ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
            }
            else -> clean.take(6).toIntOrNull(16) ?: return null
        }
        val r = (rgb ushr 16) and 0xFF
        val g = (rgb ushr 8) and 0xFF
        val b = rgb and 0xFF
        val a = (opacity * 255f).toInt().coerceIn(0, 255)
        return Color(r, g, b, a)
    }

    private fun parsePolygon(pointsAttr: String, close: Boolean = true): Shape {
        val path = GeneralPath()
        val nums = pointsAttr.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        var idx = 0
        var first = true
        while (idx + 1 < nums.size) {
            val x = nums[idx]
            val y = nums[idx + 1]
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            idx += 2
        }
        if (close && !first) {
            path.closePath()
        }
        return path
    }

    private fun parseSvgPath(d: String): Shape {
        val path = GeneralPath()
        val tokens = Regex("([MmLlHhVvCcSsQqTtAaZz])|([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)")
            .findAll(d)
            .map { it.value }
            .toList()
        var i = 0
        var cmd = 'M'
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var lastCtrlX = 0f
        var lastCtrlY = 0f
        var lastCmd = ' '

        fun nextFloat(): Float = if (i < tokens.size) tokens[i++].toFloatOrNull() ?: 0f else 0f
        fun hasNextNumber(): Boolean = i < tokens.size && tokens[i][0].let { it.isDigit() || it == '-' || it == '+' || it == '.' }

        while (i < tokens.size) {
            val tok = tokens[i]
            if (tok.length == 1 && tok[0].isLetter()) {
                cmd = tok[0]
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    path.closePath()
                    curX = startX
                    curY = startY
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = cmd
                    continue
                }
            }
            when (cmd) {
                'M' -> if (hasNextNumber()) {
                    curX = nextFloat()
                    curY = nextFloat()
                    startX = curX
                    startY = curY
                    path.moveTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'M'
                    cmd = 'L'
                } else i++
                'm' -> if (hasNextNumber()) {
                    curX += nextFloat()
                    curY += nextFloat()
                    startX = curX
                    startY = curY
                    path.moveTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'm'
                    cmd = 'l'
                } else i++
                'L' -> if (hasNextNumber()) {
                    curX = nextFloat()
                    curY = nextFloat()
                    path.lineTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'L'
                } else i++
                'l' -> if (hasNextNumber()) {
                    curX += nextFloat()
                    curY += nextFloat()
                    path.lineTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'l'
                } else i++
                'H' -> if (hasNextNumber()) {
                    curX = nextFloat()
                    path.lineTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'H'
                } else i++
                'h' -> if (hasNextNumber()) {
                    curX += nextFloat()
                    path.lineTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'h'
                } else i++
                'V' -> if (hasNextNumber()) {
                    curY = nextFloat()
                    path.lineTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'V'
                } else i++
                'v' -> if (hasNextNumber()) {
                    curY += nextFloat()
                    path.lineTo(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'v'
                } else i++
                'Q' -> if (hasNextNumber()) {
                    val x1 = nextFloat()
                    val y1 = nextFloat()
                    curX = nextFloat()
                    curY = nextFloat()
                    path.quadTo(x1, y1, curX, curY)
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmd = 'Q'
                } else i++
                'q' -> if (hasNextNumber()) {
                    val x1 = curX + nextFloat()
                    val y1 = curY + nextFloat()
                    curX += nextFloat()
                    curY += nextFloat()
                    path.quadTo(x1, y1, curX, curY)
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmd = 'q'
                } else i++
                'T' -> if (hasNextNumber()) {
                    val x1 = if (lastCmd in "QqTt") 2f * curX - lastCtrlX else curX
                    val y1 = if (lastCmd in "QqTt") 2f * curY - lastCtrlY else curY
                    curX = nextFloat()
                    curY = nextFloat()
                    path.quadTo(x1, y1, curX, curY)
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmd = 'T'
                } else i++
                't' -> if (hasNextNumber()) {
                    val x1 = if (lastCmd in "QqTt") 2f * curX - lastCtrlX else curX
                    val y1 = if (lastCmd in "QqTt") 2f * curY - lastCtrlY else curY
                    curX += nextFloat()
                    curY += nextFloat()
                    path.quadTo(x1, y1, curX, curY)
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmd = 't'
                } else i++
                'C' -> if (hasNextNumber()) {
                    val x1 = nextFloat()
                    val y1 = nextFloat()
                    val x2 = nextFloat()
                    val y2 = nextFloat()
                    curX = nextFloat()
                    curY = nextFloat()
                    path.curveTo(x1, y1, x2, y2, curX, curY)
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmd = 'C'
                } else i++
                'c' -> if (hasNextNumber()) {
                    val x1 = curX + nextFloat()
                    val y1 = curY + nextFloat()
                    val x2 = curX + nextFloat()
                    val y2 = curY + nextFloat()
                    curX += nextFloat()
                    curY += nextFloat()
                    path.curveTo(x1, y1, x2, y2, curX, curY)
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmd = 'c'
                } else i++
                'S' -> if (hasNextNumber()) {
                    val x1 = if (lastCmd in "CcSs") 2f * curX - lastCtrlX else curX
                    val y1 = if (lastCmd in "CcSs") 2f * curY - lastCtrlY else curY
                    val x2 = nextFloat()
                    val y2 = nextFloat()
                    curX = nextFloat()
                    curY = nextFloat()
                    path.curveTo(x1, y1, x2, y2, curX, curY)
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmd = 'S'
                } else i++
                's' -> if (hasNextNumber()) {
                    val x1 = if (lastCmd in "CcSs") 2f * curX - lastCtrlX else curX
                    val y1 = if (lastCmd in "CcSs") 2f * curY - lastCtrlY else curY
                    val x2 = curX + nextFloat()
                    val y2 = curY + nextFloat()
                    curX += nextFloat()
                    curY += nextFloat()
                    path.curveTo(x1, y1, x2, y2, curX, curY)
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmd = 's'
                } else i++
                'A' -> if (hasNextNumber()) {
                    val rx = nextFloat()
                    val ry = nextFloat()
                    val xAxisRotation = nextFloat()
                    val largeArcFlag = nextFloat() != 0f
                    val sweepFlag = nextFloat() != 0f
                    val nextX = nextFloat()
                    val nextY = nextFloat()
                    appendSvgArcToPath(path, curX, curY, rx, ry, xAxisRotation, largeArcFlag, sweepFlag, nextX, nextY)
                    curX = nextX
                    curY = nextY
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'A'
                } else i++
                'a' -> if (hasNextNumber()) {
                    val rx = nextFloat()
                    val ry = nextFloat()
                    val xAxisRotation = nextFloat()
                    val largeArcFlag = nextFloat() != 0f
                    val sweepFlag = nextFloat() != 0f
                    val nextX = curX + nextFloat()
                    val nextY = curY + nextFloat()
                    appendSvgArcToPath(path, curX, curY, rx, ry, xAxisRotation, largeArcFlag, sweepFlag, nextX, nextY)
                    curX = nextX
                    curY = nextY
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = 'a'
                } else i++
                else -> i++
            }
        }
        return path
    }

    private fun appendSvgArcToPath(
        path: GeneralPath,
        x1: Float,
        y1: Float,
        rxIn: Float,
        ryIn: Float,
        xAxisRotationDeg: Float,
        largeArcFlag: Boolean,
        sweepFlag: Boolean,
        x2: Float,
        y2: Float
    ) {
        if (x1 == x2 && y1 == y2) return
        if (rxIn == 0f || ryIn == 0f) {
            path.lineTo(x2, y2)
            return
        }
        var rx = kotlin.math.abs(rxIn.toDouble())
        var ry = kotlin.math.abs(ryIn.toDouble())
        val phi = Math.toRadians(xAxisRotationDeg.toDouble() % 360.0)
        val cosPhi = kotlin.math.cos(phi)
        val sinPhi = kotlin.math.sin(phi)

        val dx2 = (x1 - x2) / 2.0
        val dy2 = (y1 - y2) / 2.0
        val x1Prime = cosPhi * dx2 + sinPhi * dy2
        val y1Prime = -sinPhi * dx2 + cosPhi * dy2

        var rxSq = rx * rx
        var rySq = ry * ry
        val x1PrimeSq = x1Prime * x1Prime
        val y1PrimeSq = y1Prime * y1Prime
        val radiiCheck = x1PrimeSq / rxSq + y1PrimeSq / rySq
        if (radiiCheck > 1.0) {
            val scale = kotlin.math.sqrt(radiiCheck)
            rx *= scale
            ry *= scale
            rxSq = rx * rx
            rySq = ry * ry
        }

        val sign = if (largeArcFlag == sweepFlag) -1.0 else 1.0
        val num = (rxSq * rySq - rxSq * y1PrimeSq - rySq * x1PrimeSq).coerceAtLeast(0.0)
        val den = (rxSq * y1PrimeSq + rySq * x1PrimeSq).coerceAtLeast(1e-12)
        val coef = sign * kotlin.math.sqrt(num / den)
        val cxPrime = coef * ((rx * y1Prime) / ry)
        val cyPrime = coef * -((ry * x1Prime) / rx)

        val cx = cosPhi * cxPrime - sinPhi * cyPrime + (x1 + x2) / 2.0
        val cy = sinPhi * cxPrime + cosPhi * cyPrime + (y1 + y2) / 2.0

        fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = (kotlin.math.hypot(ux, uy) * kotlin.math.hypot(vx, vy)).coerceAtLeast(1e-12)
            val cosVal = (dot / len).coerceIn(-1.0, 1.0)
            val angle = kotlin.math.acos(cosVal)
            return if (ux * vy - uy * vx < 0.0) -angle else angle
        }

        val ux = (x1Prime - cxPrime) / rx
        val uy = (y1Prime - cyPrime) / ry
        val vx = (-x1Prime - cxPrime) / rx
        val vy = (-y1Prime - cyPrime) / ry
        val theta1 = vectorAngle(1.0, 0.0, ux, uy)
        var dTheta = vectorAngle(ux, uy, vx, vy)
        if (!sweepFlag && dTheta > 0.0) {
            dTheta -= 2.0 * Math.PI
        } else if (sweepFlag && dTheta < 0.0) {
            dTheta += 2.0 * Math.PI
        }

        val segments = kotlin.math.ceil(kotlin.math.abs(dTheta) / (Math.PI / 2.0)).toInt().coerceAtLeast(1)
        val delta = dTheta / segments
        val alpha = (4.0 / 3.0) * kotlin.math.tan(delta / 4.0)
        for (seg in 0 until segments) {
            val t1 = theta1 + seg * delta
            val t2 = t1 + delta
            val cosT1 = kotlin.math.cos(t1)
            val sinT1 = kotlin.math.sin(t1)
            val cosT2 = kotlin.math.cos(t2)
            val sinT2 = kotlin.math.sin(t2)

            val e1x = cosT1 - alpha * sinT1
            val e1y = sinT1 + alpha * cosT1
            val e2x = cosT2 + alpha * sinT2
            val e2y = sinT2 - alpha * cosT2

            val cp1x = cx + rx * cosPhi * e1x - ry * sinPhi * e1y
            val cp1y = cy + rx * sinPhi * e1x + ry * cosPhi * e1y
            val cp2x = cx + rx * cosPhi * e2x - ry * sinPhi * e2y
            val cp2y = cy + rx * sinPhi * e2x + ry * cosPhi * e2y
            val endX = cx + rx * cosPhi * cosT2 - ry * sinPhi * sinT2
            val endY = cy + rx * sinPhi * cosT2 + ry * cosPhi * sinT2

            path.curveTo(
                cp1x.toFloat(),
                cp1y.toFloat(),
                cp2x.toFloat(),
                cp2y.toFloat(),
                endX.toFloat(),
                endY.toFloat()
            )
        }
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
