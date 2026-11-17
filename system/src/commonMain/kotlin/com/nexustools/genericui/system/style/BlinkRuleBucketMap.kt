package com.nexustools.genericui.system.style

import com.nexustools.genericui.style.css.CompiledStyleRule

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

        fun fromCompiledStyleRules(rules: List<CompiledStyleRule>): BlinkRuleBucketMap =
            fromRules(rules.mapIndexed { idx, r -> BlinkCompiledRule.fromCompiledStyleRule(r, idx) })
    }
}
