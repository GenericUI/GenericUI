package com.nexustools.genericui.system.style

import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComponentStyle

/**
 * Precompiled Blink CSS rule containing pre-parsed [BlinkSelectorAtom]s, strongly-typed
 * [BlinkPropertyEntry] values, and a pre-built [ComponentStyle].
 */
class BlinkCompiledRule(
    val selector: String,
    val selectorAtoms: Array<BlinkSelectorAtom>,
    val properties: Array<BlinkPropertyEntry> = emptyArray(),
    val style: ComponentStyle = ComponentStyle().also { s ->
        for (entry in properties) {
            BlinkStyleBuilder.applyPropertyEntry(s, entry)
        }
    },
    val specificity: Int,
    val sourceOrder: Int = 0,
    val mediaMinWidthPx: Int = -1,
    val mediaMaxWidthPx: Int = -1,
    val themeFilter: Int = 0
) {
    val subjectAtom: BlinkSelectorAtom?
        get() = selectorAtoms.lastOrNull()

    val subjectPseudoMask: Int
        get() = subjectAtom?.pseudoStateMask ?: 0

    fun matchesMediaAndTheme(viewportWidthPx: Int, isDarkTheme: Boolean): Boolean {
        if (mediaMinWidthPx >= 0 && viewportWidthPx < mediaMinWidthPx) return false
        if (mediaMaxWidthPx >= 0 && viewportWidthPx > mediaMaxWidthPx) return false
        if (themeFilter == THEME_LIGHT_ONLY && isDarkTheme) return false
        if (themeFilter == THEME_DARK_ONLY && !isDarkTheme) return false
        return true
    }

    fun toCompiledStyleRule(): CompiledStyleRule {
        val pseudoClass = when (subjectPseudoMask) {
            BlinkSelectorAtom.PSEUDO_HOVER -> "hover"
            BlinkSelectorAtom.PSEUDO_FOCUS -> "focus"
            BlinkSelectorAtom.PSEUDO_ACTIVE -> "active"
            BlinkSelectorAtom.PSEUDO_DISABLED -> "disabled"
            BlinkSelectorAtom.PSEUDO_CHECKED -> "checked"
            BlinkSelectorAtom.PSEUDO_ENABLED -> "enabled"
            else -> null
        }
        return CompiledStyleRule(
            selector = selector,
            segments = selectorAtoms.mapIndexed { idx, atom ->
                atom.toCompiledSegment(selectorAtoms.getOrNull(idx + 1)?.combinatorToLeft)
            },
            style = style,
            specificity = specificity,
            mediaMinWidthPx = if (mediaMinWidthPx >= 0) mediaMinWidthPx else null,
            mediaMaxWidthPx = if (mediaMaxWidthPx >= 0) mediaMaxWidthPx else null,
            pseudoClass = pseudoClass
        )
    }

    companion object {
        const val THEME_ANY: Int = 0
        const val THEME_LIGHT_ONLY: Int = 1
        const val THEME_DARK_ONLY: Int = 2

        fun fromCompiledStyleRule(rule: CompiledStyleRule, fallbackOrder: Int = 0): BlinkCompiledRule {
            val atoms = rule.segments.mapIndexed { idx, seg ->
                BlinkSelectorAtom.fromCompiledSegment(
                    seg = seg,
                    combinatorToLeft = rule.segments.getOrNull(idx - 1)?.combinatorToNext
                )
            }.toTypedArray()
            return BlinkCompiledRule(
                selector = rule.selector,
                selectorAtoms = atoms,
                properties = emptyArray(),
                style = rule.style,
                specificity = rule.specificity,
                sourceOrder = fallbackOrder,
                mediaMinWidthPx = rule.mediaMinWidthPx ?: -1,
                mediaMaxWidthPx = rule.mediaMaxWidthPx ?: -1,
                themeFilter = THEME_ANY
            )
        }
    }
}
