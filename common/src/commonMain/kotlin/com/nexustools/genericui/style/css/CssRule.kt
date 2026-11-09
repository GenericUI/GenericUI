package com.nexustools.genericui.style.css

/**
 * Represents a single parsed CSS rule with selector, declarations, specificity, optional `@media`
 * width constraints (`min-width` / `max-width`), and optional pseudo-class (`:hover`, `:focus`, `:active`).
 *
 * Modeled after Blink's `RuleData` / `CSSSelectorList`: selector compound tokens (IDs, classes, tag names,
 * and descendant/child `>` combinator flags) and `!important` declaration flags are pre-parsed once at
 * construction time so rule matching and cascade merging execute with zero runtime regular expressions.
 */
data class CssRule(
    val selector: String,
    val declarations: Map<String, String>,
    val specificity: Int = computeSpecificity(selector),
    val mediaMinWidthPx: Int? = null,
    val mediaMaxWidthPx: Int? = null
) {
    private val sanitizedSelector: String =
        selector.replace(NOT_DISABLED_REGEX, "").replace(NOT_PSEUDO_REGEX, "").trim()

    val pseudoClass: String? = run {
        if (DISABLED_PSEUDO_OR_ATTR_REGEX.containsMatchIn(sanitizedSelector)) {
            return@run "disabled"
        }
        val lastSegment = sanitizedSelector.split(COMBINATOR_SPLIT_REGEX).lastOrNull() ?: return@run null
        val match = PSEUDO_CLASS_REGEX.find(lastSegment)
        match?.groupValues?.get(1)?.lowercase()
    }

    val baseSelectorWithoutPseudo: String =
        sanitizedSelector
            .replace(PSEUDO_CLASS_REGEX, "")
            .replace(DISABLED_ATTR_REGEX, "")
            .trim()

    internal val isUniversalStarOnly: Boolean =
        baseSelectorWithoutPseudo == "*"

    internal val chainTokens: List<String>
    internal val directParentFlags: BooleanArray
    internal val subjectToken: String
    internal val subjectRequiredIds: List<String>
    internal val subjectRequiredId: String?
    internal val subjectRequiredClasses: Set<String>
    internal val subjectLeadingTag: String
    internal val subjectHasClassOrId: Boolean
    internal val ancestorTokens: List<String>
    internal val ancestorRequiredIds: List<List<String>>
    internal val ancestorRequiredClasses: List<Set<String>>
    internal val ancestorLeadingTags: List<String>
    internal val cleanedDeclarations: Map<String, String>
    internal val importantProps: Set<String>

    init {
        val normalized = baseSelectorWithoutPseudo.replace(CHILD_COMBINATOR_NORMALIZE_REGEX, " > ")
        if (normalized.isEmpty()) {
            chainTokens = emptyList()
            directParentFlags = BooleanArray(0)
        } else if (normalized == "*") {
            chainTokens = listOf("*")
            directParentFlags = BooleanArray(0)
        } else {
            val parts = normalized.split(WHITESPACE_SPLIT_REGEX).filter { it.isNotEmpty() }
            val tokens = ArrayList<String>(parts.size)
            val flags = ArrayList<Boolean>(parts.size)
            var idx = 0
            while (idx < parts.size) {
                val part = parts[idx]
                if (part == ">") {
                    idx++
                    continue
                }
                tokens.add(part)
                if (idx + 1 < parts.size && parts[idx + 1] == ">") {
                    flags.add(true)
                    idx += 2
                } else {
                    if (idx + 1 < parts.size) {
                        flags.add(false)
                    }
                    idx++
                }
            }
            chainTokens = tokens
            directParentFlags = BooleanArray(flags.size) { flags[it] }
        }

        subjectToken = chainTokens.lastOrNull()?.trim() ?: ""
        if (subjectToken.isEmpty() || subjectToken == "*") {
            subjectRequiredIds = emptyList()
            subjectRequiredId = null
            subjectRequiredClasses = emptySet()
            subjectLeadingTag = ""
            subjectHasClassOrId = false
        } else {
            subjectRequiredIds = extractIdsFromToken(subjectToken)
            subjectRequiredId = subjectRequiredIds.firstOrNull()
            subjectRequiredClasses = extractClassesFromToken(subjectToken)
            subjectLeadingTag = normalizeSelectorTag(extractTagFromToken(subjectToken))
            subjectHasClassOrId = subjectRequiredIds.isNotEmpty() || subjectRequiredClasses.isNotEmpty()
        }

        if (chainTokens.size <= 1) {
            ancestorTokens = emptyList()
            ancestorRequiredIds = emptyList()
            ancestorRequiredClasses = emptyList()
            ancestorLeadingTags = emptyList()
        } else {
            val ancTokens = chainTokens.dropLast(1).map { it.trim() }
            ancestorTokens = ancTokens
            ancestorRequiredIds = ancTokens.map { tok -> if (tok == "*") emptyList() else extractIdsFromToken(tok) }
            ancestorRequiredClasses = ancTokens.map { tok -> if (tok == "*") emptySet() else extractClassesFromToken(tok) }
            ancestorLeadingTags = ancTokens.map { tok -> if (tok == "*") "" else normalizeSelectorTag(extractTagFromToken(tok)) }
        }

        if (declarations.isEmpty()) {
            cleanedDeclarations = emptyMap()
            importantProps = emptySet()
        } else {
            val cleanMap = LinkedHashMap<String, String>(declarations.size)
            val impSet = LinkedHashSet<String>()
            for ((prop, rawVal) in declarations) {
                if (rawVal.contains("!important", ignoreCase = true)) {
                    impSet.add(prop)
                    cleanMap[prop] = rawVal.replace(IMPORTANT_STRIP_REGEX, "").trim()
                } else {
                    cleanMap[prop] = rawVal
                }
            }
            cleanedDeclarations = cleanMap
            importantProps = impSet
        }
    }

    fun matchesMediaQuery(viewportWidthPx: Int): Boolean {
        if (mediaMinWidthPx != null && viewportWidthPx < mediaMinWidthPx) return false
        if (mediaMaxWidthPx != null && viewportWidthPx > mediaMaxWidthPx) return false
        return true
    }

    companion object {
        internal val NOT_DISABLED_REGEX =
            Regex(":not\\(\\s*(?::disabled|\\[\\s*disabled(?:\\s*=[^\\]]*)?\\s*\\]|\\.disabled)\\s*\\)", RegexOption.IGNORE_CASE)
        internal val NOT_PSEUDO_REGEX =
            Regex(":not\\([^)]*\\)", RegexOption.IGNORE_CASE)
        internal val DISABLED_ATTR_REGEX =
            Regex("\\[\\s*disabled(?:\\s*=[^\\]]*)?\\s*\\]", RegexOption.IGNORE_CASE)
        internal val DISABLED_PSEUDO_OR_ATTR_REGEX =
            Regex("(?::disabled\\b|\\[\\s*disabled(?:\\s*=[^\\]]*)?\\s*\\])", RegexOption.IGNORE_CASE)
        private val PSEUDO_CLASS_REGEX =
            Regex("(?<!\\\\):(hover|focus-visible|focus|active|visited|disabled|checked|first-child|last-child|nth-child\\([^)]*\\))\\b", RegexOption.IGNORE_CASE)
        private val COMBINATOR_SPLIT_REGEX = Regex("[\\s>+~]+")
        private val COLON_SUFFIX_REGEX = Regex(":.*")
        private val CHILD_COMBINATOR_NORMALIZE_REGEX = Regex("\\s*>\\s*")
        private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")
        private val CLASS_TOKEN_REGEX = Regex("\\.([a-zA-Z0-9_-]+)")
        private val ID_TOKEN_REGEX = Regex("#([a-zA-Z0-9_-]+)")
        internal val IMPORTANT_STRIP_REGEX = Regex("!\\s*important", RegexOption.IGNORE_CASE)

        internal fun sanitizeSelectorOrNull(rawSelector: String): String? {
            val trimmed = rawSelector.trim()
            if (trimmed.isEmpty()) return null
            val withoutNotDisabled = trimmed.replace(NOT_DISABLED_REGEX, "").trim()
            if (withoutNotDisabled.isEmpty()) return null
            if (DISABLED_PSEUDO_OR_ATTR_REGEX.containsMatchIn(withoutNotDisabled)) {
                return null
            }
            return withoutNotDisabled
        }

        internal fun normalizeSelectorTag(rawTag: String): String {
            val cleaned = rawTag.trim().replace("\\:", ":").lowercase()
            return when (cleaned) {
                "app", "genericui:app" -> "genericui:app"
                "fragment", "genericui:fragment" -> "genericui:fragment"
                else -> cleaned
            }
        }

        internal fun extractTagFromToken(token: String): String {
            val clean = token.trim().replace("\\:", ":")
            if (clean.isEmpty() || clean.startsWith(".") || clean.startsWith("#") || clean.startsWith("[") || clean.startsWith("*")) {
                return ""
            }
            val sb = StringBuilder()
            var i = 0
            while (i < clean.length) {
                val ch = clean[i]
                if (ch == '.' || ch == '#' || ch == '[') break
                if (ch == ':') {
                    val rest = clean.substring(i + 1)
                    val nextSegment = rest.takeWhile { it != '.' && it != '#' && it != '[' && it != ':' }
                    if (sb.toString().equals("genericui", ignoreCase = true) && nextSegment.isNotEmpty()) {
                        sb.append(':').append(nextSegment)
                        i += 1 + nextSegment.length
                        continue
                    }
                    break
                }
                sb.append(ch)
                i++
            }
            return sb.toString().trim().lowercase()
        }

        internal fun extractClassesFromToken(token: String): Set<String> =
            CLASS_TOKEN_REGEX.findAll(token).map { it.groupValues[1] }.toSet()

        internal fun extractIdsFromToken(token: String): List<String> =
            ID_TOKEN_REGEX.findAll(token).map { it.groupValues[1] }.toList()

        fun computeSpecificity(selector: String): Int {
            val cleaned = selector.trim()
            if (cleaned == "*") return 0
            val withoutNamespaceColon = cleaned
                .replace("genericui\\:", "genericui_")
                .replace("genericui:", "genericui_")
            val ids = withoutNamespaceColon.count { it == '#' }
            val classes = withoutNamespaceColon.count { it == '.' || it == '[' || it == ':' }
            val elements = withoutNamespaceColon.split(COMBINATOR_SPLIT_REGEX)
                .map { it.replace(COLON_SUFFIX_REGEX, "") }
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
