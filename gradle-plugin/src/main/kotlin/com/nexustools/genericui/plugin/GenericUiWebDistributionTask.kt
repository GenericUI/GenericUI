package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

/**
 * Packages the unified GenericUI Web distribution combining the compiled Kotlin/Wasm (`wasmJs`)
 * primary bundle, the compiled Kotlin/JS IR (`js`) fallback bundle, the project's Web icon
 * (preferring `.svg` for both the `#genericui-splash` icon and `<link rel="icon">` favicon),
 * optional W3C / MDN Progressive Web App `manifest.webmanifest` and installable WebApp `<head>` tags
 * (`allowInstallAsWebApp`), `genericui-loader.js`, and `index.html` with SHA-1 cache-busting hashes
 * appended to `<script type="loader-wasm">`, `<script type="loader-js">`, and `#genericui-loader-script`.
 */
abstract class GenericUiWebDistributionTask : DefaultTask() {

    @get:Input
    abstract val projectRootPath: Property<String>

    @get:Input
    abstract val projectBuildPath: Property<String>

    @get:Input
    abstract val baseName: Property<String>

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    @get:Optional
    abstract val appDescription: Property<String>

    @get:Input
    @get:Optional
    abstract val themeColor: Property<String>

    @get:Input
    @get:Optional
    abstract val themeMode: Property<String>

    @get:Input
    @get:Optional
    abstract val lightThemeColors: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val darkThemeColors: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val hasCustomThemeOverrides: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val webIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val fallbackIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val linuxIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val macIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val windowsIconPath: Property<String>

    @get:Input
    @get:Optional
    abstract val webCssBundleFileName: Property<String>

    @get:Input
    @get:Optional
    abstract val globalStyleSheet: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestEnabled: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val allowInstallAsWebApp: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val manifestFileName: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestId: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestName: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestShortName: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestDescription: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestStartUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestScope: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestDisplay: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestDisplayOverride: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val manifestOrientation: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestThemeColor: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestBackgroundColor: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestLang: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestDir: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestCategories: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val manifestPreferRelatedApplications: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val manifestAppleStatusBarStyle: Property<String>

    @get:Input
    @get:Optional
    abstract val manifestIcons: ListProperty<GenericUiWebManifestIconSpec>

    @get:Input
    @get:Optional
    abstract val manifestShortcuts: ListProperty<GenericUiWebManifestShortcutSpec>

    @get:Input
    @get:Optional
    abstract val buildVariant: Property<String>

    @get:Input
    @get:Optional
    abstract val includeTestScripts: Property<Boolean>

    @get:Optional
    @get:InputFiles
    abstract val jsOutputDirs: ConfigurableFileCollection

