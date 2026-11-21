package com.nexustools.genericui.plugin

import com.nexustools.genericui.plugin.spi.AndroidExtensionContribution
import com.nexustools.genericui.plugin.spi.ExtensionBuildContext
import com.nexustools.genericui.plugin.spi.GenericUiDiscoveredSymbol
import com.nexustools.genericui.plugin.spi.GenericUiExtensionContributorRegistry
import org.gradle.api.GradleException
import java.io.File

/**
 * Validates [GenericUiAndroidManifestModel] configurations, resolves placeholders and version codes,
 * generates `AndroidManifest.xml`, and synthesizes all companion Android `res/` XML files.
 */
object GenericUiAndroidManifestXmlGenerator {

    private val ALLOWED_LAUNCH_MODES = setOf(
        "standard",
        "singleTop",
        "singleTask",
        "singleInstance",
        "singleInstancePerTask"
    )

    private val SOFT_INPUT_STATE_TOKENS = setOf(
        "stateUnspecified",
        "stateUnchanged",
        "stateHidden",
        "stateAlwaysHidden",
        "stateVisible",
        "stateAlwaysVisible"
    )

    private val SOFT_INPUT_ADJUST_TOKENS = setOf(
        "adjustUnspecified",
        "adjustResize",
        "adjustPan",
        "adjustNothing"
    )

    private val PLACEHOLDER_REGEX = Regex("""\$\{([A-Za-z0-9_.-]+)\}""")

