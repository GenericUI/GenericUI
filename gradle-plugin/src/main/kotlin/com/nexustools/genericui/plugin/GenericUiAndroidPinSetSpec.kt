package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Certificate `<pin-set>` block inside an Android `network_security_config.xml` `<domain-config>`.
 */
open class GenericUiAndroidPinSetSpec : Serializable {
    var expiration: String? = null
    val pins: MutableList<GenericUiAndroidPinSpec> = mutableListOf()

    /** Adds a `<pin digest="...">...</pin>` entry to this pin-set. */
    @JvmOverloads
    fun pin(digest: String = "SHA-256", value: String) {
        val trimmedDigest = digest.trim().ifEmpty { "SHA-256" }
        val trimmedValue = value.trim()
        require(trimmedValue.isNotEmpty()) { "Android certificate pin value must not be blank." }
        pins.add(GenericUiAndroidPinSpec(digest = trimmedDigest, value = trimmedValue))
    }

    /** Returns an independent deep copy of this pin-set specification. */
    fun copySpec(): GenericUiAndroidPinSetSpec = GenericUiAndroidPinSetSpec().also { copy ->
        copy.expiration = expiration
        pins.mapTo(copy.pins) { it.copy() }
    }
}
