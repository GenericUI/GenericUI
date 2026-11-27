package com.nexustools.genericui.style

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssAttributeMatcher
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssMathEvaluator
import com.nexustools.genericui.style.css.CssMediaQuery
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssSelectorNode
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.CssTransform
import com.nexustools.genericui.style.sass.SassCompiler
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class W3cSelectorsVariablesMediaAndColorsTest {

    private class TestDomNode(
        override val selectorTagName: String = "div",
        override val selectorId: String? = null,
        override val selectorClasses: Set<String> = emptySet(),
        override val selectorAttributes: Map<String, String> = emptyMap(),
        override val selectorHasTextContent: Boolean = false,
        override val selectorIsHovered: Boolean = false,
        override val selectorIsFocused: Boolean = false,
        override val selectorIsActive: Boolean = false,
        override val selectorIsEnabled: Boolean = true,
        override val selectorIsChecked: Boolean = false
    ) : CssSelectorNode {
        override var selectorParent: CssSelectorNode? = null
        private val mutableChildren = mutableListOf<TestDomNode>()
        override val selectorChildren: List<CssSelectorNode>
            get() = mutableChildren
        var inheritedVars: Map<String, String> = emptyMap()
        override val selectorInheritedCustomProperties: Map<String, String>
            get() = inheritedVars

        fun addChild(child: TestDomNode): TestDomNode {
            child.selectorParent = this
            mutableChildren.add(child)
            return child
        }
    }

    @Test
    fun testCssColorsHslColor4SpaceAndExtendedNamed() {
        val lime = RgbaColor.fromCss("hsl(120, 100%, 50%)")
        assertNotNull(lime)
        assertEquals(0, lime.r)
        assertEquals(255, lime.g)
        assertEquals(0, lime.b)

        val hsla = RgbaColor.fromCss("hsla(240, 100%, 50%, 0.5)")
        assertNotNull(hsla)
        assertEquals(0, hsla.r)
        assertEquals(0, hsla.g)
        assertEquals(255, hsla.b)
        assertTrue(abs(hsla.alphaFloat - 0.5f) < 0.02f)

        val spaceRgb = RgbaColor.fromCss("rgb(100% 0% 50% / 50%)")
        assertNotNull(spaceRgb)
        assertEquals(255, spaceRgb.r)
        assertEquals(0, spaceRgb.g)
        assertTrue(abs(spaceRgb.b - 128) <= 1)
        assertTrue(abs(spaceRgb.alphaFloat - 0.5f) < 0.02f)

        val rebecca = RgbaColor.fromCss("rebeccapurple")
        assertNotNull(rebecca)
        assertEquals(102, rebecca.r)
        assertEquals(51, rebecca.g)
        assertEquals(153, rebecca.b)

        val curr = RgbaColor.fromCss("currentColor", RgbaColor(12, 34, 56, 255))
        assertEquals(RgbaColor(12, 34, 56, 255), curr)
    }

    @Test
    fun testCssMathFunctionsCalcMinMaxClamp() {
        val calcMixed = CssLength.parse("calc(50% - 20px)")
        assertNotNull(calcMixed)
        assertEquals(180f, calcMixed.resolvePx(referencePx = 400f))

        val calcNested = CssLength.parse("calc(2 * (10px + 5px) + 1rem)")
        assertNotNull(calcNested)
        assertEquals(46f, calcNested.resolvePx(referencePx = 100f, rootFontSizePx = 16f))

        val minLen = CssLength.parse("min(50%, 150px)")
        assertNotNull(minLen)
        assertEquals(100f, minLen.resolvePx(referencePx = 200f))
        assertEquals(150f, minLen.resolvePx(referencePx = 500f))

        val maxLen = CssLength.parse("max(20vw, 180px)")
        assertNotNull(maxLen)
        assertEquals(180f, maxLen.resolvePx(viewportWidthPx = 800f))
        assertEquals(300f, maxLen.resolvePx(viewportWidthPx = 1500f))

        val clampLen = CssLength.parse("clamp(12px, 2vw + 4px, 32px)")
        assertNotNull(clampLen)
        assertEquals(12f, clampLen.resolvePx(viewportWidthPx = 300f))
        assertEquals(24f, clampLen.resolvePx(viewportWidthPx = 1000f))
        assertEquals(32f, clampLen.resolvePx(viewportWidthPx = 2000f))
        assertTrue(abs((CssMathEvaluator.evaluateScalar("calc(0.25 + 0.5)") ?: 0f) - 0.75f) < 1e-4f)
    }

    @Test
    fun testCssTransformsSkewAndMatrix() {
        val t = CssTransform.parse("translateX(10px) rotate(90deg) skew(15deg, 5deg) scale(2)")
        assertNotNull(t)
        assertEquals(10f, t.translateX)
        assertEquals(90f, t.rotateDeg)
        assertEquals(15f, t.skewXDeg)
        assertEquals(5f, t.skewYDeg)
        assertEquals(2f, t.scaleX)

        val mat = CssTransform.parse("matrix(1, 0, 0.5, 1, 25, 40)")
        assertNotNull(mat)
        assertTrue(mat.hasMatrix)
        assertEquals(0.5f, mat.matrixC)
        val affine = mat.toTransform2D().resolveAffineMatrix(100, 100)
        assertEquals(25f, affine[4])
        assertEquals(40f, affine[5])
        assertNull(CssTransform.parse("not-valid-fn"))
    }

    @Test
    fun testAttributeSelectorsAndSelectorsLevel4() {
        assertTrue(CssAttributeMatcher.parse("[data-role~=\"pill\"]")!!.matches("badge pill primary"))
        assertFalse(CssAttributeMatcher.parse("[data-role~=\"pil\"]")!!.matches("badge pill"))
        assertTrue(CssAttributeMatcher.parse("[lang|=\"en\"]")!!.matches("en-US"))
        assertTrue(CssAttributeMatcher.parse("[href^=\"HTTPS\" i]")!!.matches("https://example.com"))
        assertTrue(CssAttributeMatcher.parse("[src$=\".png\"]")!!.matches("logo.png"))
        assertTrue(CssAttributeMatcher.parse("[title*=\"mid\"]")!!.matches("hello-mid-world"))

        val sheet = CssStyleSheet.parse(
            """
            :root { --accent: #ff5500; --gap: 8px; }
            ul > li:first-child { color: #111111; }
            li + li { margin-top: var(--gap); }
            li:nth-child(even of .item) { background-color: #eeeeee; }
            .card:has(> span.badge) { border-color: var(--accent); }
            :where(div, section).muted { opacity: 0.5; }
            div.muted { opacity: 0.9; }
            .badge::after { content: "!"; color: #ff0000; }
            .card:empty { opacity: 0.25; }
            """.trimIndent()
        )

        val root = TestDomNode(selectorTagName = "html")
        val rootStyle = sheet.resolveStyleForNode(root)
        assertEquals("#ff5500", rootStyle.customProperties["--accent"])

        val ul = root.addChild(TestDomNode(selectorTagName = "ul"))
        ul.inheritedVars = rootStyle.customProperties
        val li1 = ul.addChild(TestDomNode(selectorTagName = "li", selectorClasses = setOf("item"), selectorHasTextContent = true))
        li1.inheritedVars = ul.inheritedVars
        val liSpacer = ul.addChild(TestDomNode(selectorTagName = "li", selectorHasTextContent = true))
        liSpacer.inheritedVars = ul.inheritedVars
        val li2 = ul.addChild(TestDomNode(selectorTagName = "li", selectorClasses = setOf("item"), selectorHasTextContent = true))
        li2.inheritedVars = ul.inheritedVars

        val li1Style = sheet.resolveStyleForNode(li1)
        assertEquals(RgbaColor(0x11, 0x11, 0x11, 255), li1Style.textColor)
        assertNull(li1Style.marginTop)

        val li2Style = sheet.resolveStyleForNode(li2)
        assertEquals(8f, li2Style.marginTop?.resolvePx())
        assertEquals(RgbaColor(0xee, 0xee, 0xee, 255), li2Style.backgroundColor)

        val cardWithBadge = root.addChild(TestDomNode(selectorTagName = "div", selectorClasses = setOf("card", "muted")))
        cardWithBadge.inheritedVars = rootStyle.customProperties
        cardWithBadge.addChild(TestDomNode(selectorTagName = "span", selectorClasses = setOf("badge")))

        val cardStyle = sheet.resolveStyleForNode(cardWithBadge)
        assertEquals(RgbaColor(0xff, 0x55, 0x00, 255), cardStyle.borderColor)
        // `:where(div, section).muted` has specificity 100; `div.muted` has specificity 101 so `0.9` wins
        assertEquals(0.9f, cardStyle.opacity)

        val emptyCard = root.addChild(TestDomNode(selectorTagName = "div", selectorClasses = setOf("card")))
        assertEquals(0.25f, sheet.resolveStyleForNode(emptyCard).opacity)

        val badgeNode = cardWithBadge.selectorChildren[0]
        val badgeStyle = sheet.resolveStyleForNode(badgeNode)
        assertNotNull(badgeStyle.afterStyle)
        assertEquals("!", badgeStyle.afterStyle?.content)
        assertEquals(0, CssRule.computeSpecificity(":where(#a, .b)"))
        assertEquals(2, CssRule.computeSpecificity("div::after"))
    }

    @Test
    fun testMediaQueriesHeightOrientationThemeAndPrintExclusion() {
        val css = """
            .panel { width: 100px; }
            @media (min-width: 600px) and (orientation: landscape) {
                .panel { width: 500px; }
            }
            @media (prefers-color-scheme: dark) {
                .panel { background-color: #101010; }
            }
            @media print {
                .panel { display: none; }
            }
        """.trimIndent()
        val sheet = CssStyleSheet.parse(css)
        val node = TestDomNode(selectorTagName = "div", selectorClasses = setOf("panel"))

        val narrow = sheet.resolveStyleForNode(node, viewportWidthPx = 400, viewportHeightPx = 800, isDarkThemeOverride = false)
        assertEquals("100px", narrow.width)
        assertEquals("block", narrow.display ?: "block")
        assertNull(narrow.backgroundColor)

        val wideDark = sheet.resolveStyleForNode(node, viewportWidthPx = 1024, viewportHeightPx = 700, isDarkThemeOverride = true)
        assertEquals("500px", wideDark.width)
        assertEquals(RgbaColor(0x10, 0x10, 0x10, 255), wideDark.backgroundColor)
        assertTrue(wideDark.display != "none")
        assertTrue(CssMediaQuery.parse("@media not all and (min-width: 900px)").clauses.single().negated)
    }

    @Test
    fun testGridPropertiesAndTransitionLonghandsAndScssSelectorsL4() {
        val scss = """
            .grid {
                display: grid;
                grid-template-columns: repeat(3, 1fr);
                place-items: center end;
                transition-property: opacity, transform;
                transition-duration: 200ms, 400ms;
                :is(.cell, .tile) {
                    grid-column: 1 / span 2;
                }
            }
        """.trimIndent()
        val sheet = SassCompiler.compileToStyleSheet(scss)
        val gridNode = TestDomNode(selectorClasses = setOf("grid"))
        val gridStyle = sheet.resolveStyleForNode(gridNode)
        assertEquals("grid", gridStyle.display)
        assertEquals("repeat(3, 1fr)", gridStyle.gridTemplateColumns)
        assertEquals(AlignItems.CENTER, gridStyle.alignItems)
        assertEquals("end", gridStyle.justifyItems)

        val transitions = gridStyle.resolveEffectiveTransitions()
        assertEquals(2, transitions.size)
        assertEquals("opacity", transitions[0].property)
        assertEquals(200f, transitions[0].durationMs)
        assertEquals("transform", transitions[1].property)
        assertEquals(400f, transitions[1].durationMs)

        val cell = gridNode.addChild(TestDomNode(selectorClasses = setOf("cell")))
        assertEquals("1 / span 2", sheet.resolveStyleForNode(cell).gridColumn)
    }
}
