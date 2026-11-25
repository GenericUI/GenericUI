package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenericUiSassCompileTaskBackendTagsTest {

    @Test
    fun `GenericUiBackendStyleMask parses expressions canonicalizes and detects directives accurately`() {
        assertEquals(GenericUiBackendStyleMask.MACOS, GenericUiBackendStyleMask.parseToken("macos"))
        assertEquals(GenericUiBackendStyleMask.MACOS, GenericUiBackendStyleMask.parseToken("Darwin"))
        assertEquals(GenericUiBackendStyleMask.SYSTEM, GenericUiBackendStyleMask.parseToken("system"))
        assertEquals(GenericUiBackendStyleMask.DESKTOP, GenericUiBackendStyleMask.parseToken("desktop"))
        assertEquals(GenericUiBackendStyleMask.MOBILE, GenericUiBackendStyleMask.parseToken("mobile"))
        assertEquals(GenericUiBackendStyleMask.APPLE, GenericUiBackendStyleMask.parseToken("apple"))
        assertEquals(GenericUiBackendStyleMask.WEB, GenericUiBackendStyleMask.parseToken("browser"))
        assertNull(GenericUiBackendStyleMask.parseToken("media"))

        assertEquals(GenericUiBackendStyleMask.SYSTEM, GenericUiBackendStyleMask.parseExpression("not web"))
        assertEquals(
            GenericUiBackendStyleMask.WINDOWS or GenericUiBackendStyleMask.LINUX,
            GenericUiBackendStyleMask.parseExpression("windows, linux")
        )
        assertEquals(
            GenericUiBackendStyleMask.IOS_FAMILY,
            GenericUiBackendStyleMask.parseExpression("apple and not macos")
        )
        assertEquals(
            GenericUiBackendStyleMask.DESKTOP and GenericUiBackendStyleMask.MACOS.inv(),
            GenericUiBackendStyleMask.parseExpression("not macos and desktop")
        )
        assertEquals(
            GenericUiBackendStyleMask.DESKTOP or GenericUiBackendStyleMask.IOS_FAMILY,
            GenericUiBackendStyleMask.parseExpression("desktop or mobile and not android")
        )
        assertEquals(
            GenericUiBackendStyleMask.WINDOWS or GenericUiBackendStyleMask.MACOS,
            GenericUiBackendStyleMask.parseExpression("(windows or macos) and not swing")
        )
        assertNull(GenericUiBackendStyleMask.parseExpression(""))
        assertNull(GenericUiBackendStyleMask.parseExpression("windows, "))
        assertNull(GenericUiBackendStyleMask.parseExpression("(windows"))
        assertNull(GenericUiBackendStyleMask.parseExpression("unknown_platform"))

        assertEquals(GenericUiBackendStyleMask.MACOS, GenericUiBackendStyleMask.parseDirectiveHeader("@macos"))
        assertEquals(GenericUiBackendStyleMask.WEB, GenericUiBackendStyleMask.parseDirectiveHeader("@web"))
        assertEquals(GenericUiBackendStyleMask.SYSTEM, GenericUiBackendStyleMask.parseDirectiveHeader("@backend(not web)"))
        assertEquals(
            GenericUiBackendStyleMask.IOS_FAMILY,
            GenericUiBackendStyleMask.parseDirectiveHeader("@platform(apple and not macos)")
        )
        assertEquals(
            GenericUiBackendStyleMask.NATIVE_DESKTOP,
            GenericUiBackendStyleMask.parseDirectiveHeader("@target(desktop and not swing)")
        )
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@media (min-width: 768px)"))
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@keyframes pulse"))
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@-webkit-keyframes pulse"))
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@supports (display: grid)"))
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@font-face"))
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@layer base"))
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@container (min-width: 400px)"))
        assertNull(GenericUiBackendStyleMask.parseDirectiveHeader("@property --x"))

        val winLinux = GenericUiBackendStyleMask.WINDOWS or GenericUiBackendStyleMask.LINUX
        assertEquals("@backend(windows, gtk, kde)", GenericUiBackendStyleMask.formatCanonicalDirective(winLinux))
        assertEquals(winLinux, GenericUiBackendStyleMask.parseDirectiveHeader(GenericUiBackendStyleMask.formatCanonicalDirective(winLinux)))
        assertEquals("@macos", GenericUiBackendStyleMask.formatCanonicalDirective(GenericUiBackendStyleMask.MACOS))
        assertEquals("BackendStyleMask.ALL", GenericUiBackendStyleMask.formatKotlinExpr(GenericUiBackendStyleMask.ALL))
        assertEquals("BackendStyleMask.MACOS", GenericUiBackendStyleMask.formatKotlinExpr(GenericUiBackendStyleMask.MACOS))
        assertEquals("BackendStyleMask.DESKTOP", GenericUiBackendStyleMask.formatKotlinExpr(GenericUiBackendStyleMask.DESKTOP))
        assertEquals("0x00D", GenericUiBackendStyleMask.formatKotlinExpr(winLinux))

        assertFalse(GenericUiBackendStyleMask.containsGenericUiBackendDirective("/* @macos { .a { color: red; } } */\n.b { content: \"@web {}\"; }\n@media (min-width: 600px) { .c { color: blue; } }"))
        assertTrue(GenericUiBackendStyleMask.containsGenericUiBackendDirective("@media (min-width: 600px) {\n  @macos { .c { color: blue; } }\n}"))
    }

    @Test
    fun `compileGenericUiSass strips non-web directives unwraps web directives and emits backendMask in Kotlin`() {
        val workRoot = Files.createTempDirectory("genericui_backend_tags_test_").toFile()
        try {
            val project = ProjectBuilder.builder().withName("backend-tags-app").withProjectDir(workRoot).build()
            project.group = "com.example.tags"
            project.pluginManager.apply("com.nexustools.genericui")

            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.app {
                id = "com.example.tags"
                name = "TagsApp"
                packageName = "com.example.tags"
            }
            ext.backends {
                all()
            }
            ext.styles {
                compileSass = true
                generateWebCss = true
                precompileToKotlin = true
                includeBootstrapVariables = false
                webCssBundleFileName = "bundle.css"
                sourceDir = "src/commonMain/resources"
            }
            ext.theme {
                primaryColor = "#2563eb"
                accentColor = "#ff5500"
                light {
                    surfaceColor = "#ffffff"
                }
                dark {
                    accentColor = "#38bdf8"
                    surfaceColor = "#0f172a"
                }
            }

            val resDir = File(workRoot, "src/commonMain/resources").apply { mkdirs() }
            val stylesSubDir = File(resDir, "styles").apply { mkdirs() }

            File(resDir, "style.scss").writeText(
                """
                .shared-box {
                  opacity: 0.9;
                }

                @macos {
                  .mac-only {
                    padding: 14px;
                  }
                  @keyframes macPulse {
                    from { opacity: 0.2; }
                    to { opacity: 1; }
                  }
                }

                @system {
                  .system-only {
                    margin: 6px;
                  }
                }

                @desktop {
                  .desktop-only {
                    border-width: 1px;
                  }
                }

                @mobile {
                  .mobile-only {
                    padding: 20px;
                  }
                  @macos {
                    .contradictory-never-emitted {
                      opacity: 0;
                    }
                  }
                }

                @apple {
                  .apple-only {
                    border-radius: 10px;
                  }
                }

                @backend(windows, linux) {
                  .win-linux-only {
                    margin-left: 4px;
                  }
                }

                @platform(apple and not macos) {
                  .ios-family-only {
                    margin-top: 8px;
                  }
                }

                @backend(not web) {
                  .not-web-only {
                    z-index: 5;
                  }
                }

                @web {
                  .web-only {
                    padding: 2px;
                  }
                  @keyframes webFade {
                    0% { opacity: 0; }
                    100% { opacity: 1; }
                  }
                }

                @browser {
                  .browser-only {
                    margin-right: 3px;
                  }
                }

                .card {
                  width: 100%;
                  @macos {
                    padding: 12px;
                  }
                  @web {
                    padding: 16px;
                  }
                }

                @media (min-width: 768px) {
                  @macos {
                    .wide-mac {
                      width: 640px;
                    }
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            File(stylesSubDir, "app.scss").writeText(
                """
                @macos {
                  .nested-mac {
                    color: #111111;
                  }
                }
                @web {
                  .nested-web {
                    color: #222222;
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            File(resDir, "theme.scss").writeText(
                """
                .theme-shared {
                  color: ${'$'}accent;
                }
                @macos {
                  .theme-mac {
                    background-color: ${'$'}accent;
                  }
                }
                @desktop {
                  .theme-desktop {
                    border-color: ${'$'}primary;
                  }
                }
                @web {
                  .theme-web {
                    background-color: ${'$'}accent;
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            val sassTask = project.tasks.getByName("compileGenericUiSass") as GenericUiSassCompileTask
            sassTask.compileStyles()

            val webCssDir = sassTask.webCssOutputDirectory.get().asFile
            val webStyleCss = File(webCssDir, "style.css").readText(Charsets.UTF_8)
            val webNestedAppCss = File(webCssDir, "styles/app.css").readText(Charsets.UTF_8)
            val webRootAppCss = File(webCssDir, "app.css").readText(Charsets.UTF_8)
            val webBundleCss = File(webCssDir, "bundle.css").readText(Charsets.UTF_8)
            val webThemeCss = File(webCssDir, "genericui-theme.css").readText(Charsets.UTF_8)

            for ((label, css) in listOf(
                "web style.css" to webStyleCss,
                "web styles/app.css" to webNestedAppCss,
                "web root app.css" to webRootAppCss,
                "web bundle.css" to webBundleCss,
                "web genericui-theme.css" to webThemeCss
            )) {
                assertFalse(
                    GenericUiBackendStyleMask.containsGenericUiBackendDirective(css),
                    "Expected zero GenericUI @ directives in $label, got:\n$css"
                )
            }

            // Web CSS drops all non-web rules & keyframes and unwraps @web/@browser rules
            assertTrue(webStyleCss.contains(".shared-box"))
            assertTrue(webStyleCss.contains(".web-only"))
            assertTrue(webStyleCss.contains(".browser-only"))
            assertTrue(webStyleCss.contains("@keyframes webFade"))
            assertTrue(webStyleCss.contains("padding: 16px;"))
            assertFalse(webStyleCss.contains(".mac-only"))
            assertFalse(webStyleCss.contains("macPulse"))
            assertFalse(webStyleCss.contains(".system-only"))
            assertFalse(webStyleCss.contains(".desktop-only"))
            assertFalse(webStyleCss.contains(".mobile-only"))
            assertFalse(webStyleCss.contains(".apple-only"))
            assertFalse(webStyleCss.contains(".win-linux-only"))
            assertFalse(webStyleCss.contains(".ios-family-only"))
            assertFalse(webStyleCss.contains(".not-web-only"))
            assertFalse(webStyleCss.contains(".wide-mac"))
            assertFalse(webStyleCss.contains(".contradictory-never-emitted"))
            assertFalse(webStyleCss.contains("padding: 12px;"))

            assertTrue(webNestedAppCss.contains(".nested-web"))
            assertFalse(webNestedAppCss.contains(".nested-mac"))
            assertTrue(webRootAppCss.contains(".nested-web"))
            assertFalse(webRootAppCss.contains(".nested-mac"))
            assertTrue(webBundleCss.contains(".web-only"))
            assertTrue(webBundleCss.contains(".nested-web"))
            assertFalse(webBundleCss.contains(".mac-only"))
            assertFalse(webBundleCss.contains(".nested-mac"))

            assertTrue(webThemeCss.contains(".theme-shared"))
            assertTrue(webThemeCss.contains(".theme-web"))
            assertTrue(webThemeCss.contains("[data-bs-theme=\"dark\"] .theme-web"))
            assertFalse(webThemeCss.contains(".theme-mac"))
            assertFalse(webThemeCss.contains(".theme-desktop"))

            // Native Kotlin precompiled stylesheets drop @web-only rules and emit exact backendMask
            val ktDir = sassTask.kotlinOutputDirectory.get().asFile
            val styleKt = File(ktDir, "com/example/tags/styles/StylePrecompiledStyleSheet.kt").readText(Charsets.UTF_8)
            val appKt = File(ktDir, "com/example/tags/styles/AppPrecompiledStyleSheet.kt").readText(Charsets.UTF_8)
            val themeKt = File(ktDir, "com/example/tags/styles/GenericUiGeneratedThemeStyleSheet.kt").readText(Charsets.UTF_8)

            assertFalse(styleKt.contains(".web-only"), "Web-only rules must not be emitted into BlinkStyleSheet:\n$styleKt")
            assertFalse(styleKt.contains(".browser-only"))
            assertFalse(styleKt.contains("webFade"))
            assertFalse(styleKt.contains(".contradictory-never-emitted"))
            assertFalse(styleKt.contains("padding: 16px") || styleKt.contains("CssLength(16.0f, CssUnit.PX)"))

            assertTrue(styleKt.contains("macPulse"), "System @keyframes inside @macos must be retained")
            assertTrue(styleKt.contains("selector = \".mac-only\"") && styleKt.contains("backendMask = BackendStyleMask.MACOS"))
            assertTrue(styleKt.contains("selector = \".system-only\"") && styleKt.contains("backendMask = BackendStyleMask.SYSTEM"))
            assertTrue(styleKt.contains("selector = \".desktop-only\"") && styleKt.contains("backendMask = BackendStyleMask.DESKTOP"))
            assertTrue(styleKt.contains("selector = \".mobile-only\"") && styleKt.contains("backendMask = BackendStyleMask.MOBILE"))
            assertTrue(styleKt.contains("selector = \".apple-only\"") && styleKt.contains("backendMask = BackendStyleMask.APPLE"))
            assertTrue(styleKt.contains("selector = \".ios-family-only\"") && styleKt.contains("backendMask = BackendStyleMask.IOS_FAMILY"))
            assertTrue(styleKt.contains("selector = \".win-linux-only\"") && styleKt.contains("backendMask = 0x00D"))
            assertTrue(styleKt.contains("selector = \".not-web-only\"") && styleKt.contains("backendMask = BackendStyleMask.SYSTEM"))

            // Nested @media + @macos preserves both mediaMinWidthPx and backendMask
            val wideMacBlock = styleKt.substringAfter("selector = \".wide-mac\",").substringBefore("pseudoClass =")
            assertTrue(
                wideMacBlock.contains("mediaMinWidthPx = 768") && wideMacBlock.contains("backendMask = BackendStyleMask.MACOS"),
                "Expected nested @media + @macos to emit both mediaMinWidthPx and backendMask:\n$wideMacBlock"
            )

            // Untagged .shared-box rule should not emit an explicit backendMask (uses default BackendStyleMask.ALL)
            val sharedBlock = styleKt.substringAfter("selector = \".shared-box\",").substringBefore("pseudoClass =")
            assertFalse(sharedBlock.contains("backendMask ="), "Untagged rule should use default BackendStyleMask.ALL:\n$sharedBlock")

            assertTrue(appKt.contains("selector = \".nested-mac\"") && appKt.contains("backendMask = BackendStyleMask.MACOS"))
            assertFalse(appKt.contains(".nested-web"))

            assertTrue(themeKt.contains("selector = \".theme-shared\""))
            assertTrue(themeKt.contains("selector = \".theme-mac\"") && themeKt.contains("backendMask = BackendStyleMask.MACOS"))
            assertTrue(themeKt.contains("selector = \".theme-desktop\"") && themeKt.contains("backendMask = BackendStyleMask.DESKTOP"))
            val darkThemeMacBlock = themeKt
                .substringAfter("selector = \"[data-bs-theme=\\\"dark\\\"] .theme-mac\",")
                .substringBefore("pseudoClass =")
            assertTrue(
                themeKt.contains("selector = \"[data-bs-theme=\\\"dark\\\"] .theme-mac\"") &&
                    darkThemeMacBlock.contains("backendMask = BackendStyleMask.MACOS"),
                "Expected dark override for @macos theme rule with BackendStyleMask.MACOS in Kotlin:\n$themeKt"
            )
            assertFalse(themeKt.contains(".theme-web"), "Web-only theme rules must not appear in GenericUiGeneratedThemeStyleSheet.kt")
        } finally {
            workRoot.deleteRecursively()
        }
    }

    @Test
    fun `packageGenericUiWebDistribution strips backend directives from theme and staged css assets`() {
        val workRoot = Files.createTempDirectory("genericui_web_dist_tags_test_").toFile()
        try {
            val project = ProjectBuilder.builder().withName("web-dist-tags").withProjectDir(workRoot).build()
            project.group = "com.example.webtags"
            project.pluginManager.apply("com.nexustools.genericui")

            val ext = project.extensions.getByType(GenericUiExtension::class.java)
            ext.app {
                id = "com.example.webtags"
                name = "WebTags"
                packageName = "com.example.webtags"
            }
            ext.backends {
                web()
            }
            ext.theme {
                primaryColor = "#1d4ed8"
                accentColor = "#ff5500"
                dark {
                    accentColor = "#38bdf8"
                }
            }

            val resDir = File(workRoot, "src/commonMain/resources").apply { mkdirs() }
            val stylesSubDir = File(resDir, "styles").apply { mkdirs() }
            File(stylesSubDir, "theme.scss").writeText(
                """
                @macos {
                  .mac-theme-rule {
                    color: ${'$'}accent;
                  }
                }
                @web {
                  .web-theme-rule {
                    color: ${'$'}accent;
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )
            File(stylesSubDir, "style.scss").writeText(
                """
                @system {
                  .native-only {
                    padding: 4px;
                  }
                }
                @web {
                  .browser-only {
                    padding: 8px;
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )
            // Also include a raw .css resource with directives to verify copyWebCssOrFile sanitization
            File(resDir, "extra.css").writeText(
                """
                @macos {
                  .extra-mac { color: #111111; }
                }
                @web {
                  .extra-web { color: #222222; }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            val sassTask = project.tasks.getByName("compileGenericUiSass") as GenericUiSassCompileTask
            sassTask.compileStyles()

            val webTask = project.tasks.getByName("packageGenericUiWebDistribution") as GenericUiWebDistributionTask
            webTask.packageWebDistribution()

            val webOut = webTask.outputDirectory.get().asFile
            val distThemeCss = File(webOut, "gui/genericui-theme.css").readText(Charsets.UTF_8)
            assertFalse(GenericUiBackendStyleMask.containsGenericUiBackendDirective(distThemeCss))
            assertTrue(distThemeCss.contains(".web-theme-rule"))
            assertTrue(distThemeCss.contains("[data-bs-theme=\"dark\"] .web-theme-rule"))
            assertFalse(distThemeCss.contains(".mac-theme-rule"))

            val distStyleCss = File(webOut, "app/styles/style.css").readText(Charsets.UTF_8)
            assertFalse(GenericUiBackendStyleMask.containsGenericUiBackendDirective(distStyleCss))
            assertTrue(distStyleCss.contains(".browser-only"))
            assertFalse(distStyleCss.contains(".native-only"))

            val distExtraCss = File(webOut, "app/extra.css").readText(Charsets.UTF_8)
            assertFalse(GenericUiBackendStyleMask.containsGenericUiBackendDirective(distExtraCss))
            assertTrue(distExtraCss.contains(".extra-web"))
            assertFalse(distExtraCss.contains(".extra-mac"))
        } finally {
            workRoot.deleteRecursively()
        }
    }
}
