package com.nexustools.genericui.plugin

import org.gradle.api.GradleException

/**
 * Deterministic, sorted pure-Kotlin JSON serializer and deserializer for
 * [GenericUiAndroidManifestModel] (zero external JSON runtime dependencies).
 */
object GenericUiAndroidManifestModelSerializer {

    /**
     * Captures an immutable [GenericUiAndroidManifestModel] snapshot from [manifestConfig],
     * external [services], optional [variantOverrides], and optional [inheritedWebShortcuts].
     */
    fun toModel(
        manifestConfig: GenericUiAndroidManifestConfig,
        services: List<GenericUiAndroidServiceSpec> = emptyList(),
        variantOverrides: Map<String, GenericUiAndroidVariantOverrideConfig> = emptyMap(),
        inheritedWebShortcuts: List<GenericUiWebManifestShortcutSpec> = emptyList()
    ): GenericUiAndroidManifestModel {
        val mergedServices = mergeServices(services, manifestConfig.inlineServices)
        val resolvedPerms = manifestConfig.resolveAllPermissions(services)
        val resolvedVariants = linkedMapOf<String, GenericUiAndroidVariantManifestModel>()
        for (variantName in variantOverrides.keys.sorted()) {
            val overrideSpec = variantOverrides.getValue(variantName)
            val overlaidConfig = overrideSpec.overlayFrom(manifestConfig)
            val variantModel = toModel(
                manifestConfig = overlaidConfig,
                services = services,
                variantOverrides = emptyMap(),
                inheritedWebShortcuts = inheritedWebShortcuts
            )
            resolvedVariants[variantName] = GenericUiAndroidVariantManifestModel(
                variantName = variantName,
                applicationIdSuffix = overrideSpec.applicationIdSuffix,
                versionNameSuffix = overrideSpec.versionNameSuffix,
                manifest = variantModel
            )
        }

        return GenericUiAndroidManifestModel(
            versionCode = manifestConfig.versionCode,
            versionName = manifestConfig.versionName,
            minSdk = manifestConfig.minSdk,
            compileSdk = manifestConfig.compileSdk,
            targetSdk = manifestConfig.targetSdk,
            applicationClass = manifestConfig.applicationClass,
            theme = manifestConfig.theme,
            screenOrientation = manifestConfig.screenOrientation,
            configChanges = manifestConfig.configChanges,
            supportsPictureInPicture = manifestConfig.supportsPictureInPicture,
            usesCleartextTraffic = manifestConfig.usesCleartextTraffic,
            allowBackup = manifestConfig.allowBackup,
            fullBackupContent = manifestConfig.fullBackupContent,
            dataExtractionRules = manifestConfig.dataExtractionRules,
            largeHeap = manifestConfig.largeHeap,
            hardwareAccelerated = manifestConfig.hardwareAccelerated,
            supportsRtl = manifestConfig.supportsRtl,
            icon = manifestConfig.icon,
            roundIcon = manifestConfig.roundIcon,
            banner = manifestConfig.banner,
            logo = manifestConfig.logo,
            appCategory = manifestConfig.appCategory,
            localeConfig = manifestConfig.localeConfig,
            enableOnBackInvokedCallback = manifestConfig.enableOnBackInvokedCallback,
            resizeableActivity = manifestConfig.resizeableActivity,
            extractNativeLibs = manifestConfig.extractNativeLibs,
            requestLegacyExternalStorage = manifestConfig.requestLegacyExternalStorage,
            allowNativeHeapPointerTagging = manifestConfig.allowNativeHeapPointerTagging,
            isGame = manifestConfig.isGame,
            debuggable = manifestConfig.debuggable,
            networkSecurityConfig = manifestConfig.networkSecurityConfig,
            permissions = resolvedPerms.map { it.copy() },
            usesFeatures = manifestConfig.usesFeatures.map { it.copy() },
            networkSecurity = manifestConfig.networkSecurity.copySpec(),
            activityDefaults = manifestConfig.activityDefaults.copyDefaults(),
            mainActivity = manifestConfig.mainActivity.copyDefaults(),
            activityOverrides = manifestConfig.activityOverrides.entries
                .sortedBy { it.key }
                .associate { (k, v) -> k to v.copyOverride() },
            queries = manifestConfig.queries.copySpec(),
            androidTv = manifestConfig.androidTv.copySpec(),
            androidAuto = manifestConfig.androidAuto.copySpec(),
            shortcuts = manifestConfig.shortcuts.copySpec(),
            inheritedWebShortcuts = inheritedWebShortcuts.map { it.copy() },
            locales = manifestConfig.locales.toList(),
            backupRules = manifestConfig.backupRules.copySpec(),
            fileProvider = manifestConfig.fileProvider.copySpec(),
            widgets = manifestConfig.widgets.map { it.copySpec() },
            services = mergedServices,
            receivers = manifestConfig.receivers.map { it.copySpec() },
            providers = manifestConfig.providers.map { it.copySpec() },
            applicationMetaData = manifestConfig.applicationMetaData.map { it.copy() },
            manifestAttributes = manifestConfig.manifestAttributes.toSortedMap(),
            applicationAttributes = manifestConfig.applicationAttributes.toSortedMap(),
            placeholders = manifestConfig.placeholders.toSortedMap(),
            mergeManifestPaths = manifestConfig.mergeManifestPaths.toList(),
            variantOverrides = resolvedVariants
        )
    }

