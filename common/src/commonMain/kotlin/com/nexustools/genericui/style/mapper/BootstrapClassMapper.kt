package com.nexustools.genericui.style.mapper

import com.nexustools.genericui.enums.bootstrap.*

/**
 * Bidirectional mapper between Bootstrap 5 style Enums (`setType`/`getType`, `setSize`/`getSize`, etc.)
 * and CSS class strings (`setClass`/`getClass`).
 *
 * - Native backends (`:qt`, `:gtk`, `:windows`, `:macos`, `:swing`, `:android`, `:ios`) store and execute
 *   the strongly-typed Enums directly for zero-string-parsing efficiency, using [toClassString] only to
 *   simulate `getClass()` and [fromClassString] when `setClass()` is called with built-in Bootstrap classes.
 * - The `:web` backend applies the CSS class strings directly to the DOM `Element.classList` and uses
 *   [fromClassString] to simulate `getType()` and the other Enum getters.
 */
object BootstrapClassMapper {

    fun toClassTokens(snapshot: BootstrapStyleSnapshot): Set<String> {
        val tokens = linkedSetOf<String>()
        val prefix = snapshot.componentPrefix

        if (snapshot.type != null && prefix.isNotEmpty()) {
            tokens.add(prefix)
            if (snapshot.outline && prefix == "btn") {
                tokens.add("$prefix-outline-${snapshot.type.cssSuffix}")
            } else {
                tokens.add("$prefix-${snapshot.type.cssSuffix}")
            }
        }
        if (snapshot.size != null && prefix.isNotEmpty()) {
            tokens.add("$prefix-${snapshot.size.cssSuffix}")
        }
        snapshot.backgroundType?.let { tokens.add("bg-${it.cssSuffix}") }
        snapshot.textColorType?.let { tokens.add("text-${it.cssSuffix}") }
        snapshot.borderType?.let {
            tokens.add("border")
            tokens.add("border-${it.cssSuffix}")
        }
        snapshot.textAlignment?.let { tokens.add(it.cssClass) }
        snapshot.fontWeight?.let { tokens.add(it.cssClass) }
        snapshot.textTransform?.takeIf { it != TextTransform.NONE }?.let { tokens.add(it.cssClass) }
        snapshot.textOverflow?.let { tokens.add(it.cssClass) }
        snapshot.borderRadius?.let { tokens.add(it.cssClass) }
        snapshot.shadow?.let { tokens.add(it.cssClass) }
        snapshot.display?.let { tokens.add(it.cssClass) }
        snapshot.flexDirection?.let { tokens.add(it.cssClass) }
        snapshot.flexWrap?.let { tokens.add(it.cssClass) }
        snapshot.justifyContent?.let { tokens.add(it.cssClass) }
        snapshot.alignItems?.let { tokens.add(it.cssClass) }
        snapshot.alignSelf?.let { tokens.add(it.cssClass) }
        snapshot.overflow?.let { tokens.add(it.cssClass) }
        snapshot.position?.let { tokens.add(it.cssClass) }
        snapshot.marginAll?.let { tokens.add("m-${it.token}") }
        snapshot.paddingAll?.let { tokens.add("p-${it.token}") }

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
        var backgroundType: BootstrapType? = null
        var textColorType: BootstrapType? = null
        var borderType: BootstrapType? = null
        var textAlignment: TextAlignment? = null
        var fontWeight: FontWeight? = null
        var textTransform: TextTransform? = null
        var textOverflow: TextOverflow? = null
        var borderRadius: BorderRadiusStyle? = null
        var shadow: ShadowElevation? = null
        var display: DisplayMode? = null
        var flexDirection: FlexDirection? = null
        var flexWrap: FlexWrap? = null
        var justifyContent: JustifyContent? = null
        var alignItems: AlignItems? = null
        var alignSelf: AlignSelf? = null
        var overflow: OverflowBehavior? = null
        var position: PositionMode? = null
        var marginAll: SpacingScale? = null
        var paddingAll: SpacingScale? = null
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
            if (token.startsWith("bg-")) {
                val matched = BootstrapType.fromCssSuffix(token.removePrefix("bg-"))
                if (matched != null) {
                    backgroundType = matched
                    continue
                }
            }
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
            BorderRadiusStyle.fromCssClass(token)?.let { borderRadius = it; continue }
            ShadowElevation.fromCssClass(token)?.let { shadow = it; continue }
            DisplayMode.fromCssClass(token)?.let { display = it; continue }
            FlexDirection.fromCssClass(token)?.let { flexDirection = it; continue }
            FlexWrap.fromCssClass(token)?.let { flexWrap = it; continue }
            JustifyContent.fromCssClass(token)?.let { justifyContent = it; continue }
            AlignItems.fromCssClass(token)?.let { alignItems = it; continue }
            AlignSelf.fromCssClass(token)?.let { alignSelf = it; continue }
            OverflowBehavior.fromCssClass(token)?.let { overflow = it; continue }
            PositionMode.fromCssClass(token)?.let { position = it; continue }
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
            customClasses.add(token)
        }

        if (sawStandaloneBorderToken && borderType != null) {
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
            backgroundType = backgroundType,
            textColorType = textColorType,
            borderType = borderType,
            textAlignment = textAlignment,
            fontWeight = fontWeight,
            textTransform = textTransform,
            textOverflow = textOverflow,
            borderRadius = borderRadius,
            shadow = shadow,
            display = display,
            flexDirection = flexDirection,
            flexWrap = flexWrap,
            justifyContent = justifyContent,
            alignItems = alignItems,
            alignSelf = alignSelf,
            overflow = overflow,
            position = position,
            marginAll = marginAll,
            paddingAll = paddingAll,
            customClasses = customClasses
        )
    }
}
