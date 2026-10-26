package com.nexustools.genericui.plugin

/**
 * Immutable snapshot of resolved application metadata, window geometry, theme settings, and icon
 * path for a specific [targetScope] after merging `genericui { app { ... } }`, `application { ... }`,
 * and all matching [GenericUiVariantConfig] blocks along the specificity chain.
 */
data class GenericUiResolvedVariantConfig(
    val targetScope: GenericUiVariantScope = GenericUiVariantScope.COMMON,
    val appId: String = "com.nexustools.genericui.app",
    val appName: String = "GenericUI",
    val appVersion: String = "1.0.0",
    val packageName: String = "",
    val description: String = "",
    val vendor: String = "NexusTools",
    val windowTitle: String = appName,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 800,
    val minWindowWidth: Int = 320,
    val minWindowHeight: Int = 240,
    val windowResizable: Boolean = true,
    val fullscreen: Boolean = false,
    val themeMode: String = "dark",
    val themeColor: String = "#0d6efd",
    val iconPath: String? = null,
    val extensionOverrides: Map<String, Boolean> = emptyMap()
)
