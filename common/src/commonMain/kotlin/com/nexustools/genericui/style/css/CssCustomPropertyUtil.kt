package com.nexustools.genericui.style.css

/**
 * Resolves W3C CSS custom properties (`--*`) and recursive `var(--name, fallback)` expressions
 * including built-in `--bs-*` Bootstrap fallback tokens.
 */
object CssCustomPropertyUtil {

    fun substituteVars(
        rawValue: String,
        variables: Map<String, String>,
        isDarkTheme: Boolean = false,
        maxDepth: Int = 16
    ): String {
        if ("var(" !in rawValue.lowercase()) return rawValue
        return substituteRecursive(
            input = rawValue,
            variables = variables,
            isDarkTheme = isDarkTheme,
            visited = linkedSetOf(),
            depth = 0,
            maxDepth = maxDepth
        )
    }

    fun resolveVariableMap(
        inherited: Map<String, String>,
        localDeclarations: Map<String, String>,
        isDarkTheme: Boolean = false
    ): Map<String, String> {
        if (inherited.isEmpty() && localDeclarations.isEmpty()) return emptyMap()
        val combined = LinkedHashMap<String, String>(inherited.size + localDeclarations.size)
        combined.putAll(inherited)
        for ((k, v) in localDeclarations) {
            val lowerKey = k.trim().lowercase()
            if (lowerKey.startsWith("--")) {
                combined[lowerKey] = v.replace(CssRule.IMPORTANT_STRIP_REGEX, "").trim()
            }
        }
        if (combined.isEmpty()) return emptyMap()
        val resolved = LinkedHashMap<String, String>(combined.size)
        for ((k, v) in combined) {
            resolved[k] = substituteRecursive(
                input = v,
                variables = combined,
                isDarkTheme = isDarkTheme,
                visited = linkedSetOf(k),
                depth = 0,
                maxDepth = 16
            )
        }
        return resolved
    }

    private fun substituteRecursive(
        input: String,
        variables: Map<String, String>,
        isDarkTheme: Boolean,
        visited: MutableSet<String>,
        depth: Int,
        maxDepth: Int
    ): String {
        if (depth >= maxDepth) return input
        val out = StringBuilder(input.length)
        var i = 0
        val n = input.length
        while (i < n) {
            if (i + 4 <= n &&
                (input[i] == 'v' || input[i] == 'V') &&
                input.substring(i, i + 4).equals("var(", ignoreCase = true)
            ) {
                var pDepth = 1
                var j = i + 4
                var inQuote: Char? = null
                while (j < n && pDepth > 0) {
                    val ch = input[j]
                    if (inQuote != null) {
                        if (ch == '\\' && j + 1 < n) {
                            j += 2
                            continue
                        }
                        if (ch == inQuote) inQuote = null
                    } else {
                        when (ch) {
                            '"', '\'' -> inQuote = ch
                            '(' -> pDepth++
                            ')' -> pDepth--
                        }
                    }
                    j++
                }
                if (pDepth != 0) {
                    out.append(input.substring(i))
                    break
                }
                val inner = input.substring(i + 4, j - 1).trim()
                val splitIdx = findTopLevelComma(inner)
                val varName = (if (splitIdx >= 0) inner.substring(0, splitIdx) else inner)
                    .trim()
                    .lowercase()
                val fallback = if (splitIdx >= 0) inner.substring(splitIdx + 1).trim() else null

                val directValue = variables[varName] ?: bootstrapDefaultVar(varName, isDarkTheme)
                val replacement = when {
                    directValue != null && varName !in visited -> {
                        visited.add(varName)
                        val expanded = substituteRecursive(
                            input = directValue,
                            variables = variables,
                            isDarkTheme = isDarkTheme,
                            visited = visited,
                            depth = depth + 1,
                            maxDepth = maxDepth
                        )
                        visited.remove(varName)
                        expanded
                    }
                    !fallback.isNullOrEmpty() -> substituteRecursive(
                        input = fallback,
                        variables = variables,
                        isDarkTheme = isDarkTheme,
                        visited = visited,
                        depth = depth + 1,
                        maxDepth = maxDepth
                    )
                    else -> input.substring(i, j)
                }
                out.append(replacement)
                i = j
            } else {
                out.append(input[i])
                i++
            }
        }
        return out.toString()
    }

    private fun findTopLevelComma(s: String): Int {
        var pDepth = 0
        var bDepth = 0
        var inQuote: Char? = null
        for (i in s.indices) {
            val ch = s[i]
            if (inQuote != null) {
                if (ch == '\\') continue
                if (ch == inQuote) inQuote = null
                continue
            }
            when (ch) {
                '"', '\'' -> inQuote = ch
                '(' -> pDepth++
                ')' -> if (pDepth > 0) pDepth--
                '[' -> bDepth++
                ']' -> if (bDepth > 0) bDepth--
                ',' -> if (pDepth == 0 && bDepth == 0) return i
            }
        }
        return -1
    }

    private fun bootstrapDefaultVar(varName: String, isDarkTheme: Boolean): String? = when (varName) {
        "--bs-body-color",
        "--bs-card-color",
        "--bs-list-group-color",
        "--bs-dropdown-color",
        "--bs-nav-link-color",
        "--bs-heading-color" -> if (isDarkTheme) "#dee2e6" else "#212529"
        "--bs-emphasis-color" -> if (isDarkTheme) "#ffffff" else "#000000"
        "--bs-secondary-color" -> if (isDarkTheme) "rgba(222, 226, 230, 0.75)" else "rgba(33, 37, 41, 0.75)"
        "--bs-tertiary-color" -> if (isDarkTheme) "rgba(222, 226, 230, 0.5)" else "rgba(33, 37, 41, 0.5)"
        "--bs-body-bg",
        "--bs-card-bg",
        "--bs-list-group-bg",
        "--bs-dropdown-bg" -> if (isDarkTheme) "#212529" else "#ffffff"
        "--bs-secondary-bg" -> if (isDarkTheme) "#343a40" else "#e9ecef"
        "--bs-tertiary-bg" -> if (isDarkTheme) "#2b3035" else "#f8f9fa"
        "--bs-border-color" -> if (isDarkTheme) "#495057" else "#dee2e6"
        else -> null
    }
}
