package com.nexustools.genericui.permission

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.PermissionStatus
import com.nexustools.genericui.enums.system.PermissionType

/**
 * Cross-platform runtime permission checker and requester (`CAMERA`, `MICROPHONE`,
 * `NOTIFICATIONS`, `STORAGE_READ`, `STORAGE_WRITE`, `LOCATION`, `BLUETOOTH`, `PICTURE_IN_PICTURE`).
 */
object PermissionManager {
    fun checkPermission(permission: PermissionType): PermissionStatus =
        GenericUI.activeFactory?.createPermissionManager()?.checkPermission(permission)
            ?: PermissionStatus.GRANTED

    fun requestPermission(permission: PermissionType, onResult: (PermissionStatus) -> Unit) {
        val mgr = GenericUI.activeFactory?.createPermissionManager()
        if (mgr != null) {
            mgr.requestPermission(permission, onResult)
        } else {
            onResult(PermissionStatus.GRANTED)
        }
    }
}
