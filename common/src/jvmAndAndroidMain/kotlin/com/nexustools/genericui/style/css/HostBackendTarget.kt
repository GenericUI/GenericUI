package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.system.BackendTarget

private val cachedHostDefaultBackendTarget: BackendTarget by lazy {
    val vmName = System.getProperty("java.vm.name")?.lowercase().orEmpty()
    val vendor = System.getProperty("java.vendor")?.lowercase().orEmpty()
    val runtimeName = System.getProperty("java.runtime.name")?.lowercase().orEmpty()
    if ("dalvik" in vmName || "android" in vendor || "android" in runtimeName) {
        BackendTarget.Android
    } else {
        BackendTarget.Swing
    }
}

internal actual fun resolveHostDefaultBackendTarget(): BackendTarget = cachedHostDefaultBackendTarget
