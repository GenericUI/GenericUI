package com.nexustools.genericui.core

import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Root Native Implementation interface (`NativeImpl<W>`) for all UI components.
 *
 * Implements the dual-mode styling architecture:
 * - `setClass(String)` / `getClass(): String`, `addClass(String)`, `removeClass(String)`, `hasClass(String)`
 *   for string-based CSS classes.
 * - `setType(BootstrapType?)` / `getType(): BootstrapType?` and helper setters/getters for every primary
 *   Bootstrap 5 style enum (`setSize`, `setBackgroundType`, `setTextColorType`, `setBorderType`,
 *   `setTextAlignment`, `setFontWeight`, `setTextTransform`, `setTextOverflow`, `setBorderRadius`,
 *   `setShadow`, `setDisplay`, `setFlexDirection`, `setFlexWrap`, `setJustifyContent`, `setAlignItems`,
 *   `setAlignSelf`, `setOverflow`, `setPosition`, `setMarginAll`, `setPaddingAll`).
 *
 * Native backends (`:qt`, `:gtk`, `:windows`, `:macos`, `:swing`, `:android`, `:ios`) handle the Enums
 * directly for maximum efficiency and synthesize `getClass()` / parse `setClass()` via [com.nexustools.genericui.style.mapper.BootstrapClassMapper],
 * while continuing to resolve custom CSS classes via [applyComputedStyle].
 * The `:web` backend applies CSS classes directly to the DOM and simulates the Enum getters/setters.
 */
interface NativeImpl<out W> : Disposable {
    val nativeWidget: W
    val componentClassPrefix: String

    // String-based CSS Class API
    fun setClass(className: String)
    fun getClass(): String
    fun addClass(className: String)
    fun removeClass(className: String)
    fun hasClass(className: String): Boolean

    // Bootstrap Enum Helper API (Primary Contextual Type + All Bootstrap Style Enums)
    fun setType(type: BootstrapType?)
    fun getType(): BootstrapType?

    fun setOutline(outline: Boolean)
    fun isOutline(): Boolean

    fun setSize(size: BootstrapSize?)
    fun getSize(): BootstrapSize?

    fun setBackgroundType(type: BootstrapType?)
    fun getBackgroundType(): BootstrapType?

    fun setTextColorType(type: BootstrapType?)
    fun getTextColorType(): BootstrapType?

    fun setBorderType(type: BootstrapType?)
    fun getBorderType(): BootstrapType?

    fun setTextAlignment(alignment: TextAlignment?)
    fun getTextAlignment(): TextAlignment?

    fun setFontWeight(weight: FontWeight?)
    fun getFontWeight(): FontWeight?

    fun setTextTransform(transform: TextTransform?)
    fun getTextTransform(): TextTransform?

    fun setTextOverflow(overflow: TextOverflow?)
    fun getTextOverflow(): TextOverflow?

    fun setBorderRadius(radius: BorderRadiusStyle?)
    fun getBorderRadius(): BorderRadiusStyle?

    fun setShadow(shadow: ShadowElevation?)
    fun getShadow(): ShadowElevation?

    fun setDisplay(display: DisplayMode?)
    fun getDisplay(): DisplayMode?

    fun setFlexDirection(direction: FlexDirection?)
    fun getFlexDirection(): FlexDirection?

    fun setFlexWrap(wrap: FlexWrap?)
    fun getFlexWrap(): FlexWrap?

    fun setJustifyContent(justify: JustifyContent?)
    fun getJustifyContent(): JustifyContent?

    fun setAlignItems(align: AlignItems?)
    fun getAlignItems(): AlignItems?

    fun setAlignSelf(align: AlignSelf?)
    fun getAlignSelf(): AlignSelf?

    fun setOverflow(overflow: OverflowBehavior?)
    fun getOverflow(): OverflowBehavior?

    fun setPosition(position: PositionMode?)
    fun getPosition(): PositionMode?

    fun setMarginAll(scale: SpacingScale?)
    fun getMarginAll(): SpacingScale?

    fun setPaddingAll(scale: SpacingScale?)
    fun getPaddingAll(): SpacingScale?

    // Geometry, Visibility, State & Custom CSS
    fun setVisible(visible: Boolean)
    fun isVisible(): Boolean

    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean

    fun setOpacity(opacity: Float)
    fun getOpacity(): Float

    fun setTooltipText(tooltip: String?)
    fun getTooltipText(): String?

    fun setPreferredSize(size: Size?)
    fun getBounds(): Rect

    fun setPadding(padding: EdgeInsets)
    fun setMargin(margin: EdgeInsets)

    fun applyComputedStyle(style: ComputedStyle)

    fun setTint(tint: RgbaColor?) {}
    fun getTint(): RgbaColor? = null

    fun setBackgroundGradient(gradient: Gradient?) {}
    fun getBackgroundGradient(): Gradient? = null

    fun setHoveredState(hovered: Boolean) {}
    fun isHoveredState(): Boolean = false

    fun setFocusedState(focused: Boolean) {}
    fun isFocusedState(): Boolean = false

    fun setActivePressedState(active: Boolean) {}
    fun isActivePressedState(): Boolean = false

    // Native CSS Transition Processing (overridden by backends with native compositor animation)
    fun supportsNativeTransitions(): Boolean = false
    fun configureNativeTransitions(specs: List<CssTransitionSpec>) {}
    fun runNativeTransition(
        property: String,
        fromValue: String?,
        toValue: String,
        spec: CssTransitionSpec,
        onComplete: () -> Unit
    ): Boolean = false

    // Event Listeners
    fun setOnClick(listener: (() -> Unit)?)
    fun setOnDoubleClick(listener: (() -> Unit)?)
    fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?)
    fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?)
    fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?)
    fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?)
    fun setOnWheel(listener: ((WheelEvent) -> Unit)?)
    fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?)
    fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?)
}
