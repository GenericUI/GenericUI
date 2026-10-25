package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
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
@CacheableTask
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

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun packageWebDistribution() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        val rootDir = File(projectRootPath.get())
        val buildDir = File(projectBuildPath.get())
        val resolvedBase = baseName.get().trim().ifEmpty { "app" }
        val resolvedTitle = appName.get().trim().ifEmpty { resolvedBase }

        // 1. Resolve Web Icon (preferring explicit webIcon and .svg icons, falling back to project icons, then GenericUI default icon.svg)
        val iconFile = resolveWebIconFile(rootDir, outDir)
        val stagedIconName = if (iconFile != null && iconFile.exists()) {
            val targetIcon = File(outDir, iconFile.name)
            if (iconFile.canonicalFile != targetIcon.canonicalFile) {
                iconFile.copyTo(targetIcon, overwrite = true)
            }
            iconFile.name
        } else {
            val defaultSvg = GenericUiDefaultIconProvider.ensureDefaultSvgIcon(rootDir, outDir)
            defaultSvg.name
        }

        // 2. Stage compiled WasmJS artifacts & compute SHA-1 hashes
        val wasmSubDir = File(outDir, "wasm").apply { mkdirs() }
        val wasmDirs = listOf(
            File(buildDir, "dist/wasmJs/productionExecutable"),
            File(buildDir, "dist/wasmJs/developmentExecutable"),
            File(buildDir, "compileSync/wasmJs/main/productionExecutable/kotlin"),
            File(buildDir, "compileSync/wasmJs/main/developmentExecutable/kotlin")
        ).filter { it.exists() && it.isDirectory }
            .sortedByDescending { dir ->
                dir.listFiles()?.filter { it.isFile }?.maxOfOrNull { it.lastModified() } ?: 0L
            }

        var stagedWasmJsRelPath = "wasm/$resolvedBase-wasm-js.js"
        var stagedWasmJsSha1 = sha1HexOfString(stagedWasmJsRelPath)
        var stagedWasmBinaryRelPath: String? = null
        var stagedWasmBinarySha1: String? = null

        for (dir in wasmDirs) {
            wasmSubDir.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile) oldFile.delete()
            }
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != "index.html") {
                    val dst = File(wasmSubDir, f.name)
                    f.copyTo(dst, overwrite = true)
                }
            }
            val wasmFiles = wasmSubDir.listFiles()?.filter { it.isFile }.orEmpty()
            val primaryWasmBin = wasmFiles.firstOrNull { it.extension.equals("wasm", ignoreCase = true) }
            if (primaryWasmBin != null) {
                stagedWasmBinaryRelPath = "wasm/${primaryWasmBin.name}"
                stagedWasmBinarySha1 = sha1HexOfFile(primaryWasmBin)
            }
            val primaryWasmJs = wasmFiles.firstOrNull {
                it.name == "$resolvedBase.js" || it.name == "$resolvedBase-wasm-js.js"
            } ?: wasmFiles.firstOrNull {
                it.extension in setOf("js", "mjs") &&
                    !it.name.endsWith(".uninstantiated.mjs") &&
                    it.name != "genericui-loader.js"
            } ?: wasmFiles.firstOrNull { it.extension in setOf("js", "mjs") }
            if (primaryWasmJs != null) {
                stagedWasmJsRelPath = "wasm/${primaryWasmJs.name}"
                stagedWasmJsSha1 = sha1HexOfFile(primaryWasmJs)
            }
            break
        }

        // 3. Stage compiled JS IR fallback artifacts & compute SHA-1 hash
        val jsSubDir = File(outDir, "js").apply { mkdirs() }
        val jsDirs = listOf(
            File(buildDir, "dist/js/productionExecutable"),
            File(buildDir, "dist/js/developmentExecutable"),
            File(buildDir, "compileSync/js/main/productionExecutable/kotlin"),
            File(buildDir, "compileSync/js/main/developmentExecutable/kotlin")
        ).filter { it.exists() && it.isDirectory }
            .sortedByDescending { dir ->
                dir.listFiles()?.filter { it.isFile }?.maxOfOrNull { it.lastModified() } ?: 0L
            }

        var stagedJsIrRelPath = "js/$resolvedBase.js"
        var stagedJsIrSha1 = sha1HexOfString(stagedJsIrRelPath)

        for (dir in jsDirs) {
            jsSubDir.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile) oldFile.delete()
            }
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != "index.html") {
                    val dst = File(jsSubDir, f.name)
                    f.copyTo(dst, overwrite = true)
                }
            }
            val jsFiles = jsSubDir.listFiles()?.filter {
                it.isFile && it.extension == "js" && it.name != "genericui-loader.js"
            }.orEmpty()
            val primaryJsIr = jsFiles.firstOrNull { it.name == "$resolvedBase.js" } ?: jsFiles.firstOrNull()
            if (primaryJsIr != null) {
                stagedJsIrRelPath = "js/${primaryJsIr.name}"
                stagedJsIrSha1 = sha1HexOfFile(primaryJsIr)
            }
            break
        }

        // 4. Stage compiled SASS/CSS bundle if present
        val cssBundleName = webCssBundleFileName.orNull?.trim().orEmpty().ifEmpty { "genericui-styles.css" }
        val cssCandidate = File(buildDir, "generated/genericui/web-css/$cssBundleName")
        val extraStylesheetTag = if (cssCandidate.exists() && cssCandidate.isFile) {
            val dstCss = File(outDir, cssBundleName)
            cssCandidate.copyTo(dstCss, overwrite = true)
            val cssHash = sha1HexOfFile(dstCss)
            """    <link id="genericui-compiled-sass-css" rel="stylesheet" href="$cssBundleName?$cssHash">"""
        } else {
            ""
        }

        // 5. Write genericui-loader.js, genericui-namespace.xml, and genericui.css and compute their SHA-1 hashes
        val loaderFile = File(outDir, "genericui-loader.js")
        loaderFile.writeText(LOADER_JS_SOURCE, Charsets.UTF_8)
        val loaderSha1 = sha1Hex(loaderFile)

        val namespaceXmlFile = File(outDir, "genericui-namespace.xml")
        namespaceXmlFile.writeText(NAMESPACE_XML_SOURCE, Charsets.UTF_8)
        val namespaceXmlSha1 = sha1Hex(namespaceXmlFile)

        val genericUiCssFile = File(outDir, "genericui.css")
        genericUiCssFile.writeText(GENERICUI_CSS_SOURCE, Charsets.UTF_8)
        val genericUiCssHash = sha1Hex(genericUiCssFile)

        // 6. Generate W3C / MDN Progressive Web App manifest.webmanifest if configured
        val isInstallableWebApp = allowInstallAsWebApp.getOrElse(false)
        val isManifestActive = manifestEnabled.getOrElse(false) || isInstallableWebApp
        val resolvedThemeColor = manifestThemeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: themeColor.orNull?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedLang = manifestLang.orNull?.trim().orEmpty().ifEmpty { "en" }

        val webManifestAndPwaLinks = if (isManifestActive) {
            val resolvedManifestFileName = manifestFileName.orNull?.trim().orEmpty().ifEmpty { "manifest.webmanifest" }
            val manifestJson = buildManifestJson(
                rootDir = rootDir,
                outDir = outDir,
                defaultTitle = resolvedTitle,
                stagedIconName = stagedIconName,
                resolvedThemeColor = resolvedThemeColor ?: "#0d6efd"
            )
            val manifestFile = File(outDir, resolvedManifestFileName)
            manifestFile.writeText(manifestJson, Charsets.UTF_8)
            val manifestSha1 = sha1HexOfFile(manifestFile)

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

        // 7. Generate index.html from template
        val faviconLink = if (stagedIconName != null) {
            val mimeType = inferIconMimeType(stagedIconName)
            """    <link id="genericui-favicon" rel="icon" type="$mimeType" href="$stagedIconName">"""
        } else {
            ""
        }

        val splashIconHtml = if (stagedIconName != null) {
            """            <img id="genericui-splash-icon" src="$stagedIconName" alt="Application Icon" class="genericui-splash-icon mb-3 rounded" width="64" height="64" style="width: 64px; height: 64px; object-fit: contain;">"""
        } else {
            """            <i id="genericui-splash-icon" class="bi bi-layers-half fs-1 text-primary mb-3" aria-hidden="true"></i>"""
        }

        val wasmBinaryAttr = if (stagedWasmBinaryRelPath != null && stagedWasmBinarySha1 != null) {
            """ data-wasm-binary="$stagedWasmBinaryRelPath?$stagedWasmBinarySha1""""
        } else {
            ""
        }

        val indexHtml = INDEX_HTML_TEMPLATE
            .replace("{{LANG}}", escapeHtml(resolvedLang))
            .replace("{{TITLE}}", escapeHtml(resolvedTitle))
            .replace("{{THEME_MODE}}", "dark")
            .replace("{{NAMESPACE_XML_URL}}", "genericui-namespace.xml")
            .replace("{{NAMESPACE_XML_HASH}}", namespaceXmlSha1)
            .replace("{{GENERICUI_CSS_URL}}", "genericui.css")
            .replace("{{GENERICUI_CSS_HASH}}", genericUiCssHash)
            .replace("{{NAMESPACE_CSS}}", NAMESPACE_CSS_SOURCE)
            .replace("{{FAVICON_LINK}}", faviconLink)
            .replace("{{WEB_MANIFEST_AND_PWA_LINKS}}", webManifestAndPwaLinks)
            .replace("{{SPLASH_ICON}}", splashIconHtml)
            .replace("{{SPLASH_STATUS}}", "Loading application...")
            .replace("{{SPLASH_PROGRESS_BAR_CLASS}}", "progress-bar progress-bar-striped progress-bar-animated")
            .replace("{{ROOT_CONTAINER_ID}}", "genericui-app")
            .replace("{{BOOTSTRAP_CSS_URL}}", "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css")
            .replace("{{BOOTSTRAP_CSS_INTEGRITY}}", "sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH")
            .replace("{{BOOTSTRAP_JS_URL}}", "https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js")
            .replace("{{BOOTSTRAP_JS_INTEGRITY}}", "sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz")
            .replace("{{CROSSORIGIN}}", "anonymous")
            .replace(
                "{{BOOTSTRAP_ICONS_LINK}}",
                """    <link id="genericui-bootstrap-icons-css" rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" crossorigin="anonymous">"""
            )
            .replace("{{EXTRA_STYLESHEETS}}", extraStylesheetTag)
            .replace("{{EXTRA_SCRIPTS}}", "")
            .replace("{{WASM_URL}}", stagedWasmJsRelPath)
            .replace("{{WASM_HASH}}", stagedWasmJsSha1)
            .replace("{{WASM_BINARY_ATTR}}", wasmBinaryAttr)
            .replace("{{JS_URL}}", stagedJsIrRelPath)
            .replace("{{JS_HASH}}", stagedJsIrSha1)
            .replace("{{LOADER_JS_URL}}", "genericui-loader.js")
            .replace("{{LOADER_JS_HASH}}", loaderSha1)
            .lineSequence()
            .filter { line -> line.isNotBlank() || !line.contains("{{") }
            .joinToString("\n")

        File(outDir, "index.html").writeText(indexHtml, Charsets.UTF_8)
    }

    private fun resolveWebIconFile(rootDir: File, outDir: File): File? {
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
        return GenericUiDefaultIconProvider.ensureDefaultSvgIcon(rootDir, outDir)
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
        resolvedThemeColor: String
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

        val effectiveIcons: List<GenericUiWebManifestIconSpec> = if (customIcons.isNotEmpty()) {
            customIcons.map { spec ->
                val localFile = File(spec.src).let { if (it.isAbsolute) it else File(rootDir, spec.src) }
                val finalSrc = if (localFile.exists() && localFile.isFile) {
                    val target = File(outDir, localFile.name)
                    if (localFile.canonicalFile != target.canonicalFile) {
                        localFile.copyTo(target, overwrite = true)
                    }
                    localFile.name
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
        return "{\n" + lines.joinToString(",\n") + "\n}\n"
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
    <link id="genericui-css" rel="stylesheet" href="{{GENERICUI_CSS_URL}}?{{GENERICUI_CSS_HASH}}">
{{FAVICON_LINK}}
{{WEB_MANIFEST_AND_PWA_LINKS}}
    <link id="genericui-bootstrap-css" rel="stylesheet" href="{{BOOTSTRAP_CSS_URL}}" integrity="{{BOOTSTRAP_CSS_INTEGRITY}}" crossorigin="{{CROSSORIGIN}}">
{{BOOTSTRAP_ICONS_LINK}}
{{EXTRA_STYLESHEETS}}
</head>
<body class="bg-body text-body m-0 p-0">
    <div id="genericui-splash" class="genericui-splash position-fixed top-0 start-0 w-100 h-100 d-flex flex-column align-items-center justify-content-center bg-body text-body p-4" style="z-index: 2000; transition: opacity 0.35s ease, visibility 0.35s ease;">
        <div class="d-flex flex-column align-items-center justify-content-center w-100" style="max-width: 420px;">
{{SPLASH_ICON}}
            <div id="genericui-splash-title" class="fw-semibold fs-5 mb-2 text-center">{{TITLE}}</div>
            <div id="genericui-splash-status" class="genericui-splash-status text-body-secondary small mb-3 text-center">{{SPLASH_STATUS}}</div>
            <div id="genericui-splash-progress" class="progress w-100 mb-2" style="height: 8px;" role="progressbar" aria-label="Application loading progress" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0">
                <div id="genericui-splash-progress-bar" class="{{SPLASH_PROGRESS_BAR_CLASS}}" style="width: 0%" aria-valuenow="0"></div>
            </div>
            <div id="genericui-splash-error" class="genericui-splash-error alert alert-danger d-none text-start w-100 mt-3 mb-0 shadow-sm" role="alert">
                <div class="d-flex align-items-center mb-2">
                    <i class="bi bi-exclamation-triangle-fill me-2" aria-hidden="true"></i>
                    <strong id="genericui-splash-error-title">Application Error</strong>
                </div>
                <div id="genericui-splash-error-message" class="genericui-splash-error-message small fw-semibold mb-2"></div>
                <pre id="genericui-splash-error-stack" class="genericui-splash-error-stack small mb-0 p-2 bg-dark text-light rounded overflow-auto" style="max-height: 240px; white-space: pre-wrap; word-break: break-word;"></pre>
            </div>
        </div>
    </div>
    <genericui:root></genericui:root>
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

genericui\:root {
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
genericui\:scrollfragment,
genericui\:root.scroll-fragment {
    display: block;
    overflow: auto;
    box-sizing: border-box;
}

/* Top-level MenuBar & Bootstrap Dropdown Menus inside genericui:root */
genericui\:root > genericui\:menubar,
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
genericui\:avatar,
genericui\:currencypursebar {
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
}"""

        const val NAMESPACE_CSS_SOURCE: String = """genericui\:root, genericui\:component, genericui\:fragment, genericui\:fragment-container, genericui\:scrollfragment, genericui\:container, genericui\:box, genericui\:col, genericui\:scrollview, genericui\:stepscrollview, genericui\:lazylist, genericui\:lazycolumn, genericui\:lazygrid, genericui\:lazygridtree, genericui\:treeview, genericui\:collapsiblepanel, genericui\:accordion, genericui\:accordionitem, genericui\:jumbotron, genericui\:well, genericui\:listgroupitem, genericui\:tabcontainer, genericui\:formgroup, genericui\:radiogroup, genericui\:markdownview, genericui\:alert, genericui\:toast, genericui\:skeletonplaceholder, genericui\:modal, genericui\:popover, genericui\:backdrop, genericui\:dialogwindow, genericui\:dockpanel, genericui\:panelwindow, genericui\:categorizedtreeview, genericui\:menuitem, genericui\:checkmenuitem, genericui\:radiomenuitem, genericui\:menuseparator, genericui\:contextmenu, genericui\:mdidesktop { display: block; box-sizing: border-box; }
genericui\:window, genericui\:menubar, genericui\:splitpane, genericui\:titlebar, genericui\:statusbar, genericui\:mdicontainer, genericui\:mdiwindow, genericui\:dockworkspace, genericui\:dockarea, genericui\:dockgroup, genericui\:row, genericui\:flexbox, genericui\:hstack, genericui\:vstack, genericui\:lazyrow, genericui\:card, genericui\:listgroup, genericui\:navbar, genericui\:nav, genericui\:sidenav, genericui\:bottomnavigationbar, genericui\:breadcrumb, genericui\:pagination, genericui\:stepper, genericui\:toolbar, genericui\:inputgroup, genericui\:compoundunitinput, genericui\:filedropzone, genericui\:masterdetailstepselect, genericui\:horizontalcarouselselector, genericui\:banner, genericui\:toastcontainer, genericui\:progressbar, genericui\:drawer, genericui\:bottomsheet { display: flex; box-sizing: border-box; }
genericui\:toolpaletteitem, genericui\:colorswatchbox, genericui\:breadcrumbitem, genericui\:buttongroup, genericui\:avatar, genericui\:currencypursebar { display: inline-flex; box-sizing: border-box; }
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
        <element name="root" tag="genericui:root" componentClass="GenericUiApplication" category="core" display="block" role="application" description="Root GenericUI application viewport container."/>
        <element name="component" tag="genericui:component" componentClass="AbstractComponent" category="core" display="block" role="generic" description="Base GenericUI component element."/>
        <element name="fragment" tag="genericui:fragment" componentClass="Fragment" category="core" display="block" role="region" description="Modular lifecycle-aware UI fragment container."/>
        <element name="fragment-container" tag="genericui:fragment-container" componentClass="FragmentContainer" category="core" display="block" role="region" description="Host container mounting and transitioning Fragment instances."/>
        <element name="scrollfragment" tag="genericui:scrollfragment" componentClass="ScrollFragment" category="shell" display="block" role="region" description="Scrollable activity-like lifecycle fragment container."/>
        <element name="window" tag="genericui:window" componentClass="Window" category="shell" display="flex" role="region" description="Top-level GenericUI window container inside genericui:root."/>
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
        <element name="currencypursebar" tag="genericui:currencypursebar" componentClass="CurrencyPurseBar" category="content" display="inline-flex" role="status" description="Multi-denomination currency or resource balance bar."/>
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
            jsDescriptor: jsDescriptor
        };
    }

    var state = {
        hasError: false,
        fadedOut: false,
        currentProgress: 0,
        preloadedWasmBytes: {},
        runtimeTarget: null
    };

    function updateProgress(percent, statusText) {
        if (state.hasError) {
            return;
        }
        var clamped = Math.max(0, Math.min(100, Math.round(percent)));
        if (clamped < state.currentProgress && clamped !== 0) {
            clamped = state.currentProgress;
        }
        state.currentProgress = clamped;

        var els = getDomElements();
        if (els.progressBar) {
            els.progressBar.style.width = clamped + "%";
            els.progressBar.setAttribute("aria-valuenow", String(clamped));
        }
        if (els.progressContainer) {
            els.progressContainer.setAttribute("aria-valuenow", String(clamped));
        }
        if (statusText && els.statusLabel) {
            els.statusLabel.textContent = statusText;
        }
    }

    function formatErrorDetails(err) {
        if (!err) {
            return {
                message: "An unknown application error occurred.",
                stack: "No stacktrace available."
            };
        }
        if (typeof err === "string") {
            return {
                message: err,
                stack: err
            };
        }
        var message = err.message || (err.reason && err.reason.message) || String(err);
        var stack = err.stack ||
            (err.error && err.error.stack) ||
            (err.reason && err.reason.stack) ||
            (err.cause && err.cause.stack) ||
            String(err);
        if (stack && stack.indexOf(message) === -1) {
            stack = message + "\n" + stack;
        }
        return {
            message: message,
            stack: stack
        };
    }

    function showError(err, phaseTitle) {
        state.hasError = true;
        var details = formatErrorDetails(err);
        var els = getDomElements();

        if (global.console && typeof global.console.error === "function") {
            global.console.error("[GenericUI Loader] " + (phaseTitle || "Application Error") + ":", err);
        }

        if (!els.doc) {
            return;
        }

        if (els.splash) {
            els.splash.classList.remove("d-none", "opacity-0");
            els.splash.style.opacity = "1";
            els.splash.style.visibility = "visible";
            els.splash.style.pointerEvents = "auto";
        }

        if (els.progressBar) {
            els.progressBar.classList.remove("progress-bar-animated", "progress-bar-striped");
            els.progressBar.classList.add("bg-danger");
            els.progressBar.style.width = "100%";
        }

        if (els.statusLabel) {
            els.statusLabel.textContent = phaseTitle || "Failed to start application";
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
            headerDiv.textContent = phaseTitle || "Application Error";
            errorBox.appendChild(headerDiv);

            errorMessage = els.doc.createElement("div");
            errorMessage.id = "genericui-splash-error-message";
            errorMessage.className = "genericui-splash-error-message small fw-semibold mb-2";
            errorBox.appendChild(errorMessage);

            errorStack = els.doc.createElement("pre");
            errorStack.id = "genericui-splash-error-stack";
            errorStack.className = "genericui-splash-error-stack small mb-0 p-2 bg-dark text-light rounded overflow-auto";
            errorStack.style.maxHeight = "240px";
            errorStack.style.whiteSpace = "pre-wrap";
            errorStack.style.wordBreak = "break-word";
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
        updateProgress(100, "Ready");

        var els = getDomElements();
        var splash = els.splash;
        if (!splash) {
            return;
        }
        var duration = typeof fadeDurationMs === "number" && fadeDurationMs >= 0 ? fadeDurationMs : 350;
        splash.style.transition = "opacity " + duration + "ms ease, visibility " + duration + "ms ease";
        splash.classList.add("opacity-0");
        splash.style.opacity = "0";
        splash.style.pointerEvents = "none";

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

    function detectWasmSupport() {
        try {
            if (typeof global.WebAssembly !== "object" ||
                typeof global.WebAssembly.instantiate !== "function" ||
                typeof global.WebAssembly.validate !== "function") {
                return false;
            }
            var wasmGcProbe = new Uint8Array([
                0x00, 0x61, 0x73, 0x6d,
                0x01, 0x00, 0x00, 0x00,
                0x01, 0x04, 0x01, 0x5f, 0x00, 0x0b
            ]);
            return global.WebAssembly.validate(wasmGcProbe);
        } catch (_e) {
            return false;
        }
    }

    function downloadWithProgress(url, minPct, maxPct, statusPrefix) {
        updateProgress(minPct, statusPrefix);
        return global.fetch(url, { credentials: "same-origin" }).then(function (response) {
            if (!response.ok) {
                throw new Error("HTTP " + response.status + " (" + response.statusText + ") while downloading " + url);
            }
            var contentLengthHeader = response.headers ? response.headers.get("Content-Length") : null;
            var totalBytes = contentLengthHeader ? parseInt(contentLengthHeader, 10) : 0;

            if (!response.body || typeof response.body.getReader !== "function") {
                return response.arrayBuffer().then(function (buf) {
                    updateProgress(maxPct, statusPrefix);
                    return new Uint8Array(buf);
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
                        updateProgress(maxPct, statusPrefix);
                        return combined;
                    }
                    var chunk = result.value;
                    chunks.push(chunk);
                    receivedBytes += chunk.byteLength;

                    var fraction = totalBytes > 0
                        ? Math.min(1, receivedBytes / totalBytes)
                        : Math.min(0.95, 1 - Math.exp(-receivedBytes / (512 * 1024)));
                    var pct = minPct + fraction * (maxPct - minPct);
                    var suffix = totalBytes > 0
                        ? " (" + Math.round(fraction * 100) + "%)"
                        : "";
                    updateProgress(pct, statusPrefix + suffix);
                    return pump();
                });
            }

            return pump();
        });
    }

    function registerPreloadedWasmBinary(wasmUrl, wasmBytes) {
        if (!wasmUrl || !wasmBytes) {
            return;
        }
        var cleanUrl = wasmUrl.split("?")[0];
        var cleanName = cleanUrl.split("/").pop();
        state.preloadedWasmBytes[wasmUrl] = wasmBytes;
        state.preloadedWasmBytes[cleanUrl] = wasmBytes;
        if (cleanName) {
            state.preloadedWasmBytes[cleanName] = wasmBytes;
        }

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
                var cached = state.preloadedWasmBytes[reqUrl] ||
                    state.preloadedWasmBytes[reqClean] ||
                    (reqFile ? state.preloadedWasmBytes[reqFile] : null);
                if (cached && typeof global.Response === "function") {
                    return Promise.resolve(new global.Response(cached.buffer, {
                        status: 200,
                        headers: { "Content-Type": "application/wasm" }
                    }));
                }
                return originalFetch(input, init);
            };
        }
    }

    function executeDownloadedScript(jsBytes, sourceUrl) {
        return new Promise(function (resolve, reject) {
            try {
                var decoder = typeof global.TextDecoder === "function"
                    ? new global.TextDecoder("utf-8")
                    : null;
                var code = decoder ? decoder.decode(jsBytes) : "";
                var els = getDomElements();
                if (!els.doc) {
                    resolve();
                    return;
                }

                var cleanUrl = sourceUrl.split("?")[0];
                var isEsModule = /\.mjs$/i.test(cleanUrl) ||
                    /(?:^|\n)\s*(?:import|export)\s+/m.test(code);

                if (isEsModule && typeof global.Blob === "function" && global.URL && typeof global.URL.createObjectURL === "function") {
                    var blob = new global.Blob([code], { type: "text/javascript" });
                    var blobUrl = global.URL.createObjectURL(blob);
                    var modScript = els.doc.createElement("script");
                    modScript.type = "module";
                    modScript.src = blobUrl;
                    modScript.onload = function () {
                        global.URL.revokeObjectURL(blobUrl);
                        resolve();
                    };
                    modScript.onerror = function () {
                        global.URL.revokeObjectURL(blobUrl);
                        reject(new Error("Failed to execute ES module script: " + sourceUrl));
                    };
                    (els.doc.body || els.doc.head || els.doc.documentElement).appendChild(modScript);
                    return;
                }

                if (code.length > 0) {
                    var inlineScript = els.doc.createElement("script");
                    inlineScript.type = "text/javascript";
                    inlineScript.text = code + "\n//# sourceURL=" + sourceUrl;
                    (els.doc.body || els.doc.head || els.doc.documentElement).appendChild(inlineScript);
                    resolve();
                } else {
                    var extScript = els.doc.createElement("script");
                    extScript.src = sourceUrl;
                    extScript.onload = function () {
                        resolve();
                    };
                    extScript.onerror = function () {
                        reject(new Error("Failed to load script: " + sourceUrl));
                    };
                    (els.doc.body || els.doc.head || els.doc.documentElement).appendChild(extScript);
                }
            } catch (err) {
                reject(err);
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

        var wasmJsUrl = cfg.wasmJsUrl ||
            readTagAttr(wasmTag, "src") ||
            readTagAttr(scriptEl, "data-wasm-js-url") ||
            readTagAttr(splashEl, "data-wasm-js-url");

        var wasmBinaryUrl = cfg.wasmBinaryUrl ||
            readTagAttr(wasmTag, "data-wasm-binary") ||
            readTagAttr(wasmTag, "data-wasm-binary-src") ||
            readTagAttr(scriptEl, "data-wasm-binary-url") ||
            readTagAttr(splashEl, "data-wasm-binary-url");

        var jsIrUrl = cfg.jsIrUrl ||
            readTagAttr(jsTag, "src") ||
            readTagAttr(scriptEl, "data-js-ir-url") ||
            readTagAttr(splashEl, "data-js-ir-url");

        var fadeDurationMs = typeof cfg.fadeDurationMs === "number"
            ? cfg.fadeDurationMs
            : (parseInt(readTagAttr(scriptEl, "data-fade-duration-ms") || readTagAttr(splashEl, "data-fade-duration-ms"), 10) || 350);

        return {
            wasmJsUrl: wasmJsUrl,
            wasmBinaryUrl: wasmBinaryUrl,
            jsIrUrl: jsIrUrl,
            fadeDurationMs: fadeDurationMs
        };
    }

    function loadJsIrFallback(config) {
        if (!config.jsIrUrl) {
            throw new Error("Neither a supported WebAssembly bundle nor a fallback JS IR bundle URL was configured.");
        }
        state.runtimeTarget = "js-ir";
        global.__GENERICUI_WEB_RUNTIME__ = "js-ir";
        return downloadWithProgress(config.jsIrUrl, 10, 92, "Downloading JS application bundle")
            .then(function (jsBytes) {
                updateProgress(95, "Starting application (JS IR)...");
                return executeDownloadedScript(jsBytes, config.jsIrUrl);
            });
    }

    function loadWasmPrimaryWithFallback(config) {
        var wasmSupported = detectWasmSupport();
        if (wasmSupported && config.wasmJsUrl) {
            state.runtimeTarget = "wasm";
            global.__GENERICUI_WEB_RUNTIME__ = "wasm";
            updateProgress(5, "WebAssembly supported — downloading Wasm bundle");

            var wasmChain = Promise.resolve();
            if (config.wasmBinaryUrl) {
                wasmChain = downloadWithProgress(config.wasmBinaryUrl, 5, 72, "Downloading WebAssembly binary")
                    .then(function (wasmBytes) {
                        registerPreloadedWasmBinary(config.wasmBinaryUrl, wasmBytes);
                    });
            }

            return wasmChain
                .then(function () {
                    var startPct = config.wasmBinaryUrl ? 72 : 10;
                    return downloadWithProgress(config.wasmJsUrl, startPct, 92, "Downloading WebAssembly loader");
                })
                .then(function (wasmJsBytes) {
                    updateProgress(95, "Starting application (WebAssembly)...");
                    return executeDownloadedScript(wasmJsBytes, config.wasmJsUrl);
                })
                .catch(function (wasmErr) {
                    if (config.jsIrUrl && config.jsIrUrl !== config.wasmJsUrl && !state.hasError) {
                        if (global.console && typeof global.console.warn === "function") {
                            global.console.warn(
                                "[GenericUI Loader] WebAssembly bundle failed to load (" +
                                (wasmErr && wasmErr.message ? wasmErr.message : wasmErr) +
                                "); falling back to JS IR bundle: " + config.jsIrUrl
                            );
                        }
                        state.currentProgress = 10;
                        return loadJsIrFallback(config);
                    }
                    throw wasmErr;
                });
        }

        return loadJsIrFallback(config);
    }

    function installGlobalErrorListeners() {
        if (typeof global.addEventListener !== "function") {
            return;
        }
        global.addEventListener("error", function (event) {
            var errObj = (event && event.error) ? event.error : new Error(
                (event && event.message ? event.message : "Unhandled runtime error") +
                (event && event.filename ? " at " + event.filename + ":" + (event.lineno || 0) : "")
            );
            showError(errObj, "Runtime Exception");
        });
        global.addEventListener("unhandledrejection", function (event) {
            var reason = event && event.reason ? event.reason : new Error("Unhandled Promise rejection");
            showError(reason, "Unhandled Promise Rejection");
        });
    }

    function ensureRootContainer() {
        var doc = global.document;
        if (!doc) {
            return null;
        }
        var root = (typeof doc.getElementsByTagName === "function" && doc.getElementsByTagName("genericui:root")[0]) ||
            (typeof doc.querySelector === "function" ? doc.querySelector("genericui\\:root") : null);
        if (!root && doc.body && typeof doc.createElement === "function") {
            root = doc.createElement("genericui:root");
            doc.body.appendChild(root);
        }
        if (root && typeof root.removeAttribute === "function") {
            root.removeAttribute("id");
            root.removeAttribute("data-bs-theme");
        }
        return root;
    }

    function startLoader() {
        installGlobalErrorListeners();
        ensureRootContainer();
        var config = resolveLoaderConfig();

        if (!config.wasmJsUrl && !config.jsIrUrl) {
            updateProgress(100, "Ready");
            fadeOutSplash(config.fadeDurationMs);
            return Promise.resolve();
        }

        updateProgress(2, "Detecting WebAssembly support...");
        return loadWasmPrimaryWithFallback(config)
            .then(function () {
                if (!state.hasError) {
                    fadeOutSplash(config.fadeDurationMs);
                }
            })
            .catch(function (err) {
                showError(err, "Failed to Load Application");
            });
    }

    global.GenericUiSplash = {
        detectWasmSupport: detectWasmSupport,
        ensureRootContainer: ensureRootContainer,
        updateProgress: updateProgress,
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
    }
}
