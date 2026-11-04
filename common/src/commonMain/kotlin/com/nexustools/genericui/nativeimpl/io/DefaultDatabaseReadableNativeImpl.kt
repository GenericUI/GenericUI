package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.ByteOrder

/**
 * Default implementation of [DatabaseReadableNativeImpl] backed by in-memory entry bytes.
 */
open class DefaultDatabaseReadableNativeImpl(
    override val table: String,
    override val key: String,
    initialBytes: ByteArray = ByteArray(0),
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : MemoryReadableNativeImpl(initialBytes, byteOrder), DatabaseReadableNativeImpl