    /**
     * Deterministically derives a positive Android `versionCode` from a semantic [versionName]
     * (`"1.2.3"` -> `10203`, `"0.1.0-SNAPSHOT"` -> `100`, minimum `1`).
     */
    fun deriveVersionCode(versionName: String): Int {
        val cleaned = versionName.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        if (cleaned.isEmpty()) return 1
        val parts = cleaned.split('.')
        fun parseSegment(idx: Int): Int {
            val raw = parts.getOrNull(idx)?.trim().orEmpty()
            val digits = raw.takeWhile { it.isDigit() }
            return digits.toIntOrNull() ?: 0
        }
        val major = parseSegment(0).coerceAtLeast(0)
        val minor = parseSegment(1).coerceIn(0, 99)
        val patch = parseSegment(2).coerceIn(0, 99)
        return (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
    }

    /**
     * Validates [model] and [resolvedAppVersion], failing fast with a descriptive [GradleException]
     * on invalid `versionCode`, `launchMode`, `windowSoftInputMode` tokens, CIDR domains, or missing
     * provider authorities.
     */
    fun validate(model: GenericUiAndroidManifestModel, resolvedAppVersion: String) {
        if (model.versionCode != null && model.versionCode <= 0) {
            throw GradleException(
                "Invalid Android manifest versionCode '${model.versionCode}': versionCode must be > 0."
            )
        }
        if (model.minSdk <= 0 || model.compileSdk <= 0 || model.targetSdk <= 0) {
            throw GradleException(
                "Invalid Android SDK levels (minSdk=${model.minSdk}, compileSdk=${model.compileSdk}, targetSdk=${model.targetSdk}): " +
                    "all SDK versions must be > 0."
            )
        }
        validateActivitySpec("activityDefaults", model.activityDefaults)
        validateActivitySpec("mainActivity", model.mainActivity)
        for ((name, spec) in model.activityOverrides) {
            validateActivitySpec("activity(\"$name\")", spec)
        }
        model.networkSecurity.validate()

        for (provider in model.providers) {
            if (provider.authorities.isBlank()) {
                throw GradleException(
                    "Android <provider> '${provider.className}' must declare non-blank authorities."
                )
            }
        }
        if (model.fileProvider.enabled && model.fileProvider.authorities.isBlank()) {
            throw GradleException("Android FileProvider authorities must not be blank when fileProvider is enabled.")
        }
        for (widget in model.widgets) {
            if (widget.minWidthDp <= 0 || widget.minHeightDp <= 0) {
                throw GradleException(
                    "Android widget '${widget.providerClass}' must have positive minWidthDp and minHeightDp."
                )
            }
        }
    }

    private fun validateActivitySpec(label: String, spec: GenericUiAndroidActivityDefaultsSpec) {
        val rawLaunchMode = spec.launchMode?.trim()
        if (!rawLaunchMode.isNullOrEmpty() && rawLaunchMode !in ALLOWED_LAUNCH_MODES) {
            throw GradleException(
                "Invalid Android launchMode '$rawLaunchMode' in $label. Allowed values: ${ALLOWED_LAUNCH_MODES.joinToString(", ")}."
            )
        }

        val rawSoftInput = spec.windowSoftInputMode?.trim()
        if (!rawSoftInput.isNullOrEmpty()) {
            val tokens = rawSoftInput.split('|').map { it.trim() }
            if (tokens.any { it.isEmpty() }) {
                throw GradleException(
                    "Invalid Android windowSoftInputMode '$rawSoftInput' in $label: empty token around '|'."
                )
            }
            var stateCount = 0
            var adjustCount = 0
            for (token in tokens) {
                when (token) {
                    in SOFT_INPUT_STATE_TOKENS -> stateCount++
                    in SOFT_INPUT_ADJUST_TOKENS -> adjustCount++
                    else -> {
                        val allowed = (SOFT_INPUT_STATE_TOKENS + SOFT_INPUT_ADJUST_TOKENS).joinToString(", ")
                        throw GradleException(
                            "Invalid Android windowSoftInputMode token '$token' in $label. Allowed tokens: $allowed."
                        )
                    }
                }
            }
            if (stateCount > 1 || adjustCount > 1) {
                throw GradleException(
                    "Invalid Android windowSoftInputMode '$rawSoftInput' in $label: at most one state* token and one adjust* token may be combined."
                )
            }
        }

        val maxRecents = spec.maxRecents
        if (maxRecents != null && maxRecents <= 0) {
            throw GradleException("Invalid Android maxRecents '$maxRecents' in $label: must be > 0.")
        }
    }

    /**
     * Generates the complete `AndroidManifest.xml` and all companion `res/` XML files for [model].
     */
    fun generate(
        appId: String,
        appName: String,
        appVersion: String,
        model: GenericUiAndroidManifestModel,
        fragments: List<GenericUiDiscoveredFragment>,
        winningMainFragment: GenericUiDiscoveredFragment? = fragments.firstOrNull { it.isMainFragment },
        defaultPackageName: String = appId,
        enabledExtensionModulePaths: List<String> = emptyList(),
        projectRootDir: File? = null,
        discoveredSymbols: List<GenericUiDiscoveredSymbol> = emptyList(),
        extensionOptions: Map<String, String> = emptyMap()
    ): GenericUiAndroidManifestOutput {
        val resolvedAppId = appId.trim().ifEmpty { "com.nexustools.genericui.app" }
        val resolvedAppName = appName.trim().ifEmpty { "GenericUI" }
        val resolvedPkg = defaultPackageName.trim().ifEmpty { resolvedAppId }
        val rawVersionName = model.versionName?.trim()?.takeIf { it.isNotEmpty() }
            ?: appVersion.trim().ifEmpty { "1.0.0" }
        validate(model, rawVersionName)

        val basePlaceholders = linkedMapOf(
            "applicationId" to resolvedAppId,
            "versionName" to rawVersionName,
            "appName" to resolvedAppName,
            "packageName" to resolvedPkg
        )
        for ((k, v) in model.placeholders) {
            basePlaceholders[k] = expandPlaceholders(v, basePlaceholders)
        }

        val expandedVersionName = expandPlaceholders(rawVersionName, basePlaceholders)
        basePlaceholders["versionName"] = expandedVersionName
        val resolvedVersionCode = model.versionCode ?: deriveVersionCode(expandedVersionName)

        val generatedResFiles = linkedMapOf<String, String>()

        if (model.networkSecurity.isConfigured()) {
            generatedResFiles["xml/network_security_config.xml"] = renderNetworkSecurityConfigXml(model.networkSecurity)
        }

        if (model.androidAuto.enabled) {
            generatedResFiles["xml/automotive_app_desc.xml"] = renderAutomotiveAppDescXml(model.androidAuto)
        }

        val resolvedShortcuts = resolveEffectiveShortcuts(model)
        if (model.shortcuts.enabled && resolvedShortcuts.isNotEmpty()) {
            val (shortcutsXml, stringsXml) = renderShortcutsAndStringsXml(
                shortcuts = resolvedShortcuts,
                fragments = fragments,
                winningMainFragment = winningMainFragment,
                placeholders = basePlaceholders
            )
            generatedResFiles["xml/shortcuts.xml"] = shortcutsXml
            generatedResFiles["values/genericui_shortcut_strings.xml"] = stringsXml
        }

        if (model.locales.isNotEmpty()) {
            generatedResFiles["xml/locales_config.xml"] = renderLocalesConfigXml(model.locales)
        }

        if (model.backupRules.cloudRules.isNotEmpty()) {
            generatedResFiles["xml/backup_rules.xml"] = renderBackupRulesXml(model.backupRules.cloudRules)
        }
        if (model.backupRules.isNotEmpty()) {
            generatedResFiles["xml/data_extraction_rules.xml"] = renderDataExtractionRulesXml(model.backupRules)
        }

        if (model.fileProvider.enabled) {
            generatedResFiles["xml/file_paths.xml"] = renderFilePathsXml(model.fileProvider)
        }

        val widgetXmlNames = resolveWidgetXmlNames(model.widgets)
        for (widget in model.widgets) {
            val xmlName = widgetXmlNames.getValue(widget.providerClass)
            generatedResFiles["xml/$xmlName.xml"] = renderWidgetInfoXml(widget, basePlaceholders)
        }

        val normalizedExtPaths = enabledExtensionModulePaths
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val extContext = ExtensionBuildContext(
            projectRootDir = projectRootDir ?: File("."),
            projectName = resolvedAppName,
            appId = resolvedAppId,
            appName = resolvedAppName,
            appVersion = expandedVersionName,
            baseName = resolvedAppName,
            generatedPackageName = resolvedPkg,
            enabledExtensionModulePaths = normalizedExtPaths,
            discoveredFragments = fragments,
            discoveredSymbols = discoveredSymbols,
            extensionOptions = extensionOptions
        )
        GenericUiExtensionContributorRegistry.validateActiveContributors(extContext, discoveredSymbols)

        val manifestXml = renderManifestXml(
            resolvedAppId = resolvedAppId,
            resolvedAppName = resolvedAppName,
            resolvedPkg = resolvedPkg,
            resolvedVersionCode = resolvedVersionCode,
            resolvedVersionName = expandedVersionName,
            model = model,
            fragments = fragments,
            winningMainFragment = winningMainFragment,
            hasShortcuts = model.shortcuts.enabled && resolvedShortcuts.isNotEmpty(),
            widgetXmlNames = widgetXmlNames,
            placeholders = basePlaceholders,
            enabledExtensionPaths = normalizedExtPaths,
            extensionContext = extContext
        )

        return GenericUiAndroidManifestOutput(
            manifestXml = manifestXml,
            generatedResFiles = generatedResFiles,
            resolvedPlaceholders = basePlaceholders
        )
    }

    /** Expands `${key}` placeholders in [text] using [placeholders]. */
    fun expandPlaceholders(text: String, placeholders: Map<String, String>): String =
        PLACEHOLDER_REGEX.replace(text) { match ->
            val key = match.groupValues[1]
            placeholders[key] ?: match.value
        }

    /** Escapes placeholder values before substituting directly into raw XML attribute strings. */
    fun expandPlaceholdersInXml(xml: String, placeholders: Map<String, String>): String =
        PLACEHOLDER_REGEX.replace(xml) { match ->
            val key = match.groupValues[1]
            val value = placeholders[key]
            if (value != null) escapeXml(value) else match.value
        }

    private fun renderManifestXml(
        resolvedAppId: String,
        resolvedAppName: String,
        resolvedPkg: String,
        resolvedVersionCode: Int,
        resolvedVersionName: String,
        model: GenericUiAndroidManifestModel,
        fragments: List<GenericUiDiscoveredFragment>,
        winningMainFragment: GenericUiDiscoveredFragment?,
        hasShortcuts: Boolean,
        widgetXmlNames: Map<String, String>,
        placeholders: Map<String, String>,
        enabledExtensionPaths: Set<String>,
        extensionContext: ExtensionBuildContext
    ): String = buildString {
        val usesToolsNamespace = usesToolsPrefix(model)
        val appContributions = GenericUiExtensionContributorRegistry.collectAndroidContributions(extensionContext, null)
        val perFragContributions = fragments.associateWith { frag ->
            GenericUiExtensionContributorRegistry.collectAndroidContributions(extensionContext, frag)
        }
        val allManifestContributions = (appContributions + perFragContributions.values.flatten())
            .map { it.manifestContribution }

        val manifestAttrs = linkedMapOf<String, String>()
        manifestAttrs["xmlns:android"] = "http://schemas.android.com/apk/res/android"
        if (usesToolsNamespace) {
            manifestAttrs["xmlns:tools"] = "http://schemas.android.com/tools"
        }
        manifestAttrs["package"] = resolvedAppId
        manifestAttrs["android:versionCode"] = resolvedVersionCode.toString()
        manifestAttrs["android:versionName"] = resolvedVersionName
        for ((k, v) in model.manifestAttributes) {
            manifestAttrs[normalizeAttrName(k)] = expandPlaceholders(v, placeholders)
        }

        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendOpenTag(this, indent = "", tag = "manifest", attrs = manifestAttrs, selfClosing = false)

        val seenPermissions = linkedSetOf<String>()
        for (perm in model.permissions) {
            val trimmed = expandPlaceholders(perm.name.trim(), placeholders)
            if (trimmed.isEmpty() || !seenPermissions.add(trimmed)) continue
            val permAttrs = linkedMapOf<String, String>()
            permAttrs["android:name"] = trimmed
            perm.maxSdkVersion?.let { permAttrs["android:maxSdkVersion"] = it.toString() }
            perm.usesPermissionFlags?.trim()?.takeIf { it.isNotEmpty() }?.let {
                permAttrs["android:usesPermissionFlags"] = expandPlaceholders(it, placeholders)
            }
            appendInlineTag(this, indent = "    ", tag = "uses-permission", attrs = permAttrs)
        }
        for (contrib in allManifestContributions) {
            for (rawPerm in contrib.permissions) {
                val trimmed = expandPlaceholders(rawPerm.trim(), placeholders)
                if (trimmed.isEmpty() || !seenPermissions.add(trimmed)) continue
                val permAttrs = linkedMapOf("android:name" to trimmed)
                appendInlineTag(this, indent = "    ", tag = "uses-permission", attrs = permAttrs)
            }
        }

        val effectiveFeatures = model.usesFeatures.toMutableList()
        if (model.androidTv.enabled) {
            if (effectiveFeatures.none { it.name == "android.software.leanback" }) {
                effectiveFeatures.add(
                    GenericUiAndroidUsesFeatureSpec(
                        name = "android.software.leanback",
                        required = model.androidTv.required
                    )
                )
            }
            if (effectiveFeatures.none { it.name == "android.hardware.touchscreen" }) {
                effectiveFeatures.add(
                    GenericUiAndroidUsesFeatureSpec(
                        name = "android.hardware.touchscreen",
                        required = false
                    )
                )
            }
        }

        for (feat in effectiveFeatures) {
            val featAttrs = linkedMapOf<String, String>()
            if (feat.name.isNotBlank()) {
                featAttrs["android:name"] = expandPlaceholders(feat.name.trim(), placeholders)
            }
            if (feat.glEsVersion != null) {
                featAttrs["android:glEsVersion"] = "0x%08x".format(feat.glEsVersion)
            }
            featAttrs["android:required"] = feat.required.toString()
            appendInlineTag(this, indent = "    ", tag = "uses-feature", attrs = featAttrs)
        }

        if (model.queries.isNotEmpty()) {
            appendLine("""    <queries>""")
            for (pkg in model.queries.packageNames) {
                appendLine("""        <package android:name="${escapeXml(expandPlaceholders(pkg, placeholders))}" />""")
            }
            for (intentSpec in model.queries.intents) {
                renderIntentElement(this, indent = "        ", tag = "intent", filter = intentSpec, includeFilterAttrs = false, placeholders = placeholders)
            }
            for (auth in model.queries.providerAuthorities) {
                appendLine("""        <provider android:authorities="${escapeXml(expandPlaceholders(auth, placeholders))}" />""")
            }
            appendLine("""    </queries>""")
        }

        val resolvedAppClass = model.applicationClass.trim().ifEmpty {
            "$resolvedPkg.${GenericUiGenerateAndroidActivitiesTask.GENERATED_APPLICATION_SIMPLE_NAME}"
        }
        val resolvedTheme = model.theme.trim().ifEmpty { "@style/Theme.GenericUI.App" }

        val effectiveFullBackup = model.fullBackupContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (model.backupRules.cloudRules.isNotEmpty()) "@xml/backup_rules" else null
        val effectiveDataExtraction = model.dataExtractionRules?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (model.backupRules.isNotEmpty()) "@xml/data_extraction_rules" else null
        val effectiveLocaleConfig = model.localeConfig?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (model.locales.isNotEmpty()) "@xml/locales_config" else null
        val effectiveNetSec = model.networkSecurityConfig?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (model.networkSecurity.isConfigured()) "@xml/network_security_config" else null
        val effectiveBanner = model.banner?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (model.androidTv.enabled) model.androidTv.banner?.trim()?.takeIf { it.isNotEmpty() } else null

        val appAttrs = linkedMapOf<String, String>()
        appAttrs["android:name"] = expandPlaceholders(resolvedAppClass, placeholders)
        appAttrs["android:label"] = resolvedAppName
        appAttrs["android:theme"] = expandPlaceholders(resolvedTheme, placeholders)
        model.icon?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appAttrs["android:icon"] = expandPlaceholders(it, placeholders)
        }
        model.roundIcon?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appAttrs["android:roundIcon"] = expandPlaceholders(it, placeholders)
        }
        effectiveBanner?.let { appAttrs["android:banner"] = expandPlaceholders(it, placeholders) }
        model.logo?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appAttrs["android:logo"] = expandPlaceholders(it, placeholders)
        }
        appAttrs["android:supportsRtl"] = model.supportsRtl.toString()
        model.usesCleartextTraffic?.let { appAttrs["android:usesCleartextTraffic"] = it.toString() }
        model.allowBackup?.let { appAttrs["android:allowBackup"] = it.toString() }
        effectiveFullBackup?.let { appAttrs["android:fullBackupContent"] = expandPlaceholders(it, placeholders) }
        effectiveDataExtraction?.let { appAttrs["android:dataExtractionRules"] = expandPlaceholders(it, placeholders) }
        model.largeHeap?.let { appAttrs["android:largeHeap"] = it.toString() }
        model.hardwareAccelerated?.let { appAttrs["android:hardwareAccelerated"] = it.toString() }
        model.appCategory?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appAttrs["android:appCategory"] = expandPlaceholders(it, placeholders)
        }
        effectiveLocaleConfig?.let { appAttrs["android:localeConfig"] = expandPlaceholders(it, placeholders) }
        model.enableOnBackInvokedCallback?.let { appAttrs["android:enableOnBackInvokedCallback"] = it.toString() }
        model.resizeableActivity?.let { appAttrs["android:resizeableActivity"] = it.toString() }
        model.extractNativeLibs?.let { appAttrs["android:extractNativeLibs"] = it.toString() }
        model.requestLegacyExternalStorage?.let { appAttrs["android:requestLegacyExternalStorage"] = it.toString() }
        model.allowNativeHeapPointerTagging?.let { appAttrs["android:allowNativeHeapPointerTagging"] = it.toString() }
        model.isGame?.let { appAttrs["android:isGame"] = it.toString() }
        model.debuggable?.let { appAttrs["android:debuggable"] = it.toString() }
        effectiveNetSec?.let { appAttrs["android:networkSecurityConfig"] = expandPlaceholders(it, placeholders) }
        for ((k, v) in model.applicationAttributes) {
            appAttrs[normalizeAttrName(k)] = expandPlaceholders(v, placeholders)
        }

        appendLine()
        appendOpenTag(this, indent = "    ", tag = "application", attrs = appAttrs, selfClosing = false)

        val effectiveAppMeta = model.applicationMetaData.toMutableList()
        if (model.androidAuto.enabled && effectiveAppMeta.none { it.name == "com.google.android.gms.car.application" }) {
            effectiveAppMeta.add(
                GenericUiAndroidMetaDataSpec(
                    name = "com.google.android.gms.car.application",
                    resource = "@xml/automotive_app_desc"
                )
            )
        }
        for (contrib in allManifestContributions) {
            for (meta in contrib.applicationMetaData) {
                if (effectiveAppMeta.none { it.name == meta.name }) {
                    effectiveAppMeta.add(meta)
                }
            }
        }
        for (meta in effectiveAppMeta) {
            renderMetaData(this, indent = "        ", spec = meta, placeholders = placeholders)
        }

        for (frag in fragments) {
            renderActivityElement(
                sb = this,
                frag = frag,
                isMain = winningMainFragment != null && frag.qualifiedName == winningMainFragment.qualifiedName,
                resolvedAppName = resolvedAppName,
                model = model,
                hasShortcuts = hasShortcuts,
                placeholders = placeholders,
                enabledExtensionPaths = enabledExtensionPaths,
                spiContributions = perFragContributions[frag].orEmpty()
            )
        }

        val effectiveServices = resolveEffectiveServices(
            model = model,
            contributedServices = allManifestContributions.flatMap { it.services }
        )
        for (svc in effectiveServices) {
            renderServiceElement(this, svc, placeholders)
        }

        val effectiveReceivers = mergeContributedReceivers(
            configuredReceivers = model.receivers,
            contributedReceivers = allManifestContributions.flatMap { it.receivers }
        )
        for (receiver in effectiveReceivers) {
            renderReceiverElement(this, receiver, placeholders)
        }

        for (widget in model.widgets) {
            val xmlName = widgetXmlNames.getValue(widget.providerClass)
            val widgetReceiver = GenericUiAndroidReceiverSpec(widget.providerClass).apply {
                exported = true
                intentAction("android.appwidget.action.APPWIDGET_UPDATE")
                metaData("android.appwidget.provider", resource = "@xml/$xmlName")
            }
            renderReceiverElement(this, widgetReceiver, placeholders)
        }

        val effectiveProviders = mergeContributedProviders(
            configuredProviders = model.providers,
            contributedProviders = allManifestContributions.flatMap { it.providers }
        )
        for (provider in effectiveProviders) {
            renderProviderElement(this, provider, placeholders)
        }

        if (model.fileProvider.enabled) {
            val fp = GenericUiAndroidProviderSpec(model.fileProvider.providerClass.trim().ifEmpty {
                "androidx.core.content.FileProvider"
            }).apply {
                authorities = model.fileProvider.authorities
                exported = false
                grantUriPermissions = true
                metaData("android.support.FILE_PROVIDER_PATHS", resource = "@xml/file_paths")
            }
            renderProviderElement(this, fp, placeholders)
        }

        appendLine("""    </application>""")
        appendLine("""</manifest>""")
    }

    private fun renderActivityElement(
        sb: StringBuilder,
        frag: GenericUiDiscoveredFragment,
        isMain: Boolean,
        resolvedAppName: String,
        model: GenericUiAndroidManifestModel,
        hasShortcuts: Boolean,
        placeholders: Map<String, String>,
        enabledExtensionPaths: Set<String>,
        spiContributions: List<AndroidExtensionContribution> = emptyList()
    ) {
        val merged = model.activityDefaults.copyDefaults()
        if (isMain) {
            merged.overlayWith(model.mainActivity)
        }
        // Match overrides in increasing specificity: generated names < simpleName < qualifiedName
        val candidateKeys = listOf(
            frag.generatedActivitySimpleName,
            frag.generatedActivityQualifiedName,
            frag.simpleName,
            frag.qualifiedName
        ).distinct()
        for (key in candidateKeys) {
            model.activityOverrides[key]?.let { merged.overlayWith(it) }
        }

        val activityContributions = AndroidActivityExtensionRegistry.resolveContributions(frag, enabledExtensionPaths)
        val contributionEnablesPip = activityContributions.any { it.supportsPictureInPictureManifest } ||
            spiContributions.any { it.manifestContribution.supportsPictureInPicture }
        val contributionConfigChanges = (
            activityContributions.flatMap { it.requiredConfigChanges } +
                spiContributions.flatMap { it.manifestContribution.activityConfigChanges }
            ).distinct()

        val customActivityClass = merged.activityClass?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { expandPlaceholders(it, placeholders) }
        val resolvedActivityName = customActivityClass ?: frag.generatedActivityQualifiedName
        val emitLauncherFilter = merged.launcher ?: isMain

        // Strict precedence: activityDefaults < activityOverride/mainActivity < @AppFragment annotation fields
        val activityLabel = frag.label.trim().ifEmpty {
            if (isMain) resolvedAppName else frag.simpleName
        }
        val exported = if (emitLauncherFilter || isMain || frag.exported) {
            merged.exported ?: true
        } else {
            merged.exported ?: false
        }
        val baseConfigChanges = merged.configChanges?.trim()?.takeIf { it.isNotEmpty() }
            ?: model.configChanges.trim().ifEmpty { "orientation|screenSize|screenLayout|keyboardHidden|uiMode|density" }
        val configChanges = mergePipeSeparatedTokens(baseConfigChanges, contributionConfigChanges)
        val orientation = frag.screenOrientation.trim()
            .ifEmpty { merged.screenOrientation?.trim().orEmpty() }
            .ifEmpty { model.screenOrientation.trim() }
            .ifEmpty { "unspecified" }
        val pip = when {
            merged.supportsPictureInPicture == false -> false
            merged.supportsPictureInPicture == true -> true
            contributionEnablesPip -> true
            else -> model.supportsPictureInPicture
        }

        val attrs = linkedMapOf<String, String>()
        attrs["android:name"] = resolvedActivityName
        attrs["android:label"] = activityLabel
        attrs["android:exported"] = exported.toString()
        merged.launchMode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:launchMode"] = expandPlaceholders(it, placeholders)
        }
        merged.windowSoftInputMode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:windowSoftInputMode"] = expandPlaceholders(it, placeholders)
        }
        attrs["android:configChanges"] = expandPlaceholders(configChanges, placeholders)
        attrs["android:supportsPictureInPicture"] = pip.toString()
        attrs["android:screenOrientation"] = expandPlaceholders(orientation, placeholders)
        merged.resizeableActivity?.let { attrs["android:resizeableActivity"] = it.toString() }
        merged.showWhenLocked?.let { attrs["android:showWhenLocked"] = it.toString() }
        merged.turnScreenOn?.let { attrs["android:turnScreenOn"] = it.toString() }
        merged.excludeFromRecents?.let { attrs["android:excludeFromRecents"] = it.toString() }
        merged.taskAffinity?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:taskAffinity"] = expandPlaceholders(it, placeholders)
        }
        merged.theme?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:theme"] = expandPlaceholders(it, placeholders)
        }
        merged.noHistory?.let { attrs["android:noHistory"] = it.toString() }
        merged.documentLaunchMode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:documentLaunchMode"] = expandPlaceholders(it, placeholders)
        }
        merged.maxRecents?.let { attrs["android:maxRecents"] = it.toString() }
        merged.hardwareAccelerated?.let { attrs["android:hardwareAccelerated"] = it.toString() }
        merged.lockTaskMode?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:lockTaskMode"] = expandPlaceholders(it, placeholders)
        }
        for ((k, v) in merged.rawAttributes) {
            attrs[normalizeAttrName(k)] = expandPlaceholders(v, placeholders)
        }

        val effectiveMeta = merged.metaDataList.toMutableList()
        if (emitLauncherFilter && hasShortcuts && effectiveMeta.none { it.name == "android.app.shortcuts" }) {
            effectiveMeta.add(
                GenericUiAndroidMetaDataSpec(name = "android.app.shortcuts", resource = "@xml/shortcuts")
            )
        }

        val hasChildren = emitLauncherFilter ||
            merged.intentFilters.isNotEmpty() ||
            effectiveMeta.isNotEmpty() ||
            merged.properties.isNotEmpty()

        if (!hasChildren) {
            appendOpenTag(sb, indent = "        ", tag = "activity", attrs = attrs, selfClosing = true)
        } else {
            appendOpenTag(sb, indent = "        ", tag = "activity", attrs = attrs, selfClosing = false)

            if (emitLauncherFilter) {
                val mainFilter = GenericUiAndroidIntentFilterSpec().apply {
                    action("android.intent.action.MAIN")
                    category("android.intent.category.LAUNCHER")
                    if (model.androidTv.enabled) {
                        category("android.intent.category.LEANBACK_LAUNCHER")
                    }
                }
                renderIntentElement(sb, indent = "            ", tag = "intent-filter", filter = mainFilter, includeFilterAttrs = true, placeholders = placeholders)
            }

            for (filter in merged.intentFilters) {
                renderIntentElement(sb, indent = "            ", tag = "intent-filter", filter = filter, includeFilterAttrs = true, placeholders = placeholders)
            }

            for (meta in effectiveMeta) {
                renderMetaData(sb, indent = "            ", spec = meta, placeholders = placeholders)
            }

            for ((propName, propVal) in merged.properties.entries.sortedBy { it.key }) {
                val pAttrs = linkedMapOf(
                    "android:name" to expandPlaceholders(propName, placeholders),
                    "android:value" to expandPlaceholders(propVal, placeholders)
                )
                appendInlineTag(sb, indent = "            ", tag = "property", attrs = pAttrs)
            }

            sb.appendLine("""        </activity>""")
        }

        if (customActivityClass != null && customActivityClass != frag.generatedActivityQualifiedName) {
            val fallbackAttrs = linkedMapOf(
                "android:name" to frag.generatedActivityQualifiedName,
                "android:label" to frag.simpleName,
                "android:exported" to "false",
                "android:configChanges" to expandPlaceholders(configChanges, placeholders),
                "android:supportsPictureInPicture" to pip.toString(),
                "android:screenOrientation" to expandPlaceholders(orientation, placeholders)
            )
            appendOpenTag(sb, indent = "        ", tag = "activity", attrs = fallbackAttrs, selfClosing = true)
        }
    }

    private fun resolveEffectiveServices(
        model: GenericUiAndroidManifestModel,
        contributedServices: List<com.nexustools.genericui.plugin.spi.AndroidServiceContribution> = emptyList()
    ): List<GenericUiAndroidServiceSpec> {
        val mergedByName = LinkedHashMap<String, GenericUiAndroidServiceSpec>()
        for (svc in model.services) {
            mergedByName[svc.className] = svc.copySpec()
        }
        for (contrib in contributedServices) {
            val className = contrib.className.trim()
            if (className.isEmpty()) continue
            val existing = mergedByName[className]
            if (existing == null) {
                mergedByName[className] = GenericUiAndroidServiceSpec(className).apply {
                    exported = contrib.exported
                    enabled = contrib.enabled
                    foregroundServiceType = contrib.foregroundServiceType?.trim().orEmpty()
                    permission = contrib.permission?.trim().orEmpty()
                    process = contrib.process?.trim().orEmpty()
                    stopWithTask = contrib.stopWithTask
                    isolatedProcess = contrib.isolatedProcess
                    contrib.intentFilters.mapTo(intentFilters) { it.copySpec() }
                    contrib.metaData.forEach { m -> metaData(m.name, m.value, m.resource) }
                    contrib.properties.forEach { (k, v) -> property(k, v) }
                }
            } else {
                if (contrib.exported) existing.exported = true
                if (!existing.enabled && contrib.enabled) existing.enabled = true
                if (existing.foregroundServiceType.isBlank() && !contrib.foregroundServiceType.isNullOrBlank()) {
                    existing.foregroundServiceType = contrib.foregroundServiceType.trim()
                }
                if (existing.permission.isBlank() && !contrib.permission.isNullOrBlank()) {
                    existing.permission = contrib.permission.trim()
                }
                if (existing.process.isBlank() && !contrib.process.isNullOrBlank()) {
                    existing.process = contrib.process.trim()
                }
                if (existing.stopWithTask == null && contrib.stopWithTask != null) {
                    existing.stopWithTask = contrib.stopWithTask
                }
                if (existing.isolatedProcess == null && contrib.isolatedProcess != null) {
                    existing.isolatedProcess = contrib.isolatedProcess
                }
                contrib.intentFilters.mapTo(existing.intentFilters) { it.copySpec() }
                contrib.metaData.forEach { m ->
                    if (existing.metaDataList.none { it.name == m.name }) {
                        existing.metaData(m.name, m.value, m.resource)
                    }
                }
                contrib.properties.forEach { (k, v) ->
                    if (k !in existing.properties) {
                        existing.property(k, v)
                    }
                }
            }
        }
        val list = mergedByName.values.toMutableList()
        val mediaServiceName = model.androidAuto.mediaBrowserService?.trim()?.takeIf { it.isNotEmpty() }
        if (model.androidAuto.enabled && mediaServiceName != null) {
            val existing = list.firstOrNull { it.className == mediaServiceName }
            val browserAction = "android.media.browse.MediaBrowserService"
            if (existing != null) {
                val hasAction = browserAction in existing.intentActions ||
                    existing.intentFilters.any { browserAction in it.actions }
                if (!hasAction) {
                    existing.intentAction(browserAction)
                }
            } else {
                list.add(
                    GenericUiAndroidServiceSpec(mediaServiceName).apply {
                        exported = true
                        intentAction(browserAction)
                    }
                )
            }
        }
        return list
    }

    private fun mergeContributedReceivers(
        configuredReceivers: List<GenericUiAndroidReceiverSpec>,
        contributedReceivers: List<com.nexustools.genericui.plugin.spi.AndroidReceiverContribution>
    ): List<GenericUiAndroidReceiverSpec> {
        val merged = LinkedHashMap<String, GenericUiAndroidReceiverSpec>()
        for (r in configuredReceivers) {
            merged[r.className] = r.copySpec()
        }
        for (contrib in contributedReceivers) {
            val className = contrib.className.trim()
            if (className.isEmpty() || className in merged) continue
            merged[className] = GenericUiAndroidReceiverSpec(className).apply {
                exported = contrib.exported
                enabled = contrib.enabled
                permission = contrib.permission?.trim()
                process = contrib.process?.trim()
                contrib.intentFilters.mapTo(intentFilters) { it.copySpec() }
                contrib.metaData.forEach { m -> metaData(m.name, m.value, m.resource) }
            }
        }
        return merged.values.toList()
    }

    private fun mergeContributedProviders(
        configuredProviders: List<GenericUiAndroidProviderSpec>,
        contributedProviders: List<com.nexustools.genericui.plugin.spi.AndroidProviderContribution>
    ): List<GenericUiAndroidProviderSpec> {
        val merged = LinkedHashMap<String, GenericUiAndroidProviderSpec>()
        for (p in configuredProviders) {
            merged[p.className] = p.copySpec()
        }
        for (contrib in contributedProviders) {
            val className = contrib.className.trim()
            if (className.isEmpty() || className in merged) continue
            merged[className] = GenericUiAndroidProviderSpec(className).apply {
                authorities = contrib.authorities.trim()
                exported = contrib.exported
                enabled = contrib.enabled
                grantUriPermissions = contrib.grantUriPermissions
                initOrder = contrib.initOrder
                contrib.metaData.forEach { m -> metaData(m.name, m.value, m.resource) }
            }
        }
        return merged.values.toList()
    }

    private fun renderServiceElement(
        sb: StringBuilder,
        service: GenericUiAndroidServiceSpec,
        placeholders: Map<String, String>
    ) {
        val attrs = linkedMapOf<String, String>()
        attrs["android:name"] = expandPlaceholders(service.className, placeholders)
        attrs["android:exported"] = service.exported.toString()
        attrs["android:enabled"] = service.enabled.toString()
        if (service.foregroundServiceType.isNotBlank()) {
            attrs["android:foregroundServiceType"] = expandPlaceholders(service.foregroundServiceType.trim(), placeholders)
        }
        if (service.permission.isNotBlank()) {
            attrs["android:permission"] = expandPlaceholders(service.permission.trim(), placeholders)
        }
        if (service.process.isNotBlank()) {
            attrs["android:process"] = expandPlaceholders(service.process.trim(), placeholders)
        }
        service.stopWithTask?.let { attrs["android:stopWithTask"] = it.toString() }
        service.isolatedProcess?.let { attrs["android:isolatedProcess"] = it.toString() }

        val filters = mutableListOf<GenericUiAndroidIntentFilterSpec>()
        if (service.intentActions.isNotEmpty()) {
            filters.add(
                GenericUiAndroidIntentFilterSpec().apply {
                    service.intentActions.forEach { action(it) }
                }
            )
        }
        filters.addAll(service.intentFilters)

        val hasChildren = filters.isNotEmpty() || service.metaDataList.isNotEmpty() || service.properties.isNotEmpty()
        appendOpenTag(sb, indent = "        ", tag = "service", attrs = attrs, selfClosing = !hasChildren)
        if (!hasChildren) return

        for (filter in filters) {
            renderIntentElement(sb, indent = "            ", tag = "intent-filter", filter = filter, includeFilterAttrs = true, placeholders = placeholders)
        }
        for (meta in service.metaDataList) {
            renderMetaData(sb, indent = "            ", spec = meta, placeholders = placeholders)
        }
        for ((propName, propVal) in service.properties.entries.sortedBy { it.key }) {
            val pAttrs = linkedMapOf(
                "android:name" to expandPlaceholders(propName, placeholders),
                "android:value" to expandPlaceholders(propVal, placeholders)
            )
            appendInlineTag(sb, indent = "            ", tag = "property", attrs = pAttrs)
        }
        sb.appendLine("""        </service>""")
    }

    private fun renderReceiverElement(
        sb: StringBuilder,
        receiver: GenericUiAndroidReceiverSpec,
        placeholders: Map<String, String>
    ) {
        val attrs = linkedMapOf<String, String>()
        attrs["android:name"] = expandPlaceholders(receiver.className, placeholders)
        val effectiveExported = receiver.exported ?: if (receiver.intentFilters.isNotEmpty()) false else null
        effectiveExported?.let { attrs["android:exported"] = it.toString() }
        receiver.enabled?.let { attrs["android:enabled"] = it.toString() }
        receiver.permission?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:permission"] = expandPlaceholders(it, placeholders)
        }
        receiver.process?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:process"] = expandPlaceholders(it, placeholders)
        }

        val hasChildren = receiver.intentFilters.isNotEmpty() || receiver.metaDataList.isNotEmpty()
        appendOpenTag(sb, indent = "        ", tag = "receiver", attrs = attrs, selfClosing = !hasChildren)
        if (!hasChildren) return

        for (filter in receiver.intentFilters) {
            renderIntentElement(sb, indent = "            ", tag = "intent-filter", filter = filter, includeFilterAttrs = true, placeholders = placeholders)
        }
        for (meta in receiver.metaDataList) {
            renderMetaData(sb, indent = "            ", spec = meta, placeholders = placeholders)
        }
        sb.appendLine("""        </receiver>""")
    }

    private fun renderProviderElement(
        sb: StringBuilder,
        provider: GenericUiAndroidProviderSpec,
        placeholders: Map<String, String>
    ) {
        val attrs = linkedMapOf<String, String>()
        attrs["android:name"] = expandPlaceholders(provider.className, placeholders)
        attrs["android:authorities"] = expandPlaceholders(provider.authorities.trim(), placeholders)
        attrs["android:exported"] = provider.exported.toString()
        provider.enabled?.let { attrs["android:enabled"] = it.toString() }
        if (provider.grantUriPermissions) {
            attrs["android:grantUriPermissions"] = "true"
        }
        provider.initOrder?.let { attrs["android:initOrder"] = it.toString() }

        val hasChildren = provider.metaDataList.isNotEmpty()
        appendOpenTag(sb, indent = "        ", tag = "provider", attrs = attrs, selfClosing = !hasChildren)
        if (!hasChildren) return

        for (meta in provider.metaDataList) {
            renderMetaData(sb, indent = "            ", spec = meta, placeholders = placeholders)
        }
        sb.appendLine("""        </provider>""")
    }

    private fun renderIntentElement(
        sb: StringBuilder,
        indent: String,
        tag: String,
        filter: GenericUiAndroidIntentFilterSpec,
        includeFilterAttrs: Boolean,
        placeholders: Map<String, String>
    ) {
        val attrs = linkedMapOf<String, String>()
        if (includeFilterAttrs) {
            if (filter.autoVerify) {
                attrs["android:autoVerify"] = "true"
            }
            filter.priority?.let { attrs["android:priority"] = it.toString() }
            filter.label?.trim()?.takeIf { it.isNotEmpty() }?.let {
                attrs["android:label"] = expandPlaceholders(it, placeholders)
            }
        }
        if (attrs.isEmpty()) {
            sb.appendLine("$indent<$tag>")
        } else {
            val renderedAttrs = attrs.entries.joinToString(" ") { (k, v) -> """$k="${escapeXml(v)}"""" }
            sb.appendLine("$indent<$tag $renderedAttrs>")
        }

        val childIndent = "$indent    "
        for (actionName in filter.actions) {
            val expanded = expandPlaceholders(actionName, placeholders)
            sb.appendLine("""$childIndent<action android:name="${escapeXml(expanded)}" />""")
        }
        for (categoryName in filter.categories) {
            val expanded = expandPlaceholders(categoryName, placeholders)
            sb.appendLine("""$childIndent<category android:name="${escapeXml(expanded)}" />""")
        }
        for (data in filter.dataSpecs) {
            if (!data.hasAnyAttribute()) continue
            val dataAttrs = linkedMapOf<String, String>()
            data.scheme?.trim()?.takeIf { it.isNotEmpty() }?.let { dataAttrs["android:scheme"] = expandPlaceholders(it, placeholders) }
            data.host?.trim()?.takeIf { it.isNotEmpty() }?.let { dataAttrs["android:host"] = expandPlaceholders(it, placeholders) }
            data.port?.trim()?.takeIf { it.isNotEmpty() }?.let { dataAttrs["android:port"] = expandPlaceholders(it, placeholders) }
            data.path?.trim()?.takeIf { it.isNotEmpty() }?.let { dataAttrs["android:path"] = expandPlaceholders(it, placeholders) }
            data.pathPrefix?.trim()?.takeIf { it.isNotEmpty() }?.let { dataAttrs["android:pathPrefix"] = expandPlaceholders(it, placeholders) }
            data.pathPattern?.trim()?.takeIf { it.isNotEmpty() }?.let { dataAttrs["android:pathPattern"] = expandPlaceholders(it, placeholders) }
            data.mimeType?.trim()?.takeIf { it.isNotEmpty() }?.let { dataAttrs["android:mimeType"] = expandPlaceholders(it, placeholders) }
            appendInlineTag(sb, indent = childIndent, tag = "data", attrs = dataAttrs)
        }
        sb.appendLine("$indent</$tag>")
    }

    private fun renderMetaData(
        sb: StringBuilder,
        indent: String,
        spec: GenericUiAndroidMetaDataSpec,
        placeholders: Map<String, String>
    ) {
        val attrs = linkedMapOf<String, String>()
        attrs["android:name"] = expandPlaceholders(spec.name, placeholders)
        spec.value?.let { attrs["android:value"] = expandPlaceholders(it, placeholders) }
        spec.resource?.let { attrs["android:resource"] = expandPlaceholders(it, placeholders) }
        appendInlineTag(sb, indent = indent, tag = "meta-data", attrs = attrs)
    }

    // --- Companion Resource XML Generators ---

    private fun renderNetworkSecurityConfigXml(config: GenericUiAndroidNetworkSecurityConfig): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<network-security-config>""")

        val effectiveBaseCleartext = config.baseConfig?.cleartextPermitted ?: config.cleartextPermittedByDefault
        val explicitBaseTrust = config.baseConfig?.trustAnchors
        val effectiveBaseTrust = when {
            explicitBaseTrust != null && config.trustUserCertificates -> explicitBaseTrust.copySpec().apply {
                trustUser = true
            }
            explicitBaseTrust != null -> explicitBaseTrust
            config.trustUserCertificates -> GenericUiAndroidTrustAnchorsSpec().apply {
                trustSystem = true
                trustUser = true
            }
            else -> null
        }

        if (effectiveBaseCleartext != null || effectiveBaseTrust != null) {
            val baseAttrs = if (effectiveBaseCleartext != null) {
                """ cleartextTrafficPermitted="$effectiveBaseCleartext""""
            } else {
                ""
            }
            if (effectiveBaseTrust == null) {
                appendLine("""    <base-config$baseAttrs />""")
            } else {
                appendLine("""    <base-config$baseAttrs>""")
                renderTrustAnchorsXml(this, "        ", effectiveBaseTrust)
                appendLine("""    </base-config>""")
            }
        }

        for (dc in config.domainConfigs) {
            val dcAttrs = if (dc.cleartextPermitted != null) {
                """ cleartextTrafficPermitted="${dc.cleartextPermitted}""""
            } else {
                ""
            }
            appendLine("""    <domain-config$dcAttrs>""")
            for (domain in dc.domains) {
                val subAttr = if (dc.includeSubdomains) """ includeSubdomains="true"""" else ""
                appendLine("""        <domain$subAttr>${escapeXml(domain)}</domain>""")
            }
            dc.trustAnchors?.let { renderTrustAnchorsXml(this, "        ", it) }
            dc.pinSet?.let { ps ->
                val expAttr = ps.expiration?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    """ expiration="${escapeXml(it)}""""
                }.orEmpty()
                appendLine("""        <pin-set$expAttr>""")
                for (pin in ps.pins) {
                    appendLine("""            <pin digest="${escapeXml(pin.digest)}">${escapeXml(pin.value)}</pin>""")
                }
                appendLine("""        </pin-set>""")
            }
            appendLine("""    </domain-config>""")
        }

        config.debugOverridesTrustAnchors?.let { debugAnchors ->
            appendLine("""    <debug-overrides>""")
            renderTrustAnchorsXml(this, "        ", debugAnchors)
            appendLine("""    </debug-overrides>""")
        }

        appendLine("""</network-security-config>""")
    }

    private fun renderTrustAnchorsXml(
        sb: StringBuilder,
        indent: String,
        anchors: GenericUiAndroidTrustAnchorsSpec
    ) {
        sb.appendLine("""$indent<trust-anchors>""")
        if (anchors.trustSystem) {
            sb.appendLine("""$indent    <certificates src="system" />""")
        }
        if (anchors.trustUser) {
            sb.appendLine("""$indent    <certificates src="user" />""")
        }
        for (resRef in anchors.certificateResources) {
            sb.appendLine("""$indent    <certificates src="${escapeXml(resRef)}" />""")
        }
        sb.appendLine("""$indent</trust-anchors>""")
    }

    private fun renderAutomotiveAppDescXml(auto: GenericUiAndroidAutoConfig): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<automotiveApp>""")
        if (auto.usesMedia) {
            appendLine("""    <uses name="media" />""")
        }
        if (auto.usesNotification) {
            appendLine("""    <uses name="notification" />""")
        }
        appendLine("""</automotiveApp>""")
    }

    private fun resolveEffectiveShortcuts(model: GenericUiAndroidManifestModel): List<GenericUiAndroidShortcutSpec> {
        val result = LinkedHashMap<String, GenericUiAndroidShortcutSpec>()
        for (s in model.shortcuts.shortcuts) {
            result[s.shortcutId] = s
        }
        if (model.shortcuts.inheritFromWebManifest) {
            for (ws in model.inheritedWebShortcuts) {
                val baseId = sanitizeResIdentifier(ws.shortName.ifBlank { ws.name }, fallback = "shortcut")
                if (baseId !in result) {
                    val shortLabel = ws.shortName.ifBlank { ws.name }
                    val longLabel = ws.name.takeIf { it.isNotBlank() && it != shortLabel }
                    result[baseId] = GenericUiAndroidShortcutSpec(
                        shortcutId = baseId,
                        shortLabel = shortLabel,
                        longLabel = longLabel,
                        urlOrAction = ws.url
                    )
                }
            }
        }
        return result.values.toList()
    }

    private fun renderShortcutsAndStringsXml(
        shortcuts: List<GenericUiAndroidShortcutSpec>,
        fragments: List<GenericUiDiscoveredFragment>,
        winningMainFragment: GenericUiDiscoveredFragment?,
        placeholders: Map<String, String>
    ): Pair<String, String> {
        val stringsSb = StringBuilder()
        stringsSb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        stringsSb.appendLine("""<resources>""")

        val shortcutsSb = StringBuilder()
        shortcutsSb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        shortcutsSb.appendLine("""<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">""")

        val targetPackage = placeholders["applicationId"] ?: "com.nexustools.genericui.app"
        val usedKeyNames = linkedSetOf<String>()

        shortcuts.forEachIndexed { index, shortcut ->
            val rawSafe = sanitizeResIdentifier(shortcut.shortcutId, fallback = "shortcut_$index")
            var uniqueKey = rawSafe
            var suffix = 2
            while (!usedKeyNames.add(uniqueKey)) {
                uniqueKey = "${rawSafe}_$suffix"
                suffix++
            }

            val expandedShort = expandPlaceholders(shortcut.shortLabel, placeholders)
            val shortRef = if (expandedShort.startsWith("@string/")) {
                expandedShort
            } else {
                val key = "genericui_shortcut_${uniqueKey}_short"
                stringsSb.appendLine("""    <string name="$key">${escapeXml(expandedShort)}</string>""")
                "@string/$key"
            }

            val expandedLong = shortcut.longLabel?.let { expandPlaceholders(it, placeholders) }
            val longRef = when {
                expandedLong.isNullOrBlank() -> null
                expandedLong.startsWith("@string/") -> expandedLong
                else -> {
                    val key = "genericui_shortcut_${uniqueKey}_long"
                    stringsSb.appendLine("""    <string name="$key">${escapeXml(expandedLong)}</string>""")
                    "@string/$key"
                }
            }

            val sAttrs = linkedMapOf<String, String>()
            sAttrs["android:shortcutId"] = shortcut.shortcutId
            sAttrs["android:enabled"] = "true"
            shortcut.icon?.trim()?.takeIf { it.isNotEmpty() }?.let {
                sAttrs["android:icon"] = expandPlaceholders(it, placeholders)
            }
            sAttrs["android:shortcutShortLabel"] = shortRef
            if (longRef != null) {
                sAttrs["android:shortcutLongLabel"] = longRef
            }

            appendOpenTag(shortcutsSb, indent = "    ", tag = "shortcut", attrs = sAttrs, selfClosing = false)

            val targetClass = resolveShortcutTargetClass(
                targetFragment = shortcut.targetFragment,
                fragments = fragments,
                winningMainFragment = winningMainFragment
            )
            val urlOrAction = expandPlaceholders(shortcut.urlOrAction.trim(), placeholders)
            val intentAttrs = linkedMapOf<String, String>()
            var extraUrlValue: String? = null
            when {
                urlOrAction.isEmpty() -> {
                    intentAttrs["android:action"] = "android.intent.action.VIEW"
                }
                urlOrAction.contains("://") -> {
                    intentAttrs["android:action"] = "android.intent.action.VIEW"
                    intentAttrs["android:data"] = urlOrAction
                }
                urlOrAction.contains('.') && !urlOrAction.startsWith("./") && !urlOrAction.startsWith("/") -> {
                    intentAttrs["android:action"] = urlOrAction
                }
                else -> {
                    intentAttrs["android:action"] = "android.intent.action.VIEW"
                    extraUrlValue = urlOrAction
                }
            }
            intentAttrs["android:targetPackage"] = targetPackage
            if (targetClass != null) {
                intentAttrs["android:targetClass"] = targetClass
            }

            if (extraUrlValue == null) {
                appendInlineTag(shortcutsSb, indent = "        ", tag = "intent", attrs = intentAttrs)
            } else {
                appendOpenTag(shortcutsSb, indent = "        ", tag = "intent", attrs = intentAttrs, selfClosing = false)
                shortcutsSb.appendLine(
                    """            <extra android:name="genericui_shortcut_url" android:value="${escapeXml(extraUrlValue)}" />"""
                )
                shortcutsSb.appendLine("""        </intent>""")
            }

            shortcutsSb.appendLine("""    </shortcut>""")
        }

        shortcutsSb.appendLine("""</shortcuts>""")
        stringsSb.appendLine("""</resources>""")
        return shortcutsSb.toString() to stringsSb.toString()
    }

    private fun resolveShortcutTargetClass(
        targetFragment: String?,
        fragments: List<GenericUiDiscoveredFragment>,
        winningMainFragment: GenericUiDiscoveredFragment?
    ): String? {
        val raw = targetFragment?.trim()?.takeIf { it.isNotEmpty() }
        if (raw == null) {
            return winningMainFragment?.generatedActivityQualifiedName
        }
        val matched = fragments.firstOrNull {
            it.qualifiedName == raw ||
                it.simpleName == raw ||
                it.generatedActivityQualifiedName == raw ||
                it.generatedActivitySimpleName == raw
        }
        return matched?.generatedActivityQualifiedName ?: raw
    }

    private fun renderLocalesConfigXml(locales: List<String>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""")
        for (tag in locales) {
            appendLine("""    <locale android:name="${escapeXml(tag)}" />""")
        }
        appendLine("""</locale-config>""")
    }

    private fun renderBackupRulesXml(rules: List<GenericUiAndroidBackupRuleSpec>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<full-backup-content>""")
        for (rule in rules) {
            val elem = if (rule.include) "include" else "exclude"
            appendLine("""    <$elem domain="${escapeXml(rule.domain)}" path="${escapeXml(rule.path)}" />""")
        }
        appendLine("""</full-backup-content>""")
    }

    private fun renderDataExtractionRulesXml(config: GenericUiAndroidBackupRulesConfig): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<data-extraction-rules>""")
        if (config.cloudRules.isNotEmpty()) {
            appendLine("""    <cloud-backup>""")
            for (rule in config.cloudRules) {
                val elem = if (rule.include) "include" else "exclude"
                appendLine("""        <$elem domain="${escapeXml(rule.domain)}" path="${escapeXml(rule.path)}" />""")
            }
            appendLine("""    </cloud-backup>""")
        }
        if (config.deviceTransferRules.isNotEmpty()) {
            appendLine("""    <device-transfer>""")
            for (rule in config.deviceTransferRules) {
                val elem = if (rule.include) "include" else "exclude"
                appendLine("""        <$elem domain="${escapeXml(rule.domain)}" path="${escapeXml(rule.path)}" />""")
            }
            appendLine("""    </device-transfer>""")
        }
        appendLine("""</data-extraction-rules>""")
    }

    private fun renderFilePathsXml(config: GenericUiAndroidFileProviderConfig): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<paths xmlns:android="http://schemas.android.com/apk/res/android">""")
        val effectivePaths = config.paths.ifEmpty {
            listOf(
                GenericUiAndroidFileProviderPathSpec(tag = "files-path", name = "files", path = "."),
                GenericUiAndroidFileProviderPathSpec(tag = "cache-path", name = "cache", path = ".")
            )
        }
        for (entry in effectivePaths) {
            appendLine(
                """    <${entry.tag} name="${escapeXml(entry.name)}" path="${escapeXml(entry.path)}" />"""
            )
        }
        appendLine("""</paths>""")
    }

    private fun resolveWidgetXmlNames(widgets: List<GenericUiAndroidWidgetSpec>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val used = linkedSetOf<String>()
        for (widget in widgets) {
            val simple = widget.providerClass.trim().substringAfterLast('.')
            val snake = GenericUiAndroidManifestConfig.toUpperSnakeCase(simple).lowercase()
            val cleanBase = sanitizeResIdentifier(snake, fallback = "widget")
            val baseStem = if (cleanBase.endsWith("_info")) cleanBase else "${cleanBase}_info"
            var candidate = baseStem
            var counter = 2
            while (!used.add(candidate)) {
                candidate = "${baseStem}_$counter"
                counter++
            }
            result[widget.providerClass] = candidate
        }
        return result
    }

    private fun renderWidgetInfoXml(
        widget: GenericUiAndroidWidgetSpec,
        placeholders: Map<String, String>
    ): String = buildString {
        val attrs = linkedMapOf<String, String>()
        attrs["xmlns:android"] = "http://schemas.android.com/apk/res/android"
        attrs["android:minWidth"] = "${widget.minWidthDp}dp"
        attrs["android:minHeight"] = "${widget.minHeightDp}dp"
        attrs["android:updatePeriodMillis"] = widget.updatePeriodMillis.toString()
        widget.previewImage?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:previewImage"] = expandPlaceholders(it, placeholders)
        }
        widget.initialLayout?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:initialLayout"] = expandPlaceholders(it, placeholders)
        }
        if (widget.resizeMode.isNotBlank()) {
            attrs["android:resizeMode"] = expandPlaceholders(widget.resizeMode.trim(), placeholders)
        }
        if (widget.widgetCategory.isNotBlank()) {
            attrs["android:widgetCategory"] = expandPlaceholders(widget.widgetCategory.trim(), placeholders)
        }
        widget.descriptionRes?.trim()?.takeIf { it.isNotEmpty() }?.let {
            attrs["android:description"] = expandPlaceholders(it, placeholders)
        }

        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendOpenTag(this, indent = "", tag = "appwidget-provider", attrs = attrs, selfClosing = true)
    }

    // --- XML Utilities ---

    private fun usesToolsPrefix(model: GenericUiAndroidManifestModel): Boolean {
        if (model.manifestAttributes.keys.any { it.startsWith("tools:") }) return true
        if (model.applicationAttributes.keys.any { it.startsWith("tools:") }) return true
        if (model.activityDefaults.rawAttributes.keys.any { it.startsWith("tools:") }) return true
        if (model.mainActivity.rawAttributes.keys.any { it.startsWith("tools:") }) return true
        return model.activityOverrides.values.any { ov ->
            ov.rawAttributes.keys.any { it.startsWith("tools:") }
        }
    }

    private fun mergePipeSeparatedTokens(base: String, additions: List<String>): String {
        if (additions.isEmpty()) return base
        val tokens = LinkedHashSet<String>()
        for (part in base.split('|')) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                tokens.add(trimmed)
            }
        }
        for (addition in additions) {
            for (part in addition.split('|')) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    tokens.add(trimmed)
                }
            }
        }
        return tokens.joinToString("|")
    }

    private fun normalizeAttrName(raw: String): String {
        val trimmed = raw.trim()
        return if (':' in trimmed) trimmed else "android:$trimmed"
    }

    private fun sanitizeResIdentifier(raw: String, fallback: String): String {
        val cleaned = raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").replace(Regex("_+"), "_").trim('_')
        if (cleaned.isEmpty()) return fallback
        return if (cleaned.first().isDigit()) "r_$cleaned" else cleaned
    }

    private fun appendOpenTag(
        sb: StringBuilder,
        indent: String,
        tag: String,
        attrs: Map<String, String>,
        selfClosing: Boolean
    ) {
        if (attrs.isEmpty()) {
            sb.appendLine(if (selfClosing) "$indent<$tag />" else "$indent<$tag>")
            return
        }
        val entries = attrs.entries.toList()
        if (entries.size == 1) {
            val (k, v) = entries[0]
            val suffix = if (selfClosing) " />" else ">"
            sb.appendLine("""$indent<$tag $k="${escapeXml(v)}"$suffix""")
            return
        }
        sb.appendLine("$indent<$tag")
        val attrIndent = "$indent    "
        entries.forEachIndexed { index, (k, v) ->
            val isLast = index == entries.lastIndex
            val suffix = when {
                isLast && selfClosing -> " />"
                isLast -> ">"
                else -> ""
            }
            sb.appendLine("""$attrIndent$k="${escapeXml(v)}"$suffix""")
        }
    }

    private fun appendInlineTag(
        sb: StringBuilder,
        indent: String,
        tag: String,
        attrs: Map<String, String>
    ) {
        if (attrs.isEmpty()) {
            sb.appendLine("$indent<$tag />")
            return
        }
        val rendered = attrs.entries.joinToString(" ") { (k, v) -> """$k="${escapeXml(v)}"""" }
        sb.appendLine("$indent<$tag $rendered />")
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
