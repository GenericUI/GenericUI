package com.nexustools.genericui.style.sass

import com.nexustools.genericui.style.css.CompiledSelectorSegment
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.CssCombinator
import com.nexustools.genericui.style.css.CssProperty
import com.nexustools.genericui.style.css.CssRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet

/**
 * Compiles `.scss` / `.sass` / `.css` source text into a [PrecompiledStyleSheet] with pre-parsed
 * [CompiledStyleRule]s (including pre-tokenized [CompiledSelectorSegment] chains and typed
 * [com.nexustools.genericui.style.css.ComponentStyle] objects) and can also generate Kotlin source
 * code (`.kt`) for build-time precompilation.
 */
object SassKotlinPrecompiler {

    /**
     * Compiles [scssSource] into an in-memory [PrecompiledStyleSheet] whose selector chains and
     * [com.nexustools.genericui.style.css.ComponentStyle] declarations are pre-evaluated once.
     */
    fun precompile(
        scssSource: String,
        name: String = "stylesheet",
        resourcePath: String = "$name.css",
        variables: Map<String, String> = emptyMap(),
        imports: Map<String, String> = emptyMap()
    ): PrecompiledStyleSheet {
        val varHeader = if (variables.isEmpty()) {
            ""
        } else {
            variables.entries.joinToString("\n") { (k, v) ->
                val varName = if (k.startsWith("$")) k else "\$$k"
                "$varName: $v;"
            } + "\n"
        }
        val importHeader = if (imports.isEmpty()) {
            ""
        } else {
            imports.values.joinToString("\n") + "\n"
        }
        val compiledCss = SassCompiler.compileToCss(importHeader + varHeader + scssSource)
        val parsedSheet = CssStyleSheet.parse(compiledCss)
        val compiledRules = parsedSheet.rules.mapIndexed { idx, rule ->
            compileRule(rule, idx)
        }
        val keyframesMap = parsedSheet.keyframes
        val precompiledSheet = CssStyleSheet.precompiledFromCompiledRules(
            compiledRules = compiledRules,
            keyframes = keyframesMap,
            rawCss = compiledCss
        )
        return object : PrecompiledStyleSheet {
            override val name: String = name
            override val cssResourcePath: String = resourcePath
            override val rawCss: String = compiledCss
            override val compiledRules: List<CompiledStyleRule> = compiledRules
            override val keyframes: Map<String, Map<String, Map<String, String>>> = keyframesMap
            override val styleSheet: CssStyleSheet = precompiledSheet
        }
    }

    /**
     * Converts a parsed [CssRule] into a [CompiledStyleRule] with pre-parsed selector segments
     * and a typed [com.nexustools.genericui.style.css.ComponentStyle].
     */
    fun compileRule(rule: CssRule, sourceOrder: Int = 0): CompiledStyleRule {
        val segments = CompiledSelectorSegment.parseChain(rule.selector)
        val style = CssDeclarationApplyUtil.fromDeclarations(rule.declarations)
        for (impProp in rule.importantProps) {
            CssProperty.fromCssName(impProp)?.let { style.markImportant(it, true) }
        }
        return CompiledStyleRule(
            selector = rule.selector,
            specificity = rule.specificity,
            sourceOrder = sourceOrder,
            style = style,
            declarations = rule.cleanedDeclarations,
            segments = segments,
            mediaMinWidthPx = rule.mediaMinWidthPx,
            mediaMaxWidthPx = rule.mediaMaxWidthPx
        )
    }

    /**
     * Precompiles a list of [CompiledStyleRule]s directly into a [CssStyleSheet] without re-parsing
     * selector strings at runtime.
     */
    fun toCssStyleSheet(
        compiledRules: List<CompiledStyleRule>,
        keyframes: Map<String, Map<String, Map<String, String>>> = emptyMap(),
        rawCss: String = ""
    ): CssStyleSheet = CssStyleSheet.precompiledFromCompiledRules(
        compiledRules = compiledRules,
        keyframes = keyframes,
        rawCss = rawCss
    )

