package com.nexustools.genericui.plugin

/**
 * Service Provider Interface (SPI) implemented by GenericUI extension modules that need to augment
 * generated Android Fragment `AppCompatActivity` classes and `AndroidManifest.xml` `<activity>` tags.
 */
interface AndroidActivityExtensionContributor {
    val extensionModulePath: String

    fun appliesTo(fragment: GenericUiDiscoveredFragment, enabledExtensionPaths: Set<String>): Boolean

    fun contribute(fragment: GenericUiDiscoveredFragment): AndroidActivityExtensionContribution
}
