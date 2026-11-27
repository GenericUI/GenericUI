package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cacheable Gradle task (`compileGenericUiSass`) that compiles `.scss`, `.sass`, and `.css`
 * stylesheets into normalized `.css` bundles AND precompiled `.kt` (`PrecompiledStyleSheet`)
 * source files so applications have zero runtime SASS/CSS parsing overhead.
 *
 * When building a consumer application, also extracts raw `theme.scss` templates from prebuilt
 * upstream `.jar` / `.klib` dependencies and module manifests (`:common` -> enabled extensions ->
 * optional consumer `theme.scss`), evaluates them with the consumer's `genericui { theme { ... } }`
 * light/dark palette variables and Sass color/math functions, and emits `GenericUiGeneratedThemeStyleSheet`
 * (`:system`) and `genericui-theme.css` (`:web`) exclusively into the consumer application.
 */
@CacheableTask
abstract class GenericUiSassCompileTask : DefaultTask() {

    private data class ParsedRule(
        val selector: String,
        val declarations: Map<String, String>,
        val specificity: Int,
        val mediaMinWidthPx: Int? = null,
        val mediaMaxWidthPx: Int? = null,
        val backendMask: Int = GenericUiBackendStyleMask.ALL
    )

    private data class SassColor(
        val r: Int,
        val g: Int,
        val b: Int,
        val a: Double = 1.0
    )

    private data class SassMixin(
        val params: List<Pair<String, String?>>,
        val body: String
    )

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Input
    abstract val compileSass: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val generateWebCss: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val webCssBundleFileName: Property<String>

    @get:Input
    @get:Optional
    abstract val precompileToKotlin: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val kotlinPackageName: Property<String>

    @get:Input
    abstract val minify: Property<Boolean>

    @get:Input
    abstract val includeBootstrapVariables: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val consumerApplicationMode: Property<Boolean>

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
    abstract val upstreamResourceManifestPaths: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val upstreamResourceClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val spiThemeScssChunks: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val themeCssFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val webCssOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val kotlinOutputDirectory: DirectoryProperty

    init {
        consumerApplicationMode.convention(true)
        lightThemeColors.convention(emptyMap())
        darkThemeColors.convention(emptyMap())
        upstreamResourceManifestPaths.convention(emptyList())
        spiThemeScssChunks.convention(emptyList())
        themeCssFileName.convention("genericui-theme.css")
    }

    @TaskAction
    fun compileStyles() {
        val outDir = outputDirectory.get().asFile
        if (!outDir.exists()) {
            outDir.mkdirs()
        }

        val shouldEmitWebCss = generateWebCss.getOrElse(true)
        val webCssOutDir = webCssOutputDirectory.orNull?.asFile ?: File(outDir.parentFile, "web-css")
        if (shouldEmitWebCss && !webCssOutDir.exists()) {
            webCssOutDir.mkdirs()
        }

        val ktOutDir = kotlinOutputDirectory.orNull?.asFile ?: File(outDir.parentFile, "kotlin")
        val shouldEmitKt = precompileToKotlin.getOrElse(true)
        val pkgName = kotlinPackageName.getOrElse("com.nexustools.genericui.generated.styles").trim()
        val pkgDir = if (pkgName.isNotEmpty()) {
            File(ktOutDir, pkgName.replace('.', '/'))
        } else {
            ktOutDir
        }
        if (shouldEmitKt && !pkgDir.exists()) {
            pkgDir.mkdirs()
        }

        val resolvedPrimary = themeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: GenericUiThemeConfig.DEFAULT_PRIMARY_COLOR
        val resolvedLightColors = GenericUiThemeConfig.defaultLightColors(resolvedPrimary).toMutableMap().apply {
            putAll(lightThemeColors.getOrElse(emptyMap()).filterValues { it.isNotBlank() })
        }
        val resolvedDarkColors = GenericUiThemeConfig.defaultDarkColors(resolvedPrimary).toMutableMap().apply {
            putAll(darkThemeColors.getOrElse(emptyMap()).filterValues { it.isNotBlank() })
        }
        val resolvedMode = themeMode.orNull?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: "auto"
        val defaultActivePalette = if (resolvedMode == "dark") resolvedDarkColors else resolvedLightColors
        val defaultSassVars = buildThemeScssVariables(
            activePalette = defaultActivePalette,
            lightPalette = resolvedLightColors,
            darkPalette = resolvedDarkColors,
            modeName = resolvedMode
        )

        val compiledFiles = mutableListOf<String>()
        val webCssFiles = mutableListOf<String>()
        val combinedWebCssChunks = mutableListOf<String>()
        val generatedKotlinFiles = mutableListOf<String>()
        val consumerThemeChunks = mutableListOf<String>()
        val bootstrapHeader = if (includeBootstrapVariables.getOrElse(true)) {
            buildBootstrapVariablesBlock()
        } else {
            ""
        }

        val srcDir = sourceDirectory.orNull?.asFile
        val consumerAppMode = consumerApplicationMode.getOrElse(true)
        var combinedResourceIndex = LocalResourceIndex()

        if (compileSass.getOrElse(true) && srcDir != null && srcDir.exists()) {
            val resourceIndex = discoverLocalResources(srcDir)
            combinedResourceIndex = resourceIndex
            srcDir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith("_") }
                .filter { it.extension.lowercase() in setOf("scss", "sass", "css") }
                .sortedBy { it.relativeTo(srcDir).invariantSeparatorsPath }
                .forEach { file ->
                    val rawContent = resolveSassImports(file, mutableSetOf(file.canonicalPath))
                    if (file.nameWithoutExtension.equals("theme", ignoreCase = true)) {
                        if (consumerAppMode && rawContent.isNotBlank()) {
                            val relDisplay = file.relativeTo(srcDir).invariantSeparatorsPath
                            consumerThemeChunks.add("/* Consumer Theme: $relDisplay */\n$rawContent")
                        }
                        return@forEach
                    }

                    val relativePath = file.relativeTo(srcDir).path
                    val targetRelativePath = relativePath.replace(Regex("\\.(scss|sass)$", RegexOption.IGNORE_CASE), ".css")
                    val normalizedRelPath = targetRelativePath.replace('\\', '/')
                    val targetFile = File(outDir, targetRelativePath)
                    targetFile.parentFile?.mkdirs()

                    val nativeResolvedContent = resolveResourceTokens(
                        source = bootstrapHeader + "\n" + rawContent,
                        forWeb = false,
                        resources = resourceIndex
                    )
                    val processedCss = transformSassToCss(
                        source = nativeResolvedContent,
                        minifyOutput = minify.getOrElse(false),
                        initialVariables = defaultSassVars,
                        targetBackendMask = GenericUiBackendStyleMask.SYSTEM
                    )
                    targetFile.writeText(processedCss)
                    compiledFiles.add(normalizedRelPath)

                    if (shouldEmitWebCss) {
                        val webResolvedContent = resolveResourceTokens(
                            source = bootstrapHeader + "\n" + rawContent,
                            forWeb = true,
                            resources = resourceIndex
                        )
                        val webProcessedCss = transformSassToCss(
                            source = webResolvedContent,
                            minifyOutput = minify.getOrElse(false),
                            initialVariables = defaultSassVars,
                            targetBackendMask = GenericUiBackendStyleMask.WEB
                        )
                        val webTargetFile = File(webCssOutDir, targetRelativePath)
                        webTargetFile.parentFile?.mkdirs()
                        webTargetFile.writeText(webProcessedCss)
                        if (normalizedRelPath !in webCssFiles) {
                            webCssFiles.add(normalizedRelPath)
                        }

                        // Also emit at the root of webCssOutDir (e.g., "styles/app.scss" -> "app.css" as well as "styles/app.css")
                        // so both `<link href="app.css">` and `<link href="styles/app.css">` work seamlessly on the Web target.
                        val rootFileName = "${file.nameWithoutExtension}.css"
                        if (normalizedRelPath != rootFileName) {
                            val rootWebCssFile = File(webCssOutDir, rootFileName)
                            if (!rootWebCssFile.exists()) {
                                rootWebCssFile.writeText(webProcessedCss)
                                if (rootFileName !in webCssFiles) {
                                    webCssFiles.add(rootFileName)
                                }
                            }
                        }
                        combinedWebCssChunks.add("/* Source: $normalizedRelPath */\n$webProcessedCss")
                    }

                    if (shouldEmitKt) {
                        val baseName = file.nameWithoutExtension
                        val objectName = toPascalIdentifier(baseName) + "PrecompiledStyleSheet"
                        val ktFile = File(pkgDir, "$objectName.kt")
                        val ktSource = generatePrecompiledKotlinSource(
                            cssText = processedCss,
                            packageName = pkgName,
                            objectName = objectName,
                            stylesheetName = baseName,
                            cssResourcePath = normalizedRelPath
                        )
                        ktFile.writeText(ktSource)
                        generatedKotlinFiles.add(ktFile.relativeTo(ktOutDir).path.replace('\\', '/'))
                    }
                }
        }

        val themeKtFile = File(pkgDir, GENERATED_THEME_STYLESHEET_OBJECT_NAME + ".kt")
        val themeWebFileName = themeCssFileName.getOrElse("genericui-theme.css").trim().ifEmpty { "genericui-theme.css" }
        val upstreamSources = upstreamResourceManifestPaths.getOrElse(emptyList())
            .map(::File) + upstreamResourceClasspath.files
        val upstreamTemplates = if (consumerAppMode) {
            GenericUiModuleResourceManifestParser.readThemeTemplatesFromSources(upstreamSources)
        } else {
            emptyList()
        }
        val spiTemplates = if (consumerAppMode) {
            spiThemeScssChunks.getOrElse(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        val allThemeChunks = buildList {
            for (tpl in upstreamTemplates) {
                add("/* Upstream Theme Template: ${tpl.scopeId} */\n${tpl.rawScss}")
            }
            for ((idx, chunk) in spiTemplates.withIndex()) {
                add("/* Extension SPI Theme #$idx */\n$chunk")
            }
            addAll(consumerThemeChunks)
        }

        if (consumerAppMode && compileSass.getOrElse(true) && allThemeChunks.isNotEmpty()) {
            val combinedRawThemeScss = allThemeChunks.joinToString("\n\n")
            val nativeThemeSource = resolveResourceTokens(
                source = combinedRawThemeScss,
                forWeb = false,
                resources = combinedResourceIndex
            )
            val compiledNativeThemeCss = compileThemedScssForLightAndDarkStatic(
                source = nativeThemeSource,
                lightPalette = resolvedLightColors,
                darkPalette = resolvedDarkColors,
                themeMode = resolvedMode,
                minifyOutput = minify.getOrElse(false),
                targetBackendMask = GenericUiBackendStyleMask.SYSTEM
            )
            val themeOutFile = File(outDir, themeWebFileName)
            themeOutFile.parentFile?.mkdirs()
            themeOutFile.writeText(compiledNativeThemeCss)
            if (themeWebFileName !in compiledFiles) {
                compiledFiles.add(themeWebFileName)
            }

            if (shouldEmitWebCss) {
                val webThemeSource = resolveResourceTokens(
                    source = combinedRawThemeScss,
                    forWeb = true,
                    resources = combinedResourceIndex
                )
                val compiledWebThemeCss = compileThemedScssForLightAndDarkStatic(
                    source = webThemeSource,
                    lightPalette = resolvedLightColors,
                    darkPalette = resolvedDarkColors,
                    themeMode = resolvedMode,
                    minifyOutput = minify.getOrElse(false),
                    targetBackendMask = GenericUiBackendStyleMask.WEB
                )
                val webThemeFile = File(webCssOutDir, themeWebFileName)
                webThemeFile.parentFile?.mkdirs()
                webThemeFile.writeText(compiledWebThemeCss)
                if (themeWebFileName !in webCssFiles) {
                    webCssFiles.add(themeWebFileName)
                }
            }

            if (shouldEmitKt) {
                val ktSource = generatePrecompiledKotlinSource(
                    cssText = compiledNativeThemeCss,
                    packageName = pkgName,
                    objectName = GENERATED_THEME_STYLESHEET_OBJECT_NAME,
                    stylesheetName = "genericui-theme",
                    cssResourcePath = "gui/$themeWebFileName",
                    isThemeStyleSheet = true
                )
                themeKtFile.writeText(ktSource)
                generatedKotlinFiles.add(themeKtFile.relativeTo(ktOutDir).path.replace('\\', '/'))
            }
        } else if (themeKtFile.exists()) {
            themeKtFile.delete()
        }

        if (compiledFiles.isEmpty() && bootstrapHeader.isNotEmpty()) {
            val defaultThemeFile = File(outDir, "genericui-bootstrap-defaults.css")
            val css = transformSassToCss(
                bootstrapHeader,
                minify.getOrElse(false),
                defaultSassVars,
                targetBackendMask = GenericUiBackendStyleMask.SYSTEM
            )
            defaultThemeFile.writeText(css)
            compiledFiles.add("genericui-bootstrap-defaults.css")

            if (shouldEmitWebCss) {
                val webCss = transformSassToCss(
                    bootstrapHeader,
                    minify.getOrElse(false),
                    defaultSassVars,
                    targetBackendMask = GenericUiBackendStyleMask.WEB
                )
                val webDefaultThemeFile = File(webCssOutDir, "genericui-bootstrap-defaults.css")
                webDefaultThemeFile.parentFile?.mkdirs()
                webDefaultThemeFile.writeText(webCss)
                webCssFiles.add("genericui-bootstrap-defaults.css")
                combinedWebCssChunks.add(webCss)
            }

            if (shouldEmitKt) {
                val objectName = "GenericUiBootstrapDefaultsPrecompiledStyleSheet"
                val ktFile = File(pkgDir, "$objectName.kt")
                val ktSource = generatePrecompiledKotlinSource(
                    cssText = css,
                    packageName = pkgName,
                    objectName = objectName,
                    stylesheetName = "genericui-bootstrap-defaults",
                    cssResourcePath = "genericui-bootstrap-defaults.css"
                )
                ktFile.writeText(ktSource)
                generatedKotlinFiles.add(ktFile.relativeTo(ktOutDir).path.replace('\\', '/'))
            }
        }

        val bundleFileName = webCssBundleFileName.getOrElse("").trim()
        if (shouldEmitWebCss && bundleFileName.isNotEmpty() && combinedWebCssChunks.isNotEmpty()) {
            val bundleFile = File(webCssOutDir, bundleFileName)
            bundleFile.parentFile?.mkdirs()
            bundleFile.writeText(combinedWebCssChunks.joinToString("\n"))
            if (bundleFileName !in webCssFiles) {
                webCssFiles.add(bundleFileName)
            }
        }

        val filesJson = compiledFiles.joinToString(", ") { "\"$it\"" }
        val webFilesJson = webCssFiles.joinToString(", ") { "\"$it\"" }
        val ktFilesJson = generatedKotlinFiles.joinToString(", ") { "\"$it\"" }
        File(outDir, "styles-manifest.json").writeText(
            """
            |{
            |  "compileSass": ${compileSass.getOrElse(true)},
            |  "generateWebCss": $shouldEmitWebCss,
            |  "precompileToKotlin": $shouldEmitKt,
            |  "kotlinPackageName": "$pkgName",
            |  "minified": ${minify.getOrElse(false)},
            |  "includeBootstrapVariables": ${includeBootstrapVariables.getOrElse(true)},
            |  "bundles": [$filesJson],
            |  "webCssFiles": [$webFilesJson],
            |  "kotlinSources": [$ktFilesJson]
            |}
            """.trimMargin() + "\n"
        )
    }

    private fun resolveSassImports(file: File, visited: MutableSet<String>): String =
        Companion.resolveSassImportsStatic(file, visited)

    internal fun transformSassToCss(
        source: String,
        minifyOutput: Boolean,
        initialVariables: Map<String, String> = emptyMap(),
        targetBackendMask: Int? = null
    ): String = Companion.transformSassSourceToCssStatic(source, minifyOutput, initialVariables, targetBackendMask)

    internal fun compileRulesToCss(cssOrSelectors: String): String =
        Companion.compileRulesToCssStatic(cssOrSelectors)

    internal data class LocalResourceIndex(
        val colors: Map<String, String> = emptyMap(),
        val dimens: Map<String, String> = emptyMap(),
        val strings: Map<String, String> = emptyMap(),
        val drawables: Map<String, String> = emptyMap(),
        val fonts: Map<String, String> = emptyMap()
    )

    internal fun discoverLocalResources(stylesDir: File): LocalResourceIndex {
        val candidateRoots = buildList {
            add(stylesDir)
            stylesDir.parentFile?.let { add(it) }
            stylesDir.parentFile?.parentFile?.let { parent2 ->
                File(parent2, "resources").takeIf { it.isDirectory }?.let { add(it) }
            }
        }.distinctBy { it.canonicalPath }

        val colors = linkedMapOf<String, String>()
        val dimens = linkedMapOf<String, String>()
        val strings = linkedMapOf<String, String>()
        val drawables = linkedMapOf<String, String>()
        val fonts = linkedMapOf<String, String>()

        val xmlTagRegex = Regex("<(string|color|dimen)\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>([\\s\\S]*?)</\\1>")

        for (root in candidateRoots) {
            if (!root.exists() || !root.isDirectory) continue
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val parentName = file.parentFile?.name?.lowercase() ?: ""
                val ext = file.extension.lowercase()
                val ident = sanitizeResId(file.nameWithoutExtension)
                when {
                    parentName.startsWith("values") && ext == "xml" -> {
                        for (match in xmlTagRegex.findAll(file.readText())) {
                            val tag = match.groupValues[1]
                            val name = sanitizeResId(match.groupValues[2])
                            val rawVal = match.groupValues[3].trim()
                            if (name.isNotEmpty() && rawVal.isNotEmpty()) {
                                when (tag) {
                                    "color" -> colors.putIfAbsent(name, rawVal)
                                    "dimen" -> dimens.putIfAbsent(name, normalizeCssDimen(rawVal))
                                    "string" -> strings.putIfAbsent(name, rawVal)
                                }
                            }
                        }
                    }
                    (parentName.startsWith("drawable") || parentName in setOf("icons", "images", "cursors", "cursor")) &&
                        ext in setOf("svg", "png", "cur", "ico", "webp", "jpg", "jpeg") -> {
                        if (ident.isNotEmpty()) {
                            val relInRoot = file.relativeTo(root).invariantSeparatorsPath.trimStart('/')
                            val canonicalPath = if (relInRoot.startsWith("gui/") || relInRoot.startsWith("app/")) {
                                relInRoot
                            } else {
                                "app/$relInRoot"
                            }
                            drawables.putIfAbsent(ident, canonicalPath)
                        }
                    }
                    (parentName == "font" || parentName == "fonts") && ext in setOf("ttf", "otf", "woff", "woff2") -> {
                        if (ident.isNotEmpty()) {
                            fonts.putIfAbsent(ident, file.nameWithoutExtension)
                        }
                    }
                }
            }
        }
        return LocalResourceIndex(colors, dimens, strings, drawables, fonts)
    }

