package com.nexustools.genericui.nativeimpl.layout

import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Native implementation contract for [com.nexustools.genericui.component.layout.StyledContainer]
 * supporting cross-platform backgrounds (solid, RGBA alpha-transparent, linear & radial gradients),
 * borders (width, style, color, corner radius), padding, opacity, and box shadows across all 8 backends.
 *
 * Unlike [com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl], [StyledContainerNativeImpl]
 * represents a static/structural container and does not process interactive hover or focus state overrides.
 */
interface StyledContainerNativeImpl<out W> : ContainerNativeImpl<W> {
    fun setStyledVisuals(visuals: StyledVisualSpec)
    fun getStyledVisuals(): StyledVisualSpec
}
