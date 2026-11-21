package com.nexustools.genericui.plugin.spi

import com.nexustools.genericui.plugin.GenericUiAndroidMetaDataSpec

/**
 * Android `<provider>` manifest element contribution.
 */
data class AndroidProviderContribution(
    val className: String,
    val authorities: String,
    val exported: Boolean = false,
    val enabled: Boolean? = null,
    val grantUriPermissions: Boolean = false,
    val initOrder: Int? = null,
    val metaData: List<GenericUiAndroidMetaDataSpec> = emptyList()
)
