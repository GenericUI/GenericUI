package com.nexustools.genericui.style.css

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.Gradient

/**
 * Cross-platform CSS parser and cascade engine for resolving custom classes, compound/descendant selectors,
 * `@media` responsive queries, `@keyframes` animations, `:hover`/`:focus`/`:active` pseudo-classes,
 * `!important` declarations, and inline styles.
 *
 * Modeled after Chromium Blink's `RuleSet` + `ElementRuleCollector` + `MatchedPropertiesCache`:
 * - Rules are indexed at construction time into rightmost-compound-selector hash buckets
 *   ([idRules], [classRules], [tagRules], [universalRules]).
 * - Selector matching inspects only the candidate buckets for the element's `id`, `classes`, `tagName`,
 *   and `universalRules` using pre-parsed [CssRule] selector tokens with zero runtime `Regex` allocations.
 */
class CssStyleSheet(
    val rules: List<CssRule> = emptyList(),
    val keyframes: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    val isPrecompiled: Boolean = false,
    var sourceCssText: String = ""
) {
    private val sortedRules: List<CssRule> =
        if (isPrecompiled) rules else rules.sortedBy { it.specificity }

    // Blink-style RuleSet buckets storing indices into sortedRules (preserving specificity + source cascade order)
    private val idBuckets: Map<String, IntArray>
    private val classBuckets: Map<String, IntArray>
    private val tagBuckets: Map<String, IntArray>
    private val universalBucket: IntArray

    // Public/internal RuleSet views modeled after Blink's RuleSet
    internal val idRules: Map<String, List<CssRule>>
    internal val classRules: Map<String, List<CssRule>>
    internal val tagRules: Map<String, List<CssRule>>
    internal val universalRules: List<CssRule>
    private val mediaBreakpoints: IntArray
    private val subjectClasses: Set<String>
    private val ancestorClasses: Set<String>

    init {
        val idIndexMap = linkedMapOf<String, ArrayList<Int>>()
        val classIndexMap = linkedMapOf<String, ArrayList<Int>>()
        val tagIndexMap = linkedMapOf<String, ArrayList<Int>>()
        val universalIndices = ArrayList<Int>()
        val breakpointsSet = linkedSetOf<Int>()
        val subjectClassesSet = linkedSetOf<String>()
        val ancestorClassesSet = linkedSetOf<String>()

        for (i in sortedRules.indices) {
            val rule = sortedRules[i]
            rule.mediaMinWidthPx?.let { if (it > 0) breakpointsSet.add(it) }
            rule.mediaMaxWidthPx?.let { if (it in 1 until Int.MAX_VALUE) breakpointsSet.add(it) }
            if (rule.chainTokens.isEmpty()) continue
            subjectClassesSet.addAll(rule.subjectRequiredClasses)
            for (ancClasses in rule.ancestorRequiredClasses) {
                ancestorClassesSet.addAll(ancClasses)
            }
            val subjectId = rule.subjectRequiredId
            if (subjectId != null) {
                idIndexMap.getOrPut(subjectId) { ArrayList(4) }.add(i)
                continue
            }
            val firstClass = rule.subjectRequiredClasses.firstOrNull()
            if (firstClass != null) {
                classIndexMap.getOrPut(firstClass) { ArrayList(8) }.add(i)
                continue
            }
            val subjectTag = rule.subjectLeadingTag
            if (subjectTag.isNotEmpty()) {
                tagIndexMap.getOrPut(subjectTag) { ArrayList(8) }.add(i)
                continue
            }
            universalIndices.add(i)
        }

        idBuckets = idIndexMap.mapValues { (_, list) -> list.toIntArray() }
        classBuckets = classIndexMap.mapValues { (_, list) -> list.toIntArray() }
        tagBuckets = tagIndexMap.mapValues { (_, list) -> list.toIntArray() }
        universalBucket = universalIndices.toIntArray()
        mediaBreakpoints = breakpointsSet.sorted().toIntArray()
        subjectClasses = subjectClassesSet
        ancestorClasses = ancestorClassesSet

        idRules = idIndexMap.mapValues { (_, list) -> list.map { sortedRules[it] } }
        classRules = classIndexMap.mapValues { (_, list) -> list.map { sortedRules[it] } }
        tagRules = tagIndexMap.mapValues { (_, list) -> list.map { sortedRules[it] } }
        universalRules = universalIndices.map { sortedRules[it] }
    }

    internal fun isClassReferencedInSubject(cls: String): Boolean = cls in subjectClasses

    internal fun isClassReferencedInAncestor(cls: String): Boolean = cls in ancestorClasses

    fun resolveMediaQueryBucket(viewportWidthPx: Int): Int {
        systemMediaQueryBucketHook?.invoke(this, viewportWidthPx)?.let { return it }
        if (mediaBreakpoints.isEmpty()) return 0
        var hash = 1
        for (bp in mediaBreakpoints) {
            hash = hash * 31 + when {
                viewportWidthPx < bp -> 0
                viewportWidthPx == bp -> 1
                else -> 2
            }
        }
        return hash
    }

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
        val isDarkTheme = Application.current?.getResolvedTheme() == ThemeMode.DARK ||
            "bs-theme-dark" in classes ||
            ancestorChain.any { "bs-theme-dark" in it }
        val mediaBucket = resolveMediaQueryBucket(viewportWidthPx)
        val cacheKey = buildString {
            append(if (isDarkTheme) "dark|" else "light|")
            append(tagName).append('|').append(id ?: "").append('|').append(mediaBucket).append('|')
            append(classes.joinToString(",")).append('|')
            for (anc in ancestorChain) {
                append(anc.joinToString(",")).append('>')
            }
        }
        styleLookupCache[cacheKey]?.let { return it }

        val matchingRules = collectMatchingRules(classes, tagName, id, ancestorChain, viewportWidthPx)

        val baseRules = ArrayList<CssRule>(matchingRules.size)
        var hoverRules: ArrayList<CssRule>? = null
        var focusRules: ArrayList<CssRule>? = null
        var activeRules: ArrayList<CssRule>? = null
        var disabledRules: ArrayList<CssRule>? = null
        var checkedRules: ArrayList<CssRule>? = null

        for (rule in matchingRules) {
            when (rule.pseudoClass) {
                null -> baseRules.add(rule)
                "hover" -> (hoverRules ?: ArrayList<CssRule>(4).also { hoverRules = it }).add(rule)
                "focus", "focus-visible" -> (focusRules ?: ArrayList<CssRule>(4).also { focusRules = it }).add(rule)
                "active" -> (activeRules ?: ArrayList<CssRule>(4).also { activeRules = it }).add(rule)
                "disabled" -> (disabledRules ?: ArrayList<CssRule>(4).also { disabledRules = it }).add(rule)
                "checked" -> (checkedRules ?: ArrayList<CssRule>(4).also { checkedRules = it }).add(rule)
            }
        }

        val baseDecls = mergeRuleDeclarations(baseRules)
        val baseImportantProps = collectImportantProperties(baseRules)
        val hoverStyle = hoverRules?.let { rulesList ->
            val combined = baseRules + rulesList
            buildComputedStyleFromDeclarations(
                mergeRuleDeclarations(combined),
                importantProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes
            )
        }
        val focusStyle = focusRules?.let { rulesList ->
            val combined = baseRules + rulesList
            buildComputedStyleFromDeclarations(
                mergeRuleDeclarations(combined),
                importantProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes
            )
        }
        val activeStyle = activeRules?.let { rulesList ->
            val combined = baseRules + rulesList
            buildComputedStyleFromDeclarations(
                mergeRuleDeclarations(combined),
                importantProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes
            )
        }
        val disabledStyle = disabledRules?.let { rulesList ->
            val combined = baseRules + rulesList
            buildComputedStyleFromDeclarations(
                mergeRuleDeclarations(combined),
                importantProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes
            )
        }
        val checkedStyle = checkedRules?.let { rulesList ->
            val combined = baseRules + rulesList
            buildComputedStyleFromDeclarations(
                mergeRuleDeclarations(combined),
                importantProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes
            )
        }

        val computed = buildComputedStyleFromDeclarations(
            merged = baseDecls,
            hoverStyle = hoverStyle,
            focusStyle = focusStyle,
            activeStyle = activeStyle,
            disabledStyle = disabledStyle,
            checkedStyle = checkedStyle,
            importantProperties = baseImportantProps,
            isDarkTheme = isDarkTheme,
            classes = classes
        )
        if (styleLookupCache.size < 2048) {
            styleLookupCache[cacheKey] = computed
        }
        return computed
    }

    /**
     * Blink-style `ElementRuleCollector`: inspects only the `RuleSet` buckets matching the element's
     * [id], [classes], [tagName], and [universalBucket], returning matching rules in cascade order.
     */
    private fun collectMatchingRules(
        classes: Set<String>,
        tagName: String,
        id: String?,
        ancestorChain: List<Set<String>>,
        viewportWidthPx: Int
    ): List<CssRule> {
        if (sortedRules.isEmpty()) return emptyList()
        val normalizedTargetTag = normalizeTagName(tagName)
        val matchedIndices = ArrayList<Int>(16)

        fun checkBucket(bucket: IntArray?) {
            if (bucket == null) return
            for (ruleIdx in bucket) {
                val rule = sortedRules[ruleIdx]
                if (rule.matchesMediaQuery(viewportWidthPx) &&
                    matchesRule(rule, classes, normalizedTargetTag, id, ancestorChain)
                ) {
                    matchedIndices.add(ruleIdx)
                }
            }
        }

        if (!id.isNullOrEmpty()) {
            checkBucket(idBuckets[id])
        }
        for (cls in classes) {
            if (cls.isNotEmpty()) {
                checkBucket(classBuckets[cls])
            }
        }
        if (normalizedTargetTag.isNotEmpty()) {
            checkBucket(tagBuckets[normalizedTargetTag])
        }
        checkBucket(universalBucket)

        if (matchedIndices.isEmpty()) return emptyList()
        if (matchedIndices.size == 1) return listOf(sortedRules[matchedIndices[0]])

        matchedIndices.sort()
        val out = ArrayList<CssRule>(matchedIndices.size)
        var lastIdx = -1
        for (idx in matchedIndices) {
            if (idx != lastIdx) {
                out.add(sortedRules[idx])
                lastIdx = idx
            }
        }
        return out
    }

    /**
     * Builds the ordered ancestor token chain (from outermost root to immediate parent) for [component],
     * including each ancestor's CSS classes, `htmlTag`, and `#id`.
     */
    fun buildAncestorChain(component: AbstractComponent): List<Set<String>> {
        val isDarkTheme = Application.current?.getResolvedTheme() == ThemeMode.DARK
        val ancestors = mutableListOf<Set<String>>()
        var p = component.parent
        while (p != null) {
            val pTokens = linkedSetOf<String>()
            pTokens.addAll(p.getClasses())
            pTokens.addAll(p.getBootstrap().toEnumClassSet(p.componentClassPrefix))
            if (p.componentClassPrefix.isNotBlank()) {
                for (prefixCls in p.componentClassPrefix.split(WHITESPACE_REGEX)) {
                    if (prefixCls.isNotEmpty()) pTokens.add(prefixCls)
                }
            }
            val pTag = normalizeTagName(p.htmlTag.ifEmpty { "genericui:component" })
            pTokens.add(pTag)
            pTokens.add("tag:$pTag")
            if (pTag == "app" || pTag == "genericui:app") {
                pTokens.add("app")
                pTokens.add("genericui:app")
                pTokens.add("tag:app")
                pTokens.add("tag:genericui:app")
                if (isDarkTheme) {
                    pTokens.add("bs-theme-dark")
                }
            } else if (pTag == "fragment" || pTag == "genericui:fragment") {
                pTokens.add("fragment")
                pTokens.add("genericui:fragment")
                pTokens.add("tag:fragment")
                pTokens.add("tag:genericui:fragment")
            }
            p.id?.takeIf { it.isNotEmpty() }?.let { pTokens.add("#$it") }
            ancestors.add(pTokens)
            p = p.parent
        }
        val ordered = ancestors.reversed().toMutableList()
        val selfTag = normalizeTagName(component.htmlTag.ifEmpty { "genericui:component" })
        val hasAppAncestor = ordered.any { "genericui:app" in it }
        if (selfTag != "genericui:app" &&
            !hasAppAncestor &&
            (component is com.nexustools.genericui.component.fragment.Fragment || ordered.isNotEmpty())
        ) {
            val appRootSet = linkedSetOf("app", "genericui:app", "tag:app", "tag:genericui:app")
            if (isDarkTheme) {
                appRootSet.add("bs-theme-dark")
            }
            ordered.add(0, appRootSet)
        } else if (isDarkTheme && ordered.isNotEmpty() && "bs-theme-dark" !in ordered[0]) {
            ordered[0] = ordered[0] + "bs-theme-dark"
        }
        return ordered
    }

    /**
     * Resolves only the stylesheet-matched [ComputedStyle] for [component] (without layering inline
     * [ComponentStyle] properties), used by Blink-style `MatchedPropertiesCache` (`cachedSheetBaseStyle`)
     * on [AbstractComponent].
     */
    internal fun resolveSheetStyleForComponent(
        component: AbstractComponent,
        tagName: String = component.htmlTag.ifEmpty { "genericui:component" },
        viewportWidthPx: Int = 1280
    ): ComputedStyle {
        systemStyleResolverHook?.invoke(this, component, tagName, viewportWidthPx)?.let { return it }
        val classes = linkedSetOf<String>()
        classes.addAll(component.getClasses())
        classes.addAll(component.getBootstrap().toEnumClassSet(component.componentClassPrefix))
        if (component.componentClassPrefix.isNotBlank()) {
            for (prefixCls in component.componentClassPrefix.split(WHITESPACE_REGEX)) {
                if (prefixCls.isNotEmpty()) classes.add(prefixCls)
            }
        }
        val orderedAncestors = buildAncestorChain(component)
        return resolveStyleForClasses(
            classes = classes,
            tagName = tagName,
            id = component.id,
            ancestorChain = orderedAncestors,
            viewportWidthPx = viewportWidthPx
        )
    }

    /**
     * Resolves a [ComputedStyle] directly for a GenericUI [component] by walking its [AbstractComponent.parent]
     * hierarchy to evaluate descendant (`.page .menu.step-wizard`, `header h1`, `genericui:menubar genericui:menuitem`)
     * and child (`>`) combinators, then layering any inline [ComponentStyle] declarations on top.
     */
    fun resolveStyleForComponent(
        component: AbstractComponent,
        tagName: String = component.htmlTag.ifEmpty { "genericui:component" },
        viewportWidthPx: Int = 1280
    ): ComputedStyle {
        var resolved = resolveSheetStyleForComponent(component, tagName, viewportWidthPx)
        val compStyle = component.getStyle()
        if (!compStyle.isEmpty()) {
            resolved = compStyle.toComputedStyle(resolved)
        }
        return resolved
    }

    private fun collectImportantProperties(sortedRules: List<CssRule>): Set<String> {
        var important: LinkedHashSet<String>? = null
        for (rule in sortedRules) {
            if (rule.importantProps.isNotEmpty()) {
                val set = important ?: LinkedHashSet<String>().also { important = it }
                set.addAll(rule.importantProps)
            }
        }
        return important ?: emptySet()
    }

    private fun mergeRuleDeclarations(sortedRules: List<CssRule>): Map<String, String> {
        val normalDecls = linkedMapOf<String, String>()
        var importantDecls: LinkedHashMap<String, String>? = null
        for (rule in sortedRules) {
            val impProps = rule.importantProps
            for ((prop, cleanVal) in rule.cleanedDeclarations) {
                if (prop in impProps) {
                    val impMap = importantDecls ?: LinkedHashMap<String, String>().also { importantDecls = it }
                    impMap[prop] = cleanVal
                } else if (importantDecls == null || prop !in importantDecls!!) {
                    normalDecls[prop] = cleanVal
                }
            }
        }
        if (importantDecls != null) {
            normalDecls.putAll(importantDecls!!)
        }
        return normalDecls
    }

    private fun matchesRule(
        rule: CssRule,
        classes: Set<String>,
        normalizedTargetTag: String,
        id: String?,
        ancestorChain: List<Set<String>>
    ): Boolean {
        if (rule.chainTokens.isEmpty()) return false
        if (rule.isUniversalStarOnly) return true

        if (!matchesSubjectToken(rule, classes, normalizedTargetTag, id)) {
            return false
        }
        val ancestorTokens = rule.ancestorTokens
        if (ancestorTokens.isEmpty()) return true

        if (ancestorChain.isEmpty()) {
            if (!rule.subjectHasClassOrId) return false
            for (i in ancestorTokens.indices) {
                val reqTag = rule.ancestorLeadingTags[i]
                val reqClasses = rule.ancestorRequiredClasses[i]
                if (reqTag.isNotEmpty()) return false
                if (reqClasses.isEmpty()) return false
            }
            return true
        }

        val flags = rule.directParentFlags
        var ancestorIdx = ancestorChain.lastIndex
        for (i in ancestorTokens.indices.reversed()) {
            val requireDirect = if (i < flags.size) flags[i] else false
            if (requireDirect) {
                if (ancestorIdx < 0 || !matchesPreparsedAncestor(rule, i, ancestorChain[ancestorIdx])) {
                    return false
                }
                ancestorIdx--
            } else {
                var found = false
                while (ancestorIdx >= 0) {
                    if (matchesPreparsedAncestor(rule, i, ancestorChain[ancestorIdx])) {
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

    private fun matchesSubjectToken(
        rule: CssRule,
        classes: Set<String>,
        normalizedTargetTag: String,
        id: String?
    ): Boolean {
        if (rule.subjectToken == "*") return true

        val reqIds = rule.subjectRequiredIds
        if (reqIds.isNotEmpty() && (id == null || reqIds.any { it != id })) {
            return false
        }

        val reqClasses = rule.subjectRequiredClasses
        if (reqClasses.isNotEmpty() && !classes.containsAll(reqClasses)) {
            return false
        }

        val normalizedLeadingTag = rule.subjectLeadingTag
        if (normalizedLeadingTag.isNotEmpty()) {
            if (normalizedLeadingTag != normalizedTargetTag) return false
        }

        return reqIds.isNotEmpty() || reqClasses.isNotEmpty() || normalizedLeadingTag.isNotEmpty()
    }

    private fun matchesPreparsedAncestor(
        rule: CssRule,
        ancestorStepIndex: Int,
        ancestorEntry: Set<String>
    ): Boolean {
        val tok = rule.ancestorTokens[ancestorStepIndex]
        if (tok == "*") return true

        val idMatches = rule.ancestorRequiredIds[ancestorStepIndex]
        if (idMatches.isNotEmpty() && idMatches.any { "#$it" !in ancestorEntry && it !in ancestorEntry }) {
            return false
        }

        val reqClasses = rule.ancestorRequiredClasses[ancestorStepIndex]
        if (reqClasses.isNotEmpty() && !ancestorEntry.containsAll(reqClasses)) {
            return false
        }

        val normalizedReqTag = rule.ancestorLeadingTags[ancestorStepIndex]
        if (normalizedReqTag.isNotEmpty()) {
            val matchedTag = "tag:$normalizedReqTag" in ancestorEntry ||
                normalizedReqTag in ancestorEntry ||
                ancestorEntry.any { it.equals(normalizedReqTag, ignoreCase = true) }
            if (!matchedTag) return false
        }

        return idMatches.isNotEmpty() || reqClasses.isNotEmpty() || normalizedReqTag.isNotEmpty()
    }

    private fun normalizeTagName(tag: String): String =
        normalizeCustomGenericUiTagSelector(tag)

    internal fun normalizeCustomGenericUiTagSelector(rawTag: String): String =
        CssRule.normalizeSelectorTag(rawTag)

    companion object {
        var systemStyleResolverHook: ((
            sheet: CssStyleSheet,
            component: AbstractComponent,
            tagName: String,
            viewportWidthPx: Int
        ) -> ComputedStyle?)? = null

        var systemMediaQueryBucketHook: ((
            sheet: CssStyleSheet,
            viewportWidthPx: Int
        ) -> Int?)? = null

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val COMMENT_REGEX = Regex("/\\*[\\s\\S]*?\\*/")
        private val KEYFRAMES_PREFIX_REGEX = Regex("^@keyframes\\s+", RegexOption.IGNORE_CASE)
        private val MIN_WIDTH_REGEX = Regex("min-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
        private val MAX_WIDTH_REGEX = Regex("max-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
        private val URL_REGEX = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE)

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

        fun precompiledFromCompiledRules(
            compiledRules: List<CompiledStyleRule>,
            keyframes: Map<String, Map<String, Map<String, String>>> = emptyMap(),
            rawCss: String = ""
        ): CssStyleSheet = CssStyleSheet(
            rules = compiledRules.map { it.toCssRule() }.sortedBy { it.specificity },
            keyframes = keyframes,
            isPrecompiled = true,
            sourceCssText = rawCss
        )

        fun parseInlineStyle(inlineCss: String): ComputedStyle {
            val decls = parseDeclarations(inlineCss)
            val cleaned = decls.mapValues { (_, v) ->
                v.replace(CssRule.IMPORTANT_STRIP_REGEX, "").trim()
            }
            return buildComputedStyleFromDeclarations(cleaned)
        }

        internal fun buildComputedStyleFromDeclarations(
            merged: Map<String, String>,
            hoverStyle: ComputedStyle? = null,
            focusStyle: ComputedStyle? = null,
            activeStyle: ComputedStyle? = null,
            disabledStyle: ComputedStyle? = null,
            checkedStyle: ComputedStyle? = null,
            importantProperties: Set<String> = emptySet(),
            isDarkTheme: Boolean = Application.current?.getResolvedTheme() == ThemeMode.DARK,
            classes: Set<String> = emptySet()
        ): ComputedStyle {
            val accentColor = merged["accent-color"]?.let { resolveCssColorValue(it, isDarkTheme) }
            val bgProp = merged["background-color"] ?: merged["background"]
            val bgColor = bgProp?.let { extractColorFromBackgroundShorthand(it, isDarkTheme) }
            val bgImageRaw = merged["background-image"] ?: merged["background"]
            val bgGradient = bgImageRaw?.let { Gradient.fromCss(it) }
            val bgImage = bgImageRaw?.let { extractUrlFromCssValue(it) }
            var fgColor = merged["color"]?.let { resolveCssColorValue(it, isDarkTheme) }
            if (isDarkTheme && fgColor != null &&
                fgColor.r == 33 && fgColor.g == 37 && fgColor.b == 41 &&
                "text-dark" !in classes && "text-black" !in classes
            ) {
                val hasLightBg = bgColor != null && bgColor.alphaFloat > 0.2f &&
                    ((bgColor.r * 77 + bgColor.g * 150 + bgColor.b * 29) shr 8) > 140
                if (!hasLightBg) {
                    fgColor = RgbaColor(222, 226, 230, fgColor.a)
                }
            }

            var borderColor = merged["border-color"]?.let { resolveCssColorValue(it, isDarkTheme) }
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
                            else -> resolveCssColorValue(tok, isDarkTheme)?.let { borderColor = it }
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
            val filter = merged["filter"]?.let { CssFilter.parse(it) }

            return ComputedStyle(
                backgroundColor = bgColor,
                backgroundGradient = bgGradient,
                textColor = fgColor,
                accentColor = accentColor,
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
                customCursor = merged["cursor"]?.let { CssCustomCursor.parse(it) },
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
                filter = filter,
                hoverStyle = hoverStyle,
                focusStyle = focusStyle,
                activeStyle = activeStyle,
                disabledStyle = disabledStyle,
                checkedStyle = checkedStyle,
                rawDeclarations = merged,
                importantProperties = importantProperties
            )
        }

        internal fun resolveCssColorValue(rawValue: String, isDarkTheme: Boolean): RgbaColor? {
            val trimmed = rawValue.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("var(", ignoreCase = true) && trimmed.endsWith(")")) {
                val inner = trimmed.substring(4, trimmed.length - 1).trim()
                val commaIdx = inner.indexOf(',')
                val varName = (if (commaIdx >= 0) inner.substring(0, commaIdx) else inner).trim().lowercase()
                val fallback = if (commaIdx >= 0) inner.substring(commaIdx + 1).trim() else null
                when (varName) {
                    "--bs-body-color",
                    "--bs-card-color",
                    "--bs-list-group-color",
                    "--bs-dropdown-color",
                    "--bs-nav-link-color",
                    "--bs-heading-color" -> return if (isDarkTheme) {
                        RgbaColor(222, 226, 230, 1.0f) // #dee2e6
                    } else {
                        RgbaColor(33, 37, 41, 1.0f) // #212529
                    }
                    "--bs-emphasis-color" -> return if (isDarkTheme) {
                        RgbaColor(255, 255, 255, 1.0f) // #ffffff
                    } else {
                        RgbaColor(0, 0, 0, 1.0f) // #000000
                    }
                    "--bs-secondary-color" -> return if (isDarkTheme) {
                        RgbaColor(222, 226, 230, 0.75f)
                    } else {
                        RgbaColor(33, 37, 41, 0.75f)
                    }
                    "--bs-tertiary-color" -> return if (isDarkTheme) {
                        RgbaColor(222, 226, 230, 0.5f)
                    } else {
                        RgbaColor(33, 37, 41, 0.5f)
                    }
                    "--bs-body-bg",
                    "--bs-card-bg",
                    "--bs-list-group-bg",
                    "--bs-dropdown-bg" -> return if (isDarkTheme) {
                        RgbaColor(33, 37, 41, 1.0f) // #212529
                    } else {
                        RgbaColor(255, 255, 255, 1.0f) // #ffffff
                    }
                    "--bs-secondary-bg" -> return if (isDarkTheme) {
                        RgbaColor(52, 58, 64, 1.0f) // #343a40
                    } else {
                        RgbaColor(233, 236, 239, 1.0f) // #e9ecef
                    }
                    "--bs-tertiary-bg" -> return if (isDarkTheme) {
                        RgbaColor(43, 48, 53, 1.0f) // #2b3035
                    } else {
                        RgbaColor(248, 249, 250, 1.0f) // #f8f9fa
                    }
                    "--bs-border-color" -> return if (isDarkTheme) {
                        RgbaColor(73, 80, 87, 1.0f) // #495057
                    } else {
                        RgbaColor(222, 226, 230, 1.0f) // #dee2e6
                    }
                }
                if (!fallback.isNullOrEmpty()) {
                    return resolveCssColorValue(fallback, isDarkTheme)
                }
                return null
            }
            return RgbaColor.fromCss(trimmed)
        }

        internal fun extractColorFromBackgroundShorthand(value: String, isDarkTheme: Boolean = false): RgbaColor? {
            if (Gradient.fromCss(value) != null && resolveCssColorValue(value, isDarkTheme) == null) {
                for (token in splitPreservingParens(value)) {
                    if (!token.lowercase().contains("gradient(")) {
                        resolveCssColorValue(token, isDarkTheme)?.let { return it }
                    }
                }
                return null
            }
            resolveCssColorValue(value, isDarkTheme)?.let { return it }
            for (token in splitPreservingParens(value)) {
                if (!token.lowercase().contains("gradient(")) {
                    resolveCssColorValue(token, isDarkTheme)?.let { return it }
                }
            }
            return null
        }

        internal fun extractUrlFromCssValue(value: String): String? {
            val match = URL_REGEX.find(value)
            return match?.groupValues?.get(1)?.trim()
        }

        internal fun parseAspectRatio(raw: String): Float? {
            val clean = raw.trim()
            if (clean.contains("/")) {
                val parts = clean.split("/").map { it.trim().toFloatOrNull() }
                val w = parts.getOrNull(0) ?: return null
                val h = parts.getOrNull(1) ?: return null
                return if (h != 0f) w / h else null
            }
            return clean.toFloatOrNull()
        }

        internal fun splitPreservingParens(input: String): List<String> {
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
            val cleaned = cssText.replace(COMMENT_REGEX, "")

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
                        val animName = header.replace(KEYFRAMES_PREFIX_REGEX, "").trim()
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
                        val minW = MIN_WIDTH_REGEX.find(header)?.groupValues?.get(1)?.toIntOrNull()
                        val maxW = MAX_WIDTH_REGEX.find(header)?.groupValues?.get(1)?.toIntOrNull()
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
                        for (rawSel in selectors) {
                            val sel = CssRule.sanitizeSelectorOrNull(rawSel) ?: continue
                            rules.add(CssRule(sel, decls))
                        }
                    }
                }
            }

            return CssStyleSheet(rules, keyframes, sourceCssText = cssText)
        }

        fun empty(): CssStyleSheet = CssStyleSheet()

        fun parseScss(scssSource: String): CssStyleSheet =
            com.nexustools.genericui.style.sass.SassCompiler.compileToStyleSheet(scssSource)

        fun fromScss(scssSource: String): CssStyleSheet =
            com.nexustools.genericui.style.sass.SassCompiler.compileToStyleSheet(scssSource)
    }

    fun applyToTree(root: AbstractComponent, viewportWidthPx: Int = 1280) {
        root.applyStyleSheet(this, viewportWidthPx)
    }
}
