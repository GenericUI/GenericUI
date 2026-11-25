package com.nexustools.genericui.system.style

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.policy.AestheticStylePolicy

/**
 * Shared visual-state controller for styled GenericUI widgets across native backends (`Swing`,
 * `Android`, `UIKit`, `AppKit`, `Windows`, `GTK`, `KDE`).
 *
 * Tracks base, pseudo-state (`:hover`, `:focus`, `:active`, `:disabled`), CSS computed/component
 * styles, and Bootstrap snapshots while enforcing [AestheticStylePolicy] so non-`Button` widgets
 * (`allowsBootstrapTypeTint == false`) never adopt `BootstrapType` semantic tinting.
 */
class StyledWidgetVisualController(
    val allowsBootstrapTypeTint: Boolean
) {
    var componentTagName: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var componentClassPrefix: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isCustomStyleEngine: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var baseVisuals: StyledVisualSpec = StyledVisualSpec.EMPTY
        set(value) {
            val sanitized = sanitizeVisuals(value, isDarkTheme = false, backendTarget = null)
            if (field != sanitized) {
                field = sanitized
                invalidate()
            }
        }

    var hoverVisuals: StyledVisualSpec? = null
        set(value) {
            val sanitized = value?.let { sanitizeVisuals(it, isDarkTheme = false, backendTarget = null) }
            if (field != sanitized) {
                field = sanitized
                invalidate()
            }
        }

    var focusVisuals: StyledVisualSpec? = null
        set(value) {
            val sanitized = value?.let { sanitizeVisuals(it, isDarkTheme = false, backendTarget = null) }
            if (field != sanitized) {
                field = sanitized
                invalidate()
            }
        }

    var activeVisuals: StyledVisualSpec? = null
        set(value) {
            val sanitized = value?.let { sanitizeVisuals(it, isDarkTheme = false, backendTarget = null) }
            if (field != sanitized) {
                field = sanitized
                invalidate()
            }
        }

    var disabledVisuals: StyledVisualSpec? = null
        set(value) {
            val sanitized = value?.let { sanitizeVisuals(it, isDarkTheme = false, backendTarget = null) }
            if (field != sanitized) {
                field = sanitized
                invalidate()
            }
        }

    var hovered: Boolean = false
        set(value) {
            val effective = value && enabled
            if (field != effective) {
                field = effective
                invalidate()
            }
        }

    var focused: Boolean = false
        set(value) {
            val effective = value && enabled
            if (field != effective) {
                field = effective
                invalidate()
            }
        }

    var activePressed: Boolean = false
        set(value) {
            val effective = value && enabled
            if (field != effective) {
                field = effective
                invalidate()
            }
        }

    var enabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (!value) {
                    hovered = false
                    focused = false
                    activePressed = false
                }
                invalidate()
            }
        }

    var lastComponentStyle: ComponentStyle? = null
        set(value) {
            field = value
            invalidate()
        }

    var lastComputedStyle: ComputedStyle? = null
        set(value) {
            field = value
            invalidate()
        }

    var bootstrapSnapshot: BootstrapStyleSnapshot? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var cachedVisuals: StyledVisualSpec? = null
    private var cachedIsDarkTheme: Boolean = false
    private var cachedBackendTarget: LayoutBackendTarget? = null
    private var cachedAnimatedOpacity: Float? = null

    fun invalidate() {
        cachedVisuals = null
    }

    /**
     * Returns whether `BootstrapType` semantic color tinting is permitted for this widget on
     * [backendTarget], strictly honoring [allowsBootstrapTypeTint] and [AestheticStylePolicy].
     */
    fun allowsEffectiveBootstrapTypeTint(backendTarget: LayoutBackendTarget? = null): Boolean {
        if (backendTarget == LayoutBackendTarget.WEB || isCustomStyleEngine) return true
        if (!allowsBootstrapTypeTint) return false
        if (componentTagName.isBlank() && componentClassPrefix.isBlank()) return true
        if (AestheticStylePolicy.isStandardButtonTagOrPrefix(componentTagName, componentClassPrefix) ||
            AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix(componentTagName, componentClassPrefix)
        ) {
            return true
        }
        return AestheticStylePolicy.isExplicitlyStyledTagOrPrefix(componentTagName, componentClassPrefix)
    }

    /**
     * Strips `BootstrapType` semantic colors from [spec] when this widget does not permit
     * Bootstrap tinting and no explicit CSS style defined those channels.
     */
    fun sanitizeVisuals(
        spec: StyledVisualSpec,
        isDarkTheme: Boolean = false,
        backendTarget: LayoutBackendTarget? = null
    ): StyledVisualSpec {
        if (allowsEffectiveBootstrapTypeTint(backendTarget)) return spec
        val snapshot = bootstrapSnapshot ?: return spec
        val semType = snapshot.type?.takeIf { it.isSemantic }
            ?: snapshot.backgroundType?.takeIf { it.isSemantic }
        val textType = snapshot.textColorType?.takeIf { it.isSemantic }
        val borderType = snapshot.borderType?.takeIf { it.isSemantic }
        if (semType == null && textType == null && borderType == null) return spec

        val explicitBg = lastComponentStyle?.getBackgroundColor() ?: lastComputedStyle?.backgroundColor
        val explicitFg = lastComponentStyle?.getTextColor() ?: lastComputedStyle?.textColor
        val explicitBorder = lastComponentStyle?.getBorderColor() ?: lastComputedStyle?.borderColor

        var sanitized = spec
        if (explicitBg == null && semType != null && !snapshot.outline) {
            val lightBg = BootstrapColorPalette.resolveColor(semType, darkMode = false)
            val darkBg = BootstrapColorPalette.resolveColor(semType, darkMode = true)
            val curBg = BootstrapColorPalette.resolveColor(semType, darkMode = isDarkTheme)
            if (sanitized.backgroundColor == curBg || sanitized.backgroundColor == lightBg || sanitized.backgroundColor == darkBg) {
                sanitized = sanitized.copy(backgroundColor = null)
            }
        }
        if (explicitFg == null && (semType != null || textType != null)) {
            val candidateColors = buildList {
                if (textType != null) {
                    add(BootstrapColorPalette.resolveColor(textType, darkMode = false))
                    add(BootstrapColorPalette.resolveColor(textType, darkMode = true))
                    add(BootstrapColorPalette.resolveColor(textType, darkMode = isDarkTheme))
                } else if (semType != null) {
                    if (snapshot.outline) {
                        add(BootstrapColorPalette.resolveColor(semType, darkMode = false))
                        add(BootstrapColorPalette.resolveColor(semType, darkMode = true))
                        add(BootstrapColorPalette.resolveColor(semType, darkMode = isDarkTheme))
                    } else {
                        add(BootstrapColorPalette.resolveForegroundOn(semType, darkMode = false))
                        add(BootstrapColorPalette.resolveForegroundOn(semType, darkMode = true))
                        add(BootstrapColorPalette.resolveForegroundOn(semType, darkMode = isDarkTheme))
                    }
                }
            }
            if (sanitized.textColor in candidateColors) {
                sanitized = sanitized.copy(textColor = null)
            }
        }
        if (explicitBorder == null && (borderType != null || (semType != null && snapshot.outline))) {
            val targetBorderType = borderType ?: semType
            if (targetBorderType != null) {
                val lightBorder = BootstrapColorPalette.resolveColor(targetBorderType, darkMode = false)
                val darkBorder = BootstrapColorPalette.resolveColor(targetBorderType, darkMode = true)
                val curBorder = BootstrapColorPalette.resolveColor(targetBorderType, darkMode = isDarkTheme)
                if (sanitized.borderColor == curBorder || sanitized.borderColor == lightBorder || sanitized.borderColor == darkBorder) {
                    val clearWidth = lastComponentStyle?.borderWidthPx == null &&
                        lastComputedStyle?.borderWidthPx == null &&
                        snapshot.borderWidth == null &&
                        sanitized.borderWidthPx == 1f
                    sanitized = sanitized.copy(
                        borderColor = null,
                        borderWidthPx = if (clearWidth) null else sanitized.borderWidthPx,
                        borderStyle = if (clearWidth) null else sanitized.borderStyle
                    )
                }
            }
        }
        return sanitized
    }

    /**
     * Resolves the effective [StyledVisualSpec] by merging computed/component base styles ->
     * [baseVisuals] -> active pseudo-state (`:disabled` OR `:hover` -> `:focus` -> `:active`),
     * backend adaptation, and optional [animatedOpacity].
     */
    fun resolveCurrentVisuals(
        isDarkTheme: Boolean,
        backendTarget: LayoutBackendTarget? = null,
        animatedOpacity: Float? = null
    ): StyledVisualSpec {
        cachedVisuals?.let { cached ->
            if (cachedIsDarkTheme == isDarkTheme &&
                cachedBackendTarget == backendTarget &&
                cachedAnimatedOpacity == animatedOpacity
            ) {
                return cached
            }
        }

        val effectiveSnapshot = sanitizeBootstrapSnapshot(bootstrapSnapshot, backendTarget)
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = lastComputedStyle,
            snapshot = effectiveSnapshot,
            darkTheme = isDarkTheme
        )
        val sanitizedBase = sanitizeVisuals(baseVisuals, isDarkTheme = isDarkTheme, backendTarget = backendTarget)
        var effective = computedBase.mergeWith(sanitizedBase)

        if (!enabled) {
            val computedDisabled = lastComputedStyle?.disabledStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            val sanitizedDisabled = disabledVisuals?.let { sanitizeVisuals(it, isDarkTheme, backendTarget) }
            effective = effective.mergeWith(computedDisabled).mergeWith(sanitizedDisabled)
        } else {
            if (hovered) {
                val computedHover = lastComputedStyle?.hoverStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
                val sanitizedHover = hoverVisuals?.let { sanitizeVisuals(it, isDarkTheme, backendTarget) }
                effective = effective.mergeWith(computedHover).mergeWith(sanitizedHover)
            }
            if (focused) {
                val computedFocus = lastComputedStyle?.focusStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
                val sanitizedFocus = focusVisuals?.let { sanitizeVisuals(it, isDarkTheme, backendTarget) }
                effective = effective.mergeWith(computedFocus).mergeWith(sanitizedFocus)
            }
            if (activePressed) {
                val computedActive = lastComputedStyle?.activeStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
                val sanitizedActive = activeVisuals?.let { sanitizeVisuals(it, isDarkTheme, backendTarget) }
                effective = effective.mergeWith(computedActive).mergeWith(sanitizedActive)
            }
        }

        if (backendTarget != null) {
            effective = effective.forBackend(backendTarget)
        }
        if (animatedOpacity != null) {
            effective = effective.copy(opacity = animatedOpacity.coerceIn(0f, 1f))
        }

        cachedVisuals = effective
        cachedIsDarkTheme = isDarkTheme
        cachedBackendTarget = backendTarget
        cachedAnimatedOpacity = animatedOpacity
        return effective
    }

    fun resolveEffectiveBackdropFilter(spec: StyledVisualSpec): CssFilter? =
        CssFilter.backdropBlurOrFilter(spec.backdropBlurPx, spec.backdropFilter)

    fun resolveEffectiveForegroundFilter(spec: StyledVisualSpec): CssFilter? {
        val foreground = spec.filter ?: return null
        return foreground.takeIf { !it.isNone && it.hasActiveEffects }
    }

    private fun sanitizeBootstrapSnapshot(
        snapshot: BootstrapStyleSnapshot?,
        backendTarget: LayoutBackendTarget?
    ): BootstrapStyleSnapshot? {
        if (snapshot == null) return null
        if (allowsEffectiveBootstrapTypeTint(backendTarget)) return snapshot
        return snapshot.copy(
            type = BootstrapType.NONE,
            outline = false,
            backgroundType = null,
            textColorType = null,
            borderType = null
        )
    }
}
