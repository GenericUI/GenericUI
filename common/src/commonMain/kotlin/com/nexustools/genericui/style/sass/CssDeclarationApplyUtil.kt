package com.nexustools.genericui.style.sass

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssAlignContent
import com.nexustools.genericui.enums.css.CssBackgroundAttachment
import com.nexustools.genericui.enums.css.CssBackgroundClip
import com.nexustools.genericui.enums.css.CssBackgroundOrigin
import com.nexustools.genericui.enums.css.CssBackgroundRepeat
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssBoxSizing
import com.nexustools.genericui.enums.css.CssClear
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssFloat
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.enums.css.CssUserSelect
import com.nexustools.genericui.enums.css.CssVerticalAlign
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.enums.css.CssWhiteSpace
import com.nexustools.genericui.enums.css.CssWordBreak
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssAspectRatio
import com.nexustools.genericui.style.css.CssBackgroundImage
import com.nexustools.genericui.style.css.CssBackgroundPosition
import com.nexustools.genericui.style.css.CssBackgroundSize
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssCustomCursor
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssShadowList
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionController

/**
 * Internal helper that parses CSS declarations into strongly-typed [ComponentStyle] properties
 * during SCSS/CSS compilation without exposing any string-keyed declaration API on [ComponentStyle].
 */
internal object CssDeclarationApplyUtil {

