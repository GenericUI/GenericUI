package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Specification for a custom Android `<provider>` (`ContentProvider`) component.
 */
open class GenericUiAndroidProviderSpec(val className: String) : Serializable {
    var authorities: String = ""
    var exported: Boolean = false
    var enabled: Boolean? = null
    var grantUriPermissions: Boolean = false
    var initOrder: Int? = null
    val metaDataList: MutableList<GenericUiAndroidMetaDataSpec> = mutableListOf()

    /** Adds or replaces a `<meta-data>` element inside this `<provider>`. */
    @JvmOverloads
    fun metaData(name: String, value: String? = null, resource: String? = null) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Provider <meta-data> name must not be blank." }
        metaDataList.removeAll { it.name == trimmed }
        metaDataList.add(GenericUiAndroidMetaDataSpec(name = trimmed, value = value, resource = resource))
    }

    /** Returns an independent deep copy of this provider specification. */
    fun copySpec(): GenericUiAndroidProviderSpec = GenericUiAndroidProviderSpec(className).also { copy ->
        copy.authorities = authorities
        copy.exported = exported
        copy.enabled = enabled
        copy.grantUriPermissions = grantUriPermissions
        copy.initOrder = initOrder
        metaDataList.mapTo(copy.metaDataList) { it.copy() }
    }
}
