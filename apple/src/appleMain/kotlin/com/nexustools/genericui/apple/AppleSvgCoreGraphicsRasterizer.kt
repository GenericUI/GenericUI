@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextAddCurveToPoint
import platform.CoreGraphics.CGContextAddEllipseInRect
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextAddQuadCurveToPoint
import platform.CoreGraphics.CGContextAddRect
import platform.CoreGraphics.CGContextBeginPath
import platform.CoreGraphics.CGContextClosePath
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextEOFillPath
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetAlpha
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetLineJoin
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextSetRGBStrokeColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGLineJoin
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Vector SVG rasterizer backed by Apple CoreGraphics (`CGBitmapContext` / `CGContext`).
 */
object AppleSvgCoreGraphicsRasterizer {

    fun probeDimensions(svgBytes: ByteArray): Pair<Int, Int>? {
        val text = svgBytes.decodeToString()
        if (!looksLikeSvgText(text)) return null
        val header = parseSvgHeader(text)
        return header.naturalWidth to header.naturalHeight
    }

    fun rasterize(
        svgBytes: ByteArray,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer? {
        val svgText = svgBytes.decodeToString()
        if (!looksLikeSvgText(svgText)) return null

        val header = parseSvgHeader(svgText)
        val (dstWidth, dstHeight) = ImageLoader.resolveTargetDimensions(
            header.naturalWidth,
            header.naturalHeight,
            options
        )
        if (dstWidth <= 0 || dstHeight <= 0) return null

        val rgbaBytes = ByteArray(dstWidth * dstHeight * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
        val bitmapInfo = kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
        val ctx: CGContextRef? = rgbaBytes.usePinned { pinned ->
            CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = dstWidth.toULong(),
                height = dstHeight.toULong(),
                bitsPerComponent = 8uL,
                bytesPerRow = (dstWidth * 4).toULong(),
                space = colorSpace,
                bitmapInfo = bitmapInfo
            )
        }
        if (ctx == null) {
            CGColorSpaceRelease(colorSpace)
            return null
        }

        try {
            CGContextSaveGState(ctx)
            // Flip CGBitmapContext user-space to SVG top-left coordinates.
            CGContextTranslateCTM(ctx, 0.0, dstHeight.toDouble())
            CGContextScaleCTM(ctx, 1.0, -1.0)

            val scaleX = dstWidth.toDouble() / header.viewBoxWidth
            val scaleY = dstHeight.toDouble() / header.viewBoxHeight
            if (options.preserveAspectRatio && header.preserveAspect) {
                val scale = min(scaleX, scaleY)
                val offsetX = (dstWidth.toDouble() - header.viewBoxWidth * scale) / 2.0
                val offsetY = (dstHeight.toDouble() - header.viewBoxHeight * scale) / 2.0
                CGContextTranslateCTM(ctx, offsetX, offsetY)
                CGContextScaleCTM(ctx, scale, scale)
            } else {
                CGContextScaleCTM(ctx, scaleX, scaleY)
            }
            CGContextTranslateCTM(ctx, -header.viewBoxMinX, -header.viewBoxMinY)

            val initialStyle = RenderStyle(
                fillColor = SvgColor(0.0, 0.0, 0.0, 1.0),
                fillSet = true,
                strokeColor = null,
                strokeWidth = 1.0,
                opacity = 1.0,
                fillOpacity = 1.0,
                strokeOpacity = 1.0,
                evenOddFill = false,
                lineCap = CGLineCap.kCGLineCapButt,
                lineJoin = CGLineJoin.kCGLineJoinMiter
            )
            renderElements(ctx, svgText, initialStyle)
            CGContextRestoreGState(ctx)
        } finally {
            CGContextRelease(ctx)
            CGColorSpaceRelease(colorSpace)
        }

        val finalRgba = if (options.premultiplyAlpha) {
            rgbaBytes
        } else {
            AppleImagePixelUtils.unpremultiplyRgba(rgbaBytes)
        }
        val baseBuffer = ImageBuffer(
            width = dstWidth,
            height = dstHeight,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            bytes = finalRgba
        )
        return AppleImagePixelUtils.convertRgbaToTargetFormat(baseBuffer, options.preferredPixelFormat)
    }

    private fun looksLikeSvgText(text: String): Boolean {
        val sample = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n').take(1024).lowercase()
        return sample.contains("<svg")
    }

    private fun parseSvgHeader(svgText: String): SvgHeader {
        val openIdx = svgText.indexOf("<svg", ignoreCase = true)
        if (openIdx < 0) return SvgHeader(48, 48, 0.0, 0.0, 48.0, 48.0, true)
        val closeIdx = svgText.indexOf('>', startIndex = openIdx).takeIf { it > openIdx } ?: svgText.length
        val attrs = parseAttributes(svgText.substring(openIdx + 4, closeIdx))

        val viewBoxParts = attrs["viewbox"]
            ?.trim()
            ?.split(',', ' ', '\t', '\r', '\n')
            ?.mapNotNull { it.trim().toDoubleOrNull() }
            ?.takeIf { it.size >= 4 }

        val attrW = attrs["width"]?.let { parseLength(it) }
        val attrH = attrs["height"]?.let { parseLength(it) }

        val vbMinX = viewBoxParts?.get(0) ?: 0.0
        val vbMinY = viewBoxParts?.get(1) ?: 0.0
        val vbW = (viewBoxParts?.get(2) ?: attrW ?: 48.0).let { if (it > 0.0) it else 48.0 }
        val vbH = (viewBoxParts?.get(3) ?: attrH ?: 48.0).let { if (it > 0.0) it else 48.0 }

        val natW = (attrW ?: vbW).roundToInt().coerceAtLeast(1)
        val natH = (attrH ?: vbH).roundToInt().coerceAtLeast(1)
        val preserve = attrs["preserveaspectratio"]?.lowercase()?.trim() != "none"
        return SvgHeader(natW, natH, vbMinX, vbMinY, vbW, vbH, preserve)
    }

    private fun renderElements(ctx: CGContextRef, svgText: String, rootStyle: RenderStyle) {
        val styleStack = ArrayList<RenderStyle>()
        styleStack.add(rootStyle)

        var pos = 0
        val n = svgText.length
        while (pos < n) {
            val lt = svgText.indexOf('<', pos)
            if (lt < 0 || lt + 1 >= n) break
            if (svgText.startsWith("<!--", lt)) {
                val endComment = svgText.indexOf("-->", lt + 4)
                pos = if (endComment < 0) n else endComment + 3
                continue
            }
            if (svgText[lt + 1] == '?' || svgText[lt + 1] == '!') {
                val gt = svgText.indexOf('>', lt + 2)
                pos = if (gt < 0) n else gt + 1
                continue
            }
            val gt = findTagEnd(svgText, lt + 1)
            if (gt < 0) break

            val rawTag = svgText.substring(lt + 1, gt).trim()
            pos = gt + 1
            if (rawTag.isEmpty()) continue

            if (rawTag.startsWith("/")) {
                val closeName = rawTag.substring(1).trim().lowercase()
                if (closeName == "g" || closeName == "svg") {
                    if (styleStack.size > 1) {
                        CGContextRestoreGState(ctx)
                        styleStack.removeAt(styleStack.lastIndex)
                    }
                }
                continue
            }

            val selfClosing = rawTag.endsWith("/")
            val tagBody = if (selfClosing) rawTag.dropLast(1).trim() else rawTag
            val spaceIdx = tagBody.indexOfFirst { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
            val tagName = (if (spaceIdx < 0) tagBody else tagBody.substring(0, spaceIdx)).lowercase()
            val attrText = if (spaceIdx < 0) "" else tagBody.substring(spaceIdx + 1)
            val attrs = parseAttributes(attrText)

            val parentStyle = styleStack.last()
            val elementStyle = mergeStyle(parentStyle, attrs)

            if (tagName == "g" || tagName == "svg") {
                if (!selfClosing) {
                    CGContextSaveGState(ctx)
                    attrs["transform"]?.let { applyTransformString(ctx, it) }
                    styleStack.add(elementStyle)
                }
                continue
            }

            if (elementStyle.displayNone) continue

            CGContextSaveGState(ctx)
            attrs["transform"]?.let { applyTransformString(ctx, it) }
            if (elementStyle.opacity < 1.0) {
                CGContextSetAlpha(ctx, elementStyle.opacity.coerceIn(0.0, 1.0))
            }

            when (tagName) {
                "rect" -> drawRect(ctx, attrs, elementStyle)
                "circle" -> drawCircle(ctx, attrs, elementStyle)
                "ellipse" -> drawEllipse(ctx, attrs, elementStyle)
                "line" -> drawLine(ctx, attrs, elementStyle)
                "polyline" -> drawPoly(ctx, attrs, elementStyle, closed = false)
                "polygon" -> drawPoly(ctx, attrs, elementStyle, closed = true)
                "path" -> attrs["d"]?.let { drawPath(ctx, it, elementStyle) }
            }

            CGContextRestoreGState(ctx)
        }

        while (styleStack.size > 1) {
            CGContextRestoreGState(ctx)
            styleStack.removeAt(styleStack.lastIndex)
        }
    }

    private fun drawRect(ctx: CGContextRef, attrs: Map<String, String>, style: RenderStyle) {
        val x = attrs["x"]?.let(::parseLength) ?: 0.0
        val y = attrs["y"]?.let(::parseLength) ?: 0.0
        val w = attrs["width"]?.let(::parseLength) ?: 0.0
        val h = attrs["height"]?.let(::parseLength) ?: 0.0
        if (w <= 0.0 || h <= 0.0) return

        val rxAttr = attrs["rx"]?.let(::parseLength)
        val ryAttr = attrs["ry"]?.let(::parseLength)
        val rx = min(rxAttr ?: ryAttr ?: 0.0, w / 2.0).coerceAtLeast(0.0)
        val ry = min(ryAttr ?: rxAttr ?: 0.0, h / 2.0).coerceAtLeast(0.0)

        CGContextBeginPath(ctx)
        if (rx > 0.0 || ry > 0.0) {
            val r = min(rx, ry)
            val kappa = 0.5522847498307936
            val ox = rx * kappa
            val oy = ry * kappa
            val x2 = x + w
            val y2 = y + h
            CGContextMoveToPoint(ctx, x + rx, y)
            CGContextAddLineToPoint(ctx, x2 - rx, y)
            CGContextAddCurveToPoint(ctx, x2 - rx + ox, y, x2, y + ry - oy, x2, y + ry)
            CGContextAddLineToPoint(ctx, x2, y2 - ry)
            CGContextAddCurveToPoint(ctx, x2, y2 - ry + oy, x2 - rx + ox, y2, x2 - rx, y2)
            CGContextAddLineToPoint(ctx, x + rx, y2)
            CGContextAddCurveToPoint(ctx, x + rx - ox, y2, x, y2 - ry + oy, x, y2 - ry)
            CGContextAddLineToPoint(ctx, x, y + ry)
            CGContextAddCurveToPoint(ctx, x, y + ry - oy, x + rx - ox, y, x + rx, y)
            if (r >= 0.0) {
                CGContextClosePath(ctx)
            }
        } else {
            CGContextAddRect(ctx, CGRectMake(x, y, w, h))
        }
        paintCurrentPath(ctx, style, allowFill = true)
    }

    private fun drawCircle(ctx: CGContextRef, attrs: Map<String, String>, style: RenderStyle) {
        val cx = attrs["cx"]?.let(::parseLength) ?: 0.0
        val cy = attrs["cy"]?.let(::parseLength) ?: 0.0
        val r = attrs["r"]?.let(::parseLength) ?: 0.0
        if (r <= 0.0) return
        CGContextBeginPath(ctx)
        CGContextAddEllipseInRect(ctx, CGRectMake(cx - r, cy - r, r * 2.0, r * 2.0))
        paintCurrentPath(ctx, style, allowFill = true)
    }

    private fun drawEllipse(ctx: CGContextRef, attrs: Map<String, String>, style: RenderStyle) {
        val cx = attrs["cx"]?.let(::parseLength) ?: 0.0
        val cy = attrs["cy"]?.let(::parseLength) ?: 0.0
        val rx = attrs["rx"]?.let(::parseLength) ?: 0.0
        val ry = attrs["ry"]?.let(::parseLength) ?: 0.0
        if (rx <= 0.0 || ry <= 0.0) return
        CGContextBeginPath(ctx)
        CGContextAddEllipseInRect(ctx, CGRectMake(cx - rx, cy - ry, rx * 2.0, ry * 2.0))
        paintCurrentPath(ctx, style, allowFill = true)
    }

    private fun drawLine(ctx: CGContextRef, attrs: Map<String, String>, style: RenderStyle) {
        val x1 = attrs["x1"]?.let(::parseLength) ?: 0.0
        val y1 = attrs["y1"]?.let(::parseLength) ?: 0.0
        val x2 = attrs["x2"]?.let(::parseLength) ?: 0.0
        val y2 = attrs["y2"]?.let(::parseLength) ?: 0.0
        CGContextBeginPath(ctx)
        CGContextMoveToPoint(ctx, x1, y1)
        CGContextAddLineToPoint(ctx, x2, y2)
        paintCurrentPath(ctx, style, allowFill = false)
    }

    private fun drawPoly(ctx: CGContextRef, attrs: Map<String, String>, style: RenderStyle, closed: Boolean) {
        val ptsRaw = attrs["points"] ?: return
        val nums = extractAllNumbers(ptsRaw)
        if (nums.size < 4) return
        CGContextBeginPath(ctx)
        CGContextMoveToPoint(ctx, nums[0], nums[1])
        var i = 2
        while (i + 1 < nums.size) {
            CGContextAddLineToPoint(ctx, nums[i], nums[i + 1])
            i += 2
        }
        if (closed) {
            CGContextClosePath(ctx)
        }
        paintCurrentPath(ctx, style, allowFill = true)
    }

    private fun drawPath(ctx: CGContextRef, d: String, style: RenderStyle) {
        val tokens = tokenizePathData(d)
        if (tokens.isEmpty()) return

        CGContextBeginPath(ctx)
        var curX = 0.0
        var curY = 0.0
        var startX = 0.0
        var startY = 0.0
        var lastCubicCtrlX = 0.0
        var lastCubicCtrlY = 0.0
        var lastQuadCtrlX = 0.0
        var lastQuadCtrlY = 0.0
        var lastCmd = ' '

        var idx = 0
        var currentCmd = 'M'
        while (idx < tokens.size) {
            val tok = tokens[idx]
            if (tok.length == 1 && tok[0].isLetter()) {
                currentCmd = tok[0]
                idx++
                if (currentCmd == 'Z' || currentCmd == 'z') {
                    CGContextClosePath(ctx)
                    curX = startX
                    curY = startY
                    lastCmd = currentCmd
                    continue
                }
            }

            fun nextNum(): Double? {
                if (idx >= tokens.size) return null
                val t = tokens[idx]
                if (t.length == 1 && t[0].isLetter()) return null
                val parsed = t.toDoubleOrNull() ?: return null
                idx++
                return parsed
            }

            fun nextFlag(): Int? {
                val n = nextNum() ?: return null
                return if (n != 0.0) 1 else 0
            }

            val isRel = currentCmd.isLowerCase()
            when (currentCmd.uppercaseChar()) {
                'M' -> {
                    val x = nextNum() ?: break
                    val y = nextNum() ?: break
                    val absX = if (isRel) curX + x else x
                    val absY = if (isRel) curY + y else y
                    CGContextMoveToPoint(ctx, absX, absY)
                    curX = absX
                    curY = absY
                    startX = absX
                    startY = absY
                    lastCmd = currentCmd
                    currentCmd = if (isRel) 'l' else 'L'
                }
                'L' -> {
                    val x = nextNum() ?: break
                    val y = nextNum() ?: break
                    val absX = if (isRel) curX + x else x
                    val absY = if (isRel) curY + y else y
                    CGContextAddLineToPoint(ctx, absX, absY)
                    curX = absX
                    curY = absY
                    lastCmd = currentCmd
                }
                'H' -> {
                    val x = nextNum() ?: break
                    val absX = if (isRel) curX + x else x
                    CGContextAddLineToPoint(ctx, absX, curY)
                    curX = absX
                    lastCmd = currentCmd
                }
                'V' -> {
                    val y = nextNum() ?: break
                    val absY = if (isRel) curY + y else y
                    CGContextAddLineToPoint(ctx, curX, absY)
                    curY = absY
                    lastCmd = currentCmd
                }
                'C' -> {
                    val x1 = nextNum() ?: break
                    val y1 = nextNum() ?: break
                    val x2 = nextNum() ?: break
                    val y2 = nextNum() ?: break
                    val x = nextNum() ?: break
                    val y = nextNum() ?: break
                    val ax1 = if (isRel) curX + x1 else x1
                    val ay1 = if (isRel) curY + y1 else y1
                    val ax2 = if (isRel) curX + x2 else x2
                    val ay2 = if (isRel) curY + y2 else y2
                    val ax = if (isRel) curX + x else x
                    val ay = if (isRel) curY + y else y
                    CGContextAddCurveToPoint(ctx, ax1, ay1, ax2, ay2, ax, ay)
                    lastCubicCtrlX = ax2
                    lastCubicCtrlY = ay2
                    curX = ax
                    curY = ay
                    lastCmd = currentCmd
                }
                'S' -> {
                    val x2 = nextNum() ?: break
                    val y2 = nextNum() ?: break
                    val x = nextNum() ?: break
                    val y = nextNum() ?: break
                    val prevUp = lastCmd.uppercaseChar()
                    val ax1 = if (prevUp == 'C' || prevUp == 'S') 2.0 * curX - lastCubicCtrlX else curX
                    val ay1 = if (prevUp == 'C' || prevUp == 'S') 2.0 * curY - lastCubicCtrlY else curY
                    val ax2 = if (isRel) curX + x2 else x2
                    val ay2 = if (isRel) curY + y2 else y2
                    val ax = if (isRel) curX + x else x
                    val ay = if (isRel) curY + y else y
                    CGContextAddCurveToPoint(ctx, ax1, ay1, ax2, ay2, ax, ay)
                    lastCubicCtrlX = ax2
                    lastCubicCtrlY = ay2
                    curX = ax
                    curY = ay
                    lastCmd = currentCmd
                }
                'Q' -> {
                    val x1 = nextNum() ?: break
                    val y1 = nextNum() ?: break
                    val x = nextNum() ?: break
                    val y = nextNum() ?: break
                    val ax1 = if (isRel) curX + x1 else x1
                    val ay1 = if (isRel) curY + y1 else y1
                    val ax = if (isRel) curX + x else x
                    val ay = if (isRel) curY + y else y
                    CGContextAddQuadCurveToPoint(ctx, ax1, ay1, ax, ay)
                    lastQuadCtrlX = ax1
                    lastQuadCtrlY = ay1
                    curX = ax
                    curY = ay
                    lastCmd = currentCmd
                }
                'T' -> {
                    val x = nextNum() ?: break
                    val y = nextNum() ?: break
                    val prevUp = lastCmd.uppercaseChar()
                    val ax1 = if (prevUp == 'Q' || prevUp == 'T') 2.0 * curX - lastQuadCtrlX else curX
                    val ay1 = if (prevUp == 'Q' || prevUp == 'T') 2.0 * curY - lastQuadCtrlY else curY
                    val ax = if (isRel) curX + x else x
                    val ay = if (isRel) curY + y else y
                    CGContextAddQuadCurveToPoint(ctx, ax1, ay1, ax, ay)
                    lastQuadCtrlX = ax1
                    lastQuadCtrlY = ay1
                    curX = ax
                    curY = ay
                    lastCmd = currentCmd
                }
                'A' -> {
                    val rx = nextNum() ?: break
                    val ry = nextNum() ?: break
                    val xAxisRot = nextNum() ?: break
                    val largeArcFlag = nextFlag() ?: break
                    val sweepFlag = nextFlag() ?: break
                    val x = nextNum() ?: break
                    val y = nextNum() ?: break
                    val ax = if (isRel) curX + x else x
                    val ay = if (isRel) curY + y else y
                    addSvgArcAsCubics(ctx, curX, curY, rx, ry, xAxisRot, largeArcFlag != 0, sweepFlag != 0, ax, ay)
                    curX = ax
                    curY = ay
                    lastCmd = currentCmd
                }
                else -> idx++
            }
        }

        paintCurrentPath(ctx, style, allowFill = true)
    }

    private fun addSvgArcAsCubics(
        ctx: CGContextRef,
        x0: Double,
        y0: Double,
        rxIn: Double,
        ryIn: Double,
        angleDeg: Double,
        largeArc: Boolean,
        sweep: Boolean,
        x: Double,
        y: Double
    ) {
        if (x0 == x && y0 == y) return
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if (rx == 0.0 || ry == 0.0) {
            CGContextAddLineToPoint(ctx, x, y)
            return
        }

        val phi = angleDeg * PI / 180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (x0 - x) / 2.0
        val dy2 = (y0 - y) / 2.0
        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        var rxSq = rx * rx
        var rySq = ry * ry
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        val lambda = x1pSq / rxSq + y1pSq / rySq
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rx *= scale
            ry *= scale
            rxSq = rx * rx
            rySq = ry * ry
        }

        val num = max(0.0, rxSq * rySq - rxSq * y1pSq - rySq * x1pSq)
        val den = rxSq * y1pSq + rySq * x1pSq
        val factor = if (den == 0.0) 0.0 else sqrt(num / den) * if (largeArc == sweep) -1.0 else 1.0
        val cxp = factor * (rx * y1p / ry)
        val cyp = factor * (-ry * x1p / rx)

        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0

        fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            val clamped = if (len == 0.0) 1.0 else (dot / len).coerceIn(-1.0, 1.0)
            val ang = acos(clamped)
            return if (ux * vy - uy * vx < 0.0) -ang else ang
        }

        val theta1 = vectorAngle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dTheta = vectorAngle(
            (x1p - cxp) / rx,
            (y1p - cyp) / ry,
            (-x1p - cxp) / rx,
            (-y1p - cyp) / ry
        )
        if (!sweep && dTheta > 0.0) dTheta -= 2.0 * PI
        if (sweep && dTheta < 0.0) dTheta += 2.0 * PI

        val segments = ceil(abs(dTheta) / (PI / 2.0)).toInt().coerceAtLeast(1)
        val delta = dTheta / segments
        val t = (8.0 / 3.0) * sin(delta / 4.0) * sin(delta / 4.0) / sin(delta / 2.0)

        for (seg in 0 until segments) {
            val a1 = theta1 + seg * delta
            val a2 = theta1 + (seg + 1) * delta
            val cos1 = cos(a1)
            val sin1 = sin(a1)
            val cos2 = cos(a2)
            val sin2 = sin(a2)

            val ep1x = cos1 - t * sin1
            val ep1y = sin1 + t * cos1
            val ep2x = cos2 + t * sin2
            val ep2y = sin2 - t * cos2

            fun mapX(px: Double, py: Double) = cx + cosPhi * (rx * px) - sinPhi * (ry * py)
            fun mapY(px: Double, py: Double) = cy + sinPhi * (rx * px) + cosPhi * (ry * py)

            CGContextAddCurveToPoint(
                ctx,
                mapX(ep1x, ep1y),
                mapY(ep1x, ep1y),
                mapX(ep2x, ep2y),
                mapY(ep2x, ep2y),
                mapX(cos2, sin2),
                mapY(cos2, sin2)
            )
        }
    }

