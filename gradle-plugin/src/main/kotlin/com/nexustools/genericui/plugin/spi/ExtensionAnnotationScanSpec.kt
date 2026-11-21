package com.nexustools.genericui.plugin.spi

/**
 * Declares annotation names that an extension wants [com.nexustools.genericui.plugin.GenericUiFragmentScanner]
 * to extract into [GenericUiDiscoveredSymbol] instances during project scanning.
 */
data class ExtensionAnnotationScanSpec(
    val extensionModulePath: String,
    val simpleAnnotationNames: Set<String> = emptySet(),
    val qualifiedAnnotationNames: Set<String> = emptySet(),
    val targetKinds: Set<SymbolTargetKind> = setOf(SymbolTargetKind.CLASS)
) {
    enum class SymbolTargetKind {
        CLASS,
        OBJECT,
        FUNCTION
    }
}
