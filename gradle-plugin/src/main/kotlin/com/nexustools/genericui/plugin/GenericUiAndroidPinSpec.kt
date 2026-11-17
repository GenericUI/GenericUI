package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Certificate pin entry (`<pin digest="SHA-256">...</pin>`) inside an Android `<pin-set>`.
 */
data class GenericUiAndroidPinSpec(
    val digest: String = "SHA-256",
    val value: String
) : Serializable
