package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.Writable

/**
 * Native implementation contract for file-backed [Writable] streams.
 */
interface FileWritableNativeImpl : Writable {
    val filePath: String?
        get() = null
    val appendMode: Boolean
        get() = false
}
