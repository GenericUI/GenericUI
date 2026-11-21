package com.nexustools.genericui.style.css

import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType

/**
 * Global registry of precompiled stylesheets ([PrecompiledStyleSheet]) indexed by scoped canonical
 * URI/path (`res://gui/...`, `res://gui/ext/<cat>/<ext>/...`, `res://app/...`, `gui/...`, `app/...`),
 * optional [ResourceID] (`R.style.*`), and non-conflicting fallback short name.
 */
object StyleSheetRegistry {
    private val byName = linkedMapOf<String, PrecompiledStyleSheet>()
    private val byPath = linkedMapOf<String, PrecompiledStyleSheet>()
    private val byCanonicalUri = linkedMapOf<String, PrecompiledStyleSheet>()
    private val byResourceId = linkedMapOf<Int, PrecompiledStyleSheet>()

    var onStyleSheetRegistered: ((PrecompiledStyleSheet) -> Unit)? = null

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
        val isAppOrUnscoped = resourceId == null || resourceId.moduleByte == ResourceType.DEFAULT_APP_MODULE_BYTE
        if (normalizedName.isNotEmpty()) {
            if (isAppOrUnscoped || normalizedName !in byName) {
                byName[normalizedName] = sheet
            }
            val lower = normalizedName.lowercase()
            if (isAppOrUnscoped || lower !in byName) {
                byName[lower] = sheet
            }
        }
        for (candidate in normalizePathVariants(path, sheet.cssResourcePath, normalizedName)) {
            if (isAppOrUnscoped || candidate.startsWith("gui/") || candidate.startsWith("/gui/") || candidate.startsWith("app/") || candidate.startsWith("/app/") || candidate !in byPath) {
                byPath[candidate] = sheet
            }
        }
        if (resourceId != null) {
            byResourceId[resourceId.id] = sheet
            byCanonicalUri[resourceId.toResourceUri().lowercase()] = sheet
            byCanonicalUri[resourceId.canonicalStem.lowercase()] = sheet
            byCanonicalUri[resourceId.canonicalRelativePath.lowercase()] = sheet
            byCanonicalUri[resourceId.flatName.lowercase()] = sheet
        }
        onStyleSheetRegistered?.invoke(sheet)
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
        byCanonicalUri[clean]?.let { return it }
        if (clean.startsWith("res://")) {
            val trimmed = clean.removePrefix("res://").removePrefix("/")
            byCanonicalUri[trimmed]?.let { return it }
            byPath[trimmed]?.let { return it }
        }
        byPath[clean]?.let { return it }
        byName[clean]?.let { return it }
        val baseName = clean.substringAfterLast('/').substringBeforeLast('.')
        if (baseName.isNotEmpty() && !clean.startsWith("gui/ext/") && !clean.startsWith("res://gui/ext/")) {
            byName[baseName]?.let { return it }
            byName[baseName.lowercase()]?.let { return it }
        }
        return null
    }

    fun resolveByResourceId(resourceId: ResourceID): PrecompiledStyleSheet? {
        byResourceId[resourceId.id]?.let { return it }
        byCanonicalUri[resourceId.toResourceUri().lowercase()]?.let { return it }
        val registered = Resources.activeTable.findResourceId(resourceId.id)
            ?: Resources.activeTable.findResourceId(resourceId.toResourceUri())
            ?: resourceId
        if (registered !== resourceId) {
            byResourceId[registered.id]?.let { return it }
        }
        if (registered.relativePath.isNotBlank()) {
            resolveByPath(registered.relativePath)?.let { return it }
        }
        if (registered.name.isNotBlank() && registered.moduleByte == ResourceType.DEFAULT_APP_MODULE_BYTE) {
            resolveByPath(registered.name)?.let { return it }
        }
        return null
    }

    fun all(): List<PrecompiledStyleSheet> = (byResourceId.values + byPath.values + byName.values).distinct()

    fun clear() {
        byName.clear()
        byPath.clear()
        byCanonicalUri.clear()
        byResourceId.clear()
        Resources.ensureBuiltInResourcesRegistered()
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
