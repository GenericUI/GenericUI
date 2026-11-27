package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiDiscoveredFragment
import com.nexustools.genericui.plugin.GenericUiVariantScope

/**
 * Extension-agnostic build SPI implemented by first-party and third-party GenericUI extension
 * plugin contributors (via `ServiceLoader` or `META-INF/genericui/extensions/<id>.json`).
 *
 * Core plugin tasks query active contributors through [GenericUiExtensionContributorRegistry]
 * without importing or hardcoding extension runtime classes.
 */
interface GenericUiExtensionContributor {
    val extensionModulePath: String

    val dslModulePath: String?
        get() = null

    val alienModulePath: String?
        get() = null

    val annotationScanSpecs: List<ExtensionAnnotationScanSpec>
        get() = emptyList()

    val binaryCodecScanSpecs: List<ExtensionAnnotationScanSpec>
        get() = emptyList()

    fun providerInstallerCalls(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<String> = emptyList()

    fun generatedLauncherSources(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<ExtensionGeneratedSource> = emptyList()

    fun contributeAndroid(
        context: ExtensionBuildContext,
        fragment: GenericUiDiscoveredFragment?
    ): AndroidExtensionContribution? = null

    fun contributeDesktop(context: ExtensionBuildContext): DesktopExtensionContribution? = null

    fun contributeAppleMobile(context: ExtensionBuildContext): AppleMobileExtensionContribution? = null

    fun contributeWebServiceWorker(context: ExtensionBuildContext): WebServiceWorkerContribution? = null

    /**
     * Optional raw consumer-theme SCSS template (`theme.scss`) contributed by this extension.
     * GenericUI library modules MUST NOT compile this template themselves; the consumer
     * application build evaluates it against `genericui { theme { ... } }` variables.
     */
    fun contributeThemeScss(context: ExtensionBuildContext): String? = null

    /**
     * Validates extension annotations, symbol constraints, and configuration options before codegen/packaging.
     * Implementations should throw `org.gradle.api.GradleException` on invalid consumer declarations.
     */
    fun validate(
        context: ExtensionBuildContext,
        symbols: List<GenericUiDiscoveredSymbol> = context.discoveredSymbols
    ) {}
}
