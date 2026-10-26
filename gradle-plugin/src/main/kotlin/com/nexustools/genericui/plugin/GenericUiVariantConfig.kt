package com.nexustools.genericui.plugin

import org.gradle.api.Action
import org.gradle.api.GradleException

/**
 * Per-scope configuration block (`common`, `systemCommon`, `desktopCommon`, `nativeDesktopCommon`,
 * `linuxCommon`, `mobileCommon`, `windows`, `macos`, `gtk`, `qt`, `swing`, `android`, `ios`, `web`).
 *
 * Values configured in more specific [GenericUiVariantScope] blocks override values from less
 * specific blocks along the specificity chain (`COMMON` -> ... -> concrete backend target).
 */
open class GenericUiVariantConfig(val scope: GenericUiVariantScope) {
    val window: GenericUiWindowConfig = GenericUiWindowConfig()
    val theme: GenericUiThemeConfig = GenericUiThemeConfig()
    var icon: String? = null
    var title: String? = null
    var themeColor: String? = null
    val extensionOverrides: MutableMap<String, Boolean> = linkedMapOf()

    /** Configures window geometry, title, resizability, fullscreen, and icon for this scope. */
    fun window(action: Action<in GenericUiWindowConfig>) {
        action.execute(window)
    }

    /** Configures theme mode and primary accent color for this scope. */
    fun theme(action: Action<in GenericUiThemeConfig>) {
        action.execute(theme)
    }

    /** Sets the icon path for this scope. */
    fun icon(path: String) {
        this.icon = path
    }

    /** Sets the primary theme color for this scope. */
    fun themeColor(color: String) {
        this.themeColor = color
    }

    /**
     * Android-only `services { ... }` block. Throws [GradleException] when invoked on any scope
     * other than [GenericUiVariantScope.ANDROID] (`genericui { android { services { ... } } }`).
     */
    open fun services(action: Action<in GenericUiAndroidServicesConfig>) {
        throw GradleException(
            "services { ... } is Android-only and can only be configured inside the " +
                "genericui { android { services { ... } } } block (attempted in scope '${scope.id}')."
        )
    }

    /**
     * Android-only `manifest { ... }` block. Throws [GradleException] when invoked on any scope
     * other than [GenericUiVariantScope.ANDROID] (`genericui { android { manifest { ... } } }`).
     */
    open fun manifest(action: Action<in GenericUiAndroidManifestConfig>) {
        throw GradleException(
            "manifest { ... } is Android-only and can only be configured inside the " +
                "genericui { android { manifest { ... } } } block (attempted in scope '${scope.id}')."
        )
    }
}
