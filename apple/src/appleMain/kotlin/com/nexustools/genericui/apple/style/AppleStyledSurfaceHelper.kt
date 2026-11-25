@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple.style

import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssShadow
import com.nexustools.genericui.style.css.GradientStopNormalizer
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGColorCreate
import platform.CoreGraphics.CGColorRef
import platform.CoreGraphics.CGColorRelease
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextAddPath
import platform.CoreGraphics.CGContextClip
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextDrawLinearGradient
import platform.CoreGraphics.CGContextDrawRadialGradient
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineDash
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetShadowWithColor
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGGradientCreateWithColorComponents
import platform.CoreGraphics.CGGradientRelease
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGPathAddArcToPoint
import platform.CoreGraphics.CGPathAddLineToPoint
import platform.CoreGraphics.CGPathAddPath
import platform.CoreGraphics.CGPathCloseSubpath
import platform.CoreGraphics.CGPathCreateMutable
import platform.CoreGraphics.CGPathCreateWithRoundedRect
import platform.CoreGraphics.CGPathMoveToPoint
import platform.CoreGraphics.CGPathRef
import platform.CoreGraphics.CGPathRelease
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGGradientDrawsAfterEndLocation
import platform.CoreGraphics.kCGGradientDrawsBeforeStartLocation
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSNumber
import platform.Foundation.numberWithDouble
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.CALayer
import platform.QuartzCore.CAShapeLayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCAGradientLayerAxial
import platform.QuartzCore.kCAGradientLayerRadial
import platform.QuartzCore.kCALayerMaxXMaxYCorner
import platform.QuartzCore.kCALayerMaxXMinYCorner
import platform.QuartzCore.kCALayerMinXMaxYCorner
import platform.QuartzCore.kCALayerMinXMinYCorner
import platform.QuartzCore.kCALineCapButt
import platform.QuartzCore.kCALineCapRound
import platform.QuartzCore.kCALineJoinMiter
import kotlin.math.max
import kotlin.math.min

/**
 * Shared Apple CoreGraphics (`CGContextRef`) and QuartzCore (`CALayer`, `CAShapeLayer`,
 * `CAGradientLayer`) surface chrome helper shared by `macosMain` and `iosMain`.
 *
 * Supports uniform and per-corner [CssCornerRadii], `SOLID`/`DASHED`/`DOTTED`/`DOUBLE`
 * borders, box shadows, normalized linear and radial gradients via [GradientStopNormalizer],
 * and focus rings.
 */
object AppleStyledSurfaceHelper {

    /**
     * Creates a `+1` retained CoreGraphics [CGColorRef] in DeviceRGB from [color].
     * Caller owns releasing it with `CGColorRelease` (or passing to [CFBridgingRelease]).
     */
    fun createCgColor(color: RgbaColor): CGColorRef? {
        val space = CGColorSpaceCreateDeviceRGB() ?: return null
        return try {
            memScoped {
                val comps = allocArray<DoubleVar>(4)
                comps[0] = color.r / 255.0
                comps[1] = color.g / 255.0
                comps[2] = color.b / 255.0
                comps[3] = color.a / 255.0
                CGColorCreate(space, comps)
            }
        } finally {
            CGColorSpaceRelease(space)
        }
    }

    inline fun withCgColor(color: RgbaColor, block: (CGColorRef?) -> Unit) {
        val cg = createCgColor(color)
        try {
            block(cg)
        } finally {
            if (cg != null) {
                CGColorRelease(cg)
            }
        }
    }

