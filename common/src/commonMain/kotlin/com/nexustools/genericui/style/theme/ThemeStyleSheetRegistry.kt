package com.nexustools.genericui.style.theme

import com.nexustools.genericui.style.css.CssStyleSheet

/**
 * Runtime registry for compiled consumer/extension theme stylesheets (`theme.scss`).
 *
 * Prebuilt GenericUI libraries (`:common`, `:extensions:*`, `:system`) contain zero bundled
 * compiled `theme.scss` data; consumer applications register their build-time compiled theme
 * stylesheet (e.g., `GenericUiGeneratedThemeStyleSheet`) here during launch, and sheets are
 * returned in deterministic cascade order:
 * `core style -> core theme -> extension style -> extension theme -> app style -> app theme`.
 */
object ThemeStyleSheetRegistry {
    private data class RegisteredThemeEntry(
        val moduleId: String,
        val styleSheet: CssStyleSheet,
        val layerOrder: Int,
        val sequence: Long
    )

    private val entriesByModuleId = linkedMapOf<String, RegisteredThemeEntry>()
    private var nextSequence: Long = 0L

    internal var registryVersion: Long = 0L
        private set

    internal var onThemeStyleSheetsChanged: (() -> Unit)? = null

    /**
     * Registers or replaces the compiled theme [styleSheet] for [moduleId].
     */
    fun registerCompiledThemeStyleSheet(
        moduleId: String,
        styleSheet: CssStyleSheet,
        layerOrder: Int = inferLayerOrder(moduleId)
    ) {
        val key = moduleId.trim().ifEmpty { "app:theme" }
        val existing = entriesByModuleId[key]
        val seq = existing?.sequence ?: nextSequence++
        entriesByModuleId[key] = RegisteredThemeEntry(
            moduleId = key,
            styleSheet = styleSheet,
            layerOrder = layerOrder,
            sequence = seq
        )
        registryVersion++
        onThemeStyleSheetsChanged?.invoke()
    }

    /**
     * Removes a previously registered compiled theme stylesheet by [moduleId].
     */
    fun unregisterCompiledThemeStyleSheet(moduleId: String): Boolean {
        val key = moduleId.trim()
        val removed = entriesByModuleId.remove(key) != null
        if (removed) {
            registryVersion++
            onThemeStyleSheetsChanged?.invoke()
        }
        return removed
    }

    /**
     * Returns all registered compiled theme stylesheets in deterministic cascade order.
     */
    fun getCompiledThemeStyleSheets(): List<CssStyleSheet> =
        entriesByModuleId.values
            .sortedWith(compareBy<RegisteredThemeEntry> { it.layerOrder }.thenBy { it.sequence })
            .map { it.styleSheet }

    fun getCompiledThemeEntries(): List<Pair<String, CssStyleSheet>> =
        entriesByModuleId.values
            .sortedWith(compareBy<RegisteredThemeEntry> { it.layerOrder }.thenBy { it.sequence })
            .map { it.moduleId to it.styleSheet }

    internal fun getCompiledThemeLayers(): List<Pair<Int, CssStyleSheet>> =
        entriesByModuleId.values
            .sortedWith(compareBy<RegisteredThemeEntry> { it.layerOrder }.thenBy { it.sequence })
            .map { it.layerOrder to it.styleSheet }

    fun clear() {
        if (entriesByModuleId.isEmpty()) return
        entriesByModuleId.clear()
        registryVersion++
        onThemeStyleSheetsChanged?.invoke()
    }

    internal fun inferLayerOrder(moduleId: String): Int {
        val norm = moduleId.trim().lowercase()
        val isTheme = norm.contains("theme")
        val isStyle = norm.contains("style") && !isTheme
        val isExtension = norm.contains("extension") ||
            norm.contains("ext:") ||
            norm.contains("ext/") ||
            norm.startsWith("ext-")
        val isCore = norm.contains("common") ||
            norm.contains("core") ||
            norm.startsWith("genericui")

        return when {
            isCore && isStyle -> 100
            isCore -> 200
            isExtension && isStyle -> 300
            isExtension -> 400
            isStyle -> 500
            else -> 600
        }
    }
}
