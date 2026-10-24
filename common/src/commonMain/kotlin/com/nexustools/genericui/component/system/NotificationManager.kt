package com.nexustools.genericui.component.system

import com.nexustools.genericui.core.GenericUI

/**
 * Cross-platform OS notification dispatcher.
 */
object NotificationManager {
    fun registerChannel(channel: NotificationChannel) {
        GenericUI.activeFactory?.createNotificationManager()?.registerChannel(channel)
    }

    fun show(notification: Notification, onActionClicked: ((String) -> Unit)? = null) {
        GenericUI.activeFactory?.createNotificationManager()?.showNotification(notification, onActionClicked)
    }

    fun cancel(notificationId: String) {
        GenericUI.activeFactory?.createNotificationManager()?.cancelNotification(notificationId)
    }
}
