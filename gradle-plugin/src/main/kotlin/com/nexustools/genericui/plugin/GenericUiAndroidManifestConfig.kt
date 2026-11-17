package com.nexustools.genericui.plugin

import org.gradle.api.Action
import java.io.Serializable

/**
 * Android `AndroidManifest.xml` & SDK configuration block (`genericui { android { manifest { ... } } }`).
 */
open class GenericUiAndroidManifestConfig : Serializable {
    internal val explicitFields: MutableSet<String> = linkedSetOf()

    // --- Versioning & SDK ---
    var versionCode: Int? = null
        set(value) {
            field = value
            explicitFields.add("versionCode")
        }

    var versionName: String? = null
        set(value) {
            field = value
            explicitFields.add("versionName")
        }

    var minSdk: Int = 24
        set(value) {
            field = value
            explicitFields.add("minSdk")
        }

    var compileSdk: Int = 35
        set(value) {
            field = value
            explicitFields.add("compileSdk")
        }

    var targetSdk: Int = 35
        set(value) {
            field = value
            explicitFields.add("targetSdk")
        }

    // --- Core Application & Default Activity Fields ---
    var screenOrientation: String = "unspecified"
        set(value) {
            field = value
            explicitFields.add("screenOrientation")
        }

    var configChanges: String = "orientation|screenSize|screenLayout|keyboardHidden|uiMode|density"
        set(value) {
            field = value
            explicitFields.add("configChanges")
        }

    var supportsPictureInPicture: Boolean = false
        set(value) {
            field = value
            explicitFields.add("supportsPictureInPicture")
        }

    var applicationClass: String = ""
        set(value) {
            field = value
            explicitFields.add("applicationClass")
        }

    var theme: String = "@style/Theme.GenericUI.App"
        set(value) {
            field = value
            explicitFields.add("theme")
        }

