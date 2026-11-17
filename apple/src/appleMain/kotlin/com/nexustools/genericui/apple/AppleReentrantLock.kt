package com.nexustools.genericui.apple

import platform.Foundation.NSRecursiveLock

internal class AppleReentrantLock {
    private val lock = NSRecursiveLock()

    inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
