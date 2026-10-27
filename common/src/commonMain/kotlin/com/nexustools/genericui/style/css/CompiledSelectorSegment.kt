package com.nexustools.genericui.style.css

/**
 * Pre-parsed compound selector segment within a [CompiledStyleRule] selector chain.
 */
data class CompiledSelectorSegment(
    val tagName: String? = null,
    val id: String? = null,
    val classes: Set<String> = emptySet(),
    val pseudoClass: String? = null,
    val combinatorToNext: CssCombinator? = null
) {
    companion object {
        fun parseChain(selector: String): List<CompiledSelectorSegment> {
            val cleaned = selector.trim()
            if (cleaned.isEmpty()) return emptyList()

            val tokens = tokenizeSelectorChain(cleaned)
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

        private fun tokenizeSelectorChain(selector: String): List<String> {
            val normalized = selector
                .replace(">", " > ")
                .replace("+", " + ")
                .replace("~", " ~ ")
            return normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }

        private fun parseCompoundSegment(raw: String, combinator: CssCombinator?): CompiledSelectorSegment {
            var working = raw.trim()
            var pseudo: String? = null
            val colonIdx = working.indexOf(':')
            if (colonIdx >= 0) {
                pseudo = working.substring(colonIdx + 1).trim().lowercase().takeIf { it.isNotEmpty() }
                working = working.substring(0, colonIdx).trim()
            }

            var tagName: String? = null
            var id: String? = null
            val classes = linkedSetOf<String>()

            val firstClass = working.indexOf('.')
            val firstId = working.indexOf('#')
            val firstSpecial = listOf(firstClass, firstId).filter { it >= 0 }.minOrNull() ?: working.length
            if (firstSpecial > 0) {
                val rawTag = working.substring(0, firstSpecial).trim()
                if (rawTag.isNotEmpty() && rawTag != "*") {
                    tagName = rawTag.lowercase()
                }
            }

            for (idMatch in Regex("#([a-zA-Z0-9_-]+)").findAll(working)) {
                id = idMatch.groupValues[1]
            }
            for (classMatch in Regex("\\.([a-zA-Z0-9_-]+)").findAll(working)) {
                classes.add(classMatch.groupValues[1])
            }

            return CompiledSelectorSegment(
                tagName = tagName,
                id = id,
                classes = classes,
                pseudoClass = pseudo,
                combinatorToNext = combinator
            )
        }
    }
}
