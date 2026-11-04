package com.nexustools.genericui.nativeimpl.io

import com.nexustools.genericui.io.ByteOrder

/**
 * Default implementation of [FileWritableNativeImpl] supporting both in-memory commit and
 * chunk/flush/close callbacks.
 */
open class DefaultFileWritableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    override val filePath: String? = null,
    override val appendMode: Boolean = false,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onWriteChunk: ((ByteArray, Int, Int) -> Int)? = null,
    onFlushCallback: (suspend (ByteArray) -> Unit)? = null,
    onCloseCallback: (suspend () -> Unit)? = null,
    onCommit: (suspend (ByteArray) -> Unit)? = null
) : MemoryWritableNativeImpl(
    initialBytes = initialBytes,
    byteOrder = byteOrder,
    onWriteChunk = onWriteChunk,
    onFlushCallback = onFlushCallback,
    onCloseCallback = onCloseCallback,
    onCommit = onCommit
), FileWritableNativeImpl
