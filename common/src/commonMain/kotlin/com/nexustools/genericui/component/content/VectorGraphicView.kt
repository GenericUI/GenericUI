package com.nexustools.genericui.component.content

/**
 * Vector SVG graphic display component.
 */
class VectorGraphicView(
    svgSourceOrPath: String? = null
) : ImageView(sourceUrl = svgSourceOrPath)
