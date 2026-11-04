package com.nexustools.genericui.resources

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * In-memory cross-platform resource table populated at startup by the Gradle-generated
 * `GeneratedResourceRegistry` (and extendable dynamically at runtime).
 */
class ResourceTable {
    private val byNumericId = mutableMapOf<Int, ResourceID>()
    private val byTypeAndName = mutableMapOf<Pair<ResourceType, String>, ResourceID>()

    private val strings = mutableMapOf<Int, String>()
    private val colors = mutableMapOf<Int, RgbaColor>()
    private val dimens = mutableMapOf<Int, Float>()
    private val integers = mutableMapOf<Int, Int>()
    private val bools = mutableMapOf<Int, Boolean>()
    private val drawables = mutableMapOf<Int, DrawableResource>()
    private val rawTexts = mutableMapOf<Int, String>()
    private val rawBase64 = mutableMapOf<Int, String>()
    private val rawBytes = mutableMapOf<Int, ByteArray>()
    private val fontPaths = mutableMapOf<Int, String>()
    private val styleSheets = mutableMapOf<Int, PrecompiledStyleSheet>()

    fun registerResourceId(resId: ResourceID) {
        byNumericId[resId.id] = resId
        byTypeAndName[resId.type to resId.name] = resId
    }

    fun registerString(resId: ResourceID, value: String) {
        registerResourceId(resId)
        strings[resId.id] = value
    }

    fun registerColor(resId: ResourceID, colorHexOrCss: String) {
        registerResourceId(resId)
        colors[resId.id] = RgbaColor.fromHex(colorHexOrCss)
    }

    fun registerColor(resId: ResourceID, color: RgbaColor) {
        registerResourceId(resId)
        colors[resId.id] = color
    }

    fun registerDimen(resId: ResourceID, valuePxOrDp: Float) {
        registerResourceId(resId)
        dimens[resId.id] = valuePxOrDp
    }

    fun registerInteger(resId: ResourceID, value: Int) {
        registerResourceId(resId)
        integers[resId.id] = value
    }

    fun registerBool(resId: ResourceID, value: Boolean) {
        registerResourceId(resId)
        bools[resId.id] = value
    }

    fun registerDrawable(drawable: DrawableResource) {
        registerResourceId(drawable.resId)
        drawables[drawable.resId.id] = drawable
    }

    fun registerRawText(resId: ResourceID, content: String) {
        registerResourceId(resId)
        rawTexts[resId.id] = content
    }

    fun registerRawBase64(resId: ResourceID, base64: String) {
        registerResourceId(resId)
        rawBase64[resId.id] = base64
    }

    fun registerRawBytes(resId: ResourceID, bytes: ByteArray) {
        registerResourceId(resId)
        rawBytes[resId.id] = bytes
    }

    fun registerFont(resId: ResourceID, path: String) {
        registerResourceId(resId)
        fontPaths[resId.id] = path
    }

    fun registerStyleSheet(resId: ResourceID, styleSheet: PrecompiledStyleSheet) {
        registerResourceId(resId)
        styleSheets[resId.id] = styleSheet
        StyleSheetRegistry.register(resId.name, resId.relativePath.ifBlank { styleSheet.cssResourcePath }, resId, styleSheet)
    }

    fun registerStyle(resId: ResourceID, pathOrCss: String) {
        registerResourceId(resId)
        rawTexts[resId.id] = pathOrCss
    }

    fun findResourceId(id: Int): ResourceID? = byNumericId[id]

    fun findResourceId(type: ResourceType, name: String): ResourceID? =
        byTypeAndName[type to normalizeName(name)]

    fun getStringOrNull(resId: ResourceID): String? =
        strings[resId.id] ?: byTypeAndName[ResourceType.STRING to resId.name]?.let { strings[it.id] }

    fun getString(resId: ResourceID): String =
        getStringOrNull(resId) ?: resId.name

    fun getString(id: Int): String =
        strings[id] ?: byNumericId[id]?.name ?: ""

    fun getStringByName(name: String): String? =
        findResourceId(ResourceType.STRING, name)?.let { strings[it.id] }

    fun getColorOrNull(resId: ResourceID): RgbaColor? =
        colors[resId.id] ?: byTypeAndName[ResourceType.COLOR to resId.name]?.let { colors[it.id] }

    fun getColor(resId: ResourceID): RgbaColor =
        getColorOrNull(resId) ?: RgbaColor.BLACK