    /**
     * Resolves CSS corner radii `[topLeft, topRight, bottomRight, bottomLeft]` in points/pixels,
     * applying CSS Borders & Backgrounds Level 3 proportional clamping so adjacent corners never
     * overlap [width] x [height].
     */
    fun resolveCornerRadiiPx(
        radii: CssCornerRadii?,
        fallbackUniformPx: Float,
        width: Double,
        height: Double
    ): DoubleArray {
        if (width <= 0.0 || height <= 0.0) {
            return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        val refW = width.toFloat()
        var tl = (radii?.topLeft?.resolvePx(referencePx = refW) ?: fallbackUniformPx).coerceAtLeast(0f).toDouble()
        var tr = (radii?.topRight?.resolvePx(referencePx = refW) ?: fallbackUniformPx).coerceAtLeast(0f).toDouble()
        var br = (radii?.bottomRight?.resolvePx(referencePx = refW) ?: fallbackUniformPx).coerceAtLeast(0f).toDouble()
        var bl = (radii?.bottomLeft?.resolvePx(referencePx = refW) ?: fallbackUniformPx).coerceAtLeast(0f).toDouble()

        var factor = 1.0
        if (tl + tr > 0.0) factor = min(factor, width / (tl + tr))
        if (tr + br > 0.0) factor = min(factor, height / (tr + br))
        if (bl + br > 0.0) factor = min(factor, width / (bl + br))
        if (tl + bl > 0.0) factor = min(factor, height / (tl + bl))
        if (factor < 1.0) {
            tl *= factor
            tr *= factor
            br *= factor
            bl *= factor
        }
        return doubleArrayOf(tl, tr, br, bl)
    }

    /**
     * Creates a closed rounded rectangle [CGPathRef] supporting per-corner radii.
     *
     * When [topLeftIsMinY] is `true` (UIKit `UIView` and flipped `NSView`), `(x, y)` is top-left.
     * When `false` (unflipped AppKit `CGContext`), visual top corners map to `y + height`.
     * Caller must release the returned [CGPathRef] with `CGPathRelease`.
     */
    fun createRoundedRectPath(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        topLeft: Double,
        topRight: Double,
        bottomRight: Double,
        bottomLeft: Double,
        topLeftIsMinY: Boolean = true
    ): CGPathRef? {
        if (width <= 0.0 || height <= 0.0) return null
        val uniform = topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft
        if (uniform) {
            val r = min(topLeft, min(width, height) * 0.5).coerceAtLeast(0.0)
            return CGPathCreateWithRoundedRect(CGRectMake(x, y, width, height), r, r, null)
        }

        // Visual min-Y corners in local path coordinates
        val minXMinYR = if (topLeftIsMinY) topLeft else bottomLeft
        val maxXMinYR = if (topLeftIsMinY) topRight else bottomRight
        val maxXMaxYR = if (topLeftIsMinY) bottomRight else topRight
        val minXMaxYR = if (topLeftIsMinY) bottomLeft else topLeft

        val path = CGPathCreateMutable() ?: return null
        val minX = x
        val minY = y
        val maxX = x + width
        val maxY = y + height

        CGPathMoveToPoint(path, null, minX + minXMinYR, minY)
        CGPathAddLineToPoint(path, null, maxX - maxXMinYR, minY)
        if (maxXMinYR > 0.0) {
            CGPathAddArcToPoint(path, null, maxX, minY, maxX, minY + maxXMinYR, maxXMinYR)
        } else {
            CGPathAddLineToPoint(path, null, maxX, minY)
        }
        CGPathAddLineToPoint(path, null, maxX, maxY - maxXMaxYR)
        if (maxXMaxYR > 0.0) {
            CGPathAddArcToPoint(path, null, maxX, maxY, maxX - maxXMaxYR, maxY, maxXMaxYR)
        } else {
            CGPathAddLineToPoint(path, null, maxX, maxY)
        }
        CGPathAddLineToPoint(path, null, minX + minXMaxYR, maxY)
        if (minXMaxYR > 0.0) {
            CGPathAddArcToPoint(path, null, minX, maxY, minX, maxY - minXMaxYR, minXMaxYR)
        } else {
            CGPathAddLineToPoint(path, null, minX, maxY)
        }
        CGPathAddLineToPoint(path, null, minX, minY + minXMinYR)
        if (minXMinYR > 0.0) {
            CGPathAddArcToPoint(path, null, minX, minY, minX + minXMinYR, minY, minXMinYR)
        } else {
            CGPathAddLineToPoint(path, null, minX, minY)
        }
        CGPathCloseSubpath(path)
        return path
    }

    /**
     * Applies uniform or per-corner [CssCornerRadii] onto [layer], using native `CALayer.cornerRadius`
     * + `maskedCorners` whenever all positive corners match, and falling back to a `CAShapeLayer`
     * clip mask when distinct positive per-corner radii are combined with [clipsToBounds] = `true`.
     */
    fun applyLayerCornerRadii(
        layer: CALayer,
        radii: CssCornerRadii?,
        fallbackUniformPx: Float,
        boundsWidth: Double,
        boundsHeight: Double,
        clipsToBounds: Boolean? = null,
        topLeftIsMinY: Boolean = true
    ): Double {
        val w = boundsWidth.coerceAtLeast(0.0)
        val h = boundsHeight.coerceAtLeast(0.0)
        val resolved = resolveCornerRadiiPx(radii, fallbackUniformPx, w, h)

        val positives = resolved.filter { it > 0.0 }
        val distinctNonZero = positives.distinct()
        val hasAnyRadius = positives.isNotEmpty()

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        try {
            if (distinctNonZero.size <= 1) {
                val radius = distinctNonZero.firstOrNull() ?: 0.0
                layer.mask = null
                layer.cornerRadius = radius
                var maskBits = 0uL
                if (resolved[0] > 0.0) {
                    maskBits = maskBits or if (topLeftIsMinY) kCALayerMinXMinYCorner else kCALayerMinXMaxYCorner
                }
                if (resolved[1] > 0.0) {
                    maskBits = maskBits or if (topLeftIsMinY) kCALayerMaxXMinYCorner else kCALayerMaxXMaxYCorner
                }
                if (resolved[2] > 0.0) {
                    maskBits = maskBits or if (topLeftIsMinY) kCALayerMaxXMaxYCorner else kCALayerMaxXMinYCorner
                }
                if (resolved[3] > 0.0) {
                    maskBits = maskBits or if (topLeftIsMinY) kCALayerMinXMaxYCorner else kCALayerMinXMinYCorner
                }
                if (hasAnyRadius && maskBits != 0uL) {
                    layer.maskedCorners = maskBits
                } else {
                    layer.maskedCorners =
                        kCALayerMinXMinYCorner or kCALayerMaxXMinYCorner or kCALayerMinXMaxYCorner or kCALayerMaxXMaxYCorner
                }
                if (clipsToBounds != null) {
                    layer.masksToBounds = clipsToBounds
                }
                return radius
            }

            // Distinct positive per-corner radii: approximate max radius cleanly on CALayer
            // and apply an exact CAShapeLayer mask when clipping is active.
            val maxRadius = positives.maxOrNull() ?: 0.0
            layer.cornerRadius = maxRadius
            layer.maskedCorners =
                kCALayerMinXMinYCorner or kCALayerMaxXMinYCorner or kCALayerMinXMaxYCorner or kCALayerMaxXMaxYCorner
            val shouldClip = clipsToBounds ?: hasAnyRadius
            if (shouldClip && w > 0.0 && h > 0.0) {
                val path = createRoundedRectPath(
                    x = 0.0,
                    y = 0.0,
                    width = w,
                    height = h,
                    topLeft = resolved[0],
                    topRight = resolved[1],
                    bottomRight = resolved[2],
                    bottomLeft = resolved[3],
                    topLeftIsMinY = topLeftIsMinY
                )
                if (path != null) {
                    try {
                        val maskLayer = (layer.mask as? CAShapeLayer) ?: CAShapeLayer()
                        maskLayer.frame = CGRectMake(0.0, 0.0, w, h)
                        maskLayer.path = path
                        layer.mask = maskLayer
                    } finally {
                        CGPathRelease(path)
                    }
                }
                layer.masksToBounds = true
            } else {
                layer.mask = null
                if (clipsToBounds != null) {
                    layer.masksToBounds = clipsToBounds
                }
            }
            return maxRadius
        } finally {
            CATransaction.commit()
        }
    }

    /**
     * Configures [hostLayer] or an overlaid [CAShapeLayer] for `SOLID`, `DASHED`, `DOTTED`, or
     * `DOUBLE` borders, focus rings, and per-corner radii.
     */
    fun applyBorderLayer(
        hostLayer: CALayer,
        existingBorderLayer: CAShapeLayer?,
        boundsWidth: Double,
        boundsHeight: Double,
        borderColor: RgbaColor?,
        borderWidthPx: Float,
        borderStyle: CssBorderStyle?,
        radii: CssCornerRadii?,
        fallbackUniformPx: Float,
        topLeftIsMinY: Boolean = true
    ): CAShapeLayer? {
        val effectiveStyle = borderStyle ?: if (borderWidthPx > 0f) CssBorderStyle.SOLID else CssBorderStyle.NONE
        val hasVisibleBorder = borderColor != null && borderColor.a > 0 && borderWidthPx > 0f && effectiveStyle != CssBorderStyle.NONE
        if (!hasVisibleBorder) {
            hostLayer.borderWidth = 0.0
            hostLayer.borderColor = null
            existingBorderLayer?.removeFromSuperlayer()
            return null
        }

        val w = boundsWidth.coerceAtLeast(0.0)
        val h = boundsHeight.coerceAtLeast(0.0)
        val resolved = if (w > 0.0 && h > 0.0) {
            resolveCornerRadiiPx(radii, fallbackUniformPx, w, h)
        } else {
            doubleArrayOf(
                fallbackUniformPx.coerceAtLeast(0f).toDouble(),
                fallbackUniformPx.coerceAtLeast(0f).toDouble(),
                fallbackUniformPx.coerceAtLeast(0f).toDouble(),
                fallbackUniformPx.coerceAtLeast(0f).toDouble()
            )
        }
        val uniformCorners = resolved[0] == resolved[1] && resolved[1] == resolved[2] && resolved[2] == resolved[3]

        if (effectiveStyle == CssBorderStyle.SOLID && uniformCorners) {
            existingBorderLayer?.removeFromSuperlayer()
            withCgColor(borderColor!!) { cg ->
                hostLayer.borderColor = cg
            }
            hostLayer.borderWidth = borderWidthPx.toDouble()
            return null
        }

        hostLayer.borderWidth = 0.0
        hostLayer.borderColor = null
        if (w <= 0.0 || h <= 0.0) {
            return existingBorderLayer
        }

        val shape = existingBorderLayer ?: CAShapeLayer().also {
            hostLayer.addSublayer(it)
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        try {
            shape.frame = CGRectMake(0.0, 0.0, w, h)
            shape.fillColor = null
            withCgColor(borderColor!!) { cg ->
                shape.strokeColor = cg
            }
            shape.lineJoin = kCALineJoinMiter
            val strokeW = borderWidthPx.toDouble()

            when (effectiveStyle) {
                CssBorderStyle.DOUBLE -> {
                    val lineBand = max(1.0, strokeW / 3.0)
                    val outerInset = lineBand * 0.5
                    val innerInset = max(outerInset + lineBand * 2.0, strokeW - lineBand * 0.5)
                    shape.lineWidth = lineBand
                    shape.lineDashPattern = null
                    shape.lineCap = kCALineCapButt

                    val outerPath = createRoundedRectPath(
                        x = outerInset,
                        y = outerInset,
                        width = (w - outerInset * 2.0).coerceAtLeast(1.0),
                        height = (h - outerInset * 2.0).coerceAtLeast(1.0),
                        topLeft = max(0.0, resolved[0] - outerInset),
                        topRight = max(0.0, resolved[1] - outerInset),
                        bottomRight = max(0.0, resolved[2] - outerInset),
                        bottomLeft = max(0.0, resolved[3] - outerInset),
                        topLeftIsMinY = topLeftIsMinY
                    )
                    val combined = CGPathCreateMutable()
                    if (combined != null) {
                        if (outerPath != null) {
                            CGPathAddPath(combined, null, outerPath)
                            CGPathRelease(outerPath)
                        }
                        if (w > innerInset * 2.0 + 1.0 && h > innerInset * 2.0 + 1.0) {
                            val innerPath = createRoundedRectPath(
                                x = innerInset,
                                y = innerInset,
                                width = w - innerInset * 2.0,
                                height = h - innerInset * 2.0,
                                topLeft = max(0.0, resolved[0] - innerInset),
                                topRight = max(0.0, resolved[1] - innerInset),
                                bottomRight = max(0.0, resolved[2] - innerInset),
                                bottomLeft = max(0.0, resolved[3] - innerInset),
                                topLeftIsMinY = topLeftIsMinY
                            )
                            if (innerPath != null) {
                                CGPathAddPath(combined, null, innerPath)
                                CGPathRelease(innerPath)
                            }
                        }
                        shape.path = combined
                        CGPathRelease(combined)
                    }
                }

                CssBorderStyle.DASHED, CssBorderStyle.DOTTED -> {
                    val inset = strokeW * 0.5
                    shape.lineWidth = strokeW
                    if (effectiveStyle == CssBorderStyle.DOTTED) {
                        shape.lineCap = kCALineCapRound
                        shape.lineDashPattern = listOf(
                            NSNumber.numberWithDouble(0.01),
                            NSNumber.numberWithDouble(max(2.0, strokeW * 2.0))
                        )
                    } else {
                        shape.lineCap = kCALineCapButt
                        shape.lineDashPattern = listOf(
                            NSNumber.numberWithDouble(max(2.0, strokeW * 3.0)),
                            NSNumber.numberWithDouble(max(2.0, strokeW * 2.0))
                        )
                    }
                    val strokePath = createRoundedRectPath(
                        x = inset,
                        y = inset,
                        width = (w - strokeW).coerceAtLeast(1.0),
                        height = (h - strokeW).coerceAtLeast(1.0),
                        topLeft = max(0.0, resolved[0] - inset),
                        topRight = max(0.0, resolved[1] - inset),
                        bottomRight = max(0.0, resolved[2] - inset),
                        bottomLeft = max(0.0, resolved[3] - inset),
                        topLeftIsMinY = topLeftIsMinY
                    )
                    shape.path = strokePath
                    if (strokePath != null) CGPathRelease(strokePath)
                }

                else -> {
                    val inset = strokeW * 0.5
                    shape.lineWidth = strokeW
                    shape.lineDashPattern = null
                    shape.lineCap = kCALineCapButt
                    val strokePath = createRoundedRectPath(
                        x = inset,
                        y = inset,
                        width = (w - strokeW).coerceAtLeast(1.0),
                        height = (h - strokeW).coerceAtLeast(1.0),
                        topLeft = max(0.0, resolved[0] - inset),
                        topRight = max(0.0, resolved[1] - inset),
                        bottomRight = max(0.0, resolved[2] - inset),
                        bottomLeft = max(0.0, resolved[3] - inset),
                        topLeftIsMinY = topLeftIsMinY
                    )
                    shape.path = strokePath
                    if (strokePath != null) CGPathRelease(strokePath)
                }
            }
            if (shape.superlayer !== hostLayer) {
                hostLayer.addSublayer(shape)
            }
        } finally {
            CATransaction.commit()
        }
        return shape
    }

    /**
     * Configures a [CAGradientLayer] on [hostLayer] from a CSS [LinearGradient] or [RadialGradient]
     * using [GradientStopNormalizer] for browser-identical stop positions and geometry.
     */
    fun applyGradientLayer(
        hostLayer: CALayer,
        existingLayer: CAGradientLayer?,
        gradient: Gradient?,
        boundsWidth: Double,
        boundsHeight: Double,
        cornerRadius: Double,
        maskedCorners: ULong? = null,
        topLeftIsMinY: Boolean = true
    ): CAGradientLayer? {
        if (gradient == null) {
            existingLayer?.removeFromSuperlayer()
            return null
        }
        val layer = existingLayer ?: CAGradientLayer().also {
            hostLayer.insertSublayer(it, atIndex = 0u)
        }
        val w = boundsWidth.coerceAtLeast(1.0).toFloat()
        val h = boundsHeight.coerceAtLeast(1.0).toFloat()

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        try {
            layer.frame = CGRectMake(0.0, 0.0, boundsWidth.coerceAtLeast(0.0), boundsHeight.coerceAtLeast(0.0))
            layer.cornerRadius = cornerRadius
            if (maskedCorners != null) {
                layer.maskedCorners = maskedCorners
            }

            when (gradient) {
                is LinearGradient -> {
                    val norm = GradientStopNormalizer.normalizeLinear(gradient, w, h)
                    layer.type = kCAGradientLayerAxial
                    layer.colors = norm.colors.mapNotNull { rgba ->
                        createCgColor(rgba)?.let { CFBridgingRelease(it) }
                    }
                    layer.locations = norm.positions.map { NSNumber.numberWithDouble(it.toDouble()) }
                    val sy1 = if (topLeftIsMinY) (norm.y1 / h).toDouble() else 1.0 - (norm.y1 / h).toDouble()
                    val sy2 = if (topLeftIsMinY) (norm.y2 / h).toDouble() else 1.0 - (norm.y2 / h).toDouble()
                    layer.startPoint = CGPointMake((norm.x1 / w).toDouble(), sy1)
                    layer.endPoint = CGPointMake((norm.x2 / w).toDouble(), sy2)
                }

                is RadialGradient -> {
                    val norm = GradientStopNormalizer.normalizeRadial(gradient, w, h)
                    layer.type = kCAGradientLayerRadial
                    layer.colors = norm.colors.mapNotNull { rgba ->
                        createCgColor(rgba)?.let { CFBridgingRelease(it) }
                    }
                    layer.locations = norm.positions.map { NSNumber.numberWithDouble(it.toDouble()) }
                    val cyNorm = if (topLeftIsMinY) (norm.cy / h).toDouble() else 1.0 - (norm.cy / h).toDouble()
                    val endYNorm = if (topLeftIsMinY) ((norm.cy + norm.radiusY) / h).toDouble() else 1.0 - ((norm.cy + norm.radiusY) / h).toDouble()
                    layer.startPoint = CGPointMake((norm.cx / w).toDouble(), cyNorm)
                    layer.endPoint = CGPointMake(((norm.cx + norm.radiusX) / w).toDouble(), endYNorm)
                }

                else -> {
                    layer.colors = emptyList<Any>()
                    layer.locations = null
                }
            }
        } finally {
            CATransaction.commit()
        }
        return layer
    }

    /**
     * Applies box shadow chrome onto [layer].
     */
    fun applyLayerShadow(
        layer: CALayer,
        shadow: CssShadow?,
        fallbackColor: RgbaColor?,
        fallbackBlurPx: Float?,
        topLeftIsMinY: Boolean = true
    ) {
        val color = shadow?.color ?: fallbackColor
        if (color == null || color.a <= 0 || shadow?.inset == true) {
            layer.shadowOpacity = 0f
            layer.shadowRadius = 0.0
            layer.shadowColor = null
            layer.shadowOffset = CGSizeMake(0.0, 0.0)
            return
        }
        val blurPx = (shadow?.blurRadius?.resolvePx() ?: fallbackBlurPx ?: 6f).coerceAtLeast(0f).toDouble()
        val dx = (shadow?.offsetX?.resolvePx() ?: 0f).toDouble()
        val rawDy = (shadow?.offsetY?.resolvePx() ?: 2f).toDouble()
        // In CoreAnimation on iOS, +offset.height points down; on macOS, +offset.height points up.
        val dy = if (topLeftIsMinY) rawDy else -rawDy
        withCgColor(color.copy(a = 255)) { cg ->
            layer.shadowColor = cg
        }
        layer.shadowOpacity = (color.a / 255f).coerceIn(0f, 1f).let { alpha ->
            if (shadow == null && fallbackColor != null && fallbackColor.a == 255) 0.28f else alpha
        }
        layer.shadowRadius = (blurPx * 0.5).coerceAtLeast(0.0)
        layer.shadowOffset = CGSizeMake(dx, dy)
    }

    /**
     * Applies or removes a focus ring overlay [CAShapeLayer] around [hostLayer].
     */
    fun applyFocusRingLayer(
        hostLayer: CALayer,
        existingRingLayer: CAShapeLayer?,
        isFocused: Boolean,
        ringColor: RgbaColor?,
        ringWidthPx: Float?,
        boundsWidth: Double,
        boundsHeight: Double,
        radii: CssCornerRadii?,
        fallbackUniformPx: Float,
        topLeftIsMinY: Boolean = true
    ): CAShapeLayer? {
        val width = (ringWidthPx ?: 2f).coerceAtLeast(1f)
        if (!isFocused || ringColor == null || ringColor.a <= 0 || boundsWidth <= 0.0 || boundsHeight <= 0.0) {
            existingRingLayer?.removeFromSuperlayer()
            return null
        }
        val ring = existingRingLayer ?: CAShapeLayer().also { hostLayer.addSublayer(it) }
        val resolved = resolveCornerRadiiPx(radii, fallbackUniformPx, boundsWidth, boundsHeight)
        val inset = width * 0.5
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        try {
            ring.frame = CGRectMake(0.0, 0.0, boundsWidth, boundsHeight)
            ring.fillColor = null
            withCgColor(ringColor) { cg ->
                ring.strokeColor = cg
            }
            ring.lineWidth = width.toDouble()
            val path = createRoundedRectPath(
                x = inset,
                y = inset,
                width = (boundsWidth - width).coerceAtLeast(1.0),
                height = (boundsHeight - width).coerceAtLeast(1.0),
                topLeft = max(0.0, resolved[0] - inset),
                topRight = max(0.0, resolved[1] - inset),
                bottomRight = max(0.0, resolved[2] - inset),
                bottomLeft = max(0.0, resolved[3] - inset),
                topLeftIsMinY = topLeftIsMinY
            )
            ring.path = path
            if (path != null) CGPathRelease(path)
        } finally {
            CATransaction.commit()
        }
        return ring
    }

    /**
     * Draws a normalized [LinearGradient] or [RadialGradient] into [context] inside `(x, y, width, height)`
     * optionally clipped to [clipPath].
     */
    fun drawBackgroundGradient(
        context: CGContextRef,
        gradient: Gradient,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        clipPath: CGPathRef? = null
    ) {
        if (width <= 0.0 || height <= 0.0) return
        val wF = width.toFloat()
        val hF = height.toFloat()
        val space = CGColorSpaceCreateDeviceRGB() ?: return

        CGContextSaveGState(context)
        try {
            if (clipPath != null) {
                CGContextAddPath(context, clipPath)
                CGContextClip(context)
            }
            CGContextTranslateCTM(context, x, y)

            when (gradient) {
                is LinearGradient -> {
                    val norm = GradientStopNormalizer.normalizeLinear(gradient, wF, hF)
                    drawCgGradient(space, norm.colors, norm.positions) { cgGradient ->
                        CGContextDrawLinearGradient(
                            context,
                            cgGradient,
                            CGPointMake(norm.x1.toDouble(), norm.y1.toDouble()),
                            CGPointMake(norm.x2.toDouble(), norm.y2.toDouble()),
                            kCGGradientDrawsBeforeStartLocation or kCGGradientDrawsAfterEndLocation
                        )
                    }
                }

                is RadialGradient -> {
                    val norm = GradientStopNormalizer.normalizeRadial(gradient, wF, hF)
                    val rx = norm.radiusX.toDouble().coerceAtLeast(0.5)
                    val ry = norm.radiusY.toDouble().coerceAtLeast(0.5)
                    drawCgGradient(space, norm.colors, norm.positions) { cgGradient ->
                        CGContextSaveGState(context)
                        try {
                            CGContextTranslateCTM(context, norm.cx.toDouble(), norm.cy.toDouble())
                            CGContextConcatCTM(context, CGAffineTransformMakeScale(1.0, ry / rx))
                            CGContextDrawRadialGradient(
                                context,
                                cgGradient,
                                CGPointMake(0.0, 0.0),
                                0.0,
                                CGPointMake(0.0, 0.0),
                                rx,
                                kCGGradientDrawsBeforeStartLocation or kCGGradientDrawsAfterEndLocation
                            )
                        } finally {
                            CGContextRestoreGState(context)
                        }
                    }
                }

                else -> Unit
            }
        } finally {
            CGContextRestoreGState(context)
            CGColorSpaceRelease(space)
        }
    }

    /**
     * Draws a solid or gradient surface fill with optional box shadow into [context].
     */
    fun drawBoxShadowAndFill(
        context: CGContextRef,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: RgbaColor?,
        gradient: Gradient?,
        shadow: CssShadow?,
        fallbackShadowColor: RgbaColor?,
        fallbackShadowBlurPx: Float?,
        radii: CssCornerRadii?,
        fallbackUniformPx: Float,
        topLeftIsMinY: Boolean = true
    ) {
        if (width <= 0.0 || height <= 0.0) return
        val resolved = resolveCornerRadiiPx(radii, fallbackUniformPx, width, height)
        val path = createRoundedRectPath(
            x = x,
            y = y,
            width = width,
            height = height,
            topLeft = resolved[0],
            topRight = resolved[1],
            bottomRight = resolved[2],
            bottomLeft = resolved[3],
            topLeftIsMinY = topLeftIsMinY
        ) ?: return
        try {
            val shColor = shadow?.color ?: fallbackShadowColor
            if (shColor != null && shColor.a > 0 && shadow?.inset != true) {
                val blurPx = (shadow?.blurRadius?.resolvePx() ?: fallbackShadowBlurPx ?: 6f).coerceAtLeast(0f).toDouble()
                val dx = (shadow?.offsetX?.resolvePx() ?: 0f).toDouble()
                val rawDy = (shadow?.offsetY?.resolvePx() ?: 2f).toDouble()
                val dy = if (topLeftIsMinY) rawDy else -rawDy
                CGContextSaveGState(context)
                try {
                    withCgColor(shColor) { cgShadow ->
                        CGContextSetShadowWithColor(context, CGSizeMake(dx, dy), blurPx, cgShadow)
                    }
                    val opaqueBacking = fillColor ?: RgbaColor(255, 255, 255, 255)
                    withCgColor(opaqueBacking) { cgFill ->
                        CGContextSetFillColorWithColor(context, cgFill)
                        CGContextAddPath(context, path)
                        CGContextFillPath(context)
                    }
                } finally {
                    CGContextRestoreGState(context)
                }
            }
            if (gradient != null) {
                drawBackgroundGradient(context, gradient, x, y, width, height, clipPath = path)
            } else if (fillColor != null && fillColor.a > 0) {
                CGContextSaveGState(context)
                try {
                    withCgColor(fillColor) { cgFill ->
                        CGContextSetFillColorWithColor(context, cgFill)
                        CGContextAddPath(context, path)
                        CGContextFillPath(context)
                    }
                } finally {
                    CGContextRestoreGState(context)
                }
            }
        } finally {
            CGPathRelease(path)
        }
    }

    /**
     * Draws a CSS `SOLID`, `DASHED`, `DOTTED`, or `DOUBLE` border into [context].
     */
    fun drawBorder(
        context: CGContextRef,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        borderColor: RgbaColor,
        borderWidthPx: Float,
        borderStyle: CssBorderStyle,
        radii: CssCornerRadii?,
        fallbackUniformPx: Float,
        topLeftIsMinY: Boolean = true
    ) {
        if (borderWidthPx <= 0f || borderColor.a <= 0 || borderStyle == CssBorderStyle.NONE || width <= 0.0 || height <= 0.0) {
            return
        }
        val resolved = resolveCornerRadiiPx(radii, fallbackUniformPx, width, height)
        val strokeW = borderWidthPx.toDouble()

        CGContextSaveGState(context)
        try {
            withCgColor(borderColor) { cgStroke ->
                CGContextSetStrokeColorWithColor(context, cgStroke)
                when (borderStyle) {
                    CssBorderStyle.DOUBLE -> {
                        val band = max(1.0, strokeW / 3.0)
                        CGContextSetLineWidth(context, band)
                        val outerInset = band * 0.5
                        val innerInset = max(outerInset + band * 2.0, strokeW - band * 0.5)
                        strokeRoundedInset(context, x, y, width, height, outerInset, resolved, topLeftIsMinY)
                        if (width > innerInset * 2.0 + 1.0 && height > innerInset * 2.0 + 1.0) {
                            strokeRoundedInset(context, x, y, width, height, innerInset, resolved, topLeftIsMinY)
                        }
                    }

                    CssBorderStyle.DASHED -> {
                        CGContextSetLineWidth(context, strokeW)
                        CGContextSetLineCap(context, CGLineCap.kCGLineCapButt)
                        memScoped {
                            val dash = allocArray<DoubleVar>(2)
                            dash[0] = max(2.0, strokeW * 3.0)
                            dash[1] = max(2.0, strokeW * 2.0)
                            CGContextSetLineDash(context, 0.0, dash, 2uL)
                            strokeRoundedInset(context, x, y, width, height, strokeW * 0.5, resolved, topLeftIsMinY)
                        }
                    }

                    CssBorderStyle.DOTTED -> {
                        CGContextSetLineWidth(context, strokeW)
                        CGContextSetLineCap(context, CGLineCap.kCGLineCapRound)
                        memScoped {
                            val dot = allocArray<DoubleVar>(2)
                            dot[0] = 0.01
                            dot[1] = max(2.0, strokeW * 2.0)
                            CGContextSetLineDash(context, 0.0, dot, 2uL)
                            strokeRoundedInset(context, x, y, width, height, strokeW * 0.5, resolved, topLeftIsMinY)
                        }
                    }

                    else -> {
                        CGContextSetLineWidth(context, strokeW)
                        CGContextSetLineCap(context, CGLineCap.kCGLineCapButt)
                        strokeRoundedInset(context, x, y, width, height, strokeW * 0.5, resolved, topLeftIsMinY)
                    }
                }
            }
        } finally {
            CGContextRestoreGState(context)
        }
    }

    /**
     * Draws a focus ring outline inside [context].
     */
    fun drawFocusRing(
        context: CGContextRef,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        ringColor: RgbaColor,
        ringWidthPx: Float,
        radii: CssCornerRadii?,
        fallbackUniformPx: Float,
        topLeftIsMinY: Boolean = true
    ) {
        drawBorder(
            context = context,
            x = x,
            y = y,
            width = width,
            height = height,
            borderColor = ringColor,
            borderWidthPx = ringWidthPx.coerceAtLeast(1f),
            borderStyle = CssBorderStyle.SOLID,
            radii = radii,
            fallbackUniformPx = fallbackUniformPx,
            topLeftIsMinY = topLeftIsMinY
        )
    }

    private inline fun drawCgGradient(
        space: platform.CoreGraphics.CGColorSpaceRef,
        colors: Array<RgbaColor>,
        positions: FloatArray,
        block: (platform.CoreGraphics.CGGradientRef) -> Unit
    ) {
        if (colors.isEmpty()) return
        val count = colors.size
        memScoped {
            val comps = allocArray<DoubleVar>(count * 4)
            val locs = allocArray<DoubleVar>(count)
            for (i in 0 until count) {
                val c = colors[i]
                val base = i * 4
                comps[base] = c.r / 255.0
                comps[base + 1] = c.g / 255.0
                comps[base + 2] = c.b / 255.0
                comps[base + 3] = c.a / 255.0
                locs[i] = positions.getOrElse(i) {
                    if (count <= 1) 0f else i.toFloat() / (count - 1).toFloat()
                }.toDouble()
            }
            val grad = CGGradientCreateWithColorComponents(space, comps, locs, count.toULong()) ?: return
            try {
                block(grad)
            } finally {
                CGGradientRelease(grad)
            }
        }
    }

    private fun strokeRoundedInset(
        context: CGContextRef,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        inset: Double,
        resolved: DoubleArray,
        topLeftIsMinY: Boolean
    ) {
        val path = createRoundedRectPath(
            x = x + inset,
            y = y + inset,
            width = (width - inset * 2.0).coerceAtLeast(1.0),
            height = (height - inset * 2.0).coerceAtLeast(1.0),
            topLeft = max(0.0, resolved[0] - inset),
            topRight = max(0.0, resolved[1] - inset),
            bottomRight = max(0.0, resolved[2] - inset),
            bottomLeft = max(0.0, resolved[3] - inset),
            topLeftIsMinY = topLeftIsMinY
        ) ?: return
        try {
            CGContextAddPath(context, path)
            CGContextStrokePath(context)
        } finally {
            CGPathRelease(path)
        }
    }
}
