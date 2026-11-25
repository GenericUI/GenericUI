package com.nexustools.genericui.system.style

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.css.BackendStyleMask
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry
import com.nexustools.genericui.style.sass.CssDeclarationApplyUtil

/**
 * System-only Chromium Blink-modeled CSS selector matching and style resolution engine.
 *
 * Evaluates precompiled [BlinkRuleBucketMap]s right-to-left using [BlinkSelectorAtom]s and
 * [BlinkMatchedPropertiesCache] with zero runtime CSS string parsing.
 */
object BlinkStyleEngine {
    private data class BlinkAncestorSnapshot(
        val tag: String,
        val id: String?,
        val classes: Set<String>,
        val pseudoMask: Int
    )

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val activeSheets = mutableListOf<BlinkStyleSheet>()
    private val ruleSetsBySheet = linkedMapOf<CssStyleSheet, BlinkRuleBucketMap>()
    private val composedRuleSetCache = linkedMapOf<Triple<CssStyleSheet, Boolean, BackendTarget?>, BlinkRuleBucketMap>()
    private var cachedCombinedActiveCssSheet: CssStyleSheet? = null
    val matchedPropertiesCache: BlinkMatchedPropertiesCache = BlinkMatchedPropertiesCache

    init {
        installHooks()
    }

    fun ensureInstalled() {
        installHooks()
    }

    private fun installHooks() {
        SystemCssAnimationController.ensureInstalled()
        if (CssStyleSheet.systemStyleResolverHook == null) {
            CssStyleSheet.systemStyleResolverHook = { sheet, component, tagName, viewportWidthPx ->
                val isDark = Application.current?.getResolvedTheme() == ThemeMode.DARK
                val ruleSet = resolveRuleSetForSheet(sheet, isDark)
                if (ruleSet != null) {
                    resolveStyleForComponent(ruleSet, component, tagName, viewportWidthPx)
                } else {
                    null
                }
            }
        }
        if (CssStyleSheet.systemMediaQueryBucketHook == null) {
            CssStyleSheet.systemMediaQueryBucketHook = { sheet, viewportWidthPx ->
                val isDark = Application.current?.getResolvedTheme() == ThemeMode.DARK
                resolveRuleSetForSheet(sheet, isDark)?.resolveMediaQueryBucket(viewportWidthPx)
            }
        }
        if (CssStyleSheet.systemIsClassReferencedHook == null) {
            CssStyleSheet.systemIsClassReferencedHook = { sheet, cls, checkAncestor ->
                val isDark = Application.current?.getResolvedTheme() == ThemeMode.DARK
                resolveRuleSetForSheet(sheet, isDark)?.let { ruleSet ->
                    if (checkAncestor) cls in ruleSet.referencedAncestorClasses else cls in ruleSet.referencedSubjectClasses
                }
            }
        }
        if (CssStyleSheet.systemCollectDescendantInvalidationHook == null) {
            CssStyleSheet.systemCollectDescendantInvalidationHook = { sheet, component, changedClasses, outSubjectClasses, outSubjectIds, outSubjectTags ->
                val isDark = Application.current?.getResolvedTheme() == ThemeMode.DARK
                resolveRuleSetForSheet(sheet, isDark)?.let { ruleSet ->
                    val normalizedTag = CssRule.normalizeSelectorTag(component.htmlTag.ifEmpty { "genericui:component" })
                    val baseClasses = collectElementClasses(component)
                    val needDark = normalizedTag == "genericui:app" && isDark && "bs-theme-dark" !in baseClasses
                    val needHover = component.isHovered && "pseudo:hover" !in baseClasses
                    val needFocus = component.isFocused && "pseudo:focus" !in baseClasses
                    val classes = if (needDark || needHover || needFocus) {
                        LinkedHashSet(baseClasses).apply {
                            if (needDark) add("bs-theme-dark")
                            if (needHover) add("pseudo:hover")
                            if (needFocus) add("pseudo:focus")
                        }
                    } else {
                        baseClasses
                    }
                    ruleSet.collectDescendantInvalidation(
                        changedClasses = changedClasses,
                        ancestorNormalizedTag = normalizedTag,
                        ancestorId = component.id,
                        ancestorClasses = classes,
                        outSubjectClasses = outSubjectClasses,
                        outSubjectIds = outSubjectIds,
                        outSubjectTags = outSubjectTags
                    )
                }
            }
        }
        if (CssStyleSheet.systemCollectMediaInvalidationHook == null) {
            CssStyleSheet.systemCollectMediaInvalidationHook = { sheet, oldVw, newVw, outClasses, outIds, outTags, outAncClasses, outAncIds, outAncTags ->
                val isDark = Application.current?.getResolvedTheme() == ThemeMode.DARK
                resolveRuleSetForSheet(sheet, isDark)?.collectMediaInvalidation(
                    oldViewportWidthPx = oldVw,
                    newViewportWidthPx = newVw,
                    outSubjectClasses = outClasses,
                    outSubjectIds = outIds,
                    outSubjectTags = outTags,
                    outAncestorClasses = outAncClasses,
                    outAncestorIds = outAncIds,
                    outAncestorTags = outAncTags
                )
            }
        }
        if (StyleSheetRegistry.onStyleSheetRegistered == null) {
            StyleSheetRegistry.onStyleSheetRegistered = { precompiled ->
                onPrecompiledStyleSheetRegistered(precompiled)
            }
            for (existing in StyleSheetRegistry.all()) {
                onPrecompiledStyleSheetRegistered(existing)
            }
        }
    }

