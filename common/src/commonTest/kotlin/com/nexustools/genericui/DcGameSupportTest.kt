package com.nexustools.genericui

import com.nexustools.genericui.component.content.CurrencyPurseBar
import com.nexustools.genericui.component.content.HorizontalCarouselSelector
import com.nexustools.genericui.component.content.MasterDetailStepSelect
import com.nexustools.genericui.component.dock.DockPanel
import com.nexustools.genericui.component.form.CompoundUnitInput
import com.nexustools.genericui.component.fragment.helper.OverlayDockWorkspaceFragment
import com.nexustools.genericui.component.fragment.helper.PageHostContainer
import com.nexustools.genericui.component.fragment.helper.ValidatedStepPageFragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.navigation.StepScrollView
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.style.sass.SassCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DcGameSupportTest {

    private val dcGameSampleScss = """
        .page {
          width: 100%;
          height: 100%;
          position: relative;
          overflow: hidden;

          .menu {
            left: 50%;
            top: 50%;
            z-index: 9999;
            position: absolute;
            border-radius: 6px;
            transition: transform .5s ease;
            transform: translate(-50%, -50%);
            background-color: rgba(80, 80, 80, .75);
            text-shadow: 0 0 2px #000,0 0 2px #000,0 0 2px #000;

            &.chargen {
              width: 90%;
              max-width: 90%;
              aspect-ratio: 16 / 9;

              @media only screen and (min-width: 750px) {
                width: 640px;
                max-width: 640px;
              }
            }

            &.loading {
              transform: translate(-50%, -50%) scale(0, 0) !important;
              pointer-events: none;
            }
          }

          &.destroyed .menu, &.created .menu {
            transform: translate(-50%, -50%) scale(0, 0) !important;
            pointer-events: none;
          }
        }
    """.trimIndent()

    @Test
    fun testSassNestedCompilationAndCssCascadeForDcGameTheme() {
        val sheet = SassCompiler.compileToStyleSheet(dcGameSampleScss)
        assertTrue(sheet.rules.isNotEmpty(), "Expected flattened CSS rules from nested SCSS")

        val page = Container("div", "page")
        val menu = Container("div", "menu chargen")
        page.addChild(menu)

        page.applyStyleSheet(sheet, viewportWidthPx = 1280)
        val menuStyle = menu.computedStyle
        assertNotNull(menuStyle)
        assertEquals(9999, menuStyle.zIndex)
        assertEquals(6f, menuStyle.borderRadiusPx)
        assertEquals("640px", menuStyle.width)
        assertEquals(80, menuStyle.backgroundColor?.r)
        assertEquals(191, menuStyle.backgroundColor?.a)
        assertTrue((menuStyle.aspectRatio ?: 0f) in 1.77f..1.78f)
    }

    @Test
    fun testWindowManagerPreRenderedContainerBufferTransitionCaching() {
        val sheet = SassCompiler.compileToStyleSheet(dcGameSampleScss)
        val page = Container("div", "page")
        val menu = Container("div", "menu chargen")
        val childLabel = Label("Level 1 Warrior")
        menu.addChild(childLabel)
        page.addChild(menu)

        // Initial style application sets transition: transform .5s ease and transform: translate(-50%, -50%)
        page.applyStyleSheet(sheet, viewportWidthPx = 1280)
        assertFalse(menu.isTransitioning)

        // Trigger .loading on menu -> transitions transform from scale(1, 1) to scale(0, 0) over 500ms
        menu.addClass("loading")
        page.applyStyleSheet(sheet, viewportWidthPx = 1280)
        assertTrue(menu.isTransitioning, "Expected CSS transition to start on .menu")

        // Pre-rendered buffer should have been rendered exactly ONCE at transition start
        assertEquals(1, menu.renderCache.renderPassCount)
        assertFalse(menu.renderCache.isDirty)

        // Step 4 frames (50ms each = 200ms) WITHOUT modifying any child inside menu
        repeat(4) {
            page.stepTransitions(50f)
        }
        assertTrue(menu.isTransitioning)
        // Render pass count MUST still be 1 because no child inside menu changed!
        assertEquals(1, menu.renderCache.renderPassCount)
        assertEquals(4, menu.renderCache.compositePassCount)
        assertTrue(menu.transitionController.currentFrameState.transform.scaleX in 0.05f..0.95f)

        // Now update a child inside the container mid-animation -> marks renderCache dirty
        childLabel.text = "Level 2 Paladin"
        assertTrue(menu.renderCache.isDirty)

        // Next animation frame re-renders the container's buffer ONCE (renderPassCount becomes 2)
        page.stepTransitions(50f)
        assertEquals(2, menu.renderCache.renderPassCount)
        assertFalse(menu.renderCache.isDirty)

        // Remaining frames complete the 500ms transition without any further child re-renders
        repeat(6) {
            page.stepTransitions(50f)
        }
        assertFalse(menu.isTransitioning)
        assertEquals(2, menu.renderCache.renderPassCount)
        assertEquals(0f, menu.transitionController.currentFrameState.transform.scaleX, 0.001f)
    }

    @Test
    fun testValidatedStepPageWizardAndAutoRewindOnValidationError() {
        val steps = listOf("Level", "Bloodline", "Attributes")
        val host = PageHostContainer(appTitle = "Darkness Arises")

        lateinit var createStep: (Int) -> ValidatedStepPageFragment
        createStep = { ordinal ->
            object : ValidatedStepPageFragment(steps, ordinal) {
                init {
                    stepPageFactory = createStep
                }

                override fun validateStep(stepData: Any?, args: List<Any?>, response: (String?) -> Unit) {
                    if (ordinal == 0) {
                        val lvl = stepData as? Int
                        if (lvl == null || lvl !in 1..20) {
                            response("Invalid level selected")
                        } else {
                            response(null)
                        }
                    } else {
                        response(null)
                    }
                }
            }
        }

        // Navigate to Step 0 and advance to Step 1 with valid level 5
        val step0 = createStep(0)
        host.navigateToPage(step0)
        assertEquals(step0, host.currentPage)

        val step1 = createStep(1)
        step0.stepNavigate(step1, stepData = 5)
        assertEquals(step1, host.currentPage)
        assertEquals(5, step1.pageArgs.getOrNull(0))

        // Navigate directly to Step 2 with an invalid Step 0 value (99) -> should auto-rewind to Step 0!
        val step2 = createStep(2)
        host.navigateToPage(step2, 99, "human")
        assertEquals(0, (host.currentPage as? ValidatedStepPageFragment)?.currentStepOrdinal)
    }

    @Test
    fun testDcGameUiSelectionAndCurrencyHelpers() {
        val purse = CurrencyPurseBar(initialPurseGold = 12.34)
        assertEquals(12, purse.goldCoins)
        assertEquals(3, purse.silverCoins)
        assertEquals(4, purse.copperCoins)
        assertTrue(purse.canAfford(10.0))
        purse.spentGold = 2.0
        assertEquals(10, purse.goldCoins)

        val heightInput = CompoundUnitInput("ft.", "in.", subdivisionsPerPrimary = 12.0, minTotal = 1.0, maxTotal = 12.0)
        heightInput.totalValue = 5.5
        assertEquals(5.0, heightInput.primaryField.numericValue)
        assertEquals(6.0, heightInput.secondaryField.numericValue)

        val scroll = StepScrollView(wrapAround = true, stepHeightPx = 40)
        scroll.viewportHeightPx = 80
        scroll.contentHeightPx = 200
        assertEquals(0, scroll.currentTopOffsetPx)
        scroll.scrollUp() // wraps around to bottom (-120)
        assertEquals(-120, scroll.currentTopOffsetPx)
        scroll.scrollDown() // wraps around to top (0)
        assertEquals(0, scroll.currentTopOffsetPx)

        val vhSelect = MasterDetailStepSelect()
        vhSelect.addItem("sword", "Longsword", "P")
        vhSelect.addItem("bow", "Shortbow")
        assertTrue(vhSelect.selectItem("sword"))
        assertEquals("sword", vhSelect.selectedItemId)

        val carousel = HorizontalCarouselSelector()
        carousel.addEntry("human", 1.0f)
        carousel.addEntry("dwarf", 0.75f)
        carousel.selectNext()
        assertEquals("dwarf", carousel.activeEntryId())

        val base3dViewport = Container("div", "threejs-viewport")
        val overlayWorkspace = OverlayDockWorkspaceFragment(backgroundComponent = base3dViewport)
        val mapLayerPanel = DockPanel("layers", "Map Layers")
        overlayWorkspace.registerOverlayPanel(mapLayerPanel, DockPosition.LEFT, xOffsetPx = 16, yOffsetPx = 56)
        assertTrue(overlayWorkspace.isOverlayMode)
        assertEquals(base3dViewport, overlayWorkspace.backgroundComponent)
        assertEquals(20, mapLayerPanel.zIndex)
    }
}
