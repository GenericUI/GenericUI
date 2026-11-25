package com.nexustools.genericui.exception

/**
 * Thrown when attempting to open multiple concurrent top-level windows on a single-window backend.
 */
open class MultipleWindowsUnsupportedException(
    backendName: String,
    message: String = "Multiple top-level windows are not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException("multipleWindows", backendName, message, cause)
