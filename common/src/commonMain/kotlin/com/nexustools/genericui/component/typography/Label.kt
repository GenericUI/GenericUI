package com.nexustools.genericui.component.typography

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * Single-line or multi-line text label component (`<label>` / `<span>`).
 */
open class Label(
    text: String = "",
    private val tagName: String = "label",
    private val classPrefix: String = "form-label"
) : AbstractComponent(htmlTag = tagName) {
    init {
        htmlTag = tagName
    }

    var text: String = text
        set(value) {
            if (field != value) {
                field = value
                notifyParentCacheDirty()
                typedNative<TextNativeImpl<*>>()?.setText(value)
            }
        }

    var maxLines: Int? = null
        set(value) {
            field = value
            typedNative<TextNativeImpl<*>>()?.setMaxLines(value)
        }

    /**
     * `kotlinx.html`-style unary plus operator to append text to this label/heading/paragraph.
     */
    operator fun String.unaryPlus() {
        text = if (text.isEmpty()) this else text + this
    }

    override fun createNativeImpl(factory: NativeFactory): TextNativeImpl<*> =
        factory.createText(htmlTag, classPrefix)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<TextNativeImpl<*>>() ?: return
        impl.setText(text)
        impl.setMaxLines(maxLines)
    }
}
