package com.nexustools.genericui.component.layout

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.BootstrapAspectRatio
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Responsive aspect-ratio layout helper (`AspectRatioLayout`) that constrains its [host] container and
 * stacked child components (such as a video player surface and its interactive HUD overlay) to a fixed
 * aspect ratio (e.g. `16:9` via [BootstrapAspectRatio.RATIO_16X9], `4:3`, `1:1`, `21:9`, or a custom ratio).
 *
 * On `:web`, maps directly to Bootstrap 5's `.ratio` and `.ratio-16x9` / `.ratio-4x3` / `.ratio-1x1` / `.ratio-21x9`
 * helper classes (plus `--bs-aspect-ratio` for custom ratios).
 */
open class AspectRatioLayout(
    host: AbstractContainer = Container("genericui:aspectratiocontainer", "ratio"),
    ratio: BootstrapAspectRatio? = BootstrapAspectRatio.RATIO_16X9,
    customAspectRatio: Float? = null,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
) : Layout<AspectRatioLayoutImpl>(host, breakpointSpec) {

    constructor(
        ratio: BootstrapAspectRatio = BootstrapAspectRatio.RATIO_16X9,
        breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
    ) : this(
        host = Container("genericui:aspectratiocontainer", "ratio"),
        ratio = ratio,
        customAspectRatio = null,
        breakpointSpec = breakpointSpec
    )

    constructor(
        widthRatio: Float,
        heightRatio: Float,
        breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT
    ) : this(
        host = Container("genericui:aspectratiocontainer", "ratio"),
        ratio = resolveMatchingBootstrapRatio(widthRatio, heightRatio),
        customAspectRatio = if (heightRatio > 0f) widthRatio / heightRatio else 16f / 9f,
        breakpointSpec = breakpointSpec
    )

    var ratio: BootstrapAspectRatio? = ratio
        set(value) {
            field = value
            if (value != null) {
                customAspectRatioField = null
            }
            impl.syncAspectRatio()
        }

    private var customAspectRatioField: Float? = customAspectRatio?.takeIf { it > 0f }

    var customAspectRatio: Float?
        get() = customAspectRatioField
        set(value) {
            val sanitized = value?.takeIf { it > 0f }
            customAspectRatioField = sanitized
            if (sanitized != null) {
                ratio = resolveMatchingBootstrapRatio(sanitized, 1f)
                customAspectRatioField = if (ratio != null) null else sanitized
            }
            impl.syncAspectRatio()
        }

    val effectiveAspectRatio: Float
        get() = customAspectRatioField ?: when (ratio) {
            BootstrapAspectRatio.RATIO_1X1 -> 1f
            BootstrapAspectRatio.RATIO_4X3 -> 4f / 3f
            BootstrapAspectRatio.RATIO_16X9 -> 16f / 9f
            BootstrapAspectRatio.RATIO_21X9 -> 21f / 9f
            null -> 16f / 9f
        }

    var viewportWidthDp: Float = 1280f
        private set

    var viewportHeightDp: Float = 800f
        private set

    var computedHeightPx: Int = 720
        internal set

    init {
        ensureMounted()
    }

    override fun createImpl(factory: LayoutFactory): AspectRatioLayoutImpl =
        factory.createAspectRatioLayout(this)

    override fun onMount() {
        host.getBootstrap().apply {
            position = PositionMode.RELATIVE
            width = BootstrapWidth.W_100
        }
        syncHostAspectRatioClassesAndStyles()
    }

    fun setAspectRatio(ratio: BootstrapAspectRatio): AspectRatioLayout = apply {
        this.ratio = ratio
    }

    fun setAspectRatio(widthRatio: Float, heightRatio: Float): AspectRatioLayout = apply {
        if (heightRatio > 0f && widthRatio > 0f) {
            val matched = resolveMatchingBootstrapRatio(widthRatio, heightRatio)
            if (matched != null) {
                this.customAspectRatioField = null
                this.ratio = matched
            } else {
                this.ratio = null
                this.customAspectRatioField = widthRatio / heightRatio
                impl.syncAspectRatio()
            }
        }
    }

    fun addContent(component: AbstractComponent, index: Int = -1): AspectRatioLayout = apply {
        host.addChild(component, index)
        formatAspectChild(component)
    }

    fun formatAspectChild(component: AbstractComponent) {
        if (component.positionMode == PositionMode.RELATIVE) {
            component.positionMode = null
        }
        component.getStyle().apply {
            setPosition(com.nexustools.genericui.enums.css.CssPosition.ABSOLUTE)
            setTop(com.nexustools.genericui.style.css.CssLength.ZERO)
            setLeft(com.nexustools.genericui.style.css.CssLength.ZERO)
            setWidth(com.nexustools.genericui.style.css.CssLength.PERCENT_100)
            setHeight(com.nexustools.genericui.style.css.CssLength.PERCENT_100)
        }
    }

    fun syncHostAspectRatioClassesAndStyles() {
        host.addClass("ratio")
        for (entry in BootstrapAspectRatio.entries) {
            if (entry != ratio || customAspectRatioField != null) {
                host.removeClass(entry.cssClass)
            }
        }
        val activeRatio = ratio
        if (customAspectRatioField == null && activeRatio != null) {
            host.addClass(activeRatio.cssClass)
        }
        val numericRatio = effectiveAspectRatio
        host.aspectRatio = numericRatio
        val scaled = ((1f / numericRatio) * 10000f).roundToInt()
        val percentStr = if (scaled % 100 == 0) {
            "${scaled / 100}%"
        } else {
            "${scaled / 100f}%"
        }
        host.setAttribute("data-bs-aspect-ratio", percentStr)
        host.getStyle().setWidth(com.nexustools.genericui.style.css.CssLength.PERCENT_100)
        for (child in host.children) {
            formatAspectChild(child)
        }
    }

    fun updateViewport(
        viewportWidthDp: Float,
        viewportHeightDp: Float,
        smallestScreenWidthDp: Int = min(viewportWidthDp.toInt(), viewportHeightDp.toInt())
    ) {
        this.viewportWidthDp = viewportWidthDp
        this.viewportHeightDp = viewportHeightDp
        impl.syncLayout(viewportWidthDp, viewportHeightDp, smallestScreenWidthDp)
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        updateViewport(
            viewportWidthDp = viewportWidth.toFloat(),
            viewportHeightDp = viewportHeight.toFloat(),
            smallestScreenWidthDp = min(viewportWidth, viewportHeight)
        )
    }

    override fun onConfigurationChanged(config: WindowConfiguration) {
        super.onConfigurationChanged(config)
        val widthDp = breakpointSpec.toDp(config.widthPx, config.densityScale).toFloat()
        val heightDp = breakpointSpec.toDp(config.heightPx, config.densityScale).toFloat()
        updateViewport(widthDp, heightDp, config.smallestScreenWidthDp)
    }

    companion object {
        fun resolveMatchingBootstrapRatio(widthRatio: Float, heightRatio: Float): BootstrapAspectRatio? {
            if (heightRatio <= 0f || widthRatio <= 0f) return null
            val q = widthRatio / heightRatio
            return when {
                kotlin.math.abs(q - 1f) < 0.01f -> BootstrapAspectRatio.RATIO_1X1
                kotlin.math.abs(q - (4f / 3f)) < 0.01f -> BootstrapAspectRatio.RATIO_4X3
                kotlin.math.abs(q - (16f / 9f)) < 0.01f -> BootstrapAspectRatio.RATIO_16X9
                kotlin.math.abs(q - (21f / 9f)) < 0.02f -> BootstrapAspectRatio.RATIO_21X9
                else -> null
            }
        }
    }
}
