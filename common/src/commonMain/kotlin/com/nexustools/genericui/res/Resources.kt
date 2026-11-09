package com.nexustools.genericui.res

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver
import com.nexustools.genericui.resources.ResourceTable
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry

/**
 * Global Android-like resource manager (`Resources`) providing unified access to compiled
 * `R` / `RC` / `Resource` resources across all GenericUI backends (`windows`, `macos`, `gtk`, `qt`,
 * `swing`, `android`, `ios`, `web`).
 */
object Resources {
    val activeTable: ResourceTable = ResourceTable()

    val GENERICUI: ResourceID = ResourceID(
        id = 0x7f0c0ff1,
        type = ResourceType.STYLE,
        name = "genericui",
        relativePath = "gui/genericui.css"
    )
    val BOOTSTRAP: ResourceID = ResourceID(
        id = 0x7f0c0ff2,
        type = ResourceType.STYLE,
        name = "bootstrap",
        relativePath = "gui/bootstrap.min.css"
    )
    val BOOTSTRAP_JS: ResourceID = ResourceID(
        id = 0x7f070ff1,
        type = ResourceType.RAW,
        name = "bootstrap_js",
        relativePath = "gui/bootstrap.bundle.min.js"
    )
    val BOOTSTRAP_ICONS: ResourceID = ResourceID(
        id = 0x7f0c0ff3,
        type = ResourceType.STYLE,
        name = "bootstrap_icons",
        relativePath = "gui/bootstrap-icons.min.css"
    )
    val GENERICUI_NAMESPACE: ResourceID = ResourceID(
        id = 0x7f090ff1,
        type = ResourceType.XML,
        name = "genericui_namespace",
        relativePath = "genericui-namespace.xml"
    )
    val LOADING_IMAGE: ResourceID = ResourceID(
        id = 0x7f020ff1,
        type = ResourceType.DRAWABLE,
        name = "loading",
        relativePath = "gui/drawable/loading.svg"
    )

    const val DEFAULT_LOADING_SVG: String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 48 48\" width=\"48\" height=\"48\">" +
            "<rect x=\"2\" y=\"2\" width=\"44\" height=\"44\" rx=\"8\" fill=\"#252A36\" stroke=\"#3A4254\" stroke-width=\"1.5\"/>" +
            "<circle cx=\"16\" cy=\"16\" r=\"4\" fill=\"#64748B\"/>" +
            "<path d=\"M8 36L19 23L27 31L33 25L40 36Z\" fill=\"#475569\"/>" +
            "<circle cx=\"24\" cy=\"24\" r=\"14\" fill=\"none\" stroke=\"#94A3B8\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-dasharray=\"22 66\" opacity=\"0.65\"/>" +
            "</svg>"

    val DEFAULT_LOADING_SVG_DATA_URL: String =
        DataUrlCodec.encodeUtf8Text(DEFAULT_LOADING_SVG, "image/svg+xml")

    private val defaultLoadingDrawable: DrawableResource = DrawableResource(
        resId = LOADING_IMAGE,
        format = "svg",
        svgSource = DEFAULT_LOADING_SVG,
        svgPathDataList = listOf("M8 36L19 23L27 31L33 25L40 36Z"),
        viewBoxWidth = 48f,
        viewBoxHeight = 48f
    )

    private val genericUiStyleSheet: PrecompiledStyleSheet = object : PrecompiledStyleSheet {
        override val name: String = "genericui"
        override val sourcePath: String = "gui/genericui.css"
        override val cssResourcePath: String = "gui/genericui.css"
        override val styleSheet: CssStyleSheet = CssStyleSheet.empty()
        override val rawCss: String = ""
    }

    private val bootstrapStyleSheet: PrecompiledStyleSheet = object : PrecompiledStyleSheet {
        override val name: String = "bootstrap"
        override val sourcePath: String = "gui/bootstrap.min.css"
        override val cssResourcePath: String = "gui/bootstrap.min.css"
        override val styleSheet: CssStyleSheet = CssStyleSheet.empty()
        override val rawCss: String = ""
    }

    private val bootstrapIconsStyleSheet: PrecompiledStyleSheet = object : PrecompiledStyleSheet {
        override val name: String = "bootstrap_icons"
        override val sourcePath: String = "gui/bootstrap-icons.min.css"
        override val cssResourcePath: String = "gui/bootstrap-icons.min.css"
        override val styleSheet: CssStyleSheet = CssStyleSheet.empty()
        override val rawCss: String = ""
    }

    init {
        ensureBuiltInResourcesRegistered()
    }

