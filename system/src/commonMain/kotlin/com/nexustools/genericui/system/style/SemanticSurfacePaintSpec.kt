package com.nexustools.genericui.system.style

import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient

/**
 * Semantic surface chrome description resolved for Bootstrap/shell surface containers (`Card`,
 * `Alert`, `Banner`, `Toast`, `ListGroup`, `ListGroupItem`, `Jumbotron`, `Well`, `Badge`,
 * `MdiWindow`, `MdiDesktop`).
 */
data class SemanticSurfacePaintSpec(
    val backgroundColor: RgbaColor? = null,
    val backgroundGradient: Gradient? = null,
    val borderColor: RgbaColor? = null,
    val borderWidthPx: Float? = null,
    val borderStyle: CssBorderStyle? = null,
    val borderRadiusPx: Float? = null,
    val textColor: RgbaColor? = null,
    val padding: EdgeInsets? = null,
    val shadowColor: RgbaColor? = null,
    val shadowBlurPx: Float? = null,
    val elevated: Boolean = false
) {
    /**
     * Converts this semantic surface specification into a backend-neutral [StyledVisualSpec].
     */
    fun toStyledVisualSpec(includePadding: Boolean = true): StyledVisualSpec = StyledVisualSpec(
        backgroundColor = backgroundColor,
        backgroundGradient = backgroundGradient,
        textColor = textColor,
        borderColor = borderColor,
        borderWidthPx = borderWidthPx,
        borderStyle = borderStyle,
        borderRadiusPx = borderRadiusPx,
        padding = if (includePadding) padding else null,
        boxShadowColor = shadowColor,
        boxShadowBlurPx = shadowBlurPx
    )
}
