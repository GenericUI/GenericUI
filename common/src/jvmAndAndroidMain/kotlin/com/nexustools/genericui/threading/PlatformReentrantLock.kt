package com.nexustools.genericui.threading

import java.util.concurrent.locks.ReentrantLock

internal actual class PlatformReentrantLock actual constructor() {
    private val delegate = ReentrantLock()

    actual fun lock() {
        delegate.lock()
    }

    actual fun unlock() {
        if (delegate.isHeldByCurrentThread) {
            delegate.unlock()
        }
    }
}
