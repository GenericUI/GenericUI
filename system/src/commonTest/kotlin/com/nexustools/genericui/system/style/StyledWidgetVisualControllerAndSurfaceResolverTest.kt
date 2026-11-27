package com.nexustools.genericui.system.style

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.bootstrap.BootstrapBorderWidth
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StyledWidgetVisualControllerAndSurfaceResolverTest {

    @Test
    fun pseudoStatePrecedenceDisabledOverrideAndAnimatedOpacity() {
        val controller = StyledWidgetVisualController(allowsBootstrapTypeTint = true)
        val baseBg = RgbaColor(20, 30, 40, 255)
        val hoverBg = RgbaColor(40, 60, 80, 255)
        val focusBg = RgbaColor(60, 90, 120, 255)
        val activeBg = RgbaColor(90, 120, 150, 255)
        val disabledBg = RgbaColor(100, 100, 100, 180)

        controller.lastComputedStyle = ComputedStyle(
            backgroundColor = baseBg,
            hoverStyle = ComputedStyle(backgroundColor = hoverBg, borderRadiusPx = 8f),
            focusStyle = ComputedStyle(backgroundColor = focusBg),
            activeStyle = ComputedStyle(backgroundColor = activeBg),
            disabledStyle = ComputedStyle(backgroundColor = disabledBg)
        )
        controller.hoverVisuals = StyledVisualSpec(borderWidthPx = 2f)
        controller.focusVisuals = StyledVisualSpec(focusRingColor = RgbaColor(0, 122, 255, 255), focusRingWidthPx = 3f)

        assertEquals(baseBg, controller.resolveCurrentVisuals(isDarkTheme = true).backgroundColor)

        controller.hovered = true
        val hoveredSpec = controller.resolveCurrentVisuals(isDarkTheme = true)
        assertEquals(hoverBg, hoveredSpec.backgroundColor)
        assertEquals(8f, hoveredSpec.borderRadiusPx)
        assertEquals(2f, hoveredSpec.borderWidthPx)

        controller.focused = true
        val focusedSpec = controller.resolveCurrentVisuals(isDarkTheme = true)
        assertEquals(focusBg, focusedSpec.backgroundColor)
        assertEquals(3f, focusedSpec.focusRingWidthPx)

        controller.activePressed = true
        val activeSpec = controller.resolveCurrentVisuals(isDarkTheme = true, animatedOpacity = 0.45f)
        assertEquals(activeBg, activeSpec.backgroundColor)
        assertEquals(0.45f, activeSpec.opacity)

        controller.enabled = false
        val disabledSpec = controller.resolveCurrentVisuals(isDarkTheme = true, animatedOpacity = 0.6f)
        assertEquals(disabledBg, disabledSpec.backgroundColor)
        assertEquals(0.6f, disabledSpec.opacity)
        assertNull(disabledSpec.focusRingColor)
    }

    @Test
    fun policyBlocksBootstrapTintingForNonButtonWidgetsWhilePreservingExplicitCss() {
        val dangerBg = BootstrapColorPalette.resolveColor(BootstrapType.DANGER, darkMode = false)
        val dangerFg = BootstrapColorPalette.resolveForegroundOn(BootstrapType.DANGER, darkMode = false)
        val dangerSnapshot = BootstrapStyleSnapshot(type = BootstrapType.DANGER)

        val containerController = StyledWidgetVisualController(allowsBootstrapTypeTint = false)
        containerController.bootstrapSnapshot = dangerSnapshot
        containerController.baseVisuals = StyledVisualSpec(backgroundColor = dangerBg, textColor = dangerFg)

        val resolvedContainer = containerController.resolveCurrentVisuals(
            isDarkTheme = false,
            backendTarget = LayoutBackendTarget.IOS
        )
        assertNull(resolvedContainer.backgroundColor)
        assertNull(resolvedContainer.textColor)

        val customBg = RgbaColor(12, 34, 56, 255)
        val explicitStyle = ComponentStyle().apply {
            backgroundColor = customBg
        }
        containerController.lastComponentStyle = explicitStyle
        containerController.invalidate()
        val resolvedExplicit = containerController.resolveCurrentVisuals(
            isDarkTheme = false,
            backendTarget = LayoutBackendTarget.IOS
        )
        assertEquals(customBg, resolvedExplicit.backgroundColor)

        val buttonController = StyledWidgetVisualController(allowsBootstrapTypeTint = true)
        buttonController.bootstrapSnapshot = dangerSnapshot
        val resolvedButton = buttonController.resolveCurrentVisuals(
            isDarkTheme = false,
            backendTarget = LayoutBackendTarget.IOS
        )
        assertEquals(dangerBg, resolvedButton.backgroundColor)
        assertEquals(dangerFg, resolvedButton.textColor)
    }

    @Test
    fun effectiveBackdropAndForegroundFiltersSkipNoneAndMergeLegacyBlur() {
        val controller = StyledWidgetVisualController(allowsBootstrapTypeTint = false)
        val mergedBackdrop = controller.resolveEffectiveBackdropFilter(
            StyledVisualSpec(
                backdropBlurPx = 14f,
                backdropFilter = CssFilter(saturate = 1.6f)
            )
        )
        assertNotNull(mergedBackdrop)
        assertEquals(14f, mergedBackdrop.blur?.value)
        assertEquals(1.6f, mergedBackdrop.saturate)

        assertNull(
            controller.resolveEffectiveBackdropFilter(
                StyledVisualSpec(backdropBlurPx = 14f, backdropFilter = CssFilter.NONE)
            )
        )
        assertNull(
            controller.resolveEffectiveForegroundFilter(
                StyledVisualSpec(filter = CssFilter.NONE)
            )
        )
        val fgFilter = controller.resolveEffectiveForegroundFilter(
            StyledVisualSpec(filter = CssFilter.blur(CssLength.px(6f)))
        )
        assertNotNull(fgFilter)
        assertEquals(6f, fgFilter.blur?.value)
    }

    @Test
    fun semanticSurfaceResolverCoversSemanticWidgetsAndReturnsNullForPlainContainers() {
        val snapshot = BootstrapStyleSnapshot(type = BootstrapType.SUCCESS)
        for (plainTag in listOf("Container", "HStack", "VStack", "FlexBox", "LayeredContainer", "Fragment")) {
            assertNull(
                SemanticSurfaceStyleResolver.resolveForTagAndClasses(
                    tagName = plainTag,
                    classTokens = emptySet(),
                    snapshot = snapshot,
                    darkTheme = false
                ),
                message = "Plain container $plainTag should resolve to null"
            )
        }

        val semanticCases = listOf(
            "Card" to emptySet(),
            "Alert" to emptySet(),
            "Banner" to emptySet(),
            "Toast" to emptySet(),
            "ListGroup" to emptySet(),
            "ListGroupItem" to setOf("list-group-item", "active"),
            "Jumbotron" to emptySet(),
            "Well" to emptySet(),
            "span" to setOf("badge", "rounded-pill"),
            "MdiWindow" to setOf("mdi-window", "active"),
            "MdiDesktop" to setOf("mdi-desktop-surface")
        )
        for ((tag, classes) in semanticCases) {
            val spec = SemanticSurfaceStyleResolver.resolveForTagAndClasses(
                tagName = tag,
                classTokens = classes,
                snapshot = snapshot,
                darkTheme = true
            )
            assertNotNull(spec, "Expected semantic surface spec for tag=$tag classes=$classes")
            assertNotNull(spec.backgroundColor)
            assertNotNull(spec.textColor)
            assertTrue((spec.borderRadiusPx ?: 0f) >= 0f)
        }
    }

    @Test
    fun sanitizeBootstrapSnapshotStripsSemanticColorsWhilePreservingShapeAndSpacing() {
        val controller = StyledWidgetVisualController(allowsBootstrapTypeTint = false)
        val snapshot = BootstrapStyleSnapshot(
            type = BootstrapType.DANGER,
            outline = true,
            backgroundType = BootstrapType.PRIMARY,
            textColorType = BootstrapType.SUCCESS,
            borderType = BootstrapType.WARNING,
            borderRadius = BorderRadiusStyle.LARGE,
            borderWidth = BootstrapBorderWidth.BORDER_2,
            paddingAll = SpacingScale.THREE,
            shadow = ShadowElevation.SMALL,
            gap = SpacingScale.TWO
        )

        val sanitized = assertNotNull(
            controller.sanitizeBootstrapSnapshot(snapshot, LayoutBackendTarget.WINDOWS)
        )
        assertEquals(BootstrapType.NONE, sanitized.type)
        assertFalse(sanitized.outline)
        assertNull(sanitized.backgroundType)
        assertNull(sanitized.textColorType)
        assertNull(sanitized.borderType)
        assertEquals(BorderRadiusStyle.LARGE, sanitized.borderRadius)
        assertEquals(BootstrapBorderWidth.BORDER_2, sanitized.borderWidth)
        assertEquals(SpacingScale.THREE, sanitized.paddingAll)
        assertEquals(ShadowElevation.SMALL, sanitized.shadow)
        assertEquals(SpacingScale.TWO, sanitized.gap)

        controller.bootstrapSnapshot = snapshot
        val resolved = controller.resolveCurrentVisuals(
            isDarkTheme = false,
            backendTarget = LayoutBackendTarget.WINDOWS
        )
        assertNull(resolved.backgroundColor)
        assertNull(resolved.textColor)
        assertNull(resolved.borderColor)
        assertEquals(8f, resolved.borderRadiusPx)
        assertEquals(2f, resolved.borderWidthPx)
        assertEquals(EdgeInsets(16, 16, 16, 16), resolved.padding)
        assertEquals(4f, resolved.boxShadowBlurPx)
        assertEquals(8, resolved.gapPx)

        val webSnapshot = assertNotNull(
            controller.sanitizeBootstrapSnapshot(snapshot, LayoutBackendTarget.WEB)
        )
        assertEquals(snapshot, webSnapshot)
        assertNull(controller.sanitizeBootstrapSnapshot(null, LayoutBackendTarget.WINDOWS))
    }
}
