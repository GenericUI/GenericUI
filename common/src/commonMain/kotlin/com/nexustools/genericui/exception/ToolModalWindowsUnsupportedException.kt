package com.nexustools.genericui.exception

/**
 * Thrown when attempting to open a floating/docked PanelWindow on a backend without tool modal window support.
 */
open class ToolModalWindowsUnsupportedException(
    backendName: String,
    message: String = "Tool modal windows (PanelWindow) are not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException("toolModalWindows", backendName, message, cause)