    private fun paintCurrentPath(ctx: CGContextRef, style: RenderStyle, allowFill: Boolean) {
        val fillColor = if (allowFill) style.fillColor else null
        val strokeColor = style.strokeColor?.takeIf { style.strokeWidth > 0.0 }

        if (fillColor != null && strokeColor != null) {
            // CoreGraphics consumes path on fill; save/re-use isn't preserved unless drawn via fill+stroke mode or CGPath,
            // so fill and stroke separately only when needed — actually we can use CGContextDrawPath or re-apply path?
            // Simple: fill first isn't sufficient alone because fill clears current path in CGContext unless copied,
            // OR let's copy path with CGContextCopyPath? Better: CGContextFillPath consumes path ONLY IF not using kCGPathFillStroke!
            // Let's keep fill+stroke clean using CGContextSetRGBFillColor & CGContextSetRGBStrokeColor & CGContextDrawPath?
            // Even simpler: use CGContextDrawPath if available or render separate fill/stroke passes!
            // Let's check: CGContextCopyPath IS standard in CoreGraphics; let's verify or use CGContextDrawPath.
            // Using CGContextFillPath for fill when strokeColor == null, and if both present preserve path via CGPathCreateMutableCopy?
            //Wait: CGContextFillPath + CGContextStrokePath: wait — let's save via fill color / stroke color!
            CGContextSetRGBFillColor(
                ctx,
                fillColor.r,
                fillColor.g,
                fillColor.b,
                (fillColor.a * style.fillOpacity).coerceIn(0.0, 1.0)
            )
            val savedPath = platform.CoreGraphics.CGContextCopyPath(ctx)
            if (style.evenOddFill) CGContextEOFillPath(ctx) else CGContextFillPath(ctx)
            if (savedPath != null) {
                platform.CoreGraphics.CGContextAddPath(ctx, savedPath)
                platform.CoreGraphics.CGPathRelease(savedPath)
                applyStrokeSettings(ctx, strokeColor, style)
                CGContextStrokePath(ctx)
            }
            return
        }

        if (fillColor != null) {
            CGContextSetRGBFillColor(
                ctx,
                fillColor.r,
                fillColor.g,
                fillColor.b,
                (fillColor.a * style.fillOpacity).coerceIn(0.0, 1.0)
            )
            if (style.evenOddFill) CGContextEOFillPath(ctx) else CGContextFillPath(ctx)
            return
        }

        if (strokeColor != null) {
            applyStrokeSettings(ctx, strokeColor, style)
            CGContextStrokePath(ctx)
        }
    }

