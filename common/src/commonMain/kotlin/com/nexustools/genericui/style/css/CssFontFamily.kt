package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.css.CssGenericFontFamily
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.font.FontRegistry

/**
 * Strongly-typed CSS `font-family` stack supporting [ResourceID] (`R.font.*`),
 * [CssGenericFontFamily] keywords, and named font families with fallbacks.
 */
data class CssFontFamily(
    val resourceId: ResourceID? = null,
    val genericFamily: CssGenericFontFamily? = null,
    val familyName: String? = null,
    val fallbacks: List<CssGenericFontFamily> = emptyList()
) : CssValue {

    fun resolvedPrimaryName(): String? {
        if (resourceId != null) {
            Resources.getFontPath(resourceId).takeIf { it.isNotBlank() }?.let { path ->
                val base = path.substringAfterLast('/').substringBeforeLast('.')
                if (base.isNotBlank()) return base
            }
            resourceId.name.takeIf { it.isNotBlank() }?.let { return it }
        }
        familyName?.takeIf { it.isNotBlank() }?.let { return it }
        return genericFamily?.toCssString()
    }

    override fun toCssString(): String {
        val parts = mutableListOf<String>()
        val primary = when {
            resourceId != null -> {
                val resolved = resolvedPrimaryName() ?: "font_${resourceId.id}"
                formatFamilyToken(resolved)
            }
            familyName != null && familyName.isNotBlank() -> formatFamilyToken(familyName)
            genericFamily != null -> genericFamily.toCssString()
            else -> CssGenericFontFamily.SANS_SERIF.toCssString()
        }
        parts.add(primary)
        for (fb in fallbacks) {
            val fbToken = fb.toCssString()
            if (fbToken != primary) {
                parts.add(fbToken)
            }
        }
        return parts.joinToString(", ")
    }

    companion object {
        fun fromResource(
            resourceId: ResourceID,
            vararg fallbacks: CssGenericFontFamily
        ): CssFontFamily = CssFontFamily(
            resourceId = resourceId,
            fallbacks = fallbacks.toList()
        )

        fun generic(family: CssGenericFontFamily): CssFontFamily =
            CssFontFamily(genericFamily = family)

        fun named(
            familyName: String,
            vararg fallbacks: CssGenericFontFamily
        ): CssFontFamily = CssFontFamily(
            familyName = familyName.trim().removeSurrounding("\"").removeSurrounding("'"),
            fallbacks = fallbacks.toList()
        )

        fun parse(raw: String?): CssFontFamily? {
            val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val tokens = cleaned.split(",").map {
                it.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null

            val first = tokens.first()
            val fallbacks = tokens.drop(1).mapNotNull { CssGenericFontFamily.fromCss(it) }
            val firstGeneric = CssGenericFontFamily.fromCss(first)
            return if (firstGeneric != null) {
                CssFontFamily(genericFamily = firstGeneric, fallbacks = fallbacks)
            } else {
                CssFontFamily(familyName = first, fallbacks = fallbacks)
            }
        }

        private fun formatFamilyToken(name: String): String {
            val clean = name.trim().removeSurrounding("\"").removeSurrounding("'")
            if (CssGenericFontFamily.fromCss(clean) != null) return clean.lowercase()
            return if (clean.any { it.isWhitespace() }) "\"$clean\"" else clean
        }
    }
}
