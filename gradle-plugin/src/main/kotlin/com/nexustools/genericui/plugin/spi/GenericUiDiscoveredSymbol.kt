package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiVariantScope

/**
 * Symbol discovered by [com.nexustools.genericui.plugin.GenericUiFragmentScanner] matching an
 * extension's [ExtensionAnnotationScanSpec].
 */
data class GenericUiDiscoveredSymbol(
    val extensionModulePath: String,
    val annotationName: String,
    val qualifiedAnnotationName: String,
    val packageName: String,
    val symbolName: String,
    val qualifiedName: String,
    val sourceFilePath: String = "",
    val hasNoArgConstructor: Boolean = true,
    val attributes: Map<String, String> = emptyMap(),
    val targetScopes: List<GenericUiVariantScope> = emptyList()
)
