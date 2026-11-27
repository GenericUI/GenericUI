package com.nexustools.genericui.style.css

/**
 * Parsed CSS attribute selector (`[attr]`, `[attr=val]`, `[attr~=val]`, `[attr|=val]`,
 * `[attr^=val]`, `[attr$=val]`, `[attr*=val]`, including optional `i`/`s` case flags).
 */
data class CssAttributeMatcher(
    val name: String,
    val operator: CssAttributeOperator,
    val value: String? = null,
    val caseInsensitive: Boolean = false
) {
    fun matches(actualValue: String?): Boolean {
        if (actualValue == null) return false
        if (operator == CssAttributeOperator.EXISTS) return true
        val expected = value ?: return false
        return when (operator) {
            CssAttributeOperator.EXISTS -> true
            CssAttributeOperator.EQUALS -> actualValue.equals(expected, ignoreCase = caseInsensitive)
            CssAttributeOperator.INCLUDES -> {
                if (expected.isEmpty() || expected.any { it.isWhitespace() }) return false
                actualValue.split(WHITESPACE_REGEX).any { word ->
                    word.isNotEmpty() && word.equals(expected, ignoreCase = caseInsensitive)
                }
            }
            CssAttributeOperator.DASH_MATCH -> {
                if (actualValue.equals(expected, ignoreCase = caseInsensitive)) return true
                actualValue.length > expected.length &&
                    actualValue.startsWith(expected, ignoreCase = caseInsensitive) &&
                    actualValue[expected.length] == '-'
            }
            CssAttributeOperator.PREFIX ->
                expected.isNotEmpty() && actualValue.startsWith(expected, ignoreCase = caseInsensitive)
            CssAttributeOperator.SUFFIX ->
                expected.isNotEmpty() && actualValue.endsWith(expected, ignoreCase = caseInsensitive)
            CssAttributeOperator.SUBSTRING ->
                expected.isNotEmpty() && actualValue.contains(expected, ignoreCase = caseInsensitive)
        }
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")

        /**
         * Parses inner bracket content (either `attr=val` or `[attr=val]`).
         */
        fun parse(rawAttributeSelector: String): CssAttributeMatcher? {
            var inner = rawAttributeSelector.trim()
            if (inner.startsWith("[") && inner.endsWith("]") && inner.length >= 2) {
                inner = inner.substring(1, inner.length - 1).trim()
            }
            if (inner.isEmpty()) return null

            var i = 0
            while (i < inner.length && !inner[i].isWhitespace() &&
                inner[i] !in charArrayOf('=', '~', '|', '^', '$', '*')
            ) {
                i++
            }
            val attrName = inner.substring(0, i).trim().lowercase()
            if (attrName.isEmpty()) return null
            while (i < inner.length && inner[i].isWhitespace()) i++
            if (i >= inner.length) {
                return CssAttributeMatcher(name = attrName, operator = CssAttributeOperator.EXISTS)
            }

            val op = when {
                inner.startsWith("~=", i) -> {
                    i += 2
                    CssAttributeOperator.INCLUDES
                }
                inner.startsWith("|=", i) -> {
                    i += 2
                    CssAttributeOperator.DASH_MATCH
                }
                inner.startsWith("^=", i) -> {
                    i += 2
                    CssAttributeOperator.PREFIX
                }
                inner.startsWith("$=", i) -> {
                    i += 2
                    CssAttributeOperator.SUFFIX
                }
                inner.startsWith("*=", i) -> {
                    i += 2
                    CssAttributeOperator.SUBSTRING
                }
                inner[i] == '=' -> {
                    i += 1
                    CssAttributeOperator.EQUALS
                }
                else -> return null
            }

            while (i < inner.length && inner[i].isWhitespace()) i++
            if (i >= inner.length) {
                return CssAttributeMatcher(name = attrName, operator = op, value = "")
            }

            val parsedValue: String
            if (inner[i] == '"' || inner[i] == '\'') {
                val quote = inner[i]
                i++
                val startVal = i
                val sb = StringBuilder()
                while (i < inner.length && inner[i] != quote) {
                    if (inner[i] == '\\' && i + 1 < inner.length) {
                        i++
                    }
                    sb.append(inner[i])
                    i++
                }
                if (i < inner.length && inner[i] == quote) i++
                parsedValue = if (sb.isNotEmpty() || i > startVal) sb.toString() else ""
            } else {
                val startVal = i
                while (i < inner.length && !inner[i].isWhitespace()) i++
                parsedValue = inner.substring(startVal, i)
            }

            while (i < inner.length && inner[i].isWhitespace()) i++
            val flagToken = inner.substring(i).trim().lowercase()
            val caseInsensitive = flagToken == "i"
            return CssAttributeMatcher(
                name = attrName,
                operator = op,
                value = parsedValue,
                caseInsensitive = caseInsensitive
            )
        }
    }
}
