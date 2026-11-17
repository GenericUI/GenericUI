package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Specification for a custom Android `<receiver>` (`BroadcastReceiver`) component.
 */
open class GenericUiAndroidReceiverSpec(val className: String) : Serializable {
    var exported: Boolean? = null
    var enabled: Boolean? = null
    var permission: String? = null
    var process: String? = null
    val intentFilters: MutableList<GenericUiAndroidIntentFilterSpec> = mutableListOf()
    val metaDataList: MutableList<GenericUiAndroidMetaDataSpec> = mutableListOf()

    private var defaultActionFilter: GenericUiAndroidIntentFilterSpec? = null

    /** Adds an `<action android:name="..."/>` to this receiver's default `<intent-filter>`. */
    fun intentAction(action: String) {
        val trimmed = action.trim()
        if (trimmed.isEmpty()) return
        val filter = defaultActionFilter ?: GenericUiAndroidIntentFilterSpec().also {
            defaultActionFilter = it
            intentFilters.add(it)
        }
        filter.action(trimmed)
    }

    /** Configures and appends a full `<intent-filter>` to this receiver. */
    fun intentFilter(action: Action<in GenericUiAndroidIntentFilterSpec>) {
        val spec = GenericUiAndroidIntentFilterSpec()
        action.execute(spec)
        intentFilters.add(spec)
    }

    /** Adds or replaces a `<meta-data>` element inside this `<receiver>`. */
    @JvmOverloads
    fun metaData(name: String, value: String? = null, resource: String? = null) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Receiver <meta-data> name must not be blank." }
        metaDataList.removeAll { it.name == trimmed }
        metaDataList.add(GenericUiAndroidMetaDataSpec(name = trimmed, value = value, resource = resource))
    }

    /** Returns an independent deep copy of this receiver specification. */
    fun copySpec(): GenericUiAndroidReceiverSpec = GenericUiAndroidReceiverSpec(className).also { copy ->
        copy.exported = exported
        copy.enabled = enabled
        copy.permission = permission
        copy.process = process
        intentFilters.mapTo(copy.intentFilters) { it.copySpec() }
        metaDataList.mapTo(copy.metaDataList) { it.copy() }
    }
}
