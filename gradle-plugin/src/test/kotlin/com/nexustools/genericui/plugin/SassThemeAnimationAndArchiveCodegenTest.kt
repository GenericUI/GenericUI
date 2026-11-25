package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SassThemeAnimationAndArchiveCodegenTest {

    @Test
    fun `sass compiler evaluates color functions math interpolation keyframes selectors transform cursor none and animations into CSS and Kotlin`() {
        val project = ProjectBuilder.builder().withName("sass-feature-app").build()
        val tempDir = project.projectDir
        val stylesDir = File(tempDir, "src/commonMain/resources/styles").apply { mkdirs() }
        val outDir = File(tempDir, "build/compiled-css")
        val webCssDir = File(tempDir, "build/web-css")
        val ktDir = File(tempDir, "build/generated-kt")

        File(stylesDir, "style.scss").writeText(
            """
            ${'$'}base-pad: 8px;
            ${'$'}col-width: 120px;
            ${'$'}gap: (${'$'}base-pad + 4px);
            ${'$'}accent-soft: rgba(${'$'}accent, 0.45);
            ${'$'}accent-light: lighten(${'$'}accent, 15%);
            ${'$'}accent-dark: darken(${'$'}accent, 15%);
            ${'$'}accent-mix: mix(${'$'}primary, #ffffff, 50%);
            ${'$'}solid-rgb: rgb(16, 32, 48);

            @mixin rounded-pill(${'$'}radius: 999px) {
              border-radius: ${'$'}radius;
            }

            @keyframes fadeOverlayOut {
              0% {
                opacity: 1;
                transform: translate(0, 0);
              }
              100% {
                opacity: 0;
                transform: translate(0, -50%);
              }
            }

            @keyframes pulseAccent {
              from, 50% {
                background-color: ${'$'}accent-soft;
              }
              to {
                background-color: ${'$'}accent-dark;
              }
            }

            .overlay-root {
              @include rounded-pill(12px);
              z-index: 20;
              padding: ${'$'}base-pad * 2;
              margin-left: ${'$'}gap;
              min-width: ${'$'}col-width / 2;
              max-width: ${'$'}col-width - 20px;
              border-color: ${'$'}solid-rgb;
              aspect-ratio: 16 / 9;
              line-height: 1.38;

              &:not(.hidden) {
                opacity: 1;
                background-color: ${'$'}accent-mix;
                color: ${'$'}accent-light;
              }

              &.hidden {
                opacity: 0;
                pointer-events: none;
                cursor: none;
                animation-name: fadeOverlayOut;
                animation-duration: 220ms;
                animation-timing-function: ease-out;
                animation-fill-mode: forwards;
              }
            }

            composite > .seek-bar-track:disabled,
            genericui\:styled-slider .seek-bar-knob {
              position: absolute;
              top: 50%;
              left: -50%;
              transform: translate(-50%, -50%);
              width: calc(100% - 24px);
              line-height: 1;
              animation: pulseAccent 1s linear infinite;
            }

            .badge-px-line-height {
              line-height: 24px;
            }
            """.trimIndent(),
            Charsets.UTF_8
        )

        val sassTask = project.tasks.register("compileTestSass", GenericUiSassCompileTask::class.java) {
            sourceDirectory.set(stylesDir)
            compileSass.set(true)
            generateWebCss.set(true)
            precompileToKotlin.set(true)
            kotlinPackageName.set("com.example.media.styles")
            minify.set(false)
            includeBootstrapVariables.set(false)
            themeColor.set("#2563eb")
            lightThemeColors.set(
                mapOf(
                    GenericUiThemeConfig.KEY_PRIMARY to "#2563eb",
                    GenericUiThemeConfig.KEY_ACCENT to "#ff5500"
                )
            )
            darkThemeColors.set(
                mapOf(
                    GenericUiThemeConfig.KEY_PRIMARY to "#2563eb",
                    GenericUiThemeConfig.KEY_ACCENT to "#ff5500"
                )
            )
            outputDirectory.set(outDir)
            webCssOutputDirectory.set(webCssDir)
            kotlinOutputDirectory.set(ktDir)
        }.get()

        sassTask.compileStyles()

        val cssFile = File(outDir, "style.css")
        assertTrue(cssFile.isFile, "Expected style.css to be compiled")
        val cssText = cssFile.readText(Charsets.UTF_8)

        assertTrue(cssText.contains("padding: 16px;"), "Expected Sass multiplication `8px * 2` -> `16px`, got:\n$cssText")
        assertTrue(cssText.contains("margin-left: 12px;"), "Expected Sass addition `(8px + 4px)` -> `12px`, got:\n$cssText")
        assertTrue(cssText.contains("min-width: 60px;"), "Expected Sass division `120px / 2` -> `60px`, got:\n$cssText")
        assertTrue(cssText.contains("max-width: 100px;"), "Expected Sass subtraction `120px - 20px` -> `100px`, got:\n$cssText")
        assertTrue(cssText.contains("border-radius: 12px;"), "Expected @mixin/@include border-radius: 12px, got:\n$cssText")
        assertTrue(cssText.contains("border-color: #102030;"), "Expected rgb(16, 32, 48) -> #102030, got:\n$cssText")
        assertTrue(cssText.contains("aspect-ratio: 16 / 9;"), "CSS aspect-ratio slash shorthand must stay intact")
        assertTrue(cssText.contains("width: calc(100% - 24px);"), "CSS calc() expression must stay intact")
        assertTrue(cssText.contains(".overlay-root:not(.hidden)"), "Must preserve :not(.hidden) selector")
        assertTrue(cssText.contains("genericui\\:composite > .seek-bar-track:disabled"), "Must preserve :disabled selector and genericui:composite")
        assertTrue(cssText.contains("genericui\\:styled-slider .seek-bar-knob"), "Must preserve genericui:styled-slider tag")
        assertTrue(cssText.contains("cursor: none;"), "Must preserve cursor: none")
        assertTrue(cssText.contains("pointer-events: none;"), "Must preserve pointer-events: none")
        assertTrue(cssText.contains("transform: translate(-50%, -50%);"), "Must preserve negative transform percentages")
        assertTrue(cssText.contains("@keyframes fadeOverlayOut"), "Must preserve @keyframes fadeOverlayOut")
        assertTrue(cssText.contains("@keyframes pulseAccent"), "Must preserve @keyframes pulseAccent")
        assertTrue(cssText.contains("background-color: rgba(255, 85, 0, 0.45);"), "Expected rgba(\$accent, 0.45) inside @keyframes")
        assertTrue(cssText.contains("background-color: #b33b00;"), "Expected darken(#ff5500, 15%) -> #b33b00, got:\n$cssText")
        assertTrue(cssText.contains("color: #ff884d;"), "Expected lighten(#ff5500, 15%) -> #ff884d, got:\n$cssText")
        assertTrue(cssText.contains("background-color: #92b1f5;"), "Expected mix(#2563eb, #ffffff, 50%) -> #92b1f5, got:\n$cssText")

        val ktFile = File(ktDir, "com/example/media/styles/StylePrecompiledStyleSheet.kt")
        assertTrue(ktFile.isFile, "Expected StylePrecompiledStyleSheet.kt to be emitted")
        val ktText = ktFile.readText(Charsets.UTF_8)

        // Selectors & pseudo-states
        assertTrue(ktText.contains("selector = \".overlay-root:not(.hidden)\""))
        assertTrue(
            ktText.contains("classes = setOf(\"overlay-root\"), pseudoClasses = emptySet()") &&
                ktText.contains("excludedClasses = setOf(\"hidden\")") &&
                !ktText.contains("pseudoClasses = setOf(\"not(.hidden)\")"),
            "Expected .overlay-root:not(.hidden) to emit excludedClasses = setOf(\"hidden\") instead of not(.hidden):\n$ktText"
        )
        assertTrue(
            ktText.contains("specificity = 200"),
            "Expected .overlay-root:not(.hidden) to have specificity 200 per CSS Selectors Level 4:\n$ktText"
        )
        assertTrue(ktText.contains("tagName = \"genericui:composite\""), "Expected genericui:composite tagName")
        assertTrue(ktText.contains("tagName = \"genericui:styled-slider\""), "Expected genericui:styled-slider tagName")
        assertTrue(ktText.contains("pseudoClasses = setOf(\"disabled\")"), "Expected :disabled in pseudoClasses")
        assertTrue(ktText.contains("pseudoClass = \"disabled\""), "Expected subject pseudoClass = \"disabled\"")

        // Declarations & typed statements
        assertTrue(ktText.contains("setCursor(CssCursor.NONE, false)"), "Expected CssCursor.NONE")
        assertTrue(ktText.contains("setPointerEvents(CssPointerEvents.NONE, false)"))
        assertTrue(ktText.contains("setZIndex(20, false)"))
        assertTrue(ktText.contains("setTop(CssLength.PERCENT_50, false)"))
        assertTrue(ktText.contains("setLeft(CssLength(-50.0f, CssUnit.PERCENT), false)"))
        assertTrue(
            ktText.contains("setLineHeight(CssLength(1.38f, CssUnit.NUMBER), false)"),
            "Expected unitless line-height 1.38 to emit CssUnit.NUMBER:\n$ktText"
        )
        assertTrue(
            ktText.contains("setLineHeight(CssLength(1.0f, CssUnit.NUMBER), false)"),
            "Expected unitless line-height 1 to emit CssUnit.NUMBER:\n$ktText"
        )
        assertTrue(
            ktText.contains("setLineHeight(CssLength(24.0f, CssUnit.PX), false)"),
            "Expected px line-height 24px to emit CssUnit.PX:\n$ktText"
        )
        assertTrue(
            ktText.contains("CssTransform.parse(\"translate(-50%, -50%)\")?.let { setTransform(it, false) }"),
            "Expected CssTransform.parse call for transform:\n$ktText"
        )
        assertTrue(ktText.contains("setAnimationName(\"fadeOverlayOut\", false)"))
        assertTrue(ktText.contains("setAnimationDuration(\"220ms\", false)"))
        assertTrue(ktText.contains("setAnimationTimingFunction(\"ease-out\", false)"))
        assertTrue(ktText.contains("setAnimationFillMode(\"forwards\", false)"))
        assertTrue(ktText.contains("setAnimation(\"pulseAccent 1s linear infinite\", false)"))
        assertTrue(ktText.contains("\"animation-name\" to \"fadeOverlayOut\""))
        assertTrue(ktText.contains("override val keyframes: Map<String, Map<String, Map<String, String>>> = buildKeyframes()"))
        assertTrue(ktText.contains("private fun buildKeyframesChunk0(): Map<String, Map<String, Map<String, String>>> = mapOf("))
        assertTrue(ktText.contains("\"fadeOverlayOut\" to mapOf("))
        assertTrue(ktText.contains("\"pulseAccent\" to mapOf("))
        assertTrue(ktText.contains("\"transform\" to \"translate(0, -50%)\""))
        assertTrue(ktText.contains("isPrecompiled = true"))
    }

    @Test
    fun `raw theme scss templates from prebuilt jar and klib archives compile exclusively in consumer app and register across system and web`() {
        val workRoot = File(System.getProperty("java.io.tmpdir"), "genericui-theme-klib-test-${System.nanoTime()}").apply {
            mkdirs()
        }
        try {
            // 1. Create a prebuilt extension .jar carrying raw META-INF/genericui/themes/ + module manifest (NO compiled ThemeStyleSheet!)
            val overlayJar = File(workRoot, "overlay-extension.jar")
            val overlayThemeScss = """
                ${'$'}btn-tint: rgba(${'$'}accent, 0.25);
                .player-btn:not(:disabled):hover {
                  background-color: ${'$'}btn-tint;
                  border-color: ${'$'}accent;
                }
            """.trimIndent()
            val overlayB64 = Base64.getEncoder().encodeToString(overlayThemeScss.toByteArray(Charsets.UTF_8))
            val overlayManifestJson = """
                {
                  "schemaVersion": 2,
                  "scopeId": "genericui-ext-ui-overlay",
                  "moduleByte": 100,
                  "moduleGroupId": "genericui-ext-overlay",
                  "scopePrefix": "gui/ext/ui/overlay",
                  "package": "com.nexustools.genericui.ext.overlay.res",
                  "themeScssBase64": "$overlayB64",
                  "entries": []
                }
            """.trimIndent()
            ZipOutputStream(overlayJar.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/genericui/themes/genericui-ext-ui-overlay.scss"))
                zip.write(overlayThemeScss.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("META-INF/genericui/module-resources/genericui-ext-ui-overlay.json"))
                zip.write(overlayManifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // 2. Create BOTH a packed .klib archive AND an unpacked .klib directory carrying raw default/resources/META-INF/genericui/themes/
            val mediaKlibZip = File(workRoot, "media-extension.klib")
            val mediaThemeScss = """
                .seek-bar-progress {
                  background-color: ${'$'}accent;
                }
                .seek-bar-knob {
                  border-color: ${'$'}primary;
                  background-color: ${'$'}surface;
                }
            """.trimIndent()
            val mediaB64 = Base64.getEncoder().encodeToString(mediaThemeScss.toByteArray(Charsets.UTF_8))
            val mediaManifestJson = """
                {
                  "schemaVersion": 2,
                  "scopeId": "genericui-ext-media-media",
                  "moduleByte": 101,
                  "moduleGroupId": "genericui-ext-media",
                  "scopePrefix": "gui/ext/media/media",
                  "package": "com.nexustools.genericui.component.media.res",
                  "themeScssBase64": "$mediaB64",
                  "entries": []
                }
            """.trimIndent()
            ZipOutputStream(mediaKlibZip.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("default/manifest"))
                zip.write("unique_name=com.nexustools.genericui.ext\\:media\n".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("default/resources/META-INF/genericui/themes/genericui-ext-media-media.scss"))
                zip.write(mediaThemeScss.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("default/resources/META-INF/genericui/module-resources/genericui-ext-media-media.json"))
                zip.write(mediaManifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val unpackedKlibDir = File(workRoot, "common-unpacked.klib").apply { mkdirs() }
            val commonThemeScss = """
                .gui-focus-ring:focus-visible {
                  border-color: ${'$'}accent;
                }
            """.trimIndent()
            File(unpackedKlibDir, "default/resources/META-INF/genericui/themes/genericui-common.scss").apply {
                parentFile.mkdirs()
                writeText(commonThemeScss, Charsets.UTF_8)
            }

            // 3. Verify parser extracts raw theme templates in :common -> extensions order from jar + klib + dir
            val extracted = GenericUiModuleResourceManifestParser.readThemeTemplatesFromSources(
                listOf(mediaKlibZip, overlayJar, unpackedKlibDir)
            )
            assertEquals(
                listOf("genericui-common", "genericui-ext-media-media", "genericui-ext-ui-overlay"),
                extracted.map { it.scopeId },
                "Expected canonical order :common -> sorted extensions"
            )

            // 4. Verify library mode produces ZERO compiled ThemeStyleSheet / genericui-theme.css even when theme.scss exists
            val libProj = ProjectBuilder.builder().withProjectDir(File(workRoot, "lib-mod").apply { mkdirs() }).build()
            val libStylesDir = File(libProj.projectDir, "src/commonMain/resources/styles").apply { mkdirs() }
            File(libStylesDir, "theme.scss").writeText(".lib-only { color: ${'$'}accent; }")
            File(libStylesDir, "style.scss").writeText(".lib-base { display: flex; }")
            val libOutCss = File(libProj.projectDir, "build/css")
            val libWebCss = File(libProj.projectDir, "build/web-css")
            val libKt = File(libProj.projectDir, "build/kt")
            val libTask = libProj.tasks.register("compileLibSass", GenericUiSassCompileTask::class.java) {
                sourceDirectory.set(libStylesDir)
                compileSass.set(true)
                generateWebCss.set(true)
                precompileToKotlin.set(true)
                kotlinPackageName.set("com.example.lib.styles")
                minify.set(false)
                includeBootstrapVariables.set(false)
                consumerApplicationMode.set(false)
                outputDirectory.set(libOutCss)
                webCssOutputDirectory.set(libWebCss)
                kotlinOutputDirectory.set(libKt)
            }.get()
            libTask.compileStyles()
            assertFalse(
                File(libKt, "com/example/lib/styles/GenericUiGeneratedThemeStyleSheet.kt").exists(),
                "GenericUI / library builds MUST NEVER emit GenericUiGeneratedThemeStyleSheet.kt"
            )
            assertFalse(
                File(libKt, "com/example/lib/styles/ThemePrecompiledStyleSheet.kt").exists(),
                "Library builds MUST NEVER compile theme.scss into ThemePrecompiledStyleSheet.kt"
            )
            assertFalse(
                File(libOutCss, "genericui-theme.css").exists(),
                "Library builds MUST NEVER compile genericui-theme.css"
            )
            assertTrue(
                File(libKt, "com/example/lib/styles/StylePrecompiledStyleSheet.kt").isFile,
                "Library style.scss should still compile normally"
            )

            // 5. Verify consumer app extracts from prebuilt .jar + .klib + consumer theme.scss,
            // compiles GenericUiGeneratedThemeStyleSheet (:system) & gui/genericui-theme.css (:web),
            // and registers it in Launchers (incl. MainViewController/iOS), Android, and Web!
            val appDir = File(workRoot, "consumer-app").apply { mkdirs() }
            val appProject = ProjectBuilder.builder().withName("consumer-app").withProjectDir(appDir).build()
            appProject.group = "com.example.player"
            appProject.pluginManager.apply("com.nexustools.genericui")

            val commonSrc = File(appDir, "src/commonMain/kotlin/com/example/player").apply { mkdirs() }
            File(commonSrc, "PlayerFragment.kt").writeText(
                """
                package com.example.player

                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment

                @MainAppFragment
                class PlayerFragment : Fragment()
                """.trimIndent()
            )
            val consumerStylesDir = File(appDir, "src/commonMain/resources/styles").apply { mkdirs() }
            File(consumerStylesDir, "theme.scss").writeText(
                """
                .consumer-badge {
                  background-color: mix(${'$'}accent, #000000, 80%);
                }
                """.trimIndent()
            )
            File(consumerStylesDir, "style.scss").writeText(
                """
                .player-shell {
                  cursor: none;
                }
                """.trimIndent()
            )

            val ext = appProject.extensions.getByType(GenericUiExtension::class.java)
            ext.app {
                id = "com.example.player"
                name = "Player"
                packageName = "com.example.player"
            }
            ext.backends {
                all()
            }
            ext.theme {
                primaryColor = "#1d4ed8"
                accentColor = "#ff5500"
                light {
                    surfaceColor = "#ffffff"
                }
                dark {
                    accentColor = "#38bdf8"
                    surfaceColor = "#111827"
                }
            }

            val appSassTask = appProject.tasks.getByName("compileGenericUiSass") as GenericUiSassCompileTask
            appSassTask.upstreamResourceClasspath.from(overlayJar, mediaKlibZip, unpackedKlibDir)
            appSassTask.compileStyles()

            val themeKtFile = File(
                appSassTask.kotlinOutputDirectory.get().asFile,
                "com/example/player/styles/GenericUiGeneratedThemeStyleSheet.kt"
            )
            assertTrue(themeKtFile.isFile, "Expected GenericUiGeneratedThemeStyleSheet.kt in consumer app")
            assertFalse(
                File(appSassTask.kotlinOutputDirectory.get().asFile, "com/example/player/styles/ThemePrecompiledStyleSheet.kt").exists(),
                "Consumer theme.scss must compile into GenericUiGeneratedThemeStyleSheet, not duplicate ThemePrecompiledStyleSheet"
            )
            val themeKt = themeKtFile.readText(Charsets.UTF_8)
            assertTrue(themeKt.contains("object GenericUiGeneratedThemeStyleSheet : BlinkStyleSheet()"))
            assertTrue(themeKt.contains("ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(name, toCssStyleSheet())"))
            assertTrue(themeKt.contains("CssStyleSheet("))
            assertTrue(themeKt.contains("isPrecompiled = true"))
            assertTrue(themeKt.contains("\".seek-bar-progress\""), "Expected media .klib theme rule in Kotlin:\n$themeKt")
            assertTrue(themeKt.contains("\".player-btn:not(:disabled):hover\""), "Expected overlay .jar theme rule in Kotlin:\n$themeKt")
            assertTrue(themeKt.contains("\".gui-focus-ring:focus-visible\""), "Expected common klib theme rule in Kotlin:\n$themeKt")
            assertTrue(themeKt.contains("\".consumer-badge\""), "Expected consumer theme rule in Kotlin:\n$themeKt")
            assertTrue(themeKt.contains("RgbaColor(255, 85, 0, 255)"), "Expected consumer #ff5500 light accent in Kotlin:\n$themeKt")
            assertTrue(themeKt.contains("RgbaColor(255, 85, 0, 63)"), "Expected rgba(#ff5500, 0.25) in Kotlin:\n$themeKt")
            assertTrue(themeKt.contains(".bs-theme-dark") || themeKt.contains("[data-bs-theme=\\\"dark\\\"]"), "Expected dark override rule in Kotlin:\n$themeKt")

            // Verify Web distribution compiles gui/genericui-theme.css & updated NAMESPACE_XML_SOURCE / GENERICUI_CSS_SOURCE
            val webTask = appProject.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
            webTask.upstreamResourceClasspath.from(overlayJar, mediaKlibZip, unpackedKlibDir)
            webTask.packageWebDistribution()
            val webOut = webTask.outputDirectory.get().asFile
            val webThemeCss = File(webOut, "gui/genericui-theme.css").readText(Charsets.UTF_8)
            assertTrue(webThemeCss.contains("--genericui-accent: #ff5500;"), "Expected light accent Bootstrap override")
            assertTrue(webThemeCss.contains("--genericui-accent: #38bdf8;"), "Expected dark accent Bootstrap override")
            assertTrue(webThemeCss.contains("background-color: #ff5500;"), "Expected compiled media .klib \$accent rule in gui/genericui-theme.css:\n$webThemeCss")
            assertTrue(webThemeCss.contains("background-color: rgba(255, 85, 0, 0.25);"), "Expected overlay .jar rgba() rule in gui/genericui-theme.css")
            assertTrue(webThemeCss.contains("[data-bs-theme=\"dark\"] .seek-bar-progress"), "Expected dark scoped override in gui/genericui-theme.css:\n$webThemeCss")
            assertTrue(webThemeCss.contains("background-color: #38bdf8;"), "Expected dark accent #38bdf8 rule in gui/genericui-theme.css")
            assertTrue(webThemeCss.contains(".consumer-badge"), "Expected consumer theme rule in gui/genericui-theme.css")

            val namespaceXml = File(webOut, "genericui-namespace.xml").readText(Charsets.UTF_8)
            for (tag in listOf("genericui:composite", "genericui:spacer", "genericui:styled-container", "genericui:layered-container", "genericui:styled-component", "genericui:styled-slider")) {
                assertTrue(namespaceXml.contains("tag=\"$tag\""), "Expected $tag in genericui-namespace.xml")
            }
            val coreWebCss = File(webOut, "gui/genericui.css").readText(Charsets.UTF_8)
            assertTrue(coreWebCss.contains("genericui\\:composite"))
            assertTrue(coreWebCss.contains("genericui\\:styled-component"))
            assertTrue(coreWebCss.contains("genericui\\:styled-slider"))
            assertTrue(coreWebCss.contains(".disabled"))
            assertTrue(coreWebCss.contains(".hidden"))

            // Verify Launchers (Desktop + iOS) & Android automatically detect and register GenericUiGeneratedThemeStyleSheet cleanly
            val launchersTask = appProject.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
            launchersTask.generateLaunchers()
            val launchersOut = launchersTask.outputDirectory.get().asFile
            val macDesc = File(launchersOut, "macos/kotlin/com/example/player/generated/GeneratedMacosAppDescriptor.kt").readText()
            val macMain = File(launchersOut, "macos/kotlin/com/example/player/generated/GeneratedMacosLauncherMain.kt").readText()
            val iosDesc = File(launchersOut, "ios/kotlin/com/example/player/generated/GeneratedIosAppDescriptor.kt").readText()
            val iosMain = File(launchersOut, "ios/kotlin/com/example/player/generated/GeneratedIosLauncherMain.kt").readText()
            assertTrue(macDesc.contains("com.example.player.styles.GenericUiGeneratedThemeStyleSheet.install(app)"))
            assertTrue(macDesc.contains("ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet"))
            assertFalse(macDesc.contains("ThemePrecompiledStyleSheet"))
            assertTrue(macMain.contains("com.example.player.styles.GenericUiGeneratedThemeStyleSheet.install(app)"))
            assertTrue(iosDesc.contains("com.example.player.styles.GenericUiGeneratedThemeStyleSheet.install(app)"))
            assertTrue(iosMain.contains("fun MainViewController()"))
            assertTrue(iosMain.contains("ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet"))

            val androidTask = appProject.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
            androidTask.generate()
            val androidAppKt = File(androidTask.outputDirectory.get().asFile, "com/example/player/GeneratedAndroidApplication.kt").readText()
            assertTrue(androidAppKt.contains("com.example.player.styles.GenericUiGeneratedThemeStyleSheet.install(app)"))
            assertTrue(androidAppKt.contains("ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet"))
        } finally {
            workRoot.deleteRecursively()
        }
    }

    @Test
    fun largeCompiledStylesheetChunksRulesIntoHelperMethodsToAvoidJvmClinitOverflow() {
        val workRoot = java.nio.file.Files.createTempDirectory("genericui_sass_chunk_test_").toFile()
        try {
            val resDir = File(workRoot, "src/commonMain/resources/styles").apply { mkdirs() }
            val commonSrc = File(workRoot, "src/commonMain/kotlin/com/example/chunktest").apply { mkdirs() }
            File(commonSrc, "ChunkAppFragment.kt").writeText(
                """
                package com.example.chunktest

                import com.nexustools.genericui.component.fragment.Fragment
                import com.nexustools.genericui.component.fragment.MainAppFragment

                @MainAppFragment
                class ChunkAppFragment : Fragment()
                """.trimIndent(),
                Charsets.UTF_8
            )
            val scss = buildString {
                for (i in 0 until 520) {
                    appendLine(".large-rule-$i {")
                    appendLine("    background-color: \$primary;")
                    appendLine("    color: \$on-primary;")
                    appendLine("    border-color: \$accent;")
                    appendLine("    opacity: 0.95;")
                    appendLine("}")
                }
            }
            File(resDir, "theme.scss").writeText(scss, Charsets.UTF_8)

            val project = ProjectBuilder.builder().withName("chunk-app").withProjectDir(workRoot).build()
            project.group = "com.example.chunktest"
            project.pluginManager.apply("com.nexustools.genericui")
            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.app {
                id = "com.example.chunktest"
                name = "ChunkTest"
                packageName = "com.example.chunktest"
            }
            ext.backends {
                all()
            }
            ext.theme {
                primaryColor = "#FF5C7C"
                accentColor = "#FF7E96"
            }
            val compileTask = project.tasks.getByName("compileGenericUiSass") as GenericUiSassCompileTask
            compileTask.compileStyles()

            val generatedKt = File(
                compileTask.kotlinOutputDirectory.get().asFile,
                "com/example/chunktest/styles/GenericUiGeneratedThemeStyleSheet.kt"
            ).readText(Charsets.UTF_8)
            assertTrue(generatedKt.contains("override val compiledRules: List<CompiledStyleRule> = buildCompiledRules()"))
            assertTrue(generatedKt.contains("private fun buildCompiledRules(): List<CompiledStyleRule>"))
            assertTrue(generatedKt.contains("private fun buildCompiledRulesChunk0(): List<CompiledStyleRule>"))
            assertTrue(generatedKt.contains("private fun buildCompiledRulesChunk25(): List<CompiledStyleRule>"))
            assertTrue(generatedKt.contains("sourceOrder = 519"))
            assertFalse(generatedKt.contains("override val compiledRules: List<CompiledStyleRule> = listOf("))
        } finally {
            workRoot.deleteRecursively()
        }
    }
}
