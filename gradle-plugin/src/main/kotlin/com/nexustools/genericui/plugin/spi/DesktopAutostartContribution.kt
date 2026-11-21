package com.nexustools.genericui.plugin.spi

/**
 * Declarative desktop OS autostart entry (macOS LaunchAgent `.plist`, Linux XDG `.desktop`,
 * Windows registry/Run metadata) contributed by a [GenericUiExtensionContributor].
 */
data class DesktopAutostartContribution(
    val identifier: String,
    val executableName: String,
    val displayName: String,
    val arguments: List<String> = emptyList(),
    val runAtStartup: Boolean = true
)
