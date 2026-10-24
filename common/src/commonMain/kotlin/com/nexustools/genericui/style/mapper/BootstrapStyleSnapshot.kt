package com.nexustools.genericui.style.mapper

import com.nexustools.genericui.enums.bootstrap.*

/**
 * Immutable snapshot of all built-in Bootstrap 5 enum properties parsed from or serialized to CSS class strings.
 */
data class BootstrapStyleSnapshot(
    val componentPrefix: String = "btn",
    val type: BootstrapType? = null,
    val outline: Boolean = false,
    val size: BootstrapSize? = null,
    val backgroundType: BootstrapType? = null,
    val textColorType: BootstrapType? = null,
    val borderType: BootstrapType? = null,
    val textAlignment: TextAlignment? = null,
    val fontWeight: FontWeight? = null,
    val textTransform: TextTransform? = null,
    val textOverflow: TextOverflow? = null,
    val borderRadius: BorderRadiusStyle? = null,
    val shadow: ShadowElevation? = null,
    val display: DisplayMode? = null,
    val flexDirection: FlexDirection? = null,
    val flexWrap: FlexWrap? = null,
    val justifyContent: JustifyContent? = null,
    val alignItems: AlignItems? = null,
    val alignSelf: AlignSelf? = null,
    val overflow: OverflowBehavior? = null,
    val position: PositionMode? = null,
    val marginAll: SpacingScale? = null,
    val paddingAll: SpacingScale? = null,
    val customClasses: Set<String> = emptySet()
)
