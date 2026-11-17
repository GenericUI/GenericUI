package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Specification for an Android `<service>` entry configured via
 * `genericui { android { services { service("...") { ... } } } }` or
 * `genericui { android { manifest { foregroundService(...) } } }`.
 */
open class GenericUiAndroidServiceSpec(val className: String) : Serializable {
    var exported: Boolean = false
    var enabled: Boolean = true
    var foregroundServiceType: String = ""
    var permission: String = ""
    var process: String = ""
    val intentActions: MutableList<String> = mutableListOf()
    val intentFilters: MutableList<GenericUiAndroidIntentFilterSpec> = mutableListOf()
    val metaDataList: MutableList<GenericUiAndroidMetaDataSpec> = mutableListOf()
    var autoAddForegroundServicePermission: Boolean = false

    /** Adds an `<action android:name="..."/>` to this service's default `<intent-filter>`. */
    fun intentAction(action: String) {
        val trimmed = action.trim()
        if (trimmed.isNotEmpty() && trimmed !in intentActions) {
            intentActions.add(trimmed)
        }
    }

    /** Configures and appends a full `<intent-filter>` to this service. */
    fun intentFilter(action: Action<in GenericUiAndroidIntentFilterSpec>) {
        val spec = GenericUiAndroidIntentFilterSpec()
        action.execute(spec)
        intentFilters.add(spec)
    }

    /** Adds or replaces a `<meta-data>` element inside this `<service>`. */
    @JvmOverloads
    fun metaData(name: String, value: String? = null, resource: String? = null) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Service <meta-data> name must not be blank." }
        metaDataList.removeAll { it.name == trimmed }
        metaDataList.add(GenericUiAndroidMetaDataSpec(name = trimmed, value = value, resource = resource))
    }

    /** Returns an independent deep copy of this service specification. */
    fun copySpec(): GenericUiAndroidServiceSpec = GenericUiAndroidServiceSpec(className).also { copy ->
        copy.exported = exported
        copy.enabled = enabled
        copy.foregroundServiceType = foregroundServiceType
        copy.permission = permission
        copy.process = process
        copy.intentActions.addAll(intentActions)
        intentFilters.mapTo(copy.intentFilters) { it.copySpec() }
        metaDataList.mapTo(copy.metaDataList) { it.copy() }
        copy.autoAddForegroundServicePermission = autoAddForegroundServicePermission
    }
}
