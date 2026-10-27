package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase2CodegenAndAndroidTasksTest {

    @Test
    fun `single commonMain project generates per-target launchers 1-to-1 Android activities manifest iosApp and multi-backend Resource class`() {
        val project = ProjectBuilder.builder().withName("studio-app").build()
        project.group = "com.example.studio"
        project.version = "1.2.0"
        project.pluginManager.apply("com.nexustools.genericui")

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.app {
            id = "com.example.studio"
            name = "Studio App"
            version = "1.2.0"
            packageName = "com.example.studio"
        }
        ext.backends {
            all()
        }
        ext.extensions {
            dsl(true)
            canvas2d(true)
            mediaControls(true)
        }
        ext.common {
            window {
                title = "Studio Universal"
                size(1280, 720)
            }
            theme {
                color = "#0d6efd"
            }
        }
        ext.styles {
            globalStyleSheet = "styles/studio.scss"
        }
        ext.windows {
            window {
                title = "Studio Windows"
                size(1600, 900)
            }
        }
        ext.android {
            manifest {
                permissions("android.permission.INTERNET", "android.permission.FOREGROUND_SERVICE")
                supportsPictureInPicture = true
            }
            services {
                service("com.example.studio.BackgroundSyncService") {
                    exported = false
                    foregroundServiceType = "dataSync"
                    intentAction("com.example.studio.ACTION_SYNC")
                }
            }
        }

        // Write single-commonMain fragments (@MainAppFragment for COMMON and MOBILE_COMMON, plus @AppFragment)
        val srcDir = File(project.projectDir, "src/commonMain/kotlin/com/example/studio").apply { mkdirs() }
        File(srcDir, "StudioFragments.kt").writeText(
            """
            package com.example.studio

            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget

            @MainAppFragment(targets = [AppTarget.COMMON], label = "Studio Desktop & Web")
            class DesktopAndWebMainFragment : Fragment()

            @MainAppFragment(targets = [AppTarget.MOBILE_COMMON], label = "Studio Mobile")
            class MobileMainFragment : Fragment()

            @AppFragment(targets = [AppTarget.COMMON], label = "Preferences", exported = true)
            class PreferencesFragment : Fragment()
            """.trimIndent()
        )

        // Write resources in src/commonMain/resources (drawable, values/strings.xml, xml/app_config.xml)
        val resRoot = File(project.projectDir, "src/commonMain/resources").apply { mkdirs() }
        val drawableDir = File(resRoot, "drawable").apply { mkdirs() }
        File(drawableDir, "logo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val valuesDir = File(resRoot, "values").apply { mkdirs() }
        File(valuesDir, "strings.xml").writeText(
            """
            <resources>
                <string name="app_title">Studio App</string>
                <color name="brand_primary">#0d6efd</color>
                <dimen name="toolbar_height">48dp</dimen>
                <integer name="max_recent_files">10</integer>
                <bool name="enable_grid">true</bool>
            </resources>
            """.trimIndent()
        )
        val xmlDir = File(resRoot, "xml").apply { mkdirs() }
        File(xmlDir, "app_config.xml").writeText("<config version=\"1\"/>")
        val stylesDir = File(resRoot, "styles").apply { mkdirs() }
        File(stylesDir, "studio.scss").writeText(".studio-shell { background-color: @color/brand_primary; }")
        val cursorsDir = File(resRoot, "cursors").apply { mkdirs() }
        File(cursorsDir, "crosshair.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M12 2L12 22M2 12L22 12\"/></svg>")

        // 1. Validate app
        val validateTask = project.tasks.getByName("genericuiValidateApp") as GenericUiValidateAppTask
        validateTask.validateApp()
        val validationReport = validateTask.validationReportFile.get().asFile.readText()
        assertTrue(validationReport.contains("backend.windows.mainFragment=com.example.studio.DesktopAndWebMainFragment"))
        assertTrue(validationReport.contains("backend.web.mainFragment=com.example.studio.DesktopAndWebMainFragment"))
        assertTrue(validationReport.contains("backend.android.mainFragment=com.example.studio.MobileMainFragment"))
        assertTrue(validationReport.contains("backend.ios.mainFragment=com.example.studio.MobileMainFragment"))

        // 2. Generate launchers for Windows, macOS, GTK, Qt, Swing, iOS, Web
        val launchersTask = project.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersTask.generateLaunchers()
        val launchersRoot = launchersTask.outputDirectory.get().asFile

        val winDescriptor = File(launchersRoot, "windows/kotlin/com/example/studio/generated/GeneratedWindowsAppDescriptor.kt")
        val winMain = File(launchersRoot, "windows/kotlin/com/example/studio/generated/GeneratedWindowsLauncherMain.kt")
        assertTrue(winDescriptor.isFile, "Expected GeneratedWindowsAppDescriptor.kt")
        assertTrue(winMain.isFile, "Expected GeneratedWindowsLauncherMain.kt")
        val winDescriptorText = winDescriptor.readText()
        assertTrue(winDescriptorText.contains("windowTitle = \"Studio Windows\""))
        assertTrue(winDescriptorText.contains("windowWidth = 1600"))
        assertTrue(winDescriptorText.contains("themeMode = \"auto\""))
        assertTrue(winDescriptorText.contains("globalStyleSheetPath = \"styles/studio.scss\""))
        assertTrue(winDescriptorText.contains("app.addStyleSheet(\"styles/studio.scss\")"))
        assertTrue(winDescriptorText.contains("mainFragmentClass = com.example.studio.DesktopAndWebMainFragment::class"))
        assertTrue(winDescriptorText.contains("WindowsMediaControlsProvider.register()"))
        assertTrue(winMain.readText().contains("app.addStyleSheet(\"styles/studio.scss\")"))
        assertTrue(winMain.readText().contains("WindowsLauncher.launch(args, GeneratedWindowsAppDescriptor.create())"))

        val webDescriptor = File(launchersRoot, "web/kotlin/com/example/studio/generated/GeneratedWebAppDescriptor.kt")
        val webMain = File(launchersRoot, "web/kotlin/com/example/studio/generated/GeneratedWebLauncherMain.kt")
        assertTrue(webDescriptor.isFile, "Expected GeneratedWebAppDescriptor.kt")
        assertTrue(webMain.isFile, "Expected GeneratedWebLauncherMain.kt")
        assertTrue(webDescriptor.readText().contains("WebMediaControlsProvider.register()"))
        assertTrue(webMain.readText().contains("WebLauncher.launch(GeneratedWebAppDescriptor.create())"))

        val iosDescriptor = File(launchersRoot, "ios/kotlin/com/example/studio/generated/GeneratedIosAppDescriptor.kt")
        val iosMain = File(launchersRoot, "ios/kotlin/com/example/studio/generated/GeneratedIosLauncherMain.kt")
        assertTrue(iosDescriptor.isFile, "Expected GeneratedIosAppDescriptor.kt")
        assertTrue(iosMain.isFile, "Expected GeneratedIosLauncherMain.kt")
        assertTrue(iosDescriptor.readText().contains("mainFragmentClass = com.example.studio.MobileMainFragment::class"))
        assertTrue(iosMain.readText().contains("fun MainViewController()"))

        // 3. Generate 1:1 Android AppCompatActivity classes + GeneratedAndroidApplication
        val androidActivitiesTask = project.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
        androidActivitiesTask.generate()
        val androidOutDir = androidActivitiesTask.outputDirectory.get().asFile
        val mobileMainActivityFile = File(androidOutDir, "com/example/studio/MobileMainFragmentActivity.kt")
        val prefsActivityFile = File(androidOutDir, "com/example/studio/PreferencesFragmentActivity.kt")
        val androidAppFile = File(androidOutDir, "com/example/studio/GeneratedAndroidApplication.kt")
        assertTrue(mobileMainActivityFile.isFile, "Expected MobileMainFragmentActivity.kt")
        assertTrue(prefsActivityFile.isFile, "Expected PreferencesFragmentActivity.kt")
        assertTrue(androidAppFile.isFile, "Expected GeneratedAndroidApplication.kt")
        assertTrue(mobileMainActivityFile.readText().contains("class MobileMainFragmentActivity : GenericUiFragmentActivity()"))
        assertTrue(prefsActivityFile.readText().contains("class PreferencesFragmentActivity : GenericUiFragmentActivity()"))
        val androidAppText = androidAppFile.readText()
        assertTrue(androidAppText.contains("themeMode = \"auto\""))
        assertTrue(androidAppText.contains("globalStyleSheetPath = \"styles/studio.scss\""))
        assertTrue(androidAppText.contains("app.addStyleSheet(\"styles/studio.scss\")"))
        assertTrue(androidAppText.contains("AndroidActivityRegistry.register(fragmentClass = com.example.studio.MobileMainFragment::class, activityClass = com.example.studio.MobileMainFragmentActivity::class.java, isMain = true)"))
        assertTrue(androidAppText.contains("AndroidActivityRegistry.register(fragmentClass = com.example.studio.PreferencesFragment::class, activityClass = com.example.studio.PreferencesFragmentActivity::class.java, isMain = false)"))

        // 4. Generate AndroidManifest.xml
        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()
        val manifestXml = manifestTask.outputManifestFile.get().asFile.readText()
        assertTrue(manifestXml.contains("android:name=\"com.example.studio.GeneratedAndroidApplication\""))
        assertTrue(manifestXml.contains("android:name=\"com.example.studio.MobileMainFragmentActivity\""))
        assertTrue(manifestXml.contains("android:name=\"com.example.studio.PreferencesFragmentActivity\""))
        assertTrue(manifestXml.contains("android.intent.action.MAIN"))
        assertTrue(manifestXml.contains("android.intent.category.LAUNCHER"))
        assertTrue(manifestXml.contains("android:name=\"com.example.studio.BackgroundSyncService\""))
        assertTrue(manifestXml.contains("android:foregroundServiceType=\"dataSync\""))
        assertTrue(manifestXml.contains("com.example.studio.ACTION_SYNC"))

        // 5. Generate iOS Xcode host project
        val iosAppTask = project.tasks.getByName("generateGenericUiIosApp") as GenericUiGenerateIosAppTask
        iosAppTask.generateIosApp()
        val iosAppDir = iosAppTask.outputDirectory.get().asFile
        assertTrue(File(iosAppDir, "Info.plist").isFile)
        assertTrue(File(iosAppDir, "iOSApp.swift").isFile)
        assertTrue(File(iosAppDir, "ContentView.swift").isFile)
        assertTrue(File(iosAppDir, "project.yml").isFile)
        assertTrue(File(iosAppDir, "iosApp.xcodeproj/project.pbxproj").isFile)

        // 6. Compile Android-style resources into Resource.kt (Resource.ResourceID), Web assets, and Windows .rc/.res/.o
        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.compileResources()
        val ktResDir = resTask.kotlinOutputDirectory.get().asFile
        val resourceKtFile = File(ktResDir, "com/example/studio/res/Resource.kt")
        assertTrue(resourceKtFile.isFile, "Expected Resource.kt to be generated")
        val resourceKtText = resourceKtFile.readText()
        assertTrue(resourceKtText.contains("typealias ResourceID = com.nexustools.genericui.resources.ResourceID"))
        assertTrue(resourceKtText.contains("object Resource"))
        assertTrue(resourceKtText.contains("val logo: ResourceID"))
        assertTrue(resourceKtText.contains("val app_title: ResourceID"))
        assertTrue(resourceKtText.contains("val brand_primary: ResourceID"))
        assertTrue(resourceKtText.contains("val app_config: ResourceID"))
        assertTrue(resourceKtText.contains("object style"))
        assertTrue(resourceKtText.contains("val studio: ResourceID"))
        val rKtText = File(ktResDir, "com/example/studio/res/R.kt").readText()
        assertTrue(rKtText.contains("object style"))
        assertTrue(rKtText.contains("val studio: ResourceId"))

        val backendResDir = resTask.backendOutputDirectory.get().asFile
        val winCurFile = File(backendResDir, "windows/cursors/crosshair.cur")
        assertTrue(winCurFile.isFile, "Expected Windows .cur cursor file to be generated")
        val curBytes = winCurFile.readBytes()
        assertTrue(curBytes.size > 22, "Expected non-empty .cur binary")
        assertEquals(2, (curBytes[2].toInt() and 0xFF) or ((curBytes[3].toInt() and 0xFF) shl 8), "Expected ICONDIR idType == 2 (.cur)")
        assertTrue(File(backendResDir, "web/assets/drawable/logo.png").isFile, "Expected Web drawable asset")
        assertTrue(File(backendResDir, "web/assets/xml/app_config.xml").isFile, "Expected Web xml asset")
        assertTrue(File(backendResDir, "manifest/genericui-module-resources.json").isFile, "Expected module resource manifest")
        assertTrue(File(backendResDir, "windows/genericui_resources.rc").isFile, "Expected Windows .rc script")

        val winResCompileTask = project.tasks.getByName("compileGenericUiWindowsResources") as GenericUiCompileWindowsResourcesTask
        winResCompileTask.compileWindowsResources()
        assertTrue(winResCompileTask.outputResFile.get().asFile.exists(), "Expected genericui_resources.res")
        assertTrue(winResCompileTask.outputObjectFile.get().asFile.exists(), "Expected genericui_resources.o")
        assertTrue(winResCompileTask.outputResFile.get().asFile.length() > 0L)
        assertTrue(winResCompileTask.outputObjectFile.get().asFile.length() > 0L)
    }

    @Test
    fun `validateApp fails when no MainAppFragment is present for enabled backend`() {
        val discovered = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.app
            import com.nexustools.genericui.component.fragment.AppFragment

            @AppFragment
            class SecondaryFragment
            """.trimIndent()
        )

        val ex = assertFailsWith<IllegalStateException> {
            GenericUiValidateAppTask.validateDiscoveredFragments(
                discoveredFragments = discovered,
                enabledBackends = listOf(GenericUiVariantScope.WINDOWS),
                generatedLauncherPackage = "com.example.app.generated",
                requireMainAppFragment = true
            )
        }
        val msg = assertNotNull(ex.message)
        assertTrue(msg.contains("No @MainAppFragment found matching target backend 'windows'"))
    }
}
