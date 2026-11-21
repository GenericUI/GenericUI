package com.nexustools.genericui.resources

/**
 * Android-style resource categories supported by the GenericUI `R` / `RC` / `Resource` system.
 *
 * Each category has a 1-byte type prefix (`0x01` .. `0x0C`) combined with `0x7f000000`
 * to form standard Android-style `0x7fTTNNNN` integer resource IDs.
 */
enum class ResourceType(
    val typeCode: Int,
    val directoryName: String
) {
    DRAWABLE(0x01, "drawable"),
    STRING(0x02, "string"),
    COLOR(0x03, "color"),
    DIMEN(0x04, "dimen"),
    INTEGER(0x05, "integer"),
    BOOL(0x06, "bool"),
    RAW(0x07, "raw"),
    FONT(0x08, "font"),
    XML(0x09, "xml"),
    MENU(0x0A, "menu"),
    LAYOUT(0x0B, "layout"),
    STYLE(0x0C, "style");

    fun buildNumericId(entryIndex: Int): Int =
        buildNumericId(entryIndex, DEFAULT_APP_MODULE_BYTE)

    fun buildNumericId(entryIndex: Int, moduleByte: Int): Int =
        ((moduleByte and 0xFF) shl 24) or ((typeCode and 0xFF) shl 16) or (entryIndex and 0xFFFF)

    companion object {
        const val CORE_MODULE_BYTE: Int = 0x7E
        const val DEFAULT_APP_MODULE_BYTE: Int = 0x7F
        const val MIN_EXTENSION_MODULE_BYTE: Int = 0x60
        const val MAX_EXTENSION_MODULE_BYTE: Int = 0x7D

        fun extensionModuleByte(category: String, extension: String): Int {
            val normalizedKey = "${category.trim().lowercase()}:${extension.trim().lowercase()}"
            var hash = 0x811C9DC5.toInt()
            for (ch in normalizedKey) {
                hash = hash xor ch.code
                hash *= 0x01000193
            }
            val span = (MAX_EXTENSION_MODULE_BYTE - MIN_EXTENSION_MODULE_BYTE) + 1
            val offset = (hash and 0x7FFF_FFFF) % span
            return MIN_EXTENSION_MODULE_BYTE + offset
        }
    }
}
