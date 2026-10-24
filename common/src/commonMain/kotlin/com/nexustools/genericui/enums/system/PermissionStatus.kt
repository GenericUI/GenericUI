package com.nexustools.genericui.enums.system

/**
 * Status of a permission query or request in [com.nexustools.genericui.permission.PermissionManager].
 */
enum class PermissionStatus {
    GRANTED,
    DENIED,
    PROMPT_REQUIRED,
    PERMANENTLY_DENIED,
    NOT_APPLICABLE
}
