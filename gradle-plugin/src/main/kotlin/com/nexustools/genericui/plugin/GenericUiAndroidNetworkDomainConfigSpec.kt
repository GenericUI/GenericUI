package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Specification for a `<domain-config>` block inside Android `network_security_config.xml`.
 */
open class GenericUiAndroidNetworkDomainConfigSpec : Serializable {
    val domains: MutableList<String> = mutableListOf()
    var includeSubdomains: Boolean = false
    var cleartextPermitted: Boolean? = null
    var trustAnchors: GenericUiAndroidTrustAnchorsSpec? = null
    var pinSet: GenericUiAndroidPinSetSpec? = null

    /** Adds a validated hostname or exact IP literal `<domain>` to this `<domain-config>`. */
    fun domain(host: String) {
        val validated = GenericUiAndroidNetworkSecurityConfig.validateDomainHost(host)
        if (validated !in domains) {
            domains.add(validated)
        }
    }

    /** Configures `<trust-anchors>` for this `<domain-config>`. */
    fun trustAnchors(action: Action<in GenericUiAndroidTrustAnchorsSpec>) {
        val spec = trustAnchors ?: GenericUiAndroidTrustAnchorsSpec().also { trustAnchors = it }
        action.execute(spec)
    }

    /** Configures certificate pinning (`<pin-set>`) for this `<domain-config>`. */
    @JvmOverloads
    fun pinSet(
        expiration: String? = null,
        action: Action<in GenericUiAndroidPinSetSpec>
    ) {
        val spec = pinSet ?: GenericUiAndroidPinSetSpec().also { pinSet = it }
        if (expiration != null) {
            spec.expiration = expiration.trim().ifEmpty { null }
        }
        action.execute(spec)
    }

    /** Returns an independent deep copy of this domain config spec. */
    fun copySpec(): GenericUiAndroidNetworkDomainConfigSpec = GenericUiAndroidNetworkDomainConfigSpec().also { copy ->
        copy.domains.addAll(domains)
        copy.includeSubdomains = includeSubdomains
        copy.cleartextPermitted = cleartextPermitted
        copy.trustAnchors = trustAnchors?.copySpec()
        copy.pinSet = pinSet?.copySpec()
    }
}
