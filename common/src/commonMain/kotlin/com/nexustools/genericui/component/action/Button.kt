package com.nexustools.genericui.component.action

import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.css.ComputedStyle

/**
 * Interactive push button component (`<button class="btn">`) supporting dual-mode
 * Bootstrap Enum styling ([type], [size], [outline]), SASS/CSS classes, [image] / [icon] + [text],
 * and icon/image-only buttons (`text = ""` with non-blank [icon] or [image]).
 */
open class Button(
    text: String = "",
    icon: String? = null,
    type: BootstrapType? = BootstrapType.NONE,
    size: BootstrapSize = BootstrapSize.MD,
    outline: Boolean = false,
    private val classPrefix: String = "btn"
) : AbstractComponent(classPrefix, htmlTag = "button") {

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
     * Image-backed [Button] constructor accepting an [Image] component, optional caption [text],
     * and Bootstrap styling options.
     */
    constructor(
        image: Image,
        text: String = "",
        type: BootstrapType? = BootstrapType.NONE,
        size: BootstrapSize = BootstrapSize.MD,
        outline: Boolean = false,
        classPrefix: String = "btn"
    ) : this(
        text = text,
        icon = image.resourceId?.toResourceUri() ?: image.sourceUrl,
        type = type,
        size = size,
        outline = outline,
        classPrefix = classPrefix
    ) {
        this.iconRes = image.resourceId
        this.image = image
    }

    /**
     * Resource-backed [Button] constructor accepting a string or drawable [ResourceID] (`R.string.*` / `R.drawable.*`)
     * and an optional icon [ResourceID].
     */
    constructor(
        textResId: ResourceID,
        iconResId: ResourceID? = null
    ) : this(
        text = if (textResId.type == ResourceType.DRAWABLE && iconResId == null) "" else ResourceResolver.getString(textResId),
        icon = if (textResId.type == ResourceType.DRAWABLE && iconResId == null) textResId.toResourceUri() else iconResId?.toResourceUri()
    ) {
        if (textResId.type == ResourceType.DRAWABLE && iconResId == null) {
            this.iconRes = textResId
        } else {
            this.textRes = textResId
            this.iconRes = iconResId
        }
    }

    /**
     * Icon-only [Button] constructor accepting a typed [ResourceID] (`R.drawable.*` / `RC.drawable.*`) and tooltip.
     */
    constructor(
        iconRes: ResourceID,
        tooltipText: String?,
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
     * Resource-backed [Button] constructor accepting a string [ResourceID] (`R.string.*`), optional drawable [ResourceID],
     * and Bootstrap styling options.
     */
    constructor(
        textRes: ResourceID,
        iconRes: ResourceID?,
        type: BootstrapType?,
        size: BootstrapSize = BootstrapSize.MD,
        outline: Boolean = false
    ) : this(
        text = ResourceResolver.getString(textRes),
        icon = iconRes?.toResourceUri(),
        type = type,
        size = size,
        outline = outline
    ) {
        this.textRes = textRes
        this.iconRes = iconRes
    }

    /**
     * Controls whether button [image] / SVG [icon] drawables are automatically tinted to match the active theme.
     */
    var tintImageToTheme: Boolean = true
        set(value) {
            field = value
            image?.setMonocolorThemeRecolor(value)
            typedNative<ButtonNativeImpl<*>>()?.setIcon(icon)
        }

    /**
     * Optional [Image] displayed inside this button alongside or in place of [text].
     */
    var image: Image? = null
        set(value) {
            val previous = field
            field = value
            if (value != null) {
                if (tintImageToTheme && value.tintMode == com.nexustools.genericui.style.color.ImageTintMode.NONE) {
                    value.setMonocolorThemeRecolor(true)
                }
                val resolvedUri = value.resourceId?.toResourceUri()
                    ?: value.sourceUrl?.takeIf { it.isNotBlank() }
                if (value.resourceId != null && iconRes != value.resourceId) {
                    iconRes = value.resourceId
                } else if (resolvedUri != null && icon != resolvedUri) {
                    icon = resolvedUri
                }
            } else if (previous != null) {
                iconRes = null
                icon = null
            }
            notifyParentCacheDirty()
            typedNative<ButtonNativeImpl<*>>()?.setIcon(icon)
        }

    fun setText(resId: ResourceID, vararg formatArgs: Any) {
        this.textRes = resId
        this.text = ResourceResolver.getString(resId, *formatArgs)
    }

    fun setIcon(resId: ResourceID) {
        this.iconRes = resId
    }

    fun setImage(image: Image?, tintToTheme: Boolean = this.tintImageToTheme) {
        this.tintImageToTheme = tintToTheme
        if (image == null) {
            this.iconRes = null
            this.icon = null
        } else {
            image.setMonocolorThemeRecolor(tintToTheme)
        }
        this.image = image
    }

    fun setImage(resId: ResourceID, tintToTheme: Boolean = this.tintImageToTheme) {
        this.tintImageToTheme = tintToTheme
        this.iconRes = resId
        this.image = Image(resId).apply {
            setMonocolorThemeRecolor(tintToTheme)
        }
    }

    fun setImage(src: String, tintToTheme: Boolean = this.tintImageToTheme) {
        this.tintImageToTheme = tintToTheme
        this.iconRes = null
        this.icon = src
        this.image = Image(src = src).apply {
            setMonocolorThemeRecolor(tintToTheme)
        }
    }

    var textRes: ResourceID? = null
        set(value) {
            field = value
            if (value != null) {
                text = Resources.getString(value)
            }
        }

    var iconRes: ResourceID? = null
        set(value) {
            field = value
            if (value != null) {
                val uri = value.toResourceUri()
                if (icon != uri) {
                    icon = uri
                }
                if (image?.resourceId != value) {
                    image = Image(value).apply {
                        setMonocolorThemeRecolor(tintImageToTheme)
                    }
                }
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
     * Returns `true` when this button has an [icon] or [image] and no [text] caption.
     */
    val isIconOnly: Boolean
        get() = (!icon.isNullOrBlank() || image != null) && text.isBlank()

    init {
        this.type = type
        this.size = size
        this.outline = outline
        if (!icon.isNullOrBlank() && image == null) {
            image = Image(src = icon).apply {
                setMonocolorThemeRecolor(tintImageToTheme)
            }
        }
    }

    /**
     * `kotlinx.html`-style unary plus operator to set/append button caption text.
     */
    operator fun String.unaryPlus() {
        text = if (text.isEmpty()) this else text + this
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super.applyComputedStyle(style)
        image?.onThemeChanged()
    }

    internal open fun onThemeChanged() {
        image?.onThemeChanged()
    }

    protected override fun createNativeImpl(factory: NativeFactory): ButtonNativeImpl<*> =
        factory.createButton(classPrefix)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ButtonNativeImpl<*>>() ?: return
        impl.setText(text)
        impl.setIcon(icon ?: image?.resourceId?.toResourceUri() ?: image?.sourceUrl)
        impl.setTooltipText(tooltipText)
    }
}
