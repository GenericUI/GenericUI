package com.nexustools.genericui.style.sass

import com.nexustools.genericui.res.ResourceType
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.css.CompiledSelectorSegment
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry

/**
 * Compiles `.scss` / `.sass` / `.css` stylesheets into precompiled Kotlin (`.kt`) source files
 * or in-memory [PrecompiledStyleSheet] instances so applications avoid runtime SASS/CSS parsing overhead.
 */
object SassKotlinPrecompiler {

    /**
     * Resolves `@color/<name>`, `@dimen/<name>`, `@drawable/<name>`, `@font/<name>`, and `@string/<name>`
     * resource references inside a CSS/SCSS declaration value against [Resources.activeTable].
     */
    fun resolveResourceReferencesInCssValue(rawValue: String): String {
        var result = rawValue
        // @color/<name>
        result = Regex("@color/([a-zA-Z0-9_-]+)").replace(result) { match ->
            val name = match.groupValues[1]
            Resources.activeTable.getColorByName(name)?.toCssRgba() ?: match.value
        }
        // @dimen/<name>
        result = Regex("@dimen/([a-zA-Z0-9_-]+)").replace(result) { match ->
            val name = match.groupValues[1]
            val id = Resources.activeTable.findResourceId(ResourceType.DIMEN, name)
            val px = id?.let { Resources.activeTable.getDimenOrNull(it) }
            if (px != null) "${px}px" else match.value
        }
        // @drawable/<name>
        result = Regex("@drawable/([a-zA-Z0-9_-]+)").replace(result) { match ->
            val name = match.groupValues[1]
            val id = Resources.activeTable.findResourceId(ResourceType.DRAWABLE, name)
            if (id != null) "url('${Resources.getUrl(id)}')" else match.value
        }
        // @font/<name>
        result = Regex("@font/([a-zA-Z0-9_-]+)").replace(result) { match ->
            val name = match.groupValues[1]
            val id = Resources.activeTable.findResourceId(ResourceType.FONT, name)
            val fontPath = id?.let { Resources.activeTable.getFontPath(it) }
            val family = fontPath?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: name
            "\"$family\""
        }
        // @string/<name>
        result = Regex("@string/([a-zA-Z0-9_-]+)").replace(result) { match ->
            val name = match.groupValues[1]
            val resolved = Resources.activeTable.getStringByName(name)
            if (resolved != null) "\"$resolved\"" else match.value
        }
        return result
    }

    /**
     * Compiles [scssSource] into an in-memory [PrecompiledStyleSheet] with pre-sorted [CssRule]s
     * and strongly-typed [CompiledStyleRule]s.
     */
    fun precompile(
        scssSource: String,
        name: String = "PrecompiledStyles",
        resourcePath: String = "/styles/$name.css"
    ): PrecompiledStyleSheet {
        val rawCss = SassCompiler.compileToCss(scssSource)
        val parsed = CssStyleSheet.parse(rawCss)
        val resolvedRules = parsed.rules.map { rule ->
            val resolvedDecls = rule.declarations.mapValues { (_, v) -> resolveResourceReferencesInCssValue(v) }
            rule.copy(declarations = resolvedDecls)
        }
        val compiledRuleList = resolvedRules.sortedBy { it.specificity }.map { rule ->
            CompiledStyleRule(
                selector = rule.selector,
                segments = CompiledSelectorSegment.parseChain(rule.selector),
                style = ComponentStyle.fromDeclarations(rule.declarations),
                declarations = rule.declarations,
                specificity = rule.specificity,
                pseudoClass = rule.pseudoClass,
                mediaMinWidthPx = rule.mediaMinWidthPx,
                mediaMaxWidthPx = rule.mediaMaxWidthPx
            )
        }
        val precompiledSheet = CssStyleSheet.precompiled(
            rules = resolvedRules,
            keyframes = parsed.keyframes
        )
        return object : PrecompiledStyleSheet {
            override val name: String = name
            override val styleSheet: CssStyleSheet = precompiledSheet
            override val rawCss: String = rawCss
            override val cssResourcePath: String = resourcePath
            override val compiledRules: List<CompiledStyleRule> = compiledRuleList
        }.also { sheet ->
            StyleSheetRegistry.register(name, resourcePath, sheet)
        }
    }

