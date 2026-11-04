package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.ByteOrder

/**
 * Default implementation of [DatabaseWritableNativeImpl] committing written bytes to the database entry on finish/close.
 */
open class DefaultDatabaseWritableNativeImpl(
    override val table: String,
    override val key: String,
    override val appendMode: Boolean = false,
    initialBytes: ByteArray = ByteArray(0),
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onCommit: (suspend (ByteArray) -> Unit)? = null
) : MemoryWritableNativeImpl(
    initialBytes = initialBytes,
    byteOrder = byteOrder,
    onCommit = onCommit
), DatabaseWritableNativeImpl
