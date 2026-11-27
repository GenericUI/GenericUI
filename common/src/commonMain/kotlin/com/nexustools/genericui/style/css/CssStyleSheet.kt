package com.nexustools.genericui.style.css

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.sass.CssDeclarationApplyUtil

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
    var sourceCssText: String = "",
    val composedSheets: List<CssStyleSheet> = emptyList()
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
    private val mediaBreakpointMasks: IntArray
    private val mediaRuleIndices: IntArray
    private val subjectClasses: Set<String>
    private val ancestorClasses: Set<String>
    private val backendFilterCache = arrayOfNulls<CssStyleSheet>(BackendTarget.entries.size)
    internal var keyframeVariants: Map<String, List<Pair<Int, Map<String, Map<String, String>>>>> = emptyMap()

    init {
        val idIndexMap = linkedMapOf<String, ArrayList<Int>>()
        val classIndexMap = linkedMapOf<String, ArrayList<Int>>()
        val tagIndexMap = linkedMapOf<String, ArrayList<Int>>()
        val universalIndices = ArrayList<Int>()
        val breakpointMasksMap = linkedMapOf<Int, Int>()
        val mediaRuleIndexList = ArrayList<Int>()
        val subjectClassesSet = linkedSetOf<String>()
        val ancestorClassesSet = linkedSetOf<String>()

        for (i in sortedRules.indices) {
            val rule = sortedRules[i]
            val hasMedia = rule.mediaMinWidthPx != null || rule.mediaMaxWidthPx != null || rule.mediaQuery != null
            if (hasMedia) {
                mediaRuleIndexList.add(i)
            }
            rule.mediaMinWidthPx?.let {
                if (it > 0) {
                    breakpointMasksMap[it] = (breakpointMasksMap[it] ?: 0) or rule.backendMask
                }
            }
            rule.mediaMaxWidthPx?.let {
                if (it in 1 until Int.MAX_VALUE) {
                    breakpointMasksMap[it] = (breakpointMasksMap[it] ?: 0) or rule.backendMask
                }
            }
            if (rule.chainTokens.isEmpty() || rule.chainTokens.any { it.isEmpty() }) continue
            subjectClassesSet.addAll(rule.subjectRequiredClasses)
            subjectClassesSet.addAll(rule.subjectExcludedClasses)
            for (ancClasses in rule.ancestorRequiredClasses) {
                ancestorClassesSet.addAll(ancClasses)
            }
            for (ancExcl in rule.ancestorExcludedClasses) {
                ancestorClassesSet.addAll(ancExcl)
            }
            for (ancPosPseudos in rule.ancestorPositivePseudos) {
                if ("hover" in ancPosPseudos) ancestorClassesSet.add("pseudo:hover")
                if ("focus" in ancPosPseudos || "focus-visible" in ancPosPseudos || "focus-within" in ancPosPseudos) {
                    ancestorClassesSet.add("pseudo:focus")
                }
            }
            for (ancExclPseudos in rule.ancestorExcludedPseudos) {
                if ("hover" in ancExclPseudos) ancestorClassesSet.add("pseudo:hover")
                if ("focus" in ancExclPseudos || "focus-visible" in ancExclPseudos || "focus-within" in ancExclPseudos) {
                    ancestorClassesSet.add("pseudo:focus")
                }
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
        val sortedBreakpointEntries = breakpointMasksMap.entries.sortedBy { it.key }
        mediaBreakpoints = IntArray(sortedBreakpointEntries.size) { sortedBreakpointEntries[it].key }
        mediaBreakpointMasks = IntArray(sortedBreakpointEntries.size) { sortedBreakpointEntries[it].value }
        mediaRuleIndices = mediaRuleIndexList.toIntArray()
        subjectClasses = subjectClassesSet
        ancestorClasses = ancestorClassesSet

        idRules = idIndexMap.mapValues { (_, list) -> list.map { sortedRules[it] } }
        classRules = classIndexMap.mapValues { (_, list) -> list.map { sortedRules[it] } }
        tagRules = tagIndexMap.mapValues { (_, list) -> list.map { sortedRules[it] } }
        universalRules = universalIndices.map { sortedRules[it] }
    }

    internal fun isClassReferencedInSubject(cls: String): Boolean =
        systemIsClassReferencedHook?.invoke(this, cls, false) ?: (cls in subjectClasses)

    internal fun isClassReferencedInAncestor(cls: String): Boolean =
        systemIsClassReferencedHook?.invoke(this, cls, true) ?: (cls in ancestorClasses)

    internal fun collectDescendantInvalidation(
        component: AbstractComponent,
        changedClasses: List<String>,
        outSubjectClasses: MutableSet<String>,
        outSubjectIds: MutableSet<String>,
        outSubjectTags: MutableSet<String>
    ): Int {
        systemCollectDescendantInvalidationHook?.invoke(
            this,
            component,
            changedClasses,
            outSubjectClasses,
            outSubjectIds,
            outSubjectTags
        )?.let { return it }

        if (changedClasses.isEmpty() || ancestorClasses.none { it in changedClasses }) {
            return DESCENDANT_INVALIDATION_NONE
        }

        val ancestorId = component.id
        val ancestorNormalizedTag = CssRule.normalizeSelectorTag(component.htmlTag.ifEmpty { "genericui:component" })
        val isDarkTheme = Application.current?.getResolvedTheme() == ThemeMode.DARK
        val baseClasses = component.collectSelectorClassSnapshot()
        val needDark = ancestorNormalizedTag == "genericui:app" && isDarkTheme && "bs-theme-dark" !in baseClasses
        val needHover = component.isHovered && "pseudo:hover" !in baseClasses
        val needFocus = component.isFocused && "pseudo:focus" !in baseClasses
        val ancestorClassSet = if (needDark || needHover || needFocus) {
            LinkedHashSet(baseClasses).apply {
                if (needDark) add("bs-theme-dark")
                if (needHover) add("pseudo:hover")
                if (needFocus) add("pseudo:focus")
            }
        } else {
            baseClasses
        }

        val activeTarget = resolveActiveBackendTarget()
        var mode = DESCENDANT_INVALIDATION_NONE
        for (rule in sortedRules) {
            if (!rule.matchesBackend(activeTarget)) continue
            if (rule.ancestorTokens.isEmpty()) continue
            var matchedStep = false
            for (i in rule.ancestorTokens.indices) {
                val reqClasses = rule.ancestorRequiredClasses[i]
                val exclClasses = rule.ancestorExcludedClasses[i]
                val reqPseudos = rule.ancestorPositivePseudos[i]
                val exclPseudos = rule.ancestorExcludedPseudos[i]
                var mentionsChanged = false
                for (cls in changedClasses) {
                    if (cls in reqClasses || cls in exclClasses ||
                        (cls == "pseudo:hover" && ("hover" in reqPseudos || "hover" in exclPseudos)) ||
                        (cls == "pseudo:focus" && (
                            "focus" in reqPseudos || "focus-visible" in reqPseudos || "focus-within" in reqPseudos ||
                                "focus" in exclPseudos || "focus-visible" in exclPseudos || "focus-within" in exclPseudos
                            ))
                    ) {
                        mentionsChanged = true
                        break
                    }
                }
                if (!mentionsChanged) continue

                val reqIds = rule.ancestorRequiredIds[i]
                if (reqIds.isNotEmpty() && reqIds.any { it != ancestorId }) continue
                val rawReqTag = rule.ancestorLeadingTags[i]
                if (rawReqTag.isNotEmpty() && CssRule.normalizeSelectorTag(rawReqTag) != ancestorNormalizedTag) continue
                val exclIds = rule.ancestorExcludedIds[i]
                if (ancestorId != null && exclIds.isNotEmpty() && ancestorId in exclIds) continue
                val exclTags = rule.ancestorExcludedTags[i]
                if (exclTags.isNotEmpty() && exclTags.any { CssRule.normalizeSelectorTag(it) == ancestorNormalizedTag }) continue

                var reqClassesOk = true
                for (rc in reqClasses) {
                    if (rc !in ancestorClassSet && rc !in changedClasses) {
                        reqClassesOk = false
                        break
                    }
                }
                if (!reqClassesOk) continue

                var exclClassesOk = true
                for (ec in exclClasses) {
                    if (ec in ancestorClassSet && ec !in changedClasses) {
                        exclClassesOk = false
                        break
                    }
                }
                if (!exclClassesOk) continue

                if ("hover" in reqPseudos && "pseudo:hover" !in ancestorClassSet && "pseudo:hover" !in changedClasses) continue
                if ("hover" in exclPseudos && "pseudo:hover" in ancestorClassSet && "pseudo:hover" !in changedClasses) continue
                if (("focus" in reqPseudos || "focus-visible" in reqPseudos || "focus-within" in reqPseudos) &&
                    "pseudo:focus" !in ancestorClassSet && "pseudo:focus" !in changedClasses
                ) continue
                if (("focus" in exclPseudos || "focus-visible" in exclPseudos || "focus-within" in exclPseudos) &&
                    "pseudo:focus" in ancestorClassSet && "pseudo:focus" !in changedClasses
                ) continue

                matchedStep = true
                break
            }
            if (!matchedStep) continue

            val subjId = rule.subjectRequiredId
            if (subjId != null) {
                outSubjectIds.add(subjId)
                if (mode == DESCENDANT_INVALIDATION_NONE) mode = DESCENDANT_INVALIDATION_TARGETED
            } else if (rule.subjectRequiredClasses.isNotEmpty()) {
                outSubjectClasses.add(rule.subjectRequiredClasses.first())
                if (mode == DESCENDANT_INVALIDATION_NONE) mode = DESCENDANT_INVALIDATION_TARGETED
            } else if (rule.subjectLeadingTag.isNotEmpty()) {
                outSubjectTags.add(CssRule.normalizeSelectorTag(rule.subjectLeadingTag))
                if (mode == DESCENDANT_INVALIDATION_NONE) mode = DESCENDANT_INVALIDATION_TARGETED
            } else {
                return DESCENDANT_INVALIDATION_WHOLE_SUBTREE
            }
        }
        return mode
    }

    internal fun collectMediaInvalidation(
        oldViewportWidthPx: Int,
        newViewportWidthPx: Int,
        outSubjectClasses: MutableSet<String>,
        outSubjectIds: MutableSet<String>,
        outSubjectTags: MutableSet<String>,
        outAncestorClasses: MutableSet<String> = linkedSetOf(),
        outAncestorIds: MutableSet<String> = linkedSetOf(),
        outAncestorTags: MutableSet<String> = linkedSetOf()
    ): Int {
        systemCollectMediaInvalidationHook?.invoke(
            this,
            oldViewportWidthPx,
            newViewportWidthPx,
            outSubjectClasses,
            outSubjectIds,
            outSubjectTags,
            outAncestorClasses,
            outAncestorIds,
            outAncestorTags
        )?.let { return it }

        if (oldViewportWidthPx == newViewportWidthPx || mediaRuleIndices.isEmpty()) {
            return DESCENDANT_INVALIDATION_NONE
        }
        val activeTarget = resolveActiveBackendTarget()
        var mode = DESCENDANT_INVALIDATION_NONE
        for (idx in mediaRuleIndices) {
            val rule = sortedRules[idx]
            if (!rule.matchesBackend(activeTarget)) continue
            if (rule.matchesMediaQuery(oldViewportWidthPx) == rule.matchesMediaQuery(newViewportWidthPx)) {
                continue
            }
            val subjId = rule.subjectRequiredId
            if (subjId != null) {
                outSubjectIds.add(subjId)
                if (mode == DESCENDANT_INVALIDATION_NONE) mode = DESCENDANT_INVALIDATION_TARGETED
            } else if (rule.subjectRequiredClasses.isNotEmpty()) {
                outSubjectClasses.add(rule.subjectRequiredClasses.first())
                if (mode == DESCENDANT_INVALIDATION_NONE) mode = DESCENDANT_INVALIDATION_TARGETED
            } else {
                val subjTag = rule.subjectLeadingTag.takeIf { it.isNotEmpty() }?.let { CssRule.normalizeSelectorTag(it) }
                if (subjTag != null) {
                    outSubjectTags.add(subjTag)
                    if (mode == DESCENDANT_INVALIDATION_NONE) mode = DESCENDANT_INVALIDATION_TARGETED
                } else {
                    var targetedByAncestor = false
                    for (i in rule.ancestorTokens.indices.reversed()) {
                        val ancId = rule.ancestorRequiredIds.getOrNull(i)?.firstOrNull()
                        if (ancId != null) {
                            outAncestorIds.add(ancId)
                            targetedByAncestor = true
                            break
                        }
                        val ancCls = rule.ancestorRequiredClasses.getOrNull(i)?.firstOrNull()
                        if (ancCls != null) {
                            outAncestorClasses.add(ancCls)
                            targetedByAncestor = true
                            break
                        }
                        val ancTag = rule.ancestorLeadingTags.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { CssRule.normalizeSelectorTag(it) }
                        if (ancTag != null) {
                            outAncestorTags.add(ancTag)
                            targetedByAncestor = true
                            break
                        }
                    }
                    if (!targetedByAncestor) {
                        return DESCENDANT_INVALIDATION_WHOLE_SUBTREE
                    }
                    if (mode == DESCENDANT_INVALIDATION_NONE) mode = DESCENDANT_INVALIDATION_TARGETED
                }
            }
        }
        return mode
    }

    private fun resolveActiveBackendTarget(): BackendTarget =
        BackendStyleMask.resolveRuntimeBackendTarget(
            Application.current?.backendTarget,
            com.nexustools.genericui.core.GenericUI.activeFactory?.backendTarget
        )

    fun filterForBackend(target: BackendTarget): CssStyleSheet {
        backendFilterCache[target.ordinal]?.let { return it }
        val targetBit = BackendStyleMask.bitFor(target)

        if (composedSheets.isNotEmpty()) {
            val filteredChildren = composedSheets.map { it.filterForBackend(target) }
            val allIdentical = filteredChildren.indices.all { filteredChildren[it] === composedSheets[it] }
            val needsKfFilter = keyframeVariantsNeedFilter(targetBit)
            val result = if (allIdentical && !needsKfFilter) {
                this
            } else {
                combineCascade(filteredChildren)
            }
            backendFilterCache[target.ordinal] = result
            return result
        }

        val allRulesMatch = sortedRules.all { (it.backendMask and targetBit) != 0 }
        val needsKfFilter = keyframeVariantsNeedFilter(targetBit)
        if (allRulesMatch && !needsKfFilter) {
            backendFilterCache[target.ordinal] = this
            return this
        }

        val filteredRules = rules.filter { (it.backendMask and targetBit) != 0 }
        val filteredKeyframes = linkedMapOf<String, Map<String, Map<String, String>>>()
        val filteredKfVariants = linkedMapOf<String, List<Pair<Int, Map<String, Map<String, String>>>>>()
        for ((animName, stops) in keyframes) {
            val variants = keyframeVariants[animName]
            if (variants == null) {
                filteredKeyframes[animName] = stops
            } else {
                val matching = variants.filter { (mask, _) -> (mask and targetBit) != 0 }
                if (matching.isNotEmpty()) {
                    filteredKeyframes[animName] = matching.last().second
                    filteredKfVariants[animName] = matching
                }
            }
        }
        val filtered = CssStyleSheet(
            rules = filteredRules,
            keyframes = filteredKeyframes,
            isPrecompiled = isPrecompiled,
            sourceCssText = ""
        )
        filtered.keyframeVariants = filteredKfVariants
        backendFilterCache[target.ordinal] = filtered
        return filtered
    }

    private fun keyframeVariantsNeedFilter(targetBit: Int): Boolean {
        for (variants in keyframeVariants.values) {
            if (variants.size > 1) return true
            val singleMask = variants.firstOrNull()?.first ?: continue
            if ((singleMask and targetBit) == 0) return true
        }
        return false
    }

    fun toWebCss(): String = toBackendCss(BackendTarget.Web)

    internal fun toBackendCss(target: BackendTarget): String {
        val filtered = filterForBackend(target)
        val sb = StringBuilder()
        for (rule in filtered.rules) {
            if (rule.declarations.isEmpty()) continue
            val decls = rule.declarations.entries.joinToString("; ") { (k, v) -> "$k: $v" }
            val hasMedia = rule.mediaMinWidthPx != null || rule.mediaMaxWidthPx != null
            if (hasMedia) {
                val mediaParts = ArrayList<String>(2)
                rule.mediaMinWidthPx?.let { mediaParts.add("(min-width: ${it}px)") }
                rule.mediaMaxWidthPx?.let { mediaParts.add("(max-width: ${it}px)") }
                sb.append("@media ").append(mediaParts.joinToString(" and ")).append(" {\n")
                sb.append("  ").append(rule.selector).append(" { ").append(decls).append("; }\n")
                sb.append("}\n")
            } else {
                sb.append(rule.selector).append(" { ").append(decls).append("; }\n")
            }
        }
        for ((animName, frames) in filtered.keyframes) {
            sb.append("@keyframes ").append(animName).append(" {\n")
            for ((stop, declsMap) in frames) {
                val decls = declsMap.entries.joinToString("; ") { (k, v) -> "$k: $v" }
                sb.append("  ").append(stop).append(" { ").append(decls).append("; }\n")
            }
            sb.append("}\n")
        }
        return sb.toString().trim()
    }

    fun resolveMediaQueryBucket(viewportWidthPx: Int): Int {
        systemMediaQueryBucketHook?.invoke(this, viewportWidthPx)?.let { return it }
        if (mediaBreakpoints.isEmpty()) return 0
        val activeTarget = resolveActiveBackendTarget()
        var hash = 1
        var anyIncluded = false
        for (i in mediaBreakpoints.indices) {
            if (!BackendStyleMask.matches(mediaBreakpointMasks[i], activeTarget)) continue
            anyIncluded = true
            val bp = mediaBreakpoints[i]
            hash = hash * 31 + when {
                viewportWidthPx < bp -> 0
                viewportWidthPx == bp -> 1
                else -> 2
            }
        }
        return if (anyIncluded) hash else 0
    }

    private val styleLookupCache = linkedMapOf<String, ComputedStyle>()

    /**
     * Resolves full W3C Selectors Level 4, `@media`, `--custom-property` inheritance/`var()`,
     * interactive states, and `::before`/`::after` pseudo-element styles for a tree [node].
     */
    fun resolveStyleForNode(
        node: CssSelectorNode,
        viewportWidthPx: Int = 1280,
        viewportHeightPx: Int = 800,
        isDarkThemeOverride: Boolean? = null
    ): ComputedStyle {
        val activeTarget = resolveActiveBackendTarget()
        val isDarkTheme = isDarkThemeOverride ?: (
            Application.current?.getResolvedTheme() == ThemeMode.DARK ||
                "bs-theme-dark" in node.selectorClasses ||
                hasDarkAncestor(node)
            )
        val classes = node.selectorClasses
        val tagName = node.selectorTagName.ifEmpty { "div" }
        val normalizedTargetTag = normalizeTagName(tagName)
        val id = node.selectorId

        val matchedIndices = ArrayList<Int>(16)
        fun checkBucket(bucket: IntArray?) {
            if (bucket == null) return
            for (ruleIdx in bucket) {
                val rule = sortedRules[ruleIdx]
                if (!rule.matchesBackend(activeTarget)) continue
                if (!rule.matchesMediaQuery(viewportWidthPx, viewportHeightPx, isDarkTheme)) continue
                if (matchesRuleForNode(rule, node, ignoreInteractivePseudosOnSubject = true)) {
                    matchedIndices.add(ruleIdx)
                }
            }
        }

        if (!id.isNullOrEmpty()) {
            checkBucket(idBuckets[id])
        }
        for (cls in classes) {
            if (cls.isNotEmpty()) checkBucket(classBuckets[cls])
        }
        if (normalizedTargetTag.isNotEmpty()) {
            checkBucket(tagBuckets[normalizedTargetTag])
        }
        checkBucket(universalBucket)

        val matchedRules = if (matchedIndices.isEmpty()) {
            emptyList()
        } else {
            matchedIndices.sort()
            val out = ArrayList<CssRule>(matchedIndices.size)
            var prev = -1
            for (idx in matchedIndices) {
                if (idx != prev) {
                    out.add(sortedRules[idx])
                    prev = idx
                }
            }
            out
        }

        val baseRules = ArrayList<CssRule>(matchedRules.size)
        var hoverRules: ArrayList<CssRule>? = null
        var focusRules: ArrayList<CssRule>? = null
        var activeRules: ArrayList<CssRule>? = null
        var disabledRules: ArrayList<CssRule>? = null
        var checkedRules: ArrayList<CssRule>? = null
        var beforeRules: ArrayList<CssRule>? = null
        var afterRules: ArrayList<CssRule>? = null

        fun nodeMatchesInteractivePseudo(pseudo: String?): Boolean = when (pseudo) {
            null -> true
            "hover" -> node.selectorIsHovered
            "focus", "focus-visible" -> node.selectorIsFocused
            "focus-within" -> hasFocusWithinNode(node)
            "active" -> node.selectorIsActive
            "disabled" -> !node.selectorIsEnabled
            "checked" -> node.selectorIsChecked
            else -> true
        }

        for (rule in matchedRules) {
            when (rule.pseudoElement) {
                "before" -> if (nodeMatchesInteractivePseudo(rule.pseudoClass)) {
                    (beforeRules ?: ArrayList<CssRule>(2).also { beforeRules = it }).add(rule)
                }
                "after" -> if (nodeMatchesInteractivePseudo(rule.pseudoClass)) {
                    (afterRules ?: ArrayList<CssRule>(2).also { afterRules = it }).add(rule)
                }
                null -> when (rule.pseudoClass) {
                    "hover" -> (hoverRules ?: ArrayList<CssRule>(4).also { hoverRules = it }).add(rule)
                    "focus", "focus-visible", "focus-within" ->
                        (focusRules ?: ArrayList<CssRule>(4).also { focusRules = it }).add(rule)
                    "active" -> (activeRules ?: ArrayList<CssRule>(4).also { activeRules = it }).add(rule)
                    "disabled" -> (disabledRules ?: ArrayList<CssRule>(4).also { disabledRules = it }).add(rule)
                    "checked" -> (checkedRules ?: ArrayList<CssRule>(4).also { checkedRules = it }).add(rule)
                    else -> baseRules.add(rule)
                }
            }
        }

        val inheritedVars = node.selectorInheritedCustomProperties
        val rawBaseDecls = mergeRuleDeclarations(baseRules)
        val resolvedBaseVars = CssCustomPropertyUtil.resolveVariableMap(
            inherited = inheritedVars,
            localDeclarations = rawBaseDecls,
            isDarkTheme = isDarkTheme
        )
        val baseDecls = expandVarsInDeclarations(rawBaseDecls, resolvedBaseVars, isDarkTheme)
        val baseImportantProps = collectImportantProperties(baseRules)

        fun buildSubStateStyle(subRules: List<CssRule>?): ComputedStyle? {
            if (subRules.isNullOrEmpty()) return null
            val combined = baseRules + subRules
            val rawCombined = mergeRuleDeclarations(combined)
            val stateVars = CssCustomPropertyUtil.resolveVariableMap(
                inherited = inheritedVars,
                localDeclarations = rawCombined,
                isDarkTheme = isDarkTheme
            )
            return buildComputedStyleFromDeclarations(
                merged = expandVarsInDeclarations(rawCombined, stateVars, isDarkTheme),
                importantCssProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes,
                customProperties = stateVars
            )
        }

        fun buildPseudoElStyle(elRules: List<CssRule>?): ComputedStyle? {
            if (elRules.isNullOrEmpty()) return null
            val rawDecls = mergeRuleDeclarations(elRules)
            val elVars = CssCustomPropertyUtil.resolveVariableMap(
                inherited = resolvedBaseVars,
                localDeclarations = rawDecls,
                isDarkTheme = isDarkTheme
            )
            return buildComputedStyleFromDeclarations(
                merged = expandVarsInDeclarations(rawDecls, elVars, isDarkTheme),
                importantCssProperties = collectImportantProperties(elRules),
                isDarkTheme = isDarkTheme,
                classes = classes,
                customProperties = elVars
            )
        }

        val computed = buildComputedStyleFromDeclarations(
            merged = baseDecls,
            hoverStyle = buildSubStateStyle(hoverRules),
            focusStyle = buildSubStateStyle(focusRules),
            activeStyle = buildSubStateStyle(activeRules),
            disabledStyle = buildSubStateStyle(disabledRules),
            checkedStyle = buildSubStateStyle(checkedRules),
            importantCssProperties = baseImportantProps,
            isDarkTheme = isDarkTheme,
            classes = classes,
            customProperties = resolvedBaseVars,
            beforeStyle = buildPseudoElStyle(beforeRules),
            afterStyle = buildPseudoElStyle(afterRules)
        )
        return if (node.selectorIsHovered || node.selectorIsFocused ||
            node.selectorIsActive || !node.selectorIsEnabled || node.selectorIsChecked
        ) {
            computed.resolveForState(
                hovered = node.selectorIsHovered,
                focused = node.selectorIsFocused,
                active = node.selectorIsActive,
                enabled = node.selectorIsEnabled,
                checked = node.selectorIsChecked
            )
        } else {
            computed
        }
    }

    private fun hasDarkAncestor(node: CssSelectorNode): Boolean {
        var p = node.selectorParent
        while (p != null) {
            if ("bs-theme-dark" in p.selectorClasses) return true
            p = p.selectorParent
        }
        return false
    }

    private fun hasFocusWithinNode(node: CssSelectorNode): Boolean {
        if (node.selectorIsFocused) return true
        for (c in node.selectorChildren) {
            if (hasFocusWithinNode(c)) return true
        }
        return false
    }

    private fun matchesRuleForNode(
        rule: CssRule,
        node: CssSelectorNode,
        ignoreInteractivePseudosOnSubject: Boolean
    ): Boolean {
        if (rule.l4Segments.isEmpty()) return false
        if (rule.isUniversalStarOnly) return true
        return CssSelectorCompound.matchesChain(
            chain = rule.l4Segments,
            subjectNode = node,
            ignoreInteractivePseudosOnSubject = ignoreInteractivePseudosOnSubject
        )
    }

    private fun expandVarsInDeclarations(
        decls: Map<String, String>,
        variables: Map<String, String>,
        isDarkTheme: Boolean
    ): Map<String, String> {
        if (decls.isEmpty()) return decls
        val out = LinkedHashMap<String, String>(decls.size)
        for ((k, v) in decls) {
            if (k.startsWith("--")) continue
            out[k] = if ("var(" in v.lowercase()) {
                CssCustomPropertyUtil.substituteVars(v, variables, isDarkTheme)
            } else {
                v
            }
        }
        return out
    }

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
    ): ComputedStyle = resolveStyleInternal(
        classes = classes,
        tagName = tagName,
        id = id,
        ancestorChain = ancestorChain,
        viewportWidthPx = viewportWidthPx,
        subjectEnabled = true
    )

    private fun resolveStyleInternal(
        classes: Set<String>,
        tagName: String = "div",
        id: String? = null,
        ancestorChain: List<Set<String>> = emptyList(),
        viewportWidthPx: Int = 1280,
        subjectEnabled: Boolean = true
    ): ComputedStyle {
        val activeTarget = resolveActiveBackendTarget()
        val isDarkTheme = Application.current?.getResolvedTheme() == ThemeMode.DARK ||
            "bs-theme-dark" in classes ||
            ancestorChain.any { "bs-theme-dark" in it }
        val mediaBucket = resolveMediaQueryBucket(viewportWidthPx)
        val cacheKey = buildString {
            append(activeTarget.id).append('|')
            append(if (isDarkTheme) "dark|" else "light|")
            append(if (subjectEnabled) "en|" else "dis|")
            append(tagName).append('|').append(id ?: "").append('|').append(mediaBucket).append('|')
            append(classes.joinToString(",")).append('|')
            for (anc in ancestorChain) {
                append(anc.joinToString(",")).append('>')
            }
        }
        styleLookupCache[cacheKey]?.let { return it }

        val matchingRules = collectMatchingRules(
            classes = classes,
            tagName = tagName,
            id = id,
            ancestorChain = ancestorChain,
            viewportWidthPx = viewportWidthPx,
            subjectEnabled = subjectEnabled,
            activeTarget = activeTarget
        )

        val baseRules = ArrayList<CssRule>(matchingRules.size)
        var hoverRules: ArrayList<CssRule>? = null
        var focusRules: ArrayList<CssRule>? = null
        var activeRules: ArrayList<CssRule>? = null
        var disabledRules: ArrayList<CssRule>? = null
        var checkedRules: ArrayList<CssRule>? = null

        for (rule in matchingRules) {
            if (rule.pseudoElement != null) continue
            when (rule.pseudoClass) {
                null -> baseRules.add(rule)
                "hover" -> (hoverRules ?: ArrayList<CssRule>(4).also { hoverRules = it }).add(rule)
                "focus", "focus-visible", "focus-within" ->
                    (focusRules ?: ArrayList<CssRule>(4).also { focusRules = it }).add(rule)
                "active" -> (activeRules ?: ArrayList<CssRule>(4).also { activeRules = it }).add(rule)
                "disabled" -> (disabledRules ?: ArrayList<CssRule>(4).also { disabledRules = it }).add(rule)
                "checked" -> (checkedRules ?: ArrayList<CssRule>(4).also { checkedRules = it }).add(rule)
                else -> baseRules.add(rule)
            }
        }

        val rootDefaultVars = collectRootCustomProperties(viewportWidthPx, isDarkTheme)
        val rawBaseDecls = mergeRuleDeclarations(baseRules)
        val resolvedBaseVars = CssCustomPropertyUtil.resolveVariableMap(
            inherited = rootDefaultVars,
            localDeclarations = rawBaseDecls,
            isDarkTheme = isDarkTheme
        )
        val baseDecls = expandVarsInDeclarations(rawBaseDecls, resolvedBaseVars, isDarkTheme)
        val baseImportantProps = collectImportantProperties(baseRules)
        val hoverStyle = hoverRules?.let { rulesList ->
            val combined = baseRules + rulesList
            val rawCombined = mergeRuleDeclarations(combined)
            val stateVars = CssCustomPropertyUtil.resolveVariableMap(resolvedBaseVars, rawCombined, isDarkTheme)
            buildComputedStyleFromDeclarations(
                expandVarsInDeclarations(rawCombined, stateVars, isDarkTheme),
                importantCssProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes,
                customProperties = stateVars
            )
        }
        val focusStyle = focusRules?.let { rulesList ->
            val combined = baseRules + rulesList
            val rawCombined = mergeRuleDeclarations(combined)
            val stateVars = CssCustomPropertyUtil.resolveVariableMap(resolvedBaseVars, rawCombined, isDarkTheme)
            buildComputedStyleFromDeclarations(
                expandVarsInDeclarations(rawCombined, stateVars, isDarkTheme),
                importantCssProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes,
                customProperties = stateVars
            )
        }
        val activeStyle = activeRules?.let { rulesList ->
            val combined = baseRules + rulesList
            val rawCombined = mergeRuleDeclarations(combined)
            val stateVars = CssCustomPropertyUtil.resolveVariableMap(resolvedBaseVars, rawCombined, isDarkTheme)
            buildComputedStyleFromDeclarations(
                expandVarsInDeclarations(rawCombined, stateVars, isDarkTheme),
                importantCssProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes,
                customProperties = stateVars
            )
        }
        val disabledStyle = disabledRules?.let { rulesList ->
            val combined = baseRules + rulesList
            val rawCombined = mergeRuleDeclarations(combined)
            val stateVars = CssCustomPropertyUtil.resolveVariableMap(resolvedBaseVars, rawCombined, isDarkTheme)
            buildComputedStyleFromDeclarations(
                expandVarsInDeclarations(rawCombined, stateVars, isDarkTheme),
                importantCssProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes,
                customProperties = stateVars
            )
        }
        val checkedStyle = checkedRules?.let { rulesList ->
            val combined = baseRules + rulesList
            val rawCombined = mergeRuleDeclarations(combined)
            val stateVars = CssCustomPropertyUtil.resolveVariableMap(resolvedBaseVars, rawCombined, isDarkTheme)
            buildComputedStyleFromDeclarations(
                expandVarsInDeclarations(rawCombined, stateVars, isDarkTheme),
                importantCssProperties = collectImportantProperties(combined),
                isDarkTheme = isDarkTheme,
                classes = classes,
                customProperties = stateVars
            )
        }

        val computed = buildComputedStyleFromDeclarations(
            merged = baseDecls,
            hoverStyle = hoverStyle,
            focusStyle = focusStyle,
            activeStyle = activeStyle,
            disabledStyle = disabledStyle,
            checkedStyle = checkedStyle,
            importantCssProperties = baseImportantProps,
            isDarkTheme = isDarkTheme,
            classes = classes,
            customProperties = resolvedBaseVars
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
        viewportWidthPx: Int,
        subjectEnabled: Boolean = true,
        activeTarget: BackendTarget? = resolveActiveBackendTarget()
    ): List<CssRule> {
        if (sortedRules.isEmpty()) return emptyList()
        val normalizedTargetTag = normalizeTagName(tagName)
        val matchedIndices = ArrayList<Int>(16)

        fun checkBucket(bucket: IntArray?) {
            if (bucket == null) return
            for (ruleIdx in bucket) {
                val rule = sortedRules[ruleIdx]
                if (rule.pseudoElement == null &&
                    rule.matchesBackend(activeTarget) &&
                    rule.matchesMediaQuery(viewportWidthPx, 800, false) &&
                    matchesRule(rule, classes, normalizedTargetTag, id, ancestorChain, subjectEnabled)
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
            pTokens.add(if (p.isEnabled) "pseudo:enabled" else "pseudo:disabled")
            if (p.isHovered) pTokens.add("pseudo:hover")
            if (p.isFocused) pTokens.add("pseudo:focus")
            if (p.isActivePressed) pTokens.add("pseudo:active")
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
        return resolveStyleInternal(
            classes = classes,
            tagName = tagName,
            id = component.id,
            ancestorChain = orderedAncestors,
            viewportWidthPx = viewportWidthPx,
            subjectEnabled = component.isEnabled
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

    private fun collectImportantProperties(sortedRules: List<CssRule>): Set<CssProperty> {
        var important: LinkedHashSet<CssProperty>? = null
        for (rule in sortedRules) {
            if (rule.importantProps.isNotEmpty()) {
                val set = important ?: LinkedHashSet<CssProperty>().also { important = it }
                for (propName in rule.importantProps) {
                    CssProperty.fromCssName(propName)?.let { set.add(it) }
                }
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
        ancestorChain: List<Set<String>>,
        subjectEnabled: Boolean = true
    ): Boolean {
        if (rule.chainTokens.isEmpty() || rule.chainTokens.any { it.isEmpty() }) return false
        if (rule.isUniversalStarOnly) return true
        for (seg in rule.l4Segments) {
            if (seg.combinatorToLeft == CssCombinator.ADJACENT_SIBLING ||
                seg.combinatorToLeft == CssCombinator.GENERAL_SIBLING ||
                seg.isSelectors.isNotEmpty() ||
                seg.whereSelectors.isNotEmpty() ||
                seg.hasSelectors.isNotEmpty() ||
                seg.pseudoElement != null
            ) {
                return false
            }
            for (attr in seg.attributes) {
                val isSupportedDisabledAttr = attr.name == "disabled" ||
                    (attr.name == "aria-disabled" &&
                        attr.operator == CssAttributeOperator.EQUALS &&
                        attr.value?.equals("true", ignoreCase = true) == true)
                if (!isSupportedDisabledAttr) return false
            }
            for (pseudo in seg.positivePseudos) {
                if (pseudo == "root") {
                    if (ancestorChain.isNotEmpty()) return false
                } else if (pseudo !in LEGACY_SIMPLE_PSEUDOS) {
                    return false
                }
            }
        }

        if (!matchesSubjectToken(rule, classes, normalizedTargetTag, id, subjectEnabled)) {
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
        id: String?,
        subjectEnabled: Boolean
    ): Boolean {
        if (rule.subjectExcludedClasses.any { it in classes }) return false
        if (id != null && id in rule.subjectExcludedIds) return false
        if (rule.subjectExcludedTags.isNotEmpty() && normalizedTargetTag in rule.subjectExcludedTags) return false
        if ("enabled" in rule.subjectPositivePseudos && !subjectEnabled) return false
        if ("disabled" in rule.subjectExcludedPseudos && !subjectEnabled) return false
        if ("enabled" in rule.subjectExcludedPseudos && subjectEnabled) return false

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

        return reqIds.isNotEmpty() ||
            reqClasses.isNotEmpty() ||
            normalizedLeadingTag.isNotEmpty() ||
            rule.subjectExcludedClasses.isNotEmpty() ||
            rule.subjectExcludedIds.isNotEmpty() ||
            rule.subjectExcludedTags.isNotEmpty() ||
            rule.subjectPositivePseudos.isNotEmpty() ||
            rule.subjectExcludedPseudos.isNotEmpty()
    }

    private fun matchesPreparsedAncestor(
        rule: CssRule,
        ancestorStepIndex: Int,
        ancestorEntry: Set<String>
    ): Boolean {
        val exclClasses = rule.ancestorExcludedClasses.getOrNull(ancestorStepIndex).orEmpty()
        if (exclClasses.any { it in ancestorEntry }) return false
        val exclIds = rule.ancestorExcludedIds.getOrNull(ancestorStepIndex).orEmpty()
        if (exclIds.any { "#$it" in ancestorEntry || it in ancestorEntry }) return false
        val exclTags = rule.ancestorExcludedTags.getOrNull(ancestorStepIndex).orEmpty()
        if (exclTags.any { "tag:$it" in ancestorEntry || it in ancestorEntry }) return false
        val exclPseudos = rule.ancestorExcludedPseudos.getOrNull(ancestorStepIndex).orEmpty()
        for (ep in exclPseudos) {
            val mapped = if (ep == "focus-visible" || ep == "focus-within") "focus" else ep
            if ("pseudo:$mapped" in ancestorEntry) return false
        }
        val reqPseudos = rule.ancestorPositivePseudos.getOrNull(ancestorStepIndex).orEmpty()
        for (rp in reqPseudos) {
            val mapped = if (rp == "focus-visible" || rp == "focus-within") "focus" else rp
            if ("pseudo:$mapped" !in ancestorEntry) return false
        }

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

        return idMatches.isNotEmpty() ||
            reqClasses.isNotEmpty() ||
            normalizedReqTag.isNotEmpty() ||
            exclClasses.isNotEmpty() ||
            exclIds.isNotEmpty() ||
            exclTags.isNotEmpty() ||
            reqPseudos.isNotEmpty() ||
            exclPseudos.isNotEmpty()
    }

    private fun collectRootCustomProperties(viewportWidthPx: Int, isDarkTheme: Boolean): Map<String, String> {
        if (universalBucket.isEmpty() && tagBuckets["html"] == null && tagBuckets["body"] == null) {
            return emptyMap()
        }
        val out = linkedMapOf<String, String>()
        val activeTarget = resolveActiveBackendTarget()
        fun scanBucket(bucket: IntArray?) {
            if (bucket == null) return
            for (idx in bucket) {
                val rule = sortedRules[idx]
                if (!rule.matchesBackend(activeTarget)) continue
                if (!rule.matchesMediaQuery(viewportWidthPx, 800, isDarkTheme)) continue
                val sel = rule.selector.trim().lowercase()
                if (sel == ":root" || sel == "html" || sel == "body") {
                    for ((k, v) in rule.cleanedDeclarations) {
                        if (k.startsWith("--")) out[k] = v
                    }
                }
            }
        }
        scanBucket(universalBucket)
        scanBucket(tagBuckets["html"])
        scanBucket(tagBuckets["body"])
        return out
    }

    private fun normalizeTagName(tag: String): String =
        normalizeCustomGenericUiTagSelector(tag)

    internal fun normalizeCustomGenericUiTagSelector(rawTag: String): String =
        CssRule.normalizeSelectorTag(rawTag)

    companion object {
        internal const val DESCENDANT_INVALIDATION_NONE: Int = 0
        internal const val DESCENDANT_INVALIDATION_TARGETED: Int = 1
        internal const val DESCENDANT_INVALIDATION_WHOLE_SUBTREE: Int = 2

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

        internal var systemIsClassReferencedHook: ((
            sheet: CssStyleSheet,
            cls: String,
            checkAncestor: Boolean
        ) -> Boolean?)? = null

        internal var systemCollectDescendantInvalidationHook: ((
            sheet: CssStyleSheet,
            component: AbstractComponent,
            changedClasses: List<String>,
            outSubjectClasses: MutableSet<String>,
            outSubjectIds: MutableSet<String>,
            outSubjectTags: MutableSet<String>
        ) -> Int?)? = null

        internal var systemCollectMediaInvalidationHook: ((
            sheet: CssStyleSheet,
            oldViewportWidthPx: Int,
            newViewportWidthPx: Int,
            outSubjectClasses: MutableSet<String>,
            outSubjectIds: MutableSet<String>,
            outSubjectTags: MutableSet<String>,
            outAncestorClasses: MutableSet<String>,
            outAncestorIds: MutableSet<String>,
            outAncestorTags: MutableSet<String>
        ) -> Int?)? = null

        private val WHITESPACE_REGEX = Regex("\\s+")

        /**
         * Strips CSS block comments iteratively instead of using a backtracking regex so large
         * native stylesheets never overflow the Kotlin/Native regex matcher stack.
         */
        private fun stripCssBlockComments(css: String): String {
            val firstStart = css.indexOf("/*")
            if (firstStart < 0) return css
            val sb = StringBuilder(css.length)
            var cursor = 0
            var commentStart = firstStart
            while (commentStart >= 0) {
                if (commentStart > cursor) {
                    sb.append(css, cursor, commentStart)
                }
                val commentEnd = css.indexOf("*/", commentStart + 2)
                if (commentEnd < 0) {
                    return sb.toString()
                }
                cursor = commentEnd + 2
                commentStart = css.indexOf("/*", cursor)
            }
            if (cursor < css.length) {
                sb.append(css, cursor, css.length)
            }
            return sb.toString()
        }
        private val KEYFRAMES_PREFIX_REGEX = Regex("^@keyframes\\s+", RegexOption.IGNORE_CASE)
        private val WEBKIT_KEYFRAMES_PREFIX_REGEX = Regex("^@-webkit-keyframes\\s+", RegexOption.IGNORE_CASE)
        private val MIN_WIDTH_REGEX = Regex("min-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
        private val MAX_WIDTH_REGEX = Regex("max-width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
        private val URL_REGEX = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE)
        private val LEGACY_SIMPLE_PSEUDOS = setOf(
            "hover", "focus", "focus-visible", "focus-within", "active", "disabled", "enabled", "checked"
        )

        /**
         * Constructs a precompiled [CssStyleSheet] from build-time pre-parsed and specificity-sorted
         * [CssRule]s and `@keyframes` maps so zero SASS/CSS parsing occurs at runtime.
         */
        fun combineCascade(sheets: List<CssStyleSheet>): CssStyleSheet {
            if (sheets.isEmpty()) return empty()
            val flattened = ArrayList<CssStyleSheet>(sheets.size)
            fun collect(sheet: CssStyleSheet) {
                if (sheet.composedSheets.isNotEmpty()) {
                    for (child in sheet.composedSheets) collect(child)
                } else {
                    flattened.add(sheet)
                }
            }
            for (s in sheets) collect(s)
            if (flattened.isEmpty()) return empty()
            if (flattened.size == 1) return flattened[0]

            val mergedRules = ArrayList<CssRule>()
            val mergedKeyframes = linkedMapOf<String, Map<String, Map<String, String>>>()
            val mergedKfVariants = linkedMapOf<String, MutableList<Pair<Int, Map<String, Map<String, String>>>>>()
            for (s in flattened) {
                if (s.rules.isNotEmpty()) {
                    mergedRules.addAll(s.rules)
                }
                for ((animName, stops) in s.keyframes) {
                    mergedKeyframes[animName] = stops
                    val variants = s.keyframeVariants[animName]
                    if (variants != null) {
                        mergedKfVariants.getOrPut(animName) { mutableListOf() }.addAll(variants)
                    } else {
                        mergedKfVariants.getOrPut(animName) { mutableListOf() }.add(BackendStyleMask.ALL to stops)
                    }
                }
            }
            val combined = CssStyleSheet(
                rules = mergedRules,
                keyframes = mergedKeyframes,
                isPrecompiled = false,
                sourceCssText = "",
                composedSheets = flattened
            )
            combined.keyframeVariants = mergedKfVariants
            return combined
        }

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
            importantCssProperties: Set<CssProperty> = emptySet(),
            isDarkTheme: Boolean = Application.current?.getResolvedTheme() == ThemeMode.DARK,
            classes: Set<String> = emptySet(),
            customProperties: Map<String, String> = emptyMap(),
            beforeStyle: ComputedStyle? = null,
            afterStyle: ComputedStyle? = null
        ): ComputedStyle {
            val typedStyle = CssDeclarationApplyUtil.fromDeclarations(merged, isDarkTheme)
            for (impProp in importantCssProperties) {
                typedStyle.markImportant(impProp, true)
            }
            val baseComputed = typedStyle.toComputedStyle()

            val accentColor = merged["accent-color"]?.let { resolveCssColorValue(it, isDarkTheme) } ?: baseComputed.accentColor
            val bgProp = merged["background-color"] ?: merged["background"]
            val bgColor = bgProp?.let { extractColorFromBackgroundShorthand(it, isDarkTheme) } ?: baseComputed.backgroundColor
            val bgImageRaw = merged["background-image"] ?: merged["background"]
            val bgGradient = bgImageRaw?.let { Gradient.fromCss(it) } ?: baseComputed.backgroundGradient
            val bgImage = bgImageRaw?.let { extractUrlFromCssValue(it) } ?: baseComputed.backgroundImageUrl
            var fgColor = merged["color"]?.let { resolveCssColorValue(it, isDarkTheme) } ?: baseComputed.textColor
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

            var borderColor = merged["border-color"]?.let { resolveCssColorValue(it, isDarkTheme, fgColor) } ?: baseComputed.borderColor
            var borderWidthPx = merged["border-width"]?.let { CssLength.parse(it)?.resolvePx() } ?: baseComputed.borderWidthPx
            var borderStyle = merged["border-style"] ?: baseComputed.borderStyle
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
                            lower.endsWith("px") || lower.endsWith("rem") || lower.endsWith("em") || lower.endsWith("pt") ->
                                CssLength.parse(lower)?.resolvePx()?.let { borderWidthPx = it }
                            else -> resolveCssColorValue(tok, isDarkTheme, fgColor)?.let { borderColor = it }
                        }
                    }
                }
            }

            val radiusRaw = merged["border-radius"]?.trim()
            val parsedCornerRadius = radiusRaw?.let { CssCornerRadii.parse(it)?.topLeft }
            val borderRadiusPx = baseComputed.borderRadiusPx
                ?: parsedCornerRadius
                    ?.takeIf { it.unit != com.nexustools.genericui.enums.css.CssUnit.PERCENT }
                    ?.resolvePx()
                ?: radiusRaw?.takeIf { it.endsWith("px") }?.removeSuffix("px")?.toFloatOrNull()
                ?: radiusRaw?.toFloatOrNull()
            val borderRadiusPercent = baseComputed.borderRadiusPercent
                ?: parsedCornerRadius
                    ?.takeIf { it.unit == com.nexustools.genericui.enums.css.CssUnit.PERCENT }
                    ?.value
                ?: radiusRaw?.takeIf { it.endsWith("%") }?.removeSuffix("%")?.toFloatOrNull()

            val fontSizeRaw = merged["font-size"]?.trim()
            val parsedFontLen = fontSizeRaw?.let { CssLength.parse(it) }
            val fontSizePx = when {
                parsedFontLen != null && parsedFontLen.unit != com.nexustools.genericui.enums.css.CssUnit.PERCENT ->
                    parsedFontLen.resolvePx() ?: baseComputed.fontSizePx
                else -> baseComputed.fontSizePx
            }
            val fontSizePercent = parsedFontLen
                ?.takeIf { it.unit == com.nexustools.genericui.enums.css.CssUnit.PERCENT }
                ?.value
                ?: baseComputed.fontSizePercent

            val fontFamily = merged["font-family"]?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
                ?: baseComputed.fontFamily
            val fontWeightRaw = merged["font-weight"]?.trim()?.lowercase()
            val fontWeightNumeric = when (fontWeightRaw) {
                "normal" -> 400
                "bold" -> 700
                "lighter" -> 300
                "bolder" -> 800
                else -> fontWeightRaw?.toIntOrNull() ?: baseComputed.fontWeightNumeric
            }

            val opacity = merged["opacity"]?.let { rawOp ->
                rawOp.toFloatOrNull() ?: if (CssMathEvaluator.isMathFunction(rawOp)) CssMathEvaluator.evaluateScalar(rawOp) else null
            } ?: baseComputed.opacity
            val boxShadow = merged["box-shadow"] ?: merged["-webkit-box-shadow"] ?: baseComputed.boxShadow
            val textShadow = merged["text-shadow"] ?: baseComputed.textShadow
            val transform = merged["transform"] ?: baseComputed.transform
            val transition = merged["transition"] ?: baseComputed.transition
            val zIndex = merged["z-index"]?.toIntOrNull() ?: baseComputed.zIndex
            val aspectRatio = merged["aspect-ratio"]?.let { parseAspectRatio(it) } ?: baseComputed.aspectRatio
            val pointerEvents = merged["pointer-events"]?.let { !it.equals("none", ignoreCase = true) } ?: baseComputed.pointerEventsEnabled
            val filter = merged["filter"]?.let { CssFilter.parse(it) } ?: baseComputed.filter

            return baseComputed.copy(
                backgroundColor = bgColor,
                backgroundGradient = bgGradient,
                textColor = fgColor,
                accentColor = accentColor,
                borderColor = borderColor,
                borderTopColor = baseComputed.borderTopColor ?: borderColor,
                borderRightColor = baseComputed.borderRightColor ?: borderColor,
                borderBottomColor = baseComputed.borderBottomColor ?: borderColor,
                borderLeftColor = baseComputed.borderLeftColor ?: borderColor,
                borderWidthPx = borderWidthPx,
                borderTopWidthPx = baseComputed.borderTopWidthPx ?: borderWidthPx,
                borderRightWidthPx = baseComputed.borderRightWidthPx ?: borderWidthPx,
                borderBottomWidthPx = baseComputed.borderBottomWidthPx ?: borderWidthPx,
                borderLeftWidthPx = baseComputed.borderLeftWidthPx ?: borderWidthPx,
                borderStyle = borderStyle,
                borderRadiusPx = borderRadiusPx,
                borderRadiusPercent = borderRadiusPercent,
                fontSizePx = fontSizePx,
                fontSizePercent = fontSizePercent,
                fontFamily = fontFamily,
                fontWeightNumeric = fontWeightNumeric,
                opacity = opacity,
                backgroundImageUrl = bgImage,
                backgroundRepeat = merged["background-repeat"] ?: baseComputed.backgroundRepeat,
                backgroundAttachment = merged["background-attachment"] ?: baseComputed.backgroundAttachment,
                backgroundPosition = merged["background-position"] ?: baseComputed.backgroundPosition,
                backgroundSize = merged["background-size"] ?: baseComputed.backgroundSize,
                textShadow = textShadow,
                boxShadow = boxShadow,
                transform = transform,
                transition = transition,
                zIndex = zIndex,
                aspectRatio = aspectRatio,
                pointerEventsEnabled = pointerEvents,
                cursor = merged["cursor"] ?: baseComputed.cursor,
                customCursor = merged["cursor"]?.let { CssCustomCursor.parse(it) } ?: baseComputed.customCursor,
                position = merged["position"] ?: baseComputed.position,
                display = merged["display"] ?: baseComputed.display,
                overflow = merged["overflow"] ?: baseComputed.overflow,
                textAlign = merged["text-align"] ?: baseComputed.textAlign,
                verticalAlign = merged["vertical-align"] ?: baseComputed.verticalAlign,
                whiteSpace = merged["white-space"] ?: baseComputed.whiteSpace,
                floatSide = merged["float"] ?: baseComputed.floatSide,
                clearSide = merged["clear"] ?: baseComputed.clearSide,
                left = merged["left"] ?: baseComputed.left,
                top = merged["top"] ?: baseComputed.top,
                right = merged["right"] ?: baseComputed.right,
                bottom = merged["bottom"] ?: baseComputed.bottom,
                width = merged["width"] ?: baseComputed.width,
                height = merged["height"] ?: baseComputed.height,
                minWidth = merged["min-width"] ?: baseComputed.minWidth,
                minHeight = merged["min-height"] ?: baseComputed.minHeight,
                maxWidth = merged["max-width"] ?: baseComputed.maxWidth,
                maxHeight = merged["max-height"] ?: baseComputed.maxHeight,
                filter = filter,
                hoverStyle = hoverStyle,
                focusStyle = focusStyle,
                activeStyle = activeStyle,
                disabledStyle = disabledStyle,
                checkedStyle = checkedStyle,
                customProperties = if (customProperties.isEmpty()) baseComputed.customProperties else customProperties,
                beforeStyle = beforeStyle ?: baseComputed.beforeStyle,
                afterStyle = afterStyle ?: baseComputed.afterStyle
            )
        }

        internal fun resolveCssColorValue(
            rawValue: String,
            isDarkTheme: Boolean,
            currentColor: RgbaColor? = null
        ): RgbaColor? {
            val trimmed = rawValue.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.startsWith("@color/") || trimmed.startsWith("res://color/")) {
                val name = trimmed.removePrefix("@color/").removePrefix("res://color/")
                val resId = com.nexustools.genericui.res.Resources.activeTable.findResourceId(
                    com.nexustools.genericui.resources.ResourceType.COLOR,
                    name
                )
                if (resId != null) {
                    return com.nexustools.genericui.resources.ResourceResolver.getColor(resId)
                }
            }
            if (trimmed.startsWith("var(", ignoreCase = true) && trimmed.endsWith(")")) {
                val resolved = CssCustomPropertyUtil.substituteVars(trimmed, emptyMap(), isDarkTheme)
                if (resolved != trimmed) {
                    return resolveCssColorValue(resolved, isDarkTheme, currentColor)
                }
                return null
            }
            return RgbaColor.fromCss(trimmed, currentColor)
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
            val cur = StringBuilder()
            var pDepth = 0
            var bDepth = 0
            var inQuote: Char? = null

            fun commitStatement() {
                val stmt = cur.toString().trim()
                cur.clear()
                if (stmt.isEmpty()) return
                val idx = stmt.indexOf(':')
                if (idx > 0) {
                    val prop = stmt.substring(0, idx).trim().lowercase()
                    val value = stmt.substring(idx + 1).trim()
                    if (prop.isNotEmpty() && value.isNotEmpty()) {
                        decls[prop] = value
                    }
                }
            }

            var i = 0
            while (i < body.length) {
                val ch = body[i]
                if (inQuote != null) {
                    cur.append(ch)
                    if (ch == '\\' && i + 1 < body.length) {
                        i++
                        cur.append(body[i])
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
                        pDepth++
                        cur.append(ch)
                    }
                    ch == ')' -> {
                        if (pDepth > 0) pDepth--
                        cur.append(ch)
                    }
                    ch == '[' -> {
                        bDepth++
                        cur.append(ch)
                    }
                    ch == ']' -> {
                        if (bDepth > 0) bDepth--
                        cur.append(ch)
                    }
                    ch == ';' && pDepth == 0 && bDepth == 0 -> commitStatement()
                    else -> cur.append(ch)
                }
                i++
            }
            commitStatement()
            return decls
        }

        fun parse(cssText: String, targetBackend: BackendTarget? = null): CssStyleSheet {
            val rules = mutableListOf<CssRule>()
            val keyframes = linkedMapOf<String, Map<String, Map<String, String>>>()
            val keyframeVariants = linkedMapOf<String, MutableList<Pair<Int, Map<String, Map<String, String>>>>>()
            val cleaned = stripCssBlockComments(cssText.removePrefix("\uFEFF"))
            parseInto(
                cleanedCss = cleaned,
                activeMinWidthPx = null,
                activeMaxWidthPx = null,
                activeMediaQuery = null,
                activeBackendMask = BackendStyleMask.ALL,
                targetMask = targetBackend?.let { BackendStyleMask.bitFor(it) },
                outRules = rules,
                outKeyframes = keyframes,
                outKeyframeVariants = keyframeVariants
            )
            val sheet = CssStyleSheet(rules, keyframes, sourceCssText = cssText)
            sheet.keyframeVariants = keyframeVariants
            return sheet
        }

        private fun parseInto(
            cleanedCss: String,
            activeMinWidthPx: Int?,
            activeMaxWidthPx: Int?,
            activeMediaQuery: CssMediaQuery?,
            activeBackendMask: Int,
            targetMask: Int?,
            outRules: MutableList<CssRule>,
            outKeyframes: MutableMap<String, Map<String, Map<String, String>>>,
            outKeyframeVariants: MutableMap<String, MutableList<Pair<Int, Map<String, Map<String, String>>>>>
        ) {
            if (activeBackendMask == BackendStyleMask.NONE) return
            if (targetMask != null && (activeBackendMask and targetMask) == 0) return

            var i = 0
            val n = cleanedCss.length
            while (i < n) {
                while (i < n && cleanedCss[i].isWhitespace()) i++
                if (i >= n) break

                val headerStart = i
                var hQuote: Char? = null
                while (i < n) {
                    val c = cleanedCss[i]
                    if (hQuote != null) {
                        if (c == '\\' && i + 1 < n) {
                            i += 2
                            continue
                        }
                        if (c == hQuote) hQuote = null
                    } else {
                        if (c == '"' || c == '\'') hQuote = c
                        else if (c == '{') break
                    }
                    i++
                }
                if (i >= n) break
                val header = cleanedCss.substring(headerStart, i).trim()
                val bodyStart = i + 1
                var depth = 1
                var bQuote: Char? = null
                i++
                while (i < n && depth > 0) {
                    val c = cleanedCss[i]
                    if (bQuote != null) {
                        if (c == '\\' && i + 1 < n) {
                            i += 2
                            continue
                        }
                        if (c == bQuote) bQuote = null
                    } else {
                        when (c) {
                            '"', '\'' -> bQuote = c
                            '{' -> depth++
                            '}' -> depth--
                        }
                    }
                    i++
                }
                val body = cleanedCss.substring(bodyStart, (i - 1).coerceAtLeast(bodyStart)).trim()

                when {
                    header.startsWith("@keyframes", ignoreCase = true) ||
                        header.startsWith("@-webkit-keyframes", ignoreCase = true) -> {
                        val animName = header
                            .replace(KEYFRAMES_PREFIX_REGEX, "")
                            .replace(WEBKIT_KEYFRAMES_PREFIX_REGEX, "")
                            .trim()
                        val frameRules = mutableListOf<CssRule>()
                        parseInto(
                            cleanedCss = body,
                            activeMinWidthPx = null,
                            activeMaxWidthPx = null,
                            activeMediaQuery = null,
                            activeBackendMask = BackendStyleMask.ALL,
                            targetMask = null,
                            outRules = frameRules,
                            outKeyframes = linkedMapOf(),
                            outKeyframeVariants = linkedMapOf()
                        )
                        val frames = linkedMapOf<String, Map<String, String>>()
                        for (r in frameRules) {
                            val existing = frames[r.selector]
                            frames[r.selector] = if (existing != null) existing + r.declarations else r.declarations
                        }
                        if (animName.isNotEmpty()) {
                            outKeyframes[animName] = frames
                            outKeyframeVariants.getOrPut(animName) { mutableListOf() }
                                .add(activeBackendMask to frames)
                        }
                    }
                    BackendStyleMask.parseDirectiveHeader(header) != null -> {
                        val dirMask = BackendStyleMask.parseDirectiveHeader(header)!!
                        val nextMask = activeBackendMask and dirMask
                        parseInto(
                            cleanedCss = body,
                            activeMinWidthPx = activeMinWidthPx,
                            activeMaxWidthPx = activeMaxWidthPx,
                            activeMediaQuery = activeMediaQuery,
                            activeBackendMask = nextMask,
                            targetMask = targetMask,
                            outRules = outRules,
                            outKeyframes = outKeyframes,
                            outKeyframeVariants = outKeyframeVariants
                        )
                    }
                    BackendStyleMask.isBackendDirectivePrefix(header) -> {
                        // Skip malformed backend directives instead of turning them into selectors
                    }
                    header.startsWith("@media", ignoreCase = true) -> {
                        val parsedMq = CssMediaQuery.parse(header)
                        val minW = parsedMq.minWidthBoundsPx() ?: MIN_WIDTH_REGEX.find(header)?.groupValues?.get(1)?.toIntOrNull()
                        val maxW = parsedMq.maxWidthBoundsPx() ?: MAX_WIDTH_REGEX.find(header)?.groupValues?.get(1)?.toIntOrNull()
                        val nextMin = when {
                            activeMinWidthPx != null && minW != null -> maxOf(activeMinWidthPx, minW)
                            else -> minW ?: activeMinWidthPx
                        }
                        val nextMax = when {
                            activeMaxWidthPx != null && maxW != null -> minOf(activeMaxWidthPx, maxW)
                            else -> maxW ?: activeMaxWidthPx
                        }
                        val nextMediaQuery = combineMediaQueries(activeMediaQuery, parsedMq)
                        parseInto(
                            cleanedCss = body,
                            activeMinWidthPx = nextMin,
                            activeMaxWidthPx = nextMax,
                            activeMediaQuery = nextMediaQuery,
                            activeBackendMask = activeBackendMask,
                            targetMask = targetMask,
                            outRules = outRules,
                            outKeyframes = outKeyframes,
                            outKeyframeVariants = outKeyframeVariants
                        )
                    }
                    header.isNotEmpty() -> {
                        val decls = parseDeclarations(body)
                        val selectors = CssSelectorCompound.splitTopLevelSelectorList(header)
                        for (rawSel in selectors) {
                            val sel = CssRule.sanitizeSelectorOrNull(rawSel) ?: continue
                            outRules.add(
                                CssRule(
                                    selector = sel,
                                    declarations = decls,
                                    mediaMinWidthPx = activeMinWidthPx,
                                    mediaMaxWidthPx = activeMaxWidthPx,
                                    backendMask = activeBackendMask,
                                    mediaQuery = activeMediaQuery
                                )
                            )
                        }
                    }
                }
            }
        }

        private fun combineMediaQueries(parent: CssMediaQuery?, child: CssMediaQuery): CssMediaQuery {
            if (parent == null || parent.clauses.isEmpty()) return child
            if (child.clauses.isEmpty()) return parent
            val combinedClauses = ArrayList<CssMediaQuery.MediaClause>(parent.clauses.size * child.clauses.size)
            for (pc in parent.clauses) {
                for (cc in child.clauses) {
                    if (!pc.negated && !cc.negated) {
                        val mediaType = when {
                            pc.mediaType != null && cc.mediaType != null && pc.mediaType != cc.mediaType -> "print"
                            else -> pc.mediaType ?: cc.mediaType
                        }
                        combinedClauses.add(
                            CssMediaQuery.MediaClause(
                                negated = false,
                                mediaType = mediaType,
                                features = pc.features + cc.features
                            )
                        )
                    } else {
                        combinedClauses.add(cc)
                    }
                }
            }
            return CssMediaQuery(
                rawHeader = "${parent.rawHeader} and ${child.rawHeader}",
                clauses = combinedClauses
            )
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