    private fun resolveActiveBackendTarget(): BackendTarget =
        BackendStyleMask.resolveRuntimeBackendTarget(
            Application.current?.backendTarget,
            GenericUI.activeFactory?.backendTarget
        )

    private fun resolveRuleSetForSheet(sheet: CssStyleSheet, isDarkTheme: Boolean): BlinkRuleBucketMap? {
        val activeBackend = resolveActiveBackendTarget()
        ruleSetsBySheet[sheet]?.let { return it.forBackend(activeBackend) }
        val registryMatch = StyleSheetRegistry.all().firstOrNull { it.styleSheet === sheet }
        if (registryMatch != null) {
            onPrecompiledStyleSheetRegistered(registryMatch)
            ruleSetsBySheet[sheet]?.let { return it.forBackend(activeBackend) }
        }
        if (sheet.composedSheets.isNotEmpty()) {
            val cacheKey = Triple(sheet, isDarkTheme, activeBackend)
            composedRuleSetCache[cacheKey]?.let { return it }
            val mergedRules = ArrayList<BlinkCompiledRule>()
            var nextSourceOrder = 0
            for (child in sheet.composedSheets) {
                val childSet = resolveRuleSetForSheet(child, isDarkTheme)
                if (childSet != null) {
                    for (r in childSet.allRules) {
                        mergedRules.add(
                            BlinkCompiledRule(
                                selector = r.selector,
                                selectorAtoms = r.selectorAtoms,
                                properties = r.properties,
                                style = r.style,
                                specificity = r.specificity,
                                sourceOrder = nextSourceOrder++,
                                mediaMinWidthPx = r.mediaMinWidthPx,
                                mediaMaxWidthPx = r.mediaMaxWidthPx,
                                themeFilter = r.themeFilter,
                                backendMask = r.backendMask
                            )
                        )
                    }
                } else if (child.rules.isNotEmpty()) {
                    for (cssRule in child.rules) {
                        if (!cssRule.matchesBackend(activeBackend)) continue
                        mergedRules.add(compileRawCssRule(cssRule, nextSourceOrder++, isDarkTheme))
                    }
                }
            }
            val combined = if (mergedRules.isEmpty()) {
                BlinkRuleBucketMap.EMPTY
            } else {
                BlinkRuleBucketMap.fromRules(mergedRules).forBackend(activeBackend)
            }
            if (composedRuleSetCache.size < 64) {
                composedRuleSetCache[cacheKey] = combined
            }
            return combined
        }
        return null
    }