    /** Serializes [manifestConfig], [services], [variantOverrides], and [inheritedWebShortcuts] to sorted JSON. */
    fun toJson(
        manifestConfig: GenericUiAndroidManifestConfig,
        services: List<GenericUiAndroidServiceSpec> = emptyList(),
        variantOverrides: Map<String, GenericUiAndroidVariantOverrideConfig> = emptyMap(),
        inheritedWebShortcuts: List<GenericUiWebManifestShortcutSpec> = emptyList()
    ): String = toJson(toModel(manifestConfig, services, variantOverrides, inheritedWebShortcuts))

    /** Serializes [model] deterministically with sorted JSON object keys. */
    fun toJson(model: GenericUiAndroidManifestModel): String = buildString {
        writeJsonValue(modelToMap(model), this)
    }

    /** Deserializes a [GenericUiAndroidManifestModel] from deterministic JSON produced by [toJson]. */
    fun fromJson(json: String): GenericUiAndroidManifestModel {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            return GenericUiAndroidManifestModel()
        }
        val parsed = JsonParser(trimmed).parseRootObject()
        return mapToModel(parsed)
    }

    private fun mergeServices(
        externalServices: List<GenericUiAndroidServiceSpec>,
        inlineServices: List<GenericUiAndroidServiceSpec>
    ): List<GenericUiAndroidServiceSpec> {
        val merged = LinkedHashMap<String, GenericUiAndroidServiceSpec>()
        for (svc in externalServices) {
            merged[svc.className] = svc.copySpec()
        }
        for (svc in inlineServices) {
            val existing = merged[svc.className]
            if (existing == null) {
                merged[svc.className] = svc.copySpec()
            } else {
                if (svc.foregroundServiceType.isNotBlank()) {
                    existing.foregroundServiceType = svc.foregroundServiceType
                }
                if (svc.exported) {
                    existing.exported = true
                }
                if (svc.autoAddForegroundServicePermission) {
                    existing.autoAddForegroundServicePermission = true
                }
                if (svc.permission.isNotBlank()) {
                    existing.permission = svc.permission
                }
                if (svc.process.isNotBlank()) {
                    existing.process = svc.process
                }
                svc.intentActions.forEach { existing.intentAction(it) }
                svc.intentFilters.forEach { existing.intentFilters.add(it.copySpec()) }
                svc.metaDataList.forEach { m ->
                    existing.metaData(m.name, m.value, m.resource)
                }
            }
        }
        return merged.values.toList()
    }

    // --- Model -> JSON Map Tree ---

    private fun modelToMap(model: GenericUiAndroidManifestModel): Map<String, Any?> = linkedMapOf(
        "activityDefaults" to activityDefaultsToMap(model.activityDefaults),
        "activityOverrides" to model.activityOverrides.entries.sortedBy { it.key }.associate { (k, v) ->
            k to activityDefaultsToMap(v)
        },
        "allowBackup" to model.allowBackup,
        "allowNativeHeapPointerTagging" to model.allowNativeHeapPointerTagging,
        "androidAuto" to mapOf(
            "enabled" to model.androidAuto.enabled,
            "mediaBrowserService" to model.androidAuto.mediaBrowserService,
            "usesMedia" to model.androidAuto.usesMedia,
            "usesNotification" to model.androidAuto.usesNotification
        ),
        "androidTv" to mapOf(
            "banner" to model.androidTv.banner,
            "enabled" to model.androidTv.enabled,
            "required" to model.androidTv.required
        ),
        "appCategory" to model.appCategory,
        "applicationAttributes" to model.applicationAttributes.toSortedMap(),
        "applicationClass" to model.applicationClass,
        "applicationMetaData" to model.applicationMetaData.map(::metaDataToMap),
        "backupRules" to mapOf(
            "cloudRules" to model.backupRules.cloudRules.map(::backupRuleToMap),
            "deviceTransferRules" to model.backupRules.deviceTransferRules.map(::backupRuleToMap)
        ),
        "banner" to model.banner,
        "compileSdk" to model.compileSdk,
        "configChanges" to model.configChanges,
        "dataExtractionRules" to model.dataExtractionRules,
        "debuggable" to model.debuggable,
        "enableOnBackInvokedCallback" to model.enableOnBackInvokedCallback,
        "extractNativeLibs" to model.extractNativeLibs,
        "fileProvider" to mapOf(
            "authorities" to model.fileProvider.authorities,
            "enabled" to model.fileProvider.enabled,
            "paths" to model.fileProvider.paths.map { p ->
                mapOf("name" to p.name, "path" to p.path, "tag" to p.tag)
            },
            "providerClass" to model.fileProvider.providerClass
        ),
        "fullBackupContent" to model.fullBackupContent,
        "hardwareAccelerated" to model.hardwareAccelerated,
        "icon" to model.icon,
        "inheritedWebShortcuts" to model.inheritedWebShortcuts.map { ws ->
            mapOf(
                "description" to ws.description,
                "name" to ws.name,
                "shortName" to ws.shortName,
                "url" to ws.url
            )
        },
        "isGame" to model.isGame,
        "largeHeap" to model.largeHeap,
        "localeConfig" to model.localeConfig,
        "locales" to model.locales,
        "logo" to model.logo,
        "mainActivity" to activityDefaultsToMap(model.mainActivity),
        "manifestAttributes" to model.manifestAttributes.toSortedMap(),
        "mergeManifestPaths" to model.mergeManifestPaths,
        "minSdk" to model.minSdk,
        "networkSecurity" to networkSecurityToMap(model.networkSecurity),
        "networkSecurityConfig" to model.networkSecurityConfig,
        "permissions" to model.permissions.map { p ->
            mapOf(
                "maxSdkVersion" to p.maxSdkVersion,
                "name" to p.name,
                "usesPermissionFlags" to p.usesPermissionFlags
            )
        },
        "placeholders" to model.placeholders.toSortedMap(),
        "providers" to model.providers.map(::providerToMap),
        "queries" to mapOf(
            "intents" to model.queries.intents.map(::intentFilterToMap),
            "packageNames" to model.queries.packageNames,
            "providerAuthorities" to model.queries.providerAuthorities
        ),
        "receivers" to model.receivers.map(::receiverToMap),
        "requestLegacyExternalStorage" to model.requestLegacyExternalStorage,
        "resizeableActivity" to model.resizeableActivity,
        "roundIcon" to model.roundIcon,
        "screenOrientation" to model.screenOrientation,
        "services" to model.services.map(::serviceToMap),
        "shortcuts" to mapOf(
            "enabled" to model.shortcuts.enabled,
            "inheritFromWebManifest" to model.shortcuts.inheritFromWebManifest,
            "shortcuts" to model.shortcuts.shortcuts.map { s ->
                mapOf(
                    "icon" to s.icon,
                    "longLabel" to s.longLabel,
                    "shortLabel" to s.shortLabel,
                    "shortcutId" to s.shortcutId,
                    "targetFragment" to s.targetFragment,
                    "urlOrAction" to s.urlOrAction
                )
            }
        ),
        "supportsPictureInPicture" to model.supportsPictureInPicture,
        "supportsRtl" to model.supportsRtl,
        "targetSdk" to model.targetSdk,
        "theme" to model.theme,
        "usesCleartextTraffic" to model.usesCleartextTraffic,
        "usesFeatures" to model.usesFeatures.map { f ->
            mapOf(
                "glEsVersion" to f.glEsVersion,
                "name" to f.name,
                "required" to f.required
            )
        },
        "variantOverrides" to model.variantOverrides.entries.sortedBy { it.key }.associate { (k, v) ->
            k to mapOf(
                "applicationIdSuffix" to v.applicationIdSuffix,
                "manifest" to modelToMap(v.manifest),
                "variantName" to v.variantName,
                "versionNameSuffix" to v.versionNameSuffix
            )
        },
        "versionCode" to model.versionCode,
        "versionName" to model.versionName,
        "widgets" to model.widgets.map(::widgetToMap)
    )

    private fun activityDefaultsToMap(spec: GenericUiAndroidActivityDefaultsSpec): Map<String, Any?> = mapOf(
        "activityClass" to spec.activityClass,
        "configChanges" to spec.configChanges,
        "documentLaunchMode" to spec.documentLaunchMode,
        "excludeFromRecents" to spec.excludeFromRecents,
        "exported" to spec.exported,
        "hardwareAccelerated" to spec.hardwareAccelerated,
        "intentFilters" to spec.intentFilters.map(::intentFilterToMap),
        "launchMode" to spec.launchMode,
        "launcher" to spec.launcher,
        "lockTaskMode" to spec.lockTaskMode,
        "maxRecents" to spec.maxRecents,
        "metaDataList" to spec.metaDataList.map(::metaDataToMap),
        "noHistory" to spec.noHistory,
        "properties" to spec.properties.toSortedMap(),
        "rawAttributes" to spec.rawAttributes.toSortedMap(),
        "resizeableActivity" to spec.resizeableActivity,
        "screenOrientation" to spec.screenOrientation,
        "showWhenLocked" to spec.showWhenLocked,
        "supportsPictureInPicture" to spec.supportsPictureInPicture,
        "taskAffinity" to spec.taskAffinity,
        "theme" to spec.theme,
        "turnScreenOn" to spec.turnScreenOn,
        "windowSoftInputMode" to spec.windowSoftInputMode
    )

    private fun intentFilterToMap(spec: GenericUiAndroidIntentFilterSpec): Map<String, Any?> = mapOf(
        "actions" to spec.actions,
        "autoVerify" to spec.autoVerify,
        "categories" to spec.categories,
        "dataSpecs" to spec.dataSpecs.map { d ->
            mapOf(
                "host" to d.host,
                "mimeType" to d.mimeType,
                "path" to d.path,
                "pathPattern" to d.pathPattern,
                "pathPrefix" to d.pathPrefix,
                "port" to d.port,
                "scheme" to d.scheme
            )
        },
        "label" to spec.label,
        "priority" to spec.priority
    )

    private fun metaDataToMap(spec: GenericUiAndroidMetaDataSpec): Map<String, Any?> = mapOf(
        "name" to spec.name,
        "resource" to spec.resource,
        "value" to spec.value
    )

    private fun networkSecurityToMap(spec: GenericUiAndroidNetworkSecurityConfig): Map<String, Any?> = mapOf(
        "baseConfig" to spec.baseConfig?.let { bc ->
            mapOf(
                "cleartextPermitted" to bc.cleartextPermitted,
                "trustAnchors" to bc.trustAnchors?.let(::trustAnchorsToMap)
            )
        },
        "cleartextPermittedByDefault" to spec.cleartextPermittedByDefault,
        "debugOverridesTrustAnchors" to spec.debugOverridesTrustAnchors?.let(::trustAnchorsToMap),
        "domainConfigs" to spec.domainConfigs.map { dc ->
            mapOf(
                "cleartextPermitted" to dc.cleartextPermitted,
                "domains" to dc.domains,
                "includeSubdomains" to dc.includeSubdomains,
                "pinSet" to dc.pinSet?.let { ps ->
                    mapOf(
                        "expiration" to ps.expiration,
                        "pins" to ps.pins.map { pin ->
                            mapOf("digest" to pin.digest, "value" to pin.value)
                        }
                    )
                },
                "trustAnchors" to dc.trustAnchors?.let(::trustAnchorsToMap)
            )
        },
        "trustUserCertificates" to spec.trustUserCertificates
    )

    private fun trustAnchorsToMap(spec: GenericUiAndroidTrustAnchorsSpec): Map<String, Any?> = mapOf(
        "certificateResources" to spec.certificateResources,
        "trustSystem" to spec.trustSystem,
        "trustUser" to spec.trustUser
    )

    private fun backupRuleToMap(spec: GenericUiAndroidBackupRuleSpec): Map<String, Any?> = mapOf(
        "domain" to spec.domain,
        "include" to spec.include,
        "path" to spec.path
    )

    private fun serviceToMap(spec: GenericUiAndroidServiceSpec): Map<String, Any?> = mapOf(
        "autoAddForegroundServicePermission" to spec.autoAddForegroundServicePermission,
        "className" to spec.className,
        "enabled" to spec.enabled,
        "exported" to spec.exported,
        "foregroundServiceType" to spec.foregroundServiceType,
        "intentActions" to spec.intentActions,
        "intentFilters" to spec.intentFilters.map(::intentFilterToMap),
        "metaDataList" to spec.metaDataList.map(::metaDataToMap),
        "permission" to spec.permission,
        "process" to spec.process
    )

    private fun receiverToMap(spec: GenericUiAndroidReceiverSpec): Map<String, Any?> = mapOf(
        "className" to spec.className,
        "enabled" to spec.enabled,
        "exported" to spec.exported,
        "intentFilters" to spec.intentFilters.map(::intentFilterToMap),
        "metaDataList" to spec.metaDataList.map(::metaDataToMap),
        "permission" to spec.permission,
        "process" to spec.process
    )

    private fun providerToMap(spec: GenericUiAndroidProviderSpec): Map<String, Any?> = mapOf(
        "authorities" to spec.authorities,
        "className" to spec.className,
        "enabled" to spec.enabled,
        "exported" to spec.exported,
        "grantUriPermissions" to spec.grantUriPermissions,
        "initOrder" to spec.initOrder,
        "metaDataList" to spec.metaDataList.map(::metaDataToMap)
    )

    private fun widgetToMap(spec: GenericUiAndroidWidgetSpec): Map<String, Any?> = mapOf(
        "descriptionRes" to spec.descriptionRes,
        "initialLayout" to spec.initialLayout,
        "minHeightDp" to spec.minHeightDp,
        "minWidthDp" to spec.minWidthDp,
        "previewImage" to spec.previewImage,
        "providerClass" to spec.providerClass,
        "resizeMode" to spec.resizeMode,
        "updatePeriodMillis" to spec.updatePeriodMillis,
        "widgetCategory" to spec.widgetCategory
    )

    // --- JSON Map Tree -> Model ---

    private fun mapToModel(map: Map<String, Any?>): GenericUiAndroidManifestModel {
        val defaults = GenericUiAndroidManifestModel()
        val activityDefaults = map.obj("activityDefaults")?.let(::mapToActivityDefaults)
            ?: GenericUiAndroidActivityDefaultsSpec()
        val mainActivity = map.obj("mainActivity")?.let(::mapToActivityDefaults)
            ?: GenericUiAndroidActivityDefaultsSpec()
        val activityOverrides = linkedMapOf<String, GenericUiAndroidActivityOverrideSpec>()
        map.obj("activityOverrides")?.forEach { (fragName, rawObj) ->
            val ovMap = asObject(rawObj) ?: return@forEach
            val ov = GenericUiAndroidActivityOverrideSpec(fragName)
            populateActivityDefaults(ov, ovMap)
            activityOverrides[fragName] = ov
        }

        val networkSecurity = map.obj("networkSecurity")?.let(::mapToNetworkSecurity)
            ?: GenericUiAndroidNetworkSecurityConfig()

        val queries = GenericUiAndroidQueriesConfig()
        map.obj("queries")?.let { qm ->
            qm.stringList("packageNames").forEach { queries.packageName(it) }
            qm.objList("intents").forEach { queries.intents.add(mapToIntentFilter(it)) }
            qm.stringList("providerAuthorities").forEach { queries.providerAuthority(it) }
        }

        val androidTv = GenericUiAndroidTvConfig()
        map.obj("androidTv")?.let { tvm ->
            tvm.bool("enabled")?.let { androidTv.enabled = it }
            tvm.bool("required")?.let { androidTv.required = it }
            if (tvm.containsKey("banner")) androidTv.banner = tvm.str("banner")
        }

        val androidAuto = GenericUiAndroidAutoConfig()
        map.obj("androidAuto")?.let { aam ->
            aam.bool("enabled")?.let { androidAuto.enabled = it }
            if (aam.containsKey("mediaBrowserService")) androidAuto.mediaBrowserService = aam.str("mediaBrowserService")
            aam.bool("usesMedia")?.let { androidAuto.usesMedia = it }
            aam.bool("usesNotification")?.let { androidAuto.usesNotification = it }
        }

        val shortcuts = GenericUiAndroidShortcutsConfig()
        map.obj("shortcuts")?.let { sm ->
            sm.bool("enabled")?.let { shortcuts.enabled = it }
            sm.bool("inheritFromWebManifest")?.let { shortcuts.inheritFromWebManifest = it }
            sm.objList("shortcuts").forEach { item ->
                val id = item.str("shortcutId").orEmpty()
                val shortLabel = item.str("shortLabel").orEmpty()
                if (id.isNotEmpty() && shortLabel.isNotEmpty()) {
                    shortcuts.shortcuts.add(
                        GenericUiAndroidShortcutSpec(
                            shortcutId = id,
                            shortLabel = shortLabel,
                            longLabel = item.str("longLabel"),
                            icon = item.str("icon"),
                            urlOrAction = item.str("urlOrAction").orEmpty(),
                            targetFragment = item.str("targetFragment")
                        )
                    )
                }
            }
        }

        val inheritedWebShortcuts = map.objList("inheritedWebShortcuts").mapNotNull { wm ->
            val name = wm.str("name").orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            GenericUiWebManifestShortcutSpec(
                name = name,
                shortName = wm.str("shortName").orEmpty(),
                description = wm.str("description").orEmpty(),
                url = wm.str("url").orEmpty().ifEmpty { "./" }
            )
        }

        val backupRules = GenericUiAndroidBackupRulesConfig()
        map.obj("backupRules")?.let { bm ->
            bm.objList("cloudRules").forEach { r ->
                backupRules.cloudRules.add(
                    GenericUiAndroidBackupRuleSpec(
                        include = r.bool("include") ?: true,
                        domain = r.str("domain").orEmpty(),
                        path = r.str("path").orEmpty().ifEmpty { "." }
                    )
                )
            }
            bm.objList("deviceTransferRules").forEach { r ->
                backupRules.deviceTransferRules.add(
                    GenericUiAndroidBackupRuleSpec(
                        include = r.bool("include") ?: true,
                        domain = r.str("domain").orEmpty(),
                        path = r.str("path").orEmpty().ifEmpty { "." }
                    )
                )
            }
        }

        val fileProvider = GenericUiAndroidFileProviderConfig()
        map.obj("fileProvider")?.let { fpm ->
            fpm.bool("enabled")?.let { fileProvider.enabled = it }
            fpm.str("providerClass")?.let { fileProvider.providerClass = it }
            fpm.str("authorities")?.let { fileProvider.authorities = it }
            fpm.objList("paths").forEach { p ->
                val tag = p.str("tag").orEmpty()
                val name = p.str("name").orEmpty()
                val path = p.str("path").orEmpty().ifEmpty { "." }
                if (tag.isNotEmpty() && name.isNotEmpty()) {
                    fileProvider.paths.add(GenericUiAndroidFileProviderPathSpec(tag, name, path))
                }
            }
        }

        val permissions = map.objList("permissions").mapNotNull { pm ->
            val name = pm.str("name")?.trim().orEmpty()
            if (name.isEmpty()) null else GenericUiAndroidPermissionSpec(
                name = name,
                maxSdkVersion = pm.int("maxSdkVersion"),
                usesPermissionFlags = pm.str("usesPermissionFlags")
            )
        }

        val usesFeatures = map.objList("usesFeatures").map { fm ->
            GenericUiAndroidUsesFeatureSpec(
                name = fm.str("name").orEmpty(),
                required = fm.bool("required") ?: true,
                glEsVersion = fm.int("glEsVersion")
            )
        }

        val widgets = map.objList("widgets").mapNotNull { wm ->
            val cls = wm.str("providerClass")?.trim().orEmpty()
            if (cls.isEmpty()) return@mapNotNull null
            GenericUiAndroidWidgetSpec(cls).apply {
                wm.int("minWidthDp")?.let { minWidthDp = it }
                wm.int("minHeightDp")?.let { minHeightDp = it }
                wm.int("updatePeriodMillis")?.let { updatePeriodMillis = it }
                if (wm.containsKey("previewImage")) previewImage = wm.str("previewImage")
                if (wm.containsKey("initialLayout")) initialLayout = wm.str("initialLayout")
                wm.str("resizeMode")?.let { resizeMode = it }
                wm.str("widgetCategory")?.let { widgetCategory = it }
                if (wm.containsKey("descriptionRes")) descriptionRes = wm.str("descriptionRes")
            }
        }

        val services = map.objList("services").mapNotNull(::mapToService)
        val receivers = map.objList("receivers").mapNotNull(::mapToReceiver)
        val providers = map.objList("providers").mapNotNull(::mapToProvider)
        val appMetaData = map.objList("applicationMetaData").mapNotNull(::mapToMetaData)

        val variants = linkedMapOf<String, GenericUiAndroidVariantManifestModel>()
        map.obj("variantOverrides")?.forEach { (key, rawVal) ->
            val vm = asObject(rawVal) ?: return@forEach
            val vName = vm.str("variantName")?.takeIf { it.isNotEmpty() } ?: key
            val innerMap = vm.obj("manifest") ?: return@forEach
            variants[key] = GenericUiAndroidVariantManifestModel(
                variantName = vName,
                applicationIdSuffix = vm.str("applicationIdSuffix").orEmpty(),
                versionNameSuffix = vm.str("versionNameSuffix").orEmpty(),
                manifest = mapToModel(innerMap)
            )
        }

        return GenericUiAndroidManifestModel(
            versionCode = map.int("versionCode"),
            versionName = map.str("versionName"),
            minSdk = map.int("minSdk") ?: defaults.minSdk,
            compileSdk = map.int("compileSdk") ?: defaults.compileSdk,
            targetSdk = map.int("targetSdk") ?: defaults.targetSdk,
            applicationClass = map.str("applicationClass") ?: defaults.applicationClass,
            theme = map.str("theme") ?: defaults.theme,
            screenOrientation = map.str("screenOrientation") ?: defaults.screenOrientation,
            configChanges = map.str("configChanges") ?: defaults.configChanges,
            supportsPictureInPicture = map.bool("supportsPictureInPicture") ?: defaults.supportsPictureInPicture,
            usesCleartextTraffic = map.bool("usesCleartextTraffic"),
            allowBackup = map.bool("allowBackup"),
            fullBackupContent = map.str("fullBackupContent"),
            dataExtractionRules = map.str("dataExtractionRules"),
            largeHeap = map.bool("largeHeap"),
            hardwareAccelerated = map.bool("hardwareAccelerated"),
            supportsRtl = map.bool("supportsRtl") ?: defaults.supportsRtl,
            icon = if (map.containsKey("icon")) map.str("icon") else defaults.icon,
            roundIcon = if (map.containsKey("roundIcon")) map.str("roundIcon") else defaults.roundIcon,
            banner = map.str("banner"),
            logo = map.str("logo"),
            appCategory = map.str("appCategory"),
            localeConfig = map.str("localeConfig"),
            enableOnBackInvokedCallback = map.bool("enableOnBackInvokedCallback"),
            resizeableActivity = map.bool("resizeableActivity"),
            extractNativeLibs = map.bool("extractNativeLibs"),
            requestLegacyExternalStorage = map.bool("requestLegacyExternalStorage"),
            allowNativeHeapPointerTagging = map.bool("allowNativeHeapPointerTagging"),
            isGame = map.bool("isGame"),
            debuggable = map.bool("debuggable"),
            networkSecurityConfig = map.str("networkSecurityConfig"),
            permissions = permissions,
            usesFeatures = usesFeatures,
            networkSecurity = networkSecurity,
            activityDefaults = activityDefaults,
            mainActivity = mainActivity,
            activityOverrides = activityOverrides,
            queries = queries,
            androidTv = androidTv,
            androidAuto = androidAuto,
            shortcuts = shortcuts,
            inheritedWebShortcuts = inheritedWebShortcuts,
            locales = map.stringList("locales"),
            backupRules = backupRules,
            fileProvider = fileProvider,
            widgets = widgets,
            services = services,
            receivers = receivers,
            providers = providers,
            applicationMetaData = appMetaData,
            manifestAttributes = map.stringMap("manifestAttributes"),
            applicationAttributes = map.stringMap("applicationAttributes"),
            placeholders = map.stringMap("placeholders"),
            mergeManifestPaths = map.stringList("mergeManifestPaths"),
            variantOverrides = variants
        )
    }

    private fun mapToActivityDefaults(map: Map<String, Any?>): GenericUiAndroidActivityDefaultsSpec =
        GenericUiAndroidActivityDefaultsSpec().also { populateActivityDefaults(it, map) }

    private fun populateActivityDefaults(spec: GenericUiAndroidActivityDefaultsSpec, map: Map<String, Any?>) {
        spec.activityClass = map.str("activityClass")
        spec.launcher = map.bool("launcher")
        spec.launchMode = map.str("launchMode")
        spec.windowSoftInputMode = map.str("windowSoftInputMode")
        spec.configChanges = map.str("configChanges")
        spec.screenOrientation = map.str("screenOrientation")
        spec.supportsPictureInPicture = map.bool("supportsPictureInPicture")
        spec.resizeableActivity = map.bool("resizeableActivity")
        spec.showWhenLocked = map.bool("showWhenLocked")
        spec.turnScreenOn = map.bool("turnScreenOn")
        spec.excludeFromRecents = map.bool("excludeFromRecents")
        spec.taskAffinity = map.str("taskAffinity")
        spec.theme = map.str("theme")
        spec.exported = map.bool("exported")
        spec.noHistory = map.bool("noHistory")
        spec.documentLaunchMode = map.str("documentLaunchMode")
        spec.maxRecents = map.int("maxRecents")
        spec.hardwareAccelerated = map.bool("hardwareAccelerated")
        spec.lockTaskMode = map.str("lockTaskMode")
        spec.properties.putAll(map.stringMap("properties"))
        spec.rawAttributes.putAll(map.stringMap("rawAttributes"))
        map.objList("metaDataList").mapNotNullTo(spec.metaDataList, ::mapToMetaData)
        map.objList("intentFilters").mapTo(spec.intentFilters, ::mapToIntentFilter)
    }

    private fun mapToIntentFilter(map: Map<String, Any?>): GenericUiAndroidIntentFilterSpec =
        GenericUiAndroidIntentFilterSpec().apply {
            map.stringList("actions").forEach { action(it) }
            map.stringList("categories").forEach { category(it) }
            autoVerify = map.bool("autoVerify") ?: false
            priority = map.int("priority")
            label = map.str("label")
            map.objList("dataSpecs").forEach { dm ->
                dataSpecs.add(
                    GenericUiAndroidIntentDataSpec().apply {
                        scheme = dm.str("scheme")
                        host = dm.str("host")
                        port = dm.str("port")
                        path = dm.str("path")
                        pathPrefix = dm.str("pathPrefix")
                        pathPattern = dm.str("pathPattern")
                        mimeType = dm.str("mimeType")
                    }
                )
            }
        }

    private fun mapToMetaData(map: Map<String, Any?>): GenericUiAndroidMetaDataSpec? {
        val name = map.str("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        return GenericUiAndroidMetaDataSpec(
            name = name,
            value = map.str("value"),
            resource = map.str("resource")
        )
    }

    private fun mapToNetworkSecurity(map: Map<String, Any?>): GenericUiAndroidNetworkSecurityConfig =
        GenericUiAndroidNetworkSecurityConfig().apply {
            cleartextPermittedByDefault = map.bool("cleartextPermittedByDefault")
            trustUserCertificates = map.bool("trustUserCertificates") ?: false
            map.obj("baseConfig")?.let { bm ->
                baseConfig = GenericUiAndroidNetworkBaseConfigSpec().apply {
                    cleartextPermitted = bm.bool("cleartextPermitted")
                    trustAnchors = bm.obj("trustAnchors")?.let(::mapToTrustAnchors)
                }
            }
            map.objList("domainConfigs").forEach { dm ->
                domainConfigs.add(
                    GenericUiAndroidNetworkDomainConfigSpec().apply {
                        domains.addAll(dm.stringList("domains"))
                        includeSubdomains = dm.bool("includeSubdomains") ?: false
                        cleartextPermitted = dm.bool("cleartextPermitted")
                        trustAnchors = dm.obj("trustAnchors")?.let(::mapToTrustAnchors)
                        dm.obj("pinSet")?.let { pm ->
                            pinSet = GenericUiAndroidPinSetSpec().apply {
                                expiration = pm.str("expiration")
                                pm.objList("pins").forEach { pinMap ->
                                    val value = pinMap.str("value").orEmpty()
                                    if (value.isNotEmpty()) {
                                        pins.add(
                                            GenericUiAndroidPinSpec(
                                                digest = pinMap.str("digest").orEmpty().ifEmpty { "SHA-256" },
                                                value = value
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
            debugOverridesTrustAnchors = map.obj("debugOverridesTrustAnchors")?.let(::mapToTrustAnchors)
        }

    private fun mapToTrustAnchors(map: Map<String, Any?>): GenericUiAndroidTrustAnchorsSpec =
        GenericUiAndroidTrustAnchorsSpec().apply {
            trustSystem = map.bool("trustSystem") ?: true
            trustUser = map.bool("trustUser") ?: false
            certificateResources.addAll(map.stringList("certificateResources"))
        }

    private fun mapToService(map: Map<String, Any?>): GenericUiAndroidServiceSpec? {
        val className = map.str("className")?.trim().orEmpty()
        if (className.isEmpty()) return null
        return GenericUiAndroidServiceSpec(className).apply {
            exported = map.bool("exported") ?: false
            enabled = map.bool("enabled") ?: true
            foregroundServiceType = map.str("foregroundServiceType").orEmpty()
            permission = map.str("permission").orEmpty()
            process = map.str("process").orEmpty()
            map.stringList("intentActions").forEach { intentAction(it) }
            map.objList("intentFilters").mapTo(intentFilters, ::mapToIntentFilter)
            map.objList("metaDataList").mapNotNullTo(metaDataList, ::mapToMetaData)
            autoAddForegroundServicePermission = map.bool("autoAddForegroundServicePermission") ?: false
        }
    }

    private fun mapToReceiver(map: Map<String, Any?>): GenericUiAndroidReceiverSpec? {
        val className = map.str("className")?.trim().orEmpty()
        if (className.isEmpty()) return null
        return GenericUiAndroidReceiverSpec(className).apply {
            exported = map.bool("exported")
            enabled = map.bool("enabled")
            permission = map.str("permission")
            process = map.str("process")
            map.objList("intentFilters").mapTo(intentFilters, ::mapToIntentFilter)
            map.objList("metaDataList").mapNotNullTo(metaDataList, ::mapToMetaData)
        }
    }

    private fun mapToProvider(map: Map<String, Any?>): GenericUiAndroidProviderSpec? {
        val className = map.str("className")?.trim().orEmpty()
        if (className.isEmpty()) return null
        return GenericUiAndroidProviderSpec(className).apply {
            authorities = map.str("authorities").orEmpty()
            exported = map.bool("exported") ?: false
            enabled = map.bool("enabled")
            grantUriPermissions = map.bool("grantUriPermissions") ?: false
            initOrder = map.int("initOrder")
            map.objList("metaDataList").mapNotNullTo(metaDataList, ::mapToMetaData)
        }
    }

    // --- Map Extraction Helpers ---

    @Suppress("UNCHECKED_CAST")
    private fun asObject(raw: Any?): Map<String, Any?>? = raw as? Map<String, Any?>

    private fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = asObject(this[key])

    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { it as? String }.orEmpty()

    private fun Map<String, Any?>.objList(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>)?.mapNotNull { asObject(it) }.orEmpty()

    private fun Map<String, Any?>.stringMap(key: String): Map<String, String> {
        val m = obj(key) ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        for (k in m.keys.sorted()) {
            val v = m[k] as? String ?: continue
            result[k] = v
        }
        return result
    }

    // --- Pure Kotlin Deterministic Sorted JSON Writer ---

    private fun writeJsonValue(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Number -> out.append(value.toString())
            is String -> writeJsonString(value, out)
            is Map<*, *> -> {
                out.append('{')
                val sortedKeys = value.keys.mapNotNull { it?.toString() }.sorted()
                sortedKeys.forEachIndexed { idx, key ->
                    if (idx > 0) out.append(',')
                    writeJsonString(key, out)
                    out.append(':')
                    writeJsonValue(value[key], out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (elem in value) {
                    if (!first) out.append(',')
                    first = false
                    writeJsonValue(elem, out)
                }
                out.append(']')
            }
            else -> writeJsonString(value.toString(), out)
        }
    }

    private fun writeJsonString(text: String, out: StringBuilder) {
        out.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch.code < 0x20) {
                    out.append("\\u")
                    out.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }

    // --- Pure Kotlin RFC-8259 Recursive-Descent JSON Parser ---

    private class JsonParser(private val src: String) {
        private var pos: Int = 0

        fun parseRootObject(): Map<String, Any?> {
            skipWhitespace()
            val value = parseObject()
            skipWhitespace()
            if (pos < src.length) {
                throw GradleException("Unexpected trailing JSON content at index $pos.")
            }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) {
                throw GradleException("Unexpected end of Android manifest model JSON.")
            }
            return when (src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw GradleException("Unexpected JSON token '${src[pos]}' at index $pos.")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (peek('}')) {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                result[key] = value
                skipWhitespace()
                if (peek(',')) {
                    pos++
                } else if (peek('}')) {
                    pos++
                    return result
                } else {
                    throw GradleException("Expected ',' or '}' in JSON object at index $pos.")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (peek(']')) {
                pos++
                return result
            }
            while (true) {
                result.add(parseValue())
                skipWhitespace()
                if (peek(',')) {
                    pos++
                } else if (peek(']')) {
                    pos++
                    return result
                } else {
                    throw GradleException("Expected ',' or ']' in JSON array at index $pos.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw GradleException("Unterminated escape sequence in JSON string.")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw GradleException("Invalid \\u escape in JSON string.")
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw GradleException("Invalid JSON escape '\\$esc' at index ${pos - 1}.")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw GradleException("Unterminated JSON string.")
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw GradleException("Invalid JSON boolean at index $pos.")
            }
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw GradleException("Invalid JSON null literal at index $pos.")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val raw = src.substring(start, pos)
            return if (isFloat) raw.toDouble() else raw.toLong()
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(expected: Char): Boolean = pos < src.length && src[pos] == expected

        private fun expect(expected: Char) {
            if (pos >= src.length || src[pos] != expected) {
                throw GradleException("Expected '$expected' at index $pos in manifest model JSON.")
            }
            pos++
        }
    }
}
