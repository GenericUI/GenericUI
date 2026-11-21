package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiAndroidIntentFilterSpec
import com.nexustools.genericui.plugin.GenericUiAndroidMetaDataSpec

/**
 * Android `<receiver>` manifest element contribution.
 */
data class AndroidReceiverContribution(
    val className: String,
    val exported: Boolean? = null,
    val enabled: Boolean? = null,
    val permission: String? = null,
    val process: String? = null,
    val intentFilters: List<GenericUiAndroidIntentFilterSpec> = emptyList(),
    val metaData: List<GenericUiAndroidMetaDataSpec> = emptyList()
)
