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
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.max

/**
 * Converts `.svg` resources to Android `VectorDrawable` XML using Google's official
 * `com.android.ide.common.vectordrawable.Svg2Vector` (`com.android.tools:sdk-common:<version>`)
 * inside an isolated `URLClassLoader(..., ClassLoader.getPlatformClassLoader())`, with full
 * preprocessing (`currentColor`, root `<svg>` presentation-attribute `<g>` wrapping, inline style
 * expansion, `viewBox` normalization), postprocessing (`<= 108dp` clamping and transparent fill
 * omission), unsupported-element detection, SHA-256 caching, and offline DOM fallback via
 * [GenericUiBuiltinSvgToVectorConverter].
 */
object GenericUiAndroidSvgConverter {

    const val DEFAULT_SDK_COMMON_VERSION: String = "32.1.1"
    private const val PIPELINE_CACHE_VERSION: String = "svg2vec-v1"
    private const val MAX_VECTOR_DP: Double = 108.0

    private val SVG_PRESENTATION_ATTRS: Set<String> = setOf(
        "fill",
        "fill-opacity",
        "fill-rule",
        "stroke",
        "stroke-width",
        "stroke-linecap",
        "stroke-linejoin",
        "stroke-opacity",
        "opacity",
        "clip-rule",
        "display"
    )

    private val SVG_STYLE_EXPAND_ATTRS: Set<String> = SVG_PRESENTATION_ATTRS + setOf(
        "clip-path",
        "stop-color",
        "stop-opacity"
    )

    private val NON_VISUAL_SVG_ROOT_TAGS: Set<String> = setOf(
        "defs",
        "style",
        "title",
        "desc",
        "metadata"
    )

    private val UNSUPPORTED_SVG_ELEMENTS: Set<String> = setOf(
        "text",
        "tspan",
        "textpath",
        "image",
        "filter",
        "mask",
        "foreignobject",
        "animate",
        "animatecolor",
        "animatemotion",
        "animatetransform",
        "set",
        "pattern",
        "script",
        "marker"
    )

    private val classLoaderCache = ConcurrentHashMap<String, URLClassLoader>()

    private data class PreparedSvg(
        val xml: String,
        val hadCurrentColor: Boolean
    )

    /**
     * Converts an SVG [svgFile] to Android `VectorDrawable` XML or raster fallback metadata.
     */
    fun convertSvgFile(
        svgFile: File,
        sdkCommonVersion: String = DEFAULT_SDK_COMMON_VERSION,
        failOnWarnings: Boolean = false,
        cacheDir: File? = null,
        classpathFiles: Collection<File> = emptyList(),
        project: Project? = null,
        gradleUserHomeDir: File? = null,
        logger: Logger? = null,
        forceBuiltinFallback: Boolean = false,
        allowNetworkDownload: Boolean = false
    ): GenericUiSvgConversionResult {
        val svgText = svgFile.readText(Charsets.UTF_8)
        return convertSvgText(
            svgText = svgText,
            sourceName = svgFile.name,
            sdkCommonVersion = sdkCommonVersion,
            failOnWarnings = failOnWarnings,
            cacheDir = cacheDir,
            classpathFiles = classpathFiles,
            project = project,
            gradleUserHomeDir = gradleUserHomeDir,
            logger = logger,
            forceBuiltinFallback = forceBuiltinFallback,
            allowNetworkDownload = allowNetworkDownload
        )
    }

