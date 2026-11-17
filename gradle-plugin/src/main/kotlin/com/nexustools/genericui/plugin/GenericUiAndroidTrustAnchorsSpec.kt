package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Configures `<trust-anchors>` inside Android `network_security_config.xml` (`<base-config>`,
 * `<domain-config>`, or `<debug-overrides>`).
 */
open class GenericUiAndroidTrustAnchorsSpec : Serializable {
    var trustSystem: Boolean = true
    var trustUser: Boolean = false
    val certificateResources: MutableList<String> = mutableListOf()

    /** Enables or disables trusting the preinstalled Android system CAs (`<certificates src="system"/>`). */
    @JvmOverloads
    fun system(enabled: Boolean = true) {
        trustSystem = enabled
    }

    /** Enables or disables trusting user-installed CAs (`<certificates src="user"/>`). */
    @JvmOverloads
    fun user(enabled: Boolean = true) {
        trustUser = enabled
    }

    /** Adds a bundled raw/xml certificate resource reference (e.g. `"@raw/my_ca"`). */
    fun certificates(resRef: String) {
        val trimmed = resRef.trim()
        require(trimmed.isNotEmpty()) { "Certificate resource reference must not be blank." }
        if (trimmed !in certificateResources) {
            certificateResources.add(trimmed)
        }
    }

    /** Returns an independent deep copy of this trust anchors spec. */
    fun copySpec(): GenericUiAndroidTrustAnchorsSpec = GenericUiAndroidTrustAnchorsSpec().also { copy ->
        copy.trustSystem = trustSystem
        copy.trustUser = trustUser
        copy.certificateResources.addAll(certificateResources)
    }
}
