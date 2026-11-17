package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * High-level Android permission-group presets configured via
 * `genericui { android { manifest { permissionGroup { ... } } } }`.
 */
open class GenericUiAndroidPermissionGroupConfig : Serializable {
    var networkEnabled: Boolean = false
    var mediaPlaybackEnabled: Boolean = false
    var notificationsEnabled: Boolean = false
    var bluetoothEnabled: Boolean = false
    var bluetoothNearbyDevicesOnly: Boolean = true
    var locationEnabled: Boolean = false
    var locationPrecise: Boolean = true
    var cameraEnabled: Boolean = false
    var microphoneEnabled: Boolean = false
    var storageMediaEnabled: Boolean = false
    var storageImages: Boolean = true
    var storageVideo: Boolean = true
    var storageAudio: Boolean = true

    /** Enables standard network permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`). */
    fun network() {
        networkEnabled = true
    }

    /** Enables media playback foreground service permissions (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`). */
    fun mediaPlayback() {
        mediaPlaybackEnabled = true
    }

    /** Enables Android 13+ notification permission (`POST_NOTIFICATIONS`). */
    fun notifications() {
        notificationsEnabled = true
    }

    /**
     * Enables legacy (maxSdkVersion=30) and Android 12+ Bluetooth permissions, adding
     * `usesPermissionFlags="neverForLocation"` on `BLUETOOTH_SCAN` when [nearbyDevicesOnly] is `true`.
     */
    @JvmOverloads
    fun bluetooth(nearbyDevicesOnly: Boolean = true) {
        bluetoothEnabled = true
        bluetoothNearbyDevicesOnly = nearbyDevicesOnly
    }

    /** Enables `ACCESS_COARSE_LOCATION` (and `ACCESS_FINE_LOCATION` when [precise] is `true`). */
    @JvmOverloads
    fun location(precise: Boolean = true) {
        locationEnabled = true
        locationPrecise = precise
    }

    /** Enables `CAMERA` permission. */
    fun camera() {
        cameraEnabled = true
    }

    /** Enables `RECORD_AUDIO` permission. */
    fun microphone() {
        microphoneEnabled = true
    }

    /**
     * Enables `READ_EXTERNAL_STORAGE` (maxSdkVersion=32) and Android 13+ granular media permissions
     * (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`).
     */
    @JvmOverloads
    fun storageMedia(
        images: Boolean = true,
        video: Boolean = true,
        audio: Boolean = true
    ) {
        storageMediaEnabled = true
        storageImages = images
        storageVideo = video
        storageAudio = audio
    }

    /** Expands all enabled permission groups into concrete `<uses-permission>` specifications. */
    fun resolvePermissions(): List<GenericUiAndroidPermissionSpec> {
        val resolved = LinkedHashMap<String, GenericUiAndroidPermissionSpec>()
        fun addPerm(name: String, maxSdkVersion: Int? = null, usesPermissionFlags: String? = null) {
            resolved[name] = GenericUiAndroidPermissionSpec(
                name = name,
                maxSdkVersion = maxSdkVersion,
                usesPermissionFlags = usesPermissionFlags
            )
        }

        if (networkEnabled) {
            addPerm("android.permission.INTERNET")
            addPerm("android.permission.ACCESS_NETWORK_STATE")
            addPerm("android.permission.ACCESS_WIFI_STATE")
        }
        if (mediaPlaybackEnabled) {
            addPerm("android.permission.FOREGROUND_SERVICE")
            addPerm("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")
            addPerm("android.permission.WAKE_LOCK")
        }
        if (notificationsEnabled) {
            addPerm("android.permission.POST_NOTIFICATIONS")
        }
        if (bluetoothEnabled) {
            addPerm("android.permission.BLUETOOTH", maxSdkVersion = 30)
            addPerm("android.permission.BLUETOOTH_ADMIN", maxSdkVersion = 30)
            addPerm("android.permission.BLUETOOTH_CONNECT")
            addPerm(
                "android.permission.BLUETOOTH_SCAN",
                usesPermissionFlags = if (bluetoothNearbyDevicesOnly) "neverForLocation" else null
            )
            addPerm("android.permission.BLUETOOTH_ADVERTISE")
        }
        if (locationEnabled) {
            addPerm("android.permission.ACCESS_COARSE_LOCATION")
            if (locationPrecise) {
                addPerm("android.permission.ACCESS_FINE_LOCATION")
            }
        }
        if (cameraEnabled) {
            addPerm("android.permission.CAMERA")
        }
        if (microphoneEnabled) {
            addPerm("android.permission.RECORD_AUDIO")
        }
        if (storageMediaEnabled) {
            addPerm("android.permission.READ_EXTERNAL_STORAGE", maxSdkVersion = 32)
            if (storageImages) {
                addPerm("android.permission.READ_MEDIA_IMAGES")
            }
            if (storageVideo) {
                addPerm("android.permission.READ_MEDIA_VIDEO")
            }
            if (storageAudio) {
                addPerm("android.permission.READ_MEDIA_AUDIO")
            }
        }
        return resolved.values.toList()
    }

    /** Merges [overlay] group selections onto this config in a new copy. */
    fun overlayWith(overlay: GenericUiAndroidPermissionGroupConfig): GenericUiAndroidPermissionGroupConfig =
        copySpec().apply {
            if (overlay.networkEnabled) networkEnabled = true
            if (overlay.mediaPlaybackEnabled) mediaPlaybackEnabled = true
            if (overlay.notificationsEnabled) notificationsEnabled = true
            if (overlay.bluetoothEnabled) {
                bluetoothEnabled = true
                bluetoothNearbyDevicesOnly = overlay.bluetoothNearbyDevicesOnly
            }
            if (overlay.locationEnabled) {
                locationEnabled = true
                locationPrecise = overlay.locationPrecise
            }
            if (overlay.cameraEnabled) cameraEnabled = true
            if (overlay.microphoneEnabled) microphoneEnabled = true
            if (overlay.storageMediaEnabled) {
                storageMediaEnabled = true
                storageImages = overlay.storageImages
                storageVideo = overlay.storageVideo
                storageAudio = overlay.storageAudio
            }
        }

    /** Returns an independent deep copy of this permission group configuration. */
    fun copySpec(): GenericUiAndroidPermissionGroupConfig = GenericUiAndroidPermissionGroupConfig().also { copy ->
        copy.networkEnabled = networkEnabled
        copy.mediaPlaybackEnabled = mediaPlaybackEnabled
        copy.notificationsEnabled = notificationsEnabled
        copy.bluetoothEnabled = bluetoothEnabled
        copy.bluetoothNearbyDevicesOnly = bluetoothNearbyDevicesOnly
        copy.locationEnabled = locationEnabled
        copy.locationPrecise = locationPrecise
        copy.cameraEnabled = cameraEnabled
        copy.microphoneEnabled = microphoneEnabled
        copy.storageMediaEnabled = storageMediaEnabled
        copy.storageImages = storageImages
        copy.storageVideo = storageVideo
        copy.storageAudio = storageAudio
    }
}
