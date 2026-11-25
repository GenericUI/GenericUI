package com.nexustools.genericui.exception

/**
 * Thrown when attempting to open a raw TCP or UDP socket on a backend without raw socket support.
 */
open class RawSocketUnsupportedException(
    val protocol: String,
    backendName: String,
    message: String = "Raw $protocol sockets are not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException(protocol, backendName, message, cause)
