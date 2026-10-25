package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Icon entry in a W3C / MDN Progressive Web App (PWA) Web App Manifest (`manifest.webmanifest`)
 * configured via `genericui { application { webManifest { icon(...) } } }`.
 */
data class GenericUiWebManifestIconSpec(
    val src: String,
    val sizes: String = "any",
    val type: String = "",
    val purpose: String = "any maskable"
) : Serializable
