package com.nexustools.genericui.system.style

import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssShadowList
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Strongly-typed Blink property value entry inside a [BlinkCompiledRule] so stylesheet rules
 * apply directly to [com.nexustools.genericui.style.css.ComponentStyle] / [com.nexustools.genericui.style.css.ComputedStyle]
 * with zero runtime CSS string parsing.
 */
class BlinkPropertyEntry(
    val property: CssProperty,
    val important: Boolean = false,
    val lengthVal: CssLength? = null,
    val edgeLengthsVal: CssEdgeLengths? = null,
    val cornerRadiiVal: CssCornerRadii? = null,
    val colorVal: RgbaColor? = null,
    val gradientVal: Gradient? = null,
    val enumOrdinal: Int = -1,
    val floatVal: Float = 0f,
    val intVal: Int = 0,
    val boolVal: Boolean = false,
    val shadowListVal: CssShadowList? = null,
    val filterVal: CssFilter? = null,
    val fontFamilyVal: CssFontFamily? = null,
    val transformVal: CssTransform2D? = null,
    val transitionsVal: List<CssTransitionSpec>? = null,
    val stringVal: String? = null
)
