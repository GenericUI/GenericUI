package com.nexustools.genericui.res

/**
 * Android-style resource categories supported by the GenericUI `R` / `RC` resource system.
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
        0x7f000000 or ((typeCode and 0xFF) shl 16) or (entryIndex and 0xFFFF)
}
