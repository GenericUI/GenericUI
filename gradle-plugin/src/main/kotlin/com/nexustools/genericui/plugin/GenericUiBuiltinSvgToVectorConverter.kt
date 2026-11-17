package com.nexustools.genericui.plugin

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.geom.AffineTransform
import java.io.ByteArrayInputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure-JVM DOM-based offline SVG to Android `VectorDrawable` XML converter.
 *
 * Supports `<path>`, `<rect>` (including `rx`/`ry`), `<circle>`, `<ellipse>`, `<line>`, `<polyline>`,
 * `<polygon>`, `<g>`, `<use>`, `<defs>`, `<clipPath>`, `<linearGradient>`, `<radialGradient>`, `<style>`,
 * inline `style="..."`, hierarchical presentation attribute inheritance (`fill`, `fill="none"`,
 * `fill-opacity`, `fill-rule`, `stroke`, `stroke="none"`, `stroke-width`, `stroke-linecap`,
 * `stroke-linejoin`, `stroke-opacity`, `opacity`), affine `transform` attributes, `currentColor`
 * substitution (`#FF000000`), and automatic `<= 108dp` size normalization.
 */
object GenericUiBuiltinSvgToVectorConverter {

    private const val MAX_VECTOR_DP = 108.0

    private val UNSUPPORTED_SVG_TAGS: Set<String> = setOf(
        "text", "tspan", "textpath", "altglyph", "tref",
        "image", "filter",
        "feblend", "fecolormatrix", "fecomponenttransfer", "fecomposite",
        "feconvolvezone", "feconvolvematrix", "fediffuselighting", "fedisplacementmap",
        "feflood", "fefunca", "fefuncb", "fefuncg", "fefuncr", "fegaussianblur",
        "feimage", "femerge", "femergenode", "femorphology", "feoffset",
        "fespecularlighting", "fetile", "feturbulence",
        "mask", "foreignobject",
        "animate", "animatecolor", "animatemotion", "animatetransform", "set",
        "pattern", "script", "marker"
    )

    private val COORD_FORMAT_SYMBOLS = DecimalFormatSymbols(Locale.US)

    private data class StyleState(
        val fill: String? = null,
        val fillOpacity: Float = 1f,
        val fillRule: String? = null,
        val stroke: String? = null,
        val strokeWidth: String? = null,
        val strokeLineCap: String? = null,
        val strokeLineJoin: String? = null,
        val strokeOpacity: Float = 1f,
        val opacity: Float = 1f,
        val displayNone: Boolean = false,
        val nonScalingStroke: Boolean = false
    )

    private data class CssRule(
        val selector: String,
        val properties: Map<String, String>
    )

    private data class GradientStopInfo(
        val offset: Double,
        val colorArgb: String
    )

    private data class GradientInfo(
        val id: String,
        val type: String, // "linear" or "radial"
        val element: Element,
        val attrs: Map<String, String>,
        val stops: List<GradientStopInfo>
    )

    private data class ClipPathInfo(
        val id: String,
        val paths: List<Pair<String, String?>> // (pathData, fillType)
    )

    private sealed class EmittedNode {
        data class PathNode(
            val pathData: String,
            val fillColor: String?,
            val fillAlpha: Float,
            val fillType: String?,
            val fillGradient: GradientInfo?,
            val strokeColor: String?,
            val strokeAlpha: Float,
            val strokeWidth: String?,
            val strokeLineCap: String?,
            val strokeLineJoin: String?,
            val strokeGradient: GradientInfo?,
            val bounds: DoubleArray
        ) : EmittedNode()

        data class ClipGroupNode(
            val clipPaths: List<Pair<String, String?>>,
            val children: List<EmittedNode>
        ) : EmittedNode()
    }

    private data class ParsedSvgContext(
        val viewMinX: Double,
        val viewMinY: Double,
        val viewportWidth: Double,
        val viewportHeight: Double,
        val dpWidth: Double,
        val dpHeight: Double,
        val cssRules: List<CssRule>,
        val elementsById: Map<String, Element>,
        val gradients: Map<String, GradientInfo>,
        val clipPaths: Map<String, ClipPathInfo>,
        val warnings: MutableList<String>,
        var usesGradient: Boolean = false
    )

    /**
     * Converts [svgText] into an Android `VectorDrawable` wrapped in [GenericUiSvgConversionResult].
     */
    fun convert(svgText: String, sourceName: String = "icon.svg"): GenericUiSvgConversionResult {
        val doc = parseSvgDocument(svgText)
            ?: return GenericUiSvgConversionResult(
                vectorXml = null,
                warnings = listOf("Failed to parse SVG XML in '$sourceName'."),
                usedOfficialTool = false,
                fellBackToRaster = true
            )

        val root = doc.documentElement
        val rootTag = (root.localName ?: root.tagName).lowercase(Locale.US)
        if (rootTag != "svg") {
            return GenericUiSvgConversionResult(
                vectorXml = null,
                warnings = listOf("Root element in '$sourceName' is <$rootTag>, expected <svg>."),
                usedOfficialTool = false,
                fellBackToRaster = true
            )
        }

        val warnings = collectUnsupportedElementWarnings(root)
        val context = buildContext(root, warnings)
        val rootTransform = AffineTransform()
        if (context.viewMinX != 0.0 || context.viewMinY != 0.0) {
            rootTransform.translate(-context.viewMinX, -context.viewMinY)
        }

        val rootCssProps = resolveElementCssProperties(root, context.cssRules)
        val rootStyle = applyStyleAttributes(StyleState(), root, rootCssProps)
        val emittedNodes = traverseChildren(
            parent = root,
            parentStyle = rootStyle,
            parentTransform = rootTransform,
            context = context,
            useStack = mutableSetOf()
        )

        if (warnings.isNotEmpty()) {
            return GenericUiSvgConversionResult(
                vectorXml = null,
                warnings = warnings.distinct(),
                usedOfficialTool = false,
                fellBackToRaster = true
            )
        }

        if (!hasAnyPath(emittedNodes)) {
            return GenericUiSvgConversionResult(
                vectorXml = null,
                warnings = listOf("No vector content found in '$sourceName'."),
                usedOfficialTool = false,
                fellBackToRaster = true
            )
        }

        val xml = serializeVectorDrawable(context, emittedNodes)
        return GenericUiSvgConversionResult(
            vectorXml = xml,
            warnings = emptyList(),
            usedOfficialTool = false,
            fellBackToRaster = false
        )
    }

    /**
     * Converts [svgText] directly to VectorDrawable XML string, returning `null` if conversion
     * fails or triggers unsupported-element warnings.
     */
    fun convertToXml(svgText: String): String? = convert(svgText).vectorXml

    /**
     * Extracts `(viewportWidth to viewportHeight) to pathDataList` from [svgText] via DOM parsing
     * for runtime registry metadata.
     */
    fun extractViewBoxAndPaths(svgText: String): Pair<Pair<Float, Float>, List<String>> {
        val doc = parseSvgDocument(svgText) ?: return (24f to 24f) to emptyList()
        val root = doc.documentElement ?: return (24f to 24f) to emptyList()
        val rootTag = (root.localName ?: root.tagName).lowercase(Locale.US)
        if (rootTag != "svg") return (24f to 24f) to emptyList()

        val warnings = mutableListOf<String>()
        val context = buildContext(root, warnings)
        val rootTransform = AffineTransform()
        if (context.viewMinX != 0.0 || context.viewMinY != 0.0) {
            rootTransform.translate(-context.viewMinX, -context.viewMinY)
        }
        val rootCss = resolveElementCssProperties(root, context.cssRules)
        val rootStyle = applyStyleAttributes(StyleState(), root, rootCss)
        val nodes = traverseChildren(root, rootStyle, rootTransform, context, mutableSetOf(), collectEvenIfInvisible = true)
        val paths = mutableListOf<String>()
        collectPathDataStrings(nodes, paths)
        val vbW = context.viewportWidth.toFloat().coerceAtLeast(1f)
        val vbH = context.viewportHeight.toFloat().coerceAtLeast(1f)
        return (vbW to vbH) to paths
    }

    private fun collectPathDataStrings(nodes: List<EmittedNode>, out: MutableList<String>) {
        for (node in nodes) {
            when (node) {
                is EmittedNode.PathNode -> if (node.pathData.isNotBlank()) out.add(node.pathData)
                is EmittedNode.ClipGroupNode -> collectPathDataStrings(node.children, out)
            }
        }
    }

    private fun hasAnyPath(nodes: List<EmittedNode>): Boolean = nodes.any { node ->
        when (node) {
            is EmittedNode.PathNode -> node.pathData.isNotBlank()
            is EmittedNode.ClipGroupNode -> hasAnyPath(node.children)
        }
    }

