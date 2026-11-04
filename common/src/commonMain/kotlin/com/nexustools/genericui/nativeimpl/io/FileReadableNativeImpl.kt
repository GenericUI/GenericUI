package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.Readable

/**
 * Native implementation contract for file-backed [Readable] streams.
 */
interface FileReadableNativeImpl : Readable {
    val filePath: String?
        get() = null
}
