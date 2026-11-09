package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.style.css.CssAspectRatio
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.abs

/**
 * Vertical flex stack container (`flex-direction: column`) with per-child [StackConstraint] support.
 */
open class VStack(gap: SpacingScale? = null) : Container("genericui:vstack", "d-flex") {

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
        setFlexDirection(FlexDirection.COLUMN)
        addClass("flex-column")
        getStyle().setDisplay(CssDisplay.FLEX)
        getStyle().setFlexDirection(FlexDirection.COLUMN)
        if (gap != null) {
            getBootstrap().gap = gap
        }
    }

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
     * Adds [child] with an expanding [weight] along the vertical axis.
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
     * Assigns an expanding [weight] for [child] along the vertical axis.
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
            child.getStyle().setWidth(CssLength.ZERO)
            child.getStyle().setMinWidth(CssLength.PERCENT_100)
            child.getStyle().setMaxWidth(CssLength.PERCENT_100)
            if (constraint.heightPx == null && (weight ?: 0f) <= 0f) {
                child.getStyle().setHeight(CssLength.AUTO)
            }
        } else {
            child.removeAttribute("data-stack-shrink-cross")
            if (constraint.fillCrossAxis) {
                child.setAlignSelf(constraint.alignSelf ?: AlignSelf.STRETCH)
                if (constraint.widthPx == null) {
                    child.getStyle().setWidth(CssLength.PERCENT_100)
                }
            } else if (constraint.alignSelf != null) {
                child.setAlignSelf(constraint.alignSelf)
            }
        }

        if (constraint.widthPx != null && !constraint.shrinkCrossAxis) {
            child.widthPx = constraint.widthPx
        }
        if (constraint.heightPx != null) {
            child.heightPx = constraint.heightPx
        } else if ((weight ?: 0f) > 0f) {
            child.heightPx = null
        }

        if (constraint.minWidthPx != null && !constraint.shrinkCrossAxis) {
            child.minWidthPx = constraint.minWidthPx
        }
        if (constraint.minHeightPx != null) {
            child.minHeightPx = constraint.minHeightPx
        } else if ((weight ?: 0f) > 0f) {
            child.minHeightPx = 0
        }
        if (constraint.maxWidthPx != null && !constraint.shrinkCrossAxis) {
            child.maxWidthPx = constraint.maxWidthPx
        }
        if (constraint.maxHeightPx != null) {
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
            child.getStyle().setWidth(null)
            child.getStyle().setMinWidth(null)
            child.getStyle().setMaxWidth(null)
            child.getStyle().setHeight(null)
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
