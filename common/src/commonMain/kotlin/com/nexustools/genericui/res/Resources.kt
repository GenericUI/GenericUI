package com.nexustools.genericui.res

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.style.RgbaColor

/**
 * Global Android-like resource manager (`Resources`) providing unified access to compiled
 * `R` / `RC` resources across all GenericUI backends (`windows`, `macos`, `gtk`, `qt`,
 * `swing`, `android`, `ios`, `web`).
 */
object Resources {
    val activeTable: ResourceTable = ResourceTable()

    fun getString(resId: ResourceId): String = ResourceResolver.getString(resId)

    fun getString(resId: ResourceId, vararg formatArgs: Any): String =
        ResourceResolver.getString(resId, *formatArgs)

    fun getColor(resId: ResourceId): RgbaColor = ResourceResolver.getColor(resId)

    fun getDimen(resId: ResourceId): Float = ResourceResolver.getDimenPx(resId)

    fun getDimenPx(resId: ResourceId): Float = ResourceResolver.getDimenPx(resId)

    fun getInteger(resId: ResourceId): Int = activeTable.getInteger(resId)

    fun getBool(resId: ResourceId): Boolean = activeTable.getBool(resId)

    fun getDrawable(resId: ResourceId): DrawableResource? = activeTable.getDrawable(resId)

    fun getDrawable(iconOrUri: String): DrawableResource? =
        activeTable.getDrawableByName(iconOrUri)

    fun getRawText(resId: ResourceId): String = activeTable.getRawText(resId) ?: ""

    fun getRawBytes(resId: ResourceId): ByteArray? = activeTable.getRawBytes(resId)

    fun getBytes(resId: ResourceId): ByteArray? = ResourceResolver.getBytes(resId)

    fun getUrl(resId: ResourceId): String = ResourceResolver.getUrl(resId)

    fun getImageBuffer(resId: ResourceId): ImageBuffer? = ResourceResolver.getImageBuffer(resId)

    fun getFontPath(resId: ResourceId): String = activeTable.getFontPath(resId) ?: resId.relativePath

    fun getStyleSheet(resId: ResourceId): com.nexustools.genericui.style.css.PrecompiledStyleSheet? =
        activeTable.getStyleSheet(resId)

    /**
     * Resolves a string that may be a `@string/name` or `res://string/name` reference.
     */
    fun resolveStringReference(textOrRef: String): String {
        if (textOrRef.startsWith("@string/") || textOrRef.startsWith("res://string/")) {
            return activeTable.getStringByName(textOrRef) ?: textOrRef
        }
        return textOrRef
    }
}
