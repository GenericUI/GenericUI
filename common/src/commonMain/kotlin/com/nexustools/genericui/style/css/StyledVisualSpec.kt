package com.nexustools.genericui.style.css

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import kotlin.math.roundToInt

/**
 * Curated cross-platform visual style specification supported consistently across all 8 GenericUI
 * backends (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`, `:android`, `:ios`, `:web`) by
 * [com.nexustools.genericui.component.layout.StyledContainer] and
 * [com.nexustools.genericui.component.action.StyledButton].
 *
 * Covers solid & alpha-transparent backgrounds, linear/radial gradients, borders (width, style, color,
 * corner radius), foreground text/icon color, opacity, padding, explicit box dimensions, gap, box shadow,
 * focus ring styling, and CSS filter / backdrop-filter blur.
 */
data class StyledVisualSpec(
    val backgroundColor: RgbaColor? = null,
    val backgroundGradient: Gradient? = null,
    val textColor: RgbaColor? = null,
    val borderColor: RgbaColor? = null,
    val borderWidthPx: Float? = null,
    val borderStyle: CssBorderStyle? = null,
    val borderRadiusPx: Float? = null,
    val opacity: Float? = null,
    val padding: EdgeInsets? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val minWidthPx: Int? = null,
    val minHeightPx: Int? = null,
    val gapPx: Int? = null,
    val boxShadowColor: RgbaColor? = null,
    val boxShadowBlurPx: Float? = null,
    val focusRingColor: RgbaColor? = null,
    val focusRingWidthPx: Float? = null,
    val backdropBlurPx: Float? = null,
    val backdropFilter: CssFilter? = null,
    val filter: CssFilter? = null
) {
    val hasCustomBackground: Boolean
        get() = backgroundColor != null || backgroundGradient != null

    val hasBorder: Boolean
        get() = (borderWidthPx ?: 0f) > 0f && borderStyle != CssBorderStyle.NONE && borderStyle != CssBorderStyle.HIDDEN

    val hasFocusRing: Boolean
        get() = (focusRingWidthPx ?: 0f) > 0f && focusRingColor != null

    val hasBackdropBlur: Boolean
        get() = (backdropBlurPx ?: 0f) > 0f || (backdropFilter?.blur?.resolvePx() ?: 0f) > 0f

    /**
     * Merges [overrideSpec] on top of this [StyledVisualSpec], preferring non-null properties from [overrideSpec].
     */
    fun mergeWith(overrideSpec: StyledVisualSpec?): StyledVisualSpec {
        if (overrideSpec == null) return this
        return StyledVisualSpec(
            backgroundColor = overrideSpec.backgroundColor ?: backgroundColor,
            backgroundGradient = overrideSpec.backgroundGradient ?: backgroundGradient,
            textColor = overrideSpec.textColor ?: textColor,
            borderColor = overrideSpec.borderColor ?: borderColor,
            borderWidthPx = overrideSpec.borderWidthPx ?: borderWidthPx,
            borderStyle = overrideSpec.borderStyle ?: borderStyle,
            borderRadiusPx = overrideSpec.borderRadiusPx ?: borderRadiusPx,
            opacity = overrideSpec.opacity ?: opacity,
            padding = overrideSpec.padding ?: padding,
            widthPx = overrideSpec.widthPx ?: widthPx,
            heightPx = overrideSpec.heightPx ?: heightPx,
            minWidthPx = overrideSpec.minWidthPx ?: minWidthPx,
            minHeightPx = overrideSpec.minHeightPx ?: minHeightPx,
            gapPx = overrideSpec.gapPx ?: gapPx,
            boxShadowColor = overrideSpec.boxShadowColor ?: boxShadowColor,
            boxShadowBlurPx = overrideSpec.boxShadowBlurPx ?: boxShadowBlurPx,
            focusRingColor = overrideSpec.focusRingColor ?: focusRingColor,
            focusRingWidthPx = overrideSpec.focusRingWidthPx ?: focusRingWidthPx,
            backdropBlurPx = overrideSpec.backdropBlurPx ?: backdropBlurPx,
            backdropFilter = overrideSpec.backdropFilter ?: backdropFilter,
            filter = overrideSpec.filter ?: filter
        )
    }

    /**
     * Filters this [StyledVisualSpec] for the given [backendTarget]:
     * - On `WEB`, `ANDROID`, and `IOS`, retains all properties including [backdropBlurPx] and [backdropFilter].
     * - On Desktop targets (`WINDOWS`, `MACOS`, `GTK`, `QT`, `SWING`), strips [backdropBlurPx] and [backdropFilter]
     *   while preserving alpha-translucent [backgroundColor], [backgroundGradient], borders, shadows, and geometry.
     */
    fun forBackend(backendTarget: LayoutBackendTarget): StyledVisualSpec = when (backendTarget) {
        LayoutBackendTarget.WEB,
        LayoutBackendTarget.ANDROID,
        LayoutBackendTarget.IOS -> this
        LayoutBackendTarget.WINDOWS,
        LayoutBackendTarget.MACOS,
        LayoutBackendTarget.GTK,
        LayoutBackendTarget.QT,
        LayoutBackendTarget.SWING -> copy(
            backdropBlurPx = null,
            backdropFilter = null
        )
    }

    companion object {
        val EMPTY = StyledVisualSpec()

        private val BLUR_FN_REGEX = Regex("""blur\(\s*(\d+(?:\.\d+)?)\s*(?:px)?\s*\)""", RegexOption.IGNORE_CASE)

        private fun parseBackdropBlurPx(filter: CssFilter?, rawBackdropCss: String?): Float? {
            filter?.blur?.resolvePx()?.takeIf { it > 0f }?.let { return it }
            if (!rawBackdropCss.isNullOrBlank()) {
                val match = BLUR_FN_REGEX.find(rawBackdropCss)
                val parsed = match?.groupValues?.getOrNull(1)?.toFloatOrNull()
                if (parsed != null && parsed > 0f) return parsed
            }
            return null
        }

        private fun resolveAbsolutePxInt(length: CssLength?): Int? {
            if (length == null || length.isAuto || length.isNone || length.unit == CssUnit.PERCENT) {
                return null
            }
            return length.resolvePx()?.roundToInt()
        }

        private fun parseCssPxInt(raw: String?): Int? =
            resolveAbsolutePxInt(CssLength.parse(raw))

        private fun resolveComponentStylePadding(componentStyle: ComponentStyle?): EdgeInsets? {
            if (componentStyle == null) return null
            val base = componentStyle.getPadding()?.toEdgeInsets()
            val top = resolveAbsolutePxInt(componentStyle.paddingTop)
            val right = resolveAbsolutePxInt(componentStyle.paddingRight)
            val bottom = resolveAbsolutePxInt(componentStyle.paddingBottom)
            val left = resolveAbsolutePxInt(componentStyle.paddingLeft)
            if (base == null && top == null && right == null && bottom == null && left == null) {
                return null
            }
            return EdgeInsets(
                top = top ?: base?.top ?: 0,
                right = right ?: base?.right ?: 0,
                bottom = bottom ?: base?.bottom ?: 0,
                left = left ?: base?.left ?: 0
            )
        }

        private fun resolveComputedStylePadding(computed: ComputedStyle?): EdgeInsets? {
            if (computed == null) return null
            val base = computed.padding?.toEdgeInsets()
            val top = resolveAbsolutePxInt(computed.paddingTop)
            val right = resolveAbsolutePxInt(computed.paddingRight)
            val bottom = resolveAbsolutePxInt(computed.paddingBottom)
            val left = resolveAbsolutePxInt(computed.paddingLeft)
            if (base == null && top == null && right == null && bottom == null && left == null) {
                return null
            }
            return EdgeInsets(
                top = top ?: base?.top ?: 0,
                right = right ?: base?.right ?: 0,
                bottom = bottom ?: base?.bottom ?: 0,
                left = left ?: base?.left ?: 0
            )
        }

        private fun resolveSnapshotPadding(snapshot: BootstrapStyleSnapshot?): EdgeInsets? {
            if (snapshot == null) return null
            val hasAny = snapshot.paddingAll != null ||
                snapshot.paddingX != null ||
                snapshot.paddingY != null ||
                snapshot.paddingTop != null ||
                snapshot.paddingBottom != null ||
                snapshot.paddingStart != null ||
                snapshot.paddingEnd != null
            if (!hasAny) return null
            val top = (snapshot.paddingTop ?: snapshot.paddingY ?: snapshot.paddingAll)?.toPixels() ?: 0
            val bottom = (snapshot.paddingBottom ?: snapshot.paddingY ?: snapshot.paddingAll)?.toPixels() ?: 0
            val left = (snapshot.paddingStart ?: snapshot.paddingX ?: snapshot.paddingAll)?.toPixels() ?: 0
            val right = (snapshot.paddingEnd ?: snapshot.paddingX ?: snapshot.paddingAll)?.toPixels() ?: 0
            return EdgeInsets(top = top, right = right, bottom = bottom, left = left)
        }

        fun fromComputedStyle(computed: ComputedStyle?): StyledVisualSpec? {
            if (computed == null) return null
            val borderStyleEnum = CssBorderStyle.fromCss(computed.borderStyle)
            val parsedFocusRingColor = computed.focusRingColor
                ?: computed.outlineColor
                ?: computed.boxShadow?.let { raw ->
                    Regex("(rgba?\\([^)]+\\)|#[0-9a-fA-F]{3,8})").find(raw)?.value?.let { RgbaColor.fromCss(it) }
                }
            val parsedFocusRingWidth = computed.focusRingWidthPx
                ?: computed.outlineWidthPx
                ?: computed.boxShadow?.let { raw ->
                    Regex("(\\d+(?:\\.\\d+)?)px").find(raw)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                }
            val parsedPadding = resolveComputedStylePadding(computed)
            val parsedWidthPx = parseCssPxInt(computed.width)
            val parsedHeightPx = parseCssPxInt(computed.height)
            val parsedMinWidthPx = parseCssPxInt(computed.minWidth)
            val parsedMinHeightPx = parseCssPxInt(computed.minHeight)
            val parsedGapPx = resolveAbsolutePxInt(computed.gap ?: computed.columnGap)

            val parsedBackdropFilter = computed.backdropFilter
            val parsedBackdropBlurPx = parseBackdropBlurPx(parsedBackdropFilter, null)
            val parsedFilter = computed.filter

            val rawBoxShadow = computed.boxShadow
            val parsedShadow = (computed.boxShadowList ?: CssShadowList.parse(rawBoxShadow))?.shadows?.firstOrNull()
            val parsedShadowColor = parsedShadow?.color
            val parsedShadowBlurPx = parsedShadow?.blurRadius?.resolvePx()

            val hasAny = computed.backgroundColor != null ||
                computed.backgroundGradient != null ||
                computed.textColor != null ||
                computed.borderColor != null ||
                computed.borderWidthPx != null ||
                borderStyleEnum != null ||
                computed.borderRadiusPx != null ||
                computed.hasProperty(CssProperty.OPACITY) ||
                computed.opacity != 1.0f ||
                parsedPadding != null ||
                parsedWidthPx != null ||
                parsedHeightPx != null ||
                parsedMinWidthPx != null ||
                parsedMinHeightPx != null ||
                parsedGapPx != null ||
                parsedFocusRingColor != null ||
                parsedShadowColor != null ||
                parsedShadowBlurPx != null ||
                parsedBackdropBlurPx != null ||
                parsedBackdropFilter != null ||
                parsedFilter != null
            if (!hasAny) return null
            return StyledVisualSpec(
                backgroundColor = computed.backgroundColor,
                backgroundGradient = computed.backgroundGradient,
                textColor = computed.textColor,
                borderColor = computed.borderColor,
                borderWidthPx = computed.borderWidthPx,
                borderStyle = borderStyleEnum,
                borderRadiusPx = computed.borderRadiusPx,
                opacity = if (computed.hasProperty(CssProperty.OPACITY) || computed.opacity != 1.0f) computed.opacity else null,
                padding = parsedPadding,
                widthPx = parsedWidthPx,
                heightPx = parsedHeightPx,
                minWidthPx = parsedMinWidthPx,
                minHeightPx = parsedMinHeightPx,
                gapPx = parsedGapPx,
                boxShadowColor = parsedShadowColor,
                boxShadowBlurPx = parsedShadowBlurPx,
                focusRingColor = parsedFocusRingColor,
                focusRingWidthPx = parsedFocusRingWidth,
                backdropBlurPx = parsedBackdropBlurPx,
                backdropFilter = parsedBackdropFilter,
                filter = parsedFilter
            )
        }

        fun fromComponentAndComputed(
            componentStyle: ComponentStyle?,
            computedStyle: ComputedStyle?,
            snapshot: BootstrapStyleSnapshot? = null,
            darkTheme: Boolean = true
        ): StyledVisualSpec {
            val semanticType = snapshot?.type?.takeIf { it.isSemantic }
                ?: snapshot?.backgroundType?.takeIf { it.isSemantic }
            val outline = snapshot?.outline == true

            val resolvedBg = componentStyle?.backgroundColor
                ?: computedStyle?.backgroundColor
                ?: if (semanticType != null && !outline) BootstrapColorPalette.resolveColor(semanticType, darkTheme) else null

            val resolvedGrad = componentStyle?.backgroundImage?.gradient
                ?: computedStyle?.backgroundGradient

            val resolvedText = componentStyle?.color
                ?: computedStyle?.textColor
                ?: snapshot?.textColorType?.takeIf { it.isSemantic }?.let { BootstrapColorPalette.resolveColor(it, darkTheme) }
                ?: if (semanticType != null) {
                    if (outline) BootstrapColorPalette.resolveColor(semanticType, darkTheme)
                    else BootstrapColorPalette.resolveForegroundOn(semanticType)
                } else null

            val resolvedBorderColor = componentStyle?.borderColor
                ?: computedStyle?.borderColor
                ?: snapshot?.borderType?.takeIf { it.isSemantic }?.let { BootstrapColorPalette.resolveColor(it, darkTheme) }
                ?: if (semanticType != null && outline) BootstrapColorPalette.resolveColor(semanticType, darkTheme) else null

            val resolvedBorderWidth = componentStyle?.borderWidthPx
                ?: computedStyle?.borderWidthPx
                ?: snapshot?.borderWidth?.widthPx?.toFloat()
                ?: if (resolvedBorderColor != null) 1f else null

            val resolvedBorderStyle = componentStyle?.borderStyle
                ?: CssBorderStyle.fromCss(computedStyle?.borderStyle)
                ?: if ((resolvedBorderWidth ?: 0f) > 0f) CssBorderStyle.SOLID else null

            val snapshotRadiusPx = when (snapshot?.borderRadius) {
                BorderRadiusStyle.NONE -> 0f
                BorderRadiusStyle.SMALL -> 4f
                BorderRadiusStyle.DEFAULT, BorderRadiusStyle.MEDIUM -> 6f
                BorderRadiusStyle.LARGE -> 8f
                BorderRadiusStyle.EXTRA_LARGE -> 12f
                BorderRadiusStyle.XXL -> 16f
                BorderRadiusStyle.PILL, BorderRadiusStyle.CIRCLE -> 999f
                null -> null
            }
            val resolvedRadiusPx = componentStyle?.borderRadius?.topLeft?.resolvePx()
                ?: computedStyle?.borderRadiusPx
                ?: snapshotRadiusPx

            val resolvedOpacity = componentStyle?.opacity
                ?: if (computedStyle != null && (computedStyle.hasProperty(CssProperty.OPACITY) || computedStyle.opacity != 1.0f)) {
                    computedStyle.opacity
                } else {
                    snapshot?.opacity?.alpha
                }

            val resolvedPadding = resolveComponentStylePadding(componentStyle)
                ?: resolveComputedStylePadding(computedStyle)
                ?: resolveSnapshotPadding(snapshot)

            val resolvedWidthPx = resolveAbsolutePxInt(componentStyle?.width)
                ?: parseCssPxInt(computedStyle?.width)

            val resolvedHeightPx = resolveAbsolutePxInt(componentStyle?.height)
                ?: parseCssPxInt(computedStyle?.height)

            val resolvedMinWidthPx = resolveAbsolutePxInt(componentStyle?.minWidth)
                ?: parseCssPxInt(computedStyle?.minWidth)

            val resolvedMinHeightPx = resolveAbsolutePxInt(componentStyle?.minHeight)
                ?: parseCssPxInt(computedStyle?.minHeight)

            val resolvedGapPx = resolveAbsolutePxInt(componentStyle?.gap ?: componentStyle?.columnGap)
                ?: resolveAbsolutePxInt(computedStyle?.gap ?: computedStyle?.columnGap)
                ?: (snapshot?.gap ?: snapshot?.columnGap)?.toPixels()

            val rawBoxShadow = computedStyle?.boxShadow
            val firstShadow = componentStyle?.boxShadow?.shadows?.firstOrNull()
                ?: computedStyle?.boxShadowList?.shadows?.firstOrNull()
                ?: CssShadowList.parse(rawBoxShadow)?.shadows?.firstOrNull()
            val snapshotShadowBlur = when (snapshot?.shadow) {
                ShadowElevation.NONE -> 0f
                ShadowElevation.SMALL -> 4f
                ShadowElevation.DEFAULT -> 10f
                ShadowElevation.LARGE -> 20f
                null -> null
            }
            val resolvedShadowColor = firstShadow?.color
                ?: if ((snapshotShadowBlur ?: 0f) > 0f) RgbaColor(0, 0, 0, 0.35f) else null
            val resolvedShadowBlur = firstShadow?.blurRadius?.resolvePx() ?: snapshotShadowBlur

            val resolvedBackdropFilter = componentStyle?.backdropFilter
                ?: computedStyle?.backdropFilter
            val resolvedBackdropBlurPx = parseBackdropBlurPx(resolvedBackdropFilter, null)
            val resolvedFilter = componentStyle?.filter
                ?: computedStyle?.filter

            return StyledVisualSpec(
                backgroundColor = resolvedBg,
                backgroundGradient = resolvedGrad,
                textColor = resolvedText,
                borderColor = resolvedBorderColor,
                borderWidthPx = resolvedBorderWidth,
                borderStyle = resolvedBorderStyle,
                borderRadiusPx = resolvedRadiusPx,
                opacity = resolvedOpacity,
                padding = resolvedPadding,
                widthPx = resolvedWidthPx,
                heightPx = resolvedHeightPx,
                minWidthPx = resolvedMinWidthPx,
                minHeightPx = resolvedMinHeightPx,
                gapPx = resolvedGapPx,
                boxShadowColor = resolvedShadowColor,
                boxShadowBlurPx = resolvedShadowBlur,
                backdropBlurPx = resolvedBackdropBlurPx,
                backdropFilter = resolvedBackdropFilter,
                filter = resolvedFilter
            )
        }
    }
}