    private fun compileRawCssRule(
        rule: CssRule,
        sourceOrder: Int,
        isDarkTheme: Boolean
    ): BlinkCompiledRule {
        val style = CssDeclarationApplyUtil.fromDeclarations(rule.cleanedDeclarations, isDarkTheme)
        for (impName in rule.importantProps) {
            CssProperty.fromCssName(impName)?.let { style.markImportant(it, true) }
        }
        val compiled = CompiledStyleRule(
            selector = rule.selector,
            style = style,
            specificity = rule.specificity,
            sourceOrder = sourceOrder,
            mediaMinWidthPx = rule.mediaMinWidthPx,
            mediaMaxWidthPx = rule.mediaMaxWidthPx,
            backendMask = rule.backendMask
        )
        return BlinkCompiledRule.fromCompiledStyleRule(compiled, sourceOrder)
    }

    private fun onPrecompiledStyleSheetRegistered(precompiled: PrecompiledStyleSheet) {
        if (precompiled is BlinkStyleSheet) {
            registerStyleSheet(precompiled)
        } else if (precompiled.compiledRules.isNotEmpty()) {
            val ruleSet = BlinkRuleBucketMap.fromCompiledStyleRules(precompiled.compiledRules)
            registerRuleSet(precompiled.styleSheet, ruleSet)
        }
    }

    fun registerStyleSheet(sheet: BlinkStyleSheet) {
        ensureInstalled()
        if (!activeSheets.contains(sheet)) {
            activeSheets.add(sheet)
        }
        registerRuleSet(sheet.styleSheet, sheet.ruleSet)
        composedRuleSetCache.clear()
        cachedCombinedActiveCssSheet = null
        BlinkMatchedPropertiesCache.invalidateAll()
    }

    fun registerRuleSet(cssStyleSheet: CssStyleSheet, ruleSet: BlinkRuleBucketMap) {
        ensureInstalled()
        ruleSetsBySheet[cssStyleSheet] = ruleSet
        composedRuleSetCache.clear()
        cachedCombinedActiveCssSheet = null
        BlinkMatchedPropertiesCache.invalidateAll()
    }

    fun unregisterStyleSheet(sheet: BlinkStyleSheet) {
        activeSheets.remove(sheet)
        ruleSetsBySheet.remove(sheet.styleSheet)
        composedRuleSetCache.clear()
        cachedCombinedActiveCssSheet = null
        BlinkMatchedPropertiesCache.invalidateAll()
    }

    fun getActiveStyleSheets(): List<BlinkStyleSheet> = activeSheets.toList()

    fun clear() {
        activeSheets.clear()
        ruleSetsBySheet.clear()
        composedRuleSetCache.clear()
        cachedCombinedActiveCssSheet = null
        BlinkMatchedPropertiesCache.invalidateAll()
    }

    fun collectElementClasses(component: AbstractComponent): LinkedHashSet<String> {
        component.cachedSelectorClasses?.let { return it }
        val classes = LinkedHashSet<String>()
        classes.addAll(component.getClasses())
        classes.addAll(component.getBootstrap().toEnumClassSet(component.componentClassPrefix))
        if (component.componentClassPrefix.isNotBlank()) {
            for (prefixCls in component.componentClassPrefix.split(WHITESPACE_REGEX)) {
                if (prefixCls.isNotEmpty()) classes.add(prefixCls)
            }
        }
        component.cachedSelectorClasses = classes
        return classes
    }

    private fun componentPseudoMask(component: AbstractComponent): Int {
        var mask = if (component.isEnabled) BlinkSelectorAtom.PSEUDO_ENABLED else BlinkSelectorAtom.PSEUDO_DISABLED
        if (component.isHovered) mask = mask or BlinkSelectorAtom.PSEUDO_HOVER
        if (component.isFocused) mask = mask or BlinkSelectorAtom.PSEUDO_FOCUS
        if (component.isActivePressed) mask = mask or BlinkSelectorAtom.PSEUDO_ACTIVE
        return mask
    }

