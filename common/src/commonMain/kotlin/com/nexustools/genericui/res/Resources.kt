package com.nexustools.genericui.res

import com.nexustools.genericui.style.RgbaColor

/**
 * Global Android-like resource manager (`Resources`) providing unified access to compiled
 * `R` / `RC` resources across all GenericUI backends (`windows`, `macos`, `gtk`, `qt`,
 * `swing`, `android`, `ios`, `web`).
 */
object Resources {
    val activeTable: ResourceTable = ResourceTable()

    fun getString(resId: ResourceId): String = activeTable.getString(resId)

    fun getString(resId: ResourceId, vararg formatArgs: Any): String {
        var result = activeTable.getString(resId)
        formatArgs.forEachIndexed { index, arg ->
            result = result
                .replace("%${index + 1}\$s", arg.toString())
                .replace("%${index + 1}\$d", arg.toString())
                .replaceFirst("%s", arg.toString())
                .replaceFirst("%d", arg.toString())
        }
        return result
    }

    fun getColor(resId: ResourceId): RgbaColor = activeTable.getColor(resId)

    fun getDimen(resId: ResourceId): Float = activeTable.getDimen(resId)

    fun getInteger(resId: ResourceId): Int = activeTable.getInteger(resId)

    fun getBool(resId: ResourceId): Boolean = activeTable.getBool(resId)

    fun getDrawable(resId: ResourceId): DrawableResource? = activeTable.getDrawable(resId)

    fun getDrawable(iconOrUri: String): DrawableResource? =
        activeTable.getDrawableByName(iconOrUri)

    fun getRawText(resId: ResourceId): String = activeTable.getRawText(resId) ?: ""

    fun getFontPath(resId: ResourceId): String = activeTable.getFontPath(resId) ?: resId.relativePath

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
