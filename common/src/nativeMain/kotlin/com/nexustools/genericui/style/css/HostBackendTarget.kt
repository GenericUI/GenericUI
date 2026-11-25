@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.nexustools.genericui.style.css

import com.nexustools.genericui.enums.system.BackendTarget
import kotlin.native.OsFamily
import kotlin.native.Platform

internal actual fun resolveHostDefaultBackendTarget(): BackendTarget = when (Platform.osFamily) {
    OsFamily.MACOSX -> BackendTarget.MacOS
    OsFamily.WINDOWS -> BackendTarget.Windows
    OsFamily.LINUX -> BackendTarget.Gtk
    OsFamily.IOS -> BackendTarget.IOS
    OsFamily.ANDROID -> BackendTarget.Android
    else -> BackendTarget.MacOS
}
