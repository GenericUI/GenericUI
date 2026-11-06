package com.nexustools.genericui.system.style

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
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssAspectRatio
import com.nexustools.genericui.style.css.CssBackgroundImage
import com.nexustools.genericui.style.css.CssCustomCursor
import com.nexustools.genericui.style.css.CssProperty

/**
 * Strongly-typed Blink style builder that applies [BlinkPropertyEntry] values and
 * single-property inline mutations directly without CSS string serialization or parsing.
 */
object BlinkStyleBuilder {

    fun applyPropertyEntry(style: ComponentStyle, entry: BlinkPropertyEntry) {
        val imp = entry.important
        when (entry.property) {
            CssProperty.BACKGROUND_COLOR -> style.setBackgroundColor(entry.colorVal, imp)
            CssProperty.BACKGROUND_IMAGE -> {
                val img = when {
                    entry.gradientVal != null -> CssBackgroundImage.fromGradient(entry.gradientVal)
                    entry.stringVal != null -> CssBackgroundImage.fromUrl(entry.stringVal)
                    else -> null
                }
                style.setBackgroundImage(img, imp)
            }
            CssProperty.BACKGROUND_REPEAT -> style.setBackgroundRepeat(
                CssBackgroundRepeat.entries.getOrNull(entry.enumOrdinal),
                imp
            )
            CssProperty.BACKGROUND_ATTACHMENT -> style.setBackgroundAttachment(
                CssBackgroundAttachment.entries.getOrNull(entry.enumOrdinal),
                imp
            )
            CssProperty.BACKGROUND_CLIP -> style.setBackgroundClip(
                CssBackgroundClip.entries.getOrNull(entry.enumOrdinal),
                imp
            )
            CssProperty.BACKGROUND_ORIGIN -> style.setBackgroundOrigin(
                CssBackgroundOrigin.entries.getOrNull(entry.enumOrdinal),
                imp
            )
            CssProperty.COLOR -> style.setTextColor(entry.colorVal, imp)
            CssProperty.ACCENT_COLOR -> style.setAccentColor(entry.colorVal, imp)
            CssProperty.OPACITY -> style.setOpacity(entry.floatVal, imp)
            CssProperty.DISPLAY -> style.setDisplay(CssDisplay.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.VISIBILITY -> style.setVisibility(CssVisibility.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.POSITION -> style.setPosition(CssPosition.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.TOP -> style.setTop(entry.lengthVal, imp)
            CssProperty.RIGHT -> style.setRight(entry.lengthVal, imp)
            CssProperty.BOTTOM -> style.setBottom(entry.lengthVal, imp)
            CssProperty.LEFT -> style.setLeft(entry.lengthVal, imp)
            CssProperty.WIDTH -> style.setWidth(entry.lengthVal, imp)
            CssProperty.HEIGHT -> style.setHeight(entry.lengthVal, imp)
            CssProperty.MIN_WIDTH -> style.setMinWidth(entry.lengthVal, imp)
            CssProperty.MIN_HEIGHT -> style.setMinHeight(entry.lengthVal, imp)
            CssProperty.MAX_WIDTH -> style.setMaxWidth(entry.lengthVal, imp)
            CssProperty.MAX_HEIGHT -> style.setMaxHeight(entry.lengthVal, imp)
            CssProperty.MARGIN -> style.setMargin(entry.edgeLengthsVal, imp)
            CssProperty.MARGIN_TOP -> style.setMarginTop(entry.lengthVal, imp)
            CssProperty.MARGIN_RIGHT -> style.setMarginRight(entry.lengthVal, imp)
            CssProperty.MARGIN_BOTTOM -> style.setMarginBottom(entry.lengthVal, imp)
            CssProperty.MARGIN_LEFT -> style.setMarginLeft(entry.lengthVal, imp)
            CssProperty.PADDING -> style.setPadding(entry.edgeLengthsVal, imp)
            CssProperty.PADDING_TOP -> style.setPaddingTop(entry.lengthVal, imp)
            CssProperty.PADDING_RIGHT -> style.setPaddingRight(entry.lengthVal, imp)
            CssProperty.PADDING_BOTTOM -> style.setPaddingBottom(entry.lengthVal, imp)
            CssProperty.PADDING_LEFT -> style.setPaddingLeft(entry.lengthVal, imp)
            CssProperty.BOX_SIZING -> style.setBoxSizing(CssBoxSizing.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.FLOAT -> style.setFloatSide(CssFloat.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.CLEAR -> style.setClearSide(CssClear.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.BORDER_WIDTH -> style.setBorderWidth(entry.edgeLengthsVal, imp)
            CssProperty.BORDER_STYLE -> style.setBorderStyle(CssBorderStyle.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.BORDER_COLOR -> style.setBorderColor(entry.colorVal, imp)
            CssProperty.BORDER_RADIUS -> style.setBorderRadius(entry.cornerRadiiVal, imp)
            CssProperty.BOX_SHADOW -> style.setBoxShadow(entry.shadowListVal, imp)
            CssProperty.TEXT_SHADOW -> style.setTextShadow(entry.shadowListVal, imp)
            CssProperty.FONT_FAMILY -> style.setFontFamily(entry.fontFamilyVal, imp)
            CssProperty.FONT_SIZE -> style.setFontSize(entry.lengthVal, imp)
            CssProperty.FONT_WEIGHT -> {
                val weight = FontWeight.entries.getOrNull(entry.enumOrdinal)
                    ?: FontWeight.entries.firstOrNull { it.numericWeight == entry.intVal }
                if (weight != null) {
                    style.setFontWeight(weight, imp)
                } else if (entry.intVal > 0) {
                    style.fontWeightNumeric = entry.intVal
                    style.setImportant(CssProperty.FONT_WEIGHT, imp)
                }
            }
            CssProperty.FONT_STYLE -> style.setFontStyle(CssFontStyle.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.LINE_HEIGHT -> style.setLineHeight(entry.lengthVal, imp)
            CssProperty.LETTER_SPACING -> style.setLetterSpacing(entry.lengthVal, imp)
            CssProperty.TEXT_ALIGN -> style.setTextAlign(TextAlignment.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.TEXT_DECORATION -> style.setTextDecoration(CssTextDecoration.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.TEXT_TRANSFORM -> style.setTextTransform(TextTransform.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.TEXT_OVERFLOW -> style.setTextOverflow(TextOverflow.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.WHITE_SPACE -> style.setWhiteSpace(CssWhiteSpace.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.WORD_BREAK -> style.setWordBreak(CssWordBreak.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.VERTICAL_ALIGN -> style.setVerticalAlign(CssVerticalAlign.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.FLEX_DIRECTION -> style.setFlexDirection(FlexDirection.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.FLEX_WRAP -> style.setFlexWrap(FlexWrap.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.JUSTIFY_CONTENT -> style.setJustifyContent(JustifyContent.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.ALIGN_ITEMS -> style.setAlignItems(AlignItems.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.ALIGN_SELF -> style.setAlignSelf(AlignSelf.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.ALIGN_CONTENT -> style.setAlignContent(CssAlignContent.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.FLEX_GROW -> style.setFlexGrow(entry.floatVal, imp)
            CssProperty.FLEX_SHRINK -> style.setFlexShrink(entry.floatVal, imp)
            CssProperty.FLEX_BASIS -> style.setFlexBasis(entry.lengthVal, imp)
            CssProperty.GAP -> style.setGap(entry.lengthVal, imp)
            CssProperty.ROW_GAP -> style.setRowGap(entry.lengthVal, imp)
            CssProperty.COLUMN_GAP -> style.setColumnGap(entry.lengthVal, imp)
            CssProperty.OVERFLOW -> style.setOverflow(OverflowBehavior.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.OVERFLOW_X -> style.setOverflowX(OverflowBehavior.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.OVERFLOW_Y -> style.setOverflowY(OverflowBehavior.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.CURSOR -> {
                val cursorEnum = CssCursor.entries.getOrNull(entry.enumOrdinal)
                if (cursorEnum != null) {
                    style.setCursor(cursorEnum, imp)
                }
            }
            CssProperty.POINTER_EVENTS -> style.setPointerEvents(CssPointerEvents.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.USER_SELECT -> style.setUserSelect(CssUserSelect.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.OBJECT_FIT -> style.setObjectFit(ObjectFit.entries.getOrNull(entry.enumOrdinal), imp)
            CssProperty.ASPECT_RATIO -> {
                if (entry.floatVal > 0f) {
                    style.setAspectRatio(CssAspectRatio.of(entry.floatVal, 1f), imp)
                }
            }
            CssProperty.Z_INDEX -> style.setZIndex(entry.intVal, imp)
            CssProperty.TRANSFORM -> style.setTransform(entry.transformVal, imp)
            CssProperty.TRANSITION -> style.setTransitions(entry.transitionsVal, imp)
            CssProperty.FILTER -> style.setFilter(entry.filterVal, imp)
            CssProperty.BACKDROP_FILTER -> style.setBackdropFilter(entry.filterVal, imp)
            else -> {
                if (entry.stringVal != null) {
                    style.setRawDeclaration(entry.property.cssName, entry.stringVal, imp)
                }
            }
        }
    }

    fun applySingleProperty(
        property: CssProperty,
        current: ComputedStyle,
        sheetBase: ComputedStyle?,
        inlineStyle: ComponentStyle
    ): ComputedStyle = inlineStyle.applySinglePropertyToComputed(property, current, sheetBase)

    fun buildComputedStyleFromRules(rules: List<BlinkCompiledRule>): ComputedStyle {
        if (rules.isEmpty()) return ComputedStyle.EMPTY

        val baseRules = ArrayList<BlinkCompiledRule>(rules.size)
        var hoverRules: ArrayList<BlinkCompiledRule>? = null
        var focusRules: ArrayList<BlinkCompiledRule>? = null
        var activeRules: ArrayList<BlinkCompiledRule>? = null
        var disabledRules: ArrayList<BlinkCompiledRule>? = null
        var checkedRules: ArrayList<BlinkCompiledRule>? = null

        for (rule in rules) {
            val mask = rule.subjectPseudoMask
            when {
                mask == 0 || mask == BlinkSelectorAtom.PSEUDO_ENABLED -> baseRules.add(rule)
                (mask and BlinkSelectorAtom.PSEUDO_HOVER) != 0 ->
                    (hoverRules ?: ArrayList<BlinkCompiledRule>(4).also { hoverRules = it }).add(rule)
                (mask and BlinkSelectorAtom.PSEUDO_FOCUS) != 0 ->
                    (focusRules ?: ArrayList<BlinkCompiledRule>(4).also { focusRules = it }).add(rule)
                (mask and BlinkSelectorAtom.PSEUDO_ACTIVE) != 0 ->
                    (activeRules ?: ArrayList<BlinkCompiledRule>(4).also { activeRules = it }).add(rule)
                (mask and BlinkSelectorAtom.PSEUDO_DISABLED) != 0 ->
                    (disabledRules ?: ArrayList<BlinkCompiledRule>(4).also { disabledRules = it }).add(rule)
                (mask and BlinkSelectorAtom.PSEUDO_CHECKED) != 0 ->
                    (checkedRules ?: ArrayList<BlinkCompiledRule>(4).also { checkedRules = it }).add(rule)
            }
        }

        var baseComputed = ComputedStyle.EMPTY
        for (r in baseRules) {
            baseComputed = r.style.toComputedStyle(baseComputed)
        }

        fun resolvePseudoList(pseudoRules: List<BlinkCompiledRule>?): ComputedStyle? {
            if (pseudoRules.isNullOrEmpty()) return null
            var acc = baseComputed
            for (r in pseudoRules) {
                acc = r.style.toComputedStyle(acc)
            }
            return acc
        }

        val resolvedHover = resolvePseudoList(hoverRules)
        val resolvedFocus = resolvePseudoList(focusRules)
        val resolvedActive = resolvePseudoList(activeRules)
        val resolvedDisabled = resolvePseudoList(disabledRules)
        val resolvedChecked = resolvePseudoList(checkedRules)

        if (resolvedHover == null && resolvedFocus == null && resolvedActive == null &&
            resolvedDisabled == null && resolvedChecked == null
        ) {
            return baseComputed
        }

        return baseComputed.copy(
            hoverStyle = resolvedHover,
            focusStyle = resolvedFocus,
            activeStyle = resolvedActive,
            disabledStyle = resolvedDisabled,
            checkedStyle = resolvedChecked
        )
    }
}
