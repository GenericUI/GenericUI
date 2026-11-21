package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiDiscoveredFragment
import java.io.File

/**
 * Build context supplied to [GenericUiExtensionContributor] implementations during Gradle codegen
 * and packaging tasks.
 */
data class ExtensionBuildContext(
    val projectRootDir: File,
    val projectName: String,
    val appId: String,
    val appName: String,
    val appVersion: String,
    val baseName: String,
    val generatedPackageName: String,
    val enabledExtensionModulePaths: Set<String>,
    val customExtensionModulePaths: Set<String> = emptySet(),
    val discoveredFragments: List<GenericUiDiscoveredFragment> = emptyList(),
    val discoveredSymbols: List<GenericUiDiscoveredSymbol> = emptyList(),
    val extensionOptions: Map<String, String> = emptyMap()
)
