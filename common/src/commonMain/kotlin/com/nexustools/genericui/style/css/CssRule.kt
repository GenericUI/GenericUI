package com.nexustools.genericui.style.css

/**
 * Represents a single parsed CSS rule with selector, declarations, specificity, optional `@media`
 * width constraints (`min-width` / `max-width`), and optional pseudo-class (`:hover`, `:focus`, `:active`).
 */
data class CssRule(
    val selector: String,
    val declarations: Map<String, String>,
    val specificity: Int = computeSpecificity(selector),
    val mediaMinWidthPx: Int? = null,
    val mediaMaxWidthPx: Int? = null
) {
    val pseudoClass: String? = run {
        val lastSegment = selector.trim().split(Regex("[\\s>+~]+")).lastOrNull()
        val colonIdx = lastSegment?.indexOf(':') ?: -1
        if (colonIdx < 0 || lastSegment == null) null else lastSegment.substring(colonIdx + 1).trim().lowercase()
    }

    val baseSelectorWithoutPseudo: String =
        selector.replace(Regex(":(hover|focus|active|visited|disabled)\\b", RegexOption.IGNORE_CASE), "").trim()

    fun matchesMediaQuery(viewportWidthPx: Int): Boolean {
        if (mediaMinWidthPx != null && viewportWidthPx < mediaMinWidthPx) return false
        if (mediaMaxWidthPx != null && viewportWidthPx > mediaMaxWidthPx) return false
        return true
    }

    companion object {
        fun computeSpecificity(selector: String): Int {
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
    }
}
