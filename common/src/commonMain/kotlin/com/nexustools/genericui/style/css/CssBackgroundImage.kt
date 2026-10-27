package com.nexustools.genericui.style.css

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.res.ResourceType
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.style.gradient.Gradient

/**
 * Strongly-typed CSS `background-image` value supporting [Gradient]s (`LinearGradient`, `RadialGradient`),
 * drawable [ResourceID]s (`R.drawable.*`), in-memory [ImageBuffer]s, URLs, and multi-layer stacks.
 */
data class CssBackgroundImage(
    val gradient: Gradient? = null,
    val resourceId: ResourceID? = null,
    val imageBuffer: ImageBuffer? = null,
    val urlOrPath: String? = null,
    val layers: List<CssBackgroundImage> = emptyList(),
    val isNone: Boolean = false
) : CssValue {

    fun resolveDrawable(): DrawableResource? {
        if (resourceId != null) {
            ResourceResolver.getDrawable(resourceId)?.let { return it }
        }
        if (!urlOrPath.isNullOrBlank()) {
            Resources.getDrawable(urlOrPath)?.let { return it }
        }
        return null
    }

    fun resolveImageBuffer(): ImageBuffer? {
        imageBuffer?.let { return it.asRgba8888() }
        if (resourceId != null) {
            ResourceResolver.getImageBuffer(resourceId)?.let { return it.asRgba8888() }
        }
        return null
    }

    override fun toCssString(): String {
        if (isNone) return "none"
        if (layers.isNotEmpty()) {
            return layers.joinToString(", ") { it.toCssString() }
        }
        gradient?.let { return it.toCssString() }
        if (!urlOrPath.isNullOrBlank()) {
            val trimmed = urlOrPath.trim()
            return if (trimmed.startsWith("url(", ignoreCase = true)) trimmed else "url('$trimmed')"
        }
        if (resourceId != null) {
            return "url('${ResourceResolver.getUrl(resourceId)}')"
        }
        return "none"
    }

    companion object {
        val NONE = CssBackgroundImage(isNone = true)

        fun fromGradient(gradient: Gradient): CssBackgroundImage =
            CssBackgroundImage(gradient = gradient)

        fun fromResource(resourceId: ResourceID): CssBackgroundImage =
            CssBackgroundImage(resourceId = resourceId, urlOrPath = ResourceResolver.getUrl(resourceId))

        fun fromImageBuffer(imageBuffer: ImageBuffer): CssBackgroundImage =
            CssBackgroundImage(imageBuffer = imageBuffer)

        fun fromUrl(urlOrPath: String): CssBackgroundImage =
            CssBackgroundImage(urlOrPath = urlOrPath)

        fun layered(vararg layers: CssBackgroundImage): CssBackgroundImage =
            CssBackgroundImage(layers = layers.toList())

        fun layered(layers: List<CssBackgroundImage>): CssBackgroundImage =
            CssBackgroundImage(layers = layers)

        fun parse(raw: String?): CssBackgroundImage? {
            val cleaned = raw
                ?.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            if (cleaned.equals("none", ignoreCase = true)) return NONE

            Gradient.fromCss(cleaned)?.let { return fromGradient(it) }

            val urlMatch = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE).find(cleaned)
            if (urlMatch != null) {
                val extracted = urlMatch.groupValues[1].trim()
                val resId = resolveDrawableId(extracted)
                return CssBackgroundImage(resourceId = resId, urlOrPath = extracted)
            }

            if (cleaned.startsWith("@drawable/") || cleaned.startsWith("res://drawable/")) {
                val resId = resolveDrawableId(cleaned)
                return CssBackgroundImage(resourceId = resId, urlOrPath = resId?.let { ResourceResolver.getUrl(it) } ?: cleaned)
            }

            return null
        }

        private fun resolveDrawableId(ref: String): ResourceID? {
            Resources.getDrawable(ref)?.let { return it.resId }
            val name = ref
                .removePrefix("res://drawable/")
                .removePrefix("@drawable/")
                .substringAfterLast('/')
                .substringBeforeLast('.')
            return Resources.activeTable.findResourceId(ResourceType.DRAWABLE, name)
        }
    }
}
