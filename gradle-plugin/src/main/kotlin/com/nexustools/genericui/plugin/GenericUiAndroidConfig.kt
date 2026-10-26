package com.nexustools.genericui.plugin

import org.gradle.api.Action

/**
 * Android-specific variant configuration block (`genericui { android { ... } }`) extending
 * [GenericUiVariantConfig] with support for `manifest { ... }` and `services { ... }`.
 */
open class GenericUiAndroidConfig : GenericUiVariantConfig(GenericUiVariantScope.ANDROID) {
    val manifestConfig: GenericUiAndroidManifestConfig = GenericUiAndroidManifestConfig()
    val servicesConfig: GenericUiAndroidServicesConfig = GenericUiAndroidServicesConfig()

    override fun manifest(action: Action<in GenericUiAndroidManifestConfig>) {
        action.execute(manifestConfig)
    }

    override fun services(action: Action<in GenericUiAndroidServicesConfig>) {
        action.execute(servicesConfig)
    }
}
