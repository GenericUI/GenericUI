package com.nexustools.genericui.style.font

/**
 * Runtime embedded font registry used by applications like HAEditor (`FontsDockPanel`)
 * to register embedded `.ttf`, `.otf`, `.woff`, and `.woff2` fonts across all 8 backends.
 */
object FontRegistry {
    private val embeddedFonts = linkedMapOf<String, ByteArray>()
    private val listeners = mutableListOf<(String, ByteArray) -> Unit>()

    fun registerEmbeddedFont(family: String, fontBytes: ByteArray) {
        embeddedFonts[family] = fontBytes
        listeners.forEach { it(family, fontBytes) }
    }

    fun unregisterEmbeddedFont(family: String) {
        embeddedFonts.remove(family)
    }

    fun getEmbeddedFontBytes(family: String): ByteArray? = embeddedFonts[family]

    fun getRegisteredFontFamilies(): Set<String> = embeddedFonts.keys.toSet()

    fun addOnFontRegisteredListener(listener: (String, ByteArray) -> Unit) {
        listeners.add(listener)
    }
}
