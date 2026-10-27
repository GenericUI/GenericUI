package com.nexustools.genericui.style

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapAspectRatio
import com.nexustools.genericui.enums.bootstrap.BootstrapBorderWidth
import com.nexustools.genericui.enums.bootstrap.BootstrapFlexGrow
import com.nexustools.genericui.enums.bootstrap.BootstrapFlexShrink
import com.nexustools.genericui.enums.bootstrap.BootstrapFloat
import com.nexustools.genericui.enums.bootstrap.BootstrapFontSize
import com.nexustools.genericui.enums.bootstrap.BootstrapHeight
import com.nexustools.genericui.enums.bootstrap.BootstrapLineHeight
import com.nexustools.genericui.enums.bootstrap.BootstrapObjectFit
import com.nexustools.genericui.enums.bootstrap.BootstrapOpacity
import com.nexustools.genericui.enums.bootstrap.BootstrapPointerEvents
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BootstrapUserSelect
import com.nexustools.genericui.enums.bootstrap.BootstrapVisibility
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssGenericFontFamily
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.enums.css.CssUserSelect
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CompiledSelectorSegment
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssAspectRatio
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssCustomCursor
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssShadow
import com.nexustools.genericui.style.css.CssShadowList
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.SassKotlinPrecompiler
import com.nexustools.genericui.style.theme.SystemThemeDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComponentStyleBootstrapAndThemeTest {

    @Test
    fun testComponentStyleTypedPropertiesNullClearingAndImportantFlags() {
        val btn = Button("Styled Button")
        val s = btn.getStyle()

        s.setColor(RgbaColor.fromHex("#ff0000"), important = true)
        s.backgroundColor = RgbaColor.fromHex("#112233")
        s.accentColor = RgbaColor.fromHex("#0d6efd")
        s.width = CssLength.px(240f)
        s.height = CssLength.px(48f)
        s.padding = CssEdgeLengths.px(verticalPx = 8f, horizontalPx = 16f)
        s.margin = CssEdgeLengths.px(4f)
        s.borderWidth = CssEdgeLengths.px(2f)
        s.borderStyle = CssBorderStyle.SOLID
        s.borderColor = RgbaColor.fromHex("#445566")
        s.setBorderRadius(CssCornerRadii.px(14f), important = true)
        s.display = CssDisplay.INLINE_FLEX
        s.visibility = CssVisibility.VISIBLE
        s.position = CssPosition.RELATIVE
        s.boxSizing = CssBoxSizing.BORDER_BOX
        s.fontSize = CssLength.px(15f)
        s.fontWeight = FontWeight.BOLD
        s.fontStyle = CssFontStyle.ITALIC
        s.fontFamily = CssFontFamily.named("Inter", CssGenericFontFamily.SANS_SERIF)
        s.textAlign = TextAlignment.CENTER
        s.textDecoration = CssTextDecoration.UNDERLINE
        s.userSelect = CssUserSelect.NONE
        s.pointerEvents = CssPointerEvents.AUTO
        s.cursor = CssCursor.POINTER
        s.boxShadow = CssShadowList.of(CssShadow.px(offsetX = 0f, offsetY = 4f, blurRadius = 12f, color = RgbaColor(0, 0, 0, 128)))
        s.filter = CssFilter.blur(CssLength.px(3f))
        s.aspectRatio = CssAspectRatio.of(16f, 9f)
        s.zIndex = 10
        s.opacity = 0.9f

        assertTrue(s.isImportant(CssProperty.COLOR))
        assertTrue(s.isImportant(CssProperty.BORDER_RADIUS))
        assertFalse(s.isImportant(CssProperty.BACKGROUND_COLOR))
        assertEquals(RgbaColor.fromHex("#ff0000"), s.getColor())
        assertEquals(RgbaColor.fromHex("#112233"), s.getBackgroundColor())
        assertEquals(RgbaColor.fromHex("#0d6efd"), s.getAccentColor())
        assertEquals(240f, s.getWidth()?.value)
        assertEquals(48f, s.getHeight()?.value)
        assertEquals(14f, s.getBorderRadius()?.topLeft?.value)
        assertEquals(CssBorderStyle.SOLID, s.getBorderStyle())
        assertEquals(CssDisplay.INLINE_FLEX, s.getDisplay())
        assertEquals(CssVisibility.VISIBLE, s.getVisibility())
        assertEquals(CssPosition.RELATIVE, s.getPosition())
        assertEquals(CssBoxSizing.BORDER_BOX, s.getBoxSizing())
        assertEquals(FontWeight.BOLD, s.getFontWeight())
        assertEquals(CssFontStyle.ITALIC, s.getFontStyle())
        assertEquals(TextAlignment.CENTER, s.getTextAlign())
        assertEquals(CssTextDecoration.UNDERLINE, s.getTextDecoration())
        assertEquals(CssUserSelect.NONE, s.getUserSelect())
        assertEquals(CssPointerEvents.AUTO, s.getPointerEvents())
        assertEquals(CssCursor.POINTER, s.getCursor())
        assertEquals(10, s.getZIndex())
        assertEquals(0.9f, s.getOpacity())

        val inlineCss = s.toInlineCssString()
        assertTrue(inlineCss.contains("!important"))

        // Verify null clearing removes declarations and !important flags
        s.setColor(null)
        s.setBorderRadius(null)
        s.setPadding(null)
        s.setAccentColor(null)
        assertNull(s.getColor())
        assertFalse(s.isImportant(CssProperty.COLOR))
        assertNull(s.getBorderRadius())
        assertFalse(s.isImportant(CssProperty.BORDER_RADIUS))
        assertNull(s.getPadding())
        assertNull(s.getAccentColor())
    }

    @Test
    fun testComponentBootstrapAndStrictUserClassIsolation() {
        val container = Container()
        val b = container.getBootstrap()

        // Default BootstrapType is NONE
        assertEquals(BootstrapType.NONE, b.getType())

        // Configure Bootstrap properties via getBootstrap()
        b.display = DisplayMode.FLEX
        b.flexDirection = FlexDirection.COLUMN
        b.justifyContent = JustifyContent.BETWEEN
        b.alignItems = AlignItems.CENTER
        b.padding = SpacingScale.S3
        b.marginTop = SpacingScale.AUTO
        b.backgroundType = BootstrapType.PRIMARY
        b.textColorType = BootstrapType.LIGHT
        b.borderType = BootstrapType.INFO
        b.borderWidth = BootstrapBorderWidth.BORDER_2
        b.shadow = ShadowElevation.LG
        b.width = BootstrapWidth.W_75
        b.height = BootstrapHeight.H_100
        b.opacity = BootstrapOpacity.OPACITY_75
        b.fontSize = BootstrapFontSize.FS_4
        b.lineHeight = BootstrapLineHeight.LH_LG
        b.flexGrow = BootstrapFlexGrow.GROW_1
        b.flexShrink = BootstrapFlexShrink.SHRINK_0
        b.floatSide = BootstrapFloat.START
        b.userSelect = BootstrapUserSelect.NONE
        b.pointerEvents = BootstrapPointerEvents.AUTO
        b.objectFit = BootstrapObjectFit.COVER
        b.aspectRatio = BootstrapAspectRatio.RATIO_16X9
        b.visibility = BootstrapVisibility.VISIBLE

        // Strict isolation: Bootstrap enum classes MUST NOT appear in getClasses(), getClass(), or hasClass()
        assertTrue(container.getClasses().isEmpty())
        assertEquals("", container.getClass())
        assertFalse(container.hasClass("d-flex"))
        assertFalse(container.hasClass("p-3"))
        assertFalse(container.hasClass("mt-auto"))
        assertFalse(container.hasClass("bg-primary"))
        assertFalse(container.hasClass("w-75"))
        assertFalse(container.hasClass("container"))

        // User-added classes via addClass/removeClass/setClass work strictly on custom classes
        container.addClass("custom-panel featured-box")
        assertEquals(setOf("custom-panel", "featured-box"), container.getClasses())
        assertEquals("custom-panel featured-box", container.getClass())
        assertTrue(container.hasClass("custom-panel"))
        assertTrue(container.hasClass("featured-box"))

        container.removeClass("featured-box")
        assertEquals(setOf("custom-panel"), container.getClasses())
        assertFalse(container.hasClass("featured-box"))

        // Verify Bootstrap properties remain completely intact after user class manipulation
        assertEquals(DisplayMode.FLEX, b.getDisplay())
        assertEquals(SpacingScale.S3, b.getPadding())
        assertEquals(SpacingScale.AUTO, b.getMarginTop())
        assertEquals(BootstrapWidth.W_75, b.getWidth())
        assertEquals(BootstrapHeight.H_100, b.getHeight())
        assertEquals(BootstrapBorderWidth.BORDER_2, b.getBorderWidth())
        assertEquals(BootstrapOpacity.OPACITY_75, b.getOpacity())
        assertEquals(BootstrapFontSize.FS_4, b.getFontSize())
        assertEquals(BootstrapLineHeight.LH_LG, b.getLineHeight())
        assertEquals(BootstrapFlexGrow.GROW_1, b.getFlexGrow())
        assertEquals(BootstrapFlexShrink.SHRINK_0, b.getFlexShrink())
        assertEquals(BootstrapFloat.START, b.getFloatSide())
        assertEquals(BootstrapUserSelect.NONE, b.getUserSelect())
        assertEquals(BootstrapPointerEvents.AUTO, b.getPointerEvents())
        assertEquals(BootstrapObjectFit.COVER, b.getObjectFit())
        assertEquals(BootstrapAspectRatio.RATIO_16X9, b.getAspectRatio())
        assertEquals(BootstrapVisibility.VISIBLE, b.getVisibility())

        val enumClasses = b.toEnumClassSet()
        assertTrue("d-flex" in enumClasses)
        assertTrue("p-3" in enumClasses)
        assertTrue("mt-auto" in enumClasses)
        assertTrue("bg-primary" in enumClasses)
        assertTrue("w-75" in enumClasses)
        assertTrue("h-100" in enumClasses)
        assertTrue("border-2" in enumClasses)
        assertTrue("opacity-75" in enumClasses)
        assertTrue("fs-4" in enumClasses)
        assertTrue("lh-lg" in enumClasses)
        assertTrue("flex-grow-1" in enumClasses)
        assertTrue("flex-shrink-0" in enumClasses)
        assertTrue("float-start" in enumClasses)
        assertTrue("user-select-none" in enumClasses)
        assertTrue("pe-auto" in enumClasses)
        assertTrue("object-fit-cover" in enumClasses)
        assertTrue("ratio-16x9" in enumClasses)
        assertTrue("visible" in enumClasses)

        // Verify null clearing on ComponentBootstrap
        b.setPadding(null)
        b.setWidth(null)
        assertNull(b.getPadding())
        assertNull(b.getWidth())
        assertFalse("p-3" in b.toEnumClassSet())
        assertFalse("w-75" in b.toEnumClassSet())
    }

    @Test
    fun testCustomCursorFromResourceImageBufferSvgAndCssStyleSheet() {
        val cursorResId = ResourceID(0x7f010099, ResourceType.DRAWABLE, "brush_cursor", "drawable/brush_cursor.png")
        val resCursor = CssCustomCursor.fromResource(
            resourceId = cursorResId,
            hotspotX = 4,
            hotspotY = 6,
            fallback = CssCursor.CROSSHAIR
        )
        assertEquals(cursorResId, resCursor.resourceId)
        assertEquals(4, resCursor.hotspotX)
        assertEquals(6, resCursor.hotspotY)
        assertEquals(CssCursor.CROSSHAIR, resCursor.fallback)
        assertTrue(resCursor.toCssString().contains("4 6, crosshair"))

        val pixels = ImageBuffer(16, 16)
        val imgCursor = CssCustomCursor.fromImageBuffer(
            imageBuffer = pixels,
            hotspotX = 8,
            hotspotY = 8,
            fallback = CssCursor.POINTER
        )
        assertNotNull(imgCursor.imageBuffer)
        assertEquals(8, imgCursor.hotspotX)
        assertEquals(8, imgCursor.hotspotY)

        val svgCursor = CssCustomCursor.fromSvg(
            svgMarkup = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\"><circle cx=\"12\" cy=\"12\" r=\"6\"/></svg>",
            widthPx = 24,
            heightPx = 24,
            hotspotX = 12,
            hotspotY = 12,
            fallback = CssCursor.DEFAULT
        )
        assertNotNull(svgCursor.svgMarkup)
        assertTrue(svgCursor.toCssString().startsWith("url('data:image/svg+xml"))

        val btn = Button("Paint")
        btn.getStyle().setCursor(resCursor)
        assertEquals(resCursor, btn.getStyle().getCursor())
        assertEquals(resCursor, btn.resolveEffectiveStyle().customCursor)

        val sheet = CssStyleSheet.parse(
            """
            .canvas-viewport {
                cursor: url("@drawable/brush_cursor") 3 5, crosshair;
                accent-color: #22c55e;
            }
            """.trimIndent()
        )
        val resolved = sheet.resolveStyleForClasses(setOf("canvas-viewport"))
        val parsedCursor = assertIs<CssCustomCursor>(resolved.customCursor)
        assertEquals(3, parsedCursor.hotspotX)
        assertEquals(5, parsedCursor.hotspotY)
        assertEquals(CssCursor.CROSSHAIR, parsedCursor.fallback)
        assertEquals(RgbaColor.fromHex("#22c55e"), resolved.accentColor)
    }

    @Test
    fun testCompiledStyleRulePrecompilerAndResourceReferencesInScss() {
        val primaryColorId = ResourceID(0x7f030010, ResourceType.COLOR, "brand_primary")
        val padDimenId = ResourceID(0x7f040010, ResourceType.DIMEN, "card_radius")
        Resources.activeTable.registerColor(primaryColorId, RgbaColor.fromHex("#3b82f6"))
        Resources.activeTable.registerDimen(padDimenId, 16f)

        val precompiled = SassKotlinPrecompiler.precompile(
            scssSource = """
                .brand-card {
                    background-color: @color/brand_primary;
                    border-radius: @dimen/card_radius;
                }
            """.trimIndent(),
            name = "brand",
            resourcePath = "styles/brand.scss"
        )

        assertTrue(precompiled.compiledRules.isNotEmpty())
        val resolved = precompiled.styleSheet.resolveStyleForClasses(setOf("brand-card"))
        assertEquals(RgbaColor.fromHex("#3b82f6"), resolved.backgroundColor)
        assertEquals(16f, resolved.borderRadiusPx)

        // Direct CompiledStyleRule construction (used by build-time codegen)
        val compiledRule = CompiledStyleRule(
            selector = "div.compiled-box > button.action-btn",
            segments = listOf(
                CompiledSelectorSegment(tagName = "div", classes = setOf("compiled-box"), combinatorToNext = CssCombinator.CHILD),
                CompiledSelectorSegment(tagName = "button", classes = setOf("action-btn"))
            ),
            style = ComponentStyle().apply {
                setBackgroundColor(RgbaColor.fromHex("#10b981"))
                setBorderRadius(CssCornerRadii.px(12f), important = true)
            }
        )
        val compiledSheet = CssStyleSheet.precompiledFromCompiledRules(listOf(compiledRule))
        val parentBox = Container("div").apply { addClass("compiled-box") }
        val childBtn = Button("Go").apply { addClass("action-btn") }
        parentBox.addChild(childBtn)

        val btnComputed = compiledSheet.resolveStyleForComponent(childBtn)
        assertEquals(RgbaColor.fromHex("#10b981"), btnComputed.backgroundColor)
        assertEquals(12f, btnComputed.borderRadiusPx)
    }

    @Test
    fun testApplicationStyleSheetsAndThemeModeAutoDarkLight() {
        val styleResId = ResourceID(0x7f0c0001, ResourceType.STYLE, "app_main", "styles/app_main.scss")
        assertEquals(ResourceType.STYLE, styleResId.type)

        val precompiled = SassKotlinPrecompiler.precompile(
            scssSource = """
                .themed-panel {
                    background-color: #1e293b;
                    border-radius: 8px;
                }
            """.trimIndent(),
            name = "app_main",
            resourcePath = "styles/app_main.scss"
        )
        Resources.activeTable.registerStyleSheet(styleResId, precompiled)

        val root = Container().apply { addClass("themed-panel") }
        val app = Application(initialContent = root, title = "Styled App")

        // Default theme mode is AUTO
        assertEquals(ThemeMode.AUTO, app.getTheme())
        assertEquals(ThemeMode.AUTO, app.getThemeMode())

        // Simulate OS theme detection for AUTO mode
        val previousProvider = SystemThemeDetector.backendDetector
        try {
            SystemThemeDetector.backendDetector = { ThemeMode.LIGHT }
            assertEquals(ThemeMode.LIGHT, app.getResolvedTheme())

            SystemThemeDetector.backendDetector = { ThemeMode.DARK }
            assertEquals(ThemeMode.DARK, app.getResolvedTheme())

            var observedResolvedTheme: ThemeMode? = null
            app.addThemeChangeListener { resolved ->
                observedResolvedTheme = resolved
            }

            app.setTheme(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, app.getTheme())
            assertEquals(ThemeMode.LIGHT, app.getResolvedTheme())
            assertEquals(ThemeMode.LIGHT, observedResolvedTheme)

            app.setTheme(ThemeMode.AUTO)
            SystemThemeDetector.notifySystemThemeChanged(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, observedResolvedTheme)
        } finally {
            SystemThemeDetector.backendDetector = previousProvider
        }

        // Add and remove stylesheets by ResourceID and by path
        app.addStyleSheet(styleResId)
        assertTrue(app.hasStyleSheet(styleResId))
        assertTrue(app.hasStyleSheet("styles/app_main.scss"))
        assertEquals(1, app.getStyleSheets().size)
        assertEquals(RgbaColor.fromHex("#1e293b"), root.computedStyle?.backgroundColor)
        assertEquals(8f, root.computedStyle?.borderRadiusPx)

        assertTrue(app.removeStyleSheet(styleResId))
        assertFalse(app.hasStyleSheet(styleResId))
        assertTrue(app.getStyleSheets().isEmpty())

        app.addStyleSheet("styles/app_main.scss")
        assertTrue(app.hasStyleSheet("styles/app_main.scss"))
        assertTrue(app.removeStyleSheet(precompiled))
        assertFalse(app.hasStyleSheet("styles/app_main.scss"))
    }
}
