package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Android static App Shortcuts (`res/xml/shortcuts.xml`) configuration
 * (`genericui { android { manifest { shortcuts { ... } } } }`).
 */
open class GenericUiAndroidShortcutsConfig : Serializable {
    internal val explicitFields: MutableSet<String> = linkedSetOf()

    var enabled: Boolean = false
        set(value) {
            field = value
            explicitFields.add("enabled")
        }

    var inheritFromWebManifest: Boolean = true
        set(value) {
            field = value
            explicitFields.add("inheritFromWebManifest")
        }

    val shortcuts: MutableList<GenericUiAndroidShortcutSpec> = mutableListOf()

    /** Registers a static app shortcut entry and enables shortcuts unless explicitly disabled. */
    @JvmOverloads
    fun shortcut(
        shortcutId: String,
        shortLabel: String,
        longLabel: String? = null,
        icon: String? = null,
        urlOrAction: String = "",
        targetFragment: String? = null
    ) {
        val trimmedId = shortcutId.trim()
        val trimmedShort = shortLabel.trim()
        require(trimmedId.isNotEmpty()) { "Android shortcutId must not be blank." }
        require(trimmedShort.isNotEmpty()) { "Android shortcut shortLabel must not be blank." }
        if ("enabled" !in explicitFields) {
            enabled = true
        }
        shortcuts.removeAll { it.shortcutId == trimmedId }
        shortcuts.add(
            GenericUiAndroidShortcutSpec(
                shortcutId = trimmedId,
                shortLabel = trimmedShort,
                longLabel = longLabel?.trim()?.takeIf { it.isNotEmpty() },
                icon = icon?.trim()?.takeIf { it.isNotEmpty() },
                urlOrAction = urlOrAction.trim(),
                targetFragment = targetFragment?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
    }

    /** Merges explicit settings and shortcut entries from [overlay] onto a copy of this configuration. */
    fun overlayWith(overlay: GenericUiAndroidShortcutsConfig): GenericUiAndroidShortcutsConfig =
        copySpec().apply {
            if ("enabled" in overlay.explicitFields) enabled = overlay.enabled
            if ("inheritFromWebManifest" in overlay.explicitFields) {
                inheritFromWebManifest = overlay.inheritFromWebManifest
            }
            for (s in overlay.shortcuts) {
                shortcuts.removeAll { it.shortcutId == s.shortcutId }
                shortcuts.add(s.copy())
            }
        }

    /** Returns an independent deep copy of this shortcuts configuration. */
    fun copySpec(): GenericUiAndroidShortcutsConfig = GenericUiAndroidShortcutsConfig().also { copy ->
        copy.enabled = enabled
        copy.inheritFromWebManifest = inheritFromWebManifest
        shortcuts.mapTo(copy.shortcuts) { it.copy() }
        copy.explicitFields.clear()
        copy.explicitFields.addAll(explicitFields)
    }
}
