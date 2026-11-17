package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Specification for a `<data>` element inside an Android `<intent-filter>` or `<queries><intent>`.
 */
open class GenericUiAndroidIntentDataSpec : Serializable {
    var scheme: String? = null
    var host: String? = null
    var port: String? = null
    var path: String? = null
    var pathPrefix: String? = null
    var pathPattern: String? = null
    var mimeType: String? = null

    /** Returns `true` if at least one `<data>` attribute is configured. */
    fun hasAnyAttribute(): Boolean =
        !scheme.isNullOrBlank() ||
            !host.isNullOrBlank() ||
            !port.isNullOrBlank() ||
            !path.isNullOrBlank() ||
            !pathPrefix.isNullOrBlank() ||
            !pathPattern.isNullOrBlank() ||
            !mimeType.isNullOrBlank()

    /** Returns an independent deep copy of this data specification. */
    fun copySpec(): GenericUiAndroidIntentDataSpec = GenericUiAndroidIntentDataSpec().also { copy ->
        copy.scheme = scheme
        copy.host = host
        copy.port = port
        copy.path = path
        copy.pathPrefix = pathPrefix
        copy.pathPattern = pathPattern
        copy.mimeType = mimeType
    }
}