    private fun applyStrokeSettings(ctx: CGContextRef, strokeColor: SvgColor, style: RenderStyle) {
        CGContextSetRGBStrokeColor(
            ctx,
            strokeColor.r,
            strokeColor.g,
            strokeColor.b,
            (strokeColor.a * style.strokeOpacity).coerceIn(0.0, 1.0)
        )
        CGContextSetLineWidth(ctx, style.strokeWidth)
        CGContextSetLineCap(ctx, style.lineCap)
        CGContextSetLineJoin(ctx, style.lineJoin)
    }

    private fun applyTransformString(ctx: CGContextRef, transformStr: String) {
        var idx = 0
        while (idx < transformStr.length) {
            val open = transformStr.indexOf('(', idx)
            if (open < 0) break
            val close = transformStr.indexOf(')', open + 1)
            if (close < 0) break
            val fnName = transformStr.substring(idx, open).trim().lowercase()
            val args = extractAllNumbers(transformStr.substring(open + 1, close))
            when (fnName) {
                "translate" -> if (args.isNotEmpty()) {
                    CGContextTranslateCTM(ctx, args[0], args.getOrElse(1) { 0.0 })
                }
                "scale" -> if (args.isNotEmpty()) {
                    val sx = args[0]
                    CGContextScaleCTM(ctx, sx, args.getOrElse(1) { sx })
                }
                "rotate" -> if (args.isNotEmpty()) {
                    val angleRad = args[0] * PI / 180.0
                    if (args.size >= 3) {
                        CGContextTranslateCTM(ctx, args[1], args[2])
                        CGContextRotateCTM(ctx, angleRad)
                        CGContextTranslateCTM(ctx, -args[1], -args[2])
                    } else {
                        CGContextRotateCTM(ctx, angleRad)
                    }
                }
                "matrix" -> if (args.size >= 6) {
                    CGContextConcatCTM(ctx, CGAffineTransformMake(args[0], args[1], args[2], args[3], args[4], args[5]))
                }
            }
            idx = close + 1
        }
    }

