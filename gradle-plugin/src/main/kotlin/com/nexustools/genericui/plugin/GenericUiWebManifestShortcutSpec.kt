package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Shortcut entry in a W3C / MDN Progressive Web App (PWA) Web App Manifest (`manifest.webmanifest`)
 * configured via `genericui { application { webManifest { shortcut(...) } } }`.
 */
data class GenericUiWebManifestShortcutSpec(
    val name: String,
    val shortName: String = "",
    val description: String = "",
    val url: String = "./"
) : Serializable
