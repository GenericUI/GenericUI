package com.nexustools.genericui.exception

/**
 * Thrown when attempting to display a system tray icon on a backend without system tray support.
 */
open class SystemTrayUnsupportedException(
    backendName: String,
    message: String = "System tray is not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException("systemTray", backendName, message, cause)
