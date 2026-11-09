package com.nexustools.genericui.style.layout

import com.nexustools.genericui.component.layout.AspectRatioContainer
import com.nexustools.genericui.component.layout.ColumnLayout
import com.nexustools.genericui.component.layout.FlexBox
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.HybridColumnLayout
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayout
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.BootstrapAspectRatio
import com.nexustools.genericui.enums.bootstrap.BootstrapHeight
import com.nexustools.genericui.enums.bootstrap.BootstrapVisibility
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssAlignContent
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssLength
import kotlin.math.roundToInt

/**
 * Normalized snapshot of CSS box-model, flexbox, Bootstrap 12-column grid, aspect-ratio,
 * and layer-positioning properties evaluated by [BrowserLayoutEngine].
 */
data class LayoutStyleSnapshot(
    val display: CssDisplay = CssDisplay.BLOCK,
    val visibility: CssVisibility = CssVisibility.VISIBLE,
    val position: CssPosition = CssPosition.STATIC,
    val top: CssLength? = null,
    val right: CssLength? = null,
    val bottom: CssLength? = null,
    val left: CssLength? = null,
    val boxSizing: CssBoxSizing = CssBoxSizing.BORDER_BOX,
    val width: CssLength? = null,
    val height: CssLength? = null,
    val minWidth: CssLength? = null,
    val minHeight: CssLength? = null,
    val maxWidth: CssLength? = null,
    val maxHeight: CssLength? = null,
    val aspectRatio: Float? = null,
    val marginTop: CssLength? = null,
    val marginRight: CssLength? = null,
    val marginBottom: CssLength? = null,
    val marginLeft: CssLength? = null,
    val padding: EdgeInsets = EdgeInsets.ZERO,
    val border: EdgeInsets = EdgeInsets.ZERO,
    val flexDirection: FlexDirection = FlexDirection.ROW,
    val flexWrap: FlexWrap = FlexWrap.NOWRAP,
    val justifyContent: JustifyContent = JustifyContent.START,
    val alignItems: AlignItems = AlignItems.STRETCH,
    val alignSelf: AlignSelf = AlignSelf.AUTO,
    val alignContent: CssAlignContent = CssAlignContent.STRETCH,
    val flexGrow: Float = 0f,
    val flexShrink: Float = 1f,
    val flexBasis: CssLength? = null,
    val rowGapPx: Int = 0,
    val columnGapPx: Int = 0,
    val gridColSpan: Int? = null,
    val isGridAutoCol: Boolean = false,
    val isGridRow: Boolean = false,
    val overflowX: OverflowBehavior = OverflowBehavior.VISIBLE,
    val overflowY: OverflowBehavior = OverflowBehavior.VISIBLE,
    val zIndex: Int = 0,
    val translateXPercent: Float = 0f,
    val translateYPercent: Float = 0f,
    val translateXPx: Float = 0f,
    val translateYPx: Float = 0f,
    val layerRole: String? = null,
    val layerAlignment: LayerAlignment? = null,
    val layerOffsetXPx: Int = 0,
    val layerOffsetYPx: Int = 0,
    val layerLeftPercent: Float? = null,
    val layerTopPercent: Float? = null,
    val layerRightPx: Int? = null,
    val layerBottomPx: Int? = null
) {
    companion object {
        private val CALC_PERCENT_PX_REGEX = Regex(
            """calc\(\s*(-?\d+(?:\.\d+)?)%\s*([+-])\s*(-?\d+(?:\.\d+)?)\s*(?:px)?\s*\)""",
            RegexOption.IGNORE_CASE
        )
        private val TRANSLATE_FN_REGEX = Regex(
            """(translate3d|translateX|translateY|translate)\(\s*([^)]+)\s*\)""",
            RegexOption.IGNORE_CASE
        )
        private val COL_SPAN_REGEX = Regex("""^col(?:-(sm|md|lg|xl|xxl))?-(\d{1,2})$""", RegexOption.IGNORE_CASE)
        private val COL_AUTO_REGEX = Regex("""^col(?:-(sm|md|lg|xl|xxl))?$""", RegexOption.IGNORE_CASE)
        private val DISPLAY_RESPONSIVE_REGEX = Regex(
            """^d-(?:(sm|md|lg|xl|xxl)-)?(none|inline|inline-block|block|grid|flex|inline-flex)$""",
            RegexOption.IGNORE_CASE
        )

        private fun matchesBreakpoint(bp: String?, viewportWidthPx: Int): Boolean = when (bp?.lowercase()) {
            null, "" -> true
            "sm" -> viewportWidthPx >= 576
            "md" -> viewportWidthPx >= 768
            "lg" -> viewportWidthPx >= 992
            "xl" -> viewportWidthPx >= 1200
            "xxl" -> viewportWidthPx >= 1400
            else -> false
        }

        private fun breakpointRank(bp: String?): Int = when (bp?.lowercase()) {
            null, "" -> 0
            "sm" -> 1
            "md" -> 2
            "lg" -> 3
            "xl" -> 4
            "xxl" -> 5
            else -> -1
        }

        private fun parseLengthWithCalcOffset(raw: String?): Pair<CssLength?, Float> {
            val cleaned = raw?.trim()?.removeSuffix("!important")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return null to 0f
            return CssLength.parse(cleaned) to 0f
        }

        private fun parseTransformTranslate(rawTransform: String?): FloatArray {
            // [txPercent, tyPercent, txPx, tyPx]
            val result = FloatArray(4)
            if (rawTransform.isNullOrBlank() || rawTransform.equals("none", ignoreCase = true)) {
                return result
            }
            for (match in TRANSLATE_FN_REGEX.findAll(rawTransform)) {
                val fn = match.groupValues[1].lowercase()
                val args = match.groupValues[2].split(',').map { it.trim() }
                when (fn) {
                    "translatex" -> applyTranslateToken(args.getOrNull(0), isX = true, result)
                    "translatey" -> applyTranslateToken(args.getOrNull(0), isX = false, result)
                    "translate", "translate3d" -> {
                        applyTranslateToken(args.getOrNull(0), isX = true, result)
                        applyTranslateToken(args.getOrNull(1), isX = false, result)
                    }
                }
            }
            return result
        }

        private fun applyTranslateToken(token: String?, isX: Boolean, out: FloatArray) {
            val t = token?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (t.endsWith("%")) {
                val pct = t.removeSuffix("%").trim().toFloatOrNull() ?: return
                if (isX) out[0] += pct else out[1] += pct
            } else {
                val num = t.removeSuffix("px").trim().toFloatOrNull() ?: return
                if (isX) out[2] += num else out[3] += num
            }
        }

        private fun parseAspectRatioValue(raw: String?): Float? {
            val cleaned = raw?.trim()?.removeSuffix("!important")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (cleaned.equals("auto", ignoreCase = true)) return null
            if ('/' in cleaned) {
                val parts = cleaned.split('/')
                val w = parts.getOrNull(0)?.trim()?.toFloatOrNull()
                val h = parts.getOrNull(1)?.trim()?.toFloatOrNull()
                if (w != null && h != null && w > 0f && h > 0f) return w / h
            }
            return cleaned.toFloatOrNull()?.takeIf { it > 0f }
        }

        private fun parseBsAspectRatioVar(rawPercent: String?): Float? {
            val cleaned = rawPercent?.trim()?.removeSuffix("%")?.trim()?.toFloatOrNull() ?: return null
            if (cleaned <= 0f) return null
            return 100f / cleaned
        }

        private fun parseFlexShorthand(rawFlex: String?): Triple<Float, Float, CssLength?>? {
            val cleaned = rawFlex?.trim()?.removeSuffix("!important")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            when (cleaned.lowercase()) {
                "none" -> return Triple(0f, 0f, CssLength.AUTO)
                "auto" -> return Triple(1f, 1f, CssLength.AUTO)
                "initial" -> return Triple(0f, 1f, CssLength.AUTO)
            }
            val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when (tokens.size) {
                1 -> {
                    val num = tokens[0].toFloatOrNull()
                    if (num != null) Triple(num, 1f, CssLength.percent(0f))
                    else CssLength.parse(tokens[0])?.let { Triple(1f, 1f, it) }
                }
                2 -> {
                    val grow = tokens[0].toFloatOrNull() ?: 0f
                    val secondNum = tokens[1].toFloatOrNull()
                    if (secondNum != null && !tokens[1].endsWith("px") && !tokens[1].endsWith("%")) {
                        Triple(grow, secondNum, CssLength.percent(0f))
                    } else {
                        Triple(grow, 1f, CssLength.parse(tokens[1]))
                    }
                }
                else -> {
                    val grow = tokens[0].toFloatOrNull() ?: 0f
                    val shrink = tokens[1].toFloatOrNull() ?: 1f
                    val basis = CssLength.parse(tokens[2])
                    Triple(grow, shrink, basis)
                }
            }
        }

        private fun isContainerLikeTag(normalizedTag: String): Boolean = normalizedTag in setOf(
            "div",
            "container",
            "styledcontainer",
            "styled-container",
            "vstack",
            "hstack",
            "flexbox",
            "columnlayout",
            "column",
            "row",
            "aspectratiocontainer",
            "aspect-ratio-container",
            "layeredcontainer",
            "layered-container",
            "hybridcolumnlayout",
            "hybrid-column-layout",
            "hybridsplitnavigationlayout",
            "hybrid-split-navigation-layout",
            "tabcontainer",
            "tab-container",
            "card",
            "alert",
            "navbar",
            "bottomnavigationbar",
            "scrollview",
            "lazylist",
            "lazygrid"
        )

        fun fromComponent(
            component: AbstractComponent,
            viewportWidthPx: Int = 1280,
            viewportHeightPx: Int = 800
        ): LayoutStyleSnapshot {
            val bootstrap = component.getBootstrap()
            val compStyle = component.getStyle()
            val computed = component.computedStyle
            val allClasses = LinkedHashSet<String>()
            if (component.componentClassPrefix.isNotBlank()) {
                allClasses.addAll(component.componentClassPrefix.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
            }
            allClasses.addAll(bootstrap.toEnumClassSet(component.componentClassPrefix))
            allClasses.addAll(component.getClasses())

            val styleProps = linkedMapOf<String, String>()
            if (computed != null) {
                computed.display?.let { styleProps["display"] = it }
                computed.visibility?.cssValue?.let { styleProps["visibility"] = it }
                computed.position?.let { styleProps["position"] = it }
                computed.top?.let { styleProps["top"] = it }
                computed.right?.let { styleProps["right"] = it }
                computed.bottom?.let { styleProps["bottom"] = it }
                computed.left?.let { styleProps["left"] = it }
                computed.boxSizing?.cssValue?.let { styleProps["box-sizing"] = it }
                computed.width?.let { styleProps["width"] = it }
                computed.height?.let { styleProps["height"] = it }
                computed.minWidth?.let { styleProps["min-width"] = it }
                computed.minHeight?.let { styleProps["min-height"] = it }
                computed.maxWidth?.let { styleProps["max-width"] = it }
                computed.maxHeight?.let { styleProps["max-height"] = it }
                computed.aspectRatio?.let { styleProps["aspect-ratio"] = it.toString() }
                computed.padding?.toCssString()?.let { styleProps["padding"] = it }
                computed.paddingTop?.toCssString()?.let { styleProps["padding-top"] = it }
                computed.paddingRight?.toCssString()?.let { styleProps["padding-right"] = it }
                computed.paddingBottom?.toCssString()?.let { styleProps["padding-bottom"] = it }
                computed.paddingLeft?.toCssString()?.let { styleProps["padding-left"] = it }
                computed.margin?.toCssString()?.let { styleProps["margin"] = it }
                computed.marginTop?.toCssString()?.let { styleProps["margin-top"] = it }
                computed.marginRight?.toCssString()?.let { styleProps["margin-right"] = it }
                computed.marginBottom?.toCssString()?.let { styleProps["margin-bottom"] = it }
                computed.marginLeft?.toCssString()?.let { styleProps["margin-left"] = it }
                computed.borderWidthPx?.let { styleProps["border-width"] = "${it}px" }
                computed.flexDirection?.let { styleProps["flex-direction"] = it.name.lowercase().replace('_', '-') }
                computed.flexWrap?.let { styleProps["flex-wrap"] = it.name.lowercase().replace('_', '-') }
                computed.justifyContent?.let {
                    styleProps["justify-content"] = when (it) {
                        JustifyContent.START -> "flex-start"
                        JustifyContent.END -> "flex-end"
                        JustifyContent.CENTER -> "center"
                        JustifyContent.BETWEEN -> "space-between"
                        JustifyContent.AROUND -> "space-around"
                        JustifyContent.EVENLY -> "space-evenly"
                    }
                }
                computed.alignItems?.let {
                    styleProps["align-items"] = when (it) {
                        AlignItems.START -> "flex-start"
                        AlignItems.END -> "flex-end"
                        AlignItems.CENTER -> "center"
                        AlignItems.BASELINE -> "baseline"
                        AlignItems.STRETCH -> "stretch"
                    }
                }
                computed.alignSelf?.let {
                    styleProps["align-self"] = when (it) {
                        AlignSelf.AUTO -> "auto"
                        AlignSelf.START -> "flex-start"
                        AlignSelf.END -> "flex-end"
                        AlignSelf.CENTER -> "center"
                        AlignSelf.BASELINE -> "baseline"
                        AlignSelf.STRETCH -> "stretch"
                    }
                }
                computed.alignContent?.cssValue?.let { styleProps["align-content"] = it }
                computed.flexGrow?.let { styleProps["flex-grow"] = it.toString() }
                computed.flexShrink?.let { styleProps["flex-shrink"] = it.toString() }
                computed.flexBasis?.toCssString()?.let { styleProps["flex-basis"] = it }
                computed.order?.let { styleProps["order"] = it.toString() }
                computed.gap?.toCssString()?.let { styleProps["gap"] = it }
                computed.rowGap?.toCssString()?.let { styleProps["row-gap"] = it }
                computed.columnGap?.toCssString()?.let { styleProps["column-gap"] = it }
                computed.overflow?.let { styleProps["overflow"] = it }
                computed.overflowX?.let { styleProps["overflow-x"] = it.name.lowercase() }
                computed.overflowY?.let { styleProps["overflow-y"] = it.name.lowercase() }
                computed.zIndex?.let { styleProps["z-index"] = it.toString() }
                computed.transform?.let { styleProps["transform"] = it }
            }
            if (!compStyle.isEmpty()) {
                for ((k, pair) in compStyle.toCssPropertyMap()) {
                    styleProps[k.lowercase()] = pair.first
                }
            }
            // Include component top-level properties if not already in styleProps
            component.aspectRatio?.let { if (!styleProps.containsKey("aspect-ratio")) styleProps["aspect-ratio"] = it.toString() }
            component.zIndex?.let { if (!styleProps.containsKey("z-index")) styleProps["z-index"] = it.toString() }
            component.leftOffset?.let { if (!styleProps.containsKey("left")) styleProps["left"] = it }
            component.topOffset?.let { if (!styleProps.containsKey("top")) styleProps["top"] = it }
            component.transform?.let { if (!styleProps.containsKey("transform")) styleProps["transform"] = it }
            component.widthPx?.let { styleProps["width"] = "${it}px" }
            component.heightPx?.let { styleProps["height"] = "${it}px" }
            component.minWidthPx?.let { styleProps["min-width"] = "${it}px" }
            component.minHeightPx?.let { styleProps["min-height"] = "${it}px" }
            component.maxWidthPx?.let { styleProps["max-width"] = "${it}px" }
            component.maxHeightPx?.let { styleProps["max-height"] = "${it}px" }

            val attrs = LinkedHashMap(component.getAttributes())
            when (component) {
                is AspectRatioContainer -> {
                    allClasses.add("ratio")
                    if (!styleProps.containsKey("aspect-ratio")) styleProps["aspect-ratio"] = component.effectiveAspectRatio.toString()
                    if (!styleProps.containsKey("position")) styleProps["position"] = "relative"
                    if (!styleProps.containsKey("width")) styleProps["width"] = "100%"
                }
                is LayeredContainer -> {
                    allClasses.add("gui-layered-container")
                }
                is VStack -> {
                    allClasses.add("d-flex")
                    allClasses.add("flex-column")
                }
                is HStack -> {
                    allClasses.add("d-flex")
                    allClasses.add("flex-row")
                }
                is FlexBox -> {
                    allClasses.add("d-flex")
                }
                is Navbar -> {
                    allClasses.add("navbar")
                }
                is BottomNavigationBar -> {
                    allClasses.add("bottom-navigation-bar")
                }
            }

            val explicitBounds = Rect(
                x = 0,
                y = 0,
                width = component.widthPx ?: 0,
                height = component.heightPx ?: 0
            )

            return fromAttributesAndStyles(
                tagName = component.htmlTag,
                classes = allClasses,
                attributes = attrs,
                styleProps = styleProps,
                explicitBounds = explicitBounds,
                padding = component.padding,
                margin = component.margin,
                visible = component.isVisible,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
        }

        fun fromAttributesAndStyles(
            tagName: String,
            classes: Set<String>,
            attributes: Map<String, String>,
            styleProps: Map<String, String>,
            explicitBounds: Rect = Rect.ZERO,
            padding: EdgeInsets = EdgeInsets.ZERO,
            margin: EdgeInsets = EdgeInsets.ZERO,
            visible: Boolean = true,
            viewportWidthPx: Int = 1280,
            viewportHeightPx: Int = 800
        ): LayoutStyleSnapshot {
            val normalizedTag = tagName.trim().lowercase().removePrefix("genericui:")
            val normalizedClasses = classes
                .flatMap { it.trim().split(Regex("\\s+")) }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()

            val normalizedStyle = linkedMapOf<String, String>()
            for ((k, v) in styleProps) {
                val cleanVal = v.trim().removeSuffix("!important").trim()
                if (cleanVal.isNotEmpty()) {
                    normalizedStyle[k.trim().lowercase()] = cleanVal
                }
            }

            if (!visible) {
                return LayoutStyleSnapshot(display = CssDisplay.NONE)
            }

            // 1. Default display & flex direction from tag & classes
            var display: CssDisplay = when (normalizedTag) {
                "vstack", "hybridcolumnlayout", "hybrid-column-layout",
                "hybridsplitnavigationlayout", "hybrid-split-navigation-layout" -> CssDisplay.FLEX
                "hstack", "flexbox", "navbar", "bottomnavigationbar", "bottom-navigation-bar" -> CssDisplay.FLEX
                "columnlayout", "row" -> CssDisplay.FLEX
                "span", "badge", "label" -> CssDisplay.INLINE_BLOCK
                else -> CssDisplay.BLOCK
            }
            var flexDirection: FlexDirection = when (normalizedTag) {
                "vstack", "hybridcolumnlayout", "hybrid-column-layout",
                "hybridsplitnavigationlayout", "hybrid-split-navigation-layout" -> FlexDirection.COLUMN
                else -> FlexDirection.ROW
            }
            var flexWrap: FlexWrap = if (normalizedTag == "columnlayout" || normalizedTag == "row") {
                FlexWrap.WRAP
            } else {
                FlexWrap.NOWRAP
            }
            var justifyContent = JustifyContent.START
            var alignItems = if (normalizedTag == "navbar" || normalizedTag == "bottomnavigationbar" || normalizedTag == "bottom-navigation-bar") {
                AlignItems.CENTER
            } else {
                AlignItems.STRETCH
            }
            var alignSelf = AlignSelf.AUTO
            var alignContent = CssAlignContent.STRETCH
            var isGridRow = normalizedTag == "columnlayout" || normalizedTag == "row"
            var gridColSpan: Int? = null
            var bestColBpRank = -1
            var isGridAutoCol = false
            var position = if (normalizedTag == "aspectratiocontainer" || normalizedTag == "aspect-ratio-container" ||
                normalizedTag == "layeredcontainer" || normalizedTag == "layered-container"
            ) {
                CssPosition.RELATIVE
            } else {
                CssPosition.STATIC
            }
            var visibility = CssVisibility.VISIBLE
            var overflowX = OverflowBehavior.VISIBLE
            var overflowY = OverflowBehavior.VISIBLE
            var flexGrow = 0f
            var flexShrink = 1f
            var flexBasis: CssLength? = null
            var aspectRatio: Float? = null
            var widthLength: CssLength? = if (normalizedTag in setOf(
                    "navbar",
                    "bottomnavigationbar",
                    "bottom-navigation-bar",
                    "aspectratiocontainer",
                    "aspect-ratio-container"
                )
            ) {
                CssLength.percent(100f)
            } else {
                null
            }
            var heightLength: CssLength? = null
            var minWidthLength: CssLength? = null
            var minHeightLength: CssLength? = null
            var maxWidthLength: CssLength? = null
            var maxHeightLength: CssLength? = null

            var padTop = padding.top
            var padRight = padding.right
            var padBottom = padding.bottom
            var padLeft = padding.left

            var marTop: CssLength? = if (margin.top != 0) CssLength.px(margin.top) else null
            var marRight: CssLength? = if (margin.right != 0) CssLength.px(margin.right) else null
            var marBottom: CssLength? = if (margin.bottom != 0) CssLength.px(margin.bottom) else null
            var marLeft: CssLength? = if (margin.left != 0) CssLength.px(margin.left) else null

            var rowGapPx = 0
            var columnGapPx = 0

            // 2. Scan Bootstrap & utility classes
            var bestDisplayBpRank = -1
            for (cls in normalizedClasses) {
                val dispMatch = DISPLAY_RESPONSIVE_REGEX.matchEntire(cls)
                if (dispMatch != null) {
                    val bp = dispMatch.groupValues[1].takeIf { it.isNotEmpty() }
                    val rank = breakpointRank(bp)
                    if (matchesBreakpoint(bp, viewportWidthPx) && rank >= bestDisplayBpRank) {
                        bestDisplayBpRank = rank
                        display = when (dispMatch.groupValues[2].lowercase()) {
                            "none" -> CssDisplay.NONE
                            "inline" -> CssDisplay.INLINE
                            "inline-block" -> CssDisplay.INLINE_BLOCK
                            "block" -> CssDisplay.BLOCK
                            "grid" -> CssDisplay.GRID
                            "flex" -> CssDisplay.FLEX
                            "inline-flex" -> CssDisplay.INLINE_FLEX
                            else -> display
                        }
                    }
                    continue
                }

                val colSpanMatch = COL_SPAN_REGEX.matchEntire(cls)
                if (colSpanMatch != null) {
                    val bp = colSpanMatch.groupValues[1].takeIf { it.isNotEmpty() }
                    val rank = breakpointRank(bp)
                    val span = colSpanMatch.groupValues[2].toIntOrNull()?.coerceIn(1, 12)
                    if (span != null && matchesBreakpoint(bp, viewportWidthPx) && rank >= bestColBpRank) {
                        bestColBpRank = rank
                        gridColSpan = span
                    }
                    continue
                }

                val colAutoMatch = COL_AUTO_REGEX.matchEntire(cls)
                if (colAutoMatch != null) {
                    val bp = colAutoMatch.groupValues[1].takeIf { it.isNotEmpty() }
                    if (matchesBreakpoint(bp, viewportWidthPx)) {
                        isGridAutoCol = true
                    }
                    continue
                }

                when (cls) {
                    "row" -> {
                        isGridRow = true
                        if (display != CssDisplay.NONE) display = CssDisplay.FLEX
                        flexDirection = FlexDirection.ROW
                        if ("flex-nowrap" !in normalizedClasses) {
                            flexWrap = FlexWrap.WRAP
                        }
                    }
                    "vstack" -> {
                        if (display != CssDisplay.NONE) display = CssDisplay.FLEX
                        flexDirection = FlexDirection.COLUMN
                    }
                    "hstack" -> {
                        if (display != CssDisplay.NONE) display = CssDisplay.FLEX
                        flexDirection = FlexDirection.ROW
                    }
                    "flex-row" -> flexDirection = FlexDirection.ROW
                    "flex-row-reverse" -> flexDirection = FlexDirection.ROW_REVERSE
                    "flex-column" -> flexDirection = FlexDirection.COLUMN
                    "flex-column-reverse" -> flexDirection = FlexDirection.COLUMN_REVERSE
                    "flex-wrap" -> flexWrap = FlexWrap.WRAP
                    "flex-nowrap" -> flexWrap = FlexWrap.NOWRAP
                    "flex-wrap-reverse" -> flexWrap = FlexWrap.WRAP_REVERSE
                    "justify-content-start" -> justifyContent = JustifyContent.START
                    "justify-content-end" -> justifyContent = JustifyContent.END
                    "justify-content-center" -> justifyContent = JustifyContent.CENTER
                    "justify-content-between" -> justifyContent = JustifyContent.BETWEEN
                    "justify-content-around" -> justifyContent = JustifyContent.AROUND
                    "justify-content-evenly" -> justifyContent = JustifyContent.EVENLY
                    "align-items-start" -> alignItems = AlignItems.START
                    "align-items-end" -> alignItems = AlignItems.END
                    "align-items-center" -> alignItems = AlignItems.CENTER
                    "align-items-baseline" -> alignItems = AlignItems.BASELINE
                    "align-items-stretch" -> alignItems = AlignItems.STRETCH
                    "align-self-auto" -> alignSelf = AlignSelf.AUTO
                    "align-self-start" -> alignSelf = AlignSelf.START
                    "align-self-end" -> alignSelf = AlignSelf.END
                    "align-self-center" -> alignSelf = AlignSelf.CENTER
                    "align-self-baseline" -> alignSelf = AlignSelf.BASELINE
                    "align-self-stretch" -> alignSelf = AlignSelf.STRETCH
                    "flex-grow-0" -> flexGrow = 0f
                    "flex-grow-1", "flex-fill" -> flexGrow = 1f
                    "flex-shrink-0" -> flexShrink = 0f
                    "flex-shrink-1" -> flexShrink = 1f
                    "w-25" -> widthLength = CssLength.percent(25f)
                    "w-50" -> widthLength = CssLength.percent(50f)
                    "w-75" -> widthLength = CssLength.percent(75f)
                    "w-100", "vw-100", "min-vw-100" -> widthLength = CssLength.percent(100f)
                    "w-auto" -> widthLength = CssLength.AUTO
                    "mw-100" -> maxWidthLength = CssLength.percent(100f)
                    "h-25" -> heightLength = CssLength.percent(25f)
                    "h-50" -> heightLength = CssLength.percent(50f)
                    "h-75" -> heightLength = CssLength.percent(75f)
                    "h-100", "vh-100", "min-vh-100" -> heightLength = CssLength.percent(100f)
                    "h-auto" -> heightLength = CssLength.AUTO
                    "mh-100" -> maxHeightLength = CssLength.percent(100f)
                    "position-static" -> position = CssPosition.STATIC
                    "position-relative" -> position = CssPosition.RELATIVE
                    "position-absolute" -> position = CssPosition.ABSOLUTE
                    "position-fixed" -> position = CssPosition.FIXED
                    "position-sticky" -> position = CssPosition.STICKY
                    "overflow-auto" -> {
                        overflowX = OverflowBehavior.AUTO
                        overflowY = OverflowBehavior.AUTO
                    }
                    "overflow-hidden" -> {
                        overflowX = OverflowBehavior.HIDDEN
                        overflowY = OverflowBehavior.HIDDEN
                    }
                    "overflow-visible" -> {
                        overflowX = OverflowBehavior.VISIBLE
                        overflowY = OverflowBehavior.VISIBLE
                    }
                    "overflow-scroll" -> {
                        overflowX = OverflowBehavior.SCROLL
                        overflowY = OverflowBehavior.SCROLL
                    }
                    "visible" -> visibility = CssVisibility.VISIBLE
                    "invisible" -> visibility = CssVisibility.HIDDEN
                    "ratio" -> {
                        position = CssPosition.RELATIVE
                        if (widthLength == null) widthLength = CssLength.percent(100f)
                        if (aspectRatio == null) aspectRatio = 16f / 9f
                    }
                    "ratio-1x1" -> aspectRatio = 1f
                    "ratio-4x3" -> aspectRatio = 4f / 3f
                    "ratio-16x9" -> aspectRatio = 16f / 9f
                    "ratio-21x9" -> aspectRatio = 21f / 9f
                    "hybrid-columns-row", "gui-hybrid-columns-row" -> {
                        if (flexGrow == 0f) flexGrow = 1f
                        flexWrap = FlexWrap.NOWRAP
                    }
                    "mt-auto" -> marTop = CssLength.AUTO
                    "mb-auto" -> marBottom = CssLength.AUTO
                    "ms-auto", "ml-auto" -> marLeft = CssLength.AUTO
                    "me-auto", "mr-auto" -> marRight = CssLength.AUTO
                    "mx-auto" -> {
                        marLeft = CssLength.AUTO
                        marRight = CssLength.AUTO
                    }
                    "my-auto" -> {
                        marTop = CssLength.AUTO
                        marBottom = CssLength.AUTO
                    }
                    else -> {
                        when {
                            cls.startsWith("gap-") || cls.startsWith("g-") -> {
                                val tok = cls.substringAfter('-')
                                SpacingScale.fromToken(tok)?.toPixels()?.let {
                                    rowGapPx = it
                                    columnGapPx = it
                                }
                            }
                            cls.startsWith("row-gap-") || cls.startsWith("gy-") -> {
                                val tok = cls.substringAfterLast('-')
                                SpacingScale.fromToken(tok)?.toPixels()?.let { rowGapPx = it }
                            }
                            cls.startsWith("column-gap-") || cls.startsWith("gx-") -> {
                                val tok = cls.substringAfterLast('-')
                                SpacingScale.fromToken(tok)?.toPixels()?.let { columnGapPx = it }
                            }
                            cls.startsWith("p-") -> {
                                SpacingScale.fromToken(cls.removePrefix("p-"))?.toPixels()?.let {
                                    padTop = it; padRight = it; padBottom = it; padLeft = it
                                }
                            }
                            cls.startsWith("px-") -> {
                                SpacingScale.fromToken(cls.removePrefix("px-"))?.toPixels()?.let {
                                    padLeft = it; padRight = it
                                }
                            }
                            cls.startsWith("py-") -> {
                                SpacingScale.fromToken(cls.removePrefix("py-"))?.toPixels()?.let {
                                    padTop = it; padBottom = it
                                }
                            }
                            cls.startsWith("pt-") -> SpacingScale.fromToken(cls.removePrefix("pt-"))?.toPixels()?.let { padTop = it }
                            cls.startsWith("pb-") -> SpacingScale.fromToken(cls.removePrefix("pb-"))?.toPixels()?.let { padBottom = it }
                            cls.startsWith("ps-") -> SpacingScale.fromToken(cls.removePrefix("ps-"))?.toPixels()?.let { padLeft = it }
                            cls.startsWith("pe-") -> SpacingScale.fromToken(cls.removePrefix("pe-"))?.toPixels()?.let { padRight = it }
                            cls.startsWith("m-") -> {
                                SpacingScale.fromToken(cls.removePrefix("m-"))?.toPixels()?.let {
                                    val len = CssLength.px(it)
                                    marTop = len; marRight = len; marBottom = len; marLeft = len
                                }
                            }
                            cls.startsWith("mx-") -> {
                                SpacingScale.fromToken(cls.removePrefix("mx-"))?.toPixels()?.let {
                                    val len = CssLength.px(it)
                                    marLeft = len; marRight = len
                                }
                            }
                            cls.startsWith("my-") -> {
                                SpacingScale.fromToken(cls.removePrefix("my-"))?.toPixels()?.let {
                                    val len = CssLength.px(it)
                                    marTop = len; marBottom = len
                                }
                            }
                            cls.startsWith("mt-") -> SpacingScale.fromToken(cls.removePrefix("mt-"))?.toPixels()?.let { marTop = CssLength.px(it) }
                            cls.startsWith("mb-") -> SpacingScale.fromToken(cls.removePrefix("mb-"))?.toPixels()?.let { marBottom = CssLength.px(it) }
                            cls.startsWith("ms-") -> SpacingScale.fromToken(cls.removePrefix("ms-"))?.toPixels()?.let { marLeft = CssLength.px(it) }
                            cls.startsWith("me-") -> SpacingScale.fromToken(cls.removePrefix("me-"))?.toPixels()?.let { marRight = CssLength.px(it) }
                        }
                    }
                }
            }

            // 3. Apply CSS inline & stylesheet declarations (overriding class defaults unless d-none was set)
            normalizedStyle["display"]?.let { raw ->
                CssDisplay.fromCss(raw)?.let { display = it }
            }
            if ("d-none" in normalizedClasses) {
                display = CssDisplay.NONE
            }
            normalizedStyle["visibility"]?.let { raw ->
                CssVisibility.fromCss(raw)?.let { visibility = it }
            }
            normalizedStyle["position"]?.let { raw ->
                CssPosition.fromCss(raw)?.let { position = it }
            }
            val boxSizing = CssBoxSizing.fromCss(normalizedStyle["box-sizing"]) ?: CssBoxSizing.BORDER_BOX

            normalizedStyle["flex-direction"]?.let { raw ->
                when (raw.lowercase()) {
                    "row" -> flexDirection = FlexDirection.ROW
                    "row-reverse" -> flexDirection = FlexDirection.ROW_REVERSE
                    "column" -> flexDirection = FlexDirection.COLUMN
                    "column-reverse" -> flexDirection = FlexDirection.COLUMN_REVERSE
                }
            }
            normalizedStyle["flex-wrap"]?.let { raw ->
                when (raw.lowercase()) {
                    "nowrap" -> flexWrap = FlexWrap.NOWRAP
                    "wrap" -> flexWrap = FlexWrap.WRAP
                    "wrap-reverse" -> flexWrap = FlexWrap.WRAP_REVERSE
                }
            }
            normalizedStyle["justify-content"]?.let { raw ->
                when (raw.lowercase()) {
                    "flex-start", "start", "left" -> justifyContent = JustifyContent.START
                    "flex-end", "end", "right" -> justifyContent = JustifyContent.END
                    "center" -> justifyContent = JustifyContent.CENTER
                    "space-between" -> justifyContent = JustifyContent.BETWEEN
                    "space-around" -> justifyContent = JustifyContent.AROUND
                    "space-evenly" -> justifyContent = JustifyContent.EVENLY
                }
            }
            normalizedStyle["align-items"]?.let { raw ->
                when (raw.lowercase()) {
                    "flex-start", "start", "self-start" -> alignItems = AlignItems.START
                    "flex-end", "end", "self-end" -> alignItems = AlignItems.END
                    "center" -> alignItems = AlignItems.CENTER
                    "baseline" -> alignItems = AlignItems.BASELINE
                    "stretch" -> alignItems = AlignItems.STRETCH
                }
            }
            normalizedStyle["align-self"]?.let { raw ->
                when (raw.lowercase()) {
                    "auto" -> alignSelf = AlignSelf.AUTO
                    "flex-start", "start", "self-start" -> alignSelf = AlignSelf.START
                    "flex-end", "end", "self-end" -> alignSelf = AlignSelf.END
                    "center" -> alignSelf = AlignSelf.CENTER
                    "baseline" -> alignSelf = AlignSelf.BASELINE
                    "stretch" -> alignSelf = AlignSelf.STRETCH
                }
            }
            normalizedStyle["align-content"]?.let { raw ->
                CssAlignContent.fromCss(raw)?.let { alignContent = it }
            }

            parseFlexShorthand(normalizedStyle["flex"])?.let { (g, s, b) ->
                flexGrow = g
                flexShrink = s
                flexBasis = b
            }
            normalizedStyle["flex-grow"]?.toFloatOrNull()?.let { flexGrow = it }
            normalizedStyle["flex-shrink"]?.toFloatOrNull()?.let { flexShrink = it }
            normalizedStyle["flex-basis"]?.let { raw ->
                CssLength.parse(raw)?.let { flexBasis = it }
            }
            attributes["flexGrow"]?.toFloatOrNull()?.let { flexGrow = it }
            attributes["flexShrink"]?.toFloatOrNull()?.let { flexShrink = it }
            attributes["flexBasis"]?.let { raw -> CssLength.parse(raw)?.let { flexBasis = it } }

            normalizedStyle["gap"]?.let { raw ->
                val parts = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val r = CssLength.parse(parts.getOrNull(0))?.resolvePx(viewportWidthPx = viewportWidthPx.toFloat(), viewportHeightPx = viewportHeightPx.toFloat())?.roundToInt()
                val c = CssLength.parse(parts.getOrNull(1) ?: parts.getOrNull(0))?.resolvePx(viewportWidthPx = viewportWidthPx.toFloat(), viewportHeightPx = viewportHeightPx.toFloat())?.roundToInt()
                if (r != null) rowGapPx = r
                if (c != null) columnGapPx = c
            }
            normalizedStyle["row-gap"]?.let { raw ->
                CssLength.parse(raw)?.resolvePx(viewportWidthPx = viewportWidthPx.toFloat(), viewportHeightPx = viewportHeightPx.toFloat())?.roundToInt()?.let { rowGapPx = it }
            }
            normalizedStyle["column-gap"]?.let { raw ->
                CssLength.parse(raw)?.resolvePx(viewportWidthPx = viewportWidthPx.toFloat(), viewportHeightPx = viewportHeightPx.toFloat())?.roundToInt()?.let { columnGapPx = it }
            }
            attributes["gapPx"]?.toIntOrNull()?.let {
                rowGapPx = it
                columnGapPx = it
            }

            parseBsAspectRatioVar(normalizedStyle["--bs-aspect-ratio"])?.let { aspectRatio = it }
            parseAspectRatioValue(normalizedStyle["aspect-ratio"])?.let { aspectRatio = it }
            attributes["aspectRatio"]?.toFloatOrNull()?.takeIf { it > 0f }?.let { aspectRatio = it }

            normalizedStyle["width"]?.let { raw -> CssLength.parse(raw)?.let { widthLength = it } }
            normalizedStyle["height"]?.let { raw -> CssLength.parse(raw)?.let { heightLength = it } }
            normalizedStyle["min-width"]?.let { raw -> CssLength.parse(raw)?.let { minWidthLength = it } }
            normalizedStyle["min-height"]?.let { raw -> CssLength.parse(raw)?.let { minHeightLength = it } }
            normalizedStyle["max-width"]?.let { raw -> CssLength.parse(raw)?.let { maxWidthLength = it } }
            normalizedStyle["max-height"]?.let { raw -> CssLength.parse(raw)?.let { maxHeightLength = it } }

            // Padding from CSS
            CssEdgeLengths.parse(normalizedStyle["padding"])?.toEdgeInsets()?.let {
                padTop = it.top; padRight = it.right; padBottom = it.bottom; padLeft = it.left
            }
            CssLength.parse(normalizedStyle["padding-top"] ?: normalizedStyle["padding-block"])?.resolvePx()?.roundToInt()?.let { padTop = it }
            CssLength.parse(normalizedStyle["padding-right"] ?: normalizedStyle["padding-inline"])?.resolvePx()?.roundToInt()?.let { padRight = it }
            CssLength.parse(normalizedStyle["padding-bottom"] ?: normalizedStyle["padding-block"])?.resolvePx()?.roundToInt()?.let { padBottom = it }
            CssLength.parse(normalizedStyle["padding-left"] ?: normalizedStyle["padding-inline"])?.resolvePx()?.roundToInt()?.let { padLeft = it }

            // Margin from CSS
            normalizedStyle["margin"]?.let { raw ->
                val parts = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { CssLength.parse(it) }
                when (parts.size) {
                    1 -> { marTop = parts[0]; marRight = parts[0]; marBottom = parts[0]; marLeft = parts[0] }
                    2 -> { marTop = parts[0]; marBottom = parts[0]; marRight = parts[1]; marLeft = parts[1] }
                    3 -> { marTop = parts[0]; marRight = parts[1]; marLeft = parts[1]; marBottom = parts[2] }
                    4 -> { marTop = parts[0]; marRight = parts[1]; marBottom = parts[2]; marLeft = parts[3] }
                }
            }
            normalizedStyle["margin-top"]?.let { CssLength.parse(it)?.let { l -> marTop = l } }
            normalizedStyle["margin-right"]?.let { CssLength.parse(it)?.let { l -> marRight = l } }
            normalizedStyle["margin-bottom"]?.let { CssLength.parse(it)?.let { l -> marBottom = l } }
            normalizedStyle["margin-left"]?.let { CssLength.parse(it)?.let { l -> marLeft = l } }

            // Border widths from CSS
            val borderBase = CssLength.parse(normalizedStyle["border-width"])?.resolvePx()?.roundToInt()
                ?: normalizedStyle["border"]?.let { raw ->
                    Regex("(\\d+(?:\\.\\d+)?)px").find(raw)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.roundToInt()
                } ?: 0
            val borderTop = CssLength.parse(normalizedStyle["border-top-width"])?.resolvePx()?.roundToInt() ?: borderBase
            val borderRight = CssLength.parse(normalizedStyle["border-right-width"])?.resolvePx()?.roundToInt() ?: borderBase
            val borderBottom = CssLength.parse(normalizedStyle["border-bottom-width"])?.resolvePx()?.roundToInt() ?: borderBase
            val borderLeft = CssLength.parse(normalizedStyle["border-left-width"])?.resolvePx()?.roundToInt() ?: borderBase

            // Overflow from CSS
            normalizedStyle["overflow"]?.let { raw ->
                val ov = parseOverflowValue(raw)
                if (ov != null) {
                    overflowX = ov
                    overflowY = ov
                }
            }
            normalizedStyle["overflow-x"]?.let { raw -> parseOverflowValue(raw)?.let { overflowX = it } }
            normalizedStyle["overflow-y"]?.let { raw -> parseOverflowValue(raw)?.let { overflowY = it } }

            // Insets & calc(...) offsets
            val insetLength = CssLength.parse(normalizedStyle["inset"])
            val (parsedTop, calcTopOffsetPx) = parseLengthWithCalcOffset(normalizedStyle["top"])
            val (parsedRight, _) = parseLengthWithCalcOffset(normalizedStyle["right"])
            val (parsedBottom, _) = parseLengthWithCalcOffset(normalizedStyle["bottom"])
            val (parsedLeft, calcLeftOffsetPx) = parseLengthWithCalcOffset(normalizedStyle["left"])

            val topLength = parsedTop ?: insetLength
            val rightLength = parsedRight ?: insetLength
            val bottomLength = parsedBottom ?: insetLength
            val leftLength = parsedLeft ?: insetLength

            val transforms = parseTransformTranslate(normalizedStyle["transform"])
            var translateXPercent = transforms[0]
            var translateYPercent = transforms[1]
            var translateXPx = transforms[2] + calcLeftOffsetPx
            var translateYPx = transforms[3] + calcTopOffsetPx

            val zIndex = normalizedStyle["z-index"]?.toIntOrNull()
                ?: attributes["layerZIndex"]?.toIntOrNull()
                ?: attributes["zIndex"]?.toIntOrNull()
                ?: 0

            // Layer metadata attributes (LayeredContainer / Desktop widget layer attributes)
            val rawLayerSizing = attributes["layerSizingMode"] ?: attributes["layerRole"]
            val layerRole = when {
                rawLayerSizing.equals("FILL", ignoreCase = true) || "gui-layer-fill" in normalizedClasses -> "fill"
                rawLayerSizing.equals("FLOAT", ignoreCase = true) ||
                    rawLayerSizing.equals("FLOATING", ignoreCase = true) ||
                    "gui-layer-float" in normalizedClasses -> "floating"
                else -> null
            }
            val layerAlignment = attributes["layerAlignment"]?.takeIf { it.isNotBlank() }?.let { raw ->
                LayerAlignment.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            }
            val layerOffsetXPx = attributes["layerOffsetXPx"]?.toIntOrNull()
                ?: attributes["offsetXPx"]?.toIntOrNull()
                ?: 0
            val layerOffsetYPx = attributes["layerOffsetYPx"]?.toIntOrNull()
                ?: attributes["offsetYPx"]?.toIntOrNull()
                ?: 0
            val layerLeftPercent = attributes["layerLeftPercent"]?.toFloatOrNull()
                ?: attributes["leftPercent"]?.toFloatOrNull()
            val layerTopPercent = attributes["layerTopPercent"]?.toFloatOrNull()
                ?: attributes["topPercent"]?.toFloatOrNull()
            val layerRightPx = attributes["layerRightPx"]?.toIntOrNull()
                ?: attributes["rightPx"]?.toIntOrNull()
            val layerBottomPx = attributes["layerBottomPx"]?.toIntOrNull()
                ?: attributes["bottomPx"]?.toIntOrNull()

            if (layerRole != null && position == CssPosition.STATIC) {
                position = CssPosition.ABSOLUTE
            }

            // Use explicitBounds for leaf nodes when no CSS/Bootstrap width or height was specified
            val containerLike = isContainerLikeTag(normalizedTag)
            if (widthLength == null && explicitBounds.width > 0 && gridColSpan == null && !isGridAutoCol && flexGrow == 0f && !containerLike) {
                widthLength = CssLength.px(explicitBounds.width)
            }
            if (heightLength == null && explicitBounds.height > 0 && aspectRatio == null && flexGrow == 0f && !containerLike) {
                heightLength = CssLength.px(explicitBounds.height)
            }
            // If aspectRatio is active and height was not explicitly set in CSS, ensure height is auto (null)
            // so BrowserLayoutEngine computes height = width / aspectRatio.
            if (aspectRatio != null && "height" !in normalizedStyle && "h-100" !in normalizedClasses) {
                heightLength = null
            }

            return LayoutStyleSnapshot(
                display = display,
                visibility = visibility,
                position = position,
                top = topLength,
                right = rightLength,
                bottom = bottomLength,
                left = leftLength,
                boxSizing = boxSizing,
                width = widthLength,
                height = heightLength,
                minWidth = minWidthLength,
                minHeight = minHeightLength,
                maxWidth = maxWidthLength,
                maxHeight = maxHeightLength,
                aspectRatio = aspectRatio,
                marginTop = marTop,
                marginRight = marRight,
                marginBottom = marBottom,
                marginLeft = marLeft,
                padding = EdgeInsets(padTop, padRight, padBottom, padLeft),
                border = EdgeInsets(borderTop, borderRight, borderBottom, borderLeft),
                flexDirection = flexDirection,
                flexWrap = flexWrap,
                justifyContent = justifyContent,
                alignItems = alignItems,
                alignSelf = alignSelf,
                alignContent = alignContent,
                flexGrow = flexGrow,
                flexShrink = flexShrink,
                flexBasis = flexBasis,
                rowGapPx = rowGapPx,
                columnGapPx = columnGapPx,
                gridColSpan = gridColSpan,
                isGridAutoCol = isGridAutoCol,
                isGridRow = isGridRow,
                overflowX = overflowX,
                overflowY = overflowY,
                zIndex = zIndex,
                translateXPercent = translateXPercent,
                translateYPercent = translateYPercent,
                translateXPx = translateXPx,
                translateYPx = translateYPx,
                layerRole = layerRole,
                layerAlignment = layerAlignment,
                layerOffsetXPx = layerOffsetXPx,
                layerOffsetYPx = layerOffsetYPx,
                layerLeftPercent = layerLeftPercent,
                layerTopPercent = layerTopPercent,
                layerRightPx = layerRightPx,
                layerBottomPx = layerBottomPx
            )
        }

        private fun parseOverflowValue(raw: String): OverflowBehavior? = when (raw.trim().lowercase()) {
            "auto" -> OverflowBehavior.AUTO
            "hidden", "clip" -> OverflowBehavior.HIDDEN
            "visible" -> OverflowBehavior.VISIBLE
            "scroll" -> OverflowBehavior.SCROLL
            else -> null
        }
    }
}
