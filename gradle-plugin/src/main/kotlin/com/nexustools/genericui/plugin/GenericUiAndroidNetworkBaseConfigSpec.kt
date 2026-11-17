package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Specification for `<base-config>` inside Android `network_security_config.xml`.
 */
open class GenericUiAndroidNetworkBaseConfigSpec : Serializable {
    var cleartextPermitted: Boolean? = null
    var trustAnchors: GenericUiAndroidTrustAnchorsSpec? = null

    /** Configures `<trust-anchors>` for `<base-config>`. */
    fun trustAnchors(action: Action<in GenericUiAndroidTrustAnchorsSpec>) {
        val spec = trustAnchors ?: GenericUiAndroidTrustAnchorsSpec().also { trustAnchors = it }
        action.execute(spec)
    }

    /** Returns an independent deep copy of this base network security config. */
    fun copySpec(): GenericUiAndroidNetworkBaseConfigSpec = GenericUiAndroidNetworkBaseConfigSpec().also { copy ->
        copy.cleartextPermitted = cleartextPermitted
        copy.trustAnchors = trustAnchors?.copySpec()
    }
}
