package com.nexustools.genericui.style.css

import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID

/**
 * Global registry of precompiled stylesheets ([PrecompiledStyleSheet]) indexed by logical name,
 * virtual resource path (`styles/app.scss`, `/styles/app.css`), and optional [ResourceID] (`R.style.*`).
 */
object StyleSheetRegistry {
    private val byName = linkedMapOf<String, PrecompiledStyleSheet>()
    private val byPath = linkedMapOf<String, PrecompiledStyleSheet>()
    private val byResourceId = linkedMapOf<Int, PrecompiledStyleSheet>()

    fun register(
        name: String,
        path: String,
        sheet: PrecompiledStyleSheet
    ) {
        register(name, path, null, sheet)
    }

    fun register(
        name: String,
        path: String,
        resourceId: ResourceID?,
        sheet: PrecompiledStyleSheet
    ) {
        val normalizedName = name.trim()
        if (normalizedName.isNotEmpty()) {
            byName[normalizedName] = sheet
            byName[normalizedName.lowercase()] = sheet
        }
        for (candidate in normalizePathVariants(path, sheet.cssResourcePath, normalizedName)) {
            byPath[candidate] = sheet
        }
        if (resourceId != null) {
            byResourceId[resourceId.id] = sheet
        }
    }

    fun register(sheet: PrecompiledStyleSheet) {
        register(sheet.name, sheet.sourcePath, null, sheet)
    }

    fun register(sheet: PrecompiledStyleSheet, resourceId: ResourceID?) {
        register(sheet.name, sheet.sourcePath, resourceId, sheet)
    }

    fun get(nameOrPath: String): PrecompiledStyleSheet? =
        resolveByPath(nameOrPath) ?: byName[nameOrPath.trim()] ?: byName[nameOrPath.trim().lowercase()]

    fun resolveByPath(path: String): PrecompiledStyleSheet? {
        val clean = normalizePath(path)
        byPath[clean]?.let { return it }
        byName[clean]?.let { return it }
        val baseName = clean.substringAfterLast('/').substringBeforeLast('.')
        if (baseName.isNotEmpty()) {
            byName[baseName]?.let { return it }
            byName[baseName.lowercase()]?.let { return it }
        }
        return null
    }

    fun resolveByResourceId(resourceId: ResourceID): PrecompiledStyleSheet? {
        byResourceId[resourceId.id]?.let { return it }
        val registered = Resources.activeTable.findResourceId(resourceId.id) ?: resourceId
        if (registered.relativePath.isNotBlank()) {
            resolveByPath(registered.relativePath)?.let { return it }
        }
        if (registered.name.isNotBlank()) {
            resolveByPath(registered.name)?.let { return it }
        }
        return null
    }

    fun all(): List<PrecompiledStyleSheet> = byName.values.distinct()

    fun clear() {
        byName.clear()
        byPath.clear()
        byResourceId.clear()
    }

    fun normalizePath(raw: String): String =
        raw.trim().replace('\\', '/').removePrefix("/").lowercase()

    private fun normalizePathVariants(vararg paths: String): Set<String> {
        val result = linkedSetOf<String>()
        for (raw in paths) {
            val clean = normalizePath(raw)
            if (clean.isEmpty()) continue
            result.add(clean)
            result.add("/$clean")
            val withoutExt = clean.substringBeforeLast('.')
            if (withoutExt.isNotEmpty()) {
                result.add(withoutExt)
                result.add("$withoutExt.css")
                result.add("$withoutExt.scss")
                result.add("$withoutExt.sass")
                result.add("styles/${withoutExt.substringAfterLast('/')}.css")
                result.add("styles/${withoutExt.substringAfterLast('/')}.scss")
                result.add("styles/${withoutExt.substringAfterLast('/')}.sass")
            }
        }
        return result
    }
}
