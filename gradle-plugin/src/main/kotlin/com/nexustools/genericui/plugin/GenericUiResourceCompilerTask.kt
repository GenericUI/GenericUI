package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Base64

/**
 * Gradle task (`compileGenericUiResources`) that implements an Android-like `R` / `RC` / `Resource`
 * resource compiler and multi-backend resource translator for GenericUI projects.
 *
 * Generates:
 * 1. `R.kt`, `RC.kt`, `Resource.kt`, and `GeneratedResourceRegistry.kt` (`1 Class Per File`) in `kotlinOutputDirectory`.
 * 2. Translated native backend resource bundles in `backendOutputDirectory`:
 *    - Windows Win32 `.rc` script, `.h` header, and staged `windows/assets/...` payloads (`windows/genericui_resources.rc`, `windows/genericui_resource_ids.h`)
 *    - Android `res/values/<name>.xml` & `res/drawable/<name>.xml` (`VectorDrawable` translated from `.svg`)
 *    - macOS / iOS `Localizable.strings` & `Assets.xcassets`
 *    - Linux GTK4 `genericui.gresource.xml` & Qt6 `genericui_resources.qrc`
 *    - Web / Swing `genericui-resources.json`, `genericui-resource-vars.css`, and staged `web/assets/<type>/<fileName>`
 *    - Shared module resource manifest (`manifest/genericui-module-resources.json`) with upstream module merging
 */
abstract class GenericUiResourceCompilerTask : DefaultTask() {

    @get:Input
    abstract val resourcesEnabled: Property<Boolean>

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val resourcePackageName: Property<String>

    @get:Input
    abstract val candidateSourceDirs: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val upstreamResourceManifestPaths: ListProperty<String>

    @get:Input
    abstract val translateForAllBackends: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val themeColor: Property<String>

    @get:Input
    @get:Optional
    abstract val appName: Property<String>

    @get:OutputDirectory
    abstract val kotlinOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val backendOutputDirectory: DirectoryProperty

    private data class DrawableEntry(
        val name: String,
        val numericId: Int,
        val relativePath: String,
        val format: String,
        val svgSource: String?,
        val svgPaths: List<String>,
        val viewBoxWidth: Float,
        val viewBoxHeight: Float,
        val base64Data: String?
    ) {
        val win32ResourceId: Int
            get() = win32IdFor(0x01, numericId)
    }

    private data class ValueEntry(
        val name: String,
        val numericId: Int,
        val rawValue: String,
        val base64Data: String? = null
    ) {
        fun win32ResourceId(typeCode: Int): Int = win32IdFor(typeCode, numericId)
    }

    private data class RawEntry(
        val name: String,
        val numericId: Int,
        val relativePath: String,
        val textContent: String?,
        val base64Content: String?
    ) {
        fun win32ResourceId(typeCode: Int = 0x07): Int = win32IdFor(typeCode, numericId)
    }

