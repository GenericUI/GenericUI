package com.nexustools.genericui.exception

/**
 * Thrown when attempting to open a directory chooser on a backend without directory selection support.
 */
open class DirectorySelectionUnsupportedException(
    backendName: String,
    message: String = "Directory selection is not supported on backend '$backendName'.",
    cause: Throwable? = null
) : UnsupportedBackendCapabilityException("directorySelection", backendName, message, cause)
