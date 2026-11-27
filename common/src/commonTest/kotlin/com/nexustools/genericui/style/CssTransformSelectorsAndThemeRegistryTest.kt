package com.nexustools.genericui.style

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.surface.styled.StyledComponent
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.CssTransform
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CssTransformSelectorsAndThemeRegistryTest {

    @AfterTest
    fun tearDown() {
        ThemeStyleSheetRegistry.clear()
        Application.current = null
    }

    @Test
    fun testCssTransformParsingPercentAndPxAndComponentStyleSetter() {
        val tCenter = CssTransform.parse("translate(-50%, -50%)")
        assertNotNull(tCenter)
        assertEquals(-50f, tCenter.translateX)
        assertTrue(tCenter.translateXIsPercent)
        assertEquals(-50f, tCenter.translateY)
        assertTrue(tCenter.translateYIsPercent)
        assertEquals(-60f to -20f, tCenter.resolveTranslationPx(120, 40))

        val tVerticalHalf = CssTransform.parse("translate(0, 50%) scale(1.2)")
        assertNotNull(tVerticalHalf)
        assertEquals(0f, tVerticalHalf.translateX)
        assertEquals(50f, tVerticalHalf.translateY)
        assertTrue(tVerticalHalf.translateYIsPercent)
        assertEquals(1.2f, tVerticalHalf.scaleX)
        assertEquals(1.2f, tVerticalHalf.scaleY)
        assertEquals(0f to 15f, tVerticalHalf.resolveTranslationPx(200, 30))

        val tPx = CssTransform.parse("translateX(14px) translateY(-6px)")
        assertNotNull(tPx)
        assertEquals(14f to -6f, tPx.resolveTranslationPx(100, 100))

        assertEquals(CssTransform.IDENTITY, CssTransform.parse("none"))
        assertNull(CssTransform.parse("   "))
        assertNull(CssTransform.parse("not-a-transform"))

        val style = ComponentStyle()
        style.setTransform(tCenter, important = true)
        assertTrue(style.isImportant(CssProperty.TRANSFORM))
        val computed = style.toComputedStyle()
        assertEquals(tCenter, computed.transformSpec)
    }

    @Test
    fun testNotPseudoChainingAndDisabledLiveIncrementalRestyle() {
        val sheet = CssStyleSheet.parse(
            """
            .overlay-bar:not(.hidden) { opacity: 1; pointer-events: auto; cursor: pointer; }
            .overlay-bar.hidden { opacity: 0; pointer-events: none; cursor: none; }
            .badge:not(.muted):not(.hidden) { background-color: #ff6600; }
            .badge.muted { background-color: #666666; }
            .control-btn:not(:disabled) { opacity: 0.95; }
            .control-btn:disabled { opacity: 0.4; pointer-events: none; }
            .panel:disabled .panel-item { opacity: 0.35; }
            """.trimIndent()
        )

        val ruleNotHidden = sheet.rules.first { it.selector == ".overlay-bar:not(.hidden)" }
        assertEquals(CssRule.computeSpecificity(".overlay-bar.active"), ruleNotHidden.specificity)

        val bar = StyledContainer("overlay-bar").apply { addClass("overlay-bar") }
        val badge = StyledComponent("badge").apply { addClass("badge") }
        val btn = StyledComponent("control-btn").apply { addClass("control-btn") }
        val panelItem = StyledComponent("panel-item").apply { addClass("panel-item") }
        val panel = StyledContainer("panel").apply {
            addClass("panel")
            addChild(panelItem)
        }
        val root = Container().apply {
            addChild(bar)
            addChild(badge)
            addChild(btn)
            addChild(panel)
        }

        sheet.applyToTree(root)

        assertEquals(1.0f, bar.opacity)
        assertTrue(bar.computedStyle?.pointerEventsEnabled == true)
        assertEquals(CssCursor.POINTER, bar.computedStyle?.customCursor)
        assertEquals(RgbaColor(255, 102, 0, 255), badge.computedStyle?.backgroundColor)
        assertEquals(0.95f, btn.opacity)
        assertEquals(1.0f, panelItem.opacity)

        // Toggle .hidden at runtime -> immediate incremental restyle without manual tree restyle
        bar.addClass("hidden")
        assertEquals(0.0f, bar.opacity)
        assertEquals(false, bar.computedStyle?.pointerEventsEnabled)
        assertEquals(CssCursor.NONE, bar.computedStyle?.customCursor)

        bar.removeClass("hidden")
        assertEquals(1.0f, bar.opacity)
        assertEquals(true, bar.computedStyle?.pointerEventsEnabled)
        assertEquals(CssCursor.POINTER, bar.computedStyle?.customCursor)

        assertTrue(bar.toggleClass("hidden"))
        assertEquals(0.0f, bar.opacity)
        assertFalse(bar.toggleClass("hidden"))
        assertEquals(1.0f, bar.opacity)

        // Chained :not(.muted):not(.hidden)
        badge.addClass("hidden")
        assertNull(badge.computedStyle?.backgroundColor)
        badge.removeClass("hidden")
        assertEquals(RgbaColor(255, 102, 0, 255), badge.computedStyle?.backgroundColor)
        badge.addClass("muted")
        assertEquals(RgbaColor(102, 102, 102, 255), badge.computedStyle?.backgroundColor)

        // :disabled & :not(:disabled) & ancestor:disabled
        btn.isEnabled = false
        assertEquals(0.4f, btn.opacity)
        assertEquals(false, btn.pointerEventsEnabled)
        assertEquals(false, btn.resolveEffectiveStyle().pointerEventsEnabled)
        btn.isEnabled = true
        assertEquals(0.95f, btn.opacity)
        assertEquals(true, btn.pointerEventsEnabled)

        panel.isEnabled = false
        assertEquals(0.35f, panelItem.opacity)
        panel.isEnabled = true
        assertEquals(1.0f, panelItem.opacity)
    }

    @Test
    fun testThemeStyleSheetRegistryIsEmptyByDefaultAndCascadesDeterministically() {
        // GenericUI :common contains ZERO bundled theme.scss sheets by default
        assertTrue(ThemeStyleSheetRegistry.getCompiledThemeStyleSheets().isEmpty())

        val coreStyle = CssStyleSheet.parse(".token-box { opacity: 0.2; z-index: 1; }")
        val coreTheme = CssStyleSheet.parse(".token-box { opacity: 0.3; z-index: 2; }")
        val extStyle = CssStyleSheet.parse(".token-box { opacity: 0.4; z-index: 3; }")
        val extTheme = CssStyleSheet.parse(".token-box { opacity: 0.5; z-index: 4; }")
        val appStyle = CssStyleSheet.parse(".token-box { opacity: 0.6; z-index: 5; }")
        val appTheme = CssStyleSheet.parse(".token-box { opacity: 0.7; z-index: 6; }")
        val runtimeSheet = object : PrecompiledStyleSheet {
            override val name: String = "runtime"
            override val sourcePath: String = "runtime/override.css"
            override val rawCss: String = ""
            override val compiledRules: List<CompiledStyleRule> = emptyList()
            override val styleSheet: CssStyleSheet = CssStyleSheet.parse(".token-box { opacity: 0.85; z-index: 7; }")
        }
        val appStylePrecompiled = object : PrecompiledStyleSheet {
            override val name: String = "StylePrecompiled"
            override val sourcePath: String = "styles/style.scss"
            override val cssResourcePath: String = "app/style.css"
            override val rawCss: String = ""
            override val compiledRules: List<CompiledStyleRule> = emptyList()
            override val styleSheet: CssStyleSheet = appStyle
        }

        // Register out of order to verify deterministic sorting by cascade layer
        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet("app:theme", appTheme)
        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet("ext:media:theme", extTheme)
        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet("common:theme", coreTheme)
        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet("ext:media:style", extStyle)
        ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet("common:style", coreStyle)

        assertEquals(
            listOf(coreStyle, coreTheme, extStyle, extTheme, appTheme),
            ThemeStyleSheetRegistry.getCompiledThemeStyleSheets()
        )

        val leaf = StyledComponent("token-box").apply { addClass("token-box") }
        val app = object : Application(initialContent = Container().apply { addChild(leaf) }) {}
        app.addStyleSheet(appStylePrecompiled)
        app.applyActiveStyleSheetsToRootContent()
        assertEquals(6, leaf.computedStyle?.zIndex)
        assertEquals(0.7f, leaf.opacity)

        // Runtime sheet overrides app:theme
        app.addStyleSheet(runtimeSheet)
        assertEquals(7, leaf.computedStyle?.zIndex)
        assertEquals(0.85f, leaf.opacity)
    }

    @Test
    fun testCssKeyframesAndAnimationSpecsParseCleanly() {
        val css = CssStyleSheet.parse(
            """
            @keyframes fadeSlide {
                from { opacity: 0; transform: translate(0, 50%); }
                50% { opacity: 0.5; }
                to { opacity: 1; transform: translate(0, 0); }
            }
            .anim-target {
                animation: fadeSlide 300ms ease-out 50ms 2 alternate forwards;
            }
            .anim-longhand {
                animation-name: fadeSlide;
                animation-duration: 0.4s;
                animation-timing-function: cubic-bezier(0.2, 0.8, 0.2, 1);
                animation-iteration-count: infinite;
                animation-fill-mode: both;
                animation-play-state: paused;
            }
            """.trimIndent()
        )

        val frames = css.keyframes["fadeSlide"]
        assertNotNull(frames)
        assertEquals("0", frames["from"]?.get("opacity"))
        assertEquals("translate(0, 50%)", frames["from"]?.get("transform"))
        assertEquals("0.5", frames["50%"]?.get("opacity"))
        assertEquals("1", frames["to"]?.get("opacity"))

        val targetStyle = css.resolveStyleForClasses(setOf("anim-target"))
        val specs = targetStyle.resolveEffectiveAnimations()
        assertEquals(1, specs.size)
        assertEquals("fadeSlide", specs[0].name)
        assertEquals(300f, specs[0].durationMs)
        assertEquals(50f, specs[0].delayMs)
        assertEquals(2f, specs[0].iterationCount)
        assertEquals("alternate", specs[0].direction)
        assertEquals("forwards", specs[0].fillMode)

        val longhandStyle = css.resolveStyleForClasses(setOf("anim-longhand"))
        val lhSpecs = longhandStyle.resolveEffectiveAnimations()
        assertEquals(1, lhSpecs.size)
        assertEquals("fadeSlide", lhSpecs[0].name)
        assertEquals(400f, lhSpecs[0].durationMs)
        assertTrue(lhSpecs[0].isInfinite)
        assertTrue(lhSpecs[0].isPaused)
        assertEquals("both", lhSpecs[0].fillMode)
    }
}