    /**
     * Precompiles [scssSource] into a standalone Kotlin source file (`<objectName>.kt`) declaring
     * `object <objectName> : PrecompiledStyleSheet` with pre-parsed [CompiledStyleRule]s, [CssRule]s,
     * precomputed specificity, `@media` constraints, and `@keyframes`.
     */
    fun precompileToKotlinSource(
        scssSource: String,
        packageName: String,
        objectName: String,
        stylesheetName: String = objectName
    ): String {
        val rawCss = SassCompiler.compileToCss(scssSource)
        val sheet = CssStyleSheet.parse(rawCss)
        return generateKotlinSourceFromStyleSheet(
            sheet = sheet,
            rawCss = rawCss,
            packageName = packageName,
            objectName = objectName,
            stylesheetName = stylesheetName
        )
    }

    /**
     * Generates the Kotlin `.kt` file contents for an already parsed [CssStyleSheet].
     */
    fun generateKotlinSourceFromStyleSheet(
        sheet: CssStyleSheet,
        rawCss: String,
        packageName: String,
        objectName: String,
        stylesheetName: String = objectName,
        cssResourcePath: String = "/styles/$stylesheetName.css"
    ): String = buildString {
        if (packageName.isNotBlank()) {
            appendLine("package $packageName")
            appendLine()
        }
        appendLine("import com.nexustools.genericui.style.css.CompiledStyleRule")
        appendLine("import com.nexustools.genericui.style.css.ComponentStyle")
        appendLine("import com.nexustools.genericui.style.css.CssRule")
        appendLine("import com.nexustools.genericui.style.css.CssStyleSheet")
        appendLine("import com.nexustools.genericui.style.css.PrecompiledStyleSheet")
        appendLine("import com.nexustools.genericui.style.css.StyleSheetRegistry")
        appendLine()
        appendLine("/**")
        appendLine(" * Auto-generated precompiled GenericUI stylesheet for `$stylesheetName`.")
        appendLine(" * Pre-parsed at build time to eliminate runtime SASS/CSS parsing overhead.")
        appendLine(" */")
        appendLine("object $objectName : PrecompiledStyleSheet {")
        appendLine("    override val name: String = ${escapeKotlinString(stylesheetName)}")
        appendLine()
        appendLine("    override val cssResourcePath: String = ${escapeKotlinString(cssResourcePath)}")
        appendLine()
        appendLine("    override val rawCss: String = ${escapeKotlinString(rawCss)}")
        appendLine()
        val sortedRules = sheet.rules.sortedBy { it.specificity }
        appendLine("    override val compiledRules: List<CompiledStyleRule> = listOf(")
        for ((ruleIdx, rule) in sortedRules.withIndex()) {
            val comma = if (ruleIdx < sortedRules.lastIndex) "," else ""
            appendLine("        CompiledStyleRule(")
            appendLine("            selector = ${escapeKotlinString(rule.selector)},")
            appendLine("            style = ComponentStyle().apply {")
            for ((k, v) in rule.declarations) {
                appendLine("                setRawDeclaration(${escapeKotlinString(k)}, ${escapeKotlinString(v)})")
            }
            appendLine("            },")
            appendLine("            specificity = ${rule.specificity},")
            appendLine("            mediaMinWidthPx = ${rule.mediaMinWidthPx ?: "null"},")
            appendLine("            mediaMaxWidthPx = ${rule.mediaMaxWidthPx ?: "null"}")
            appendLine("        )$comma")
        }
        appendLine("    )")
        appendLine()
        appendLine("    override val styleSheet: CssStyleSheet = CssStyleSheet.precompiled(")
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
        if (sheet.keyframes.isEmpty()) {
            appendLine("        keyframes = emptyMap()")
        } else {
            appendLine("        keyframes = mapOf(")
            val kfEntries = sheet.keyframes.entries.toList()
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
        appendLine()
        appendLine("    init {")
        appendLine("        StyleSheetRegistry.register(name, cssResourcePath, this)")
        appendLine("    }")
        appendLine("}")
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
}
