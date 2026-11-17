package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Specification for an Android `<uses-feature>` element (by feature [name] or OpenGL ES [glEsVersion]).
 */
data class GenericUiAndroidUsesFeatureSpec(
    val name: String = "",
    val required: Boolean = true,
    val glEsVersion: Int? = null
) : Serializable
