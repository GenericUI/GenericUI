package com.nexustools.genericui.plugin

import org.gradle.api.Action
import org.gradle.api.GradleException
import java.io.Serializable

/**
 * Reusable activity attribute defaults and child-element configuration block used for
 * `activityDefaults { ... }`, `mainActivity { ... }`, and per-fragment `activity("...") { ... }` overrides.
 */
open class GenericUiAndroidActivityDefaultsSpec : Serializable {
    var activityClass: String? = null
    var launcher: Boolean? = null
    var launchMode: String? = null
    var windowSoftInputMode: String? = null
    var configChanges: String? = null
    var screenOrientation: String? = null
    var supportsPictureInPicture: Boolean? = null
    var resizeableActivity: Boolean? = null
    var showWhenLocked: Boolean? = null
    var turnScreenOn: Boolean? = null
    var excludeFromRecents: Boolean? = null
    var taskAffinity: String? = null
    var theme: String? = null
    var exported: Boolean? = null
    var noHistory: Boolean? = null
    var documentLaunchMode: String? = null
    var maxRecents: Int? = null
    var hardwareAccelerated: Boolean? = null
    var lockTaskMode: String? = null

    val properties: MutableMap<String, String> = linkedMapOf()
    val metaDataList: MutableList<GenericUiAndroidMetaDataSpec> = mutableListOf()
    val intentFilters: MutableList<GenericUiAndroidIntentFilterSpec> = mutableListOf()
    val rawAttributes: MutableMap<String, String> = linkedMapOf()

    /** Adds or replaces a `<property android:name="..." android:value="..."/>` on the activity. */
    fun property(name: String, value: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Activity <property> name must not be blank." }
        properties[trimmed] = value
    }