    /**
     * Converts raw [svgText] to Android `VectorDrawable` XML or raster fallback metadata.
     */
    fun convertSvgText(
        svgText: String,
        sourceName: String = "icon.svg",
        sdkCommonVersion: String = DEFAULT_SDK_COMMON_VERSION,
        failOnWarnings: Boolean = false,
        cacheDir: File? = null,
        classpathFiles: Collection<File> = emptyList(),
        project: Project? = null,
        gradleUserHomeDir: File? = null,
        logger: Logger? = null,
        forceBuiltinFallback: Boolean = false,
        allowNetworkDownload: Boolean = false
    ): GenericUiSvgConversionResult {
        val resolvedVersion = sdkCommonVersion.trim().ifEmpty { DEFAULT_SDK_COMMON_VERSION }
        val resolvedClasspath = if (forceBuiltinFallback) {
            emptyList()
        } else {
            resolveSdkCommonClasspath(
                version = resolvedVersion,
                explicitClasspath = classpathFiles,
                project = project,
                gradleUserHomeDir = gradleUserHomeDir,
                cacheDir = cacheDir,
                allowNetworkDownload = allowNetworkDownload,
                logger = logger
            )
        }

        val officialClassLoader = if (forceBuiltinFallback || resolvedClasspath.isEmpty()) {
            null
        } else {
            getOrCreateIsolatedClassLoader(resolvedVersion, resolvedClasspath, logger)
        }

        val toolKey = if (officialClassLoader != null) {
            "sdk-common:$resolvedVersion"
        } else {
            "builtin"
        }
        val cacheKey = sha256Hex("$PIPELINE_CACHE_VERSION|$toolKey|$svgText")
        val cacheFile = cacheDir?.let { File(it, "$cacheKey.cache") }
        if (cacheFile != null && cacheFile.isFile) {
            val cached = readCachedResult(cacheFile)
            if (cached != null) {
                return handleWarningsOutcome(
                    result = cached,
                    sourceName = sourceName,
                    failOnWarnings = failOnWarnings,
                    logger = logger
                )
            }
        }

        val result = if (officialClassLoader != null) {
            runOfficialSvg2Vector(
                svgText = svgText,
                sourceName = sourceName,
                classLoader = officialClassLoader,
                logger = logger
            )
        } else {
            if (!forceBuiltinFallback) {
                logger?.info(
                    "GenericUI: Official com.android.tools:sdk-common:$resolvedVersion Svg2Vector was unavailable; " +
                        "converting '$sourceName' using built-in offline SVG converter."
                )
            }
            GenericUiBuiltinSvgToVectorConverter.convert(svgText, sourceName)
        }

        if (cacheFile != null) {
            writeCachedResult(cacheFile, result)
        }

        return handleWarningsOutcome(
            result = result,
            sourceName = sourceName,
            failOnWarnings = failOnWarnings,
            logger = logger
        )
    }

    /**
     * Preprocesses raw [svgText] for official `Svg2Vector`:
     * - Normalizes `viewBox` separators and strips `dp` suffixes on root `width`/`height`
     * - Replaces `currentColor` with `#000000` (and `"transparent"` with `"none"`)
     * - Expands inline `style="..."` presentation declarations onto XML attributes
     * - Moves presentation attributes (`fill="none"`, `stroke`, `stroke-width`, etc.) from root `<svg>`
     *   onto a `<g>` wrapper around visual child elements so `Svg2Vector` inherits them faithfully.
     */
    fun preprocessSvgForSvg2Vector(svgText: String): String =
        prepareSvgForSvg2Vector(svgText).xml

    /**
     * Postprocesses VectorDrawable XML emitted by `Svg2Vector`:
     * - Scales `android:width` and `android:height` proportionally to `<= 108dp` when larger
     *   (keeping `android:viewportWidth` / `android:viewportHeight` unchanged)
     * - Replaces any remaining `currentColor` with `#FF000000`
     * - Removes `#00000000` / `none` / `transparent` `android:fillColor` attributes so stroke-only
     *   paths omit `android:fillColor`.
     */
    fun postprocessVectorDrawableXml(vectorXml: String): String =
        postprocessVectorDrawableXml(vectorXml, normalizeBlackToArgb = false)

