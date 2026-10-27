package com.nexustools.genericui.style.css

/**
 * Precompiled CSS rule holding pre-parsed selector segments, specificity, media query bounds,
 * and a strongly-typed [ComponentStyle] block.
 */
data class CompiledStyleRule(
    val selector: String,
    val segments: List<CompiledSelectorSegment> = CompiledSelectorSegment.parseChain(selector),
    val style: ComponentStyle = ComponentStyle(),
    val declarations: Map<String, String> = emptyMap(),
    val specificity: Int = CssRule.computeSpecificity(selector),
    val pseudoClass: String? = segments.lastOrNull()?.pseudoClass,
    val mediaMinWidthPx: Int? = null,
    val mediaMaxWidthPx: Int? = null
) {
    fun toCssRule(): CssRule {
        val mergedDecls = linkedMapOf<String, String>()
        mergedDecls.putAll(declarations)
        for ((prop, pair) in style.toCssPropertyMap()) {
            val (value, isImp) = pair
            mergedDecls[prop] = if (isImp) "$value !important" else value
        }
        return CssRule(
            selector = selector,
            declarations = mergedDecls,
            mediaMinWidthPx = mediaMinWidthPx,
            mediaMaxWidthPx = mediaMaxWidthPx,
            specificity = specificity
        )
    }
}
