package com.nexustools.genericui.plugin

/**
 * Contribution returned by an [AndroidActivityExtensionContributor] for a discovered fragment,
 * augmenting both the generated `<FragmentName>Activity.kt` source file and the corresponding
 * `<activity>` entry in `AndroidManifest.xml`.
 */
data class AndroidActivityExtensionContribution(
    val imports: List<String> = emptyList(),
    val properties: List<String> = emptyList(),
    val onCreateAfterSuperStatements: List<String> = emptyList(),
    val onDestroyBeforeSuperStatements: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    val supportsPictureInPictureManifest: Boolean = false,
    val requiredConfigChanges: List<String> = emptyList()
)
