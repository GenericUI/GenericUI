package com.nexustools.genericui.style.mapper

import com.nexustools.genericui.enums.bootstrap.*

/**
 * Bidirectional mapper between Bootstrap 5 style Enums (`ComponentBootstrap`) and CSS class tokens.
 */
object BootstrapClassMapper {

    /**
     * Returns ONLY the Bootstrap 5 CSS classes derived from enum properties on [snapshot],
     * strictly excluding user-added [BootstrapStyleSnapshot.customClasses].
     */
    fun toEnumClassSet(snapshot: BootstrapStyleSnapshot): Set<String> {
        val tokens = linkedSetOf<String>()
        val prefix = snapshot.componentPrefix

        val semanticType = snapshot.type?.takeIf { it.isSemantic }
        if (semanticType != null && prefix.isNotEmpty()) {
            tokens.add(prefix)
            if (snapshot.outline && prefix == "btn") {
                tokens.add("$prefix-outline-${semanticType.cssSuffix}")
            } else {
                tokens.add("$prefix-${semanticType.cssSuffix}")
            }
        }
        if (snapshot.size != null && prefix.isNotEmpty()) {
            if (prefix !in tokens) tokens.add(prefix)
            tokens.add("$prefix-${snapshot.size.cssSuffix}")
        }
        snapshot.width?.let { tokens.add(it.cssClass) }
        snapshot.height?.let { tokens.add(it.cssClass) }
        snapshot.backgroundType?.takeIf { it.isSemantic }?.let { tokens.add("bg-${it.cssSuffix}") }
        snapshot.textColorType?.takeIf { it.isSemantic }?.let { tokens.add("text-${it.cssSuffix}") }
        snapshot.borderType?.takeIf { it.isSemantic }?.let {
            tokens.add("border")
            tokens.add("border-${it.cssSuffix}")
        }
        snapshot.borderWidth?.let {
            tokens.add("border")
            tokens.add(it.cssClass)
        }
        snapshot.textAlignment?.let { tokens.add(it.cssClass) }
        snapshot.fontWeight?.let { tokens.add(it.cssClass) }
        snapshot.fontSize?.let { tokens.add(it.cssClass) }
        snapshot.lineHeight?.let { tokens.add(it.cssClass) }
        snapshot.textTransform?.takeIf { it != TextTransform.NONE }?.let { tokens.add(it.cssClass) }
        snapshot.textOverflow?.let { tokens.add(it.cssClass) }
        snapshot.borderRadius?.let { tokens.add(it.cssClass) }
        snapshot.shadow?.let { tokens.add(it.cssClass) }
        snapshot.opacity?.let { tokens.add(it.cssClass) }
        snapshot.display?.let { tokens.add(it.cssClass) }
        snapshot.flexDirection?.let { tokens.add(it.cssClass) }
        snapshot.flexWrap?.let { tokens.add(it.cssClass) }
        snapshot.justifyContent?.let { tokens.add(it.cssClass) }
        snapshot.alignItems?.let { tokens.add(it.cssClass) }
        snapshot.alignSelf?.let { tokens.add(it.cssClass) }
        snapshot.flexGrow?.let { tokens.add(it.cssClass) }
        snapshot.flexShrink?.let { tokens.add(it.cssClass) }
        if (snapshot.flexFill) tokens.add("flex-fill")
        snapshot.gap?.let { tokens.add("gap-${it.token}") }
        snapshot.rowGap?.let { tokens.add("row-gap-${it.token}") }
        snapshot.columnGap?.let { tokens.add("column-gap-${it.token}") }
        snapshot.overflow?.let { tokens.add(it.cssClass) }
        snapshot.position?.let { tokens.add(it.cssClass) }
        snapshot.floatSide?.let { tokens.add(it.cssClass) }
        snapshot.userSelect?.let { tokens.add(it.cssClass) }
        snapshot.pointerEvents?.let { tokens.add(it.cssClass) }
        snapshot.objectFit?.let { tokens.add(it.cssClass) }
        snapshot.aspectRatio?.let {
            tokens.add("ratio")
            tokens.add(it.cssClass)
        }
        snapshot.visibility?.let { tokens.add(it.cssClass) }
        snapshot.marginAll?.let { tokens.add("m-${it.token}") }
        snapshot.marginTop?.let { tokens.add("mt-${it.token}") }
        snapshot.marginBottom?.let { tokens.add("mb-${it.token}") }
        snapshot.marginStart?.let { tokens.add("ms-${it.token}") }
        snapshot.marginEnd?.let { tokens.add("me-${it.token}") }
        snapshot.marginX?.let { tokens.add("mx-${it.token}") }
        snapshot.marginY?.let { tokens.add("my-${it.token}") }
        snapshot.paddingAll?.let { tokens.add("p-${it.token}") }
        snapshot.paddingTop?.let { tokens.add("pt-${it.token}") }
        snapshot.paddingBottom?.let { tokens.add("pb-${it.token}") }
        snapshot.paddingStart?.let { tokens.add("ps-${it.token}") }
        snapshot.paddingEnd?.let { tokens.add("pe-${it.token}") }
        snapshot.paddingX?.let { tokens.add("px-${it.token}") }
        snapshot.paddingY?.let { tokens.add("py-${it.token}") }

        return tokens
    }

