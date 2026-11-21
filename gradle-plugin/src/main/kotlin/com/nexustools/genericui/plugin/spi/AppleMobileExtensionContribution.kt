package com.nexustools.genericui.plugin.spi

/**
 * Apple mobile (iOS & iPadOS) `Info.plist`, background modes, NSUserActivity types,
 * and generated code contributions from a [GenericUiExtensionContributor].
 */
data class AppleMobileExtensionContribution(
    val backgroundModes: List<String> = emptyList(),
    val userActivityTypes: List<String> = emptyList(),
    val infoPlistEntries: Map<String, Any> = emptyMap(),
    val generatedSources: List<ExtensionGeneratedSource> = emptyList(),
    val providerInstallerCalls: List<String> = emptyList()
)