    private fun buildAncestorSnapshots(
        component: AbstractComponent,
        isDarkTheme: Boolean
    ): List<BlinkAncestorSnapshot> {
        val list = ArrayList<BlinkAncestorSnapshot>()
        var p = component.parent
        while (p != null) {
            val pTag = CssRule.normalizeSelectorTag(p.htmlTag.ifEmpty { "genericui:component" })
            val baseClasses = collectElementClasses(p)
            val pClasses = if (pTag == "genericui:app" && isDarkTheme && "bs-theme-dark" !in baseClasses) {
                LinkedHashSet(baseClasses).apply { add("bs-theme-dark") }
            } else {
                baseClasses
            }
            list.add(BlinkAncestorSnapshot(pTag, p.id, pClasses, componentPseudoMask(p)))
            p = p.parent
        }
        val ordered = list.reversed().toMutableList()
        val selfTag = CssRule.normalizeSelectorTag(component.htmlTag.ifEmpty { "genericui:component" })
        val hasAppRoot = ordered.any { it.tag == "genericui:app" }
        if (selfTag != "genericui:app" && !hasAppRoot && (component is Fragment || ordered.isNotEmpty())) {
            val rootClasses = linkedSetOf<String>()
            if (isDarkTheme) rootClasses.add("bs-theme-dark")
            ordered.add(0, BlinkAncestorSnapshot("genericui:app", null, rootClasses, BlinkSelectorAtom.PSEUDO_ENABLED))
        }
        return ordered
    }

    private fun matchesAtom(
        atom: BlinkSelectorAtom,
        normalizedTag: String,
        id: String?,
        classes: Set<String>,
        pseudoMask: Int,
        ignoreSubjectPseudos: Boolean,
        subjectEnabled: Boolean
    ): Boolean {
        if (atom.unmatchable) return false
        val reqId = atom.id
        if (reqId != null && !reqId.equals(id, ignoreCase = false)) {
            return false
        }
        val reqTag = atom.normalizedTagName
        if (reqTag != null && reqTag != normalizedTag) {
            return false
        }
        for (cls in atom.classes) {
            if (cls !in classes) return false
        }
        for (excluded in atom.excludedClasses) {
            if (excluded in classes) return false
        }
        if (atom.excludedIds.isNotEmpty() && id != null && id in atom.excludedIds) {
            return false
        }
        if (atom.normalizedExcludedTags.isNotEmpty() && normalizedTag in atom.normalizedExcludedTags) {
            return false
        }
        if (ignoreSubjectPseudos) {
            if ((atom.pseudoStateMask and BlinkSelectorAtom.PSEUDO_ENABLED) != 0 && !subjectEnabled) return false
            if ((atom.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_DISABLED) != 0 && !subjectEnabled) return false
            if ((atom.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_ENABLED) != 0 && subjectEnabled) return false
        } else {
            if ((pseudoMask and atom.pseudoStateMask) != atom.pseudoStateMask) return false
            if ((pseudoMask and atom.excludedPseudoStateMask) != 0) return false
        }
        return true
    }

    private fun matchesRuleRightToLeft(
        rule: BlinkCompiledRule,
        normalizedTag: String,
        id: String?,
        classes: Set<String>,
        subjectEnabled: Boolean,
        ancestors: List<BlinkAncestorSnapshot>
    ): Boolean {
        val atoms = rule.selectorAtoms
        if (atoms.isEmpty()) return false
        val subject = atoms[atoms.lastIndex]
        val subjectPseudoMask = if (subjectEnabled) BlinkSelectorAtom.PSEUDO_ENABLED else BlinkSelectorAtom.PSEUDO_DISABLED
        if (!matchesAtom(
                atom = subject,
                normalizedTag = normalizedTag,
                id = id,
                classes = classes,
                pseudoMask = subjectPseudoMask,
                ignoreSubjectPseudos = true,
                subjectEnabled = subjectEnabled
            )
        ) {
            return false
        }
        if (atoms.size == 1) return true

        var ancIdx = ancestors.lastIndex
        var atomIdx = atoms.lastIndex - 1
        var combinator = subject.combinatorToLeft ?: CssCombinator.DESCENDANT

        while (atomIdx >= 0) {
            if (ancIdx < 0) return false
            val atom = atoms[atomIdx]
            if (combinator == CssCombinator.CHILD) {
                val anc = ancestors[ancIdx]
                if (!matchesAtom(atom, anc.tag, anc.id, anc.classes, anc.pseudoMask, ignoreSubjectPseudos = false, subjectEnabled = true)) {
                    return false
                }
                ancIdx--
            } else {
                var matchedAt = -1
                var scan = ancIdx
                while (scan >= 0) {
                    val anc = ancestors[scan]
                    if (matchesAtom(atom, anc.tag, anc.id, anc.classes, anc.pseudoMask, ignoreSubjectPseudos = false, subjectEnabled = true)) {
                        matchedAt = scan
                        break
                    }
                    scan--
                }
                if (matchedAt < 0) return false
                ancIdx = matchedAt - 1
            }
            combinator = atom.combinatorToLeft ?: CssCombinator.DESCENDANT
            atomIdx--
        }
        return true
    }