    internal fun resolveResourceTokens(
        source: String,
        forWeb: Boolean = false,
        resources: LocalResourceIndex = LocalResourceIndex()
    ): String {
        var resolved = source

        // 1. Resolve url("@drawable/<name>") / url("res://drawable/<name>") / url(@drawable/<name>)
        val urlDrawableRegex = Regex("""url\(\s*(['"]?)(?:@drawable/|res://drawable/)([a-zA-Z0-9_.-]+)\1\s*\)""")
        resolved = urlDrawableRegex.replace(resolved) { m ->
            val rawName = m.groupValues[2]
            val id = sanitizeResId(rawName.substringBeforeLast('.', rawName))
            if (forWeb) {
                val resolvedPath = resources.drawables[id] ?: ("app/drawable/" + if ('.' in rawName) rawName else "$id.svg")
                "url(\"$resolvedPath\")"
            } else {
                "url(\"res://drawable/$id\")"
            }
        }

        // 2. Resolve bare @drawable/<name>
        val bareDrawableRegex = Regex("""@drawable/([a-zA-Z0-9_-]+)""")
        resolved = bareDrawableRegex.replace(resolved) { m ->
            val id = sanitizeResId(m.groupValues[1])
            if (forWeb) {
                val resolvedPath = resources.drawables[id] ?: "app/drawable/$id.svg"
                "url(\"$resolvedPath\")"
            } else {
                "res://drawable/$id"
            }
        }

        // 3. Resolve @color/<name> and res://color/<name>
        val colorTokenRegex = Regex("""(?:@color/|res://color/)([a-zA-Z0-9_-]+)""")
        resolved = colorTokenRegex.replace(resolved) { m ->
            val id = sanitizeResId(m.groupValues[1])
            val resolvedColor = resources.colors[id]
            when {
                resolvedColor != null -> resolvedColor
                forWeb -> "var(--rc-color-${id.replace('_', '-')})"
                else -> "res://color/$id"
            }
        }

        // 4. Resolve @dimen/<name> and res://dimen/<name>
        val dimenTokenRegex = Regex("""(?:@dimen/|res://dimen/)([a-zA-Z0-9_-]+)""")
        resolved = dimenTokenRegex.replace(resolved) { m ->
            val id = sanitizeResId(m.groupValues[1])
            val resolvedDimen = resources.dimens[id]
            when {
                resolvedDimen != null -> resolvedDimen
                forWeb -> "var(--rc-dimen-${id.replace('_', '-')})"
                else -> "res://dimen/$id"
            }
        }

        // 5. Resolve @font/<name> and res://font/<name>
        val fontTokenRegex = Regex("""(?:@font/|res://font/)([a-zA-Z0-9_-]+)""")
        resolved = fontTokenRegex.replace(resolved) { m ->
            val id = sanitizeResId(m.groupValues[1])
            resources.fonts[id] ?: id
        }

        // 6. Resolve @string/<name> and res://string/<name>
        val stringTokenRegex = Regex("""(?:@string/|res://string/)([a-zA-Z0-9_-]+)""")
        resolved = stringTokenRegex.replace(resolved) { m ->
            val id = sanitizeResId(m.groupValues[1])
            val strVal = resources.strings[id] ?: id
            "\"${strVal.replace("\"", "\\\"")}\""
        }

        return resolved
    }

    private fun normalizeCssDimen(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.endsWith("dp", ignoreCase = true) -> trimmed.dropLast(2).trim() + "px"
            trimmed.endsWith("sp", ignoreCase = true) -> trimmed.dropLast(2).trim() + "px"
            trimmed.endsWith("dip", ignoreCase = true) -> trimmed.dropLast(3).trim() + "px"
            else -> trimmed
        }
    }

    private fun sanitizeResId(raw: String): String =
        raw.trim().replace('-', '_').replace('.', '_').lowercase()

    internal fun generatePrecompiledKotlinSource(
        cssText: String,
        packageName: String,
        objectName: String,
        stylesheetName: String,
        cssResourcePath: String = "$stylesheetName.css",
        isThemeStyleSheet: Boolean = false
    ): String {
        val normalizedCss = resolveResourceTokens(cssText, forWeb = false)
        val (rawRules, keyframes) = parseCssRulesAndKeyframes(normalizedCss)
        val sortedRules = rawRules
            .filter { (it.backendMask and GenericUiBackendStyleMask.SYSTEM) != 0 }
            .sortedBy { it.specificity }

        return buildString {
            if (packageName.isNotBlank()) {
                appendLine("package $packageName")
                appendLine()
            }
            appendLine("import com.nexustools.genericui.component.shell.IApplication")
            appendLine("import com.nexustools.genericui.enums.bootstrap.*")
            appendLine("import com.nexustools.genericui.enums.css.*")
            appendLine("import com.nexustools.genericui.enums.ui.ObjectFit")
            appendLine("import com.nexustools.genericui.style.color.RgbaColor")
            appendLine("import com.nexustools.genericui.style.css.*")
            appendLine("import com.nexustools.genericui.style.gradient.Gradient.GradientStop")
            appendLine("import com.nexustools.genericui.style.gradient.Gradient.LinearGradient")
            if (isThemeStyleSheet) {
                appendLine("import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry")
            }
            appendLine("import com.nexustools.genericui.style.transition.CssTransform2D")
            appendLine("import com.nexustools.genericui.style.transition.CssTransitionSpec")
            appendLine("import com.nexustools.genericui.system.style.*")
            appendLine()
            appendLine("/**")
            appendLine(" * Auto-generated precompiled GenericUI Blink stylesheet for `$stylesheetName`.")
            appendLine(" * Pre-parsed at build time by `compileGenericUiSass` with zero runtime CSS parsing.")
            appendLine(" */")
            appendLine("object $objectName : BlinkStyleSheet() {")
            appendLine("    override val name: String = ${escapeKotlinString(stylesheetName)}")
            appendLine()
            appendLine("    override val cssResourcePath: String = ${escapeKotlinString(cssResourcePath)}")
            appendLine()
            appendLine("    override val rawCss: String = \"\"")
            appendLine()
            if (sortedRules.isEmpty()) {
                appendLine("    override val compiledRules: List<CompiledStyleRule> = emptyList()")
            } else {
                val ruleChunks = sortedRules.withIndex().toList().chunked(20)
                appendLine("    override val compiledRules: List<CompiledStyleRule> = buildCompiledRules()")
                appendLine()
                appendLine("    private fun buildCompiledRules(): List<CompiledStyleRule> {")
                appendLine("        val result = ArrayList<CompiledStyleRule>(${sortedRules.size})")
                for (chunkIdx in ruleChunks.indices) {
                    appendLine("        result.addAll(buildCompiledRulesChunk$chunkIdx())")
                }
                appendLine("        return result")
                appendLine("    }")
                for ((chunkIdx, chunk) in ruleChunks.withIndex()) {
                    appendLine()
                    appendLine("    private fun buildCompiledRulesChunk$chunkIdx(): List<CompiledStyleRule> = listOf(")
                    for ((localIdx, indexedRule) in chunk.withIndex()) {
                        val ruleIdx = indexedRule.index
                        val rule = indexedRule.value
                        val comma = if (localIdx < chunk.lastIndex) "," else ""
                        val parsedSegments = parseSelectorSegmentsAtBuildTime(rule.selector)
                        val subjectPseudo = parsedSegments.lastOrNull()?.pseudoClasses
                            ?.firstOrNull { !it.startsWith("not(", ignoreCase = true) }
                        appendLine("        CompiledStyleRule(")
                        appendLine("            selector = ${escapeKotlinString(rule.selector)},")
                        if (parsedSegments.isEmpty()) {
                            appendLine("            segments = emptyList(),")
                        } else {
                            appendLine("            segments = listOf(")
                            for ((segIdx, seg) in parsedSegments.withIndex()) {
                                val segComma = if (segIdx < parsedSegments.lastIndex) "," else ""
                                val tagExpr = seg.tagName?.let { escapeKotlinString(it) } ?: "null"
                                val idExpr = seg.id?.let { escapeKotlinString(it) } ?: "null"
                                val classesExpr = if (seg.classes.isEmpty()) {
                                    "emptySet()"
                                } else {
                                    "setOf(${seg.classes.joinToString(", ") { escapeKotlinString(it) }})"
                                }
                                val pseudosExpr = if (seg.pseudoClasses.isEmpty()) {
                                    "emptySet()"
                                } else {
                                    "setOf(${seg.pseudoClasses.joinToString(", ") { escapeKotlinString(it) }})"
                                }
                                val combExpr = seg.combinatorToLeft?.let { "CssCombinator.$it" } ?: "null"
                                val nextCombExpr = parsedSegments.getOrNull(segIdx + 1)?.combinatorToLeft
                                    ?.let { "CssCombinator.$it" } ?: "null"
                                val exclClassesArg = if (seg.excludedClasses.isEmpty()) {
                                    ""
                                } else {
                                    ", excludedClasses = setOf(${seg.excludedClasses.joinToString(", ") { escapeKotlinString(it) }})"
                                }
                                val exclPseudosArg = if (seg.excludedPseudoClasses.isEmpty()) {
                                    ""
                                } else {
                                    ", excludedPseudoClasses = setOf(${seg.excludedPseudoClasses.joinToString(", ") { escapeKotlinString(it) }})"
                                }
                                val exclTagsArg = if (seg.excludedTags.isEmpty()) {
                                    ""
                                } else {
                                    ", excludedTags = setOf(${seg.excludedTags.joinToString(", ") { escapeKotlinString(it) }})"
                                }
                                val exclIdsArg = if (seg.excludedIds.isEmpty()) {
                                    ""
                                } else {
                                    ", excludedIds = setOf(${seg.excludedIds.joinToString(", ") { escapeKotlinString(it) }})"
                                }
                                appendLine(
                                    "                CompiledSelectorSegment(tagName = $tagExpr, id = $idExpr, classes = $classesExpr, " +
                                        "pseudoClasses = $pseudosExpr, combinatorToLeft = $combExpr, combinatorToNext = $nextCombExpr" +
                                        "$exclClassesArg$exclPseudosArg$exclTagsArg$exclIdsArg)$segComma"
                                )
                            }
                            appendLine("            ),")
                        }
                        appendLine("            style = ComponentStyle().apply {")
                        for ((prop, rawVal) in rule.declarations) {
                            val isImp = rawVal.contains("!important", ignoreCase = true)
                            val cleanVal = rawVal.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "").trim()
                            for (stmt in emitTypedComponentStyleStatements(prop, cleanVal, isImp)) {
                                appendLine("                $stmt")
                            }
                        }
                        appendLine("            },")
                        val declPairs = rule.declarations.entries.joinToString(", ") { (k, v) ->
                            val cleanedDeclVal = v.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "").trim()
                            "${escapeKotlinString(k)} to ${escapeKotlinString(cleanedDeclVal)}"
                        }
                        val declsExpr = if (declPairs.isEmpty()) "emptyMap()" else "mapOf($declPairs)"
                        appendLine("            declarations = $declsExpr,")
                        appendLine("            specificity = ${rule.specificity},")
                        appendLine("            sourceOrder = $ruleIdx,")
                        appendLine("            mediaMinWidthPx = ${rule.mediaMinWidthPx ?: "null"},")
                        appendLine("            mediaMaxWidthPx = ${rule.mediaMaxWidthPx ?: "null"},")
                        if (rule.backendMask != GenericUiBackendStyleMask.ALL) {
                            appendLine("            backendMask = ${GenericUiBackendStyleMask.formatKotlinExpr(rule.backendMask)},")
                        }
                        appendLine("            pseudoClass = ${subjectPseudo?.let { escapeKotlinString(it) } ?: "null"}")
                        appendLine("        )$comma")
                    }
                    appendLine("    )")
                }
            }
            appendLine()
            if (keyframes.isEmpty()) {
                appendLine("    override val keyframes: Map<String, Map<String, Map<String, String>>> = emptyMap()")
            } else {
                val kfChunks = keyframes.entries.toList().chunked(16)
                appendLine("    override val keyframes: Map<String, Map<String, Map<String, String>>> = buildKeyframes()")
                appendLine()
                appendLine("    private fun buildKeyframes(): Map<String, Map<String, Map<String, String>>> {")
                appendLine("        val result = linkedMapOf<String, Map<String, Map<String, String>>>()")
                for (chunkIdx in kfChunks.indices) {
                    appendLine("        result.putAll(buildKeyframesChunk$chunkIdx())")
                }
                appendLine("        return result")
                appendLine("    }")
                for ((chunkIdx, kfEntries) in kfChunks.withIndex()) {
                    appendLine()
                    appendLine("    private fun buildKeyframesChunk$chunkIdx(): Map<String, Map<String, Map<String, String>>> = mapOf(")
                    for ((kfIdx, kfEntry) in kfEntries.withIndex()) {
                        val kfComma = if (kfIdx < kfEntries.lastIndex) "," else ""
                        appendLine("        ${escapeKotlinString(kfEntry.key)} to mapOf(")
                        val stepEntries = kfEntry.value.entries.toList()
                        for ((stepIdx, stepEntry) in stepEntries.withIndex()) {
                            val stepComma = if (stepIdx < stepEntries.lastIndex) "," else ""
                            val declPairs = stepEntry.value.entries.joinToString(", ") {
                                "${escapeKotlinString(it.key)} to ${escapeKotlinString(it.value)}"
                            }
                            val declMapExpr = if (declPairs.isEmpty()) "emptyMap()" else "mapOf($declPairs)"
                            appendLine("            ${escapeKotlinString(stepEntry.key)} to $declMapExpr$stepComma")
                        }
                        appendLine("        )$kfComma")
                    }
                    appendLine("    )")
                }
            }
            appendLine()
            appendLine("    override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)")
            appendLine()
            appendLine("    private val cachedCssStyleSheet: CssStyleSheet by lazy {")
            appendLine("        CssStyleSheet(")
            appendLine("            rules = compiledRules.map { it.toCssRule() },")
            appendLine("            keyframes = keyframes,")
            appendLine("            isPrecompiled = true")
            appendLine("        )")
            appendLine("    }")
            appendLine()
            appendLine("    fun toCssStyleSheet(): CssStyleSheet = cachedCssStyleSheet")
            appendLine()
            appendLine("    fun install(app: IApplication) {")
            appendLine("        BlinkStyleEngine.registerStyleSheet(this)")
            appendLine("        StyleSheetRegistry.register(name, cssResourcePath, this)")
            if (isThemeStyleSheet) {
                appendLine("        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(name, toCssStyleSheet())")
            }
            appendLine("        attachTo(app)")
            appendLine("    }")
            appendLine()
            appendLine("    init {")
            appendLine("        BlinkStyleEngine.registerStyleSheet(this)")
            appendLine("        StyleSheetRegistry.register(name, cssResourcePath, this)")
            if (isThemeStyleSheet) {
                appendLine("        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(name, toCssStyleSheet())")
            }
            appendLine("    }")
            appendLine("}")
        }
    }

    private data class BuildTimeSelectorSegment(
        val tagName: String?,
        val id: String?,
        val classes: List<String>,
        val pseudoClasses: List<String>,
        val combinatorToLeft: String?,
        val excludedClasses: List<String> = emptyList(),
        val excludedPseudoClasses: List<String> = emptyList(),
        val excludedTags: List<String> = emptyList(),
        val excludedIds: List<String> = emptyList()
    )

    private fun sanitizeSelectorOrNull(rawSelector: String): String? =
        Companion.sanitizeSelectorOrNull(rawSelector)

    private fun parseSelectorSegmentsAtBuildTime(selector: String): List<BuildTimeSelectorSegment> {
        val sanitized = sanitizeSelectorOrNull(selector) ?: return emptyList()
        val rawTokens = tokenizeSelectorChain(sanitized)
        if (rawTokens.isEmpty()) return emptyList()

        val result = mutableListOf<BuildTimeSelectorSegment>()
        var pendingCombinator: String? = null
        for (tok in rawTokens) {
            when (tok) {
                ">" -> pendingCombinator = "CHILD"
                "+" -> pendingCombinator = "ADJACENT_SIBLING"
                "~" -> pendingCombinator = "GENERAL_SIBLING"
                else -> {
                    val comb = if (result.isEmpty()) null else (pendingCombinator ?: "DESCENDANT")
                    pendingCombinator = null
                    result.add(parseSingleSelectorAtom(tok, comb))
                }
            }
        }
        return result
    }

    private fun tokenizeSelectorChain(selector: String): List<String> {
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        var parenDepth = 0
        var bracketDepth = 0
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < selector.length) {
            val ch = selector[i]
            if (inSingle) {
                cur.append(ch)
                if (ch == '\\' && i + 1 < selector.length) {
                    cur.append(selector[++i])
                } else if (ch == '\'') {
                    inSingle = false
                }
                i++
                continue
            }
            if (inDouble) {
                cur.append(ch)
                if (ch == '\\' && i + 1 < selector.length) {
                    cur.append(selector[++i])
                } else if (ch == '"') {
                    inDouble = false
                }
                i++
                continue
            }
            when (ch) {
                '\'' -> {
                    inSingle = true
                    cur.append(ch)
                }
                '"' -> {
                    inDouble = true
                    cur.append(ch)
                }
                '(' -> {
                    parenDepth++
                    cur.append(ch)
                }
                ')' -> {
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                    cur.append(ch)
                }
                '[' -> {
                    bracketDepth++
                    cur.append(ch)
                }
                ']' -> {
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                    cur.append(ch)
                }
                '>', '+', '~' -> {
                    if (parenDepth == 0 && bracketDepth == 0) {
                        if (cur.isNotEmpty()) {
                            tokens.add(cur.toString())
                            cur.clear()
                        }
                        tokens.add(ch.toString())
                    } else {
                        cur.append(ch)
                    }
                }
                else -> {
                    if (ch.isWhitespace() && parenDepth == 0 && bracketDepth == 0) {
                        if (cur.isNotEmpty()) {
                            tokens.add(cur.toString())
                            cur.clear()
                        }
                    } else {
                        cur.append(ch)
                    }
                }
            }
            i++
        }
        if (cur.isNotEmpty()) {
            tokens.add(cur.toString())
        }
        return tokens
    }

    private fun parseSingleSelectorAtom(rawToken: String, combinatorToLeft: String?): BuildTimeSelectorSegment {
        val pseudos = mutableListOf<String>()
        val excludedClasses = mutableListOf<String>()
        val excludedPseudos = mutableListOf<String>()
        val excludedTags = mutableListOf<String>()
        val excludedIds = mutableListOf<String>()
        val nsSentinel = "\u0001GUINS\u0001"
        val protectedToken = rawToken.trim()
            .replace("\\:", nsSentinel)
            .replace(Regex("\\bgenericui:", RegexOption.IGNORE_CASE), "genericui$nsSentinel")

        val strippedBuilder = StringBuilder()
        var idx = 0
        while (idx < protectedToken.length) {
            if (idx + 5 <= protectedToken.length &&
                protectedToken.substring(idx, idx + 5).equals(":not(", ignoreCase = true)
            ) {
                var depth = 1
                var end = idx + 5
                while (end < protectedToken.length && depth > 0) {
                    when (protectedToken[end]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    end++
                }
                val innerEnd = if (depth == 0) end - 1 else protectedToken.length
                val inner = protectedToken.substring(idx + 5, innerEnd)
                for (arg in splitTopLevelCommas(inner)) {
                    var argWork = arg.trim()
                    if (argWork.isEmpty()) continue
                    if (Regex("\\[\\s*(?:disabled(?:\\s*=[^\\]]*)?|aria-disabled\\s*=\\s*[\"']?true[\"']?)\\s*\\]", RegexOption.IGNORE_CASE)
                            .containsMatchIn(argWork) && "disabled" !in excludedPseudos
                    ) {
                        excludedPseudos.add("disabled")
                    }
                    argWork = argWork.replace(Regex("\\[[^\\]]*]"), "")
                    val exclPseudoRegex = Regex(":(hover|focus-visible|focus-within|focus|active|disabled|checked|enabled)", RegexOption.IGNORE_CASE)
                    for (m in exclPseudoRegex.findAll(argWork)) {
                        val name = m.groupValues[1].lowercase()
                        if (name !in excludedPseudos) excludedPseudos.add(name)
                    }
                    argWork = argWork.replace(exclPseudoRegex, "")
                    for (m in Regex("#([a-zA-Z0-9_-]+)").findAll(argWork)) {
                        val exclId = m.groupValues[1]
                        if (exclId !in excludedIds) excludedIds.add(exclId)
                    }
                    argWork = argWork.replace(Regex("#[a-zA-Z0-9_-]+"), "")
                    for (m in Regex("\\.([a-zA-Z0-9_-]+)").findAll(argWork)) {
                        val exclCls = m.groupValues[1]
                        if (exclCls !in excludedClasses) excludedClasses.add(exclCls)
                    }
                    val exclTag = argWork
                        .replace(Regex("\\.[a-zA-Z0-9_-]+"), "")
                        .replace(nsSentinel, ":")
                        .trim()
                        .takeIf { it.isNotEmpty() && it != "*" }
                        ?.let { normalizeSelectorTagName(it) }
                    if (exclTag != null && exclTag !in excludedTags) {
                        excludedTags.add(exclTag)
                    }
                }
                idx = end
                continue
            }
            strippedBuilder.append(protectedToken[idx])
            idx++
        }

        var working = strippedBuilder.toString().replace(nsSentinel, "\\:")

        val pseudoRegex = Regex(":(hover|focus-visible|focus-within|focus|active|disabled|checked|enabled)", RegexOption.IGNORE_CASE)
        for (m in pseudoRegex.findAll(working)) {
            val name = m.groupValues[1].lowercase()
            if (name !in pseudos) pseudos.add(name)
        }
        if (Regex("\\[\\s*disabled(?:\\s*=[^]]*)?\\s*\\]", RegexOption.IGNORE_CASE).containsMatchIn(working) &&
            "disabled" !in pseudos
        ) {
            pseudos.add("disabled")
        }
        working = working.replace(pseudoRegex, "")

        // Map attribute selectors like [data-bs-theme="dark"] into class token used by BlinkStyleEngine
        if (working.contains("[data-bs-theme=\"dark\"]", ignoreCase = true) ||
            working.contains("[data-bs-theme='dark']", ignoreCase = true)
        ) {
            working = working
                .replace("[data-bs-theme=\"dark\"]", ".bs-theme-dark", ignoreCase = true)
                .replace("[data-bs-theme='dark']", ".bs-theme-dark", ignoreCase = true)
        }
        working = working.replace(Regex("\\[[^]]*]"), "")

        val idMatch = Regex("#([a-zA-Z0-9_-]+)").find(working)
        val id = idMatch?.groupValues?.get(1)
        working = working.replace(Regex("#[a-zA-Z0-9_-]+"), "")

        val classes = Regex("\\.([a-zA-Z0-9_-]+)").findAll(working).map { it.groupValues[1] }.toList()
        val rawTag = working
            .replace(Regex("\\.[a-zA-Z0-9_-]+"), "")
            .replace("\\:", ":")
            .trim()
            .takeIf { it.isNotEmpty() && it != "*" }
        val leadingTag = rawTag?.let { normalizeSelectorTagName(it) }

        return BuildTimeSelectorSegment(
            tagName = leadingTag,
            id = id,
            classes = classes,
            pseudoClasses = pseudos,
            combinatorToLeft = combinatorToLeft,
            excludedClasses = excludedClasses,
            excludedPseudoClasses = excludedPseudos,
            excludedTags = excludedTags,
            excludedIds = excludedIds
        )
    }

    private fun emitTypedComponentStyleStatements(prop: String, cleanVal: String, isImp: Boolean): List<String> {
        val out = mutableListOf<String>()
        when (prop.lowercase()) {
            "background-color" -> {
                emitColorExpr(cleanVal)?.let { out.add("setBackgroundColor($it, $isImp)") }
            }
            "background" -> {
                val gradExpr = emitLinearGradientExpr(cleanVal)
                if (gradExpr != null) {
                    out.add("setBackgroundImage(CssBackgroundImage.fromGradient($gradExpr), $isImp)")
                } else {
                    emitColorExpr(cleanVal)?.let { out.add("setBackgroundColor($it, $isImp)") }
                }
            }
            "background-image" -> {
                val gradExpr = emitLinearGradientExpr(cleanVal)
                if (gradExpr != null) {
                    out.add("setBackgroundImage(CssBackgroundImage.fromGradient($gradExpr), $isImp)")
                } else if (cleanVal.startsWith("url(", ignoreCase = true)) {
                    val url = cleanVal.removePrefix("url(").removeSuffix(")").trim().trim('\'', '"')
                    out.add("setBackgroundImage(CssBackgroundImage.url(${escapeKotlinString(url)}), $isImp)")
                }
            }
            "color" -> emitColorExpr(cleanVal)?.let { out.add("setTextColor($it, $isImp)") }
            "accent-color" -> emitColorExpr(cleanVal)?.let { out.add("setAccentColor($it, $isImp)") }
            "border-color" -> emitColorExpr(cleanVal)?.let { out.add("setBorderColor($it, $isImp)") }
            "opacity" -> cleanVal.toFloatOrNull()?.let { out.add("setOpacity(${it}f, $isImp)") }
            "display" -> emitDisplayEnum(cleanVal)?.let { out.add("setDisplay($it, $isImp)") }
            "visibility" -> emitVisibilityEnum(cleanVal)?.let { out.add("setVisibility($it, $isImp)") }
            "position" -> emitPositionEnum(cleanVal)?.let { out.add("setPosition($it, $isImp)") }
            "top" -> emitLengthExpr(cleanVal)?.let { out.add("setTop($it, $isImp)") }
            "right" -> emitLengthExpr(cleanVal)?.let { out.add("setRight($it, $isImp)") }
            "bottom" -> emitLengthExpr(cleanVal)?.let { out.add("setBottom($it, $isImp)") }
            "left" -> emitLengthExpr(cleanVal)?.let { out.add("setLeft($it, $isImp)") }
            "width" -> emitLengthExpr(cleanVal)?.let { out.add("setWidth($it, $isImp)") }
            "height" -> emitLengthExpr(cleanVal)?.let { out.add("setHeight($it, $isImp)") }
            "min-width" -> emitLengthExpr(cleanVal)?.let { out.add("setMinWidth($it, $isImp)") }
            "min-height" -> emitLengthExpr(cleanVal)?.let { out.add("setMinHeight($it, $isImp)") }
            "max-width" -> emitLengthExpr(cleanVal)?.let { out.add("setMaxWidth($it, $isImp)") }
            "max-height" -> emitLengthExpr(cleanVal)?.let { out.add("setMaxHeight($it, $isImp)") }
            "margin" -> emitEdgeLengthsExpr(cleanVal)?.let { out.add("setMargin($it, $isImp)") }
            "margin-top" -> emitLengthExpr(cleanVal)?.let { out.add("setMarginTop($it, $isImp)") }
            "margin-right" -> emitLengthExpr(cleanVal)?.let { out.add("setMarginRight($it, $isImp)") }
            "margin-bottom" -> emitLengthExpr(cleanVal)?.let { out.add("setMarginBottom($it, $isImp)") }
            "margin-left" -> emitLengthExpr(cleanVal)?.let { out.add("setMarginLeft($it, $isImp)") }
            "padding" -> emitEdgeLengthsExpr(cleanVal)?.let { out.add("setPadding($it, $isImp)") }
            "padding-top" -> emitLengthExpr(cleanVal)?.let { out.add("setPaddingTop($it, $isImp)") }
            "padding-right" -> emitLengthExpr(cleanVal)?.let { out.add("setPaddingRight($it, $isImp)") }
            "padding-bottom" -> emitLengthExpr(cleanVal)?.let { out.add("setPaddingBottom($it, $isImp)") }
            "padding-left" -> emitLengthExpr(cleanVal)?.let { out.add("setPaddingLeft($it, $isImp)") }
            "box-sizing" -> emitBoxSizingEnum(cleanVal)?.let { out.add("setBoxSizing($it, $isImp)") }
            "border-width" -> emitEdgeLengthsExpr(cleanVal)?.let { out.add("setBorderWidth($it, $isImp)") }
            "border-style" -> emitBorderStyleEnum(cleanVal)?.let { out.add("setBorderStyle($it, $isImp)") }
            "border" -> {
                if (cleanVal.equals("none", ignoreCase = true) || cleanVal == "0") {
                    out.add("setBorderStyle(CssBorderStyle.NONE, $isImp)")
                    out.add("setBorderWidth(CssEdgeLengths.all(CssLength.ZERO), $isImp)")
                } else {
                    for (tok in splitTokensPreservingParens(cleanVal)) {
                        val bStyle = emitBorderStyleEnum(tok)
                        if (bStyle != null) {
                            out.add("setBorderStyle($bStyle, $isImp)")
                            continue
                        }
                        val bColor = emitColorExpr(tok)
                        if (bColor != null) {
                            out.add("setBorderColor($bColor, $isImp)")
                            continue
                        }
                        val bLen = emitLengthExpr(tok)
                        if (bLen != null) {
                            out.add("setBorderWidth(CssEdgeLengths.all($bLen), $isImp)")
                        }
                    }
                }
            }
            "border-radius" -> emitCornerRadiiExpr(cleanVal)?.let { out.add("setBorderRadius($it, $isImp)") }
            "font-size" -> emitLengthExpr(cleanVal)?.let { out.add("setFontSize($it, $isImp)") }
            "font-weight" -> emitFontWeightStmt(cleanVal, isImp)?.let { out.add(it) }
            "font-style" -> emitFontStyleEnum(cleanVal)?.let { out.add("setFontStyle($it, $isImp)") }
            "font-family" -> {
                val cleanedFontFamily = cleanVal.trim()
                if (cleanedFontFamily.isNotEmpty()) {
                    out.add("CssFontFamily.parse(${escapeKotlinString(cleanedFontFamily)})?.let { setFontFamily(it, $isImp) }")
                }
            }
            "line-height" -> emitLengthExpr(cleanVal, unitlessAsNumber = true)?.let { out.add("setLineHeight($it, $isImp)") }
            "letter-spacing" -> emitLengthExpr(cleanVal)?.let { out.add("setLetterSpacing($it, $isImp)") }
            "text-align" -> emitTextAlignEnum(cleanVal)?.let { out.add("setTextAlign($it, $isImp)") }
            "text-transform" -> emitTextTransformEnum(cleanVal)?.let { out.add("setTextTransform($it, $isImp)") }
            "text-overflow" -> emitTextOverflowEnum(cleanVal)?.let { out.add("setTextOverflow($it, $isImp)") }
            "text-decoration" -> emitTextDecorationEnum(cleanVal)?.let { out.add("setTextDecoration($it, $isImp)") }
            "white-space" -> emitWhiteSpaceEnum(cleanVal)?.let { out.add("setWhiteSpace($it, $isImp)") }
            "flex-direction" -> emitFlexDirectionEnum(cleanVal)?.let { out.add("setFlexDirection($it, $isImp)") }
            "flex-wrap" -> emitFlexWrapEnum(cleanVal)?.let { out.add("setFlexWrap($it, $isImp)") }
            "justify-content" -> emitJustifyContentEnum(cleanVal)?.let { out.add("setJustifyContent($it, $isImp)") }
            "align-items" -> emitAlignItemsEnum(cleanVal)?.let { out.add("setAlignItems($it, $isImp)") }
            "align-self" -> emitAlignSelfEnum(cleanVal)?.let { out.add("setAlignSelf($it, $isImp)") }
            "flex-grow" -> cleanVal.toFloatOrNull()?.let { out.add("setFlexGrow(${it}f, $isImp)") }
            "flex-shrink" -> cleanVal.toFloatOrNull()?.let { out.add("setFlexShrink(${it}f, $isImp)") }
            "flex-basis" -> emitLengthExpr(cleanVal)?.let { out.add("setFlexBasis($it, $isImp)") }
            "gap" -> emitLengthExpr(cleanVal)?.let { out.add("setGap($it, $isImp)") }
            "row-gap" -> emitLengthExpr(cleanVal)?.let { out.add("setRowGap($it, $isImp)") }
            "column-gap" -> emitLengthExpr(cleanVal)?.let { out.add("setColumnGap($it, $isImp)") }
            "overflow" -> emitOverflowEnum(cleanVal)?.let { out.add("setOverflow($it, $isImp)") }
            "overflow-x" -> emitOverflowEnum(cleanVal)?.let { out.add("setOverflowX($it, $isImp)") }
            "overflow-y" -> emitOverflowEnum(cleanVal)?.let { out.add("setOverflowY($it, $isImp)") }
            "pointer-events" -> emitPointerEventsEnum(cleanVal)?.let { out.add("setPointerEvents($it, $isImp)") }
            "user-select", "-webkit-user-select" -> emitUserSelectEnum(cleanVal)?.let { out.add("setUserSelect($it, $isImp)") }
            "object-fit" -> emitObjectFitEnum(cleanVal)?.let { out.add("setObjectFit($it, $isImp)") }
            "cursor" -> {
                val cursorEnum = emitCursorEnum(cleanVal)
                if (cursorEnum != null) {
                    out.add("setCursor($cursorEnum, $isImp)")
                } else if (cleanVal.isNotEmpty()) {
                    out.add("CssCustomCursor.parse(${escapeKotlinString(cleanVal)})?.let { setCursor(it, $isImp) }")
                }
            }
            "z-index" -> cleanVal.toIntOrNull()?.let { out.add("setZIndex($it, $isImp)") }
            "transform", "-webkit-transform" -> {
                if (cleanVal.isNotEmpty()) {
                    out.add("CssTransform.parse(${escapeKotlinString(cleanVal)})?.let { setTransform(it, $isImp) }")
                }
            }
            "transform-origin", "-webkit-transform-origin" -> {
                if (cleanVal.isNotEmpty()) {
                    out.add("CssBackgroundPosition.parse(${escapeKotlinString(cleanVal)})?.let { setTransformOrigin(it, $isImp) }")
                }
            }
            "transition", "-webkit-transition" -> {
                if (cleanVal.isNotEmpty()) {
                    out.add("CssTransitionSpec.parseList(${escapeKotlinString(cleanVal)}).takeIf { it.isNotEmpty() }?.let { setTransitions(it, $isImp) }")
                }
            }
            "animation", "-webkit-animation" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimation(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-name", "-webkit-animation-name" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationName(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-duration", "-webkit-animation-duration" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationDuration(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-timing-function", "-webkit-animation-timing-function" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationTimingFunction(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-delay", "-webkit-animation-delay" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationDelay(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-iteration-count", "-webkit-animation-iteration-count" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationIterationCount(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-direction", "-webkit-animation-direction" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationDirection(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-fill-mode", "-webkit-animation-fill-mode" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationFillMode(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "animation-play-state", "-webkit-animation-play-state" -> {
                if (cleanVal.isNotEmpty()) out.add("setAnimationPlayState(${escapeKotlinString(cleanVal)}, $isImp)")
            }
            "box-shadow", "-webkit-box-shadow" -> emitShadowListExpr(cleanVal)?.let { out.add("setBoxShadow($it, $isImp)") }
            "text-shadow" -> emitShadowListExpr(cleanVal)?.let { out.add("setTextShadow($it, $isImp)") }
            "filter" -> emitFilterExpr(cleanVal)?.let { out.add("setFilter($it, $isImp)") }
            "backdrop-filter", "-webkit-backdrop-filter" -> emitFilterExpr(cleanVal)?.let { out.add("setBackdropFilter($it, $isImp)") }
        }
        return out
    }

    private fun emitColorExpr(raw: String): String? {
        val clean = raw.trim().lowercase()
        when (clean) {
            "transparent" -> return "RgbaColor(0, 0, 0, 0)"
            "white" -> return "RgbaColor(255, 255, 255, 255)"
            "black" -> return "RgbaColor(0, 0, 0, 255)"
            "red" -> return "RgbaColor(255, 0, 0, 255)"
            "green" -> return "RgbaColor(0, 128, 0, 255)"
            "blue" -> return "RgbaColor(0, 0, 255, 255)"
        }
        if (clean.startsWith("#")) {
            val hex = clean.removePrefix("#")
            return when (hex.length) {
                3 -> {
                    val r = hex.substring(0, 1).repeat(2).toIntOrNull(16) ?: return null
                    val g = hex.substring(1, 2).repeat(2).toIntOrNull(16) ?: return null
                    val b = hex.substring(2, 3).repeat(2).toIntOrNull(16) ?: return null
                    "RgbaColor($r, $g, $b, 255)"
                }
                4 -> {
                    val r = hex.substring(0, 1).repeat(2).toIntOrNull(16) ?: return null
                    val g = hex.substring(1, 2).repeat(2).toIntOrNull(16) ?: return null
                    val b = hex.substring(2, 3).repeat(2).toIntOrNull(16) ?: return null
                    val a = hex.substring(3, 4).repeat(2).toIntOrNull(16) ?: return null
                    "RgbaColor($r, $g, $b, $a)"
                }
                6 -> {
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    "RgbaColor($r, $g, $b, 255)"
                }
                8 -> {
                    val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                    val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                    val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                    val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                    "RgbaColor($r, $g, $b, $a)"
                }
                else -> null
            }
        }
        val rgbaMatch = Regex("^rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*([0-9.]+))?\\s*\\)$").matchEntire(clean)
        if (rgbaMatch != null) {
            val r = rgbaMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val g = rgbaMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val b = rgbaMatch.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val aStr = rgbaMatch.groupValues[4]
            val a = if (aStr.isEmpty()) 255 else {
                val af = aStr.toFloatOrNull() ?: 1f
                if (af <= 1f && aStr.contains('.')) (af * 255f).toInt().coerceIn(0, 255)
                else if (af <= 1f) (af * 255f).toInt().coerceIn(0, 255)
                else af.toInt().coerceIn(0, 255)
            }
            return "RgbaColor($r, $g, $b, $a)"
        }
        return null
    }

    private fun emitLengthExpr(raw: String, unitlessAsNumber: Boolean = false): String? {
        val clean = raw.trim().lowercase()
        if (unitlessAsNumber) {
            val bare = clean.toFloatOrNull()
            if (bare != null && bare.isFinite()) {
                return "CssLength(${bare}f, CssUnit.NUMBER)"
            }
        }
        if (clean == "auto") return "CssLength.AUTO"
        if (clean == "none") return "CssLength.NONE"
        if (clean == "0" || clean == "0px") return "CssLength.ZERO"
        if (clean == "100%") return "CssLength.PERCENT_100"
        if (clean == "50%") return "CssLength.PERCENT_50"
        val calcMatch = Regex("^calc\\(\\s*([+-]?[0-9]*\\.?[0-9]+)%\\s*([+-])\\s*([0-9]*\\.?[0-9]+)px\\s*\\)$").matchEntire(clean)
        if (calcMatch != null) {
            val pct = calcMatch.groupValues[1].toFloatOrNull() ?: return null
            val op = calcMatch.groupValues[2]
            val px = calcMatch.groupValues[3].toFloatOrNull() ?: return null
            val signedPx = if (op == "-") -px else px
            return "CssLength.calcPercentPx(${pct}f, ${signedPx}f)"
        }
        val unitMap = listOf(
            "vmin" to "CssUnit.VMIN",
            "vmax" to "CssUnit.VMAX",
            "rem" to "CssUnit.REM",
            "px" to "CssUnit.PX",
            "dp" to "CssUnit.DP",
            "em" to "CssUnit.EM",
            "vw" to "CssUnit.VW",
            "vh" to "CssUnit.VH",
            "pt" to "CssUnit.PT",
            "%" to "CssUnit.PERCENT"
        )
        for ((suffix, unitEnum) in unitMap) {
            if (clean.endsWith(suffix)) {
                val num = clean.removeSuffix(suffix).trim().toFloatOrNull() ?: return null
                return "CssLength(${num}f, $unitEnum)"
            }
        }
        val bare = clean.toFloatOrNull() ?: return null
        return "CssLength(${bare}f, CssUnit.PX)"
    }

    private fun emitEdgeLengthsExpr(raw: String): String? {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { emitLengthExpr(it) ?: return null }
        return when (tokens.size) {
            1 -> "CssEdgeLengths.all(${tokens[0]})"
            2 -> "CssEdgeLengths(top = ${tokens[0]}, right = ${tokens[1]}, bottom = ${tokens[0]}, left = ${tokens[1]})"
            3 -> "CssEdgeLengths(top = ${tokens[0]}, right = ${tokens[1]}, bottom = ${tokens[2]}, left = ${tokens[1]})"
            4 -> "CssEdgeLengths(top = ${tokens[0]}, right = ${tokens[1]}, bottom = ${tokens[2]}, left = ${tokens[3]})"
            else -> null
        }
    }

    private fun emitCornerRadiiExpr(raw: String): String? {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { emitLengthExpr(it) ?: return null }
        return when (tokens.size) {
            1 -> "CssCornerRadii.all(${tokens[0]})"
            2 -> "CssCornerRadii(topLeft = ${tokens[0]}, topRight = ${tokens[1]}, bottomRight = ${tokens[0]}, bottomLeft = ${tokens[1]})"
            3 -> "CssCornerRadii(topLeft = ${tokens[0]}, topRight = ${tokens[1]}, bottomRight = ${tokens[2]}, bottomLeft = ${tokens[1]})"
            4 -> "CssCornerRadii(topLeft = ${tokens[0]}, topRight = ${tokens[1]}, bottomRight = ${tokens[2]}, bottomLeft = ${tokens[3]})"
            else -> null
        }
    }

    private fun emitLinearGradientExpr(raw: String): String? {
        val clean = raw.trim()
        if (!clean.startsWith("linear-gradient(", ignoreCase = true) || !clean.endsWith(")")) return null
        val inner = clean.substring("linear-gradient(".length, clean.length - 1).trim()
        val parts = splitTopLevelCommas(inner)
        if (parts.size < 2) return null
        var angleDeg = 180f
        var startIdx = 0
        val first = parts[0].trim().lowercase()
        if (first.endsWith("deg")) {
            angleDeg = first.removeSuffix("deg").trim().toFloatOrNull() ?: 180f
            startIdx = 1
        } else if (first.startsWith("to ")) {
            angleDeg = when (first.removePrefix("to ").trim()) {
                "right" -> 90f
                "left" -> 270f
                "top" -> 0f
                "bottom" -> 180f
                else -> 135f
            }
            startIdx = 1
        }
        val stopExprs = mutableListOf<String>()
        val stopCount = parts.size - startIdx
        for ((i, stopRaw) in parts.subList(startIdx, parts.size).withIndex()) {
            val tokens = splitTokensPreservingParens(stopRaw.trim())
            if (tokens.isEmpty()) continue
            val colorExpr = emitColorExpr(tokens[0]) ?: continue
            val pos = if (tokens.size > 1 && tokens[1].endsWith("%")) {
                (tokens[1].removeSuffix("%").toFloatOrNull() ?: 0f) / 100f
            } else {
                if (stopCount <= 1) 0f else i.toFloat() / (stopCount - 1).toFloat()
            }
            stopExprs.add("GradientStop($colorExpr, ${pos}f)")
        }
        if (stopExprs.size < 2) return null
        return "LinearGradient(${angleDeg}f, listOf(${stopExprs.joinToString(", ")}))"
    }

    private fun emitShadowListExpr(raw: String): String? {
        val clean = raw.trim()
        if (clean.equals("none", ignoreCase = true)) return "CssShadowList.NONE"
        val shadowParts = splitTopLevelCommas(clean)
        val shadowExprs = mutableListOf<String>()
        for (part in shadowParts) {
            val tokens = splitTokensPreservingParens(part.trim())
            var inset = false
            var colorExpr = "RgbaColor(0, 0, 0, 128)"
            val lengths = mutableListOf<String>()
            for (tok in tokens) {
                if (tok.equals("inset", ignoreCase = true)) {
                    inset = true
                    continue
                }
                val col = emitColorExpr(tok)
                if (col != null) {
                    colorExpr = col
                    continue
                }
                val len = emitLengthExpr(tok)
                if (len != null) {
                    lengths.add(len)
                }
            }
            if (lengths.size >= 2) {
                val ox = lengths[0]
                val oy = lengths[1]
                val blur = lengths.getOrNull(2) ?: "CssLength.ZERO"
                val spread = lengths.getOrNull(3) ?: "CssLength.ZERO"
                shadowExprs.add("CssShadow(offsetX = $ox, offsetY = $oy, blurRadius = $blur, spreadRadius = $spread, color = $colorExpr, inset = $inset)")
            }
        }
        if (shadowExprs.isEmpty()) return null
        return "CssShadowList(listOf(${shadowExprs.joinToString(", ")}))"
    }

    private fun emitFilterExpr(raw: String): String? {
        val blurMatch = Regex("blur\\(\\s*([0-9.]+)(?:px)?\\s*\\)", RegexOption.IGNORE_CASE).find(raw)
        if (blurMatch != null) {
            val blurPx = blurMatch.groupValues[1].toFloatOrNull() ?: return null
            return "CssFilter(blur = CssLength(${blurPx}f, CssUnit.PX))"
        }
        return null
    }

    private fun emitDisplayEnum(v: String): String? = when (v.trim().lowercase()) {
        "none" -> "CssDisplay.NONE"
        "block" -> "CssDisplay.BLOCK"
        "inline" -> "CssDisplay.INLINE"
        "inline-block" -> "CssDisplay.INLINE_BLOCK"
        "flex" -> "CssDisplay.FLEX"
        "inline-flex" -> "CssDisplay.INLINE_FLEX"
        "grid" -> "CssDisplay.GRID"
        "inline-grid" -> "CssDisplay.INLINE_GRID"
        "contents" -> "CssDisplay.CONTENTS"
        else -> null
    }

    private fun emitVisibilityEnum(v: String): String? = when (v.trim().lowercase()) {
        "visible" -> "CssVisibility.VISIBLE"
        "hidden" -> "CssVisibility.HIDDEN"
        "collapse" -> "CssVisibility.COLLAPSE"
        else -> null
    }

    private fun emitPositionEnum(v: String): String? = when (v.trim().lowercase()) {
        "static" -> "CssPosition.STATIC"
        "relative" -> "CssPosition.RELATIVE"
        "absolute" -> "CssPosition.ABSOLUTE"
        "fixed" -> "CssPosition.FIXED"
        "sticky" -> "CssPosition.STICKY"
        else -> null
    }

    private fun emitBoxSizingEnum(v: String): String? = when (v.trim().lowercase()) {
        "border-box" -> "CssBoxSizing.BORDER_BOX"
        "content-box" -> "CssBoxSizing.CONTENT_BOX"
        else -> null
    }

    private fun emitBorderStyleEnum(v: String): String? = when (v.trim().lowercase()) {
        "none" -> "CssBorderStyle.NONE"
        "solid" -> "CssBorderStyle.SOLID"
        "dashed" -> "CssBorderStyle.DASHED"
        "dotted" -> "CssBorderStyle.DOTTED"
        "double" -> "CssBorderStyle.DOUBLE"
        else -> null
    }

    private fun emitFontWeightStmt(v: String, isImp: Boolean): String? {
        val num = when (v.trim().lowercase()) {
            "normal" -> 400
            "bold" -> 700
            "lighter" -> 300
            "bolder" -> 800
            else -> v.trim().toIntOrNull()
        } ?: return null
        val enumName = when (num) {
            300 -> "FontWeight.LIGHT"
            400 -> "FontWeight.NORMAL"
            500 -> "FontWeight.MEDIUM"
            600 -> "FontWeight.SEMIBOLD"
            700 -> "FontWeight.BOLD"
            else -> null
        }
        return if (enumName != null) {
            "setFontWeight($enumName, $isImp)"
        } else {
            "fontWeightNumeric = $num"
        }
    }

    private fun emitFontStyleEnum(v: String): String? = when (v.trim().lowercase()) {
        "normal" -> "CssFontStyle.NORMAL"
        "italic" -> "CssFontStyle.ITALIC"
        "oblique" -> "CssFontStyle.OBLIQUE"
        else -> null
    }

    private fun emitTextAlignEnum(v: String): String? = when (v.trim().lowercase()) {
        "left", "start" -> "TextAlignment.START"
        "center" -> "TextAlignment.CENTER"
        "right", "end" -> "TextAlignment.END"
        "justify" -> "TextAlignment.JUSTIFY"
        else -> null
    }

    private fun emitTextTransformEnum(v: String): String? = when (v.trim().lowercase()) {
        "none" -> "TextTransform.NONE"
        "lowercase" -> "TextTransform.LOWERCASE"
        "uppercase" -> "TextTransform.UPPERCASE"
        "capitalize" -> "TextTransform.CAPITALIZE"
        else -> null
    }

    private fun emitTextOverflowEnum(v: String): String? = when (v.trim().lowercase()) {
        "ellipsis", "truncate" -> "TextOverflow.ELLIPSIS"
        "nowrap" -> "TextOverflow.NOWRAP"
        "clip", "wrap" -> "TextOverflow.WRAP"
        else -> null
    }

    private fun emitTextDecorationEnum(v: String): String? = when (v.trim().lowercase()) {
        "none" -> "CssTextDecoration.NONE"
        "underline" -> "CssTextDecoration.UNDERLINE"
        "line-through" -> "CssTextDecoration.LINE_THROUGH"
        "overline" -> "CssTextDecoration.OVERLINE"
        else -> null
    }

    private fun emitWhiteSpaceEnum(v: String): String? = when (v.trim().lowercase()) {
        "normal" -> "CssWhiteSpace.NORMAL"
        "nowrap" -> "CssWhiteSpace.NOWRAP"
        "pre" -> "CssWhiteSpace.PRE"
        "pre-wrap" -> "CssWhiteSpace.PRE_WRAP"
        "pre-line" -> "CssWhiteSpace.PRE_LINE"
        else -> null
    }

    private fun emitFlexDirectionEnum(v: String): String? = when (v.trim().lowercase()) {
        "row" -> "FlexDirection.ROW"
        "row-reverse" -> "FlexDirection.ROW_REVERSE"
        "column" -> "FlexDirection.COLUMN"
        "column-reverse" -> "FlexDirection.COLUMN_REVERSE"
        else -> null
    }

    private fun emitFlexWrapEnum(v: String): String? = when (v.trim().lowercase()) {
        "nowrap" -> "FlexWrap.NOWRAP"
        "wrap" -> "FlexWrap.WRAP"
        "wrap-reverse" -> "FlexWrap.WRAP_REVERSE"
        else -> null
    }

    private fun emitJustifyContentEnum(v: String): String? = when (v.trim().lowercase()) {
        "flex-start", "start", "left" -> "JustifyContent.START"
        "flex-end", "end", "right" -> "JustifyContent.END"
        "center" -> "JustifyContent.CENTER"
        "space-between", "between" -> "JustifyContent.BETWEEN"
        "space-around", "around" -> "JustifyContent.AROUND"
        "space-evenly", "evenly" -> "JustifyContent.EVENLY"
        else -> null
    }

    private fun emitAlignItemsEnum(v: String): String? = when (v.trim().lowercase()) {
        "flex-start", "start" -> "AlignItems.START"
        "flex-end", "end" -> "AlignItems.END"
        "center" -> "AlignItems.CENTER"
        "baseline" -> "AlignItems.BASELINE"
        "stretch" -> "AlignItems.STRETCH"
        else -> null
    }

    private fun emitAlignSelfEnum(v: String): String? = when (v.trim().lowercase()) {
        "auto" -> "AlignSelf.AUTO"
        "flex-start", "start" -> "AlignSelf.START"
        "flex-end", "end" -> "AlignSelf.END"
        "center" -> "AlignSelf.CENTER"
        "baseline" -> "AlignSelf.BASELINE"
        "stretch" -> "AlignSelf.STRETCH"
        else -> null
    }

    private fun emitOverflowEnum(v: String): String? = when (v.trim().lowercase()) {
        "auto" -> "OverflowBehavior.AUTO"
        "hidden", "clip" -> "OverflowBehavior.HIDDEN"
        "visible" -> "OverflowBehavior.VISIBLE"
        "scroll" -> "OverflowBehavior.SCROLL"
        else -> null
    }

    private fun emitPointerEventsEnum(v: String): String? = when (v.trim().lowercase()) {
        "auto" -> "CssPointerEvents.AUTO"
        "none" -> "CssPointerEvents.NONE"
        else -> null
    }

    private fun emitUserSelectEnum(v: String): String? = when (v.trim().lowercase()) {
        "auto" -> "CssUserSelect.AUTO"
        "none" -> "CssUserSelect.NONE"
        "text" -> "CssUserSelect.TEXT"
        "all" -> "CssUserSelect.ALL"
        else -> null
    }

    private fun emitObjectFitEnum(v: String): String? = when (v.trim().lowercase()) {
        "contain" -> "ObjectFit.CONTAIN"
        "cover" -> "ObjectFit.COVER"
        "fill" -> "ObjectFit.FILL"
        "scale-down", "scale" -> "ObjectFit.SCALE_DOWN"
        "none" -> "ObjectFit.NONE"
        else -> null
    }

    private fun emitCursorEnum(v: String): String? = when (v.trim().lowercase()) {
        "default" -> "CssCursor.DEFAULT"
        "none" -> "CssCursor.NONE"
        "pointer" -> "CssCursor.POINTER"
        "text" -> "CssCursor.TEXT"
        "move" -> "CssCursor.MOVE"
        "grab" -> "CssCursor.GRAB"
        "grabbing" -> "CssCursor.GRABBING"
        "not-allowed" -> "CssCursor.NOT_ALLOWED"
        "crosshair" -> "CssCursor.CROSSHAIR"
        "ew-resize", "col-resize" -> "CssCursor.EW_RESIZE"
        "ns-resize", "row-resize" -> "CssCursor.NS_RESIZE"
        else -> null
    }

    private fun splitTokensPreservingParens(raw: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in raw) {
            when {
                ch == '(' -> {
                    depth++
                    current.append(ch)
                }
                ch == ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                ch.isWhitespace() && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        out.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private fun splitTopLevelCommas(raw: String): List<String> =
        Companion.splitTopLevelCommasStatic(raw)

    private fun parseCssRulesAndKeyframes(
        cssText: String
    ): Pair<List<ParsedRule>, Map<String, Map<String, Map<String, String>>>> =
        Companion.parseCssRulesAndKeyframesStatic(cssText)

    private fun parseDeclarations(body: String): Map<String, String> =
        Companion.parseDeclarationsStatic(body)

    private fun computeSpecificity(selector: String): Int =
        Companion.computeSpecificityStatic(selector)

    private fun toPascalIdentifier(raw: String): String = Companion.toPascalIdentifier(raw)

    companion object {
        const val GENERATED_THEME_STYLESHEET_OBJECT_NAME: String = "GenericUiGeneratedThemeStyleSheet"

        private val GENERICUI_CUSTOM_TAGS: Set<String> = setOf(
            "app",
            "component",
            "composite",
            "fragment",
            "fragment-container",
            "styled-container",
            "spacer",
            "layered-container",
            "styled-component",
            "styled-slider",
            "window",
            "menubar",
            "menu",
            "menuitem",
            "checkmenuitem",
            "radiomenuitem",
            "menuseparator",
            "contextmenu",
            "splitpane",
            "titlebar",
            "statusbar",
            "mdicontainer",
            "mdidesktop",
            "mdiwindow",
            "dockworkspace",
            "dockarea",
            "dockgroup",
            "dockpanel",
            "panelwindow",
            "toolpalette",
            "toolpaletteitem",
            "categorizedtreeview",
            "colorswatchbox",
            "container",
            "box",
            "row",
            "col",
            "flexbox",
            "hstack",
            "vstack",
            "scrollview",
            "stepscrollview",
            "aspectratiocontainer",
            "lazylist",
            "lazycolumn",
            "lazyrow",
            "lazygrid",
            "lazygridtree",
            "treeview",
            "card",
            "collapsiblepanel",
            "accordion",
            "accordionitem",
            "jumbotron",
            "well",
            "listgroup",
            "listgroupitem",
            "tabcontainer",
            "tab",
            "closeabletab",
            "pinnedtab",
            "badgetab",
            "editabletab",
            "navbar",
            "nav",
            "sidenav",
            "bottomnavigationbar",
            "breadcrumb",
            "breadcrumbitem",
            "pagination",
            "stepper",
            "dropdown",
            "buttongroup",
            "toolbar",
            "formgroup",
            "inputgroup",
            "radiogroup",
            "compoundunitinput",
            "filedropzone",
            "markdownview",
            "avatar",
            "masterdetailstepselect",
            "horizontalcarouselselector",
            "alert",
            "banner",
            "toast",
            "toastcontainer",
            "progressbar",
            "spinner",
            "skeletonplaceholder",
            "modal",
            "drawer",
            "bottomsheet",
            "popover",
            "tooltip",
            "backdrop",
            "dialogwindow"
        )

        internal fun normalizeSelectorTagName(rawTag: String): String {
            val cleaned = rawTag.trim().replace("\\:", ":").lowercase(Locale.US)
            if (cleaned.startsWith("genericui:")) return cleaned
            return if (cleaned in GENERICUI_CUSTOM_TAGS) "genericui:$cleaned" else cleaned
        }

        internal fun sanitizeSelectorOrNull(rawSelector: String): String? =
            rawSelector.trim().takeIf { it.isNotEmpty() }

        internal fun toPascalIdentifier(raw: String): String {
            val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return "Generated"
            val joined = parts.joinToString("") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            return if (joined.first().isDigit()) "Style$joined" else joined
        }

        internal fun buildThemeScssVariables(
            activePalette: Map<String, String>,
            lightPalette: Map<String, String>,
            darkPalette: Map<String, String>,
            modeName: String
        ): Map<String, String> {
            val vars = linkedMapOf<String, String>()
            fun populatePalette(prefix: String, palette: Map<String, String>) {
                val primary = palette[GenericUiThemeConfig.KEY_PRIMARY] ?: "#0d6efd"
                val secondary = palette[GenericUiThemeConfig.KEY_SECONDARY] ?: "#6c757d"
                val accent = palette[GenericUiThemeConfig.KEY_ACCENT] ?: primary
                val success = palette[GenericUiThemeConfig.KEY_SUCCESS] ?: "#198754"
                val danger = palette[GenericUiThemeConfig.KEY_DANGER] ?: "#dc3545"
                val warning = palette[GenericUiThemeConfig.KEY_WARNING] ?: "#ffc107"
                val info = palette[GenericUiThemeConfig.KEY_INFO] ?: "#0dcaf0"
                val light = palette[GenericUiThemeConfig.KEY_LIGHT] ?: "#f8f9fa"
                val dark = palette[GenericUiThemeConfig.KEY_DARK] ?: "#212529"
                val background = palette[GenericUiThemeConfig.KEY_BACKGROUND] ?: "#ffffff"
                val surface = palette[GenericUiThemeConfig.KEY_SURFACE] ?: "#f8f9fa"
                val textPrimary = palette[GenericUiThemeConfig.KEY_TEXT_PRIMARY] ?: "#212529"
                val textSecondary = palette[GenericUiThemeConfig.KEY_TEXT_SECONDARY] ?: "#6c757d"
                val border = palette[GenericUiThemeConfig.KEY_BORDER] ?: "#dee2e6"

                vars["${prefix}primary"] = primary
                vars["${prefix}secondary"] = secondary
                vars["${prefix}accent"] = accent
                vars["${prefix}success"] = success
                vars["${prefix}danger"] = danger
                vars["${prefix}error"] = danger
                vars["${prefix}warning"] = warning
                vars["${prefix}info"] = info
                vars["${prefix}light"] = light
                vars["${prefix}dark"] = dark
                vars["${prefix}background"] = background
                vars["${prefix}surface"] = surface
                vars["${prefix}text-primary"] = textPrimary
                vars["${prefix}text"] = textPrimary
                vars["${prefix}text-secondary"] = textSecondary
                vars["${prefix}border"] = border
            }

            populatePalette("", activePalette)
            populatePalette("light-", lightPalette)
            populatePalette("dark-", darkPalette)
            vars["theme-mode"] = modeName
            return vars
        }

        internal fun compileThemeTemplatesToCss(
            sources: List<String>,
            lightPalette: Map<String, String>,
            darkPalette: Map<String, String>,
            themeMode: String = "auto",
            minifyOutput: Boolean = false,
            targetBackendMask: Int? = null
        ): String {
            val combined = sources.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
            if (combined.isEmpty()) return ""
            return compileThemedScssForLightAndDarkStatic(
                source = combined,
                lightPalette = lightPalette,
                darkPalette = darkPalette,
                themeMode = themeMode,
                minifyOutput = minifyOutput,
                targetBackendMask = targetBackendMask
            )
        }

        internal fun compileThemedScssForLightAndDarkStatic(
            source: String,
            lightPalette: Map<String, String>,
            darkPalette: Map<String, String>,
            themeMode: String,
            minifyOutput: Boolean,
            targetBackendMask: Int? = null
        ): String {
            val cleanMode = themeMode.trim().lowercase(Locale.US).ifEmpty { "auto" }
            val lightVars = buildThemeScssVariables(lightPalette, lightPalette, darkPalette, "light")
            val darkVars = buildThemeScssVariables(darkPalette, lightPalette, darkPalette, "dark")

            val lightCss = transformSassSourceToCssStatic(
                source = source,
                minifyOutput = false,
                initialVariables = lightVars,
                targetBackendMask = targetBackendMask
            ).trim()
            val darkCss = transformSassSourceToCssStatic(
                source = source,
                minifyOutput = false,
                initialVariables = darkVars,
                targetBackendMask = targetBackendMask
            ).trim()

            if (lightCss == darkCss) {
                val base = if (lightCss.isEmpty()) "" else lightCss + "\n"
                return if (minifyOutput) minifyCssStatic(base) else base
            }

            val (lightRules, _) = parseCssRulesAndKeyframesStatic(lightCss)
            val (darkRules, _) = parseCssRulesAndKeyframesStatic(darkCss)

            val isDarkDefault = cleanMode == "dark"
            val baseCss = if (isDarkDefault) darkCss else lightCss
            val baseRules = if (isDarkDefault) darkRules else lightRules
            val altRules = if (isDarkDefault) lightRules else darkRules
            val altAttr = if (isDarkDefault) "[data-bs-theme=\"light\"]" else "[data-bs-theme=\"dark\"]"

            val baseByKey = LinkedHashMap<String, Map<String, String>>()
            for (r in baseRules) {
                baseByKey["${r.backendMask}|${r.mediaMinWidthPx ?: ""}|${r.mediaMaxWidthPx ?: ""}|${r.selector}"] = r.declarations
            }

            val overrideBlocks = StringBuilder()
            val keepMultiSystemWrapper = targetBackendMask == null ||
                Integer.bitCount(targetBackendMask and GenericUiBackendStyleMask.ALL) > 1
            for (r in altRules) {
                if (r.selector.contains("[data-bs-theme=", ignoreCase = true) ||
                    r.selector.startsWith(":root")
                ) {
                    continue
                }
                val key = "${r.backendMask}|${r.mediaMinWidthPx ?: ""}|${r.mediaMaxWidthPx ?: ""}|${r.selector}"
                val baseDecls = baseByKey[key]
                if (baseDecls == r.declarations) continue

                val changedDecls = if (baseDecls != null) {
                    r.declarations.filter { (k, v) -> baseDecls[k] != v }
                } else {
                    r.declarations
                }
                if (changedDecls.isEmpty()) continue

                val scopedSelector = "$altAttr ${r.selector}"
                val mediaConds = listOfNotNull(
                    r.mediaMinWidthPx?.let { "(min-width: ${it}px)" },
                    r.mediaMaxWidthPx?.let { "(max-width: ${it}px)" }
                )
                val declBody = changedDecls.entries.joinToString("; ") { (k, v) -> "$k: $v" } + ";"
                val wrapBackend = keepMultiSystemWrapper &&
                    targetBackendMask != GenericUiBackendStyleMask.WEB &&
                    r.backendMask != GenericUiBackendStyleMask.ALL
                val indent = if (wrapBackend) "  " else ""
                if (wrapBackend) {
                    overrideBlocks.append(GenericUiBackendStyleMask.formatCanonicalDirective(r.backendMask)).append(" {\n")
                }
                if (mediaConds.isNotEmpty()) {
                    overrideBlocks.append("${indent}@media ${mediaConds.joinToString(" and ")} {\n")
                    overrideBlocks.append("${indent}  $scopedSelector { $declBody }\n")
                    overrideBlocks.append("${indent}}\n")
                } else {
                    overrideBlocks.append("${indent}$scopedSelector {\n")
                    for ((k, v) in changedDecls) {
                        overrideBlocks.append("${indent}  $k: $v;\n")
                    }
                    overrideBlocks.append("${indent}}\n")
                }
                if (wrapBackend) {
                    overrideBlocks.append("}\n")
                }
            }

            val merged = buildString {
                if (baseCss.isNotEmpty()) {
                    append(baseCss)
                    if (!baseCss.endsWith("\n")) append("\n")
                }
                if (overrideBlocks.isNotEmpty()) {
                    append(overrideBlocks)
                }
            }
            return if (minifyOutput) minifyCssStatic(merged) else merged
        }

        internal fun transformSassSourceToCssStatic(
            source: String,
            minifyOutput: Boolean,
            initialVariables: Map<String, String> = emptyMap(),
            targetBackendMask: Int? = null
        ): String {
            val withoutComments = stripSassCommentsPreservingStringsStatic(source)
            val mixins = linkedMapOf<String, SassMixin>()
            val withoutMixins = extractSassMixinsStatic(withoutComments, mixins)
            val withMixinsExpanded = expandSassMixinsStatic(withoutMixins, mixins)

            val variables = LinkedHashMap<String, String>(initialVariables)
            val withoutVars = extractAndEvaluateVariablesStatic(withMixinsExpanded, variables, topLevelOnly = false)
            val substituted = evaluateSassExpressionsInTextStatic(withoutVars, variables)

            val outCss = StringBuilder()
            flattenBlockStatic(
                source = substituted,
                parentSelectors = emptyList(),
                activeMediaQuery = null,
                activeBackendMask = GenericUiBackendStyleMask.ALL,
                targetBackendMask = targetBackendMask,
                output = outCss
            )
            val flattened = compileRulesToCssStatic(outCss.toString().trim())
            return if (minifyOutput) {
                minifyCssStatic(flattened)
            } else {
                if (flattened.isEmpty()) "" else flattened + "\n"
            }
        }

        private fun minifyCssStatic(css: String): String =
            css.replace(Regex("""\s+"""), " ")
                .replace(Regex("""\s*([{}:;,])\s*"""), "$1")
                .trim()

        private fun compileRulesToCssStatic(cssOrSelectors: String): String {
            var result = Regex("(?<!\\\\)genericui:([a-zA-Z0-9_-]+)").replace(cssOrSelectors, "genericui\\\\:$1")
            val bareCustomTagRegex = Regex("(?<![.#:\\\\a-zA-Z0-9_-])(composite|styled-container|styled-component|styled-slider|layered-container|fragment-container)(?![a-zA-Z0-9_-])(?=[^;{}]*\\{)")
            result = bareCustomTagRegex.replace(result) { "genericui\\:${it.groupValues[1]}" }
            return result
        }

        private fun flattenBlockStatic(
            source: String,
            parentSelectors: List<String>,
            activeMediaQuery: String?,
            activeBackendMask: Int = GenericUiBackendStyleMask.ALL,
            targetBackendMask: Int? = null,
            output: StringBuilder
        ) {
            if (activeBackendMask == 0 || (targetBackendMask != null && (activeBackendMask and targetBackendMask) == 0)) {
                return
            }
            val declarations = mutableListOf<String>()
            val nestedBlocks = mutableListOf<Pair<String, String>>()
            var i = 0
            val n = source.length
            var tokenStart = 0
            var inSingle = false
            var inDouble = false
            var parenDepth = 0
            while (i < n) {
                val ch = source[i]
                if (inSingle) {
                    if (ch == '\\' && i + 1 < n) { i += 2; continue }
                    if (ch == '\'') inSingle = false
                    i++; continue
                }
                if (inDouble) {
                    if (ch == '\\' && i + 1 < n) { i += 2; continue }
                    if (ch == '"') inDouble = false
                    i++; continue
                }
                when (ch) {
                    '\'' -> { inSingle = true; i++ }
                    '"' -> { inDouble = true; i++ }
                    '(' -> { parenDepth++; i++ }
                    ')' -> { parenDepth = (parenDepth - 1).coerceAtLeast(0); i++ }
                    ';' -> {
                        if (parenDepth == 0) {
                            val decl = source.substring(tokenStart, i).trim()
                            if (decl.isNotEmpty()) declarations.add(decl)
                            tokenStart = i + 1
                        }
                        i++
                    }
                    '{' -> {
                        if (parenDepth == 0) {
                            val header = source.substring(tokenStart, i).trim()
                            val bodyStart = i + 1
                            var depth = 1
                            var sSingle = false
                            var sDouble = false
                            i++
                            while (i < n && depth > 0) {
                                val bch = source[i]
                                if (sSingle) {
                                    if (bch == '\\' && i + 1 < n) { i += 2; continue }
                                    if (bch == '\'') sSingle = false
                                } else if (sDouble) {
                                    if (bch == '\\' && i + 1 < n) { i += 2; continue }
                                    if (bch == '"') sDouble = false
                                } else {
                                    when (bch) {
                                        '\'' -> sSingle = true
                                        '"' -> sDouble = true
                                        '{' -> depth++
                                        '}' -> depth--
                                    }
                                }
                                i++
                            }
                            val body = source.substring(bodyStart, (i - 1).coerceAtLeast(bodyStart))
                            if (header.isNotEmpty()) nestedBlocks.add(header to body)
                            tokenStart = i
                        } else {
                            i++
                        }
                    }
                    else -> i++
                }
            }
            val trailing = source.substring(tokenStart, n).trim()
            if (trailing.isNotEmpty() && trailing.contains(":") && !trailing.contains("{")) {
                declarations.add(trailing.removeSuffix(";").trim())
            }
            val shouldWrapBackend = activeBackendMask != GenericUiBackendStyleMask.ALL &&
                (targetBackendMask == null || Integer.bitCount(targetBackendMask and GenericUiBackendStyleMask.ALL) > 1)
            if (declarations.isNotEmpty() && parentSelectors.isNotEmpty()) {
                val selectorLine = compileRulesToCssStatic(parentSelectors.joinToString(", "))
                val declBody = declarations.joinToString("; ") { it.trim() } + ";"
                val indent = if (shouldWrapBackend) "  " else ""
                if (shouldWrapBackend) {
                    output.append(GenericUiBackendStyleMask.formatCanonicalDirective(activeBackendMask)).append(" {\n")
                }
                if (activeMediaQuery != null) {
                    output.append(indent).append(activeMediaQuery).append(" {\n")
                    output.append(indent).append("  ").append(selectorLine).append(" { ").append(declBody).append(" }\n")
                    output.append(indent).append("}\n")
                } else {
                    output.append(indent).append(selectorLine).append(" {\n")
                    for (d in declarations) output.append(indent).append("  ").append(d).append(";\n")
                    output.append(indent).append("}\n")
                }
                if (shouldWrapBackend) {
                    output.append("}\n")
                }
            }
            for ((header, body) in nestedBlocks) {
                if (header.startsWith("@keyframes", ignoreCase = true) ||
                    header.startsWith("@-webkit-keyframes", ignoreCase = true)
                ) {
                    if (targetBackendMask != null && (activeBackendMask and targetBackendMask) == 0) {
                        continue
                    }
                    val formattedBody = formatKeyframesBodyStatic(body)
                    if (shouldWrapBackend) {
                        output.append(GenericUiBackendStyleMask.formatCanonicalDirective(activeBackendMask)).append(" {\n")
                        output.append(header).append(" {\n").append(formattedBody).append("}\n")
                        output.append("}\n")
                    } else {
                        output.append(header).append(" {\n").append(formattedBody).append("}\n")
                    }
                } else if (header.startsWith("@media", ignoreCase = true)) {
                    flattenBlockStatic(body, parentSelectors, header, activeBackendMask, targetBackendMask, output)
                } else {
                    val dirMask = GenericUiBackendStyleMask.parseDirectiveHeader(header)
                    if (dirMask != null) {
                        val nextMask = activeBackendMask and dirMask
                        if (nextMask != 0 && (targetBackendMask == null || (nextMask and targetBackendMask) != 0)) {
                            flattenBlockStatic(
                                source = body,
                                parentSelectors = parentSelectors,
                                activeMediaQuery = activeMediaQuery,
                                activeBackendMask = nextMask,
                                targetBackendMask = targetBackendMask,
                                output = output
                            )
                        }
                    } else {
                        val children = splitTopLevelCommasStatic(header)
                        val combined = if (parentSelectors.isEmpty()) children else buildList {
                            for (p in parentSelectors) {
                                for (c in children) {
                                    add(if (c.contains("&")) c.replace("&", p) else "$p $c")
                                }
                            }
                        }
                        flattenBlockStatic(body, combined, activeMediaQuery, activeBackendMask, targetBackendMask, output)
                    }
                }
            }
        }

        private fun formatKeyframesBodyStatic(body: String): String {
            val sb = StringBuilder()
            var i = 0
            val n = body.length
            while (i < n) {
                while (i < n && body[i].isWhitespace()) i++
                if (i >= n) break
                val headerStart = i
                while (i < n && body[i] != '{') i++
                if (i >= n) break
                val stopHeader = body.substring(headerStart, i).trim()
                val frameStart = i + 1
                var depth = 1
                i++
                while (i < n && depth > 0) {
                    if (body[i] == '{') depth++
                    else if (body[i] == '}') depth--
                    i++
                }
                val frameBody = body.substring(frameStart, (i - 1).coerceAtLeast(frameStart)).trim()
                if (stopHeader.isNotEmpty()) {
                    val decls = splitTopLevelSemicolonsStatic(frameBody)
                    if (decls.isNotEmpty()) {
                        sb.append("  ").append(stopHeader).append(" {\n")
                        for (d in decls) sb.append("    ").append(d).append(";\n")
                        sb.append("  }\n")
                    }
                }
            }
            return sb.toString()
        }

        private fun parseCssRulesAndKeyframesStatic(
            cssText: String
        ): Pair<List<ParsedRule>, Map<String, Map<String, Map<String, String>>>> {
            val rules = mutableListOf<ParsedRule>()
            val keyframes = linkedMapOf<String, Map<String, Map<String, String>>>()
            val cleaned = stripSassCommentsPreservingStringsStatic(cssText)

            fun parseCssBlock(
                blockText: String,
                activeMinW: Int?,
                activeMaxW: Int?,
                activeBackendMask: Int
            ) {
                var i = 0
                val n = blockText.length
                while (i < n) {
                    while (i < n && blockText[i].isWhitespace()) i++
                    if (i >= n) break

                    val headerStart = i
                    var inSingle = false
                    var inDouble = false
                    while (i < n) {
                        val ch = blockText[i]
                        if (inSingle) {
                            if (ch == '\\' && i + 1 < n) { i += 2; continue }
                            if (ch == '\'') inSingle = false
                        } else if (inDouble) {
                            if (ch == '\\' && i + 1 < n) { i += 2; continue }
                            if (ch == '"') inDouble = false
                        } else {
                            if (ch == '\'') inSingle = true
                            else if (ch == '"') inDouble = true
                            else if (ch == '{') break
                        }
                        i++
                    }
                    if (i >= n) break
                    val header = blockText.substring(headerStart, i).trim()
                    val bodyStart = i + 1
                    var depth = 1
                    i++
                    var bSingle = false
                    var bDouble = false
                    while (i < n && depth > 0) {
                        val ch = blockText[i]
                        if (bSingle) {
                            if (ch == '\\' && i + 1 < n) { i += 2; continue }
                            if (ch == '\'') bSingle = false
                        } else if (bDouble) {
                            if (ch == '\\' && i + 1 < n) { i += 2; continue }
                            if (ch == '"') bDouble = false
                        } else {
                            when (ch) {
                                '\'' -> bSingle = true
                                '"' -> bDouble = true
                                '{' -> depth++
                                '}' -> depth--
                            }
                        }
                        i++
                    }
                    val body = blockText.substring(bodyStart, (i - 1).coerceAtLeast(bodyStart)).trim()

                    when {
                        header.startsWith("@keyframes", ignoreCase = true) ||
                            header.startsWith("@-webkit-keyframes", ignoreCase = true) -> {
                            val animName = header.replace(
                                Regex("^@(?:-[a-z]+-)?keyframes\\s+", RegexOption.IGNORE_CASE),
                                ""
                            ).trim().trim('"', '\'')
                            val frames = parseKeyframeStepsStatic(body)
                            if (animName.isNotEmpty() && frames.isNotEmpty()) {
                                keyframes[animName] = frames
                            }
                        }
                        header.startsWith("@media", ignoreCase = true) -> {
                            val minW = Regex("min-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
                                .find(header)?.groupValues?.get(1)?.toIntOrNull()
                            val maxW = Regex("max-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
                                .find(header)?.groupValues?.get(1)?.toIntOrNull()
                            parseCssBlock(
                                blockText = body,
                                activeMinW = minW ?: activeMinW,
                                activeMaxW = maxW ?: activeMaxW,
                                activeBackendMask = activeBackendMask
                            )
                        }
                        else -> {
                            val dirMask = GenericUiBackendStyleMask.parseDirectiveHeader(header)
                            if (dirMask != null) {
                                val nextMask = activeBackendMask and dirMask
                                if (nextMask != 0) {
                                    parseCssBlock(
                                        blockText = body,
                                        activeMinW = activeMinW,
                                        activeMaxW = activeMaxW,
                                        activeBackendMask = nextMask
                                    )
                                }
                            } else if (header.isNotEmpty()) {
                                val decls = parseDeclarationsStatic(body)
                                val selectors = splitTopLevelCommasStatic(header)
                                for (rawSel in selectors) {
                                    val sel = sanitizeSelectorOrNull(rawSel) ?: continue
                                    rules.add(
                                        ParsedRule(
                                            selector = sel,
                                            declarations = decls,
                                            specificity = computeSpecificityStatic(sel),
                                            mediaMinWidthPx = activeMinW,
                                            mediaMaxWidthPx = activeMaxW,
                                            backendMask = activeBackendMask
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            parseCssBlock(
                blockText = cleaned,
                activeMinW = null,
                activeMaxW = null,
                activeBackendMask = GenericUiBackendStyleMask.ALL
            )
            return rules to keyframes
        }

        private fun parseKeyframeStepsStatic(body: String): Map<String, Map<String, String>> {
            val frames = linkedMapOf<String, LinkedHashMap<String, String>>()
            var i = 0
            val n = body.length
            while (i < n) {
                while (i < n && body[i].isWhitespace()) i++
                if (i >= n) break
                val stepStart = i
                while (i < n && body[i] != '{') i++
                if (i >= n) break
                val stepHeader = body.substring(stepStart, i).trim()
                val frameBodyStart = i + 1
                var depth = 1
                i++
                while (i < n && depth > 0) {
                    if (body[i] == '{') depth++
                    else if (body[i] == '}') depth--
                    i++
                }
                val frameBody = body.substring(frameBodyStart, (i - 1).coerceAtLeast(frameBodyStart)).trim()
                val decls = parseDeclarationsStatic(frameBody)
                if (decls.isNotEmpty() && stepHeader.isNotEmpty()) {
                    for (stop in splitTopLevelCommasStatic(stepHeader)) {
                        val cleanStop = stop.trim()
                        if (cleanStop.isNotEmpty()) {
                            frames.getOrPut(cleanStop) { LinkedHashMap() }.putAll(decls)
                        }
                    }
                }
            }
            return frames
        }

        internal fun parseDeclarationsStatic(body: String): Map<String, String> {
            val decls = linkedMapOf<String, String>()
            for (stmt in splitTopLevelSemicolonsStatic(body)) {
                val idx = stmt.indexOf(':')
                if (idx > 0) {
                    val prop = stmt.substring(0, idx).trim().lowercase(Locale.US)
                    val value = stmt.substring(idx + 1).trim()
                    if (prop.isNotEmpty() && value.isNotEmpty()) {
                        decls[prop] = value
                    }
                }
            }
            return decls
        }

        private fun computeSpecificityStatic(selector: String): Int {
            val cleaned = selector.trim()
            if (cleaned == "*") return 0
            val withoutNotWrapper = cleaned
                .replace(Regex(":not\\(", RegexOption.IGNORE_CASE), "(")
                .replace("genericui\\:", "genericui_")
                .replace("genericui:", "genericui_")
            val ids = withoutNotWrapper.count { it == '#' }
            val classes = withoutNotWrapper.count { it == '.' || it == '[' || it == ':' }
            val elements = withoutNotWrapper.split(Regex("[\\s>+~]+"))
                .map { it.replace(Regex(":.*"), "").replace("(", "").replace(")", "") }
                .count { token ->
                    token.isNotEmpty() &&
                        token != "*" &&
                        !token.startsWith(".") &&
                        !token.startsWith("#")
                }
            return ids * 10000 + classes * 100 + elements
        }

        internal fun splitTopLevelSemicolonsStatic(raw: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var depth = 0
            var inSingle = false
            var inDouble = false
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (inSingle) {
                    current.append(ch)
                    if (ch == '\\' && i + 1 < raw.length) { current.append(raw[++i]) }
                    else if (ch == '\'') inSingle = false
                } else if (inDouble) {
                    current.append(ch)
                    if (ch == '\\' && i + 1 < raw.length) { current.append(raw[++i]) }
                    else if (ch == '"') inDouble = false
                } else {
                    when (ch) {
                        '\'' -> { inSingle = true; current.append(ch) }
                        '"' -> { inDouble = true; current.append(ch) }
                        '(' -> { depth++; current.append(ch) }
                        ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(ch) }
                        ';' -> if (depth == 0) {
                            val item = current.toString().trim()
                            if (item.isNotEmpty()) out.add(item)
                            current.clear()
                        } else {
                            current.append(ch)
                        }
                        else -> current.append(ch)
                    }
                }
                i++
            }
            val rem = current.toString().trim()
            if (rem.isNotEmpty()) out.add(rem)
            return out
        }

        internal fun splitTopLevelCommasStatic(raw: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var depth = 0
            var inSingle = false
            var inDouble = false
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (inSingle) {
                    current.append(ch)
                    if (ch == '\\' && i + 1 < raw.length) { current.append(raw[++i]) }
                    else if (ch == '\'') inSingle = false
                } else if (inDouble) {
                    current.append(ch)
                    if (ch == '\\' && i + 1 < raw.length) { current.append(raw[++i]) }
                    else if (ch == '"') inDouble = false
                } else {
                    when (ch) {
                        '\'' -> { inSingle = true; current.append(ch) }
                        '"' -> { inDouble = true; current.append(ch) }
                        '(' -> { depth++; current.append(ch) }
                        ')' -> { depth = (depth - 1).coerceAtLeast(0); current.append(ch) }
                        ',' -> if (depth == 0) {
                            val item = current.toString().trim()
                            if (item.isNotEmpty()) out.add(item)
                            current.clear()
                        } else {
                            current.append(ch)
                        }
                        else -> current.append(ch)
                    }
                }
                i++
            }
            val rem = current.toString().trim()
            if (rem.isNotEmpty()) out.add(rem)
            return out
        }

        private fun stripSassCommentsPreservingStringsStatic(source: String): String {
            val sb = StringBuilder(source.length)
            var i = 0
            val n = source.length
            var inSingle = false
            var inDouble = false
            while (i < n) {
                val ch = source[i]
                if (inSingle) {
                    sb.append(ch)
                    if (ch == '\\' && i + 1 < n) { sb.append(source[++i]) }
                    else if (ch == '\'') inSingle = false
                    i++; continue
                }
                if (inDouble) {
                    sb.append(ch)
                    if (ch == '\\' && i + 1 < n) { sb.append(source[++i]) }
                    else if (ch == '"') inDouble = false
                    i++; continue
                }
                if (ch == '\'') {
                    inSingle = true
                    sb.append(ch)
                    i++
                } else if (ch == '"') {
                    inDouble = true
                    sb.append(ch)
                    i++
                } else if (ch == '/' && i + 1 < n && source[i + 1] == '*') {
                    i += 2
                    while (i + 1 < n && !(source[i] == '*' && source[i + 1] == '/')) i++
                    i += 2
                } else if (ch == '/' && i + 1 < n && source[i + 1] == '/') {
                    i += 2
                    while (i < n && source[i] != '\n') i++
                } else {
                    sb.append(ch)
                    i++
                }
            }
            return sb.toString()
        }

        private fun extractSassMixinsStatic(source: String, mixins: MutableMap<String, SassMixin>): String {
            val sb = StringBuilder(source.length)
            val mixinRegex = Regex("""@mixin\s+([a-zA-Z0-9_-]+)\s*(?:\(([^)]*)\))?\s*\{""")
            var pos = 0
            while (pos < source.length) {
                val m = mixinRegex.find(source, pos)
                if (m == null) {
                    sb.append(source, pos, source.length)
                    break
                }
                sb.append(source, pos, m.range.first)
                val name = m.groupValues[1].trim()
                val paramsRaw = m.groupValues[2].trim()
                val params = if (paramsRaw.isEmpty()) {
                    emptyList()
                } else {
                    splitTopLevelCommasStatic(paramsRaw).mapNotNull { paramSpec ->
                        val cleanSpec = paramSpec.trim()
                        if (!cleanSpec.startsWith("$")) null
                        else {
                            val colonIdx = cleanSpec.indexOf(':')
                            if (colonIdx > 1) {
                                cleanSpec.substring(1, colonIdx).trim() to cleanSpec.substring(colonIdx + 1).trim()
                            } else {
                                cleanSpec.substring(1).trim() to null
                            }
                        }
                    }
                }
                val bodyStart = m.range.last + 1
                var i = bodyStart
                var depth = 1
                var inSingle = false
                var inDouble = false
                while (i < source.length && depth > 0) {
                    val ch = source[i]
                    if (inSingle) {
                        if (ch == '\\' && i + 1 < source.length) { i += 2; continue }
                        if (ch == '\'') inSingle = false
                    } else if (inDouble) {
                        if (ch == '\\' && i + 1 < source.length) { i += 2; continue }
                        if (ch == '"') inDouble = false
                    } else {
                        when (ch) {
                            '\'' -> inSingle = true
                            '"' -> inDouble = true
                            '{' -> depth++
                            '}' -> depth--
                        }
                    }
                    i++
                }
                val body = source.substring(bodyStart, (i - 1).coerceAtLeast(bodyStart))
                mixins[name] = SassMixin(params, body)
                pos = i
            }
            return sb.toString()
        }

        private fun expandSassMixinsStatic(source: String, mixins: Map<String, SassMixin>): String {
            if (mixins.isEmpty()) return source
            val includeRegex = Regex("""@include\s+([a-zA-Z0-9_-]+)\s*(?:\(([^)]*)\))?\s*;""")
            var current = source
            repeat(12) {
                var changed = false
                current = includeRegex.replace(current) { match ->
                    val name = match.groupValues[1].trim()
                    val mixin = mixins[name] ?: return@replace match.value
                    changed = true
                    val argStr = match.groupValues[2].trim()
                    val providedArgs = if (argStr.isEmpty()) emptyList() else splitTopLevelCommasStatic(argStr)
                    var expandedBody = mixin.body
                    val paramBindings = linkedMapOf<String, String>()
                    mixin.params.forEachIndexed { index, (paramName, defaultVal) ->
                        val bound = providedArgs.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultVal
                        if (bound != null) {
                            paramBindings[paramName] = bound
                        }
                    }
                    for ((paramName, paramVal) in paramBindings.entries.sortedByDescending { it.key.length }) {
                        expandedBody = substituteSassVariablesInExprStatic(expandedBody, mapOf(paramName to paramVal))
                    }
                    expandedBody
                }
                if (!changed) return current
            }
            return current
        }

        private fun extractAndEvaluateVariablesStatic(
            source: String,
            variables: MutableMap<String, String>,
            topLevelOnly: Boolean
        ): String {
            val varDeclRegex = Regex("""(?<![a-zA-Z0-9_-])\$([a-zA-Z0-9_-]+)\s*:\s*([^;{}]+);""")
            return varDeclRegex.replace(source) { match ->
                val varName = match.groupValues[1].trim()
                var rhs = match.groupValues[2].trim()
                val isDefault = Regex("""\s+!default\s*$""", RegexOption.IGNORE_CASE).containsMatchIn(rhs)
                rhs = rhs.replace(Regex("""\s+!(?:default|global)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                if (!isDefault || varName !in variables) {
                    val evaluated = evaluateSassValueExprStatic(rhs, variables, forceArithmetic = true)
                    variables[varName] = evaluated
                }
                ""
            }
        }

        private fun evaluateSassExpressionsInTextStatic(
            source: String,
            variables: Map<String, String>
        ): String {
            // Preserve division intent for `$var / rhs` or `lhs / $var` without disturbing CSS shorthands like `aspect-ratio: 16 / 9`
            val sassDivisionRegex = Regex(
                """(\$[a-zA-Z0-9_-]+\s*/\s*(?:\$[a-zA-Z0-9_-]+|-?\d+(?:\.\d+)?[a-z%]*)|-?\d+(?:\.\d+)?[a-z%]*\s*/\s*\$[a-zA-Z0-9_-]+)"""
            )
            val preWrapped = sassDivisionRegex.replace(source) { m -> "(${m.groupValues[1].trim()})" }

            // Substitute #{...} interpolations and $var tokens line/value-wise safely
            val out = StringBuilder(preWrapped.length)
            var i = 0
            val n = preWrapped.length
            var inSingle = false
            var inDouble = false
            while (i < n) {
                val ch = preWrapped[i]
                if (ch == '#' && i + 1 < n && preWrapped[i + 1] == '{') {
                    val end = preWrapped.indexOf('}', i + 2)
                    if (end > i + 1) {
                        val innerExpr = preWrapped.substring(i + 2, end).trim()
                        out.append(evaluateSassValueExprStatic(innerExpr, variables, forceArithmetic = true))
                        i = end + 1
                        continue
                    }
                }
                if (inSingle) {
                    out.append(ch)
                    if (ch == '\\' && i + 1 < n) out.append(preWrapped[++i])
                    else if (ch == '\'') inSingle = false
                    i++; continue
                }
                if (inDouble) {
                    out.append(ch)
                    if (ch == '\\' && i + 1 < n) out.append(preWrapped[++i])
                    else if (ch == '"') inDouble = false
                    i++; continue
                }
                if (ch == '\'') {
                    inSingle = true
                    out.append(ch)
                    i++
                } else if (ch == '"') {
                    inDouble = true
                    out.append(ch)
                    i++
                } else if (ch == '$') {
                    val start = i + 1
                    var j = start
                    while (j < n && (preWrapped[j].isLetterOrDigit() || preWrapped[j] == '_' || preWrapped[j] == '-')) j++
                    val varName = preWrapped.substring(start, j)
                    val replacement = variables[varName]
                    if (replacement != null) {
                        out.append(replacement)
                        i = j
                    } else {
                        out.append(ch)
                        i++
                    }
                } else {
                    out.append(ch)
                    i++
                }
            }

            val varSubstituted = out.toString()
            return evaluateSassFunctionsAndMathInTextStatic(varSubstituted, variables)
        }

        private fun evaluateSassValueExprStatic(
            expr: String,
            variables: Map<String, String>,
            forceArithmetic: Boolean
        ): String {
            var current = substituteSassVariablesInExprStatic(expr, variables)
            current = evaluateInnermostSassFunctionsStatic(current)
            if (forceArithmetic) {
                current = evaluateSassArithmeticOutsideCalcStatic(current, allowDashAndSlash = true)
            }
            return current.trim()
        }

        private fun evaluateSassFunctionsAndMathInTextStatic(
            source: String,
            variables: Map<String, String>
        ): String {
            var resolved = evaluateInnermostSassFunctionsStatic(source)
            // Evaluate safe parenthesized / explicit arithmetic expressions outside calc(...)
            resolved = evaluateSassArithmeticOutsideCalcStatic(resolved, allowDashAndSlash = false)
            return resolved
        }

        private fun substituteSassVariablesInExprStatic(expr: String, variables: Map<String, String>): String {
            var current = expr
            val interpRegex = Regex("""#\{\s*([^}]+)\s*\}""")
            repeat(8) {
                var changed = false
                current = interpRegex.replace(current) { m ->
                    changed = true
                    evaluateSassValueExprStatic(m.groupValues[1], variables, forceArithmetic = true)
                }
                val varRegex = Regex("""\$([a-zA-Z0-9_-]+)(?![a-zA-Z0-9_-])""")
                current = varRegex.replace(current) { m ->
                    val replacement = variables[m.groupValues[1]]
                    if (replacement != null) {
                        changed = true
                        replacement
                    } else {
                        m.value
                    }
                }
                if (!changed) return current
            }
            return current
        }

        private val INNERMOST_SASS_FN_REGEX = Regex(
            """\b(rgba|rgb|mix|lighten|darken|fade-in|opacify|fade-out|transparentize)\(\s*([^()]*)\s*\)""",
            RegexOption.IGNORE_CASE
        )

        private fun evaluateInnermostSassFunctionsStatic(input: String): String {
            var current = input
            repeat(12) {
                var changed = false
                current = INNERMOST_SASS_FN_REGEX.replace(current) { match ->
                    val fnName = match.groupValues[1].lowercase(Locale.US)
                    val argsRaw = match.groupValues[2].trim()
                    val evaluated = evaluateSingleSassFunctionCallStatic(fnName, argsRaw)
                    if (evaluated != null) {
                        changed = true
                        evaluated
                    } else {
                        match.value
                    }
                }
                if (!changed) return current
            }
            return current
        }

        private fun evaluateSingleSassFunctionCallStatic(fnName: String, rawArgs: String): String? {
            val args = splitTopLevelCommasStatic(rawArgs).map {
                evaluateSassArithmeticOutsideCalcStatic(it.trim(), allowDashAndSlash = true)
            }
            return when (fnName) {
                "rgb" -> {
                    if (args.size == 1 && args[0].contains(' ')) {
                        // Space syntax: rgb(r g b / a?)
                        val slashParts = args[0].split('/').map { it.trim() }
                        val rgbToks = slashParts[0].split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (rgbToks.size != 3) return null
                        val r = parseChannel255(rgbToks[0]) ?: return null
                        val g = parseChannel255(rgbToks[1]) ?: return null
                        val b = parseChannel255(rgbToks[2]) ?: return null
                        val alpha = slashParts.getOrNull(1)?.let { parseAlphaFactor(it) } ?: 1.0
                        formatSassColorStatic(SassColor(r, g, b, alpha), preferRgbaOnAlpha = alpha < 0.9999)
                    } else if (args.size == 3) {
                        val r = parseChannel255(args[0]) ?: return null
                        val g = parseChannel255(args[1]) ?: return null
                        val b = parseChannel255(args[2]) ?: return null
                        formatSassColorStatic(SassColor(r, g, b, 1.0), preferRgbaOnAlpha = false)
                    } else null
                }
                "rgba" -> when (args.size) {
                    2 -> {
                        val base = parseSassColorStatic(args[0]) ?: return null
                        val alpha = parseAlphaFactor(args[1]) ?: return null
                        formatSassColorStatic(base.copy(a = alpha), preferRgbaOnAlpha = true)
                    }
                    4 -> {
                        val r = parseChannel255(args[0]) ?: return null
                        val g = parseChannel255(args[1]) ?: return null
                        val b = parseChannel255(args[2]) ?: return null
                        val alpha = parseAlphaFactor(args[3]) ?: return null
                        formatSassColorStatic(SassColor(r, g, b, alpha), preferRgbaOnAlpha = true)
                    }
                    else -> null
                }
                "mix" -> {
                    if (args.size < 2 || args.size > 3) return null
                    val c1 = parseSassColorStatic(args[0]) ?: return null
                    val c2 = parseSassColorStatic(args[1]) ?: return null
                    val weight = if (args.size == 3) (parsePercentOrUnitRatio(args[2]) ?: 0.5) else 0.5
                    val p = weight * 2.0 - 1.0
                    val aDiff = c1.a - c2.a
                    val combinedAlphaWeight = if (p * aDiff == -1.0) p else (p + aDiff) / (1.0 + p * aDiff)
                    val w1 = ((combinedAlphaWeight + 1.0) / 2.0).coerceIn(0.0, 1.0)
                    val w2 = 1.0 - w1
                    val r = (c1.r * w1 + c2.r * w2).roundToInt().coerceIn(0, 255)
                    val g = (c1.g * w1 + c2.g * w2).roundToInt().coerceIn(0, 255)
                    val b = (c1.b * w1 + c2.b * w2).roundToInt().coerceIn(0, 255)
                    val a = (c1.a * weight + c2.a * (1.0 - weight)).coerceIn(0.0, 1.0)
                    formatSassColorStatic(SassColor(r, g, b, a), preferRgbaOnAlpha = a < 0.9999)
                }
                "lighten" -> adjustColorLightnessStatic(args, lighten = true)
                "darken" -> adjustColorLightnessStatic(args, lighten = false)
                "fade-in", "opacify" -> {
                    if (args.size != 2) return null
                    val c = parseSassColorStatic(args[0]) ?: return null
                    val delta = parseAlphaFactor(args[1]) ?: return null
                    val a = (c.a + delta).coerceIn(0.0, 1.0)
                    formatSassColorStatic(c.copy(a = a), preferRgbaOnAlpha = a < 0.9999)
                }
                "fade-out", "transparentize" -> {
                    if (args.size != 2) return null
                    val c = parseSassColorStatic(args[0]) ?: return null
                    val delta = parseAlphaFactor(args[1]) ?: return null
                    val a = (c.a - delta).coerceIn(0.0, 1.0)
                    formatSassColorStatic(c.copy(a = a), preferRgbaOnAlpha = true)
                }
                else -> null
            }
        }

        private fun adjustColorLightnessStatic(args: List<String>, lighten: Boolean): String? {
            if (args.size != 2) return null
            val c = parseSassColorStatic(args[0]) ?: return null
            val delta = parsePercentOrUnitRatio(args[1]) ?: return null
            val (h, s, l) = rgbToHslStatic(c.r, c.g, c.b)
            val newL = (if (lighten) l + delta else l - delta).coerceIn(0.0, 1.0)
            val (nr, ng, nb) = hslToRgbStatic(h, s, newL)
            return formatSassColorStatic(SassColor(nr, ng, nb, c.a), preferRgbaOnAlpha = c.a < 0.9999)
        }

        private fun parseChannel255(raw: String): Int? {
            val trimmed = raw.trim()
            if (trimmed.endsWith("%")) {
                val pct = trimmed.removeSuffix("%").trim().toDoubleOrNull() ?: return null
                return ((pct / 100.0) * 255.0).roundToInt().coerceIn(0, 255)
            }
            return trimmed.toDoubleOrNull()?.roundToInt()?.coerceIn(0, 255)
        }

        private fun parseAlphaFactor(raw: String): Double? {
            val trimmed = raw.trim()
            if (trimmed.endsWith("%")) {
                val pct = trimmed.removeSuffix("%").trim().toDoubleOrNull() ?: return null
                return (pct / 100.0).coerceIn(0.0, 1.0)
            }
            return trimmed.toDoubleOrNull()?.coerceIn(0.0, 1.0)
        }

        private fun parsePercentOrUnitRatio(raw: String): Double? {
            val trimmed = raw.trim()
            if (trimmed.endsWith("%")) {
                return ((trimmed.removeSuffix("%").trim().toDoubleOrNull() ?: return null) / 100.0).coerceIn(0.0, 1.0)
            }
            val num = trimmed.toDoubleOrNull() ?: return null
            return if (num <= 1.0 && '.' in trimmed) num.coerceIn(0.0, 1.0) else (num / 100.0).coerceIn(0.0, 1.0)
        }

        private fun parseSassColorStatic(raw: String): SassColor? {
            val clean = raw.trim().lowercase(Locale.US)
            when (clean) {
                "transparent" -> return SassColor(0, 0, 0, 0.0)
                "white" -> return SassColor(255, 255, 255, 1.0)
                "black" -> return SassColor(0, 0, 0, 1.0)
                "red" -> return SassColor(255, 0, 0, 1.0)
                "green" -> return SassColor(0, 128, 0, 1.0)
                "blue" -> return SassColor(0, 0, 255, 1.0)
                "gray", "grey" -> return SassColor(128, 128, 128, 1.0)
                "yellow" -> return SassColor(255, 255, 0, 1.0)
                "cyan", "aqua" -> return SassColor(0, 255, 255, 1.0)
                "magenta", "fuchsia" -> return SassColor(255, 0, 255, 1.0)
                "orange" -> return SassColor(255, 165, 0, 1.0)
            }
            if (clean.startsWith("#")) {
                val hex = clean.removePrefix("#")
                return when (hex.length) {
                    3 -> {
                        val r = hex.substring(0, 1).repeat(2).toIntOrNull(16) ?: return null
                        val g = hex.substring(1, 2).repeat(2).toIntOrNull(16) ?: return null
                        val b = hex.substring(2, 3).repeat(2).toIntOrNull(16) ?: return null
                        SassColor(r, g, b, 1.0)
                    }
                    4 -> {
                        val r = hex.substring(0, 1).repeat(2).toIntOrNull(16) ?: return null
                        val g = hex.substring(1, 2).repeat(2).toIntOrNull(16) ?: return null
                        val b = hex.substring(2, 3).repeat(2).toIntOrNull(16) ?: return null
                        val a = (hex.substring(3, 4).repeat(2).toIntOrNull(16) ?: return null) / 255.0
                        SassColor(r, g, b, a)
                    }
                    6 -> {
                        val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                        val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                        val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                        SassColor(r, g, b, 1.0)
                    }
                    8 -> {
                        val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                        val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                        val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                        val a = (hex.substring(6, 8).toIntOrNull(16) ?: return null) / 255.0
                        SassColor(r, g, b, a)
                    }
                    else -> null
                }
            }
            val rgbMatch = Regex("""^rgba?\(\s*([0-9.%]+)\s*,\s*([0-9.%]+)\s*,\s*([0-9.%]+)(?:\s*,\s*([0-9.%]+))?\s*\)$""")
                .matchEntire(clean)
            if (rgbMatch != null) {
                val r = parseChannel255(rgbMatch.groupValues[1]) ?: return null
                val g = parseChannel255(rgbMatch.groupValues[2]) ?: return null
                val b = parseChannel255(rgbMatch.groupValues[3]) ?: return null
                val a = rgbMatch.groupValues[4].takeIf { it.isNotEmpty() }?.let { parseAlphaFactor(it) } ?: 1.0
                return SassColor(r, g, b, a)
            }
            return null
        }

        private fun formatSassColorStatic(color: SassColor, preferRgbaOnAlpha: Boolean): String {
            val r = color.r.coerceIn(0, 255)
            val g = color.g.coerceIn(0, 255)
            val b = color.b.coerceIn(0, 255)
            val a = color.a.coerceIn(0.0, 1.0)
            if (preferRgbaOnAlpha || a < 0.9999) {
                val roundedA = ((a * 1000.0).roundToInt() / 1000.0)
                val alphaStr = if (abs(roundedA - roundedA.toInt()) < 1e-6) {
                    roundedA.toInt().toString()
                } else {
                    roundedA.toString()
                }
                return "rgba($r, $g, $b, $alphaStr)"
            }
            return "#%02x%02x%02x".format(Locale.US, r, g, b)
        }

        private fun rgbToHslStatic(r255: Int, g255: Int, b255: Int): Triple<Double, Double, Double> {
            val r = r255 / 255.0
            val g = g255 / 255.0
            val b = b255 / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val l = (max + min) / 2.0
            if (abs(max - min) < 1e-9) {
                return Triple(0.0, 0.0, l)
            }
            val d = max - min
            val s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
            val h = when (max) {
                r -> (g - b) / d + (if (g < b) 6.0 else 0.0)
                g -> (b - r) / d + 2.0
                else -> (r - g) / d + 4.0
            } / 6.0
            return Triple(h, s, l)
        }

        private fun hslToRgbStatic(h: Double, s: Double, l: Double): Triple<Int, Int, Int> {
            if (s <= 1e-9) {
                val v = (l * 255.0).roundToInt().coerceIn(0, 255)
                return Triple(v, v, v)
            }
            fun hue2rgb(p: Double, q: Double, tIn: Double): Double {
                var t = tIn
                if (t < 0.0) t += 1.0
                if (t > 1.0) t -= 1.0
                return when {
                    t < 1.0 / 6.0 -> p + (q - p) * 6.0 * t
                    t < 0.5 -> q
                    t < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6.0
                    else -> p
                }
            }
            val q = if (l < 0.5) l * (1.0 + s) else l + s - l * s
            val p = 2.0 * l - q
            val r = (hue2rgb(p, q, h + 1.0 / 3.0) * 255.0).roundToInt().coerceIn(0, 255)
            val g = (hue2rgb(p, q, h) * 255.0).roundToInt().coerceIn(0, 255)
            val b = (hue2rgb(p, q, h - 1.0 / 3.0) * 255.0).roundToInt().coerceIn(0, 255)
            return Triple(r, g, b)
        }

        private fun evaluateSassArithmeticOutsideCalcStatic(input: String, allowDashAndSlash: Boolean): String {
            // Protect calc(...) and url(...) spans so CSS calc() and URLs stay untouched
            val protectedSpans = mutableListOf<String>()
            val protectedRegex = Regex("""\b(?:calc|url|var)\([^()]*\)""", RegexOption.IGNORE_CASE)
            var working = input
            repeat(4) {
                working = protectedRegex.replace(working) { m ->
                    val token = "__GUI_PROTECTED_${protectedSpans.size}__"
                    protectedSpans.add(m.value)
                    token
                }
            }

            // Evaluate parenthesized math not preceded by function name characters: (10px + 4px) or ($w / 2)
            val parenMathRegex = Regex("""(?<![a-zA-Z0-9_-])\(\s*([^()]+?)\s*\)""")
            repeat(6) {
                var changed = false
                working = parenMathRegex.replace(working) { m ->
                    val inner = evaluateBinaryMathSequenceStatic(m.groupValues[1], allowDashAndSlash = true)
                    if (inner != null) {
                        changed = true
                        inner
                    } else {
                        m.value
                    }
                }
                if (!changed) return@repeat
            }

            val finalMath = if (allowDashAndSlash) {
                evaluateBinaryMathSequenceStatic(working, allowDashAndSlash = true) ?: working
            } else {
                // Only evaluate explicit + / * or spaced binary subtraction `-` in CSS property values (leaving aspect-ratio 16 / 9 and shorthands intact)
                val mulAddRegex = Regex("""(?<=^|[\s:(,])(-?\d+(?:\.\d+)?[a-z%]*)(?:\s*([+*])\s*|\s+(-)\s+)(-?\d+(?:\.\d+)?[a-z%]*)""")
                var cur = working
                repeat(4) {
                    var changed = false
                    cur = mulAddRegex.replace(cur) { m ->
                        val opChar = (m.groupValues[2].ifEmpty { m.groupValues[3] }).firstOrNull() ?: return@replace m.value
                        val eval = evaluateBinaryOpStatic(m.groupValues[1], opChar, m.groupValues[4])
                        if (eval != null) {
                            changed = true
                            eval
                        } else {
                            m.value
                        }
                    }
                    if (!changed) return@repeat
                }
                cur
            }

            var restored = finalMath
            for (idx in protectedSpans.indices.reversed()) {
                restored = restored.replace("__GUI_PROTECTED_${idx}__", protectedSpans[idx])
            }
            return restored
        }

        private fun evaluateBinaryMathSequenceStatic(expr: String, allowDashAndSlash: Boolean): String? {
            val trimmed = expr.trim()
            if (trimmed.isEmpty()) return null
            val ops = if (allowDashAndSlash) "[*/+-]" else "[+*]"
            val binRegex = Regex("""^\s*(-?\d+(?:\.\d+)?[a-z%]*)\s*($ops)\s*(-?\d+(?:\.\d+)?[a-z%]*)\s*$""")
            val m = binRegex.matchEntire(trimmed) ?: return null
            return evaluateBinaryOpStatic(m.groupValues[1], m.groupValues[2][0], m.groupValues[3])
        }

        private fun evaluateBinaryOpStatic(leftTok: String, op: Char, rightTok: String): String? {
            val numUnitRegex = Regex("""^(-?\d+(?:\.\d+)?)([a-z%]*)$""", RegexOption.IGNORE_CASE)
            val lMatch = numUnitRegex.matchEntire(leftTok.trim()) ?: return null
            val rMatch = numUnitRegex.matchEntire(rightTok.trim()) ?: return null
            val lv = lMatch.groupValues[1].toDoubleOrNull() ?: return null
            val rv = rMatch.groupValues[1].toDoubleOrNull() ?: return null
            val lu = lMatch.groupValues[2]
            val ru = rMatch.groupValues[2]
            val resultUnit: String
            val resVal: Double
            when (op) {
                '+' -> {
                    if (lu.isNotEmpty() && ru.isNotEmpty() && !lu.equals(ru, ignoreCase = true)) return null
                    resultUnit = lu.ifEmpty { ru }
                    resVal = lv + rv
                }
                '-' -> {
                    if (lu.isNotEmpty() && ru.isNotEmpty() && !lu.equals(ru, ignoreCase = true)) return null
                    resultUnit = lu.ifEmpty { ru }
                    resVal = lv - rv
                }
                '*' -> {
                    if (lu.isNotEmpty() && ru.isNotEmpty()) return null
                    resultUnit = lu.ifEmpty { ru }
                    resVal = lv * rv
                }
                '/' -> {
                    if (abs(rv) < 1e-12) return null
                    resultUnit = when {
                        lu.isNotEmpty() && ru.isEmpty() -> lu
                        lu.equals(ru, ignoreCase = true) -> ""
                        else -> return null
                    }
                    resVal = lv / rv
                }
                else -> return null
            }
            val rounded = ((resVal * 10000.0).roundToInt() / 10000.0)
            val numStr = if (abs(rounded - rounded.toLong()) < 1e-6) {
                rounded.toLong().toString()
            } else {
                rounded.toString()
            }
            return "$numStr$resultUnit"
        }

        internal fun resolveSassImportsStatic(file: File, visited: MutableSet<String>): String {
            val raw = file.readText(Charsets.UTF_8)
            val importRegex = Regex("""@(?:import|use)\s+["']([^"']+)["'](?:\s+as\s+[^;]+)?\s*;""")
            return importRegex.replace(raw) { match ->
                val importTarget = match.groupValues[1].trim()
                if (importTarget.startsWith("http://") || importTarget.startsWith("https://") || importTarget.endsWith(".css")) {
                    match.value
                } else {
                    val parentDir = file.parentFile ?: return@replace ""
                    val candidates = listOf(
                        File(parentDir, importTarget),
                        File(parentDir, "$importTarget.scss"),
                        File(parentDir, "$importTarget.sass"),
                        File(parentDir, "_$importTarget.scss"),
                        File(parentDir, "_$importTarget.sass"),
                        File(parentDir, "_$importTarget")
                    )
                    val resolved = candidates.firstOrNull { it.exists() && it.isFile }
                    if (resolved != null && visited.add(resolved.canonicalPath)) {
                        resolveSassImportsStatic(resolved, visited)
                    } else {
                        ""
                    }
                }
            }
        }
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

    private fun buildBootstrapVariablesBlock(): String =
        """
        |:root {
        |  --bs-primary: #0d6efd;
        |  --bs-secondary: #6c757d;
        |  --bs-success: #198754;
        |  --bs-info: #0dcaf0;
        |  --bs-warning: #ffc107;
        |  --bs-danger: #dc3545;
        |  --bs-light: #f8f9fa;
        |  --bs-dark: #212529;
        |  --bs-border-radius-sm: 4px;
        |  --bs-border-radius: 6px;
        |  --bs-border-radius-lg: 8px;
        |  --bs-border-radius-xl: 12px;
        |  --bs-border-radius-2xl: 16px;
        |  --bs-border-radius-pill: 9999px;
        |}
        """.trimMargin()
}
