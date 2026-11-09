package com.nexustools.genericui.component.tabs

import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.css.ComputedStyle

/**
 * Base tab header component inside a [TabContainer] or [FragmentTabContainer].
 *
 * A [Tab] is a first-class component that is translated to a native tab item (`JTabbedPane` tab / custom tab
 * component on `:swing`, `TCITEMW` on `:windows`, `NSTabViewItem` on `:macos`, `AdwTabPage` / `GtkNotebookPage`
 * on `:gtk`, `QTabBar` item on `:qt`, `TabLayout.Tab` on `:android`, `UITabBarItem` on `:ios`, and `.nav-link`
 * on `:web`).
 *
 * Supports canonical [image] / [iconRes] / [icon] icons with configurable [iconSizePx], [iconPlacement]
 * ([TabIconPlacement.START] or [TabIconPlacement.TOP]), and automatic theme recoloring ([tintImageToTheme]),
 * as well as embedding any arbitrary [customComponent] inside the tab header.
 */
open class Tab(
    val tabId: String = "tab_${nextTabCounter()}",
    title: String = "",
    icon: String? = null,
    tooltip: String? = null,
    customComponent: AbstractComponent? = null,
    iconRes: ResourceID? = null,
    image: Image? = null,
    tintImageToTheme: Boolean = true,
    iconSizePx: Int = image?.widthPx ?: image?.heightPx ?: 20,
    iconPlacement: TabIconPlacement = TabIconPlacement.START
) : AbstractContainer() {

    var title: String = title
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setTitleNative(displayTitle)
            ownerNotifyUpdated?.invoke(this)
        }

    /**
     * Controls whether tab [image] / SVG [icon] drawables are automatically tinted to match the active theme.
     */
    var tintImageToTheme: Boolean = tintImageToTheme
        set(value) {
            field = value
            image?.setMonocolorThemeRecolor(value)
            typedNative<TabNativeImpl<*>>()?.setTintImageToThemeNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    var iconSizePx: Int = iconSizePx.coerceAtLeast(1)
        set(value) {
            val clamped = value.coerceAtLeast(1)
            field = clamped
            typedNative<TabNativeImpl<*>>()?.setIconSizePxNative(clamped)
            ownerNotifyUpdated?.invoke(this)
        }

    var iconPlacement: TabIconPlacement = iconPlacement
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setIconPlacementNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    var icon: String? = icon
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setIconNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    var iconRes: ResourceID? = iconRes
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
            ownerNotifyUpdated?.invoke(this)
        }

    /**
     * Optional [Image] displayed inside this tab header alongside or above [title].
     */
    var image: Image? = null
        set(value) {
            val previous = field
            field = value
            if (value != null) {
                if (tintImageToTheme && value.tintMode == ImageTintMode.NONE) {
                    value.setMonocolorThemeRecolor(true)
                }
                val explicitSize = value.widthPx ?: value.heightPx
                if (explicitSize != null && explicitSize > 0) {
                    iconSizePx = explicitSize
                }
                val resolvedUri = value.resourceId?.toResourceUri()
                    ?: value.sourceUrl?.takeIf { it.isNotBlank() }
                if (value.resourceId != null && iconRes != value.resourceId) {
                    iconRes = value.resourceId
                }
                if (resolvedUri != null && icon != resolvedUri) {
                    icon = resolvedUri
                }
            } else if (previous != null) {
                iconRes = null
                icon = null
            }
            typedNative<TabNativeImpl<*>>()?.setIconNative(icon)
            ownerNotifyUpdated?.invoke(this)
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

    var tooltip: String? = tooltip
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setTooltipNative(value)
            ownerNotifyUpdated?.invoke(this)
        }

    var isDirty: Boolean = false
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setDirtyNative(value)
            typedNative<TabNativeImpl<*>>()?.setTitleNative(displayTitle)
            ownerNotifyUpdated?.invoke(this)
        }

    open val isSelectable: Boolean = true
    open val supportsContent: Boolean = true

    open var isSelected: Boolean = false
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setSelectedNative(value)
        }

    var customComponent: AbstractComponent? = null
        set(value) {
            field?.let { removeChild(it) }
            field = value
            if (value != null) {
                addChild(value)
            }
            typedNative<TabNativeImpl<*>>()?.setCustomHeaderComponentNative(value?.nativeImpl)
            ownerNotifyUpdated?.invoke(this)
        }

    val displayTitle: String
        get() = if (isDirty && !title.startsWith("*")) "*$title" else title

    var onSelected: (() -> Unit)? = null

    internal var ownerNotifyUpdated: ((Tab) -> Unit)? = null
    internal var ownerRequestClose: ((Tab) -> Boolean)? = null

    internal val nativeTabImpl: TabNativeImpl<*>?
        get() = typedNative<TabNativeImpl<*>>()

    init {
        htmlTag = "genericui:tab"
        if (id.isNullOrEmpty()) {
            id = tabId
        }
        val initialSizePx = iconSizePx.coerceAtLeast(1)
        when {
            image != null -> {
                this.image = image
                if (image.widthPx == null && image.heightPx == null) {
                    this.iconSizePx = initialSizePx
                }
            }
            iconRes != null -> {
                val uri = iconRes.toResourceUri()
                if (this.icon.isNullOrBlank()) {
                    this.icon = uri
                }
                this.image = Image(iconRes).apply {
                    setMonocolorThemeRecolor(tintImageToTheme)
                }
                this.iconSizePx = initialSizePx
            }
            !icon.isNullOrBlank() -> {
                this.image = Image(src = icon).apply {
                    setMonocolorThemeRecolor(tintImageToTheme)
                }
                this.iconSizePx = initialSizePx
            }
        }
        if (customComponent != null) {
            this.customComponent = customComponent
        }
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super.applyComputedStyle(style)
        image?.onThemeChanged()
    }

    protected override fun createNativeImpl(factory: NativeFactory): TabNativeImpl<*> =
        factory.createTab(closable = false)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabNativeImpl<*>>() ?: return
        impl.setTabIdNative(tabId)
        impl.setTitleNative(displayTitle)
        impl.setIconNative(icon ?: image?.resourceId?.toResourceUri() ?: image?.sourceUrl)
        impl.setIconSizePxNative(iconSizePx)
        impl.setIconPlacementNative(iconPlacement)
        impl.setTintImageToThemeNative(tintImageToTheme)
        impl.setTooltipNative(tooltip)
        impl.setClosableNative(false)
        impl.setDirtyNative(isDirty)
        impl.setSelectedNative(isSelected)
        impl.setCustomHeaderComponentNative(customComponent?.nativeImpl)
    }

    companion object {
        private var counter: Long = 0L
        internal fun nextTabCounter(): Long = ++counter
    }
}
