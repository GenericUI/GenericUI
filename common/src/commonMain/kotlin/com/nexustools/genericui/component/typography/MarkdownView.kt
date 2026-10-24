package com.nexustools.genericui.component.typography

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl

/**
 * Rich Markdown rendering component.
 */
class MarkdownView(
    markdown: String = ""
) : AbstractComponent() {
    var markdown: String = markdown
        set(value) {
            field = value
            typedNative<MarkdownViewNativeImpl<*>>()?.setMarkdown(value)
        }

    override fun createNativeImpl(factory: NativeFactory): MarkdownViewNativeImpl<*> =
        factory.createMarkdownView()

    override fun syncStateToNative() {
        super.syncStateToNative()
        typedNative<MarkdownViewNativeImpl<*>>()?.setMarkdown(markdown)
    }
}
