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
    val combinatorToLeft: CssCombinator? = null
) {
    val normalizedTagName: String? = tagName
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() && it != "*" }
        ?.let { CssRule.normalizeSelectorTag(it) }

    fun toCompiledSegment(combinatorToNext: CssCombinator? = null): CompiledSelectorSegment {
        val pseudo = when {
            (pseudoStateMask and PSEUDO_HOVER) != 0 -> "hover"
            (pseudoStateMask and PSEUDO_FOCUS) != 0 -> "focus"
            (pseudoStateMask and PSEUDO_ACTIVE) != 0 -> "active"
            (pseudoStateMask and PSEUDO_DISABLED) != 0 -> "disabled"
            (pseudoStateMask and PSEUDO_CHECKED) != 0 -> "checked"
            (pseudoStateMask and PSEUDO_ENABLED) != 0 -> "enabled"
            else -> null
        }
        return CompiledSelectorSegment(
            tagName = tagName,
            id = id,
            classes = classes.toSet(),
            pseudoClass = pseudo,
            combinatorToNext = combinatorToNext
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
            return BlinkSelectorAtom(
                tagName = seg.tagName,
                id = seg.id,
                classes = seg.classes.toTypedArray(),
                pseudoStateMask = mask,
                combinatorToLeft = seg.combinatorToLeft ?: combinatorToLeft
            )
        }
    }
}
