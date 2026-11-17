package com.nexustools.genericui.plugin

import org.gradle.api.Action
import org.gradle.api.GradleException
import java.io.Serializable

/**
 * Typed DSL configuration for Android `res/xml/network_security_config.xml`
 * (`genericui { android { manifest { networkSecurity { ... } } } }`).
 */
open class GenericUiAndroidNetworkSecurityConfig : Serializable {
    var cleartextPermittedByDefault: Boolean? = null
    var trustUserCertificates: Boolean = false
    var baseConfig: GenericUiAndroidNetworkBaseConfigSpec? = null
    val domainConfigs: MutableList<GenericUiAndroidNetworkDomainConfigSpec> = mutableListOf()
    var debugOverridesTrustAnchors: GenericUiAndroidTrustAnchorsSpec? = null

    /**
     * Permits cleartext HTTP traffic for a specific hostname or exact IP literal (such as `"10.0.2.2"`
     * or `"localhost"`). Rejects CIDR notation (`/`) since Android `<domain>` elements do not accept subnets.
     */
    @JvmOverloads
    fun cleartextDomain(host: String, includeSubdomains: Boolean = false) {
        val validated = validateDomainHost(host)
        val spec = GenericUiAndroidNetworkDomainConfigSpec().apply {
            this.includeSubdomains = includeSubdomains
            this.cleartextPermitted = true
            domain(validated)
        }
        domainConfigs.add(spec)
    }

    /** Configures `<base-config>` inside `network_security_config.xml`. */
    fun baseConfig(action: Action<in GenericUiAndroidNetworkBaseConfigSpec>) {
        val spec = baseConfig ?: GenericUiAndroidNetworkBaseConfigSpec().also { baseConfig = it }
        action.execute(spec)
    }

    /** Configures and registers a `<domain-config>` block for [host]. */
    @JvmOverloads
    fun domainConfig(
        host: String,
        includeSubdomains: Boolean = false,
        action: Action<in GenericUiAndroidNetworkDomainConfigSpec>
    ) {
        val validated = validateDomainHost(host)
        val spec = GenericUiAndroidNetworkDomainConfigSpec().apply {
            this.includeSubdomains = includeSubdomains
            domain(validated)
        }
        action.execute(spec)
        domainConfigs.add(spec)
    }

    /** Configures `<debug-overrides>` inside `network_security_config.xml`. */
    fun debugOverrides(action: Action<in GenericUiAndroidDebugOverridesSpec>) {
        val wrapper = GenericUiAndroidDebugOverridesSpec(debugOverridesTrustAnchors)
        action.execute(wrapper)
        debugOverridesTrustAnchors = wrapper.trustAnchors
    }

    /** Returns `true` if any network security rule or override has been configured. */
    fun isConfigured(): Boolean =
        cleartextPermittedByDefault != null ||
            trustUserCertificates ||
            baseConfig != null ||
            domainConfigs.isNotEmpty() ||
            debugOverridesTrustAnchors != null

    /** Validates all configured domains and certificate pins. */
    fun validate() {
        for (dc in domainConfigs) {
            if (dc.domains.isEmpty()) {
                throw GradleException("Android networkSecurity <domain-config> must specify at least one <domain>.")
            }
            for (d in dc.domains) {
                validateDomainHost(d)
            }
            val pinSet = dc.pinSet
            if (pinSet != null) {
                if (pinSet.pins.isEmpty()) {
                    throw GradleException(
                        "Android networkSecurity <pin-set> for domain(s) ${dc.domains.joinToString(", ")} " +
                            "must declare at least one <pin>."
                    )
                }
                for (pin in pinSet.pins) {
                    if (pin.value.isBlank()) {
                        throw GradleException(
                            "Android networkSecurity <pin> value for domain(s) ${dc.domains.joinToString(", ")} must not be blank."
                        )
                    }
                }
            }
        }
    }

    /** Merges [overlay] on top of this configuration into a new independent copy. */
    fun overlayWith(overlay: GenericUiAndroidNetworkSecurityConfig): GenericUiAndroidNetworkSecurityConfig =
        copySpec().apply {
            overlay.cleartextPermittedByDefault?.let { cleartextPermittedByDefault = it }
            if (overlay.trustUserCertificates) {
                trustUserCertificates = true
            }
            if (overlay.baseConfig != null) {
                val targetBase = baseConfig ?: GenericUiAndroidNetworkBaseConfigSpec().also { baseConfig = it }
                overlay.baseConfig?.cleartextPermitted?.let { targetBase.cleartextPermitted = it }
                overlay.baseConfig?.trustAnchors?.let { targetBase.trustAnchors = it.copySpec() }
            }
            overlay.domainConfigs.mapTo(domainConfigs) { it.copySpec() }
            overlay.debugOverridesTrustAnchors?.let { debugOverridesTrustAnchors = it.copySpec() }
        }

    /** Returns an independent deep copy of this network security configuration. */
    fun copySpec(): GenericUiAndroidNetworkSecurityConfig = GenericUiAndroidNetworkSecurityConfig().also { copy ->
        copy.cleartextPermittedByDefault = cleartextPermittedByDefault
        copy.trustUserCertificates = trustUserCertificates
        copy.baseConfig = baseConfig?.copySpec()
        domainConfigs.mapTo(copy.domainConfigs) { it.copySpec() }
        copy.debugOverridesTrustAnchors = debugOverridesTrustAnchors?.copySpec()
    }

    companion object {
        /**
         * Validates that [host] is a valid Android `<domain>` hostname or exact IP literal
         * (rejecting CIDR `/` ranges and URL schemes).
         */
        fun validateDomainHost(host: String): String {
            val trimmed = host.trim()
            if (trimmed.isEmpty()) {
                throw GradleException("Android networkSecurity domain must not be blank.")
            }
            if (trimmed.contains("://")) {
                throw GradleException(
                    "Invalid Android networkSecurity domain '$trimmed': pass only the hostname or exact IP " +
                        "without a URL scheme (for example 'example.com' or '10.0.2.2')."
                )
            }
            if (trimmed.contains('/')) {
                throw GradleException(
                    "Invalid Android networkSecurity domain '$trimmed': CIDR notation ('/') is not supported by " +
                        "Android <domain> entries; Android <domain> only accepts hostnames or exact IP literals."
                )
            }
            if (trimmed.any { it.isWhitespace() }) {
                throw GradleException("Invalid Android networkSecurity domain '$trimmed': must not contain whitespace.")
            }
            return trimmed
        }
    }
}
