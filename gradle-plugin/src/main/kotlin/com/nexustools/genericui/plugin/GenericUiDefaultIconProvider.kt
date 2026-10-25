package com.nexustools.genericui.plugin

import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Provides the default GenericUI 6-platform `icon.svg` (and rasterized `icon.png` / `icon.ico`
 * equivalents) whenever a project's `build.gradle.kts` does not explicitly configure custom icons.
 */
object GenericUiDefaultIconProvider {

    const val DEFAULT_ICON_SVG_FILE_NAME: String = "icon.svg"
    const val DEFAULT_ICON_PNG_FILE_NAME: String = "icon.png"
    const val DEFAULT_ICON_ICO_FILE_NAME: String = "icon.ico"

    private const val CLASSPATH_ICON_RESOURCE = "/com/nexustools/genericui/plugin/icon.svg"

    /**
     * Loads the default GenericUI `icon.svg` XML text, checking the GenericUI repository root first
     * (`icon.svg` or `lib/GenericUI/icon.svg`), then the bundled classpath resource.
     */
    fun loadDefaultIconSvgText(projectRootDir: File? = null): String {
        if (projectRootDir != null) {
            val repoCandidates = listOf(
                File(projectRootDir, "lib/GenericUI/icon.svg"),
                File(projectRootDir, "../icon.svg"),
                File(projectRootDir, "../../icon.svg"),
                File(projectRootDir, "../../../icon.svg"),
                File(projectRootDir, "icon.svg")
            )
            for (candidate in repoCandidates) {
                if (candidate.exists() && candidate.isFile) {
                    val text = runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull()
                    if (!text.isNullOrBlank() && text.contains("<svg")) {
                        return text
                    }
                }
            }
        }
        val stream = GenericUiDefaultIconProvider::class.java.getResourceAsStream(CLASSPATH_ICON_RESOURCE)
        if (stream != null) {
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.isNotBlank()) {
                return text
            }
        }
        return FALLBACK_DEFAULT_ICON_SVG
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
     * Materializes a 256×256 opaque `icon.png` rendered from the default GenericUI 6-platform icon design.
     */
    fun ensureDefaultPngIcon(projectRootDir: File?, outputDir: File, fileName: String = DEFAULT_ICON_PNG_FILE_NAME): File {
        outputDir.mkdirs()
        val pngFile = File(outputDir, fileName)
        pngFile.writeBytes(renderDefaultIconPngBytes(256))
        return pngFile
    }

    /**
     * Materializes a multi-resolution Windows `icon.ico` (16×16, 32×32, 48×48, 256×256) rendered
     * from the default GenericUI 6-platform icon design so `windres.exe` can embed it in `.exe` binaries.
     */
    fun ensureDefaultIcoIcon(projectRootDir: File?, outputDir: File, fileName: String = DEFAULT_ICON_ICO_FILE_NAME): File {
        outputDir.mkdirs()
        val icoFile = File(outputDir, fileName)
        val sizes = listOf(16, 32, 48, 256)
        val pngFrames = sizes.map { size -> size to renderDefaultIconPngBytes(size) }

        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { dos ->
            // ICONDIR header (6 bytes, little-endian)
            writeLeShort(dos, 0) // idReserved = 0
            writeLeShort(dos, 1) // idType = 1 (ICO)
            writeLeShort(dos, pngFrames.size) // idCount

            var dataOffset = 6 + (16 * pngFrames.size)
            for ((size, bytes) in pngFrames) {
                val dimByte = if (size >= 256) 0 else size
                dos.writeByte(dimByte) // bWidth
                dos.writeByte(dimByte) // bHeight
                dos.writeByte(0)       // bColorCount
                dos.writeByte(0)       // bReserved
                writeLeShort(dos, 1)   // wPlanes = 1
                writeLeShort(dos, 32)  // wBitCount = 32
                writeLeInt(dos, bytes.size) // dwBytesInRes
                writeLeInt(dos, dataOffset) // dwImageOffset
                dataOffset += bytes.size
            }
            for ((_, bytes) in pngFrames) {
                dos.write(bytes)
            }
        }
        icoFile.writeBytes(baos.toByteArray())
        return icoFile
    }

