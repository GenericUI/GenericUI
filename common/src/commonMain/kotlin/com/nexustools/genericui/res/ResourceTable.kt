package com.nexustools.genericui.res

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.style.RgbaColor
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.css.StyleSheetRegistry
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * In-memory cross-platform resource table populated at startup by the Gradle-generated
 * `GeneratedResourceRegistry` (and extendable dynamically at runtime).
 */
class ResourceTable {
    private val byNumericId = mutableMapOf<Int, ResourceId>()
    private val byTypeAndName = mutableMapOf<Pair<ResourceType, String>, ResourceId>()

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

    fun registerResourceId(resId: ResourceId) {
        byNumericId[resId.id] = resId
        byTypeAndName[resId.type to resId.name] = resId
    }

    fun registerString(resId: ResourceId, value: String) {
        registerResourceId(resId)
        strings[resId.id] = value
    }

    fun registerColor(resId: ResourceId, colorHexOrCss: String) {
        registerResourceId(resId)
        colors[resId.id] = RgbaColor.fromHex(colorHexOrCss)
    }

    fun registerColor(resId: ResourceId, color: RgbaColor) {
        registerResourceId(resId)
        colors[resId.id] = color
    }

    fun registerDimen(resId: ResourceId, valuePxOrDp: Float) {
        registerResourceId(resId)
        dimens[resId.id] = valuePxOrDp
    }

    fun registerInteger(resId: ResourceId, value: Int) {
        registerResourceId(resId)
        integers[resId.id] = value
    }

    fun registerBool(resId: ResourceId, value: Boolean) {
        registerResourceId(resId)
        bools[resId.id] = value
    }

    fun registerDrawable(drawable: DrawableResource) {
        registerResourceId(drawable.resId)
        drawables[drawable.resId.id] = drawable
    }

    fun registerRawText(resId: ResourceId, content: String) {
        registerResourceId(resId)
        rawTexts[resId.id] = content
    }

    fun registerRawBase64(resId: ResourceId, base64: String) {
        registerResourceId(resId)
        rawBase64[resId.id] = base64
    }

    fun registerRawBytes(resId: ResourceId, bytes: ByteArray) {
        registerResourceId(resId)
        rawBytes[resId.id] = bytes
    }

    fun registerFont(resId: ResourceId, path: String) {
        registerResourceId(resId)
        fontPaths[resId.id] = path
    }

    fun registerStyleSheet(resId: ResourceId, styleSheet: PrecompiledStyleSheet) {
        registerResourceId(resId)
        styleSheets[resId.id] = styleSheet
        StyleSheetRegistry.register(resId.name, resId.relativePath.ifBlank { styleSheet.cssResourcePath }, resId, styleSheet)
    }

    fun registerStyle(resId: ResourceId, pathOrCss: String) {
        registerResourceId(resId)
        rawTexts[resId.id] = pathOrCss
    }

    fun findResourceId(id: Int): ResourceId? = byNumericId[id]

    fun findResourceId(type: ResourceType, name: String): ResourceId? =
        byTypeAndName[type to normalizeName(name)]

    fun getStringOrNull(resId: ResourceId): String? =
        strings[resId.id] ?: byTypeAndName[ResourceType.STRING to resId.name]?.let { strings[it.id] }

    fun getString(resId: ResourceId): String =
        getStringOrNull(resId) ?: resId.name

    fun getString(id: Int): String =
        strings[id] ?: byNumericId[id]?.name ?: ""

    fun getStringByName(name: String): String? =
        findResourceId(ResourceType.STRING, name)?.let { strings[it.id] }

    fun getColorOrNull(resId: ResourceId): RgbaColor? =
        colors[resId.id] ?: byTypeAndName[ResourceType.COLOR to resId.name]?.let { colors[it.id] }

    fun getColor(resId: ResourceId): RgbaColor =
        getColorOrNull(resId) ?: RgbaColor.BLACK

    fun getColorByName(name: String): RgbaColor? =
        findResourceId(ResourceType.COLOR, name)?.let { colors[it.id] }

    fun getDimenOrNull(resId: ResourceId): Float? =
        dimens[resId.id] ?: byTypeAndName[ResourceType.DIMEN to resId.name]?.let { dimens[it.id] }

    fun getDimen(resId: ResourceId): Float =
        getDimenOrNull(resId) ?: 0f

    fun getInteger(resId: ResourceId): Int =
        integers[resId.id] ?: byTypeAndName[ResourceType.INTEGER to resId.name]?.let { integers[it.id] } ?: 0

    fun getBool(resId: ResourceId): Boolean =
        bools[resId.id] ?: byTypeAndName[ResourceType.BOOL to resId.name]?.let { bools[it.id] } ?: false

    fun getDrawable(resId: ResourceId): DrawableResource? =
        drawables[resId.id] ?: byTypeAndName[ResourceType.DRAWABLE to resId.name]?.let { drawables[it.id] }

    fun getDrawableByName(name: String): DrawableResource? =
        findResourceId(ResourceType.DRAWABLE, name)?.let { drawables[it.id] }

    fun getRawText(resId: ResourceId): String? =
        rawTexts[resId.id] ?: byTypeAndName[ResourceType.RAW to resId.name]?.let { rawTexts[it.id] }

    fun getRawBase64(resId: ResourceId): String? =
        rawBase64[resId.id] ?: byTypeAndName[ResourceType.RAW to resId.name]?.let { rawBase64[it.id] }

    fun getRawBytes(resId: ResourceId): ByteArray? {
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

    fun getBytes(resId: ResourceId): ByteArray? = getRawBytes(resId)

    fun getUrl(resId: ResourceId): String {
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

    fun getImageBuffer(resId: ResourceId): ImageBuffer? {
        val bytes = getRawBytes(resId) ?: return null
        if (bytes.isEmpty()) return null
        return ImageLoader.loadFromBytes(bytes)
    }

    fun getFontPath(resId: ResourceId): String? =
        fontPaths[resId.id] ?: byTypeAndName[ResourceType.FONT to resId.name]?.let { fontPaths[it.id] }

    fun getStyleSheet(resId: ResourceId): PrecompiledStyleSheet? =
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
