package com.nexustools.genericui.plugin.spi

/**
 * Android Activity, Application, manifest, and generated code contributions from an extension.
 */
data class AndroidExtensionContribution(
    val manifestContribution: AndroidManifestContribution = AndroidManifestContribution(),
    val generatedSources: List<ExtensionGeneratedSource> = emptyList(),
    val activityProperties: List<String> = emptyList(),
    val activityOnCreateAfterSuperStatements: List<String> = emptyList(),
    val activityOnDestroyBeforeSuperStatements: List<String> = emptyList(),
    val activityMethods: List<String> = emptyList(),
    val activityImports: List<String> = emptyList(),
    val applicationOnCreateStatements: List<String> = emptyList(),
    val applicationImports: List<String> = emptyList(),
    val providerInstallerCalls: List<String> = emptyList()
)
