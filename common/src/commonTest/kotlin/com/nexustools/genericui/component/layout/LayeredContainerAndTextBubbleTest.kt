package com.nexustools.genericui.component.layout

import com.nexustools.genericui.component.fragment.helper.LayeredFragment
import com.nexustools.genericui.component.lazy.LazyGrid
import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.component.lazy.TreeView
import com.nexustools.genericui.component.surface.TextBubble
import com.nexustools.genericui.component.surface.TextBubblePointerSide
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayeredContainerAndTextBubbleTest {

    @Test
    fun testLayeredContainerOrderingAndConstraints() {
        val container = LayeredContainer()
        assertEquals("genericui:layered-container", container.htmlTag)
        assertTrue(container.getClasses().contains("gui-layered-container"))
        assertEquals("relative", container.getStyle().getPosition()?.cssValue)

        val videoSurface = Box()
        val subtitleOverlay = Box()
        val hudControls = Box()

        container.addLayer(hudControls, LayerConstraint.fill(zIndex = 20, passThroughPointerEvents = true))
        container.addLayer(videoSurface, LayerConstraint.fill(zIndex = 1))
        container.addLayer(
            subtitleOverlay,
            LayerConstraint.float(
                alignment = LayerAlignment.BOTTOM_CENTER,
                zIndex = 10,
                bottomPx = 48,
                leftPercent = 50.0f,
                passThroughPointerEvents = true
            )
        )

        assertEquals(3, container.layers.size)
        assertEquals(listOf(hudControls, videoSurface, subtitleOverlay), container.layers)

        val subtitleConstraint = container.getLayerConstraint(subtitleOverlay)
        assertNotNull(subtitleConstraint)
        assertEquals(LayerSizingMode.FLOAT, subtitleConstraint.sizingMode)
        assertEquals(LayerAlignment.BOTTOM_CENTER, subtitleConstraint.alignment)
        assertEquals(10, subtitleConstraint.zIndex)
        assertEquals(48, subtitleConstraint.bottomPx)
        assertEquals(50.0f, subtitleConstraint.leftPercent)
        assertTrue(subtitleConstraint.passThroughPointerEvents)
        assertTrue(subtitleOverlay.getClasses().contains("gui-layer-float"))
        assertTrue(subtitleOverlay.getClasses().contains("gui-layer-pass-through"))
        assertEquals("none", subtitleOverlay.getStyle().getPointerEvents()?.cssValue)
        assertEquals("48px", subtitleOverlay.getStyle().getBottom()?.toCssString())
        assertEquals("50%", subtitleOverlay.getStyle().getLeft()?.toCssString())

        assertTrue(videoSurface.getClasses().contains("gui-layer-fill"))
        assertEquals("100%", videoSurface.getStyle().getWidth()?.toCssString())
        assertEquals("100%", videoSurface.getStyle().getHeight()?.toCssString())

        // Updating layer constraint updates zIndex
        container.setLayerConstraint(videoSurface, LayerConstraint.fill(zIndex = 21))
        assertEquals(21, container.getLayerConstraint(videoSurface)?.zIndex)

        // Removing a layer cleans up constraint tracking
        container.removeLayer(subtitleOverlay)
        assertNull(container.getLayerConstraint(subtitleOverlay))
        assertEquals(2, container.layers.size)

        container.clearLayers()
        assertEquals(0, container.layers.size)
    }

    @Test
    fun testLayeredFragmentDelegatesToLayeredLayout() {
        val fragment = object : LayeredFragment() {}
        val background = Text("Background")
        val badge = Text("Floating Badge")

        fragment.addLayer(background, LayerConstraint.fill(zIndex = 0))
        fragment.addLayer(
            badge,
            LayerConstraint.float(
                alignment = LayerAlignment.TOP_END,
                zIndex = 5,
                offsetYPx = 12,
                rightPx = 16
            )
        )

        assertEquals(2, fragment.layers.size)
        assertEquals(listOf(background, badge), fragment.layers)
        assertEquals(LayerAlignment.TOP_END, fragment.getLayerConstraint(badge)?.alignment)
        assertEquals("12px", badge.getStyle().getTop()?.toCssString())
        assertEquals("16px", badge.getStyle().getRight()?.toCssString())
    }

    @Test
    fun testTextBubblePropertiesAndPointerSides() {
        val bubble = TextBubble(
            text = "12:34",
            pointerSide = TextBubblePointerSide.BOTTOM,
            pointerOffset = 0.5f,
            pointerWidthPx = 12,
            pointerHeightPx = 8
        )

        assertEquals("genericui:text-bubble", bubble.htmlTag)
        assertEquals("12:34", bubble.text)
        assertEquals("12:34", bubble.textLabel.text)
        assertTrue(bubble.getClasses().contains("gui-text-bubble"))
        assertTrue(bubble.getClasses().contains("gui-text-bubble-pointer-bottom"))
        assertEquals("BOTTOM", bubble.getAttribute("pointerSide"))
        assertEquals("0.5", bubble.getAttribute("pointerOffset"))
        assertEquals("12", bubble.getAttribute("pointerWidthPx"))
        assertEquals("8", bubble.getAttribute("pointerHeightPx"))

        bubble.text = "01:02:03"
        bubble.pointerSide = TextBubblePointerSide.TOP
        bubble.pointerOffset = 1.25f // Clamped to 1.0f
        bubble.pointerWidthPx = 14
        bubble.pointerHeightPx = 10

        assertEquals("01:02:03", bubble.textLabel.text)
        assertTrue(bubble.getClasses().contains("gui-text-bubble-pointer-top"))
        assertFalse(bubble.getClasses().contains("gui-text-bubble-pointer-bottom"))
        assertEquals("TOP", bubble.getAttribute("pointerSide"))
        assertEquals(1.0f, bubble.pointerOffset)
        assertEquals("1.0", bubble.getAttribute("pointerOffset"))
        assertEquals("14", bubble.getAttribute("pointerWidthPx"))
        assertEquals("10", bubble.getAttribute("pointerHeightPx"))

        bubble.pointerSide = TextBubblePointerSide.NONE
        assertTrue(bubble.getClasses().contains("gui-text-bubble-pointer-none"))
    }

    @Test
    fun testSpacerFillAndFixedAttributes() {
        val fillSpacer = Spacer.fill(1.5f)
        assertTrue(fillSpacer.isFill)
        assertEquals("0", fillSpacer.getAttribute("spacerWidthPx"))
        assertEquals("0", fillSpacer.getAttribute("spacerHeightPx"))
        assertEquals("1.5", fillSpacer.getAttribute("spacerWeight"))
        assertEquals("true", fillSpacer.getAttribute("spacerFill"))

        val fixedHorizontal = Spacer.horizontal(8)
        assertFalse(fixedHorizontal.isFill)
        assertEquals("8", fixedHorizontal.getAttribute("spacerWidthPx"))
        assertEquals("0", fixedHorizontal.getAttribute("spacerHeightPx"))
        assertEquals("0.0", fixedHorizontal.getAttribute("spacerWeight"))
        assertEquals("false", fixedHorizontal.getAttribute("spacerFill"))

        val fixedVertical = Spacer.vertical(12)
        assertFalse(fixedVertical.isFill)
        assertEquals("0", fixedVertical.getAttribute("spacerWidthPx"))
        assertEquals("12", fixedVertical.getAttribute("spacerHeightPx"))
        assertEquals("false", fixedVertical.getAttribute("spacerFill"))
    }

    @Test
    fun testStyledVisualSpecBoxModelResolution() {
        val compStyle = ComponentStyle().apply {
            setBackgroundColor(RgbaColor(0, 0, 0, 0.52f))
            setBorderColor(RgbaColor(255, 255, 255, 0.3f))
            setBorderWidth(CssEdgeLengths.px(1))
            setPadding(CssEdgeLengths.px(4, 14))
            width = CssLength(40f, CssUnit.PX)
            height = CssLength(40f, CssUnit.PX)
            minWidth = CssLength(32f, CssUnit.PX)
            minHeight = CssLength(32f, CssUnit.PX)
            gap = CssLength(8f, CssUnit.PX)
        }
        val computed = ComputedStyle(
            paddingTop = CssLength.px(6f),
            width = "64px",
            height = "64px",
            gap = CssLength.px(12f)
        )

        val spec = StyledVisualSpec.fromComponentAndComputed(compStyle, computed)
        assertEquals(EdgeInsets(top = 4, right = 14, bottom = 4, left = 14), spec.padding)
        assertEquals(40, spec.widthPx)
        assertEquals(40, spec.heightPx)
        assertEquals(32, spec.minWidthPx)
        assertEquals(32, spec.minHeightPx)
        assertEquals(8, spec.gapPx)

        val fromComputedOnly = StyledVisualSpec.fromComputedStyle(computed)
        assertNotNull(fromComputedOnly)
        assertEquals(EdgeInsets(top = 6, right = 0, bottom = 0, left = 0), fromComputedOnly.padding)
        assertEquals(64, fromComputedOnly.widthPx)
        assertEquals(64, fromComputedOnly.heightPx)
        assertEquals(12, fromComputedOnly.gapPx)
    }

    @Test
    fun testLazyGridAndTreeViewViewportVirtualization() {
        val grid = LazyGrid(columns = 3, estimatedRowHeightPx = 100f, overscanRows = 1)
        grid.setItems(count = 60, onLoadItem = { idx -> Text("Cell $idx") })

        val initialWindow = grid.activeViewportWindow
        assertEquals(0, initialWindow.firstActiveIndex)
        assertTrue(initialWindow.lastActiveIndex < 59)

        val scrolledWindow = grid.onViewportChanged(scrollOffsetPx = 500f, viewportHeightPx = 300f)
        assertSame(scrolledWindow, grid.activeViewportWindow)
        assertTrue(scrolledWindow.firstActiveIndex >= 12)
        assertTrue(grid.loadedItemIndices.contains(scrolledWindow.firstActiveIndex))
        assertFalse(grid.loadedItemIndices.contains(0))

        val tree = TreeView<String>(estimatedRowHeightPx = 30f, overscanRows = 1)
        val roots = (0 until 40).map { idx -> TreeNode(id = "node-$idx", label = "Node $idx", data = "data-$idx") }
        tree.setRootNodes(roots)
        val treeScrolled = tree.onViewportChanged(scrollOffsetPx = 300f, viewportHeightPx = 120f)
        assertSame(treeScrolled, tree.activeViewportWindow)
        assertTrue(treeScrolled.firstActiveIndex >= 9)
        assertFalse(tree.loadedRowIndices.contains(0))
        assertTrue(tree.loadedRowIndices.contains(10))
    }
}
