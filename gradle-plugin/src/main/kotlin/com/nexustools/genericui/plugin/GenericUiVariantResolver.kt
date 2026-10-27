package com.nexustools.genericui.plugin

/**
 * Resolves a [GenericUiResolvedVariantConfig] for any [GenericUiVariantScope] by merging
 * `extension.app`, `extension.application` (`desktop`), top-level `window`/`theme` blocks,
 * and every [GenericUiVariantConfig] along `COMMON -> ... -> targetScope`.
 */
object GenericUiVariantResolver {

    fun resolve(
        extension: GenericUiExtension,
        targetScope: GenericUiVariantScope
    ): GenericUiResolvedVariantConfig {
        val desktop = extension.application.desktop
        val app = extension.app

        val resolvedAppId = app.id.trim().ifEmpty {
            desktop.appId.trim().ifEmpty {
                extension.application.androidNamespace.trim().ifEmpty { "com.nexustools.genericui.app" }
            }
        }
        val resolvedAppName = app.name.trim().ifEmpty {
            desktop.appName.trim().ifEmpty {
                extension.application.baseName.trim().ifEmpty { "GenericUI" }
            }
        }
        val resolvedAppVersion = when {
            app.versionExplicitlySet && app.version.isNotBlank() -> app.version.trim()
            desktop.appVersion.isNotBlank() -> desktop.appVersion.trim()
            app.version.isNotBlank() -> app.version.trim()
            else -> "1.0.0"
        }
        val resolvedPackageName = app.packageName.trim().ifEmpty {
            extension.application.androidNamespace.trim().ifEmpty { resolvedAppId }
        }
        val resolvedDescription = app.description.trim().ifEmpty { desktop.description.trim() }
        val resolvedVendor = app.vendor.trim().ifEmpty { desktop.vendor.trim().ifEmpty { "NexusTools" } }

        var windowTitle: String = desktop.windowTitle.trim().ifEmpty { resolvedAppName }
        var windowWidth: Int = desktop.windowWidth
        var windowHeight: Int = desktop.windowHeight
        var minWindowWidth: Int = desktop.minWindowWidth
        var minWindowHeight: Int = desktop.minWindowHeight
        var windowResizable: Boolean = desktop.resizable
        var fullscreen: Boolean = false
        var themeMode: String = if (desktop.darkTitleBarExplicitlySet) {
            if (desktop.darkTitleBar) "dark" else "light"
        } else {
            "auto"
        }
        var themeColor: String = desktop.themeColor.trim().ifEmpty { "#0d6efd" }
        var iconPath: String? = resolveLegacyIconForTarget(desktop, targetScope)
        var globalStyleSheet: String? = extension.styles.globalStyleSheet?.trim()?.takeIf { it.isNotEmpty() }
            ?: extension.themeConfig.styleSheet?.trim()?.takeIf { it.isNotEmpty() }
            ?: app.styleSheet?.trim()?.takeIf { it.isNotEmpty() }
        val mergedExtensionOverrides = linkedMapOf<String, Boolean>()

        // Apply top-level genericui { window { ... } } and genericui { theme { ... } }
        applyWindowConfig(extension.windowConfig) { t, w, h, mw, mh, r, fs, ic ->
            if (t != null) windowTitle = t
            if (w != null) windowWidth = w
            if (h != null) windowHeight = h
            if (mw != null) minWindowWidth = mw
            if (mh != null) minWindowHeight = mh
            if (r != null) windowResizable = r
            if (fs != null) fullscreen = fs
            if (ic != null) iconPath = ic
        }
        applyThemeConfig(extension.themeConfig) { m, c, ss ->
            if (m != null) themeMode = m
            if (c != null) themeColor = c
            if (ss != null) globalStyleSheet = ss
        }

        // Cascade along specificity chain from COMMON (rank 0) up to targetScope (rank 5)
        val chain = GenericUiVariantScope.chainFromCommonToTarget(targetScope)
        for (scope in chain) {
            val variant = extension.variantConfigFor(scope)
            variant.title?.takeIf { it.isNotBlank() }?.let { windowTitle = it }
            variant.icon?.takeIf { it.isNotBlank() }?.let { iconPath = it }
            variant.themeColor?.takeIf { it.isNotBlank() }?.let { themeColor = it }
            variant.styleSheet?.trim()?.takeIf { it.isNotEmpty() }?.let { globalStyleSheet = it }
            applyWindowConfig(variant.window) { t, w, h, mw, mh, r, fs, ic ->
                if (t != null) windowTitle = t
                if (w != null) windowWidth = w
                if (h != null) windowHeight = h
                if (mw != null) minWindowWidth = mw
                if (mh != null) minWindowHeight = mh
                if (r != null) windowResizable = r
                if (fs != null) fullscreen = fs
                if (ic != null) iconPath = ic
            }
            applyThemeConfig(variant.theme) { m, c, ss ->
                if (m != null) themeMode = m
                if (c != null) themeColor = c
                if (ss != null) globalStyleSheet = ss
            }
            mergedExtensionOverrides.putAll(variant.extensionOverrides)
        }

        return GenericUiResolvedVariantConfig(
            targetScope = targetScope,
            appId = resolvedAppId,
            appName = resolvedAppName,
            appVersion = resolvedAppVersion,
            packageName = resolvedPackageName,
            description = resolvedDescription,
            vendor = resolvedVendor,
            windowTitle = windowTitle,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            minWindowWidth = minWindowWidth,
            minWindowHeight = minWindowHeight,
            windowResizable = windowResizable,
            fullscreen = fullscreen,
            themeMode = themeMode,
            themeColor = themeColor,
            iconPath = iconPath,
            globalStyleSheet = globalStyleSheet,
            extensionOverrides = mergedExtensionOverrides
        )
    }

    private fun resolveLegacyIconForTarget(
        desktop: GenericUiDesktopConfig,
        targetScope: GenericUiVariantScope
    ): String? {
        val candidate = when (targetScope) {
            GenericUiVariantScope.WINDOWS -> desktop.windowsIcon.ifBlank { desktop.icon }
            GenericUiVariantScope.MACOS -> desktop.macIcon.ifBlank { desktop.icon }
            GenericUiVariantScope.GTK, GenericUiVariantScope.QT, GenericUiVariantScope.LINUX_COMMON ->
                desktop.linuxIcon.ifBlank { desktop.icon }
            GenericUiVariantScope.WEB -> desktop.webIcon.ifBlank { desktop.icon }
            else -> desktop.icon
        }
        return candidate.trim().takeIf { it.isNotEmpty() }
    }

    private inline fun applyWindowConfig(
        window: GenericUiWindowConfig,
        consumer: (
            title: String?,
            width: Int?,
            height: Int?,
            minWidth: Int?,
            minHeight: Int?,
            resizable: Boolean?,
            fullscreen: Boolean?,
            icon: String?
        ) -> Unit
    ) {
        consumer(
            window.title?.takeIf { it.isNotBlank() },
            window.width,
            window.height,
            window.minWidth,
            window.minHeight,
            window.resizable,
            window.fullscreen,
            window.icon?.takeIf { it.isNotBlank() }
        )
    }

    private inline fun applyThemeConfig(
        theme: GenericUiThemeConfig,
        consumer: (mode: String?, color: String?, styleSheet: String?) -> Unit
    ) {
        consumer(
            if (theme.modeExplicitlySet) theme.mode.takeIf { it.isNotBlank() } else null,
            theme.color?.takeIf { it.isNotBlank() },
            theme.styleSheet?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}
