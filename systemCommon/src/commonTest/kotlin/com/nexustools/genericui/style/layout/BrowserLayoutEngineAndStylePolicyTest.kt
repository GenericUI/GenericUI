package com.nexustools.genericui.style.layout

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.StyledButton
import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.form.TextArea
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.AspectRatioContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HybridColumnLayout
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayout
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.component.layout.StackConstraint
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.TextBubble
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapAspectRatio
import com.nexustools.genericui.event.InputFocusManager
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PopupMouseCaptureManager
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CompiledSelectorSegment
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.policy.AestheticStylePolicy
import com.nexustools.genericui.system.style.BlinkStyleEngine
import com.nexustools.genericui.system.style.BlinkStyleSheet
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserLayoutEngineAndStylePolicyTest {

    private fun findBoxForComponent(
        rootComponent: AbstractComponent,
        rootBox: ComputedLayoutBox,
        target: AbstractComponent,
        adapter: ComponentLayoutNodeAdapter
    ): ComputedLayoutBox? {
        if (rootComponent === target) return rootBox
        val children = adapter.childrenOf(rootComponent)
        for (i in children.indices) {
            val childBox = rootBox.children.getOrNull(i) ?: continue
            val found = findBoxForComponent(children[i], childBox, target, adapter)
            if (found != null) return found
        }
        return null
    }

    @Test
    fun episodeCaster1440x900LayoutMatchesBrowserGeometryWithoutAspectRatioClamping() {
        val root = VStack(gapPx = 0).apply {
            setStyleProperty("width", "100%")
            setStyleProperty("height", "100%")
        }

        val navbar = Navbar(brandTitle = "Episode Caster")
        root.addChild(navbar, StackConstraint.FIXED)

        val mainColumns = HybridColumnLayout()
        mainColumns.updateViewport(viewportWidthDp = 1440, viewportHeightDp = 900, smallestScreenWidthDp = 900, isMobileTarget = false)

        // Left Column (8/12): 16:9 Video AspectRatioContainer + MediaControlBar
        val leftCol = VStack(gapPx = 0).apply {
            setStyleProperty("height", "100%")
        }
        val videoAspectContainer = AspectRatioContainer(BootstrapAspectRatio.RATIO_16X9)
        val videoSurface = Container("div", "video-surface")
        val overlayLayer = LayeredContainer()
        val hudSurface = StyledContainer()
        val subtitleOverlay = TextBubble("Subtitles Active").apply {
            widthPx = 220
            heightPx = 36
        }
        overlayLayer.addFillLayer(hudSurface)
        overlayLayer.addFloatingLayer(
            component = subtitleOverlay,
            alignment = LayerAlignment.BOTTOM_CENTER,
            bottomPx = 14
        )
        videoAspectContainer.addChild(videoSurface)
        videoAspectContainer.addChild(overlayLayer)

        val mediaControlBar = StyledContainer().apply {
            setStyleProperty("width", "100%")
            heightPx = 84
        }
        leftCol.addChild(videoAspectContainer, StackConstraint.FIXED)
        leftCol.addChild(mediaControlBar, StackConstraint.FIXED)

        // Right Column (4/12): HybridSplitNavigationLayout -> LayeredContainer -> LazyList + Binging button
        val splitNav = HybridSplitNavigationLayout(inColumnMode = true)
        val libraryLayered = LayeredContainer()
        val episodeLazyList = LazyList(estimatedItemExtentPx = 68f).apply {
            setItems(40, onLoadItem = { Container() })
        }
        val bingingFloatingButton = StyledButton("Binging").apply {
            widthPx = 108
            heightPx = 40
        }
        libraryLayered.addFillLayer(episodeLazyList)
        libraryLayered.addFloatingLayer(
            component = bingingFloatingButton,
            alignment = LayerAlignment.BOTTOM_END,
            rightPx = 20,
            bottomPx = 20
        )
        splitNav.addSection("episodes", "Episodes", content = libraryLayered)
        splitNav.addSection("podcasts", "Podcasts", content = Container())
        splitNav.addSection("downloads", "Downloads", content = Container())

        mainColumns.addColumn(
            id = "player",
            title = "Player",
            weight = 8f,
            colLg = 8,
            component = leftCol
        )
        mainColumns.addColumn(
            id = "library",
            title = "Library",
            weight = 4f,
            colLg = 4,
            component = splitNav.host
        )
        root.addChild(mainColumns.host, StackConstraint.EXPAND)

        val adapter = ComponentLayoutNodeAdapter()
        val engine = BrowserLayoutEngine.forComponents()
        val layoutTree = engine.computeLayout(root, Rect(0, 0, 1440, 900))

        // 1. Navbar is 1440x56 at top
        val navBox = findBoxForComponent(root, layoutTree, navbar, adapter)
        assertNotNull(navBox)
        assertEquals(Rect(0, 0, 1440, 56), navBox.borderBox)

        // 2. Left column and right column have 8:4 (2:1) width ratio
        val leftColBox = findBoxForComponent(root, layoutTree, leftCol, adapter)
        val rightColBox = findBoxForComponent(root, layoutTree, splitNav.host, adapter)
        assertNotNull(leftColBox)
        assertNotNull(rightColBox)
        assertEquals(960, leftColBox.borderBox.width)
        assertEquals(480, rightColBox.borderBox.width)
        assertEquals(1440, leftColBox.borderBox.width + rightColBox.borderBox.width)

        // 3. 16:9 AspectRatioContainer height is derived from width * 9 / 16 (~537px), NEVER clamped to 430px
        val aspectBox = findBoxForComponent(root, layoutTree, videoAspectContainer, adapter)
        assertNotNull(aspectBox)
        val expectedAspectHeight = (leftColBox.borderBox.width / (16f / 9f)).roundToInt()
        assertEquals(leftColBox.borderBox.width, aspectBox.borderBox.width)
        assertEquals(expectedAspectHeight, aspectBox.borderBox.height)
        assertTrue(aspectBox.borderBox.height > 520, "16:9 container height must not be clamped to 430px")

        // 4. Subtitle overlay is horizontally centered and 14px above bottom of video overlay
        val overlayBox = findBoxForComponent(root, layoutTree, overlayLayer, adapter)
        val subtitleBox = findBoxForComponent(root, layoutTree, subtitleOverlay, adapter)
        assertNotNull(overlayBox)
        assertNotNull(subtitleBox)
        assertEquals(aspectBox.borderBox, overlayBox.borderBox)
        val expectedSubtitleX = overlayBox.paddingBox.x + ((overlayBox.paddingBox.width - 220) / 2f).roundToInt()
        val expectedSubtitleY = overlayBox.paddingBox.bottom - 14 - 36
        assertTrue(abs(subtitleBox.borderBox.x - expectedSubtitleX) <= 1)
        assertEquals(expectedSubtitleY, subtitleBox.borderBox.y)

        // 5. LazyList fills 100% of the library LayeredContainer height below the 44px tab strip
        val libraryLayeredBox = findBoxForComponent(root, layoutTree, libraryLayered, adapter)
        val lazyListBox = findBoxForComponent(root, layoutTree, episodeLazyList, adapter)
        val bingingBox = findBoxForComponent(root, layoutTree, bingingFloatingButton, adapter)
        assertNotNull(libraryLayeredBox)
        assertNotNull(lazyListBox)
        assertNotNull(bingingBox)
        assertEquals(844 - 44, libraryLayeredBox.borderBox.height)
        assertEquals(libraryLayeredBox.paddingBox, lazyListBox.borderBox)

        // 6. Binging floating button is anchored at bottom: 20px, right: 20px
        assertEquals(libraryLayeredBox.paddingBox.right - 20 - 108, bingingBox.borderBox.x)
        assertEquals(libraryLayeredBox.paddingBox.bottom - 20 - 40, bingingBox.borderBox.y)

        // 7. Hidden bottomNavigationBar in column mode has zero box and isVisibleInLayout == false
        val bottomNavBox = findBoxForComponent(root, layoutTree, splitNav.bottomNavigationBar, adapter)
        assertNotNull(bottomNavBox)
        assertFalse(bottomNavBox.isVisibleInLayout)
        assertEquals(Rect.ZERO, bottomNavBox.borderBox)
    }

    @Test
    fun aestheticStylePolicyAndBackdropBlurBackendFiltering() {
        val styledContainer = StyledContainer()
        val styledButton = StyledButton("Play")
        val textBubble = TextBubble("01:23")
        val card = Card()
        val badge = Badge("NEW")
        val alert = Alert("Ready")
        val plainButton = Button("Plain")
        val plainContainer = Container()

        // Explicitly styled components
        for (comp in listOf(styledContainer, styledButton, textBubble, card, badge, alert)) {
            assertTrue(AestheticStylePolicy.isExplicitlyStyledComponent(comp))
            assertTrue(AestheticStylePolicy.supportsExtendedVisualStyles(comp, LayoutBackendTarget.WINDOWS))
            assertTrue(AestheticStylePolicy.supportsExtendedVisualStyles(comp, LayoutBackendTarget.ANDROID))
            assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.WEB))
            assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.ANDROID))
            assertTrue(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.IOS))
            assertFalse(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.WINDOWS))
            assertFalse(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.MACOS))
            assertFalse(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.GTK))
            assertFalse(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.QT))
            assertFalse(AestheticStylePolicy.supportsBackdropFilterBlur(comp, LayoutBackendTarget.SWING))
            assertFalse(AestheticStylePolicy.usesSystemThemeControlAppearance(comp, LayoutBackendTarget.WINDOWS))
            assertFalse(AestheticStylePolicy.usesSystemThemeControlAppearance(comp, LayoutBackendTarget.ANDROID))
        }

        // Standard system-themed controls
        for (comp in listOf(plainButton, plainContainer)) {
            assertFalse(AestheticStylePolicy.isExplicitlyStyledComponent(comp))
            assertFalse(AestheticStylePolicy.supportsExtendedVisualStyles(comp, LayoutBackendTarget.WINDOWS))
            assertFalse(AestheticStylePolicy.supportsExtendedVisualStyles(comp, LayoutBackendTarget.ANDROID))
            assertTrue(AestheticStylePolicy.supportsExtendedVisualStyles(comp, LayoutBackendTarget.WEB))
            assertTrue(AestheticStylePolicy.usesSystemThemeControlAppearance(comp, LayoutBackendTarget.WINDOWS))
            assertTrue(AestheticStylePolicy.usesSystemThemeControlAppearance(comp, LayoutBackendTarget.ANDROID))
            assertTrue(AestheticStylePolicy.usesSystemThemeControlAppearance(comp, LayoutBackendTarget.IOS))
            assertFalse(AestheticStylePolicy.usesSystemThemeControlAppearance(comp, LayoutBackendTarget.WEB))
        }

        // StyledVisualSpec backdrop-filter parsing & per-backend filtering
        val sheet = CssStyleSheet.parse(
            """
            .hud-pill {
                background-color: rgba(15, 23, 42, 0.72);
                backdrop-filter: blur(14px);
                border-radius: 999px;
                box-shadow: 0px 4px 16px rgba(0, 0, 0, 0.45);
            }
            """.trimIndent()
        )
        styledButton.addClass("hud-pill")
        styledButton.applyStyleSheet(sheet)

        val fullSpec = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = styledButton.getStyle(),
            computedStyle = styledButton.computedStyle
        )
        assertEquals(14f, fullSpec.backdropBlurPx)
        assertTrue(fullSpec.hasBackdropBlur)
        assertEquals(16f, fullSpec.boxShadowBlurPx)
        assertEquals(RgbaColor(15, 23, 42, 0.72f), fullSpec.backgroundColor)

        val mobileSpec = fullSpec.forBackend(LayoutBackendTarget.ANDROID)
        assertEquals(14f, mobileSpec.backdropBlurPx)
        assertNotNull(mobileSpec.backdropFilter)

        val desktopSpec = fullSpec.forBackend(LayoutBackendTarget.WINDOWS)
        assertNull(desktopSpec.backdropBlurPx)
        assertNull(desktopSpec.backdropFilter)
        assertEquals(RgbaColor(15, 23, 42, 0.72f), desktopSpec.backgroundColor)
        assertEquals(999f, desktopSpec.borderRadiusPx)
    }

    @Test
    fun blinkStyleEngineResolvesPrecompiledRulesWithBucketedMatchingAndCache() {
        BlinkStyleEngine.clear()
        BlinkStyleEngine.ensureInstalled()

        val precompiled = object : PrecompiledStyleSheet {
            override val name: String = "test-blink"
            override val cssResourcePath: String = "test-blink.css"
            override val rawCss: String = ""
            override val compiledRules: List<CompiledStyleRule> = listOf(
                CompiledStyleRule(
                    selector = ".theme-dark",
                    specificity = CssRule.computeSpecificity(".theme-dark"),
                    style = ComponentStyle().apply {
                        setTextColor(RgbaColor(240, 244, 248, 255), false)
                        setFontSize(CssLength.px(18f), false)
                    },
                    segments = listOf(
                        CompiledSelectorSegment(classes = setOf("theme-dark"))
                    )
                ),
                CompiledStyleRule(
                    selector = ".theme-dark .card-item",
                    specificity = CssRule.computeSpecificity(".theme-dark .card-item"),
                    style = ComponentStyle().apply {
                        setBackgroundColor(RgbaColor(30, 41, 59, 255), false)
                        setPaddingTop(CssLength.px(12f), false)
                        setPaddingBottom(CssLength.px(12f), false)
                    },
                    segments = listOf(
                        CompiledSelectorSegment(classes = setOf("theme-dark"), combinatorToNext = CssCombinator.DESCENDANT),
                        CompiledSelectorSegment(classes = setOf("card-item"))
                    )
                ),
                CompiledStyleRule(
                    selector = "#featured-card",
                    specificity = CssRule.computeSpecificity("#featured-card"),
                    style = ComponentStyle().apply {
                        setBackgroundColor(RgbaColor(59, 130, 246, 255), false)
                    },
                    segments = listOf(
                        CompiledSelectorSegment(id = "featured-card")
                    )
                )
            )
            override val styleSheet: CssStyleSheet by lazy {
                CssStyleSheet.precompiledFromCompiledRules(compiledRules)
            }
        }

        val blinkSheet = BlinkStyleSheet.fromPrecompiled(precompiled)
        BlinkStyleEngine.registerStyleSheet(blinkSheet)

        val root = Container("div", "theme-dark")
        val card1 = Container("div", "card-item")
        val card2 = Container("div", "card-item")
        val featured = Container("div", "card-item").apply {
            id = "featured-card"
        }
        root.addChild(card1)
        root.addChild(card2)
        root.addChild(featured)

        BlinkStyleEngine.resolveTree(root)

        // Inherited color & font-size from .theme-dark to children
        assertEquals(RgbaColor(240, 244, 248, 255), card1.computedStyle?.textColor)
        assertEquals(18f, card1.computedStyle?.fontSizePx)
        // Matched .theme-dark .card-item on card1 and card2 (card2 should hit MatchedPropertiesCache)
        assertEquals(RgbaColor(30, 41, 59, 255), card1.computedStyle?.backgroundColor)
        assertEquals(RgbaColor(30, 41, 59, 255), card2.computedStyle?.backgroundColor)
        assertEquals("12px", card1.computedStyle?.rawDeclarations?.get("padding-top"))
        assertTrue(BlinkStyleEngine.matchedPropertiesCache.hitCount >= 1L, "Identical sibling should hit BlinkMatchedPropertiesCache")

        // #featured-card overrides .theme-dark .card-item background-color due to higher ID specificity (1,0,0 > 0,2,0)
        assertEquals(RgbaColor(59, 130, 246, 255), featured.computedStyle?.backgroundColor)
    }

    @Test
    fun popupMouseHookingDismissesPopupsOnOutsideClickAndTextInputFocusFiltersKeyEvents() {
        PopupMouseCaptureManager.clear()
        InputFocusManager.reset()

        var popupDismissedCount = 0
        val popup = StyledContainer().apply {
            bounds = Rect(100, 100, 200, 150)
            hookMouseWhenPopupVisible = true
            isVisible = false
            onPopupOutsideClickDismiss = {
                popupDismissedCount++
                isVisible = false
            }
        }

        // Becoming visible auto-registers with PopupMouseCaptureManager
        popup.isVisible = true
        assertTrue(PopupMouseCaptureManager.hasActivePopup)

        // Clicking inside the popup bounds (150, 150) does NOT dismiss it
        val insideIntercepted = PopupMouseCaptureManager.interceptPointerDown(150f, 150f)
        assertFalse(insideIntercepted)
        assertEquals(0, popupDismissedCount)
        assertTrue(popup.isVisible)

        // Clicking outside the popup bounds (20, 20) dismisses it and intercepts the event
        val outsideIntercepted = PopupMouseCaptureManager.interceptPointerDown(20f, 20f)
        assertTrue(outsideIntercepted)
        assertEquals(1, popupDismissedCount)
        assertFalse(popup.isVisible)
        assertFalse(PopupMouseCaptureManager.hasActivePopup)

        // DialogWindow defaults to dismissOnBackdropClick = true
        val dialog = DialogWindow(title = "Settings")
        assertTrue(dialog.dismissOnBackdropClick)

        // Text-input focus filtering: non-text components ignore key events while typing in TextField/TextArea
        val textField = TextField()
        val textArea = TextArea()
        assertTrue(textField.isTextInputComponent)
        assertTrue(textArea.isTextInputComponent)
        assertFalse(textField.ignoreKeyEventsWhenTypingInTextInput)
        assertFalse(textArea.ignoreKeyEventsWhenTypingInTextInput)

        var shortcutTriggered = 0
        val shortcutListenerHost = Container().apply {
            assertTrue(ignoreKeyEventsWhenTypingInTextInput)
            onKeyDown = {
                shortcutTriggered++
            }
        }

        // When no text input is focused, shortcut fires
        shortcutListenerHost.triggerKeyDown(KeyEvent(key = "Space", code = "Space", keyCode = 32))
        assertEquals(1, shortcutTriggered)

        // When TextField is focused, global/non-text component key listener is suppressed
        textField.isFocused = true
        assertTrue(InputFocusManager.isTypingInTextInput)
        shortcutListenerHost.triggerKeyDown(KeyEvent(key = "Space", code = "Space", keyCode = 32))
        assertEquals(1, shortcutTriggered, "Non-text shortcut listener must not fire while typing in a focused TextField")

        // TextField's own key listener still fires while focused
        var textFieldKeyCount = 0
        textField.onKeyDown = { textFieldKeyCount++ }
        textField.triggerKeyDown(KeyEvent(key = "a", code = "KeyA", keyCode = 65))
        assertEquals(1, textFieldKeyCount)

        // Blurring TextField restores non-text component key events
        textField.isFocused = false
        assertFalse(InputFocusManager.isTypingInTextInput)
        shortcutListenerHost.triggerKeyDown(KeyEvent(key = "Space", code = "Space", keyCode = 32))
        assertEquals(2, shortcutTriggered)
    }

    @Test
    fun singlePropertyStyleUpdatesAndCalcPercentPxWorkInO1WithoutFullStyleReserialization() {
        val calcLen = CssLength.parse("calc(50% - 12px)")
        assertNotNull(calcLen)
        assertEquals(50f, calcLen.value)
        assertEquals(-12f, calcLen.secondaryPxOffset)
        assertEquals(188f, calcLen.resolvePx(referencePx = 400f))
        assertEquals("calc(50% - 12px)", calcLen.toCssString())

        val mutatedProperties = mutableListOf<CssProperty>()
        val box = object : Container() {
            override fun onSingleInlineStylePropertyMutated(
                property: CssProperty,
                cssPropertyName: String,
                cssValue: String?
            ) {
                super.onSingleInlineStylePropertyMutated(property, cssPropertyName, cssValue)
                mutatedProperties.add(property)
            }
        }

        box.getStyle().setWidth(CssLength.PERCENT_100)
        assertEquals(listOf(CssProperty.WIDTH), mutatedProperties)
        assertEquals("100%", box.getStyle().getSinglePropertyCssString(CssProperty.WIDTH))

        box.minWidthPx = 240
        assertEquals(240, box.minWidthPx)
        assertEquals(CssProperty.MIN_WIDTH, mutatedProperties.last())
        assertEquals("240px", box.computedStyle?.minWidth)
        assertEquals(240f, box.getStyle().getMinWidth()?.resolvePx())
    }
}
