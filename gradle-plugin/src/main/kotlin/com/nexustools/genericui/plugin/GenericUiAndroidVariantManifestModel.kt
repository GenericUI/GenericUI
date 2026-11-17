package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Immutable resolved variant snapshot pairing a variant name, applicationId/versionName suffixes,
 * and its overlaid [GenericUiAndroidManifestModel].
 */
data class GenericUiAndroidVariantManifestModel(
    val variantName: String,
    val applicationIdSuffix: String = "",
    val versionNameSuffix: String = "",
    val manifest: GenericUiAndroidManifestModel
) : Serializable