    fun matchRulesForComponent(
        ruleSet: BlinkRuleBucketMap,
        component: AbstractComponent,
        tagName: String = component.htmlTag.ifEmpty { "genericui:component" },
        viewportWidthPx: Int = 1280,
        isDarkTheme: Boolean = Application.current?.getResolvedTheme() == ThemeMode.DARK
    ): List<BlinkCompiledRule> {
        val normalizedTag = CssRule.normalizeSelectorTag(tagName)
        val baseClasses = collectElementClasses(component)
        val classes = if (normalizedTag == "genericui:app" && isDarkTheme && "bs-theme-dark" !in baseClasses) {
            LinkedHashSet(baseClasses).apply { add("bs-theme-dark") }
        } else {
            baseClasses
        }
        val id = component.id
        val ancestors = buildAncestorSnapshots(component, isDarkTheme)
        val effectiveDark = isDarkTheme || "bs-theme-dark" in classes || ancestors.any { "bs-theme-dark" in it.classes }
        val subjectEnabled = component.isEnabled

        val candidates = LinkedHashSet<BlinkCompiledRule>()
        if (!id.isNullOrEmpty()) {
            ruleSet.idBuckets[id]?.let { bucket ->
                for (r in bucket) candidates.add(r)
            }
        }
        for (cls in classes) {
            ruleSet.classBuckets[cls]?.let { bucket ->
                for (r in bucket) candidates.add(r)
            }
        }
        ruleSet.tagBuckets[normalizedTag]?.let { bucket ->
            for (r in bucket) candidates.add(r)
        }
        for (r in ruleSet.directUniversalBucket) {
            candidates.add(r)
        }
        for (r in ruleSet.otherDescendantUniversalBucket) {
            candidates.add(r)
        }
        if (ruleSet.universalAncestorClasses.isNotEmpty()) {
            for (anc in ancestors) {
                for (cls in anc.classes) {
                    ruleSet.descendantUniversalByAncestorClass[cls]?.let { bucket ->
                        for (r in bucket) candidates.add(r)
                    }
                }
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val matched = ArrayList<BlinkCompiledRule>(candidates.size)
        for (rule in candidates) {
            if (!rule.matchesMediaAndTheme(viewportWidthPx, effectiveDark)) continue
            if (matchesRuleRightToLeft(rule, normalizedTag, id, classes, subjectEnabled, ancestors)) {
                matched.add(rule)
            }
        }
        matched.sortWith(compareBy<BlinkCompiledRule> { it.specificity }.thenBy { it.sourceOrder })
        return matched
    }

    fun resolveStyleForComponent(
        ruleSet: BlinkRuleBucketMap,
        component: AbstractComponent,
        tagName: String = component.htmlTag.ifEmpty { "genericui:component" },
        viewportWidthPx: Int = 1280
    ): ComputedStyle {
        val isDarkTheme = Application.current?.getResolvedTheme() == ThemeMode.DARK
        val normalizedTag = CssRule.normalizeSelectorTag(tagName)
        val baseClasses = collectElementClasses(component)
        val classes = if (normalizedTag == "genericui:app" && isDarkTheme && "bs-theme-dark" !in baseClasses) {
            LinkedHashSet(baseClasses).apply { add("bs-theme-dark") }
        } else {
            baseClasses
        }
        val id = component.id

        var hasAnyCandidate = ruleSet.directUniversalBucket.isNotEmpty() || ruleSet.otherDescendantUniversalBucket.isNotEmpty()
        var hasAnyMediaRule = ruleSet.hasUnconstrainedUniversalMediaRules
        var needsAncestors = if (isDarkTheme) {
            ruleSet.unconstrainedUniversalNeedsAncestorsDark
        } else {
            ruleSet.unconstrainedUniversalNeedsAncestorsLight
        }
        if (!id.isNullOrEmpty()) {
            ruleSet.idBuckets[id]?.let { bucket ->
                if (bucket.isNotEmpty()) {
                    hasAnyCandidate = true
                    if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != -1 || it.mediaMaxWidthPx != -1 }) {
                        hasAnyMediaRule = true
                    }
                    if (!needsAncestors && bucket.any { it.selectorAtoms.size > 1 || (!isDarkTheme && it.themeFilter != BlinkCompiledRule.THEME_ANY) }) {
                        needsAncestors = true
                    }
                }
            }
        }
        for (cls in classes) {
            ruleSet.classBuckets[cls]?.let { bucket ->
                if (bucket.isNotEmpty()) {
                    hasAnyCandidate = true
                    if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != -1 || it.mediaMaxWidthPx != -1 }) {
                        hasAnyMediaRule = true
                    }
                    if (!needsAncestors && bucket.any { it.selectorAtoms.size > 1 || (!isDarkTheme && it.themeFilter != BlinkCompiledRule.THEME_ANY) }) {
                        needsAncestors = true
                    }
                }
            }
        }
        ruleSet.tagBuckets[normalizedTag]?.let { bucket ->
            if (bucket.isNotEmpty()) {
                hasAnyCandidate = true
                if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != -1 || it.mediaMaxWidthPx != -1 }) {
                    hasAnyMediaRule = true
                }
                if (!needsAncestors && bucket.any { it.selectorAtoms.size > 1 || (!isDarkTheme && it.themeFilter != BlinkCompiledRule.THEME_ANY) }) {
                    needsAncestors = true
                }
            }
        }
        var matchedUniversalAncBuckets: ArrayList<Array<BlinkCompiledRule>>? = null
        if (ruleSet.universalAncestorClasses.isNotEmpty()) {
            var p = component.parent
            while (p != null) {
                val pClasses = collectElementClasses(p)
                for (cls in pClasses) {
                    if (cls in ruleSet.universalAncestorClasses) {
                        val bucket = ruleSet.descendantUniversalByAncestorClass[cls] ?: continue
                        val list = matchedUniversalAncBuckets ?: ArrayList<Array<BlinkCompiledRule>>(2).also { matchedUniversalAncBuckets = it }
                        list.add(bucket)
                        hasAnyCandidate = true
                        needsAncestors = true
                        if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != -1 || it.mediaMaxWidthPx != -1 }) {
                            hasAnyMediaRule = true
                        }
                    }
                }
                p = p.parent
            }
            if (isDarkTheme && "bs-theme-dark" in ruleSet.universalAncestorClasses) {
                ruleSet.descendantUniversalByAncestorClass["bs-theme-dark"]?.let { bucket ->
                    val list = matchedUniversalAncBuckets ?: ArrayList<Array<BlinkCompiledRule>>(2).also { matchedUniversalAncBuckets = it }
                    list.add(bucket)
                    hasAnyCandidate = true
                    needsAncestors = true
                    if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != -1 || it.mediaMaxWidthPx != -1 }) {
                        hasAnyMediaRule = true
                    }
                }
            }
        }
        component.cachedSheetHasMediaRules = hasAnyMediaRule
        if (!hasAnyCandidate) {
            return ComputedStyle.EMPTY
        }

        val ancestors = if (needsAncestors) buildAncestorSnapshots(component, isDarkTheme) else emptyList()
        val effectiveDark = isDarkTheme || "bs-theme-dark" in classes || ancestors.any { "bs-theme-dark" in it.classes }
        val subjectEnabled = component.isEnabled
        val subjectPseudoCacheMask = if (subjectEnabled) BlinkSelectorAtom.PSEUDO_ENABLED else BlinkSelectorAtom.PSEUDO_DISABLED
        val ancSig = if (ancestors.isEmpty()) {
            ""
        } else {
            buildString {
                for (anc in ancestors) {
                    append(anc.tag).append('#').append(anc.id ?: "").append(':').append(anc.pseudoMask).append('.')
                    for (c in anc.classes) append(c).append(',')
                    append('>')
                }
            }
        }
        val mediaBucket = if (hasAnyMediaRule) ruleSet.resolveMediaQueryBucket(viewportWidthPx) else 0
        val cacheKey = BlinkMatchedPropertiesCache.buildKey(
            sheetId = ruleSet.hashCode(),
            tagName = normalizedTag,
            id = id,
            classes = classes,
            pseudoStateMask = subjectPseudoCacheMask,
            themeOrdinal = if (effectiveDark) 1 else 0,
            viewportWidthBucket = mediaBucket,
            ancestorSignature = ancSig
        )
        BlinkMatchedPropertiesCache.get(cacheKey)?.let { return it }

        val candidates = LinkedHashSet<BlinkCompiledRule>()
        if (!id.isNullOrEmpty()) {
            ruleSet.idBuckets[id]?.let { bucket ->
                for (r in bucket) candidates.add(r)
            }
        }
        for (cls in classes) {
            ruleSet.classBuckets[cls]?.let { bucket ->
                for (r in bucket) candidates.add(r)
            }
        }
        ruleSet.tagBuckets[normalizedTag]?.let { bucket ->
            for (r in bucket) candidates.add(r)
        }
        for (r in ruleSet.directUniversalBucket) {
            candidates.add(r)
        }
        for (r in ruleSet.otherDescendantUniversalBucket) {
            candidates.add(r)
        }
        matchedUniversalAncBuckets?.let { buckets ->
            for (bucket in buckets) {
                for (r in bucket) candidates.add(r)
            }
        }
        val matched = ArrayList<BlinkCompiledRule>(candidates.size)
        for (rule in candidates) {
            if (!rule.matchesMediaAndTheme(viewportWidthPx, effectiveDark)) continue
            if (matchesRuleRightToLeft(rule, normalizedTag, id, classes, subjectEnabled, ancestors)) {
                matched.add(rule)
            }
        }
        matched.sortWith(compareBy<BlinkCompiledRule> { it.specificity }.thenBy { it.sourceOrder })
        val computed = BlinkStyleBuilder.buildComputedStyleFromRules(matched)
        BlinkMatchedPropertiesCache.put(cacheKey, computed)
        return computed
    }

    private fun resolveCombinedActiveCssStyleSheet(): CssStyleSheet? {
        if (activeSheets.isEmpty()) return null
        cachedCombinedActiveCssSheet?.let { return it }
        return CssStyleSheet.combineCascade(activeSheets.map { it.styleSheet }).also {
            cachedCombinedActiveCssSheet = it
        }
    }

    fun resolveTree(
        root: AbstractComponent,
        viewportWidthPx: Int = 1280,
        parentStyle: ComputedStyle? = null
    ) {
        SystemCssAnimationController.ensureInstalled()
        resolveCombinedActiveCssStyleSheet()?.let { root.lastAppliedStyleSheet = it }
        val activeBackend = resolveActiveBackendTarget()
        var resolved = ComputedStyle.EMPTY
        for (sheet in activeSheets) {
            val fromSheet = resolveStyleForComponent(sheet.ruleSet.forBackend(activeBackend), root, viewportWidthPx = viewportWidthPx)
            resolved = resolved.mergeWith(fromSheet)
        }
        if (parentStyle != null) {
            resolved = resolved.copy(
                textColor = resolved.textColor ?: parentStyle.textColor,
                fontSizePx = resolved.fontSizePx ?: parentStyle.fontSizePx,
                fontFamily = resolved.fontFamily ?: parentStyle.fontFamily,
                fontWeightNumeric = resolved.fontWeightNumeric ?: parentStyle.fontWeightNumeric,
                textAlign = resolved.textAlign ?: parentStyle.textAlign
            )
        }
        if (!root.getStyle().isEmpty()) {
            resolved = root.getStyle().toComputedStyle(resolved)
        }
        root.applyComputedStyle(resolved)
        if (root is com.nexustools.genericui.core.AbstractContainer) {
            for (child in root.children) {
                resolveTree(child, viewportWidthPx, resolved)
            }
        }
        if (root is Composite) {
            for (child in root.internalCompositeChildren()) {
                resolveTree(child, viewportWidthPx, resolved)
            }
        }
    }
}
