package com.nexustools.genericui.plugin.spi

/**
 * Specification for an additional desktop native/JVM executable binary (e.g. a companion
 * background daemon executable) contributed by a [GenericUiExtensionContributor].
 */
data class SecondaryDesktopExecutableContribution(
    val binaryBaseName: String,
    val entryPointFqn: String,
    val generatedEntrypointSource: ExtensionGeneratedSource? = null,
    val windowsSubsystem: String = "windows",
    val windowsExtraLibs: List<String> = emptyList(),
    val macosExtraLinkerOpts: List<String> = emptyList(),
    val linuxExtraLinkerOpts: List<String> = emptyList()
)
