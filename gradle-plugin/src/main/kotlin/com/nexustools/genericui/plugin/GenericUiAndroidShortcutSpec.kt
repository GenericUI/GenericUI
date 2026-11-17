package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Specification for a static Android App Shortcut entry in `res/xml/shortcuts.xml`.
 */
data class GenericUiAndroidShortcutSpec(
    val shortcutId: String,
    val shortLabel: String,
    val longLabel: String? = null,
    val icon: String? = null,
    val urlOrAction: String = "",
    val targetFragment: String? = null
) : Serializable