    private fun mergeStyle(parent: RenderStyle, attrs: Map<String, String>): RenderStyle {
        val mergedAttrs = LinkedHashMap(attrs)
        attrs["style"]?.let { styleDecl ->
            for (part in styleDecl.split(';')) {
                val colon = part.indexOf(':')
                if (colon > 0) {
                    val k = part.substring(0, colon).trim().lowercase()
                    val v = part.substring(colon + 1).trim()
                    if (k.isNotEmpty()) mergedAttrs[k] = v
                }
            }
        }

        val currentColor = mergedAttrs["color"]?.let { parseSvgColor(it, parent.currentColor) } ?: parent.currentColor
        val fillColor = mergedAttrs["fill"]?.let { parseSvgColor(it, currentColor) } ?: parent.fillColor
        val strokeColor = mergedAttrs["stroke"]?.let { parseSvgColor(it, currentColor) } ?: parent.strokeColor
        val strokeWidth = mergedAttrs["stroke-width"]?.let(::parseLength) ?: parent.strokeWidth
        val opacity = mergedAttrs["opacity"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
        val fillOpacity = mergedAttrs["fill-opacity"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: parent.fillOpacity
        val strokeOpacity = mergedAttrs["stroke-opacity"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: parent.strokeOpacity
        val evenOdd = mergedAttrs["fill-rule"]?.lowercase()?.trim()?.let { it == "evenodd" } ?: parent.evenOddFill
        val cap = when (mergedAttrs["stroke-linecap"]?.lowercase()?.trim()) {
            "round" -> CGLineCap.kCGLineCapRound
            "square" -> CGLineCap.kCGLineCapSquare
            "butt" -> CGLineCap.kCGLineCapButt
            else -> parent.lineCap
        }
        val join = when (mergedAttrs["stroke-linejoin"]?.lowercase()?.trim()) {
            "round" -> CGLineJoin.kCGLineJoinRound
            "bevel" -> CGLineJoin.kCGLineJoinBevel
            "miter" -> CGLineJoin.kCGLineJoinMiter
            else -> parent.lineJoin
        }
        val displayNone = mergedAttrs["display"]?.lowercase()?.trim() == "none" ||
            mergedAttrs["visibility"]?.lowercase()?.trim() == "hidden"

        return RenderStyle(
            fillColor = fillColor,
            fillSet = mergedAttrs.containsKey("fill") || parent.fillSet,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            opacity = opacity,
            fillOpacity = fillOpacity,
            strokeOpacity = strokeOpacity,
            evenOddFill = evenOdd,
            lineCap = cap,
            lineJoin = join,
            currentColor = currentColor,
            displayNone = displayNone
        )
    }

    private fun parseSvgColor(raw: String, currentColor: SvgColor): SvgColor? {
        val value = raw.trim().lowercase()
        if (value.isEmpty() || value == "none" || value == "transparent") return null
        if (value == "currentcolor") return currentColor

        if (value.startsWith("#")) {
            val hex = value.substring(1)
            return when (hex.length) {
                3 -> {
                    val r = hex[0].digitToIntOrNull(16) ?: return null
                    val g = hex[1].digitToIntOrNull(16) ?: return null
                    val b = hex[2].digitToIntOrNull(16) ?: return null
                    SvgColor((r * 17) / 255.0, (g * 17) / 255.0, (b * 17) / 255.0, 1.0)
                }
                6 -> {
                    val rgb = hex.toLongOrNull(16) ?: return null
                    SvgColor(
                        ((rgb ushr 16) and 0xFFL) / 255.0,
                        ((rgb ushr 8) and 0xFFL) / 255.0,
                        (rgb and 0xFFL) / 255.0,
                        1.0
                    )
                }
                8 -> {
                    val rgba = hex.toLongOrNull(16) ?: return null
                    SvgColor(
                        ((rgba ushr 24) and 0xFFL) / 255.0,
                        ((rgba ushr 16) and 0xFFL) / 255.0,
                        ((rgba ushr 8) and 0xFFL) / 255.0,
                        (rgba and 0xFFL) / 255.0
                    )
                }
                else -> null
            }
        }

        if (value.startsWith("rgb(") || value.startsWith("rgba(")) {
            val nums = extractAllNumbers(value)
            if (nums.size >= 3) {
                val r = (nums[0] / 255.0).coerceIn(0.0, 1.0)
                val g = (nums[1] / 255.0).coerceIn(0.0, 1.0)
                val b = (nums[2] / 255.0).coerceIn(0.0, 1.0)
                val a = if (nums.size >= 4) nums[3].coerceIn(0.0, 1.0) else 1.0
                return SvgColor(r, g, b, a)
            }
        }

        return NAMED_COLORS[value]
    }

    private fun parseLength(raw: String): Double? {
        val cleaned = raw.trim().lowercase()
            .removeSuffix("px")
            .removeSuffix("pt")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun findTagEnd(text: String, start: Int): Int {
        var quote: Char? = null
        var i = start
        while (i < text.length) {
            val ch = text[i]
            if (quote != null) {
                if (ch == quote) quote = null
            } else {
                if (ch == '"' || ch == '\'') quote = ch
                else if (ch == '>') return i
            }
            i++
        }
        return -1
    }

    private fun parseAttributes(attrText: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        val n = attrText.length
        while (i < n) {
            while (i < n && attrText[i].isWhitespace()) i++
            if (i >= n) break
            val nameStart = i
            while (i < n && attrText[i] != '=' && !attrText[i].isWhitespace()) i++
            val name = attrText.substring(nameStart, i).trim().lowercase()
            while (i < n && attrText[i].isWhitespace()) i++
            if (i >= n || attrText[i] != '=') continue
            i++
            while (i < n && attrText[i].isWhitespace()) i++
            if (i >= n) break
            val quote = attrText[i]
            val value = if (quote == '"' || quote == '\'') {
                i++
                val vStart = i
                while (i < n && attrText[i] != quote) i++
                val s = attrText.substring(vStart, i)
                if (i < n) i++
                s
            } else {
                val vStart = i
                while (i < n && !attrText[i].isWhitespace()) i++
                attrText.substring(vStart, i)
            }
            if (name.isNotEmpty()) out[name] = value
        }
        return out
    }

    private fun scanNumberEnd(text: String, start: Int): Int {
        var i = start
        val n = text.length
        if (i < n && (text[i] == '-' || text[i] == '+')) i++
        var seenDot = false
        var seenExp = false
        while (i < n) {
            val c = text[i]
            when {
                c.isDigit() -> i++
                c == '.' && !seenDot && !seenExp -> {
                    seenDot = true
                    i++
                }
                (c == 'e' || c == 'E') && !seenExp -> {
                    seenExp = true
                    i++
                    if (i < n && (text[i] == '+' || text[i] == '-')) i++
                }
                else -> break
            }
        }
        return i
    }

    private fun extractAllNumbers(input: String): List<Double> {
        val result = ArrayList<Double>()
        var i = 0
        val n = input.length
        while (i < n) {
            val ch = input[i]
            if (ch == '-' || ch == '+' || ch == '.' || ch.isDigit()) {
                val end = scanNumberEnd(input, i)
                if (end > i) {
                    input.substring(i, end).toDoubleOrNull()?.let { result.add(it) }
                    i = end
                } else {
                    i++
                }
            } else {
                i++
            }
        }
        return result
    }

    private fun tokenizePathData(d: String): List<String> {
        val tokens = ArrayList<String>()
        var i = 0
        val n = d.length
        while (i < n) {
            val c = d[i]
            when {
                c.isWhitespace() || c == ',' -> i++
                c.isLetter() -> {
                    tokens.add(c.toString())
                    i++
                }
                c == '-' || c == '+' || c == '.' || c.isDigit() -> {
                    val end = scanNumberEnd(d, i)
                    if (end > i) {
                        tokens.add(d.substring(i, end))
                        i = end
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return tokens
    }

    private data class SvgHeader(
        val naturalWidth: Int,
        val naturalHeight: Int,
        val viewBoxMinX: Double,
        val viewBoxMinY: Double,
        val viewBoxWidth: Double,
        val viewBoxHeight: Double,
        val preserveAspect: Boolean
    )

    private data class SvgColor(
        val r: Double,
        val g: Double,
        val b: Double,
        val a: Double
    )

    private data class RenderStyle(
        val fillColor: SvgColor?,
        val fillSet: Boolean,
        val strokeColor: SvgColor?,
        val strokeWidth: Double,
        val opacity: Double,
        val fillOpacity: Double,
        val strokeOpacity: Double,
        val evenOddFill: Boolean,
        val lineCap: CGLineCap,
        val lineJoin: CGLineJoin,
        val currentColor: SvgColor = SvgColor(0.0, 0.0, 0.0, 1.0),
        val displayNone: Boolean = false
    )

    private val NAMED_COLORS = mapOf(
        "black" to SvgColor(0.0, 0.0, 0.0, 1.0),
        "white" to SvgColor(1.0, 1.0, 1.0, 1.0),
        "red" to SvgColor(1.0, 0.0, 0.0, 1.0),
        "green" to SvgColor(0.0, 128.0 / 255.0, 0.0, 1.0),
        "lime" to SvgColor(0.0, 1.0, 0.0, 1.0),
        "blue" to SvgColor(0.0, 0.0, 1.0, 1.0),
        "yellow" to SvgColor(1.0, 1.0, 0.0, 1.0),
        "cyan" to SvgColor(0.0, 1.0, 1.0, 1.0),
        "magenta" to SvgColor(1.0, 0.0, 1.0, 1.0),
        "gray" to SvgColor(128.0 / 255.0, 128.0 / 255.0, 128.0 / 255.0, 1.0),
        "grey" to SvgColor(128.0 / 255.0, 128.0 / 255.0, 128.0 / 255.0, 1.0),
        "silver" to SvgColor(192.0 / 255.0, 192.0 / 255.0, 192.0 / 255.0, 1.0),
        "orange" to SvgColor(1.0, 165.0 / 255.0, 0.0, 1.0),
        "purple" to SvgColor(128.0 / 255.0, 0.0, 128.0 / 255.0, 1.0)
    )
}
