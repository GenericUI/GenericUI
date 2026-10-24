package com.nexustools.genericui.style.sass

import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * Pure Kotlin Multiplatform SASS/SCSS compiler supporting `$variables`, arbitrarily deep nested selectors,
 * parent `&` references (including `&.modifier`, `&:hover`, `&:focus`, `&:active`), child combinators (`>`),
 * `@media` queries nested inside rules, `@keyframes`, and `@mixin` / `@include` expansion into standard CSS.
 */
object SassCompiler {

    private data class MixinDef(
        val params: List<String>,
        val body: String
    )

    fun compileToCss(scssSource: String): String {
        val withoutComments = stripComments(scssSource)
        val mixins = linkedMapOf<String, MixinDef>()
        val afterMixins = extractMixins(withoutComments, mixins)
        val expandedIncludes = expandIncludes(afterMixins, mixins)

        val variables = linkedMapOf<String, String>()
        val varRegex = Regex("\\$([a-zA-Z0-9_-]+)\\s*:\\s*([^;{}]+);")
        for (match in varRegex.findAll(expandedIncludes)) {
            variables[match.groupValues[1]] = match.groupValues[2].trim()
        }

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
                    val params: List<String>
                    if (parenStart > 0 && parenEnd > parenStart) {
                        name = sig.substring(0, parenStart).trim()
                        params = sig.substring(parenStart + 1, parenEnd)
                            .split(",")
                            .map { it.trim().removePrefix("$") }
                            .filter { it.isNotEmpty() }
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
        val includeRegex = Regex("@include\\s+([a-zA-Z0-9_-]+)(?:\\(([^)]*)\\))?\\s*;")
        return includeRegex.replace(source) { match ->
            val name = match.groupValues[1]
            val argsRaw = match.groupValues[2]
            val mixin = mixins[name] ?: return@replace ""
            val args = if (argsRaw.isBlank()) emptyList() else argsRaw.split(",").map { it.trim() }
            var body = mixin.body
            for ((idx, param) in mixin.params.withIndex()) {
                val argVal = args.getOrNull(idx) ?: ""
                body = body.replace("\$$param", argVal)
            }
            body
        }
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
            val selectorLine = parentSelectors.joinToString(", ")
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
                } else if (c.startsWith(">") || c.startsWith("+") || c.startsWith("~")) {
                    combined.add("$p $c")
                } else {
                    combined.add("$p $c")
                }
            }
        }
        return combined
    }
}
