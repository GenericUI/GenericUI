package com.nexustools.genericui.plugin

/**
 * Result produced by converting an SVG resource into an Android `VectorDrawable` XML document.
 */
data class GenericUiSvgConversionResult(
    val vectorXml: String?,
    val warnings: List<String>,
    val usedOfficialTool: Boolean,
    val fellBackToRaster: Boolean
)
