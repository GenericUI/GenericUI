package com.nexustools.genericui.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
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
 * `genericui-loader.js`, and `index.html` with SHA-1 cache-busting hashes appended to
 * `<script type="loader-wasm">`, `<script type="loader-js">`, and `#genericui-loader-script`.
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

        // 1. Resolve Web Icon (preferring explicit webIcon and .svg icons, falling back to project icons)
        val iconFile = resolveWebIconFile(rootDir)
        val stagedIconName = if (iconFile != null && iconFile.exists()) {
            val targetIcon = File(outDir, iconFile.name)
            if (iconFile.canonicalFile != targetIcon.canonicalFile) {
                iconFile.copyTo(targetIcon, overwrite = true)
            }
            iconFile.name
        } else {
            null
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

        // 5. Write genericui-loader.js and compute its SHA-1 hash
        val loaderFile = File(outDir, "genericui-loader.js")
        loaderFile.writeText(LOADER_JS_SOURCE, Charsets.UTF_8)
        val loaderSha1 = sha1HexOfFile(loaderFile)

        // 6. Generate index.html from template
        val faviconLink = if (stagedIconName != null) {
            val mimeType = inferIconMimeType(stagedIconName)
            """    <link id="genericui-favicon" rel="icon" type="$mimeType" href="$stagedIconName">"""
        } else {
            ""
        }

        val splashIconHtml = if (stagedIconName != null) {
            """            <img id="genericui-splash-icon" src="$stagedIconName" alt="Application Icon" class="genericui-splash-icon mb-3 rounded" width="256" height="256" style="width: 256px; height: 256px; object-fit: contain;">"""
        } else {
            """            <i id="genericui-splash-icon" class="bi bi-layers-half text-primary mb-3 d-inline-flex align-items-center justify-content-center" style="width: 256px; height: 256px; font-size: 256px; line-height: 1;" aria-hidden="true"></i>"""
        }

        val wasmBinaryAttr = if (stagedWasmBinaryRelPath != null && stagedWasmBinarySha1 != null) {
            """ data-wasm-binary="$stagedWasmBinaryRelPath?$stagedWasmBinarySha1""""
        } else {
            ""
        }

        val indexHtml = INDEX_HTML_TEMPLATE
            .replace("{{LANG}}", "en")
            .replace("{{TITLE}}", escapeHtml(resolvedTitle))
            .replace("{{THEME_MODE}}", "dark")
            .replace("{{FAVICON_LINK}}", faviconLink)
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

    private fun resolveWebIconFile(rootDir: File): File? {
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

    private fun sha1HexOfFile(file: File): String =
        sha1HexOfBytes(file.readBytes())

    private fun sha1HexOfString(text: String): String =
        sha1HexOfBytes(text.toByteArray(Charsets.UTF_8))

    private fun sha1HexOfBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        const val INDEX_HTML_TEMPLATE: String = """<!DOCTYPE html>
<html lang="{{LANG}}" data-bs-theme="{{THEME_MODE}}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{TITLE}}</title>
{{FAVICON_LINK}}
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
    <div id="{{ROOT_CONTAINER_ID}}" data-bs-theme="{{THEME_MODE}}"></div>
    <script id="genericui-bootstrap-js" src="{{BOOTSTRAP_JS_URL}}" integrity="{{BOOTSTRAP_JS_INTEGRITY}}" crossorigin="{{CROSSORIGIN}}" defer></script>
{{EXTRA_SCRIPTS}}
    <script type="loader-wasm" src="{{WASM_URL}}?{{WASM_HASH}}"{{WASM_BINARY_ATTR}}></script>
    <script type="loader-js" src="{{JS_URL}}?{{JS_HASH}}"></script>
    <script id="genericui-loader-script" src="{{LOADER_JS_URL}}?{{LOADER_JS_HASH}}" defer></script>
</body>
</html>"""

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

        if (typeof global.setTimeout === "function") {
            global.setTimeout(function () {
                if (!state.hasError && splash) {
                    splash.classList.add("d-none");
                    splash.style.visibility = "hidden";
                }
            }, duration + 20);
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

    function startLoader() {
        installGlobalErrorListeners();
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
