package com.nexustools.genericui.system.style

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.style.css.BackendStyleMask
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * Chromium Blink-style `RuleSet` hash bucket map partitioning [BlinkCompiledRule]s by their
 * rightmost compound selector (`#id`, `.class`, `tagName`, or universal `*`).
 */
class BlinkRuleBucketMap(
    val idBuckets: Map<String, Array<BlinkCompiledRule>> = emptyMap(),
    val classBuckets: Map<String, Array<BlinkCompiledRule>> = emptyMap(),
    val tagBuckets: Map<String, Array<BlinkCompiledRule>> = emptyMap(),
    val universalBucket: Array<BlinkCompiledRule> = emptyArray(),
    val allRules: List<BlinkCompiledRule> = emptyList()
) {
    private class AncestorClassInvalidationEntry(
        val ancestorAtom: BlinkSelectorAtom,
        val subjectAtom: BlinkSelectorAtom
    )

    val hasBackendSpecificRules: Boolean = allRules.any { it.backendMask != BackendStyleMask.ALL }
    private val backendPartitionCache = arrayOfNulls<BlinkRuleBucketMap>(BackendTarget.entries.size)

    fun forBackend(target: BackendTarget?): BlinkRuleBucketMap {
        if (target == null || !hasBackendSpecificRules) return this
        backendPartitionCache[target.ordinal]?.let { return it }
        val filtered = allRules.filter { it.matchesBackend(target) }
        val partitioned = when {
            filtered.isEmpty() -> EMPTY
            filtered.size == allRules.size -> this
            else -> fromRules(filtered)
        }
        backendPartitionCache[target.ordinal] = partitioned
        if (partitioned !== EMPTY && partitioned !== this) {
            partitioned.backendPartitionCache[target.ordinal] = partitioned
        }
        return partitioned
    }

    val referencedSubjectClasses: Set<String> = buildSet {
        for (rule in allRules) {
            val subject = rule.subjectAtom ?: continue
            for (c in subject.classes) add(c)
            for (ec in subject.excludedClasses) add(ec)
        }
    }

    val referencedAncestorClasses: Set<String> = buildSet {
        for (rule in allRules) {
            val atoms = rule.selectorAtoms
            for (i in 0 until (atoms.size - 1)) {
                val anc = atoms[i]
                for (c in anc.classes) add(c)
                for (ec in anc.excludedClasses) add(ec)
                if ((anc.pseudoStateMask and BlinkSelectorAtom.PSEUDO_HOVER) != 0 ||
                    (anc.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_HOVER) != 0
                ) {
                    add("pseudo:hover")
                }
                if ((anc.pseudoStateMask and BlinkSelectorAtom.PSEUDO_FOCUS) != 0 ||
                    (anc.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_FOCUS) != 0
                ) {
                    add("pseudo:focus")
                }
            }
        }
    }

    private val ancestorInvalidationByClass: Map<String, Array<AncestorClassInvalidationEntry>> = run {
        val map = linkedMapOf<String, ArrayList<AncestorClassInvalidationEntry>>()
        for (rule in allRules) {
            val atoms = rule.selectorAtoms
            val subject = rule.subjectAtom ?: continue
            for (i in 0 until (atoms.size - 1)) {
                val ancAtom = atoms[i]
                val entry = AncestorClassInvalidationEntry(ancAtom, subject)
                for (c in ancAtom.classes) {
                    map.getOrPut(c) { ArrayList(4) }.add(entry)
                }
                for (ec in ancAtom.excludedClasses) {
                    map.getOrPut(ec) { ArrayList(4) }.add(entry)
                }
                if ((ancAtom.pseudoStateMask and BlinkSelectorAtom.PSEUDO_HOVER) != 0 ||
                    (ancAtom.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_HOVER) != 0
                ) {
                    map.getOrPut("pseudo:hover") { ArrayList(4) }.add(entry)
                }
                if ((ancAtom.pseudoStateMask and BlinkSelectorAtom.PSEUDO_FOCUS) != 0 ||
                    (ancAtom.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_FOCUS) != 0
                ) {
                    map.getOrPut("pseudo:focus") { ArrayList(4) }.add(entry)
                }
            }
        }
        map.mapValues { (_, list) -> list.toTypedArray() }
    }

    /**
     * Evaluates compound ancestor invalidation sets (`DescendantInvalidationSet`) for [changedClasses]
     * on an ancestor element, returning `DESCENDANT_INVALIDATION_NONE`, `TARGETED`, or `WHOLE_SUBTREE`.
     */
    internal fun collectDescendantInvalidation(
        changedClasses: List<String>,
        ancestorNormalizedTag: String,
        ancestorId: String?,
        ancestorClasses: Set<String>,
        outSubjectClasses: MutableSet<String>,
        outSubjectIds: MutableSet<String>,
        outSubjectTags: MutableSet<String>
    ): Int {
        if (changedClasses.isEmpty() || ancestorInvalidationByClass.isEmpty()) {
            return CssStyleSheet.DESCENDANT_INVALIDATION_NONE
        }
        var mode = CssStyleSheet.DESCENDANT_INVALIDATION_NONE
        for (changedCls in changedClasses) {
            val entries = ancestorInvalidationByClass[changedCls] ?: continue
            for (entry in entries) {
                val anc = entry.ancestorAtom
                val reqId = anc.id
                if (reqId != null && !reqId.equals(ancestorId, ignoreCase = false)) continue
                val reqTag = anc.normalizedTagName
                if (reqTag != null && reqTag != ancestorNormalizedTag) continue
                if (ancestorId != null && anc.excludedIds.isNotEmpty() && ancestorId in anc.excludedIds) continue
                if (anc.normalizedExcludedTags.isNotEmpty() && ancestorNormalizedTag in anc.normalizedExcludedTags) continue

                var reqClassesOk = true
                for (reqCls in anc.classes) {
                    if (reqCls !in ancestorClasses && reqCls !in changedClasses) {
                        reqClassesOk = false
                        break
                    }
                }
                if (!reqClassesOk) continue

                var exclClassesOk = true
                for (ec in anc.excludedClasses) {
                    if (ec in ancestorClasses && ec !in changedClasses) {
                        exclClassesOk = false
                        break
                    }
                }
                if (!exclClassesOk) continue
                if ((anc.pseudoStateMask and BlinkSelectorAtom.PSEUDO_HOVER) != 0 &&
                    "pseudo:hover" !in ancestorClasses && "pseudo:hover" !in changedClasses
                ) continue
                if ((anc.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_HOVER) != 0 &&
                    "pseudo:hover" in ancestorClasses && "pseudo:hover" !in changedClasses
                ) continue
                if ((anc.pseudoStateMask and BlinkSelectorAtom.PSEUDO_FOCUS) != 0 &&
                    "pseudo:focus" !in ancestorClasses && "pseudo:focus" !in changedClasses
                ) continue
                if ((anc.excludedPseudoStateMask and BlinkSelectorAtom.PSEUDO_FOCUS) != 0 &&
                    "pseudo:focus" in ancestorClasses && "pseudo:focus" !in changedClasses
                ) continue

                val subj = entry.subjectAtom
                val subjId = subj.id?.trim()?.takeIf { it.isNotEmpty() }
                if (subjId != null) {
                    outSubjectIds.add(subjId)
                    if (mode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE) {
                        mode = CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
                    }
                } else if (subj.classes.isNotEmpty()) {
                    outSubjectClasses.add(subj.classes[0])
                    if (mode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE) {
                        mode = CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
                    }
                } else {
                    val subjTag = subj.normalizedTagName
                    if (subjTag != null) {
                        outSubjectTags.add(subjTag)
                        if (mode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE) {
                            mode = CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
                        }
                    } else {
                        return CssStyleSheet.DESCENDANT_INVALIDATION_WHOLE_SUBTREE
                    }
                }
            }
        }
        return mode
    }
    private val mediaRules: Array<BlinkCompiledRule> = allRules.filter {
        it.mediaMinWidthPx >= 0 || it.mediaMaxWidthPx >= 0
    }.toTypedArray()

    val directUniversalBucket: Array<BlinkCompiledRule> =
        universalBucket.filter { it.selectorAtoms.size == 1 }.toTypedArray()

    val descendantUniversalByAncestorClass: Map<String, Array<BlinkCompiledRule>>
    val otherDescendantUniversalBucket: Array<BlinkCompiledRule>

    init {
        val byAncCls = linkedMapOf<String, ArrayList<BlinkCompiledRule>>()
        val other = ArrayList<BlinkCompiledRule>()
        for (rule in universalBucket) {
            if (rule.selectorAtoms.size <= 1) continue
            var keyClass: String? = null
            for (i in (rule.selectorAtoms.lastIndex - 1) downTo 0) {
                val firstCls = rule.selectorAtoms[i].classes.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                if (firstCls != null) {
                    keyClass = firstCls
                    break
                }
            }
            if (keyClass != null) {
                byAncCls.getOrPut(keyClass) { ArrayList(4) }.add(rule)
            } else {
                other.add(rule)
            }
        }
        descendantUniversalByAncestorClass = byAncCls.mapValues { (_, list) -> list.toTypedArray() }
        otherDescendantUniversalBucket = other.toTypedArray()
    }

    val universalAncestorClasses: Set<String> = descendantUniversalByAncestorClass.keys

    val hasUnconstrainedUniversalMediaRules: Boolean =
        directUniversalBucket.any { it.mediaMinWidthPx != -1 || it.mediaMaxWidthPx != -1 } ||
            otherDescendantUniversalBucket.any { it.mediaMinWidthPx != -1 || it.mediaMaxWidthPx != -1 }

    val unconstrainedUniversalNeedsAncestorsLight: Boolean =
        otherDescendantUniversalBucket.isNotEmpty() ||
            directUniversalBucket.any { it.themeFilter != BlinkCompiledRule.THEME_ANY }

    val unconstrainedUniversalNeedsAncestorsDark: Boolean =
        otherDescendantUniversalBucket.isNotEmpty()

    /**
     * Evaluates which rightmost subjects (or constraining ancestor selectors for descendant universal rules)
     * can change match status when viewport width changes from [oldViewportWidthPx] to [newViewportWidthPx].
     */
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
        if (oldViewportWidthPx == newViewportWidthPx || mediaRules.isEmpty()) {
            return CssStyleSheet.DESCENDANT_INVALIDATION_NONE
        }
        var mode = CssStyleSheet.DESCENDANT_INVALIDATION_NONE
        for (rule in mediaRules) {
            if (rule.matchesMediaWidth(oldViewportWidthPx) == rule.matchesMediaWidth(newViewportWidthPx)) {
                continue
            }
            val subj = rule.subjectAtom ?: continue
            val subjId = subj.id?.trim()?.takeIf { it.isNotEmpty() }
            if (subjId != null) {
                outSubjectIds.add(subjId)
                if (mode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE) {
                    mode = CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
                }
            } else if (subj.classes.isNotEmpty()) {
                outSubjectClasses.add(subj.classes[0])
                if (mode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE) {
                    mode = CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
                }
            } else {
                val subjTag = subj.normalizedTagName
                if (subjTag != null) {
                    outSubjectTags.add(subjTag)
                    if (mode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE) {
                        mode = CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
                    }
                } else {
                    val atoms = rule.selectorAtoms
                    var targetedByAncestor = false
                    if (atoms.size > 1) {
                        for (i in (atoms.lastIndex - 1) downTo 0) {
                            val anc = atoms[i]
                            val ancId = anc.id?.trim()?.takeIf { it.isNotEmpty() }
                            if (ancId != null) {
                                outAncestorIds.add(ancId)
                                targetedByAncestor = true
                                break
                            }
                            val ancCls = anc.classes.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                            if (ancCls != null) {
                                outAncestorClasses.add(ancCls)
                                targetedByAncestor = true
                                break
                            }
                            val ancTag = anc.normalizedTagName
                            if (ancTag != null) {
                                outAncestorTags.add(ancTag)
                                targetedByAncestor = true
                                break
                            }
                        }
                    }
                    if (!targetedByAncestor) {
                        return CssStyleSheet.DESCENDANT_INVALIDATION_WHOLE_SUBTREE
                    }
                    if (mode == CssStyleSheet.DESCENDANT_INVALIDATION_NONE) {
                        mode = CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
                    }
                }
            }
        }
        return mode
    }

    val mediaBreakpoints: IntArray = buildSet {
        for (rule in allRules) {
            if (rule.mediaMinWidthPx > 0) add(rule.mediaMinWidthPx)
            if (rule.mediaMaxWidthPx in 1 until Int.MAX_VALUE) add(rule.mediaMaxWidthPx)
        }
    }.sorted().toIntArray()

    fun resolveMediaQueryBucket(viewportWidthPx: Int): Int {
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

    companion object {
        val EMPTY = BlinkRuleBucketMap()

        fun fromRules(rules: List<BlinkCompiledRule>): BlinkRuleBucketMap {
            val sorted = rules.sortedWith(compareBy<BlinkCompiledRule> { it.specificity }.thenBy { it.sourceOrder })
            val idMap = linkedMapOf<String, ArrayList<BlinkCompiledRule>>()
            val classMap = linkedMapOf<String, ArrayList<BlinkCompiledRule>>()
            val tagMap = linkedMapOf<String, ArrayList<BlinkCompiledRule>>()
            val universalList = ArrayList<BlinkCompiledRule>()

            for (rule in sorted) {
                if (rule.selectorAtoms.isEmpty() || rule.selectorAtoms.any { it.unmatchable }) continue
                val subject = rule.subjectAtom ?: continue
                val id = subject.id?.trim()?.takeIf { it.isNotEmpty() }
                if (id != null) {
                    idMap.getOrPut(id) { ArrayList(4) }.add(rule)
                    continue
                }
                val firstClass = subject.classes.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                if (firstClass != null) {
                    classMap.getOrPut(firstClass) { ArrayList(8) }.add(rule)
                    continue
                }
                val tag = subject.normalizedTagName
                if (tag != null) {
                    tagMap.getOrPut(tag) { ArrayList(8) }.add(rule)
                    continue
                }
                universalList.add(rule)
            }

            return BlinkRuleBucketMap(
                idBuckets = idMap.mapValues { (_, list) -> list.toTypedArray() },
                classBuckets = classMap.mapValues { (_, list) -> list.toTypedArray() },
                tagBuckets = tagMap.mapValues { (_, list) -> list.toTypedArray() },
                universalBucket = universalList.toTypedArray(),
                allRules = sorted
            )
        }

        fun fromCompiledStyleRules(
            rules: List<CompiledStyleRule>,
            targetBackend: BackendTarget? = null
        ): BlinkRuleBucketMap {
            val effectiveRules = if (targetBackend != null) {
                rules.filter { BackendStyleMask.matches(it.backendMask, targetBackend) }
            } else {
                rules
            }
            if (effectiveRules.isEmpty()) return EMPTY
            return fromRules(effectiveRules.mapIndexed { idx, r -> BlinkCompiledRule.fromCompiledStyleRule(r, idx) })
        }
    }
}