    /** Adds or replaces a `<meta-data>` entry on the activity. */
    @JvmOverloads
    fun metaData(name: String, value: String? = null, resource: String? = null) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Activity <meta-data> name must not be blank." }
        metaDataList.removeAll { it.name == trimmed }
        metaDataList.add(GenericUiAndroidMetaDataSpec(name = trimmed, value = value, resource = resource))
    }

    /** Adds a custom `<intent-filter>` block to the activity. */
    fun intentFilter(action: Action<in GenericUiAndroidIntentFilterSpec>) {
        val spec = GenericUiAndroidIntentFilterSpec()
        action.execute(spec)
        intentFilters.add(spec)
    }

    /** Sets a raw XML attribute on `<activity>` (defaults to `android:` namespace when unprefixed). */
    fun activityAttribute(name: String, value: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Activity attribute name must not be blank." }
        rawAttributes[trimmed] = value
    }

    /**
     * Parses a URI pattern `scheme://host[:port][/path]` into an `ACTION_VIEW` + `CATEGORY_DEFAULT` +
     * `CATEGORY_BROWSABLE` `<intent-filter>` (with `android:autoVerify="true"` when [autoVerify] is true).
     */
    @JvmOverloads
    fun deepLink(uriPattern: String, autoVerify: Boolean = false) {
        val trimmed = uriPattern.trim()
        val schemeSep = trimmed.indexOf("://")
        if (schemeSep <= 0) {
            throw GradleException(
                "Invalid Android deepLink URI '$uriPattern': expected format 'scheme://host[:port][/path]'."
            )
        }
        val parsedScheme = trimmed.substring(0, schemeSep).trim()
        if (parsedScheme.isEmpty() || parsedScheme.any { it.isWhitespace() }) {
            throw GradleException(
                "Invalid Android deepLink URI '$uriPattern': scheme must not be empty or contain whitespace."
            )
        }

        var remainder = trimmed.substring(schemeSep + 3)
        val queryIdx = remainder.indexOf('?')
        if (queryIdx >= 0) {
            remainder = remainder.substring(0, queryIdx)
        }
        val fragIdx = remainder.indexOf('#')
        if (fragIdx >= 0) {
            remainder = remainder.substring(0, fragIdx)
        }

        val slashIdx = remainder.indexOf('/')
        val authority = if (slashIdx >= 0) remainder.substring(0, slashIdx) else remainder
        val rawPath = if (slashIdx >= 0) remainder.substring(slashIdx) else ""

        var parsedHost: String? = null
        var parsedPort: String? = null
        if (authority.isNotEmpty()) {
            val lastColon = authority.lastIndexOf(':')
            if (lastColon > 0 && !authority.endsWith("]") && !authority.substring(0, lastColon).contains(':')) {
                parsedHost = authority.substring(0, lastColon).trim().ifEmpty { null }
                parsedPort = authority.substring(lastColon + 1).trim().ifEmpty { null }
            } else {
                parsedHost = authority.trim().ifEmpty { null }
            }
        }

        intentFilter {
            this.autoVerify = autoVerify
            action("android.intent.action.VIEW")
            category("android.intent.category.DEFAULT")
            category("android.intent.category.BROWSABLE")
            data {
                scheme = parsedScheme
                host = parsedHost
                port = parsedPort
                if (rawPath.isNotEmpty()) {
                    when {
                        rawPath.contains('*') -> pathPattern = rawPath
                        rawPath.endsWith("/") && rawPath.length > 1 -> pathPrefix = rawPath
                        else -> path = rawPath
                    }
                }
            }
        }
    }

    /** Copies all non-null properties and merges collections from [other] over this spec's values. */
    fun overlayWith(other: GenericUiAndroidActivityDefaultsSpec) {
        other.activityClass?.let { activityClass = it }
        other.launcher?.let { launcher = it }
        other.launchMode?.let { launchMode = it }
        other.windowSoftInputMode?.let { windowSoftInputMode = it }
        other.configChanges?.let { configChanges = it }
        other.screenOrientation?.let { screenOrientation = it }
        other.supportsPictureInPicture?.let { supportsPictureInPicture = it }
        other.resizeableActivity?.let { resizeableActivity = it }
        other.showWhenLocked?.let { showWhenLocked = it }
        other.turnScreenOn?.let { turnScreenOn = it }
        other.excludeFromRecents?.let { excludeFromRecents = it }
        other.taskAffinity?.let { taskAffinity = it }
        other.theme?.let { theme = it }
        other.exported?.let { exported = it }
        other.noHistory?.let { noHistory = it }
        other.documentLaunchMode?.let { documentLaunchMode = it }
        other.maxRecents?.let { maxRecents = it }
        other.hardwareAccelerated?.let { hardwareAccelerated = it }
        other.lockTaskMode?.let { lockTaskMode = it }
        properties.putAll(other.properties)
        for (meta in other.metaDataList) {
            metaDataList.removeAll { it.name == meta.name }
            metaDataList.add(meta.copy())
        }
        other.intentFilters.mapTo(intentFilters) { it.copySpec() }
        rawAttributes.putAll(other.rawAttributes)
    }

    /** Populates [target] with a deep copy of this spec's properties and collections. */
    protected fun copyInto(target: GenericUiAndroidActivityDefaultsSpec) {
        target.activityClass = activityClass
        target.launcher = launcher
        target.launchMode = launchMode
        target.windowSoftInputMode = windowSoftInputMode
        target.configChanges = configChanges
        target.screenOrientation = screenOrientation
        target.supportsPictureInPicture = supportsPictureInPicture
        target.resizeableActivity = resizeableActivity
        target.showWhenLocked = showWhenLocked
        target.turnScreenOn = turnScreenOn
        target.excludeFromRecents = excludeFromRecents
        target.taskAffinity = taskAffinity
        target.theme = theme
        target.exported = exported
        target.noHistory = noHistory
        target.documentLaunchMode = documentLaunchMode
        target.maxRecents = maxRecents
        target.hardwareAccelerated = hardwareAccelerated
        target.lockTaskMode = lockTaskMode
        target.properties.putAll(properties)
        metaDataList.mapTo(target.metaDataList) { it.copy() }
        intentFilters.mapTo(target.intentFilters) { it.copySpec() }
        target.rawAttributes.putAll(rawAttributes)
    }

    /** Returns an independent deep copy of this activity defaults spec. */
    open fun copyDefaults(): GenericUiAndroidActivityDefaultsSpec =
        GenericUiAndroidActivityDefaultsSpec().also { copyInto(it) }
}
