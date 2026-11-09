package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.style.css.CssAspectRatio
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.abs

/**
 * Horizontal flex stack container (`flex-direction: row`) with per-child [StackConstraint] support.
 */
open class HStack(gap: SpacingScale? = null) : Container("genericui:hstack", "d-flex") {

    constructor(gapPx: Int) : this(null) {
        this.gapPx = gapPx
    }

    private val childConstraints = LinkedHashMap<AbstractComponent, StackConstraint>()

    var gap: SpacingScale? = gap
        set(value) {
            field = value
            getBootstrap().gap = value
        }

    var gapPx: Int = 8
        set(value) {
            field = value.coerceAtLeast(0)
            getStyle().setGap(CssLength.px(field))
        }

    init {
        setDisplay(DisplayMode.FLEX)
        setFlexDirection(FlexDirection.ROW)
        addClass("flex-row")
        getStyle().setDisplay(CssDisplay.FLEX)
        getStyle().setFlexDirection(FlexDirection.ROW)
        if (gap != null) {
            getBootstrap().gap = gap
        }
    }

    override fun setFlexWrap(wrap: FlexWrap?) {
        super.setFlexWrap(wrap)
        getStyle().setFlexWrap(wrap)
    }

    override fun getFlexWrap(): FlexWrap? =
        super.getFlexWrap() ?: getStyle().getFlexWrap()

    override fun addChild(child: AbstractComponent, index: Int) {
        if (child is Spacer && child !in childConstraints) {
            val constraint = if (child.weight > 0f) {
                StackConstraint(
                    weight = child.weight,
                    flexShrink = 1f,
                    fillCrossAxis = false,
                    widthPx = child.spacerWidthPx.takeIf { it > 0 },
                    heightPx = child.spacerHeightPx.takeIf { it > 0 },
                    minWidthPx = child.spacerWidthPx.takeIf { it > 0 },
                    minHeightPx = child.spacerHeightPx.takeIf { it > 0 }
                )
            } else {
                StackConstraint.fixed(
                    widthPx = child.spacerWidthPx.takeIf { it > 0 },
                    heightPx = child.spacerHeightPx.takeIf { it > 0 },
                    fillCrossAxis = false
                )
            }
            childConstraints[child] = constraint
            applyConstraintToChild(child, constraint)
        }
        super.addChild(child, index)
    }

    /**
     * Adds [child] with an explicit [StackConstraint].
     */
    fun addChild(child: AbstractComponent, constraint: StackConstraint, index: Int = -1) {
        childConstraints[child] = constraint
        applyConstraintToChild(child, constraint)
        super.addChild(child, index)
    }

    /**
     * Adds [child] with an expanding [weight] along the horizontal axis.
     */
    fun addChild(child: AbstractComponent, weight: Float, index: Int = -1) {
        addChild(child, StackConstraint.weight(weight), index)
    }

    /**
     * Assigns or updates the [StackConstraint] for [child].
     */
    fun setChildConstraint(child: AbstractComponent, constraint: StackConstraint) {
        childConstraints[child] = constraint
        applyConstraintToChild(child, constraint)
    }

    /**
     * Assigns an expanding [weight] for [child] along the horizontal axis.
     */
    fun setChildWeight(child: AbstractComponent, weight: Float) {
        setChildConstraint(child, StackConstraint.weight(weight))
    }

    /**
     * Returns the [StackConstraint] currently assigned to [child], if any.
     */
    fun getChildConstraint(child: AbstractComponent): StackConstraint? = childConstraints[child]

    /**
     * Returns the expanding weight assigned to [child], if any.
     */
    fun getChildWeight(child: AbstractComponent): Float? = childConstraints[child]?.weight

    /**
     * Clears any [StackConstraint] previously applied to [child].
     */
    fun clearChildConstraint(child: AbstractComponent) {
        if (childConstraints.remove(child) != null) {
            resetChildConstraintStyles(child)
        }
    }

    override fun removeChild(child: AbstractComponent): Boolean {
        childConstraints.remove(child)
        return super.removeChild(child)
    }

    override fun clearChildren() {
        childConstraints.clear()
        super.clearChildren()
    }

