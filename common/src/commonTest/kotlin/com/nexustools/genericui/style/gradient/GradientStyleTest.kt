package com.nexustools.genericui.style.gradient

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.style.ColorRgba
import com.nexustools.genericui.style.Gradient
import com.nexustools.genericui.style.GradientStop
import com.nexustools.genericui.style.LinearGradient
import com.nexustools.genericui.style.RadialGradient
import com.nexustools.genericui.style.RadialGradientShape
import com.nexustools.genericui.style.css.CssBackgroundImage
import com.nexustools.genericui.style.css.CssStyleSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradientStyleTest {

    @Test
    fun testLinearGradientAndRadialGradientExtendGradient() {
        val linear: Gradient = LinearGradient(
            90f,
            ColorRgba(255, 0, 0, 1.0),
            ColorRgba(0, 0, 255, 1.0)
        )
        val radial: Gradient = RadialGradient(
            RadialGradientShape.CIRCLE,
            ColorRgba(0, 255, 0, 1.0),
            ColorRgba(0, 0, 0, 1.0)
        )

        assertIs<LinearGradient>(linear)
        assertIs<RadialGradient>(radial)

        // Linear 90deg = left (u=0 -> red) to right (u=1 -> blue)
        val leftSample = linear.sampleAt(0f, 0.5f)
        val midSample = linear.sampleAt(0.5f, 0.5f)
        val rightSample = linear.sampleAt(1f, 0.5f)
        assertEquals(255, leftSample.r)
        assertEquals(0, leftSample.b)
        assertTrue(midSample.r in 120..135)
        assertTrue(midSample.b in 120..135)
        assertEquals(0, rightSample.r)
        assertEquals(255, rightSample.b)

        // Radial center (u=0.5, v=0.5 -> green) to edge (u=1.0, v=0.5 -> black)
        val centerSample = radial.sampleAt(0.5f, 0.5f)
        val edgeSample = radial.sampleAt(1.0f, 0.5f)
        assertEquals(255, centerSample.g)
        assertEquals(0, edgeSample.g)
    }

    @Test
    fun testComponentBackgroundGradientWithinStylesAndHoverStates() {
        val defaultGrad = LinearGradient(
            135f,
            GradientStop(ColorRgba(37, 99, 235, 1.0), 0f),
            GradientStop(ColorRgba(29, 78, 216, 1.0), 1f)
        )
        val hoverGrad = RadialGradient(
            RadialGradientShape.CIRCLE,
            ColorRgba(59, 130, 246, 1.0),
            ColorRgba(30, 64, 175, 1.0)
        )

        val btn = Button("Launch")
        btn.addClass("launch-btn")
        btn.getStyle().setBackgroundImage(CssBackgroundImage.fromGradient(defaultGrad))
        val hoverSheet = CssStyleSheet.parse(
            """
            .launch-btn:hover {
                background-image: radial-gradient(circle at 50% 50%, rgba(59, 130, 246, 1), rgba(30, 64, 175, 1));
            }
            """.trimIndent()
        )
        hoverSheet.applyToTree(btn)

        assertEquals(defaultGrad, btn.backgroundGradient)
        assertEquals(defaultGrad, btn.resolveEffectiveStyle().backgroundGradient)

        btn.isHovered = true
        assertIs<RadialGradient>(btn.resolveEffectiveStyle().backgroundGradient)

        btn.isHovered = false
        assertEquals(defaultGrad, btn.resolveEffectiveStyle().backgroundGradient)

        val panel = Container()
        panel.getStyle().setBackgroundImage(
            CssBackgroundImage.fromGradient(
                RadialGradient(
                    RadialGradientShape.ELLIPSE,
                    ColorRgba(20, 20, 30, 1.0),
                    ColorRgba(10, 10, 15, 1.0)
                )
            )
        )
        assertIs<RadialGradient>(panel.backgroundGradient)
    }

    @Test
    fun testCssGradientParsingIntoComputedStyle() {
        val css = """
            .hero-card {
                background: linear-gradient(90deg, rgba(255, 0, 0, 1) 0%, rgba(0, 0, 255, 1) 100%);
            }
            .hero-card:hover {
                background-image: radial-gradient(circle at 50% 50%, rgba(0, 255, 0, 1), rgba(0, 0, 0, 1));
            }
        """.trimIndent()

        val sheet = CssStyleSheet.parse(css)
        val computed = sheet.resolveStyleForClasses(setOf("hero-card"))
        val baseGrad = computed.backgroundGradient
        assertNotNull(baseGrad)
        assertIs<LinearGradient>(baseGrad)
        assertEquals(90f, baseGrad.angleDegrees)
        assertEquals(2, baseGrad.stops.size)

        val hoverResolved = computed.resolveForState(hovered = true)
        val hoverGrad = hoverResolved.backgroundGradient
        assertNotNull(hoverGrad)
        assertIs<RadialGradient>(hoverGrad)
        assertEquals(RadialGradientShape.CIRCLE, hoverGrad.shape)
    }
}
