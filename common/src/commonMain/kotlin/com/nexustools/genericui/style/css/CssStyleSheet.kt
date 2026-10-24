package com.nexustools.genericui.style.css

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.style.RgbaColor

/**
 * Cross-platform CSS parser and cascade engine for resolving custom classes, compound/descendant selectors,
 * `@media` responsive queries, `@keyframes` animations, `:hover`/`:focus`/`:active` pseudo-classes,
 * `!important` declarations, and inline styles.
 */
class CssStyleSheet(
    val rules: List<CssRule> = emptyList(),
    val keyframes: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    val isPrecompiled: Boolean = false
) {
    private val sortedRules: List<CssRule> =
        if (isPrecompiled) rules else rules.sortedBy { it.specificity }

    private val styleLookupCache = linkedMapOf<String, ComputedStyle>()

    /**
     * Resolves a [ComputedStyle] for a set of CSS classes, optional element tag, optional ID,
     * optional ancestor class chain, and viewport width (for `@media (min-width: ...)` rules).
     */
    fun resolveStyleForClasses(
        classes: Set<String>,
        tagName: String = "div",
        id: String? = null,
        ancestorChain: List<Set<String>> = emptyList(),
        viewportWidthPx: Int = 1280
    ): ComputedStyle {
        val cacheKey = buildString {
            append(tagName).append('|').append(id ?: "").append('|').append(viewportWidthPx).append('|')
            append(classes.joinToString(",")).append('|')
            for (anc in ancestorChain) {
                append(anc.joinToString(",")).append('>')
            }
        }
        styleLookupCache[cacheKey]?.let { return it }

        val matchingRules = sortedRules.filter { rule ->
            rule.matchesMediaQuery(viewportWidthPx) &&
                matchesFullSelector(rule.baseSelectorWithoutPseudo, classes, tagName, id, ancestorChain)
        }

        val baseRules = matchingRules.filter { it.pseudoClass == null }
        val hoverRules = matchingRules.filter { it.pseudoClass == "hover" }
        val focusRules = matchingRules.filter { it.pseudoClass == "focus" }
        val activeRules = matchingRules.filter { it.pseudoClass == "active" }

        val baseDecls = mergeRuleDeclarations(baseRules)
        val hoverStyle = if (hoverRules.isNotEmpty()) {
            buildComputedStyleFromDeclarations(mergeRuleDeclarations(baseRules + hoverRules))
        } else {
            null
        }
        val focusStyle = if (focusRules.isNotEmpty()) {
            buildComputedStyleFromDeclarations(mergeRuleDeclarations(baseRules + focusRules))
        } else {
            null
        }
        val activeStyle = if (activeRules.isNotEmpty()) {
            buildComputedStyleFromDeclarations(mergeRuleDeclarations(baseRules + activeRules))
        } else {
            null
        }

        val computed = buildComputedStyleFromDeclarations(
            merged = baseDecls,
            hoverStyle = hoverStyle,
            focusStyle = focusStyle,
            activeStyle = activeStyle
        )
        if (styleLookupCache.size < 2048) {
            styleLookupCache[cacheKey] = computed
        }
        return computed
    }

    /**
     * Resolves a [ComputedStyle] directly for a GenericUI [component] by walking its [AbstractComponent.parent]
     * hierarchy to evaluate descendant (`.page .menu.chargen`) and child (`>`) combinators.
     */
    fun resolveStyleForComponent(
        component: AbstractComponent,
        tagName: String = component.componentClassPrefix.ifEmpty { "div" },
        viewportWidthPx: Int = 1280
    ): ComputedStyle {
        val classes = component.getClass()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .toSet()
        val ancestors = mutableListOf<Set<String>>()
        var p = component.parent
        while (p != null) {
            val pClasses = p.getClass()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .toSet()
            ancestors.add(pClasses)
            p = p.parent
        }
        // Ancestors from outermost root to immediate parent
        val orderedAncestors = ancestors.reversed()
        val sheetStyle = resolveStyleForClasses(
            classes = classes,
            tagName = tagName,
            id = component.id,
            ancestorChain = orderedAncestors,
            viewportWidthPx = viewportWidthPx
        )
        val inlineStyleStr = component.inlineStyle
        return if (inlineStyleStr.isNotBlank()) {
            sheetStyle.mergeWith(parseInlineStyle(inlineStyleStr))
        } else {
            sheetStyle
        }
    }

    private fun mergeRuleDeclarations(sortedRules: List<CssRule>): Map<String, String> {
        val normalDecls = linkedMapOf<String, String>()
        val importantDecls = linkedMapOf<String, String>()
        for (rule in sortedRules) {
            for ((prop, rawVal) in rule.declarations) {
                val isImportant = rawVal.contains("!important", ignoreCase = true)
                val cleanVal = rawVal.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "").trim()
                if (isImportant) {
                    importantDecls[prop] = cleanVal
                } else if (prop !in importantDecls) {
                    normalDecls[prop] = cleanVal
                }
            }
        }
        normalDecls.putAll(importantDecls)
        return normalDecls
    }

    private fun matchesFullSelector(
        selector: String,
        classes: Set<String>,
        tagName: String,
        id: String?,
        ancestorChain: List<Set<String>>
    ): Boolean {
        val normalized = selector.trim().replace(Regex("\\s*>\\s*"), " > ")
        if (normalized.isEmpty()) return false
        if (normalized == "*") return true

        val parts = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false

        // Last token must match the target element itself
        val targetToken = parts.last()
        if (!matchesCompoundToken(targetToken, classes, tagName, id)) {
            return false
        }
        if (parts.size == 1) return true

        // Verify ancestor chain for multi-part selectors (e.g. ".page .menu.chargen .vhlisting" or ".menu > .d-flex")
        val ancestorTokens = parts.dropLast(1).filter { it != ">" }
        if (ancestorTokens.isEmpty()) return true

        if (ancestorChain.isEmpty()) {
            // When resolving standalone class sets without a live parent tree, allow match if all classes in selector are present
            // or if the target compound token explicitly matched a unique class/id.
            val targetHasClassOrId = targetToken.contains(".") || targetToken.contains("#")
            return targetHasClassOrId && ancestorTokens.all { tok ->
                val reqClasses = extractClassesFromToken(tok)
                reqClasses.all { it in classes } || reqClasses.isNotEmpty()
            }
        }

        var ancestorIdx = ancestorChain.lastIndex
        val requireDirectParent = parts.size >= 2 && parts[parts.size - 2] == ">"
        for (i in ancestorTokens.indices.reversed()) {
            val tok = ancestorTokens[i]
            val reqClasses = extractClassesFromToken(tok)
            if (reqClasses.isEmpty()) continue
            if (i == ancestorTokens.lastIndex && requireDirectParent) {
                if (ancestorIdx < 0 || !ancestorChain[ancestorIdx].containsAll(reqClasses)) {
                    return false
                }
                ancestorIdx--
            } else {
                var found = false
                while (ancestorIdx >= 0) {
                    if (ancestorChain[ancestorIdx].containsAll(reqClasses)) {
                        found = true
                        ancestorIdx--
                        break
                    }
                    ancestorIdx--
                }
                if (!found) return false
            }
        }
        return true
    }

    private fun extractClassesFromToken(token: String): Set<String> =
        Regex("\\.([a-zA-Z0-9_-]+)").findAll(token).map { it.groupValues[1] }.toSet()

    private fun matchesCompoundToken(
        token: String,
        classes: Set<String>,
        tagName: String,
        id: String?
    ): Boolean {
        val clean = token.trim()
        if (clean == "*") return true

        val idMatches = Regex("#([a-zA-Z0-9_-]+)").findAll(clean).map { it.groupValues[1] }.toList()
        if (idMatches.isNotEmpty() && (id == null || idMatches.any { it != id })) {
            return false
        }

        val classMatches = extractClassesFromToken(clean)
        if (classMatches.isNotEmpty() && !classes.containsAll(classMatches)) {
            return false
        }

        val leadingTag = clean.takeWhile { it != '.' && it != '#' && it != '[' && it != ':' }.trim()
        if (leadingTag.isNotEmpty() && !leadingTag.equals(tagName, ignoreCase = true)) {
            return false
        }

        return idMatches.isNotEmpty() || classMatches.isNotEmpty() || leadingTag.isNotEmpty()
    }

    companion object {

        /**
         * Constructs a precompiled [CssStyleSheet] from build-time pre-parsed and specificity-sorted
         * [CssRule]s and `@keyframes` maps so zero SASS/CSS parsing occurs at runtime.
         */
        fun precompiled(
            rules: List<CssRule>,
            keyframes: Map<String, Map<String, Map<String, String>>> = emptyMap()
        ): CssStyleSheet = CssStyleSheet(
            rules = rules.sortedBy { it.specificity },
            keyframes = keyframes,
            isPrecompiled = true
        )

        fun parseInlineStyle(inlineCss: String): ComputedStyle {
            val decls = parseDeclarations(inlineCss)
            val cleaned = decls.mapValues { (_, v) ->
                v.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "").trim()
            }
            return buildComputedStyleFromDeclarations(cleaned)
        }

        internal fun buildComputedStyleFromDeclarations(
            merged: Map<String, String>,
            hoverStyle: ComputedStyle? = null,
            focusStyle: ComputedStyle? = null,
            activeStyle: ComputedStyle? = null
        ): ComputedStyle {
            val bgProp = merged["background-color"] ?: merged["background"]
            val bgColor = bgProp?.let { extractColorFromBackgroundShorthand(it) }
            val bgImage = (merged["background-image"] ?: merged["background"])?.let { extractUrlFromCssValue(it) }
            val fgColor = merged["color"]?.let { RgbaColor.fromCss(it) }

            var borderColor = merged["border-color"]?.let { RgbaColor.fromCss(it) }
            var borderWidthPx = merged["border-width"]?.removeSuffix("px")?.trim()?.toFloatOrNull()
            var borderStyle = merged["border-style"]
            merged["border"]?.let { borderShorthand ->
                if (borderShorthand.equals("none", ignoreCase = true) || borderShorthand == "0") {
                    borderStyle = "none"
                    borderWidthPx = 0f
                } else {
                    val tokens = splitPreservingParens(borderShorthand)
                    for (tok in tokens) {
                        val lower = tok.lowercase()
                        when {
                            lower in setOf("solid", "dashed", "dotted", "double", "none") -> borderStyle = lower
                            lower.endsWith("px") -> borderWidthPx = lower.removeSuffix("px").toFloatOrNull()
                            else -> RgbaColor.fromCss(tok)?.let { borderColor = it }
                        }
                    }
                }
            }

            val radiusRaw = merged["border-radius"]?.trim()
            val borderRadiusPx = radiusRaw?.takeIf { it.endsWith("px") }?.removeSuffix("px")?.toFloatOrNull()
                ?: radiusRaw?.toFloatOrNull()
            val borderRadiusPercent = radiusRaw?.takeIf { it.endsWith("%") }?.removeSuffix("%")?.toFloatOrNull()

            val fontSizeRaw = merged["font-size"]?.trim()
            val fontSizePx = when {
                fontSizeRaw == null -> null
                fontSizeRaw.endsWith("px") -> fontSizeRaw.removeSuffix("px").toFloatOrNull()
                fontSizeRaw.endsWith("em") -> (fontSizeRaw.removeSuffix("em").toFloatOrNull() ?: 1f) * 16f
                fontSizeRaw.endsWith("rem") -> (fontSizeRaw.removeSuffix("rem").toFloatOrNull() ?: 1f) * 16f
                else -> fontSizeRaw.toFloatOrNull()
            }
            val fontSizePercent = fontSizeRaw?.takeIf { it.endsWith("%") }?.removeSuffix("%")?.toFloatOrNull()

            val fontFamily = merged["font-family"]?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
            val fontWeightRaw = merged["font-weight"]?.trim()?.lowercase()
            val fontWeightNumeric = when (fontWeightRaw) {
                "normal" -> 400
                "bold" -> 700
                "lighter" -> 300
                "bolder" -> 800
                else -> fontWeightRaw?.toIntOrNull()
            }

            val opacity = merged["opacity"]?.toFloatOrNull() ?: 1.0f
            val boxShadow = merged["box-shadow"] ?: merged["-webkit-box-shadow"]
            val textShadow = merged["text-shadow"]
            val transform = merged["transform"]
            val transition = merged["transition"]
            val zIndex = merged["z-index"]?.toIntOrNull()
            val aspectRatio = merged["aspect-ratio"]?.let { parseAspectRatio(it) }
            val pointerEvents = merged["pointer-events"]?.let { !it.equals("none", ignoreCase = true) } ?: true

            return ComputedStyle(
                backgroundColor = bgColor,
                textColor = fgColor,
                borderColor = borderColor,
                borderWidthPx = borderWidthPx,
                borderStyle = borderStyle,
                borderRadiusPx = borderRadiusPx,
                borderRadiusPercent = borderRadiusPercent,
                fontSizePx = fontSizePx,
                fontSizePercent = fontSizePercent,
                fontFamily = fontFamily,
                fontWeightNumeric = fontWeightNumeric,
                opacity = opacity,
                backgroundImageUrl = bgImage,
                backgroundRepeat = merged["background-repeat"],
                backgroundAttachment = merged["background-attachment"],
                backgroundPosition = merged["background-position"],
                backgroundSize = merged["background-size"],
                textShadow = textShadow,
                boxShadow = boxShadow,
                transform = transform,
                transition = transition,
                zIndex = zIndex,
                aspectRatio = aspectRatio,
                pointerEventsEnabled = pointerEvents,
                cursor = merged["cursor"],
                position = merged["position"],
                display = merged["display"],
                overflow = merged["overflow"],
                textAlign = merged["text-align"],
                verticalAlign = merged["vertical-align"],
                whiteSpace = merged["white-space"],
                floatSide = merged["float"],
                clearSide = merged["clear"],
                left = merged["left"],
                top = merged["top"],
                right = merged["right"],
                bottom = merged["bottom"],
                width = merged["width"],
                height = merged["height"],
                minWidth = merged["min-width"],
                minHeight = merged["min-height"],
                maxWidth = merged["max-width"],
                maxHeight = merged["max-height"],
                hoverStyle = hoverStyle,
                focusStyle = focusStyle,
                activeStyle = activeStyle,
                rawDeclarations = merged
            )
        }

        private fun extractColorFromBackgroundShorthand(value: String): RgbaColor? {
            RgbaColor.fromCss(value)?.let { return it }
            for (token in splitPreservingParens(value)) {
                RgbaColor.fromCss(token)?.let { return it }
            }
            return null
        }

        private fun extractUrlFromCssValue(value: String): String? {
            val match = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE).find(value)
            return match?.groupValues?.get(1)?.trim()
        }

        private fun parseAspectRatio(raw: String): Float? {
            val clean = raw.trim()
            if (clean.contains("/")) {
                val parts = clean.split("/").map { it.trim().toFloatOrNull() }
                val w = parts.getOrNull(0) ?: return null
                val h = parts.getOrNull(1) ?: return null
                return if (h != 0f) w / h else null
            }
            return clean.toFloatOrNull()
        }

        private fun splitPreservingParens(input: String): List<String> {
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
                    ch.isWhitespace() && depth == 0 -> {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current.clear()
                        }
                    }
                    else -> current.append(ch)
                }
            }
            if (current.isNotEmpty()) {
                result.add(current.toString())
            }
            return result
        }

        internal fun parseDeclarations(body: String): Map<String, String> {
            val decls = linkedMapOf<String, String>()
            for (stmt in body.split(";")) {
                val idx = stmt.indexOf(':')
                if (idx > 0) {
                    val prop = stmt.substring(0, idx).trim().lowercase()
                    val value = stmt.substring(idx + 1).trim()
                    if (prop.isNotEmpty() && value.isNotEmpty()) {
                        decls[prop] = value
                    }
                }
            }
            return decls
        }

        fun parse(cssText: String): CssStyleSheet {
            val rules = mutableListOf<CssRule>()
            val keyframes = linkedMapOf<String, Map<String, Map<String, String>>>()
            val cleaned = cssText.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

            var i = 0
            val n = cleaned.length
            while (i < n) {
                while (i < n && cleaned[i].isWhitespace()) i++
                if (i >= n) break

                val headerStart = i
                while (i < n && cleaned[i] != '{') i++
                if (i >= n) break
                val header = cleaned.substring(headerStart, i).trim()
                val bodyStart = i + 1
                var depth = 1
                i++
                while (i < n && depth > 0) {
                    if (cleaned[i] == '{') depth++
                    else if (cleaned[i] == '}') depth--
                    i++
                }
                val body = cleaned.substring(bodyStart, (i - 1).coerceAtLeast(bodyStart)).trim()

                when {
                    header.startsWith("@keyframes", ignoreCase = true) -> {
                        val animName = header.replace(Regex("^@keyframes\\s+", RegexOption.IGNORE_CASE), "").trim()
                        val frames = linkedMapOf<String, Map<String, String>>()
                        val subSheet = parse(body)
                        for (r in subSheet.rules) {
                            frames[r.selector] = r.declarations
                        }
                        if (animName.isNotEmpty()) {
                            keyframes[animName] = frames
                        }
                    }
                    header.startsWith("@media", ignoreCase = true) -> {
                        val minW = Regex("min-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
                            .find(header)?.groupValues?.get(1)?.toIntOrNull()
                        val maxW = Regex("max-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
                            .find(header)?.groupValues?.get(1)?.toIntOrNull()
                        val innerSheet = parse(body)
                        for (innerRule in innerSheet.rules) {
                            rules.add(
                                innerRule.copy(
                                    mediaMinWidthPx = minW ?: innerRule.mediaMinWidthPx,
                                    mediaMaxWidthPx = maxW ?: innerRule.mediaMaxWidthPx
                                )
                            )
                        }
                    }
                    header.isNotEmpty() -> {
                        val decls = parseDeclarations(body)
                        val selectors = header.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        for (sel in selectors) {
                            rules.add(CssRule(sel, decls))
                        }
                    }
                }
            }

            return CssStyleSheet(rules, keyframes)
        }
    }
}
