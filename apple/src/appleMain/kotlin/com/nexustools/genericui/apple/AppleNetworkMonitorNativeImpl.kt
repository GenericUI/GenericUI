@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import platform.darwin.DISPATCH_TIME_NOW

class AppleNetworkMonitorNativeImpl : NetworkMonitorNativeImpl {
    private val lock = AppleReentrantLock()
    private val monitorQueue = dispatch_queue_create("com.nexustools.genericui.apple.network", null)
    private val initialStatusSem = dispatch_semaphore_create(0)

    private var monitor: nw_path_monitor_t = null
    private var onlineState = false
    private var hasReceivedInitialUpdate = false
    private var listener: ((Boolean) -> Unit)? = null
    private var disposedState = false

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    init {
        val pathMonitor = nw_path_monitor_create()
        monitor = pathMonitor
        if (pathMonitor != null) {
            nw_path_monitor_set_queue(pathMonitor, monitorQueue)
            nw_path_monitor_set_update_handler(pathMonitor) { path ->
                val satisfied = path != null && nw_path_get_status(path) == nw_path_status_satisfied
                var changedListener: ((Boolean) -> Unit)? = null
                lock.withLock {
                    if (!disposedState) {
                        val wasInitialized = hasReceivedInitialUpdate
                        val previous = onlineState
                        onlineState = satisfied
                        hasReceivedInitialUpdate = true
                        if (wasInitialized && previous != satisfied) {
                            changedListener = listener
                        }
                    }
                }
                initialStatusSem?.let { dispatch_semaphore_signal(it) }
                changedListener?.invoke(satisfied)
            }
            nw_path_monitor_start(pathMonitor)
        } else {
            hasReceivedInitialUpdate = true
        }
    }

    override fun isOnline(): Boolean {
        if (isDisposed) return false
        val alreadyInitialized = lock.withLock { hasReceivedInitialUpdate }
        if (!alreadyInitialized && initialStatusSem != null) {
            dispatch_semaphore_wait(initialStatusSem, dispatch_time(DISPATCH_TIME_NOW, 100L * NSEC_PER_MSEC.toLong()))
        }
        return lock.withLock { onlineState }
    }

    override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {
        lock.withLock {
            this.listener = listener
        }
    }

    override fun dispose() {
        val toCancel = lock.withLock {
            if (disposedState) return
            disposedState = true
            listener = null
            val m = monitor
            monitor = null
            m
        }
        initialStatusSem?.let { dispatch_semaphore_signal(it) }
        if (toCancel != null) {
            nw_path_monitor_cancel(toCancel)
        }
    }
}
