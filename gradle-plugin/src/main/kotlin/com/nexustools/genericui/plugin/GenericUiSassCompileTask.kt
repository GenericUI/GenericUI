package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Cacheable Gradle task (`compileGenericUiSass`) that compiles `.scss`, `.sass`, and `.css`
 * stylesheets into normalized `.css` bundles AND precompiled `.kt` (`PrecompiledStyleSheet`)
 * source files so applications have zero runtime SASS/CSS parsing overhead.
 */
@CacheableTask
abstract class GenericUiSassCompileTask : DefaultTask() {

    private data class ParsedRule(
        val selector: String,
        val declarations: Map<String, String>,
        val specificity: Int,
        val mediaMinWidthPx: Int? = null,
        val mediaMaxWidthPx: Int? = null
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

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val webCssOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val kotlinOutputDirectory: DirectoryProperty

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

        val compiledFiles = mutableListOf<String>()
        val webCssFiles = mutableListOf<String>()
        val combinedWebCssChunks = mutableListOf<String>()
        val generatedKotlinFiles = mutableListOf<String>()
        val bootstrapHeader = if (includeBootstrapVariables.getOrElse(true)) {
            buildBootstrapVariablesBlock()
        } else {
            ""
        }

        val srcDir = sourceDirectory.orNull?.asFile
        if (compileSass.getOrElse(true) && srcDir != null && srcDir.exists()) {
            val resourceIndex = discoverLocalResources(srcDir)
            srcDir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith("_") }
                .filter { it.extension.lowercase() in setOf("scss", "sass", "css") }
                .forEach { file ->
                    val relativePath = file.relativeTo(srcDir).path
                    val targetRelativePath = relativePath.replace(Regex("\\.(scss|sass)$", RegexOption.IGNORE_CASE), ".css")
                    val normalizedRelPath = targetRelativePath.replace('\\', '/')
                    val targetFile = File(outDir, targetRelativePath)
                    targetFile.parentFile?.mkdirs()

                    val rawContent = resolveSassImports(file, mutableSetOf(file.canonicalPath))
                    val nativeResolvedContent = resolveResourceTokens(
                        source = bootstrapHeader + "\n" + rawContent,
                        forWeb = false,
                        resources = resourceIndex
                    )
                    val processedCss = transformSassToCss(nativeResolvedContent, minify.getOrElse(false))
                    targetFile.writeText(processedCss)
                    compiledFiles.add(normalizedRelPath)

                    if (shouldEmitWebCss) {
                        val webResolvedContent = resolveResourceTokens(
                            source = bootstrapHeader + "\n" + rawContent,
                            forWeb = true,
                            resources = resourceIndex
                        )
                        val webProcessedCss = transformSassToCss(webResolvedContent, minify.getOrElse(false))
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

        if (compiledFiles.isEmpty() && bootstrapHeader.isNotEmpty()) {
            val defaultThemeFile = File(outDir, "genericui-bootstrap-defaults.css")
            val css = transformSassToCss(bootstrapHeader, minify.getOrElse(false))
            defaultThemeFile.writeText(css)
            compiledFiles.add("genericui-bootstrap-defaults.css")

            if (shouldEmitWebCss) {
                val webDefaultThemeFile = File(webCssOutDir, "genericui-bootstrap-defaults.css")
                webDefaultThemeFile.parentFile?.mkdirs()
                webDefaultThemeFile.writeText(css)
                webCssFiles.add("genericui-bootstrap-defaults.css")
                combinedWebCssChunks.add(css)
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

    private fun resolveSassImports(file: File, visited: MutableSet<String>): String {
        val raw = file.readText()
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
                    resolveSassImports(resolved, visited)
                } else {
                    ""
                }
            }
        }
    }

    internal fun transformSassToCss(source: String, minifyOutput: Boolean): String {
        val withoutComments = source.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .lines()
            .map { line ->
                val commentIdx = line.indexOf("//")
                if (commentIdx >= 0 && !line.substring(0, commentIdx).contains("http") && !line.substring(0, commentIdx).contains("data:")) {
                    line.substring(0, commentIdx)
                } else {
                    line
                }
            }
            .joinToString("\n")

        val variables = linkedMapOf<String, String>()
        val variableRegex = Regex("""\$([a-zA-Z0-9_-]+)\s*:\s*([^;{}]+);""")

        variableRegex.findAll(withoutComments).forEach { match ->
            variables[match.groupValues[1]] = match.groupValues[2].trim()
        }

        var substituted = withoutComments.replace(variableRegex, "")
        for ((name, value) in variables.entries.sortedByDescending { it.key.length }) {
            substituted = substituted.replace("$$name", value)
        }

        val outCss = StringBuilder()
        flattenBlock(
            source = substituted,
            parentSelectors = emptyList(),
            activeMediaQuery = null,
            output = outCss
        )
        val flattened = compileRulesToCss(outCss.toString().trim())

        return if (minifyOutput) {
            flattened
                .replace(Regex("""\s+"""), " ")
                .replace(Regex("""\s*([{}:;,])\s*"""), "$1")
                .trim()
        } else {
            flattened + "\n"
        }
    }

    internal fun compileRulesToCss(cssOrSelectors: String): String =
        Regex("(?<!\\\\)genericui:([a-zA-Z0-9_-]+)").replace(cssOrSelectors, "genericui\\\\:$1")

    private fun flattenBlock(
        source: String,
        parentSelectors: List<String>,
        activeMediaQuery: String?,
        output: StringBuilder
    ) {
        val declarations = mutableListOf<String>()
        val nestedBlocks = mutableListOf<Pair<String, String>>()

        var i = 0
        val n = source.length
        var tokenStart = 0
        while (i < n) {
            when (source[i]) {
                ';' -> {
                    val decl = source.substring(tokenStart, i).trim()
                    if (decl.isNotEmpty()) {
                        declarations.add(decl)
                    }
                    i++
                    tokenStart = i
                }
                '{' -> {
                    val header = source.substring(tokenStart, i).trim()
                    val bodyStart = i + 1
                    var depth = 1
                    i++
                    while (i < n && depth > 0) {
                        if (source[i] == '{') depth++
                        else if (source[i] == '}') depth--
                        i++
                    }
                    val body = source.substring(bodyStart, (i - 1).coerceAtLeast(bodyStart))
                    if (header.isNotEmpty()) {
                        nestedBlocks.add(header to body)
                    }
                    tokenStart = i
                }
                else -> i++
            }
        }
        val trailing = source.substring(tokenStart, n).trim()
        if (trailing.isNotEmpty() && trailing.contains(":") && !trailing.contains("{")) {
            declarations.add(trailing.removeSuffix(";").trim())
        }

        if (declarations.isNotEmpty() && parentSelectors.isNotEmpty()) {
            val selectorLine = compileRulesToCss(parentSelectors.joinToString(", "))
            val declBody = declarations.joinToString("; ") { it.trim() } + ";"
            if (activeMediaQuery != null) {
                output.append(activeMediaQuery).append(" {\n")
                output.append("  ").append(selectorLine).append(" { ").append(declBody).append(" }\n")
                output.append("}\n")
            } else {
                output.append(selectorLine).append(" {\n")
                for (d in declarations) {
                    output.append("  ").append(d).append(";\n")
                }
                output.append("}\n")
            }
        }

        for ((header, body) in nestedBlocks) {
            if (header.startsWith("@keyframes", ignoreCase = true)) {
                output.append(header).append(" {\n").append(body.trim()).append("\n}\n")
            } else if (header.startsWith("@media", ignoreCase = true)) {
                flattenBlock(
                    source = body,
                    parentSelectors = parentSelectors,
                    activeMediaQuery = header,
                    output = output
                )
            } else {
                val childSelectors = combineSelectors(parentSelectors, header)
                flattenBlock(
                    source = body,
                    parentSelectors = childSelectors,
                    activeMediaQuery = activeMediaQuery,
                    output = output
                )
            }
        }
    }

    private fun combineSelectors(parents: List<String>, rawChildHeader: String): List<String> {
        val children = rawChildHeader.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parents.isEmpty()) return children
        val combined = mutableListOf<String>()
        for (p in parents) {
            for (c in children) {
                if (c.contains("&")) {
                    combined.add(c.replace("&", p))
                } else {
                    combined.add("$p $c")
                }
            }
        }
        return combined
    }

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
                            drawables.putIfAbsent(ident, file.name)
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
                val fileName = resources.drawables[id] ?: if ('.' in rawName) rawName else "$id.svg"
                "url(\"assets/drawable/$fileName\")"
            } else {
                "url(\"res://drawable/$id\")"
            }
        }

        // 2. Resolve bare @drawable/<name>
        val bareDrawableRegex = Regex("""@drawable/([a-zA-Z0-9_-]+)""")
        resolved = bareDrawableRegex.replace(resolved) { m ->
            val id = sanitizeResId(m.groupValues[1])
            if (forWeb) {
                val fileName = resources.drawables[id] ?: "$id.svg"
                "url(\"assets/drawable/$fileName\")"
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
        cssResourcePath: String = "$stylesheetName.css"
    ): String {
        val normalizedCss = resolveResourceTokens(cssText, forWeb = false)
        val (rules, keyframes) = parseCssRulesAndKeyframes(normalizedCss)
        val sortedRules = rules.sortedBy { it.specificity }

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
                appendLine("    override val compiledRules: List<CompiledStyleRule> = listOf(")
                for ((ruleIdx, rule) in sortedRules.withIndex()) {
                    val comma = if (ruleIdx < sortedRules.lastIndex) "," else ""
                    val parsedSegments = parseSelectorSegmentsAtBuildTime(rule.selector)
                    val subjectPseudo = parsedSegments.lastOrNull()?.pseudoClasses?.firstOrNull()
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
                            appendLine("                CompiledSelectorSegment(tagName = $tagExpr, id = $idExpr, classes = $classesExpr, pseudoClasses = $pseudosExpr, combinatorToLeft = $combExpr)$segComma")
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
                    appendLine("            specificity = ${rule.specificity},")
                    appendLine("            sourceOrder = $ruleIdx,")
                    appendLine("            mediaMinWidthPx = ${rule.mediaMinWidthPx ?: "null"},")
                    appendLine("            mediaMaxWidthPx = ${rule.mediaMaxWidthPx ?: "null"},")
                    appendLine("            pseudoClass = ${subjectPseudo?.let { escapeKotlinString(it) } ?: "null"}")
                    appendLine("        )$comma")
                }
                appendLine("    )")
            }
            appendLine()
            if (keyframes.isEmpty()) {
                appendLine("    override val keyframes: Map<String, Map<String, Map<String, String>>> = emptyMap()")
            } else {
                appendLine("    override val keyframes: Map<String, Map<String, Map<String, String>>> = mapOf(")
                val kfEntries = keyframes.entries.toList()
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
            appendLine()
            appendLine("    override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)")
            appendLine()
            appendLine("    fun install(app: IApplication) {")
            appendLine("        BlinkStyleEngine.registerStyleSheet(this)")
            appendLine("        StyleSheetRegistry.register(name, cssResourcePath, this)")
            appendLine("        attachTo(app)")
            appendLine("    }")
            appendLine()
            appendLine("    init {")
            appendLine("        BlinkStyleEngine.registerStyleSheet(this)")
            appendLine("        StyleSheetRegistry.register(name, cssResourcePath, this)")
            appendLine("    }")
            appendLine("}")
        }
    }

    private data class BuildTimeSelectorSegment(
        val tagName: String?,
        val id: String?,
        val classes: List<String>,
        val pseudoClasses: List<String>,
        val combinatorToLeft: String?
    )

    private val NOT_DISABLED_REGEX =
        Regex(":not\\(\\s*(?::disabled|\\[\\s*disabled(?:\\s*=[^]]*)?\\s*\\]|\\.disabled)\\s*\\)", RegexOption.IGNORE_CASE)
    private val DISABLED_PSEUDO_OR_ATTR_REGEX =
        Regex("(?::disabled\\b|\\[\\s*disabled(?:\\s*=[^]]*)?\\s*\\])", RegexOption.IGNORE_CASE)

    private fun sanitizeSelectorOrNull(rawSelector: String): String? {
        val trimmed = rawSelector.trim()
        if (trimmed.isEmpty()) return null
        val withoutNotDisabled = trimmed.replace(NOT_DISABLED_REGEX, "").trim()
        if (withoutNotDisabled.isEmpty()) return null
        if (DISABLED_PSEUDO_OR_ATTR_REGEX.containsMatchIn(withoutNotDisabled)) {
            return null
        }
        return withoutNotDisabled
    }

    private fun parseSelectorSegmentsAtBuildTime(selector: String): List<BuildTimeSelectorSegment> {
        val sanitized = sanitizeSelectorOrNull(selector) ?: return emptyList()
        val rawTokens = sanitized
            .replace(">", " > ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
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

    private fun parseSingleSelectorAtom(rawToken: String, combinatorToLeft: String?): BuildTimeSelectorSegment {
        var working = rawToken.trim().replace(NOT_DISABLED_REGEX, "")
        working = working.replace(Regex(":not\\([^)]*\\)", RegexOption.IGNORE_CASE), "")
        val pseudos = mutableListOf<String>()
        val pseudoRegex = Regex(":(hover|focus-visible|focus-within|focus|active|disabled|checked|enabled)", RegexOption.IGNORE_CASE)
        for (m in pseudoRegex.findAll(working)) {
            pseudos.add(m.groupValues[1].lowercase())
        }
        if (Regex("\\[\\s*disabled(?:\\s*=[^]]*)?\\s*\\]", RegexOption.IGNORE_CASE).containsMatchIn(working) &&
            "disabled" !in pseudos
        ) {
            pseudos.add("disabled")
        }
        working = working.replace(pseudoRegex, "")
        // Strip attribute selectors like [data-bs-theme="dark"] into class token if present
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
        val leadingTag = working.replace(Regex("\\.[a-zA-Z0-9_-]+"), "").trim()
            .takeIf { it.isNotEmpty() && it != "*" }

        return BuildTimeSelectorSegment(
            tagName = leadingTag,
            id = id,
            classes = classes,
            pseudoClasses = pseudos,
            combinatorToLeft = combinatorToLeft
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
            "line-height" -> emitLengthExpr(cleanVal)?.let { out.add("setLineHeight($it, $isImp)") }
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
            "cursor" -> emitCursorEnum(cleanVal)?.let { out.add("setCursor($it, $isImp)") }
            "z-index" -> cleanVal.toIntOrNull()?.let { out.add("setZIndex($it, $isImp)") }
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

    private fun emitLengthExpr(raw: String): String? {
        val clean = raw.trim().lowercase()
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

    private fun splitTopLevelCommas(raw: String): List<String> {
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
                ch == ',' && depth == 0 -> {
                    out.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString().trim())
        return out.filter { it.isNotEmpty() }
    }

    private fun parseCssRulesAndKeyframes(
        cssText: String
    ): Pair<List<ParsedRule>, Map<String, Map<String, Map<String, String>>>> {
        val rules = mutableListOf<ParsedRule>()
        val keyframes = linkedMapOf<String, Map<String, Map<String, String>>>()
        val cleaned = cssText.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

        var i = 0
        val n = cleaned.length
        while (i < n) {
            while (i < n && cleaned[i].isWhitespace()) i++
            if (i >= n) break

            val headerStart = i
            while (i < n && cleaned[i] != '{') i++
            if (i >= n) break
            val header = cleaned.substring(headerStart, i).trim()
            val bodyStart = i + 1
            var depth = 1
            i++
            while (i < n && depth > 0) {
                if (cleaned[i] == '{') depth++
                else if (cleaned[i] == '}') depth--
                i++
            }
            val body = cleaned.substring(bodyStart, (i - 1).coerceAtLeast(bodyStart)).trim()

            when {
                header.startsWith("@keyframes", ignoreCase = true) -> {
                    val animName = header.replace(Regex("^@keyframes\\s+", RegexOption.IGNORE_CASE), "").trim()
                    val frames = linkedMapOf<String, Map<String, String>>()
                    val (subRules, _) = parseCssRulesAndKeyframes(body)
                    for (r in subRules) {
                        frames[r.selector] = r.declarations
                    }
                    if (animName.isNotEmpty()) {
                        keyframes[animName] = frames
                    }
                }
                header.startsWith("@media", ignoreCase = true) -> {
                    val minW = Regex("min-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
                        .find(header)?.groupValues?.get(1)?.toIntOrNull()
                    val maxW = Regex("max-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
                        .find(header)?.groupValues?.get(1)?.toIntOrNull()
                    val (innerRules, _) = parseCssRulesAndKeyframes(body)
                    for (innerRule in innerRules) {
                        rules.add(
                            innerRule.copy(
                                mediaMinWidthPx = minW ?: innerRule.mediaMinWidthPx,
                                mediaMaxWidthPx = maxW ?: innerRule.mediaMaxWidthPx
                            )
                        )
                    }
                }
                header.isNotEmpty() -> {
                    val decls = parseDeclarations(body)
                    val selectors = splitTopLevelCommas(header)
                    for (rawSel in selectors) {
                        val sel = sanitizeSelectorOrNull(rawSel) ?: continue
                        rules.add(
                            ParsedRule(
                                selector = sel,
                                declarations = decls,
                                specificity = computeSpecificity(sel)
                            )
                        )
                    }
                }
            }
        }
        return rules to keyframes
    }

    private fun parseDeclarations(body: String): Map<String, String> {
        val decls = linkedMapOf<String, String>()
        for (stmt in body.split(";")) {
            val idx = stmt.indexOf(':')
            if (idx > 0) {
                val prop = stmt.substring(0, idx).trim().lowercase()
                val value = stmt.substring(idx + 1).trim()
                if (prop.isNotEmpty() && value.isNotEmpty()) {
                    decls[prop] = value
                }
            }
        }
        return decls
    }

    private fun computeSpecificity(selector: String): Int {
        val cleaned = selector.trim()
        if (cleaned == "*") return 0
        val ids = cleaned.count { it == '#' }
        val classes = cleaned.count { it == '.' || it == '[' || it == ':' }
        val elements = cleaned.split(Regex("[\\s>+~]+"))
            .map { it.replace(Regex(":.*"), "") }
            .count { token ->
                token.isNotEmpty() &&
                    token != "*" &&
                    !token.startsWith(".") &&
                    !token.startsWith("#")
            }
        return ids * 10000 + classes * 100 + elements
    }

    private fun toPascalIdentifier(raw: String): String = Companion.toPascalIdentifier(raw)

    companion object {
        internal fun toPascalIdentifier(raw: String): String {
            val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
            if (parts.isEmpty()) return "Generated"
            val joined = parts.joinToString("") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            return if (joined.first().isDigit()) "Style$joined" else joined
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
