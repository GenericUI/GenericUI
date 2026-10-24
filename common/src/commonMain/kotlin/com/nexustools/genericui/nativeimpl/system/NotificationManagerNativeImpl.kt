package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.component.system.Notification
import com.nexustools.genericui.component.system.NotificationChannel
import com.nexustools.genericui.core.Disposable

interface NotificationManagerNativeImpl : Disposable {
    fun registerChannel(channel: NotificationChannel)
    fun showNotification(notification: Notification, onActionClicked: ((String) -> Unit)? = null)
    fun cancelNotification(notificationId: String)
}
