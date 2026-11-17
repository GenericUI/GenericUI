package com.nexustools.genericui.plugin

/**
 * Resolves a [GenericUiResolvedVariantConfig] for any [GenericUiVariantScope] by merging
 * `extension.app`, `extension.application` (`desktop`), `extension.resources`, top-level
 * `window`/`theme` blocks, and every [GenericUiVariantConfig] along `COMMON -> ... -> targetScope`.
 */
object GenericUiVariantResolver {

    fun resolve(
        extension: GenericUiExtension,
        targetScope: GenericUiVariantScope
    ): GenericUiResolvedVariantConfig {
        val desktop = extension.application.desktop
        val resources = extension.resources
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
        var windowAlwaysOnTop: Boolean = false
        var windowAspectRatio: Double? = null
        var themeMode: String = if (desktop.darkTitleBarExplicitlySet) {
            if (desktop.darkTitleBar) "dark" else "light"
        } else {
            "auto"
        }
        var themeColor: String = desktop.themeColor.trim().ifEmpty { "#0d6efd" }
        val mergedTheme = GenericUiThemeConfig()
        if (themeColor != GenericUiThemeConfig.DEFAULT_PRIMARY_COLOR) {
            mergedTheme.primaryColor = themeColor
        }
        var genericIconPath: String? = resolveLegacyGenericIconForTarget(desktop, resources, targetScope)
        var globalStyleSheet: String? = extension.styles.globalStyleSheet?.trim()?.takeIf { it.isNotEmpty() }
            ?: extension.themeConfig.styleSheet?.trim()?.takeIf { it.isNotEmpty() }
            ?: app.styleSheet?.trim()?.takeIf { it.isNotEmpty() }
        val mergedExtensionOverrides = linkedMapOf<String, Boolean>()

        // Apply top-level genericui { window { ... } } and genericui { theme { ... } }
        applyWindowConfig(extension.windowConfig) { t, w, h, mw, mh, r, fs, aot, ar, ic ->
            if (t != null) windowTitle = t
            if (w != null) windowWidth = w
            if (h != null) windowHeight = h
            if (mw != null) minWindowWidth = mw
            if (mh != null) minWindowHeight = mh
            if (r != null) windowResizable = r
            if (fs != null) fullscreen = fs
            if (aot != null) windowAlwaysOnTop = aot
            if (ar != null) windowAspectRatio = ar
            if (ic != null) genericIconPath = ic
        }
        mergedTheme.applyOverridesFrom(extension.themeConfig)
        applyThemeConfig(extension.themeConfig) { m, c, ss ->
            if (m != null) themeMode = m
            if (c != null) themeColor = c
            if (ss != null) globalStyleSheet = ss
        }

        val appleOnlyScopes = setOf(
            GenericUiVariantScope.APPLE_COMMON,
            GenericUiVariantScope.IOS_COMMON,
            GenericUiVariantScope.MACOS,
            GenericUiVariantScope.IOS,
            GenericUiVariantScope.IPADOS
        )

        // Cascade along specificity chain from COMMON (rank 0) up to targetScope (rank 6)
        val chain = GenericUiVariantScope.chainFromCommonToTarget(targetScope)
        for (scope in chain) {
            val variant = extension.variantConfigFor(scope)
            variant.title?.takeIf { it.isNotBlank() }?.let { windowTitle = it }
            if (scope !in appleOnlyScopes) {
                variant.icon?.takeIf { it.isNotBlank() }?.let { genericIconPath = it }
            }
            variant.themeColor?.takeIf { it.isNotBlank() }?.let {
                themeColor = it
                mergedTheme.primaryColor = it.trim()
            }
            variant.styleSheet?.trim()?.takeIf { it.isNotEmpty() }?.let { globalStyleSheet = it }
            applyWindowConfig(variant.window) { t, w, h, mw, mh, r, fs, aot, ar, ic ->
                if (t != null) windowTitle = t
                if (w != null) windowWidth = w
                if (h != null) windowHeight = h
                if (mw != null) minWindowWidth = mw
                if (mh != null) minWindowHeight = mh
                if (r != null) windowResizable = r
                if (fs != null) fullscreen = fs
                if (aot != null) windowAlwaysOnTop = aot
                if (ar != null) windowAspectRatio = ar
                if (ic != null && scope !in appleOnlyScopes) {
                    genericIconPath = ic
                }
            }
            mergedTheme.applyOverridesFrom(variant.theme)
            applyThemeConfig(variant.theme) { m, c, ss ->
                if (m != null) themeMode = m
                if (c != null) themeColor = c
                if (ss != null) globalStyleSheet = ss
            }
            mergedExtensionOverrides.putAll(variant.extensionOverrides)
        }

        val (resolvedIconPath, appleIconExplicitlyProvided) = resolveEffectiveIconAndExplicitFlag(
            extension = extension,
            desktop = desktop,
            resources = resources,
            targetScope = targetScope,
            genericIconFallback = genericIconPath
        )

        windowAspectRatio?.let { ratio ->
            require(ratio.isFinite() && ratio > 0.0) {
                "Window aspectRatio must be positive and finite for scope '${targetScope.id}', got $ratio."
            }
        }

        val resolvedLight = mergedTheme.resolveLightColors()
        val resolvedDark = mergedTheme.resolveDarkColors()
        if (themeColor == GenericUiThemeConfig.DEFAULT_PRIMARY_COLOR) {
            themeColor = resolvedLight[GenericUiThemeConfig.KEY_PRIMARY] ?: themeColor
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
            windowAlwaysOnTop = windowAlwaysOnTop,
            windowAspectRatio = windowAspectRatio,
            themeMode = themeMode,
            themeColor = themeColor,
            lightThemeColors = resolvedLight,
            darkThemeColors = resolvedDark,
            hasCustomThemeOverrides = mergedTheme.hasCustomThemeOverrides(),
            iconPath = resolvedIconPath,
            appleIconExplicitlyProvided = appleIconExplicitlyProvided,
            globalStyleSheet = globalStyleSheet,
            extensionOverrides = mergedExtensionOverrides
        )
    }

