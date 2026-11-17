package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Specification for an Android `<meta-data>` element inside `<application>`, `<activity>`,
 * `<service>`, `<receiver>`, or `<provider>`.
 */
data class GenericUiAndroidMetaDataSpec(
    val name: String,
    var value: String? = null,
    var resource: String? = null
) : Serializable
