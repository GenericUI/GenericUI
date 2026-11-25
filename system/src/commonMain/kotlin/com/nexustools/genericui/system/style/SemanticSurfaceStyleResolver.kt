package com.nexustools.genericui.system.style

import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlin.math.abs

/**
 * Canonical Bootstrap and shell semantic surface resolver shared across backends.
 *
 * Resolves semantic visual chrome for `Card`, `Alert`, `Banner`, `Toast`, `ListGroup`,
 * `ListGroupItem`, `Jumbotron`, `Well`, `Badge`, `MdiWindow`, and `MdiDesktop` while returning
 * `null` for plain layout containers (`Container`, `HStack`, `VStack`, `FlexBox`,
 * `LayeredContainer`, `Fragment`).
 */
object SemanticSurfaceStyleResolver {

    private val PLAIN_CONTAINER_TAGS = setOf(
        "container",
        "hstack",
        "h-stack",
        "vstack",
        "v-stack",
        "flexbox",
        "flex-box",
        "layeredcontainer",
        "layered-container",
        "fragment"
    )

    /**
     * Resolves a [SemanticSurfacePaintSpec] from [tagName], [classTokens], [snapshot], and
     * [darkTheme], returning `null` for plain unstyled layout containers.
     */
    fun resolveForTagAndClasses(
        tagName: String?,
        classTokens: Set<String>,
        snapshot: BootstrapStyleSnapshot?,
        darkTheme: Boolean
    ): SemanticSurfacePaintSpec? {
        val normalizedTag = normalizeTag(tagName)
        val normalizedTokens = normalizeTokens(classTokens, snapshot)

        val isPlainContainerTag = normalizedTag in PLAIN_CONTAINER_TAGS
        val hasSemanticSurfaceToken = hasAnySemanticSurfaceToken(normalizedTokens)
        if (isPlainContainerTag && !hasSemanticSurfaceToken) {
            return null
        }

        val config: ThemeConfig = BootstrapColorPalette.activeThemeConfig
        val colors = config.resolveEffectiveColors(darkTheme)
        val bgBase = colors.background
            ?: if (darkTheme) RgbaColor(28, 30, 36, 255) else RgbaColor(246, 247, 250, 255)
        val surfaceBase = colors.surface
            ?: if (darkTheme) RgbaColor(40, 43, 52, 255) else RgbaColor(255, 255, 255, 255)
        val onSurface = colors.onSurface
            ?: colors.onBackground
            ?: if (darkTheme) RgbaColor(238, 240, 246, 255) else RgbaColor(24, 28, 36, 255)
        val neutralBorder = blendRgb(
            surfaceBase,
            onSurface,
            if (darkTheme) 0.16f else 0.14f
        ).copy(a = 255)

        val semType = snapshot?.backgroundType?.takeIf { it.isSemantic }
            ?: snapshot?.type?.takeIf { it.isSemantic }
        val borderType = snapshot?.borderType?.takeIf { it.isSemantic }
        val textType = snapshot?.textColorType?.takeIf { it.isSemantic }

        return when {
            normalizedTag == "card" || "card" in normalizedTokens -> {
                val cardBg = if (semType != null) {
                    BootstrapColorPalette.resolveColor(semType, darkTheme, config)
                } else {
                    surfaceBase
                }
                val cardFg = when {
                    textType != null -> BootstrapColorPalette.resolveColor(textType, darkTheme, config)
                    semType != null -> BootstrapColorPalette.resolveForegroundOn(semType, darkTheme, config)
                    else -> onSurface
                }
                SemanticSurfacePaintSpec(
                    backgroundColor = cardBg,
                    borderColor = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: neutralBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 8f),
                    textColor = ensureReadableTextOn(cardFg, cardBg),
                    padding = EdgeInsets.all(16),
                    shadowColor = RgbaColor(0, 0, 0, if (darkTheme) 70 else 20),
                    shadowBlurPx = 4f,
                    elevated = true
                )
            }

            normalizedTag == "alert" || "alert" in normalizedTokens -> {
                val alertType = semType ?: BootstrapType.INFO
                val semantic = BootstrapColorPalette.resolveColor(alertType, darkTheme, config)
                val alertBg = blendRgb(surfaceBase, semantic, if (darkTheme) 0.24f else 0.14f).copy(a = 248)
                val alertBorder = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                    ?: blendRgb(surfaceBase, semantic, if (darkTheme) 0.55f else 0.38f).copy(a = 255)
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                    ?: if (darkTheme) blendRgb(semantic, RgbaColor.WHITE, 0.72f) else blendRgb(semantic, RgbaColor.BLACK, 0.45f)
                SemanticSurfacePaintSpec(
                    backgroundColor = alertBg,
                    borderColor = alertBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 6f),
                    textColor = ensureReadableTextOn(preferredFg, alertBg),
                    padding = EdgeInsets.symmetric(vertical = 12, horizontal = 16),
                    elevated = false
                )
            }