    // --- <application> Attributes ---
    var usesCleartextTraffic: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("usesCleartextTraffic")
        }

    var allowBackup: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("allowBackup")
        }

    var fullBackupContent: String? = null
        set(value) {
            field = value
            explicitFields.add("fullBackupContent")
        }

    var dataExtractionRules: String? = null
        set(value) {
            field = value
            explicitFields.add("dataExtractionRules")
        }

    var largeHeap: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("largeHeap")
        }

    var hardwareAccelerated: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("hardwareAccelerated")
        }

    var supportsRtl: Boolean = true
        set(value) {
            field = value
            explicitFields.add("supportsRtl")
        }

    var icon: String? = "@mipmap/ic_launcher"
        set(value) {
            field = value
            explicitFields.add("icon")
        }

    var roundIcon: String? = "@mipmap/ic_launcher_round"
        set(value) {
            field = value
            explicitFields.add("roundIcon")
        }

    var banner: String? = null
        set(value) {
            field = value
            explicitFields.add("banner")
        }

    var logo: String? = null
        set(value) {
            field = value
            explicitFields.add("logo")
        }

    var appCategory: String? = null
        set(value) {
            field = value
            explicitFields.add("appCategory")
        }

    var localeConfig: String? = null
        set(value) {
            field = value
            explicitFields.add("localeConfig")
        }

    var enableOnBackInvokedCallback: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("enableOnBackInvokedCallback")
        }

    var resizeableActivity: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("resizeableActivity")
        }

    var extractNativeLibs: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("extractNativeLibs")
        }

    var requestLegacyExternalStorage: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("requestLegacyExternalStorage")
        }

    var allowNativeHeapPointerTagging: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("allowNativeHeapPointerTagging")
        }

    var isGame: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("isGame")
        }

    var debuggable: Boolean? = null
        set(value) {
            field = value
            explicitFields.add("debuggable")
        }

    var networkSecurityConfig: String? = null
        set(value) {
            field = value
            explicitFields.add("networkSecurityConfig")
        }

    // --- Permissions, Features, & Inline Services ---
    val permissions: MutableList<String> = mutableListOf()
    val permissionSpecs: MutableList<GenericUiAndroidPermissionSpec> = mutableListOf()
    val permissionGroups: GenericUiAndroidPermissionGroupConfig = GenericUiAndroidPermissionGroupConfig()
    val usesFeatures: MutableList<GenericUiAndroidUsesFeatureSpec> = mutableListOf()
    val inlineServices: MutableList<GenericUiAndroidServiceSpec> = mutableListOf()

    // --- Nested DSL Blocks & Collections ---
    val networkSecurity: GenericUiAndroidNetworkSecurityConfig = GenericUiAndroidNetworkSecurityConfig()
    val activityDefaults: GenericUiAndroidActivityDefaultsSpec = GenericUiAndroidActivityDefaultsSpec()
    val mainActivity: GenericUiAndroidActivityDefaultsSpec = GenericUiAndroidActivityDefaultsSpec()
    val activityOverrides: MutableMap<String, GenericUiAndroidActivityOverrideSpec> = linkedMapOf()
    val queries: GenericUiAndroidQueriesConfig = GenericUiAndroidQueriesConfig()
    val androidTv: GenericUiAndroidTvConfig = GenericUiAndroidTvConfig()
    val androidAuto: GenericUiAndroidAutoConfig = GenericUiAndroidAutoConfig()
    val shortcuts: GenericUiAndroidShortcutsConfig = GenericUiAndroidShortcutsConfig()
    val locales: MutableList<String> = mutableListOf()
    val backupRules: GenericUiAndroidBackupRulesConfig = GenericUiAndroidBackupRulesConfig()
    val fileProvider: GenericUiAndroidFileProviderConfig = GenericUiAndroidFileProviderConfig()
    val widgets: MutableList<GenericUiAndroidWidgetSpec> = mutableListOf()
    val receivers: MutableList<GenericUiAndroidReceiverSpec> = mutableListOf()
    val providers: MutableList<GenericUiAndroidProviderSpec> = mutableListOf()
    val applicationMetaData: MutableList<GenericUiAndroidMetaDataSpec> = mutableListOf()
    val manifestAttributes: MutableMap<String, String> = linkedMapOf()
    val applicationAttributes: MutableMap<String, String> = linkedMapOf()
    val placeholders: MutableMap<String, String> = linkedMapOf()
    val mergeManifestPaths: MutableList<String> = mutableListOf()

    /** Adds one or more Android manifest permissions (e.g. `"android.permission.INTERNET"`). */
    fun permissions(vararg perms: String) {
        for (perm in perms) {
            permission(perm)
        }
    }

    /**
     * Adds a single Android manifest permission with optional `maxSdkVersion` and
     * `usesPermissionFlags` (for example `"neverForLocation"`).
     */
    @JvmOverloads
    fun permission(
        name: String,
        maxSdkVersion: Int? = null,
        usesPermissionFlags: String? = null
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (trimmed !in permissions) {
            permissions.add(trimmed)
        }
        val cleanedFlags = usesPermissionFlags?.trim()?.takeIf { it.isNotEmpty() }
        if (maxSdkVersion != null || cleanedFlags != null) {
            permissionSpecs.removeAll { it.name == trimmed }
            permissionSpecs.add(
                GenericUiAndroidPermissionSpec(
                    name = trimmed,
                    maxSdkVersion = maxSdkVersion,
                    usesPermissionFlags = cleanedFlags
                )
            )
        }
    }

    /** Configures preset Android permission groups. */
    fun permissionGroup(action: Action<in GenericUiAndroidPermissionGroupConfig>) {
        action.execute(permissionGroups)
    }

    /** Declares an Android `<uses-feature>` entry by [name] or OpenGL ES [glEsVersion]. */
    @JvmOverloads
    fun usesFeature(
        name: String = "",
        required: Boolean = true,
        glEsVersion: Int? = null
    ) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() || glEsVersion != null) {
            "Android <uses-feature> must specify either a non-blank name or glEsVersion."
        }
        val spec = GenericUiAndroidUsesFeatureSpec(
            name = trimmedName,
            required = required,
            glEsVersion = glEsVersion
        )
        if (trimmedName.isNotEmpty()) {
            usesFeatures.removeAll { it.name == trimmedName }
        } else {
            usesFeatures.removeAll { it.glEsVersion == glEsVersion && it.name.isEmpty() }
        }
        usesFeatures.add(spec)
    }

    /**
     * Declares an inline foreground `<service>` and automatically adds `FOREGROUND_SERVICE` plus
     * `FOREGROUND_SERVICE_<UPPER_SNAKE_TYPE>` permissions for every type token in [foregroundServiceType].
     */
    @JvmOverloads
    fun foregroundService(
        className: String,
        foregroundServiceType: String,
        exported: Boolean = false
    ): GenericUiAndroidServiceSpec {
        val trimmedClass = className.trim()
        val trimmedType = foregroundServiceType.trim()
        require(trimmedClass.isNotEmpty()) { "Foreground service className must not be blank." }
        require(trimmedType.isNotEmpty()) { "Foreground service foregroundServiceType must not be blank." }

        permission("android.permission.FOREGROUND_SERVICE")
        for (rawType in trimmedType.split('|')) {
            val typeToken = rawType.trim()
            if (typeToken.isNotEmpty()) {
                val upperSnake = toUpperSnakeCase(typeToken)
                permission("android.permission.FOREGROUND_SERVICE_$upperSnake")
            }
        }

        val existing = inlineServices.firstOrNull { it.className == trimmedClass }
        val spec = existing ?: GenericUiAndroidServiceSpec(trimmedClass).also { inlineServices.add(it) }
        spec.foregroundServiceType = trimmedType
        spec.exported = exported
        spec.autoAddForegroundServicePermission = true
        return spec
    }

    /** Configures Android `network_security_config.xml`. */
    fun networkSecurity(action: Action<in GenericUiAndroidNetworkSecurityConfig>) {
        action.execute(networkSecurity)
    }

    /** Configures default attributes and child elements applied to all generated `<activity>` entries. */
    fun activityDefaults(action: Action<in GenericUiAndroidActivityDefaultsSpec>) {
        action.execute(activityDefaults)
    }

    /** Configures overrides and child elements applied specifically to the `@MainAppFragment` `<activity>`. */
    fun mainActivity(action: Action<in GenericUiAndroidActivityDefaultsSpec>) {
        action.execute(mainActivity)
    }

    /** Configures overrides for a specific `@AppFragment`/`@MainAppFragment` activity (by simple name or FQN). */
    fun activity(
        fragmentName: String,
        action: Action<in GenericUiAndroidActivityOverrideSpec>
    ) {
        val trimmed = fragmentName.trim()
        require(trimmed.isNotEmpty()) { "Android activity override fragmentName must not be blank." }
        val existing = activityOverrides[trimmed]
        val spec = existing ?: GenericUiAndroidActivityOverrideSpec(trimmed).also {
            activityOverrides[trimmed] = it
        }
        action.execute(spec)
    }

    /** Adds an Android App Link / Deep Link `<intent-filter>` to [mainActivity]. */
    @JvmOverloads
    fun deepLink(uriPattern: String, autoVerify: Boolean = false) {
        mainActivity.deepLink(uriPattern, autoVerify)
    }

    /** Configures Android 11+ `<queries>` package visibility rules. */
    fun queries(action: Action<in GenericUiAndroidQueriesConfig>) {
        action.execute(queries)
    }

    /** Configures Android TV / Leanback manifest support. */
    fun androidTv(action: Action<in GenericUiAndroidTvConfig>) {
        action.execute(androidTv)
        if ("enabled" !in androidTv.explicitFields) {
            androidTv.enabled = true
        }
    }

    /** Configures Android Auto manifest & `automotive_app_desc.xml` support. */
    fun androidAuto(action: Action<in GenericUiAndroidAutoConfig>) {
        action.execute(androidAuto)
        if ("enabled" !in androidAuto.explicitFields) {
            androidAuto.enabled = true
        }
    }

    /** Configures static Android App Shortcuts (`res/xml/shortcuts.xml`). */
    fun shortcuts(action: Action<in GenericUiAndroidShortcutsConfig>) {
        action.execute(shortcuts)
        if ("enabled" !in shortcuts.explicitFields) {
            shortcuts.enabled = true
        }
    }

    /** Declares supported BCP-47 locale tags for Android 13+ `res/xml/locales_config.xml`. */
    fun locales(vararg tags: String) {
        for (tag in tags) {
            val trimmed = tag.trim()
            if (trimmed.isNotEmpty() && trimmed !in locales) {
                locales.add(trimmed)
            }
        }
    }

    /** Configures Android Auto Backup (`backup_rules.xml`) and Data Extraction Rules (`data_extraction_rules.xml`). */
    fun backupRules(action: Action<in GenericUiAndroidBackupRulesConfig>) {
        action.execute(backupRules)
    }

    /** Configures synthesized AndroidX `FileProvider` and `res/xml/file_paths.xml`. */
    fun fileProvider(action: Action<in GenericUiAndroidFileProviderConfig>) {
        action.execute(fileProvider)
        if ("enabled" !in fileProvider.explicitFields) {
            fileProvider.enabled = true
        }
    }

    /** Declares an AppWidget `<receiver>` and generates `res/xml/<widget>_info.xml`. */
    fun widget(
        providerClass: String,
        action: Action<in GenericUiAndroidWidgetSpec>
    ) {
        val trimmed = providerClass.trim()
        require(trimmed.isNotEmpty()) { "Android widget providerClass must not be blank." }
        val existing = widgets.firstOrNull { it.providerClass == trimmed }
        val spec = existing ?: GenericUiAndroidWidgetSpec(trimmed).also { widgets.add(it) }
        action.execute(spec)
    }

    /** Declares a custom `<receiver>` component. */
    fun receiver(
        className: String,
        action: Action<in GenericUiAndroidReceiverSpec>
    ) {
        val trimmed = className.trim()
        require(trimmed.isNotEmpty()) { "Android receiver className must not be blank." }
        val existing = receivers.firstOrNull { it.className == trimmed }
        val spec = existing ?: GenericUiAndroidReceiverSpec(trimmed).also { receivers.add(it) }
        action.execute(spec)
    }

    /** Declares a custom `<provider>` component. */
    fun provider(
        className: String,
        action: Action<in GenericUiAndroidProviderSpec>
    ) {
        val trimmed = className.trim()
        require(trimmed.isNotEmpty()) { "Android provider className must not be blank." }
        val existing = providers.firstOrNull { it.className == trimmed }
        val spec = existing ?: GenericUiAndroidProviderSpec(trimmed).also { providers.add(it) }
        action.execute(spec)
    }

    /** Adds or replaces a `<meta-data>` element inside `<application>`. */
    @JvmOverloads
    fun applicationMetaData(name: String, value: String? = null, resource: String? = null) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Application <meta-data> name must not be blank." }
        applicationMetaData.removeAll { it.name == trimmed }
        applicationMetaData.add(GenericUiAndroidMetaDataSpec(name = trimmed, value = value, resource = resource))
    }

    /** Sets a custom raw XML attribute on `<manifest>` (defaults to `android:` when unprefixed). */
    fun manifestAttribute(name: String, value: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Manifest attribute name must not be blank." }
        manifestAttributes[trimmed] = value
    }

    /** Sets a custom raw XML attribute on `<application>` (defaults to `android:` when unprefixed). */
    fun applicationAttribute(name: String, value: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Application attribute name must not be blank." }
        applicationAttributes[trimmed] = value
    }

    /** Sets a manifest placeholder value substituted for `${key}` during generation and merging. */
    fun placeholder(key: String, value: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "Manifest placeholder key must not be blank." }
        placeholders[trimmed] = value
    }

    /** Adds an overlay `AndroidManifest.xml` file path (relative to project root or absolute) to merge via `ManifestMerger2`. */
    fun mergeManifest(path: String) {
        val trimmed = path.trim()
        if (trimmed.isNotEmpty() && trimmed !in mergeManifestPaths) {
            mergeManifestPaths.add(trimmed)
        }
    }

    /** Resolves effective permission specifications across [permissions], [permissionSpecs], [permissionGroups], and inline services. */
    fun resolveAllPermissions(extraServices: List<GenericUiAndroidServiceSpec> = emptyList()): List<GenericUiAndroidPermissionSpec> {
        val merged = LinkedHashMap<String, GenericUiAndroidPermissionSpec>()
        for (groupPerm in permissionGroups.resolvePermissions()) {
            merged[groupPerm.name] = groupPerm
        }
        for (rawPerm in permissions) {
            val trimmed = rawPerm.trim()
            if (trimmed.isNotEmpty() && trimmed !in merged) {
                merged[trimmed] = GenericUiAndroidPermissionSpec(trimmed)
            }
        }
        for (spec in permissionSpecs) {
            merged[spec.name] = spec.copy()
        }
        for (svc in inlineServices + extraServices) {
            if (svc.autoAddForegroundServicePermission && svc.foregroundServiceType.isNotBlank()) {
                merged.putIfAbsent(
                    "android.permission.FOREGROUND_SERVICE",
                    GenericUiAndroidPermissionSpec("android.permission.FOREGROUND_SERVICE")
                )
                for (rawType in svc.foregroundServiceType.split('|')) {
                    val token = rawType.trim()
                    if (token.isNotEmpty()) {
                        val permName = "android.permission.FOREGROUND_SERVICE_${toUpperSnakeCase(token)}"
                        merged.putIfAbsent(permName, GenericUiAndroidPermissionSpec(permName))
                    }
                }
            }
        }
        return merged.values.toList()
    }

    /** Returns an independent deep copy of this manifest configuration. */
    fun copyConfig(): GenericUiAndroidManifestConfig = GenericUiAndroidManifestConfig().also { copy ->
        copy.versionCode = versionCode
        copy.versionName = versionName
        copy.minSdk = minSdk
        copy.compileSdk = compileSdk
        copy.targetSdk = targetSdk
        copy.screenOrientation = screenOrientation
        copy.configChanges = configChanges
        copy.supportsPictureInPicture = supportsPictureInPicture
        copy.applicationClass = applicationClass
        copy.theme = theme
        copy.usesCleartextTraffic = usesCleartextTraffic
        copy.allowBackup = allowBackup
        copy.fullBackupContent = fullBackupContent
        copy.dataExtractionRules = dataExtractionRules
        copy.largeHeap = largeHeap
        copy.hardwareAccelerated = hardwareAccelerated
        copy.supportsRtl = supportsRtl
        copy.icon = icon
        copy.roundIcon = roundIcon
        copy.banner = banner
        copy.logo = logo
        copy.appCategory = appCategory
        copy.localeConfig = localeConfig
        copy.enableOnBackInvokedCallback = enableOnBackInvokedCallback
        copy.resizeableActivity = resizeableActivity
        copy.extractNativeLibs = extractNativeLibs
        copy.requestLegacyExternalStorage = requestLegacyExternalStorage
        copy.allowNativeHeapPointerTagging = allowNativeHeapPointerTagging
        copy.isGame = isGame
        copy.debuggable = debuggable
        copy.networkSecurityConfig = networkSecurityConfig

        copy.permissions.addAll(permissions)
        permissionSpecs.mapTo(copy.permissionSpecs) { it.copy() }
        val copiedGroups = permissionGroups.copySpec()
        copy.permissionGroups.networkEnabled = copiedGroups.networkEnabled
        copy.permissionGroups.mediaPlaybackEnabled = copiedGroups.mediaPlaybackEnabled
        copy.permissionGroups.notificationsEnabled = copiedGroups.notificationsEnabled
        copy.permissionGroups.bluetoothEnabled = copiedGroups.bluetoothEnabled
        copy.permissionGroups.bluetoothNearbyDevicesOnly = copiedGroups.bluetoothNearbyDevicesOnly
        copy.permissionGroups.locationEnabled = copiedGroups.locationEnabled
        copy.permissionGroups.locationPrecise = copiedGroups.locationPrecise
        copy.permissionGroups.cameraEnabled = copiedGroups.cameraEnabled
        copy.permissionGroups.microphoneEnabled = copiedGroups.microphoneEnabled
        copy.permissionGroups.storageMediaEnabled = copiedGroups.storageMediaEnabled
        copy.permissionGroups.storageImages = copiedGroups.storageImages
        copy.permissionGroups.storageVideo = copiedGroups.storageVideo
        copy.permissionGroups.storageAudio = copiedGroups.storageAudio

        usesFeatures.mapTo(copy.usesFeatures) { it.copy() }
        inlineServices.mapTo(copy.inlineServices) { it.copySpec() }

        val copiedNet = networkSecurity.copySpec()
        copy.networkSecurity.cleartextPermittedByDefault = copiedNet.cleartextPermittedByDefault
        copy.networkSecurity.trustUserCertificates = copiedNet.trustUserCertificates
        copy.networkSecurity.baseConfig = copiedNet.baseConfig
        copy.networkSecurity.domainConfigs.addAll(copiedNet.domainConfigs)
        copy.networkSecurity.debugOverridesTrustAnchors = copiedNet.debugOverridesTrustAnchors

        copy.activityDefaults.overlayWith(activityDefaults)
        copy.mainActivity.overlayWith(mainActivity)
        activityOverrides.forEach { (k, v) -> copy.activityOverrides[k] = v.copyOverride() }

        val copiedQueries = queries.copySpec()
        copy.queries.packageNames.addAll(copiedQueries.packageNames)
        copy.queries.intents.addAll(copiedQueries.intents)
        copy.queries.providerAuthorities.addAll(copiedQueries.providerAuthorities)

        val copiedTv = androidTv.copySpec()
        copy.androidTv.enabled = copiedTv.enabled
        copy.androidTv.required = copiedTv.required
        copy.androidTv.banner = copiedTv.banner
        copy.androidTv.explicitFields.clear()
        copy.androidTv.explicitFields.addAll(copiedTv.explicitFields)

        val copiedAuto = androidAuto.copySpec()
        copy.androidAuto.enabled = copiedAuto.enabled
        copy.androidAuto.mediaBrowserService = copiedAuto.mediaBrowserService
        copy.androidAuto.usesMedia = copiedAuto.usesMedia
        copy.androidAuto.usesNotification = copiedAuto.usesNotification
        copy.androidAuto.explicitFields.clear()
        copy.androidAuto.explicitFields.addAll(copiedAuto.explicitFields)

        val copiedShortcuts = shortcuts.copySpec()
        copy.shortcuts.enabled = copiedShortcuts.enabled
        copy.shortcuts.inheritFromWebManifest = copiedShortcuts.inheritFromWebManifest
        copy.shortcuts.shortcuts.addAll(copiedShortcuts.shortcuts)
        copy.shortcuts.explicitFields.clear()
        copy.shortcuts.explicitFields.addAll(copiedShortcuts.explicitFields)

        copy.locales.addAll(locales)

        val copiedBackup = backupRules.copySpec()
        copy.backupRules.cloudRules.addAll(copiedBackup.cloudRules)
        copy.backupRules.deviceTransferRules.addAll(copiedBackup.deviceTransferRules)

        val copiedFp = fileProvider.copySpec()
        copy.fileProvider.enabled = copiedFp.enabled
        copy.fileProvider.providerClass = copiedFp.providerClass
        copy.fileProvider.authorities = copiedFp.authorities
        copy.fileProvider.paths.addAll(copiedFp.paths)
        copy.fileProvider.explicitFields.clear()
        copy.fileProvider.explicitFields.addAll(copiedFp.explicitFields)

        widgets.mapTo(copy.widgets) { it.copySpec() }
        receivers.mapTo(copy.receivers) { it.copySpec() }
        providers.mapTo(copy.providers) { it.copySpec() }
        applicationMetaData.mapTo(copy.applicationMetaData) { it.copy() }
        copy.manifestAttributes.putAll(manifestAttributes)
        copy.applicationAttributes.putAll(applicationAttributes)
        copy.placeholders.putAll(placeholders)
        copy.mergeManifestPaths.addAll(mergeManifestPaths)

        copy.explicitFields.clear()
        copy.explicitFields.addAll(explicitFields)
    }

    companion object {
        internal fun toUpperSnakeCase(token: String): String = buildString {
            val cleaned = token.trim().replace('-', '_')
            for (i in cleaned.indices) {
                val ch = cleaned[i]
                if (ch.isUpperCase() && i > 0) {
                    val prev = cleaned[i - 1]
                    if (prev.isLowerCase() || prev.isDigit()) {
                        append('_')
                    }
                }
                append(ch.uppercaseChar())
            }
        }.replace(Regex("_+"), "_").trim('_')
    }
}
