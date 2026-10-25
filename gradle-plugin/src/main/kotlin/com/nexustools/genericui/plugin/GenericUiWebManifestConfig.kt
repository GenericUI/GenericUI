package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * DSL configuration block (`genericui { application { webManifest { ... } } }` / `genericui { webManifest { ... } }`
 * / `genericui { backends { web { ... } } }`) for configuring a W3C / MDN Progressive Web App (PWA)
 * Web App Manifest (`manifest.webmanifest`, `https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Manifest`)
 * and enabling standalone WebApp installation (`allowInstallAsWebApp`) on Android via Chrome, iOS, and Desktop browsers.
 */
open class GenericUiWebManifestConfig : Serializable {

    /**
     * Whether to generate `manifest.webmanifest` in `build/dist/web` and link it in `index.html`.
     * Automatically becomes active when [allowInstallAsWebApp] is `true`.
     */
    var enabled: Boolean = false

    /**
     * Whether to enable HTML `<head>` tags and Web App Manifest settings that allow the Web target
     * to be installed as a standalone WebApp on platforms like Android via Chrome (`WebAPK` /
     * `"Add to Home screen"` / `"Install app"`), iOS Safari, and Desktop Chrome/Edge.
     *
     * Setting `allowInstallAsWebApp = true` automatically enables `manifest.webmanifest` generation
     * and injects:
     * - `<link id="genericui-web-manifest" rel="manifest" href="manifest.webmanifest?..." crossorigin="use-credentials">`
     * - `<meta id="genericui-theme-color" name="theme-color" content="...">`
     * - `<meta id="genericui-mobile-web-app-capable" name="mobile-web-app-capable" content="yes">`
     * - `<meta id="genericui-apple-mobile-web-app-capable" name="apple-mobile-web-app-capable" content="yes">`
     * - `<meta id="genericui-apple-mobile-web-app-status-bar-style" name="apple-mobile-web-app-status-bar-style" content="...">`
     * - `<meta id="genericui-application-name" name="application-name" content="...">`
     * - `<meta id="genericui-apple-mobile-web-app-title" name="apple-mobile-web-app-title" content="...">`
     * - `<link id="genericui-apple-touch-icon" rel="apple-touch-icon" href="...">`
     */
    var allowInstallAsWebApp: Boolean = false

    /** Output file name for the generated Web App Manifest (defaults to `"manifest.webmanifest"`). */
    var fileName: String = "manifest.webmanifest"

    /** Optional unique manifest `id` string (defaults to `startUrl` / `appId` when blank). */
    var id: String = ""

    /** Full human-readable name of the web application (`name` in `manifest.webmanifest`; defaults to `application.appName`). */
    var name: String = ""

    /** Short name displayed on Android/iOS home screen icons (`short_name` in `manifest.webmanifest`; defaults to [name]). */
    var shortName: String = ""

    /** Description of the web application (`description` in `manifest.webmanifest`; defaults to `application.description`). */
    var description: String = ""

    /** Start URL launched when the installed WebApp opens (`start_url` in `manifest.webmanifest`, defaults to `"./"`). */
    var startUrl: String = "./"

    /** Navigation scope of the installed WebApp (`scope` in `manifest.webmanifest`, defaults to `"./"`). */
    var scope: String = "./"

    /**
     * Preferred display mode (`display` in `manifest.webmanifest`):
     * `"standalone"` (default, required for native-like Android Chrome WebApp), `"fullscreen"`,
     * `"minimal-ui"`, or `"browser"`.
     */
    var display: String = "standalone"

    /** Optional `display_override` fallback chain (e.g. `listOf("window-controls-overlay", "standalone")`). */
    val displayOverride: MutableList<String> = mutableListOf()

    /** Default screen orientation (`orientation` in `manifest.webmanifest`, e.g. `"any"`, `"portrait"`, `"landscape"`). */
    var orientation: String = "any"

    /**
     * Manifest `theme_color` and `<meta name="theme-color">`.
     * Inherits `genericui { application { themeColor } }` when left blank!
     */
    var themeColor: String = ""

    /** Manifest splash/launch `background_color` (defaults to `"#121519"`). */
    var backgroundColor: String = "#121519"

    /** Primary language tag (`lang` in `manifest.webmanifest`, defaults to `"en"`). */
    var lang: String = "en"

    /** Text direction (`dir` in `manifest.webmanifest`: `"auto"`, `"ltr"`, or `"rtl"`). */
    var dir: String = "auto"

    /** W3C application categories (e.g. `"entertainment"`, `"productivity"`, `"utilities"`, `"games"`). */
    val categories: MutableList<String> = mutableListOf()

    /** Whether native related applications are preferred over the WebApp (`prefer_related_applications`). */
    var preferRelatedApplications: Boolean = false

    /** iOS `apple-mobile-web-app-status-bar-style` value (`"black-translucent"`, `"black"`, or `"default"`). */
    var appleStatusBarStyle: String = "black-translucent"

    /** Explicit manifest icon specs. When empty, automatically populated from the project's Web icon (`192x192`, `512x512`, `any`). */
    val icons: MutableList<GenericUiWebManifestIconSpec> = mutableListOf()

    /** Optional app shortcuts exposed in the OS launcher long-press / context menu. */
    val shortcuts: MutableList<GenericUiWebManifestShortcutSpec> = mutableListOf()

    /** True when either [enabled] or [allowInstallAsWebApp] is `true`. */
    val isActive: Boolean
        get() = enabled || allowInstallAsWebApp

    /** Adds an icon entry to `icons` in `manifest.webmanifest`. */
    fun icon(
        src: String,
        sizes: String = "any",
        type: String = "",
        purpose: String = "any maskable"
    ) {
        if (src.isNotBlank()) {
            icons.add(
                GenericUiWebManifestIconSpec(
                    src = src.trim(),
                    sizes = sizes.trim().ifEmpty { "any" },
                    type = type.trim(),
                    purpose = purpose.trim().ifEmpty { "any maskable" }
                )
            )
        }
    }

    /** Adds a shortcut entry to `shortcuts` in `manifest.webmanifest`. */
    fun shortcut(
        name: String,
        url: String = "./",
        shortName: String = "",
        description: String = ""
    ) {
        if (name.isNotBlank()) {
            shortcuts.add(
                GenericUiWebManifestShortcutSpec(
                    name = name.trim(),
                    shortName = shortName.trim(),
                    description = description.trim(),
                    url = url.trim().ifEmpty { "./" }
                )
            )
        }
    }

    /** Adds one or more W3C manifest categories (e.g. `categories("entertainment", "multimedia")`). */
    fun categories(vararg values: String) {
        values.map { it.trim() }.filter { it.isNotEmpty() }.forEach { cat ->
            if (cat !in categories) {
                categories.add(cat)
            }
        }
    }

    /** Adds one or more `display_override` modes (e.g. `displayOverride("window-controls-overlay", "standalone")`). */
    fun displayOverride(vararg values: String) {
        values.map { it.trim() }.filter { it.isNotEmpty() }.forEach { mode ->
            if (mode !in displayOverride) {
                displayOverride.add(mode)
            }
        }
    }
}
