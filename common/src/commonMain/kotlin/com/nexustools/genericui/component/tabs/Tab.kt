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
 * on `:gtk`, `QTabBar` item on `:kde`, `TabLayout.Tab` on `:android`, `UITabBarItem` on `:ios`, and `.nav-link`
 * on `:web`).
 *
 * Supports canonical [image] / [iconRes] / [icon] icons with configurable [iconSizePx], [iconPlacement]
 * ([TabIconPlacement.START] or [TabIconPlacement.TOP]), automatic theme recoloring ([tintImageToTheme]),
 * optional native close button ([isCloseable], [onCloseRequested], [onClosed]), as well as embedding any
 * arbitrary [customComponent] inside the tab header.
 */
open class Tab(
    val tabId: String = "tab_${nextTabCounter()}",
    title: String = "",
    icon: String? = null,
    tooltip: String? = null,
    customComponent: AbstractComponent? = null,
    iconRes: ResourceID? = null,
    image: Image? = null,
    tintImageToTheme: Boolean? = null,
    iconSizePx: Int = image?.widthPx ?: image?.heightPx ?: 20,
    iconPlacement: TabIconPlacement = TabIconPlacement.START,
    closable: Boolean = false,
    onCloseRequested: (() -> Boolean)? = null,
    onClosed: (() -> Unit)? = null
) : AbstractContainer() {

    private var explicitTintImageToTheme: Boolean? = tintImageToTheme
    private var suppressExplicitTintCapture: Boolean = false
    private var isSyncingIconState: Boolean = false

    var title: String = title
        set(value) {
            field = value
            typedNative<TabNativeImpl<*>>()?.setTitleNative(displayTitle)
            ownerNotifyUpdated?.invoke(this)
        }

    /**
     * Controls whether tab [image] / SVG [icon] drawables are automatically tinted to match the active theme.
     *
     * Defaults to `true` for monocolor/vector drawables and automatically defaults to `false` when a
     * remote HTTP(S), `data:image/`, or raster (`.png`, `.ico`, `.jpg`, `.webp`, `.gif`, `.bmp`) source
     * is supplied without explicitly specifying [tintImageToTheme].
     */
    var tintImageToTheme: Boolean = tintImageToTheme ?: run {
        val initialSource = icon
            ?: iconRes?.relativePath?.takeIf { it.isNotBlank() }
            ?: iconRes?.toResourceUri()
            ?: image?.resourceId?.relativePath?.takeIf { it.isNotBlank() }
            ?: image?.resourceId?.toResourceUri()
            ?: image?.sourceUrl
        if (image != null && image.tintMode != ImageTintMode.NONE) {
            true
        } else {
            !Companion.isRasterOrRemoteColorIcon(initialSource)
        }
    }
        set(value) {
            if (!suppressExplicitTintCapture) {
                explicitTintImageToTheme = value
            }
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
            if (!isSyncingIconState) {
                val resolvedTint = resolveTintForSource(value, null)
                applyResolvedTint(resolvedTint, explicit = false)
                typedNative<TabNativeImpl<*>>()?.setTintImageToThemeNative(this.tintImageToTheme)
            }
            typedNative<TabNativeImpl<*>>()?.setIconNative(value)
            if (!isSyncingIconState) {
                ownerNotifyUpdated?.invoke(this)
            }
        }

    var iconRes: ResourceID? = iconRes
        set(value) {
            field = value
            if (isSyncingIconState) return
            if (value != null) {
                val uri = value.toResourceUri()
                val sourceForTint = value.relativePath.takeIf { it.isNotBlank() } ?: uri
                val resolvedTint = resolveTintForSource(sourceForTint, null)
                applyResolvedTint(resolvedTint, explicit = false)
                isSyncingIconState = true
                try {
                    if (icon != uri) {
                        icon = uri
                    }
                    if (image?.resourceId != value) {
                        image = Image(value).apply {
                            setMonocolorThemeRecolor(this@Tab.tintImageToTheme)
                        }
                    }
                } finally {
                    isSyncingIconState = false
                }
                typedNative<TabNativeImpl<*>>()?.let { impl ->
                    impl.setTintImageToThemeNative(this.tintImageToTheme)
                    impl.setIconNative(uri)
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
            if (isSyncingIconState) return
            if (value != null) {
                val resolvedUri = value.resourceId?.toResourceUri()
                    ?: value.sourceUrl?.takeIf { it.isNotBlank() }
                val sourceForTint = value.resourceId?.relativePath?.takeIf { it.isNotBlank() } ?: resolvedUri
                val resolvedTint = resolveTintForSource(sourceForTint, value)
                applyResolvedTint(resolvedTint, explicit = false)
                if (resolvedTint && value.tintMode == ImageTintMode.NONE) {
                    value.setMonocolorThemeRecolor(true)
                }
                val explicitSize = value.widthPx ?: value.heightPx
                if (explicitSize != null && explicitSize > 0) {
                    iconSizePx = explicitSize
                }
                isSyncingIconState = true
                try {
                    if (value.resourceId != null && iconRes != value.resourceId) {
                        iconRes = value.resourceId
                    }
                    if (resolvedUri != null && icon != resolvedUri) {
                        icon = resolvedUri
                    }
                } finally {
                    isSyncingIconState = false
                }
                typedNative<TabNativeImpl<*>>()?.let { impl ->
                    impl.setTintImageToThemeNative(this.tintImageToTheme)
                    impl.setIconSizePxNative(iconSizePx)
                    impl.setIconNative(icon ?: resolvedUri)
                }
            } else if (previous != null) {
                isSyncingIconState = true
                try {
                    iconRes = null
                    icon = null
                } finally {
                    isSyncingIconState = false
                }
                typedNative<TabNativeImpl<*>>()?.setIconNative(null)
            }
            ownerNotifyUpdated?.invoke(this)
        }

    /**
     * Returns `true` when [source] looks like a remote URL or raster image that should not be monocolor-tinted
     * by default unless explicitly requested.
     */
    fun isRasterOrRemoteColorIcon(source: String?): Boolean = Companion.isRasterOrRemoteColorIcon(source)

    private fun resolveAutoTint(source: String?, targetImage: Image?): Boolean {
        if (targetImage != null && targetImage.tintMode != ImageTintMode.NONE) {
            return true
        }
        return !Companion.isRasterOrRemoteColorIcon(source)
    }

    private fun resolveTintForSource(source: String?, targetImage: Image?): Boolean {
        explicitTintImageToTheme?.let { return it }
        return resolveAutoTint(source, targetImage)
    }

    private fun applyResolvedTint(value: Boolean, explicit: Boolean) {
        if (explicit) {
            explicitTintImageToTheme = value
        }
        suppressExplicitTintCapture = true
        try {
            if (tintImageToTheme != value) {
                tintImageToTheme = value
            } else {
                image?.setMonocolorThemeRecolor(value)
                typedNative<TabNativeImpl<*>>()?.setTintImageToThemeNative(value)
            }
        } finally {
            suppressExplicitTintCapture = false
        }
    }

    fun setIcon(resId: ResourceID) {
        this.iconRes = resId
    }

    fun setImage(image: Image?, tintToTheme: Boolean? = null) {
        if (tintToTheme != null) {
            explicitTintImageToTheme = tintToTheme
        }
        if (image == null) {
            if (tintToTheme != null) {
                applyResolvedTint(tintToTheme, explicit = true)
            }
            isSyncingIconState = true
            try {
                this.iconRes = null
                this.icon = null
                this.image = null
            } finally {
                isSyncingIconState = false
            }
            typedNative<TabNativeImpl<*>>()?.setIconNative(null)
            ownerNotifyUpdated?.invoke(this)
            return
        }
        val resolvedSource = image.resourceId?.relativePath?.takeIf { it.isNotBlank() }
            ?: image.resourceId?.toResourceUri()
            ?: image.sourceUrl
        val resolvedTint = tintToTheme ?: resolveTintForSource(resolvedSource, image)
        applyResolvedTint(resolvedTint, explicit = tintToTheme != null)
        if (tintToTheme != null || resolvedTint) {
            image.setMonocolorThemeRecolor(resolvedTint)
        }
        this.image = image
    }

    fun setImage(resId: ResourceID, tintToTheme: Boolean? = null) {
        val uri = resId.toResourceUri()
        val sourceForTint = resId.relativePath.takeIf { it.isNotBlank() } ?: uri
        val resolvedTint = tintToTheme ?: resolveTintForSource(sourceForTint, null)
        applyResolvedTint(resolvedTint, explicit = tintToTheme != null)
        isSyncingIconState = true
        try {
            this.iconRes = resId
            this.icon = uri
            this.image = Image(resId).apply {
                setMonocolorThemeRecolor(resolvedTint)
            }
        } finally {
            isSyncingIconState = false
        }
        typedNative<TabNativeImpl<*>>()?.let { impl ->
            impl.setTintImageToThemeNative(this.tintImageToTheme)
            impl.setIconNative(uri)
        }
        ownerNotifyUpdated?.invoke(this)
    }

    fun setImage(src: String, tintToTheme: Boolean? = null) {
        val resolvedTint = tintToTheme ?: resolveTintForSource(src, null)
        applyResolvedTint(resolvedTint, explicit = tintToTheme != null)
        isSyncingIconState = true
        try {
            this.iconRes = null
            this.icon = src
            this.image = Image(src = src).apply {
                setMonocolorThemeRecolor(resolvedTint)
            }
        } finally {
            isSyncingIconState = false
        }
        typedNative<TabNativeImpl<*>>()?.let { impl ->
            impl.setTintImageToThemeNative(this.tintImageToTheme)
            impl.setIconNative(src)
        }
        ownerNotifyUpdated?.invoke(this)
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

    open var isCloseable: Boolean = closable
        set(value) {
            field = value
            val impl = typedNative<TabNativeImpl<*>>()
            if (impl != null) {
                impl.setClosableNative(value)
                if (value) {
                    impl.setOnCloseClicked {
                        requestClose()
                    }
                }
            }
            ownerNotifyUpdated?.invoke(this)
        }

    var isClosable: Boolean
        get() = isCloseable
        set(value) {
            isCloseable = value
        }

    open var onCloseRequested: (() -> Boolean)? = onCloseRequested
    open var onClosed: (() -> Unit)? = onClosed

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
                if (this@Tab.tintImageToTheme && image.tintMode == ImageTintMode.NONE) {
                    image.setMonocolorThemeRecolor(true)
                }
                isSyncingIconState = true
                try {
                    this.image = image
                    val resolvedUri = image.resourceId?.toResourceUri()
                        ?: image.sourceUrl?.takeIf { it.isNotBlank() }
                    if (image.resourceId != null) {
                        this.iconRes = image.resourceId
                    }
                    if (resolvedUri != null && this.icon.isNullOrBlank()) {
                        this.icon = resolvedUri
                    }
                } finally {
                    isSyncingIconState = false
                }
                if (image.widthPx == null && image.heightPx == null) {
                    this.iconSizePx = initialSizePx
                }
            }
            iconRes != null -> {
                val uri = iconRes.toResourceUri()
                isSyncingIconState = true
                try {
                    if (this.icon.isNullOrBlank()) {
                        this.icon = uri
                    }
                    this.image = Image(iconRes).apply {
                        setMonocolorThemeRecolor(this@Tab.tintImageToTheme)
                    }
                } finally {
                    isSyncingIconState = false
                }
                this.iconSizePx = initialSizePx
            }
            !icon.isNullOrBlank() -> {
                isSyncingIconState = true
                try {
                    this.image = Image(src = icon).apply {
                        setMonocolorThemeRecolor(this@Tab.tintImageToTheme)
                    }
                } finally {
                    isSyncingIconState = false
                }
                this.iconSizePx = initialSizePx
            }
        }
        if (customComponent != null) {
            this.customComponent = customComponent
        }
    }

    /**
     * Requests closing this tab. Invokes [onCloseRequested] (if set) to allow vetoing (e.g. unsaved changes prompt),
     * then notifies the owning [TabContainer] or [FragmentTabContainer] to remove the tab and invokes [onClosed].
     */
    open fun requestClose(): Boolean {
        if (!isCloseable) return false
        val allowed = onCloseRequested?.invoke() ?: true
        if (!allowed) return false
        val closedByOwner = ownerRequestClose?.invoke(this) ?: true
        if (closedByOwner) {
            onClosed?.invoke()
        }
        return closedByOwner
    }

    override val supportsFocus: Boolean
        get() = true

    internal override fun onComputedStyleChanged(previous: ComputedStyle?, current: ComputedStyle) {
        image?.onThemeChanged()
    }

    protected override fun createNativeImpl(factory: NativeFactory): TabNativeImpl<*> =
        factory.createTab(closable = isCloseable)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TabNativeImpl<*>>() ?: return
        impl.setTabIdNative(tabId)
        impl.setTitleNative(displayTitle)
        impl.setTintImageToThemeNative(tintImageToTheme)
        impl.setIconSizePxNative(iconSizePx)
        impl.setIconPlacementNative(iconPlacement)
        impl.setIconNative(icon ?: image?.resourceId?.toResourceUri() ?: image?.sourceUrl)
        impl.setTooltipNative(tooltip)
        impl.setClosableNative(isCloseable)
        impl.setDirtyNative(isDirty)
        impl.setSelectedNative(isSelected)
        impl.setCustomHeaderComponentNative(customComponent?.nativeImpl)
        impl.setOnCloseClicked {
            requestClose()
        }
    }

    companion object {
        private var counter: Long = 0L
        internal fun nextTabCounter(): Long = ++counter

        /**
         * Returns `true` when [source] is a remote HTTP(S) URL, a non-SVG `data:image/` URI, or a
         * raster image path/extension (`.ico`, `.png`, `.jpg`, `.jpeg`, `.webp`, `.gif`, `.bmp`).
         */
        fun isRasterOrRemoteColorIcon(source: String?): Boolean {
            val trimmed = source?.trim()?.takeIf { it.isNotEmpty() } ?: return false
            val lower = trimmed.lowercase()
            if (lower.startsWith("data:image/svg")) return false
            val path = lower.substringBefore('?').substringBefore('#')
            if (path.endsWith(".svg") || path.endsWith(".svgz")) return false
            if (lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("data:image/")
            ) {
                return true
            }
            return path.endsWith(".ico") ||
                path.endsWith(".png") ||
                path.endsWith(".jpg") ||
                path.endsWith(".jpeg") ||
                path.endsWith(".webp") ||
                path.endsWith(".gif") ||
                path.endsWith(".bmp")
        }
    }
}
