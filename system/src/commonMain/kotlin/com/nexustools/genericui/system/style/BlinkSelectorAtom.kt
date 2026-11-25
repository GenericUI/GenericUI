package com.nexustools.genericui.system.style

import com.nexustools.genericui.style.css.CompiledSelectorSegment
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssRule

/**
 * Single compound selector atom in a [BlinkCompiledRule]'s selector chain.
 */
class BlinkSelectorAtom(
    val tagName: String? = null,
    val id: String? = null,
    val classes: Array<String> = emptyArray(),
    val excludedClasses: Array<String> = emptyArray(),
    val pseudoStateMask: Int = 0,
    val excludedPseudoStateMask: Int = 0,
    val combinatorToLeft: CssCombinator? = null,
    val excludedTags: Array<String> = emptyArray(),
    val excludedIds: Array<String> = emptyArray(),
    val unmatchable: Boolean = false
) {
    val normalizedTagName: String? = tagName
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() && it != "*" }
        ?.let { CssRule.normalizeSelectorTag(it) }

    val normalizedExcludedTags: Array<String> =
        if (excludedTags.isEmpty()) emptyArray()
        else Array(excludedTags.size) { CssRule.normalizeSelectorTag(excludedTags[it]) }

    fun toCompiledSegment(combinatorToNext: CssCombinator? = null): CompiledSelectorSegment {
        val positivePseudos = linkedSetOf<String>()
        if ((pseudoStateMask and PSEUDO_HOVER) != 0) positivePseudos.add("hover")
        if ((pseudoStateMask and PSEUDO_FOCUS) != 0) positivePseudos.add("focus")
        if ((pseudoStateMask and PSEUDO_ACTIVE) != 0) positivePseudos.add("active")
        if ((pseudoStateMask and PSEUDO_DISABLED) != 0) positivePseudos.add("disabled")
        if ((pseudoStateMask and PSEUDO_CHECKED) != 0) positivePseudos.add("checked")
        if ((pseudoStateMask and PSEUDO_ENABLED) != 0) positivePseudos.add("enabled")
        val excludedPseudos = linkedSetOf<String>()
        if ((excludedPseudoStateMask and PSEUDO_HOVER) != 0) excludedPseudos.add("hover")
        if ((excludedPseudoStateMask and PSEUDO_FOCUS) != 0) excludedPseudos.add("focus")
        if ((excludedPseudoStateMask and PSEUDO_ACTIVE) != 0) excludedPseudos.add("active")
        if ((excludedPseudoStateMask and PSEUDO_DISABLED) != 0) excludedPseudos.add("disabled")
        if ((excludedPseudoStateMask and PSEUDO_CHECKED) != 0) excludedPseudos.add("checked")
        if ((excludedPseudoStateMask and PSEUDO_ENABLED) != 0) excludedPseudos.add("enabled")
        return CompiledSelectorSegment(
            tagName = tagName,
            id = id,
            classes = classes.toSet(),
            pseudoClass = CssRule.selectPrimaryPseudoClass(positivePseudos),
            pseudoClasses = positivePseudos,
            combinatorToNext = combinatorToNext,
            excludedClasses = excludedClasses.toSet(),
            excludedPseudoClasses = excludedPseudos,
            excludedTags = normalizedExcludedTags.toSet(),
            excludedIds = excludedIds.toSet(),
            unmatchable = unmatchable
        )
    }

    companion object {
        const val PSEUDO_HOVER: Int = 1 shl 0
        const val PSEUDO_FOCUS: Int = 1 shl 1
        const val PSEUDO_ACTIVE: Int = 1 shl 2
        const val PSEUDO_DISABLED: Int = 1 shl 3
        const val PSEUDO_CHECKED: Int = 1 shl 4
        const val PSEUDO_ENABLED: Int = 1 shl 5

        fun pseudoMaskForName(name: String?): Int = when (name?.trim()?.lowercase()) {
            "hover" -> PSEUDO_HOVER
            "focus", "focus-visible", "focus-within" -> PSEUDO_FOCUS
            "active" -> PSEUDO_ACTIVE
            "disabled" -> PSEUDO_DISABLED
            "checked" -> PSEUDO_CHECKED
            "enabled" -> PSEUDO_ENABLED
            else -> 0
        }

        fun fromCompiledSegment(
            seg: CompiledSelectorSegment,
            combinatorToLeft: CssCombinator? = null
        ): BlinkSelectorAtom {
            var mask = pseudoMaskForName(seg.pseudoClass)
            for (p in seg.pseudoClasses) {
                mask = mask or pseudoMaskForName(p)
            }
            var exclMask = 0
            for (ep in seg.excludedPseudoClasses) {
                exclMask = exclMask or pseudoMaskForName(ep)
            }
            val extraExcludedClasses = linkedSetOf<String>()
            val extraExcludedIds = linkedSetOf<String>()
            val extraExcludedTags = linkedSetOf<String>()
            for (p in seg.pseudoClasses) {
                if (p.startsWith("not(", ignoreCase = true) && p.endsWith(")")) {
                    val parsedNot = CssRule.parseCompoundToken(":$p")
                    extraExcludedClasses.addAll(parsedNot.excludedClasses)
                    extraExcludedIds.addAll(parsedNot.excludedIds)
                    extraExcludedTags.addAll(parsedNot.excludedTags)
                    for (ep in parsedNot.excludedPseudos) {
                        exclMask = exclMask or pseudoMaskForName(ep)
                    }
                }
            }
            val mergedExcludedClasses = if (extraExcludedClasses.isEmpty()) {
                seg.excludedClasses.toTypedArray()
            } else {
                (seg.excludedClasses + extraExcludedClasses).toTypedArray()
            }
            val mergedExcludedIds = if (extraExcludedIds.isEmpty()) {
                seg.excludedIds.toTypedArray()
            } else {
                (seg.excludedIds + extraExcludedIds).toTypedArray()
            }
            val mergedExcludedTags = if (extraExcludedTags.isEmpty()) {
                seg.excludedTags.toTypedArray()
            } else {
                (seg.excludedTags + extraExcludedTags).toTypedArray()
            }
            return BlinkSelectorAtom(
                tagName = seg.tagName,
                id = seg.id,
                classes = seg.classes.toTypedArray(),
                excludedClasses = mergedExcludedClasses,
                pseudoStateMask = mask,
                excludedPseudoStateMask = exclMask,
                combinatorToLeft = seg.combinatorToLeft ?: combinatorToLeft,
                excludedTags = mergedExcludedTags,
                excludedIds = mergedExcludedIds,
                unmatchable = seg.unmatchable
            )
        }
    }
}