            normalizedTag == "banner" || "banner" in normalizedTokens -> {
                val bannerType = semType ?: BootstrapType.PRIMARY
                val semantic = BootstrapColorPalette.resolveColor(bannerType, darkTheme, config)
                val bannerBg = blendRgb(surfaceBase, semantic, if (darkTheme) 0.28f else 0.16f).copy(a = 250)
                val bannerBorder = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                    ?: blendRgb(surfaceBase, semantic, if (darkTheme) 0.58f else 0.42f).copy(a = 255)
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                    ?: if (darkTheme) blendRgb(semantic, RgbaColor.WHITE, 0.74f) else blendRgb(semantic, RgbaColor.BLACK, 0.42f)
                SemanticSurfacePaintSpec(
                    backgroundColor = bannerBg,
                    borderColor = bannerBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 8f),
                    textColor = ensureReadableTextOn(preferredFg, bannerBg),
                    padding = EdgeInsets.symmetric(vertical = 12, horizontal = 18),
                    shadowColor = RgbaColor(0, 0, 0, if (darkTheme) 80 else 24),
                    shadowBlurPx = 6f,
                    elevated = true
                )
            }

            normalizedTag == "toast" || "toast" in normalizedTokens -> {
                val semantic = semType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                val toastBg = if (semantic != null) {
                    blendRgb(surfaceBase, semantic, if (darkTheme) 0.26f else 0.14f).copy(a = 248)
                } else {
                    surfaceBase.copy(a = 248)
                }
                val toastBorder = when {
                    borderType != null -> BootstrapColorPalette.resolveColor(borderType, darkTheme, config)
                    semantic != null -> blendRgb(surfaceBase, semantic, if (darkTheme) 0.55f else 0.35f).copy(a = 255)
                    else -> neutralBorder
                }
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: onSurface
                SemanticSurfacePaintSpec(
                    backgroundColor = toastBg,
                    borderColor = toastBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 8f),
                    textColor = ensureReadableTextOn(preferredFg, toastBg),
                    padding = EdgeInsets.symmetric(vertical = 10, horizontal = 14),
                    shadowColor = RgbaColor(0, 0, 0, if (darkTheme) 110 else 36),
                    shadowBlurPx = 10f,
                    elevated = true
                )
            }

            normalizedTag == "listgroupitem" || normalizedTag == "list-group-item" || "list-group-item" in normalizedTokens -> {
                val active = "active" in normalizedTokens
                val accent = colors.primary ?: colors.accent ?: BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, darkTheme, config)
                val itemBg = when {
                    active -> semType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: accent
                    semType != null -> blendRgb(
                        surfaceBase,
                        BootstrapColorPalette.resolveColor(semType, darkTheme, config),
                        if (darkTheme) 0.22f else 0.12f
                    ).copy(a = 255)
                    else -> surfaceBase
                }
                val itemBorder = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                    ?: if (active) itemBg else neutralBorder
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                    ?: if (active) RgbaColor.WHITE else onSurface
                SemanticSurfacePaintSpec(
                    backgroundColor = itemBg,
                    borderColor = itemBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 4f),
                    textColor = ensureReadableTextOn(preferredFg, itemBg),
                    padding = EdgeInsets.symmetric(vertical = 8, horizontal = 16),
                    elevated = false
                )
            }

            normalizedTag == "listgroup" || normalizedTag == "list-group" || "list-group" in normalizedTokens -> {
                val groupBg = semType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: surfaceBase
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: onSurface
                SemanticSurfacePaintSpec(
                    backgroundColor = groupBg,
                    borderColor = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: neutralBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 8f),
                    textColor = ensureReadableTextOn(preferredFg, groupBg),
                    padding = EdgeInsets.ZERO,
                    elevated = false
                )
            }

            normalizedTag == "jumbotron" || "jumbotron" in normalizedTokens -> {
                val jumboBase = blendRgb(bgBase, onSurface, if (darkTheme) 0.08f else 0.04f).copy(a = 255)
                val jumboBg = if (semType != null) {
                    blendRgb(jumboBase, BootstrapColorPalette.resolveColor(semType, darkTheme, config), if (darkTheme) 0.25f else 0.15f).copy(a = 255)
                } else {
                    jumboBase
                }
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: onSurface
                SemanticSurfacePaintSpec(
                    backgroundColor = jumboBg,
                    borderColor = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: neutralBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 12f),
                    textColor = ensureReadableTextOn(preferredFg, jumboBg),
                    padding = EdgeInsets.all(24),
                    elevated = false
                )
            }

            normalizedTag == "well" || "well" in normalizedTokens -> {
                val wellBase = blendRgb(bgBase, onSurface, if (darkTheme) 0.06f else 0.05f).copy(a = 255)
                val wellBg = if (semType != null) {
                    blendRgb(wellBase, BootstrapColorPalette.resolveColor(semType, darkTheme, config), if (darkTheme) 0.20f else 0.12f).copy(a = 255)
                } else {
                    wellBase
                }
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: onSurface
                SemanticSurfacePaintSpec(
                    backgroundColor = wellBg,
                    borderColor = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) } ?: neutralBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 6f),
                    textColor = ensureReadableTextOn(preferredFg, wellBg),
                    padding = EdgeInsets.all(16),
                    elevated = false
                )
            }

            normalizedTag == "badge" || "badge" in normalizedTokens -> {
                val badgeType = semType ?: BootstrapType.PRIMARY
                val fill = BootstrapColorPalette.resolveColor(badgeType, darkTheme, config)
                val preferredFg = textType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) }
                    ?: BootstrapColorPalette.resolveForegroundOn(badgeType, darkTheme, config)
                val radiusPx = if ("rounded-pill" in normalizedTokens || snapshot?.borderRadius == BorderRadiusStyle.PILL) {
                    999f
                } else {
                    resolveRadiusOrDefault(snapshot?.borderRadius, 6f)
                }
                SemanticSurfacePaintSpec(
                    backgroundColor = fill,
                    borderColor = borderType?.let { BootstrapColorPalette.resolveColor(it, darkTheme, config) },
                    borderWidthPx = if (borderType != null) 1f else null,
                    borderStyle = if (borderType != null) CssBorderStyle.SOLID else null,
                    borderRadiusPx = radiusPx,
                    textColor = ensureReadableTextOn(preferredFg, fill),
                    padding = EdgeInsets.symmetric(vertical = 4, horizontal = 8),
                    elevated = false
                )
            }

            normalizedTag == "mdiwindow" || normalizedTag == "mdi-window" || "mdi-window" in normalizedTokens -> {
                val active = "active" in normalizedTokens
                val accent = colors.primary ?: colors.accent ?: BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, darkTheme, config)
                SemanticSurfacePaintSpec(
                    backgroundColor = surfaceBase,
                    borderColor = if (active) accent else neutralBorder,
                    borderWidthPx = if (active) 1.5f else 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 8f),
                    textColor = onSurface,
                    padding = EdgeInsets.ZERO,
                    shadowColor = RgbaColor(0, 0, 0, if (darkTheme) 115 else 36),
                    shadowBlurPx = 8f,
                    elevated = true
                )
            }

            normalizedTag == "mdidesktop" || normalizedTag == "mdi-desktop" || "mdi-desktop-surface" in normalizedTokens -> {
                val desktopBg = blendRgb(bgBase, neutralBorder, if (darkTheme) 0.10f else 0.12f)
                SemanticSurfacePaintSpec(
                    backgroundColor = desktopBg,
                    borderColor = neutralBorder,
                    borderWidthPx = 1f,
                    borderStyle = CssBorderStyle.SOLID,
                    borderRadiusPx = resolveRadiusOrDefault(snapshot?.borderRadius, 6f),
                    textColor = onSurface,
                    padding = EdgeInsets.ZERO,
                    elevated = false
                )
            }

            else -> null
        }
    }

    fun blendRgb(base: RgbaColor, overlay: RgbaColor, weightOverlay: Float): RgbaColor {
        val w = weightOverlay.coerceIn(0f, 1f)
        val inv = 1f - w
        return RgbaColor(
            r = (base.r * inv + overlay.r * w).toInt().coerceIn(0, 255),
            g = (base.g * inv + overlay.g * w).toInt().coerceIn(0, 255),
            b = (base.b * inv + overlay.b * w).toInt().coerceIn(0, 255),
            a = base.a
        )
    }

    fun relativeLuminance(color: RgbaColor): Double =
        (0.299 * color.r + 0.587 * color.g + 0.114 * color.b) / 255.0

    fun ensureReadableTextOn(preferred: RgbaColor?, background: RgbaColor): RgbaColor {
        val bgLum = relativeLuminance(background)
        if (preferred != null && abs(relativeLuminance(preferred) - bgLum) >= 0.35) {
            return preferred
        }
        return if (bgLum >= 0.54) RgbaColor(24, 28, 34, 255) else RgbaColor(246, 248, 252, 255)
    }

    private fun resolveRadiusOrDefault(borderRadius: BorderRadiusStyle?, defaultPx: Float): Float =
        when (borderRadius) {
            BorderRadiusStyle.NONE -> 0f
            BorderRadiusStyle.SMALL -> 4f
            BorderRadiusStyle.DEFAULT, BorderRadiusStyle.MEDIUM -> 6f
            BorderRadiusStyle.LARGE -> 8f
            BorderRadiusStyle.EXTRA_LARGE -> 12f
            BorderRadiusStyle.XXL -> 16f
            BorderRadiusStyle.PILL, BorderRadiusStyle.CIRCLE -> 999f
            null -> defaultPx
        }

    private fun normalizeTag(tagName: String?): String =
        tagName?.trim()?.lowercase()?.removePrefix("genericui:").orEmpty()

    private fun normalizeTokens(classTokens: Set<String>, snapshot: BootstrapStyleSnapshot?): Set<String> {
        val out = linkedSetOf<String>()
        fun addSplit(raw: String?) {
            if (raw.isNullOrEmpty()) return
            var hasWhitespaceOrUpper = false
            for (i in raw.indices) {
                val c = raw[i]
                if (c <= ' ' || c in 'A'..'Z') {
                    hasWhitespaceOrUpper = true
                    break
                }
            }
            if (!hasWhitespaceOrUpper) {
                out.add(raw)
                return
            }
            var start = -1
            var needsLower = false
            for (i in raw.indices) {
                val c = raw[i]
                if (c <= ' ') {
                    if (start >= 0) {
                        val sub = raw.substring(start, i)
                        out.add(if (needsLower) sub.lowercase() else sub)
                        start = -1
                        needsLower = false
                    }
                } else {
                    if (start < 0) start = i
                    if (c in 'A'..'Z') needsLower = true
                }
            }
            if (start >= 0) {
                val sub = raw.substring(start)
                out.add(if (needsLower) sub.lowercase() else sub)
            }
        }
        classTokens.forEach(::addSplit)
        snapshot?.customClasses?.forEach(::addSplit)
        return out
    }

    private fun hasAnySemanticSurfaceToken(tokens: Set<String>): Boolean =
        "card" in tokens ||
            "alert" in tokens ||
            "banner" in tokens ||
            "toast" in tokens ||
            "list-group" in tokens ||
            "list-group-item" in tokens ||
            "jumbotron" in tokens ||
            "well" in tokens ||
            "badge" in tokens ||
            "mdi-window" in tokens ||
            "mdi-desktop-surface" in tokens
}
