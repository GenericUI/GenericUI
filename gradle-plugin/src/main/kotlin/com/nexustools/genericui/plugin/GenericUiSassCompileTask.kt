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
                    val processedCss = transformSassToCss(bootstrapHeader + "\n" + rawContent, minify.getOrElse(false))
                    targetFile.writeText(processedCss)
                    compiledFiles.add(normalizedRelPath)

                    if (shouldEmitWebCss) {
                        val webTargetFile = File(webCssOutDir, targetRelativePath)
                        webTargetFile.parentFile?.mkdirs()
                        webTargetFile.writeText(processedCss)
                        if (normalizedRelPath !in webCssFiles) {
                            webCssFiles.add(normalizedRelPath)
                        }

                        // Also emit at the root of webCssOutDir (e.g., "styles/app.scss" -> "app.css" as well as "styles/app.css")
                        // so both `<link href="app.css">` and `<link href="styles/app.css">` work seamlessly on the Web target.
                        val rootFileName = "${file.nameWithoutExtension}.css"
                        if (normalizedRelPath != rootFileName) {
                            val rootWebCssFile = File(webCssOutDir, rootFileName)
                            if (!rootWebCssFile.exists()) {
                                rootWebCssFile.writeText(processedCss)
                                if (rootFileName !in webCssFiles) {
                                    webCssFiles.add(rootFileName)
                                }
                            }
                        }
                        combinedWebCssChunks.add("/* Source: $normalizedRelPath */\n$processedCss")
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

    internal fun generatePrecompiledKotlinSource(
        cssText: String,
        packageName: String,
        objectName: String,
        stylesheetName: String,
        cssResourcePath: String = "$stylesheetName.css"
    ): String {
        val (rules, keyframes) = parseCssRulesAndKeyframes(cssText)
        val sortedRules = rules.sortedBy { it.specificity }

        return buildString {
            if (packageName.isNotBlank()) {
                appendLine("package $packageName")
                appendLine()
            }
            appendLine("import com.nexustools.genericui.style.css.CssRule")
            appendLine("import com.nexustools.genericui.style.css.CssStyleSheet")
            appendLine("import com.nexustools.genericui.style.css.PrecompiledStyleSheet")
            appendLine()
            appendLine("/**")
            appendLine(" * Auto-generated precompiled GenericUI stylesheet for `$stylesheetName`.")
            appendLine(" * Pre-parsed at build time by `compileGenericUiSass` for zero-overhead runtime styling.")
            appendLine(" */")
            appendLine("object $objectName : PrecompiledStyleSheet {")
            appendLine("    override val name: String = ${escapeKotlinString(stylesheetName)}")
            appendLine()
            appendLine("    override val cssResourcePath: String = ${escapeKotlinString(cssResourcePath)}")
            appendLine()
            appendLine("    override val rawCss: String = ${escapeKotlinString(cssText)}")
            appendLine()
            appendLine("    override val styleSheet: CssStyleSheet = CssStyleSheet.precompiled(")
            if (sortedRules.isEmpty()) {
                appendLine("        rules = emptyList(),")
            } else {
                appendLine("        rules = listOf(")
                for ((ruleIdx, rule) in sortedRules.withIndex()) {
                    val comma = if (ruleIdx < sortedRules.lastIndex) "," else ""
                    appendLine("            CssRule(")
                    appendLine("                selector = ${escapeKotlinString(rule.selector)},")
                    if (rule.declarations.isEmpty()) {
                        appendLine("                declarations = emptyMap(),")
                    } else {
                        appendLine("                declarations = mapOf(")
                        val entries = rule.declarations.entries.toList()
                        for ((declIdx, entry) in entries.withIndex()) {
                            val declComma = if (declIdx < entries.lastIndex) "," else ""
                            appendLine("                    ${escapeKotlinString(entry.key)} to ${escapeKotlinString(entry.value)}$declComma")
                        }
                        appendLine("                ),")
                    }
                    appendLine("                specificity = ${rule.specificity},")
                    appendLine("                mediaMinWidthPx = ${rule.mediaMinWidthPx ?: "null"},")
                    appendLine("                mediaMaxWidthPx = ${rule.mediaMaxWidthPx ?: "null"}")
                    appendLine("            )$comma")
                }
                appendLine("        ),")
            }
            if (keyframes.isEmpty()) {
                appendLine("        keyframes = emptyMap()")
            } else {
                appendLine("        keyframes = mapOf(")
                val kfEntries = keyframes.entries.toList()
                for ((kfIdx, kfEntry) in kfEntries.withIndex()) {
                    val kfComma = if (kfIdx < kfEntries.lastIndex) "," else ""
                    appendLine("            ${escapeKotlinString(kfEntry.key)} to mapOf(")
                    val stepEntries = kfEntry.value.entries.toList()
                    for ((stepIdx, stepEntry) in stepEntries.withIndex()) {
                        val stepComma = if (stepIdx < stepEntries.lastIndex) "," else ""
                        val declPairs = stepEntry.value.entries.joinToString(", ") {
                            "${escapeKotlinString(it.key)} to ${escapeKotlinString(it.value)}"
                        }
                        val declMapExpr = if (declPairs.isEmpty()) "emptyMap()" else "mapOf($declPairs)"
                        appendLine("                ${escapeKotlinString(stepEntry.key)} to $declMapExpr$stepComma")
                    }
                    appendLine("            )$kfComma")
                }
                appendLine("        )")
            }
            appendLine("    )")
            appendLine("}")
        }
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
                    val selectors = header.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    for (sel in selectors) {
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

    private fun toPascalIdentifier(raw: String): String {
        val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "Generated"
        val joined = parts.joinToString("") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return if (joined.first().isDigit()) "Style$joined" else joined
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
