package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable

interface NetworkMonitorNativeImpl : Disposable {
    fun isOnline(): Boolean
    fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?)
}
