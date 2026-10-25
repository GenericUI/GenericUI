package com.nexustools.genericui.style.sass

import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * Pure Kotlin Multiplatform SASS/SCSS compiler supporting `$variables` (including `!default` and
 * variable-to-variable references), arbitrarily deep nested selectors, parent `&` references
 * (including `&.modifier`, `&:hover`, `&:focus`, `&:active`), child combinators (`>`),
 * `@media` queries nested inside rules, `@keyframes`, `@mixin` (with default parameter values) /
 * `@include` expansion, and live syntax validation.
 */
object SassCompiler {

    private data class MixinParam(
        val name: String,
        val defaultValue: String? = null
    )

    private data class MixinDef(
        val params: List<MixinParam>,
        val body: String
    )

    fun compileToCss(scssSource: String): String {
        val withoutComments = stripComments(scssSource)
        val mixins = linkedMapOf<String, MixinDef>()
        val afterMixins = extractMixins(withoutComments, mixins)
        val expandedIncludes = expandIncludes(afterMixins, mixins)

        val variables = extractVariablesFromStripped(expandedIncludes)
        val varRegex = Regex("\\$([a-zA-Z0-9_-]+)\\s*:\\s*([^;{}]+);")

        var substituted = expandedIncludes.replace(varRegex, "")
        for ((name, value) in variables.entries.sortedByDescending { it.key.length }) {
            substituted = substituted.replace("\$$name", value)
        }

        val outCss = StringBuilder()
        flattenBlock(
            source = substituted,
            parentSelectors = emptyList(),
            activeMediaQuery = null,
            output = outCss
        )
        return outCss.toString().trim()
    }

    fun compileToStyleSheet(scssSource: String): CssStyleSheet =
        CssStyleSheet.parse(compileToCss(scssSource))

    fun compileDetailed(scssSource: String): ScssCompileResult {
        val errors = validateScss(scssSource)
        val vars = extractVariables(scssSource)
        val mixins = extractMixinNames(scssSource)
        return if (errors.isEmpty()) {
            val css = compileToCss(scssSource)
            val sheet = CssStyleSheet.parse(css)
            ScssCompileResult(
                scssSource = scssSource,
                compiledCss = css,
                styleSheet = sheet,
                errors = emptyList(),
                variables = vars,
                mixinNames = mixins
            )
        } else {
            ScssCompileResult(
                scssSource = scssSource,
                compiledCss = "",
                styleSheet = CssStyleSheet(),
                errors = errors,
                variables = vars,
                mixinNames = mixins
            )
        }
    }

    fun extractVariables(scssSource: String): Map<String, String> =
        extractVariablesFromStripped(stripComments(scssSource))

    fun extractMixinNames(scssSource: String): Set<String> {
        val mixins = linkedMapOf<String, MixinDef>()
        extractMixins(stripComments(scssSource), mixins)
        return mixins.keys.toSet()
    }

    fun validateScss(scssSource: String): List<ScssSyntaxError> {
        val errors = mutableListOf<ScssSyntaxError>()
        val stripped = stripComments(scssSource)
        var braceDepth = 0
        var parenDepth = 0
        val lines = stripped.lines()
        for ((lineIdx, line) in lines.withIndex()) {
            val lineNum = lineIdx + 1
            for ((colIdx, ch) in line.withIndex()) {
                when (ch) {
                    '{' -> braceDepth++
                    '}' -> {
                        braceDepth--
                        if (braceDepth < 0) {
                            errors.add(ScssSyntaxError(lineNum, colIdx + 1, "Unexpected closing '}'"))
                            braceDepth = 0
                        }
                    }
                    '(' -> parenDepth++
                    ')' -> {
                        parenDepth--
                        if (parenDepth < 0) {
                            errors.add(ScssSyntaxError(lineNum, colIdx + 1, "Unexpected closing ')'"))
                            parenDepth = 0
                        }
                    }
                }
            }
        }
        if (braceDepth > 0) {
            errors.add(ScssSyntaxError(lines.size.coerceAtLeast(1), 1, "Unclosed '{' block ($braceDepth remaining)"))
        }
        if (parenDepth > 0) {
            errors.add(ScssSyntaxError(lines.size.coerceAtLeast(1), 1, "Unclosed '(' parenthesis ($parenDepth remaining)"))
        }
        return errors
    }

    private fun extractVariablesFromStripped(stripped: String): Map<String, String> {
        val variables = linkedMapOf<String, String>()
        val varRegex = Regex("\\$([a-zA-Z0-9_-]+)\\s*:\\s*([^;{}]+);")
        for (match in varRegex.findAll(stripped)) {
            val name = match.groupValues[1].trim()
            val rawVal = match.groupValues[2].trim()
            val isDefault = rawVal.endsWith("!default", ignoreCase = true)
            val cleanVal = rawVal.replace(Regex("!\\s*default$", RegexOption.IGNORE_CASE), "").trim()
            if (isDefault && variables.containsKey(name)) {
                continue
            }
            var resolvedVal = cleanVal
            for ((existingName, existingVal) in variables.entries.sortedByDescending { it.key.length }) {
                resolvedVal = resolvedVal.replace("\$$existingName", existingVal)
            }
            variables[name] = resolvedVal
        }
        return variables
    }

