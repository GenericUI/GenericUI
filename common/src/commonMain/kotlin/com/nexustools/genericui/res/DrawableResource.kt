package com.nexustools.genericui.res

/**
 * Cross-platform compiled drawable resource (SVG vector icon or raster image) registered in [ResourceTable].
 *
 * Translated automatically by the GenericUI Gradle plugin for each backend:
 * - **Windows (`:desktop:windows`)**: Win32 `.rc` resource + GDI vector path / DIBits renderer
 * - **macOS / iOS (`:desktop:macos`, `:mobile:ios`)**: `.imageset` bundle asset + CoreGraphics vector/bitmap
 * - **Linux GTK / Qt (`:desktop:gtk`, `:desktop:qt`)**: `gresource.xml` / `.qrc` resource
 * - **Android (`:mobile:android`)**: Android VectorDrawable XML (`res/drawable/<name>.xml`) or bitmap
 * - **Web (`:web`) & Swing (`:desktop:swing`)**: Inline `<svg>` / data URI or classpath resource
 */
data class DrawableResource(
    val resId: ResourceId,
    val format: String = "svg",
    val svgSource: String? = null,
    val svgPathDataList: List<String> = emptyList(),
    val viewBoxWidth: Float = 24f,
    val viewBoxHeight: Float = 24f,
    val base64Data: String? = null
) {
    /**
     * Returns the canonical `"res://drawable/<name>"` URI for assigning to `Button.icon`,
     * `ToolPaletteItem.iconSvgOrPath`, `DockPanel.iconSvgOrPath`, or `MenuItem`.
     */
    fun toIconUri(): String = resId.toResourceUri()
}
