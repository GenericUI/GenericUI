package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Specification for an Android `<uses-permission>` element, supporting optional
 * `android:maxSdkVersion` and `android:usesPermissionFlags` (e.g. `"neverForLocation"`).
 */
data class GenericUiAndroidPermissionSpec(
    val name: String,
    val maxSdkVersion: Int? = null,
    val usesPermissionFlags: String? = null
) : Serializable