    private fun stripComments(scss: String): String =
        scss.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
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

    private fun splitTopLevelCommas(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in input) {
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
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) {
            result.add(tail)
        }
        return result
    }

    private fun extractMixins(source: String, mixins: MutableMap<String, MixinDef>): String {
        val sb = StringBuilder()
        var i = 0
        val n = source.length
        while (i < n) {
            if (source.startsWith("@mixin", i)) {
                val headerStart = i + "@mixin".length
                val braceIdx = source.indexOf('{', headerStart)
                if (braceIdx > headerStart) {
                    val sig = source.substring(headerStart, braceIdx).trim()
                    val parenStart = sig.indexOf('(')
                    val parenEnd = sig.lastIndexOf(')')
                    val name: String
                    val params: List<MixinParam>
                    if (parenStart > 0 && parenEnd > parenStart) {
                        name = sig.substring(0, parenStart).trim()
                        params = splitTopLevelCommas(sig.substring(parenStart + 1, parenEnd))
                            .mapNotNull { rawParam ->
                                val clean = rawParam.trim().removePrefix("$")
                                if (clean.isEmpty()) return@mapNotNull null
                                val colonIdx = clean.indexOf(':')
                                if (colonIdx > 0) {
                                    MixinParam(
                                        name = clean.substring(0, colonIdx).trim(),
                                        defaultValue = clean.substring(colonIdx + 1).trim()
                                    )
                                } else {
                                    MixinParam(name = clean)
                                }
                            }
                    } else {
                        name = sig
                        params = emptyList()
                    }
                    var depth = 1
                    var j = braceIdx + 1
                    while (j < n && depth > 0) {
                        if (source[j] == '{') depth++
                        else if (source[j] == '}') depth--
                        j++
                    }
                    val body = source.substring(braceIdx + 1, (j - 1).coerceAtLeast(braceIdx + 1))
                    mixins[name] = MixinDef(params, body)
                    i = j
                    continue
                }
            }
            sb.append(source[i])
            i++
        }
        return sb.toString()
    }

    private fun expandIncludes(source: String, mixins: Map<String, MixinDef>): String {
        if (mixins.isEmpty()) return source
        val sb = StringBuilder()
        var i = 0
        val n = source.length
        while (i < n) {
            if (source.startsWith("@include", i)) {
                var cursor = i + "@include".length
                while (cursor < n && source[cursor].isWhitespace()) cursor++
                val nameStart = cursor
                while (cursor < n && (source[cursor].isLetterOrDigit() || source[cursor] == '_' || source[cursor] == '-')) {
                    cursor++
                }
                val name = source.substring(nameStart, cursor).trim()
                while (cursor < n && source[cursor].isWhitespace()) cursor++
                var argsRaw = ""
                if (cursor < n && source[cursor] == '(') {
                    val argsStart = cursor + 1
                    var depth = 1
                    cursor++
                    while (cursor < n && depth > 0) {
                        if (source[cursor] == '(') depth++
                        else if (source[cursor] == ')') depth--
                        cursor++
                    }
                    argsRaw = source.substring(argsStart, (cursor - 1).coerceAtLeast(argsStart))
                }
                while (cursor < n && source[cursor].isWhitespace()) cursor++
                if (cursor < n && source[cursor] == ';') {
                    cursor++
                }
                val mixin = mixins[name]
                if (mixin != null) {
                    val args = if (argsRaw.isBlank()) emptyList() else splitTopLevelCommas(argsRaw)
                    var body = mixin.body
                    for ((idx, param) in mixin.params.withIndex()) {
                        val argVal = args.getOrNull(idx)?.takeIf { it.isNotEmpty() } ?: param.defaultValue ?: ""
                        body = body.replace("\$${param.name}", argVal)
                    }
                    sb.append(body)
                }
                i = cursor
                continue
            }
            sb.append(source[i])
            i++
        }
        return sb.toString()
    }

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
            val selectorLine = parentSelectors.joinToString(", ") { escapeGenericUiNamespaceInSelector(it) }
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

    private val UNESCAPED_GENERICUI_TAG_REGEX = Regex("(?<!\\\\)genericui:([a-zA-Z0-9_-]+)")

    private fun escapeGenericUiNamespaceInSelector(selector: String): String =
        selector.replace(UNESCAPED_GENERICUI_TAG_REGEX, "genericui\\\\:$1")
}