    fun toClassTokens(snapshot: BootstrapStyleSnapshot): Set<String> {
        val tokens = linkedSetOf<String>()
        tokens.addAll(toEnumClassSet(snapshot))
        tokens.addAll(snapshot.customClasses)
        return tokens
    }

    fun toClassString(snapshot: BootstrapStyleSnapshot): String =
        toClassTokens(snapshot).joinToString(" ")

    fun fromClassString(classString: String, componentPrefix: String = "btn"): BootstrapStyleSnapshot {
        val rawTokens = classString.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var type: BootstrapType? = null
        var outline = false
        var size: BootstrapSize? = null
        var width: BootstrapWidth? = null
        var height: BootstrapHeight? = null
        var backgroundType: BootstrapType? = null
        var textColorType: BootstrapType? = null
        var borderType: BootstrapType? = null
        var borderWidth: BootstrapBorderWidth? = null
        var textAlignment: TextAlignment? = null
        var fontWeight: FontWeight? = null
        var fontSize: BootstrapFontSize? = null
        var lineHeight: BootstrapLineHeight? = null
        var textTransform: TextTransform? = null
        var textOverflow: TextOverflow? = null
        var borderRadius: BorderRadiusStyle? = null
        var shadow: ShadowElevation? = null
        var opacity: BootstrapOpacity? = null
        var display: DisplayMode? = null
        var flexDirection: FlexDirection? = null
        var flexWrap: FlexWrap? = null
        var justifyContent: JustifyContent? = null
        var alignItems: AlignItems? = null
        var alignSelf: AlignSelf? = null
        var flexGrow: BootstrapFlexGrow? = null
        var flexShrink: BootstrapFlexShrink? = null
        var flexFill = false
        var gap: SpacingScale? = null
        var rowGap: SpacingScale? = null
        var columnGap: SpacingScale? = null
        var overflow: OverflowBehavior? = null
        var position: PositionMode? = null
        var floatSide: BootstrapFloat? = null
        var userSelect: BootstrapUserSelect? = null
        var pointerEvents: BootstrapPointerEvents? = null
        var objectFit: BootstrapObjectFit? = null
        var aspectRatio: BootstrapAspectRatio? = null
        var visibility: BootstrapVisibility? = null
        var marginAll: SpacingScale? = null
        var marginTop: SpacingScale? = null
        var marginBottom: SpacingScale? = null
        var marginStart: SpacingScale? = null
        var marginEnd: SpacingScale? = null
        var marginX: SpacingScale? = null
        var marginY: SpacingScale? = null
        var paddingAll: SpacingScale? = null
        var paddingTop: SpacingScale? = null
        var paddingBottom: SpacingScale? = null
        var paddingStart: SpacingScale? = null
        var paddingEnd: SpacingScale? = null
        var paddingX: SpacingScale? = null
        var paddingY: SpacingScale? = null
        val customClasses = linkedSetOf<String>()

        var sawComponentPrefixToken = false
        var sawStandaloneBorderToken = false
        for (token in rawTokens) {
            if (token == componentPrefix) {
                sawComponentPrefixToken = true
                continue
            }
            if (token == "border") {
                sawStandaloneBorderToken = true
                customClasses.add(token)
                continue
            }
            if (token == "flex-fill") {
                flexFill = true
                continue
            }
            if (componentPrefix.isNotEmpty() && token.startsWith("$componentPrefix-outline-")) {
                val suffix = token.removePrefix("$componentPrefix-outline-")
                val matched = BootstrapType.fromCssSuffix(suffix)
                if (matched != null) {
                    type = matched
                    outline = true
                    continue
                }
            }
            if (componentPrefix.isNotEmpty() && token.startsWith("$componentPrefix-")) {
                val suffix = token.removePrefix("$componentPrefix-")
                val matchedType = BootstrapType.fromCssSuffix(suffix)
                if (matchedType != null) {
                    type = matchedType
                    outline = false
                    continue
                }
                val matchedSize = BootstrapSize.fromCssSuffix(suffix)
                if (matchedSize != null) {
                    size = matchedSize
                    continue
                }
            }
            BootstrapWidth.fromCssClass(token)?.let { width = it; continue }
            BootstrapHeight.fromCssClass(token)?.let { height = it; continue }
            if (token.startsWith("bg-")) {
                val matched = BootstrapType.fromCssSuffix(token.removePrefix("bg-"))
                if (matched != null) {
                    backgroundType = matched
                    continue
                }
            }
            BootstrapBorderWidth.fromCssClass(token)?.let { borderWidth = it; continue }
            if (token.startsWith("border-")) {
                val matched = BootstrapType.fromCssSuffix(token.removePrefix("border-"))
                if (matched != null) {
                    borderType = matched
                    continue
                }
            }
            TextAlignment.fromCssClass(token)?.let {
                textAlignment = it
                if (token.equals("text-left", ignoreCase = true) || token.equals("text-right", ignoreCase = true)) {
                    customClasses.add(token)
                }
                continue
            }
            TextTransform.fromCssClass(token)?.let { textTransform = it; continue }
            TextOverflow.fromCssClass(token)?.let { textOverflow = it; continue }
            if (token.startsWith("text-")) {
                val matched = BootstrapType.fromCssSuffix(token.removePrefix("text-"))
                if (matched != null) {
                    textColorType = matched
                    continue
                }
            }
            FontWeight.fromCssClass(token)?.let { fontWeight = it; continue }
            BootstrapFontSize.fromCssClass(token)?.let { fontSize = it; continue }
            BootstrapLineHeight.fromCssClass(token)?.let { lineHeight = it; continue }
            BorderRadiusStyle.fromCssClass(token)?.let { borderRadius = it; continue }
            ShadowElevation.fromCssClass(token)?.let { shadow = it; continue }
            BootstrapOpacity.fromCssClass(token)?.let { opacity = it; continue }
            DisplayMode.fromCssClass(token)?.let { display = it; continue }
            FlexDirection.fromCssClass(token)?.let { flexDirection = it; continue }
            FlexWrap.fromCssClass(token)?.let { flexWrap = it; continue }
            JustifyContent.fromCssClass(token)?.let { justifyContent = it; continue }
            AlignItems.fromCssClass(token)?.let { alignItems = it; continue }
            AlignSelf.fromCssClass(token)?.let { alignSelf = it; continue }
            BootstrapFlexGrow.fromCssClass(token)?.let { flexGrow = it; continue }
            BootstrapFlexShrink.fromCssClass(token)?.let { flexShrink = it; continue }
            OverflowBehavior.fromCssClass(token)?.let { overflow = it; continue }
            PositionMode.fromCssClass(token)?.let { position = it; continue }
            BootstrapFloat.fromCssClass(token)?.let { floatSide = it; continue }
            BootstrapUserSelect.fromCssClass(token)?.let { userSelect = it; continue }
            BootstrapPointerEvents.fromCssClass(token)?.let { pointerEvents = it; continue }
            BootstrapObjectFit.fromCssClass(token)?.let { objectFit = it; continue }
            BootstrapAspectRatio.fromCssClass(token)?.let { aspectRatio = it; continue }
            BootstrapVisibility.fromCssClass(token)?.let { visibility = it; continue }
            if (token.startsWith("gap-")) {
                SpacingScale.fromToken(token.removePrefix("gap-"))?.let { gap = it; continue }
            }
            if (token.startsWith("row-gap-")) {
                SpacingScale.fromToken(token.removePrefix("row-gap-"))?.let { rowGap = it; continue }
            }
            if (token.startsWith("column-gap-")) {
                SpacingScale.fromToken(token.removePrefix("column-gap-"))?.let { columnGap = it; continue }
            }
            if (token.startsWith("m-")) {
                val scale = SpacingScale.fromToken(token.removePrefix("m-"))
                if (scale != null) {
                    marginAll = scale
                    if (token.equals("m-zero", ignoreCase = true)) {
                        customClasses.add(token)
                    }
                    continue
                }
            }
            if (token.startsWith("mt-")) {
                SpacingScale.fromToken(token.removePrefix("mt-"))?.let { marginTop = it; continue }
            }
            if (token.startsWith("mb-")) {
                SpacingScale.fromToken(token.removePrefix("mb-"))?.let { marginBottom = it; continue }
            }
            if (token.startsWith("ms-")) {
                SpacingScale.fromToken(token.removePrefix("ms-"))?.let { marginStart = it; continue }
            }
            if (token.startsWith("me-")) {
                SpacingScale.fromToken(token.removePrefix("me-"))?.let { marginEnd = it; continue }
            }
            if (token.startsWith("mx-")) {
                SpacingScale.fromToken(token.removePrefix("mx-"))?.let { marginX = it; continue }
            }
            if (token.startsWith("my-")) {
                SpacingScale.fromToken(token.removePrefix("my-"))?.let { marginY = it; continue }
            }
            if (token.startsWith("p-")) {
                val scale = SpacingScale.fromToken(token.removePrefix("p-"))
                if (scale != null) {
                    paddingAll = scale
                    if (token.equals("p-zero", ignoreCase = true)) {
                        customClasses.add(token)
                    }
                    continue
                }
            }
            if (token.startsWith("pt-")) {
                SpacingScale.fromToken(token.removePrefix("pt-"))?.let { paddingTop = it; continue }
            }
            if (token.startsWith("pb-")) {
                SpacingScale.fromToken(token.removePrefix("pb-"))?.let { paddingBottom = it; continue }
            }
            if (token.startsWith("ps-")) {
                SpacingScale.fromToken(token.removePrefix("ps-"))?.let { paddingStart = it; continue }
            }
            if (token.startsWith("pe-")) {
                SpacingScale.fromToken(token.removePrefix("pe-"))?.let { paddingEnd = it; continue }
            }
            if (token.startsWith("px-")) {
                SpacingScale.fromToken(token.removePrefix("px-"))?.let { paddingX = it; continue }
            }
            if (token.startsWith("py-")) {
                SpacingScale.fromToken(token.removePrefix("py-"))?.let { paddingY = it; continue }
            }
            customClasses.add(token)
        }

        if (sawStandaloneBorderToken && (borderType != null || borderWidth != null)) {
            customClasses.remove("border")
        }

        if (sawComponentPrefixToken && type == null && size == null && componentPrefix.isNotEmpty()) {
            customClasses.add(componentPrefix)
        }

        return BootstrapStyleSnapshot(
            componentPrefix = componentPrefix,
            type = type,
            outline = outline,
            size = size,
            width = width,
            height = height,
            backgroundType = backgroundType,
            textColorType = textColorType,
            borderType = borderType,
            borderWidth = borderWidth,
            textAlignment = textAlignment,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            textTransform = textTransform,
            textOverflow = textOverflow,
            borderRadius = borderRadius,
            shadow = shadow,
            opacity = opacity,
            display = display,
            flexDirection = flexDirection,
            flexWrap = flexWrap,
            justifyContent = justifyContent,
            alignItems = alignItems,
            alignSelf = alignSelf,
            flexGrow = flexGrow,
            flexShrink = flexShrink,
            flexFill = flexFill,
            gap = gap,
            rowGap = rowGap,
            columnGap = columnGap,
            overflow = overflow,
            position = position,
            floatSide = floatSide,
            userSelect = userSelect,
            pointerEvents = pointerEvents,
            objectFit = objectFit,
            aspectRatio = aspectRatio,
            visibility = visibility,
            marginAll = marginAll,
            marginTop = marginTop,
            marginBottom = marginBottom,
            marginStart = marginStart,
            marginEnd = marginEnd,
            marginX = marginX,
            marginY = marginY,
            paddingAll = paddingAll,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom,
            paddingStart = paddingStart,
            paddingEnd = paddingEnd,
            paddingX = paddingX,
            paddingY = paddingY,
            customClasses = customClasses
        )
    }
}
