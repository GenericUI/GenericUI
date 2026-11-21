package com.nexustools.genericui.plugin.spi

/**
 * Desktop (Windows, macOS, GTK, Qt, Swing) build, secondary binary, autostart,
 * and `Info.plist` contributions from a [GenericUiExtensionContributor].
 */
data class DesktopExtensionContribution(
    val secondaryExecutables: List<SecondaryDesktopExecutableContribution> = emptyList(),
    val autostartEntries: List<DesktopAutostartContribution> = emptyList(),
    val generatedSources: List<ExtensionGeneratedSource> = emptyList(),
    val providerInstallerCalls: List<String> = emptyList(),
    val macosInfoPlistEntries: Map<String, Any> = emptyMap()
)
