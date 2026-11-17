package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Android Auto manifest and `automotive_app_desc.xml` configuration
 * (`genericui { android { manifest { androidAuto { ... } } } }`).
 */
open class GenericUiAndroidAutoConfig : Serializable {
    internal val explicitFields: MutableSet<String> = linkedSetOf()

    var enabled: Boolean = false
        set(value) {
            field = value
            explicitFields.add("enabled")
        }

    var mediaBrowserService: String? = null
        set(value) {
            field = value
            explicitFields.add("mediaBrowserService")
        }

    var usesMedia: Boolean = true
        set(value) {
            field = value
            explicitFields.add("usesMedia")
        }

    var usesNotification: Boolean = false
        set(value) {
            field = value
            explicitFields.add("usesNotification")
        }

    /** Merges explicit fields from [overlay] onto a copy of this configuration. */
    fun overlayWith(overlay: GenericUiAndroidAutoConfig): GenericUiAndroidAutoConfig =
        copySpec().apply {
            if ("enabled" in overlay.explicitFields) enabled = overlay.enabled
            if ("mediaBrowserService" in overlay.explicitFields || overlay.mediaBrowserService != null) {
                mediaBrowserService = overlay.mediaBrowserService
            }
            if ("usesMedia" in overlay.explicitFields) usesMedia = overlay.usesMedia
            if ("usesNotification" in overlay.explicitFields) usesNotification = overlay.usesNotification
        }

    /** Returns an independent deep copy of this Android Auto configuration. */
    fun copySpec(): GenericUiAndroidAutoConfig = GenericUiAndroidAutoConfig().also { copy ->
        copy.enabled = enabled
        copy.mediaBrowserService = mediaBrowserService
        copy.usesMedia = usesMedia
        copy.usesNotification = usesNotification
        copy.explicitFields.clear()
        copy.explicitFields.addAll(explicitFields)
    }
}
