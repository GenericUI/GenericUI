package com.nexustools.genericui.component.system

/**
 * OS-level notification payload dispatched via [NotificationManager].
 */
data class Notification(
    val id: String,
    val channelId: String = "default",
    val title: String,
    val body: String,
    val iconPathOrUrl: String? = null,
    val actions: List<Pair<String, String>> = emptyList()
)
