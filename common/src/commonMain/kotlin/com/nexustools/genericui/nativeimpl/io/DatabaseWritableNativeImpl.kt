package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.Writable

/**
 * Native implementation contract for database-entry-backed [Writable] streams.
 */
interface DatabaseWritableNativeImpl : Writable {
    val table: String
    val key: String
    val appendMode: Boolean
        get() = false
}
