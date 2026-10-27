package com.nexustools.genericui.resources

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Unified cross-platform resource resolver delegating to the active backend's
 * [ResourceResolverNativeImpl] and [Resources.activeTable].
 */
object ResourceResolver {
    private val fallbackResolver: ResourceResolverNativeImpl = DefaultResourceResolverNativeImpl()

    /** Optional explicit override resolver for tests or custom host environments. */
    var customResolver: ResourceResolverNativeImpl? = null

    private fun currentResolver(): ResourceResolverNativeImpl =
        customResolver ?: GenericUI.activeFactory?.createResourceResolver() ?: fallbackResolver

    fun getString(resId: ResourceID, vararg formatArgs: Any): String {
        val raw = currentResolver().resolveString(resId) ?: Resources.activeTable.getString(resId)
        if (formatArgs.isEmpty()) return raw
        var result = raw
        formatArgs.forEachIndexed { index, arg ->
            result = result
                .replace("%${index + 1}\$s", arg.toString())
                .replace("%${index + 1}\$d", arg.toString())
                .replaceFirst("%s", arg.toString())
                .replaceFirst("%d", arg.toString())
        }
        return result
    }

    fun getColor(resId: ResourceID): RgbaColor =
        currentResolver().resolveColor(resId) ?: Resources.activeTable.getColor(resId)

    fun getDimenPx(resId: ResourceID): Float =
        currentResolver().resolveDimenPx(resId) ?: Resources.activeTable.getDimen(resId)

    fun getInteger(resId: ResourceID): Int =
        Resources.activeTable.getInteger(resId)

    fun getBool(resId: ResourceID): Boolean =
        Resources.activeTable.getBool(resId)

    fun getUrl(resId: ResourceID): String =
        currentResolver().resolveUrl(resId)

    fun getBytes(resId: ResourceID): ByteArray? =
        currentResolver().resolveBytes(resId) ?: Resources.activeTable.getRawBytes(resId)

    fun getImageBuffer(resId: ResourceID): ImageBuffer? {
        val bytes = getBytes(resId) ?: return null
        if (bytes.isEmpty()) return null
        return ImageLoader.loadFromBytes(bytes)
    }

    fun getDrawable(resId: ResourceID): DrawableResource? =
        Resources.activeTable.getDrawable(resId)

    fun getStyleSheet(resId: ResourceID): com.nexustools.genericui.style.css.PrecompiledStyleSheet? =
        Resources.activeTable.getStyleSheet(resId)
}
