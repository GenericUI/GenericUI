package com.nexustools.genericui.exception

/**
 * Thrown when an operation invokes a capability not supported by the active platform backend.
 */
open class UnsupportedBackendCapabilityException(
    val capability: String,
    val backendName: String,
    message: String = "Capability '$capability' is not supported on backend '$backendName'.",
    cause: Throwable? = null
) : GenericUiException(message, cause)
