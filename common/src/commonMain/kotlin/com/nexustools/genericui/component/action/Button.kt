package com.nexustools.genericui.component.action

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.res.ResourceId
import com.nexustools.genericui.res.Resources

/**
 * Interactive push button component (`<button class="btn">`) supporting dual-mode
 * Bootstrap Enum styling ([type], [size], [outline]), SASS/CSS classes, [icon] + [text],
 * and icon-only buttons (`text = ""` with non-blank [icon]).
 */
open class Button(
    text: String = "",
    icon: String? = null,
    type: BootstrapType? = null,
    size: BootstrapSize = BootstrapSize.MD,
    outline: Boolean = false,
    private val classPrefix: String = "btn"
) : AbstractComponent(classPrefix) {

    constructor(
        text: String,
        type: BootstrapType?,
        size: BootstrapSize = BootstrapSize.MD,
        outline: Boolean = false,
        classPrefix: String = "btn"
    ) : this(
        text = text,
        icon = null,
        type = type,
        size = size,
        outline = outline,
        classPrefix = classPrefix
    )

    /**
     * Icon-only [Button] constructor accepting a typed [ResourceId] (`R.drawable.*` / `RC.drawable.*`).
     */
    constructor(
        iconRes: ResourceId,
        tooltipText: String? = null,
        type: BootstrapType? = null,
        size: BootstrapSize = BootstrapSize.SM,
        outline: Boolean = false
    ) : this(
        text = "",
        icon = iconRes.toResourceUri(),
        type = type,
        size = size,
        outline = outline
    ) {
        this.iconRes = iconRes
        this.tooltipText = tooltipText
    }

    /**
     * Resource-backed [Button] constructor accepting a string [ResourceId] (`R.string.*`) and optional drawable [ResourceId].
     */
    constructor(
        textRes: ResourceId,
        iconRes: ResourceId? = null,
        type: BootstrapType? = null,
        size: BootstrapSize = BootstrapSize.MD,
        outline: Boolean = false
    ) : this(
        text = Resources.getString(textRes),
        icon = iconRes?.toResourceUri(),
        type = type,
        size = size,
        outline = outline
    ) {
        this.textRes = textRes
        this.iconRes = iconRes
    }

    var textRes: ResourceId? = null
        set(value) {
            field = value
            if (value != null) {
                text = Resources.getString(value)
            }
        }

    var iconRes: ResourceId? = null
        set(value) {
            field = value
            if (value != null) {
                icon = value.toResourceUri()
            }
        }

    var text: String = text
        set(value) {
            if (field != value) {
                field = value
                notifyParentCacheDirty()
                typedNative<ButtonNativeImpl<*>>()?.setText(value)
            }
        }

    var icon: String? = icon
        set(value) {
            if (field != value) {
                field = value
                notifyParentCacheDirty()
                typedNative<ButtonNativeImpl<*>>()?.setIcon(value)
            }
        }

    /**
     * Returns `true` when this button has an [icon] and no [text] caption.
     */
    val isIconOnly: Boolean
        get() = !icon.isNullOrBlank() && text.isBlank()

    init {
        this.type = type
        this.size = size
        this.outline = outline
    }

    /**
     * `kotlinx.html`-style unary plus operator to set/append button caption text.
     */
    operator fun String.unaryPlus() {
        text = if (text.isEmpty()) this else text + this
    }

    override fun createNativeImpl(factory: NativeFactory): ButtonNativeImpl<*> =
        factory.createButton(classPrefix)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ButtonNativeImpl<*>>() ?: return
        impl.setText(text)
        impl.setIcon(icon)
        impl.setTooltipText(tooltipText)
    }
}
