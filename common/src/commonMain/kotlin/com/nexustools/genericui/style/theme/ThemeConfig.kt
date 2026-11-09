package com.nexustools.genericui.style.theme

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.roundToInt

/**
 * Cross-platform GenericUI theme configuration (`ThemeConfig`), unifying:
 * - Android Material Design theme attributes (`colorPrimary`, `colorSecondary`, `colorAccent`, `colorError`, etc.)
 * - Windows & Desktop/Mobile native control tinting (`BootstrapType` tinting over native OS controls)
 * - Web pre-generated (`gui/genericui-theme.css`) and runtime (`#genericui-runtime-theme-css`) Bootstrap 5 CSS overrides.
 */
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.AUTO,
    val colors: ThemeColorSet = ThemeColorSet.EMPTY,
    val lightColors: ThemeColorSet = ThemeColorSet.EMPTY,
    val darkColors: ThemeColorSet = ThemeColorSet.EMPTY,
    val styleSheet: String? = null
) {
    val hasColorOverrides: Boolean
        get() = !colors.isEmpty || !lightColors.isEmpty || !darkColors.isEmpty

    fun withPrimaryAccentColor(colorHex: String): ThemeConfig {
        val trimmed = colorHex.trim()
        if (trimmed.isEmpty()) return this
        val parsed = RgbaColor.fromCss(trimmed) ?: RgbaColor.fromHex(trimmed)
        return copy(colors = colors.copy(primary = parsed, accent = parsed))
    }

    fun withMode(mode: ThemeMode): ThemeConfig = copy(mode = mode)

    /**
     * Resolves the effective [ThemeColorSet] for [isDark], merging mode-specific overrides
     * ([darkColors] or [lightColors]) on top of the shared [colors] set.
     */
    fun resolveEffectiveColors(isDark: Boolean): ThemeColorSet {
        val modeSet = if (isDark) darkColors else lightColors
        return colors.mergeWith(modeSet)
    }

    /**
     * Resolves an explicit theme override color for [type] in the given [isDark] mode, or `null`
     * if no override was configured for [type].
     */
    fun resolveBootstrapColor(type: BootstrapType, isDark: Boolean): RgbaColor? {
        val effective = resolveEffectiveColors(isDark)
        return when (type) {
            BootstrapType.NONE -> null
            BootstrapType.PRIMARY -> effective.primary ?: effective.accent
            BootstrapType.SECONDARY -> effective.secondary
            BootstrapType.SUCCESS -> effective.success
            BootstrapType.INFO -> effective.info
            BootstrapType.WARNING -> effective.warning
            BootstrapType.DANGER -> effective.danger
            BootstrapType.LIGHT -> effective.light
            BootstrapType.DARK -> effective.dark
            BootstrapType.LINK -> effective.primary ?: effective.accent
        }
    }

    /**
     * Resolves an explicit or auto-contrasting foreground text/icon color on top of [type]
     * in the given [isDark] mode, or `null` if no override was configured for [type].
     */
    fun resolveBootstrapForeground(type: BootstrapType, isDark: Boolean): RgbaColor? {
        val effective = resolveEffectiveColors(isDark)
        val explicitOn = when (type) {
            BootstrapType.NONE -> null
            BootstrapType.PRIMARY -> effective.onPrimary
            BootstrapType.SECONDARY -> effective.onSecondary
            BootstrapType.SUCCESS -> effective.onSuccess
            BootstrapType.INFO -> effective.onInfo
            BootstrapType.WARNING -> effective.onWarning
            BootstrapType.DANGER -> effective.onDanger
            BootstrapType.LIGHT -> effective.onLight
            BootstrapType.DARK -> effective.onDark
            BootstrapType.LINK -> effective.onPrimary
        }
        if (explicitOn != null) return explicitOn
        val bg = resolveBootstrapColor(type, isDark) ?: return null
        return autoContrastForeground(bg)
    }

    /**
     * Generates a complete CSS stylesheet overriding Bootstrap 5's CSS custom properties and
     * semantic component rules (`.btn-*`, `.btn-outline-*`, `.bg-*`, `.text-*`, `.border-*`,
     * `.text-bg-*`, `.alert-*`, `.badge.bg-*`, `.progress-bar.bg-*`, `.form-check-input:checked`,
     * `.form-range::-webkit-slider-thumb`) to match this [ThemeConfig].
     */
    fun generateWebBootstrapOverrideCss(): String = buildString {
        val lightSet = resolveEffectiveColors(isDark = false)
        val darkSet = resolveEffectiveColors(isDark = true)

        appendLine("/* GenericUI ThemeConfig Bootstrap Overrides */")
        emitScopeCss(selector = ":root, [data-bs-theme=\"light\"]", colorSet = lightSet, isDark = false)
        emitScopeCss(selector = "[data-bs-theme=\"dark\"]", colorSet = darkSet, isDark = true)
    }

    private fun StringBuilder.emitScopeCss(
        selector: String,
        colorSet: ThemeColorSet,
        isDark: Boolean
    ) {
        val primary = colorSet.primary ?: colorSet.accent
        val hasAnyInScope = primary != null ||
            colorSet.secondary != null ||
            colorSet.success != null ||
            colorSet.info != null ||
            colorSet.warning != null ||
            colorSet.danger != null ||
            colorSet.light != null ||
            colorSet.dark != null ||
            colorSet.background != null ||
            colorSet.onBackground != null ||
            colorSet.surface != null ||
            colorSet.onSurface != null

        if (hasAnyInScope) {
            appendLine("$selector {")
            if (primary != null) {
                val hex = primary.toHexRgb()
                val rgbTuple = "${primary.r}, ${primary.g}, ${primary.b}"
                appendLine("  --bs-primary: $hex;")
                appendLine("  --bs-primary-rgb: $rgbTuple;")
                appendLine("  --bs-link-color: $hex;")
                appendLine("  --bs-link-color-rgb: $rgbTuple;")
                val hover = shiftLuminance(primary, if (isDark) 0.12f else -0.12f)
                appendLine("  --bs-link-hover-color: ${hover.toHexRgb()};")
                appendLine("  --bs-link-hover-color-rgb: ${hover.r}, ${hover.g}, ${hover.b};")
                appendLine("  --genericui-theme-color: $hex;")
                appendLine("  --genericui-accent: $hex;")
            }
            emitRootVarPair("secondary", colorSet.secondary)
            emitRootVarPair("success", colorSet.success)
            emitRootVarPair("info", colorSet.info)
            emitRootVarPair("warning", colorSet.warning)
            emitRootVarPair("danger", colorSet.danger)
            emitRootVarPair("light", colorSet.light)
            emitRootVarPair("dark", colorSet.dark)
            if (colorSet.background != null) {
                val bg = colorSet.background
                appendLine("  --bs-body-bg: ${bg.toHexRgb()};")
                appendLine("  --bs-body-bg-rgb: ${bg.r}, ${bg.g}, ${bg.b};")
            }
            if (colorSet.onBackground != null) {
                val fg = colorSet.onBackground
                appendLine("  --bs-body-color: ${fg.toHexRgb()};")
                appendLine("  --bs-body-color-rgb: ${fg.r}, ${fg.g}, ${fg.b};")
            }
            if (colorSet.surface != null) {
                val surf = colorSet.surface
                appendLine("  --bs-tertiary-bg: ${surf.toHexRgb()};")
                appendLine("  --bs-tertiary-bg-rgb: ${surf.r}, ${surf.g}, ${surf.b};")
            }
            appendLine("}")
        }

        emitSemanticComponentRules(selector, "primary", primary, colorSet.onPrimary, colorSet.primaryVariant)
        emitSemanticComponentRules(selector, "secondary", colorSet.secondary, colorSet.onSecondary, colorSet.secondaryVariant)
        emitSemanticComponentRules(selector, "success", colorSet.success, colorSet.onSuccess, null)
        emitSemanticComponentRules(selector, "info", colorSet.info, colorSet.onInfo, null)
        emitSemanticComponentRules(selector, "warning", colorSet.warning, colorSet.onWarning, null)
        emitSemanticComponentRules(selector, "danger", colorSet.danger, colorSet.onDanger, null)
        emitSemanticComponentRules(selector, "light", colorSet.light, colorSet.onLight, null)
        emitSemanticComponentRules(selector, "dark", colorSet.dark, colorSet.onDark, null)

        if (primary != null) {
            val hex = primary.toHexRgb()
            appendLine("$selector .form-check-input:checked { background-color: $hex; border-color: $hex; }")
            appendLine("$selector .form-range::-webkit-slider-thumb { background-color: $hex; }")
            appendLine("$selector .form-range::-moz-range-thumb { background-color: $hex; }")
            appendLine("$selector .nav-pills .nav-link.active { background-color: $hex; color: ${(colorSet.onPrimary ?: autoContrastForeground(primary)).toHexRgb()}; }")
        }
    }

    private fun StringBuilder.emitRootVarPair(name: String, color: RgbaColor?) {
        if (color == null) return
        appendLine("  --bs-$name: ${color.toHexRgb()};")
        appendLine("  --bs-$name-rgb: ${color.r}, ${color.g}, ${color.b};")
    }

    private fun StringBuilder.emitSemanticComponentRules(
        scopeSelector: String,
        name: String,
        baseColor: RgbaColor?,
        explicitOnColor: RgbaColor?,
        variantColor: RgbaColor?
    ) {
        if (baseColor == null) return
        val fg = explicitOnColor ?: autoContrastForeground(baseColor)
        val hoverBg = variantColor ?: shiftLuminance(baseColor, -0.10f)
        val activeBg = shiftLuminance(baseColor, -0.16f)
        val subtleBg = mixWith(baseColor, RgbaColor(255, 255, 255), 0.82f)
        val subtleBorder = mixWith(baseColor, RgbaColor(255, 255, 255), 0.62f)
        val baseHex = baseColor.toHexRgb()
        val fgHex = fg.toHexRgb()
        val hoverHex = hoverBg.toHexRgb()
        val activeHex = activeBg.toHexRgb()

        appendLine("$scopeSelector .btn-$name {")
        appendLine("  --bs-btn-color: $fgHex;")
        appendLine("  --bs-btn-bg: $baseHex;")
        appendLine("  --bs-btn-border-color: $baseHex;")
        appendLine("  --bs-btn-hover-color: $fgHex;")
        appendLine("  --bs-btn-hover-bg: $hoverHex;")
        appendLine("  --bs-btn-hover-border-color: $hoverHex;")
        appendLine("  --bs-btn-active-color: $fgHex;")
        appendLine("  --bs-btn-active-bg: $activeHex;")
        appendLine("  --bs-btn-active-border-color: $activeHex;")
        appendLine("  --bs-btn-disabled-color: $fgHex;")
        appendLine("  --bs-btn-disabled-bg: $baseHex;")
        appendLine("  --bs-btn-disabled-border-color: $baseHex;")
        appendLine("  background-color: $baseHex;")
        appendLine("  border-color: $baseHex;")
        appendLine("  color: $fgHex;")
        appendLine("}")

        appendLine("$scopeSelector .btn-outline-$name {")
        appendLine("  --bs-btn-color: $baseHex;")
        appendLine("  --bs-btn-border-color: $baseHex;")
        appendLine("  --bs-btn-hover-color: $fgHex;")
        appendLine("  --bs-btn-hover-bg: $baseHex;")
        appendLine("  --bs-btn-hover-border-color: $baseHex;")
        appendLine("  --bs-btn-active-color: $fgHex;")
        appendLine("  --bs-btn-active-bg: $activeHex;")
        appendLine("  --bs-btn-active-border-color: $activeHex;")
        appendLine("  --bs-btn-disabled-color: $baseHex;")
        appendLine("  --bs-btn-disabled-border-color: $baseHex;")
        appendLine("  color: $baseHex;")
        appendLine("  border-color: $baseHex;")
        appendLine("}")

        appendLine("$scopeSelector .bg-$name { background-color: $baseHex !important; }")
        appendLine("$scopeSelector .text-$name { color: $baseHex !important; }")
        appendLine("$scopeSelector .border-$name { border-color: $baseHex !important; }")
        appendLine("$scopeSelector .text-bg-$name { color: $fgHex !important; background-color: $baseHex !important; }")
        appendLine("$scopeSelector .badge.bg-$name { color: $fgHex; background-color: $baseHex !important; }")
        appendLine("$scopeSelector .progress-bar.bg-$name { background-color: $baseHex !important; }")
        appendLine("$scopeSelector .alert-$name {")
        appendLine("  --bs-alert-color: ${shiftLuminance(baseColor, -0.35f).toHexRgb()};")
        appendLine("  --bs-alert-bg: ${subtleBg.toHexRgb()};")
        appendLine("  --bs-alert-border-color: ${subtleBorder.toHexRgb()};")
        appendLine("}")
    }

    companion object {
        val DEFAULT = ThemeConfig()

        fun autoContrastForeground(bg: RgbaColor): RgbaColor {
            val luma = (bg.r * 299 + bg.g * 587 + bg.b * 114) / 1000
            return if (luma >= 150) RgbaColor(33, 37, 41) else RgbaColor(255, 255, 255)
        }

        internal fun shiftLuminance(color: RgbaColor, delta: Float): RgbaColor {
            val factor = 1f + delta
            val r = (color.r * factor).roundToInt().coerceIn(0, 255)
            val g = (color.g * factor).roundToInt().coerceIn(0, 255)
            val b = (color.b * factor).roundToInt().coerceIn(0, 255)
            return RgbaColor(r, g, b, color.a)
        }

        internal fun mixWith(a: RgbaColor, b: RgbaColor, weightB: Float): RgbaColor {
            val wb = weightB.coerceIn(0f, 1f)
            val wa = 1f - wb
            return RgbaColor(
                r = (a.r * wa + b.r * wb).roundToInt().coerceIn(0, 255),
                g = (a.g * wa + b.g * wb).roundToInt().coerceIn(0, 255),
                b = (a.b * wa + b.b * wb).roundToInt().coerceIn(0, 255),
                a = a.a
            )
        }

        private fun RgbaColor.toHexRgb(): String {
            fun hex2(v: Int): String = v.coerceIn(0, 255).toString(16).padStart(2, '0')
            return "#${hex2(r)}${hex2(g)}${hex2(b)}"
        }
    }
}