    private fun resolveEffectiveIconAndExplicitFlag(
        extension: GenericUiExtension,
        desktop: GenericUiDesktopConfig,
        resources: GenericUiResourcesConfig,
        targetScope: GenericUiVariantScope,
        genericIconFallback: String?
    ): Pair<String?, Boolean> {
        fun variantIconOrNull(scope: GenericUiVariantScope): String? {
            val v = extension.variantConfigFor(scope)
            return (v.window.icon?.takeIf { it.isNotBlank() } ?: v.icon?.takeIf { it.isNotBlank() })?.trim()
        }

        val targetIsApple = targetScope in setOf(
            GenericUiVariantScope.MACOS,
            GenericUiVariantScope.IOS,
            GenericUiVariantScope.IPADOS,
            GenericUiVariantScope.IOS_COMMON,
            GenericUiVariantScope.APPLE_COMMON
        )
        if (!targetIsApple) {
            return genericIconFallback to false
        }

        val targetSpecificIcon: String? = when (targetScope) {
            GenericUiVariantScope.MACOS ->
                variantIconOrNull(GenericUiVariantScope.MACOS)
                    ?: desktop.macIcon.trim().takeIf { it.isNotEmpty() }
                    ?: resources.macIcon.trim().takeIf { it.isNotEmpty() }
            GenericUiVariantScope.IOS ->
                variantIconOrNull(GenericUiVariantScope.IOS)
                    ?: desktop.iosIcon.trim().takeIf { it.isNotEmpty() }
                    ?: resources.iosIcon.trim().takeIf { it.isNotEmpty() }
            GenericUiVariantScope.IPADOS ->
                variantIconOrNull(GenericUiVariantScope.IPADOS)
                    ?: desktop.ipadosIcon.trim().takeIf { it.isNotEmpty() }
                    ?: resources.ipadosIcon.trim().takeIf { it.isNotEmpty() }
            else -> null
        }
        if (targetSpecificIcon != null) {
            return targetSpecificIcon to true
        }

        val iosFamilyShared = if (targetScope in setOf(
                GenericUiVariantScope.IOS,
                GenericUiVariantScope.IPADOS,
                GenericUiVariantScope.IOS_COMMON
            )
        ) {
            variantIconOrNull(GenericUiVariantScope.IOS_COMMON)
        } else {
            null
        }
        val sharedAppleIcon = iosFamilyShared
            ?: variantIconOrNull(GenericUiVariantScope.APPLE_COMMON)
            ?: desktop.appleIcon.trim().takeIf { it.isNotEmpty() }
            ?: resources.appleIcon.trim().takeIf { it.isNotEmpty() }
        if (sharedAppleIcon != null) {
            return sharedAppleIcon to true
        }

        return genericIconFallback to false
    }

    private fun resolveLegacyGenericIconForTarget(
        desktop: GenericUiDesktopConfig,
        resources: GenericUiResourcesConfig,
        targetScope: GenericUiVariantScope
    ): String? {
        val candidate = when (targetScope) {
            GenericUiVariantScope.WINDOWS ->
                desktop.windowsIcon.ifBlank { desktop.icon }.ifBlank { resources.icon }
            GenericUiVariantScope.GTK, GenericUiVariantScope.QT, GenericUiVariantScope.LINUX_COMMON ->
                desktop.linuxIcon.ifBlank { desktop.icon }.ifBlank { resources.icon }
            GenericUiVariantScope.WEB ->
                desktop.webIcon.ifBlank { desktop.icon }.ifBlank { resources.icon }
            else ->
                desktop.icon.ifBlank { resources.icon }
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
            alwaysOnTop: Boolean?,
            aspectRatio: Double?,
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
            window.alwaysOnTop,
            window.aspectRatio,
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
