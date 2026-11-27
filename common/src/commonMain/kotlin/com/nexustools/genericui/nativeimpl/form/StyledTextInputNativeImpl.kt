package com.nexustools.genericui.nativeimpl.form

import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Native implementation contract for
 * [com.nexustools.genericui.component.form.styled.StyledTextField] and
 * [com.nexustools.genericui.component.form.styled.StyledTextArea],
 * supporting cross-platform backgrounds (solid, RGBA alpha-transparent, linear & radial gradients),
 * borders (width, style, color, corner radius including pill radii), foreground text & placeholder
 * colors, padding, typography, and interactive `:hover`, `:focus`, `:active`, and `:disabled`
 * visual state overrides across all 9 backends.
 */
interface StyledTextInputNativeImpl<out W> : TextInputNativeImpl<W> {
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
     * Resolves the effective [StyledVisualSpec] for the input's current interactive state
     * (`isHoveredState()`, `isFocusedState()`, `isActivePressedState()`, `isEnabled()`).
     */
    fun resolveCurrentVisuals(): StyledVisualSpec {
        var resolved = getBaseVisuals()
        if (!isEnabled()) {
            return resolved.mergeWith(getDisabledVisuals())
        }
        if (isHoveredState()) {
            resolved = resolved.mergeWith(getHoverVisuals())
        }
        if (isFocusedState()) {
            resolved = resolved.mergeWith(getFocusVisuals())
        }
        if (isActivePressedState()) {
            resolved = resolved.mergeWith(getActiveVisuals())
        }
        return resolved
    }
}
