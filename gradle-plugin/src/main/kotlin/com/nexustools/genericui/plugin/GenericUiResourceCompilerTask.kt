package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Base64
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

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
 *    - Linux GTK4 & KDE ELF `.rodata` tables (`linux/genericui_app_resources.c`)
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

    @get:Classpath
    @get:Optional
    abstract val upstreamResourceClasspath: ConfigurableFileCollection

    @get:Input
    abstract val translateForAllBackends: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val themeColor: Property<String>

    @get:Input
    @get:Optional
    abstract val themeMode: Property<String>

    @get:Input
    @get:Optional
    abstract val lightThemeColors: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val darkThemeColors: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val appName: Property<String>

    @get:Input
    @get:Optional
    abstract val configuredIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val appleIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val macIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val trayIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val macosTrayIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val iosIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val ipadosIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val macIconExplicitlyProvided: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val iosIconExplicitlyProvided: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val ipadosIconExplicitlyProvided: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val androidLauncherIconConfig: Property<String>

    @get:Input
    @get:Optional
    abstract val androidSvgConverterVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val failOnSvgConversionWarnings: Property<Boolean>

    @get:Classpath
    @get:Optional
    abstract val svgConverterClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val gradleUserHomePath: Property<String>

    @get:Input
    @get:Optional
    abstract val offlineMode: Property<Boolean>

    @get:OutputDirectory
    abstract val kotlinOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val backendOutputDirectory: DirectoryProperty

    init {
        androidSvgConverterVersion.convention(GenericUiAndroidSvgConverter.DEFAULT_SDK_COMMON_VERSION)
        failOnSvgConversionWarnings.convention(false)
        gradleUserHomePath.convention(project.gradle.gradleUserHomeDir.absolutePath)
        offlineMode.convention(project.gradle.startParameter.isOffline)
        macIconExplicitlyProvided.convention(false)
        iosIconExplicitlyProvided.convention(false)
        ipadosIconExplicitlyProvided.convention(false)
    }

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

        val nativeResourceKey: String
            get() {
                val stripped = GenericUiModuleResourceManifestParser.stripScopePrefix(relativePath)
                return GenericUiModuleResourceManifestParser.sanitizeFlatIdentifier(
                    stripped.ifEmpty { "drawable/$name.$format" }
                )
            }

        val flatName: String
            get() = GenericUiModuleResourceManifestParser.sanitizeFlatIdentifier(
                canonicalStemOf(relativePath, "drawable", name)
            )
    }

    private data class AndroidDrawableVariant(
        val name: String,
        val flatName: String,
        val resourceKind: String,
        val qualifierSuffix: String,
        val format: String,
        val svgSource: String?,
        val viewBoxWidth: Float,
        val viewBoxHeight: Float,
        val base64Data: String?
    )

    private data class ValueEntry(
        val name: String,
        val numericId: Int,
        val rawValue: String,
        val base64Data: String? = null,
        val scopePrefix: String = "app",
        val categoryDir: String = ""
    ) {
        fun win32ResourceId(typeCode: Int): Int = win32IdFor(typeCode, numericId)

        val nativeResourceKey: String
            get() {
                val stripped = GenericUiModuleResourceManifestParser.stripScopePrefix(rawValue)
                return GenericUiModuleResourceManifestParser.sanitizeFlatIdentifier(
                    stripped.ifEmpty { "${categoryDir.ifEmpty { "value" }}/$name" }
                )
            }

        val flatName: String
            get() {
                val isPath = rawValue.contains('/') || rawValue.contains('.')
                val base = if (isPath && categoryDir == "font") {
                    canonicalStemOf(rawValue, "font", name, scopePrefix)
                } else {
                    "${scopePrefix}/${categoryDir.ifEmpty { "value" }}/$name"
                }
                return GenericUiModuleResourceManifestParser.sanitizeFlatIdentifier(base)
            }
    }

    private data class RawEntry(
        val name: String,
        val numericId: Int,
        val relativePath: String,
        val textContent: String?,
        val base64Content: String?,
        val categoryDir: String = "raw"
    ) {
        fun win32ResourceId(typeCode: Int = 0x07): Int = win32IdFor(typeCode, numericId)

        val nativeResourceKey: String
            get() {
                val stripped = GenericUiModuleResourceManifestParser.stripScopePrefix(relativePath)
                return GenericUiModuleResourceManifestParser.sanitizeFlatIdentifier(
                    stripped.ifEmpty { "$categoryDir/$name" }
                )
            }

        val flatName: String
            get() = GenericUiModuleResourceManifestParser.sanitizeFlatIdentifier(
                canonicalStemOf(relativePath, categoryDir, name)
            )
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
        val androidVariants = linkedMapOf<String, AndroidDrawableVariant>()
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

        val drawableExts = setOf("svg", "png", "ico", "webp", "jpg", "jpeg", "gif", "bmp")

        fun buildDrawableEntry(
            entryName: String,
            file: File,
            relPath: String,
            ext: String,
            defaultViewBox: Float = 24f
        ): DrawableEntry {
            val canonicalRel = canonicalAppPath(relPath)
            val numId = (0x7f01 shl 16) or (nextDrawableIdx++ and 0xFFFF)
            return if (ext == "svg") {
                val svgText = file.readText()
                val (vbPair, pathList) = GenericUiBuiltinSvgToVectorConverter.extractViewBoxAndPaths(svgText)
                val (vbW, vbH) = vbPair
                DrawableEntry(
                    name = entryName,
                    numericId = numId,
                    relativePath = canonicalRel,
                    format = "svg",
                    svgSource = svgText,
                    svgPaths = pathList,
                    viewBoxWidth = vbW,
                    viewBoxHeight = vbH,
                    base64Data = null
                )
            } else {
                val b64 = Base64.getEncoder().encodeToString(file.readBytes())
                DrawableEntry(
                    name = entryName,
                    numericId = numId,
                    relativePath = canonicalRel,
                    format = ext,
                    svgSource = null,
                    svgPaths = emptyList(),
                    viewBoxWidth = defaultViewBox,
                    viewBoxHeight = defaultViewBox,
                    base64Data = b64
                )
            }
        }

        fun recordAndroidVariant(entry: DrawableEntry, parentDirLower: String) {
            val (kind, qualifier) = extractAndroidKindAndQualifier(parentDirLower)
            val resolvedFlatName = drawables[entry.name]?.flatName ?: entry.flatName
            val key = "$kind$qualifier/$resolvedFlatName"
            if (key !in androidVariants) {
                androidVariants[key] = AndroidDrawableVariant(
                    name = entry.name,
                    flatName = resolvedFlatName,
                    resourceKind = kind,
                    qualifierSuffix = qualifier,
                    format = entry.format,
                    svgSource = entry.svgSource,
                    viewBoxWidth = entry.viewBoxWidth,
                    viewBoxHeight = entry.viewBoxHeight,
                    base64Data = entry.base64Data
                )
            }
        }

        fun isExcludedDrawableFolderSegment(segment: String): Boolean {
            val lower = segment.lowercase()
            return lower.startsWith("values") ||
                lower.startsWith("layout") ||
                lower.startsWith("font") ||
                lower.startsWith("raw") ||
                lower == "scripts" ||
                lower.startsWith("xml") ||
                lower.startsWith("menu") ||
                lower.startsWith("styles") ||
                lower.startsWith("style") ||
                lower.startsWith("css") ||
                lower.startsWith("scss") ||
                lower.startsWith("sass") ||
                lower.startsWith("samples") ||
                lower.startsWith("cursors") ||
                lower.startsWith("cursor")
        }

        val subdirUnprefixedCandidates = linkedMapOf<String, MutableList<Pair<String, () -> DrawableEntry>>>()

        for (root in existingDirs) {
            root.walkTopDown().filter { it.isFile }
                .sortedWith(
                    compareBy<File> { it.relativeTo(root).invariantSeparatorsPath.count { ch -> ch == '/' } }
                        .thenBy { it.parentFile?.name?.contains('-') == true }
                        .thenBy { it.relativeTo(root).invariantSeparatorsPath }
                )
                .forEach { file ->
                val relPath = file.relativeTo(root).invariantSeparatorsPath
                val parentDir = file.parentFile?.name?.lowercase() ?: ""
                val ext = file.extension.lowercase()
                val ident = sanitizeIdentifier(file.nameWithoutExtension)
                val pathSegments = relPath.split('/').dropLast(1)

                when {
                    parentDir == "cursors" || parentDir == "cursor" || ext == "cur" -> {
                        if (ident.isNotEmpty() && (ext == "cur" || ext in drawableExts)) {
                            cursors.putIfAbsent(ident, file)
                            val entry = buildDrawableEntry(ident, file, relPath, ext, defaultViewBox = 32f)
                            if (ident !in drawables) {
                                drawables[ident] = entry
                            }
                            recordAndroidVariant(entry, parentDir)
                        }
                    }
                    parentDir.startsWith("drawable") || parentDir.startsWith("mipmap") ||
                        parentDir == "icons" || parentDir == "images" ||
                        (file.parentFile == root && ext in drawableExts) -> {
                        if (ident.isNotEmpty() && ext in drawableExts) {
                            if (ident.contains("cursor")) {
                                cursors.putIfAbsent(ident, file)
                            }
                            val entry = buildDrawableEntry(ident, file, relPath, ext, defaultViewBox = 24f)
                            if (ident !in drawables) {
                                drawables[ident] = entry
                            }
                            recordAndroidVariant(entry, parentDir)
                        }
                    }
                    (parentDir in setOf("styles", "style", "css", "scss", "sass") ||
                        relPath.substringBefore('/', "").lowercase() in setOf("styles", "style", "css", "scss", "sass") ||
                        file.parentFile == root) &&
                        ext in setOf("scss", "sass", "css") && !file.name.startsWith("_") -> {
                        if (ident.isNotEmpty() && ident !in styles) {
                            val numId = (0x7f0c shl 16) or (nextStyleIdx++ and 0xFFFF)
                            styles[ident] = RawEntry(
                                name = ident,
                                numericId = numId,
                                relativePath = canonicalAppPath(relPath),
                                textContent = file.readText(),
                                base64Content = null,
                                categoryDir = "style"
                            )
                        }
                    }
                    parentDir.startsWith("values") && ext == "xml" -> {
                        parseAndroidValuesXml(
                            xmlText = file.readText(),
                            onString = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in strings) {
                                    strings[id] = ValueEntry(id, (0x7f02 shl 16) or (nextStringIdx++ and 0xFFFF), v, categoryDir = "string")
                                }
                            },
                            onColor = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in colors) {
                                    colors[id] = ValueEntry(id, (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF), v, categoryDir = "color")
                                }
                            },
                            onDimen = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in dimens) {
                                    dimens[id] = ValueEntry(id, (0x7f04 shl 16) or (nextDimenIdx++ and 0xFFFF), v, categoryDir = "dimen")
                                }
                            },
                            onInteger = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in integers) {
                                    integers[id] = ValueEntry(id, (0x7f05 shl 16) or (nextIntIdx++ and 0xFFFF), v, categoryDir = "integer")
                                }
                            },
                            onBool = { k, v ->
                                val id = sanitizeIdentifier(k)
                                if (id.isNotEmpty() && id !in bools) {
                                    bools[id] = ValueEntry(id, (0x7f06 shl 16) or (nextBoolIdx++ and 0xFFFF), v, categoryDir = "bool")
                                }
                            }
                        )
                    }
                    parentDir == "raw" || parentDir == "scripts" -> {
                        val effectiveIdent = if (ident !in raws) ident else "${ident}_${sanitizeIdentifier(ext)}"
                        if (ident.isNotEmpty() && effectiveIdent.isNotEmpty() && effectiveIdent !in raws) {
                            val numId = (0x7f07 shl 16) or (nextRawIdx++ and 0xFFFF)
                            val isText = ext in setOf("txt", "json", "js", "ts", "glsl", "wgsl", "xml", "csv", "md")
                            raws[effectiveIdent] = RawEntry(
                                name = effectiveIdent,
                                numericId = numId,
                                relativePath = canonicalAppPath(relPath),
                                textContent = if (isText) file.readText() else null,
                                base64Content = if (!isText) Base64.getEncoder().encodeToString(file.readBytes()) else null,
                                categoryDir = "raw"
                            )
                        }
                    }
                    parentDir == "font" || parentDir == "fonts" -> {
                        val effectiveIdent = if (ident !in fonts) ident else "${ident}_${sanitizeIdentifier(ext)}"
                        if (ident.isNotEmpty() && effectiveIdent.isNotEmpty() && effectiveIdent !in fonts && ext in setOf("ttf", "otf", "woff", "woff2")) {
                            val numId = (0x7f08 shl 16) or (nextFontIdx++ and 0xFFFF)
                            val b64 = Base64.getEncoder().encodeToString(file.readBytes())
                            fonts[effectiveIdent] = ValueEntry(effectiveIdent, numId, canonicalAppPath(relPath), b64, categoryDir = "font")
                        }
                    }
                    parentDir.startsWith("xml") && ext == "xml" -> {
                        if (ident.isNotEmpty() && ident !in xmls) {
                            val numId = (0x7f09 shl 16) or (nextXmlIdx++ and 0xFFFF)
                            xmls[ident] = RawEntry(
                                name = ident,
                                numericId = numId,
                                relativePath = canonicalAppPath(relPath),
                                textContent = file.readText(),
                                base64Content = null,
                                categoryDir = "xml"
                            )
                        }
                    }
                    ext in drawableExts && pathSegments.none { isExcludedDrawableFolderSegment(it) } -> {
                        val relPathWithoutExt = relPath.substringBeforeLast('.', relPath)
                        val subdirIdent = sanitizeIdentifier(relPathWithoutExt.replace('/', '_'))
                        if (subdirIdent.isNotEmpty() && subdirIdent !in drawables) {
                            val entry = buildDrawableEntry(subdirIdent, file, relPath, ext, defaultViewBox = 24f)
                            drawables[subdirIdent] = entry
                            recordAndroidVariant(entry, parentDir)
                        }
                        if (ident.isNotEmpty() && ident != subdirIdent) {
                            subdirUnprefixedCandidates.getOrPut(ident) { mutableListOf() }.add(
                                subdirIdent to { buildDrawableEntry(ident, file, relPath, ext, defaultViewBox = 24f) }
                            )
                        }
                    }
                }
            }
        }

        for ((ident, candidates) in subdirUnprefixedCandidates) {
            if (ident !in drawables) {
                val uniqueBySubdir = candidates.distinctBy { it.first }
                if (uniqueBySubdir.size == 1) {
                    val entry = uniqueBySubdir[0].second()
                    drawables[ident] = entry
                    recordAndroidVariant(entry, "drawable")
                }
            }
        }

        val pkgDir = File(ktOutRoot, pkgName.replace('.', '/'))
        pkgDir.mkdirs()

        // Ensure R.drawable.icon resolves to the project's favicon/icon or the default GenericUI icon.svg
        if ("icon" !in drawables) {
            val faviconDrawable = drawables["favicon"]
            val numId = (0x7f01 shl 16) or (nextDrawableIdx++ and 0xFFFF)
            if (faviconDrawable != null) {
                drawables["icon"] = faviconDrawable.copy(
                    name = "icon",
                    numericId = numId
                )
            } else {
                val defaultSvg = GenericUiDefaultIconProvider.loadDefaultIconSvgText(projDir)
                val (vbW, vbH) = extractSvgViewBox(defaultSvg)
                val pathList = extractSvgPaths(defaultSvg)
                drawables["icon"] = DrawableEntry(
                    name = "icon",
                    numericId = numId,
                    relativePath = "app/drawable/icon.svg",
                    format = "svg",
                    svgSource = defaultSvg,
                    svgPaths = pathList,
                    viewBoxWidth = vbW,
                    viewBoxHeight = vbH,
                    base64Data = null
                )
            }
        }

        val configuredThemeColor = themeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
        if (configuredThemeColor != null) {
            if ("theme_color" !in colors) {
                colors["theme_color"] = ValueEntry(
                    name = "theme_color",
                    numericId = (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF),
                    rawValue = configuredThemeColor,
                    categoryDir = "color"
                )
            }
            if ("colorPrimary" !in colors) {
                colors["colorPrimary"] = ValueEntry(
                    name = "colorPrimary",
                    numericId = (0x7f03 shl 16) or (nextColorIdx++ and 0xFFFF),
                    rawValue = configuredThemeColor,
                    categoryDir = "color"
                )
            }
        }

        val configuredAppName = appName.orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "GenericUI"
        val configuredThemeMode = themeMode.orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "auto"

        // Collect upstream module manifests (for single-blob backend translations only — NEVER polluting app R.kt/RC.kt/native binary symbols)
        val upstreamSourceFiles = buildList {
            for (rawPath in upstreamResourceManifestPaths.getOrElse(emptyList())) {
                add(File(rawPath))
            }
            addAll(upstreamResourceClasspath.files)
        }
        val upstreamManifests = GenericUiModuleResourceManifestParser.readManifestsFromSources(upstreamSourceFiles)
        val upstreamDrawables = linkedMapOf<String, DrawableEntry>()
        val upstreamAndroidVariants = linkedMapOf<String, AndroidDrawableVariant>()
        val upstreamStrings = linkedMapOf<String, ValueEntry>()
        val upstreamColors = linkedMapOf<String, ValueEntry>()
        val upstreamDimens = linkedMapOf<String, ValueEntry>()
        val upstreamIntegers = linkedMapOf<String, ValueEntry>()
        val upstreamBools = linkedMapOf<String, ValueEntry>()
        val upstreamRaws = linkedMapOf<String, RawEntry>()
        val upstreamFonts = linkedMapOf<String, ValueEntry>()
        val upstreamXmls = linkedMapOf<String, RawEntry>()
        val upstreamStyles = linkedMapOf<String, RawEntry>()

        for (manifest in upstreamManifests) {
            for (entry in manifest.entries) {
                val scopeKey = "${entry.canonicalRelativePath}|${entry.name}"
                when (entry.type) {
                    "DRAWABLE" -> {
                        val vbW = entry.viewBoxWidth
                        val vbH = entry.viewBoxHeight
                        val d = DrawableEntry(
                            name = entry.name,
                            numericId = entry.id ?: ((manifest.moduleByte shl 24) or (0x01 shl 16) or (nextDrawableIdx++ and 0xFFFF)),
                            relativePath = entry.canonicalRelativePath,
                            format = entry.format.ifEmpty { "svg" },
                            svgSource = entry.textContent,
                            svgPaths = entry.textContent?.let { extractSvgPaths(it) } ?: emptyList(),
                            viewBoxWidth = vbW,
                            viewBoxHeight = vbH,
                            base64Data = entry.base64Data
                        )
                        upstreamDrawables.putIfAbsent(scopeKey, d)
                        val parentDirLower = entry.moduleLocalRelativePath.substringBeforeLast('/', "").substringAfterLast('/').lowercase(Locale.US)
                        val (kind, qual) = extractAndroidKindAndQualifier(parentDirLower)
                        val varKey = "$kind$qual/${d.flatName}"
                        upstreamAndroidVariants.putIfAbsent(
                            varKey,
                            AndroidDrawableVariant(
                                name = d.name,
                                flatName = d.flatName,
                                resourceKind = kind,
                                qualifierSuffix = qual,
                                format = d.format,
                                svgSource = d.svgSource,
                                viewBoxWidth = d.viewBoxWidth,
                                viewBoxHeight = d.viewBoxHeight,
                                base64Data = d.base64Data
                            )
                        )
                    }
                    "STRING" -> upstreamStrings.putIfAbsent(
                        "${manifest.scopePrefix}/string/${entry.name}",
                        ValueEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x02 shl 16) or (nextStringIdx++ and 0xFFFF)), entry.rawValue.orEmpty(), scopePrefix = manifest.scopePrefix, categoryDir = "string")
                    )
                    "COLOR" -> upstreamColors.putIfAbsent(
                        "${manifest.scopePrefix}/color/${entry.name}",
                        ValueEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x03 shl 16) or (nextColorIdx++ and 0xFFFF)), entry.rawValue.orEmpty(), scopePrefix = manifest.scopePrefix, categoryDir = "color")
                    )
                    "DIMEN" -> upstreamDimens.putIfAbsent(
                        "${manifest.scopePrefix}/dimen/${entry.name}",
                        ValueEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x04 shl 16) or (nextDimenIdx++ and 0xFFFF)), entry.rawValue.orEmpty(), scopePrefix = manifest.scopePrefix, categoryDir = "dimen")
                    )
                    "INTEGER" -> upstreamIntegers.putIfAbsent(
                        "${manifest.scopePrefix}/integer/${entry.name}",
                        ValueEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x05 shl 16) or (nextIntIdx++ and 0xFFFF)), entry.rawValue.orEmpty(), scopePrefix = manifest.scopePrefix, categoryDir = "integer")
                    )
                    "BOOL" -> upstreamBools.putIfAbsent(
                        "${manifest.scopePrefix}/bool/${entry.name}",
                        ValueEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x06 shl 16) or (nextBoolIdx++ and 0xFFFF)), entry.rawValue.orEmpty(), scopePrefix = manifest.scopePrefix, categoryDir = "bool")
                    )
                    "RAW" -> upstreamRaws.putIfAbsent(
                        scopeKey,
                        RawEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x07 shl 16) or (nextRawIdx++ and 0xFFFF)), entry.canonicalRelativePath, entry.textContent, entry.base64Data, categoryDir = "raw")
                    )
                    "FONT" -> upstreamFonts.putIfAbsent(
                        scopeKey,
                        ValueEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x08 shl 16) or (nextFontIdx++ and 0xFFFF)), entry.canonicalRelativePath, entry.base64Data, scopePrefix = manifest.scopePrefix, categoryDir = "font")
                    )
                    "XML" -> upstreamXmls.putIfAbsent(
                        scopeKey,
                        RawEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x09 shl 16) or (nextXmlIdx++ and 0xFFFF)), entry.canonicalRelativePath, entry.textContent, entry.base64Data, categoryDir = "xml")
                    )
                    "STYLE" -> upstreamStyles.putIfAbsent(
                        scopeKey,
                        RawEntry(entry.name, entry.id ?: ((manifest.moduleByte shl 24) or (0x0c shl 16) or (nextStyleIdx++ and 0xFFFF)), entry.canonicalRelativePath, entry.textContent, entry.base64Data, categoryDir = "style")
                    )
                }
            }
        }

        // 1. Emit GeneratedResourceRegistry.kt (1 Class Per File)
        File(pkgDir, "GeneratedResourceRegistry.kt").writeText(
            buildGeneratedResourceRegistryKotlin(
                pkgName = pkgName,
                appName = configuredAppName,
                themeMode = configuredThemeMode,
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
            val singleBlobDrawables = drawables.values.toList() + upstreamDrawables.values
            val singleBlobVariants = androidVariants.values.toList() + upstreamAndroidVariants.values
            val singleBlobStrings = strings.values.toList() + upstreamStrings.values
            val singleBlobColors = colors.values.toList() + upstreamColors.values
            val singleBlobDimens = dimens.values.toList() + upstreamDimens.values
            val singleBlobIntegers = integers.values.toList() + upstreamIntegers.values
            val singleBlobBools = bools.values.toList() + upstreamBools.values
            val singleBlobRaws = raws.values.toList() + upstreamRaws.values
            val singleBlobFonts = fonts.values.toList() + upstreamFonts.values
            val singleBlobXmls = xmls.values.toList() + upstreamXmls.values
            val singleBlobStyles = styles.values.toList() + upstreamStyles.values

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
                androidVariants = singleBlobVariants,
                drawables = singleBlobDrawables,
                strings = singleBlobStrings,
                colors = singleBlobColors,
                dimens = singleBlobDimens,
                integers = singleBlobIntegers,
                bools = singleBlobBools,
                raws = singleBlobRaws,
                fonts = singleBlobFonts,
                xmls = singleBlobXmls,
                svgCacheDir = File(backendOutRoot.parentFile, "svg-cache")
            )
            // macOS uses Mach-O embedded C resources (`macos/genericui_app_resources.c`) + icon.icns only; never Assets.xcassets
            emitAppleResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "ios"),
                drawables = singleBlobDrawables,
                strings = singleBlobStrings,
                colors = singleBlobColors,
                raws = singleBlobRaws,
                fonts = singleBlobFonts,
                xmls = singleBlobXmls,
                styles = singleBlobStyles,
                targetSpecificIconPath = iosIconPath.orNull,
                fullBleedAppIcon = true,
                explicitFlagProperty = iosIconExplicitlyProvided.orNull
            )
            emitAppleResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "ipados"),
                drawables = singleBlobDrawables,
                strings = singleBlobStrings,
                colors = singleBlobColors,
                raws = singleBlobRaws,
                fonts = singleBlobFonts,
                xmls = singleBlobXmls,
                styles = singleBlobStyles,
                targetSpecificIconPath = ipadosIconPath.orNull,
                fullBleedAppIcon = true,
                explicitFlagProperty = ipadosIconExplicitlyProvided.orNull
            )
            emitPosixBinaryResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "macos"),
                drawables = drawables.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList(),
                xmls = xmls.values.toList(),
                styles = styles.values.toList(),
                includeMacosUnwrappedTrayIcon = true
            )
            emitPosixBinaryResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "linux"),
                drawables = drawables.values.toList(),
                raws = raws.values.toList(),
                fonts = fonts.values.toList(),
                xmls = xmls.values.toList(),
                styles = styles.values.toList(),
                includeMacosUnwrappedTrayIcon = false
            )
            emitWebAndSwingResourceTranslations(
                projDir = projDir,
                outDir = File(backendOutRoot, "web"),
                drawables = singleBlobDrawables,
                strings = singleBlobStrings,
                colors = singleBlobColors,
                dimens = singleBlobDimens,
                raws = singleBlobRaws,
                fonts = singleBlobFonts,
                xmls = singleBlobXmls,
                styles = singleBlobStyles
            )
        }
    }

    private fun buildGeneratedResourceRegistryKotlin(
        pkgName: String,
        appName: String,
        themeMode: String = "auto",
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
        appendLine("import com.nexustools.genericui.enums.ui.ThemeMode")
        appendLine("import com.nexustools.genericui.res.DrawableResource")
        appendLine("import com.nexustools.genericui.resources.ResourceID")
        appendLine("import com.nexustools.genericui.resources.ResourceResolver")
        appendLine("import com.nexustools.genericui.resources.ResourceType")
        appendLine("import com.nexustools.genericui.res.Resources")
        appendLine()
        appendLine("/**")
        appendLine(" * Auto-generated by GenericUI `compileGenericUiResources`.")
        appendLine(" * Populates [Resources.activeTable], [GenericUI.applicationName], and [GenericUI.defaultThemeMode] across all target backends.")
        appendLine(" */")
        appendLine("object GeneratedResourceRegistry {")
        appendLine("    const val APP_NAME: String = ${escapeKotlinString(appName)}")
        appendLine("    const val THEME_MODE: String = ${escapeKotlinString(themeMode)}")
        appendLine("    val DEFAULT_THEME_MODE: ThemeMode = ThemeMode.fromString(THEME_MODE)")
        appendLine("    private var initialized: Boolean = false")
        appendLine()
        appendLine("    init {")
        appendLine("        ResourceResolver.registerModuleInitializer { ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    fun ensureInitialized() {")
        appendLine("        if (initialized) return")
        appendLine("        initialized = true")
        appendLine("        GenericUI.applicationName = APP_NAME")
        appendLine("        GenericUI.defaultThemeMode = DEFAULT_THEME_MODE")
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
                    "resId = ResourceID(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)}), " +
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
                "        table.registerString(ResourceID(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)}), ${escapeKotlinString(s.rawValue)})"
            )
        }
        for (c in colors) {
            appendLine(
                "        table.registerColor(ResourceID(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)}), ${escapeKotlinString(c.rawValue)})"
            )
        }
        for (dm in dimens) {
            val numericVal = dm.rawValue.replace(Regex("[^0-9.-]"), "").toFloatOrNull() ?: 0f
            appendLine(
                "        table.registerDimen(ResourceID(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)}), ${numericVal}f)"
            )
        }
        for (iv in integers) {
            val intVal = iv.rawValue.trim().toIntOrNull() ?: 0
            appendLine(
                "        table.registerInteger(ResourceID(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)}), $intVal)"
            )
        }
        for (bv in bools) {
            val boolVal = bv.rawValue.trim().equals("true", ignoreCase = true)
            appendLine(
                "        table.registerBool(ResourceID(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)}), $boolVal)"
            )
        }
        for (rw in raws) {
            if (rw.textContent != null) {
                appendLine(
                    "        table.registerRawText(ResourceID(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)}), ${escapeKotlinString(rw.textContent)})"
                )
            } else if (rw.base64Content != null) {
                appendLine(
                    "        table.registerRawBase64(ResourceID(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)}), ${escapeKotlinString(rw.base64Content)})"
                )
            }
        }
        for (fn in fonts) {
            appendLine(
                "        table.registerFont(ResourceID(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)}), ${escapeKotlinString(fn.rawValue)})"
            )
            if (fn.base64Data != null) {
                appendLine(
                    "        table.registerRawBase64(ResourceID(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)}), ${escapeKotlinString(fn.base64Data)})"
                )
            }
        }
        for (xm in xmls) {
            if (xm.textContent != null) {
                appendLine(
                    "        table.registerRawText(ResourceID(0x${xm.numericId.toString(16)}, ResourceType.XML, ${escapeKotlinString(xm.name)}, ${escapeKotlinString(xm.relativePath)}), ${escapeKotlinString(xm.textContent)})"
                )
            }
        }
        for (st in styles) {
            val resIdExpr = "ResourceID(0x${st.numericId.toString(16)}, ResourceType.STYLE, ${escapeKotlinString(st.name)}, ${escapeKotlinString(st.relativePath)})"
            val text = st.textContent
            if (text != null) {
                val textExpr = if (text.length <= 32768) {
                    escapeKotlinString(text)
                } else {
                    buildString {
                        append("buildString { ")
                        var offset = 0
                        while (offset < text.length) {
                            val end = (offset + 16384).coerceAtMost(text.length)
                            append("append(")
                            append(escapeKotlinString(text.substring(offset, end)))
                            append("); ")
                            offset = end
                        }
                        append("}")
                    }
                }
                appendLine("        table.registerStyle($resIdExpr, $textExpr)")
            } else {
                appendLine("        table.registerResourceId($resIdExpr)")
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
        appendLine("import com.nexustools.genericui.resources.ResourceID")
        appendLine("import com.nexustools.genericui.resources.ResourceType")
        appendLine()
        appendLine("/**")
        appendLine(" * Android-like `R` resource class generated by the GenericUI Gradle plugin.")
        appendLine(" */")
        appendLine("object R {")
        appendLine("    object drawable {")
        for (d in drawables) {
            appendLine(
                "        val ${d.name}: ResourceID = ResourceID(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object string {")
        for (s in strings) {
            appendLine(
                "        val ${s.name}: ResourceID = ResourceID(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object color {")
        for (c in colors) {
            appendLine(
                "        val ${c.name}: ResourceID = ResourceID(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object dimen {")
        for (dm in dimens) {
            appendLine(
                "        val ${dm.name}: ResourceID = ResourceID(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object integer {")
        for (iv in integers) {
            appendLine(
                "        val ${iv.name}: ResourceID = ResourceID(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object bool {")
        for (bv in bools) {
            appendLine(
                "        val ${bv.name}: ResourceID = ResourceID(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object raw {")
        for (rw in raws) {
            appendLine(
                "        val ${rw.name}: ResourceID = ResourceID(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object font {")
        for (fn in fonts) {
            appendLine(
                "        val ${fn.name}: ResourceID = ResourceID(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object xml {")
        for (xm in xmls) {
            appendLine(
                "        val ${xm.name}: ResourceID = ResourceID(0x${xm.numericId.toString(16)}, ResourceType.XML, ${escapeKotlinString(xm.name)}, ${escapeKotlinString(xm.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object style {")
        for (st in styles) {
            appendLine(
                "        val ${st.name}: ResourceID = ResourceID(0x${st.numericId.toString(16)}, ResourceType.STYLE, ${escapeKotlinString(st.name)}, ${escapeKotlinString(st.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    init {")
        appendLine("        GeneratedResourceRegistry.ensureInitialized()")
        appendLine("    }")
        appendLine("}")
    }

    private fun buildRcAliasKotlin(pkgName: String): String = """
package $pkgName

import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.RgbaColor

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

    fun getString(resId: ResourceID): String = Resources.getString(resId)
    fun getString(resId: ResourceID, vararg args: Any): String = Resources.getString(resId, *args)
    fun getColor(resId: ResourceID): RgbaColor = Resources.getColor(resId)
    fun getDimen(resId: ResourceID): Float = Resources.getDimen(resId)
    fun getDrawable(resId: ResourceID): DrawableResource? = Resources.getDrawable(resId)
    suspend fun openReadable(resId: ResourceID): com.nexustools.genericui.io.Readable? = Resources.openReadable(resId)
    suspend fun openStream(resId: ResourceID): com.nexustools.genericui.io.Readable? = Resources.openReadable(resId)
    fun icon(resId: ResourceID): String = resId.toResourceUri()
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
        appendLine("import com.nexustools.genericui.resources.ResourceID")
        appendLine()
        appendLine("/**")
        appendLine(" * Unified `Resource` object generated by the GenericUI Gradle plugin (`1 Class Per File`),")
        appendLine(" * exposing typed [ResourceID] constants and cross-platform [com.nexustools.genericui.resources.ResourceResolver] helpers.")
        appendLine(" */")
        appendLine("object Resource {")
        appendLine("    object drawable {")
        for (d in drawables) {
            appendLine(
                "        val ${d.name}: ResourceID = ResourceID(0x${d.numericId.toString(16)}, ResourceType.DRAWABLE, ${escapeKotlinString(d.name)}, ${escapeKotlinString(d.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object string {")
        for (s in strings) {
            appendLine(
                "        val ${s.name}: ResourceID = ResourceID(0x${s.numericId.toString(16)}, ResourceType.STRING, ${escapeKotlinString(s.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object color {")
        for (c in colors) {
            appendLine(
                "        val ${c.name}: ResourceID = ResourceID(0x${c.numericId.toString(16)}, ResourceType.COLOR, ${escapeKotlinString(c.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object dimen {")
        for (dm in dimens) {
            appendLine(
                "        val ${dm.name}: ResourceID = ResourceID(0x${dm.numericId.toString(16)}, ResourceType.DIMEN, ${escapeKotlinString(dm.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object integer {")
        for (iv in integers) {
            appendLine(
                "        val ${iv.name}: ResourceID = ResourceID(0x${iv.numericId.toString(16)}, ResourceType.INTEGER, ${escapeKotlinString(iv.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object bool {")
        for (bv in bools) {
            appendLine(
                "        val ${bv.name}: ResourceID = ResourceID(0x${bv.numericId.toString(16)}, ResourceType.BOOL, ${escapeKotlinString(bv.name)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object raw {")
        for (rw in raws) {
            appendLine(
                "        val ${rw.name}: ResourceID = ResourceID(0x${rw.numericId.toString(16)}, ResourceType.RAW, ${escapeKotlinString(rw.name)}, ${escapeKotlinString(rw.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object font {")
        for (fn in fonts) {
            appendLine(
                "        val ${fn.name}: ResourceID = ResourceID(0x${fn.numericId.toString(16)}, ResourceType.FONT, ${escapeKotlinString(fn.name)}, ${escapeKotlinString(fn.rawValue)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object xml {")
        for (xm in xmls) {
            appendLine(
                "        val ${xm.name}: ResourceID = ResourceID(0x${xm.numericId.toString(16)}, ResourceType.XML, ${escapeKotlinString(xm.name)}, ${escapeKotlinString(xm.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    object style {")
        for (st in styles) {
            appendLine(
                "        val ${st.name}: ResourceID = ResourceID(0x${st.numericId.toString(16)}, ResourceType.STYLE, ${escapeKotlinString(st.name)}, ${escapeKotlinString(st.relativePath)})"
            )
        }
        appendLine("        init { GeneratedResourceRegistry.ensureInitialized() }")
        appendLine("    }")
        appendLine()
        appendLine("    init {")
        appendLine("        GeneratedResourceRegistry.ensureInitialized()")
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
        appendLine("    suspend fun openReadable(id: ResourceID): com.nexustools.genericui.io.Readable? =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.openReadable(id)")
        appendLine()
        appendLine("    suspend fun openStream(id: ResourceID): com.nexustools.genericui.io.Readable? =")
        appendLine("        com.nexustools.genericui.resources.ResourceResolver.openReadable(id)")
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
            val modRel = GenericUiModuleResourceManifestParser.stripScopePrefix(d.relativePath)
            entries.add(
                "{\"type\":\"DRAWABLE\",\"name\":${escapeJsonString(d.name)},\"id\":${d.numericId}," +
                    "\"relativePath\":${escapeJsonString(modRel)},\"canonicalRelativePath\":${escapeJsonString(d.relativePath)}," +
                    "\"nativeResourceKey\":${escapeJsonString(d.nativeResourceKey)},\"nativeLibraryGroupId\":\"\"," +
                    "\"format\":${escapeJsonString(d.format)},\"viewBoxWidth\":${d.viewBoxWidth},\"viewBoxHeight\":${d.viewBoxHeight}," +
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
            val modRel = GenericUiModuleResourceManifestParser.stripScopePrefix(rw.relativePath)
            entries.add(
                "{\"type\":\"RAW\",\"name\":${escapeJsonString(rw.name)},\"id\":${rw.numericId}," +
                    "\"relativePath\":${escapeJsonString(modRel)},\"canonicalRelativePath\":${escapeJsonString(rw.relativePath)}," +
                    "\"nativeResourceKey\":${escapeJsonString(rw.nativeResourceKey)},\"nativeLibraryGroupId\":\"\"," +
                    "\"textBase64\":${escapeJsonString(txtB64)},\"base64Data\":${escapeJsonString(binB64)}}"
            )
        }
        for (fn in fonts) {
            val b64 = fn.base64Data ?: ""
            val modRel = GenericUiModuleResourceManifestParser.stripScopePrefix(fn.rawValue)
            entries.add(
                "{\"type\":\"FONT\",\"name\":${escapeJsonString(fn.name)},\"id\":${fn.numericId}," +
                    "\"relativePath\":${escapeJsonString(modRel)},\"canonicalRelativePath\":${escapeJsonString(fn.rawValue)}," +
                    "\"nativeResourceKey\":${escapeJsonString(fn.nativeResourceKey)},\"nativeLibraryGroupId\":\"\"," +
                    "\"base64Data\":${escapeJsonString(b64)}}"
            )
        }
        for (xm in xmls) {
            val txtB64 = xm.textContent?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""
            val modRel = GenericUiModuleResourceManifestParser.stripScopePrefix(xm.relativePath)
            entries.add(
                "{\"type\":\"XML\",\"name\":${escapeJsonString(xm.name)},\"id\":${xm.numericId}," +
                    "\"relativePath\":${escapeJsonString(modRel)},\"canonicalRelativePath\":${escapeJsonString(xm.relativePath)}," +
                    "\"nativeResourceKey\":${escapeJsonString(xm.nativeResourceKey)},\"nativeLibraryGroupId\":\"\"," +
                    "\"textBase64\":${escapeJsonString(txtB64)}}"
            )
        }
        for (st in styles) {
            val txtB64 = st.textContent?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""
            val modRel = GenericUiModuleResourceManifestParser.stripScopePrefix(st.relativePath)
            val ext = st.relativePath.substringAfterLast('.', "scss").lowercase()
            entries.add(
                "{\"type\":\"STYLE\",\"name\":${escapeJsonString(st.name)},\"id\":${st.numericId}," +
                    "\"relativePath\":${escapeJsonString(modRel)},\"canonicalRelativePath\":${escapeJsonString(st.relativePath)}," +
                    "\"nativeResourceKey\":${escapeJsonString(st.nativeResourceKey)},\"nativeLibraryGroupId\":\"\"," +
                    "\"format\":${escapeJsonString(ext)},\"textBase64\":${escapeJsonString(txtB64)},\"base64Data\":${escapeJsonString(txtB64)}}"
            )
        }

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"schemaVersion\": 2,")
        sb.appendLine("  \"scopeId\": \"genericui-app\",")
        sb.appendLine("  \"moduleByte\": 127,")
        sb.appendLine("  \"moduleGroupId\": \"\",")
        sb.appendLine("  \"scopePrefix\": \"app\",")
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

    private fun resolveProjectIconSourceFile(projDir: File): File? {
        val explicit = configuredIconPath.orNull?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) {
            val f = File(explicit).let { if (it.isAbsolute) it else File(projDir, explicit) }
            if (f.exists() && f.isFile) return f
        }
        val candidates = mutableListOf(
            "src/commonMain/resources/icon.svg",
            "src/commonMain/resources/icon.png",
            "src/commonMain/resources/icon.ico",
            "src/commonMain/resources/favicon.ico",
            "src/commonMain/resources/drawable/icon.svg",
            "src/commonMain/resources/drawable/icon.png",
            "src/commonMain/resources/drawable/icon.ico",
            "src/commonMain/resources/drawable/favicon.ico"
        )
        for (srcDir in candidateSourceDirs.getOrElse(emptyList())) {
            val clean = srcDir.trim().trimEnd('/', '\\')
            if (clean.isEmpty()) continue
            for (rel in listOf(
                "$clean/icon.svg",
                "$clean/icon.png",
                "$clean/icon.ico",
                "$clean/favicon.ico",
                "$clean/drawable/icon.svg",
                "$clean/drawable/icon.png",
                "$clean/drawable/icon.ico",
                "$clean/drawable/favicon.ico"
            )) {
                if (rel !in candidates) candidates.add(rel)
            }
        }
        candidates.addAll(listOf("icon.svg", "icon.png", "icon.ico", "favicon.ico"))
        return candidates.map { File(projDir, it) }.firstOrNull { it.exists() && it.isFile }
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
        val sourceIconFile = resolveProjectIconSourceFile(projDir)
        GenericUiDefaultIconProvider.ensureWindowsIcoFromSourceOrDefault(sourceIconFile, projDir, outDir)
        val headerSb = StringBuilder()
        headerSb.appendLine("// Auto-generated Win32 Resource Header by GenericUI")
        headerSb.appendLine("#pragma once")
        for (s in strings) {
            headerSb.appendLine("#define IDS_STRING_${s.name.uppercase()} ${s.win32ResourceId(0x02)}")
        }
        var nextCursorResId = 13001
        val cursorIds = linkedMapOf<String, Int>()
        for (cursorName in cursors.keys) {
            val cid = nextCursorResId++
            cursorIds[cursorName] = cid
            headerSb.appendLine("#define IDC_CURSOR_${cursorName.uppercase()} $cid")
        }
        File(outDir, "genericui_resource_ids.h").writeText(headerSb.toString())

        val rcSb = StringBuilder()
        rcSb.appendLine("// Auto-generated Win32 Resource Script (.rc) by GenericUI (app-only, string-named RCDATA; 1 ICON handled by app_resources.rc)")
        rcSb.appendLine("#include \"genericui_resource_ids.h\"")
        rcSb.appendLine()

        // Generate Windows .cur files for explicit cursors and drawables
        val generatedCurFiles = linkedMapOf<String, File>()
        for ((cursorName, srcFile) in cursors) {
            val curOut = File(cursorsDir, "$cursorName.cur")
            WindowsCurResourceGenerator.generateCurFile(srcFile, curOut)
            generatedCurFiles[cursorName] = curOut
            rcSb.appendLine("IDC_CURSOR_${cursorName.uppercase()} CURSOR \"${curOut.invariantSeparatorsPath}\"")
        }

        val emittedRdataKeys = linkedSetOf<String>()
        fun appendRdataLine(key: String, assetFile: File) {
            if (emittedRdataKeys.add(key)) {
                rcSb.appendLine("$key RCDATA \"${assetFile.invariantSeparatorsPath}\"")
            }
        }

        val drawableAssetDir = File(assetsRoot, "drawable").apply { mkdirs() }
        for (d in drawables) {
            val fileName = d.relativePath.substringAfterLast('/').ifEmpty { "${d.name}.${d.format}" }
            val assetFile = File(drawableAssetDir, fileName)
            writeDrawablePayload(d, assetFile)
            appendRdataLine(d.nativeResourceKey, assetFile)
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
            appendRdataLine(r.nativeResourceKey, assetFile)
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
            appendRdataLine(f.nativeResourceKey, assetFile)
        }

        val xmlAssetDir = File(assetsRoot, "xml").apply { mkdirs() }
        for (x in xmls) {
            val fileName = x.relativePath.substringAfterLast('/').ifEmpty { "${x.name}.xml" }
            val assetFile = File(xmlAssetDir, fileName)
            writeRawPayload(x, assetFile)
            appendRdataLine(x.nativeResourceKey, assetFile)
        }

        val styleAssetDir = File(assetsRoot, "style").apply { mkdirs() }
        for (st in styles) {
            val fileName = st.relativePath.substringAfterLast('/').ifEmpty { "${st.name}.scss" }
            val assetFile = File(styleAssetDir, fileName)
            writeRawPayload(st, assetFile)
            appendRdataLine(st.nativeResourceKey, assetFile)
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

    private fun emitPosixBinaryResourceTranslations(
        projDir: File,
        outDir: File,
        drawables: List<DrawableEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>,
        styles: List<RawEntry>,
        includeMacosUnwrappedTrayIcon: Boolean
    ) {
        outDir.mkdirs()
        val payloadMap = linkedMapOf<String, ByteArray>()
        for (d in drawables) {
            val bytes = when {
                d.svgSource != null -> d.svgSource.toByteArray(Charsets.UTF_8)
                d.base64Data != null -> Base64.getDecoder().decode(d.base64Data)
                else -> ByteArray(0)
            }
            payloadMap.putIfAbsent(d.nativeResourceKey, bytes)
        }
        for (r in raws) {
            val bytes = when {
                r.textContent != null -> r.textContent.toByteArray(Charsets.UTF_8)
                r.base64Content != null -> Base64.getDecoder().decode(r.base64Content)
                else -> ByteArray(0)
            }
            payloadMap.putIfAbsent(r.nativeResourceKey, bytes)
        }
        for (f in fonts) {
            val bytes = f.base64Data?.let { Base64.getDecoder().decode(it) } ?: ByteArray(0)
            payloadMap.putIfAbsent(f.nativeResourceKey, bytes)
        }
        for (x in xmls) {
            val bytes = when {
                x.textContent != null -> x.textContent.toByteArray(Charsets.UTF_8)
                x.base64Content != null -> Base64.getDecoder().decode(x.base64Content)
                else -> ByteArray(0)
            }
            payloadMap.putIfAbsent(x.nativeResourceKey, bytes)
        }
        for (st in styles) {
            val bytes = when {
                st.textContent != null -> st.textContent.toByteArray(Charsets.UTF_8)
                st.base64Content != null -> Base64.getDecoder().decode(st.base64Content)
                else -> ByteArray(0)
            }
            payloadMap.putIfAbsent(st.nativeResourceKey, bytes)
        }

        if (includeMacosUnwrappedTrayIcon) {
            val traySource = resolveExistingIconFileOrNull(projDir, trayIconPath.orNull)
                ?: resolveExistingIconFileOrNull(projDir, macIconPath.orNull)
                ?: resolveExistingIconFileOrNull(projDir, appleIconPath.orNull)
                ?: resolveProjectIconSourceFile(projDir)
            if ("tray_icon_png" !in payloadMap) {
                val trayTmpDir = File(outDir, ".tray-tmp").apply { mkdirs() }
                val trayPngFile = GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
                    sourceIconFile = traySource,
                    projectRootDir = projDir,
                    outputDir = trayTmpDir,
                    fileName = "tray_icon.png",
                    wrapInAppleTile = false,
                    fullBleedSquare = false,
                    requestedSizeOverride = 64
                )
                payloadMap["tray_icon_png"] = trayPngFile.readBytes()
                trayTmpDir.deleteRecursively()
            }
            if ("tray_icon_svg" !in payloadMap && traySource != null && traySource.extension.equals("svg", ignoreCase = true)) {
                payloadMap["tray_icon_svg"] = traySource.readBytes()
            }
            val explicitMacosTraySource = resolveExistingIconFileOrNull(projDir, macosTrayIconPath.orNull)
            if (explicitMacosTraySource != null) {
                if ("macos_tray_icon_png" !in payloadMap) {
                    val macTrayTmpDir = File(outDir, ".macos-tray-tmp").apply { mkdirs() }
                    val macTrayPngFile = GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
                        sourceIconFile = explicitMacosTraySource,
                        projectRootDir = projDir,
                        outputDir = macTrayTmpDir,
                        fileName = "macos_tray_icon.png",
                        wrapInAppleTile = false,
                        fullBleedSquare = false,
                        requestedSizeOverride = 64
                    )
                    payloadMap["macos_tray_icon_png"] = macTrayPngFile.readBytes()
                    macTrayTmpDir.deleteRecursively()
                }
                if ("macos_tray_icon_svg" !in payloadMap && explicitMacosTraySource.extension.equals("svg", ignoreCase = true)) {
                    payloadMap["macos_tray_icon_svg"] = explicitMacosTraySource.readBytes()
                }
            }
        }

        val cSource = GenericUiNativeResourceEmbedder.generatePosixResourceTableCSource(
            lookupSymbol = "genericui_res_lookup_app",
            resources = payloadMap,
            includeMachOConstSection = includeMacosUnwrappedTrayIcon
        )
        val cFile = File(outDir, "genericui_app_resources.c")
        cFile.writeText(cSource, Charsets.UTF_8)

        val defaultKonanTarget = if (includeMacosUnwrappedTrayIcon) "macos_arm64" else "linux_x64"
        GenericUiNativeResourceEmbedder.compileCSourceToObject(
            cFile = cFile,
            objOut = File(outDir, "genericui_app_resources.o"),
            konanTargetName = defaultKonanTarget
        )
    }

    private fun emitAndroidResourceTranslations(
        projDir: File,
        outDir: File,
        androidVariants: List<AndroidDrawableVariant>,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        integers: List<ValueEntry> = emptyList(),
        bools: List<ValueEntry> = emptyList(),
        raws: List<RawEntry> = emptyList(),
        fonts: List<ValueEntry> = emptyList(),
        xmls: List<RawEntry> = emptyList(),
        svgCacheDir: File? = null
    ) {
        val valuesDir = File(outDir, "values").apply { mkdirs() }
        val valuesNightDir = File(outDir, "values-night").apply { mkdirs() }
        File(outDir, "drawable").mkdirs()

        val resolvedLight = GenericUiThemeConfig.defaultLightColors(themeColor.orNull).toMutableMap().apply {
            putAll(lightThemeColors.orNull.orEmpty().filterValues { it.isNotBlank() })
        }
        val resolvedDark = GenericUiThemeConfig.defaultDarkColors(themeColor.orNull).toMutableMap().apply {
            putAll(darkThemeColors.orNull.orEmpty().filterValues { it.isNotBlank() })
        }

        File(valuesDir, "genericui_theme_colors.xml").writeText(
            buildAndroidThemeColorsXml(resolvedLight, resolvedDark)
        )

        val modeLower = themeMode.orNull?.trim()?.lowercase() ?: "auto"
        val dayUsesDark = modeLower == "dark"
        val nightUsesLight = modeLower == "light"
        File(valuesDir, "themes.xml").writeText(
            buildAndroidThemesXml(
                parentTheme = if (dayUsesDark) "Theme.MaterialComponents.NoActionBar" else "Theme.MaterialComponents.Light.NoActionBar",
                colorPrefix = if (dayUsesDark) "genericui_dark" else "genericui_light"
            )
        )
        File(valuesNightDir, "themes.xml").writeText(
            buildAndroidThemesXml(
                parentTheme = if (nightUsesLight) "Theme.MaterialComponents.Light.NoActionBar" else "Theme.MaterialComponents.NoActionBar",
                colorPrefix = if (nightUsesLight) "genericui_light" else "genericui_dark"
            )
        )

        run {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            val emittedNames = linkedSetOf<String>()
            val configuredAppTitle = strings.firstOrNull { it.name == "app_name" }?.rawValue
                ?: appName.orNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: "GenericUI"
            if (emittedNames.add("app_name")) {
                sb.appendLine("    <string name=\"app_name\">${escapeXml(configuredAppTitle)}</string>")
            }
            for (s in strings) {
                if (emittedNames.add(s.flatName)) {
                    sb.appendLine("    <string name=\"${s.flatName}\">${escapeXml(s.rawValue)}</string>")
                }
            }
            sb.appendLine("</resources>")
            File(valuesDir, "strings.xml").writeText(sb.toString())
        }

        if (colors.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            val emittedNames = linkedSetOf<String>()
            for (c in colors) {
                if (emittedNames.add(c.flatName)) {
                    sb.appendLine("    <color name=\"${c.flatName}\">${escapeXml(c.rawValue)}</color>")
                }
            }
            sb.appendLine("</resources>")
            File(valuesDir, "colors.xml").writeText(sb.toString())
        }

        if (dimens.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            val emittedNames = linkedSetOf<String>()
            for (d in dimens) {
                if (emittedNames.add(d.flatName)) {
                    sb.appendLine("    <dimen name=\"${d.flatName}\">${escapeXml(d.rawValue)}</dimen>")
                }
            }
            sb.appendLine("</resources>")
            File(valuesDir, "dimens.xml").writeText(sb.toString())
        }

        if (integers.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            val emittedNames = linkedSetOf<String>()
            for (iv in integers) {
                val parsedInt = iv.rawValue.trim().toIntOrNull() ?: continue
                if (emittedNames.add(iv.flatName)) {
                    sb.appendLine("    <integer name=\"${iv.flatName}\">$parsedInt</integer>")
                }
            }
            sb.appendLine("</resources>")
            File(valuesDir, "integers.xml").writeText(sb.toString())
        }

        if (bools.isNotEmpty()) {
            val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            val emittedNames = linkedSetOf<String>()
            for (bv in bools) {
                val parsedBool = bv.rawValue.trim().equals("true", ignoreCase = true)
                if (emittedNames.add(bv.flatName)) {
                    sb.appendLine("    <bool name=\"${bv.flatName}\">$parsedBool</bool>")
                }
            }
            sb.appendLine("</resources>")
            File(valuesDir, "bools.xml").writeText(sb.toString())
        }

        if (raws.isNotEmpty()) {
            val rawDir = File(outDir, "raw").apply { mkdirs() }
            val emittedNames = linkedSetOf<String>()
            for (r in raws) {
                if (!emittedNames.add(r.flatName)) continue
                val ext = r.relativePath.substringAfterLast('/', "").substringAfterLast('.', "").lowercase(Locale.US)
                val fileName = if (ext.isNotEmpty() && ext.all { it.isLetterOrDigit() }) "${r.flatName}.$ext" else r.flatName
                writeRawPayload(r, File(rawDir, fileName))
            }
        }

        if (fonts.isNotEmpty()) {
            val fontDir = File(outDir, "font").apply { mkdirs() }
            val androidSupportedFontExts = setOf("ttf", "otf", "ttc", "xml")
            val emittedNames = linkedSetOf<String>()
            for (f in fonts) {
                val ext = f.rawValue.substringAfterLast('/', "").substringAfterLast('.', "").lowercase(Locale.US)
                if (ext !in androidSupportedFontExts) continue
                if (!emittedNames.add(f.flatName)) continue
                val b64 = f.base64Data ?: continue
                File(fontDir, "${f.flatName}.$ext").writeBytes(Base64.getDecoder().decode(b64))
            }
        }

        if (xmls.isNotEmpty()) {
            val xmlDir = File(outDir, "xml").apply { mkdirs() }
            val emittedNames = linkedSetOf<String>()
            for (x in xmls) {
                if (!emittedNames.add(x.flatName)) continue
                writeRawPayload(x, File(xmlDir, "${x.flatName}.xml"))
            }
        }

        val sdkVer = androidSvgConverterVersion.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: GenericUiAndroidSvgConverter.DEFAULT_SDK_COMMON_VERSION
        val failWarnings = failOnSvgConversionWarnings.orNull ?: false
        val gradleUserHome = gradleUserHomePath.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }
        val isOffline = offlineMode.orNull ?: false
        val explicitClasspath = svgConverterClasspath.files.toList()

        val colorLookup = linkedMapOf<String, String>()
        for (c in colors) {
            colorLookup[c.name] = c.rawValue
        }
        resolvedLight.forEach { (k, v) -> colorLookup.putIfAbsent("genericui_light_$k", v) }
        resolvedDark.forEach { (k, v) -> colorLookup.putIfAbsent("genericui_dark_$k", v) }

        val sourceIconFile = resolveProjectIconSourceFile(projDir)
        val launcherConfig = GenericUiAndroidLauncherIconConfig.fromSerializedString(androidLauncherIconConfig.orNull)
        GenericUiAndroidLauncherIconGenerator.generate(
            projDir = projDir,
            androidResDir = outDir,
            launcherConfig = launcherConfig,
            fallbackSourceIconFile = sourceIconFile,
            resolvedColors = colorLookup,
            svgConverterVersion = sdkVer,
            failOnSvgWarnings = failWarnings,
            svgCacheDir = svgCacheDir,
            svgConverterClasspath = explicitClasspath,
            gradleUserHomeDir = gradleUserHome,
            offlineMode = isOffline,
            logger = logger
        )

        val androidSupportedRasterExts = setOf("png", "webp", "jpg", "jpeg", "gif")
        val variantsToEmit = if (androidVariants.isNotEmpty()) {
            androidVariants
        } else {
            drawables.map { d ->
                val parentLower = d.relativePath.substringBeforeLast('/', "").substringAfterLast('/').lowercase(Locale.US)
                val (kind, qual) = extractAndroidKindAndQualifier(parentLower)
                AndroidDrawableVariant(
                    name = d.name,
                    flatName = d.flatName,
                    resourceKind = kind,
                    qualifierSuffix = qual,
                    format = d.format,
                    svgSource = d.svgSource,
                    viewBoxWidth = d.viewBoxWidth,
                    viewBoxHeight = d.viewBoxHeight,
                    base64Data = d.base64Data
                )
            }
        }

        val emittedVariantKeys = linkedSetOf<String>()
        for (v in variantsToEmit) {
            val variantKey = "${v.resourceKind}${v.qualifierSuffix}/${v.flatName}"
            if (!emittedVariantKeys.add(variantKey)) continue
            if (v.format == "svg" && v.svgSource != null) {
                val conv = GenericUiAndroidSvgConverter.convertSvgText(
                    svgText = v.svgSource,
                    sourceName = "${v.flatName}.svg",
                    sdkCommonVersion = sdkVer,
                    failOnWarnings = failWarnings,
                    cacheDir = svgCacheDir,
                    classpathFiles = explicitClasspath,
                    gradleUserHomeDir = gradleUserHome,
                    logger = logger,
                    allowNetworkDownload = !isOffline
                )
                val targetFolder = "${v.resourceKind}${v.qualifierSuffix}"
                if (conv.vectorXml != null) {
                    val dir = File(outDir, targetFolder).apply { mkdirs() }
                    File(dir, "${v.flatName}.xml").writeText(conv.vectorXml, Charsets.UTF_8)
                } else {
                    emitRasterFallbackDrawables(
                        outDir = outDir,
                        variant = v
                    )
                }
            } else if (v.format in androidSupportedRasterExts && v.base64Data != null) {
                val targetFolder = "${v.resourceKind}${v.qualifierSuffix}"
                val dir = File(outDir, targetFolder).apply { mkdirs() }
                File(dir, "${v.flatName}.${v.format}").writeBytes(Base64.getDecoder().decode(v.base64Data))
            }
        }
    }

    private fun emitRasterFallbackDrawables(
        outDir: File,
        variant: AndroidDrawableVariant
    ) {
        val svgText = variant.svgSource ?: return
        var baseW = variant.viewBoxWidth.toDouble().coerceAtLeast(1.0)
        var baseH = variant.viewBoxHeight.toDouble().coerceAtLeast(1.0)
        val maxBase = max(baseW, baseH)
        if (maxBase > 108.0) {
            val scale = 108.0 / maxBase
            baseW *= scale
            baseH *= scale
        }

        val qualifierTokens = variant.qualifierSuffix.split('-').filter { it.isNotEmpty() }.map { it.lowercase(Locale.US) }
        val fixedDensityMap = mapOf(
            "ldpi" to 0.75,
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0,
            "tvdpi" to 1.33
        )
        val explicitDensity = qualifierTokens.firstOrNull { it in fixedDensityMap }
        if (explicitDensity != null) {
            val factor = fixedDensityMap[explicitDensity] ?: 1.0
            val wPx = (baseW * factor).roundToInt().coerceAtLeast(1)
            val hPx = (baseH * factor).roundToInt().coerceAtLeast(1)
            val dir = File(outDir, "${variant.resourceKind}${variant.qualifierSuffix}").apply { mkdirs() }
            File(dir, "${variant.flatName}.png").writeBytes(
                GenericUiDefaultIconProvider.renderSvgTextToPngBytes(svgText, wPx, hPx)
            )
            return
        }

        val densities = listOf(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0
        )
        for ((density, factor) in densities) {
            val wPx = (baseW * factor).roundToInt().coerceAtLeast(1)
            val hPx = (baseH * factor).roundToInt().coerceAtLeast(1)
            val folderName = appendDensityQualifier(variant.resourceKind, variant.qualifierSuffix, density)
            val dir = File(outDir, folderName).apply { mkdirs() }
            File(dir, "${variant.flatName}.png").writeBytes(
                GenericUiDefaultIconProvider.renderSvgTextToPngBytes(svgText, wPx, hPx)
            )
        }
    }

    private fun extractAndroidKindAndQualifier(parentDirLower: String): Pair<String, String> = when {
        parentDirLower == "drawable" -> "drawable" to ""
        parentDirLower.startsWith("drawable-") -> "drawable" to parentDirLower.removePrefix("drawable")
        parentDirLower == "mipmap" -> "mipmap" to ""
        parentDirLower.startsWith("mipmap-") -> "mipmap" to parentDirLower.removePrefix("mipmap")
        else -> "drawable" to ""
    }

    private fun appendDensityQualifier(kind: String, qualifierSuffix: String, density: String): String {
        val tokens = qualifierSuffix.split('-')
            .filter { it.isNotEmpty() }
            .filterNot { it == "anydpi" || it == "nodpi" }
            .toMutableList()
        val versionRegex = Regex("^v\\d+$")
        val afterDensityTokens = setOf("notouch", "finger", "keyssoft", "keysexposed", "keyshidden", "qwerty", "12key", "navexposed", "navhidden", "nonav", "dpad", "trackball", "wheel")
        val insertIdx = tokens.indexOfFirst { versionRegex.matches(it) || it in afterDensityTokens }
            .let { if (it < 0) tokens.size else it }
        tokens.add(insertIdx, density)
        return if (tokens.isEmpty()) kind else "$kind-${tokens.joinToString("-")}"
    }

    private fun buildAndroidThemeColorsXml(
        lightColors: Map<String, String>,
        darkColors: Map<String, String>
    ): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<resources>")
        fun emitPalette(prefix: String, map: Map<String, String>, isDark: Boolean) {
            val primary = normalizeAndroidHexColor(map[GenericUiThemeConfig.KEY_PRIMARY] ?: "#0d6efd")
            val primaryDark = darkenAndroidHexColor(primary, 0.80)
            val secondary = normalizeAndroidHexColor(map[GenericUiThemeConfig.KEY_SECONDARY] ?: "#6c757d")
            val accent = normalizeAndroidHexColor(map[GenericUiThemeConfig.KEY_ACCENT] ?: primary)
            val success = normalizeAndroidHexColor(map[GenericUiThemeConfig.KEY_SUCCESS] ?: "#198754")
            val danger = normalizeAndroidHexColor(map[GenericUiThemeConfig.KEY_DANGER] ?: "#dc3545")
            val warning = normalizeAndroidHexColor(map[GenericUiThemeConfig.KEY_WARNING] ?: "#ffc107")
            val info = normalizeAndroidHexColor(map[GenericUiThemeConfig.KEY_INFO] ?: "#0dcaf0")
            val background = normalizeAndroidHexColor(
                map[GenericUiThemeConfig.KEY_BACKGROUND] ?: if (isDark) "#212529" else "#ffffff"
            )
            val surface = normalizeAndroidHexColor(
                map[GenericUiThemeConfig.KEY_SURFACE] ?: if (isDark) "#2b3035" else "#f8f9fa"
            )
            val textPrimary = normalizeAndroidHexColor(
                map[GenericUiThemeConfig.KEY_TEXT_PRIMARY] ?: if (isDark) "#f8f9fa" else "#212529"
            )
            val textSecondary = normalizeAndroidHexColor(
                map[GenericUiThemeConfig.KEY_TEXT_SECONDARY] ?: if (isDark) "#adb5bd" else "#6c757d"
            )
            val border = normalizeAndroidHexColor(
                map[GenericUiThemeConfig.KEY_BORDER] ?: if (isDark) "#495057" else "#dee2e6"
            )
            appendLine("    <color name=\"${prefix}_primary\">${escapeXml(primary)}</color>")
            appendLine("    <color name=\"${prefix}_primary_dark\">${escapeXml(primaryDark)}</color>")
            appendLine("    <color name=\"${prefix}_secondary\">${escapeXml(secondary)}</color>")
            appendLine("    <color name=\"${prefix}_accent\">${escapeXml(accent)}</color>")
            appendLine("    <color name=\"${prefix}_success\">${escapeXml(success)}</color>")
            appendLine("    <color name=\"${prefix}_danger\">${escapeXml(danger)}</color>")
            appendLine("    <color name=\"${prefix}_warning\">${escapeXml(warning)}</color>")
            appendLine("    <color name=\"${prefix}_info\">${escapeXml(info)}</color>")
            appendLine("    <color name=\"${prefix}_background\">${escapeXml(background)}</color>")
            appendLine("    <color name=\"${prefix}_surface\">${escapeXml(surface)}</color>")
            appendLine("    <color name=\"${prefix}_text_primary\">${escapeXml(textPrimary)}</color>")
            appendLine("    <color name=\"${prefix}_text_secondary\">${escapeXml(textSecondary)}</color>")
            appendLine("    <color name=\"${prefix}_border\">${escapeXml(border)}</color>")
        }
        emitPalette("genericui_light", lightColors, isDark = false)
        emitPalette("genericui_dark", darkColors, isDark = true)
        appendLine("</resources>")
    }

    private fun buildAndroidThemesXml(
        parentTheme: String,
        colorPrefix: String
    ): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<resources>")
        appendLine("    <style name=\"Theme.GenericUI.App\" parent=\"${escapeXml(parentTheme)}\">")
        appendLine("        <item name=\"colorPrimary\">@color/${colorPrefix}_primary</item>")
        appendLine("        <item name=\"colorPrimaryDark\">@color/${colorPrefix}_primary_dark</item>")
        appendLine("        <item name=\"colorAccent\">@color/${colorPrefix}_accent</item>")
        appendLine("        <item name=\"colorPrimaryVariant\">@color/${colorPrefix}_primary_dark</item>")
        appendLine("        <item name=\"colorSecondary\">@color/${colorPrefix}_accent</item>")
        appendLine("        <item name=\"android:colorPrimary\">@color/${colorPrefix}_primary</item>")
        appendLine("        <item name=\"android:colorPrimaryDark\">@color/${colorPrefix}_primary_dark</item>")
        appendLine("        <item name=\"android:colorAccent\">@color/${colorPrefix}_accent</item>")
        appendLine("        <item name=\"android:windowBackground\">@color/${colorPrefix}_background</item>")
        appendLine("        <item name=\"android:colorBackground\">@color/${colorPrefix}_background</item>")
        appendLine("        <item name=\"android:textColorPrimary\">@color/${colorPrefix}_text_primary</item>")
        appendLine("        <item name=\"android:textColorSecondary\">@color/${colorPrefix}_text_secondary</item>")
        appendLine("        <item name=\"android:statusBarColor\">@color/${colorPrefix}_primary_dark</item>")
        appendLine("        <item name=\"android:navigationBarColor\">@color/${colorPrefix}_surface</item>")
        appendLine("    </style>")
        appendLine("    <style name=\"Theme.GenericUI.Dialog\" parent=\"Theme.MaterialComponents.DayNight.Dialog\">")
        appendLine("        <item name=\"windowNoTitle\">true</item>")
        appendLine("        <item name=\"android:windowNoTitle\">true</item>")
        appendLine("        <item name=\"android:windowIsFloating\">true</item>")
        appendLine("        <item name=\"android:windowCloseOnTouchOutside\">true</item>")
        appendLine("    </style>")
        appendLine("</resources>")
    }

    private fun normalizeAndroidHexColor(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "#0d6efd"
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun darkenAndroidHexColor(hexColor: String, factor: Double): String {
        val clean = hexColor.trim().removePrefix("#")
        val rgbHex = when (clean.length) {
            3 -> "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}"
            6 -> clean
            8 -> clean.substring(clean.length - 6)
            else -> return hexColor
        }
        val r = rgbHex.substring(0, 2).toIntOrNull(16) ?: return hexColor
        val g = rgbHex.substring(2, 4).toIntOrNull(16) ?: return hexColor
        val b = rgbHex.substring(4, 6).toIntOrNull(16) ?: return hexColor
        val dr = (r * factor).toInt().coerceIn(0, 255)
        val dg = (g * factor).toInt().coerceIn(0, 255)
        val db = (b * factor).toInt().coerceIn(0, 255)
        return "#%02x%02x%02x".format(dr, dg, db)
    }

    private fun resolveExistingIconFileOrNull(projDir: File, rawPath: String?): File? {
        val clean = rawPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val f = File(clean).let { if (it.isAbsolute) it else File(projDir, clean) }
        return f.takeIf { it.exists() && it.isFile }
    }

    private fun emitAppleResourceTranslations(
        projDir: File,
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        raws: List<RawEntry> = emptyList(),
        fonts: List<ValueEntry> = emptyList(),
        xmls: List<RawEntry> = emptyList(),
        styles: List<RawEntry> = emptyList(),
        targetSpecificIconPath: String? = null,
        fullBleedAppIcon: Boolean = false,
        explicitFlagProperty: Boolean? = null
    ) {
        val lproj = File(outDir, "en.lproj").apply { mkdirs() }
        val xcassets = File(outDir, "Assets.xcassets").apply { mkdirs() }
        val appIconSet = File(xcassets, "AppIcon.appiconset").apply { mkdirs() }

        val targetIconFile = resolveExistingIconFileOrNull(projDir, targetSpecificIconPath)
        val sharedAppleIconFile = resolveExistingIconFileOrNull(projDir, appleIconPath.orNull)
        val hasExplicitApplePath = explicitFlagProperty ?: (
            !targetSpecificIconPath.isNullOrBlank() || !appleIconPath.orNull.isNullOrBlank()
        )
        val sourceIconFile = targetIconFile
            ?: sharedAppleIconFile
            ?: resolveProjectIconSourceFile(projDir)
        val wrapInTile = !hasExplicitApplePath

        GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(
            sourceIconFile = sourceIconFile,
            projectRootDir = projDir,
            outputDir = appIconSet,
            fileName = GenericUiDefaultIconProvider.DEFAULT_ICON_PNG_FILE_NAME,
            wrapInAppleTile = wrapInTile,
            fullBleedSquare = fullBleedAppIcon,
            requestedSizeOverride = if (fullBleedAppIcon) 1024 else 512
        )
        GenericUiDefaultIconProvider.ensureAppleIcnsFromSourceOrDefault(
            sourceIconFile = sourceIconFile,
            projectRootDir = projDir,
            outputDir = appIconSet,
            wrapInAppleTile = wrapInTile
        )
        GenericUiDefaultIconProvider.ensureAppleIcnsFromSourceOrDefault(
            sourceIconFile = sourceIconFile,
            projectRootDir = projDir,
            outputDir = outDir,
            wrapInAppleTile = wrapInTile
        )
        val contentsSize = if (fullBleedAppIcon) "1024x1024" else "512x512"
        File(appIconSet, "Contents.json").writeText(
            """
            {
              "images": [{ "idiom": "universal", "filename": "icon.png", "size": "$contentsSize" }],
              "info": { "version": 1, "author": "genericui" }
            }
            """.trimIndent()
        )

        val ls = StringBuilder("// Auto-generated Apple Localizable.strings by GenericUI\n")
        val seenKeys = linkedSetOf<String>()
        for (s in strings) {
            val esc = s.rawValue.replace("\"", "\\\"").replace("\n", "\\n")
            if (seenKeys.add(s.flatName)) {
                ls.appendLine("\"${s.flatName}\" = \"$esc\";")
            }
        }
        File(lproj, "Localizable.strings").writeText(ls.toString())

        val seenImageSets = linkedSetOf<String>()
        for (d in drawables) {
            // Hierarchical single-blob bundle layout (`gui/...`, `gui/ext/...`, `app/...`)
            val relTarget = File(outDir, d.relativePath.trimStart('/'))
            writeDrawablePayload(d, relTarget)

            // Flat Assets.xcassets image set keyed by `flatName`
            if (!seenImageSets.add(d.flatName)) continue
            val imgSet = File(xcassets, "${d.flatName}.imageset").apply { mkdirs() }
            val assetFile = File(imgSet, "${d.flatName}.${d.format}")
            writeDrawablePayload(d, assetFile)
            File(imgSet, "Contents.json").writeText(
                """
                {
                  "images": [{ "idiom": "universal", "filename": "${d.flatName}.${d.format}" }],
                  "info": { "version": 1, "author": "genericui" }
                }
                """.trimIndent()
            )
        }
        for (r in raws) {
            writeRawPayload(r, File(outDir, r.relativePath.trimStart('/')))
        }
        for (f in fonts) {
            val target = File(outDir, f.rawValue.trimStart('/'))
            target.parentFile?.mkdirs()
            if (f.base64Data != null) {
                target.writeBytes(Base64.getDecoder().decode(f.base64Data))
            }
        }
        for (x in xmls) {
            writeRawPayload(x, File(outDir, x.relativePath.trimStart('/')))
        }
        for (st in styles) {
            writeRawPayload(st, File(outDir, st.relativePath.trimStart('/')))
        }

        val seenColorSets = linkedSetOf<String>()
        for (c in colors) {
            if (seenColorSets.add(c.flatName)) {
                val colSet = File(xcassets, "${c.flatName}.colorset").apply { mkdirs() }
                File(colSet, "Contents.json").writeText(
                    """
                    {
                      "colors": [{ "idiom": "universal", "color": { "color-space": "srgb", "hex": "${c.rawValue}" } }],
                      "info": { "version": 1, "author": "genericui" }
                    }
                    """.trimIndent()
                )
            }
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

    private fun emitWebAndSwingResourceTranslations(
        projDir: File,
        outDir: File,
        drawables: List<DrawableEntry>,
        strings: List<ValueEntry>,
        colors: List<ValueEntry>,
        dimens: List<ValueEntry>,
        raws: List<RawEntry>,
        fonts: List<ValueEntry>,
        xmls: List<RawEntry>,
        styles: List<RawEntry> = emptyList()
    ) {
        outDir.mkdirs()
        val sourceIconFile = resolveProjectIconSourceFile(projDir)
        GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(sourceIconFile, projDir, outDir)
        GenericUiDefaultIconProvider.ensureWindowsIcoFromSourceOrDefault(sourceIconFile, projDir, outDir)
        GenericUiDefaultIconProvider.ensureDefaultSvgIcon(projDir, outDir)

        // Hierarchical single-blob staging: 1x per file at `gui/...`, `gui/ext/<extcat>/<ext>/...`, or `app/...`
        val stagedPaths = linkedSetOf<String>()
        fun stageOnce(relPath: String, writer: (File) -> Unit) {
            val cleanRel = relPath.replace('\\', '/').trimStart('/')
            if (cleanRel.isEmpty() || !stagedPaths.add(cleanRel)) return
            writer(File(outDir, cleanRel))
        }

        for (d in drawables) {
            val relPath = d.relativePath.replace('\\', '/').trimStart('/').ifEmpty { "app/drawable/${d.name}.${d.format}" }
            stageOnce(relPath) { writeDrawablePayload(d, it) }
        }
        for (r in raws) {
            val relPath = r.relativePath.replace('\\', '/').trimStart('/').ifEmpty { "app/raw/${r.name}" }
            stageOnce(relPath) { writeRawPayload(r, it) }
        }
        for (f in fonts) {
            val relPath = f.rawValue.replace('\\', '/').trimStart('/').ifEmpty { "app/font/${f.name}.ttf" }
            val b64 = f.base64Data
            if (b64 != null) {
                stageOnce(relPath) { target ->
                    target.parentFile?.mkdirs()
                    target.writeBytes(Base64.getDecoder().decode(b64))
                }
            }
        }
        for (x in xmls) {
            val relPath = x.relativePath.replace('\\', '/').trimStart('/').ifEmpty { "app/xml/${x.name}.xml" }
            stageOnce(relPath) { writeRawPayload(x, it) }
        }
        for (st in styles) {
            val relPath = st.relativePath.replace('\\', '/').trimStart('/').ifEmpty { "app/styles/${st.name}.scss" }
            stageOnce(relPath) { writeRawPayload(st, it) }
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
            val relPath = d.relativePath.replace('\\', '/').trimStart('/').ifEmpty { "app/drawable/${d.name}.${d.format}" }
            json.appendLine("    \"${d.name}\": { \"id\": ${d.numericId}, \"format\": \"${d.format}\", \"path\": ${escapeJsonString(relPath)} }$comma")
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

    private fun extractSvgViewBox(svg: String): Pair<Float, Float> =
        GenericUiBuiltinSvgToVectorConverter.extractViewBoxAndPaths(svg).first

    private fun extractSvgPaths(svg: String): List<String> =
        GenericUiBuiltinSvgToVectorConverter.extractViewBoxAndPaths(svg).second

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

    private fun canonicalAppPath(relPath: String): String {
        val clean = relPath.trim().replace('\\', '/').trimStart('/')
        return if (clean.startsWith("app/") || clean.startsWith("gui/")) clean else "app/$clean"
    }

    companion object {
        internal fun win32IdFor(typeCode: Int, numericId: Int): Int =
            ((typeCode and 0x0F) * 1000) + (numericId and 0x0FFF)

        internal fun canonicalStemOf(
            relativePath: String,
            categoryDir: String,
            name: String,
            scopePrefix: String = "app"
        ): String {
            val clean = relativePath.trim().replace('\\', '/').trimStart('/')
            val normalized = when {
                clean.startsWith("gui/") || clean.startsWith("app/") -> clean
                clean.isNotEmpty() -> "$scopePrefix/$clean"
                else -> "$scopePrefix/$categoryDir/$name"
            }
            val lastSlash = normalized.lastIndexOf('/')
            val lastDot = normalized.lastIndexOf('.')
            return if (lastDot > lastSlash) normalized.substring(0, lastDot) else normalized
        }
    }
}
