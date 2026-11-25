package com.nexustools.genericui.exception

/**
 * Base exception for GenericUI runtime, lifecycle, capability, and platform errors.
 */
open class GenericUiException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
