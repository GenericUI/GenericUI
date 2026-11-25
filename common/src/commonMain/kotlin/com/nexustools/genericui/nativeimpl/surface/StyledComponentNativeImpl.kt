package com.nexustools.genericui.nativeimpl.surface

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.style.css.StyledVisualSpec

/**
 * Native implementation contract for [com.nexustools.genericui.component.surface.StyledComponent],
 * representing a non-container, non-button CSS-styled visual leaf (backgrounds, gradients,
 * borders, shadows, opacity, cursor, pointer-events, transforms, and interactive `:hover`,
 * `:focus`, `:active`, and `:disabled` visual state overrides).
 */
interface StyledComponentNativeImpl<out W> : NativeImpl<W> {
    fun setStyledVisuals(visuals: StyledVisualSpec)
    fun getStyledVisuals(): StyledVisualSpec

    fun setHoverVisuals(visuals: StyledVisualSpec?)
    fun getHoverVisuals(): StyledVisualSpec? = null

    fun setFocusVisuals(visuals: StyledVisualSpec?)
    fun getFocusVisuals(): StyledVisualSpec? = null

    fun setActiveVisuals(visuals: StyledVisualSpec?)
    fun getActiveVisuals(): StyledVisualSpec? = null

    fun setDisabledVisuals(visuals: StyledVisualSpec?)
    fun getDisabledVisuals(): StyledVisualSpec? = null

    /**
     * Resolves the effective [StyledVisualSpec] for this component's current interactive state
     * (`isFocusedState()`, `isHoveredState()`, `isActivePressedState()`, `isEnabled()`).
     */
    fun resolveCurrentVisuals(): StyledVisualSpec {
        var resolved = getStyledVisuals()
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