    private fun applyConstraintToChild(child: AbstractComponent, constraint: StackConstraint) {
        val weight = constraint.weight
        val shrink = constraint.flexShrink ?: if ((weight ?: 0f) > 0f) 1f else 0f
        val basis = constraint.flexBasis ?: if ((weight ?: 0f) > 0f) "0%" else "auto"
        val parsedBasis = CssLength.parse(basis) ?: if ((weight ?: 0f) > 0f) CssLength.percent(0) else CssLength.AUTO

        if (weight != null) {
            val coercedWeight = weight.coerceAtLeast(0f)
            child.getStyle().setFlex(coercedWeight, shrink, parsedBasis)
            child.setAttribute("data-stack-weight", formatFloat(coercedWeight))
            if (coercedWeight > 0f) {
                child.addClass("flex-grow-1")
            } else {
                child.removeClass("flex-grow-1")
            }
        } else {
            child.getStyle().setFlexGrow(null)
            child.removeAttribute("data-stack-weight")
            child.removeClass("flex-grow-1")
        }

        child.getStyle().setFlexShrink(shrink)
        if (shrink == 0f) {
            child.addClass("flex-shrink-0")
        } else {
            child.removeClass("flex-shrink-0")
        }

        child.getStyle().setFlexBasis(parsedBasis)

        val ratio = constraint.aspectRatio
        if (ratio != null && ratio > 0f) {
            val cssRatio = parseAspectRatioObject(ratio)
            child.aspectRatio = ratio
            child.getStyle().setAspectRatio(cssRatio)
            child.setAttribute("data-stack-aspect-ratio", formatFloat(ratio))
        } else {
            child.getStyle().setAspectRatio(null)
            child.removeAttribute("data-stack-aspect-ratio")
        }

        if (constraint.shrinkCrossAxis) {
            child.setAttribute("data-stack-shrink-cross", "true")
            child.setAlignSelf(constraint.alignSelf ?: AlignSelf.STRETCH)
            child.getStyle().setHeight(CssLength.ZERO)
            child.getStyle().setMinHeight(CssLength.PERCENT_100)
            child.getStyle().setMaxHeight(CssLength.PERCENT_100)
            if (constraint.widthPx == null && (weight ?: 0f) <= 0f) {
                child.getStyle().setWidth(CssLength.AUTO)
            }
        } else {
            child.removeAttribute("data-stack-shrink-cross")
            if (constraint.fillCrossAxis) {
                child.setAlignSelf(constraint.alignSelf ?: AlignSelf.STRETCH)
                if (constraint.heightPx == null && ratio == null) {
                    child.getStyle().setHeight(CssLength.PERCENT_100)
                }
            } else if (constraint.alignSelf != null) {
                child.setAlignSelf(constraint.alignSelf)
            }
        }

        if (constraint.widthPx != null) {
            child.widthPx = constraint.widthPx
        } else if ((weight ?: 0f) > 0f) {
            child.widthPx = null
        }
        if (constraint.heightPx != null && !constraint.shrinkCrossAxis) {
            child.heightPx = constraint.heightPx
        }

        if (constraint.minWidthPx != null) {
            child.minWidthPx = constraint.minWidthPx
        } else if ((weight ?: 0f) > 0f) {
            child.minWidthPx = 0
        }
        if (constraint.minHeightPx != null && !constraint.shrinkCrossAxis) {
            child.minHeightPx = constraint.minHeightPx
        }
        if (constraint.maxWidthPx != null) {
            child.maxWidthPx = constraint.maxWidthPx
        }
        if (constraint.maxHeightPx != null && !constraint.shrinkCrossAxis) {
            child.maxHeightPx = constraint.maxHeightPx
        }
    }

    private fun resetChildConstraintStyles(child: AbstractComponent) {
        val hadShrinkCross = child.getAttribute("data-stack-shrink-cross") == "true"
        child.getStyle().setFlexGrow(null)
        child.getStyle().setFlexShrink(null)
        child.getStyle().setFlexBasis(null)
        child.getStyle().setAspectRatio(null)
        if (hadShrinkCross) {
            child.getStyle().setHeight(null)
            child.getStyle().setMinHeight(null)
            child.getStyle().setMaxHeight(null)
            child.getStyle().setWidth(null)
        }
        child.removeAttribute("data-stack-weight")
        child.removeAttribute("data-stack-aspect-ratio")
        child.removeAttribute("data-stack-shrink-cross")
        child.removeClass("flex-grow-1")
        child.removeClass("flex-shrink-0")
    }

    private fun formatFloat(value: Float): String {
        val asInt = value.toInt()
        return if (abs(value - asInt.toFloat()) < 0.0001f) {
            asInt.toString()
        } else {
            value.toString()
        }
    }

    private fun parseAspectRatioObject(ratio: Float): CssAspectRatio {
        return when {
            abs(ratio - (16f / 9f)) < 0.01f -> CssAspectRatio.RATIO_16_9
            abs(ratio - (4f / 3f)) < 0.01f -> CssAspectRatio.RATIO_4_3
            abs(ratio - (21f / 9f)) < 0.01f -> CssAspectRatio.RATIO_21_9
            abs(ratio - 1f) < 0.01f -> CssAspectRatio.RATIO_1_1
            else -> CssAspectRatio(ratio, 1f)
        }
    }
}