    fun ensureBuiltInResourcesRegistered() {
        activeTable.registerStyleSheet(GENERICUI, genericUiStyleSheet)
        StyleSheetRegistry.register("genericui.css", "gui/genericui.css", GENERICUI, genericUiStyleSheet)
        StyleSheetRegistry.register("gui/genericui.css", "gui/genericui.css", GENERICUI, genericUiStyleSheet)
        StyleSheetRegistry.register("genericui", "styles/genericui.css", GENERICUI, genericUiStyleSheet)
        activeTable.registerStyleSheet(BOOTSTRAP, bootstrapStyleSheet)
        StyleSheetRegistry.register("bootstrap", "gui/bootstrap.min.css", BOOTSTRAP, bootstrapStyleSheet)
        StyleSheetRegistry.register("bootstrap.min", "gui/bootstrap.min.css", BOOTSTRAP, bootstrapStyleSheet)
        StyleSheetRegistry.register("gui/bootstrap.min.css", "gui/bootstrap.min.css", BOOTSTRAP, bootstrapStyleSheet)
        activeTable.registerStyleSheet(BOOTSTRAP_ICONS, bootstrapIconsStyleSheet)
        StyleSheetRegistry.register("bootstrap-icons", "gui/bootstrap-icons.min.css", BOOTSTRAP_ICONS, bootstrapIconsStyleSheet)
        StyleSheetRegistry.register("bootstrap_icons", "gui/bootstrap-icons.min.css", BOOTSTRAP_ICONS, bootstrapIconsStyleSheet)
        StyleSheetRegistry.register("bootstrap-icons.min", "gui/bootstrap-icons.min.css", BOOTSTRAP_ICONS, bootstrapIconsStyleSheet)
        StyleSheetRegistry.register("gui/bootstrap-icons.min.css", "gui/bootstrap-icons.min.css", BOOTSTRAP_ICONS, bootstrapIconsStyleSheet)
        activeTable.registerResourceId(BOOTSTRAP_JS)
        activeTable.registerResourceId(GENERICUI_NAMESPACE)
        activeTable.registerDrawable(defaultLoadingDrawable)
        activeTable.registerRawText(LOADING_IMAGE, DEFAULT_LOADING_SVG)
    }

    fun getString(resId: ResourceID): String = ResourceResolver.getString(resId)

    fun getString(resId: ResourceID, vararg formatArgs: Any): String =
        ResourceResolver.getString(resId, *formatArgs)

    fun getColor(resId: ResourceID): RgbaColor = ResourceResolver.getColor(resId)

    fun getDimen(resId: ResourceID): Float = ResourceResolver.getDimenPx(resId)

    fun getDimenPx(resId: ResourceID): Float = ResourceResolver.getDimenPx(resId)

    fun getInteger(resId: ResourceID): Int = activeTable.getInteger(resId)

    fun getBool(resId: ResourceID): Boolean = activeTable.getBool(resId)

    fun getDrawable(resId: ResourceID): DrawableResource? = activeTable.getDrawable(resId)

    fun getDrawable(iconOrUri: String): DrawableResource? =
        activeTable.getDrawableByName(iconOrUri)

    fun getRawText(resId: ResourceID): String = activeTable.getRawText(resId) ?: ""

    fun getRawBytes(resId: ResourceID): ByteArray? = activeTable.getRawBytes(resId)

    fun getBytes(resId: ResourceID): ByteArray? = ResourceResolver.getBytes(resId)

    fun getBuffer(resId: ResourceID): ByteBuffer? = ResourceResolver.getBuffer(resId)

    suspend fun openReadable(resId: ResourceID): Readable? = ResourceResolver.openReadable(resId)

    suspend fun openStream(resId: ResourceID): Readable? = ResourceResolver.openReadable(resId)

    fun getUrl(resId: ResourceID): String = ResourceResolver.getUrl(resId)

    fun getImageBuffer(
        resId: ResourceID,
        options: ImageDecodeOptions = ImageDecodeOptions()
    ): ImageBuffer? = ResourceResolver.getImageBuffer(resId, options)

    fun getFontPath(resId: ResourceID): String = activeTable.getFontPath(resId) ?: resId.relativePath

    fun getStyleSheet(resId: ResourceID): PrecompiledStyleSheet? =
        activeTable.getStyleSheet(resId)

    /**
     * Resolves a string that may be a `@string/name` or `res://string/name` reference.
     */
    fun resolveStringReference(textOrRef: String): String {
        if (textOrRef.startsWith("@string/") || textOrRef.startsWith("res://string/")) {
            return activeTable.getStringByName(textOrRef) ?: textOrRef
        }
        return textOrRef
    }
}
