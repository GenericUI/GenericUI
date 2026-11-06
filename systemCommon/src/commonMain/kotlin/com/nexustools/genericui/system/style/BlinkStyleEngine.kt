package com.nexustools.genericui.system.style

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry

/**
 * System-only Chromium Blink-modeled CSS selector matching and style resolution engine.
 *
 * Evaluates precompiled [BlinkRuleBucketMap]s right-to-left using [BlinkSelectorAtom]s and
 * [BlinkMatchedPropertiesCache] with zero runtime CSS string parsing.
 */
object BlinkStyleEngine {
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val activeSheets = mutableListOf<BlinkStyleSheet>()
    private val ruleSetsBySheet = linkedMapOf<CssStyleSheet, BlinkRuleBucketMap>()
    val matchedPropertiesCache: BlinkMatchedPropertiesCache = BlinkMatchedPropertiesCache

    init {
        installHooks()
    }

    fun ensureInstalled() {
        installHooks()
    }

    private fun installHooks() {
        if (CssStyleSheet.systemStyleResolverHook == null) {
            CssStyleSheet.systemStyleResolverHook = { sheet, component, tagName, viewportWidthPx ->
                var ruleSet = ruleSetsBySheet[sheet]
                if (ruleSet == null) {
                    val existing = StyleSheetRegistry.all().firstOrNull { it.styleSheet === sheet }
                    if (existing != null) {
                        onPrecompiledStyleSheetRegistered(existing)
                        ruleSet = ruleSetsBySheet[sheet]
                    }
                }
                if (ruleSet != null) {
                    resolveStyleForComponent(ruleSet, component, tagName, viewportWidthPx)
                } else {
                    null
                }
            }
        }
        if (CssStyleSheet.systemMediaQueryBucketHook == null) {
            CssStyleSheet.systemMediaQueryBucketHook = { sheet, viewportWidthPx ->
                var ruleSet = ruleSetsBySheet[sheet]
                if (ruleSet == null) {
                    val existing = StyleSheetRegistry.all().firstOrNull { it.styleSheet === sheet }
                    if (existing != null) {
                        onPrecompiledStyleSheetRegistered(existing)
                        ruleSet = ruleSetsBySheet[sheet]
                    }
                }
                ruleSet?.resolveMediaQueryBucket(viewportWidthPx)
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
        BlinkMatchedPropertiesCache.invalidateAll()
    }

    fun registerRuleSet(cssStyleSheet: CssStyleSheet, ruleSet: BlinkRuleBucketMap) {
        ensureInstalled()
        ruleSetsBySheet[cssStyleSheet] = ruleSet
        BlinkMatchedPropertiesCache.invalidateAll()
    }

    fun unregisterStyleSheet(sheet: BlinkStyleSheet) {
        activeSheets.remove(sheet)
        ruleSetsBySheet.remove(sheet.styleSheet)
        BlinkMatchedPropertiesCache.invalidateAll()
    }

    fun getActiveStyleSheets(): List<BlinkStyleSheet> = activeSheets.toList()

    fun clear() {
        activeSheets.clear()
        ruleSetsBySheet.clear()
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

    private fun buildAncestorSnapshots(
        component: AbstractComponent,
        isDarkTheme: Boolean
    ): List<Triple<String, String?, Set<String>>> {
        val list = ArrayList<Triple<String, String?, Set<String>>>()
        var p = component.parent
        while (p != null) {
            val pTag = CssRule.normalizeSelectorTag(p.htmlTag.ifEmpty { "genericui:component" })
            val baseClasses = collectElementClasses(p)
            val pClasses = if (pTag == "genericui:app" && isDarkTheme && "bs-theme-dark" !in baseClasses) {
                LinkedHashSet(baseClasses).apply { add("bs-theme-dark") }
            } else {
                baseClasses
            }
            list.add(Triple(pTag, p.id, pClasses))
            p = p.parent
        }
        val ordered = list.reversed().toMutableList()
        val selfTag = CssRule.normalizeSelectorTag(component.htmlTag.ifEmpty { "genericui:component" })
        val hasAppRoot = ordered.any { it.first == "genericui:app" }
        if (selfTag != "genericui:app" && !hasAppRoot && (component is Fragment || ordered.isNotEmpty())) {
            val rootClasses = linkedSetOf<String>()
            if (isDarkTheme) rootClasses.add("bs-theme-dark")
            ordered.add(0, Triple("genericui:app", null, rootClasses))
        }
        return ordered
    }

    private fun matchesAtom(
        atom: BlinkSelectorAtom,
        normalizedTag: String,
        id: String?,
        classes: Set<String>,
        ignoreSubjectPseudos: Boolean
    ): Boolean {
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
        if (!ignoreSubjectPseudos && atom.pseudoStateMask != 0) {
            return false
        }
        return true
    }

    private fun matchesRuleRightToLeft(
        rule: BlinkCompiledRule,
        normalizedTag: String,
        id: String?,
        classes: Set<String>,
        ancestors: List<Triple<String, String?, Set<String>>>
    ): Boolean {
        val atoms = rule.selectorAtoms
        if (atoms.isEmpty()) return false
        val subject = atoms[atoms.lastIndex]
        if (!matchesAtom(subject, normalizedTag, id, classes, ignoreSubjectPseudos = true)) {
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
                if (!matchesAtom(atom, anc.first, anc.second, anc.third, ignoreSubjectPseudos = false)) {
                    return false
                }
                ancIdx--
            } else {
                var matchedAt = -1
                var scan = ancIdx
                while (scan >= 0) {
                    val anc = ancestors[scan]
                    if (matchesAtom(atom, anc.first, anc.second, anc.third, ignoreSubjectPseudos = false)) {
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
        val effectiveDark = isDarkTheme || "bs-theme-dark" in classes || ancestors.any { "bs-theme-dark" in it.third }

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
        for (r in ruleSet.universalBucket) {
            candidates.add(r)
        }

        if (candidates.isEmpty()) return emptyList()

        val matched = ArrayList<BlinkCompiledRule>(candidates.size)
        for (rule in candidates) {
            if (!rule.matchesMediaAndTheme(viewportWidthPx, effectiveDark)) continue
            if (matchesRuleRightToLeft(rule, normalizedTag, id, classes, ancestors)) {
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

        var hasAnyCandidate = ruleSet.universalBucket.isNotEmpty()
        var hasAnyMediaRule = ruleSet.universalBucket.any { it.mediaMinWidthPx != null || it.mediaMaxWidthPx != null }
        var needsAncestors = ruleSet.universalBucket.any { it.selectorAtoms.size > 1 || (!isDarkTheme && it.themeFilter != BlinkCompiledRule.THEME_ANY) }
        if (!id.isNullOrEmpty()) {
            ruleSet.idBuckets[id]?.let { bucket ->
                if (bucket.isNotEmpty()) {
                    hasAnyCandidate = true
                    if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != null || it.mediaMaxWidthPx != null }) {
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
                    if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != null || it.mediaMaxWidthPx != null }) {
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
                if (!hasAnyMediaRule && bucket.any { it.mediaMinWidthPx != null || it.mediaMaxWidthPx != null }) {
                    hasAnyMediaRule = true
                }
                if (!needsAncestors && bucket.any { it.selectorAtoms.size > 1 || (!isDarkTheme && it.themeFilter != BlinkCompiledRule.THEME_ANY) }) {
                    needsAncestors = true
                }
            }
        }
        component.cachedSheetHasMediaRules = hasAnyMediaRule
        if (!hasAnyCandidate) {
            return ComputedStyle.EMPTY
        }

        val ancestors = if (needsAncestors) buildAncestorSnapshots(component, isDarkTheme) else emptyList()
        val effectiveDark = isDarkTheme || "bs-theme-dark" in classes || ancestors.any { "bs-theme-dark" in it.third }
        val ancSig = if (ancestors.isEmpty()) {
            ""
        } else {
            buildString {
                for (anc in ancestors) {
                    append(anc.first).append('#').append(anc.second ?: "").append('.')
                    for (c in anc.third) append(c).append(',')
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
            pseudoStateMask = 0,
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
        for (r in ruleSet.universalBucket) {
            candidates.add(r)
        }
        val matched = ArrayList<BlinkCompiledRule>(candidates.size)
        for (rule in candidates) {
            if (!rule.matchesMediaAndTheme(viewportWidthPx, effectiveDark)) continue
            if (matchesRuleRightToLeft(rule, normalizedTag, id, classes, ancestors)) {
                matched.add(rule)
            }
        }
        matched.sortWith(compareBy<BlinkCompiledRule> { it.specificity }.thenBy { it.sourceOrder })
        val computed = BlinkStyleBuilder.buildComputedStyleFromRules(matched)
        BlinkMatchedPropertiesCache.put(cacheKey, computed)
        return computed
    }

    fun resolveTree(
        root: AbstractComponent,
        viewportWidthPx: Int = 1280,
        parentStyle: ComputedStyle? = null
    ) {
        var resolved = ComputedStyle.EMPTY
        for (sheet in activeSheets) {
            val fromSheet = resolveStyleForComponent(sheet.ruleSet, root, viewportWidthPx = viewportWidthPx)
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
    }
}
