package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.Readable

/**
 * Native implementation contract for database-entry-backed [Readable] streams.
 */
interface DatabaseReadableNativeImpl : Readable {
    val table: String
    val key: String
}