    internal fun createSecureDocumentBuilderFactory(namespaceAware: Boolean = true): DocumentBuilderFactory {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = namespaceAware
        dbf.isXIncludeAware = false
        dbf.isExpandEntityReferences = false
        runCatching { dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        return dbf
    }

    private fun parseSvgDocument(svgText: String): Document? = runCatching {
        val dbf = createSecureDocumentBuilderFactory(namespaceAware = true)
        dbf.newDocumentBuilder().parse(ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)))
    }.getOrNull()

    private fun collectUnsupportedElementWarnings(root: Element): MutableList<String> {
        val warnings = linkedSetOf<String>()
        fun walk(el: Element) {
            val tag = localTagName(el)
            if (tag in UNSUPPORTED_SVG_TAGS) {
                warnings.add("Unsupported SVG element <$tag>")
            }
            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                walk(child)
            }
        }
        walk(root)
        return warnings.toMutableList()
    }

    private fun buildContext(root: Element, warnings: MutableList<String>): ParsedSvgContext {
        val vbAttr = root.getAttribute("viewBox").trim()
        var minX = 0.0
        var minY = 0.0
        var vbW = -1.0
        var vbH = -1.0
        if (vbAttr.isNotEmpty()) {
            val nums = vbAttr.split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
            if (nums.size >= 4 && nums[2] > 0.0 && nums[3] > 0.0) {
                minX = nums[0]
                minY = nums[1]
                vbW = nums[2]
                vbH = nums[3]
            }
        }

        val rawW = parseSvgLength(root.getAttribute("width"), if (vbW > 0) vbW else 24.0)
        val rawH = parseSvgLength(root.getAttribute("height"), if (vbH > 0) vbH else 24.0)

        if (vbW <= 0.0 || vbH <= 0.0) {
            if (rawW != null && rawW > 0.0 && rawH != null && rawH > 0.0) {
                vbW = rawW
                vbH = rawH
            } else {
                vbW = 24.0
                vbH = 24.0
            }
        }

        var dpW = if (rawW != null && rawW > 0.0) rawW else vbW
        var dpH = if (rawH != null && rawH > 0.0) rawH else vbH
        val maxDp = max(dpW, dpH)
        if (maxDp > MAX_VECTOR_DP) {
            val scale = MAX_VECTOR_DP / maxDp
            dpW *= scale
            dpH *= scale
        }

        val cssRules = mutableListOf<CssRule>()
        val elementsById = linkedMapOf<String, Element>()
        val rawGradients = linkedMapOf<String, Element>()
        val rawClipPaths = linkedMapOf<String, Element>()

        fun indexTree(el: Element) {
            val id = el.getAttribute("id").trim()
            if (id.isNotEmpty()) {
                elementsById[id] = el
            }
            when (localTagName(el)) {
                "style" -> parseCssStyleElement(el.textContent.orEmpty(), cssRules)
                "lineargradient", "radialgradient" -> if (id.isNotEmpty()) rawGradients[id] = el
                "clippath" -> if (id.isNotEmpty()) rawClipPaths[id] = el
            }
            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                indexTree(child)
            }
        }
        indexTree(root)

        val gradients = resolveGradients(rawGradients)
        val placeholderCtx = ParsedSvgContext(
            viewMinX = minX,
            viewMinY = minY,
            viewportWidth = vbW,
            viewportHeight = vbH,
            dpWidth = dpW,
            dpHeight = dpH,
            cssRules = cssRules,
            elementsById = elementsById,
            gradients = gradients,
            clipPaths = emptyMap(),
            warnings = warnings
        )
        val clipPaths = resolveClipPaths(rawClipPaths, placeholderCtx)

        return placeholderCtx.copy(clipPaths = clipPaths)
    }

    private fun resolveGradients(rawGradients: Map<String, Element>): Map<String, GradientInfo> {
        val result = linkedMapOf<String, GradientInfo>()
        fun build(id: String, visiting: MutableSet<String>): GradientInfo? {
            result[id]?.let { return it }
            if (!visiting.add(id)) return null
            val el = rawGradients[id] ?: return null
            val hrefId = extractHrefId(el)
            val parentGrad = if (hrefId != null) build(hrefId, visiting) else null

            val attrs = linkedMapOf<String, String>()
            if (parentGrad != null) {
                attrs.putAll(parentGrad.attrs)
            }
            for (attrName in listOf("x1", "y1", "x2", "y2", "cx", "cy", "r", "spreadMethod", "gradientUnits", "gradientTransform")) {
                val v = el.getAttribute(attrName).trim()
                if (v.isNotEmpty()) attrs[attrName] = v
            }

            val stops = mutableListOf<GradientStopInfo>()
            var lastOffset = 0.0
            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                if (localTagName(child) != "stop") continue
                val inlineProps = parseInlineStyle(child.getAttribute("style"))
                val offsetRaw = (inlineProps["offset"] ?: child.getAttribute("offset")).trim()
                val offsetVal = if (offsetRaw.isNotEmpty()) {
                    val parsed = if (offsetRaw.endsWith("%")) {
                        (offsetRaw.removeSuffix("%").trim().toDoubleOrNull() ?: 0.0) / 100.0
                    } else {
                        offsetRaw.toDoubleOrNull() ?: 0.0
                    }
                    max(lastOffset, parsed.coerceIn(0.0, 1.0))
                } else {
                    lastOffset
                }
                lastOffset = offsetVal

                val stopColorRaw = (inlineProps["stop-color"] ?: child.getAttribute("stop-color")).trim().ifEmpty { "#000000" }
                val stopOpacityRaw = (inlineProps["stop-opacity"] ?: child.getAttribute("stop-opacity")).trim()
                val stopOpacity = parseOpacity(stopOpacityRaw, 1f)
                val hexColor = svgColorToAndroidHex(stopColorRaw, stopOpacity) ?: "#FF000000"
                stops.add(GradientStopInfo(offsetVal, normalizeToArgb8(hexColor)))
            }
            val finalStops = if (stops.isNotEmpty()) stops else parentGrad?.stops.orEmpty()
            val type = if (localTagName(el) == "radialgradient") "radial" else (parentGrad?.type ?: "linear")
            val info = GradientInfo(id = id, type = type, element = el, attrs = attrs, stops = finalStops)
            result[id] = info
            visiting.remove(id)
            return info
        }
        for (id in rawGradients.keys) {
            build(id, mutableSetOf())
        }
        return result
    }

    private fun resolveClipPaths(rawClipPaths: Map<String, Element>, ctx: ParsedSvgContext): Map<String, ClipPathInfo> {
        val result = linkedMapOf<String, ClipPathInfo>()
        val baseTransform = AffineTransform()
        if (ctx.viewMinX != 0.0 || ctx.viewMinY != 0.0) {
            baseTransform.translate(-ctx.viewMinX, -ctx.viewMinY)
        }
        for ((id, el) in rawClipPaths) {
            val clipTransform = AffineTransform(baseTransform)
            parseTransformAttribute(el.getAttribute("transform"))?.let { clipTransform.concatenate(it) }
            val paths = mutableListOf<Pair<String, String?>>()
            val nodes = traverseChildren(
                parent = el,
                parentStyle = StyleState(fill = "#000000"),
                parentTransform = clipTransform,
                context = ctx,
                useStack = mutableSetOf(),
                collectEvenIfInvisible = true
            )
            fun gather(list: List<EmittedNode>) {
                for (n in list) {
                    when (n) {
                        is EmittedNode.PathNode -> if (n.pathData.isNotBlank()) {
                            paths.add(n.pathData to n.fillType)
                        }
                        is EmittedNode.ClipGroupNode -> gather(n.children)
                    }
                }
            }
            gather(nodes)
            if (paths.isNotEmpty()) {
                result[id] = ClipPathInfo(id, paths)
            }
        }
        return result
    }

    private fun traverseChildren(
        parent: Element,
        parentStyle: StyleState,
        parentTransform: AffineTransform,
        context: ParsedSvgContext,
        useStack: MutableSet<String>,
        collectEvenIfInvisible: Boolean = false
    ): List<EmittedNode> {
        val emitted = mutableListOf<EmittedNode>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            val tag = localTagName(child)
            if (tag == "defs" || tag == "style" || tag == "clippath" ||
                tag == "lineargradient" || tag == "radialgradient" ||
                tag == "title" || tag == "desc" || tag == "metadata"
            ) {
                continue
            }

            val cssProps = resolveElementCssProperties(child, context.cssRules)
            val childStyle = applyStyleAttributes(parentStyle, child, cssProps)
            if (childStyle.displayNone) continue

            val combinedTransform = AffineTransform(parentTransform)
            parseTransformAttribute(child.getAttribute("transform"))?.let { combinedTransform.concatenate(it) }

            val clipAttr = (cssProps["clip-path"]
                ?: parseInlineStyle(child.getAttribute("style"))["clip-path"]
                ?: child.getAttribute("clip-path")).trim()
            val clipId = extractUrlId(clipAttr)
            val clipInfo = clipId?.let { context.clipPaths[it] }

            val nodesForElement: List<EmittedNode> = when (tag) {
                "g" -> traverseChildren(child, childStyle, combinedTransform, context, useStack, collectEvenIfInvisible)
                "use" -> {
                    val hrefId = extractHrefId(child)
                    val target = hrefId?.let { context.elementsById[it] }
                    if (hrefId != null && target != null && useStack.add(hrefId)) {
                        val x = parseSvgLength(child.getAttribute("x"), context.viewportWidth) ?: 0.0
                        val y = parseSvgLength(child.getAttribute("y"), context.viewportHeight) ?: 0.0
                        val useTransform = AffineTransform(combinedTransform)
                        if (x != 0.0 || y != 0.0) {
                            useTransform.translate(x, y)
                        }
                        val targetTag = localTagName(target)
                        val targetCss = resolveElementCssProperties(target, context.cssRules)
                        val targetStyle = applyStyleAttributes(childStyle, target, targetCss)
                        val res = if (targetTag == "g" || targetTag == "svg") {
                            traverseChildren(target, targetStyle, useTransform, context, useStack, collectEvenIfInvisible)
                        } else {
                            convertLeafElement(target, targetTag, targetStyle, useTransform, context, collectEvenIfInvisible)
                        }
                        useStack.remove(hrefId)
                        res
                    } else {
                        emptyList()
                    }
                }
                else -> convertLeafElement(child, tag, childStyle, combinedTransform, context, collectEvenIfInvisible)
            }

            if (nodesForElement.isNotEmpty()) {
                if (clipInfo != null) {
                    emitted.add(EmittedNode.ClipGroupNode(clipInfo.paths, nodesForElement))
                } else {
                    emitted.addAll(nodesForElement)
                }
            }
        }
        return emitted
    }

    private fun convertLeafElement(
        el: Element,
        tag: String,
        style: StyleState,
        transform: AffineTransform,
        context: ParsedSvgContext,
        collectEvenIfInvisible: Boolean
    ): List<EmittedNode> {
        val rawPathAndBounds: Pair<String, DoubleArray>? = when (tag) {
            "path" -> {
                val d = el.getAttribute("d").trim()
                if (d.isEmpty()) null else {
                    val transformed = transformSvgPathData(d, transform)
                    transformed to estimatePathBounds(transformed)
                }
            }
            "rect" -> buildRectPath(el, transform, context)
            "circle" -> buildCirclePath(el, transform, context)
            "ellipse" -> buildEllipsePath(el, transform, context)
            "line" -> buildLinePath(el, transform, context)
            "polygon" -> buildPolyPath(el, transform, close = true)
            "polyline" -> buildPolyPath(el, transform, close = false)
            else -> null
        }
        val (pathData, bounds) = rawPathAndBounds ?: return emptyList()
        if (pathData.isBlank()) return emptyList()

        val rawFill = style.fill?.trim()
        val isFillNone = rawFill != null && (rawFill.equals("none", ignoreCase = true) || rawFill.equals("transparent", ignoreCase = true))
        val fillUrlId = rawFill?.let { extractUrlId(it) }
        val fillGradient = fillUrlId?.let { context.gradients[it] }
        val resolvedFillColor = when {
            isFillNone -> null
            fillGradient != null -> {
                context.usesGradient = true
                null
            }
            rawFill.isNullOrEmpty() -> "#FF000000" // SVG default fill is black unless explicitly none
            else -> svgColorToAndroidHex(rawFill, 1f) ?: "#FF000000"
        }

        val rawStroke = style.stroke?.trim()
        val isStrokeNone = rawStroke.isNullOrEmpty() || rawStroke.equals("none", ignoreCase = true) || rawStroke.equals("transparent", ignoreCase = true)
        val strokeUrlId = rawStroke?.let { extractUrlId(it) }
        val strokeGradient = strokeUrlId?.let { context.gradients[it] }
        val resolvedStrokeColor = when {
            isStrokeNone -> null
            strokeGradient != null -> {
                context.usesGradient = true
                null
            }
            else -> svgColorToAndroidHex(rawStroke!!, 1f)
        }

        val effectiveFillAlpha = (style.opacity * style.fillOpacity).coerceIn(0f, 1f)
        val effectiveStrokeAlpha = (style.opacity * style.strokeOpacity).coerceIn(0f, 1f)

        var resolvedStrokeWidth: String? = null
        if (resolvedStrokeColor != null || strokeGradient != null) {
            val baseSw = parseSvgLength(style.strokeWidth, context.viewportWidth) ?: 1.0
            if (baseSw <= 0.0) {
                // Zero stroke-width draws nothing
                if (resolvedFillColor == null && fillGradient == null && !collectEvenIfInvisible) {
                    return emptyList()
                }
            } else {
                val scaledSw = if (!style.nonScalingStroke && (transform.type and AffineTransform.TYPE_MASK_SCALE) != 0) {
                    val det = abs(transform.determinant)
                    if (det > 1e-12) baseSw * sqrt(det) else baseSw
                } else {
                    baseSw
                }
                resolvedStrokeWidth = formatCoord(scaledSw)
            }
        }

        val hasVisibleFill = (resolvedFillColor != null || fillGradient != null) && effectiveFillAlpha > 0f
        val hasVisibleStroke = (resolvedStrokeColor != null || strokeGradient != null) &&
            effectiveStrokeAlpha > 0f && resolvedStrokeWidth != null

        if (!hasVisibleFill && !hasVisibleStroke && !collectEvenIfInvisible) {
            return emptyList()
        }

        val finalFillColor = if (hasVisibleFill) resolvedFillColor else null
        val finalFillGrad = if (hasVisibleFill) fillGradient else null
        val finalStrokeColor = if (hasVisibleStroke) resolvedStrokeColor else null
        val finalStrokeGrad = if (hasVisibleStroke) strokeGradient else null
        val finalStrokeWidth = if (hasVisibleStroke) resolvedStrokeWidth else null

        val fillType = when (style.fillRule?.lowercase(Locale.US)) {
            "evenodd" -> "evenOdd"
            "nonzero" -> "nonZero"
            else -> null
        }

        val cap = when (style.strokeLineCap?.lowercase(Locale.US)) {
            "round" -> "round"
            "square" -> "square"
            "butt" -> "butt"
            else -> null
        }

        val join = when (style.strokeLineJoin?.lowercase(Locale.US)) {
            "round" -> "round"
            "bevel" -> "bevel"
            "miter" -> "miter"
            else -> null
        }

        return listOf(
            EmittedNode.PathNode(
                pathData = pathData,
                fillColor = finalFillColor,
                fillAlpha = effectiveFillAlpha,
                fillType = fillType,
                fillGradient = finalFillGrad,
                strokeColor = finalStrokeColor,
                strokeAlpha = effectiveStrokeAlpha,
                strokeWidth = finalStrokeWidth,
                strokeLineCap = if (hasVisibleStroke) cap else null,
                strokeLineJoin = if (hasVisibleStroke) join else null,
                strokeGradient = finalStrokeGrad,
                bounds = bounds
            )
        )
    }

    private fun buildRectPath(el: Element, transform: AffineTransform, ctx: ParsedSvgContext): Pair<String, DoubleArray>? {
        val x = parseSvgLength(el.getAttribute("x"), ctx.viewportWidth) ?: 0.0
        val y = parseSvgLength(el.getAttribute("y"), ctx.viewportHeight) ?: 0.0
        val w = parseSvgLength(el.getAttribute("width"), ctx.viewportWidth) ?: return null
        val h = parseSvgLength(el.getAttribute("height"), ctx.viewportHeight) ?: return null
        if (w <= 0.0 || h <= 0.0) return null

        val rawRx = parseSvgLength(el.getAttribute("rx"), ctx.viewportWidth)
        val rawRy = parseSvgLength(el.getAttribute("ry"), ctx.viewportHeight)
        var rx = rawRx ?: rawRy ?: 0.0
        var ry = rawRy ?: rawRx ?: 0.0
        if (rx < 0.0) rx = 0.0
        if (ry < 0.0) ry = 0.0
        if (rx > w / 2.0) rx = w / 2.0
        if (ry > h / 2.0) ry = h / 2.0

        val d = if (rx <= 0.0 || ry <= 0.0) {
            "M${formatCoord(x)},${formatCoord(y)} L${formatCoord(x + w)},${formatCoord(y)} L${formatCoord(x + w)},${formatCoord(y + h)} L${formatCoord(x)},${formatCoord(y + h)} Z"
        } else {
            buildString {
                append("M").append(formatCoord(x + rx)).append(',').append(formatCoord(y))
                append(" L").append(formatCoord(x + w - rx)).append(',').append(formatCoord(y))
                append(" A").append(formatCoord(rx)).append(',').append(formatCoord(ry)).append(" 0 0,1 ")
                    .append(formatCoord(x + w)).append(',').append(formatCoord(y + ry))
                append(" L").append(formatCoord(x + w)).append(',').append(formatCoord(y + h - ry))
                append(" A").append(formatCoord(rx)).append(',').append(formatCoord(ry)).append(" 0 0,1 ")
                    .append(formatCoord(x + w - rx)).append(',').append(formatCoord(y + h))
                append(" L").append(formatCoord(x + rx)).append(',').append(formatCoord(y + h))
                append(" A").append(formatCoord(rx)).append(',').append(formatCoord(ry)).append(" 0 0,1 ")
                    .append(formatCoord(x)).append(',').append(formatCoord(y + h - ry))
                append(" L").append(formatCoord(x)).append(',').append(formatCoord(y + ry))
                append(" A").append(formatCoord(rx)).append(',').append(formatCoord(ry)).append(" 0 0,1 ")
                    .append(formatCoord(x + rx)).append(',').append(formatCoord(y))
                append(" Z")
            }
        }
        val finalD = transformSvgPathData(d, transform)
        return finalD to estimatePathBounds(finalD)
    }

    private fun buildCirclePath(el: Element, transform: AffineTransform, ctx: ParsedSvgContext): Pair<String, DoubleArray>? {
        val cx = parseSvgLength(el.getAttribute("cx"), ctx.viewportWidth) ?: 0.0
        val cy = parseSvgLength(el.getAttribute("cy"), ctx.viewportHeight) ?: 0.0
        val r = parseSvgLength(el.getAttribute("r"), min(ctx.viewportWidth, ctx.viewportHeight)) ?: return null
        if (r <= 0.0) return null
        val d = "M${formatCoord(cx - r)},${formatCoord(cy)} " +
            "A${formatCoord(r)},${formatCoord(r)} 0 1,1 ${formatCoord(cx + r)},${formatCoord(cy)} " +
            "A${formatCoord(r)},${formatCoord(r)} 0 1,1 ${formatCoord(cx - r)},${formatCoord(cy)} Z"
        val finalD = transformSvgPathData(d, transform)
        return finalD to estimatePathBounds(finalD)
    }

    private fun buildEllipsePath(el: Element, transform: AffineTransform, ctx: ParsedSvgContext): Pair<String, DoubleArray>? {
        val cx = parseSvgLength(el.getAttribute("cx"), ctx.viewportWidth) ?: 0.0
        val cy = parseSvgLength(el.getAttribute("cy"), ctx.viewportHeight) ?: 0.0
        val rx = parseSvgLength(el.getAttribute("rx"), ctx.viewportWidth) ?: return null
        val ry = parseSvgLength(el.getAttribute("ry"), ctx.viewportHeight) ?: return null
        if (rx <= 0.0 || ry <= 0.0) return null
        val d = "M${formatCoord(cx - rx)},${formatCoord(cy)} " +
            "A${formatCoord(rx)},${formatCoord(ry)} 0 1,1 ${formatCoord(cx + rx)},${formatCoord(cy)} " +
            "A${formatCoord(rx)},${formatCoord(ry)} 0 1,1 ${formatCoord(cx - rx)},${formatCoord(cy)} Z"
        val finalD = transformSvgPathData(d, transform)
        return finalD to estimatePathBounds(finalD)
    }

    private fun buildLinePath(el: Element, transform: AffineTransform, ctx: ParsedSvgContext): Pair<String, DoubleArray>? {
        val x1 = parseSvgLength(el.getAttribute("x1"), ctx.viewportWidth) ?: 0.0
        val y1 = parseSvgLength(el.getAttribute("y1"), ctx.viewportHeight) ?: 0.0
        val x2 = parseSvgLength(el.getAttribute("x2"), ctx.viewportWidth) ?: 0.0
        val y2 = parseSvgLength(el.getAttribute("y2"), ctx.viewportHeight) ?: 0.0
        val d = "M${formatCoord(x1)},${formatCoord(y1)} L${formatCoord(x2)},${formatCoord(y2)}"
        val finalD = transformSvgPathData(d, transform)
        return finalD to estimatePathBounds(finalD)
    }

    private fun buildPolyPath(el: Element, transform: AffineTransform, close: Boolean): Pair<String, DoubleArray>? {
        val ptsRaw = el.getAttribute("points").trim()
        if (ptsRaw.isEmpty()) return null
        val nums = ptsRaw.split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
        if (nums.size < 4) return null
        val d = buildString {
            append("M").append(formatCoord(nums[0])).append(',').append(formatCoord(nums[1]))
            var i = 2
            while (i + 1 < nums.size) {
                append(" L").append(formatCoord(nums[i])).append(',').append(formatCoord(nums[i + 1]))
                i += 2
            }
            if (close) append(" Z")
        }
        val finalD = transformSvgPathData(d, transform)
        return finalD to estimatePathBounds(finalD)
    }

    private fun serializeVectorDrawable(context: ParsedSvgContext, nodes: List<EmittedNode>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        append("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
        if (context.usesGradient) {
            appendLine()
            append("""    xmlns:aapt="http://schemas.android.com/aapt"""")
        }
        appendLine()
        appendLine("""    android:width="${formatCoord(context.dpWidth)}dp"""")
        appendLine("""    android:height="${formatCoord(context.dpHeight)}dp"""")
        appendLine("""    android:viewportWidth="${formatCoord(context.viewportWidth)}"""")
        appendLine("""    android:viewportHeight="${formatCoord(context.viewportHeight)}">""")

        fun writeNodes(list: List<EmittedNode>, indent: String) {
            for (node in list) {
                when (node) {
                    is EmittedNode.ClipGroupNode -> {
                        appendLine("$indent<group>")
                        for ((clipD, clipFillType) in node.clipPaths) {
                            appendLine("$indent    <clip-path")
                            if (clipFillType != null) {
                                appendLine("""$indent        android:fillType="$clipFillType"""")
                            }
                            appendLine("""$indent        android:pathData="${escapeXml(clipD)}" />""")
                        }
                        writeNodes(node.children, "$indent    ")
                        appendLine("$indent</group>")
                    }
                    is EmittedNode.PathNode -> {
                        val hasChildAttr = node.fillGradient != null || node.strokeGradient != null
                        appendLine("$indent<path")
                        if (node.fillColor != null) {
                            appendLine("""$indent    android:fillColor="${escapeXml(node.fillColor)}"""")
                        }
                        if (node.fillAlpha < 0.9999f && (node.fillColor != null || node.fillGradient != null)) {
                            appendLine("""$indent    android:fillAlpha="${formatCoord(node.fillAlpha.toDouble())}"""")
                        }
                        if (node.fillType != null) {
                            appendLine("""$indent    android:fillType="${node.fillType}"""")
                        }
                        if (node.strokeColor != null) {
                            appendLine("""$indent    android:strokeColor="${escapeXml(node.strokeColor)}"""")
                        }
                        if (node.strokeWidth != null) {
                            appendLine("""$indent    android:strokeWidth="${node.strokeWidth}"""")
                        }
                        if (node.strokeAlpha < 0.9999f && (node.strokeColor != null || node.strokeGradient != null)) {
                            appendLine("""$indent    android:strokeAlpha="${formatCoord(node.strokeAlpha.toDouble())}"""")
                        }
                        if (node.strokeLineCap != null) {
                            appendLine("""$indent    android:strokeLineCap="${node.strokeLineCap}"""")
                        }
                        if (node.strokeLineJoin != null) {
                            appendLine("""$indent    android:strokeLineJoin="${node.strokeLineJoin}"""")
                        }
                        if (hasChildAttr) {
                            appendLine("""$indent    android:pathData="${escapeXml(node.pathData)}">""")
                            node.fillGradient?.let {
                                writeGradientAttr(this, "$indent    ", "android:fillColor", it, node.bounds, context)
                            }
                            node.strokeGradient?.let {
                                writeGradientAttr(this, "$indent    ", "android:strokeColor", it, node.bounds, context)
                            }
                            appendLine("$indent</path>")
                        } else {
                            appendLine("""$indent    android:pathData="${escapeXml(node.pathData)}" />""")
                        }
                    }
                }
            }
        }
        writeNodes(nodes, "    ")
        appendLine("</vector>")
    }

    private fun writeGradientAttr(
        sb: StringBuilder,
        indent: String,
        attrName: String,
        grad: GradientInfo,
        bounds: DoubleArray,
        context: ParsedSvgContext
    ) {
        if (grad.stops.isEmpty()) return
        val isUserSpace = grad.attrs["gradientUnits"] == "userSpaceOnUse"
        val startX = if (isUserSpace) 0.0 else bounds[0]
        val startY = if (isUserSpace) 0.0 else bounds[1]
        val w = if (isUserSpace) context.viewportWidth else max(1e-6, bounds[2] - bounds[0])
        val h = if (isUserSpace) context.viewportHeight else max(1e-6, bounds[3] - bounds[1])

        val tileMode = when (grad.attrs["spreadMethod"]) {
            "reflect" -> "mirror"
            "repeat" -> "repeat"
            else -> "clamp"
        }

        sb.appendLine("""$indent<aapt:attr name="$attrName">""")
        sb.appendLine("$indent    <gradient")
        sb.appendLine("""$indent        android:type="${grad.type}"""")
        sb.appendLine("""$indent        android:tileMode="$tileMode"""")
        if (grad.type == "linear") {
            val x1 = resolveGradientCoord(grad.attrs["x1"], 0.0, isUserSpace, startX, w)
            val y1 = resolveGradientCoord(grad.attrs["y1"], 0.0, isUserSpace, startY, h)
            val x2 = resolveGradientCoord(grad.attrs["x2"], 1.0, isUserSpace, startX, w)
            val y2 = resolveGradientCoord(grad.attrs["y2"], 0.0, isUserSpace, startY, h)
            sb.appendLine("""$indent        android:startX="${formatCoord(x1)}"""")
            sb.appendLine("""$indent        android:startY="${formatCoord(y1)}"""")
            sb.appendLine("""$indent        android:endX="${formatCoord(x2)}"""")
            sb.appendLine("""$indent        android:endY="${formatCoord(y2)}"""")
        } else {
            val cx = resolveGradientCoord(grad.attrs["cx"], 0.5, isUserSpace, startX, w)
            val cy = resolveGradientCoord(grad.attrs["cy"], 0.5, isUserSpace, startY, h)
            val rRaw = grad.attrs["r"]
            val r = if (rRaw == null) {
                0.5 * max(w, h)
            } else if (rRaw.endsWith("%")) {
                (rRaw.removeSuffix("%").toDoubleOrNull() ?: 50.0) / 100.0 * max(w, h)
            } else {
                val parsed = rRaw.toDoubleOrNull() ?: 0.5
                if (!isUserSpace) parsed * max(w, h) else parsed
            }
            sb.appendLine("""$indent        android:centerX="${formatCoord(cx)}"""")
            sb.appendLine("""$indent        android:centerY="${formatCoord(cy)}"""")
            sb.appendLine("""$indent        android:gradientRadius="${formatCoord(r)}"""")
        }
        sb.appendLine("$indent        >")
        for (stop in grad.stops) {
            sb.appendLine("""$indent        <item android:offset="${formatCoord(stop.offset)}" android:color="${stop.colorArgb}" />""")
        }
        if (grad.stops.size == 1) {
            sb.appendLine("""$indent        <item android:offset="1" android:color="${grad.stops[0].colorArgb}" />""")
        }
        sb.appendLine("$indent    </gradient>")
        sb.appendLine("$indent</aapt:attr>")
    }

    private fun resolveGradientCoord(
        raw: String?,
        defaultFrac: Double,
        isUserSpace: Boolean,
        origin: Double,
        span: Double
    ): Double {
        if (raw.isNullOrBlank()) return origin + defaultFrac * span
        val trimmed = raw.trim()
        return if (trimmed.endsWith("%")) {
            val frac = (trimmed.removeSuffix("%").toDoubleOrNull() ?: (defaultFrac * 100.0)) / 100.0
            origin + frac * span
        } else {
            val valNum = trimmed.toDoubleOrNull() ?: defaultFrac
            if (!isUserSpace) origin + valNum * span else valNum
        }
    }

    // -------------------------------------------------------------------------
    // SVG Path Tokenizer, Arc-to-Cubic Converter, & Affine Path Transformer
    // -------------------------------------------------------------------------

    private fun tokenizeSvgPath(d: String): List<String> {
        val tokens = mutableListOf<String>()
        val n = d.length
        var i = 0
        var lastCmd = 'M'
        var argCountInCmd = 0

        while (i < n) {
            val c = d[i]
            if (c.isWhitespace() || c == ',') {
                i++
                continue
            }
            if (c.isLetter()) {
                tokens.add(c.toString())
                lastCmd = c
                argCountInCmd = 0
                i++
                continue
            }

            // Handle SVG tightly-packed A/a flags (largeArcFlag & sweepFlag are 0 or 1 without separators)
            if ((lastCmd == 'A' || lastCmd == 'a') && (argCountInCmd % 7 == 3 || argCountInCmd % 7 == 4)) {
                if (c == '0' || c == '1') {
                    tokens.add(c.toString())
                    argCountInCmd++
                    i++
                    continue
                }
            }

            val start = i
            if (c == '+' || c == '-') i++
            var hasDot = false
            while (i < n && (d[i].isDigit() || (d[i] == '.' && !hasDot))) {
                if (d[i] == '.') hasDot = true
                i++
            }
            if (i < n && (d[i] == 'e' || d[i] == 'E')) {
                val expPos = i
                i++
                if (i < n && (d[i] == '+' || d[i] == '-')) i++
                if (i < n && d[i].isDigit()) {
                    while (i < n && d[i].isDigit()) i++
                } else {
                    i = expPos
                }
            }
            if (i > start) {
                tokens.add(d.substring(start, i))
                argCountInCmd++
            } else {
                i++
            }
        }
        return tokens
    }

    private fun transformSvgPathData(d: String, transform: AffineTransform): String {
        val tokens = tokenizeSvgPath(d)
        if (tokens.isEmpty()) return ""

        if (transform.isIdentity) {
            return normalizePathTokens(tokens)
        }

        val sb = StringBuilder()
        var i = 0
        var cmd = 'M'
        var curX = 0.0
        var curY = 0.0
        var startX = 0.0
        var startY = 0.0
        var lastCtrlX = 0.0
        var lastCtrlY = 0.0
        var lastCmd = ' '
        val ptSrc = DoubleArray(6)
        val ptDst = DoubleArray(6)

        fun hasNum(): Boolean = i < tokens.size && !tokens[i][0].isLetter()
        fun nextNum(): Double = if (i < tokens.size) tokens[i++].toDoubleOrNull() ?: 0.0 else 0.0

        fun emitPt(x: Double, y: Double) {
            ptSrc[0] = x
            ptSrc[1] = y
            transform.transform(ptSrc, 0, ptDst, 0, 1)
            sb.append(formatCoord(ptDst[0])).append(',').append(formatCoord(ptDst[1]))
        }

        fun emitSpace() {
            if (sb.isNotEmpty() && sb[sb.length - 1] != ' ') sb.append(' ')
        }

        while (i < tokens.size) {
            val tok = tokens[i]
            if (tok.length == 1 && tok[0].isLetter()) {
                cmd = tok[0]
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    emitSpace()
                    sb.append('Z')
                    curX = startX
                    curY = startY
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = cmd
                    continue
                }
            }
            when (cmd) {
                'M', 'm' -> if (hasNum()) {
                    val isRel = cmd == 'm'
                    val nx = if (isRel) curX + nextNum() else nextNum()
                    val ny = if (isRel) curY + nextNum() else nextNum()
                    curX = nx
                    curY = ny
                    startX = curX
                    startY = curY
                    emitSpace()
                    sb.append('M')
                    emitPt(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = cmd
                    cmd = if (isRel) 'l' else 'L'
                } else i++
                'L', 'l' -> if (hasNum()) {
                    val isRel = cmd == 'l'
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    emitSpace()
                    sb.append('L')
                    emitPt(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = cmd
                } else i++
                'H', 'h' -> if (hasNum()) {
                    val isRel = cmd == 'h'
                    curX = if (isRel) curX + nextNum() else nextNum()
                    emitSpace()
                    sb.append('L')
                    emitPt(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = cmd
                } else i++
                'V', 'v' -> if (hasNum()) {
                    val isRel = cmd == 'v'
                    curY = if (isRel) curY + nextNum() else nextNum()
                    emitSpace()
                    sb.append('L')
                    emitPt(curX, curY)
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = cmd
                } else i++
                'Q', 'q' -> if (hasNum()) {
                    val isRel = cmd == 'q'
                    val x1 = if (isRel) curX + nextNum() else nextNum()
                    val y1 = if (isRel) curY + nextNum() else nextNum()
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    ptSrc[0] = x1; ptSrc[1] = y1
                    ptSrc[2] = curX; ptSrc[3] = curY
                    transform.transform(ptSrc, 0, ptDst, 0, 2)
                    emitSpace()
                    sb.append('Q')
                        .append(formatCoord(ptDst[0])).append(',').append(formatCoord(ptDst[1])).append(' ')
                        .append(formatCoord(ptDst[2])).append(',').append(formatCoord(ptDst[3]))
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmd = cmd
                } else i++
                'T', 't' -> if (hasNum()) {
                    val isRel = cmd == 't'
                    val x1 = if (lastCmd in "QqTt") 2.0 * curX - lastCtrlX else curX
                    val y1 = if (lastCmd in "QqTt") 2.0 * curY - lastCtrlY else curY
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    ptSrc[0] = x1; ptSrc[1] = y1
                    ptSrc[2] = curX; ptSrc[3] = curY
                    transform.transform(ptSrc, 0, ptDst, 0, 2)
                    emitSpace()
                    sb.append('Q')
                        .append(formatCoord(ptDst[0])).append(',').append(formatCoord(ptDst[1])).append(' ')
                        .append(formatCoord(ptDst[2])).append(',').append(formatCoord(ptDst[3]))
                    lastCtrlX = x1
                    lastCtrlY = y1
                    lastCmd = cmd
                } else i++
                'C', 'c' -> if (hasNum()) {
                    val isRel = cmd == 'c'
                    val x1 = if (isRel) curX + nextNum() else nextNum()
                    val y1 = if (isRel) curY + nextNum() else nextNum()
                    val x2 = if (isRel) curX + nextNum() else nextNum()
                    val y2 = if (isRel) curY + nextNum() else nextNum()
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    ptSrc[0] = x1; ptSrc[1] = y1
                    ptSrc[2] = x2; ptSrc[3] = y2
                    ptSrc[4] = curX; ptSrc[5] = curY
                    transform.transform(ptSrc, 0, ptDst, 0, 3)
                    emitSpace()
                    sb.append('C')
                        .append(formatCoord(ptDst[0])).append(',').append(formatCoord(ptDst[1])).append(' ')
                        .append(formatCoord(ptDst[2])).append(',').append(formatCoord(ptDst[3])).append(' ')
                        .append(formatCoord(ptDst[4])).append(',').append(formatCoord(ptDst[5]))
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmd = cmd
                } else i++
                'S', 's' -> if (hasNum()) {
                    val isRel = cmd == 's'
                    val x1 = if (lastCmd in "CcSs") 2.0 * curX - lastCtrlX else curX
                    val y1 = if (lastCmd in "CcSs") 2.0 * curY - lastCtrlY else curY
                    val x2 = if (isRel) curX + nextNum() else nextNum()
                    val y2 = if (isRel) curY + nextNum() else nextNum()
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    ptSrc[0] = x1; ptSrc[1] = y1
                    ptSrc[2] = x2; ptSrc[3] = y2
                    ptSrc[4] = curX; ptSrc[5] = curY
                    transform.transform(ptSrc, 0, ptDst, 0, 3)
                    emitSpace()
                    sb.append('C')
                        .append(formatCoord(ptDst[0])).append(',').append(formatCoord(ptDst[1])).append(' ')
                        .append(formatCoord(ptDst[2])).append(',').append(formatCoord(ptDst[3])).append(' ')
                        .append(formatCoord(ptDst[4])).append(',').append(formatCoord(ptDst[5]))
                    lastCtrlX = x2
                    lastCtrlY = y2
                    lastCmd = cmd
                } else i++
                'A', 'a' -> if (hasNum()) {
                    val isRel = cmd == 'a'
                    val rx = nextNum()
                    val ry = nextNum()
                    val rot = nextNum()
                    val largeArc = nextNum() != 0.0
                    val sweep = nextNum() != 0.0
                    val nx = if (isRel) curX + nextNum() else nextNum()
                    val ny = if (isRel) curY + nextNum() else nextNum()
                    if (transform.type == AffineTransform.TYPE_TRANSLATION) {
                        ptSrc[0] = nx
                        ptSrc[1] = ny
                        transform.transform(ptSrc, 0, ptDst, 0, 1)
                        emitSpace()
                        sb.append('A')
                            .append(formatCoord(abs(rx))).append(',').append(formatCoord(abs(ry))).append(' ')
                            .append(formatCoord(rot)).append(' ')
                            .append(if (largeArc) '1' else '0').append(',')
                            .append(if (sweep) '1' else '0').append(' ')
                            .append(formatCoord(ptDst[0])).append(',').append(formatCoord(ptDst[1]))
                    } else {
                        val cubics = svgArcToCubicSegments(curX, curY, rx, ry, rot, largeArc, sweep, nx, ny)
                        for (seg in cubics) {
                            transform.transform(seg, 0, ptDst, 0, 3)
                            emitSpace()
                            sb.append('C')
                                .append(formatCoord(ptDst[0])).append(',').append(formatCoord(ptDst[1])).append(' ')
                                .append(formatCoord(ptDst[2])).append(',').append(formatCoord(ptDst[3])).append(' ')
                                .append(formatCoord(ptDst[4])).append(',').append(formatCoord(ptDst[5]))
                        }
                    }
                    curX = nx
                    curY = ny
                    lastCtrlX = curX
                    lastCtrlY = curY
                    lastCmd = cmd
                } else i++
                else -> i++
            }
        }
        return sb.toString().trim()
    }

    private fun normalizePathTokens(tokens: List<String>): String = buildString {
        var prevWasNum = false
        for (tok in tokens) {
            val isCmd = tok.length == 1 && tok[0].isLetter()
            if (isCmd) {
                if (isNotEmpty()) append(' ')
                append(tok)
                prevWasNum = false
            } else {
                if (prevWasNum) append(',')
                append(tok)
                prevWasNum = true
            }
        }
    }.trim()

    private fun svgArcToCubicSegments(
        x1: Double,
        y1: Double,
        rxIn: Double,
        ryIn: Double,
        xAxisRotationDeg: Double,
        largeArcFlag: Boolean,
        sweepFlag: Boolean,
        x2: Double,
        y2: Double
    ): List<DoubleArray> {
        if (x1 == x2 && y1 == y2) return emptyList()
        if (rxIn == 0.0 || ryIn == 0.0) {
            return listOf(doubleArrayOf(x1, y1, x2, y2, x2, y2))
        }
        var rx = abs(rxIn)
        var ry = abs(ryIn)
        val phi = Math.toRadians(xAxisRotationDeg % 360.0)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (x1 - x2) / 2.0
        val dy2 = (y1 - y2) / 2.0
        val x1Prime = cosPhi * dx2 + sinPhi * dy2
        val y1Prime = -sinPhi * dx2 + cosPhi * dy2

        var rxSq = rx * rx
        var rySq = ry * ry
        val x1PrimeSq = x1Prime * x1Prime
        val y1PrimeSq = y1Prime * y1Prime
        val radiiCheck = x1PrimeSq / rxSq + y1PrimeSq / rySq
        if (radiiCheck > 1.0) {
            val scale = sqrt(radiiCheck)
            rx *= scale
            ry *= scale
            rxSq = rx * rx
            rySq = ry * ry
        }

        val sign = if (largeArcFlag == sweepFlag) -1.0 else 1.0
        val num = max(0.0, rxSq * rySq - rxSq * y1PrimeSq - rySq * x1PrimeSq)
        val den = max(1e-12, rxSq * y1PrimeSq + rySq * x1PrimeSq)
        val coef = sign * sqrt(num / den)
        val cxPrime = coef * ((rx * y1Prime) / ry)
        val cyPrime = coef * -((ry * x1Prime) / rx)

        val cx = cosPhi * cxPrime - sinPhi * cyPrime + (x1 + x2) / 2.0
        val cy = sinPhi * cxPrime + cosPhi * cyPrime + (y1 + y2) / 2.0

        fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = max(1e-12, hypot(ux, uy) * hypot(vx, vy))
            val angle = acos((dot / len).coerceIn(-1.0, 1.0))
            return if (ux * vy - uy * vx < 0.0) -angle else angle
        }

        val ux = (x1Prime - cxPrime) / rx
        val uy = (y1Prime - cyPrime) / ry
        val vx = (-x1Prime - cxPrime) / rx
        val vy = (-y1Prime - cyPrime) / ry
        val theta1 = vectorAngle(1.0, 0.0, ux, uy)
        var dTheta = vectorAngle(ux, uy, vx, vy)
        if (!sweepFlag && dTheta > 0.0) {
            dTheta -= 2.0 * PI
        } else if (sweepFlag && dTheta < 0.0) {
            dTheta += 2.0 * PI
        }

        val segments = ceil(abs(dTheta) / (PI / 2.0)).toInt().coerceAtLeast(1)
        val delta = dTheta / segments
        val alpha = (4.0 / 3.0) * tan(delta / 4.0)
        val out = ArrayList<DoubleArray>(segments)
        for (seg in 0 until segments) {
            val t1 = theta1 + seg * delta
            val t2 = t1 + delta
            val cosT1 = cos(t1)
            val sinT1 = sin(t1)
            val cosT2 = cos(t2)
            val sinT2 = sin(t2)

            val e1x = cosT1 - alpha * sinT1
            val e1y = sinT1 + alpha * cosT1
            val e2x = cosT2 + alpha * sinT2
            val e2y = sinT2 - alpha * cosT2

            out.add(
                doubleArrayOf(
                    cx + rx * cosPhi * e1x - ry * sinPhi * e1y,
                    cy + rx * sinPhi * e1x + ry * cosPhi * e1y,
                    cx + rx * cosPhi * e2x - ry * sinPhi * e2y,
                    cy + rx * sinPhi * e2x + ry * cosPhi * e2y,
                    cx + rx * cosPhi * cosT2 - ry * sinPhi * sinT2,
                    cy + rx * sinPhi * cosT2 + ry * cosPhi * sinT2
                )
            )
        }
        return out
    }

    private fun estimatePathBounds(d: String): DoubleArray {
        val tokens = tokenizeSvgPath(d)
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun include(x: Double, y: Double) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        var i = 0
        var cmd = 'M'
        var curX = 0.0
        var curY = 0.0
        var startX = 0.0
        var startY = 0.0
        fun hasNum(): Boolean = i < tokens.size && !tokens[i][0].isLetter()
        fun nextNum(): Double = if (i < tokens.size) tokens[i++].toDoubleOrNull() ?: 0.0 else 0.0

        while (i < tokens.size) {
            val tok = tokens[i]
            if (tok.length == 1 && tok[0].isLetter()) {
                cmd = tok[0]
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    curX = startX
                    curY = startY
                }
                continue
            }
            when (cmd) {
                'M', 'm' -> if (hasNum()) {
                    val isRel = cmd == 'm'
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    startX = curX; startY = curY
                    include(curX, curY)
                    cmd = if (isRel) 'l' else 'L'
                } else i++
                'L', 'l' -> if (hasNum()) {
                    val isRel = cmd == 'l'
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    include(curX, curY)
                } else i++
                'H', 'h' -> if (hasNum()) {
                    curX = if (cmd == 'h') curX + nextNum() else nextNum()
                    include(curX, curY)
                } else i++
                'V', 'v' -> if (hasNum()) {
                    curY = if (cmd == 'v') curY + nextNum() else nextNum()
                    include(curX, curY)
                } else i++
                'Q', 'q' -> if (hasNum()) {
                    val isRel = cmd == 'q'
                    val x1 = if (isRel) curX + nextNum() else nextNum()
                    val y1 = if (isRel) curY + nextNum() else nextNum()
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    include(x1, y1); include(curX, curY)
                } else i++
                'T', 't' -> if (hasNum()) {
                    val isRel = cmd == 't'
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    include(curX, curY)
                } else i++
                'C', 'c' -> if (hasNum()) {
                    val isRel = cmd == 'c'
                    val x1 = if (isRel) curX + nextNum() else nextNum()
                    val y1 = if (isRel) curY + nextNum() else nextNum()
                    val x2 = if (isRel) curX + nextNum() else nextNum()
                    val y2 = if (isRel) curY + nextNum() else nextNum()
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    include(x1, y1); include(x2, y2); include(curX, curY)
                } else i++
                'S', 's' -> if (hasNum()) {
                    val isRel = cmd == 's'
                    val x2 = if (isRel) curX + nextNum() else nextNum()
                    val y2 = if (isRel) curY + nextNum() else nextNum()
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    include(x2, y2); include(curX, curY)
                } else i++
                'A', 'a' -> if (hasNum()) {
                    val isRel = cmd == 'a'
                    nextNum(); nextNum(); nextNum(); nextNum(); nextNum()
                    curX = if (isRel) curX + nextNum() else nextNum()
                    curY = if (isRel) curY + nextNum() else nextNum()
                    include(curX, curY)
                } else i++
                else -> i++
            }
        }
        return if (minX.isFinite() && minY.isFinite() && maxX.isFinite() && maxY.isFinite()) {
            doubleArrayOf(minX, minY, maxX, maxY)
        } else {
            doubleArrayOf(0.0, 0.0, 24.0, 24.0)
        }
    }

    // -------------------------------------------------------------------------
    // Transform, CSS, & Color Helpers
    // -------------------------------------------------------------------------

    internal fun parseTransformAttribute(raw: String?): AffineTransform? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val matchRegex = Regex("([A-Za-z]+)\\s*\\(([^)]*)\\)")
        var result: AffineTransform? = null
        for (m in matchRegex.findAll(text)) {
            val op = m.groupValues[1].trim().lowercase(Locale.US)
            val nums = m.groupValues[2].trim().split(Regex("[\\s,]+")).mapNotNull { it.toDoubleOrNull() }
            val step = when (op) {
                "matrix" -> if (nums.size >= 6) {
                    AffineTransform(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5])
                } else null
                "translate" -> if (nums.isNotEmpty()) {
                    AffineTransform.getTranslateInstance(nums[0], nums.getOrElse(1) { 0.0 })
                } else null
                "scale" -> if (nums.isNotEmpty()) {
                    AffineTransform.getScaleInstance(nums[0], nums.getOrElse(1) { nums[0] })
                } else null
                "rotate" -> if (nums.isNotEmpty()) {
                    val rad = Math.toRadians(nums[0])
                    if (nums.size >= 3) {
                        AffineTransform.getRotateInstance(rad, nums[1], nums[2])
                    } else {
                        AffineTransform.getRotateInstance(rad)
                    }
                } else null
                "skewx" -> if (nums.isNotEmpty()) {
                    AffineTransform.getShearInstance(tan(Math.toRadians(nums[0])), 0.0)
                } else null
                "skewy" -> if (nums.isNotEmpty()) {
                    AffineTransform.getShearInstance(0.0, tan(Math.toRadians(nums[0])))
                } else null
                else -> null
            } ?: continue
            if (result == null) {
                result = AffineTransform(step)
            } else {
                result.concatenate(step)
            }
        }
        return result
    }

    private fun parseCssStyleElement(cssText: String, out: MutableList<CssRule>) {
        val stripped = cssText.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        val ruleRegex = Regex("([^{}]+)\\{([^}]*)\\}")
        for (m in ruleRegex.findAll(stripped)) {
            val selectors = m.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val props = parseInlineStyle(m.groupValues[2])
            if (props.isEmpty()) continue
            for (sel in selectors) {
                out.add(CssRule(sel, props))
            }
        }
    }

    private fun resolveElementCssProperties(el: Element, rules: List<CssRule>): Map<String, String> {
        if (rules.isEmpty()) return emptyMap()
        val tag = localTagName(el)
        val id = el.getAttribute("id").trim()
        val classes = el.getAttribute("class").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        val merged = linkedMapOf<String, String>()
        for (rule in rules) {
            val sel = rule.selector
            val matches = when {
                sel == "*" -> true
                sel.startsWith("#") -> id.isNotEmpty() && sel.substring(1) == id
                sel.startsWith(".") -> sel.substring(1) in classes
                sel.contains('.') -> {
                    val selTag = sel.substringBefore('.').lowercase(Locale.US)
                    val selCls = sel.substringAfter('.')
                    selTag == tag && selCls in classes
                }
                else -> sel.lowercase(Locale.US) == tag
            }
            if (matches) {
                merged.putAll(rule.properties)
            }
        }
        return merged
    }

    internal fun parseInlineStyle(raw: String?): Map<String, String> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyMap()
        val map = linkedMapOf<String, String>()
        for (part in text.split(';')) {
            val colon = part.indexOf(':')
            if (colon <= 0) continue
            val key = part.substring(0, colon).trim().lowercase(Locale.US)
            val value = part.substring(colon + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun applyStyleAttributes(
        parent: StyleState,
        el: Element,
        cssProps: Map<String, String>
    ): StyleState {
        val inline = parseInlineStyle(el.getAttribute("style"))

        fun attrOrCss(name: String): String? =
            inline[name]?.takeIf { it.isNotEmpty() }
                ?: cssProps[name]?.takeIf { it.isNotEmpty() }
                ?: el.getAttribute(name).trim().takeIf { it.isNotEmpty() }

        val displayVal = attrOrCss("display")?.lowercase(Locale.US)
        val visVal = attrOrCss("visibility")?.lowercase(Locale.US)
        val displayNone = parent.displayNone || displayVal == "none" || visVal == "hidden" || visVal == "collapse"

        val localOpacity = attrOrCss("opacity")?.let { parseOpacity(it, 1f) } ?: 1f
        val localFillOpacity = attrOrCss("fill-opacity")?.let { parseOpacity(it, parent.fillOpacity) } ?: parent.fillOpacity
        val localStrokeOpacity = attrOrCss("stroke-opacity")?.let { parseOpacity(it, parent.strokeOpacity) } ?: parent.strokeOpacity

        val vectorEffect = attrOrCss("vector-effect")?.lowercase(Locale.US)

        return StyleState(
            fill = attrOrCss("fill") ?: parent.fill,
            fillOpacity = localFillOpacity,
            fillRule = attrOrCss("fill-rule") ?: parent.fillRule,
            stroke = attrOrCss("stroke") ?: parent.stroke,
            strokeWidth = attrOrCss("stroke-width") ?: parent.strokeWidth,
            strokeLineCap = attrOrCss("stroke-linecap") ?: parent.strokeLineCap,
            strokeLineJoin = attrOrCss("stroke-linejoin") ?: parent.strokeLineJoin,
            strokeOpacity = localStrokeOpacity,
            opacity = (parent.opacity * localOpacity).coerceIn(0f, 1f),
            displayNone = displayNone,
            nonScalingStroke = vectorEffect == "non-scaling-stroke"
        )
    }

    internal fun svgColorToAndroidHex(rawColor: String?, alphaMultiplier: Float = 1f): String? {
        val color = rawColor?.trim().orEmpty()
        if (color.isEmpty()) return null
        if (color.equals("none", ignoreCase = true) || color.equals("transparent", ignoreCase = true)) {
            return null
        }
        if (color.equals("currentColor", ignoreCase = true)) {
            return applyAlphaToHex("#FF000000", alphaMultiplier)
        }
        if (color.startsWith("#")) {
            val clean = color.substring(1)
            val argb = when (clean.length) {
                3 -> {
                    val r = "${clean[0]}${clean[0]}"
                    val g = "${clean[1]}${clean[1]}"
                    val b = "${clean[2]}${clean[2]}"
                    "#FF$r$g$b".uppercase(Locale.US)
                }
                4 -> {
                    // CSS4 #RGBA -> #AARRGGBB
                    val r = "${clean[0]}${clean[0]}"
                    val g = "${clean[1]}${clean[1]}"
                    val b = "${clean[2]}${clean[2]}"
                    val a = "${clean[3]}${clean[3]}"
                    "#$a$r$g$b".uppercase(Locale.US)
                }
                6 -> "#FF${clean.uppercase(Locale.US)}"
                8 -> {
                    // CSS4 #RRGGBBAA -> #AARRGGBB
                    val rrggbb = clean.substring(0, 6)
                    val aa = clean.substring(6, 8)
                    "#$aa$rrggbb".uppercase(Locale.US)
                }
                else -> return null
            }
            return applyAlphaToHex(argb, alphaMultiplier)
        }

        val lower = color.lowercase(Locale.US)
        if (lower.startsWith("rgb(") && lower.endsWith(")")) {
            val parts = lower.substring(4, lower.length - 1).split(',')
            if (parts.size != 3) return null
            val r = parseRgbComponent(parts[0]) ?: return null
            val g = parseRgbComponent(parts[1]) ?: return null
            val b = parseRgbComponent(parts[2]) ?: return null
            val a = (alphaMultiplier.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
            return String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
        }
        if (lower.startsWith("rgba(") && lower.endsWith(")")) {
            val parts = lower.substring(5, lower.length - 1).split(',')
            if (parts.size != 4) return null
            val r = parseRgbComponent(parts[0]) ?: return null
            val g = parseRgbComponent(parts[1]) ?: return null
            val b = parseRgbComponent(parts[2]) ?: return null
            val rawA = parts[3].trim()
            val alphaFloat = if (rawA.endsWith("%")) {
                (rawA.removeSuffix("%").toFloatOrNull() ?: 100f) / 100f
            } else {
                val v = rawA.toFloatOrNull() ?: 1f
                if (v > 1f) v / 255f else v
            }
            val a = (alphaFloat.coerceIn(0f, 1f) * alphaMultiplier.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
            return String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
        }

        val named = NAMED_SVG_COLORS[lower] ?: return null
        return applyAlphaToHex("#FF${named.removePrefix("#")}", alphaMultiplier)
    }

    private fun applyAlphaToHex(argbHex: String, alphaMultiplier: Float): String {
        if (alphaMultiplier >= 0.9999f) return argbHex
        val clean = argbHex.removePrefix("#")
        if (clean.length != 8) return argbHex
        val baseA = clean.substring(0, 2).toIntOrNull(16) ?: 255
        val newA = (baseA * alphaMultiplier.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return String.format(Locale.US, "#%02X%s", newA, clean.substring(2))
    }

    private fun normalizeToArgb8(hex: String): String {
        val clean = hex.removePrefix("#").uppercase(Locale.US)
        return when (clean.length) {
            6 -> "#FF$clean"
            8 -> "#$clean"
            else -> "#FF000000"
        }
    }

    private fun parseRgbComponent(part: String): Int? {
        val t = part.trim()
        return if (t.endsWith("%")) {
            val pct = t.removeSuffix("%").trim().toFloatOrNull() ?: return null
            (pct / 100f * 255f).roundToInt().coerceIn(0, 255)
        } else {
            t.toFloatOrNull()?.roundToInt()?.coerceIn(0, 255)
        }
    }

    private fun parseOpacity(raw: String?, defaultVal: Float): Float {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return defaultVal
        val v = if (clean.endsWith("%")) {
            (clean.removeSuffix("%").trim().toFloatOrNull() ?: (defaultVal * 100f)) / 100f
        } else {
            clean.toFloatOrNull() ?: defaultVal
        }
        return v.coerceIn(0f, 1f)
    }

    private fun parseSvgLength(raw: String?, percentageBase: Double): Double? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.endsWith("%")) {
            val pct = text.removeSuffix("%").trim().toDoubleOrNull() ?: return null
            return pct / 100.0 * percentageBase
        }
        val lower = text.lowercase(Locale.US)
        return when {
            lower.endsWith("px") || lower.endsWith("dp") -> lower.dropLast(2).trim().toDoubleOrNull()
            lower.endsWith("pt") -> lower.dropLast(2).trim().toDoubleOrNull()?.let { it * 96.0 / 72.0 }
            lower.endsWith("pc") -> lower.dropLast(2).trim().toDoubleOrNull()?.let { it * 16.0 }
            lower.endsWith("in") -> lower.dropLast(2).trim().toDoubleOrNull()?.let { it * 96.0 }
            lower.endsWith("cm") -> lower.dropLast(2).trim().toDoubleOrNull()?.let { it * 96.0 / 2.54 }
            lower.endsWith("mm") -> lower.dropLast(2).trim().toDoubleOrNull()?.let { it * 96.0 / 25.4 }
            lower.endsWith("em") || lower.endsWith("ex") -> lower.dropLast(2).trim().toDoubleOrNull()?.let { it * 16.0 }
            else -> text.toDoubleOrNull()
        }
    }

    private fun extractHrefId(el: Element): String? {
        val href = el.getAttribute("href").ifBlank {
            el.getAttributeNS("http://www.w3.org/1999/xlink", "href").ifBlank {
                el.getAttribute("xlink:href")
            }
        }.trim()
        return if (href.startsWith("#") && href.length > 1) href.substring(1).trim() else null
    }

    private fun extractUrlId(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (!text.startsWith("url(") || !text.endsWith(")")) return null
        val hashIdx = text.indexOf('#')
        if (hashIdx < 0) return null
        return text.substring(hashIdx + 1, text.length - 1).trim().trim('\'', '"').takeIf { it.isNotEmpty() }
    }

    private fun localTagName(el: Element): String =
        (el.localName ?: el.tagName).lowercase(Locale.US)

    private fun formatCoord(value: Double): String {
        val clean = if (abs(value) < 1e-6) 0.0 else value
        val df = DecimalFormat("0.####", COORD_FORMAT_SYMBOLS)
        return df.format(clean)
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private val NAMED_SVG_COLORS: Map<String, String> = mapOf(
        "aliceblue" to "#F0F8FF", "antiquewhite" to "#FAEBD7", "aqua" to "#00FFFF",
        "aquamarine" to "#7FFFD4", "azure" to "#F0FFFF", "beige" to "#F5F5DC",
        "bisque" to "#FFE4C4", "black" to "#000000", "blanchedalmond" to "#FFEBCD",
        "blue" to "#0000FF", "blueviolet" to "#8A2BE2", "brown" to "#A52A2A",
        "burlywood" to "#DEB887", "cadetblue" to "#5F9EA0", "chartreuse" to "#7FFF00",
        "chocolate" to "#D2691E", "coral" to "#FF7F50", "cornflowerblue" to "#6495ED",
        "cornsilk" to "#FFF8DC", "crimson" to "#DC143C", "cyan" to "#00FFFF",
        "darkblue" to "#00008B", "darkcyan" to "#008B8B", "darkgoldenrod" to "#B8860B",
        "darkgray" to "#A9A9A9", "darkgrey" to "#A9A9A9", "darkgreen" to "#006400",
        "darkkhaki" to "#BDB76B", "darkmagenta" to "#8B008B", "darkolivegreen" to "#556B2F",
        "darkorange" to "#FF8C00", "darkorchid" to "#9932CC", "darkred" to "#8B0000",
        "darksalmon" to "#E9967A", "darkseagreen" to "#8FBC8F", "darkslateblue" to "#483D8B",
        "darkslategray" to "#2F4F4F", "darkslategrey" to "#2F4F4F", "darkturquoise" to "#00CED1",
        "darkviolet" to "#9400D3", "deeppink" to "#FF1493", "deepskyblue" to "#00BFFF",
        "dimgray" to "#696969", "dimgrey" to "#696969", "dodgerblue" to "#1E90FF",
        "firebrick" to "#B22222", "floralwhite" to "#FFFAF0", "forestgreen" to "#228B22",
        "fuchsia" to "#FF00FF", "gainsboro" to "#DCDCDC", "ghostwhite" to "#F8F8FF",
        "gold" to "#FFD700", "goldenrod" to "#DAA520", "gray" to "#808080",
        "grey" to "#808080", "green" to "#008000", "greenyellow" to "#ADFF2F",
        "honeydew" to "#F0FFF0", "hotpink" to "#FF69B4", "indianred" to "#CD5C5C",
        "indigo" to "#4B0082", "ivory" to "#FFFFF0", "khaki" to "#F0E68C",
        "lavender" to "#E6E6FA", "lavenderblush" to "#FFF0F5", "lawngreen" to "#7CFC00",
        "lemonchiffon" to "#FFFACD", "lightblue" to "#ADD8E6", "lightcoral" to "#F08080",
        "lightcyan" to "#E0FFFF", "lightgoldenrodyellow" to "#FAFAD2", "lightgray" to "#D3D3D3",
        "lightgrey" to "#D3D3D3", "lightgreen" to "#90EE90", "lightpink" to "#FFB6C1",
        "lightsalmon" to "#FFA07A", "lightseagreen" to "#20B2AA", "lightskyblue" to "#87CEFA",
        "lightslategray" to "#778899", "lightslategrey" to "#778899", "lightsteelblue" to "#B0C4DE",
        "lightyellow" to "#FFFFE0", "lime" to "#00FF00", "limegreen" to "#32CD32",
        "linen" to "#FAF0E6", "magenta" to "#FF00FF", "maroon" to "#800000",
        "mediumaquamarine" to "#66CDAA", "mediumblue" to "#0000CD", "mediumorchid" to "#BA55D3",
        "mediumpurple" to "#9370DB", "mediumseagreen" to "#3CB371", "mediumslateblue" to "#7B68EE",
        "mediumspringgreen" to "#00FA9A", "mediumturquoise" to "#48D1CC", "mediumvioletred" to "#C71585",
        "midnightblue" to "#191970", "mintcream" to "#F5FFFA", "mistyrose" to "#FFE4E1",
        "moccasin" to "#FFE4B5", "navajowhite" to "#FFDEAD", "navy" to "#000080",
        "oldlace" to "#FDF5E6", "olive" to "#808000", "olivedrab" to "#6B8E23",
        "orange" to "#FFA500", "orangered" to "#FF4500", "orchid" to "#DA70D6",
        "palegoldenrod" to "#EEE8AA", "palegreen" to "#98FB98", "paleturquoise" to "#AFEEEE",
        "palevioletred" to "#DB7093", "papayawhip" to "#FFEFD5", "peachpuff" to "#FFDAB9",
        "peru" to "#CD853F", "pink" to "#FFC0CB", "plum" to "#DDA0DD",
        "powderblue" to "#B0E0E6", "purple" to "#800080", "rebeccapurple" to "#663399",
        "red" to "#FF0000", "rosybrown" to "#BC8F8F", "royalblue" to "#4169E1",
        "saddlebrown" to "#8B4513", "salmon" to "#FA8072", "sandybrown" to "#F4A460",
        "seagreen" to "#2E8B57", "seashell" to "#FFF5EE", "sienna" to "#A0522D",
        "silver" to "#C0C0C0", "skyblue" to "#87CEEB", "slateblue" to "#6A5ACD",
        "slategray" to "#708090", "slategrey" to "#708090", "snow" to "#FFFAFA",
        "springgreen" to "#00FF7F", "steelblue" to "#4682B4", "tan" to "#D2B48C",
        "teal" to "#008080", "thistle" to "#D8BFD8", "tomato" to "#FF6347",
        "turquoise" to "#40E0D0", "violet" to "#EE82EE", "wheat" to "#F5DEB3",
        "white" to "#FFFFFF", "whitesmoke" to "#F5F5F5", "yellow" to "#FFFF00",
        "yellowgreen" to "#9ACD32"
    )
}
