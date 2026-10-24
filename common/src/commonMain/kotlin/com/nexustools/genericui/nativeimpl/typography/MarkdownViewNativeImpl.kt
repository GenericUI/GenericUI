package com.nexustools.genericui.nativeimpl.typography

import com.nexustools.genericui.core.NativeImpl

interface MarkdownViewNativeImpl<out W> : NativeImpl<W> {
    fun setMarkdown(markdown: String)
    fun getMarkdown(): String
}
