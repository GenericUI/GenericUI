package com.nexustools.genericui.component.system

/**
 * System notification channel descriptor (used on Android 8.0+ and desktop notification categories).
 */
data class NotificationChannel(
    val id: String,
    val name: String,
    val description: String = "",
    val highImportance: Boolean = false
)
