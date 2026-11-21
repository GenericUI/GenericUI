package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiAndroidMetaDataSpec

/**
 * Android manifest additions (permissions, services, receivers, providers, application meta-data,
 * and activity configChanges / PiP flags) contributed by a [GenericUiExtensionContributor].
 */
data class AndroidManifestContribution(
    val permissions: List<String> = emptyList(),
    val services: List<AndroidServiceContribution> = emptyList(),
    val receivers: List<AndroidReceiverContribution> = emptyList(),
    val providers: List<AndroidProviderContribution> = emptyList(),
    val applicationMetaData: List<GenericUiAndroidMetaDataSpec> = emptyList(),
    val activityConfigChanges: List<String> = emptyList(),
    val supportsPictureInPicture: Boolean = false
)