    private fun renderDefaultIconPngBytes(size: Int): ByteArray {
        val img = BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Full-bleed opaque background
        g.color = Color(0x18, 0x19, 0x20)
        g.fillRect(0, 0, 512, 512)

        val macTopClip = Polygon(intArrayOf(0, 224, 168, 0), intArrayOf(0, 0, 256, 256), 4)
        val gtkTopClip = Polygon(intArrayOf(224, 368, 312, 168), intArrayOf(0, 0, 256, 256), 4)
        val winTopClip = Polygon(intArrayOf(368, 512, 512, 312), intArrayOf(0, 0, 256, 256), 4)
        val iosBottomClip = Polygon(intArrayOf(0, 168, 112, 0), intArrayOf(256, 256, 512, 512), 4)
        val webBottomClip = Polygon(intArrayOf(168, 312, 256, 112), intArrayOf(256, 256, 512, 512), 4)
        val androidBottomClip = Polygon(intArrayOf(312, 512, 512, 256), intArrayOf(256, 256, 512, 512), 4)

        // 1. Top-Left: macOS (Traffic lights, NO in-window MenuBar)
        g.clip = macTopClip
        g.color = Color(0x1C, 0x1D, 0x24)
        g.fillRect(0, 0, 512, 256)
        g.color = Color(0x2F, 0x30, 0x38)
        g.fillRect(0, 0, 512, 52)
        g.color = Color(0xFF, 0x5F, 0x56)
        g.fillOval(23, 15, 22, 22)
        g.color = Color(0xFF, 0xBD, 0x2E)
        g.fillOval(57, 15, 22, 22)
        g.color = Color(0x27, 0xC9, 0x3F)
        g.fillOval(91, 15, 22, 22)
        g.color = Color(0x0A, 0x84, 0xFF)
        g.fill(RoundRectangle2D.Float(148f, 14f, 96f, 24f, 12f, 12f))
        g.fill(RoundRectangle2D.Float(34f, 153f, 444f, 38f, 20f, 20f))

        // 2. Top-Center: Linux GTK4 (Plain compositor title bar + in-window GTK MenuBar y=52..82)
        g.clip = gtkTopClip
        g.color = Color(0x24, 0x24, 0x24)
        g.fillRect(0, 0, 512, 256)
        g.color = Color(0x28, 0x28, 0x28)
        g.fillRect(0, 0, 512, 52)
        g.color = Color(0x2B, 0x2B, 0x2B)
        g.fillRect(0, 52, 512, 30)
        g.color = Color(0x35, 0x84, 0xE4)
        g.fill(RoundRectangle2D.Float(206f, 57f, 44f, 20f, 10f, 10f))
        g.fill(RoundRectangle2D.Float(328f, 99f, 146f, 26f, 16f, 16f))

        // 3. Top-Right: Windows 11 (Caption buttons + in-window Win32 MenuBar y=52..82)
        g.clip = winTopClip
        g.color = Color(0x19, 0x1B, 0x22)
        g.fillRect(0, 0, 512, 256)
        g.color = Color(0x16, 0x18, 0x1F)
        g.fillRect(0, 0, 512, 52)
        g.color = Color(0xC4, 0x2B, 0x1C)
        g.fillRect(462, 0, 50, 52)
        g.color = Color(0x1F, 0x23, 0x2E)
        g.fillRect(0, 52, 512, 30)
        g.color = Color(0x00, 0x78, 0xD4)
        g.fillRect(328, 99, 146, 26)

        // 4. Bottom-Left: iOS
        g.clip = iosBottomClip
        g.color = Color(0x12, 0x12, 0x16)
        g.fillRect(0, 256, 512, 256)
        g.color = Color(0x34, 0xC7, 0x59)
        g.fill(RoundRectangle2D.Float(44f, 280f, 52f, 28f, 28f, 28f))
        g.color = Color(0x00, 0x7A, 0xFF)
        g.fill(RoundRectangle2D.Float(38f, 394f, 190f, 32f, 32f, 32f))

        // 5. Bottom-Center: Web
        g.clip = webBottomClip
        g.color = Color(0x18, 0x1C, 0x24)
        g.fillRect(0, 256, 512, 256)
        g.color = Color(0x0D, 0x6E, 0xFD)
        g.fill(RoundRectangle2D.Float(210f, 282f, 84f, 24f, 24f, 24f))
        g.color = Color(0x6F, 0x42, 0xC1)
        g.fill(RoundRectangle2D.Float(240f, 394f, 110f, 32f, 12f, 12f))

        // 6. Bottom-Right: Android
        g.clip = androidBottomClip
        g.color = Color(0x14, 0x12, 0x18)
        g.fillRect(0, 256, 512, 256)
        g.color = Color(0xD0, 0xBC, 0xFF)
        g.fill(RoundRectangle2D.Float(396f, 278f, 60f, 32f, 32f, 32f))
        g.fill(RoundRectangle2D.Float(430f, 388f, 44f, 44f, 28f, 28f))

        g.clip = null
        g.dispose()

        val finalImg = if (size == 512) {
            img
        } else {
            val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val sg = scaled.createGraphics()
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            sg.drawImage(img, 0, 0, size, size, null)
            sg.dispose()
            scaled
        }

        val out = ByteArrayOutputStream()
        ImageIO.write(finalImg, "png", out)
        return out.toByteArray()
    }

    private fun writeLeShort(dos: DataOutputStream, value: Int) {
        dos.writeByte(value and 0xFF)
        dos.writeByte((value ushr 8) and 0xFF)
    }

    private fun writeLeInt(dos: DataOutputStream, value: Int) {
        dos.writeByte(value and 0xFF)
        dos.writeByte((value ushr 8) and 0xFF)
        dos.writeByte((value ushr 16) and 0xFF)
        dos.writeByte((value ushr 24) and 0xFF)
    }

    private const val FALLBACK_DEFAULT_ICON_SVG: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <rect x="0" y="0" width="512" height="512" fill="#181920"/>
</svg>"""
}
