package com.nexustools.genericui.nativeimpl.system

import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.system.PermissionStatus
import com.nexustools.genericui.enums.system.PermissionType

interface PermissionManagerNativeImpl : Disposable {
    fun checkPermission(permission: PermissionType): PermissionStatus
    fun requestPermission(permission: PermissionType, onResult: (PermissionStatus) -> Unit)
}
