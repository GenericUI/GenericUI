@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.Foundation.NSData
import platform.Foundation.data
import platform.Foundation.dataWithBytes
import platform.darwin.dispatch_data_apply
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_data_get_size
import platform.darwin.dispatch_data_t
import platform.posix.memcpy

internal object AppleDataBridge {
    fun byteArrayToNSData(bytes: ByteArray): NSData {
        if (bytes.isEmpty()) return NSData.data()
        return bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
    }

    fun nsDataToByteArray(data: NSData?): ByteArray {
        if (data == null) return ByteArray(0)
        val len = data.length.toInt()
        if (len <= 0) return ByteArray(0)
        val src = data.bytes ?: return ByteArray(0)
        val out = ByteArray(len)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), src, data.length)
        }
        return out
    }

    fun byteArrayToCFData(bytes: ByteArray): CFDataRef? {
        if (bytes.isEmpty()) {
            return CFDataCreate(null, null, 0L)
        }
        return bytes.usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
        }
    }

    fun byteArrayToDispatchData(bytes: ByteArray): dispatch_data_t {
        if (bytes.isEmpty()) {
            return dispatch_data_create(null, 0uL, null, null)
        }
        return bytes.usePinned { pinned ->
            dispatch_data_create(pinned.addressOf(0), bytes.size.toULong(), null, null)
        }
    }

    fun dispatchDataToByteArray(data: dispatch_data_t): ByteArray {
        if (data == null) return ByteArray(0)
        val totalSize = dispatch_data_get_size(data).toInt()
        if (totalSize <= 0) return ByteArray(0)
        val out = ByteArray(totalSize)
        out.usePinned { pinned ->
            val dst = pinned.addressOf(0)
            dispatch_data_apply(data) { _, offset, buffer, size ->
                if (buffer != null && size > 0uL) {
                    val off = offset.toInt()
                    val copyLen = minOf(size.toInt(), totalSize - off)
                    if (copyLen > 0) {
                        memcpy(dst + off, buffer, copyLen.toULong())
                    }
                }
                true
            }
        }
        return out
    }
}