    /**
     * Generates a Kotlin source file (`<objectName>.kt`) implementing [PrecompiledStyleSheet]
     * from [scssSource], suitable for build-time SCSS -> Kotlin code generation.
     */
    fun generateKotlinSource(
        scssSource: String,
        packageName: String,
        objectName: String,
        styleName: String = objectName,
        cssResourcePath: String = "styles/$styleName.css",
        variables: Map<String, String> = emptyMap(),
        imports: Map<String, String> = emptyMap()
    ): String {
        val precompiled = precompile(
            scssSource = scssSource,
            name = styleName,
            resourcePath = cssResourcePath,
            variables = variables,
            imports = imports
        )
        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import com.nexustools.genericui.style.color.RgbaColor")
        sb.appendLine("import com.nexustools.genericui.style.css.CompiledSelectorSegment")
        sb.appendLine("import com.nexustools.genericui.style.css.CompiledStyleRule")
        sb.appendLine("import com.nexustools.genericui.style.css.ComponentStyle")
        sb.appendLine("import com.nexustools.genericui.style.css.CssAspectRatio")
        sb.appendLine("import com.nexustools.genericui.style.css.CssBackgroundImage")
        sb.appendLine("import com.nexustools.genericui.style.css.CssBackgroundPosition")
        sb.appendLine("import com.nexustools.genericui.style.css.CssBackgroundSize")
        sb.appendLine("import com.nexustools.genericui.style.css.CssCombinator")
        sb.appendLine("import com.nexustools.genericui.style.css.CssCornerRadii")
        sb.appendLine("import com.nexustools.genericui.style.css.CssCustomCursor")
        sb.appendLine("import com.nexustools.genericui.style.css.CssEdgeLengths")
        sb.appendLine("import com.nexustools.genericui.style.css.CssFilter")
        sb.appendLine("import com.nexustools.genericui.style.css.CssFontFamily")
        sb.appendLine("import com.nexustools.genericui.style.css.CssLength")
        sb.appendLine("import com.nexustools.genericui.style.css.CssProperty")
        sb.appendLine("import com.nexustools.genericui.style.css.CssShadowList")
        sb.appendLine("import com.nexustools.genericui.style.css.CssStyleSheet")
        sb.appendLine("import com.nexustools.genericui.style.css.PrecompiledStyleSheet")
        sb.appendLine("import com.nexustools.genericui.style.transition.CssTransform2D")
        sb.appendLine("import com.nexustools.genericui.style.transition.CssTransitionController")
        sb.appendLine()
        sb.appendLine("object $objectName : PrecompiledStyleSheet {")
        sb.appendLine("    override val name: String = ${escapeKotlinString(precompiled.name)}")
        sb.appendLine("    override val cssResourcePath: String = ${escapeKotlinString(precompiled.cssResourcePath)}")
        sb.appendLine("    override val rawCss: String = ${escapeKotlinString(precompiled.rawCss)}")
        sb.appendLine("    override val compiledRules: List<CompiledStyleRule> = listOf(")
        for ((idx, rule) in precompiled.compiledRules.withIndex()) {
            val comma = if (idx < precompiled.compiledRules.lastIndex) "," else ""
            sb.appendLine("        CompiledStyleRule(")
            sb.appendLine("            selector = ${escapeKotlinString(rule.selector)},")
            sb.appendLine("            specificity = ${rule.specificity},")
            sb.appendLine("            sourceOrder = ${rule.sourceOrder},")
            sb.appendLine("            mediaMinWidthPx = ${rule.mediaMinWidthPx ?: "null"},")
            sb.appendLine("            mediaMaxWidthPx = ${rule.mediaMaxWidthPx ?: "null"},")
            sb.appendLine("            segments = listOf(")
            for ((sIdx, seg) in rule.segments.withIndex()) {
                val sComma = if (sIdx < rule.segments.lastIndex) "," else ""
                val classesLiteral = if (seg.classes.isEmpty()) "emptySet()" else "setOf(${seg.classes.joinToString(", ") { escapeKotlinString(it) }})"
                val pseudosLiteral = if (seg.pseudoClasses.isEmpty()) "emptySet()" else "setOf(${seg.pseudoClasses.joinToString(", ") { escapeKotlinString(it) }})"
                val combLiteral = seg.combinatorToNext?.let { "CssCombinator.${it.name}" } ?: "null"
                sb.appendLine("                CompiledSelectorSegment(tagName = ${seg.tagName?.let { escapeKotlinString(it) } ?: "null"}, id = ${seg.id?.let { escapeKotlinString(it) } ?: "null"}, classes = $classesLiteral, pseudoClasses = $pseudosLiteral, combinatorToNext = $combLiteral)$sComma")
            }
            sb.appendLine("            ),")
            sb.appendLine("            style = ComponentStyle().apply {")
            emitTypedStyleCalls(sb, rule.style)
            sb.appendLine("            }")
            sb.appendLine("        )$comma")
        }
        sb.appendLine("    )")
        sb.appendLine("    override val styleSheet: CssStyleSheet by lazy {")
        sb.appendLine("        CssStyleSheet.precompiledFromCompiledRules(compiledRules, keyframes, rawCss)")
        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun emitTypedStyleCalls(sb: StringBuilder, s: com.nexustools.genericui.style.css.ComponentStyle) {
        fun colorLiteral(c: com.nexustools.genericui.style.color.RgbaColor): String =
            "RgbaColor(${c.r}, ${c.g}, ${c.b}, ${c.a})"

        s.getBackgroundColor()?.let {
            sb.appendLine("                setBackgroundColor(${colorLiteral(it)}, ${s.isImportant(CssProperty.BACKGROUND_COLOR)})")
        }
        s.getBackgroundImage()?.let {
            sb.appendLine("                CssBackgroundImage.parse(${escapeKotlinString(it.toCssString())})?.let { img -> setBackgroundImage(img, ${s.isImportant(CssProperty.BACKGROUND_IMAGE)}) }")
        }
        s.getBackgroundSize()?.let {
            sb.appendLine("                CssBackgroundSize.parse(${escapeKotlinString(it.toCssString())})?.let { sz -> setBackgroundSize(sz, ${s.isImportant(CssProperty.BACKGROUND_SIZE)}) }")
        }
        s.getBackgroundPosition()?.let {
            sb.appendLine("                CssBackgroundPosition.parse(${escapeKotlinString(it.toCssString())})?.let { pos -> setBackgroundPosition(pos, ${s.isImportant(CssProperty.BACKGROUND_POSITION)}) }")
        }
        s.getBackgroundRepeat()?.let {
            sb.appendLine("                setBackgroundRepeat(com.nexustools.genericui.enums.css.CssBackgroundRepeat.${it.name}, ${s.isImportant(CssProperty.BACKGROUND_REPEAT)})")
        }
        s.getBackgroundAttachment()?.let {
            sb.appendLine("                setBackgroundAttachment(com.nexustools.genericui.enums.css.CssBackgroundAttachment.${it.name}, ${s.isImportant(CssProperty.BACKGROUND_ATTACHMENT)})")
        }
        s.getBackgroundClip()?.let {
            sb.appendLine("                setBackgroundClip(com.nexustools.genericui.enums.css.CssBackgroundClip.${it.name}, ${s.isImportant(CssProperty.BACKGROUND_CLIP)})")
        }
        s.getBackgroundOrigin()?.let {
            sb.appendLine("                setBackgroundOrigin(com.nexustools.genericui.enums.css.CssBackgroundOrigin.${it.name}, ${s.isImportant(CssProperty.BACKGROUND_ORIGIN)})")
        }
        s.getColor()?.let {
            sb.appendLine("                setTextColor(${colorLiteral(it)}, ${s.isImportant(CssProperty.COLOR)})")
        }
        s.getAccentColor()?.let {
            sb.appendLine("                setAccentColor(${colorLiteral(it)}, ${s.isImportant(CssProperty.ACCENT_COLOR)})")
        }
        s.getOpacity()?.let {
            sb.appendLine("                setOpacity(${it}f, ${s.isImportant(CssProperty.OPACITY)})")
        }
        s.getDisplay()?.let {
            sb.appendLine("                setDisplay(com.nexustools.genericui.enums.css.CssDisplay.${it.name}, ${s.isImportant(CssProperty.DISPLAY)})")
        }
        s.getVisibility()?.let {
            sb.appendLine("                setVisibility(com.nexustools.genericui.enums.css.CssVisibility.${it.name}, ${s.isImportant(CssProperty.VISIBILITY)})")
        }
        s.getPosition()?.let {
            sb.appendLine("                setPosition(com.nexustools.genericui.enums.css.CssPosition.${it.name}, ${s.isImportant(CssProperty.POSITION)})")
        }
        s.getTop()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setTop(l, ${s.isImportant(CssProperty.TOP)}) }")
        }
        s.getRight()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setRight(l, ${s.isImportant(CssProperty.RIGHT)}) }")
        }
        s.getBottom()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setBottom(l, ${s.isImportant(CssProperty.BOTTOM)}) }")
        }
        s.getLeft()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setLeft(l, ${s.isImportant(CssProperty.LEFT)}) }")
        }
        s.getWidth()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setWidth(l, ${s.isImportant(CssProperty.WIDTH)}) }")
        }
        s.getHeight()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setHeight(l, ${s.isImportant(CssProperty.HEIGHT)}) }")
        }
        s.getMinWidth()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMinWidth(l, ${s.isImportant(CssProperty.MIN_WIDTH)}) }")
        }
        s.getMinHeight()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMinHeight(l, ${s.isImportant(CssProperty.MIN_HEIGHT)}) }")
        }
        s.getMaxWidth()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMaxWidth(l, ${s.isImportant(CssProperty.MAX_WIDTH)}) }")
        }
        s.getMaxHeight()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMaxHeight(l, ${s.isImportant(CssProperty.MAX_HEIGHT)}) }")
        }
        s.getMargin()?.let {
            sb.appendLine("                CssEdgeLengths.parse(${escapeKotlinString(it.toCssString())})?.let { e -> setMargin(e, ${s.isImportant(CssProperty.MARGIN)}) }")
        }
        s.marginTop?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMarginTop(l, ${s.isImportant(CssProperty.MARGIN_TOP)}) }")
        }
        s.marginRight?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMarginRight(l, ${s.isImportant(CssProperty.MARGIN_RIGHT)}) }")
        }
        s.marginBottom?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMarginBottom(l, ${s.isImportant(CssProperty.MARGIN_BOTTOM)}) }")
        }
        s.marginLeft?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setMarginLeft(l, ${s.isImportant(CssProperty.MARGIN_LEFT)}) }")
        }
        s.getPadding()?.let {
            sb.appendLine("                CssEdgeLengths.parse(${escapeKotlinString(it.toCssString())})?.let { e -> setPadding(e, ${s.isImportant(CssProperty.PADDING)}) }")
        }
        s.paddingTop?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setPaddingTop(l, ${s.isImportant(CssProperty.PADDING_TOP)}) }")
        }
        s.paddingRight?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setPaddingRight(l, ${s.isImportant(CssProperty.PADDING_RIGHT)}) }")
        }
        s.paddingBottom?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setPaddingBottom(l, ${s.isImportant(CssProperty.PADDING_BOTTOM)}) }")
        }
        s.paddingLeft?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setPaddingLeft(l, ${s.isImportant(CssProperty.PADDING_LEFT)}) }")
        }
        s.getBoxSizing()?.let {
            sb.appendLine("                setBoxSizing(com.nexustools.genericui.enums.css.CssBoxSizing.${it.name}, ${s.isImportant(CssProperty.BOX_SIZING)})")
        }
        s.getBorderWidth()?.let {
            sb.appendLine("                CssEdgeLengths.parse(${escapeKotlinString(it.toCssString())})?.let { e -> setBorderWidth(e, ${s.isImportant(CssProperty.BORDER_WIDTH)}) }")
        }
        s.getBorderStyle()?.let {
            sb.appendLine("                setBorderStyle(com.nexustools.genericui.enums.css.CssBorderStyle.${it.name}, ${s.isImportant(CssProperty.BORDER_STYLE)})")
        }
        s.getBorderColor()?.let {
            sb.appendLine("                setBorderColor(${colorLiteral(it)}, ${s.isImportant(CssProperty.BORDER_COLOR)})")
        }
        s.getBorderRadius()?.let {
            sb.appendLine("                CssCornerRadii.parse(${escapeKotlinString(it.toCssString())})?.let { r -> setBorderRadius(r, ${s.isImportant(CssProperty.BORDER_RADIUS)}) }")
        }
        s.getBoxShadow()?.let {
            sb.appendLine("                CssShadowList.parse(${escapeKotlinString(it.toCssString())})?.let { sh -> setBoxShadow(sh, ${s.isImportant(CssProperty.BOX_SHADOW)}) }")
        }
        s.getTextShadow()?.let {
            sb.appendLine("                CssShadowList.parse(${escapeKotlinString(it.toCssString())})?.let { sh -> setTextShadow(sh, ${s.isImportant(CssProperty.TEXT_SHADOW)}) }")
        }
        s.getFontFamily()?.let {
            sb.appendLine("                CssFontFamily.parse(${escapeKotlinString(it.toCssString())})?.let { ff -> setFontFamily(ff, ${s.isImportant(CssProperty.FONT_FAMILY)}) }")
        }
        s.getFontSize()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setFontSize(l, ${s.isImportant(CssProperty.FONT_SIZE)}) }")
        }
        s.getFontWeight()?.let {
            sb.appendLine("                setFontWeight(com.nexustools.genericui.enums.bootstrap.FontWeight.${it.name}, ${s.isImportant(CssProperty.FONT_WEIGHT)})")
        } ?: s.fontWeightNumeric?.let {
            sb.appendLine("                fontWeightNumeric = $it")
            if (s.isImportant(CssProperty.FONT_WEIGHT)) {
                sb.appendLine("                markImportant(CssProperty.FONT_WEIGHT, true)")
            }
        }
        s.getFontStyle()?.let {
            sb.appendLine("                setFontStyle(com.nexustools.genericui.enums.css.CssFontStyle.${it.name}, ${s.isImportant(CssProperty.FONT_STYLE)})")
        }
        s.getLineHeight()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setLineHeight(l, ${s.isImportant(CssProperty.LINE_HEIGHT)}) }")
        }
        s.getLetterSpacing()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setLetterSpacing(l, ${s.isImportant(CssProperty.LETTER_SPACING)}) }")
        }
        s.getTextAlign()?.let {
            sb.appendLine("                setTextAlign(com.nexustools.genericui.enums.bootstrap.TextAlignment.${it.name}, ${s.isImportant(CssProperty.TEXT_ALIGN)})")
        }
        s.getTextDecoration()?.let {
            sb.appendLine("                setTextDecoration(com.nexustools.genericui.enums.css.CssTextDecoration.${it.name}, ${s.isImportant(CssProperty.TEXT_DECORATION)})")
        }
        s.getTextTransform()?.let {
            sb.appendLine("                setTextTransform(com.nexustools.genericui.enums.bootstrap.TextTransform.${it.name}, ${s.isImportant(CssProperty.TEXT_TRANSFORM)})")
        }
        s.getTextOverflow()?.let {
            sb.appendLine("                setTextOverflow(com.nexustools.genericui.enums.bootstrap.TextOverflow.${it.name}, ${s.isImportant(CssProperty.TEXT_OVERFLOW)})")
        }
        s.getWhiteSpace()?.let {
            sb.appendLine("                setWhiteSpace(com.nexustools.genericui.enums.css.CssWhiteSpace.${it.name}, ${s.isImportant(CssProperty.WHITE_SPACE)})")
        }
        s.getWordBreak()?.let {
            sb.appendLine("                setWordBreak(com.nexustools.genericui.enums.css.CssWordBreak.${it.name}, ${s.isImportant(CssProperty.WORD_BREAK)})")
        }
        s.getFlexDirection()?.let {
            sb.appendLine("                setFlexDirection(com.nexustools.genericui.enums.bootstrap.FlexDirection.${it.name}, ${s.isImportant(CssProperty.FLEX_DIRECTION)})")
        }
        s.getFlexWrap()?.let {
            sb.appendLine("                setFlexWrap(com.nexustools.genericui.enums.bootstrap.FlexWrap.${it.name}, ${s.isImportant(CssProperty.FLEX_WRAP)})")
        }
        s.getJustifyContent()?.let {
            sb.appendLine("                setJustifyContent(com.nexustools.genericui.enums.bootstrap.JustifyContent.${it.name}, ${s.isImportant(CssProperty.JUSTIFY_CONTENT)})")
        }
        s.getAlignItems()?.let {
            sb.appendLine("                setAlignItems(com.nexustools.genericui.enums.bootstrap.AlignItems.${it.name}, ${s.isImportant(CssProperty.ALIGN_ITEMS)})")
        }
        s.getAlignSelf()?.let {
            sb.appendLine("                setAlignSelf(com.nexustools.genericui.enums.bootstrap.AlignSelf.${it.name}, ${s.isImportant(CssProperty.ALIGN_SELF)})")
        }
        s.getAlignContent()?.let {
            sb.appendLine("                setAlignContent(com.nexustools.genericui.enums.css.CssAlignContent.${it.name}, ${s.isImportant(CssProperty.ALIGN_CONTENT)})")
        }
        s.getFlexGrow()?.let {
            sb.appendLine("                setFlexGrow(${it}f, ${s.isImportant(CssProperty.FLEX_GROW)})")
        }
        s.getFlexShrink()?.let {
            sb.appendLine("                setFlexShrink(${it}f, ${s.isImportant(CssProperty.FLEX_SHRINK)})")
        }
        s.getFlexBasis()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setFlexBasis(l, ${s.isImportant(CssProperty.FLEX_BASIS)}) }")
        }
        s.getOrder()?.let {
            sb.appendLine("                setOrder($it, ${s.isImportant(CssProperty.ORDER)})")
        }
        s.getGap()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setGap(l, ${s.isImportant(CssProperty.GAP)}) }")
        }
        s.getRowGap()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setRowGap(l, ${s.isImportant(CssProperty.ROW_GAP)}) }")
        }
        s.getColumnGap()?.let {
            sb.appendLine("                CssLength.parse(${escapeKotlinString(it.toCssString())})?.let { l -> setColumnGap(l, ${s.isImportant(CssProperty.COLUMN_GAP)}) }")
        }
        s.getOverflow()?.let {
            sb.appendLine("                setOverflow(com.nexustools.genericui.enums.bootstrap.OverflowBehavior.${it.name}, ${s.isImportant(CssProperty.OVERFLOW)})")
        }
        s.getOverflowX()?.let {
            sb.appendLine("                setOverflowX(com.nexustools.genericui.enums.bootstrap.OverflowBehavior.${it.name}, ${s.isImportant(CssProperty.OVERFLOW_X)})")
        }
        s.getOverflowY()?.let {
            sb.appendLine("                setOverflowY(com.nexustools.genericui.enums.bootstrap.OverflowBehavior.${it.name}, ${s.isImportant(CssProperty.OVERFLOW_Y)})")
        }
        s.getPointerEvents()?.let {
            sb.appendLine("                setPointerEvents(com.nexustools.genericui.enums.css.CssPointerEvents.${it.name}, ${s.isImportant(CssProperty.POINTER_EVENTS)})")
        }
        s.getUserSelect()?.let {
            sb.appendLine("                setUserSelect(com.nexustools.genericui.enums.css.CssUserSelect.${it.name}, ${s.isImportant(CssProperty.USER_SELECT)})")
        }
        s.getObjectFit()?.let {
            sb.appendLine("                setObjectFit(com.nexustools.genericui.enums.ui.ObjectFit.${it.name}, ${s.isImportant(CssProperty.OBJECT_FIT)})")
        }
        s.getAspectRatio()?.let {
            sb.appendLine("                CssAspectRatio.parse(${escapeKotlinString(it.toCssString())})?.let { ar -> setAspectRatio(ar, ${s.isImportant(CssProperty.ASPECT_RATIO)}) }")
        }
        s.getZIndex()?.let {
            sb.appendLine("                setZIndex($it, ${s.isImportant(CssProperty.Z_INDEX)})")
        }
        s.getTransform()?.let {
            sb.appendLine("                CssTransform2D.parse(${escapeKotlinString(it.toCssString())})?.let { tr -> setTransform(tr, ${s.isImportant(CssProperty.TRANSFORM)}) }")
        }
        s.getFilter()?.let {
            sb.appendLine("                CssFilter.parse(${escapeKotlinString(it.toCssString())})?.let { f -> setFilter(f, ${s.isImportant(CssProperty.FILTER)}) }")
        }
        s.getBackdropFilter()?.let {
            sb.appendLine("                CssFilter.parse(${escapeKotlinString(it.toCssString())})?.let { f -> setBackdropFilter(f, ${s.isImportant(CssProperty.BACKDROP_FILTER)}) }")
        }
    }

    /**
     * Parses a single CSS selector (no commas) into a list of [CompiledSelectorSegment]s.
     */
    fun parseSelectorSegments(selector: String): List<CompiledSelectorSegment> =
        CompiledSelectorSegment.parseChain(selector)

    private fun escapeKotlinString(value: String): String {
        val sb = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '$' -> sb.append("\\$")
                else -> sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
