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
        val lastSegment = selector.trim().split(Regex("[\\s>+~]+")).lastOrNull() ?: return@run null
        val match = PSEUDO_CLASS_REGEX.find(lastSegment)
        match?.groupValues?.get(1)?.lowercase()
    }

    val baseSelectorWithoutPseudo: String =
        selector.replace(PSEUDO_CLASS_REGEX, "").trim()

    fun matchesMediaQuery(viewportWidthPx: Int): Boolean {
        if (mediaMinWidthPx != null && viewportWidthPx < mediaMinWidthPx) return false
        if (mediaMaxWidthPx != null && viewportWidthPx > mediaMaxWidthPx) return false
        return true
    }

    companion object {
        private val PSEUDO_CLASS_REGEX =
            Regex("(?<!\\\\):(hover|focus-visible|focus|active|visited|disabled|checked|first-child|last-child|nth-child\\([^)]*\\))\\b", RegexOption.IGNORE_CASE)

        fun computeSpecificity(selector: String): Int {
            val cleaned = selector.trim()
            if (cleaned == "*") return 0
            val withoutNamespaceColon = cleaned
                .replace("genericui\\:", "genericui_")
                .replace("genericui:", "genericui_")
            val ids = withoutNamespaceColon.count { it == '#' }
            val classes = withoutNamespaceColon.count { it == '.' || it == '[' || it == ':' }
            val elements = withoutNamespaceColon.split(Regex("[\\s>+~]+"))
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
