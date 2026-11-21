package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiAndroidIntentFilterSpec
import com.nexustools.genericui.plugin.GenericUiAndroidMetaDataSpec

/**
 * Android `<service>` manifest element contribution, including foreground service type,
 * lifecycle flags, and nested `<property>` entries (such as `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).
 */
data class AndroidServiceContribution(
    val className: String,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val foregroundServiceType: String? = null,
    val permission: String? = null,
    val process: String? = null,
    val stopWithTask: Boolean? = null,
    val isolatedProcess: Boolean? = null,
    val intentFilters: List<GenericUiAndroidIntentFilterSpec> = emptyList(),
    val metaData: List<GenericUiAndroidMetaDataSpec> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)
