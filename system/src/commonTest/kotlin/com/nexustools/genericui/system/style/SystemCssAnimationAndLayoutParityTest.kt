package com.nexustools.genericui.system.style

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.form.StyledSlider
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.surface.StyledComponent
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.system.DefaultAnimationNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CompiledSelectorSegment
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.CssTransform
import com.nexustools.genericui.style.layout.BrowserLayoutEngine
import com.nexustools.genericui.style.layout.ComponentLayoutNodeAdapter
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SystemCssAnimationAndLayoutParityTest {

    private class TestAnimationScheduler : DefaultAnimationNativeImpl()

    private lateinit var scheduler: TestAnimationScheduler

    @BeforeTest
    fun setUp() {
        SystemCssAnimationController.clear()
        BlinkStyleEngine.clear()
        scheduler = TestAnimationScheduler()
        Animator.customScheduler = scheduler
        BlinkStyleEngine.ensureInstalled()
        SystemCssAnimationController.ensureInstalled()
    }

    @AfterTest
    fun tearDown() {
        SystemCssAnimationController.clear()
        BlinkStyleEngine.clear()
        Animator.cancelAllAnimations()
        Animator.customScheduler = null
        scheduler.dispose()
    }

    @Test
    fun testCssKeyframeAnimationsInterpolateRetriggerPauseAndCommitForwardsViaAnimator() {
        val sheet = CssStyleSheet.parse(
            """
            @keyframes fadeSlideOut {
                0% { opacity: 1; transform: translate(0, 0); background-color: #000000; width: 100px; }
                100% { opacity: 0; transform: translate(0, 50%); background-color: #ffffff; width: 200px; }
            }
            @keyframes fadeSlideIn {
                from { opacity: 0; transform: translate(0, 50%); }
                to { opacity: 1; transform: translate(0, 0); }
            }
            .controls { animation: fadeSlideIn 200ms linear forwards; }
            .controls.hidden { animation: fadeSlideOut 200ms linear forwards; }
            .controls.paused-state { animation-play-state: paused; }
            """.trimIndent()
        )

        val panel = StyledContainer("controls").apply { addClass("controls") }
        sheet.applyToTree(panel)

        // Immediately at t=0 of fadeSlideIn
        assertEquals(0f, panel.opacity)
        Animator.advanceTimeBy(100.0)
        assertTrue(panel.opacity in 0.45f..0.55f, "Expected ~0.5 opacity at 100ms, got ${panel.opacity}")
        assertEquals(25f, panel.computedStyle?.transformSpec?.translateY)

        Animator.advanceTimeBy(150.0)
        assertEquals(1f, panel.opacity)
        assertEquals(0f, panel.computedStyle?.transformSpec?.translateY)

        // Toggle .hidden -> switches active animation to fadeSlideOut cleanly
        panel.addClass("hidden")
        assertEquals(1f, panel.opacity)
        Animator.advanceTimeBy(100.0)
        assertTrue(panel.opacity in 0.45f..0.55f, "Expected ~0.5 opacity during fadeSlideOut, got ${panel.opacity}")
        assertEquals(25f, panel.computedStyle?.transformSpec?.translateY)
        val midBg = panel.computedStyle?.backgroundColor
        assertNotNull(midBg)
        assertTrue(midBg.r in 120..136, "Expected midpoint gray ~128, got ${midBg.r}")
        assertEquals("150px", panel.computedStyle?.width)

        // Pause halfway -> advancing time must NOT progress animation further
        panel.addClass("paused-state")
        val pausedOpacity = panel.opacity
        Animator.advanceTimeBy(150.0)
        assertTrue(abs(panel.opacity - pausedOpacity) < 1e-4f, "Paused animation should stay frozen at $pausedOpacity")

        // Resume -> finishes to 0 opacity with forwards fill committed
        panel.removeClass("paused-state")
        Animator.flushAll()
        assertEquals(0f, panel.opacity)
        assertEquals(50f, panel.computedStyle?.transformSpec?.translateY)
        assertEquals(RgbaColor(255, 255, 255, 255), panel.computedStyle?.backgroundColor)
        assertEquals("200px", panel.computedStyle?.width)

        panel.dispose()
        assertEquals(0, SystemCssAnimationController.activeTrackCount())
    }

    @Test
    fun testCssAnimationDelayBackwardsFillAlternateAndInfiniteVBlank() {
        val sheet = CssStyleSheet.parse(
            """
            @keyframes pulse {
                from { opacity: 0.2; }
                to { opacity: 0.8; }
            }
            @keyframes spinPulse {
                0% { opacity: 0.1; }
                50% { opacity: 0.9; }
                100% { opacity: 0.1; }
            }
            .delayed-alt {
                opacity: 1;
                animation: pulse 100ms linear 50ms 2 alternate backwards;
            }
            .infinite-loader {
                animation: spinPulse 100ms linear infinite;
            }
            """.trimIndent()
        )

        val delayedBox = StyledComponent("delayed-alt").apply { addClass("delayed-alt") }
        val spinner = StyledComponent("infinite-loader").apply { addClass("infinite-loader") }
        val root = Container().apply {
            addChild(delayedBox)
            addChild(spinner)
        }
        sheet.applyToTree(root)

        // During delay, backwards fill applies 0% stop (0.2)
        assertEquals(0.2f, delayedBox.opacity)

        Animator.advanceTimeBy(25.0)
        assertEquals(0.2f, delayedBox.opacity)

        // Past 50ms delay + 50ms into first cycle (0.5 between 0.2 and 0.8 -> 0.5)
        Animator.advanceTimeBy(75.0)
        assertTrue(delayedBox.opacity in 0.45f..0.55f, "Expected ~0.5 opacity at cycle 1 midpoint, got ${delayedBox.opacity}")

        // At 150ms (cycle 2 start in alternate = 0.8 reversing toward 0.2)
        // Finish all finite animations: without forwards fill, restores base style opacity = 1.0f
        Animator.flushAll()
        assertEquals(1.0f, delayedBox.opacity)

        // Infinite animation tracks VBlank frames smoothly via stepFrame
        spinner.applyStyleSheet(sheet)
        Animator.stepFrame(25.0)
        assertTrue(spinner.opacity in 0.45f..0.55f, "Expected ~0.5 opacity at 25% of spinPulse, got ${spinner.opacity}")
        Animator.stepFrame(25.0)
        assertTrue(spinner.opacity > 0.85f, "Expected ~0.9 opacity at 50% of spinPulse, got ${spinner.opacity}")

        root.dispose()
        assertEquals(0, SystemCssAnimationController.activeTrackCount())
    }

    @Test
    fun testBlinkStyleEngineNotAndDisabledAndCompositeTraversal() {
        val compiledRules = listOf(
            CompiledStyleRule(
                selector = ".card:not(.hidden)",
                segments = CompiledSelectorSegment.parseChain(".card:not(.hidden)"),
                style = ComponentStyle().apply { setOpacity(1f) }
            ),
            CompiledStyleRule(
                selector = ".card.hidden",
                segments = CompiledSelectorSegment.parseChain(".card.hidden"),
                style = ComponentStyle().apply { setOpacity(0f) }
            ),
            CompiledStyleRule(
                selector = ".slider-host:disabled .slider-knob",
                segments = listOf(
                    CompiledSelectorSegment(classes = setOf("slider-host"), pseudoClass = "disabled", combinatorToNext = CssCombinator.DESCENDANT),
                    CompiledSelectorSegment(classes = setOf("slider-knob"))
                ),
                style = ComponentStyle().apply { setOpacity(0.3f) }
            )
        )
        val blinkSheet = object : BlinkStyleSheet() {
            override val name: String = "TestBlink"
            override val cssResourcePath: String = "test/blink.css"
            override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)
        }
        BlinkStyleEngine.registerStyleSheet(blinkSheet)

        val knob = StyledComponent("slider-knob").apply { addClass("slider-knob") }
        val host = object : Composite("slider-host") {
            init {
                addClass("slider-host")
                addCompositeChild(knob)
            }
        }
        val card = StyledContainer("card").apply {
            addClass("card")
            addChild(host)
        }

        BlinkStyleEngine.resolveTree(card)
        assertEquals(1.0f, card.opacity)
        assertEquals(1.0f, knob.opacity)

        card.addClass("hidden")
        assertEquals(0.0f, card.opacity)
        card.removeClass("hidden")
        assertEquals(1.0f, card.opacity)

        host.isEnabled = false
        BlinkStyleEngine.resolveTree(card)
        assertEquals(0.3f, knob.opacity)
    }

    @Test
    fun testBrowserLayoutEngineProximalRelativeContainingBlockViewportFallbackTransformAndComposite() {
        // 1. Proximal position: relative ancestor containing block (skipping static intermediate parent)
        val outer = Container("div", "outer").apply {
            style.setPosition(com.nexustools.genericui.enums.css.CssPosition.RELATIVE)
            widthPx = 400
            heightPx = 300
        }
        val staticWrapper = Container("div", "static-wrapper").apply {
            style.setPosition(com.nexustools.genericui.enums.css.CssPosition.STATIC)
            widthPx = 400
            heightPx = 80
        }
        val badge = StyledComponent("abs-badge").apply {
            style.setPosition(com.nexustools.genericui.enums.css.CssPosition.ABSOLUTE)
            style.setBottom(com.nexustools.genericui.style.css.CssLength.px(10))
            style.setRight(com.nexustools.genericui.style.css.CssLength.px(10))
            widthPx = 40
            heightPx = 20
        }
        staticWrapper.addChild(badge)
        outer.addChild(staticWrapper)

        val adapter = ComponentLayoutNodeAdapter()
        val engine = BrowserLayoutEngine.forComponents()
        val relLayout = engine.computeLayout(outer, Rect(0, 0, 800, 600))
        val wrapperBox = relLayout.children[0]
        val badgeBox = wrapperBox.children[0]
        // Positioned against outer (400x300), NOT staticWrapper (400x80)!
        assertEquals(Rect(350, 270, 40, 20), badgeBox.borderBox)

        // 2. Fallback to root/Window viewportRect when NO ancestor has position != STATIC
        val staticRoot = Container("div", "static-root").apply {
            style.setPosition(com.nexustools.genericui.enums.css.CssPosition.STATIC)
            widthPx = 300
            heightPx = 100
        }
        val windowOverlay = StyledComponent("window-overlay").apply {
            style.setPosition(com.nexustools.genericui.enums.css.CssPosition.ABSOLUTE)
            style.setRight(com.nexustools.genericui.style.css.CssLength.px(20))
            style.setBottom(com.nexustools.genericui.style.css.CssLength.px(20))
            widthPx = 50
            heightPx = 30
        }
        staticRoot.addChild(windowOverlay)
        val vpLayout = engine.computeLayout(staticRoot, Rect(0, 0, 1000, 600))
        assertEquals(Rect(930, 550, 50, 30), vpLayout.children[0].borderBox)

        // 3. StyledSlider Composite layout, signed CssTransform (-50%, -50%), and z-index paint sorting
        val slider = StyledSlider(min = 0.0, max = 100.0, value = 50.0).apply {
            widthPx = 200
            heightPx = 24
        }
        val sliderCss = CssStyleSheet.parse(
            """
            .styled-slider { position: relative; }
            .styled-slider-track { position: absolute; top: 50%; left: 0; width: 100%; height: 6px; transform: translate(0, -50%); z-index: 1; }
            .styled-slider-progress { position: absolute; top: 50%; left: 0; height: 6px; transform: translate(0, -50%); z-index: 3; }
            .styled-slider-knob { position: absolute; top: 50%; width: 16px; height: 16px; transform: translate(-50%, -50%); z-index: 4; }
            """.trimIndent()
        )
        slider.applyStyleSheet(sliderCss)

        val sliderBox = engine.computeLayout(slider, Rect(0, 0, 200, 24))
        assertEquals(3, sliderBox.children.size)
        val trackBox = sliderBox.children[0]
        val progressBox = sliderBox.children[1]
        val knobBox = sliderBox.children[2]
        assertEquals(Rect(0, 9, 200, 6), trackBox.borderBox)
        assertEquals(Rect(0, 9, 100, 6), progressBox.borderBox)
        assertEquals(Rect(92, 4, 16, 16), knobBox.borderBox)
        assertEquals(listOf(1, 3, 4), sliderBox.childrenSortedByZIndex().map { it.effectiveZIndex })

        // 4. Normal-flow element with CssTransform translate(0, 50%) shifts box cleanly without moving sibling
        val flowContainer = Container().apply {
            widthPx = 200
            heightPx = 200
        }
        val flowShifted = StyledComponent().apply {
            widthPx = 100
            heightPx = 40
            style.setTransform(CssTransform.parse("translate(10px, 50%)")!!)
        }
        val flowSibling = StyledComponent().apply {
            widthPx = 100
            heightPx = 40
        }
        flowContainer.addChild(flowShifted)
        flowContainer.addChild(flowSibling)
        val flowLayout = engine.computeLayout(flowContainer, Rect(0, 0, 200, 200))
        assertEquals(Rect(10, 20, 100, 40), flowLayout.children[0].borderBox)
        assertEquals(Rect(0, 40, 100, 40), flowLayout.children[1].borderBox)
    }

    @Test
    fun testBlinkCompoundAncestorInvalidationPrunesNonMatchingSubtreesAndUpdatesMatchingTargets() {
        val compiledRules = listOf(
            CompiledStyleRule(
                selector = ".pip-host.hidden .pip-bar",
                segments = listOf(
                    CompiledSelectorSegment(classes = setOf("pip-host", "hidden"), combinatorToNext = CssCombinator.DESCENDANT),
                    CompiledSelectorSegment(classes = setOf("pip-bar"))
                ),
                style = ComponentStyle().apply {
                    setPointerEvents(com.nexustools.genericui.enums.css.CssPointerEvents.NONE)
                }
            ),
            CompiledStyleRule(
                selector = ".video-host:not(.hidden)",
                segments = CompiledSelectorSegment.parseChain(".video-host:not(.hidden)"),
                style = ComponentStyle().apply { setOpacity(1f) }
            ),
            CompiledStyleRule(
                selector = ".video-host.hidden",
                segments = CompiledSelectorSegment.parseChain(".video-host.hidden"),
                style = ComponentStyle().apply { setOpacity(0f) }
            )
        )
        val blinkSheet = object : BlinkStyleSheet() {
            override val name: String = "TestAncestorInvalidation"
            override val cssResourcePath: String = "test/ancestor-invalidation.css"
            override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)
        }
        BlinkStyleEngine.registerStyleSheet(blinkSheet)

        // 1. Matching ancestor (.pip-host) toggling .hidden updates targeted .pip-bar descendant immediately
        val pipBar = StyledComponent("pip-bar").apply { addClass("pip-bar") }
        val pipHost = StyledContainer("pip-host").apply {
            addClass("pip-host")
            addChild(pipBar)
        }
        BlinkStyleEngine.resolveTree(pipHost)
        pipHost.applyStyleSheet(blinkSheet.styleSheet)
        assertEquals(true, pipBar.computedStyle?.pointerEventsEnabled)

        pipHost.addClass("hidden")
        assertEquals(false, pipBar.computedStyle?.pointerEventsEnabled)
        pipHost.removeClass("hidden")
        assertEquals(true, pipBar.computedStyle?.pointerEventsEnabled)

        // 2. Non-matching ancestor (.video-host lacks .pip-host) returns NONE and leaves descendant cache untouched
        val childControl = StyledComponent("hud-btn").apply { addClass("hud-btn") }
        val videoHost = StyledContainer("video-host").apply {
            addClass("video-host")
            addChild(childControl)
        }
        BlinkStyleEngine.resolveTree(videoHost)
        videoHost.applyStyleSheet(blinkSheet.styleSheet)
        val cachedBefore = childControl.computedStyle
        assertTrue(childControl.hasCachedSheetBaseStyle(), "Expected childControl cachedSheetBaseStyle populated")

        val outClasses = linkedSetOf<String>()
        val outIds = linkedSetOf<String>()
        val outTags = linkedSetOf<String>()
        val mode = blinkSheet.styleSheet.collectDescendantInvalidation(
            component = videoHost,
            changedClasses = listOf("hidden"),
            outSubjectClasses = outClasses,
            outSubjectIds = outIds,
            outSubjectTags = outTags
        )
        assertEquals(0, mode, "video-host lacks .pip-host so toggling .hidden must not invalidate descendants")

        videoHost.addClass("hidden")
        assertEquals(0.0f, videoHost.opacity)
        assertTrue(childControl.hasCachedSheetBaseStyle(), "Descendant cache must stay intact on non-matching ancestor toggle")
        assertTrue(childControl.computedStyle === cachedBefore, "Descendant computedStyle must not be recomputed")

        videoHost.removeClass("hidden")
        assertEquals(1.0f, videoHost.opacity)
        assertTrue(childControl.computedStyle === cachedBefore, "Descendant computedStyle must stay intact on removeClass")
    }

    @Test
    fun mediaQueryViewportResizeSelectivelyInvalidatesOnlyMatchingBreakpoints() {
        val compiledRules = listOf(
            CompiledStyleRule(
                selector = ".card-row",
                segments = CompiledSelectorSegment.parseChain(".card-row"),
                style = ComponentStyle().apply { setOpacity(0.9f) }
            ),
            CompiledStyleRule(
                selector = ".card-row",
                segments = CompiledSelectorSegment.parseChain(".card-row"),
                style = ComponentStyle().apply { setOpacity(0.5f) },
                mediaMaxWidthPx = 767
            ),
            CompiledStyleRule(
                selector = ".col-xxl-wide",
                segments = CompiledSelectorSegment.parseChain(".col-xxl-wide"),
                style = ComponentStyle().apply {
                    setWidth(com.nexustools.genericui.style.css.CssLength.px(320))
                }
            ),
            CompiledStyleRule(
                selector = ".col-xxl-wide",
                segments = CompiledSelectorSegment.parseChain(".col-xxl-wide"),
                style = ComponentStyle().apply {
                    setWidth(com.nexustools.genericui.style.css.CssLength.px(420))
                },
                mediaMinWidthPx = 1400
            )
        )
        val blinkSheet = object : BlinkStyleSheet() {
            override val name: String = "TestMediaInvalidation"
            override val cssResourcePath: String = "test/media-invalidation.css"
            override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)
        }
        BlinkStyleEngine.registerStyleSheet(blinkSheet)

        val colWide = StyledContainer("col-xxl-wide").apply { addClass("col-xxl-wide") }
        val cardRow = StyledContainer("card-row").apply { addClass("card-row") }
        val root = StyledContainer("page-root").apply {
            addChild(colWide)
            addChild(cardRow)
        }

        root.applyStyleSheet(blinkSheet.styleSheet, viewportWidthPx = 1440)
        assertEquals("420px", colWide.computedStyle?.width)
        assertEquals(0.9f, cardRow.opacity)
        val cardComputedBefore = cardRow.computedStyle
        assertNotNull(cardComputedBefore)

        root.applyStyleSheet(blinkSheet.styleSheet, viewportWidthPx = 1280)
        assertEquals("320px", colWide.computedStyle?.width)
        assertTrue(
            cardRow.computedStyle === cardComputedBefore,
            "cardRow should keep cached ComputedStyle identity across 1440 -> 1280 when only 1400px breakpoint changes"
        )

        root.applyStyleSheet(blinkSheet.styleSheet, viewportWidthPx = 640)
        assertEquals(0.5f, cardRow.opacity)
    }

    @Test
    fun bootstrapUniversalDescendantMediaQueryDoesNotInvalidateWholeSubtreeOn1440To1280() {
        val compiledRules = listOf(
            CompiledStyleRule(
                selector = ".row-cols-xxl-auto > *",
                segments = listOf(
                    CompiledSelectorSegment(classes = setOf("row-cols-xxl-auto"), combinatorToNext = CssCombinator.CHILD),
                    CompiledSelectorSegment(tagName = "*")
                ),
                style = ComponentStyle().apply {
                    setFlexGrow(0f)
                    setFlexShrink(0f)
                },
                mediaMinWidthPx = 1400
            ),
            CompiledStyleRule(
                selector = ".unrelated-card",
                segments = CompiledSelectorSegment.parseChain(".unrelated-card"),
                style = ComponentStyle().apply { setOpacity(0.85f) }
            )
        )
        val blinkSheet = object : BlinkStyleSheet() {
            override val name: String = "TestBootstrapUniversalMedia"
            override val cssResourcePath: String = "test/bootstrap-universal-media.css"
            override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)
        }
        BlinkStyleEngine.registerStyleSheet(blinkSheet)

        val outSubjClasses = linkedSetOf<String>()
        val outSubjIds = linkedSetOf<String>()
        val outSubjTags = linkedSetOf<String>()
        val outAncClasses = linkedSetOf<String>()
        val outAncIds = linkedSetOf<String>()
        val outAncTags = linkedSetOf<String>()
        val mode = blinkSheet.styleSheet.collectMediaInvalidation(
            oldViewportWidthPx = 1440,
            newViewportWidthPx = 1280,
            outSubjectClasses = outSubjClasses,
            outSubjectIds = outSubjIds,
            outSubjectTags = outSubjTags,
            outAncestorClasses = outAncClasses,
            outAncestorIds = outAncIds,
            outAncestorTags = outAncTags
        )
        assertEquals(
            1, // CssStyleSheet.DESCENDANT_INVALIDATION_TARGETED
            mode,
            "Bootstrap .row-cols-xxl-auto > * media transition must NOT return DESCENDANT_INVALIDATION_WHOLE_SUBTREE"
        )
        assertTrue("row-cols-xxl-auto" in outAncClasses)

        val gridItem = StyledComponent("col").apply { addClass("col") }
        val autoRow = StyledContainer("row-cols-xxl-auto").apply {
            addClass("row-cols-xxl-auto")
            addChild(gridItem)
        }
        val unrelatedCard = StyledComponent("unrelated-card").apply { addClass("unrelated-card") }
        val root = StyledContainer("page-root").apply {
            addChild(autoRow)
            addChild(unrelatedCard)
        }

        root.applyStyleSheet(blinkSheet.styleSheet, viewportWidthPx = 1440)
        assertEquals(0f, gridItem.computedStyle?.flexGrow)
        assertEquals(false, unrelatedCard.cachedSheetHasMediaRules)
        val unrelatedBefore = unrelatedCard.computedStyle
        assertNotNull(unrelatedBefore)

        root.applyStyleSheet(blinkSheet.styleSheet, viewportWidthPx = 1280)
        assertEquals(null, gridItem.computedStyle?.flexGrow)
        assertTrue(
            unrelatedCard.computedStyle === unrelatedBefore,
            "Unrelated component without .row-cols-xxl-auto ancestor must keep ComputedStyle identity across 1440 -> 1280"
        )
    }

    @Test
    fun ariaDisabledAndUnsupportedAttributeSelectorsNeverMatchEnabledElements() {
        val rawSheet = CssStyleSheet.parse(
            """
            [aria-disabled="true"] {
                opacity: 0.55;
                pointer-events: none;
            }
            .styled-slider[aria-disabled="true"] {
                opacity: 0.5;
            }
            [data-custom-flag="yes"] {
                opacity: 0.2;
            }
            """.trimIndent()
        )
        val compiledRules = rawSheet.rules.mapIndexed { idx, r ->
            CompiledStyleRule(
                selector = r.selector,
                declarations = r.declarations,
                style = com.nexustools.genericui.style.sass.CssDeclarationApplyUtil.fromDeclarations(r.declarations, false),
                specificity = r.specificity,
                sourceOrder = idx
            )
        }
        val blinkSheet = object : BlinkStyleSheet() {
            override val name: String = "TestAriaDisabledAndAttrs"
            override val cssResourcePath: String = "test/aria-disabled.css"
            override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(compiledRules)
        }
        BlinkStyleEngine.registerStyleSheet(blinkSheet)

        val enabledBox = StyledContainer("styled-container").apply { addClass("styled-container") }
        val enabledSlider = StyledSlider(min = 0.0, max = 100.0, value = 25.0)
        val root = Container().apply {
            addChild(enabledBox)
            addChild(enabledSlider)
        }

        // Test BlinkStyleEngine path (system hook active)
        root.applyStyleSheet(blinkSheet.styleSheet, viewportWidthPx = 1280)
        assertEquals(1.0f, enabledBox.opacity, 0.001f)
        assertTrue(enabledBox.resolveEffectiveStyle().pointerEventsEnabled)
        assertEquals(0.55f, enabledBox.computedStyle?.disabledStyle?.opacity)
        assertEquals(1.0f, enabledSlider.opacity, 0.001f)
        assertTrue(enabledSlider.resolveEffectiveStyle().pointerEventsEnabled)
        assertEquals(0.5f, enabledSlider.computedStyle?.disabledStyle?.opacity)

        // Test CssStyleSheet fallback path (system hook cleared)
        val savedResolverHook = CssStyleSheet.systemStyleResolverHook
        CssStyleSheet.systemStyleResolverHook = null
        try {
            val resolvedBox = rawSheet.resolveStyleForComponent(enabledBox, viewportWidthPx = 1280)
            val resolvedSlider = rawSheet.resolveStyleForComponent(enabledSlider, viewportWidthPx = 1280)
            assertEquals(1.0f, resolvedBox.opacity)
            assertEquals(0.55f, resolvedBox.disabledStyle?.opacity)
            assertEquals(1.0f, resolvedSlider.opacity)
            assertEquals(0.5f, resolvedSlider.disabledStyle?.opacity)
        } finally {
            CssStyleSheet.systemStyleResolverHook = savedResolverHook
        }
    }

    @Test
    fun bingingOverlayVisibleOverridesLaterNotVisibleWithImportantPointerEvents() {
        val visibleSegmentRules = listOf(
            CompiledStyleRule(
                selector = ".binging-mode-queue-overlay",
                segments = listOf(
                    CompiledSelectorSegment(classes = setOf("binging-mode-queue-overlay"))
                ),
                style = ComponentStyle().apply {
                    setOpacity(0f, false)
                    setPointerEvents(com.nexustools.genericui.enums.css.CssPointerEvents.NONE, true)
                },
                specificity = 100,
                sourceOrder = 0
            ),
            CompiledStyleRule(
                selector = ".binging-mode-queue-overlay.visible",
                segments = listOf(
                    CompiledSelectorSegment(classes = setOf("binging-mode-queue-overlay", "visible"))
                ),
                style = ComponentStyle().apply {
                    setOpacity(1f, false)
                    setPointerEvents(com.nexustools.genericui.enums.css.CssPointerEvents.AUTO, true)
                },
                specificity = 200,
                sourceOrder = 1
            ),
            CompiledStyleRule(
                selector = ".binging-mode-queue-overlay:not(.visible)",
                segments = listOf(
                    CompiledSelectorSegment(
                        classes = setOf("binging-mode-queue-overlay"),
                        excludedClasses = setOf("visible")
                    )
                ),
                style = ComponentStyle().apply {
                    setOpacity(0f, false)
                    setPointerEvents(com.nexustools.genericui.enums.css.CssPointerEvents.NONE, true)
                },
                specificity = 200,
                sourceOrder = 2
            ),
            CompiledStyleRule(
                selector = ".binging-mode-queue-overlay:not(.visible)",
                segments = listOf(
                    CompiledSelectorSegment(
                        classes = setOf("binging-mode-queue-overlay"),
                        pseudoClasses = setOf("not(.visible)")
                    )
                ),
                style = ComponentStyle().apply {
                    setOpacity(0f, false)
                    setPointerEvents(com.nexustools.genericui.enums.css.CssPointerEvents.NONE, true)
                },
                specificity = 300,
                sourceOrder = 3
            )
        )
        val blinkSheet = object : BlinkStyleSheet() {
            override val name: String = "TestBingingOverlayVisibleNotVisible"
            override val cssResourcePath: String = "test/binging-overlay.css"
            override val ruleSet: BlinkRuleBucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(visibleSegmentRules)
        }
        BlinkStyleEngine.registerStyleSheet(blinkSheet)

        val overlay = StyledContainer("binging-overlay").apply {
            addClass("binging-mode-queue-overlay")
        }
        overlay.applyStyleSheet(blinkSheet.styleSheet, viewportWidthPx = 1440)
        assertEquals(0f, overlay.opacity, 0.001f)
        assertFalse(overlay.resolveEffectiveStyle().pointerEventsEnabled)

        overlay.addClass("visible")
        val resolvedVisible = overlay.resolveEffectiveStyle()
        assertEquals(1.0f, overlay.opacity, 0.001f)
        assertTrue(resolvedVisible.pointerEventsEnabled, "Expected .visible to enable pointerEvents even with later :not(.visible) rules")

        overlay.removeClass("visible")
        assertEquals(0f, overlay.opacity, 0.001f)
        assertFalse(overlay.resolveEffectiveStyle().pointerEventsEnabled)
    }
}