    @get:Optional
    @get:InputFiles
    abstract val jsTestOutputDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        includeTestScripts.convention(false)
    }

    @TaskAction
    fun packageWebDistribution() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        val rootDir = File(projectRootPath.get())
        val buildDir = File(projectBuildPath.get())
        val resolvedBase = baseName.get().trim().ifEmpty { "app" }
        val resolvedTitle = appName.get().trim().ifEmpty { resolvedBase }
        val isInstallableWebApp = allowInstallAsWebApp.getOrElse(false)
        val isManifestActive = manifestEnabled.getOrElse(false) || isInstallableWebApp
        val resolvedManifestFileName = manifestFileName.orNull?.trim().orEmpty().ifEmpty { "manifest.webmanifest" }

        // Clean any legacy directories/files so root dist/web only contains allowed root files + gui/ and res/
        cleanLegacyDistributionLayout(outDir, resolvedBase, resolvedManifestFileName, isManifestActive)

        val guiDir = File(outDir, "gui").apply { mkdirs() }
        val resDir = File(outDir, "res").apply { mkdirs() }

        // 1. Copy packaged application resources into res/ preserving the exact folder structure of the app's resources,
        // and stage compiled GenericUI resource metadata into gui/
        copyCandidateResourcesToDistribution(rootDir, buildDir, resDir, guiDir)

        // 2. Resolve Web Icon (staging app icons into res/ and fallback GenericUI default icon into gui/)
        val projectIconFile = resolveProjectWebIconFile(rootDir)
        val iconTargetDir = if (projectIconFile != null) resDir else guiDir
        val iconPrefix = if (projectIconFile != null) "res" else "gui"
        val iconFile = projectIconFile ?: GenericUiDefaultIconProvider.ensureDefaultSvgIcon(rootDir, guiDir)
        val stagedIconFileName = if (iconFile.exists()) {
            val targetIcon = File(iconTargetDir, iconFile.name)
            if (iconFile.canonicalFile != targetIcon.canonicalFile) {
                targetIcon.parentFile?.mkdirs()
                iconFile.copyTo(targetIcon, overwrite = true)
            }
            iconFile.name
        } else {
            val defaultSvg = GenericUiDefaultIconProvider.ensureDefaultSvgIcon(rootDir, guiDir)
            defaultSvg.name
        }
        val stagedIconName = "$iconPrefix/$stagedIconFileName"

        // Ensure standard PWA & favicon icon files exist in iconTargetDir (res/ for app icon, gui/ for default icon)
        if (!File(iconTargetDir, "icon-192.png").exists()) {
            GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(iconFile, rootDir, iconTargetDir, "icon-192.png")
        }
        if (!File(iconTargetDir, "icon-512.png").exists()) {
            GenericUiDefaultIconProvider.ensurePngFromSourceOrDefault(iconFile, rootDir, iconTargetDir, "icon-512.png")
        }
        if (!File(iconTargetDir, "favicon.ico").exists()) {
            GenericUiDefaultIconProvider.ensureWindowsIcoFromSourceOrDefault(iconFile, rootDir, iconTargetDir, "favicon.ico")
        }

        // 3. Stage single ${resolvedBase}.wasm and ${resolvedBase}.js directly in outDir (no wasm/ or js/ folders)
        val requestedVariant = buildVariant.orNull?.trim()?.lowercase()
        val orderedWasmDirs = when (requestedVariant) {
            "development" -> listOf(
                File(buildDir, "dist/wasmJs/developmentExecutable"),
                File(buildDir, "compileSync/wasmJs/main/developmentExecutable/kotlin"),
                File(buildDir, "dist/wasmJs/productionExecutable"),
                File(buildDir, "compileSync/wasmJs/main/productionExecutable/kotlin")
            )
            "production" -> listOf(
                File(buildDir, "dist/wasmJs/productionExecutable"),
                File(buildDir, "compileSync/wasmJs/main/productionExecutable/kotlin"),
                File(buildDir, "dist/wasmJs/developmentExecutable"),
                File(buildDir, "compileSync/wasmJs/main/developmentExecutable/kotlin")
            )
            else -> listOf(
                File(buildDir, "dist/wasmJs/productionExecutable"),
                File(buildDir, "dist/wasmJs/developmentExecutable"),
                File(buildDir, "compileSync/wasmJs/main/productionExecutable/kotlin"),
                File(buildDir, "compileSync/wasmJs/main/developmentExecutable/kotlin")
            ).sortedByDescending { dir ->
                dir.listFiles()?.filter { it.isFile }?.maxOfOrNull { it.lastModified() } ?: 0L
            }
        }
        val wasmDirs = orderedWasmDirs.filter { it.exists() && it.isDirectory }

        val orderedJsDirs = when (requestedVariant) {
            "development" -> listOf(
                File(buildDir, "dist/js/developmentExecutable"),
                File(buildDir, "compileSync/js/main/developmentExecutable/kotlin"),
                File(buildDir, "dist/js/productionExecutable"),
                File(buildDir, "compileSync/js/main/productionExecutable/kotlin")
            )
            "production" -> listOf(
                File(buildDir, "dist/js/productionExecutable"),
                File(buildDir, "compileSync/js/main/productionExecutable/kotlin"),
                File(buildDir, "dist/js/developmentExecutable"),
                File(buildDir, "compileSync/js/main/developmentExecutable/kotlin")
            )
            else -> listOf(
                File(buildDir, "dist/js/productionExecutable"),
                File(buildDir, "dist/js/developmentExecutable"),
                File(buildDir, "compileSync/js/main/productionExecutable/kotlin"),
                File(buildDir, "compileSync/js/main/developmentExecutable/kotlin")
            ).sortedByDescending { dir ->
                dir.listFiles()?.filter { it.isFile }?.maxOfOrNull { it.lastModified() } ?: 0L
            }
        }
        val configuredJsDirs = jsOutputDirs.files.filter { it.exists() && it.isDirectory }
        val jsDirs = (configuredJsDirs + orderedJsDirs.filter { it.exists() && it.isDirectory }).distinct()

        val stagedWasmRelPath = "$resolvedBase.wasm"
        val stagedJsRelPath = "$resolvedBase.js"
        val targetWasmFile = File(outDir, stagedWasmRelPath)
        val targetJsFile = File(outDir, stagedJsRelPath)

        var foundWasmBin: File? = null
        var foundWasmGlueJs: File? = null
        for (dir in wasmDirs) {
            val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
            if (foundWasmBin == null) {
                foundWasmBin = files.firstOrNull {
                    it.extension.equals("wasm", ignoreCase = true) && it.nameWithoutExtension.startsWith(resolvedBase)
                } ?: files.filter { it.extension.equals("wasm", ignoreCase = true) }
                    .maxByOrNull { it.length() }
            }
            if (foundWasmGlueJs == null) {
                foundWasmGlueJs = files.firstOrNull {
                    it.name == "$resolvedBase.js" || it.name == "$resolvedBase-wasm-js.js"
                } ?: files.firstOrNull {
                    it.extension in setOf("js", "mjs") &&
                        !it.name.endsWith(".uninstantiated.mjs") &&
                        it.name != "genericui-loader.js" &&
                        it.name != "loader.js"
                }
            }
            if (foundWasmBin != null && foundWasmGlueJs != null) break
        }

        if (foundWasmBin != null && foundWasmBin.canonicalFile != targetWasmFile.canonicalFile) {
            foundWasmBin.copyTo(targetWasmFile, overwrite = true)
        }

        var foundJsBundle: File? = foundWasmGlueJs
        if (foundJsBundle == null) {
            for (dir in jsDirs) {
                val jsFiles = dir.listFiles()?.filter {
                    it.isFile && it.extension == "js" && it.name != "genericui-loader.js" && it.name != "loader.js"
                }.orEmpty()
                foundJsBundle = jsFiles.firstOrNull { it.name == "$resolvedBase.js" }
                    ?: jsFiles.maxByOrNull { it.length() }
                if (foundJsBundle != null) break
            }
        }

        if (foundJsBundle != null) {
            var jsSourceText = foundJsBundle.readText(Charsets.UTF_8)
            if (foundWasmBin != null && foundWasmBin.name != stagedWasmRelPath) {
                jsSourceText = jsSourceText.replace(foundWasmBin.name, stagedWasmRelPath)
            }
            jsSourceText = Regex(""""([A-Za-z0-9_.-]+\.wasm)"""").replace(jsSourceText) {
                "\"$stagedWasmRelPath\""
            }
            targetJsFile.writeText(jsSourceText, Charsets.UTF_8)
        }

        val stagedWasmSha1 = if (targetWasmFile.isFile) sha1HexOfFile(targetWasmFile) else sha1HexOfString(stagedWasmRelPath)
        val stagedJsSha1 = if (targetJsFile.isFile) sha1HexOfFile(targetJsFile) else sha1HexOfString(stagedJsRelPath)

        // 3b. When includeTestScripts is true, stage compiled test .js files and web-test-harness.js
        val testLoaderScriptTags = mutableListOf<String>()
        if (includeTestScripts.getOrElse(false)) {
            val defaultTestJsDirs = listOf(
                File(buildDir, "compileSync/js/test/testDevelopmentExecutable/kotlin"),
                File(buildDir, "compileSync/js/test/developmentExecutable/kotlin"),
                File(buildDir, "js/packages/${resolvedBase}-test/kotlin")
            )
            val candidateTestDirs = (jsTestOutputDirs.files + defaultTestJsDirs)
                .filter { it.exists() && it.isDirectory }
                .distinctBy { it.canonicalPath }

            val stagedTestJsNames = linkedSetOf<String>()
            for (testDir in candidateTestDirs) {
                val testJsFiles = testDir.listFiles()
                    ?.filter {
                        it.isFile &&
                            it.extension.equals("js", ignoreCase = true) &&
                            it.name != "loader.js" &&
                            it.name != "genericui-loader.js" &&
                            it.name != "web-test-harness.js" &&
                            it.name != stagedJsRelPath
                    }
                    ?.sortedBy { it.name }
                    .orEmpty()
                for (testJs in testJsFiles) {
                    val dstTestJs = File(outDir, testJs.name)
                    if (testJs.canonicalFile != dstTestJs.canonicalFile) {
                        testJs.copyTo(dstTestJs, overwrite = true)
                    }
                    if (stagedTestJsNames.add(testJs.name)) {
                        val hash = sha1HexOfFile(dstTestJs)
                        testLoaderScriptTags.add("""    <script type="loader-js" src="${escapeHtml(testJs.name)}?$hash"></script>""")
                    }
                }
            }

            val harnessFile = File(outDir, "web-test-harness.js")
            harnessFile.writeText(WEB_TEST_HARNESS_JS_SOURCE, Charsets.UTF_8)
            val harnessHash = sha1HexOfFile(harnessFile)
            testLoaderScriptTags.add("""    <script type="loader-js" src="web-test-harness.js?$harnessHash"></script>""")
        }

        // 4. Stage compiled SASS/CSS app stylesheet into res/ (autoloaded via <link rel="stylesheet">)
        // and packaged local Bootstrap 5 + Bootstrap Icons + loading.svg into gui/
        val appStylesheetRelPath = resolveAndStageAppStylesheet(rootDir, buildDir, resDir)
        val extraStylesheetTag = if (appStylesheetRelPath != null) {
            val dstCss = File(outDir, appStylesheetRelPath)
            val cssHash = if (dstCss.isFile) sha1HexOfFile(dstCss) else sha1HexOfString(appStylesheetRelPath)
            """    <link id="genericui-compiled-sass-css" rel="stylesheet" href="$appStylesheetRelPath?$cssHash">"""
        } else {
            ""
        }

        val (bootstrapCssSha1, bootstrapJsSha1, bootstrapIconsCssSha1) = stagePackagedBootstrapAssets(rootDir, guiDir)
        val loadingSvgFile = File(guiDir, "drawable/loading.svg")
        loadingSvgFile.parentFile?.mkdirs()
        loadingSvgFile.writeText(DEFAULT_LOADING_SVG_SOURCE, Charsets.UTF_8)

        // 5. Write loader.js and genericui-namespace.xml to root outDir, and gui/genericui.css to guiDir
        val loaderFile = File(outDir, "loader.js")
        loaderFile.writeText(LOADER_JS_SOURCE, Charsets.UTF_8)
        val loaderSha1 = sha1Hex(loaderFile)

        val namespaceXmlFile = File(outDir, "genericui-namespace.xml")
        namespaceXmlFile.writeText(NAMESPACE_XML_SOURCE, Charsets.UTF_8)
        val namespaceXmlSha1 = sha1Hex(namespaceXmlFile)

        val genericUiCssFile = File(guiDir, "genericui.css")
        genericUiCssFile.writeText(GENERICUI_CSS_SOURCE, Charsets.UTF_8)
        val genericUiCssHash = sha1Hex(genericUiCssFile)

        // 5b. Pre-generate gui/genericui-theme.css when custom theme colors/overrides are configured
        val buildThemeStylesheetTag = maybeGenerateBuildThemeCss(guiDir)

        // 6. Scan packaged resources (gui/ and res/) and emit resources.json in root outDir with per-file SHA-1 hashes
        val resourceDescriptors = scanPackagedResources(outDir, resolvedManifestFileName, resolvedBase)
        val resourcesManifestJson = buildResourcesManifestJson(resourceDescriptors, pretty = true)
        val resourcesJsonFile = File(outDir, "resources.json")
        resourcesJsonFile.writeText(resourcesManifestJson, Charsets.UTF_8)
        val resourcesJsonSha1 = sha1HexOfFile(resourcesJsonFile)
        val inlineResourceManifestJson = buildResourcesManifestJson(resourceDescriptors, pretty = false)

        // 7. Generate W3C / MDN Progressive Web App manifest.webmanifest in root outDir & offline gui/genericui-sw.js if configured
        val resolvedThemeColor = manifestThemeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: themeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedLang = manifestLang.orNull?.trim().orEmpty().ifEmpty { "en" }

        val offlineResourceList = collectOfflineResourcePaths(
            outDir = outDir,
            resolvedBase = resolvedBase,
            manifestFileName = resolvedManifestFileName,
            appStylesheetRelPath = appStylesheetRelPath,
            stagedIconName = stagedIconName,
            iconPrefix = iconPrefix,
            stagedWasmRelPath = stagedWasmRelPath,
            stagedJsRelPath = stagedJsRelPath,
            resourceDescriptors = resourceDescriptors
        )

        val webManifestAndPwaLinks = if (isManifestActive) {
            val manifestJson = generateWebManifestJson(
                rootDir = rootDir,
                outDir = outDir,
                defaultTitle = resolvedTitle,
                stagedIconName = stagedIconName,
                iconPrefix = iconPrefix,
                resolvedThemeColor = resolvedThemeColor ?: "#0d6efd",
                offlineResources = offlineResourceList
            )
            val manifestFile = File(outDir, resolvedManifestFileName)
            manifestFile.writeText(manifestJson, Charsets.UTF_8)
            val manifestSha1 = sha1HexOfFile(manifestFile)

            val swFile = File(guiDir, "genericui-sw.js")
            swFile.writeText(generateServiceWorkerJs(offlineResourceList), Charsets.UTF_8)

            val resolvedFullName = manifestName.orNull?.trim().orEmpty().ifEmpty { resolvedTitle }
            val resolvedShort = manifestShortName.orNull?.trim().orEmpty().ifEmpty { resolvedFullName }
            val effectiveTheme = resolvedThemeColor ?: "#0d6efd"
            val customIcons = manifestIcons.getOrElse(emptyList())
            val primaryTouchIcon = customIcons.firstOrNull()?.src?.trim()?.takeIf { it.isNotEmpty() }
                ?: stagedIconName

            val headTags = mutableListOf<String>()
            headTags.add("""    <link id="genericui-web-manifest" rel="manifest" href="${escapeHtml(resolvedManifestFileName)}?$manifestSha1" crossorigin="use-credentials">""")
            headTags.add("""    <meta id="genericui-theme-color" name="theme-color" content="${escapeHtml(effectiveTheme)}">""")
            if (isInstallableWebApp) {
                val statusStyle = manifestAppleStatusBarStyle.orNull?.trim().orEmpty().ifEmpty { "black-translucent" }
                headTags.add("""    <meta id="genericui-mobile-web-app-capable" name="mobile-web-app-capable" content="yes">""")
                headTags.add("""    <meta id="genericui-apple-mobile-web-app-capable" name="apple-mobile-web-app-capable" content="yes">""")
                headTags.add("""    <meta id="genericui-apple-mobile-web-app-status-bar-style" name="apple-mobile-web-app-status-bar-style" content="${escapeHtml(statusStyle)}">""")
                headTags.add("""    <meta id="genericui-application-name" name="application-name" content="${escapeHtml(resolvedShort)}">""")
                headTags.add("""    <meta id="genericui-apple-mobile-web-app-title" name="apple-mobile-web-app-title" content="${escapeHtml(resolvedShort)}">""")
                if (!primaryTouchIcon.isNullOrBlank()) {
                    headTags.add("""    <link id="genericui-apple-touch-icon" rel="apple-touch-icon" href="${escapeHtml(primaryTouchIcon)}">""")
                }
            }
            headTags.joinToString("\n")
        } else if (resolvedThemeColor != null) {
            """    <meta id="genericui-theme-color" name="theme-color" content="${escapeHtml(resolvedThemeColor)}">"""
        } else {
            ""
        }

        val serviceWorkerRegistrationScript = if (isManifestActive) {
            """    <script id="genericui-sw-register">if (typeof navigator !== "undefined" && "serviceWorker" in navigator) { navigator.serviceWorker.register('./gui/genericui-sw.js').catch(function () {}); }</script>"""
        } else {
            ""
        }

        // 8. Generate index.html from template
        val faviconLink = if (stagedIconName.isNotBlank()) {
            val mimeType = inferIconMimeType(stagedIconName)
            """    <link id="genericui-favicon" rel="icon" type="$mimeType" href="$stagedIconName">"""
        } else {
            ""
        }

        val splashIconHtml = if (stagedIconName.isNotBlank()) {
            """            <img id="genericui-splash-icon" src="$stagedIconName" alt="Application Icon" class="genericui-splash-icon mb-3 rounded" width="64" height="64">"""
        } else {
            """            <i id="genericui-splash-icon" class="bi bi-layers-half fs-1 text-primary mb-3" aria-hidden="true"></i>"""
        }

        val resolvedThemeMode = when (themeMode.orNull?.trim()?.lowercase()) {
            "light" -> "light"
            "dark" -> "dark"
            else -> "auto"
        }

        val primaryLoaderJsTag = """    <script type="loader-js" src="$stagedJsRelPath?$stagedJsSha1"></script>"""
        val allLoaderJsTags = if (testLoaderScriptTags.isEmpty()) {
            primaryLoaderJsTag
        } else {
            (listOf(primaryLoaderJsTag) + testLoaderScriptTags).joinToString("\n")
        }

        val indexHtml = INDEX_HTML_TEMPLATE
            .replace("{{LANG}}", escapeHtml(resolvedLang))
            .replace("{{TITLE}}", escapeHtml(resolvedTitle))
            .replace("{{THEME_MODE}}", resolvedThemeMode)
            .replace("{{NAMESPACE_XML_URL}}", "genericui-namespace.xml")
            .replace("{{NAMESPACE_XML_HASH}}", namespaceXmlSha1)
            .replace("{{RESOURCES_JSON_URL}}", "resources.json")
            .replace("{{RESOURCES_JSON_HASH}}", resourcesJsonSha1)
            .replace("{{GENERICUI_CSS_URL}}", "gui/genericui.css")
            .replace("{{GENERICUI_CSS_HASH}}", genericUiCssHash)
            .replace("{{NAMESPACE_CSS}}", NAMESPACE_CSS_SOURCE)
            .replace("{{FAVICON_LINK}}", faviconLink)
            .replace("{{WEB_MANIFEST_AND_PWA_LINKS}}", webManifestAndPwaLinks)
            .replace("{{SPLASH_ICON}}", splashIconHtml)
            .replace("{{SPLASH_STATUS}}", "Initializing")
            .replace("{{SPLASH_PROGRESS_BAR_CLASS}}", "progress-bar progress-bar-striped progress-bar-animated")
            .replace("{{ROOT_CONTAINER_ID}}", "genericui-app")
            .replace("{{BOOTSTRAP_CSS_URL}}", "gui/bootstrap.min.css?$bootstrapCssSha1")
            .replace("{{BOOTSTRAP_CSS_INTEGRITY}}", "")
            .replace("{{BOOTSTRAP_JS_URL}}", "gui/bootstrap.bundle.min.js?$bootstrapJsSha1")
            .replace("{{BOOTSTRAP_JS_INTEGRITY}}", "")
            .replace("{{CROSSORIGIN}}", "")
            .replace(""" integrity=""""""", "")
            .replace(""" crossorigin=""""""", "")
            .replace(
                "{{BOOTSTRAP_ICONS_LINK}}",
                """    <link id="genericui-bootstrap-icons-css" rel="stylesheet" href="gui/bootstrap-icons.min.css?$bootstrapIconsCssSha1">"""
            )
            .replace(
                "{{EXTRA_STYLESHEETS}}",
                listOf(buildThemeStylesheetTag, extraStylesheetTag)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            )
            .replace("{{RESOURCE_MANIFEST_JSON}}", inlineResourceManifestJson)
            .replace("{{EXTRA_SCRIPTS}}", serviceWorkerRegistrationScript)
            .replace("{{WASM_URL}}", stagedWasmRelPath)
            .replace("{{WASM_HASH}}", stagedWasmSha1)
            .replace("{{WASM_BINARY_ATTR}}", "")
            .replace("""    <script type="loader-js" src="{{JS_URL}}?{{JS_HASH}}"></script>""", allLoaderJsTags)
            .replace("{{JS_URL}}", stagedJsRelPath)
            .replace("{{JS_HASH}}", stagedJsSha1)
            .replace("{{LOADER_JS_URL}}", "loader.js")
            .replace("{{LOADER_JS_HASH}}", loaderSha1)
            .lineSequence()
            .filter { line -> line.isNotBlank() || !line.contains("{{") }
            .joinToString("\n")

        File(outDir, "index.html").writeText(indexHtml, Charsets.UTF_8)
    }

    private fun maybeGenerateBuildThemeCss(guiDir: File): String {
        val themeCssFile = File(guiDir, "genericui-theme.css")
        val configuredPrimary = themeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
        val defaultLight = GenericUiThemeConfig.defaultLightColors()
        val defaultDark = GenericUiThemeConfig.defaultDarkColors()
        val lightInput = lightThemeColors.orNull.orEmpty().filterValues { it.isNotBlank() }
        val darkInput = darkThemeColors.orNull.orEmpty().filterValues { it.isNotBlank() }

        val resolvedLight = GenericUiThemeConfig.defaultLightColors(configuredPrimary).toMutableMap().apply {
            putAll(lightInput)
        }
        val resolvedDark = GenericUiThemeConfig.defaultDarkColors(configuredPrimary).toMutableMap().apply {
            putAll(darkInput)
        }

        val hasOverrides = (hasCustomThemeOverrides.orNull == true) ||
            (configuredPrimary != null && !configuredPrimary.equals(GenericUiThemeConfig.DEFAULT_PRIMARY_COLOR, ignoreCase = true)) ||
            (lightInput.isNotEmpty() && resolvedLight != defaultLight) ||
            (darkInput.isNotEmpty() && resolvedDark != defaultDark)

        if (!hasOverrides) {
            if (themeCssFile.exists()) {
                themeCssFile.delete()
            }
            return ""
        }

        val cssText = buildThemeOverrideCss(resolvedLight, resolvedDark)
        themeCssFile.parentFile?.mkdirs()
        themeCssFile.writeText(cssText, Charsets.UTF_8)
        return """    <link id="genericui-build-theme-css" rel="stylesheet" href="gui/genericui-theme.css">"""
    }

    private fun buildThemeOverrideCss(
        lightColors: Map<String, String>,
        darkColors: Map<String, String>
    ): String = buildString {
        appendLine("/* Pre-generated GenericUI Theme Stylesheet (gui/genericui-theme.css) */")
        emitWebThemeScopeCss(":root, [data-bs-theme=\"light\"]", lightColors, isDark = false)
        appendLine()
        emitWebThemeScopeCss("[data-bs-theme=\"dark\"]", darkColors, isDark = true)
    }

    private fun StringBuilder.emitWebThemeScopeCss(
        scopeSelector: String,
        palette: Map<String, String>,
        isDark: Boolean
    ) {
        val primary = normalizeCssHex(palette[GenericUiThemeConfig.KEY_PRIMARY] ?: "#0d6efd")
        val secondary = normalizeCssHex(palette[GenericUiThemeConfig.KEY_SECONDARY] ?: "#6c757d")
        val accent = normalizeCssHex(palette[GenericUiThemeConfig.KEY_ACCENT] ?: primary)
        val success = normalizeCssHex(palette[GenericUiThemeConfig.KEY_SUCCESS] ?: "#198754")
        val danger = normalizeCssHex(palette[GenericUiThemeConfig.KEY_DANGER] ?: "#dc3545")
        val warning = normalizeCssHex(palette[GenericUiThemeConfig.KEY_WARNING] ?: "#ffc107")
        val info = normalizeCssHex(palette[GenericUiThemeConfig.KEY_INFO] ?: "#0dcaf0")
        val light = normalizeCssHex(palette[GenericUiThemeConfig.KEY_LIGHT] ?: "#f8f9fa")
        val dark = normalizeCssHex(palette[GenericUiThemeConfig.KEY_DARK] ?: "#212529")
        val background = normalizeCssHex(
            palette[GenericUiThemeConfig.KEY_BACKGROUND] ?: if (isDark) "#212529" else "#ffffff"
        )
        val surface = normalizeCssHex(
            palette[GenericUiThemeConfig.KEY_SURFACE] ?: if (isDark) "#2b3035" else "#f8f9fa"
        )
        val textPrimary = normalizeCssHex(
            palette[GenericUiThemeConfig.KEY_TEXT_PRIMARY] ?: if (isDark) "#f8f9fa" else "#212529"
        )
        val textSecondary = normalizeCssHex(
            palette[GenericUiThemeConfig.KEY_TEXT_SECONDARY] ?: if (isDark) "#adb5bd" else "#6c757d"
        )
        val border = normalizeCssHex(
            palette[GenericUiThemeConfig.KEY_BORDER] ?: if (isDark) "#495057" else "#dee2e6"
        )

        appendLine("$scopeSelector {")
        emitBsCssVar("primary", primary)
        emitBsCssVar("secondary", secondary)
        emitBsCssVar("success", success)
        emitBsCssVar("danger", danger)
        emitBsCssVar("warning", warning)
        emitBsCssVar("info", info)
        emitBsCssVar("light", light)
        emitBsCssVar("dark", dark)
        appendLine("  --bs-link-color: $primary;")
        appendLine("  --bs-link-color-rgb: ${hexToRgbTuple(primary)};")
        appendLine("  --bs-body-bg: $background;")
        appendLine("  --bs-body-bg-rgb: ${hexToRgbTuple(background)};")
        appendLine("  --bs-tertiary-bg: $surface;")
        appendLine("  --bs-tertiary-bg-rgb: ${hexToRgbTuple(surface)};")
        appendLine("  --bs-body-color: $textPrimary;")
        appendLine("  --bs-body-color-rgb: ${hexToRgbTuple(textPrimary)};")
        appendLine("  --bs-secondary-color: $textSecondary;")
        appendLine("  --bs-secondary-color-rgb: ${hexToRgbTuple(textSecondary)};")
        appendLine("  --bs-border-color: $border;")
        appendLine("  --genericui-theme-color: $primary;")
        appendLine("  --genericui-accent: $accent;")
        appendLine("}")

        val semanticVariants = listOf(
            "primary" to primary,
            "secondary" to secondary,
            "success" to success,
            "danger" to danger,
            "warning" to warning,
            "info" to info,
            "light" to light,
            "dark" to dark
        )
        for ((name, colorHex) in semanticVariants) {
            emitBsComponentRules(scopeSelector, name, colorHex)
        }
    }

    private fun StringBuilder.emitBsCssVar(name: String, hex: String) {
        appendLine("  --bs-$name: $hex;")
        appendLine("  --bs-$name-rgb: ${hexToRgbTuple(hex)};")
    }

    private fun StringBuilder.emitBsComponentRules(
        scopeSelector: String,
        name: String,
        baseHex: String
    ) {
        val (r, g, b) = parseHexRgb(baseHex)
        val luma = (r * 299 + g * 587 + b * 114) / 1000
        val fgHex = if (luma >= 150) "#212529" else "#ffffff"
        val hoverHex = scaleHexRgb(r, g, b, 0.90)
        val activeHex = scaleHexRgb(r, g, b, 0.84)

        appendLine("$scopeSelector .btn-$name, .btn-$name {")
        appendLine("  --bs-btn-color: $fgHex;")
        appendLine("  --bs-btn-bg: $baseHex;")
        appendLine("  --bs-btn-border-color: $baseHex;")
        appendLine("  --bs-btn-hover-color: $fgHex;")
        appendLine("  --bs-btn-hover-bg: $hoverHex;")
        appendLine("  --bs-btn-hover-border-color: $hoverHex;")
        appendLine("  --bs-btn-active-color: $fgHex;")
        appendLine("  --bs-btn-active-bg: $activeHex;")
        appendLine("  --bs-btn-active-border-color: $activeHex;")
        appendLine("  background-color: $baseHex;")
        appendLine("  border-color: $baseHex;")
        appendLine("  color: $fgHex;")
        appendLine("}")

        appendLine("$scopeSelector .btn-outline-$name, .btn-outline-$name {")
        appendLine("  --bs-btn-color: $baseHex;")
        appendLine("  --bs-btn-border-color: $baseHex;")
        appendLine("  --bs-btn-hover-color: $fgHex;")
        appendLine("  --bs-btn-hover-bg: $baseHex;")
        appendLine("  --bs-btn-hover-border-color: $baseHex;")
        appendLine("  --bs-btn-active-color: $fgHex;")
        appendLine("  --bs-btn-active-bg: $activeHex;")
        appendLine("  --bs-btn-active-border-color: $activeHex;")
        appendLine("  color: $baseHex;")
        appendLine("  border-color: $baseHex;")
        appendLine("}")

        appendLine("$scopeSelector .bg-$name, .bg-$name { background-color: $baseHex !important; }")
        appendLine("$scopeSelector .text-$name, .text-$name { color: $baseHex !important; }")
        appendLine("$scopeSelector .border-$name, .border-$name { border-color: $baseHex !important; }")
    }

    private fun normalizeCssHex(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "#0d6efd"
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun parseHexRgb(hex: String): Triple<Int, Int, Int> {
        val clean = hex.trim().removePrefix("#")
        val full = when (clean.length) {
            3 -> "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}"
            6 -> clean
            8 -> clean.substring(0, 6)
            else -> "0d6efd"
        }
        val r = full.substring(0, 2).toIntOrNull(16) ?: 13
        val g = full.substring(2, 4).toIntOrNull(16) ?: 110
        val b = full.substring(4, 6).toIntOrNull(16) ?: 253
        return Triple(r, g, b)
    }

    private fun hexToRgbTuple(hex: String): String {
        val (r, g, b) = parseHexRgb(hex)
        return "$r, $g, $b"
    }

    private fun scaleHexRgb(r: Int, g: Int, b: Int, factor: Double): String {
        val sr = (r * factor).toInt().coerceIn(0, 255)
        val sg = (g * factor).toInt().coerceIn(0, 255)
        val sb = (b * factor).toInt().coerceIn(0, 255)
        return "#%02x%02x%02x".format(sr, sg, sb)
    }

    private fun cleanLegacyDistributionLayout(
        outDir: File,
        resolvedBase: String,
        resolvedManifestFileName: String,
        isManifestActive: Boolean
    ) {
        val allowedRootEntries = buildSet {
            add("index.html")
            add("loader.js")
            add("genericui-namespace.xml")
            add("resources.json")
            add("$resolvedBase.js")
            add("$resolvedBase.wasm")
            add("gui")
            add("res")
            if (isManifestActive) {
                add("manifest.webmanifest")
                add(resolvedManifestFileName)
            }
        }
        outDir.listFiles()?.forEach { child ->
            if (child.name !in allowedRootEntries) {
                child.deleteRecursively()
            }
        }
    }

    private fun discoverAppSassRelativePaths(rootDir: File): Map<String, String> {
        val sourceCandidateDirs = listOf(
            File(rootDir, "src/commonMain/resources"),
            File(rootDir, "src/common/resources"),
            File(rootDir, "src/webMain/resources"),
            File(rootDir, "src/web/resources"),
            File(rootDir, "src/wasmJsMain/resources"),
            File(rootDir, "src/jsMain/resources"),
            File(rootDir, "src/main/resources"),
            File(rootDir, "resources")
        )
        val stemToRelCssPath = linkedMapOf<String, String>()
        for (candidateDir in sourceCandidateDirs) {
            if (!candidateDir.exists() || !candidateDir.isDirectory) continue
            candidateDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
                val ext = srcFile.extension.lowercase()
                if (ext in setOf("scss", "sass") && !srcFile.name.startsWith("_")) {
                    val relPath = srcFile.relativeTo(candidateDir).invariantSeparatorsPath
                    val relCss = relPath.replace(Regex("\\.(scss|sass)$", RegexOption.IGNORE_CASE), ".css")
                    stemToRelCssPath.putIfAbsent(srcFile.nameWithoutExtension, relCss)
                }
            }
        }
        return stemToRelCssPath
    }

    private fun copyCandidateResourcesToDistribution(
        rootDir: File,
        buildDir: File,
        resDir: File,
        guiDir: File
    ) {
        val reservedFileNames = setOf(
            "index.html",
            "index.template.html",
            "loader.js",
            "genericui-loader.js",
            "genericui-sw.js",
            "manifest.webmanifest",
            "resources-manifest.json",
            "resources.json",
            "genericui-namespace.xml",
            "genericui.css",
            "genericui-theme.css",
            "genericui-styles.css",
            "bootstrap.min.css",
            "bootstrap.bundle.min.js",
            "bootstrap-icons.min.css"
        )
        val guiMetadataFileNames = setOf(
            "genericui-resources.json",
            "genericui-resource-vars.css"
        )
        val sassStemToRelCss = discoverAppSassRelativePaths(rootDir)

        val sourceCandidateDirs = listOf(
            File(rootDir, "src/commonMain/resources"),
            File(rootDir, "src/common/resources"),
            File(rootDir, "src/webMain/resources"),
            File(rootDir, "src/web/resources"),
            File(rootDir, "src/wasmJsMain/resources"),
            File(rootDir, "src/jsMain/resources"),
            File(rootDir, "src/main/resources"),
            File(rootDir, "resources")
        )
        val buildResourceDirs = listOf(
            File(buildDir, "processedResources/wasmJs/main"),
            File(buildDir, "processedResources/js/main")
        )

        for (candidateDir in (sourceCandidateDirs + buildResourceDirs)) {
            if (!candidateDir.exists() || !candidateDir.isDirectory) continue
            candidateDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
                val fileName = srcFile.name
                val ext = srcFile.extension.lowercase()
                if (fileName in reservedFileNames) return@forEach
                if (ext in setOf("scss", "sass", "php", "map")) return@forEach
                val relPath = srcFile.relativeTo(candidateDir).invariantSeparatorsPath
                if (fileName in guiMetadataFileNames) {
                    val dstGuiFile = File(guiDir, fileName)
                    if (srcFile.canonicalFile != dstGuiFile.canonicalFile) {
                        dstGuiFile.parentFile?.mkdirs()
                        srcFile.copyTo(dstGuiFile, overwrite = true)
                    }
                    return@forEach
                }
                val mappedRelPath = if (!relPath.contains('/') && ext == "css") {
                    sassStemToRelCss[srcFile.nameWithoutExtension] ?: relPath
                } else {
                    relPath
                }
                val dstFile = File(resDir, mappedRelPath)
                if (srcFile.canonicalFile != dstFile.canonicalFile) {
                    dstFile.parentFile?.mkdirs()
                    srcFile.copyTo(dstFile, overwrite = true)
                }
            }
        }

        val generatedWebResDir = File(buildDir, "generated/genericui/resources/backends/web")
        if (generatedWebResDir.exists() && generatedWebResDir.isDirectory) {
            generatedWebResDir.walkTopDown().filter { it.isFile }.forEach { srcFile ->
                val fileName = srcFile.name
                val relPath = srcFile.relativeTo(generatedWebResDir).invariantSeparatorsPath
                if (fileName in guiMetadataFileNames) {
                    val dstGuiFile = File(guiDir, fileName)
                    if (srcFile.canonicalFile != dstGuiFile.canonicalFile) {
                        dstGuiFile.parentFile?.mkdirs()
                        srcFile.copyTo(dstGuiFile, overwrite = true)
                    }
                } else if (fileName !in reservedFileNames && fileName !in setOf("icon.png", "icon.ico", "icon.svg")) {
                    val dstResFile = File(resDir, relPath)
                    if (!dstResFile.exists() && srcFile.canonicalFile != dstResFile.canonicalFile) {
                        dstResFile.parentFile?.mkdirs()
                        srcFile.copyTo(dstResFile, overwrite = true)
                    }
                }
            }
        }
    }

    private fun resolveAndStageAppStylesheet(rootDir: File, buildDir: File, resDir: File): String? {
        val sassStemToRelCss = discoverAppSassRelativePaths(rootDir)
        val compiledCssDirs = listOf(
            File(buildDir, "generated/genericui/web-css"),
            File(buildDir, "generated/genericui/styles")
        ).filter { it.exists() && it.isDirectory }

        for (cssDir in compiledCssDirs) {
            cssDir.walkTopDown().filter { it.isFile && it.extension.equals("css", ignoreCase = true) }.forEach { cssFile ->
                if (cssFile.name in setOf("genericui.css", "genericui-styles.css", "bootstrap.min.css", "bootstrap-icons.min.css", "genericui-resource-vars.css")) {
                    return@forEach
                }
                val relInCssDir = cssFile.relativeTo(cssDir).invariantSeparatorsPath
                val targetRelInRes = if (!relInCssDir.contains('/')) {
                    sassStemToRelCss[cssFile.nameWithoutExtension] ?: relInCssDir
                } else {
                    relInCssDir
                }
                val dstFile = File(resDir, targetRelInRes)
                if (cssFile.canonicalFile != dstFile.canonicalFile) {
                    dstFile.parentFile?.mkdirs()
                    cssFile.copyTo(dstFile, overwrite = true)
                }
                if (targetRelInRes.contains('/')) {
                    val duplicateRootCss = File(resDir, cssFile.name)
                    if (duplicateRootCss.isFile && duplicateRootCss.canonicalFile != dstFile.canonicalFile) {
                        duplicateRootCss.delete()
                    }
                }
            }
        }

        val configuredGlobal = globalStyleSheet.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: webCssBundleFileName.orNull?.trim()?.takeIf { it.isNotEmpty() }
        if (configuredGlobal != null) {
            val cleanConfigured = configuredGlobal
                .removePrefix("./")
                .removePrefix("/")
                .removePrefix("res/")
                .replace('\\', '/')
                .replace(Regex("\\.(scss|sass)$", RegexOption.IGNORE_CASE), ".css")
            val candidateInRes = File(resDir, cleanConfigured)
            if (candidateInRes.isFile) {
                return "res/$cleanConfigured"
            }
            val stem = File(cleanConfigured).nameWithoutExtension
            val mappedRel = sassStemToRelCss[stem]
            if (mappedRel != null && File(resDir, mappedRel).isFile) {
                return "res/$mappedRel"
            }
            val matchingFileInRes = resDir.walkTopDown().firstOrNull {
                it.isFile && it.name.equals(File(cleanConfigured).name, ignoreCase = true)
            }
            if (matchingFileInRes != null) {
                return "res/${matchingFileInRes.relativeTo(resDir).invariantSeparatorsPath}"
            }
            // Search compiled CSS directories if not yet in resDir
            for (cssDir in compiledCssDirs) {
                val srcCss = File(cssDir, cleanConfigured).takeIf { it.isFile }
                    ?: File(cssDir, File(cleanConfigured).name).takeIf { it.isFile }
                if (srcCss != null) {
                    val dstInRes = File(resDir, cleanConfigured)
                    dstInRes.parentFile?.mkdirs()
                    srcCss.copyTo(dstInRes, overwrite = true)
                    return "res/$cleanConfigured"
                }
            }
        }

        // Fallback: if compiledCssDirs produced a single primary stylesheet in resDir, autoload it
        for (cssDir in compiledCssDirs) {
            val firstCompiled = cssDir.walkTopDown().firstOrNull {
                it.isFile && it.extension.equals("css", ignoreCase = true) &&
                    it.name !in setOf("genericui.css", "genericui-styles.css", "bootstrap.min.css", "bootstrap-icons.min.css", "genericui-resource-vars.css")
            }
            if (firstCompiled != null) {
                val relInCssDir = firstCompiled.relativeTo(cssDir).invariantSeparatorsPath
                val targetRelInRes = if (!relInCssDir.contains('/')) {
                    sassStemToRelCss[firstCompiled.nameWithoutExtension] ?: relInCssDir
                } else {
                    relInCssDir
                }
                if (File(resDir, targetRelInRes).isFile) {
                    return "res/$targetRelInRes"
                }
            }
        }

        return null
    }

    private fun stagePackagedBootstrapAssets(rootDir: File, guiDir: File): Triple<String, String, String> {
        val assetRelPaths = listOf(
            "bootstrap.min.css",
            "bootstrap.bundle.min.js",
            "bootstrap-icons.min.css",
            "fonts/bootstrap-icons.woff2",
            "fonts/bootstrap-icons.woff"
        )
        val userDir = File(System.getProperty("user.dir") ?: ".")
        for (relPath in assetRelPaths) {
            val dstFile = File(guiDir, relPath)
            val candidates = listOf(
                dstFile,
                File(rootDir, "web/src/commonMain/resources/genericui/$relPath"),
                File(rootDir, "lib/GenericUI/web/src/commonMain/resources/genericui/$relPath"),
                File(rootDir, "../web/src/commonMain/resources/genericui/$relPath"),
                File(rootDir, "../../web/src/commonMain/resources/genericui/$relPath"),
                File(rootDir, "../../../web/src/commonMain/resources/genericui/$relPath"),
                File(userDir, "web/src/commonMain/resources/genericui/$relPath"),
                File(userDir, "../web/src/commonMain/resources/genericui/$relPath"),
                File(userDir, "../../web/src/commonMain/resources/genericui/$relPath"),
                File("D:/IdeaProjects/GenericUI/web/src/commonMain/resources/genericui/$relPath")
            )
            val srcFile = candidates.firstOrNull { it.exists() && it.isFile && it.length() > 256L }
            if (srcFile != null && srcFile.canonicalFile != dstFile.canonicalFile) {
                dstFile.parentFile?.mkdirs()
                srcFile.copyTo(dstFile, overwrite = true)
            }
        }
        val cssFile = File(guiDir, "bootstrap.min.css")
        val jsFile = File(guiDir, "bootstrap.bundle.min.js")
        val iconsCssFile = File(guiDir, "bootstrap-icons.min.css")
        return Triple(
            if (cssFile.isFile) sha1HexOfFile(cssFile) else sha1HexOfString("gui/bootstrap.min.css"),
            if (jsFile.isFile) sha1HexOfFile(jsFile) else sha1HexOfString("gui/bootstrap.bundle.min.js"),
            if (iconsCssFile.isFile) sha1HexOfFile(iconsCssFile) else sha1HexOfString("gui/bootstrap-icons.min.css")
        )
    }

    private fun scanPackagedResources(
        outDir: File,
        manifestFileName: String,
        resolvedBase: String
    ): List<GenericUiWebResourceDescriptor> {
        val excludedPaths = setOf(
            "index.html",
            "loader.js",
            "genericui-loader.js",
            "web-test-harness.js",
            "genericui-sw.js",
            "gui/genericui-sw.js",
            "manifest.webmanifest",
            manifestFileName,
            "resources.json",
            "resources-manifest.json",
            "$resolvedBase.js",
            "$resolvedBase.wasm"
        )

        val files = outDir.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val relPath = file.relativeTo(outDir).invariantSeparatorsPath
                if (relPath in excludedPaths) return@mapNotNull null
                val lowerRel = relPath.lowercase()
                if (lowerRel.endsWith(".php") || lowerRel.endsWith(".map") ||
                    lowerRel.endsWith(".scss") || lowerRel.endsWith(".sass") ||
                    lowerRel.endsWith(".wasm")
                ) return@mapNotNull null
                relPath
            }
            .sorted()
            .toList()

        val seenIds = mutableSetOf<String>()
        val descriptors = mutableListOf<GenericUiWebResourceDescriptor>()
        for (relPath in files) {
            val (type, baseName) = classifyResourceTypeAndName(relPath)
            val mimeType = inferResourceMimeType(relPath)
            var candidateName = baseName
            var candidateId = "$type/$candidateName"
            if (!seenIds.add(candidateId)) {
                val fileNameWithExt = relPath.substringAfterLast('/')
                candidateName = fileNameWithExt
                candidateId = "$type/$candidateName"
                if (!seenIds.add(candidateId)) {
                    candidateName = relPath.replace('/', '_')
                    candidateId = "$type/$candidateName"
                    seenIds.add(candidateId)
                }
            }
            val fileOnDisk = File(outDir, relPath)
            val fileHash = if (fileOnDisk.isFile) sha1HexOfFile(fileOnDisk) else sha1HexOfString(relPath)
            val fileSize = if (fileOnDisk.isFile) fileOnDisk.length() else 0L
            descriptors.add(
                GenericUiWebResourceDescriptor(
                    id = candidateId,
                    type = type,
                    name = candidateName,
                    path = relPath,
                    hash = fileHash,
                    mimeType = mimeType,
                    size = fileSize
                )
            )
        }
        val wasmFile = File(outDir, "$resolvedBase.wasm")
        if (wasmFile.isFile) {
            descriptors.add(
                GenericUiWebResourceDescriptor(
                    id = "wasm/$resolvedBase",
                    type = "wasm",
                    name = resolvedBase,
                    path = "$resolvedBase.wasm",
                    hash = sha1HexOfFile(wasmFile),
                    mimeType = "application/wasm",
                    size = wasmFile.length()
                )
            )
        }
        val jsFile = File(outDir, "$resolvedBase.js")
        if (jsFile.isFile) {
            descriptors.add(
                GenericUiWebResourceDescriptor(
                    id = "js/$resolvedBase",
                    type = "js",
                    name = resolvedBase,
                    path = "$resolvedBase.js",
                    hash = sha1HexOfFile(jsFile),
                    mimeType = "application/javascript",
                    size = jsFile.length()
                )
            )
        }
        return descriptors
    }

    private fun classifyResourceTypeAndName(relPath: String): Pair<String, String> {
        val normalized = relPath.replace('\\', '/').trimStart('/')
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        val fileName = segments.lastOrNull() ?: normalized
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val rawName = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        val name = rawName.ifEmpty { fileName }

        val folderSegments = segments.dropLast(1)
        val primaryFolder = when {
            folderSegments.size >= 2 && folderSegments[0].lowercase() in setOf("res", "gui", "resources", "assets") ->
                folderSegments[1].lowercase()
            folderSegments.size == 1 && folderSegments[0].lowercase() in setOf("res", "gui", "resources", "assets") ->
                ""
            folderSegments.isNotEmpty() ->
                folderSegments[0].lowercase()
            else -> ""
        }

        val type = when (primaryFolder) {
            "drawable", "drawables", "mipmap", "icons", "cursors", "cursor" -> "drawable"
            "images", "image", "img" -> "images"
            "styles", "style", "css", "scss", "sass" -> "styles"
            "raw", "scripts" -> "raw"
            "font", "fonts" -> "font"
            "values", "value" -> "values"
            "xml" -> "xml"
            "layout", "layouts" -> "layout"
            "menu", "menus" -> "menu"
            "res", "gui", "resources", "assets", "" -> inferResourceTypeFromExtension(ext)
            else -> primaryFolder
        }
        return type to name
    }

    private fun inferResourceTypeFromExtension(ext: String): String =
        when (ext.lowercase()) {
            "css", "scss", "sass" -> "styles"
            "svg", "png", "jpg", "jpeg", "webp", "gif", "ico", "cur", "bmp" -> "drawable"
            "ttf", "otf", "woff", "woff2" -> "font"
            "xml" -> "xml"
            "js", "mjs" -> "raw"
            else -> "raw"
        }

    private fun inferResourceMimeType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".scss") || lower.endsWith(".sass") -> "text/x-scss"
            lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript"
            lower.endsWith(".json") || lower.endsWith(".webmanifest") -> "application/json"
            lower.endsWith(".xml") -> "application/xml"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".ico") || lower.endsWith(".cur") -> "image/x-icon"
            lower.endsWith(".bmp") -> "image/bmp"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".ttf") -> "font/ttf"
            lower.endsWith(".otf") -> "font/otf"
            lower.endsWith(".wasm") -> "application/wasm"
            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv") ||
                lower.endsWith(".glsl") || lower.endsWith(".wgsl") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun buildResourcesManifestJson(
        descriptors: List<GenericUiWebResourceDescriptor>,
        pretty: Boolean
    ): String {
        if (descriptors.isEmpty()) {
            return if (pretty) "[]\n" else "[]"
        }
        return if (pretty) {
            val entries = descriptors.joinToString(",\n") { d ->
                buildString {
                    append("  {\n")
                    append("""    "id": "${escapeJson(d.id)}",""").append("\n")
                    append("""    "type": "${escapeJson(d.type)}",""").append("\n")
                    append("""    "name": "${escapeJson(d.name)}",""").append("\n")
                    append("""    "path": "${escapeJson(d.path)}",""").append("\n")
                    append("""    "hash": "${escapeJson(d.hash)}",""").append("\n")
                    append("""    "mimeType": "${escapeJson(d.mimeType)}",""").append("\n")
                    append("""    "size": ${d.size}""").append("\n")
                    append("  }")
                }
            }
            "[\n$entries\n]\n"
        } else {
            val entries = descriptors.joinToString(",") { d ->
                """{"id":"${escapeJson(d.id)}","type":"${escapeJson(d.type)}","name":"${escapeJson(d.name)}","path":"${escapeJson(d.path)}","hash":"${escapeJson(d.hash)}","mimeType":"${escapeJson(d.mimeType)}","size":${d.size}}"""
            }
            "[$entries]"
        }
    }

    private fun collectOfflineResourcePaths(
        outDir: File,
        resolvedBase: String,
        manifestFileName: String,
        appStylesheetRelPath: String?,
        stagedIconName: String?,
        iconPrefix: String,
        stagedWasmRelPath: String,
        stagedJsRelPath: String,
        resourceDescriptors: List<GenericUiWebResourceDescriptor>
    ): List<String> {
        val paths = linkedSetOf<String>()
        paths.add("./")
        paths.add("index.html")
        paths.add(manifestFileName)
        paths.add("genericui-namespace.xml")
        paths.add("loader.js")
        paths.add("resources.json")
        paths.add("gui/genericui.css")
        paths.add("gui/bootstrap.min.css")
        paths.add("gui/bootstrap.bundle.min.js")
        paths.add("gui/bootstrap-icons.min.css")
        paths.add("gui/drawable/loading.svg")
        if (!appStylesheetRelPath.isNullOrBlank()) {
            paths.add(appStylesheetRelPath)
        }
        paths.add(stagedWasmRelPath)
        paths.add(stagedJsRelPath)

        // Include all generated & configured icon files
        if (!stagedIconName.isNullOrBlank()) {
            paths.add(stagedIconName)
        }
        paths.add("$iconPrefix/icon-192.png")
        paths.add("$iconPrefix/icon-512.png")
        paths.add("$iconPrefix/favicon.ico")
        manifestIcons.getOrElse(emptyList()).forEach { spec ->
            val cleanSrc = spec.src.trim()
            if (cleanSrc.isNotEmpty()) {
                val fileName = File(cleanSrc).name
                val inIconDir = "$iconPrefix/$fileName"
                if (File(outDir, inIconDir).exists()) {
                    paths.add(inIconDir)
                } else {
                    paths.add(cleanSrc)
                }
            }
        }

        // Include all scanned application resource files
        resourceDescriptors.forEach { d ->
            paths.add(d.path)
        }

        // Include any remaining files in outDir (excluding genericui-sw.js itself)
        outDir.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(outDir).invariantSeparatorsPath
            if (rel != "genericui-sw.js" && rel != "gui/genericui-sw.js") {
                paths.add(rel)
            }
        }

        // Only list files that actually exist in the distribution: a single 404 makes the service worker's
        // cache.addAll() reject. "./", index.html, and the web manifest are written after this list is built.
        val writtenLater = setOf("./", "index.html", manifestFileName)
        return paths.filter { p ->
            p in writtenLater || File(outDir, p.substringBefore('?')).isFile
        }
    }

    private fun resolveProjectWebIconFile(rootDir: File): File? {
        val configuredCandidates = listOfNotNull(
            webIconPath.orNull?.trim()?.takeIf { it.isNotEmpty() },
            fallbackIconPath.orNull?.trim()?.takeIf { it.isNotEmpty() },
            linuxIconPath.orNull?.trim()?.takeIf { it.isNotEmpty() },
            macIconPath.orNull?.trim()?.takeIf { it.isNotEmpty() },
            windowsIconPath.orNull?.trim()?.takeIf { it.isNotEmpty() }
        )
        val defaultCandidates = listOf(
            "icon.svg",
            "web-icon.svg",
            "favicon.svg",
            "src/webMain/resources/icon.svg",
            "src/commonMain/resources/icon.svg",
            "favicon.ico",
            "src/webMain/resources/favicon.ico",
            "src/commonMain/resources/favicon.ico",
            "icon.png",
            "src/webMain/resources/icon.png",
            "src/commonMain/resources/icon.png",
            "icon.ico",
            "src/commonMain/resources/icon.ico"
        )
        for (rel in configuredCandidates + defaultCandidates) {
            val f = File(rel).let { if (it.isAbsolute) it else File(rootDir, rel) }
            if (f.exists() && f.isFile) {
                return f
            }
        }
        return null
    }

    private fun inferIconMimeType(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".ico") -> "image/x-icon"
            lower.endsWith(".webp") -> "image/webp"
            else -> "image/svg+xml"
        }
    }

    private fun sha1Hex(file: File): String =
        sha1HexOfBytes(file.readBytes())

    private fun sha1HexOfFile(file: File): String =
        sha1HexOfBytes(file.readBytes())

    private fun sha1HexOfString(text: String): String =
        sha1HexOfBytes(text.toByteArray(Charsets.UTF_8))

    private fun sha1HexOfBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildManifestJson(
        rootDir: File,
        outDir: File,
        defaultTitle: String,
        stagedIconName: String?,
        iconPrefix: String = "res",
        resolvedThemeColor: String,
        offlineResources: List<String> = emptyList()
    ): String = generateWebManifestJson(
        rootDir = rootDir,
        outDir = outDir,
        defaultTitle = defaultTitle,
        stagedIconName = stagedIconName,
        iconPrefix = iconPrefix,
        resolvedThemeColor = resolvedThemeColor,
        offlineResources = offlineResources
    )

    internal fun generateWebManifestJson(
        rootDir: File,
        outDir: File,
        defaultTitle: String,
        stagedIconName: String?,
        iconPrefix: String = "res",
        resolvedThemeColor: String,
        offlineResources: List<String> = emptyList()
    ): String {
        val resolvedFullName = manifestName.orNull?.trim().orEmpty().ifEmpty { defaultTitle }
        val resolvedShort = manifestShortName.orNull?.trim().orEmpty().ifEmpty { resolvedFullName }
        val resolvedDesc = manifestDescription.orNull?.trim().orEmpty().ifEmpty {
            appDescription.orNull?.trim().orEmpty()
        }
        val resolvedId = manifestId.orNull?.trim().orEmpty()
        val resolvedLang = manifestLang.orNull?.trim().orEmpty().ifEmpty { "en" }
        val resolvedDir = manifestDir.orNull?.trim().orEmpty().ifEmpty { "auto" }
        val resolvedStartUrl = manifestStartUrl.orNull?.trim().orEmpty().ifEmpty { "./" }
        val resolvedScope = manifestScope.orNull?.trim().orEmpty().ifEmpty { "./" }
        val resolvedDisplay = manifestDisplay.orNull?.trim().orEmpty().ifEmpty { "standalone" }
        val resolvedOverrides = manifestDisplayOverride.getOrElse(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
        val resolvedOrientation = manifestOrientation.orNull?.trim().orEmpty().ifEmpty { "any" }
        val resolvedBgColor = manifestBackgroundColor.orNull?.trim().orEmpty().ifEmpty { "#121519" }
        val preferRelated = manifestPreferRelatedApplications.getOrElse(false)
        val resolvedCategories = manifestCategories.getOrElse(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
        val customIcons = manifestIcons.getOrElse(emptyList())
        val customShortcuts = manifestShortcuts.getOrElse(emptyList())
        val targetIconSubDir = File(outDir, iconPrefix).apply { mkdirs() }

        val effectiveIcons: List<GenericUiWebManifestIconSpec> = if (customIcons.isNotEmpty()) {
            customIcons.map { spec ->
                val localFile = File(spec.src).let { if (it.isAbsolute) it else File(rootDir, spec.src) }
                val finalSrc = if (localFile.exists() && localFile.isFile) {
                    val target = File(targetIconSubDir, localFile.name)
                    if (localFile.canonicalFile != target.canonicalFile) {
                        target.parentFile?.mkdirs()
                        localFile.copyTo(target, overwrite = true)
                    }
                    "$iconPrefix/${localFile.name}"
                } else {
                    spec.src
                }
                spec.copy(
                    src = finalSrc,
                    type = spec.type.ifBlank { inferIconMimeType(finalSrc) }
                )
            }
        } else if (stagedIconName != null) {
            val mime = inferIconMimeType(stagedIconName)
            if (mime == "image/svg+xml") {
                listOf(
                    GenericUiWebManifestIconSpec(
                        src = stagedIconName,
                        sizes = "any",
                        type = mime,
                        purpose = "any maskable"
                    ),
                    GenericUiWebManifestIconSpec(
                        src = stagedIconName,
                        sizes = "192x192 512x512",
                        type = mime,
                        purpose = "any maskable"
                    ),
                    GenericUiWebManifestIconSpec(
                        src = "$iconPrefix/icon-192.png",
                        sizes = "192x192",
                        type = "image/png",
                        purpose = "any maskable"
                    ),
                    GenericUiWebManifestIconSpec(
                        src = "$iconPrefix/icon-512.png",
                        sizes = "512x512",
                        type = "image/png",
                        purpose = "any maskable"
                    )
                )
            } else {
                listOf(
                    GenericUiWebManifestIconSpec(
                        src = stagedIconName,
                        sizes = "192x192",
                        type = mime,
                        purpose = "any maskable"
                    ),
                    GenericUiWebManifestIconSpec(
                        src = stagedIconName,
                        sizes = "512x512",
                        type = mime,
                        purpose = "any maskable"
                    )
                )
            }
        } else {
            emptyList()
        }

        val lines = mutableListOf<String>()
        if (resolvedId.isNotEmpty()) {
            lines.add("""  "id": "${escapeJson(resolvedId)}"""")
        }
        lines.add("""  "name": "${escapeJson(resolvedFullName)}"""")
        lines.add("""  "short_name": "${escapeJson(resolvedShort)}"""")
        if (resolvedDesc.isNotEmpty()) {
            lines.add("""  "description": "${escapeJson(resolvedDesc)}"""")
        }
        lines.add("""  "lang": "${escapeJson(resolvedLang)}"""")
        lines.add("""  "dir": "${escapeJson(resolvedDir)}"""")
        lines.add("""  "start_url": "${escapeJson(resolvedStartUrl)}"""")
        lines.add("""  "scope": "${escapeJson(resolvedScope)}"""")
        lines.add("""  "display": "${escapeJson(resolvedDisplay)}"""")
        if (resolvedOverrides.isNotEmpty()) {
            val ovJson = resolvedOverrides.joinToString(", ") { """"${escapeJson(it)}"""" }
            lines.add("""  "display_override": [$ovJson]""")
        }
        lines.add("""  "orientation": "${escapeJson(resolvedOrientation)}"""")
        lines.add("""  "theme_color": "${escapeJson(resolvedThemeColor)}"""")
        lines.add("""  "background_color": "${escapeJson(resolvedBgColor)}"""")
        lines.add("""  "prefer_related_applications": $preferRelated""")
        lines.add("""  "offline_enabled": true""")
        if (resolvedCategories.isNotEmpty()) {
            val catJson = resolvedCategories.joinToString(", ") { """"${escapeJson(it)}"""" }
            lines.add("""  "categories": [$catJson]""")
        }
        if (effectiveIcons.isNotEmpty()) {
            val iconsJson = effectiveIcons.joinToString(",\n") { icon ->
                buildString {
                    append("    {\n")
                    append("""      "src": "${escapeJson(icon.src)}",""").append("\n")
                    append("""      "sizes": "${escapeJson(icon.sizes.ifBlank { "any" })}",""").append("\n")
                    append("""      "type": "${escapeJson(icon.type.ifBlank { inferIconMimeType(icon.src) })}",""").append("\n")
                    append("""      "purpose": "${escapeJson(icon.purpose.ifBlank { "any maskable" })}"""").append("\n")
                    append("    }")
                }
            }
            lines.add("  \"icons\": [\n$iconsJson\n  ]")
        } else {
            lines.add("  \"icons\": []")
        }
        if (customShortcuts.isNotEmpty()) {
            val shortcutsJson = customShortcuts.joinToString(",\n") { sc ->
                buildString {
                    append("    {\n")
                    append("""      "name": "${escapeJson(sc.name)}",""").append("\n")
                    if (sc.shortName.isNotBlank()) {
                        append("""      "short_name": "${escapeJson(sc.shortName)}",""").append("\n")
                    }
                    if (sc.description.isNotBlank()) {
                        append("""      "description": "${escapeJson(sc.description)}",""").append("\n")
                    }
                    append("""      "url": "${escapeJson(sc.url.ifBlank { "./" })}"""").append("\n")
                    append("    }")
                }
            }
            lines.add("  \"shortcuts\": [\n$shortcutsJson\n  ]")
        }
        val resourcesJson = offlineResources.joinToString(",\n") { res ->
            """    "${escapeJson(res)}""""
        }
        if (resourcesJson.isNotEmpty()) {
            lines.add("  \"resources\": [\n$resourcesJson\n  ]")
        } else {
            lines.add("  \"resources\": []")
        }
        return "{\n" + lines.joinToString(",\n") + "\n}\n"
    }

    internal fun generateServiceWorkerJs(offlineResources: List<String>): String {
        val resourceItems = offlineResources.joinToString(",\n") { res ->
            """        "${escapeJson(res)}""""
        }
        return """/* GenericUI Offline Progressive Web App Service Worker (genericui-sw.js) */
(function (self) {
    "use strict";

    const CACHE_NAME = "genericui-offline-v1";
    const OFFLINE_RESOURCES = [
$resourceItems
    ];

    self.addEventListener("install", function (event) {
        event.waitUntil(
            caches.open(CACHE_NAME).then(function (cache) {
                return cache.addAll(OFFLINE_RESOURCES);
            }).then(function () {
                return self.skipWaiting();
            })
        );
    });

    self.addEventListener("activate", function (event) {
        event.waitUntil(
            caches.keys().then(function (cacheNames) {
                return Promise.all(
                    cacheNames.map(function (name) {
                        if (name !== CACHE_NAME && name.indexOf("genericui-offline-") === 0) {
                            return caches.delete(name);
                        }
                        return Promise.resolve();
                    })
                );
            }).then(function () {
                return self.clients.claim();
            })
        );
    });

    self.addEventListener("fetch", function (event) {
        if (!event.request || event.request.method !== "GET") {
            return;
        }
        event.respondWith(
            caches.match(event.request, { ignoreSearch: true }).then(function (cachedResponse) {
                if (cachedResponse) {
                    return cachedResponse;
                }
                return fetch(event.request).then(function (networkResponse) {
                    if (networkResponse && networkResponse.ok && networkResponse.type !== "opaque") {
                        var responseClone = networkResponse.clone();
                        caches.open(CACHE_NAME).then(function (cache) {
                            cache.put(event.request, responseClone);
                        });
                    }
                    return networkResponse;
                }).catch(function () {
                    if (event.request.mode === "navigate") {
                        return caches.match("index.html", { ignoreSearch: true }) ||
                            caches.match("./", { ignoreSearch: true });
                    }
                    return Response.error();
                });
            })
        );
    });
})(typeof self !== "undefined" ? self : this);
"""
    }

    private fun escapeJson(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        const val INDEX_HTML_TEMPLATE: String = """<!DOCTYPE html>
<html lang="{{LANG}}" xmlns:genericui="https://nexustools.com/ns/genericui" data-bs-theme="{{THEME_MODE}}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{TITLE}}</title>
    <link id="genericui-namespace-spec" rel="namespace" data-namespace-prefix="genericui" data-namespace-uri="https://nexustools.com/ns/genericui" type="application/xml" href="{{NAMESPACE_XML_URL}}?{{NAMESPACE_XML_HASH}}">
    <link id="genericui-resources-manifest" rel="resources" type="application/json" href="{{RESOURCES_JSON_URL}}?{{RESOURCES_JSON_HASH}}">
    <link id="genericui-css" rel="stylesheet" href="{{GENERICUI_CSS_URL}}?{{GENERICUI_CSS_HASH}}">
{{FAVICON_LINK}}
{{WEB_MANIFEST_AND_PWA_LINKS}}
    <link id="genericui-bootstrap-css" rel="stylesheet" href="{{BOOTSTRAP_CSS_URL}}" integrity="{{BOOTSTRAP_CSS_INTEGRITY}}" crossorigin="{{CROSSORIGIN}}">
{{BOOTSTRAP_ICONS_LINK}}
{{EXTRA_STYLESHEETS}}
</head>
<body class="bg-body text-body m-0 p-0">
    <div id="genericui-splash" class="genericui-splash position-fixed top-0 start-0 w-100 h-100 d-flex flex-column align-items-center justify-content-center bg-body text-body p-4">
        <div class="genericui-splash-content d-flex flex-column align-items-center justify-content-center w-100">
{{SPLASH_ICON}}
            <div id="genericui-splash-title" class="fw-semibold fs-5 mb-2 text-center">{{TITLE}}</div>
            <div id="genericui-splash-status" class="genericui-splash-status text-body-secondary small mb-3 text-center" data-status="initializing"><span id="genericui-splash-status-icon" class="genericui-splash-status-icon spinner-border spinner-border-sm" aria-hidden="true"></span><span id="genericui-splash-status-text" class="genericui-splash-status-text">{{SPLASH_STATUS}}</span></div>
            <div id="genericui-splash-progress" class="genericui-splash-progress progress w-100 mb-2" role="progressbar" aria-label="Application loading progress" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0">
                <div id="genericui-splash-progress-bar" class="{{SPLASH_PROGRESS_BAR_CLASS}}" aria-valuenow="0"></div>
            </div>
            <div id="genericui-splash-error" class="genericui-splash-error alert alert-danger d-none text-start w-100 mt-3 mb-0 shadow-sm" role="alert">
                <div class="d-flex align-items-center mb-2">
                    <i class="bi bi-exclamation-triangle-fill me-2" aria-hidden="true"></i>
                    <strong id="genericui-splash-error-title">Error</strong>
                </div>
                <div id="genericui-splash-error-message" class="genericui-splash-error-message small fw-semibold mb-2"></div>
                <pre id="genericui-splash-error-stack" class="genericui-splash-error-stack small mb-0 p-2 bg-dark text-light rounded overflow-auto"></pre>
            </div>
        </div>
    </div>
    <genericui:app></genericui:app>
    <script id="genericui-bootstrap-js" src="{{BOOTSTRAP_JS_URL}}" integrity="{{BOOTSTRAP_JS_INTEGRITY}}" crossorigin="{{CROSSORIGIN}}" defer></script>
{{EXTRA_SCRIPTS}}
    <script type="loader-wasm" src="{{WASM_URL}}?{{WASM_HASH}}"{{WASM_BINARY_ATTR}}></script>
    <script type="loader-js" src="{{JS_URL}}?{{JS_HASH}}"></script>
    <script id="genericui-loader-script" src="{{LOADER_JS_URL}}?{{LOADER_JS_HASH}}" defer></script>
</body>
</html>"""

        const val DEFAULT_INDEX_TEMPLATE: String = INDEX_HTML_TEMPLATE

        const val GENERICUI_CSS_SOURCE: String = """/* GenericUI Default Namespace & Layout Stylesheet (genericui.css) */
html,
body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    overflow: hidden;
}

genericui\:app {
    display: block;
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    overflow: hidden;
    box-sizing: border-box;
}

.genericui-window,
genericui\:window {
    display: flex;
    flex-direction: column;
    position: relative;
    width: 100%;
    height: 100%;
    overflow: hidden;
    box-sizing: border-box;
}

.genericui-window-content-slot {
    position: relative;
    flex: 1 1 auto;
    width: 100%;
    height: 100%;
    overflow: hidden;
    box-sizing: border-box;
}

genericui\:fragment,
genericui\:fragment-container {
    display: block;
    position: relative;
    width: 100%;
    height: 100%;
    box-sizing: border-box;
}

/* Scrolling in a GenericUI web app requires an explicit ScrollView for cross-target uniformity */
genericui\:scrollview,
genericui\:stepscrollview,
genericui\:fragment.scroll-fragment,
genericui\:app.scroll-fragment {
    display: block;
    overflow: auto;
    box-sizing: border-box;
}

/* Top-level MenuBar & Bootstrap Dropdown Menus inside genericui:app */
genericui\:app > genericui\:menubar,
genericui\:menubar {
    display: flex;
    flex-direction: row;
    align-items: center;
    flex-wrap: nowrap;
    position: relative;
    top: 0;
    left: 0;
    width: 100%;
    height: 2.25rem;
    min-height: 2.25rem;
    padding: 0 0.35rem;
    margin: 0;
    z-index: 1050;
    overflow: visible;
    background-color: var(--bs-tertiary-bg, #212529);
    border-bottom: 1px solid var(--bs-border-color, rgba(255, 255, 255, 0.15));
    user-select: none;
    box-sizing: border-box;
}

genericui\:menubar ~ .genericui-window,
genericui\:menubar ~ genericui\:window {
    height: calc(100% - 2.25rem) !important;
}

genericui\:menu {
    display: inline-block;
    position: relative;
    box-sizing: border-box;
}

genericui\:menu > .genericui-menu-toggle {
    display: inline-flex;
    align-items: center;
    padding: 0.25rem 0.65rem;
    font-size: 0.875rem;
    line-height: 1.25;
    border-radius: 0.25rem;
    color: var(--bs-body-color, inherit);
    background: transparent;
    border: none;
    text-decoration: none;
}

genericui\:menu > .genericui-menu-toggle:hover,
genericui\:menu.show > .genericui-menu-toggle,
genericui\:menu > .genericui-menu-toggle.show {
    background-color: var(--bs-secondary-bg, rgba(255, 255, 255, 0.1));
}

genericui\:menu > .dropdown-menu {
    position: absolute;
    top: 100%;
    left: 0;
    z-index: 1060;
    min-width: 11rem;
    padding: 0.35rem 0;
    margin: 0;
}

genericui\:menu.dropend > .dropdown-menu,
genericui\:menu.genericui-submenu > .dropdown-menu {
    top: 0;
    left: 100%;
}

genericui\:menuitem,
genericui\:checkmenuitem,
genericui\:radiomenuitem {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1.25rem;
    padding: 0.35rem 0.85rem;
    font-size: 0.875rem;
    cursor: pointer;
    user-select: none;
    white-space: nowrap;
    box-sizing: border-box;
}

genericui\:checkmenuitem::before,
genericui\:radiomenuitem::before {
    content: "";
    display: inline-block;
    width: 1rem;
    margin-right: 0.35rem;
    text-align: center;
    font-weight: 700;
}

genericui\:checkmenuitem[aria-checked="true"]::before {
    content: "\2713";
}

genericui\:radiomenuitem[aria-checked="true"]::before {
    content: "\25CF";
}

genericui\:menuitem[data-shortcut]::after,
genericui\:checkmenuitem[data-shortcut]::after,
genericui\:radiomenuitem[data-shortcut]::after {
    content: attr(data-shortcut);
    margin-left: auto;
    padding-left: 1.5rem;
    font-size: 0.78em;
    opacity: 0.65;
}

hr.dropdown-divider,
genericui\:menuseparator {
    display: block;
    height: 0;
    margin: 0.35rem 0;
    overflow: hidden;
    border-top: 1px solid var(--bs-border-color, rgba(255, 255, 255, 0.15));
    opacity: 1;
}

genericui\:contextmenu.dropdown-menu {
    min-width: 11rem;
    padding: 0.35rem 0;
    margin: 0;
    z-index: 2050;
}

[data-has-context-menu="true"] {
    -webkit-touch-callout: none;
}

/* Block GenericUI Custom Elements */
genericui\:component,
genericui\:container,
genericui\:box,
genericui\:col,
genericui\:aspectratiocontainer,
genericui\:lazylist,
genericui\:lazycolumn,
genericui\:lazygrid,
genericui\:lazygridtree,
genericui\:treeview,
genericui\:collapsiblepanel,
genericui\:accordion,
genericui\:accordionitem,
genericui\:jumbotron,
genericui\:well,
genericui\:listgroupitem,
genericui\:tabcontainer,
genericui\:formgroup,
genericui\:radiogroup,
genericui\:markdownview,
genericui\:alert,
genericui\:toast,
genericui\:skeletonplaceholder,
genericui\:modal,
genericui\:popover,
genericui\:backdrop,
genericui\:dialogwindow,
genericui\:dockpanel,
genericui\:panelwindow,
genericui\:categorizedtreeview,
genericui\:menuseparator,
genericui\:contextmenu,
genericui\:mdidesktop {
    display: block;
    box-sizing: border-box;
}

/* Flex GenericUI Custom Elements */
genericui\:splitpane,
genericui\:titlebar,
genericui\:statusbar,
genericui\:mdicontainer,
genericui\:mdiwindow,
genericui\:dockworkspace,
genericui\:dockarea,
genericui\:dockgroup,
genericui\:row,
genericui\:flexbox,
genericui\:hstack,
genericui\:vstack,
genericui\:lazyrow,
genericui\:card,
genericui\:listgroup,
genericui\:navbar,
genericui\:nav,
genericui\:sidenav,
genericui\:bottomnavigationbar,
genericui\:breadcrumb,
genericui\:pagination,
genericui\:stepper,
genericui\:toolbar,
genericui\:inputgroup,
genericui\:compoundunitinput,
genericui\:filedropzone,
genericui\:masterdetailstepselect,
genericui\:horizontalcarouselselector,
genericui\:banner,
genericui\:toastcontainer,
genericui\:progressbar,
genericui\:drawer,
genericui\:bottomsheet {
    display: flex;
    box-sizing: border-box;
}

/* Inline-Flex GenericUI Custom Elements */
genericui\:toolpaletteitem,
genericui\:colorswatchbox,
genericui\:breadcrumbitem,
genericui\:buttongroup,
genericui\:avatar {
    display: inline-flex;
    box-sizing: border-box;
}

/* Inline-Block GenericUI Custom Elements */
genericui\:tab,
genericui\:closeabletab,
genericui\:pinnedtab,
genericui\:badgetab,
genericui\:editabletab,
genericui\:dropdown,
genericui\:spinner,
genericui\:tooltip {
    display: inline-block;
    box-sizing: border-box;
}

/* Grid GenericUI Custom Elements */
genericui\:toolpalette {
    display: grid;
    box-sizing: border-box;
}

/* Splash Screen (#genericui-splash) */
#genericui-splash,
.genericui-splash {
    z-index: 2000;
    transition: opacity 0.35s ease, visibility 0.35s ease;
}

#genericui-splash.genericui-splash-hidden,
.genericui-splash.genericui-splash-hidden {
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
}

.genericui-splash-content {
    max-width: 420px;
}

.genericui-splash-icon {
    object-fit: contain;
}

#genericui-splash-status,
.genericui-splash-status {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
}

.genericui-splash-status-icon {
    flex: 0 0 auto;
}

.genericui-splash-status-icon.bi {
    font-size: 1rem;
    line-height: 1;
}

#genericui-splash-progress,
.genericui-splash-progress {
    height: 8px;
}

#genericui-splash-progress-bar {
    width: 0%;
}

#genericui-splash-error-stack,
.genericui-splash-error-stack {
    max-height: 240px;
    white-space: pre-wrap;
    word-break: break-word;
}

/* Splash Error Alert & Stacktrace <pre> Text Selection */
#genericui-splash-error,
#genericui-splash-error *,
.genericui-splash-error,
.genericui-splash-error * {
    -webkit-user-select: text !important;
    -moz-user-select: text !important;
    user-select: text !important;
    cursor: text;
}

/* ProgressBar Busy Mode Bouncing Bootstrap Animation */
genericui\:progressbar.progress.genericui-progress-busy,
.progress.genericui-progress-busy {
    position: relative;
    overflow: hidden;
}

genericui\:progressbar.progress.genericui-progress-busy > .progress-bar.genericui-progress-bar-busy,
.progress.genericui-progress-busy > .progress-bar.genericui-progress-bar-busy {
    width: 30% !important;
    border-radius: inherit;
    transform-origin: 0% 50%;
    will-change: transform;
    animation: genericui-progress-busy-bounce 1.35s cubic-bezier(0.45, 0.05, 0.55, 0.95) infinite alternate !important;
}

@keyframes genericui-progress-busy-bounce {
    0% {
        transform: translateX(0%) scaleX(0.86);
    }
    8% {
        transform: translateX(0%) scaleX(1);
    }
    50% {
        transform: translateX(113.3333%) scaleX(1.06);
    }
    92% {
        transform: translateX(233.3333%) scaleX(1);
    }
    100% {
        transform: translateX(247.3333%) scaleX(0.86);
    }
}"""

        const val NAMESPACE_CSS_SOURCE: String = """genericui\:app, genericui\:component, genericui\:fragment, genericui\:fragment-container, genericui\:container, genericui\:box, genericui\:col, genericui\:scrollview, genericui\:stepscrollview, genericui\:aspectratiocontainer, genericui\:lazylist, genericui\:lazycolumn, genericui\:lazygrid, genericui\:lazygridtree, genericui\:treeview, genericui\:collapsiblepanel, genericui\:accordion, genericui\:accordionitem, genericui\:jumbotron, genericui\:well, genericui\:listgroupitem, genericui\:tabcontainer, genericui\:formgroup, genericui\:radiogroup, genericui\:markdownview, genericui\:alert, genericui\:toast, genericui\:skeletonplaceholder, genericui\:modal, genericui\:popover, genericui\:backdrop, genericui\:dialogwindow, genericui\:dockpanel, genericui\:panelwindow, genericui\:categorizedtreeview, genericui\:menuitem, genericui\:checkmenuitem, genericui\:radiomenuitem, genericui\:menuseparator, genericui\:contextmenu, genericui\:mdidesktop { display: block; box-sizing: border-box; }
genericui\:window, genericui\:menubar, genericui\:splitpane, genericui\:titlebar, genericui\:statusbar, genericui\:mdicontainer, genericui\:mdiwindow, genericui\:dockworkspace, genericui\:dockarea, genericui\:dockgroup, genericui\:row, genericui\:flexbox, genericui\:hstack, genericui\:vstack, genericui\:lazyrow, genericui\:card, genericui\:listgroup, genericui\:navbar, genericui\:nav, genericui\:sidenav, genericui\:bottomnavigationbar, genericui\:breadcrumb, genericui\:pagination, genericui\:stepper, genericui\:toolbar, genericui\:inputgroup, genericui\:compoundunitinput, genericui\:filedropzone, genericui\:masterdetailstepselect, genericui\:horizontalcarouselselector, genericui\:banner, genericui\:toastcontainer, genericui\:progressbar, genericui\:drawer, genericui\:bottomsheet { display: flex; box-sizing: border-box; }
genericui\:toolpaletteitem, genericui\:colorswatchbox, genericui\:breadcrumbitem, genericui\:buttongroup, genericui\:avatar { display: inline-flex; box-sizing: border-box; }
genericui\:menu, genericui\:tab, genericui\:closeabletab, genericui\:pinnedtab, genericui\:badgetab, genericui\:editabletab, genericui\:dropdown, genericui\:spinner, genericui\:tooltip { display: inline-block; box-sizing: border-box; }
genericui\:toolpalette { display: grid; box-sizing: border-box; }"""

        const val NAMESPACE_XML_SOURCE: String = """<?xml version="1.0" encoding="UTF-8"?>
<namespaceSpec xmlns="https://nexustools.com/ns/genericui/spec" prefix="genericui" uri="https://nexustools.com/ns/genericui" version="1.0">
    <commonAttributes>
        <attribute name="id" type="string" description="Unique DOM element identifier."/>
        <attribute name="class" type="string" description="Space-separated Bootstrap 5 and custom CSS class list."/>
        <attribute name="style" type="string" description="Inline CSS property declarations."/>
        <attribute name="title" type="string" description="Tooltip text displayed on hover."/>
        <attribute name="role" type="string" description="WAI-ARIA semantic role."/>
        <attribute name="data-bs-theme" type="string" description="Bootstrap color mode (dark or light)."/>
    </commonAttributes>
    <elements>
        <element name="app" tag="genericui:app" componentClass="GenericUiApplication" category="core" display="block" role="application" description="Root GenericUI application viewport container."/>
        <element name="component" tag="genericui:component" componentClass="AbstractComponent" category="core" display="block" role="generic" description="Base GenericUI component element."/>
        <element name="fragment" tag="genericui:fragment" componentClass="Fragment" category="core" display="block" role="region" description="Modular lifecycle-aware UI fragment container."/>
        <element name="fragment-container" tag="genericui:fragment-container" componentClass="FragmentContainer" category="core" display="block" role="region" description="Host container mounting and transitioning Fragment instances."/>
        <element name="window" tag="genericui:window" componentClass="Window" category="shell" display="flex" role="region" description="Top-level GenericUI window container inside genericui:app."/>
        <element name="menubar" tag="genericui:menubar" componentClass="MenuBar" category="shell" display="flex" role="menubar" description="Top-level horizontal application menu bar."/>
        <element name="menu" tag="genericui:menu" componentClass="Menu" category="shell" display="inline-block" role="menu" description="Pull-down or cascading submenu container."/>
        <element name="menuitem" tag="genericui:menuitem" componentClass="MenuItem" category="shell" display="block" role="menuitem" description="Executable action item inside a menu or context menu."/>
        <element name="checkmenuitem" tag="genericui:checkmenuitem" componentClass="CheckMenuItem" category="shell" display="block" role="menuitemcheckbox" description="Checkable toggle menu item."/>
        <element name="radiomenuitem" tag="genericui:radiomenuitem" componentClass="RadioMenuItem" category="shell" display="block" role="menuitemradio" description="Mutually exclusive radio menu item."/>
        <element name="menuseparator" tag="genericui:menuseparator" componentClass="MenuSeparator" category="shell" display="block" role="separator" description="Visual horizontal divider between menu items."/>
        <element name="contextmenu" tag="genericui:contextmenu" componentClass="ContextMenu" category="shell" display="block" role="menu" description="Floating popup context menu anchored at pointer coordinates."/>
        <element name="splitpane" tag="genericui:splitpane" componentClass="SplitPane" category="shell" display="flex" role="group" description="Resizable two-panel horizontal or vertical split container."/>
        <element name="titlebar" tag="genericui:titlebar" componentClass="TitleBar" category="shell" display="flex" role="banner" description="Custom window title bar with window controls and title."/>
        <element name="statusbar" tag="genericui:statusbar" componentClass="StatusBar" category="shell" display="flex" role="status" description="Bottom application status bar displaying status segments and indicators."/>
        <element name="mdicontainer" tag="genericui:mdicontainer" componentClass="MdiContainer" category="shell" display="flex" role="region" description="Multi-Document Interface (MDI) workspace container."/>
        <element name="mdidesktop" tag="genericui:mdidesktop" componentClass="MdiContainer.DesktopSurface" category="shell" display="block" role="group" description="MDI desktop surface hosting internal MDI child windows."/>
        <element name="mdiwindow" tag="genericui:mdiwindow" componentClass="MdiWindow" category="shell" display="flex" role="dialog" description="Movable, resizable, minimizable, and maximizable internal MDI child window."/>
        <element name="dockworkspace" tag="genericui:dockworkspace" componentClass="DockWorkspace" category="dock" display="flex" role="main" description="Root multi-area dockable studio workspace."/>
        <element name="dockarea" tag="genericui:dockarea" componentClass="DockArea" category="dock" display="flex" role="region" description="Left, right, top, or bottom dockable panel area."/>
        <element name="dockgroup" tag="genericui:dockgroup" componentClass="DockGroup" category="dock" display="flex" role="tablist" description="Tabbed or split stack of dockable panels within a dock area."/>
        <element name="dockpanel" tag="genericui:dockpanel" componentClass="DockPanel" category="dock" display="block" role="tabpanel" description="Individual dockable studio tool or inspector panel."/>
        <element name="panelwindow" tag="genericui:panelwindow" componentClass="PanelWindow" category="dock" display="block" role="dialog" description="Floating utility panel window detached from a dock area."/>
        <element name="toolpalette" tag="genericui:toolpalette" componentClass="ToolPalette" category="dock" display="grid" role="toolbar" description="Multi-column icon tool palette with foreground/background color swatches."/>
        <element name="toolpaletteitem" tag="genericui:toolpaletteitem" componentClass="ToolPaletteItem" category="dock" display="inline-flex" role="button" description="Selectable tool button inside a tool palette."/>
        <element name="categorizedtreeview" tag="genericui:categorizedtreeview" componentClass="CategorizedTreeView" category="dock" display="block" role="tree" description="Categorized hierarchy tree view for studio presets and assets."/>
        <element name="colorswatchbox" tag="genericui:colorswatchbox" componentClass="ColorSwatchBox" category="dock" display="inline-flex" role="group" description="Foreground and background color swatch selector box."/>
        <element name="container" tag="genericui:container" componentClass="Container" category="layout" display="block" role="region" description="Responsive layout container."/>
        <element name="box" tag="genericui:box" componentClass="Box" category="layout" display="block" role="group" description="Single-purpose positioning and alignment box container."/>
        <element name="row" tag="genericui:row" componentClass="Row" category="layout" display="flex" role="group" description="12-column responsive Bootstrap grid row."/>
        <element name="col" tag="genericui:col" componentClass="Col" category="layout" display="block" role="group" description="Responsive grid column within a genericui:row."/>
        <element name="flexbox" tag="genericui:flexbox" componentClass="FlexBox" category="layout" display="flex" role="group" description="Configurable CSS Flexbox layout container."/>
        <element name="hstack" tag="genericui:hstack" componentClass="HStack" category="layout" display="flex" role="group" description="Horizontal flex stack layout container."/>
        <element name="vstack" tag="genericui:vstack" componentClass="VStack" category="layout" display="flex" role="group" description="Vertical flex stack layout container."/>
        <element name="scrollview" tag="genericui:scrollview" componentClass="ScrollView" category="layout" display="block" role="region" description="Scrollable viewport container."/>
        <element name="stepscrollview" tag="genericui:stepscrollview" componentClass="StepScrollView" category="layout" display="block" role="region" description="Discrete step-by-step scrollable container."/>
        <element name="aspectratiocontainer" tag="genericui:aspectratiocontainer" componentClass="AspectRatioContainer" category="layout" display="block" role="group" description="Bootstrap aspect-ratio layout container (.ratio)."/>
        <element name="lazylist" tag="genericui:lazylist" componentClass="LazyList" category="lazy" display="block" role="list" description="Virtualized scrolling list mounting only visible item DOM nodes."/>
        <element name="lazycolumn" tag="genericui:lazycolumn" componentClass="LazyColumn" category="lazy" display="block" role="list" description="Vertical virtualized scrolling list."/>
        <element name="lazyrow" tag="genericui:lazyrow" componentClass="LazyRow" category="lazy" display="flex" role="list" description="Horizontal virtualized scrolling list."/>
        <element name="lazygrid" tag="genericui:lazygrid" componentClass="LazyGrid" category="lazy" display="block" role="grid" description="Multi-column virtualized scrolling grid."/>
        <element name="lazygridtree" tag="genericui:lazygridtree" componentClass="LazyGridTree" category="lazy" display="block" role="treegrid" description="Virtualized multi-column collapsible tree-grid supporting text and component cells."/>
        <element name="treeview" tag="genericui:treeview" componentClass="TreeView" category="lazy" display="block" role="tree" description="Virtualized hierarchical tree view with expandable nodes."/>
        <element name="card" tag="genericui:card" componentClass="Card" category="surface" display="flex" role="region" description="Elevated surface card with optional header, body, and footer."/>
        <element name="collapsiblepanel" tag="genericui:collapsiblepanel" componentClass="CollapsiblePanel" category="surface" display="block" role="region" description="Expandable and collapsible content panel."/>
        <element name="accordion" tag="genericui:accordion" componentClass="Accordion" category="surface" display="block" role="region" description="Vertically stacked group of collapsible accordion items."/>
        <element name="accordionitem" tag="genericui:accordionitem" componentClass="AccordionItem" category="surface" display="block" role="region" description="Individual collapsible section within an accordion."/>
        <element name="jumbotron" tag="genericui:jumbotron" componentClass="Jumbotron" category="surface" display="block" role="region" description="Hero showcase surface container."/>
        <element name="well" tag="genericui:well" componentClass="Well" category="surface" display="block" role="region" description="Inset bordered surface container."/>
        <element name="listgroup" tag="genericui:listgroup" componentClass="ListGroup" category="surface" display="flex" role="list" description="Bordered vertical list group of interactive or static items."/>
        <element name="listgroupitem" tag="genericui:listgroupitem" componentClass="ListGroupItem" category="surface" display="block" role="listitem" description="Individual item within a genericui:listgroup."/>
        <element name="tabcontainer" tag="genericui:tabcontainer" componentClass="TabContainer" category="tabs" display="block" role="region" description="Multi-tab container with a navigation tab strip and tab content panes."/>
        <element name="tab" tag="genericui:tab" componentClass="Tab" category="tabs" display="inline-block" role="presentation" description="Standard tab header item within a genericui:tabcontainer."/>
        <element name="closeabletab" tag="genericui:closeabletab" componentClass="CloseableTab" category="tabs" display="inline-block" role="presentation" description="Tab header item with an interactive close button."/>
        <element name="pinnedtab" tag="genericui:pinnedtab" componentClass="PinnedTab" category="tabs" display="inline-block" role="presentation" description="Non-closeable pinned tab header item."/>
        <element name="badgetab" tag="genericui:badgetab" componentClass="BadgeTab" category="tabs" display="inline-block" role="presentation" description="Tab header item with an attached status/counter badge."/>
        <element name="editabletab" tag="genericui:editabletab" componentClass="EditableTab" category="tabs" display="inline-block" role="presentation" description="Tab header item supporting inline title renaming."/>
        <element name="navbar" tag="genericui:navbar" componentClass="Navbar" category="navigation" display="flex" role="navigation" description="Responsive top navigation bar with brand title and navigation links."/>
        <element name="nav" tag="genericui:nav" componentClass="Nav" category="navigation" display="flex" role="navigation" description="Horizontal or vertical navigation link group."/>
        <element name="sidenav" tag="genericui:sidenav" componentClass="SideNav" category="navigation" display="flex" role="navigation" description="Vertical sidebar navigation panel."/>
        <element name="bottomnavigationbar" tag="genericui:bottomnavigationbar" componentClass="BottomNavigationBar" category="navigation" display="flex" role="navigation" description="Mobile/compact bottom navigation bar with icon/label destinations."/>
        <element name="breadcrumb" tag="genericui:breadcrumb" componentClass="Breadcrumb" category="navigation" display="flex" role="navigation" description="Hierarchical page location breadcrumb trail."/>
        <element name="breadcrumbitem" tag="genericui:breadcrumbitem" componentClass="BreadcrumbItem" category="navigation" display="inline-flex" role="listitem" description="Individual step link inside a genericui:breadcrumb."/>
        <element name="pagination" tag="genericui:pagination" componentClass="Pagination" category="navigation" display="flex" role="navigation" description="Page number navigation bar."/>
        <element name="stepper" tag="genericui:stepper" componentClass="Stepper" category="navigation" display="flex" role="group" description="Multi-step wizard progress and navigation indicator."/>
        <element name="dropdown" tag="genericui:dropdown" componentClass="DropdownButton" category="action" display="inline-block" role="group" description="Button that toggles a floating action selection menu."/>
        <element name="buttongroup" tag="genericui:buttongroup" componentClass="ButtonGroup" category="action" display="inline-flex" role="group" description="Segmented horizontal or vertical group of buttons."/>
        <element name="toolbar" tag="genericui:toolbar" componentClass="Toolbar" category="action" display="flex" role="toolbar" description="Action toolbar grouping multiple button groups and controls."/>
        <element name="formgroup" tag="genericui:formgroup" componentClass="FormGroup" category="form" display="block" role="group" description="Labeled form field group with validation feedback and help text."/>
        <element name="inputgroup" tag="genericui:inputgroup" componentClass="InputGroup" category="form" display="flex" role="group" description="Composite input control with attached prefix/suffix addons or buttons."/>
        <element name="radiogroup" tag="genericui:radiogroup" componentClass="RadioGroup" category="form" display="block" role="radiogroup" description="Mutually exclusive group of radio button options."/>
        <element name="compoundunitinput" tag="genericui:compoundunitinput" componentClass="CompoundUnitInput" category="form" display="flex" role="group" description="Numeric input paired with a unit selector (e.g., px, %, em, deg)."/>
        <element name="filedropzone" tag="genericui:filedropzone" componentClass="FileDropZone" category="form" display="flex" role="region" description="Drag-and-drop file upload target zone."/>
        <element name="markdownview" tag="genericui:markdownview" componentClass="MarkdownView" category="typography" display="block" role="document" description="Formatted Markdown document viewer."/>
        <element name="avatar" tag="genericui:avatar" componentClass="Avatar" category="content" display="inline-flex" role="img" description="Circular or rounded user/entity avatar with image or initials fallback."/>
        <element name="masterdetailstepselect" tag="genericui:masterdetailstepselect" componentClass="MasterDetailStepSelect" category="content" display="flex" role="region" description="Two-pane master category and detail item selector."/>
        <element name="horizontalcarouselselector" tag="genericui:horizontalcarouselselector" componentClass="HorizontalCarouselSelector" category="content" display="flex" role="region" description="Horizontal card carousel with previous/next selection controls."/>
        <element name="alert" tag="genericui:alert" componentClass="Alert" category="feedback" display="block" role="alert" description="Contextual feedback alert banner."/>
        <element name="banner" tag="genericui:banner" componentClass="Banner" category="feedback" display="flex" role="banner" description="Full-width top announcement or warning banner."/>
        <element name="toast" tag="genericui:toast" componentClass="Toast" category="feedback" display="block" role="status" description="Transient non-modal notification toast."/>
        <element name="toastcontainer" tag="genericui:toastcontainer" componentClass="ToastContainer" category="feedback" display="flex" role="region" description="Viewport-anchored stack container for transient toasts."/>
        <element name="progressbar" tag="genericui:progressbar" componentClass="ProgressBar" category="feedback" display="flex" role="progressbar" description="Determinate or indeterminate horizontal progress bar."/>
        <element name="spinner" tag="genericui:spinner" componentClass="Spinner" category="feedback" display="inline-block" role="status" description="Animated border or grow loading spinner."/>
        <element name="skeletonplaceholder" tag="genericui:skeletonplaceholder" componentClass="SkeletonPlaceholder" category="feedback" display="block" role="status" description="Shimmering content placeholder shown while data is loading."/>
        <element name="modal" tag="genericui:modal" componentClass="Modal" category="overlay" display="block" role="dialog" description="Modal overlay container with header, body, and action footer."/>
        <element name="drawer" tag="genericui:drawer" componentClass="Drawer" category="overlay" display="flex" role="dialog" description="Slide-out offcanvas edge drawer."/>
        <element name="bottomsheet" tag="genericui:bottomsheet" componentClass="BottomSheet" category="overlay" display="flex" role="dialog" description="Bottom-anchored slide-up sheet overlay."/>
        <element name="popover" tag="genericui:popover" componentClass="Popover" category="overlay" display="block" role="tooltip" description="Anchored floating popover card."/>
        <element name="tooltip" tag="genericui:tooltip" componentClass="Tooltip" category="overlay" display="inline-block" role="tooltip" description="Floating text tooltip anchored to a target element."/>
        <element name="backdrop" tag="genericui:backdrop" componentClass="Backdrop" category="overlay" display="block" role="presentation" description="Dimmed modal or drawer background scrim."/>
        <element name="dialogwindow" tag="genericui:dialogwindow" componentClass="DialogWindow" category="dialog" display="block" role="dialog" description="Modal or non-modal dialog window."/>
    </elements>
</namespaceSpec>"""

        val LOADER_JS_SOURCE: String = """
(function (global) {
    "use strict";

    if (global.__GENERICUI_LOADER_ACTIVE__) {
        return;
    }
    global.__GENERICUI_LOADER_ACTIVE__ = true;

    const setTheme = (theme) => {
        document.documentElement.setAttribute('data-bs-theme', theme);
    };
    global.setTheme = setTheme;

    function applyInitialBrowserTheme() {
        var doc = global.document;
        if (!doc || !doc.documentElement) {
            return;
        }
        var currentTheme = (doc.documentElement.getAttribute("data-bs-theme") || "auto").toLowerCase();
        if (currentTheme === "auto") {
            const isDarkMode = typeof global.matchMedia === "function"
                ? global.matchMedia('(prefers-color-scheme: dark)').matches
                : true;
            if (global.console && typeof global.console.log === "function") {
                global.console.log(isDarkMode ? 'Dark mode' : 'Light mode');
            }
            setTheme(isDarkMode ? 'dark' : 'light');
        }
    }
    applyInitialBrowserTheme();

    var STATUS_INITIALIZING = "Initializing";
    var STATUS_LOADING_RESOURCES = "Loading Resources";
    var STATUS_LOADING_APPLICATION = "Loading Application";
    var STATUS_LOADING_FALLBACK = "Loading Fallback";
    var STATUS_ERROR = "Error";
    var STATUS_READY = "Ready";

    var ALLOWED_STATUS_MAP = {
        "Initializing": true,
        "Loading Resources": true,
        "Loading Application": true,
        "Loading Fallback": true,
        "Error": true,
        "Ready": true
    };

    if (!global.__GENERICUI_PRELOADED_RESOURCES__) {
        global.__GENERICUI_PRELOADED_RESOURCES__ = {};
    }

    function normalizeStatusMessage(statusText) {
        if (!statusText || typeof statusText !== "string") {
            return STATUS_INITIALIZING;
        }
        var trimmed = statusText.replace(/^\s+|\s+$/g, "");
        if (ALLOWED_STATUS_MAP[trimmed]) {
            return trimmed;
        }
        var lower = trimmed.toLowerCase();
        if (lower.indexOf("fallback") !== -1) {
            return STATUS_LOADING_FALLBACK;
        }
        if (lower.indexOf("error") !== -1 || lower.indexOf("fail") !== -1 || lower.indexOf("exception") !== -1) {
            return STATUS_ERROR;
        }
        if (lower.indexOf("ready") !== -1 || lower.indexOf("complete") !== -1 || lower.indexOf("finish") !== -1) {
            return STATUS_READY;
        }
        if (lower.indexOf("resource") !== -1 || lower.indexOf("preload") !== -1 || lower.indexOf("asset") !== -1) {
            return STATUS_LOADING_RESOURCES;
        }
        if (lower.indexOf("application") !== -1 || lower.indexOf("wasm") !== -1 || lower.indexOf("bundle") !== -1 || lower.indexOf("download") !== -1) {
            return STATUS_LOADING_APPLICATION;
        }
        return STATUS_INITIALIZING;
    }

    function getDomElements() {
        var doc = global.document;
        if (!doc) {
            return {};
        }
        var splash = doc.getElementById("genericui-splash") || doc.querySelector(".genericui-splash");
        var progressContainer = doc.getElementById("genericui-splash-progress") ||
            (splash ? splash.querySelector(".progress") : null);
        var progressBar = doc.getElementById("genericui-splash-progress-bar") ||
            (progressContainer ? progressContainer.querySelector(".progress-bar") : null);
        var statusLabel = doc.getElementById("genericui-splash-status") ||
            (splash ? splash.querySelector(".genericui-splash-status") : null);
        var errorBox = doc.getElementById("genericui-splash-error") ||
            (splash ? splash.querySelector(".genericui-splash-error") : null);
        var errorTitle = doc.getElementById("genericui-splash-error-title");
        var errorMessage = doc.getElementById("genericui-splash-error-message") ||
            (errorBox ? errorBox.querySelector(".genericui-splash-error-message") : null);
        var errorStack = doc.getElementById("genericui-splash-error-stack") ||
            (errorBox ? errorBox.querySelector("pre") : null);
        var loaderScript = doc.getElementById("genericui-loader-script") || doc.currentScript;
        var wasmDescriptor = doc.querySelector('script[type="loader-wasm"]');
        var jsDescriptor = doc.querySelector('script[type="loader-js"]');
        var resourcesLink = doc.querySelector('link[rel="resources"]') ||
            doc.getElementById("genericui-resources-manifest");
        var manifestScript = doc.getElementById("genericui-resource-manifest") ||
            doc.querySelector('script[type="genericui-resource-manifest"]');

        return {
            doc: doc,
            splash: splash,
            progressContainer: progressContainer,
            progressBar: progressBar,
            statusLabel: statusLabel,
            errorBox: errorBox,
            errorTitle: errorTitle,
            errorMessage: errorMessage,
            errorStack: errorStack,
            loaderScript: loaderScript,
            wasmDescriptor: wasmDescriptor,
            jsDescriptor: jsDescriptor,
            resourcesLink: resourcesLink,
            manifestScript: manifestScript
        };
    }

    var state = {
        hasError: false,
        fadedOut: false,
        currentProgress: 0,
        currentStatus: STATUS_INITIALIZING,
        wasmSupported: null,
        totalBytes: 0,
        loadedBytes: 0,
        resourceBytes: 0,
        wasmBinaryBytes: 0,
        wasmJsBytes: 0,
        jsIrBytes: 0,
        preloadedWasmBytes: {},
        lastPreloadedWasmBytes: null,
        loadingWasm: false,
        canFallbackToJs: false,
        activeWasmPromises: [],
        onWasmAsyncFailure: null,
        runtimeTarget: null,
        diagnostics: [],
        startTime: Date.now()
    };

    function resolveAbsoluteUrl(url) {
        try {
            var base = (global.document && global.document.baseURI) || (global.location && global.location.href) || undefined;
            return new global.URL(String(url), base).href;
        } catch (e) {
            return String(url);
        }
    }

    function logDiagnostic(message) {
        var line = "[+" + (Date.now() - state.startTime) + "ms] " + message;
        state.diagnostics.push(line);
        if (state.diagnostics.length > 300) {
            state.diagnostics.shift();
        }
        if (global.console && typeof global.console.debug === "function") {
            global.console.debug("[GenericUI Loader] " + line);
        }
    }

    function createLoaderError(message, cause, context) {
        var err = new Error(message);
        if (cause !== undefined && cause !== null) {
            try {
                err.cause = cause;
            } catch (e) {
            }
        }
        if (context) {
            err.genericUiContext = context;
        }
        return err;
    }

    function describeContext(ctx) {
        if (!ctx || typeof ctx !== "object") {
            return "";
        }
        var parts = [];
        var keys = Object.keys(ctx);
        for (var i = 0; i < keys.length; i++) {
            var v = ctx[keys[i]];
            if (v !== undefined && v !== null && v !== "") {
                parts.push(keys[i] + "=" + String(v));
            }
        }
        return parts.length > 0 ? " {" + parts.join(", ") + "}" : "";
    }

    var BOOTSTRAP_BG_CLASSES = [
        "bg-primary",
        "bg-secondary",
        "bg-success",
        "bg-danger",
        "bg-warning",
        "bg-info",
        "bg-light",
        "bg-dark"
    ];

    var BOOTSTRAP_TEXT_CLASSES = [
        "text-primary",
        "text-secondary",
        "text-success",
        "text-danger",
        "text-warning",
        "text-info",
        "text-light",
        "text-dark",
        "text-body-secondary",
        "fw-semibold"
    ];

    var STATUS_ICON_CLASSES = {
        "Ready": "genericui-splash-status-icon bi bi-check-circle-fill text-success",
        "Error": "genericui-splash-status-icon bi bi-dash-circle-fill text-danger"
    };
    var STATUS_SPINNER_CLASS = "genericui-splash-status-icon spinner-border spinner-border-sm";

    function ensureStatusParts(els) {
        var label = els.statusLabel;
        if (!label || !els.doc) {
            return null;
        }
        var icon = els.doc.getElementById("genericui-splash-status-icon");
        var text = els.doc.getElementById("genericui-splash-status-text");
        if (!icon || !text) {
            var existingText = label.textContent || "";
            while (label.firstChild) {
                label.removeChild(label.firstChild);
            }
            icon = els.doc.createElement("span");
            icon.id = "genericui-splash-status-icon";
            icon.setAttribute("aria-hidden", "true");
            text = els.doc.createElement("span");
            text.id = "genericui-splash-status-text";
            text.className = "genericui-splash-status-text";
            text.textContent = existingText.replace(/^\s+|\s+$/g, "");
            label.appendChild(icon);
            label.appendChild(text);
        }
        return { icon: icon, text: text };
    }

    function setStatusDisplay(els, status, bootstrapType) {
        var parts = ensureStatusParts(els);
        if (!parts) {
            return;
        }
        parts.text.textContent = status;
        var normType = (bootstrapType && typeof bootstrapType === "string")
            ? bootstrapType.toLowerCase().replace(/^\s+|\s+$/g, "")
            : "";
        var iconClass;
        if (normType === "danger" || status === STATUS_ERROR) {
            iconClass = STATUS_ICON_CLASSES["Error"];
        } else if (status === STATUS_READY || normType === "success") {
            iconClass = STATUS_ICON_CLASSES["Ready"];
        } else {
            iconClass = STATUS_SPINNER_CLASS + (normType && normType !== "none" ? " text-" + normType : "");
        }
        if (parts.icon.className !== iconClass) {
            parts.icon.className = iconClass;
        }
        if (els.statusLabel.classList) {
            for (var i = 0; i < BOOTSTRAP_TEXT_CLASSES.length; i++) {
                els.statusLabel.classList.remove(BOOTSTRAP_TEXT_CLASSES[i]);
            }
            if (normType && normType !== "none") {
                els.statusLabel.classList.add("text-" + normType);
                if (normType === "danger") {
                    els.statusLabel.classList.add("fw-semibold");
                }
            } else if (status === STATUS_ERROR) {
                els.statusLabel.classList.add("text-danger", "fw-semibold");
            } else {
                els.statusLabel.classList.add("text-body-secondary");
            }
        }
        els.statusLabel.setAttribute("data-status", status.toLowerCase().replace(/\s+/g, "-"));
    }

    function updateProgress(percent, statusText, allowCustomStatus, bootstrapType) {
        if (state.hasError) {
            return;
        }
        var clamped = Math.max(0, Math.min(100, Math.round(percent)));
        if (clamped < state.currentProgress && clamped !== 0) {
            clamped = state.currentProgress;
        }
        state.currentProgress = clamped;

        var normalizedStatus;
        if (allowCustomStatus === true && typeof statusText === "string" && statusText.replace(/^\s+|\s+$/g, "").length > 0) {
            normalizedStatus = statusText.replace(/^\s+|\s+$/g, "");
        } else {
            normalizedStatus = statusText ? normalizeStatusMessage(statusText) : state.currentStatus;
        }
        state.currentStatus = normalizedStatus;

        var els = getDomElements();
        if (els.progressBar) {
            els.progressBar.style.width = clamped + "%";
            els.progressBar.setAttribute("aria-valuenow", String(clamped));
        }
        if (els.progressContainer) {
            els.progressContainer.setAttribute("aria-valuenow", String(clamped));
        }
        if (els.statusLabel) {
            setStatusDisplay(els, normalizedStatus, bootstrapType);
        }
    }

    function setStatus(statusText, bootstrapType) {
        if (state.hasError && (!bootstrapType || String(bootstrapType).toLowerCase() !== "danger")) {
            return;
        }
        if (typeof statusText === "string" && statusText.replace(/^\s+|\s+$/g, "").length > 0) {
            state.currentStatus = statusText.replace(/^\s+|\s+$/g, "");
        }
        var els = getDomElements();
        if (els.statusLabel) {
            setStatusDisplay(els, state.currentStatus, bootstrapType);
        }
    }

    function setProgress(percent, bootstrapType) {
        if (state.hasError) {
            return;
        }
        var clamped = Math.max(0, Math.min(100, Math.round(typeof percent === "number" ? percent : state.currentProgress)));
        state.currentProgress = clamped;
        var normType = (bootstrapType && typeof bootstrapType === "string")
            ? bootstrapType.toLowerCase().replace(/^\s+|\s+$/g, "")
            : "primary";
        var els = getDomElements();
        if (els.progressBar) {
            els.progressBar.style.width = clamped + "%";
            els.progressBar.setAttribute("aria-valuenow", String(clamped));
            if (els.progressBar.classList) {
                for (var i = 0; i < BOOTSTRAP_BG_CLASSES.length; i++) {
                    els.progressBar.classList.remove(BOOTSTRAP_BG_CLASSES[i]);
                }
                var bgClass = (normType && normType !== "none") ? ("bg-" + normType) : "bg-primary";
                els.progressBar.classList.add(bgClass);
            }
        }
        if (els.progressContainer) {
            els.progressContainer.setAttribute("aria-valuenow", String(clamped));
        }
    }

    function setError(message, stackTrace, title) {
        var err = new Error(message || "Application Error");
        err.stack = (stackTrace !== undefined && stackTrace !== null) ? String(stackTrace) : "";
        showError(err, title || STATUS_ERROR);
    }

    function setApplicationStatus(statusText, percent) {
        var pct = typeof percent === "number" ? percent : Math.max(state.currentProgress, 99);
        updateProgress(pct, statusText, true);
    }

    function formatErrorDetails(err) {
        var lines = [];
        var headline = "An unknown application error occurred.";
        var queue = [{ error: err, prefix: "" }];
        var seen = [];
        var count = 0;

        while (queue.length > 0 && count < 12) {
            var item = queue.shift();
            var current = item.error;
            count++;
            if (current === undefined || current === null) {
                if (count === 1) {
                    lines.push("Error: (no error object was provided; the failure was reported as " + String(current) + ")");
                }
                continue;
            }
            if (seen.indexOf(current) !== -1) {
                continue;
            }
            seen.push(current);

            if (typeof current !== "object" && typeof current !== "function") {
                var primitiveText = item.prefix + "Thrown value (" + typeof current + "): " + String(current);
                lines.push(primitiveText);
                if (count === 1) {
                    headline = String(current);
                }
                continue;
            }

            var name = current.name || (current.constructor && current.constructor.name) || "Error";
            var message = current.message !== undefined ? String(current.message) : String(current);
            if (count === 1) {
                headline = name + ": " + message;
            }
            if (lines.length > 0) {
                lines.push("");
            }
            lines.push(item.prefix + name + ": " + message);
            if (current.genericUiContext) {
                var ctxKeys = Object.keys(current.genericUiContext);
                for (var c = 0; c < ctxKeys.length; c++) {
                    lines.push("    [context] " + ctxKeys[c] + ": " + String(current.genericUiContext[ctxKeys[c]]));
                }
            }
            if (current.genericUiWasmContext) {
                var wKeys = Object.keys(current.genericUiWasmContext);
                for (var w = 0; w < wKeys.length; w++) {
                    lines.push("    [wasm] " + wKeys[w] + ": " + String(current.genericUiWasmContext[wKeys[w]]));
                }
            }
            if (current.filename || current.lineno) {
                lines.push("    [source] " + (current.filename || "?") + ":" + (current.lineno || 0) + ":" + (current.colno || 0));
            }
            var stack = current.stack ? String(current.stack) : "";
            if (stack) {
                var stackLines = stack.split("\n");
                for (var s = 0; s < stackLines.length; s++) {
                    var sl = stackLines[s];
                    if (s === 0 && (sl.indexOf(message) !== -1 || sl === name)) {
                        continue;
                    }
                    lines.push("    " + sl.replace(/^\s+/, ""));
                }
            } else {
                lines.push("    (no stacktrace available)");
            }

            if (current.cause !== undefined && current.cause !== null) {
                queue.push({ error: current.cause, prefix: "Caused by: " });
            }
            if (current.error && current.error !== current && typeof current.error === "object") {
                queue.push({ error: current.error, prefix: "Wrapped error: " });
            }
            if (current.reason && current.reason !== current) {
                queue.push({ error: current.reason, prefix: "Rejection reason: " });
            }
            if (Array.isArray(current.suppressed)) {
                for (var p = 0; p < current.suppressed.length; p++) {
                    queue.push({ error: current.suppressed[p], prefix: "Earlier failure: " });
                }
            }
            if (Array.isArray(current.errors)) {
                for (var a = 0; a < current.errors.length; a++) {
                    queue.push({ error: current.errors[a], prefix: "Aggregated error: " });
                }
            }
        }

        lines.push("");
        lines.push("Environment:");
        lines.push("    runtimeTarget: " + (state.runtimeTarget || "(not selected yet)"));
        lines.push("    loaderStatus: " + state.currentStatus + " (" + state.currentProgress + "%)");
        lines.push("    page: " + (global.location ? global.location.href : "(unknown)"));
        lines.push("    baseURI: " + (global.document && global.document.baseURI ? global.document.baseURI : "(unknown)"));
        lines.push("    userAgent: " + (global.navigator && global.navigator.userAgent ? global.navigator.userAgent : "(unknown)"));
        lines.push("    elapsed: " + (Date.now() - state.startTime) + "ms");

        if (state.diagnostics.length > 0) {
            lines.push("");
            lines.push("Loader log (most recent last):");
            var startIdx = Math.max(0, state.diagnostics.length - 60);
            for (var d = startIdx; d < state.diagnostics.length; d++) {
                lines.push("    " + state.diagnostics[d]);
            }
        }

        return {
            message: headline,
            stack: lines.join("\n")
        };
    }

    function showError(err, phaseTitle) {
        state.hasError = true;
        state.loadingWasm = false;
        state.currentStatus = STATUS_ERROR;
        var details = formatErrorDetails(err);
        var els = getDomElements();

        if (global.console && typeof global.console.error === "function") {
            global.console.error("[GenericUI Loader] " + (phaseTitle || STATUS_ERROR) + ": " + details.message + "\n" + details.stack, err);
        }

        if (!els.doc) {
            return;
        }

        if (els.splash) {
            els.splash.classList.remove("d-none", "opacity-0", "genericui-splash-hidden");
        }

        if (els.progressBar) {
            els.progressBar.classList.remove("progress-bar-animated", "progress-bar-striped");
            els.progressBar.classList.add("bg-danger");
            els.progressBar.style.width = "100%";
        }

        if (els.statusLabel) {
            setStatusDisplay(els, STATUS_ERROR);
            els.statusLabel.classList.remove("text-body-secondary");
            els.statusLabel.classList.add("text-danger", "fw-semibold");
        }

        var errorBox = els.errorBox;
        var errorMessage = els.errorMessage;
        var errorStack = els.errorStack;

        if (!errorBox && els.splash) {
            errorBox = els.doc.createElement("div");
            errorBox.id = "genericui-splash-error";
            errorBox.className = "genericui-splash-error alert alert-danger text-start w-100 mt-3 mb-0 shadow-sm";
            errorBox.setAttribute("role", "alert");

            var headerDiv = els.doc.createElement("div");
            headerDiv.className = "fw-bold mb-1";
            headerDiv.id = "genericui-splash-error-title";
            headerDiv.textContent = phaseTitle || STATUS_ERROR;
            errorBox.appendChild(headerDiv);

            errorMessage = els.doc.createElement("div");
            errorMessage.id = "genericui-splash-error-message";
            errorMessage.className = "genericui-splash-error-message small fw-semibold mb-2";
            errorBox.appendChild(errorMessage);

            errorStack = els.doc.createElement("pre");
            errorStack.id = "genericui-splash-error-stack";
            errorStack.className = "genericui-splash-error-stack small mb-0 p-2 bg-dark text-light rounded overflow-auto";
            errorBox.appendChild(errorStack);

            var innerWrapper = els.splash.firstElementChild || els.splash;
            innerWrapper.appendChild(errorBox);
        }

        if (els.errorTitle && phaseTitle) {
            els.errorTitle.textContent = phaseTitle;
        }
        if (errorMessage) {
            errorMessage.textContent = details.message;
        }
        if (errorStack) {
            errorStack.textContent = details.stack;
        }
        if (errorBox) {
            errorBox.classList.remove("d-none");
        }
    }

    function fadeOutSplash(fadeDurationMs) {
        if (state.hasError || state.fadedOut) {
            return;
        }
        state.fadedOut = true;
        state.loadingWasm = false;
        updateProgress(100, STATUS_READY);

        var els = getDomElements();
        var splash = els.splash;
        if (!splash) {
            return;
        }
        var duration = typeof fadeDurationMs === "number" && fadeDurationMs >= 0 ? fadeDurationMs : 350;
        if (duration !== 350) {
            splash.style.transitionDuration = duration + "ms";
        }
        splash.classList.add("genericui-splash-hidden");

        function removeSplashNode() {
            if (!state.hasError && splash) {
                if (typeof splash.remove === "function") {
                    splash.remove();
                } else if (splash.parentNode) {
                    splash.parentNode.removeChild(splash);
                }
            }
        }

        if (typeof global.setTimeout === "function") {
            global.setTimeout(removeSplashNode, duration + 20);
        } else {
            removeSplashNode();
        }
    }

    function warnWasmUnsupported(reason, error) {
        if (global.console && typeof global.console.warn === "function") {
            var detail = "";
            if (error) {
                detail = " Details: " + (error.stack || error.message || String(error));
            }
            global.console.warn(
                "[GenericUI Loader] WebAssembly detected as not supported: " + reason + detail
            );
        }
    }

    function logWasmFallbackErrorSilently(error) {
        if (global.console && typeof global.console.error === "function") {
            var details = formatErrorDetails(error);
            global.console.error(
                "[GenericUI Loader] WebAssembly failed to load; falling back to the JS bundle.\n" + details.stack,
                error
            );
        }
    }

    function probeWasmCompileError(bytes) {
        if (!global.WebAssembly || typeof global.WebAssembly.Module !== "function") {
            return "";
        }
        try {
            new global.WebAssembly.Module(bytes);
            return "";
        } catch (compileErr) {
            return compileErr && compileErr.message ? compileErr.message : String(compileErr);
        }
    }

    function detectWasmSupport() {
        try {
            if (typeof global.WebAssembly !== "object" || global.WebAssembly === null) {
                warnWasmUnsupported(
                    "global WebAssembly object is missing (typeof WebAssembly === \"" + (typeof global.WebAssembly) + "\")."
                );
                return false;
            }
            if (typeof global.WebAssembly.instantiate !== "function") {
                warnWasmUnsupported(
                    "WebAssembly.instantiate is not a function (typeof WebAssembly.instantiate === \"" + (typeof global.WebAssembly.instantiate) + "\")."
                );
                return false;
            }
            if (typeof global.WebAssembly.validate !== "function") {
                warnWasmUnsupported(
                    "WebAssembly.validate is not a function (typeof WebAssembly.validate === \"" + (typeof global.WebAssembly.validate) + "\")."
                );
                return false;
            }
            var basicWasmProbe = new Uint8Array([0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00]);
            if (!global.WebAssembly.validate(basicWasmProbe)) {
                var basicError = probeWasmCompileError(basicWasmProbe);
                warnWasmUnsupported(
                    "baseline WebAssembly module header validation (WebAssembly.validate) returned false" +
                    (basicError ? " (" + basicError + ")." : ".")
                );
                return false;
            }
            var wasmGcProbe = new Uint8Array([
                0x00, 0x61, 0x73, 0x6d,
                0x01, 0x00, 0x00, 0x00,
                0x01, 0x03, 0x01, 0x5f, 0x00
            ]);
            if (global.WebAssembly.validate(wasmGcProbe)) {
                return true;
            }
            if (global.GenericUiLoaderConfig && global.GenericUiLoaderConfig.allowStandardWasm === true) {
                return true;
            }
            var gcError = probeWasmCompileError(wasmGcProbe);
            warnWasmUnsupported(
                "browser supports baseline WebAssembly, but WasmGC struct type validation (0x5f required by Kotlin/Wasm) returned false" +
                (gcError ? " (" + gcError + ")." : ".")
            );
            return false;
        } catch (err) {
            warnWasmUnsupported("an exception was thrown while probing WebAssembly support.", err);
            return false;
        }
    }

    function downloadWithProgress(url, minPct, maxPct, statusMessage, expectedBytes) {
        var resolvedStatus = normalizeStatusMessage(statusMessage);
        if (state.totalBytes <= 0) {
            updateProgress(minPct, resolvedStatus);
        } else {
            var startPct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
            updateProgress(startPct, resolvedStatus);
        }
        if (typeof global.fetch !== "function") {
            return new Promise(function (resolve, reject) {
                var xhr = new global.XMLHttpRequest();
                xhr.open("GET", url, true);
                xhr.responseType = "arraybuffer";
                xhr.onprogress = function (ev) {
                    if (state.totalBytes > 0 && ev.loaded > 0) {
                        var inFlightBytes = (typeof expectedBytes === "number" && expectedBytes > 0)
                            ? Math.min(expectedBytes, ev.loaded)
                            : ev.loaded;
                        var bytePct = Math.min(99, Math.round(((state.loadedBytes + inFlightBytes) / state.totalBytes) * 100));
                        updateProgress(bytePct, resolvedStatus);
                    } else if (ev.lengthComputable && ev.total > 0) {
                        var ratio = ev.loaded / ev.total;
                        var pct = minPct + ratio * (maxPct - minPct);
                        updateProgress(pct, resolvedStatus);
                    }
                };
                xhr.onload = function () {
                    if (xhr.status >= 200 && xhr.status < 300) {
                        var respLen = xhr.response ? xhr.response.byteLength : 0;
                        if (state.totalBytes > 0) {
                            var doneBytes = (typeof expectedBytes === "number" && expectedBytes > 0) ? expectedBytes : respLen;
                            state.loadedBytes += doneBytes;
                            var donePct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
                            updateProgress(donePct, resolvedStatus);
                        } else {
                            updateProgress(maxPct, resolvedStatus);
                        }
                        logDiagnostic("XHR GET " + resolveAbsoluteUrl(url) + " -> " + xhr.status + " (" + respLen + " bytes)");
                        resolve(new Uint8Array(xhr.response));
                    } else {
                        logDiagnostic("XHR GET " + resolveAbsoluteUrl(url) + " -> " + xhr.status + " " + xhr.statusText);
                        reject(createLoaderError(
                            "HTTP " + xhr.status + " " + (xhr.statusText || "") + " while downloading " + resolveAbsoluteUrl(url),
                            null,
                            {
                                phase: resolvedStatus,
                                requestedUrl: url,
                                resolvedUrl: resolveAbsoluteUrl(url),
                                httpStatus: xhr.status,
                                httpStatusText: xhr.statusText,
                                contentType: xhr.getResponseHeader ? xhr.getResponseHeader("Content-Type") : ""
                            }
                        ));
                    }
                };
                xhr.onerror = function () {
                    logDiagnostic("XHR GET " + resolveAbsoluteUrl(url) + " -> network error");
                    reject(createLoaderError(
                        "Network error while downloading " + resolveAbsoluteUrl(url) + " (offline, CORS, DNS, or connection reset)",
                        null,
                        { phase: resolvedStatus, requestedUrl: url, resolvedUrl: resolveAbsoluteUrl(url) }
                    ));
                };
                xhr.send();
            });
        }

        var absoluteUrl = resolveAbsoluteUrl(url);
        return global.fetch(url, { credentials: "same-origin" }).then(function (response) {
            if (!response.ok) {
                var errContentType = response.headers ? response.headers.get("Content-Type") : "";
                logDiagnostic("GET " + absoluteUrl + " -> " + response.status + " " + response.statusText + " (" + (errContentType || "no content-type") + ")");
                throw createLoaderError(
                    "HTTP " + response.status + " " + (response.statusText || "") + " while downloading " + absoluteUrl,
                    null,
                    {
                        phase: resolvedStatus,
                        requestedUrl: url,
                        resolvedUrl: absoluteUrl,
                        responseUrl: response.url,
                        redirected: response.redirected,
                        httpStatus: response.status,
                        httpStatusText: response.statusText,
                        contentType: errContentType
                    }
                );
            }
            var contentLengthHeader = response.headers ? response.headers.get("Content-Length") : null;
            var totalBytes = contentLengthHeader ? parseInt(contentLengthHeader, 10) : 0;
            logDiagnostic("GET " + absoluteUrl + " -> " + response.status + " (" + (response.headers ? response.headers.get("Content-Type") : "?") + ", " + (totalBytes || "unknown") + " bytes)");

            if (!response.body || typeof response.body.getReader !== "function") {
                return response.arrayBuffer().then(function (buf) {
                    var out = new Uint8Array(buf);
                    if (state.totalBytes > 0) {
                        var doneBytes = (typeof expectedBytes === "number" && expectedBytes > 0) ? expectedBytes : out.byteLength;
                        state.loadedBytes += doneBytes;
                        var donePct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
                        updateProgress(donePct, resolvedStatus);
                    } else {
                        updateProgress(maxPct, resolvedStatus);
                    }
                    return out;
                });
            }

            var reader = response.body.getReader();
            var chunks = [];
            var receivedBytes = 0;

            function pump() {
                return reader.read().then(function (result) {
                    if (result.done) {
                        var combined = new Uint8Array(receivedBytes);
                        var offset = 0;
                        for (var i = 0; i < chunks.length; i++) {
                            combined.set(chunks[i], offset);
                            offset += chunks[i].byteLength;
                        }
                        if (state.totalBytes > 0) {
                            var doneBytes = (typeof expectedBytes === "number" && expectedBytes > 0) ? expectedBytes : receivedBytes;
                            state.loadedBytes += doneBytes;
                            var donePct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
                            updateProgress(donePct, resolvedStatus);
                        } else {
                            updateProgress(maxPct, resolvedStatus);
                        }
                        return combined;
                    }
                    var chunk = result.value;
                    chunks.push(chunk);
                    receivedBytes += chunk.byteLength;

                    if (state.totalBytes > 0) {
                        var inFlightBytes = (typeof expectedBytes === "number" && expectedBytes > 0)
                            ? Math.min(expectedBytes, receivedBytes)
                            : receivedBytes;
                        var bytePct = Math.min(99, Math.round(((state.loadedBytes + inFlightBytes) / state.totalBytes) * 100));
                        updateProgress(bytePct, resolvedStatus);
                    } else {
                        var fraction = totalBytes > 0
                            ? Math.min(1, receivedBytes / totalBytes)
                            : Math.min(0.95, 1 - Math.exp(-receivedBytes / (512 * 1024)));
                        var pct = minPct + fraction * (maxPct - minPct);
                        updateProgress(pct, resolvedStatus);
                    }
                    return pump();
                });
            }

            return pump();
        }).catch(function (fetchErr) {
            if (fetchErr && fetchErr.genericUiContext) {
                throw fetchErr;
            }
            logDiagnostic("GET " + absoluteUrl + " -> failed: " + (fetchErr && fetchErr.message ? fetchErr.message : String(fetchErr)));
            throw createLoaderError(
                "Request failed while downloading " + absoluteUrl + " (network error, CORS, offline, or the response stream was interrupted)",
                fetchErr,
                { phase: resolvedStatus, requestedUrl: url, resolvedUrl: absoluteUrl }
            );
        });
    }

    var BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    function bytesToBase64(bytes) {
        if (!bytes || bytes.length === 0) {
            return "";
        }
        if (typeof global.btoa === "function") {
            var binary = "";
            var chunkSize = 0x8000;
            for (var c = 0; c < bytes.length; c += chunkSize) {
                binary += String.fromCharCode.apply(null, bytes.subarray(c, Math.min(c + chunkSize, bytes.length)));
            }
            return global.btoa(binary);
        }
        var result = "";
        var len = bytes.length;
        var i = 0;
        while (i < len) {
            var b0 = bytes[i++] & 0xff;
            if (i === len) {
                result += BASE64_CHARS.charAt(b0 >> 2);
                result += BASE64_CHARS.charAt((b0 & 0x03) << 4);
                result += "==";
                break;
            }
            var b1 = bytes[i++] & 0xff;
            if (i === len) {
                result += BASE64_CHARS.charAt(b0 >> 2);
                result += BASE64_CHARS.charAt(((b0 & 0x03) << 4) | ((b1 & 0xf0) >> 4));
                result += BASE64_CHARS.charAt((b1 & 0x0f) << 2);
                result += "=";
                break;
            }
            var b2 = bytes[i++] & 0xff;
            result += BASE64_CHARS.charAt(b0 >> 2);
            result += BASE64_CHARS.charAt(((b0 & 0x03) << 4) | ((b1 & 0xf0) >> 4));
            result += BASE64_CHARS.charAt(((b1 & 0x0f) << 2) | ((b2 & 0xc0) >> 6));
            result += BASE64_CHARS.charAt(b2 & 0x3f);
        }
        return result;
    }

    function base64ToBytes(base64) {
        if (!base64) {
            return new Uint8Array(0);
        }
        var clean = String(base64).replace(/[^A-Za-z0-9+/=]/g, "");
        if (typeof global.atob === "function") {
            var bin = global.atob(clean);
            var out = new Uint8Array(bin.length);
            for (var i = 0; i < bin.length; i++) {
                out[i] = bin.charCodeAt(i) & 0xff;
            }
            return out;
        }
        var bytes = [];
        for (var j = 0; j < clean.length; j += 4) {
            var c0 = BASE64_CHARS.indexOf(clean.charAt(j));
            var c1 = BASE64_CHARS.indexOf(clean.charAt(j + 1));
            var c2 = clean.charAt(j + 2) === "=" ? -1 : BASE64_CHARS.indexOf(clean.charAt(j + 2));
            var c3 = clean.charAt(j + 3) === "=" ? -1 : BASE64_CHARS.indexOf(clean.charAt(j + 3));
            if (c0 < 0 || c1 < 0) {
                break;
            }
            bytes.push((c0 << 2) | (c1 >> 4));
            if (c2 >= 0) {
                bytes.push(((c1 & 15) << 4) | (c2 >> 2));
                if (c3 >= 0) {
                    bytes.push(((c2 & 3) << 6) | c3);
                }
            }
        }
        return new Uint8Array(bytes);
    }

    function utf8ToBytes(text) {
        if (typeof global.TextEncoder === "function") {
            return new global.TextEncoder().encode(text || "");
        }
        var str = String(text || "");
        var utf8 = unescape(encodeURIComponent(str));
        var arr = new Uint8Array(utf8.length);
        for (var i = 0; i < utf8.length; i++) {
            arr[i] = utf8.charCodeAt(i) & 0xff;
        }
        return arr;
    }

    function bytesToUtf8(bytes) {
        if (!bytes) {
            return "";
        }
        if (typeof global.TextDecoder === "function") {
            return new global.TextDecoder("utf-8").decode(bytes);
        }
        var s = "";
        for (var i = 0; i < bytes.length; i++) {
            s += String.fromCharCode(bytes[i]);
        }
        try {
            return decodeURIComponent(escape(s));
        } catch (e) {
            return s;
        }
    }

    function guessMimeType(pathOrName, resourceType) {
        var clean = String(pathOrName || "").split("?")[0].split("#")[0].toLowerCase();
        var dotIdx = clean.lastIndexOf(".");
        var ext = dotIdx >= 0 ? clean.substring(dotIdx + 1) : clean;
        switch (ext) {
            case "css":
            case "scss":
            case "sass":
                return "text/css";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "svg":
                return "image/svg+xml";
            case "ico":
                return "image/x-icon";
            case "woff2":
                return "font/woff2";
            case "woff":
                return "font/woff";
            case "ttf":
                return "font/ttf";
            case "otf":
                return "font/otf";
            case "json":
            case "webmanifest":
                return "application/json";
            case "xml":
                return "application/xml";
            case "txt":
                return "text/plain";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "ogg":
                return "audio/ogg";
            case "mp4":
                return "video/mp4";
            case "webm":
                return "video/webm";
        }
        var t = String(resourceType || "").toLowerCase();
        if (t === "style" || t === "styles" || t === "stylesheet" || t === "css") {
            return "text/css";
        }
        if (t === "drawable" || t === "image" || t === "icon") {
            return "image/png";
        }
        if (t === "font") {
            return "font/ttf";
        }
        if (t === "xml") {
            return "application/xml";
        }
        if (t === "string") {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    function isTextMimeType(mimeType, resourceType) {
        var m = String(mimeType || "").toLowerCase();
        var t = String(resourceType || "").toLowerCase();
        return m.indexOf("text/") === 0 ||
            m.indexOf("xml") !== -1 ||
            m.indexOf("json") !== -1 ||
            m.indexOf("svg") !== -1 ||
            m.indexOf("javascript") !== -1 ||
            t === "style" ||
            t === "styles" ||
            t === "xml" ||
            t === "string";
    }

    function inferResourceType(path) {
        var clean = String(path || "").replace(/^\.?\//, "").toLowerCase();
        if (clean.indexOf("styles/") === 0 || clean.indexOf("css/") === 0 || /\.s?css$/i.test(clean)) {
            return "style";
        }
        if (clean.indexOf("assets/drawable/") === 0 || clean.indexOf("drawable/") === 0 || /\.(png|jpe?g|gif|webp|svg|ico)$/i.test(clean)) {
            return "drawable";
        }
        if (clean.indexOf("assets/font/") === 0 || clean.indexOf("font/") === 0 || /\.(woff2?|ttf|otf)$/i.test(clean)) {
            return "font";
        }
        if (clean.indexOf("assets/xml/") === 0 || clean.indexOf("xml/") === 0 || /\.xml$/i.test(clean)) {
            return "xml";
        }
        return "raw";
    }

    function inferResourceName(path, id) {
        var clean = String(path || "").split("?")[0].split("#")[0];
        var lastSlash = clean.lastIndexOf("/");
        var file = lastSlash >= 0 ? clean.substring(lastSlash + 1) : clean;
        var dot = file.lastIndexOf(".");
        var stem = dot > 0 ? file.substring(0, dot) : file;
        return stem || String(id || "");
    }

    function registerNameKeysInStore(store, type, name, record) {
        if (!name) {
            return;
        }
        var variants = [name];
        var underscored = name.replace(/-/g, "_");
        if (underscored !== name) {
            variants.push(underscored);
        }
        for (var i = 0; i < variants.length; i++) {
            var n = variants[i];
            store[n] = record;
            if (type) {
                store[type + "/" + n] = record;
                store["res://" + type + "/" + n] = record;
                store["@" + type + "/" + n] = record;
                if (type === "style" || type === "styles") {
                    store["style/" + n] = record;
                    store["res://style/" + n] = record;
                    store["@style/" + n] = record;
                    store["styles/" + n] = record;
                    store["res://styles/" + n] = record;
                    store["@styles/" + n] = record;
                    store["styles/" + n + ".css"] = record;
                }
            }
        }
    }

    function applyGenericUiResourcesCatalog(jsonText) {
        if (!jsonText) {
            return;
        }
        try {
            var catalog = typeof jsonText === "string" ? JSON.parse(jsonText) : jsonText;
            if (!catalog || typeof catalog !== "object") {
                return;
            }
            state.pendingResourcesCatalog = catalog;
            var store = global.__GENERICUI_PRELOADED_RESOURCES__;
            if (!store) {
                return;
            }
            var drawables = catalog.drawables;
            if (drawables && typeof drawables === "object") {
                var keys = Object.keys(drawables);
                for (var i = 0; i < keys.length; i++) {
                    var resName = keys[i];
                    var item = drawables[resName];
                    if (item && typeof item === "object") {
                        var itemPath = item.path ? String(item.path).replace(/^\.?\//, "") : "";
                        var rec = (itemPath && (store[itemPath] || store["res/" + itemPath] || store["res://" + itemPath])) ||
                            store["drawable/" + resName] ||
                            store[resName];
                        if (rec) {
                            if (item.id != null) {
                                store[String(item.id)] = rec;
                                store["res://" + String(item.id)] = rec;
                            }
                            if (itemPath) {
                                store[itemPath] = rec;
                                store["res/" + itemPath] = rec;
                                store["res://" + itemPath] = rec;
                            }
                            registerNameKeysInStore(store, "drawable", resName, rec);
                        }
                    }
                }
            }
        } catch (e) {
        }
    }

    function buildHashedResourceUrl(rawUrl, hash) {
        if (!rawUrl) {
            return "";
        }
        var urlStr = String(rawUrl);
        var h = hash != null ? String(hash).replace(/^\s+|\s+$/g, "").replace(/^\?+/, "") : "";
        if (!h) {
            return urlStr;
        }
        var base = urlStr.split("?")[0].split("#")[0];
        return base + "?" + h;
    }

    function registerPreloadedResourceEntry(entry, bytes) {
        if (!global.__GENERICUI_PRELOADED_RESOURCES__) {
            global.__GENERICUI_PRELOADED_RESOURCES__ = {};
        }
        var store = global.__GENERICUI_PRELOADED_RESOURCES__;
        var id = entry && entry.id != null ? String(entry.id) : (entry && entry.resourceId != null ? String(entry.resourceId) : "");
        var path = (entry && (entry.path || entry.url || entry.src || entry.href)) ? String(entry.path || entry.url || entry.src || entry.href) : "";
        var entryHash = (entry && (entry.hash || entry.sha1 || entry.version)) ? String(entry.hash || entry.sha1 || entry.version) : "";
        var hashedUrl = buildHashedResourceUrl(path, entryHash);
        var type = (entry && (entry.type || entry.resourceType)) ? String(entry.type || entry.resourceType) : inferResourceType(path);
        var name = (entry && entry.name) ? String(entry.name) : inferResourceName(path, id);
        var mimeType = (entry && (entry.mimeType || entry.contentType)) ? String(entry.mimeType || entry.contentType) : guessMimeType(path || name, type);

        var resolvedBytes = bytes instanceof Uint8Array
            ? bytes
            : (bytes ? new Uint8Array(bytes) : new Uint8Array(0));
        var base64 = bytesToBase64(resolvedBytes);
        var dataUrl = (entry && typeof entry.dataUrl === "string" && entry.dataUrl.indexOf("data:") === 0)
            ? entry.dataUrl
            : ("data:" + mimeType + ";base64," + base64);
        var blob = typeof global.Blob === "function"
            ? new global.Blob([resolvedBytes], { type: mimeType })
            : null;
        var text = (entry && typeof entry.text === "string")
            ? entry.text
            : (isTextMimeType(mimeType, type) ? bytesToUtf8(resolvedBytes) : null);

        var record = {
            id: id,
            type: type,
            name: name,
            path: path,
            hash: entryHash,
            url: hashedUrl,
            mimeType: mimeType,
            bytes: resolvedBytes,
            blob: blob,
            dataUrl: dataUrl,
            text: text
        };

        if (mimeType && mimeType.indexOf("image/") === 0 && dataUrl && typeof global.Image === "function") {
            try {
                var eagerImg = new global.Image();
                eagerImg.decoding = "async";
                eagerImg.src = dataUrl;
                record.imageElement = eagerImg;
                if (eagerImg.complete && eagerImg.naturalWidth > 0) {
                    record.naturalWidth = eagerImg.naturalWidth;
                    record.naturalHeight = eagerImg.naturalHeight;
                }
            } catch (imgErr) {
            }
        }

        if (id) {
            store[id] = record;
            store["res://" + id] = record;
        }
        if (hashedUrl) {
            store[hashedUrl] = record;
        }
        if (path) {
            store[path] = record;
            var cleanPath = path.replace(/^\.?\//, "").split("?")[0];
            store[cleanPath] = record;
            store["res://" + cleanPath] = record;
            store["res/" + cleanPath] = record;
            var lastSlashIdx = cleanPath.lastIndexOf("/");
            var fileName = lastSlashIdx >= 0 ? cleanPath.substring(lastSlashIdx + 1) : cleanPath;
            if (fileName) {
                if (!store[fileName]) store[fileName] = record;
                if (!store["res/" + fileName]) store["res/" + fileName] = record;
                if (!store["assets/drawable/" + fileName]) store["assets/drawable/" + fileName] = record;
                if (!store["res/assets/drawable/" + fileName]) store["res/assets/drawable/" + fileName] = record;
                if (!store["res://assets/drawable/" + fileName]) store["res://assets/drawable/" + fileName] = record;
                if (!store["drawable/" + fileName]) store["drawable/" + fileName] = record;
                if (!store["res://drawable/" + fileName]) store["res://drawable/" + fileName] = record;
            }
            if (cleanPath.indexOf("res/") === 0) {
                var strippedRes = cleanPath.substring(4);
                if (strippedRes) {
                    store[strippedRes] = record;
                    store["res://" + strippedRes] = record;
                }
            } else if (cleanPath.indexOf("gui/") === 0) {
                var strippedGui = cleanPath.substring(4);
                if (strippedGui) {
                    store[strippedGui] = record;
                    store["res://" + strippedGui] = record;
                }
            }
            var relForIdent = cleanPath
                .replace(/^(?:res|gui)\//i, "")
                .replace(/^assets\/drawable\//i, "")
                .replace(/^(?:drawable|mipmap|icons|images)\//i, "");
            if (relForIdent && relForIdent.indexOf("/") !== -1) {
                var relNoExt = relForIdent.replace(/\.[^.\/]+$/, "");
                var subdirIdent = relNoExt
                    .replace(/[-.\s\/]+/g, "_")
                    .replace(/[^A-Za-z0-9_]/g, "")
                    .toLowerCase();
                if (subdirIdent) {
                    registerNameKeysInStore(store, type, subdirIdent, record);
                }
            }
            if (/\.scss$/i.test(cleanPath)) {
                store[cleanPath.replace(/\.scss$/i, ".css")] = record;
            }
        }
        registerNameKeysInStore(store, type, name, record);

        if (text && (name === "genericui-resources" || /genericui-resources\.json$/i.test(path))) {
            applyGenericUiResourcesCatalog(text);
        }

        return record;
    }

    function preloadRecordImageElement(record) {
        if (!record || !record.mimeType || record.mimeType.indexOf("image/") !== 0 || !record.dataUrl || typeof global.Image !== "function") {
            return Promise.resolve(record);
        }
        if (record.imageElement && record.imageElement.complete && record.imageElement.naturalWidth > 0) {
            record.naturalWidth = record.imageElement.naturalWidth;
            record.naturalHeight = record.imageElement.naturalHeight || 0;
            return Promise.resolve(record);
        }
        var img = record.imageElement || new global.Image();
        img.decoding = "async";
        record.imageElement = img;
        return new Promise(function (resolve) {
            var settled = false;
            var timer = typeof global.setTimeout === "function"
                ? global.setTimeout(function () {
                    if (!settled) {
                        settled = true;
                        record.naturalWidth = img.naturalWidth || 0;
                        record.naturalHeight = img.naturalHeight || 0;
                        resolve(record);
                    }
                }, 3000)
                : null;
            function finish() {
                if (settled) {
                    return;
                }
                settled = true;
                if (timer !== null && typeof global.clearTimeout === "function") {
                    global.clearTimeout(timer);
                }
                record.naturalWidth = img.naturalWidth || 0;
                record.naturalHeight = img.naturalHeight || 0;
                resolve(record);
            }
            img.onload = function () {
                if (typeof img.decode === "function") {
                    try {
                        var p = img.decode();
                        if (p && typeof p.then === "function") {
                            p.then(finish, finish);
                            return;
                        }
                    } catch (decErr) {
                    }
                }
                finish();
            };
            img.onerror = finish;
            if (!img.src) {
                img.src = record.dataUrl;
            }
            if (img.complete && img.naturalWidth > 0) {
                if (typeof img.decode === "function") {
                    try {
                        var dp = img.decode();
                        if (dp && typeof dp.then === "function") {
                            dp.then(finish, finish);
                            return;
                        }
                    } catch (decErr2) {
                    }
                }
                finish();
            }
        });
    }

    function getPreloadedResource(key) {
        var store = global.__GENERICUI_PRELOADED_RESOURCES__;
        if (!store || !key) {
            return null;
        }
        var k = String(key);
        if (store[k]) {
            return store[k];
        }
        var clean = k.replace(/^\.?\//, "").split("?")[0];
        if (store[clean]) {
            return store[clean];
        }
        if (store["res://" + clean]) {
            return store["res://" + clean];
        }
        if (store["res/" + clean]) {
            return store["res/" + clean];
        }
        if (store["gui/" + clean]) {
            return store["gui/" + clean];
        }
        return null;
    }

    function extractManifestEntries(manifestData) {
        if (!manifestData) {
            return [];
        }
        if (Array.isArray(manifestData)) {
            return manifestData;
        }
        if (Array.isArray(manifestData.resources)) {
            return manifestData.resources;
        }
        if (Array.isArray(manifestData.entries)) {
            return manifestData.entries;
        }
        if (Array.isArray(manifestData.assets)) {
            return manifestData.assets;
        }
        if (manifestData.drawables && typeof manifestData.drawables === "object") {
            var drawableList = [];
            var dKeys = Object.keys(manifestData.drawables);
            for (var d = 0; d < dKeys.length; d++) {
                var dName = dKeys[d];
                var dItem = manifestData.drawables[dName];
                if (dItem && dItem.path) {
                    drawableList.push({
                        id: dItem.id != null ? String(dItem.id) : ("drawable/" + dName),
                        type: "drawable",
                        name: dName,
                        path: dItem.path
                    });
                }
            }
            return drawableList;
        }
        if (typeof manifestData === "object") {
            var list = [];
            var keys = Object.keys(manifestData);
            for (var i = 0; i < keys.length; i++) {
                var k = keys[i];
                var item = manifestData[k];
                if (item && typeof item === "object") {
                    if (!item.id && !item.path) {
                        item.id = k;
                    }
                    list.push(item);
                }
            }
            return list;
        }
        return [];
    }

    function shouldSkipManifestResourcePath(path) {
        if (!path || typeof path !== "string") {
            return false;
        }
        var clean = path.split("?")[0].split("#")[0].toLowerCase();
        return /\.php$/i.test(clean) ||
            /\.map$/i.test(clean) ||
            /\.s[ac]ss$/i.test(clean);
    }

    function filterManifestEntries(entries, config) {
        if (!Array.isArray(entries)) {
            return [];
        }
        state.resourceBytes = 0;
        state.wasmBinaryBytes = 0;
        state.wasmJsBytes = 0;
        state.jsIrBytes = 0;
        state.loadedBytes = 0;
        var filtered = [];
        for (var i = 0; i < entries.length; i++) {
            var item = entries[i];
            if (!item) {
                continue;
            }
            var itemType = String(item.type || item.resourceType || "").toLowerCase();
            var targetPath = item.path || item.url || item.src || item.href || "";
            var itemSize = (typeof item.size === "number" && item.size > 0) ? item.size : 0;
            if (itemType === "wasm" || /\.wasm(\?.*)?$/i.test(targetPath)) {
                if (itemSize > 0) {
                    state.wasmBinaryBytes = itemSize;
                }
                continue;
            }
            if (itemType === "js") {
                if (itemSize > 0) {
                    state.wasmJsBytes = itemSize;
                    state.jsIrBytes = itemSize;
                }
                continue;
            }
            if (targetPath && shouldSkipManifestResourcePath(targetPath) && !item.bytes && !item.dataUrl && !item.base64 && typeof item.text !== "string") {
                continue;
            }
            state.resourceBytes += itemSize;
            filtered.push(item);
        }
        var wasmSupported = state.wasmSupported !== null ? state.wasmSupported : detectWasmSupport();
        var useWasm = Boolean(wasmSupported && config && config.wasmJsUrl);
        var appBundleBytes = useWasm ? (state.wasmBinaryBytes + state.wasmJsBytes) : state.jsIrBytes;
        state.totalBytes = state.resourceBytes + appBundleBytes;
        return filtered;
    }

    function resolveManifestEntries(config) {
        if (global.__GENERICUI_RESOURCE_MANIFEST__) {
            var inlineEntries = filterManifestEntries(extractManifestEntries(global.__GENERICUI_RESOURCE_MANIFEST__), config);
            if (inlineEntries.length > 0) {
                return Promise.resolve(inlineEntries);
            }
        }
        var els = getDomElements();
        if (els.manifestScript && els.manifestScript.textContent) {
            var scriptText = els.manifestScript.textContent.replace(/^\s+|\s+$/g, "");
            if (scriptText.length > 0 && scriptText.indexOf("window.__GENERICUI_RESOURCE_MANIFEST__") === -1) {
                try {
                    var parsedInline = JSON.parse(scriptText);
                    global.__GENERICUI_RESOURCE_MANIFEST__ = parsedInline;
                    return Promise.resolve(filterManifestEntries(extractManifestEntries(parsedInline), config));
                } catch (parseErr) {
                    return Promise.reject(parseErr);
                }
            }
        }
        var linkHref = (els.resourcesLink && typeof els.resourcesLink.getAttribute === "function")
            ? cleanTemplateAttr(els.resourcesLink.getAttribute("href"))
            : "";
        var manifestUrl = (config && config.resourceManifestUrl)
            ? config.resourceManifestUrl
            : (linkHref || "./resources.json");
        if (typeof global.fetch !== "function" && typeof global.XMLHttpRequest !== "function") {
            return Promise.resolve([]);
        }
        return downloadWithProgress(manifestUrl, 0, 5, STATUS_LOADING_RESOURCES, 0)
            .then(function (bytes) {
                var jsonText = bytesToUtf8(bytes);
                if (!jsonText || !jsonText.replace(/^\s+|\s+$/g, "")) {
                    return [];
                }
                var parsed = JSON.parse(jsonText);
                global.__GENERICUI_RESOURCE_MANIFEST__ = parsed;
                return filterManifestEntries(extractManifestEntries(parsed), config);
            })
            .catch(function (err) {
                if (config && config.requireResourceManifest === true) {
                    throw err;
                }
                return downloadWithProgress("./gui/genericui-resources.json", 0, 5, STATUS_LOADING_RESOURCES, 0)
                    .then(function (bytes) {
                        var jsonText = bytesToUtf8(bytes);
                        if (!jsonText || !jsonText.replace(/^\s+|\s+$/g, "")) {
                            return [];
                        }
                        var parsed = JSON.parse(jsonText);
                        return filterManifestEntries(extractManifestEntries(parsed), config);
                    })
                    .catch(function () {
                        return [];
                    });
            });
    }

    function preloadSingleResource(entry, minPct, maxPct, config) {
        if (!entry) {
            updateProgress(maxPct, STATUS_LOADING_RESOURCES);
            return Promise.resolve(null);
        }
        var entrySize = (typeof entry.size === "number" && entry.size > 0) ? entry.size : 0;
        function advanceInlineBytes(bytesLen) {
            if (state.totalBytes > 0) {
                state.loadedBytes += entrySize > 0 ? entrySize : bytesLen;
                var pct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
                updateProgress(pct, STATUS_LOADING_RESOURCES);
            } else {
                updateProgress(maxPct, STATUS_LOADING_RESOURCES);
            }
        }
        if (entry.bytes instanceof Uint8Array) {
            var recBytes = registerPreloadedResourceEntry(entry, entry.bytes);
            advanceInlineBytes(entry.bytes.byteLength);
            return preloadRecordImageElement(recBytes);
        }
        if (Array.isArray(entry.bytes)) {
            var arrBytes = new Uint8Array(entry.bytes);
            var recArr = registerPreloadedResourceEntry(entry, arrBytes);
            advanceInlineBytes(arrBytes.byteLength);
            return preloadRecordImageElement(recArr);
        }
        if (typeof entry.dataUrl === "string" && entry.dataUrl.indexOf("data:") === 0) {
            var commaIdx = entry.dataUrl.indexOf(",");
            var payload = commaIdx >= 0 ? entry.dataUrl.substring(commaIdx + 1) : "";
            var decoded = entry.dataUrl.indexOf(";base64,") !== -1
                ? base64ToBytes(payload)
                : utf8ToBytes(decodeURIComponent(payload));
            var recDataUrl = registerPreloadedResourceEntry(entry, decoded);
            advanceInlineBytes(decoded.byteLength);
            return preloadRecordImageElement(recDataUrl);
        }
        if (typeof entry.base64 === "string" && entry.base64.length > 0) {
            var b64Bytes = base64ToBytes(entry.base64);
            var recB64 = registerPreloadedResourceEntry(entry, b64Bytes);
            advanceInlineBytes(b64Bytes.byteLength);
            return preloadRecordImageElement(recB64);
        }
        if (typeof entry.text === "string") {
            var txtBytes = utf8ToBytes(entry.text);
            var recText = registerPreloadedResourceEntry(entry, txtBytes);
            advanceInlineBytes(txtBytes.byteLength);
            return Promise.resolve(recText);
        }
        var rawTargetUrl = entry.path || entry.url || entry.src || entry.href;
        if (!rawTargetUrl) {
            var recEmpty = registerPreloadedResourceEntry(entry, new Uint8Array(0));
            advanceInlineBytes(0);
            return Promise.resolve(recEmpty);
        }
        var entryHash = entry.hash || entry.sha1 || entry.version || "";
        var targetUrl = buildHashedResourceUrl(rawTargetUrl, entryHash);
        return downloadWithProgress(targetUrl, minPct, maxPct, STATUS_LOADING_RESOURCES, entrySize)
            .then(function (downloadedBytes) {
                return registerPreloadedResourceEntry(entry, downloadedBytes);
            })
            .then(preloadRecordImageElement)
            .catch(function (err) {
                if (config && config.requireResourceManifest === true) {
                    throw err;
                }
                logDiagnostic("resource preload skipped for " + resolveAbsoluteUrl(targetUrl) + ": " + (err && err.message ? err.message : String(err)));
                if (global.console && typeof global.console.warn === "function") {
                    global.console.warn("[GenericUI Loader] Failed to preload resource " + resolveAbsoluteUrl(targetUrl) + "\n" + formatErrorDetails(err).stack, err);
                }
                advanceInlineBytes(0);
                return null;
            });
    }

    function preloadAllResources(config) {
        updateProgress(0, STATUS_LOADING_RESOURCES);
        return resolveManifestEntries(config).then(function (entries) {
            if (!entries || entries.length === 0) {
                if (state.totalBytes <= 0) {
                    updateProgress(50, STATUS_LOADING_RESOURCES);
                }
                return global.__GENERICUI_PRELOADED_RESOURCES__;
            }
            var count = entries.length;
            var nextIndex = 0;
            var completed = 0;
            var concurrency = Math.max(1, Math.min(6, count));

            function runWorker() {
                if (nextIndex >= count) {
                    return Promise.resolve();
                }
                var item = entries[nextIndex++];
                return preloadSingleResource(item, 5, 5, config).then(function () {
                    completed++;
                    if (state.totalBytes <= 0) {
                        updateProgress(5 + Math.floor((completed / count) * 45), STATUS_LOADING_RESOURCES);
                    }
                    return runWorker();
                });
            }

            var workers = [];
            for (var w = 0; w < concurrency; w++) {
                workers.push(runWorker());
            }
            return Promise.all(workers).then(function () {
                if (state.pendingResourcesCatalog) {
                    applyGenericUiResourcesCatalog(state.pendingResourcesCatalog);
                }
                if (state.totalBytes > 0) {
                    var resDonePct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
                    updateProgress(resDonePct, STATUS_LOADING_RESOURCES);
                } else {
                    updateProgress(50, STATUS_LOADING_RESOURCES);
                }
                return global.__GENERICUI_PRELOADED_RESOURCES__;
            });
        });
    }

    function installWasmHooks() {
        if (!global.__GENERICUI_WASM_FETCH_HOOKED__ && typeof global.fetch === "function") {
            global.__GENERICUI_WASM_FETCH_HOOKED__ = true;
            var originalFetch = global.fetch.bind(global);
            global.fetch = function (input, init) {
                var reqUrl = typeof input === "string"
                    ? input
                    : (input && typeof input.url === "string"
                        ? input.url
                        : (input && typeof input.href === "string" ? input.href : String(input || "")));
                var reqClean = reqUrl.split("?")[0];
                var reqFile = reqClean.split("/").pop();
                var isWasmRequest = /\.wasm$/i.test(reqClean);
                var cached = state.preloadedWasmBytes[reqUrl] ||
                    state.preloadedWasmBytes[reqClean] ||
                    (reqFile ? state.preloadedWasmBytes[reqFile] : null) ||
                    (isWasmRequest ? state.preloadedWasmBytes["*"] : null);
                if (cached && typeof global.Response === "function") {
                    logDiagnostic("fetch " + resolveAbsoluteUrl(reqUrl) + " -> served from preloaded Wasm bytes (" + cached.byteLength + " bytes)");
                    var slice = cached.buffer.slice(cached.byteOffset, cached.byteOffset + cached.byteLength);
                    return Promise.resolve(new global.Response(slice, {
                        status: 200,
                        headers: { "Content-Type": "application/wasm" }
                    }));
                }
                if (isWasmRequest) {
                    var preloadedNames = Object.keys(state.preloadedWasmBytes).join(", ") || "(none)";
                    logDiagnostic("fetch " + resolveAbsoluteUrl(reqUrl) + " -> not in preloaded Wasm cache (preloaded: " + preloadedNames + "); requesting from network");
                }
                function logWasmResponse(label, res) {
                    if (isWasmRequest && res) {
                        logDiagnostic(label + " " + (res.url || resolveAbsoluteUrl(reqUrl)) + " -> " + res.status + " " + (res.statusText || "") + " (" + (res.headers ? res.headers.get("Content-Type") : "?") + ")");
                    }
                    return res;
                }
                if (isWasmRequest) {
                    return originalFetch(input, init).then(function (res) {
                        return logWasmResponse("fetch", res);
                    });
                }
                return originalFetch(input, init);
            };
        }

        if (!global.__GENERICUI_WASM_COMPILE_HOOKED__ && typeof global.WebAssembly === "object" && global.WebAssembly !== null) {
            global.__GENERICUI_WASM_COMPILE_HOOKED__ = true;
            var wasmObj = global.WebAssembly;
            var methods = ["instantiateStreaming", "compileStreaming", "instantiate", "compile"];
            for (var i = 0; i < methods.length; i++) {
                (function (methodName) {
                    var orig = wasmObj[methodName];
                    if (typeof orig === "function") {
                        var boundOrig = orig.bind(wasmObj);
                        try {
                            wasmObj[methodName] = function () {
                                var args = arguments;
                                var source = args.length > 0 ? args[0] : undefined;
                                var sourceInfo = { method: "WebAssembly." + methodName };

                                function describeResponse(res) {
                                    if (res && typeof res === "object" && typeof res.status === "number") {
                                        sourceInfo.responseUrl = res.url || "(constructed Response, no URL)";
                                        sourceInfo.httpStatus = res.status + " " + (res.statusText || "");
                                        sourceInfo.contentType = res.headers ? res.headers.get("Content-Type") : "";
                                    }
                                }

                                if (source && typeof source.then === "function") {
                                    source.then(describeResponse, function (srcErr) {
                                        sourceInfo.sourcePromiseRejected = srcErr && srcErr.message ? srcErr.message : String(srcErr);
                                    });
                                } else if (source && typeof source.byteLength === "number") {
                                    sourceInfo.inputBytes = source.byteLength;
                                } else if (source && typeof source === "object" && typeof source.status === "number") {
                                    describeResponse(source);
                                } else if (source && typeof global.WebAssembly.Module === "function" && source instanceof global.WebAssembly.Module) {
                                    sourceInfo.input = "WebAssembly.Module";
                                }

                                logDiagnostic(sourceInfo.method + " called" + describeContext({ inputBytes: sourceInfo.inputBytes, input: sourceInfo.input }));
                                var p = boundOrig.apply(wasmObj, args);
                                if (p && typeof p.then === "function") {
                                    p.then(function () {
                                        logDiagnostic(sourceInfo.method + " succeeded" + describeContext({ responseUrl: sourceInfo.responseUrl, httpStatus: sourceInfo.httpStatus }));
                                    }, function (wasmErr) {
                                        logDiagnostic(sourceInfo.method + " failed: " + (wasmErr && wasmErr.name ? wasmErr.name + ": " : "") + (wasmErr && wasmErr.message ? wasmErr.message : String(wasmErr)) + describeContext(sourceInfo));
                                        if (wasmErr && typeof wasmErr === "object") {
                                            try {
                                                wasmErr.genericUiWasmContext = sourceInfo;
                                            } catch (annotateErr) {
                                            }
                                        }
                                    });
                                    if (state.loadingWasm) {
                                        p.catch(function () {});
                                        state.activeWasmPromises.push(p);
                                    }
                                }
                                return p;
                            };
                        } catch (e) {
                        }
                    }
                })(methods[i]);
            }
        }
    }

    function registerPreloadedWasmBinary(wasmUrl, wasmBytes) {
        if (!wasmUrl || !wasmBytes) {
            return;
        }
        installWasmHooks();
        var cleanUrl = wasmUrl.split("?")[0];
        var cleanName = cleanUrl.split("/").pop();
        state.preloadedWasmBytes[wasmUrl] = wasmBytes;
        state.preloadedWasmBytes[cleanUrl] = wasmBytes;
        state.preloadedWasmBytes["*"] = wasmBytes;
        if (cleanName) {
            state.preloadedWasmBytes[cleanName] = wasmBytes;
        }
        logDiagnostic("registered preloaded Wasm binary " + cleanName + " (" + wasmBytes.byteLength + " bytes) from " + resolveAbsoluteUrl(wasmUrl));
    }

    function snapshotGlobalPromiseExports(beforeKeys) {
        var found = [];
        try {
            var currentKeys = Object.keys(global);
            for (var i = 0; i < currentKeys.length; i++) {
                var k = currentKeys[i];
                if (!beforeKeys[k]) {
                    var val = global[k];
                    if (val && (typeof val === "object" || typeof val === "function") && typeof val.then === "function") {
                        val.catch(function () {});
                        found.push(val);
                    }
                }
            }
        } catch (e) {
        }
        return found;
    }

    function executeDownloadedScript(jsBytes, sourceUrl, isWasm) {
        return new Promise(function (resolve, reject) {
            var settled = false;
            function settleResolve() {
                if (!settled) {
                    settled = true;
                    state.onWasmAsyncFailure = null;
                    resolve();
                }
            }
            function settleReject(err) {
                if (!settled) {
                    settled = true;
                    state.onWasmAsyncFailure = null;
                    reject(err);
                }
            }

            if (isWasm) {
                state.activeWasmPromises = [];
                state.onWasmAsyncFailure = settleReject;
            }

            function finalizeExecution(beforeKeys) {
                if (!isWasm) {
                    settleResolve();
                    return;
                }
                var exportedPromises = snapshotGlobalPromiseExports(beforeKeys);
                var allPromises = state.activeWasmPromises.concat(exportedPromises);
                logDiagnostic("executed " + resolveAbsoluteUrl(sourceUrl) + "; awaiting " + allPromises.length + " Wasm initialization promise(s)");
                if (allPromises.length > 0) {
                    Promise.all(allPromises).then(function () {
                        logDiagnostic("Kotlin/Wasm module initialized successfully");
                        settleResolve();
                    }).catch(function (wasmErr) {
                        settleReject(createLoaderError(
                            "Kotlin/Wasm module failed to initialize after executing " + resolveAbsoluteUrl(sourceUrl),
                            wasmErr,
                            { phase: "Wasm initialization", glueScript: resolveAbsoluteUrl(sourceUrl) }
                        ));
                    });
                } else {
                    settleResolve();
                }
            }

            try {
                var code = bytesToUtf8(jsBytes);
                var els = getDomElements();
                if (!els.doc) {
                    settleResolve();
                    return;
                }

                var beforeKeys = {};
                if (isWasm) {
                    try {
                        var keys = Object.keys(global);
                        for (var i = 0; i < keys.length; i++) {
                            beforeKeys[keys[i]] = true;
                        }
                    } catch (e) {
                    }
                }

                var cleanUrl = sourceUrl.split("?")[0];
                var isEsModule = /\.mjs$/i.test(cleanUrl) ||
                    /(?:^|\n)\s*(?:import|export)\s+/m.test(code);

                if (isWasm && !isEsModule && sourceUrl) {
                    var wasmGlueScript = els.doc.createElement("script");
                    wasmGlueScript.src = sourceUrl;
                    wasmGlueScript.onload = function () {
                        finalizeExecution(beforeKeys);
                    };
                    wasmGlueScript.onerror = function () {
                        settleReject(createLoaderError(
                            "Failed to load Wasm glue script " + resolveAbsoluteUrl(sourceUrl) + " via <script src> (HTTP error, blocked by CSP, or network failure)",
                            null,
                            { phase: "Wasm glue script", scriptUrl: resolveAbsoluteUrl(sourceUrl) }
                        ));
                    };
                    logDiagnostic("executing Wasm glue script " + resolveAbsoluteUrl(sourceUrl) + " via <script src> (keeps webpack publicPath at the script's directory)");
                    (els.doc.body || els.doc.head || els.doc.documentElement).appendChild(wasmGlueScript);
                    return;
                }

                if (isEsModule && typeof global.Blob === "function" && global.URL && typeof global.URL.createObjectURL === "function") {
                    var blob = new global.Blob([code], { type: "text/javascript" });
                    var blobUrl = global.URL.createObjectURL(blob);
                    var modScript = els.doc.createElement("script");
                    modScript.type = "module";
                    modScript.src = blobUrl;
                    modScript.onload = function () {
                        global.URL.revokeObjectURL(blobUrl);
                        finalizeExecution(beforeKeys);
                    };
                    modScript.onerror = function () {
                        global.URL.revokeObjectURL(blobUrl);
                        settleReject(createLoaderError(
                            "Failed to execute ES module script " + resolveAbsoluteUrl(sourceUrl),
                            null,
                            { phase: isWasm ? "Wasm glue script" : "JS bundle", scriptUrl: resolveAbsoluteUrl(sourceUrl), bytes: jsBytes ? jsBytes.byteLength : 0 }
                        ));
                    };
                    (els.doc.body || els.doc.head || els.doc.documentElement).appendChild(modScript);
                    return;
                }

                if (code.length > 0) {
                    var inlineScript = els.doc.createElement("script");
                    inlineScript.type = "text/javascript";
                    inlineScript.text = code + "\n//# sourceURL=" + sourceUrl;
                    logDiagnostic("executing " + (isWasm ? "Wasm glue" : "JS bundle") + " " + resolveAbsoluteUrl(sourceUrl) + " inline (" + jsBytes.byteLength + " bytes)");
                    (els.doc.body || els.doc.head || els.doc.documentElement).appendChild(inlineScript);
                    finalizeExecution(beforeKeys);
                } else {
                    var extScript = els.doc.createElement("script");
                    extScript.src = sourceUrl;
                    extScript.onload = function () {
                        finalizeExecution(beforeKeys);
                    };
                    extScript.onerror = function () {
                        settleReject(createLoaderError(
                            "Failed to load script " + resolveAbsoluteUrl(sourceUrl) + " (downloaded body was empty and <script src> also failed)",
                            null,
                            { phase: isWasm ? "Wasm glue script" : "JS bundle", scriptUrl: resolveAbsoluteUrl(sourceUrl) }
                        ));
                    };
                    (els.doc.body || els.doc.head || els.doc.documentElement).appendChild(extScript);
                }
            } catch (err) {
                settleReject(createLoaderError(
                    "Exception while executing " + resolveAbsoluteUrl(sourceUrl),
                    err,
                    { phase: isWasm ? "Wasm glue script" : "JS bundle", scriptUrl: resolveAbsoluteUrl(sourceUrl) }
                ));
            }
        });
    }

    function cleanTemplateAttr(val) {
        if (!val || val.indexOf("{{") !== -1) {
            return "";
        }
        if (val.charAt(0) === "?") {
            return "";
        }
        return val;
    }

    function resolveLoaderConfig() {
        var cfg = global.GenericUiLoaderConfig || {};
        var els = getDomElements();
        var wasmTag = els.wasmDescriptor;
        var jsTag = els.jsDescriptor;
        var scriptEl = els.loaderScript;
        var splashEl = els.splash;

        function readTagAttr(el, attrName) {
            if (el && typeof el.getAttribute === "function") {
                return cleanTemplateAttr(el.getAttribute(attrName));
            }
            return "";
        }

        var rawWasmSrc = readTagAttr(wasmTag, "src");
        var isDirectWasmBinary = /\.wasm(\?.*)?$/i.test(rawWasmSrc);

        var jsIrUrl = cfg.jsIrUrl ||
            readTagAttr(jsTag, "src") ||
            readTagAttr(scriptEl, "data-js-ir-url") ||
            readTagAttr(splashEl, "data-js-ir-url") ||
            cfg.appBundleUrl ||
            readTagAttr(scriptEl, "data-app-bundle-url");

        var additionalJsUrls = [];
        if (els.doc && typeof els.doc.querySelectorAll === "function") {
            var allJsTags = els.doc.querySelectorAll('script[type="loader-js"]');
            for (var j = 1; j < allJsTags.length; j++) {
                var extraSrc = readTagAttr(allJsTags[j], "src");
                if (extraSrc) {
                    additionalJsUrls.push(extraSrc);
                }
            }
        }

        var wasmJsUrl = cfg.wasmJsUrl ||
            (isDirectWasmBinary ? jsIrUrl : rawWasmSrc) ||
            readTagAttr(scriptEl, "data-wasm-js-url") ||
            readTagAttr(splashEl, "data-wasm-js-url");

        var wasmBinaryUrl = cfg.wasmBinaryUrl ||
            readTagAttr(wasmTag, "data-wasm-binary") ||
            readTagAttr(wasmTag, "data-wasm-binary-src") ||
            (isDirectWasmBinary ? rawWasmSrc : "") ||
            readTagAttr(scriptEl, "data-wasm-binary-url") ||
            readTagAttr(splashEl, "data-wasm-binary-url");

        var resourcesLink = els.resourcesLink;
        var resourceManifestUrl = cfg.resourceManifestUrl ||
            readTagAttr(resourcesLink, "href") ||
            readTagAttr(scriptEl, "data-resource-manifest-url") ||
            readTagAttr(splashEl, "data-resource-manifest-url") ||
            "./resources.json";

        var fadeDurationMs = typeof cfg.fadeDurationMs === "number"
            ? cfg.fadeDurationMs
            : (parseInt(readTagAttr(scriptEl, "data-fade-duration-ms") || readTagAttr(splashEl, "data-fade-duration-ms"), 10) || 350);

        return {
            wasmJsUrl: wasmJsUrl,
            wasmBinaryUrl: wasmBinaryUrl,
            jsIrUrl: jsIrUrl,
            additionalJsUrls: additionalJsUrls,
            resourceManifestUrl: resourceManifestUrl,
            requireResourceManifest: cfg.requireResourceManifest === true,
            fadeDurationMs: fadeDurationMs
        };
    }

    function loadAdditionalJsScriptsSequentially(additionalUrls) {
        if (!additionalUrls || additionalUrls.length === 0) {
            return Promise.resolve();
        }
        var chain = Promise.resolve();
        for (var i = 0; i < additionalUrls.length; i++) {
            (function (scriptUrl) {
                chain = chain.then(function () {
                    return downloadWithProgress(scriptUrl, 95, 99, STATUS_LOADING_APPLICATION, 0)
                        .then(function (scriptBytes) {
                            return executeDownloadedScript(scriptBytes, scriptUrl, false);
                        });
                });
            })(additionalUrls[i]);
        }
        return chain;
    }

    function loadJsIrFallback(config, statusOverride, priorWasmError) {
        if (!config.jsIrUrl) {
            throw createLoaderError(
                "Neither a supported WebAssembly bundle nor a fallback JS IR bundle URL was configured.",
                priorWasmError || null,
                { wasmJsUrl: config.wasmJsUrl, wasmBinaryUrl: config.wasmBinaryUrl, jsIrUrl: config.jsIrUrl }
            );
        }
        var statusMsg = statusOverride || STATUS_LOADING_APPLICATION;
        state.loadingWasm = false;
        state.runtimeTarget = "js-ir";
        global.__GENERICUI_WEB_RUNTIME__ = "js-ir";
        logDiagnostic("loading JS IR bundle " + resolveAbsoluteUrl(config.jsIrUrl) + (priorWasmError ? " (fallback after WebAssembly failure)" : ""));
        if (state.resourceBytes + state.jsIrBytes > 0) {
            state.totalBytes = state.resourceBytes + state.jsIrBytes;
            state.loadedBytes = state.resourceBytes;
            var jsStartPct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
            updateProgress(jsStartPct, statusMsg);
        } else {
            updateProgress(50, statusMsg);
        }
        return downloadWithProgress(config.jsIrUrl, 55, 95, statusMsg, state.jsIrBytes)
            .then(function (jsBytes) {
                updateProgress(99, statusMsg);
                return executeDownloadedScript(jsBytes, config.jsIrUrl, false);
            })
            .catch(function (jsErr) {
                if (priorWasmError) {
                    var combined = createLoaderError(
                        "Both the WebAssembly bundle and the JS fallback bundle failed to load",
                        jsErr,
                        { jsIrUrl: resolveAbsoluteUrl(config.jsIrUrl), wasmJsUrl: resolveAbsoluteUrl(config.wasmJsUrl || "") }
                    );
                    combined.suppressed = [priorWasmError];
                    throw combined;
                }
                throw jsErr;
            });
    }

    function loadWasmPrimaryWithFallback(config) {
        var canFallback = Boolean(config.jsIrUrl && config.jsIrUrl !== config.wasmJsUrl);
        state.canFallbackToJs = canFallback;
        logDiagnostic("config" + describeContext({
            wasmJsUrl: config.wasmJsUrl ? resolveAbsoluteUrl(config.wasmJsUrl) : "",
            wasmBinaryUrl: config.wasmBinaryUrl ? resolveAbsoluteUrl(config.wasmBinaryUrl) : "",
            jsIrUrl: config.jsIrUrl ? resolveAbsoluteUrl(config.jsIrUrl) : "",
            canFallbackToJs: canFallback
        }));
        var wasmSupported = state.wasmSupported !== null ? state.wasmSupported : detectWasmSupport();
        logDiagnostic("WebAssembly + WasmGC support detected: " + wasmSupported);
        if (wasmSupported && config.wasmJsUrl) {
            installWasmHooks();
            state.loadingWasm = true;
            state.runtimeTarget = "wasm";
            global.__GENERICUI_WEB_RUNTIME__ = "wasm";
            if (state.totalBytes > 0) {
                var wasmStartPct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
                updateProgress(wasmStartPct, STATUS_LOADING_APPLICATION);
            } else {
                updateProgress(50, STATUS_LOADING_APPLICATION);
            }

            var wasmChain = Promise.resolve();
            if (config.wasmBinaryUrl) {
                wasmChain = downloadWithProgress(config.wasmBinaryUrl, 50, 80, STATUS_LOADING_APPLICATION, state.wasmBinaryBytes)
                    .then(function (wasmBytes) {
                        registerPreloadedWasmBinary(config.wasmBinaryUrl, wasmBytes);
                    });
            }

            return wasmChain
                .then(function () {
                    var startPct = config.wasmBinaryUrl ? 80 : 55;
                    return downloadWithProgress(config.wasmJsUrl, startPct, 95, STATUS_LOADING_APPLICATION, state.wasmJsBytes);
                })
                .then(function (wasmJsBytes) {
                    updateProgress(99, STATUS_LOADING_APPLICATION);
                    return executeDownloadedScript(wasmJsBytes, config.wasmJsUrl, true);
                })
                .then(function () {
                    state.loadingWasm = false;
                })
                .catch(function (wasmErr) {
                    state.loadingWasm = false;
                    logWasmFallbackErrorSilently(wasmErr);
                    if (canFallback && !state.hasError) {
                        state.currentProgress = state.totalBytes > 0
                            ? Math.min(99, Math.round((state.resourceBytes / Math.max(1, state.resourceBytes + state.jsIrBytes)) * 100))
                            : 50;
                        updateProgress(state.currentProgress, STATUS_LOADING_FALLBACK);
                        return loadJsIrFallback(config, STATUS_LOADING_FALLBACK, wasmErr);
                    }
                    throw wasmErr;
                });
        }

        if (config.wasmJsUrl && canFallback) {
            var unsupportedErr = createLoaderError(
                "WebAssembly (with WasmGC) is not supported in this browser; loading JS fallback.",
                null,
                { userAgent: global.navigator ? global.navigator.userAgent : "" }
            );
            logWasmFallbackErrorSilently(unsupportedErr);
            state.currentProgress = state.totalBytes > 0
                ? Math.min(99, Math.round((state.resourceBytes / Math.max(1, state.resourceBytes + state.jsIrBytes)) * 100))
                : 50;
            updateProgress(state.currentProgress, STATUS_LOADING_FALLBACK);
            return loadJsIrFallback(config, STATUS_LOADING_FALLBACK, unsupportedErr);
        }

        return loadJsIrFallback(config, STATUS_LOADING_APPLICATION, null);
    }

    function errorFromEvent(event) {
        if (event && event.error) {
            var e = event.error;
            if (e && typeof e === "object" && !e.genericUiContext && (event.filename || event.lineno)) {
                try {
                    e.genericUiContext = {
                        source: (event.filename || "?") + ":" + (event.lineno || 0) + ":" + (event.colno || 0)
                    };
                } catch (annotateErr) {
                }
            }
            return e;
        }
        return createLoaderError(
            (event && event.message ? event.message : "Unhandled runtime error (no error object; possibly a cross-origin script error)"),
            null,
            {
                source: event && event.filename ? event.filename + ":" + (event.lineno || 0) + ":" + (event.colno || 0) : "",
                target: event && event.target && event.target.tagName ? event.target.tagName + " " + (event.target.src || event.target.href || "") : ""
            }
        );
    }

    function installGlobalErrorListeners() {
        if (typeof global.addEventListener !== "function") {
            return;
        }
        global.addEventListener("error", function (event) {
            var errObj = errorFromEvent(event);
            logDiagnostic("window error event: " + (errObj && errObj.message ? errObj.message : String(errObj)));
            if (state.loadingWasm && state.canFallbackToJs && typeof state.onWasmAsyncFailure === "function") {
                if (event && typeof event.preventDefault === "function") {
                    event.preventDefault();
                }
                state.onWasmAsyncFailure(errObj);
                return;
            }
            showError(errObj, STATUS_ERROR);
        });
        global.addEventListener("unhandledrejection", function (event) {
            var reason = event && event.reason !== undefined && event.reason !== null
                ? event.reason
                : createLoaderError("Unhandled Promise rejection with no reason value", null, null);
            logDiagnostic("unhandled rejection: " + (reason && reason.message ? reason.message : String(reason)));
            if (state.loadingWasm && state.canFallbackToJs && typeof state.onWasmAsyncFailure === "function") {
                if (event && typeof event.preventDefault === "function") {
                    event.preventDefault();
                }
                state.onWasmAsyncFailure(reason);
                return;
            }
            showError(reason, STATUS_ERROR);
        });
    }

    function ensureRootContainer() {
        var doc = global.document;
        if (!doc) {
            return null;
        }
        var root = (typeof doc.getElementsByTagName === "function" && doc.getElementsByTagName("genericui:app")[0]) ||
            (typeof doc.querySelector === "function" ? doc.querySelector("genericui\\:app") : null);
        if (!root && doc.body && typeof doc.createElement === "function") {
            root = doc.createElement("genericui:app");
            doc.body.appendChild(root);
        }
        if (root && typeof root.removeAttribute === "function") {
            root.removeAttribute("id");
            root.removeAttribute("data-bs-theme");
        }
        return root;
    }

    function registerServiceWorkerIfEnabled() {
        var doc = global.document;
        if (!doc || !global.navigator || !("serviceWorker" in global.navigator)) {
            return;
        }
        var manifestLink = typeof doc.getElementById === "function" ? doc.getElementById("genericui-web-manifest") : null;
        if (manifestLink && global.navigator.serviceWorker && typeof global.navigator.serviceWorker.register === "function") {
            try {
                var p = global.navigator.serviceWorker.register('./gui/genericui-sw.js');
                if (p && typeof p.catch === "function") {
                    p.catch(function () {});
                }
            } catch (e) {
            }
        }
    }

    function startLoader() {
        applyInitialBrowserTheme();
        installGlobalErrorListeners();
        ensureRootContainer();
        registerServiceWorkerIfEnabled();
        var config = resolveLoaderConfig();

        updateProgress(0, STATUS_INITIALIZING);
        state.wasmSupported = detectWasmSupport();
        return preloadAllResources(config)
            .then(function () {
                if (!config.wasmJsUrl && !config.jsIrUrl) {
                    return loadAdditionalJsScriptsSequentially(config.additionalJsUrls);
                }
                if (state.totalBytes <= 0) {
                    updateProgress(50, STATUS_LOADING_APPLICATION);
                } else {
                    var afterResPct = Math.min(99, Math.round((state.loadedBytes / state.totalBytes) * 100));
                    updateProgress(afterResPct, STATUS_LOADING_APPLICATION);
                }
                return loadWasmPrimaryWithFallback(config)
                    .then(function () {
                        return loadAdditionalJsScriptsSequentially(config.additionalJsUrls);
                    });
            })
            .catch(function (err) {
                showError(err, STATUS_ERROR);
            });
    }

    global.GenericUiSplash = {
        STATUS_INITIALIZING: STATUS_INITIALIZING,
        STATUS_LOADING_RESOURCES: STATUS_LOADING_RESOURCES,
        STATUS_LOADING_APPLICATION: STATUS_LOADING_APPLICATION,
        STATUS_LOADING_FALLBACK: STATUS_LOADING_FALLBACK,
        STATUS_ERROR: STATUS_ERROR,
        STATUS_READY: STATUS_READY,
        ALLOWED_STATUS_MESSAGES: [
            STATUS_INITIALIZING,
            STATUS_LOADING_RESOURCES,
            STATUS_LOADING_APPLICATION,
            STATUS_LOADING_FALLBACK,
            STATUS_ERROR,
            STATUS_READY
        ],
        normalizeStatusMessage: normalizeStatusMessage,
        detectWasmSupport: detectWasmSupport,
        ensureRootContainer: ensureRootContainer,
        preloadAllResources: preloadAllResources,
        buildHashedResourceUrl: buildHashedResourceUrl,
        registerPreloadedResource: registerPreloadedResourceEntry,
        getPreloadedResource: getPreloadedResource,
        updateProgress: updateProgress,
        setStatus: setStatus,
        setProgress: setProgress,
        setError: setError,
        setApplicationStatus: setApplicationStatus,
        finish: fadeOutSplash,
        showError: showError,
        start: startLoader,
        getRuntimeTarget: function () {
            return state.runtimeTarget;
        }
    };

    if (global.document) {
        if (global.document.readyState === "loading") {
            global.document.addEventListener("DOMContentLoaded", function () {
                startLoader();
            });
        } else {
            startLoader();
        }
    }
})(typeof window !== "undefined" ? window : globalThis);
""".trimIndent()

        const val WEB_TEST_HARNESS_JS_SOURCE: String = """(function (global) {
    "use strict";
    if (global.__GENERICUI_WEB_TEST_HARNESS_INSTALLED__) {
        return;
    }
    global.__GENERICUI_WEB_TEST_HARNESS_INSTALLED__ = true;

    var startTime = Date.now();
    var capturedErrors = [];

    if (typeof global.addEventListener === "function") {
        global.addEventListener("error", function (ev) {
            var msg = (ev && ev.message) ? String(ev.message) : "Unhandled window error";
            var stack = (ev && ev.error && ev.error.stack) ? String(ev.error.stack) : msg;
            capturedErrors.push({ name: "window.onerror", message: msg, stack: stack });
        });
        global.addEventListener("unhandledrejection", function (ev) {
            var reason = ev ? ev.reason : null;
            var msg = (reason && reason.message) ? String(reason.message) : String(reason || "Unhandled Promise rejection");
            var stack = (reason && reason.stack) ? String(reason.stack) : msg;
            capturedErrors.push({ name: "unhandledrejection", message: msg, stack: stack });
        });
    }

    function finalizeReportIfPending() {
        if (global.__GENERICUI_TEST_REPORT_JSON__) {
            return;
        }
        var results = Array.isArray(global.__GENERICUI_TEST_RESULTS__) ? global.__GENERICUI_TEST_RESULTS__ : [];
        var passed = 0;
        var failed = 0;
        var failures = [];
        for (var i = 0; i < results.length; i++) {
            var r = results[i];
            if (r && r.status === "FAILED") {
                failed++;
                failures.push({
                    suite: r.suite || "WebTestSuite",
                    name: r.name || ("test_" + i),
                    message: r.message || "Test failed",
                    stack: r.stack || ""
                });
            } else {
                passed++;
            }
        }
        for (var e = 0; e < capturedErrors.length; e++) {
            failed++;
            failures.push({
                suite: "WebTestHarness",
                name: capturedErrors[e].name,
                message: capturedErrors[e].message,
                stack: capturedErrors[e].stack
            });
        }
        var report = {
            status: failed > 0 ? "FAILED" : "PASSED",
            total: passed + failed,
            passed: passed,
            failed: failed,
            durationMs: Date.now() - startTime,
            failures: failures
        };
        global.__GENERICUI_TEST_REPORT_JSON__ = JSON.stringify(report);
    }

    global.__GENERICUI_FINALIZE_WEB_TEST_REPORT__ = finalizeReportIfPending;
    if (typeof global.setTimeout === "function") {
        global.setTimeout(finalizeReportIfPending, 50);
    } else {
        finalizeReportIfPending();
    }
})(typeof window !== "undefined" ? window : globalThis);
"""

        const val DEFAULT_LOADING_SVG_SOURCE: String = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 90" width="160" height="90">
  <defs>
    <linearGradient id="guiLoadingBg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#1E2530"/>
      <stop offset="50%" stop-color="#283242"/>
      <stop offset="100%" stop-color="#1A2029"/>
    </linearGradient>
    <linearGradient id="guiLoadingRing" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#4FA3FF" stop-opacity="0.95"/>
      <stop offset="100%" stop-color="#4FA3FF" stop-opacity="0.15"/>
    </linearGradient>
  </defs>
  <rect width="160" height="90" rx="8" ry="8" fill="url(#guiLoadingBg)"/>
  <circle cx="80" cy="45" r="14" fill="none" stroke="#364357" stroke-width="3"/>
  <path d="M 80 31 A 14 14 0 0 1 94 45" fill="none" stroke="url(#guiLoadingRing)" stroke-width="3" stroke-linecap="round">
    <animateTransform attributeName="transform" type="rotate" from="0 80 45" to="360 80 45" dur="0.9s" repeatCount="indefinite"/>
  </path>
</svg>
"""
    }
}
