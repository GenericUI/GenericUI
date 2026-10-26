package com.nexustools.genericui.plugin

/**
 * Specification for an Android `<service>` entry configured via
 * `genericui { android { services { service("...") { ... } } } }`.
 */
open class GenericUiAndroidServiceSpec(val className: String) {
    var exported: Boolean = false
    var enabled: Boolean = true
    var foregroundServiceType: String = ""
    var permission: String = ""
    var process: String = ""
    val intentActions: MutableList<String> = mutableListOf()

    /** Adds an `<action android:name="..."/>` to this service's `<intent-filter>`. */
    fun intentAction(action: String) {
        val trimmed = action.trim()
        if (trimmed.isNotEmpty() && trimmed !in intentActions) {
            intentActions.add(trimmed)
        }
    }
}
