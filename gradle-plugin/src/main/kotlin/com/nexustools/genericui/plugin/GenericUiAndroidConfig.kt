package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * Android-specific variant configuration block (`genericui { android { ... } }`) extending
 * [GenericUiVariantConfig] with support for `manifest { ... }`, `services { ... }`,
 * `launcherIcon { ... }`, and per-variant overrides (`variant("development") { ... }`).
 */
open class GenericUiAndroidConfig : GenericUiVariantConfig(GenericUiVariantScope.ANDROID) {
    val manifestConfig: GenericUiAndroidManifestConfig = GenericUiAndroidManifestConfig()
    val servicesConfig: GenericUiAndroidServicesConfig = GenericUiAndroidServicesConfig()
    val launcherIconConfig: GenericUiAndroidLauncherIconConfig = GenericUiAndroidLauncherIconConfig()
    val variants: MutableMap<String, GenericUiAndroidVariantOverrideConfig> = linkedMapOf()
    var svgConverterVersion: String = GenericUiAndroidSvgConverter.DEFAULT_SDK_COMMON_VERSION
    var manifestMergerVersion: String = GenericUiAndroidManifestMergerRunner.DEFAULT_MERGER_VERSION
    var failOnSvgConversionWarnings: Boolean = false

    override fun manifest(action: Action<in GenericUiAndroidManifestConfig>) {
        action.execute(manifestConfig)
    }

    override fun services(action: Action<in GenericUiAndroidServicesConfig>) {
        action.execute(servicesConfig)
    }

    override fun launcherIcon(action: Action<in GenericUiAndroidLauncherIconConfig>) {
        action.execute(launcherIconConfig)
    }

    override fun variant(name: String, action: Action<in GenericUiAndroidVariantOverrideConfig>) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Android variant name must not be blank." }
        val spec = variants.getOrPut(trimmed) { GenericUiAndroidVariantOverrideConfig(trimmed) }
        action.execute(spec)
    }
}