    @TaskAction
    fun compileResources() {
        val ktOutRoot = kotlinOutputDirectory.get().asFile
        val backendOutRoot = backendOutputDirectory.get().asFile
        ktOutRoot.deleteRecursively()
        backendOutRoot.deleteRecursively()
        ktOutRoot.mkdirs()
        backendOutRoot.mkdirs()

        if (!resourcesEnabled.getOrElse(true)) {
            return
        }

        val projDir = File(projectRootPath.get())
        val pkgName = resourcePackageName.get().trim().ifEmpty { "com.nexustools.genericui.generated.res" }
        val existingDirs = candidateSourceDirs.getOrElse(emptyList())
            .map { File(projDir, it) }
            .filter { it.exists() && it.isDirectory }

        val drawables = linkedMapOf<String, DrawableEntry>()
        val strings = linkedMapOf<String, ValueEntry>()
        val colors = linkedMapOf<String, ValueEntry>()
        val dimens = linkedMapOf<String, ValueEntry>()
        val integers = linkedMapOf<String, ValueEntry>()
        val bools = linkedMapOf<String, ValueEntry>()
        val raws = linkedMapOf<String, RawEntry>()
        val fonts = linkedMapOf<String, ValueEntry>()
        val xmls = linkedMapOf<String, RawEntry>()
        val styles = linkedMapOf<String, RawEntry>()
        val cursors = linkedMapOf<String, File>()

        var nextDrawableIdx = 1
        var nextStringIdx = 1
        var nextColorIdx = 1
        var nextDimenIdx = 1
        var nextIntIdx = 1
        var nextBoolIdx = 1
        var nextRawIdx = 1
        var nextFontIdx = 1
        var nextXmlIdx = 1
        var nextStyleIdx = 1

        for (root in existingDirs) {
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.relativeTo(root).invariantSeparatorsPath
                val parentDir = file.parentFile?.name?.lowercase() ?: ""
                val ext = file.extension.lowercase()
                val ident = sanitizeIdentifier(file.nameWithoutExtension)

                when {
                    parentDir == "cursors" || parentDir == "cursor" || ext == "cur" -> {
                        if (ident.isNotEmpty() && ext in setOf("svg", "png", "cur", "ico", "webp", "jpg", "jpeg")) {
                            cursors.putIfAbsent(ident, file)
                            if (ident !in drawables) {
                                val numId = (0x7f01 shl 16) or (nextDrawableIdx++ and 0xFFFF)
                                if (ext == "svg") {
                                    val svgText = file.readText()
                                    val (vbW, vbH) = extractSvgViewBox(svgText)
                                    val pathList = extractSvgPaths(svgText)
                                    drawables[ident] = DrawableEntry(
                                        name = ident,
                                        numericId = numId,
                                        relativePath = relPath,
                                        format = "svg",
                                        svgSource = svgText,
                                        svgPaths = pathList,
                                        viewBoxWidth = vbW,
                                        viewBoxHeight = vbH,
                                        base64Data = null
                                    )
                                } else {
                                    val b64 = Base64.getEncoder().encodeToString(file.readBytes())
                                    drawables[ident] = DrawableEntry(
                                        name = ident,
                                        numericId = numId,
                                        relativePath = relPath,
                                        format = ext,
                                        svgSource = null,
                                        svgPaths = emptyList(),
                                        viewBoxWidth = 32f,
                                        viewBoxHeight = 32f,
                                        base64Data = b64
                                    )
                                }
                            }
                        }
                    }
                    parentDir.startsWith("drawable") || parentDir == "icons" || parentDir == "images" ||
                        (parentDir == root.name.lowercase() && ext in setOf("svg", "png", "ico", "webp", "jpg")) -> {
                        if (ident.isNotEmpty() && ident !in drawables && ext in setOf("svg", "png", "ico", "webp", "jpg", "jpeg")) {
                            if (ident.contains("cursor")) {
                                cursors.putIfAbsent(ident, file)
                            }
                            val numId = (0x7f01 shl 16) or (nextDrawableIdx++ and 0xFFFF)
                            if (ext == "svg") {
                                val svgText = file.readText()
                                val (vbW, vbH) = extractSvgViewBox(svgText)
                                val pathList = extractSvgPaths(svgText)
                                drawables[ident] = DrawableEntry(
                                    name = ident,
                                    numericId = numId,
                                    relativePath = relPath,
                                    format = "svg",
                                    svgSource = svgText,
                                    svgPaths = pathList,
                                    viewBoxWidth = vbW,
                                    viewBoxHeight = vbH,
                                    base64Data = null
                                )
                            } else {
                                val b64 = Base64.getEncoder().encodeToString(file.readBytes())
                                drawables[ident] = DrawableEntry(
                                    name = ident,
                                    numericId = numId,
                                    relativePath = relPath,
                                    format = ext,
                                    svgSource = null,
                                    svgPaths = emptyList(),
                                    viewBoxWidth = 24f,
                                    viewBoxHeight = 24f,
                                    base64Data = b64
                                )
                            }
                        }
                    }
                    (parentDir in setOf("styles", "style", "css", "scss", "sass") || parentDir == root.name.lowercase()) &&
                        ext in setOf("scss", "sass", "css") && !file.name.startsWith("_") -> {
                        if (ident.isNotEmpty() && ident !in styles) {
                            val numId = (0x7f0c shl 16) or (nextStyleIdx++ and 0xFFFF)
                            styles[ident] = RawEntry(
                                name = ident,
                                numericId = numId,
                                relativePath = relPath,
                                textContent = file.readText(),
                                base64Content = null
                            )
                        }
                    }
                    parentDir.startsWith("values") && ext == "xml" -> {
                        parseAndroidValuesXml(
                            xmlText = file.readText(),
                            onString = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in strings) {
                                    strings[id] = ValueEntry(id, (0x7f02 shl 16) or (nextStringIdx++ and 0xFFFF), v)
                                }
                            },
                            onColor = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in colors) {
                                    colors[id] = ValueEntry(id, (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF), v)
                                }
                            },
                            onDimen = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in dimens) {
                                    dimens[id] = ValueEntry(id, (0x7f04 shl 16) or (nextDimenIdx++ and 0xFFFF), v)
                                }
                            },
                            onInteger = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in integers) {
                                    integers[id] = ValueEntry(id, (0x7f05 shl 16) or (nextIntIdx++ and 0xFFFF), v)
                                }
                            },
                            onBool = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in bools) {
                                    bools[id] = ValueEntry(id, (0x7f06 shl 16) or (nextBoolIdx++ and 0xFFFF), v)
                                }
                            }
                        )
                    }
                    parentDir == "raw" || parentDir == "scripts" -> {
                        if (ident.isNotEmpty() && ident !in raws) {
                            val numId = (0x7f07 shl 16) or (nextRawIdx++ and 0xFFFF)
                            val isText = ext in setOf("txt", "json", "js", "ts", "glsl", "wgsl", "xml", "csv", "md")
                            raws[ident] = RawEntry(
                                name = ident,
                                numericId = numId,
                                relativePath = relPath,
                                textContent = if (isText) file.readText() else null,
                                base64Content = if (!isText) Base64.getEncoder().encodeToString(file.readBytes()) else null
                            )
                        }
                    }
                    parentDir == "font" || parentDir == "fonts" -> {
                        if (ident.isNotEmpty() && ident !in fonts && ext in setOf("ttf", "otf", "woff", "woff2")) {
                            val numId = (0x7f08 shl 16) or (nextFontIdx++ and 0xFFFF)
                            val b64 = Base64.getEncoder().encodeToString(file.readBytes())
                            fonts[ident] = ValueEntry(ident, numId, relPath, b64)
                        }
                    }
                    parentDir.startsWith("xml") && ext == "xml" -> {
                        if (ident.isNotEmpty() && ident !in xmls) {
                            val numId = (0x7f09 shl 16) or (nextXmlIdx++ and 0xFFFF)
                            xmls[ident] = RawEntry(
                                name = ident,
                                numericId = numId,
                                relativePath = relPath,
                                textContent = file.readText(),
                                base64Content = null
                            )
                        }
                    }
                }
            }
        }

        // Merge upstream shared KMP module resource manifests without ID collisions
        val upstreamPaths = upstreamResourceManifestPaths.getOrElse(emptyList())
        for (rawManifestPath in upstreamPaths) {
            val candidate = File(rawManifestPath)
            val manifestFile = when {
                candidate.isFile -> candidate
                File(candidate, "manifest/genericui-module-resources.json").isFile ->
                    File(candidate, "manifest/genericui-module-resources.json")
                File(candidate, "genericui-module-resources.json").isFile ->
                    File(candidate, "genericui-module-resources.json")
                else -> null
            }
            if (manifestFile != null && manifestFile.exists()) {
                mergeUpstreamManifest(
                    manifestFile = manifestFile,
                    drawables = drawables,
                    strings = strings,
                    colors = colors,
                    dimens = dimens,
                    integers = integers,
                    bools = bools,
                    raws = raws,
                    fonts = fonts,
                    xmls = xmls,
                    styles = styles,
                    nextDrawableId = { (0x7f01 shl 16) or (nextDrawableIdx++ and 0xFFFF) },
                    nextStringId = { (0x7f02 shl 16) or (nextStringIdx++ and 0xFFFF) },
                    nextColorId = { (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF) },
                    nextDimenId = { (0x7f04 shl 16) or (nextDimenIdx++ and 0xFFFF) },
                    nextIntId = { (0x7f05 shl 16) or (nextIntIdx++ and 0xFFFF) },
                    nextBoolId = { (0x7f06 shl 16) or (nextBoolIdx++ and 0xFFFF) },
                    nextRawId = { (0x7f07 shl 16) or (nextRawIdx++ and 0xFFFF) },
                    nextFontId = { (0x7f08 shl 16) or (nextFontIdx++ and 0xFFFF) },
                    nextXmlId = { (0x7f09 shl 16) or (nextXmlIdx++ and 0xFFFF) },
                    nextStyleId = { (0x7f0c shl 16) or (nextStyleIdx++ and 0xFFFF) }
                )
            }
        }

        val pkgDir = File(ktOutRoot, pkgName.replace('.', '/'))
        pkgDir.mkdirs()

        // Ensure default GenericUI icon.svg is available as R.drawable.icon if no icon drawable was provided
        if ("icon" !in drawables) {
            val defaultSvg = GenericUiDefaultIconProvider.loadDefaultIconSvgText(projDir)
            val (vbW, vbH) = extractSvgViewBox(defaultSvg)
            val pathList = extractSvgPaths(defaultSvg)
            val numId = (0x7f01 shl 16) or (nextDrawableIdx++ and 0xFFFF)
            drawables["icon"] = DrawableEntry(
                name = "icon",
                numericId = numId,
                relativePath = "drawable/icon.svg",
                format = "svg",
                svgSource = defaultSvg,
                svgPaths = pathList,
                viewBoxWidth = vbW,
                viewBoxHeight = vbH,
                base64Data = null
            )
        }

        val configuredThemeColor = themeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
        if (configuredThemeColor != null) {
            if ("theme_color" !in colors) {
                colors["theme_color"] = ValueEntry(
                    name = "theme_color",
                    numericId = (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF),
                    rawValue = configuredThemeColor
                )
            }
            if ("colorPrimary" !in colors) {
                colors["colorPrimary"] = ValueEntry(
                    name = "colorPrimary",
                    numericId = (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF),
                    rawValue = configuredThemeColor
                )
            }
        }

        val configuredAppName = appName.orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "GenericUI"

        // 1. Emit GeneratedResourceRegistry.kt (1 Class Per File)
        File(pkgDir, "GeneratedResourceRegistry.kt").writeText(
            buildGeneratedResourceRegistryKotlin(
                pkgName = pkgName,
                appName = configuredAppName,
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList(),
                integers = integers.values.toList(),
                bools = bools.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList(),
                xmls = xmls.values.toList(),
                styles = styles.values.toList()
            )
        )

        // 2. Emit R.kt (1 Class Per File)
        File(pkgDir, "R.kt").writeText(
            buildRClassKotlin(
                pkgName = pkgName,
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList(),
                integers = integers.values.toList(),
                bools = bools.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList(),
                xmls = xmls.values.toList(),
                styles = styles.values.toList()
            )
        )

        // 3. Emit RC.kt (1 Class Per File)
        File(pkgDir, "RC.kt").writeText(buildRcAliasKotlin(pkgName))

        // 4. Emit Resource.kt (1 Class Per File)
        File(pkgDir, "Resource.kt").writeText(
            buildResourceObjectKotlin(
                pkgName = pkgName,
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList(),
                integers = integers.values.toList(),
                bools = bools.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList(),
                xmls = xmls.values.toList(),
                styles = styles.values.toList()
            )
        )

        // 5. Emit shared module resource manifest (backendOutRoot/manifest/genericui-module-resources.json)
        emitModuleResourceManifest(
            outDir = File(backendOutRoot, "manifest"),
            pkgName = pkgName,
            drawables = drawables.values.toList(),
            strings = strings.values.toList(),
            colors = colors.values.toList(),
            dimens = dimens.values.toList(),
            integers = integers.values.toList(),
            bools = bools.values.toList(),
            raws = raws.values.toList(),
            fonts = fonts.values.toList(),
            xmls = xmls.values.toList(),
            styles = styles.values.toList()
        )

        // 6. Emit translated native backend resource files when enabled
        if (translateForAllBackends.getOrElse(true)) {
            emitWindowsRcTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "windows"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList(),
                xmls = xmls.values.toList(),
                styles = styles.values.toList(),
                cursors = cursors
            )
            emitAndroidResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "android/res"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList()
            )
            emitAppleResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "apple"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList()
            )
            emitLinuxGtkAndQtTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "linux"),
                pkgName = pkgName,
                drawables = drawables.values.toList(),
                raws = raws.values.toList()
            )
            emitWebAndSwingResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "web"),
                drawables = drawables.values.toList(),
                strings = strings.values.toList(),
                colors = colors.values.toList(),
                dimens = dimens.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList(),
                xmls = xmls.values.toList()
            )
        }
    }

    private fun buildGeneratedResourceRegistryKotlin(
        pkgName: String,
        appName: String,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        integers: List<ValueEntry>,
        bools: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>,
        styles: List<RawEntry> = emptyList()
    ): String = buildString {
        appendLine("package $pkgName")
        appendLine()
        appendLine("import com.nexustools.genericui.core.GenericUI")
        appendLine("import com.nexustools.genericui.res.DrawableResource")
        appendLine("import com.nexustools.genericui.res.ResourceId")
        appendLine("import com.nexustools.genericui.res.ResourceType")
        appendLine("import com.nexustools.genericui.res.Resources")
        appendLine()
        appendLine("/**")
        appendLine(" * Auto-generated by GenericUI `compileGenericUiResources`.")
        appendLine(" * Populates [Resources.activeTable] and [GenericUI.applicationName] across all target backends.")
        appendLine(" */")
        appendLine("object GeneratedResourceRegistry {")
        appendLine("    const val APP_NAME: String = ${escapeKotlinString(appName)}")
        appendLine("    private var initialized: Boolean = false")
        appendLine()
        appendLine("    fun ensureInitialized() {")
        appendLine("        if (initialized) return")
        appendLine("        initialized = true")
        appendLine("        GenericUI.applicationName = APP_NAME")
        appendLine("        val table = Resources.activeTable")
        for (d in drawables) {
            val pathsLiteral = if (d.svgPaths.isEmpty()) {
                "emptyList()"
            } else {
                "listOf(" + d.svgPaths.joinToString(", ") { escapeKotlinString(it) } + ")"
            }
            val svgSrcLiteral = d.svgSource?.let { escapeKotlinString(it) } ?: "null"
            val b64Literal = d.base64Data?.let { escapeKotlinString(it) } ?: "null"
            appendLine(
                "        table.registerDrawable(DrawableResource(" +
                    "resId = ResourceId(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)}), " +
                    "format = ${escapeKotlinString(d.format)}, " +
                    "svgSource = $svgSrcLiteral, " +
                    "svgPathDataList = $pathsLiteral, " +
                    "viewBoxWidth = ${d.viewBoxWidth}f, " +
                    "viewBoxHeight = ${d.viewBoxHeight}f, " +
                    "base64Data = $b64Literal))"
            )
        }
        for (s in strings) {
            appendLine(
                "        table.registerString(ResourceId(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)}), ${escapeKotlinString(s.rawValue)})"
            )
        }
        for (c in colors) {
            appendLine(
                "        table.registerColor(ResourceId(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)}), ${escapeKotlinString(c.rawValue)})"
            )
        }
        for (dm in dimens) {
            val numericVal = dm.rawValue.replace(Regex("[^0-9.-]"), "").toFloatOrNull() ?: 0f
            appendLine(
                "        table.registerDimen(ResourceId(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)}), ${numericVal}f)"
            )
        }
        for (iv in integers) {
            val intVal = iv.rawValue.trim().toIntOrNull() ?: 0
            appendLine(
                "        table.registerInteger(ResourceId(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)}), $intVal)"
            )
        }
        for (bv in bools) {
            val boolVal = bv.rawValue.trim().equals("true", ignoreCase = true)
            appendLine(
                "        table.registerBool(ResourceId(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)}), $boolVal)"
            )
        }
        for (rw in raws) {
            if (rw.textContent != null) {
                appendLine(
                    "        table.registerRawText(ResourceId(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)}), ${escapeKotlinString(rw.textContent)})"
                )
            } else if (rw.base64Content != null) {
                appendLine(
                    "        table.registerRawBase64(ResourceId(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)}), ${escapeKotlinString(rw.base64Content)})"
                )
            }
        }
        for (fn in fonts) {
            appendLine(
                "        table.registerFont(ResourceId(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)}), ${escapeKotlinString(fn.rawValue)})"
            )
            if (fn.base64Data != null) {
                appendLine(
                    "        table.registerRawBase64(ResourceId(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)}), ${escapeKotlinString(fn.base64Data)})"
                )
            }
        }
        for (xm in xmls) {
            if (xm.textContent != null) {
                appendLine(
                    "        table.registerRawText(ResourceId(0x${xm.numericId.toString(16)}, ResourceType.XML, ${escapeKotlinString(xm.name)}, ${escapeKotlinString(xm.relativePath)}), ${escapeKotlinString(xm.textContent)})"
                )
            }
        }
        for (st in styles) {
            if (st.textContent != null) {
                appendLine(
                    "        table.registerRawText(ResourceId(0x${st.numericId.toString(16)}, ResourceType.STYLE, ${escapeKotlinString(st.name)}, ${escapeKotlinString(st.relativePath)}), ${escapeKotlinString(st.textContent)})"
                )
            }
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun buildRClassKotlin(
        pkgName: String,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        integers: List<ValueEntry>,
        bools: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>,
        styles: List<RawEntry> = emptyList()
    ): String = buildString {
        appendLine("package $pkgName")
        appendLine()
        appendLine("import com.nexustools.genericui.res.ResourceId")
        appendLine("import com.nexustools.genericui.res.ResourceType")
        appendLine()
        appendLine("/**")
        appendLine(" * Android-like `R` resource class generated by the GenericUI Gradle plugin.")
        appendLine(" */")
        appendLine("object R {")
        appendLine("    init {")
        appendLine("        GeneratedResourceRegistry.ensureInitialized()")
        appendLine("    }")
        appendLine()
        appendLine("    object drawable {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (d in drawables) {
            appendLine(
                "        val ${d.name}: ResourceId = ResourceId(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object string {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (s in strings) {
            appendLine(
                "        val ${s.name}: ResourceId = ResourceId(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object color {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (c in colors) {
            appendLine(
                "        val ${c.name}: ResourceId = ResourceId(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object dimen {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (dm in dimens) {
            appendLine(
                "        val ${dm.name}: ResourceId = ResourceId(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object integer {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (iv in integers) {
            appendLine(
                "        val ${iv.name}: ResourceId = ResourceId(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object bool {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (bv in bools) {
            appendLine(
                "        val ${bv.name}: ResourceId = ResourceId(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object raw {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (rw in raws) {
            appendLine(
                "        val ${rw.name}: ResourceId = ResourceId(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object font {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (fn in fonts) {
            appendLine(
                "        val ${fn.name}: ResourceId = ResourceId(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object xml {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (xm in xmls) {
            appendLine(
                "        val ${xm.name}: ResourceId = ResourceId(0x${xm.numericId.toString(16)}, ResourceType.XML, ${escapeKotlinString(xm.name)}, ${escapeKotlinString(xm.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object style {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (st in styles) {
            appendLine(
                "        val ${st.name}: ResourceId = ResourceId(0x${st.numericId.toString(16)}, ResourceType.STYLE, ${escapeKotlinString(st.name)}, ${escapeKotlinString(st.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine("}")
    }

    private fun buildRcAliasKotlin(pkgName: String): String = """
package $pkgName

import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.res.ResourceId
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.RgbaColor

/**
 * Android/Win32-style `RC` resource accessor generated by the GenericUI Gradle plugin.
 * Provides direct access to [R.drawable], [R.string], [R.color], [R.dimen], [R.raw], [R.font], [R.xml], and [R.style]
 * as well as convenience lookup helpers across all target backends.
 */
object RC {
    init {
        GeneratedResourceRegistry.ensureInitialized()
    }

    val drawable: R.drawable get() = R.drawable
    val string: R.string get() = R.string
    val color: R.color get() = R.color
    val dimen: R.dimen get() = R.dimen
    val integer: R.integer get() = R.integer
    val bool: R.bool get() = R.bool
    val raw: R.raw get() = R.raw
    val font: R.font get() = R.font
    val xml: R.xml get() = R.xml
    val style: R.style get() = R.style

    fun getString(resId: ResourceId): String = Resources.getString(resId)
    fun getString(resId: ResourceId, vararg args: Any): String = Resources.getString(resId, *args)
    fun getColor(resId: ResourceId): RgbaColor = Resources.getColor(resId)
    fun getDimen(resId: ResourceId): Float = Resources.getDimen(resId)
    fun getDrawable(resId: ResourceId): DrawableResource? = Resources.getDrawable(resId)
    fun icon(resId: ResourceId): String = resId.toResourceUri()
}
""".trimIndent() + "\n"

    private fun buildResourceObjectKotlin(
        pkgName: String,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        integers: List<ValueEntry>,
        bools: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>,
        styles: List<RawEntry> = emptyList()
    ): String = buildString {
        appendLine("package $pkgName")
        appendLine()
        appendLine("import com.nexustools.genericui.resources.ResourceType")
        appendLine()
        appendLine("typealias ResourceID = com.nexustools.genericui.resources.ResourceID")
        appendLine()
        appendLine("/**")
        appendLine(" * Unified `Resource` object generated by the GenericUI Gradle plugin (`1 Class Per File`),")
        appendLine(" * exposing typed [ResourceID] constants and cross-platform [com.nexustools.genericui.resources.ResourceResolver] helpers.")
        appendLine(" */")
        appendLine("object Resource {")
        appendLine("    init {")
        appendLine("        GeneratedResourceRegistry.ensureInitialized()")
        appendLine("    }")
        appendLine()
        appendLine("    object drawable {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (d in drawables) {
            appendLine(
                "        val ${d.name}: ResourceID = ResourceID(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object string {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (s in strings) {
            appendLine(
                "        val ${s.name}: ResourceID = ResourceID(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object color {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (c in colors) {
            appendLine(
                "        val ${c.name}: ResourceID = ResourceID(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object dimen {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (dm in dimens) {
            appendLine(
                "        val ${dm.name}: ResourceID = ResourceID(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object integer {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (iv in integers) {
            appendLine(
                "        val ${iv.name}: ResourceID = ResourceID(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object bool {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (bv in bools) {
            appendLine(
                "        val ${bv.name}: ResourceID = ResourceID(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object raw {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (rw in raws) {
            appendLine(
                "        val ${rw.name}: ResourceID = ResourceID(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object font {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (fn in fonts) {
            appendLine(
                "        val ${fn.name}: ResourceID = ResourceID(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object xml {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (xm in xmls) {
            appendLine(
                "        val ${xm.name}: ResourceID = ResourceID(0x${xm.numericId.toString(16)}, ResourceType.XML, ${escapeKotlinString(xm.name)}, ${escapeKotlinString(xm.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    object style {")
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        for (st in styles) {
            appendLine(
                "        val ${st.name}: ResourceID = ResourceID(0x${st.numericId.toString(16)}, ResourceType.STYLE, ${escapeKotlinString(st.name)}, ${escapeKotlinString(st.relativePath)})"
            )
        }
        appendLine("    }")
        appendLine()
        appendLine("    fun getString(id: ResourceID, vararg formatArgs: Any): String =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getString(id, *formatArgs)")
        appendLine()
        appendLine("    fun getColor(id: ResourceID): com.nexustools.genericui.style.color.RgbaColor =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getColor(id)")
        appendLine()
        appendLine("    fun getDimenPx(id: ResourceID): Float =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getDimenPx(id)")
        appendLine()
        appendLine("    fun getInteger(id: ResourceID): Int =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getInteger(id)")
        appendLine()
        appendLine("    fun getBool(id: ResourceID): Boolean =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getBool(id)")
        appendLine()
        appendLine("    fun getUrl(id: ResourceID): String =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getUrl(id)")
        appendLine()
        appendLine("    fun getBytes(id: ResourceID): ByteArray? =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getBytes(id)")
        appendLine()
        appendLine("    fun getImageBuffer(id: ResourceID): com.nexustools.genericui.image.ImageBuffer? =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.getImageBuffer(id)")
        appendLine("}")
    }

    private fun emitModuleResourceManifest(
        outDir: File,
        pkgName: String,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        integers: List<ValueEntry>,
        bools: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>,
        styles: List<RawEntry> = emptyList()
    ) {
        outDir.mkdirs()
        val entries = mutableListOf<String>()
        for (d in drawables) {
            val svgB64 = d.svgSource?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""
            val dataB64 = d.base64Data ?: ""
            entries.add(
                "{\"type\":\"DRAWABLE\",\"name\":${escapeJsonString(d.name)},\"id\":${d.numericId}," +
                    "\"relativePath\":${escapeJsonString(d.relativePath)},\"format\":${escapeJsonString(d.format)}," +
                    "\"viewBoxWidth\":${d.viewBoxWidth},\"viewBoxHeight\":${d.viewBoxHeight}," +
                    "\"svgBase64\":${escapeJsonString(svgB64)},\"base64Data\":${escapeJsonString(dataB64)}}"
            )
        }
        for (s in strings) {
            entries.add(
                "{\"type\":\"STRING\",\"name\":${escapeJsonString(s.name)},\"id\":${s.numericId},\"value\":${escapeJsonString(s.rawValue)}}"
            )
        }
        for (c in colors) {
            entries.add(
                "{\"type\":\"COLOR\",\"name\":${escapeJsonString(c.name)},\"id\":${c.numericId},\"value\":${escapeJsonString(c.rawValue)}}"
            )
        }
        for (dm in dimens) {
            entries.add(
                "{\"type\":\"DIMEN\",\"name\":${escapeJsonString(dm.name)},\"id\":${dm.numericId},\"value\":${escapeJsonString(dm.rawValue)}}"
            )
        }
        for (iv in integers) {
            entries.add(
                "{\"type\":\"INTEGER\",\"name\":${escapeJsonString(iv.name)},\"id\":${iv.numericId},\"value\":${escapeJsonString(iv.rawValue)}}"
            )
        }
        for (bv in bools) {
            entries.add(
                "{\"type\":\"BOOL\",\"name\":${escapeJsonString(bv.name)},\"id\":${bv.numericId},\"value\":${escapeJsonString(bv.rawValue)}}"
            )
        }
        for (rw in raws) {
            val txtB64 = rw.textContent?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""
            val binB64 = rw.base64Content ?: ""
            entries.add(
                "{\"type\":\"RAW\",\"name\":${escapeJsonString(rw.name)},\"id\":${rw.numericId}," +
                    "\"relativePath\":${escapeJsonString(rw.relativePath)},\"textBase64\":${escapeJsonString(txtB64)},\"base64Data\":${escapeJsonString(binB64)}}"
            )
        }
        for (fn in fonts) {
            val b64 = fn.base64Data ?: ""
            entries.add(
                "{\"type\":\"FONT\",\"name\":${escapeJsonString(fn.name)},\"id\":${fn.numericId}," +
                    "\"relativePath\":${escapeJsonString(fn.rawValue)},\"base64Data\":${escapeJsonString(b64)}}"
            )
        }
        for (xm in xmls) {
            val txtB64 = xm.textContent?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""
            entries.add(
                "{\"type\":\"XML\",\"name\":${escapeJsonString(xm.name)},\"id\":${xm.numericId}," +
                    "\"relativePath\":${escapeJsonString(xm.relativePath)},\"textBase64\":${escapeJsonString(txtB64)}}"
            )
        }
        for (st in styles) {
            val txtB64 = st.textContent?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""
            entries.add(
                "{\"type\":\"STYLE\",\"name\":${escapeJsonString(st.name)},\"id\":${st.numericId}," +
                    "\"relativePath\":${escapeJsonString(st.relativePath)},\"textBase64\":${escapeJsonString(txtB64)}}"
            )
        }

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"package\": ${escapeJsonString(pkgName)},")
        sb.appendLine("  \"entries\": [")
        entries.forEachIndexed { idx, line ->
            val comma = if (idx < entries.lastIndex) "," else ""
            sb.appendLine("    $line$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        File(outDir, "genericui-module-resources.json").writeText(sb.toString())
    }

    private fun mergeUpstreamManifest(
        manifestFile: File,
        drawables: MutableMap<String, DrawableEntry>,
        strings: MutableMap<String, ValueEntry>,
        colors: MutableMap<String, ValueEntry>,
        dimens: MutableMap<String, ValueEntry>,
        integers: MutableMap<String, ValueEntry>,
        bools: MutableMap<String, ValueEntry>,
        raws: MutableMap<String, RawEntry>,
        fonts: MutableMap<String, ValueEntry>,
        xmls: MutableMap<String, RawEntry>,
        styles: MutableMap<String, RawEntry> = linkedMapOf(),
        nextDrawableId: () -> Int,
        nextStringId: () -> Int,
        nextColorId: () -> Int,
        nextDimenId: () -> Int,
        nextIntId: () -> Int,
        nextBoolId: () -> Int,
        nextRawId: () -> Int,
        nextFontId: () -> Int,
        nextXmlId: () -> Int,
        nextStyleId: () -> Int = { 0x7f0c0001 }
    ) {
        manifestFile.readLines().forEach { rawLine ->
            val line = rawLine.trim().removeSuffix(",")
            if (!line.startsWith("{\"type\":")) return@forEach
            val type = extractJsonField(line, "type") ?: return@forEach
            val name = extractJsonField(line, "name") ?: return@forEach
            when (type) {
                "DRAWABLE" -> if (name !in drawables) {
                    val relPath = extractJsonField(line, "relativePath") ?: "drawable/$name.svg"
                    val format = extractJsonField(line, "format") ?: "svg"
                    val vbW = extractJsonNumber(line, "viewBoxWidth")?.toFloatOrNull() ?: 24f
                    val vbH = extractJsonNumber(line, "viewBoxHeight")?.toFloatOrNull() ?: 24f
                    val svgB64 = extractJsonField(line, "svgBase64").orEmpty()
                    val svgSource = if (svgB64.isNotEmpty()) {
                        String(Base64.getDecoder().decode(svgB64), Charsets.UTF_8)
                    } else {
                        null
                    }
                    val dataB64 = extractJsonField(line, "base64Data")?.takeIf { it.isNotEmpty() }
                    drawables[name] = DrawableEntry(
                        name = name,
                        numericId = nextDrawableId(),
                        relativePath = relPath,
                        format = format,
                        svgSource = svgSource,
                        svgPaths = svgSource?.let { extractSvgPaths(it) } ?: emptyList(),
                        viewBoxWidth = vbW,
                        viewBoxHeight = vbH,
                        base64Data = dataB64
                    )
                }
                "STRING" -> if (name !in strings) {
                    strings[name] = ValueEntry(name, nextStringId(), extractJsonField(line, "value").orEmpty())
                }
                "COLOR" -> if (name !in colors) {
                    colors[name] = ValueEntry(name, nextColorId(), extractJsonField(line, "value").orEmpty())
                }
                "DIMEN" -> if (name !in dimens) {
                    dimens[name] = ValueEntry(name, nextDimenId(), extractJsonField(line, "value").orEmpty())
                }
                "INTEGER" -> if (name !in integers) {
                    integers[name] = ValueEntry(name, nextIntId(), extractJsonField(line, "value").orEmpty())
                }
                "BOOL" -> if (name !in bools) {
                    bools[name] = ValueEntry(name, nextBoolId(), extractJsonField(line, "value").orEmpty())
                }
                "RAW" -> if (name !in raws) {
                    val relPath = extractJsonField(line, "relativePath") ?: "raw/$name"
                    val txtB64 = extractJsonField(line, "textBase64").orEmpty()
                    val textContent = if (txtB64.isNotEmpty()) {
                        String(Base64.getDecoder().decode(txtB64), Charsets.UTF_8)
                    } else {
                        null
                    }
                    val binB64 = extractJsonField(line, "base64Data")?.takeIf { it.isNotEmpty() }
                    raws[name] = RawEntry(
                        name = name,
                        numericId = nextRawId(),
                        relativePath = relPath,
                        textContent = textContent,
                        base64Content = binB64
                    )
                }
                "FONT" -> if (name !in fonts) {
                    val relPath = extractJsonField(line, "relativePath") ?: "font/$name.ttf"
                    val b64 = extractJsonField(line, "base64Data")?.takeIf { it.isNotEmpty() }
                    fonts[name] = ValueEntry(name, nextFontId(), relPath, b64)
                }
                "XML" -> if (name !in xmls) {
                    val relPath = extractJsonField(line, "relativePath") ?: "xml/$name.xml"
                    val txtB64 = extractJsonField(line, "textBase64").orEmpty()
                    val textContent = if (txtB64.isNotEmpty()) {
                        String(Base64.getDecoder().decode(txtB64), Charsets.UTF_8)
                    } else {
                        ""
                    }
                    xmls[name] = RawEntry(name, nextXmlId(), relPath, textContent, null)
                }
                "STYLE" -> if (name !in styles) {
                    val relPath = extractJsonField(line, "relativePath") ?: "styles/$name.scss"
                    val txtB64 = extractJsonField(line, "textBase64").orEmpty()
                    val textContent = if (txtB64.isNotEmpty()) {
                        String(Base64.getDecoder().decode(txtB64), Charsets.UTF_8)
                    } else {
                        ""
                    }
                    styles[name] = RawEntry(name, nextStyleId(), relPath, textContent, null)
                }
            }
        }
    }

    private fun emitWindowsRcTranslations(
        projDir: File,
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>,
        styles: List<RawEntry> = emptyList(),
        cursors: Map<String, File> = emptyMap()
    ) {
        outDir.mkdirs()
        val assetsRoot = File(outDir, "assets").apply { mkdirs() }
        val cursorsDir = File(outDir, "cursors").apply { mkdirs() }
        val icoFile = GenericUiDefaultIconProvider.ensureDefaultIcoIcon(projDir, outDir)
        val headerSb = StringBuilder()
        headerSb.appendLine("// Auto-generated Win32 Resource Header by GenericUI")
        headerSb.appendLine("#pragma once")
        headerSb.appendLine("#define IDI_APPICON 1")
        for (d in drawables) {
            headerSb.appendLine("#define IDR_DRAWABLE_${d.name.uppercase()} ${d.win32ResourceId}")
        }
        for (s in strings) {
            headerSb.appendLine("#define IDS_STRING_${s.name.uppercase()} ${s.win32ResourceId(0x02)}")
        }
        for (r in raws) {
            headerSb.appendLine("#define IDR_RAW_${r.name.uppercase()} ${r.win32ResourceId(0x07)}")
        }
        for (f in fonts) {
            headerSb.appendLine("#define IDR_FONT_${f.name.uppercase()} ${f.win32ResourceId(0x08)}")
        }
        for (x in xmls) {
            headerSb.appendLine("#define IDR_XML_${x.name.uppercase()} ${x.win32ResourceId(0x09)}")
        }
        for (st in styles) {
            headerSb.appendLine("#define IDR_STYLE_${st.name.uppercase()} ${st.win32ResourceId(0x0C)}")
        }
        var nextCursorResId = 13001
        val cursorIds = linkedMapOf<String, Int>()
        for (cursorName in cursors.keys) {
            val cid = nextCursorResId++
            cursorIds[cursorName] = cid
            headerSb.appendLine("#define IDC_CURSOR_${cursorName.uppercase()} $cid")
        }
        File(outDir, "genericui_resource_ids.h").writeText(headerSb.toString())

        val escapedIcoPath = icoFile.invariantSeparatorsPath
        val rcSb = StringBuilder()
        rcSb.appendLine("// Auto-generated Win32 Resource Script (.rc) by GenericUI")
        rcSb.appendLine("#include \"genericui_resource_ids.h\"")
        rcSb.appendLine("1 ICON \"$escapedIcoPath\"")
        rcSb.appendLine("IDI_APPICON ICON \"$escapedIcoPath\"")
        rcSb.appendLine()

        // Generate Windows .cur files for explicit cursors and for drawables so custom cursors work natively
        val generatedCurFiles = linkedMapOf<String, File>()
        for ((cursorName, srcFile) in cursors) {
            val curOut = File(cursorsDir, "$cursorName.cur")
            WindowsCurResourceGenerator.generateCurFile(srcFile, curOut)
            generatedCurFiles[cursorName] = curOut
            rcSb.appendLine("IDC_CURSOR_${cursorName.uppercase()} CURSOR \"${curOut.invariantSeparatorsPath}\"")
        }

        val drawableAssetDir = File(assetsRoot, "drawable").apply { mkdirs() }
        for (d in drawables) {
            val fileName = d.relativePath.substringAfterLast('/').ifEmpty { "${d.name}.${d.format}" }
            val assetFile = File(drawableAssetDir, fileName)
            writeDrawablePayload(d, assetFile)
            rcSb.appendLine("IDR_DRAWABLE_${d.name.uppercase()} RCDATA \"${assetFile.invariantSeparatorsPath}\"")
            if (d.name !in generatedCurFiles && d.format in setOf("svg", "png", "cur")) {
                val curOut = File(cursorsDir, "${d.name}.cur")
                WindowsCurResourceGenerator.generateCurFile(assetFile, curOut)
                generatedCurFiles[d.name] = curOut
            }
        }

        val rawAssetDir = File(assetsRoot, "raw").apply { mkdirs() }
        for (r in raws) {
            val fileName = r.relativePath.substringAfterLast('/').ifEmpty { "${r.name}.bin" }
            val assetFile = File(rawAssetDir, fileName)
            writeRawPayload(r, assetFile)
            rcSb.appendLine("IDR_RAW_${r.name.uppercase()} RCDATA \"${assetFile.invariantSeparatorsPath}\"")
        }

        val fontAssetDir = File(assetsRoot, "font").apply { mkdirs() }
        for (f in fonts) {
            val fileName = f.rawValue.substringAfterLast('/').ifEmpty { "${f.name}.ttf" }
            val assetFile = File(fontAssetDir, fileName)
            if (f.base64Data != null) {
                assetFile.writeBytes(Base64.getDecoder().decode(f.base64Data))
            } else {
                assetFile.writeBytes(ByteArray(0))
            }
            rcSb.appendLine("IDR_FONT_${f.name.uppercase()} RCDATA \"${assetFile.invariantSeparatorsPath}\"")
        }

        val xmlAssetDir = File(assetsRoot, "xml").apply { mkdirs() }
        for (x in xmls) {
            val fileName = x.relativePath.substringAfterLast('/').ifEmpty { "${x.name}.xml" }
            val assetFile = File(xmlAssetDir, fileName)
            writeRawPayload(x, assetFile)
            rcSb.appendLine("IDR_XML_${x.name.uppercase()} RCDATA \"${assetFile.invariantSeparatorsPath}\"")
        }

        val styleAssetDir = File(assetsRoot, "style").apply { mkdirs() }
        for (st in styles) {
            val fileName = st.relativePath.substringAfterLast('/').ifEmpty { "${st.name}.scss" }
            val assetFile = File(styleAssetDir, fileName)
            writeRawPayload(st, assetFile)
            rcSb.appendLine("IDR_STYLE_${st.name.uppercase()} RCDATA \"${assetFile.invariantSeparatorsPath}\"")
        }

        if (strings.isNotEmpty()) {
            rcSb.appendLine()
            rcSb.appendLine("STRINGTABLE")
            rcSb.appendLine("BEGIN")
            for (s in strings) {
                val escaped = s.rawValue.replace("\"", "\"\"").replace("\n", "\\n")
                rcSb.appendLine("    IDS_STRING_${s.name.uppercase()} \"$escaped\"")
            }
            rcSb.appendLine("END")
        }
        File(outDir, "genericui_resources.rc").writeText(rcSb.toString())
    }

    private fun emitAndroidResourceTranslations(
        projDir: File,
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>
    ) {
        val valuesDir = File(outDir, "values").apply { mkdirs() }
        val drawableDir = File(outDir, "drawable").apply { mkdirs() }
        GenericUiDefaultIconProvider.ensureDefaultPngIcon(projDir, drawableDir)

        if (strings.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            for (s in strings) {
                sb.appendLine("    <string name=\"${s.name}\">${escapeXml(s.rawValue)}</string>")
            }
            sb.appendLine("</resources>")
            File(valuesDir, "strings.xml").writeText(sb.toString())
        }

        if (colors.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            for (c in colors) {
                sb.appendLine("    <color name=\"${c.name}\">${escapeXml(c.rawValue)}</color>")
            }
            sb.appendLine("</resources>")
            File(valuesDir, "colors.xml").writeText(sb.toString())
        }

        if (dimens.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            for (d in dimens) {
                sb.appendLine("    <dimen name=\"${d.name}\">${escapeXml(d.rawValue)}</dimen>")
            }
            sb.appendLine("</resources>")
            File(valuesDir, "dimens.xml").writeText(sb.toString())
        }

        for (d in drawables) {
            if (d.format == "svg" && d.svgPaths.isNotEmpty()) {
                val vd = StringBuilder()
                vd.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                vd.appendLine("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                vd.appendLine("    android:width=\"${d.viewBoxWidth.toInt()}dp\"")
                vd.appendLine("    android:height=\"${d.viewBoxHeight.toInt()}dp\"")
                vd.appendLine("    android:viewportWidth=\"${d.viewBoxWidth}\"")
                vd.appendLine("    android:viewportHeight=\"${d.viewBoxHeight}\">")
                for (pathData in d.svgPaths) {
                    vd.appendLine("    <path")
                    vd.appendLine("        android:fillColor=\"#FF000000\"")
                    vd.appendLine("        android:strokeColor=\"#FF000000\"")
                    vd.appendLine("        android:strokeWidth=\"1.8\"")
                    vd.appendLine("        android:pathData=\"${escapeXml(pathData)}\" />")
                }
                vd.appendLine("</vector>")
                File(drawableDir, "${d.name}.xml").writeText(vd.toString())
            }
        }
    }

    private fun emitAppleResourceTranslations(
        projDir: File,
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>
    ) {
        val lproj = File(outDir, "en.lproj").apply { mkdirs() }
        val xcassets = File(outDir, "Assets.xcassets").apply { mkdirs() }
        val appIconSet = File(xcassets, "AppIcon.appiconset").apply { mkdirs() }
        GenericUiDefaultIconProvider.ensureDefaultPngIcon(projDir, appIconSet)
        File(appIconSet, "Contents.json").writeText(
            """
            {
              "images": [{ "idiom": "universal", "filename": "icon.png", "size": "512x512" }],
              "info": { "version": 1, "author": "genericui" }
            }
            """.trimIndent()
        )

        val ls = StringBuilder("// Auto-generated Apple Localizable.strings by GenericUI\n")
        for (s in strings) {
            val esc = s.rawValue.replace("\"", "\\\"").replace("\n", "\\n")
            ls.appendLine("\"${s.name}\" = \"$esc\";")
        }
        File(lproj, "Localizable.strings").writeText(ls.toString())

        for (d in drawables) {
            val imgSet = File(xcassets, "${d.name}.imageset").apply { mkdirs() }
            if (d.svgSource != null) {
                File(imgSet, "${d.name}.svg").writeText(d.svgSource)
            }
            if (d.name == "icon") {
                GenericUiDefaultIconProvider.ensureDefaultPngIcon(projDir, imgSet)
            }
            File(imgSet, "Contents.json").writeText(
                """
                {
                  "images": [{ "idiom": "universal", "filename": "${d.name}.${d.format}" }],
                  "info": { "version": 1, "author": "genericui" }
                }
                """.trimIndent()
            )
        }
        for (c in colors) {
            val colSet = File(xcassets, "${c.name}.colorset").apply { mkdirs() }
            File(colSet, "Contents.json").writeText(
                """
                {
                  "colors": [{ "idiom": "universal", "color": { "color-space": "srgb", "hex": "${c.rawValue}" } }],
                  "info": { "version": 1, "author": "genericui" }
                }
                """.trimIndent()
            )
            if (c.name == "theme_color") {
                val accentSet = File(xcassets, "AccentColor.colorset").apply { mkdirs() }
                File(accentSet, "Contents.json").writeText(
                    """
                    {
                      "colors": [{ "idiom": "universal", "color": { "color-space": "srgb", "hex": "${c.rawValue}" } }],
                      "info": { "version": 1, "author": "genericui" }
                    }
                    """.trimIndent()
                )
            }
        }
    }

    private fun emitLinuxGtkAndQtTranslations(
        projDir: File,
        outDir: File,
        pkgName: String,
        drawables: List<DrawableEntry>,
        raws: List<RawEntry>
    ) {
        outDir.mkdirs()
        val drawableDir = File(outDir, "drawable").apply { mkdirs() }
        GenericUiDefaultIconProvider.ensureDefaultPngIcon(projDir, drawableDir)
        GenericUiDefaultIconProvider.ensureDefaultSvgIcon(projDir, drawableDir)
        val prefixPath = "/" + pkgName.replace('.', '/')
        val gres = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gresources>\n  <gresource prefix=\"$prefixPath\">\n")
        val qrc = StringBuilder("<!DOCTYPE RCC>\n<RCC version=\"1.0\">\n  <qresource prefix=\"/\">\n")
        gres.appendLine("    <file>drawable/icon.png</file>")
        qrc.appendLine("    <file alias=\"drawable/icon.png\">drawable/icon.png</file>")
        for (d in drawables) {
            gres.appendLine("    <file>${d.relativePath}</file>")
            qrc.appendLine("    <file alias=\"drawable/${d.name}.${d.format}\">${d.relativePath}</file>")
        }
        for (r in raws) {
            gres.appendLine("    <file>${r.relativePath}</file>")
            qrc.appendLine("    <file alias=\"raw/${r.name}\">${r.relativePath}</file>")
        }
        gres.appendLine("  </gresource>\n</gresources>")
        qrc.appendLine("  </qresource>\n</RCC>")
        File(outDir, "genericui.gresource.xml").writeText(gres.toString())
        File(outDir, "genericui_resources.qrc").writeText(qrc.toString())
    }

    private fun emitWebAndSwingResourceTranslations(
        projDir: File,
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>
    ) {
        outDir.mkdirs()
        GenericUiDefaultIconProvider.ensureDefaultPngIcon(projDir, outDir)
        GenericUiDefaultIconProvider.ensureDefaultIcoIcon(projDir, outDir)
        GenericUiDefaultIconProvider.ensureDefaultSvgIcon(projDir, outDir)

        // Stage Web assets under web/assets/<type>/<fileName>
        val assetsRoot = File(outDir, "assets").apply { mkdirs() }
        val drawableAssetsDir = File(assetsRoot, "drawable").apply { mkdirs() }
        for (d in drawables) {
            val fileName = d.relativePath.substringAfterLast('/').ifEmpty { "${d.name}.${d.format}" }
            writeDrawablePayload(d, File(drawableAssetsDir, fileName))
        }
        val rawAssetsDir = File(assetsRoot, "raw").apply { mkdirs() }
        for (r in raws) {
            val fileName = r.relativePath.substringAfterLast('/').ifEmpty { "${r.name}.bin" }
            writeRawPayload(r, File(rawAssetsDir, fileName))
        }
        val fontAssetsDir = File(assetsRoot, "font").apply { mkdirs() }
        for (f in fonts) {
            val fileName = f.rawValue.substringAfterLast('/').ifEmpty { "${f.name}.ttf" }
            val target = File(fontAssetsDir, fileName)
            if (f.base64Data != null) {
                target.writeBytes(Base64.getDecoder().decode(f.base64Data))
            }
        }
        val xmlAssetsDir = File(assetsRoot, "xml").apply { mkdirs() }
        for (x in xmls) {
            val fileName = x.relativePath.substringAfterLast('/').ifEmpty { "${x.name}.xml" }
            writeRawPayload(x, File(xmlAssetsDir, fileName))
        }

        val css = StringBuilder(":root {\n")
        for (c in colors) {
            if (c.name == "theme_color") {
                css.appendLine("  --genericui-theme-color: ${c.rawValue};")
            }
            css.appendLine("  --rc-color-${c.name.replace('_', '-')}: ${c.rawValue};")
        }
        for (d in dimens) {
            css.appendLine("  --rc-dimen-${d.name.replace('_', '-')}: ${d.rawValue};")
        }
        css.appendLine("}")
        File(outDir, "genericui-resource-vars.css").writeText(css.toString())

        val json = StringBuilder("{\n  \"drawables\": {\n")
        drawables.forEachIndexed { idx, d ->
            val comma = if (idx < drawables.lastIndex) "," else ""
            val fileName = d.relativePath.substringAfterLast('/').ifEmpty { "${d.name}.${d.format}" }
            json.appendLine("    \"${d.name}\": { \"id\": ${d.numericId}, \"format\": \"${d.format}\", \"path\": \"assets/drawable/$fileName\" }$comma")
        }
        json.appendLine("  },\n  \"strings\": {")
        strings.forEachIndexed { idx, s ->
            val comma = if (idx < strings.lastIndex) "," else ""
            val esc = s.rawValue.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            json.appendLine("    \"${s.name}\": \"$esc\"$comma")
        }
        json.appendLine("  }\n}")
        File(outDir, "genericui-resources.json").writeText(json.toString())
    }

    private fun writeDrawablePayload(d: DrawableEntry, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        when {
            d.svgSource != null -> targetFile.writeText(d.svgSource)
            d.base64Data != null -> targetFile.writeBytes(Base64.getDecoder().decode(d.base64Data))
            else -> targetFile.writeBytes(ByteArray(0))
        }
    }

    private fun writeRawPayload(r: RawEntry, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        when {
            r.textContent != null -> targetFile.writeText(r.textContent)
            r.base64Content != null -> targetFile.writeBytes(Base64.getDecoder().decode(r.base64Content))
            else -> targetFile.writeBytes(ByteArray(0))
        }
    }

    private fun parseAndroidValuesXml(
        xmlText: String,
        onString: (String, String) -> Unit,
        onColor: (String, String) -> Unit,
        onDimen: (String, String) -> Unit,
        onInteger: (String, String) -> Unit,
        onBool: (String, String) -> Unit
    ) {
        val tagRegex = Regex("<(string|color|dimen|integer|bool)\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>([\\s\\S]*?)</\\1>")
        for (match in tagRegex.findAll(xmlText)) {
            val tag = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val value = unescapeXml(match.groupValues[3].trim())
            when (tag) {
                "string" -> onString(name, value)
                "color" -> onColor(name, value)
                "dimen" -> onDimen(name, value)
                "integer" -> onInteger(name, value)
                "bool" -> onBool(name, value)
            }
        }
    }

    private fun extractSvgViewBox(svg: String): Pair<Float, Float> {
        val vbMatch = Regex("viewBox\\s*=\\s*\"([^\"]+)\"").find(svg)
        if (vbMatch != null) {
            val nums = vbMatch.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (nums.size >= 4) {
                return nums[2].coerceAtLeast(1f) to nums[3].coerceAtLeast(1f)
            }
        }
        return 24f to 24f
    }

    private fun extractSvgPaths(svg: String): List<String> {
        val result = mutableListOf<String>()
        for (m in Regex("<path[^>]*\\bd\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(svg)) {
            val d = m.groupValues[1].trim()
            if (d.isNotEmpty()) result.add(d)
        }
        for (m in Regex("<rect[^>]*>").findAll(svg)) {
            val tag = m.value
            val x = attrFloat(tag, "x", 3f)
            val y = attrFloat(tag, "y", 3f)
            val w = attrFloat(tag, "width", 18f)
            val h = attrFloat(tag, "height", 18f)
            result.add("M $x $y L ${x + w} $y L ${x + w} ${y + h} L $x ${y + h} Z")
        }
        for (m in Regex("<line[^>]*>").findAll(svg)) {
            val tag = m.value
            val x1 = attrFloat(tag, "x1", 0f)
            val y1 = attrFloat(tag, "y1", 0f)
            val x2 = attrFloat(tag, "x2", 24f)
            val y2 = attrFloat(tag, "y2", 24f)
            result.add("M $x1 $y1 L $x2 $y2")
        }
        for (m in Regex("<polyline[^>]*\\bpoints\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(svg)) {
            val pts = m.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (pts.size >= 4) {
                val sb = StringBuilder("M ${pts[0]} ${pts[1]}")
                var i = 2
                while (i + 1 < pts.size) {
                    sb.append(" L ${pts[i]} ${pts[i + 1]}")
                    i += 2
                }
                result.add(sb.toString())
            }
        }
        for (m in Regex("<polygon[^>]*\\bpoints\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(svg)) {
            val pts = m.groupValues[1].trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            if (pts.size >= 4) {
                val sb = StringBuilder("M ${pts[0]} ${pts[1]}")
                var i = 2
                while (i + 1 < pts.size) {
                    sb.append(" L ${pts[i]} ${pts[i + 1]}")
                    i += 2
                }
                sb.append(" Z")
                result.add(sb.toString())
            }
        }
        for (m in Regex("<circle[^>]*>").findAll(svg)) {
            val tag = m.value
            val cx = attrFloat(tag, "cx", 12f)
            val cy = attrFloat(tag, "cy", 12f)
            val r = attrFloat(tag, "r", 6f)
            result.add("M ${cx - r} $cy Q ${cx - r} ${cy - r} $cx ${cy - r} Q ${cx + r} ${cy - r} ${cx + r} $cy Q ${cx + r} ${cy + r} $cx ${cy + r} Q ${cx - r} ${cy + r} ${cx - r} $cy Z")
        }
        for (m in Regex("<ellipse[^>]*>").findAll(svg)) {
            val tag = m.value
            val cx = attrFloat(tag, "cx", 12f)
            val cy = attrFloat(tag, "cy", 12f)
            val rx = attrFloat(tag, "rx", 8f)
            val ry = attrFloat(tag, "ry", 6f)
            result.add("M ${cx - rx} $cy Q ${cx - rx} ${cy - ry} $cx ${cy - ry} Q ${cx + rx} ${cy - ry} ${cx + rx} $cy Q ${cx + rx} ${cy + ry} $cx ${cy + ry} Q ${cx - rx} ${cy + ry} ${cx - rx} $cy Z")
        }
        return result
    }

    private fun attrFloat(tag: String, attrName: String, defaultVal: Float): Float {
        val m = Regex("\\b$attrName\\s*=\\s*\"([^\"]+)\"").find(tag) ?: return defaultVal
        return m.groupValues[1].replace("px", "").trim().toFloatOrNull() ?: defaultVal
    }

    private fun sanitizeIdentifier(raw: String): String {
        val cleaned = raw.trim()
            .replace('-', '_')
            .replace('.', '_')
            .replace(' ', '_')
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .lowercase()
        if (cleaned.isEmpty()) return ""
        return if (cleaned[0].isDigit()) "_$cleaned" else cleaned
    }

    private fun escapeKotlinString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun escapeJsonString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun extractJsonField(line: String, key: String): String? {
        val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(line) ?: return null
        return m.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun extractJsonNumber(line: String, key: String): String? {
        val m = Regex("\"${Regex.escape(key)}\"\\s*:\\s*([-0-9.]+)").find(line) ?: return null
        return m.groupValues[1]
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun unescapeXml(value: String): String =
        value.replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    companion object {
        internal fun win32IdFor(typeCode: Int, numericId: Int): Int =
            ((typeCode and 0x0F) * 1000) + (numericId and 0x0FFF)
    }
}
