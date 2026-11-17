@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.apple

import com.nexustools.genericui.component.system.Notification
import com.nexustools.genericui.component.system.NotificationChannel
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationAttachment
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

class AppleNotificationManagerNativeImpl : NotificationManagerNativeImpl {
    private val lock = AppleReentrantLock()
    private val channels = LinkedHashMap<String, NotificationChannel>()
    private val activeNotifications = LinkedHashMap<String, Notification>()
    private val actionCallbacks = LinkedHashMap<String, (String) -> Unit>()
    private val categoriesById = LinkedHashMap<String, UNNotificationCategory>()

    private var authorizationRequested = false
    private var delegateInstalled = false
    private var disposedState = false

    /**
     * `UNUserNotificationCenter.currentNotificationCenter()` throws an Objective-C
     * `NSInternalInconsistencyException` when invoked inside unbundled executables (such as unit-test binaries
     * lacking an Info.plist bundle identifier). Guarding on `bundleIdentifier != null` ensures real `.app`
     * bundles deliver genuine system notifications while unbundled CLI processes operate safely without aborting.
     */
    val isBundleNotificationCenterAvailable: Boolean =
        !NSBundle.mainBundle.bundleIdentifier.isNullOrBlank()

    private val delegate = CenterDelegate()

    override val isDisposed: Boolean
        get() = lock.withLock { disposedState }

    override fun registerChannel(channel: NotificationChannel) {
        lock.withLock {
            if (disposedState) return
            channels[channel.id] = channel
        }
    }

    override fun showNotification(notification: Notification, onActionClicked: ((String) -> Unit)?) {
        val channel = lock.withLock {
            if (disposedState) return
            activeNotifications[notification.id] = notification
            if (onActionClicked != null) {
                actionCallbacks[notification.id] = onActionClicked
            } else {
                actionCallbacks.remove(notification.id)
            }
            channels[notification.channelId]
        }

        val center = currentCenterOrNull() ?: return
        ensureCenterInitialized(center)

        val content = UNMutableNotificationContent()
        content.setTitle(notification.title)
        content.setBody(notification.body)
        content.setThreadIdentifier(notification.channelId)
        if (channel == null || channel.highImportance) {
            content.setSound(UNNotificationSound.defaultSound)
        }

        val iconPath = notification.iconPathOrUrl
        if (!iconPath.isNullOrBlank()) {
            val fileUrl = when {
                iconPath.startsWith("file://", ignoreCase = true) -> NSURL.URLWithString(iconPath)
                NSFileManager.defaultManager.fileExistsAtPath(iconPath) -> NSURL.fileURLWithPath(iconPath)
                else -> null
            }
            if (fileUrl != null) {
                val attachment = UNNotificationAttachment.attachmentWithIdentifier(
                    identifier = "${notification.id}_icon",
                    URL = fileUrl,
                    options = null,
                    error = null
                )
                if (attachment != null) {
                    content.setAttachments(listOf(attachment))
                }
            }
        }

        if (notification.actions.isNotEmpty()) {
            val categoryId = "genericui.${notification.channelId}.${notification.id}"
            val unActions = notification.actions.map { (actionId, actionTitle) ->
                UNNotificationAction.actionWithIdentifier(
                    identifier = actionId,
                    title = actionTitle,
                    options = 0uL
                )
            }
            val category = UNNotificationCategory.categoryWithIdentifier(
                identifier = categoryId,
                actions = unActions,
                intentIdentifiers = emptyList<Any>(),
                options = 0uL
            )
            val allCategories = lock.withLock {
                categoriesById[categoryId] = category
                categoriesById.values.toSet()
            }
            center.setNotificationCategories(allCategories)
            content.setCategoryIdentifier(categoryId)
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = notification.id,
            content = content,
            trigger = null
        )
        center.addNotificationRequest(request) { _ -> }
    }

    override fun cancelNotification(notificationId: String) {
        lock.withLock {
            activeNotifications.remove(notificationId)
            actionCallbacks.remove(notificationId)
        }
        val center = currentCenterOrNull() ?: return
        center.removePendingNotificationRequestsWithIdentifiers(listOf(notificationId))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(notificationId))
    }

    fun activeNotificationCount(): Int = lock.withLock { activeNotifications.size }

    override fun dispose() {
        lock.withLock {
            if (disposedState) return
            disposedState = true
            activeNotifications.clear()
            actionCallbacks.clear()
            channels.clear()
            categoriesById.clear()
        }
    }

    private fun currentCenterOrNull(): UNUserNotificationCenter? {
        return if (isBundleNotificationCenterAvailable) {
            UNUserNotificationCenter.currentNotificationCenter()
        } else {
            null
        }
    }

    private fun ensureCenterInitialized(center: UNUserNotificationCenter) {
        val (needDelegate, needAuth) = lock.withLock {
            val nd = !delegateInstalled
            val na = !authorizationRequested
            delegateInstalled = true
            authorizationRequested = true
            nd to na
        }
        if (needDelegate) {
            center.setDelegate(delegate)
        }
        if (needAuth) {
            val opts = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
            center.requestAuthorizationWithOptions(opts) { _, _ -> }
        }
    }

    private inner class CenterDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
        ) {
            val opts = UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionSound or
                UNNotificationPresentationOptionList
            withCompletionHandler(opts)
        }

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit
        ) {
            val notifId = didReceiveNotificationResponse.notification.request.identifier
            val actionId = didReceiveNotificationResponse.actionIdentifier
            val callback = lock.withLock { actionCallbacks[notifId] }
            callback?.invoke(actionId)
            withCompletionHandler()
        }
    }
}
