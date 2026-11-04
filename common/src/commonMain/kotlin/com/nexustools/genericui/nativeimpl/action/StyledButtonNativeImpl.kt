package com.nexustools.genericui.nativeimpl.action

import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Native implementation contract for [com.nexustools.genericui.component.action.StyledButton]
 * supporting cross-platform backgrounds (solid, RGBA alpha-transparent, linear & radial gradients),
 * borders (width, style, color, corner radius), foreground text/icon tinting, and interactive
 * `:hover`, `:focus`, `:active` (pressed), and `:disabled` visual state overrides across all 8 backends.
 */
interface StyledButtonNativeImpl<out W> : ButtonNativeImpl<W> {
    fun setBaseVisuals(visuals: StyledVisualSpec)
    fun getBaseVisuals(): StyledVisualSpec

    fun setHoverVisuals(visuals: StyledVisualSpec?)
    fun getHoverVisuals(): StyledVisualSpec?

    fun setFocusVisuals(visuals: StyledVisualSpec?)
    fun getFocusVisuals(): StyledVisualSpec?

    fun setActiveVisuals(visuals: StyledVisualSpec?)
    fun getActiveVisuals(): StyledVisualSpec?

    fun setDisabledVisuals(visuals: StyledVisualSpec?)
    fun getDisabledVisuals(): StyledVisualSpec?

    /**
     * Resolves the effective [StyledVisualSpec] for the button's current interactive state
     * (`isFocusedState()`, `isHoveredState()`, `isActivePressedState()`, `isEnabled()`).
     */
    fun resolveCurrentVisuals(): StyledVisualSpec {
        var resolved = getBaseVisuals()
        if (!isEnabled()) {
            return resolved.mergeWith(getDisabledVisuals())
        }
        if (isFocusedState()) {
            resolved = resolved.mergeWith(getFocusVisuals())
        }
        if (isHoveredState()) {
            resolved = resolved.mergeWith(getHoverVisuals())
        }
        if (isActivePressedState()) {
            resolved = resolved.mergeWith(getActiveVisuals())
        }
        return resolved
    }
}
