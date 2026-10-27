package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Phase2ModelAndResolutionTest {

    @Test
    fun `scanner parses MainAppFragment and AppFragment from Kotlin sources and files`() {
        val project = ProjectBuilder.builder().build()
        val commonDir = File(project.projectDir, "src/commonMain/kotlin/com/example/studio").apply { mkdirs() }
        File(commonDir, "Fragments.kt").writeText(
            """
            package com.example.studio

            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget

            // @MainAppFragment(targets = [AppTarget.WEB]) ignored in comment
            @MainAppFragment(
                targets = [AppTarget.COMMON],
                label = "Studio Home",
                route = "home",
                screenOrientation = "landscape"
            )
            class HomeFragment : Fragment()

            @AppFragment(
                targets = [AppTarget.ANDROID, AppTarget.DESKTOP_COMMON],
                label = "Settings",
                route = "settings",
                exported = true
            )
            class SettingsActivity(val showAdvanced: Boolean = true) : Fragment()
            """.trimIndent()
        )

        val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
        assertEquals(2, discovered.size)

        val home = discovered.first { it.simpleName == "HomeFragment" }
        assertTrue(home.isMainFragment)
        assertEquals("com.example.studio.HomeFragment", home.qualifiedName)
        assertEquals(listOf(GenericUiVariantScope.COMMON), home.targets)
        assertEquals("Studio Home", home.label)
        assertEquals("home", home.route)
        assertTrue(home.exported)
        assertEquals("landscape", home.screenOrientation)
        assertTrue(home.hasNoArgConstructor)
        assertEquals("HomeFragmentActivity", home.generatedActivitySimpleName)
        assertEquals("com.example.studio.HomeFragmentActivity", home.generatedActivityQualifiedName)

        val settings = discovered.first { it.simpleName == "SettingsActivity" }
        assertFalse(settings.isMainFragment)
        assertEquals(
            listOf(GenericUiVariantScope.ANDROID, GenericUiVariantScope.DESKTOP_COMMON),
            settings.targets
        )
        assertEquals("Settings", settings.label)
        assertEquals("settings", settings.route)
        assertTrue(settings.exported)
        assertTrue(settings.hasNoArgConstructor)
        assertEquals("SettingsActivityHostActivity", settings.generatedActivitySimpleName)
    }

    @Test
    fun `resolver resolves single COMMON MainAppFragment across all 8 backends`() {
        val discovered = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.app
            import com.nexustools.genericui.component.fragment.MainAppFragment

            @MainAppFragment
            class UniversalMainFragment
            """.trimIndent()
        )

        val resolvedByBackend = GenericUiMainFragmentResolver.resolveAllBackends(discovered)
        assertEquals(8, resolvedByBackend.size)
        for (backend in GenericUiVariantScope.ALL_BACKENDS) {
            assertEquals(
                "com.example.app.UniversalMainFragment",
                resolvedByBackend[backend]?.qualifiedName,
                "Expected UniversalMainFragment for backend ${backend.id}"
            )
        }
    }

    @Test
    fun `resolver picks most specific MainAppFragment per backend across COMMON MOBILE_COMMON and WINDOWS`() {
        val discovered = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.app

            @MainAppFragment(targets = [AppTarget.COMMON])
            class CommonMainFragment

            @MainAppFragment(targets = [AppTarget.MOBILE_COMMON])
            class MobileMainFragment

            @MainAppFragment(targets = [AppTarget.WINDOWS])
            class WindowsMainFragment
            """.trimIndent()
        )

        assertEquals(
            "com.example.app.WindowsMainFragment",
            GenericUiMainFragmentResolver.resolveMainFragmentForBackend(discovered, GenericUiVariantScope.WINDOWS).qualifiedName
        )
        assertEquals(
            "com.example.app.MobileMainFragment",
            GenericUiMainFragmentResolver.resolveMainFragmentForBackend(discovered, GenericUiVariantScope.ANDROID).qualifiedName
        )
        assertEquals(
            "com.example.app.MobileMainFragment",
            GenericUiMainFragmentResolver.resolveMainFragmentForBackend(discovered, GenericUiVariantScope.IOS).qualifiedName
        )
        for (backend in listOf(
            GenericUiVariantScope.WEB,
            GenericUiVariantScope.MACOS,
            GenericUiVariantScope.GTK,
            GenericUiVariantScope.QT,
            GenericUiVariantScope.SWING
        )) {
            assertEquals(
                "com.example.app.CommonMainFragment",
                GenericUiMainFragmentResolver.resolveMainFragmentForBackend(discovered, backend).qualifiedName,
                "Expected CommonMainFragment for backend ${backend.id}"
            )
        }
    }

    @Test
    fun `resolver fails with descriptive error when two MainAppFragment classes tie at highest specificity rank`() {
        val discovered = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.conflict

            @MainAppFragment(targets = [AppTarget.DESKTOP_COMMON])
            class FirstDesktopFragment

            @MainAppFragment(targets = [AppTarget.DESKTOP_COMMON, AppTarget.COMMON])
            class SecondDesktopFragment
            """.trimIndent()
        )

        val ex = assertFailsWith<IllegalStateException> {
            GenericUiMainFragmentResolver.resolveMainFragmentForBackend(discovered, GenericUiVariantScope.WINDOWS)
        }
        val msg = ex.message.orEmpty()
        assertTrue(msg.contains("com.example.conflict.FirstDesktopFragment"), "Error should name FirstDesktopFragment: $msg")
        assertTrue(msg.contains("com.example.conflict.SecondDesktopFragment"), "Error should name SecondDesktopFragment: $msg")
        assertTrue(msg.contains("windows"), "Error should name target backend 'windows': $msg")
    }

    @Test
    fun `resolver fails when MainAppFragment or AppFragment lacks no-arg constructor`() {
        val discovered = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.invalid

            @MainAppFragment(targets = [AppTarget.COMMON])
            class ParameterizedMainFragment(val requiredToken: String)
            """.trimIndent()
        )

        assertFalse(discovered.single().hasNoArgConstructor)
        val ex = assertFailsWith<IllegalStateException> {
            GenericUiMainFragmentResolver.resolveMainFragmentForBackend(discovered, GenericUiVariantScope.WEB)
        }
        assertTrue(ex.message.orEmpty().contains("com.example.invalid.ParameterizedMainFragment"))
        assertTrue(ex.message.orEmpty().contains("no-arg constructor"))
    }

    @Test
    fun `variant resolver cascades overrides along specificity chain and enforces android-only services and manifest`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        ext.app {
            id = "com.example.cascade"
            name = "Cascade App"
            version = "2.4.0"
        }
        ext.common {
            window {
                title = "Cascade Common"
                size(1024, 600)
                minSize(400, 300)
                resizable = true
            }
            theme {
                mode = "dark"
                color = "#112233"
            }
            icon("common-icon.png")
        }
        ext.desktopCommon {
            window {
                title = "Cascade Desktop"
                size(1440, 900)
            }
            theme {
                color = "#445566"
            }
            icon("desktop-icon.png")
        }
        ext.windows {
            window {
                title = "Cascade Windows Pro"
                size(1920, 1080)
                fullscreen = true
            }
            theme {
                mode = "light"
                color = "#778899"
            }
            icon("win-icon.ico")
        }
        ext.android {
            manifest {
                permissions("android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS")
                supportsPictureInPicture = true
            }
            services {
                service("com.example.cascade.PlaybackService") {
                    exported = false
                    foregroundServiceType = "mediaPlayback"
                    intentAction("com.example.cascade.ACTION_PLAY")
                }
            }
        }

        val webResolved = GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WEB)
        assertEquals("com.example.cascade", webResolved.appId)
        assertEquals("Cascade App", webResolved.appName)
        assertEquals("2.4.0", webResolved.appVersion)
        assertEquals("Cascade Common", webResolved.windowTitle)
        assertEquals(1024, webResolved.windowWidth)
        assertEquals(600, webResolved.windowHeight)
        assertEquals("dark", webResolved.themeMode)
        assertEquals("#112233", webResolved.themeColor)
        assertEquals("common-icon.png", webResolved.iconPath)

        val macResolved = GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.MACOS)
        assertEquals("Cascade Desktop", macResolved.windowTitle)
        assertEquals(1440, macResolved.windowWidth)
        assertEquals(900, macResolved.windowHeight)
        assertEquals(400, macResolved.minWindowWidth)
        assertEquals(300, macResolved.minWindowHeight)
        assertEquals("dark", macResolved.themeMode)
        assertEquals("#445566", macResolved.themeColor)
        assertEquals("desktop-icon.png", macResolved.iconPath)

        val winResolved = GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WINDOWS)
        assertEquals("Cascade Windows Pro", winResolved.windowTitle)
        assertEquals(1920, winResolved.windowWidth)
        assertEquals(1080, winResolved.windowHeight)
        assertTrue(winResolved.fullscreen)
        assertEquals("light", winResolved.themeMode)
        assertEquals("#778899", winResolved.themeColor)
        assertEquals("win-icon.ico", winResolved.iconPath)

        assertEquals(2, ext.android.manifestConfig.permissions.size)
        assertTrue(ext.android.manifestConfig.supportsPictureInPicture)
        assertEquals(1, ext.android.servicesConfig.services.size)
        assertEquals("com.example.cascade.PlaybackService", ext.android.servicesConfig.services.single().className)
        assertEquals("mediaPlayback", ext.android.servicesConfig.services.single().foregroundServiceType)

        val winServicesEx = assertFailsWith<GradleException> {
            ext.windows {
                services {
                    service("com.example.InvalidWinService")
                }
            }
        }
        assertTrue(winServicesEx.message.orEmpty().contains("Android-only"))
        assertTrue(winServicesEx.message.orEmpty().contains("windows"))

        val commonServicesEx = assertFailsWith<GradleException> {
            ext.common {
                services {
                    service("com.example.InvalidCommonService")
                }
            }
        }
        assertTrue(commonServicesEx.message.orEmpty().contains("Android-only"))
        assertTrue(commonServicesEx.message.orEmpty().contains("common"))

        val winManifestEx = assertFailsWith<GradleException> {
            ext.windows {
                manifest {
                    permission("android.permission.CAMERA")
                }
            }
        }
        assertTrue(winManifestEx.message.orEmpty().contains("Android-only"))
    }

    @Test
    fun `variant resolver defaults themeMode to auto and cascades globalStyleSheet across styles app and variants`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.nexustools.genericui")
        val ext = project.extensions.getByType(GenericUiExtension::class.java)

        // Default unconfigured themeMode must be "auto"
        val defaultWeb = GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WEB)
        val defaultWin = GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WINDOWS)
        assertEquals("auto", defaultWeb.themeMode)
        assertEquals("auto", defaultWin.themeMode)

        ext.styles {
            globalStyleSheet = "styles/global.scss"
        }
        assertEquals("styles/global.scss", GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WEB).globalStyleSheet)

        ext.desktopCommon {
            styleSheet("styles/desktop.scss")
        }
        ext.windows {
            styleSheet = "styles/windows.scss"
        }
        assertEquals("styles/global.scss", GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WEB).globalStyleSheet)
        assertEquals("styles/desktop.scss", GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.MACOS).globalStyleSheet)
        assertEquals("styles/windows.scss", GenericUiVariantResolver.resolve(ext, GenericUiVariantScope.WINDOWS).globalStyleSheet)
    }
}