    fun getColorByName(name: String): RgbaColor? =
        findResourceId(ResourceType.COLOR, name)?.let { colors[it.id] }

    fun getDimenOrNull(resId: ResourceID): Float? =
        dimens[resId.id] ?: byTypeAndName[ResourceType.DIMEN to resId.name]?.let { dimens[it.id] }

    fun getDimen(resId: ResourceID): Float =
        getDimenOrNull(resId) ?: 0f

    fun getInteger(resId: ResourceID): Int =
        integers[resId.id] ?: byTypeAndName[ResourceType.INTEGER to resId.name]?.let { integers[it.id] } ?: 0

    fun getBool(resId: ResourceID): Boolean =
        bools[resId.id] ?: byTypeAndName[ResourceType.BOOL to resId.name]?.let { bools[it.id] } ?: false

    fun getDrawable(resId: ResourceID): DrawableResource? =
        drawables[resId.id] ?: byTypeAndName[ResourceType.DRAWABLE to resId.name]?.let { drawables[it.id] }

    fun getDrawableByName(name: String): DrawableResource? =
        findResourceId(ResourceType.DRAWABLE, name)?.let { drawables[it.id] }

    fun getRawText(resId: ResourceID): String? =
        rawTexts[resId.id] ?: byTypeAndName[ResourceType.RAW to resId.name]?.let { rawTexts[it.id] }

    fun getRawBase64(resId: ResourceID): String? =
        rawBase64[resId.id] ?: byTypeAndName[ResourceType.RAW to resId.name]?.let { rawBase64[it.id] }

    fun getRawBytes(resId: ResourceID): ByteArray? {
        rawBytes[resId.id]?.let { return it }
        byTypeAndName[resId.type to resId.name]?.let { matched ->
            rawBytes[matched.id]?.let { return it }
        }
        getRawBase64(resId)?.let { b64 ->
            decodeBase64Safe(b64)?.let { return it }
        }
        getRawText(resId)?.let { txt ->
            return txt.encodeToByteArray()
        }
        getDrawable(resId)?.let { drawable ->
            drawable.base64Data?.let { b64 ->
                decodeBase64Safe(b64)?.let { return it }
            }
            drawable.svgSource?.let { svg ->
                return svg.encodeToByteArray()
            }
        }
        return null
    }

    fun getBytes(resId: ResourceID): ByteArray? = getRawBytes(resId)

    fun getUrl(resId: ResourceID): String {
        val registered = byNumericId[resId.id]
            ?: byTypeAndName[resId.type to resId.name]
            ?: resId
        val rel = registered.relativePath.trim()
        if (rel.isNotEmpty()) {
            val fileName = rel.substringAfterLast('/')
            if (fileName.isNotEmpty()) {
                return "assets/${registered.type.directoryName}/$fileName"
            }
        }
        return registered.toResourceUri()
    }

    fun getImageBuffer(resId: ResourceID): ImageBuffer? {
        val bytes = getRawBytes(resId) ?: return null
        if (bytes.isEmpty()) return null
        return ImageLoader.loadFromBytes(bytes)
    }

    fun getFontPath(resId: ResourceID): String? =
        fontPaths[resId.id] ?: byTypeAndName[ResourceType.FONT to resId.name]?.let { fontPaths[it.id] }

    fun getStyleSheet(resId: ResourceID): PrecompiledStyleSheet? =
        styleSheets[resId.id]
            ?: byTypeAndName[ResourceType.STYLE to resId.name]?.let { styleSheets[it.id] }
            ?: StyleSheetRegistry.resolveByResourceId(resId)

    fun allDrawables(): Collection<DrawableResource> = drawables.values

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64Safe(base64: String): ByteArray? = try {
        Base64.decode(base64.trim())
    } catch (_: Throwable) {
        null
    }

    companion object {
        fun normalizeName(raw: String): String =
            raw.trim()
                .removePrefix("res://drawable/")
                .removePrefix("res://string/")
                .removePrefix("res://color/")
                .removePrefix("res://dimen/")
                .removePrefix("res://raw/")
                .removePrefix("res://font/")
                .removePrefix("res://style/")
                .removePrefix("@drawable/")
                .removePrefix("@string/")
                .removePrefix("@color/")
                .removePrefix("@dimen/")
                .removePrefix("@raw/")
                .removePrefix("@font/")
                .removePrefix("@style/")
                .substringBeforeLast('.')
                .replace('-', '_')
                .replace(' ', '_')
                .lowercase()
    }
}
