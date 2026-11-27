package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiDiscoveredFragment
import com.nexustools.genericui.plugin.GenericUiVariantScope
import com.nexustools.genericui.plugin.PipAndroidActivityExtensionContributor

/**
 * Built-in SPI contributor for `:extensions:ui:pip` bridging Activity PiP lifecycle hooks
 * and Apple UIBackgroundModes without introducing any direct core-to-extension code dependency.
 */
object PipExtensionContributor : GenericUiExtensionContributor {

    const val PIP_MODULE_PATH: String = ":extensions:ui:pip"

    override val extensionModulePath: String = PIP_MODULE_PATH

    override val dslModulePath: String? = null

    override fun providerInstallerCalls(
        backendScope: GenericUiVariantScope,
        context: ExtensionBuildContext
    ): List<String> = emptyList()

    override fun contributeAndroid(
        context: ExtensionBuildContext,
        fragment: GenericUiDiscoveredFragment?
    ): AndroidExtensionContribution? {
        if (fragment == null) return null
        if (!PipAndroidActivityExtensionContributor.appliesTo(fragment, context.enabledExtensionModulePaths)) {
            return null
        }
        val legacy = PipAndroidActivityExtensionContributor.contribute(fragment)
        return AndroidExtensionContribution(
            manifestContribution = AndroidManifestContribution(
                activityConfigChanges = legacy.requiredConfigChanges,
                supportsPictureInPicture = legacy.supportsPictureInPictureManifest
            ),
            activityProperties = legacy.properties,
            activityOnCreateAfterSuperStatements = legacy.onCreateAfterSuperStatements,
            activityOnDestroyBeforeSuperStatements = legacy.onDestroyBeforeSuperStatements,
            activityMethods = legacy.methods,
            activityImports = legacy.imports
        )
    }

    override fun contributeAppleMobile(context: ExtensionBuildContext): AppleMobileExtensionContribution? {
        if (PIP_MODULE_PATH !in context.enabledExtensionModulePaths) return null
        return AppleMobileExtensionContribution(
            backgroundModes = listOf("audio")
        )
    }
}