    private val IMPORTANT_REGEX = Regex("!\\s*important", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun fromDeclarations(
        declarations: Map<String, String>,
        isDarkTheme: Boolean = false
    ): ComponentStyle {
        val style = ComponentStyle()
        style.update {
            for ((k, v) in declarations) {
                applyDeclaration(this, k, v, isDarkTheme = isDarkTheme)
            }
        }
        return style
    }

    fun applyDeclaration(
        style: ComponentStyle,
        propertyName: String,
        rawValue: String,
        important: Boolean = false,
        isDarkTheme: Boolean = false
    ) {
        val propName = propertyName.trim().lowercase()
        if (propName.isEmpty()) return

        val hasBangImportant = rawValue.contains("!important", ignoreCase = true)
        val cleanVal = rawValue.replace(IMPORTANT_REGEX, "").trim()
        if (cleanVal.isEmpty()) return
        val isImp = important || hasBangImportant

        when (propName) {
            "background-color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setBackgroundColor(it, isImp) }
            "background" -> {
                val grad = Gradient.fromCss(cleanVal)
                if (grad != null) {
                    style.setBackgroundImage(CssBackgroundImage.fromGradient(grad), isImp)
                }
                val color = CssStyleSheet.extractColorFromBackgroundShorthand(cleanVal, isDarkTheme)
                    ?: resolveColor(cleanVal, isDarkTheme)
                if (color != null) {
                    style.setBackgroundColor(color, isImp)
                } else if (grad == null) {
                    CssBackgroundImage.parse(cleanVal)?.let { style.setBackgroundImage(it, isImp) }
                }
            }
            "background-image" -> CssBackgroundImage.parse(cleanVal)?.let { style.setBackgroundImage(it, isImp) }
            "background-size" -> CssBackgroundSize.parse(cleanVal)?.let { style.setBackgroundSize(it, isImp) }
            "background-position" -> CssBackgroundPosition.parse(cleanVal)?.let { style.setBackgroundPosition(it, isImp) }
            "background-repeat" -> CssBackgroundRepeat.fromCss(cleanVal)?.let { style.setBackgroundRepeat(it, isImp) }
            "background-attachment" -> CssBackgroundAttachment.fromCss(cleanVal)?.let { style.setBackgroundAttachment(it, isImp) }
            "background-clip", "-webkit-background-clip" -> CssBackgroundClip.fromCss(cleanVal)?.let { style.setBackgroundClip(it, isImp) }
            "background-origin" -> CssBackgroundOrigin.fromCss(cleanVal)?.let { style.setBackgroundOrigin(it, isImp) }
            "color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setTextColor(it, isImp) }
            "accent-color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setAccentColor(it, isImp) }
            "opacity" -> cleanVal.toFloatOrNull()?.let { style.setOpacity(it, isImp) }
            "display" -> CssDisplay.fromCss(cleanVal)?.let { style.setDisplay(it, isImp) }
            "visibility" -> CssVisibility.fromCss(cleanVal)?.let { style.setVisibility(it, isImp) }
            "position" -> CssPosition.fromCss(cleanVal)?.let { style.setPosition(it, isImp) }
            "inset" -> CssLength.parse(cleanVal)?.let {
                style.setTop(it, isImp)
                style.setRight(it, isImp)
                style.setBottom(it, isImp)
                style.setLeft(it, isImp)
            }
            "top" -> CssLength.parse(cleanVal)?.let { style.setTop(it, isImp) }
            "right" -> CssLength.parse(cleanVal)?.let { style.setRight(it, isImp) }
            "bottom" -> CssLength.parse(cleanVal)?.let { style.setBottom(it, isImp) }
            "left" -> CssLength.parse(cleanVal)?.let { style.setLeft(it, isImp) }
            "width" -> CssLength.parse(cleanVal)?.let { style.setWidth(it, isImp) }
            "height" -> CssLength.parse(cleanVal)?.let { style.setHeight(it, isImp) }
            "min-width" -> CssLength.parse(cleanVal)?.let { style.setMinWidth(it, isImp) }
            "min-height" -> CssLength.parse(cleanVal)?.let { style.setMinHeight(it, isImp) }
            "max-width" -> CssLength.parse(cleanVal)?.let { style.setMaxWidth(it, isImp) }
            "max-height" -> CssLength.parse(cleanVal)?.let { style.setMaxHeight(it, isImp) }
            "margin" -> {
                CssEdgeLengths.parse(cleanVal)?.let {
                    style.setMargin(it, isImp)
                    style.setMarginTop(it.top, isImp)
                    style.setMarginRight(it.right, isImp)
                    style.setMarginBottom(it.bottom, isImp)
                    style.setMarginLeft(it.left, isImp)
                }
            }
            "margin-top", "margin-block-start" -> CssLength.parse(cleanVal)?.let { style.setMarginTop(it, isImp) }
            "margin-right", "margin-inline-end" -> CssLength.parse(cleanVal)?.let { style.setMarginRight(it, isImp) }
            "margin-bottom", "margin-block-end" -> CssLength.parse(cleanVal)?.let { style.setMarginBottom(it, isImp) }
            "margin-left", "margin-inline-start" -> CssLength.parse(cleanVal)?.let { style.setMarginLeft(it, isImp) }
            "margin-inline" -> CssLength.parse(cleanVal)?.let {
                style.setMarginLeft(it, isImp)
                style.setMarginRight(it, isImp)
            }
            "margin-block" -> CssLength.parse(cleanVal)?.let {
                style.setMarginTop(it, isImp)
                style.setMarginBottom(it, isImp)
            }
            "padding" -> {
                CssEdgeLengths.parse(cleanVal)?.let {
                    style.setPadding(it, isImp)
                    style.setPaddingTop(it.top, isImp)
                    style.setPaddingRight(it.right, isImp)
                    style.setPaddingBottom(it.bottom, isImp)
                    style.setPaddingLeft(it.left, isImp)
                }
            }
            "padding-top", "padding-block-start" -> CssLength.parse(cleanVal)?.let { style.setPaddingTop(it, isImp) }
            "padding-right", "padding-inline-end" -> CssLength.parse(cleanVal)?.let { style.setPaddingRight(it, isImp) }
            "padding-bottom", "padding-block-end" -> CssLength.parse(cleanVal)?.let { style.setPaddingBottom(it, isImp) }
            "padding-left", "padding-inline-start" -> CssLength.parse(cleanVal)?.let { style.setPaddingLeft(it, isImp) }
            "padding-inline" -> CssLength.parse(cleanVal)?.let {
                style.setPaddingLeft(it, isImp)
                style.setPaddingRight(it, isImp)
            }
            "padding-block" -> CssLength.parse(cleanVal)?.let {
                style.setPaddingTop(it, isImp)
                style.setPaddingBottom(it, isImp)
            }
            "box-sizing" -> CssBoxSizing.fromCss(cleanVal)?.let { style.setBoxSizing(it, isImp) }
            "float" -> CssFloat.fromCss(cleanVal)?.let { style.setFloatSide(it, isImp) }
            "clear" -> CssClear.fromCss(cleanVal)?.let { style.setClearSide(it, isImp) }
            "border-width" -> CssEdgeLengths.parse(cleanVal)?.let { style.setBorderWidth(it, isImp) }
            "border-top-width" -> CssLength.parse(cleanVal)?.let { w ->
                val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                style.setBorderWidth(cur.copy(top = w), isImp)
            }
            "border-right-width" -> CssLength.parse(cleanVal)?.let { w ->
                val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                style.setBorderWidth(cur.copy(right = w), isImp)
            }
            "border-bottom-width" -> CssLength.parse(cleanVal)?.let { w ->
                val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                style.setBorderWidth(cur.copy(bottom = w), isImp)
            }
            "border-left-width" -> CssLength.parse(cleanVal)?.let { w ->
                val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                style.setBorderWidth(cur.copy(left = w), isImp)
            }
            "border-style" -> CssBorderStyle.fromCss(cleanVal)?.let { style.setBorderStyle(it, isImp) }
            "border-color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setBorderColor(it, isImp) }
            "border-top-color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setBorderTopColor(it, isImp) }
            "border-right-color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setBorderRightColor(it, isImp) }
            "border-bottom-color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setBorderBottomColor(it, isImp) }
            "border-left-color" -> resolveColor(cleanVal, isDarkTheme)?.let { style.setBorderLeftColor(it, isImp) }
            "border" -> parseBorderShorthand(style, cleanVal, isImp, isDarkTheme, side = null)
            "border-top" -> parseBorderShorthand(style, cleanVal, isImp, isDarkTheme, side = "top")
            "border-right" -> parseBorderShorthand(style, cleanVal, isImp, isDarkTheme, side = "right")
            "border-bottom" -> parseBorderShorthand(style, cleanVal, isImp, isDarkTheme, side = "bottom")
            "border-left" -> parseBorderShorthand(style, cleanVal, isImp, isDarkTheme, side = "left")
            "border-radius" -> CssCornerRadii.parse(cleanVal)?.let { style.setBorderRadius(it, isImp) }
            "border-top-left-radius" -> CssLength.parse(cleanVal)?.let { r ->
                val cur = style.getBorderRadius() ?: CssCornerRadii.ZERO
                style.setBorderRadius(cur.copy(topLeft = r), isImp)
            }
            "border-top-right-radius" -> CssLength.parse(cleanVal)?.let { r ->
                val cur = style.getBorderRadius() ?: CssCornerRadii.ZERO
                style.setBorderRadius(cur.copy(topRight = r), isImp)
            }
            "border-bottom-right-radius" -> CssLength.parse(cleanVal)?.let { r ->
                val cur = style.getBorderRadius() ?: CssCornerRadii.ZERO
                style.setBorderRadius(cur.copy(bottomRight = r), isImp)
            }
            "border-bottom-left-radius" -> CssLength.parse(cleanVal)?.let { r ->
                val cur = style.getBorderRadius() ?: CssCornerRadii.ZERO
                style.setBorderRadius(cur.copy(bottomLeft = r), isImp)
            }
            "outline" -> parseOutlineShorthand(style, cleanVal, isImp, isDarkTheme)
            "outline-width" -> CssLength.parse(cleanVal)?.let {
                style.setOutlineWidth(it, isImp)
                style.setFocusRingWidthPx(it.resolvePx(), isImp)
            }
            "outline-style" -> CssBorderStyle.fromCss(cleanVal)?.let { style.setOutlineStyle(it, isImp) }
            "outline-color" -> resolveColor(cleanVal, isDarkTheme)?.let {
                style.setOutlineColor(it, isImp)
                style.setFocusRingColor(it, isImp)
            }
            "outline-offset" -> CssLength.parse(cleanVal)?.let { style.setOutlineOffset(it, isImp) }
            "box-shadow", "-webkit-box-shadow" -> CssShadowList.parse(cleanVal)?.let { style.setBoxShadow(it, isImp) }
            "text-shadow" -> CssShadowList.parse(cleanVal)?.let { style.setTextShadow(it, isImp) }
            "font-family" -> CssFontFamily.parse(cleanVal)?.let { style.setFontFamily(it, isImp) }
            "font-size" -> CssLength.parse(cleanVal)?.let { style.setFontSize(it, isImp) }
            "font-weight" -> parseFontWeight(style, cleanVal, isImp)
            "font-style" -> CssFontStyle.fromCss(cleanVal)?.let { style.setFontStyle(it, isImp) }
            "line-height" -> CssLength.parse(cleanVal)?.let { style.setLineHeight(it, isImp) }
            "letter-spacing" -> CssLength.parse(cleanVal)?.let { style.setLetterSpacing(it, isImp) }
            "text-align" -> parseTextAlign(style, cleanVal, isImp)
            "text-decoration" -> CssTextDecoration.fromCss(cleanVal)?.let { style.setTextDecoration(it, isImp) }
            "text-transform" -> parseTextTransform(style, cleanVal, isImp)
            "text-overflow" -> parseTextOverflow(style, cleanVal, isImp)
            "white-space" -> CssWhiteSpace.fromCss(cleanVal)?.let { style.setWhiteSpace(it, isImp) }
            "word-break" -> CssWordBreak.fromCss(cleanVal)?.let { style.setWordBreak(it, isImp) }
            "vertical-align" -> CssVerticalAlign.fromCss(cleanVal)?.let { style.setVerticalAlign(it, isImp) }
            "flex-direction" -> parseFlexDirection(style, cleanVal, isImp)
            "flex-wrap" -> parseFlexWrap(style, cleanVal, isImp)
            "justify-content" -> parseJustifyContent(style, cleanVal, isImp)
            "align-items" -> parseAlignItems(style, cleanVal, isImp)
            "align-self" -> parseAlignSelf(style, cleanVal, isImp)
            "align-content" -> CssAlignContent.fromCss(cleanVal)?.let { style.setAlignContent(it, isImp) }
            "flex" -> parseFlexShorthand(style, cleanVal, isImp)
            "flex-grow" -> cleanVal.toFloatOrNull()?.let { style.setFlexGrow(it, isImp) }
            "flex-shrink" -> cleanVal.toFloatOrNull()?.let { style.setFlexShrink(it, isImp) }
            "flex-basis" -> CssLength.parse(cleanVal)?.let { style.setFlexBasis(it, isImp) }
            "order" -> cleanVal.toIntOrNull()?.let { style.setOrder(it, isImp) }
            "gap" -> {
                val parts = cleanVal.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                val first = CssLength.parse(parts.getOrNull(0))
                val second = CssLength.parse(parts.getOrNull(1)) ?: first
                if (first != null) {
                    style.setGap(first, isImp)
                    style.setRowGap(first, isImp)
                }
                if (second != null) {
                    style.setColumnGap(second, isImp)
                }
            }
            "row-gap" -> CssLength.parse(cleanVal)?.let { style.setRowGap(it, isImp) }
            "column-gap" -> CssLength.parse(cleanVal)?.let { style.setColumnGap(it, isImp) }
            "overflow" -> parseOverflow(cleanVal)?.let {
                style.setOverflow(it, isImp)
                style.setOverflowX(it, isImp)
                style.setOverflowY(it, isImp)
            }
            "overflow-x" -> parseOverflow(cleanVal)?.let { style.setOverflowX(it, isImp) }
            "overflow-y" -> parseOverflow(cleanVal)?.let { style.setOverflowY(it, isImp) }
            "cursor" -> (CssCustomCursor.parse(cleanVal) ?: CssCursor.fromCss(cleanVal))?.let { style.setCursor(it, isImp) }
            "pointer-events" -> CssPointerEvents.fromCss(cleanVal)?.let { style.setPointerEvents(it, isImp) }
            "user-select", "-webkit-user-select" -> CssUserSelect.fromCss(cleanVal)?.let { style.setUserSelect(it, isImp) }
            "object-fit" -> parseObjectFit(style, cleanVal, isImp)
            "aspect-ratio" -> CssAspectRatio.parse(cleanVal)?.let { style.setAspectRatio(it, isImp) }
            "--bs-aspect-ratio" -> {
                val pct = cleanVal.removeSuffix("%").trim().toFloatOrNull()
                if (pct != null && pct > 0f) {
                    style.setAspectRatio(CssAspectRatio.of(100f, pct), isImp)
                }
            }
            "z-index" -> cleanVal.toIntOrNull()?.let { style.setZIndex(it, isImp) }
            "transform" -> CssTransform2D.parse(cleanVal)?.let { style.setTransform(it, isImp) }
            "transform-origin" -> CssBackgroundPosition.parse(cleanVal)?.let { style.setTransformOrigin(it, isImp) }
            "transition" -> {
                val specs = com.nexustools.genericui.style.transition.CssTransitionSpec.parseList(cleanVal)
                if (specs.isNotEmpty()) style.setTransitions(specs, isImp)
            }
            "filter" -> CssFilter.parse(cleanVal)?.let { style.setFilter(it, isImp) }
            "backdrop-filter", "-webkit-backdrop-filter" -> CssFilter.parse(cleanVal)?.let { style.setBackdropFilter(it, isImp) }
        }
    }

    private fun resolveColor(rawValue: String, isDarkTheme: Boolean): RgbaColor? =
        CssStyleSheet.resolveCssColorValue(rawValue, isDarkTheme) ?: RgbaColor.fromCss(rawValue)

    private fun parseBorderShorthand(
        style: ComponentStyle,
        raw: String,
        important: Boolean,
        isDarkTheme: Boolean,
        side: String?
    ) {
        val trimmed = raw.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed == "0") {
            when (side) {
                "top" -> {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(top = CssLength.ZERO), important)
                }
                "right" -> {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(right = CssLength.ZERO), important)
                }
                "bottom" -> {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(bottom = CssLength.ZERO), important)
                }
                "left" -> {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(left = CssLength.ZERO), important)
                }
                else -> {
                    style.setBorderStyle(CssBorderStyle.NONE, important)
                    style.setBorderWidth(CssEdgeLengths.ZERO, important)
                }
            }
            return
        }

        val tokens = splitTokensPreservingFunctions(trimmed)
        var parsedWidth: CssLength? = null
        var parsedStyle: CssBorderStyle? = null
        var parsedColor: RgbaColor? = null

        for (tok in tokens) {
            val st = CssBorderStyle.fromCss(tok)
            if (st != null) {
                parsedStyle = st
                continue
            }
            val len = CssLength.parse(tok)
            if (len != null && (tok.any { it.isDigit() })) {
                parsedWidth = len
                continue
            }
            val col = resolveColor(tok, isDarkTheme)
            if (col != null) {
                parsedColor = col
            }
        }

        when (side) {
            "top" -> {
                if (parsedWidth != null) {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(top = parsedWidth), important)
                }
                if (parsedStyle != null && style.getBorderStyle() == null) style.setBorderStyle(parsedStyle, important)
                if (parsedColor != null) style.setBorderTopColor(parsedColor, important)
            }
            "right" -> {
                if (parsedWidth != null) {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(right = parsedWidth), important)
                }
                if (parsedStyle != null && style.getBorderStyle() == null) style.setBorderStyle(parsedStyle, important)
                if (parsedColor != null) style.setBorderRightColor(parsedColor, important)
            }
            "bottom" -> {
                if (parsedWidth != null) {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(bottom = parsedWidth), important)
                }
                if (parsedStyle != null && style.getBorderStyle() == null) style.setBorderStyle(parsedStyle, important)
                if (parsedColor != null) style.setBorderBottomColor(parsedColor, important)
            }
            "left" -> {
                if (parsedWidth != null) {
                    val cur = style.getBorderWidth() ?: CssEdgeLengths.ZERO
                    style.setBorderWidth(cur.copy(left = parsedWidth), important)
                }
                if (parsedStyle != null && style.getBorderStyle() == null) style.setBorderStyle(parsedStyle, important)
                if (parsedColor != null) style.setBorderLeftColor(parsedColor, important)
            }
            else -> {
                if (parsedWidth != null) style.setBorderWidth(CssEdgeLengths.all(parsedWidth), important)
                if (parsedStyle != null) style.setBorderStyle(parsedStyle, important)
                if (parsedColor != null) style.setBorderColor(parsedColor, important)
            }
        }
    }

    private fun parseOutlineShorthand(
        style: ComponentStyle,
        raw: String,
        important: Boolean,
        isDarkTheme: Boolean
    ) {
        val trimmed = raw.trim()
        if (trimmed.equals("none", ignoreCase = true) || trimmed == "0") {
            style.setOutlineStyle(CssBorderStyle.NONE, important)
            style.setOutlineWidth(CssLength.ZERO, important)
            style.setFocusRingWidthPx(0f, important)
            return
        }
        val tokens = splitTokensPreservingFunctions(trimmed)
        for (tok in tokens) {
            val st = CssBorderStyle.fromCss(tok)
            if (st != null) {
                style.setOutlineStyle(st, important)
                continue
            }
            val len = CssLength.parse(tok)
            if (len != null && tok.any { it.isDigit() }) {
                style.setOutlineWidth(len, important)
                style.setFocusRingWidthPx(len.resolvePx(), important)
                continue
            }
            val col = resolveColor(tok, isDarkTheme)
            if (col != null) {
                style.setOutlineColor(col, important)
                style.setFocusRingColor(col, important)
            }
        }
    }

    private fun parseFontWeight(style: ComponentStyle, raw: String, important: Boolean) {
        when (val lower = raw.trim().lowercase()) {
            "normal" -> style.setFontWeight(FontWeight.NORMAL, important)
            "bold" -> style.setFontWeight(FontWeight.BOLD, important)
            "lighter" -> style.setFontWeight(FontWeight.LIGHT, important)
            "bolder" -> style.setFontWeight(FontWeight.BOLDER, important)
            else -> {
                val num = lower.toIntOrNull()
                if (num != null) {
                    val matched = FontWeight.entries.firstOrNull { it.numericWeight == num }
                    if (matched != null) {
                        style.setFontWeight(matched, important)
                    } else {
                        style.fontWeightNumeric = num
                        if (important) style.markImportant(CssProperty.FONT_WEIGHT, true)
                    }
                }
            }
        }
    }

    private fun parseTextAlign(style: ComponentStyle, raw: String, important: Boolean) {
        val align = when (raw.trim().lowercase()) {
            "left", "start" -> TextAlignment.START
            "right", "end" -> TextAlignment.END
            "center" -> TextAlignment.CENTER
            "justify" -> TextAlignment.JUSTIFY
            else -> null
        }
        if (align != null) style.setTextAlign(align, important)
    }

    private fun parseTextTransform(style: ComponentStyle, raw: String, important: Boolean) {
        val tf = when (raw.trim().lowercase()) {
            "lowercase" -> TextTransform.LOWERCASE
            "uppercase" -> TextTransform.UPPERCASE
            "capitalize" -> TextTransform.CAPITALIZE
            else -> null
        }
        if (tf != null) style.setTextTransform(tf, important)
    }

    private fun parseTextOverflow(style: ComponentStyle, raw: String, important: Boolean) {
        val to = when (raw.trim().lowercase()) {
            "ellipsis" -> TextOverflow.ELLIPSIS
            "clip" -> TextOverflow.WRAP
            else -> null
        }
        if (to != null) style.setTextOverflow(to, important)
    }

    private fun parseFlexDirection(style: ComponentStyle, raw: String, important: Boolean) {
        val dir = when (raw.trim().lowercase()) {
            "row" -> FlexDirection.ROW
            "row-reverse" -> FlexDirection.ROW_REVERSE
            "column" -> FlexDirection.COLUMN
            "column-reverse" -> FlexDirection.COLUMN_REVERSE
            else -> null
        }
        if (dir != null) style.setFlexDirection(dir, important)
    }

    private fun parseFlexWrap(style: ComponentStyle, raw: String, important: Boolean) {
        val wrap = when (raw.trim().lowercase()) {
            "wrap" -> FlexWrap.WRAP
            "nowrap" -> FlexWrap.NOWRAP
            "wrap-reverse" -> FlexWrap.WRAP_REVERSE
            else -> null
        }
        if (wrap != null) style.setFlexWrap(wrap, important)
    }

    private fun parseJustifyContent(style: ComponentStyle, raw: String, important: Boolean) {
        val jc = when (raw.trim().lowercase()) {
            "flex-start", "start", "left" -> JustifyContent.START
            "flex-end", "end", "right" -> JustifyContent.END
            "center" -> JustifyContent.CENTER
            "space-between", "between" -> JustifyContent.BETWEEN
            "space-around", "around" -> JustifyContent.AROUND
            "space-evenly", "evenly" -> JustifyContent.EVENLY
            else -> null
        }
        if (jc != null) style.setJustifyContent(jc, important)
    }

    private fun parseAlignItems(style: ComponentStyle, raw: String, important: Boolean) {
        val ai = when (raw.trim().lowercase()) {
            "flex-start", "start", "self-start" -> AlignItems.START
            "flex-end", "end", "self-end" -> AlignItems.END
            "center" -> AlignItems.CENTER
            "baseline" -> AlignItems.BASELINE
            "stretch" -> AlignItems.STRETCH
            else -> null
        }
        if (ai != null) style.setAlignItems(ai, important)
    }

    private fun parseAlignSelf(style: ComponentStyle, raw: String, important: Boolean) {
        val asf = when (raw.trim().lowercase()) {
            "auto" -> AlignSelf.AUTO
            "flex-start", "start", "self-start" -> AlignSelf.START
            "flex-end", "end", "self-end" -> AlignSelf.END
            "center" -> AlignSelf.CENTER
            "baseline" -> AlignSelf.BASELINE
            "stretch" -> AlignSelf.STRETCH
            else -> null
        }
        if (asf != null) style.setAlignSelf(asf, important)
    }

    private fun parseFlexShorthand(style: ComponentStyle, raw: String, important: Boolean) {
        val cleaned = raw.trim()
        when (cleaned.lowercase()) {
            "none" -> {
                style.setFlexGrow(0f, important)
                style.setFlexShrink(0f, important)
                style.setFlexBasis(CssLength.AUTO, important)
                return
            }
            "auto" -> {
                style.setFlexGrow(1f, important)
                style.setFlexShrink(1f, important)
                style.setFlexBasis(CssLength.AUTO, important)
                return
            }
            "initial" -> {
                style.setFlexGrow(0f, important)
                style.setFlexShrink(1f, important)
                style.setFlexBasis(CssLength.AUTO, important)
                return
            }
        }
        val tokens = cleaned.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        when (tokens.size) {
            1 -> {
                val g = tokens[0].toFloatOrNull()
                if (g != null) {
                    style.setFlexGrow(g, important)
                    style.setFlexShrink(1f, important)
                    style.setFlexBasis(CssLength.percent(0f), important)
                } else {
                    CssLength.parse(tokens[0])?.let {
                        style.setFlexGrow(1f, important)
                        style.setFlexShrink(1f, important)
                        style.setFlexBasis(it, important)
                    }
                }
            }
            2 -> {
                tokens[0].toFloatOrNull()?.let { style.setFlexGrow(it, important) }
                val secondNum = tokens[1].toFloatOrNull()
                if (secondNum != null && !tokens[1].endsWith("px") && !tokens[1].endsWith("%")) {
                    style.setFlexShrink(secondNum, important)
                    style.setFlexBasis(CssLength.percent(0f), important)
                } else {
                    style.setFlexShrink(1f, important)
                    CssLength.parse(tokens[1])?.let { style.setFlexBasis(it, important) }
                }
            }
            3 -> {
                tokens[0].toFloatOrNull()?.let { style.setFlexGrow(it, important) }
                tokens[1].toFloatOrNull()?.let { style.setFlexShrink(it, important) }
                CssLength.parse(tokens[2])?.let { style.setFlexBasis(it, important) }
            }
        }
    }

    private fun parseOverflow(raw: String): OverflowBehavior? = when (raw.trim().lowercase()) {
        "auto" -> OverflowBehavior.AUTO
        "hidden", "clip" -> OverflowBehavior.HIDDEN
        "visible" -> OverflowBehavior.VISIBLE
        "scroll" -> OverflowBehavior.SCROLL
        else -> null
    }

    private fun parseObjectFit(style: ComponentStyle, raw: String, important: Boolean) {
        val fit = when (raw.trim().lowercase()) {
            "contain" -> ObjectFit.CONTAIN
            "cover" -> ObjectFit.COVER
            "fill" -> ObjectFit.FILL
            "none" -> ObjectFit.NONE
            "scale-down" -> ObjectFit.SCALE_DOWN
            else -> null
        }
        if (fit != null) style.setObjectFit(fit, important)
    }

    private fun splitTokensPreservingFunctions(input: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (ch in input) {
            when {
                ch == '(' -> {
                    depth++
                    sb.append(ch)
                }
                ch == ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    sb.append(ch)
                }
                ch.isWhitespace() && depth == 0 -> {
                    if (sb.isNotEmpty()) {
                        result.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }
}
