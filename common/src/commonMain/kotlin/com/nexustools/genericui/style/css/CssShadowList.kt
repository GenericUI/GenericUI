package com.nexustools.genericui.style.css

/**
 * Strongly-typed CSS `box-shadow` or `text-shadow` list (`none` or comma-separated [CssShadow] layers).
 */
data class CssShadowList(
    val shadows: List<CssShadow> = emptyList(),
    val isNone: Boolean = false
) : CssValue {

    override fun toCssString(): String = when {
        isNone || shadows.isEmpty() -> "none"
        else -> shadows.joinToString(", ") { it.toCssString() }
    }

    companion object {
        val NONE = CssShadowList(emptyList(), isNone = true)

        fun of(vararg shadows: CssShadow): CssShadowList =
            if (shadows.isEmpty()) NONE else CssShadowList(shadows.toList(), isNone = false)

        fun parse(raw: String?): CssShadowList? {
            val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (cleaned.equals("none", ignoreCase = true)) return NONE
            val parts = splitTopLevelCommas(cleaned)
            val parsed = parts.mapNotNull { CssShadow.parse(it) }
            if (parsed.isEmpty()) return null
            return CssShadowList(parsed, isNone = false)
        }

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
                        val segment = current.toString().trim()
                        if (segment.isNotEmpty()) result.add(segment)
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            val tail = current.toString().trim()
            if (tail.isNotEmpty()) result.add(tail)
            return result
        }
    }
}
