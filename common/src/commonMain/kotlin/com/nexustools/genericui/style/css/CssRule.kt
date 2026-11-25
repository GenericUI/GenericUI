package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.system.BackendTarget

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
    val mediaMaxWidthPx: Int? = null,
    val backendMask: Int = BackendStyleMask.ALL
) {
    private val subjectParsedToken: ParsedSelectorToken

    val pseudoClass: String?
    val baseSelectorWithoutPseudo: String

    internal val isUniversalStarOnly: Boolean

    internal val chainTokens: List<String>
    internal val directParentFlags: BooleanArray
    internal val subjectToken: String
    internal val subjectRequiredIds: List<String>
    internal val subjectRequiredId: String?
    internal val subjectRequiredClasses: Set<String>
    internal val subjectLeadingTag: String
    internal val subjectHasClassOrId: Boolean
    internal val subjectExcludedClasses: Set<String>
    internal val subjectExcludedIds: Set<String>
    internal val subjectExcludedTags: Set<String>
    internal val subjectPositivePseudos: Set<String>
    internal val subjectExcludedPseudos: Set<String>
    internal val ancestorTokens: List<String>
    internal val ancestorRequiredIds: List<List<String>>
    internal val ancestorRequiredClasses: List<Set<String>>
    internal val ancestorLeadingTags: List<String>
    internal val ancestorExcludedClasses: List<Set<String>>
    internal val ancestorExcludedIds: List<Set<String>>
    internal val ancestorExcludedTags: List<Set<String>>
    internal val ancestorPositivePseudos: List<Set<String>>
    internal val ancestorExcludedPseudos: List<Set<String>>
    internal val cleanedDeclarations: Map<String, String>
    internal val importantProps: Set<String>

    init {
        val rawSegments = splitChainPreservingParens(selector)
        val parsedSegments = ArrayList<ParsedSelectorToken>(rawSegments.size)
        val rawTokens = ArrayList<String>(rawSegments.size)
        val flags = ArrayList<Boolean>(rawSegments.size)
        var sIdx = 0
        while (sIdx < rawSegments.size) {
            val part = rawSegments[sIdx]
            if (part == ">") {
                sIdx++
                continue
            }
            val parsed = parseCompoundToken(part)
            parsedSegments.add(parsed)
            rawTokens.add(parsed.positiveToken)
            if (sIdx + 1 < rawSegments.size && rawSegments[sIdx + 1] == ">") {
                flags.add(true)
                sIdx += 2
            } else {
                if (sIdx + 1 < rawSegments.size) {
                    flags.add(false)
                }
                sIdx++
            }
        }

        chainTokens = rawTokens
        directParentFlags = BooleanArray(flags.size) { flags[it] }

        val lastParsed = parsedSegments.lastOrNull() ?: ParsedSelectorToken.EMPTY
        subjectParsedToken = lastParsed
        pseudoClass = selectPrimaryPseudoClass(lastParsed.positivePseudos)

        val baseParts = ArrayList<String>(parsedSegments.size * 2)
        for (idx in parsedSegments.indices) {
            if (idx > 0) {
                val isDirect = if (idx - 1 < directParentFlags.size) directParentFlags[idx - 1] else false
                if (isDirect) baseParts.add(">")
            }
            val posTok = parsedSegments[idx].positiveToken
            if (posTok.isNotEmpty()) baseParts.add(posTok)
        }
        baseSelectorWithoutPseudo = baseParts.joinToString(" ").trim()

        subjectToken = lastParsed.positiveToken
        if (subjectToken.isEmpty() || subjectToken == "*") {
            subjectRequiredIds = emptyList()
            subjectRequiredId = null
            subjectRequiredClasses = emptySet()
            subjectLeadingTag = ""
            subjectHasClassOrId = false
        } else {
            subjectRequiredIds = lastParsed.ids
            subjectRequiredId = lastParsed.ids.firstOrNull()
            subjectRequiredClasses = lastParsed.classes
            subjectLeadingTag = lastParsed.tagName ?: ""
            subjectHasClassOrId = subjectRequiredIds.isNotEmpty() || subjectRequiredClasses.isNotEmpty()
        }
        subjectExcludedClasses = lastParsed.excludedClasses
        subjectExcludedIds = lastParsed.excludedIds
        subjectExcludedTags = lastParsed.excludedTags
        subjectPositivePseudos = lastParsed.positivePseudos
        subjectExcludedPseudos = lastParsed.excludedPseudos

        isUniversalStarOnly = parsedSegments.size == 1 &&
            subjectToken == "*" &&
            subjectExcludedClasses.isEmpty() &&
            subjectExcludedIds.isEmpty() &&
            subjectExcludedTags.isEmpty() &&
            subjectPositivePseudos.isEmpty() &&
            subjectExcludedPseudos.isEmpty()

        if (parsedSegments.size <= 1) {
            ancestorTokens = emptyList()
            ancestorRequiredIds = emptyList()
            ancestorRequiredClasses = emptyList()
            ancestorLeadingTags = emptyList()
            ancestorExcludedClasses = emptyList()
            ancestorExcludedIds = emptyList()
            ancestorExcludedTags = emptyList()
            ancestorPositivePseudos = emptyList()
            ancestorExcludedPseudos = emptyList()
        } else {
            val ancParsed = parsedSegments.dropLast(1)
            ancestorTokens = ancParsed.map { it.positiveToken }
            ancestorRequiredIds = ancParsed.map { tok -> if (tok.positiveToken == "*") emptyList() else tok.ids }
            ancestorRequiredClasses = ancParsed.map { tok -> if (tok.positiveToken == "*") emptySet() else tok.classes }
            ancestorLeadingTags = ancParsed.map { tok -> if (tok.positiveToken == "*") "" else (tok.tagName ?: "") }
            ancestorExcludedClasses = ancParsed.map { it.excludedClasses }
            ancestorExcludedIds = ancParsed.map { it.excludedIds }
            ancestorExcludedTags = ancParsed.map { it.excludedTags }
            ancestorPositivePseudos = ancParsed.map { it.positivePseudos }
            ancestorExcludedPseudos = ancParsed.map { it.excludedPseudos }
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

    fun matchesBackend(target: BackendTarget?): Boolean =
        BackendStyleMask.matches(backendMask, target)

    companion object {
        private const val NS_COLON_SENTINEL = "\u0001GUICLN\u0001"

        internal data class ParsedSelectorToken(
            val positiveToken: String,
            val tagName: String?,
            val ids: List<String>,
            val classes: Set<String>,
            val excludedTags: Set<String>,
            val excludedIds: Set<String>,
            val excludedClasses: Set<String>,
            val positivePseudos: Set<String>,
            val excludedPseudos: Set<String>
        ) {
            companion object {
                val EMPTY = ParsedSelectorToken(
                    positiveToken = "",
                    tagName = null,
                    ids = emptyList(),
                    classes = emptySet(),
                    excludedTags = emptySet(),
                    excludedIds = emptySet(),
                    excludedClasses = emptySet(),
                    positivePseudos = emptySet(),
                    excludedPseudos = emptySet()
                )
            }
        }

        private val ATTR_SELECTOR_REGEX = Regex("\\[[^\\]]*\\]")
        private val INTERACTIVE_PSEUDO_PRIORITY = listOf("hover", "focus", "focus-visible", "focus-within", "active", "disabled", "checked")

        internal val NOT_DISABLED_REGEX =
            Regex(":not\\(\\s*(?::disabled|\\[\\s*(?:disabled(?:\\s*=[^\\]]*)?|aria-disabled\\s*=\\s*[\"']?true[\"']?)\\s*\\]|\\.disabled)\\s*\\)", RegexOption.IGNORE_CASE)
        internal val NOT_PSEUDO_REGEX =
            Regex(":not\\([^)]*\\)", RegexOption.IGNORE_CASE)
        internal val DISABLED_ATTR_REGEX =
            Regex("\\[\\s*(?:disabled(?:\\s*=[^\\]]*)?|aria-disabled\\s*=\\s*[\"']?true[\"']?)\\s*\\]", RegexOption.IGNORE_CASE)
        internal val DISABLED_PSEUDO_OR_ATTR_REGEX =
            Regex("(?::disabled\\b|\\[\\s*(?:disabled(?:\\s*=[^\\]]*)?|aria-disabled\\s*=\\s*[\"']?true[\"']?)\\s*\\])", RegexOption.IGNORE_CASE)
        private val PSEUDO_CLASS_REGEX =
            Regex("(?<!\\\\):(hover|focus-visible|focus-within|focus|active|visited|disabled|enabled|checked|first-child|last-child|nth-child\\([^)]*\\))\\b", RegexOption.IGNORE_CASE)
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
            return trimmed
        }

        internal fun selectPrimaryPseudoClass(positivePseudos: Set<String>): String? {
            if (positivePseudos.isEmpty()) return null
            for (prio in INTERACTIVE_PSEUDO_PRIORITY) {
                if (prio in positivePseudos) return prio
            }
            return positivePseudos.firstOrNull { it != "enabled" }
        }

        internal fun splitChainPreservingParens(selector: String): List<String> {
            val out = ArrayList<String>(4)
            val cur = StringBuilder()
            var parenDepth = 0
            var bracketDepth = 0
            fun flush() {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString())
                    cur.clear()
                }
            }
            for (ch in selector.trim()) {
                when {
                    ch == '(' -> {
                        parenDepth++
                        cur.append(ch)
                    }
                    ch == ')' -> {
                        if (parenDepth > 0) parenDepth--
                        cur.append(ch)
                    }
                    ch == '[' -> {
                        bracketDepth++
                        cur.append(ch)
                    }
                    ch == ']' -> {
                        if (bracketDepth > 0) bracketDepth--
                        cur.append(ch)
                    }
                    parenDepth == 0 && bracketDepth == 0 && (ch == '>' || ch == '+' || ch == '~') -> {
                        flush()
                        out.add(ch.toString())
                    }
                    parenDepth == 0 && bracketDepth == 0 && ch.isWhitespace() -> flush()
                    else -> cur.append(ch)
                }
            }
            flush()
            return out
        }

        private fun protectNamespaceColons(raw: String): String =
            raw.replace("\\:", NS_COLON_SENTINEL)
                .replace(Regex("\\bgenericui:", RegexOption.IGNORE_CASE), "genericui$NS_COLON_SENTINEL")

        private fun restoreNamespaceColons(value: String): String =
            value.replace(NS_COLON_SENTINEL, ":")

        internal fun parseCompoundToken(rawToken: String): ParsedSelectorToken {
            val trimmed = rawToken.trim()
            if (trimmed.isEmpty()) return ParsedSelectorToken.EMPTY
            if (trimmed == "*") {
                return ParsedSelectorToken(
                    positiveToken = "*",
                    tagName = null,
                    ids = emptyList(),
                    classes = emptySet(),
                    excludedTags = emptySet(),
                    excludedIds = emptySet(),
                    excludedClasses = emptySet(),
                    positivePseudos = emptySet(),
                    excludedPseudos = emptySet()
                )
            }

            val excludedTags = linkedSetOf<String>()
            val excludedIds = linkedSetOf<String>()
            val excludedClasses = linkedSetOf<String>()
            val positivePseudos = linkedSetOf<String>()
            val excludedPseudos = linkedSetOf<String>()

            val protectedRaw = protectNamespaceColons(trimmed)
            val remainingBuilder = StringBuilder()
            var i = 0
            while (i < protectedRaw.length) {
                if (i + 5 <= protectedRaw.length &&
                    protectedRaw.substring(i, i + 5).equals(":not(", ignoreCase = true)
                ) {
                    var depth = 1
                    var j = i + 5
                    while (j < protectedRaw.length && depth > 0) {
                        when (protectedRaw[j]) {
                            '(' -> depth++
                            ')' -> depth--
                        }
                        j++
                    }
                    val innerEnd = if (depth == 0) j - 1 else protectedRaw.length
                    val inner = protectedRaw.substring(i + 5, innerEnd)
                    parseNegationInner(
                        inner = inner,
                        excludedTags = excludedTags,
                        excludedIds = excludedIds,
                        excludedClasses = excludedClasses,
                        excludedPseudos = excludedPseudos
                    )
                    i = j
                    continue
                }
                remainingBuilder.append(protectedRaw[i])
                i++
            }

            var positiveWork = remainingBuilder.toString()
            if (DISABLED_ATTR_REGEX.containsMatchIn(positiveWork)) {
                positivePseudos.add("disabled")
                positiveWork = positiveWork.replace(DISABLED_ATTR_REGEX, "")
            }
            positiveWork = positiveWork.replace(ATTR_SELECTOR_REGEX, "")

            // Separate trailing/interleaved :pseudo suffixes while keeping #id / .class / tag base parts
            val baseBuilder = StringBuilder()
            var p = 0
            while (p < positiveWork.length) {
                val ch = positiveWork[p]
                if (ch == ':') {
                    var end = p + 1
                    var pDepth = 0
                    while (end < positiveWork.length) {
                        val c = positiveWork[end]
                        if (c == '(') pDepth++
                        else if (c == ')') {
                            if (pDepth > 0) pDepth--
                        } else if (pDepth == 0 && (c == '.' || c == '#' || c == ':' || c == '[')) {
                            break
                        }
                        end++
                    }
                    val pseudoName = positiveWork.substring(p + 1, end).trim().lowercase()
                    if (pseudoName.isNotEmpty()) {
                        positivePseudos.add(pseudoName)
                    }
                    p = end
                } else {
                    baseBuilder.append(ch)
                    p++
                }
            }

            val restoredBase = restoreNamespaceColons(baseBuilder.toString()).trim()
            val ids = if (restoredBase.isEmpty() || restoredBase == "*") emptyList() else extractIdsFromToken(restoredBase)
            val classes = if (restoredBase.isEmpty() || restoredBase == "*") emptySet() else extractClassesFromToken(restoredBase)
            val rawTag = if (restoredBase.isEmpty() || restoredBase == "*") "" else normalizeSelectorTag(extractTagFromToken(restoredBase))
            val resolvedPositiveToken = when {
                restoredBase.isNotEmpty() -> restoredBase
                excludedClasses.isNotEmpty() || excludedIds.isNotEmpty() || excludedTags.isNotEmpty() ||
                    positivePseudos.isNotEmpty() || excludedPseudos.isNotEmpty() -> "*"
                else -> ""
            }

            return ParsedSelectorToken(
                positiveToken = resolvedPositiveToken,
                tagName = rawTag.takeIf { it.isNotEmpty() },
                ids = ids,
                classes = classes,
                excludedTags = excludedTags,
                excludedIds = excludedIds,
                excludedClasses = excludedClasses,
                positivePseudos = positivePseudos,
                excludedPseudos = excludedPseudos
            )
        }

        private fun parseNegationInner(
            inner: String,
            excludedTags: MutableSet<String>,
            excludedIds: MutableSet<String>,
            excludedClasses: MutableSet<String>,
            excludedPseudos: MutableSet<String>
        ) {
            for (rawPart in inner.split(',')) {
                var work = rawPart.trim()
                if (work.isEmpty()) continue
                if (DISABLED_ATTR_REGEX.containsMatchIn(work)) {
                    excludedPseudos.add("disabled")
                    work = work.replace(DISABLED_ATTR_REGEX, "")
                }
                work = work.replace(ATTR_SELECTOR_REGEX, "")
                var idx = 0
                val cleanBase = StringBuilder()
                while (idx < work.length) {
                    if (work[idx] == ':') {
                        var end = idx + 1
                        var pDepth = 0
                        while (end < work.length) {
                            val c = work[end]
                            if (c == '(') pDepth++
                            else if (c == ')' && pDepth > 0) pDepth--
                            else if (pDepth == 0 && (c == '.' || c == '#' || c == ':')) break
                            end++
                        }
                        val pseudoName = work.substring(idx + 1, end).trim().lowercase()
                        if (pseudoName.isNotEmpty()) excludedPseudos.add(pseudoName)
                        idx = end
                    } else {
                        cleanBase.append(work[idx])
                        idx++
                    }
                }
                val restored = restoreNamespaceColons(cleanBase.toString()).trim()
                if (restored.isNotEmpty()) {
                    for (id in extractIdsFromToken(restored)) excludedIds.add(id)
                    for (cls in extractClassesFromToken(restored)) excludedClasses.add(cls)
                    val tag = normalizeSelectorTag(extractTagFromToken(restored))
                    if (tag.isNotEmpty() && tag != "*") excludedTags.add(tag)
                }
            }
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
            val withoutNotWrapper = cleaned
                .replace(Regex(":not\\(", RegexOption.IGNORE_CASE), "(")
                .replace("genericui\\:", "genericui_")
                .replace("genericui:", "genericui_")
            val ids = withoutNotWrapper.count { it == '#' }
            val classes = withoutNotWrapper.count { it == '.' || it == '[' || it == ':' }
            val elements = withoutNotWrapper.split(COMBINATOR_SPLIT_REGEX)
                .map { it.replace(COLON_SUFFIX_REGEX, "").replace("(", "").replace(")", "") }
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