    private fun postprocessVectorDrawableXml(
        vectorXml: String,
        normalizeBlackToArgb: Boolean
    ): String {
        val doc = parseXmlDocument(vectorXml, namespaceAware = false) ?: return vectorXml
        val root = doc.documentElement ?: return vectorXml
        if (stripPrefix(root.tagName).lowercase(Locale.US) != "vector") return vectorXml

        val vbW = root.getAttribute("android:viewportWidth").trim().toDoubleOrNull() ?: 24.0
        val vbH = root.getAttribute("android:viewportHeight").trim().toDoubleOrNull() ?: 24.0
        val rawW = parseDpValue(root.getAttribute("android:width")) ?: vbW
        val rawH = parseDpValue(root.getAttribute("android:height")) ?: vbH

        val maxDim = max(rawW, rawH)
        if (maxDim > MAX_VECTOR_DP) {
            val scale = MAX_VECTOR_DP / maxDim
            root.setAttribute("android:width", "${formatNumber(rawW * scale)}dp")
            root.setAttribute("android:height", "${formatNumber(rawH * scale)}dp")
        }

        fun visit(el: Element) {
            val tag = stripPrefix(el.tagName).lowercase(Locale.US)
            if (tag == "path") {
                val fill = el.getAttribute("android:fillColor").trim()
                if (fill.equals("currentColor", ignoreCase = true)) {
                    el.setAttribute("android:fillColor", "#FF000000")
                } else if (fill.equals("none", ignoreCase = true) ||
                    fill.equals("transparent", ignoreCase = true) ||
                    fill == "#00000000"
                ) {
                    el.removeAttribute("android:fillColor")
                } else if (normalizeBlackToArgb && fill == "#000000") {
                    el.setAttribute("android:fillColor", "#FF000000")
                }

                val stroke = el.getAttribute("android:strokeColor").trim()
                if (stroke.equals("currentColor", ignoreCase = true)) {
                    el.setAttribute("android:strokeColor", "#FF000000")
                } else if (stroke.equals("none", ignoreCase = true) ||
                    stroke.equals("transparent", ignoreCase = true) ||
                    stroke == "#00000000"
                ) {
                    el.removeAttribute("android:strokeColor")
                } else if (normalizeBlackToArgb && stroke == "#000000") {
                    el.setAttribute("android:strokeColor", "#FF000000")
                }
            } else if (tag == "item") {
                val col = el.getAttribute("android:color").trim()
                if (col.equals("currentColor", ignoreCase = true)) {
                    el.setAttribute("android:color", "#FF000000")
                } else if (normalizeBlackToArgb && col == "#000000") {
                    el.setAttribute("android:color", "#FF000000")
                }
            }

            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                visit(child)
            }
        }
        visit(root)

