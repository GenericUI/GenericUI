package com.nexustools.genericui.style.sass

import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet

/**
 * Compiles `.scss` / `.sass` / `.css` stylesheets into precompiled Kotlin (`.kt`) source files
 * or in-memory [PrecompiledStyleSheet] instances so applications avoid runtime SASS/CSS parsing overhead.
 */
object SassKotlinPrecompiler {

    /**
     * Compiles [scssSource] into an in-memory [PrecompiledStyleSheet] with pre-sorted [CssRule]s.
     */
    fun precompile(
        scssSource: String,
        name: String = "PrecompiledStyles"
    ): PrecompiledStyleSheet {
        val rawCss = SassCompiler.compileToCss(scssSource)
        val parsed = CssStyleSheet.parse(rawCss)
        val precompiledSheet = CssStyleSheet.precompiled(
            rules = parsed.rules,
            keyframes = parsed.keyframes
        )
        return object : PrecompiledStyleSheet {
            override val name: String = name
            override val styleSheet: CssStyleSheet = precompiledSheet
            override val rawCss: String = rawCss
        }
    }

    /**
     * Precompiles [scssSource] into a standalone Kotlin source file (`<objectName>.kt`) declaring
     * `object <objectName> : PrecompiledStyleSheet` with pre-parsed [CssRule]s, precomputed
     * specificity, `@media` constraints, and `@keyframes`.
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
        stylesheetName: String = objectName
    ): String = buildString {
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
        appendLine(" * Pre-parsed at build time to eliminate runtime SASS/CSS parsing overhead.")
        appendLine(" */")
        appendLine("object $objectName : PrecompiledStyleSheet {")
        appendLine("    override val name: String = ${escapeKotlinString(stylesheetName)}")
        appendLine()
        appendLine("    override val rawCss: String = ${escapeKotlinString(rawCss)}")
        appendLine()
        appendLine("    override val styleSheet: CssStyleSheet = CssStyleSheet.precompiled(")
        appendLine("        rules = listOf(")
        val sortedRules = sheet.rules.sortedBy { it.specificity }
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
