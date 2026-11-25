package com.nexustools.genericui.style.css

/**
 * Pre-parsed compound selector segment within a [CompiledStyleRule] selector chain.
 */
data class CompiledSelectorSegment(
    val tagName: String? = null,
    val id: String? = null,
    val classes: Set<String> = emptySet(),
    val pseudoClass: String? = null,
    val pseudoClasses: Set<String> = if (pseudoClass != null) setOf(pseudoClass) else emptySet(),
    val combinatorToLeft: CssCombinator? = null,
    val combinatorToNext: CssCombinator? = null,
    val excludedClasses: Set<String> = emptySet(),
    val excludedPseudoClasses: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val excludedIds: Set<String> = emptySet(),
    val unmatchable: Boolean = false
) {
    companion object {
        fun parseChain(selector: String): List<CompiledSelectorSegment> {
            val cleaned = selector.trim()
            if (cleaned.isEmpty()) return emptyList()

            val tokens = CssRule.splitChainPreservingParens(cleaned)
            if (tokens.isEmpty()) return emptyList()

            val segments = mutableListOf<CompiledSelectorSegment>()
            var i = 0
            while (i < tokens.size) {
                val compoundToken = tokens[i]
                var combinator: CssCombinator? = null
                if (i + 1 < tokens.size) {
                    val next = tokens[i + 1]
                    combinator = when (next) {
                        ">" -> {
                            i++
                            CssCombinator.CHILD
                        }
                        "+" -> {
                            i++
                            CssCombinator.ADJACENT_SIBLING
                        }
                        "~" -> {
                            i++
                            CssCombinator.GENERAL_SIBLING
                        }
                        else -> CssCombinator.DESCENDANT
                    }
                }
                segments.add(parseCompoundSegment(compoundToken, combinator))
                i++
            }
            return segments
        }

        private fun parseCompoundSegment(raw: String, combinator: CssCombinator?): CompiledSelectorSegment {
            val parsed = CssRule.parseCompoundToken(raw)
            val pseudo = CssRule.selectPrimaryPseudoClass(parsed.positivePseudos)
            return CompiledSelectorSegment(
                tagName = parsed.tagName,
                id = parsed.ids.lastOrNull(),
                classes = parsed.classes,
                pseudoClass = pseudo,
                pseudoClasses = parsed.positivePseudos,
                combinatorToNext = combinator,
                excludedClasses = parsed.excludedClasses,
                excludedPseudoClasses = parsed.excludedPseudos,
                excludedTags = parsed.excludedTags,
                excludedIds = parsed.excludedIds,
                unmatchable = parsed.positiveToken.isEmpty()
            )
        }
    }
}