        return serializeDocument(doc)
    }

    /**
     * Inspects the SVG DOM for unsupported elements (`<text>`, `<image>`, `<filter>`, `<mask>`,
     * `<foreignObject>`, animations, etc.) and returns descriptive warning messages.
     */
    fun detectUnsupportedSvgElements(svgText: String): List<String> {
        val doc = parseXmlDocument(svgText, namespaceAware = false) ?: return emptyList()
        val root = doc.documentElement ?: return emptyList()
        val warnings = linkedSetOf<String>()
        fun walk(el: Element) {
            val tag = stripPrefix(el.tagName).lowercase(Locale.US)
            if (tag in UNSUPPORTED_SVG_ELEMENTS) {
                warnings.add("Unsupported SVG element <$tag>")
            }
            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                walk(child)
            }
        }
        walk(root)
        return warnings.toList()
    }

    /**
     * Resolves `com.android.tools:sdk-common:<version>` and its runtime classpath JARs without
     * mutating `project.repositories`:
     * 1. Explicit [explicitClasspath] if it contains `sdk-common`.
     * 2. Gradle `detachedConfiguration` when [project] has repositories available.
     * 3. Local Gradle module cache (`GRADLE_USER_HOME/caches/modules-2/files-2.1`) and `~/.m2/repository`.
     * 4. Optional HTTP download from Google Maven + Maven Central when [allowNetworkDownload] is `true`.
     */
    fun resolveSdkCommonClasspath(
        version: String = DEFAULT_SDK_COMMON_VERSION,
        explicitClasspath: Collection<File> = emptyList(),
        project: Project? = null,
        gradleUserHomeDir: File? = null,
        cacheDir: File? = null,
        allowNetworkDownload: Boolean = false,
        logger: Logger? = null
    ): List<File> {
        val validExplicit = explicitClasspath.filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
        if (validExplicit.any { it.name.contains("sdk-common", ignoreCase = true) }) {
            return validExplicit
        }

        if (project != null) {
            val detached = tryResolveViaDetachedConfiguration(project, version)
            if (detached.any { it.name.contains("sdk-common", ignoreCase = true) }) {
                return detached
            }
        }

        val resolvedGradleHome = gradleUserHomeDir
            ?: System.getenv("GRADLE_USER_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }
            ?: File(System.getProperty("user.home"), ".gradle")

        val fromCache = resolveFromLocalCaches(resolvedGradleHome, version)
        if (fromCache.any { it.name.contains("sdk-common", ignoreCase = true) }) {
            return fromCache
        }

        val isOffline = runCatching { project?.gradle?.startParameter?.isOffline == true }.getOrDefault(false)
        if (allowNetworkDownload && !isOffline) {
            val dlDir = File(cacheDir ?: File(resolvedGradleHome, "caches/genericui-svg2vector"), version)
            val downloaded = tryDownloadFromGoogleAndMavenCentral(version, dlDir, logger)
            if (downloaded.any { it.name.contains("sdk-common", ignoreCase = true) }) {
                return downloaded
            }
        }

        return emptyList()
    }

    internal fun tryResolveViaDetachedConfiguration(project: Project, version: String): List<File> = runCatching {
        val dep = project.dependencies.create("com.android.tools:sdk-common:$version")
        val cfg = project.configurations.detachedConfiguration(dep)
        cfg.isTransitive = true
        cfg.resolve().filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
    }.getOrElse { emptyList() }

    private fun runOfficialSvg2Vector(
        svgText: String,
        sourceName: String,
        classLoader: URLClassLoader,
        logger: Logger?
    ): GenericUiSvgConversionResult {
        val unsupportedWarnings = detectUnsupportedSvgElements(svgText)
        val prepared = prepareSvgForSvg2Vector(svgText)
        val tempFile = Files.createTempFile("genericui-svg2vec-", ".svg").toFile()
        val outStream = ByteArrayOutputStream()

        val rawErrorMessage: String
        try {
            tempFile.writeText(prepared.xml, Charsets.UTF_8)
            val svg2VectorClass = Class.forName("com.android.ide.common.vectordrawable.Svg2Vector", true, classLoader)
            val parseMethod = svg2VectorClass.getMethod("parseSvgToXml", Path::class.java, OutputStream::class.java)
            rawErrorMessage = (parseMethod.invoke(null, tempFile.toPath(), outStream) as? String).orEmpty()
        } catch (ex: InvocationTargetException) {
            val cause = ex.targetException ?: ex
            if (cause is LinkageError || cause is ClassNotFoundException) {
                logger?.info(
                    "GenericUI: Official Svg2Vector dependency missing for '$sourceName' " +
                        "(${cause.javaClass.simpleName}: ${cause.message}); falling back to built-in SVG converter."
                )
                return GenericUiBuiltinSvgToVectorConverter.convert(svgText, sourceName)
            }
            val msg = "Svg2Vector failed for '$sourceName': ${cause.message ?: cause.javaClass.simpleName}"
            logger?.warn(msg)
            return GenericUiSvgConversionResult(
                vectorXml = null,
                warnings = (unsupportedWarnings + msg).distinct(),
                usedOfficialTool = true,
                fellBackToRaster = true
            )
        } catch (t: Throwable) {
            // Classloader / missing transitive class -> degrade cleanly to built-in converter
            logger?.info(
                "GenericUI: Official Svg2Vector failed to load for '$sourceName' (${t.javaClass.simpleName}: ${t.message}); " +
                    "falling back to built-in SVG converter."
            )
            return GenericUiBuiltinSvgToVectorConverter.convert(svgText, sourceName)
        } finally {
            runCatching { tempFile.delete() }
        }

        val svg2VecWarnings = rawErrorMessage
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val allWarnings = (unsupportedWarnings + svg2VecWarnings).distinct()
        val rawOutputXml = outStream.toString(Charsets.UTF_8.name()).trim()

        if (allWarnings.isNotEmpty() || rawOutputXml.isEmpty() || !rawOutputXml.contains("<vector")) {
            val finalWarnings = if (allWarnings.isEmpty()) {
                listOf("Svg2Vector produced empty or invalid VectorDrawable output for '$sourceName'.")
            } else {
                allWarnings
            }
            return GenericUiSvgConversionResult(
                vectorXml = null,
                warnings = finalWarnings,
                usedOfficialTool = true,
                fellBackToRaster = true
            )
        }

        val postprocessed = postprocessVectorDrawableXml(
            vectorXml = rawOutputXml,
            normalizeBlackToArgb = prepared.hadCurrentColor
        )
        return GenericUiSvgConversionResult(
            vectorXml = postprocessed,
            warnings = emptyList(),
            usedOfficialTool = true,
            fellBackToRaster = false
        )
    }

    private fun handleWarningsOutcome(
        result: GenericUiSvgConversionResult,
        sourceName: String,
        failOnWarnings: Boolean,
        logger: Logger?
    ): GenericUiSvgConversionResult {
        if (result.warnings.isNotEmpty()) {
            val formatted = result.warnings.joinToString("; ")
            if (failOnWarnings) {
                throw GradleException("SVG conversion failed for $sourceName: $formatted")
            }
            logger?.warn(
                "GenericUI: SVG '$sourceName' produced VectorDrawable conversion warnings ($formatted); " +
                    "falling back to multi-density PNG rasterization."
            )
        }
        return result
    }

    private fun prepareSvgForSvg2Vector(svgText: String): PreparedSvg {
        val currentColorRegex = Regex("\\bcurrentColor\\b", RegexOption.IGNORE_CASE)
        val hadCurrentColor = currentColorRegex.containsMatchIn(svgText)
        val doc = parseXmlDocument(svgText, namespaceAware = false)
            ?: return PreparedSvg(
                xml = currentColorRegex.replace(svgText, "#000000"),
                hadCurrentColor = hadCurrentColor
            )
        val root = doc.documentElement
            ?: return PreparedSvg(
                xml = currentColorRegex.replace(svgText, "#000000"),
                hadCurrentColor = hadCurrentColor
            )
        if (stripPrefix(root.tagName).lowercase(Locale.US) != "svg") {
            return PreparedSvg(
                xml = currentColorRegex.replace(svgText, "#000000"),
                hadCurrentColor = hadCurrentColor
            )
        }

        // Normalize root viewBox & dp width/height
        val rawViewBox = root.getAttribute("viewBox").trim()
        if (rawViewBox.isNotEmpty()) {
            val normalizedVb = rawViewBox.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.joinToString(" ")
            root.setAttribute("viewBox", normalizedVb)
        }
        for (dimAttr in listOf("width", "height")) {
            val v = root.getAttribute(dimAttr).trim()
            if (v.endsWith("dp", ignoreCase = true)) {
                root.setAttribute(dimAttr, v.dropLast(2).trim() + "px")
            }
        }

        fun normalizeColorValue(raw: String): String {
            var updated = currentColorRegex.replace(raw.trim(), "#000000")
            if (updated.equals("transparent", ignoreCase = true)) {
                updated = "none"
            }
            return updated
        }

        fun normalizeElementTree(el: Element) {
            val styleRaw = el.getAttribute("style")
            if (styleRaw.isNotBlank()) {
                val styleProps = GenericUiBuiltinSvgToVectorConverter.parseInlineStyle(styleRaw)
                val remaining = mutableListOf<String>()
                for ((k, v) in styleProps) {
                    val cleanVal = normalizeColorValue(v)
                    if (k in SVG_STYLE_EXPAND_ATTRS) {
                        el.setAttribute(k, cleanVal)
                    } else {
                        remaining.add("$k:$cleanVal")
                    }
                }
                if (remaining.isEmpty()) {
                    el.removeAttribute("style")
                } else {
                    el.setAttribute("style", remaining.joinToString(";"))
                }
            }

            for (colorAttr in listOf("fill", "stroke", "stop-color", "color")) {
                val attrVal = el.getAttribute(colorAttr)
                if (attrVal.isNotEmpty()) {
                    el.setAttribute(colorAttr, normalizeColorValue(attrVal))
                }
            }

            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                normalizeElementTree(child)
            }
        }
        normalizeElementTree(root)

        // Wrap visual root children in <g ...> whenever root <svg> holds presentation attributes
        val rootPresentationAttrs = linkedMapOf<String, String>()
        for (attr in SVG_PRESENTATION_ATTRS) {
            val value = root.getAttribute(attr).trim()
            if (value.isNotEmpty()) {
                rootPresentationAttrs[attr] = value
                root.removeAttribute(attr)
            }
        }

        if (rootPresentationAttrs.isNotEmpty()) {
            val visualChildren = mutableListOf<Node>()
            val childList = root.childNodes
            for (i in 0 until childList.length) {
                val node = childList.item(i)
                if (node is Element) {
                    val tag = stripPrefix(node.tagName).lowercase(Locale.US)
                    if (tag !in NON_VISUAL_SVG_ROOT_TAGS) {
                        visualChildren.add(node)
                    }
                }
            }
            if (visualChildren.isNotEmpty()) {
                val groupEl = doc.createElement("g")
                for ((k, v) in rootPresentationAttrs) {
                    groupEl.setAttribute(k, v)
                }
                for (child in visualChildren) {
                    root.removeChild(child)
                    groupEl.appendChild(child)
                }
                root.appendChild(groupEl)
            }
        }

        return PreparedSvg(
            xml = serializeDocument(doc),
            hadCurrentColor = hadCurrentColor
        )
    }

    private fun getOrCreateIsolatedClassLoader(
        version: String,
        jars: List<File>,
        logger: Logger?
    ): URLClassLoader? {
        val sortedPaths = jars.map { it.canonicalPath }.sorted()
        val key = "$version|${sortedPaths.joinToString(";")}"
        return runCatching {
            classLoaderCache.computeIfAbsent(key) {
                val urls = sortedPaths.map { File(it).toURI().toURL() }.toTypedArray()
                URLClassLoader(urls, ClassLoader.getPlatformClassLoader())
            }
        }.onFailure { err ->
            logger?.warn("GenericUI: Failed to initialize isolated Svg2Vector URLClassLoader: ${err.message}")
        }.getOrNull()
    }

    private fun resolveFromLocalCaches(gradleUserHomeDir: File, sdkCommonVersion: String): List<File> {
        val files21 = File(gradleUserHomeDir, "caches/modules-2/files-2.1")
        val m2Repo = File(System.getProperty("user.home"), ".m2/repository")
        val jars = linkedMapOf<String, File>()

        fun findArtifactJar(group: String, artifact: String, exactVersion: String?, preferSuffix: String? = null): File? {
            if (files21.isDirectory) {
                val artDir = File(File(files21, group), artifact)
                if (artDir.isDirectory) {
                    val verDirs = if (exactVersion != null) {
                        val exact = File(artDir, exactVersion)
                        if (exact.isDirectory) listOf(exact) else emptyList()
                    } else {
                        artDir.listFiles()?.filter { it.isDirectory }
                            ?.sortedWith { a, b -> compareVersionsDesc(a.name, b.name) }
                            .orEmpty()
                    }
                    val allCandidates = mutableListOf<File>()
                    for (vd in verDirs) {
                        val candidates = vd.walkTopDown()
                            .filter {
                                it.isFile &&
                                    it.extension.equals("jar", ignoreCase = true) &&
                                    !it.name.endsWith("-sources.jar") &&
                                    !it.name.endsWith("-javadoc.jar") &&
                                    !it.name.endsWith("-tests.jar") &&
                                    !it.name.endsWith("-all.jar") &&
                                    !it.name.endsWith("-common.jar")
                            }
                            .toList()
                        allCandidates.addAll(candidates)
                    }
                    if (preferSuffix != null) {
                        allCandidates.firstOrNull { it.name.contains(preferSuffix) }?.let { return it }
                    }
                    allCandidates.firstOrNull()?.let { return it }
                }
            }
            if (m2Repo.isDirectory) {
                val groupPath = group.replace('.', '/')
                val artDir = File(File(m2Repo, groupPath), artifact)
                if (artDir.isDirectory) {
                    val verDirs = if (exactVersion != null) {
                        val exact = File(artDir, exactVersion)
                        if (exact.isDirectory) listOf(exact) else emptyList()
                    } else {
                        artDir.listFiles()?.filter { it.isDirectory }
                            ?.sortedWith { a, b -> compareVersionsDesc(a.name, b.name) }
                            .orEmpty()
                    }
                    for (vd in verDirs) {
                        val candidates = vd.listFiles()
                            ?.filter {
                                it.isFile &&
                                    it.extension.equals("jar", ignoreCase = true) &&
                                    !it.name.endsWith("-sources.jar") &&
                                    !it.name.endsWith("-javadoc.jar")
                            }
                            .orEmpty()
                        val matched = if (preferSuffix != null) {
                            candidates.firstOrNull { it.name.contains(preferSuffix) } ?: candidates.firstOrNull()
                        } else {
                            candidates.firstOrNull()
                        }
                        if (matched != null) return matched
                    }
                }
            }
            return null
        }

        val sdkCommonJar = findArtifactJar("com.android.tools", "sdk-common", sdkCommonVersion) ?: return emptyList()
        jars["com.android.tools:sdk-common"] = sdkCommonJar

        val coreDeps = listOf(
            Triple("com.android.tools", "common", sdkCommonVersion),
            Triple("com.android.tools", "annotations", sdkCommonVersion),
            Triple("com.android.tools.layoutlib", "layoutlib-api", sdkCommonVersion),
            Triple("com.google.guava", "guava", null),
            Triple("com.google.guava", "failureaccess", null),
            Triple("org.jetbrains.kotlin", "kotlin-stdlib", null),
            Triple("org.jetbrains.kotlin", "kotlin-stdlib-jdk8", null),
            Triple("org.jetbrains.kotlin", "kotlin-stdlib-jdk7", null),
            Triple("org.jetbrains", "annotations", null),
            Triple("net.sf.kxml", "kxml2", null),
            Triple("com.google.code.gson", "gson", null)
        )
        for ((grp, art, ver) in coreDeps) {
            val prefer = if (art == "guava") "-jre" else null
            val found = findArtifactJar(grp, art, ver, prefer)
                ?: if (ver != null) findArtifactJar(grp, art, null, prefer) else null
            if (found != null) {
                jars["$grp:$art"] = found
            }
        }
        return jars.values.toList()
    }

    private fun tryDownloadFromGoogleAndMavenCentral(
        sdkCommonVersion: String,
        targetDir: File,
        logger: Logger?
    ): List<File> {
        targetDir.mkdirs()
        val coords = listOf(
            "https://dl.google.com/dl/android/maven2" to "com/android/tools/sdk-common/$sdkCommonVersion/sdk-common-$sdkCommonVersion.jar",
            "https://dl.google.com/dl/android/maven2" to "com/android/tools/common/$sdkCommonVersion/common-$sdkCommonVersion.jar",
            "https://dl.google.com/dl/android/maven2" to "com/android/tools/annotations/$sdkCommonVersion/annotations-$sdkCommonVersion.jar",
            "https://dl.google.com/dl/android/maven2" to "com/android/tools/layoutlib/layoutlib-api/$sdkCommonVersion/layoutlib-api-$sdkCommonVersion.jar",
            "https://repo1.maven.org/maven2" to "com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar",
            "https://repo1.maven.org/maven2" to "com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar",
            "https://repo1.maven.org/maven2" to "org/jetbrains/kotlin/kotlin-stdlib/2.2.10/kotlin-stdlib-2.2.10.jar",
            "https://repo1.maven.org/maven2" to "org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar",
            "https://repo1.maven.org/maven2" to "net/sf/kxml/kxml2/2.3.0/kxml2-2.3.0.jar",
            "https://repo1.maven.org/maven2" to "com/google/code/gson/gson/2.11.0/gson-2.11.0.jar"
        )
        val downloaded = mutableListOf<File>()
        for ((repoBase, relPath) in coords) {
            val fileName = relPath.substringAfterLast('/')
            val outFile = File(targetDir, fileName)
            if (outFile.isFile && outFile.length() > 0L) {
                downloaded.add(outFile)
                continue
            }
            val ok = downloadUrlToFile("$repoBase/$relPath", outFile)
            if (ok) {
                downloaded.add(outFile)
            } else {
                runCatching { outFile.delete() }
                if (fileName.startsWith("sdk-common-")) {
                    logger?.info("GenericUI: Could not download $fileName from $repoBase.")
                    return emptyList()
                }
            }
        }
        return downloaded
    }

    private fun downloadUrlToFile(urlStr: String, target: File): Boolean = runCatching {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return false
        }
        val tmp = File(target.parentFile, "${target.name}.tmp")
        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()
        if (tmp.isFile && tmp.length() > 0L) {
            tmp.renameTo(target)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    private fun readCachedResult(file: File): GenericUiSvgConversionResult? = runCatching {
        val text = file.readText(Charsets.UTF_8)
        val marker = "\n---XML---\n"
        val idx = text.indexOf(marker)
        if (idx < 0) return null
        val headerLines = text.substring(0, idx).lines()
        val xmlBody = text.substring(idx + marker.length).takeIf { it.isNotBlank() }

        var usedOfficial = false
        var fellBack = false
        var warningsCount = 0
        val warnings = mutableListOf<String>()
        var readingWarnings = false
        for (line in headerLines) {
            when {
                line.startsWith("usedOfficial=") -> usedOfficial = line.removePrefix("usedOfficial=").toBoolean()
                line.startsWith("fellBack=") -> fellBack = line.removePrefix("fellBack=").toBoolean()
                line.startsWith("warningsCount=") -> {
                    warningsCount = line.removePrefix("warningsCount=").toIntOrNull() ?: 0
                    readingWarnings = warningsCount > 0
                }
                readingWarnings && warnings.size < warningsCount -> {
                    warnings.add(line.replace("\\n", "\n").replace("\\r", "\r"))
                }
            }
        }
        GenericUiSvgConversionResult(
            vectorXml = xmlBody,
            warnings = warnings,
            usedOfficialTool = usedOfficial,
            fellBackToRaster = fellBack
        )
    }.getOrNull()

    private fun writeCachedResult(file: File, result: GenericUiSvgConversionResult) {
        runCatching {
            file.parentFile?.mkdirs()
            val content = buildString {
                appendLine("usedOfficial=${result.usedOfficialTool}")
                appendLine("fellBack=${result.fellBackToRaster}")
                appendLine("warningsCount=${result.warnings.size}")
                for (w in result.warnings) {
                    appendLine(w.replace("\r", "\\r").replace("\n", "\\n"))
                }
                append("---XML---\n")
                if (result.vectorXml != null) {
                    append(result.vectorXml)
                }
            }
            file.writeText(content, Charsets.UTF_8)
        }
    }

    private fun parseXmlDocument(xml: String, namespaceAware: Boolean): Document? = runCatching {
        val dbf = GenericUiBuiltinSvgToVectorConverter.createSecureDocumentBuilderFactory(namespaceAware = namespaceAware)
        dbf.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }.getOrNull()

    private fun serializeDocument(doc: Document): String {
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

    private fun parseDpValue(raw: String?): Double? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return t.removeSuffix("dp").removeSuffix("dip").removeSuffix("px").trim().toDoubleOrNull()
    }

    private fun formatNumber(value: Double): String {
        val df = DecimalFormat("0.####", DecimalFormatSymbols(Locale.US))
        return df.format(value)
    }

    private fun stripPrefix(tagName: String): String =
        tagName.substringAfter(':')

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun compareVersionsDesc(a: String, b: String): Int {
        val pa = a.split('.', '-').map { it.toIntOrNull() }
        val pb = b.split('.', '-').map { it.toIntOrNull() }
        val maxLen = max(pa.size, pb.size)
        for (i in 0 until maxLen) {
            val va = pa.getOrNull(i)
            val vb = pb.getOrNull(i)
            if (va != null && vb != null) {
                if (va != vb) return vb.compareTo(va)
            } else {
                val sa = a.split('.', '-').getOrElse(i) { "" }
                val sb = b.split('.', '-').getOrElse(i) { "" }
                val cmp = sb.compareTo(sa)
                if (cmp != 0) return cmp
            }
        }
        return b.compareTo(a)
    }
}
