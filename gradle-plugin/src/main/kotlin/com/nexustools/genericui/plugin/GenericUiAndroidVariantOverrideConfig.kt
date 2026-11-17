package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Per-build-variant Android manifest override block (e.g. `"debug"`, `"release"`, `"development"`,
 * `"production"`) supporting `applicationIdSuffix`, `versionNameSuffix`, `debuggable`, and nested
 * [GenericUiAndroidManifestConfig] overrides merged cleanly over a base configuration.
 */
open class GenericUiAndroidVariantOverrideConfig(
    val variantName: String
) : Serializable {
    var applicationIdSuffix: String = ""
    var versionNameSuffix: String = ""
    var debuggable: Boolean? = null
    val manifestConfig: GenericUiAndroidManifestConfig = GenericUiAndroidManifestConfig()

    /** Configures variant-specific Android manifest overrides. */
    fun manifest(action: Action<in GenericUiAndroidManifestConfig>) {
        action.execute(manifestConfig)
    }

    /**
     * Overlays this variant's explicit settings and collections on top of [base], returning a
     * new independent [GenericUiAndroidManifestConfig].
     */
    fun overlayFrom(base: GenericUiAndroidManifestConfig): GenericUiAndroidManifestConfig {
        val result = base.copyConfig()
        val overlay = manifestConfig
        val exp = overlay.explicitFields

        if ("versionCode" in exp || overlay.versionCode != null) result.versionCode = overlay.versionCode
        if ("versionName" in exp || overlay.versionName != null) result.versionName = overlay.versionName
        if ("minSdk" in exp) result.minSdk = overlay.minSdk
        if ("compileSdk" in exp) result.compileSdk = overlay.compileSdk
        if ("targetSdk" in exp) result.targetSdk = overlay.targetSdk
        if ("screenOrientation" in exp) result.screenOrientation = overlay.screenOrientation
        if ("configChanges" in exp) result.configChanges = overlay.configChanges
        if ("supportsPictureInPicture" in exp) result.supportsPictureInPicture = overlay.supportsPictureInPicture
        if ("applicationClass" in exp) result.applicationClass = overlay.applicationClass
        if ("theme" in exp) result.theme = overlay.theme

        if ("usesCleartextTraffic" in exp || overlay.usesCleartextTraffic != null) {
            result.usesCleartextTraffic = overlay.usesCleartextTraffic
        }
        if ("allowBackup" in exp || overlay.allowBackup != null) result.allowBackup = overlay.allowBackup
        if ("fullBackupContent" in exp || overlay.fullBackupContent != null) {
            result.fullBackupContent = overlay.fullBackupContent
        }
        if ("dataExtractionRules" in exp || overlay.dataExtractionRules != null) {
            result.dataExtractionRules = overlay.dataExtractionRules
        }
        if ("largeHeap" in exp || overlay.largeHeap != null) result.largeHeap = overlay.largeHeap
        if ("hardwareAccelerated" in exp || overlay.hardwareAccelerated != null) {
            result.hardwareAccelerated = overlay.hardwareAccelerated
        }
        if ("supportsRtl" in exp) result.supportsRtl = overlay.supportsRtl
        if ("icon" in exp) result.icon = overlay.icon
        if ("roundIcon" in exp) result.roundIcon = overlay.roundIcon
        if ("banner" in exp || overlay.banner != null) result.banner = overlay.banner
        if ("logo" in exp || overlay.logo != null) result.logo = overlay.logo
        if ("appCategory" in exp || overlay.appCategory != null) result.appCategory = overlay.appCategory
        if ("localeConfig" in exp || overlay.localeConfig != null) result.localeConfig = overlay.localeConfig
        if ("enableOnBackInvokedCallback" in exp || overlay.enableOnBackInvokedCallback != null) {
            result.enableOnBackInvokedCallback = overlay.enableOnBackInvokedCallback
        }
        if ("resizeableActivity" in exp || overlay.resizeableActivity != null) {
            result.resizeableActivity = overlay.resizeableActivity
        }
        if ("extractNativeLibs" in exp || overlay.extractNativeLibs != null) {
            result.extractNativeLibs = overlay.extractNativeLibs
        }
        if ("requestLegacyExternalStorage" in exp || overlay.requestLegacyExternalStorage != null) {
            result.requestLegacyExternalStorage = overlay.requestLegacyExternalStorage
        }
        if ("allowNativeHeapPointerTagging" in exp || overlay.allowNativeHeapPointerTagging != null) {
            result.allowNativeHeapPointerTagging = overlay.allowNativeHeapPointerTagging
        }
        if ("isGame" in exp || overlay.isGame != null) result.isGame = overlay.isGame

        if ("debuggable" in exp || overlay.debuggable != null) {
            result.debuggable = overlay.debuggable
        } else if (debuggable != null) {
            result.debuggable = debuggable
        }

        if ("networkSecurityConfig" in exp || overlay.networkSecurityConfig != null) {
            result.networkSecurityConfig = overlay.networkSecurityConfig
        }

        for (p in overlay.permissions) {
            if (p !in result.permissions) result.permissions.add(p)
        }
        for (spec in overlay.permissionSpecs) {
            result.permissionSpecs.removeAll { it.name == spec.name }
            result.permissionSpecs.add(spec.copy())
        }
        val mergedGroups = result.permissionGroups.overlayWith(overlay.permissionGroups)
        result.permissionGroups.networkEnabled = mergedGroups.networkEnabled
        result.permissionGroups.mediaPlaybackEnabled = mergedGroups.mediaPlaybackEnabled
        result.permissionGroups.notificationsEnabled = mergedGroups.notificationsEnabled
        result.permissionGroups.bluetoothEnabled = mergedGroups.bluetoothEnabled
        result.permissionGroups.bluetoothNearbyDevicesOnly = mergedGroups.bluetoothNearbyDevicesOnly
        result.permissionGroups.locationEnabled = mergedGroups.locationEnabled
        result.permissionGroups.locationPrecise = mergedGroups.locationPrecise
        result.permissionGroups.cameraEnabled = mergedGroups.cameraEnabled
        result.permissionGroups.microphoneEnabled = mergedGroups.microphoneEnabled
        result.permissionGroups.storageMediaEnabled = mergedGroups.storageMediaEnabled
        result.permissionGroups.storageImages = mergedGroups.storageImages
        result.permissionGroups.storageVideo = mergedGroups.storageVideo
        result.permissionGroups.storageAudio = mergedGroups.storageAudio

        for (feat in overlay.usesFeatures) {
            if (feat.name.isNotEmpty()) {
                result.usesFeatures.removeAll { it.name == feat.name }
            } else {
                result.usesFeatures.removeAll { it.glEsVersion == feat.glEsVersion && it.name.isEmpty() }
            }
            result.usesFeatures.add(feat.copy())
        }
        for (svc in overlay.inlineServices) {
            result.inlineServices.removeAll { it.className == svc.className }
            result.inlineServices.add(svc.copySpec())
        }

        val mergedNet = result.networkSecurity.overlayWith(overlay.networkSecurity)
        result.networkSecurity.cleartextPermittedByDefault = mergedNet.cleartextPermittedByDefault
        result.networkSecurity.trustUserCertificates = mergedNet.trustUserCertificates
        result.networkSecurity.baseConfig = mergedNet.baseConfig
        result.networkSecurity.domainConfigs.clear()
        result.networkSecurity.domainConfigs.addAll(mergedNet.domainConfigs)
        result.networkSecurity.debugOverridesTrustAnchors = mergedNet.debugOverridesTrustAnchors

        result.activityDefaults.overlayWith(overlay.activityDefaults)
        result.mainActivity.overlayWith(overlay.mainActivity)
        overlay.activityOverrides.forEach { (key, spec) ->
            val existing = result.activityOverrides[key]
            if (existing != null) {
                existing.overlayWith(spec)
            } else {
                result.activityOverrides[key] = spec.copyOverride()
            }
        }

        val mergedQueries = result.queries.overlayWith(overlay.queries)
        result.queries.packageNames.clear()
        result.queries.packageNames.addAll(mergedQueries.packageNames)
        result.queries.intents.clear()
        result.queries.intents.addAll(mergedQueries.intents)
        result.queries.providerAuthorities.clear()
        result.queries.providerAuthorities.addAll(mergedQueries.providerAuthorities)

        val mergedTv = result.androidTv.overlayWith(overlay.androidTv)
        result.androidTv.enabled = mergedTv.enabled
        result.androidTv.required = mergedTv.required
        result.androidTv.banner = mergedTv.banner

        val mergedAuto = result.androidAuto.overlayWith(overlay.androidAuto)
        result.androidAuto.enabled = mergedAuto.enabled
        result.androidAuto.mediaBrowserService = mergedAuto.mediaBrowserService
        result.androidAuto.usesMedia = mergedAuto.usesMedia
        result.androidAuto.usesNotification = mergedAuto.usesNotification

        val mergedShortcuts = result.shortcuts.overlayWith(overlay.shortcuts)
        result.shortcuts.enabled = mergedShortcuts.enabled
        result.shortcuts.inheritFromWebManifest = mergedShortcuts.inheritFromWebManifest
        result.shortcuts.shortcuts.clear()
        result.shortcuts.shortcuts.addAll(mergedShortcuts.shortcuts)

        for (loc in overlay.locales) {
            if (loc !in result.locales) result.locales.add(loc)
        }

        val mergedBackup = result.backupRules.overlayWith(overlay.backupRules)
        result.backupRules.cloudRules.clear()
        result.backupRules.cloudRules.addAll(mergedBackup.cloudRules)
        result.backupRules.deviceTransferRules.clear()
        result.backupRules.deviceTransferRules.addAll(mergedBackup.deviceTransferRules)

        val mergedFp = result.fileProvider.overlayWith(overlay.fileProvider)
        result.fileProvider.enabled = mergedFp.enabled
        result.fileProvider.providerClass = mergedFp.providerClass
        result.fileProvider.authorities = mergedFp.authorities
        result.fileProvider.paths.clear()
        result.fileProvider.paths.addAll(mergedFp.paths)

        for (w in overlay.widgets) {
            result.widgets.removeAll { it.providerClass == w.providerClass }
            result.widgets.add(w.copySpec())
        }
        for (r in overlay.receivers) {
            result.receivers.removeAll { it.className == r.className }
            result.receivers.add(r.copySpec())
        }
        for (p in overlay.providers) {
            result.providers.removeAll { it.className == p.className }
            result.providers.add(p.copySpec())
        }
        for (meta in overlay.applicationMetaData) {
            result.applicationMetaData.removeAll { it.name == meta.name }
            result.applicationMetaData.add(meta.copy())
        }
        result.manifestAttributes.putAll(overlay.manifestAttributes)
        result.applicationAttributes.putAll(overlay.applicationAttributes)
        result.placeholders.putAll(overlay.placeholders)
        for (path in overlay.mergeManifestPaths) {
            if (path !in result.mergeManifestPaths) result.mergeManifestPaths.add(path)
        }

        return result
    }
}
