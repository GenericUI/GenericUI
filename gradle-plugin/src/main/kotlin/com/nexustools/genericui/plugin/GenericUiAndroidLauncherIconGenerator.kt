package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Generates Android adaptive launcher icon resources (`drawable/icon.xml` or fallback PNGs,
 * `drawable/ic_launcher_foreground.xml`, `values/ic_launcher_background.xml` / `values-night/...` or
 * `drawable/ic_launcher_background_layer.*`, `drawable/ic_launcher_monochrome.xml`,
 * `mipmap-anydpi-v26/ic_launcher*.xml`, and legacy `mipmap-{mdpi..xxxhdpi}/ic_launcher*.png`)
 * into `android/res`.
 */
object GenericUiAndroidLauncherIconGenerator {

    private val DENSITY_SIZES: List<Pair<String, Int>> = listOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192
    )

    private val ADAPTIVE_DENSITY_SIZES: List<Pair<String, Int>> = listOf(
        "mdpi" to 108,
        "hdpi" to 162,
        "xhdpi" to 216,
        "xxhdpi" to 324,
        "xxxhdpi" to 432
    )

    private val RASTER_EXTS: Set<String> = setOf("png", "webp", "jpg", "jpeg", "gif")

    /**
     * Generates all Android icon and adaptive launcher resources into [androidResDir].
     */
    fun generate(
        projDir: File,
        androidResDir: File,
        launcherConfig: GenericUiAndroidLauncherIconConfig,
        fallbackSourceIconFile: File?,
        resolvedColors: Map<String, String> = emptyMap(),
        svgConverterVersion: String = GenericUiAndroidSvgConverter.DEFAULT_SDK_COMMON_VERSION,
        failOnSvgWarnings: Boolean = false,
        svgCacheDir: File? = null,
        svgConverterClasspath: Collection<File> = emptyList(),
        project: Project? = null,
        gradleUserHomeDir: File? = null,
        offlineMode: Boolean = false,
        logger: Logger? = null
    ) {
        androidResDir.mkdirs()
        val drawableDir = File(androidResDir, "drawable").apply { mkdirs() }
        val valuesDir = File(androidResDir, "values").apply { mkdirs() }

        val resolvedSourceFile: File? = resolveConfiguredFile(
            rawPath = launcherConfig.source,
            projDir = projDir,
            label = "android.launcherIcon.source"
        ) ?: fallbackSourceIconFile

        var effectiveSvgText: String? = null
        var effectiveVectorXml: String? = null
        var effectiveRasterFile: File? = null

        deleteStaleDrawableStem(androidResDir, "icon")

        val isSvgSource = resolvedSourceFile == null ||
            resolvedSourceFile.extension.equals("svg", ignoreCase = true)

        if (isSvgSource) {
            val svgText = resolvedSourceFile?.readText(Charsets.UTF_8)
                ?: GenericUiDefaultIconProvider.loadDefaultIconSvgText(projDir)
            effectiveSvgText = svgText
            val conv = GenericUiAndroidSvgConverter.convertSvgText(
                svgText = svgText,
                sourceName = resolvedSourceFile?.name ?: "icon.svg",
                sdkCommonVersion = svgConverterVersion,
                failOnWarnings = failOnSvgWarnings,
                cacheDir = svgCacheDir,
                classpathFiles = svgConverterClasspath,
                project = project,
                gradleUserHomeDir = gradleUserHomeDir,
                logger = logger,
                allowNetworkDownload = !offlineMode
            )
            if (conv.vectorXml != null) {
                File(drawableDir, "icon.xml").writeText(conv.vectorXml, Charsets.UTF_8)
                effectiveVectorXml = conv.vectorXml
            } else {
                for ((density, px) in ADAPTIVE_DENSITY_SIZES) {
                    val densityDir = File(androidResDir, "drawable-$density").apply { mkdirs() }
                    File(densityDir, "icon.png").writeBytes(
                        GenericUiDefaultIconProvider.renderSvgTextToPngBytes(svgText, px, px)
                    )
                }
            }
        } else {
            effectiveRasterFile = GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
                sourceIconFile = resolvedSourceFile,
                projectRootDir = projDir,
                outputDir = drawableDir,
                fileName = "icon.png"
            )
        }

        if (!launcherConfig.enabled) {
            return
        }

        val insetPercent = launcherConfig.foregroundInsetPercent.coerceIn(0, 45)

        // 1. Foreground inset drawable
        File(drawableDir, "ic_launcher_foreground.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <inset xmlns:android="http://schemas.android.com/apk/res/android"
                android:drawable="@drawable/icon"
                android:inset="${insetPercent}%" />
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        // 2. Background layer (custom drawable, @color/... ref, or generated values/ic_launcher_background.xml)
        val bgDrawableFile = resolveConfiguredFile(
            rawPath = launcherConfig.backgroundDrawable,
            projDir = projDir,
            label = "android.launcherIcon.backgroundDrawable"
        )
        var resolvedLegacyBgHex = resolveColorRefHex(launcherConfig.backgroundColor, resolvedColors) ?: "#FFFFFF"
        val backgroundLayerRef: String = if (bgDrawableFile != null) {
            deleteStaleDrawableStem(androidResDir, "ic_launcher_background_layer")
            if (bgDrawableFile.extension.equals("svg", ignoreCase = true)) {
                val bgSvg = bgDrawableFile.readText(Charsets.UTF_8)
                val bgConv = GenericUiAndroidSvgConverter.convertSvgText(
                    svgText = bgSvg,
                    sourceName = bgDrawableFile.name,
                    sdkCommonVersion = svgConverterVersion,
                    failOnWarnings = failOnSvgWarnings,
                    cacheDir = svgCacheDir,
                    classpathFiles = svgConverterClasspath,
                    project = project,
                    gradleUserHomeDir = gradleUserHomeDir,
                    logger = logger,
                    allowNetworkDownload = !offlineMode
                )
                if (bgConv.vectorXml != null) {
                    File(drawableDir, "ic_launcher_background_layer.xml").writeText(bgConv.vectorXml, Charsets.UTF_8)
                } else {
                    for ((density, px) in ADAPTIVE_DENSITY_SIZES) {
                        val densityDir = File(androidResDir, "drawable-$density").apply { mkdirs() }
                        File(densityDir, "ic_launcher_background_layer.png").writeBytes(
                            GenericUiDefaultIconProvider.renderSvgTextToPngBytes(bgSvg, px, px)
                        )
                    }
                }
            } else {
                GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
                    sourceIconFile = bgDrawableFile,
                    projectRootDir = projDir,
                    outputDir = drawableDir,
                    fileName = "ic_launcher_background_layer.png"
                )
            }
            "@drawable/ic_launcher_background_layer"
        } else if (launcherConfig.backgroundColor.trim().startsWith("@color/")) {
            launcherConfig.backgroundColor.trim()
        } else {
            val dayBgHex = normalizeAndroidColorHex(launcherConfig.backgroundColor, "#FFFFFF")
            resolvedLegacyBgHex = dayBgHex
            File(valuesDir, "ic_launcher_background.xml").writeText(
                buildColorResourceXml("ic_launcher_background", dayBgHex),
                Charsets.UTF_8
            )
            val nightBgRaw = launcherConfig.backgroundColorNight?.trim()?.takeIf { it.isNotEmpty() }
            if (nightBgRaw != null) {
                val nightBgHex = normalizeAndroidColorHex(nightBgRaw, "#121212")
                val valuesNightDir = File(androidResDir, "values-night").apply { mkdirs() }
                File(valuesNightDir, "ic_launcher_background.xml").writeText(
                    buildColorResourceXml("ic_launcher_background", nightBgHex),
                    Charsets.UTF_8
                )
            }
            "@color/ic_launcher_background"
        }

        // 3. Monochrome layer for Android 13+ themed icons
        var monochromeRef: String? = null
        deleteStaleDrawableStem(androidResDir, "ic_launcher_monochrome")
        if (launcherConfig.monochrome) {
            val explicitMonoFile = resolveConfiguredFile(
                rawPath = launcherConfig.monochromeSource,
                projDir = projDir,
                label = "android.launcherIcon.monochromeSource"
            )
            if (explicitMonoFile != null) {
                if (explicitMonoFile.extension.equals("svg", ignoreCase = true)) {
                    val monoSvg = explicitMonoFile.readText(Charsets.UTF_8)
                    val monoConv = GenericUiAndroidSvgConverter.convertSvgText(
                        svgText = monoSvg,
                        sourceName = explicitMonoFile.name,
                        sdkCommonVersion = svgConverterVersion,
                        failOnWarnings = failOnSvgWarnings,
                        cacheDir = svgCacheDir,
                        classpathFiles = svgConverterClasspath,
                        project = project,
                        gradleUserHomeDir = gradleUserHomeDir,
                        logger = logger,
                        allowNetworkDownload = !offlineMode
                    )
                    if (monoConv.vectorXml != null) {
                        val normalizedXml = normalizeVectorXmlToMonochrome(
                            vectorXml = monoConv.vectorXml,
                            fromExplicitSource = true,
                            insetPercent = 0
                        )
                        File(drawableDir, "ic_launcher_monochrome.xml").writeText(normalizedXml, Charsets.UTF_8)
                    } else {
                        File(drawableDir, "ic_launcher_monochrome.png").writeBytes(
                            GenericUiDefaultIconProvider.renderMonochromeRasterPngBytes(
                                sourceIconFile = explicitMonoFile,
                                fallbackSvgText = monoSvg,
                                targetSize = 432,
                                insetPercent = 0
                            )
                        )
                    }
                } else {
                    File(drawableDir, "ic_launcher_monochrome.png").writeBytes(
                        GenericUiDefaultIconProvider.renderMonochromeRasterPngBytes(
                            sourceIconFile = explicitMonoFile,
                            fallbackSvgText = null,
                            targetSize = 432,
                            insetPercent = 0
                        )
                    )
                }
                monochromeRef = "@drawable/ic_launcher_monochrome"
            } else if (effectiveVectorXml != null) {
                val derivedMonoXml = normalizeVectorXmlToMonochrome(
                    vectorXml = effectiveVectorXml,
                    fromExplicitSource = false,
                    insetPercent = insetPercent
                )
                File(drawableDir, "ic_launcher_monochrome.xml").writeText(derivedMonoXml, Charsets.UTF_8)
                monochromeRef = "@drawable/ic_launcher_monochrome"
            } else {
                File(drawableDir, "ic_launcher_monochrome.png").writeBytes(
                    GenericUiDefaultIconProvider.renderMonochromeRasterPngBytes(
                        sourceIconFile = effectiveRasterFile ?: resolvedSourceFile,
                        fallbackSvgText = effectiveSvgText,
                        targetSize = 432,
                        insetPercent = insetPercent
                    )
                )
                monochromeRef = "@drawable/ic_launcher_monochrome"
            }
        }

        // 4. Adaptive mipmap-anydpi-v26 XMLs
        val anydpiDir = File(androidResDir, "mipmap-anydpi-v26").apply { mkdirs() }
        val adaptiveXml = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">""")
            appendLine("""    <background android:drawable="${escapeXml(backgroundLayerRef)}" />""")
            appendLine("""    <foreground android:drawable="@drawable/ic_launcher_foreground" />""")
            if (monochromeRef != null) {
                appendLine("""    <monochrome android:drawable="${escapeXml(monochromeRef)}" />""")
            }
            appendLine("""</adaptive-icon>""")
        }
        File(anydpiDir, "ic_launcher.xml").writeText(adaptiveXml, Charsets.UTF_8)
        if (launcherConfig.roundIcon) {
            File(anydpiDir, "ic_launcher_round.xml").writeText(adaptiveXml, Charsets.UTF_8)
        } else {
            runCatching { File(anydpiDir, "ic_launcher_round.xml").delete() }
        }

        // 5. Legacy density mipmap-*/ic_launcher.png & ic_launcher_round.png
        if (launcherConfig.legacyPngs) {
            val fgSourceForRaster = effectiveRasterFile ?: resolvedSourceFile
            for ((density, sizePx) in DENSITY_SIZES) {
                val mipDir = File(androidResDir, "mipmap-$density").apply { mkdirs() }
                File(mipDir, "ic_launcher.png").writeBytes(
                    GenericUiDefaultIconProvider.renderLauncherIconPngBytes(
                        sourceIconFile = fgSourceForRaster,
                        fallbackSvgText = effectiveSvgText,
                        targetSize = sizePx,
                        backgroundColorHex = resolvedLegacyBgHex,
                        backgroundDrawableFile = bgDrawableFile,
                        insetPercent = insetPercent,
                        circularMask = false,
                        roundedSquareMask = true
                    )
                )
                if (launcherConfig.roundIcon) {
                    File(mipDir, "ic_launcher_round.png").writeBytes(
                        GenericUiDefaultIconProvider.renderLauncherIconPngBytes(
                            sourceIconFile = fgSourceForRaster,
                            fallbackSvgText = effectiveSvgText,
                            targetSize = sizePx,
                            backgroundColorHex = resolvedLegacyBgHex,
                            backgroundDrawableFile = bgDrawableFile,
                            insetPercent = insetPercent,
                            circularMask = true,
                            roundedSquareMask = false
                        )
                    )
                } else {
                    runCatching { File(mipDir, "ic_launcher_round.png").delete() }
                }
            }
        }
    }

    /**
     * Converts a `VectorDrawable` XML string into a monochrome `#FFFFFFFF` silhouette/outline
     * `VectorDrawable` suitable for Android 13+ `<monochrome>` adaptive icon layers.
     *
     * When [fromExplicitSource] is `false` (derived automatically from `drawable/icon.xml`),
     * paths that have both a visible fill and a visible stroke keep `android:strokeColor="#FFFFFFFF"`
     * and set `android:fillColor="#00000000"` so nested multi-layer shapes remain crisp outlines,
     * while fill-only paths become `#FFFFFFFF`.
     */
    internal fun normalizeVectorXmlToMonochrome(
        vectorXml: String,
        fromExplicitSource: Boolean,
        insetPercent: Int
    ): String {
        val doc = parseXml(vectorXml) ?: return vectorXml
        val root = doc.documentElement ?: return vectorXml
        if (!root.tagName.substringAfter(':').equals("vector", ignoreCase = true)) {
            return vectorXml
        }

        root.removeAttribute("xmlns:aapt")
        val vbW = root.getAttribute("android:viewportWidth").trim().toDoubleOrNull() ?: 108.0
        val vbH = root.getAttribute("android:viewportHeight").trim().toDoubleOrNull() ?: 108.0

        fun removeAaptChildren(el: Element): Pair<Boolean, Boolean> {
            var hadFillGradient = false
            var hadStrokeGradient = false
            val toRemove = mutableListOf<Node>()
            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                if (child.tagName.equals("aapt:attr", ignoreCase = true)) {
                    val attrName = child.getAttribute("name").trim()
                    if (attrName == "android:fillColor") hadFillGradient = true
                    if (attrName == "android:strokeColor") hadStrokeGradient = true
                    toRemove.add(child)
                }
            }
            for (n in toRemove) {
                el.removeChild(n)
            }
            return hadFillGradient to hadStrokeGradient
        }

        fun visit(el: Element) {
            val tag = el.tagName.substringAfter(':').lowercase(Locale.US)
            if (tag == "path") {
                val (hadFillGrad, hadStrokeGrad) = removeAaptChildren(el)
                val rawFill = el.getAttribute("android:fillColor").trim()
                val rawStroke = el.getAttribute("android:strokeColor").trim()

                val hasFill = hadFillGrad || isVisibleColor(rawFill)
                val hasStroke = hadStrokeGrad || isVisibleColor(rawStroke)

                if (fromExplicitSource) {
                    if (hasFill) {
                        el.setAttribute("android:fillColor", "#FFFFFFFF")
                    } else if (rawFill.isNotEmpty()) {
                        el.removeAttribute("android:fillColor")
                    }
                    if (hasStroke) {
                        el.setAttribute("android:strokeColor", "#FFFFFFFF")
                    }
                } else {
                    when {
                        hasFill && hasStroke -> {
                            el.setAttribute("android:fillColor", "#00000000")
                            el.removeAttribute("android:fillAlpha")
                            el.setAttribute("android:strokeColor", "#FFFFFFFF")
                        }
                        hasStroke -> {
                            if (rawFill.isNotEmpty()) {
                                el.removeAttribute("android:fillColor")
                            }
                            el.removeAttribute("android:fillAlpha")
                            el.setAttribute("android:strokeColor", "#FFFFFFFF")
                        }
                        hasFill -> {
                            el.setAttribute("android:fillColor", "#FFFFFFFF")
                        }
                    }
                }
            }
            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                visit(child)
            }
        }
        visit(root)

        val clampedInset = insetPercent.coerceIn(0, 45)
        if (!fromExplicitSource && clampedInset > 0) {
            val scale = (1.0 - 2.0 * (clampedInset / 100.0)).coerceIn(0.1, 1.0)
            val scaleStr = formatFloat(scale)
            val pivotXStr = formatFloat(vbW / 2.0)
            val pivotYStr = formatFloat(vbH / 2.0)

            val childNodes = mutableListOf<Node>()
            val list = root.childNodes
            for (i in 0 until list.length) {
                val n = list.item(i)
                if (n is Element) childNodes.add(n)
            }
            if (childNodes.isNotEmpty()) {
                val group = doc.createElement("group")
                group.setAttribute("android:pivotX", pivotXStr)
                group.setAttribute("android:pivotY", pivotYStr)
                group.setAttribute("android:scaleX", scaleStr)
                group.setAttribute("android:scaleY", scaleStr)
                for (n in childNodes) {
                    root.removeChild(n)
                    group.appendChild(n)
                }
                root.appendChild(group)
            }
        }

        return serializeXml(doc)
    }

    private fun isVisibleColor(colorValue: String): Boolean {
        if (colorValue.isEmpty()) return false
        val lower = colorValue.lowercase(Locale.US)
        if (lower == "none" || lower == "transparent") return false
        if (lower.startsWith("#") && lower.length == 9 && lower.startsWith("#00")) {
            return false
        }
        return true
    }

    private fun resolveConfiguredFile(rawPath: String?, projDir: File, label: String): File? {
        val trimmed = rawPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val candidate = File(trimmed).let { if (it.isAbsolute) it else File(projDir, trimmed) }
        if (!candidate.isFile) {
            throw GradleException("Configured $label file not found: ${candidate.absolutePath}")
        }
        return candidate
    }

    private fun resolveColorRefHex(raw: String, resolvedColors: Map<String, String>): String? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("@color/")) {
            return normalizeAndroidColorHex(trimmed, "#FFFFFF")
        }
        val key = trimmed.removePrefix("@color/").trim()
        val found = resolvedColors[key]?.trim()
        return if (!found.isNullOrEmpty() && !found.startsWith("@")) {
            normalizeAndroidColorHex(found, "#FFFFFF")
        } else {
            null
        }
    }

    private fun deleteStaleDrawableStem(androidResDir: File, stem: String) {
        val baseDrawable = File(androidResDir, "drawable")
        val targetDirs = mutableListOf(baseDrawable)
        for ((density, _) in ADAPTIVE_DENSITY_SIZES) {
            targetDirs.add(File(androidResDir, "drawable-$density"))
        }
        val extsToRemove = setOf("xml") + RASTER_EXTS
        for (dir in targetDirs) {
            if (!dir.isDirectory) continue
            for (ext in extsToRemove) {
                val candidate = File(dir, "$stem.$ext")
                if (candidate.isFile) {
                    runCatching { candidate.delete() }
                }
            }
        }
    }

    private fun buildColorResourceXml(name: String, hexValue: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<resources>""")
        appendLine("""    <color name="${escapeXml(name)}">${escapeXml(hexValue)}</color>""")
        appendLine("""</resources>""")
    }

    private fun normalizeAndroidColorHex(raw: String, defaultColor: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return defaultColor
        if (trimmed.startsWith("@")) return trimmed
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun parseXml(xml: String): Document? = runCatching {
        val dbf = GenericUiBuiltinSvgToVectorConverter.createSecureDocumentBuilderFactory(namespaceAware = false)
        dbf.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }.getOrNull()

    private fun serializeXml(doc: Document): String {
        val tf = TransformerFactory.newInstance()
        runCatching { tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        val transformer = tf.newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toString(Charsets.UTF_8.name())
    }

    private fun formatFloat(value: Double): String {
        val df = DecimalFormat("0.####", DecimalFormatSymbols(Locale.US))
        return df.format(value)
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
