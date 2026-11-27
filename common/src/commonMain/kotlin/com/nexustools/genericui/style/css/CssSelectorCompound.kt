package com.nexustools.genericui.style.css

/**
 * Parsed W3C Selectors Level 4 compound selector segment supporting type tags, `#id`, `.class`,
 * attribute matchers (`[...]`), structural pseudo-classes (`:first-child`, `:last-child`,
 * `:only-child`, `:nth-child(An+B [of S])`, `:nth-last-child`, `:*-of-type`, `:empty`, `:root`),
 * functional pseudo-classes (`:not()`, `:is()`, `:where()`, `:has()`), state pseudo-classes,
 * and pseudo-elements (`::before`, `::after`).
 */
data class CssSelectorCompound(
    val positiveToken: String = "*",
    val tagName: String? = null,
    val ids: List<String> = emptyList(),
    val classes: Set<String> = emptySet(),
    val attributes: List<CssAttributeMatcher> = emptyList(),
    val positivePseudos: Set<String> = emptySet(),
    val notSelectors: List<String> = emptyList(),
    val isSelectors: List<List<String>> = emptyList(),
    val whereSelectors: List<List<String>> = emptyList(),
    val hasSelectors: List<List<String>> = emptyList(),
    val pseudoElement: String? = null,
    val combinatorToLeft: CssCombinator? = null
) {
    companion object {
        private const val NS_COLON_SENTINEL = "\u0001GUICLN\u0001"

        private val PSEUDO_ELEMENTS = setOf("before", "after", "first-line", "first-letter", "placeholder")
        private val INTERACTIVE_STATE_PSEUDOS = setOf(
            "hover", "focus", "focus-visible", "focus-within", "active", "disabled", "enabled",
            "checked", "valid", "invalid", "placeholder-shown", "visited"
        )
        private val AN_PLUS_B_WHITESPACE = Regex("\\s+")
        private val GENERICUI_NAMESPACE_REGEX = Regex("\\bgenericui:", RegexOption.IGNORE_CASE)

        fun parseChain(selector: String): List<CssSelectorCompound> {
            val cleaned = selector.trim()
            if (cleaned.isEmpty()) return emptyList()
            val tokens = splitSelectorTokens(cleaned)
            return parseChainFromTokens(tokens)
        }

        internal fun parseChainFromTokens(tokens: List<String>): List<CssSelectorCompound> {
            if (tokens.isEmpty()) return emptyList()
            val compounds = ArrayList<CssSelectorCompound>(tokens.size)
            var pendingCombinator: CssCombinator? = null
            for (tok in tokens) {
                when (tok) {
                    ">" -> pendingCombinator = CssCombinator.CHILD
                    "+" -> pendingCombinator = CssCombinator.ADJACENT_SIBLING
                    "~" -> pendingCombinator = CssCombinator.GENERAL_SIBLING
                    else -> {
                        val combinator = if (compounds.isEmpty()) null else (pendingCombinator ?: CssCombinator.DESCENDANT)
                        compounds.add(parseCompound(tok, combinator))
                        pendingCombinator = null
                    }
                }
            }
            return compounds
        }

        fun splitSelectorTokens(selector: String): List<String> {
            val out = ArrayList<String>(4)
            val cur = StringBuilder()
            var parenDepth = 0
            var bracketDepth = 0
            var inQuote: Char? = null

            fun flush() {
                if (cur.isNotEmpty()) {
                    out.add(cur.toString())
                    cur.clear()
                }
            }

            val n = selector.length
            var i = 0
            while (i < n) {
                val ch = selector[i]
                if (inQuote != null) {
                    cur.append(ch)
                    if (ch == '\\' && i + 1 < n) {
                        i++
                        cur.append(selector[i])
                    } else if (ch == inQuote) {
                        inQuote = null
                    }
                    i++
                    continue
                }
                when {
                    ch == '"' || ch == '\'' -> {
                        inQuote = ch
                        cur.append(ch)
                    }
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
                i++
            }
            flush()
            return out
        }

        fun splitTopLevelSelectorList(input: String): List<String> {
            val out = ArrayList<String>(4)
            val cur = StringBuilder()
            var parenDepth = 0
            var bracketDepth = 0
            var inQuote: Char? = null
            var i = 0
            while (i < input.length) {
                val ch = input[i]
                if (inQuote != null) {
                    cur.append(ch)
                    if (ch == '\\' && i + 1 < input.length) {
                        i++
                        cur.append(input[i])
                    } else if (ch == inQuote) {
                        inQuote = null
                    }
                    i++
                    continue
                }
                when {
                    ch == '"' || ch == '\'' -> {
                        inQuote = ch
                        cur.append(ch)
                    }
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
                    ch == ',' && parenDepth == 0 && bracketDepth == 0 -> {
                        val s = cur.toString().trim()
                        if (s.isNotEmpty()) out.add(s)
                        cur.clear()
                    }
                    else -> cur.append(ch)
                }
                i++
            }
            val tail = cur.toString().trim()
            if (tail.isNotEmpty()) out.add(tail)
            return out
        }

        fun parseCompound(rawToken: String, combinatorToLeft: CssCombinator? = null): CssSelectorCompound {
            val trimmed = rawToken.trim()
            if (trimmed.isEmpty()) return CssSelectorCompound(positiveToken = "", combinatorToLeft = combinatorToLeft)
            if (trimmed == "*") return CssSelectorCompound(positiveToken = "*", tagName = null, combinatorToLeft = combinatorToLeft)

            var protectedRaw = if ('\\' in trimmed) trimmed.replace("\\:", NS_COLON_SENTINEL) else trimmed
            if (protectedRaw.contains("genericui:", ignoreCase = true)) {
                protectedRaw = protectedRaw.replace(GENERICUI_NAMESPACE_REGEX, "genericui$NS_COLON_SENTINEL")
            }

            val baseParts = StringBuilder()
            val ids = ArrayList<String>()
            val classes = linkedSetOf<String>()
            val attributes = mutableListOf<CssAttributeMatcher>()
            val positivePseudos = linkedSetOf<String>()
            val notSelectors = mutableListOf<String>()
            val isSelectors = mutableListOf<List<String>>()
            val whereSelectors = mutableListOf<List<String>>()
            val hasSelectors = mutableListOf<List<String>>()
            var pseudoElement: String? = null

            var i = 0
            val n = protectedRaw.length
            while (i < n) {
                val ch = protectedRaw[i]
                when (ch) {
                    '[' -> {
                        var depth = 1
                        var q: Char? = null
                        val start = i
                        i++
                        while (i < n && depth > 0) {
                            val c = protectedRaw[i]
                            if (q != null) {
                                if (c == '\\' && i + 1 < n) {
                                    i += 2
                                    continue
                                }
                                if (c == q) q = null
                            } else {
                                when (c) {
                                    '"', '\'' -> q = c
                                    '[' -> depth++
                                    ']' -> depth--
                                }
                            }
                            i++
                        }
                        val bracketRaw = protectedRaw.substring(start, i)
                        CssAttributeMatcher.parse(restoreNS(bracketRaw))?.let { matcher ->
                            attributes.add(matcher)
                        }
                    }
                    ':' -> {
                        val isDoubleColon = i + 1 < n && protectedRaw[i + 1] == ':'
                        var p = if (isDoubleColon) i + 2 else i + 1
                        val nameStart = p
                        while (p < n && (protectedRaw[p].isLetterOrDigit() || protectedRaw[p] == '-' || protectedRaw[p] == '_')) {
                            p++
                        }
                        val rawName = protectedRaw.substring(nameStart, p).lowercase()
                        var argInner: String? = null
                        if (p < n && protectedRaw[p] == '(') {
                            var depth = 1
                            var q: Char? = null
                            val argStart = p + 1
                            p++
                            while (p < n && depth > 0) {
                                val c = protectedRaw[p]
                                if (q != null) {
                                    if (c == '\\' && p + 1 < n) {
                                        p += 2
                                        continue
                                    }
                                    if (c == q) q = null
                                } else {
                                    when (c) {
                                        '"', '\'' -> q = c
                                        '(' -> depth++
                                        ')' -> depth--
                                    }
                                }
                                p++
                            }
                            argInner = restoreNS(protectedRaw.substring(argStart, (p - 1).coerceAtLeast(argStart)).trim())
                        }
                        i = p

                        when {
                            rawName in PSEUDO_ELEMENTS -> {
                                pseudoElement = rawName
                            }
                            rawName == "not" && argInner != null -> {
                                notSelectors.addAll(splitTopLevelSelectorList(argInner))
                            }
                            rawName == "is" && argInner != null -> {
                                val branches = splitTopLevelSelectorList(argInner)
                                if (branches.isNotEmpty()) isSelectors.add(branches)
                            }
                            rawName == "where" && argInner != null -> {
                                val branches = splitTopLevelSelectorList(argInner)
                                if (branches.isNotEmpty()) whereSelectors.add(branches)
                            }
                            rawName == "has" && argInner != null -> {
                                val branches = splitTopLevelSelectorList(argInner)
                                if (branches.isNotEmpty()) hasSelectors.add(branches)
                            }
                            rawName.isNotEmpty() -> {
                                val normalizedPseudo = if (argInner != null) "$rawName($argInner)" else rawName
                                positivePseudos.add(normalizedPseudo)
                            }
                        }
                    }
                    else -> {
                        baseParts.append(ch)
                        i++
                    }
                }
            }

            val restoredBase = restoreNS(baseParts.toString()).trim()
            val rawTag = if (restoredBase.isEmpty() || restoredBase == "*") {
                ""
            } else {
                extractLeadingTag(restoredBase)
            }
            if (restoredBase.isNotEmpty() && restoredBase != "*") {
                extractTokensWithSigil(restoredBase, '#') { ids.add(it) }
                extractTokensWithSigil(restoredBase, '.') { classes.add(it) }
            }
            val normalizedPosTok = when {
                restoredBase.isNotEmpty() -> restoredBase
                else -> "*"
            }
            return CssSelectorCompound(
                positiveToken = normalizedPosTok,
                tagName = rawTag.takeIf { it.isNotEmpty() && it != "*" },
                ids = ids,
                classes = classes,
                attributes = attributes,
                positivePseudos = positivePseudos,
                notSelectors = notSelectors,
                isSelectors = isSelectors,
                whereSelectors = whereSelectors,
                hasSelectors = hasSelectors,
                pseudoElement = pseudoElement,
                combinatorToLeft = combinatorToLeft
            )
        }

        private fun restoreNS(s: String): String = s.replace(NS_COLON_SENTINEL, ":")

        private fun extractLeadingTag(base: String): String {
            if (base.isEmpty() || base[0] == '.' || base[0] == '#' || base[0] == '*') return ""
            val sb = StringBuilder()
            var i = 0
            while (i < base.length) {
                val c = base[i]
                if (c == '.' || c == '#' || c == '[' || c == '*') break
                sb.append(c)
                i++
            }
            return CssRule.normalizeSelectorTag(sb.toString())
        }

        private inline fun extractTokensWithSigil(base: String, sigil: Char, onToken: (String) -> Unit) {
            var i = 0
            while (i < base.length) {
                if (base[i] == sigil) {
                    val start = i + 1
                    var end = start
                    while (end < base.length) {
                        val c = base[end]
                        if (c == '.' || c == '#' || c == ':' || c == '[') break
                        end++
                    }
                    if (end > start) {
                        onToken(base.substring(start, end))
                    }
                    i = end
                } else {
                    i++
                }
            }
        }

        fun computeSpecificity(selector: String): Int {
            val cleaned = selector.trim()
            if (cleaned.isEmpty() || cleaned == "*") return 0
            val branches = splitTopLevelSelectorList(cleaned)
            if (branches.size > 1) {
                var maxSpec = 0
                for (b in branches) {
                    val spec = computeSpecificity(b)
                    if (spec > maxSpec) maxSpec = spec
                }
                return maxSpec
            }

            val compounds = parseChain(cleaned)
            var total = 0
            for (comp in compounds) {
                total += computeCompoundSpecificity(comp)
            }
            return total
        }

        private fun computeCompoundSpecificity(comp: CssSelectorCompound): Int {
            var a = comp.ids.size
            var b = comp.classes.size + comp.attributes.size
            var c = if (!comp.tagName.isNullOrEmpty() && comp.tagName != "*") 1 else 0
            if (comp.pseudoElement != null) {
                c += 1
            }

            for (pseudo in comp.positivePseudos) {
                if (pseudo.startsWith("nth-child(", ignoreCase = true) ||
                    pseudo.startsWith("nth-last-child(", ignoreCase = true)
                ) {
                    b += 1
                    val inner = pseudo.substringAfter("(").removeSuffix(")")
                    val ofIdx = inner.indexOf(" of ", ignoreCase = true)
                    if (ofIdx >= 0) {
                        val filterSel = inner.substring(ofIdx + 4).trim()
                        val sub = computeSpecificity(filterSel)
                        a += sub / 10000
                        b += (sub % 10000) / 100
                        c += sub % 100
                    }
                } else {
                    b += 1
                }
            }

            if (comp.notSelectors.isNotEmpty()) {
                var maxBranch = 0
                for (ns in comp.notSelectors) {
                    val spec = computeSpecificity(ns)
                    if (spec > maxBranch) maxBranch = spec
                }
                a += maxBranch / 10000
                b += (maxBranch % 10000) / 100
                c += maxBranch % 100
            }

            for (isBranches in comp.isSelectors) {
                var maxBranch = 0
                for (br in isBranches) {
                    val spec = computeSpecificity(br)
                    if (spec > maxBranch) maxBranch = spec
                }
                a += maxBranch / 10000
                b += (maxBranch % 10000) / 100
                c += maxBranch % 100
            }

            for (hasBranches in comp.hasSelectors) {
                var maxBranch = 0
                for (br in hasBranches) {
                    val stripped = br.trim().removePrefix(">").removePrefix("+").removePrefix("~").trim()
                    val spec = computeSpecificity(stripped)
                    if (spec > maxBranch) maxBranch = spec
                }
                a += maxBranch / 10000
                b += (maxBranch % 10000) / 100
                c += maxBranch % 100
            }

            // `:where(...)` contributes zero specificity by W3C spec.
            return a * 10000 + b * 100 + c
        }

        fun matchesChain(
            chain: List<CssSelectorCompound>,
            subjectNode: CssSelectorNode,
            ignoreInteractivePseudosOnSubject: Boolean = false
        ): Boolean {
            if (chain.isEmpty()) return false
            return matchBackward(
                chain = chain,
                segIdx = chain.lastIndex,
                node = subjectNode,
                isSubject = true,
                ignoreInteractivePseudosOnSubject = ignoreInteractivePseudosOnSubject
            )
        }

        private fun matchBackward(
            chain: List<CssSelectorCompound>,
            segIdx: Int,
            node: CssSelectorNode,
            isSubject: Boolean,
            ignoreInteractivePseudosOnSubject: Boolean
        ): Boolean {
            val comp = chain[segIdx]
            if (!matchesCompound(
                    comp = comp,
                    node = node,
                    ignoreInteractivePseudos = isSubject && ignoreInteractivePseudosOnSubject
                )
            ) {
                return false
            }
            if (segIdx == 0) return true

            val combinator = comp.combinatorToLeft ?: CssCombinator.DESCENDANT
            return when (combinator) {
                CssCombinator.CHILD -> {
                    val parent = node.selectorParent ?: return false
                    matchBackward(chain, segIdx - 1, parent, isSubject = false, ignoreInteractivePseudosOnSubject = false)
                }
                CssCombinator.DESCENDANT -> {
                    var anc = node.selectorParent
                    while (anc != null) {
                        if (matchBackward(chain, segIdx - 1, anc, isSubject = false, ignoreInteractivePseudosOnSubject = false)) {
                            return true
                        }
                        anc = anc.selectorParent
                    }
                    false
                }
                CssCombinator.ADJACENT_SIBLING -> {
                    val parent = node.selectorParent ?: return false
                    val siblings = parent.selectorChildren
                    val idx = siblings.indexOfFirst { it === node }
                    if (idx <= 0) return false
                    matchBackward(chain, segIdx - 1, siblings[idx - 1], isSubject = false, ignoreInteractivePseudosOnSubject = false)
                }
                CssCombinator.GENERAL_SIBLING -> {
                    val parent = node.selectorParent ?: return false
                    val siblings = parent.selectorChildren
                    val idx = siblings.indexOfFirst { it === node }
                    if (idx <= 0) return false
                    for (prevIdx in (idx - 1) downTo 0) {
                        if (matchBackward(chain, segIdx - 1, siblings[prevIdx], isSubject = false, ignoreInteractivePseudosOnSubject = false)) {
                            return true
                        }
                    }
                    false
                }
            }
        }

        fun matchesCompound(
            comp: CssSelectorCompound,
            node: CssSelectorNode,
            ignoreInteractivePseudos: Boolean = false
        ): Boolean {
            val reqTag = comp.tagName
            if (!reqTag.isNullOrEmpty() && reqTag != "*") {
                val nodeTag = CssRule.normalizeSelectorTag(node.selectorTagName)
                if (reqTag != nodeTag) return false
            }

            if (comp.ids.isNotEmpty()) {
                val nid = node.selectorId ?: return false
                for (reqId in comp.ids) {
                    if (reqId != nid) return false
                }
            }

            if (comp.classes.isNotEmpty()) {
                if (!node.selectorClasses.containsAll(comp.classes)) return false
            }

            for (attr in comp.attributes) {
                if (!attr.matches(node.getSelectorAttribute(attr.name))) return false
            }

            for (pseudo in comp.positivePseudos) {
                val baseName = pseudo.substringBefore('(').lowercase()
                if (ignoreInteractivePseudos && baseName in INTERACTIVE_STATE_PSEUDOS &&
                    baseName != "enabled" && baseName != "valid" && baseName != "invalid" && baseName != "placeholder-shown"
                ) {
                    continue
                }
                if (!matchesPseudoClass(pseudo, node)) return false
            }

            for (notSel in comp.notSelectors) {
                val notChain = parseChain(notSel)
                if (notChain.isNotEmpty() && matchesChain(notChain, node, ignoreInteractivePseudosOnSubject = false)) {
                    return false
                }
            }

            for (isBranches in comp.isSelectors) {
                val matchedAny = isBranches.any { br ->
                    val ch = parseChain(br)
                    ch.isNotEmpty() && matchesChain(ch, node, ignoreInteractivePseudosOnSubject = false)
                }
                if (!matchedAny) return false
            }

            for (whereBranches in comp.whereSelectors) {
                val matchedAny = whereBranches.any { br ->
                    val ch = parseChain(br)
                    ch.isNotEmpty() && matchesChain(ch, node, ignoreInteractivePseudosOnSubject = false)
                }
                if (!matchedAny) return false
            }

            for (hasBranches in comp.hasSelectors) {
                val matchedAny = hasBranches.any { rel ->
                    matchesRelativeSelector(node, rel)
                }
                if (!matchedAny) return false
            }

            return true
        }

        private fun matchesRelativeSelector(anchor: CssSelectorNode, rawRelative: String): Boolean {
            val trimmed = rawRelative.trim()
            if (trimmed.isEmpty()) return false
            val leadingComb = when {
                trimmed.startsWith(">") -> CssCombinator.CHILD
                trimmed.startsWith("+") -> CssCombinator.ADJACENT_SIBLING
                trimmed.startsWith("~") -> CssCombinator.GENERAL_SIBLING
                else -> CssCombinator.DESCENDANT
            }
            val cleaned = trimmed.removePrefix(">").removePrefix("+").removePrefix("~").trim()
            val chain = parseChain(cleaned)
            if (chain.isEmpty()) return false

            fun collectCandidatesForStep(base: CssSelectorNode, comb: CssCombinator): List<CssSelectorNode> {
                return when (comb) {
                    CssCombinator.CHILD -> base.selectorChildren
                    CssCombinator.DESCENDANT -> {
                        val all = ArrayList<CssSelectorNode>()
                        fun walk(cur: CssSelectorNode) {
                            for (c in cur.selectorChildren) {
                                all.add(c)
                                walk(c)
                            }
                        }
                        walk(base)
                        all
                    }
                    CssCombinator.ADJACENT_SIBLING -> {
                        val siblings = base.selectorParent?.selectorChildren ?: return emptyList()
                        val idx = siblings.indexOfFirst { it === base }
                        if (idx >= 0 && idx + 1 < siblings.size) listOf(siblings[idx + 1]) else emptyList()
                    }
                    CssCombinator.GENERAL_SIBLING -> {
                        val siblings = base.selectorParent?.selectorChildren ?: return emptyList()
                        val idx = siblings.indexOfFirst { it === base }
                        if (idx >= 0 && idx + 1 < siblings.size) siblings.subList(idx + 1, siblings.size) else emptyList()
                    }
                }
            }

            fun matchForward(currentBase: CssSelectorNode, stepIdx: Int, stepComb: CssCombinator): Boolean {
                val seg = chain[stepIdx]
                val candidates = collectCandidatesForStep(currentBase, stepComb)
                for (cand in candidates) {
                    if (!matchesCompound(seg, cand, ignoreInteractivePseudos = false)) continue
                    if (stepIdx == chain.lastIndex) return true
                    val nextComb = chain[stepIdx + 1].combinatorToLeft ?: CssCombinator.DESCENDANT
                    if (matchForward(cand, stepIdx + 1, nextComb)) return true
                }
                return false
            }

            return matchForward(anchor, 0, leadingComb)
        }

        private fun matchesPseudoClass(rawPseudo: String, node: CssSelectorNode): Boolean {
            val lower = rawPseudo.trim().lowercase()
            return when {
                lower == "hover" -> node.selectorIsHovered
                lower == "focus" || lower == "focus-visible" -> node.selectorIsFocused
                lower == "focus-within" -> hasFocusWithin(node)
                lower == "active" -> node.selectorIsActive
                lower == "disabled" -> !node.selectorIsEnabled
                lower == "enabled" -> node.selectorIsEnabled
                lower == "checked" -> node.selectorIsChecked
                lower == "valid" -> node.selectorIsValid
                lower == "invalid" -> !node.selectorIsValid
                lower == "placeholder-shown" -> node.selectorIsPlaceholderShown
                lower == "root" -> node.selectorParent == null ||
                    node.selectorTagName.equals("html", ignoreCase = true)
                lower == "empty" -> node.selectorChildren.isEmpty() && !node.selectorHasTextContent
                lower == "first-child" -> childPositionOneBased(node, fromEnd = false, filterSelector = null) == 1
                lower == "last-child" -> childPositionOneBased(node, fromEnd = true, filterSelector = null) == 1
                lower == "only-child" -> {
                    val siblings = node.selectorParent?.selectorChildren
                    siblings != null && siblings.size == 1 && siblings[0] === node
                }
                lower == "first-of-type" -> ofTypePositionOneBased(node, fromEnd = false) == 1
                lower == "last-of-type" -> ofTypePositionOneBased(node, fromEnd = true) == 1
                lower == "only-of-type" -> ofTypeCount(node) == 1
                lower.startsWith("nth-child(") && lower.endsWith(")") -> {
                    matchesNthChild(rawPseudo.substring(rawPseudo.indexOf('(') + 1, rawPseudo.length - 1), node, fromEnd = false)
                }
                lower.startsWith("nth-last-child(") && lower.endsWith(")") -> {
                    matchesNthChild(rawPseudo.substring(rawPseudo.indexOf('(') + 1, rawPseudo.length - 1), node, fromEnd = true)
                }
                lower.startsWith("nth-of-type(") && lower.endsWith(")") -> {
                    val expr = rawPseudo.substring(rawPseudo.indexOf('(') + 1, rawPseudo.length - 1)
                    val ab = parseAnPlusB(expr) ?: return false
                    val pos = ofTypePositionOneBased(node, fromEnd = false) ?: return false
                    matchesAnPlusB(ab.first, ab.second, pos)
                }
                lower.startsWith("nth-last-of-type(") && lower.endsWith(")") -> {
                    val expr = rawPseudo.substring(rawPseudo.indexOf('(') + 1, rawPseudo.length - 1)
                    val ab = parseAnPlusB(expr) ?: return false
                    val pos = ofTypePositionOneBased(node, fromEnd = true) ?: return false
                    matchesAnPlusB(ab.first, ab.second, pos)
                }
                else -> false
            }
        }

        private fun hasFocusWithin(node: CssSelectorNode): Boolean {
            if (node.selectorIsFocused) return true
            for (c in node.selectorChildren) {
                if (hasFocusWithin(c)) return true
            }
            return false
        }

        private fun childPositionOneBased(
            node: CssSelectorNode,
            fromEnd: Boolean,
            filterSelector: String?
        ): Int? {
            val parent = node.selectorParent ?: return null
            val siblings = parent.selectorChildren
            val filterChains = filterSelector?.let { sel ->
                splitTopLevelSelectorList(sel).mapNotNull { br -> parseChain(br).takeIf { it.isNotEmpty() } }
            }
            if (filterChains != null) {
                val selfMatches = filterChains.any { ch -> matchesChain(ch, node) }
                if (!selfMatches) return null
            }
            var count = 0
            val range = if (fromEnd) siblings.indices.reversed() else siblings.indices
            for (idx in range) {
                val sib = siblings[idx]
                if (filterChains == null || filterChains.any { ch -> matchesChain(ch, sib) }) {
                    count++
                }
                if (sib === node) return count
            }
            return null
        }

        private fun ofTypePositionOneBased(node: CssSelectorNode, fromEnd: Boolean): Int? {
            val parent = node.selectorParent ?: return null
            val targetTag = CssRule.normalizeSelectorTag(node.selectorTagName)
            val siblings = parent.selectorChildren
            var count = 0
            val range = if (fromEnd) siblings.indices.reversed() else siblings.indices
            for (idx in range) {
                val sib = siblings[idx]
                if (CssRule.normalizeSelectorTag(sib.selectorTagName) == targetTag) {
                    count++
                }
                if (sib === node) return count
            }
            return null
        }

        private fun ofTypeCount(node: CssSelectorNode): Int {
            val parent = node.selectorParent ?: return 0
            val targetTag = CssRule.normalizeSelectorTag(node.selectorTagName)
            var count = 0
            for (sib in parent.selectorChildren) {
                if (CssRule.normalizeSelectorTag(sib.selectorTagName) == targetTag) count++
            }
            return count
        }

        private fun matchesNthChild(rawArgs: String, node: CssSelectorNode, fromEnd: Boolean): Boolean {
            val ofIdx = rawArgs.indexOf(" of ", ignoreCase = true)
            val expr = if (ofIdx >= 0) rawArgs.substring(0, ofIdx).trim() else rawArgs.trim()
            val filterSel = if (ofIdx >= 0) rawArgs.substring(ofIdx + 4).trim().takeIf { it.isNotEmpty() } else null
            val ab = parseAnPlusB(expr) ?: return false
            val pos = childPositionOneBased(node, fromEnd = fromEnd, filterSelector = filterSel) ?: return false
            return matchesAnPlusB(ab.first, ab.second, pos)
        }

        fun parseAnPlusB(rawExpr: String): Pair<Int, Int>? {
            val cleaned = rawExpr.replace(AN_PLUS_B_WHITESPACE, "").lowercase()
            if (cleaned.isEmpty()) return null
            if (cleaned == "odd") return 2 to 1
            if (cleaned == "even") return 2 to 0
            val nIdx = cleaned.indexOf('n')
            if (nIdx < 0) {
                val b = cleaned.toIntOrNull() ?: return null
                return 0 to b
            }
            val aPart = cleaned.substring(0, nIdx)
            val a = when (aPart) {
                "", "+" -> 1
                "-" -> -1
                else -> aPart.toIntOrNull() ?: return null
            }
            val bPart = cleaned.substring(nIdx + 1)
            val b = if (bPart.isEmpty()) {
                0
            } else {
                bPart.toIntOrNull() ?: return null
            }
            return a to b
        }

        fun matchesAnPlusB(a: Int, b: Int, oneBasedPosition: Int): Boolean {
            if (oneBasedPosition <= 0) return false
            if (a == 0) return oneBasedPosition == b
            val diff = oneBasedPosition - b
            if (diff % a != 0) return false
            return (diff / a) >= 0
        }
    }
}
