package com.nexustools.genericui.plugin

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FragmentMetadataAndServiceWorkerCodegenTest {

    @Test
    fun `codegen emits fragmentIds fragmentRoutes fragmentMetadata Android registerFragmentId and ServiceWorker registerMetadata`() {
        val project = ProjectBuilder.builder().withName("media-codegen-app").build()
        project.group = "com.example.mediaapp"
        project.version = "1.0.0"
        project.pluginManager.apply("com.nexustools.genericui")

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.app {
            id = "com.example.mediaapp"
            name = "MediaApp"
            version = "1.0.0"
            packageName = "com.example.mediaapp"
        }
        ext.backends {
            all()
        }
        ext.extensions {
            serviceWorker(true)
        }

        val commonDir = File(project.projectDir, "src/commonMain/kotlin/com/example/mediaapp").apply { mkdirs() }
        File(commonDir, "MainPlayerFragment.kt").writeText(
            """
            package com.example.mediaapp
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            @MainAppFragment(route = "/player")
            class MainPlayerFragment : Fragment()
            """.trimIndent()
        )
        File(commonDir, "QueueWindowFragment.kt").writeText(
            """
            package com.example.mediaapp
            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            @AppFragment(route = "/queue")
            class QueueWindowFragment : Fragment()
            """.trimIndent()
        )
        File(commonDir, "AppWorker.kt").writeText(
            """
            package com.example.mediaapp
            import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
            import com.nexustools.genericui.ext.serviceworker.ServiceWorker
            @MainServiceWorker
            class AppWorker : ServiceWorker()
            """.trimIndent()
        )

        val launchersTask = project.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersTask.generateLaunchers()
        val launcherOut = launchersTask.outputDirectory.get().asFile

        val expectedMainMeta =
            "com.nexustools.genericui.component.fragment.FragmentMetadata(" +
                "fragmentId = \"com.example.mediaapp.MainPlayerFragment\", " +
                "route = \"/player\", label = \"\", isMainFragment = true, " +
                "multipleWindows = false, dialog = false)"
        val expectedQueueMeta =
            "com.nexustools.genericui.component.fragment.FragmentMetadata(" +
                "fragmentId = \"com.example.mediaapp.QueueWindowFragment\", " +
                "route = \"/queue\", label = \"\", isMainFragment = false, " +
                "multipleWindows = false, dialog = false)"

        for (platformDir in listOf("swing", "macos", "windows", "gtk", "kde", "ios", "ipados", "web")) {
            val pascal = platformDir.replaceFirstChar { it.uppercaseChar() }
            val descriptorSrc = File(
                launcherOut,
                "$platformDir/kotlin/com/example/mediaapp/generated/Generated${pascal}AppDescriptor.kt"
            ).readText(Charsets.UTF_8)
            assertTrue(
                descriptorSrc.contains("fragmentIds = mapOf("),
                "$platformDir descriptor must emit fragmentIds"
            )
            assertTrue(
                descriptorSrc.contains("com.example.mediaapp.MainPlayerFragment::class to \"com.example.mediaapp.MainPlayerFragment\""),
                "$platformDir descriptor must contain MainPlayerFragment ID"
            )
            assertTrue(
                descriptorSrc.contains("com.example.mediaapp.QueueWindowFragment::class to \"com.example.mediaapp.QueueWindowFragment\""),
                "$platformDir descriptor must contain QueueWindowFragment ID"
            )
            assertTrue(
                descriptorSrc.contains("fragmentRoutes = mapOf("),
                "$platformDir descriptor must emit fragmentRoutes"
            )
            assertTrue(
                descriptorSrc.contains("com.example.mediaapp.QueueWindowFragment::class to \"/queue\""),
                "$platformDir descriptor must contain QueueWindowFragment route"
            )
            assertTrue(
                descriptorSrc.contains("fragmentMetadata = mapOf("),
                "$platformDir descriptor must emit fragmentMetadata"
            )
            assertTrue(
                descriptorSrc.contains("com.example.mediaapp.MainPlayerFragment::class to $expectedMainMeta"),
                "$platformDir descriptor must contain MainPlayerFragment FragmentMetadata"
            )
            assertTrue(
                descriptorSrc.contains("com.example.mediaapp.QueueWindowFragment::class to $expectedQueueMeta"),
                "$platformDir descriptor must contain QueueWindowFragment FragmentMetadata"
            )
        }

        val expectedMainReg =
            "com.nexustools.genericui.component.fragment.FragmentRegistry.registerMetadata(" +
                "fragmentClass = com.example.mediaapp.MainPlayerFragment::class, " +
                "fragmentId = \"com.example.mediaapp.MainPlayerFragment\", " +
                "route = \"/player\", label = \"\", isMainFragment = true, " +
                "multipleWindows = false, dialog = false)"
        val expectedQueueReg =
            "com.nexustools.genericui.component.fragment.FragmentRegistry.registerMetadata(" +
                "fragmentClass = com.example.mediaapp.QueueWindowFragment::class, " +
                "fragmentId = \"com.example.mediaapp.QueueWindowFragment\", " +
                "route = \"/queue\", label = \"\", isMainFragment = false, " +
                "multipleWindows = false, dialog = false)"

        for ((dirName, fileName) in listOf(
            "swing" to "GeneratedSwingServiceWorkerMain.kt",
            "macos" to "GeneratedMacosServiceWorkerMain.kt",
            "windows" to "GeneratedWindowsServiceWorkerMain.kt",
            "gtk" to "GeneratedGtkServiceWorkerMain.kt",
            "kde" to "GeneratedKdeServiceWorkerMain.kt"
        )) {
            val workerMainSrc = File(
                launcherOut,
                "$dirName/kotlin/com/example/mediaapp/generated/$fileName"
            ).readText(Charsets.UTF_8)
            assertTrue(
                workerMainSrc.contains(expectedMainReg),
                "$fileName must register MainPlayerFragment metadata:\n$workerMainSrc"
            )
            assertTrue(
                workerMainSrc.contains(expectedQueueReg),
                "$fileName must register QueueWindowFragment metadata:\n$workerMainSrc"
            )
            assertTrue(
                workerMainSrc.indexOf("FragmentRegistry.registerMetadata(") <
                    workerMainSrc.indexOf("runDesktopWorkerMain")
            )
        }

        val webMainSrc = File(
            launcherOut,
            "web/kotlin/com/example/mediaapp/generated/GeneratedWebLauncherMain.kt"
        ).readText(Charsets.UTF_8)
        assertTrue(webMainSrc.contains(expectedMainReg), "Web worker startup must register MainPlayerFragment metadata")
        assertTrue(webMainSrc.contains(expectedQueueReg), "Web worker startup must register QueueWindowFragment metadata")
        assertTrue(
            webMainSrc.indexOf("FragmentRegistry.registerMetadata(") <
                webMainSrc.indexOf("ServiceWorkerExtension.ensureStarted()")
        )

        val androidTask = project.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
        androidTask.generate()
        val androidOutDir = androidTask.outputDirectory.get().asFile
        val androidAppFile = File(androidOutDir, "com/example/mediaapp/GeneratedAndroidApplication.kt")
        val androidAppSrc = androidAppFile.readText(Charsets.UTF_8)
        assertTrue(androidAppSrc.contains("fragmentIds = mapOf("))
        assertTrue(androidAppSrc.contains("fragmentRoutes = mapOf("))
        assertTrue(androidAppSrc.contains("fragmentMetadata = mapOf("))
        assertTrue(androidAppSrc.contains(expectedMainMeta))
        assertTrue(androidAppSrc.contains(expectedQueueMeta))
        assertTrue(
            androidAppSrc.contains(
                "AndroidActivityRegistry.registerFragmentId(" +
                    "fragmentId = \"com.example.mediaapp.QueueWindowFragment\", " +
                    "activityClass = com.example.mediaapp.QueueWindowFragmentActivity::class.java)"
            )
        )

        val androidWorkerSrc = File(
            androidOutDir,
            "com/example/mediaapp/GeneratedAndroidServiceWorkerService.kt"
        ).readText(Charsets.UTF_8)
        assertTrue(androidWorkerSrc.contains(expectedMainReg), "Android service worker must register MainPlayerFragment metadata")
        assertTrue(androidWorkerSrc.contains(expectedQueueReg), "Android service worker must register QueueWindowFragment metadata")
        assertTrue(
            androidWorkerSrc.indexOf("FragmentRegistry.registerMetadata(") <
                androidWorkerSrc.indexOf("AndroidServiceWorkerController.onServiceCreated(")
        )
    }

    @Test
    fun `scanner parses multipleWindows dialog and illegal main dialog flags across named and positional arguments`() {
        val discovered = GenericUiFragmentScanner.parseKotlinSource(
            """
            package com.example.flags

            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            import com.nexustools.genericui.enums.system.AppTarget

            @MainAppFragment(
                targets = [AppTarget.DESKTOP_COMMON],
                label = "Named Main",
                route = "/main-named",
                multipleWindows = true
            )
            class NamedMainFragment : Fragment()

            @MainAppFragment([AppTarget.WEB], "Positional Main", "/main-pos", true, "portrait", true)
            class PositionalMainFragment : Fragment()

            @AppFragment(
                targets = [AppTarget.COMMON],
                label = "Named Dialog",
                route = "/dialog-named",
                multipleWindows = true,
                dialog = true
            )
            class NamedDialogFragment : Fragment()

            @AppFragment([AppTarget.ANDROID], "Positional Dialog", "/dialog-pos", false, "unspecified", true, true)
            class PositionalDialogFragment : Fragment()

            @AppFragment(targets = [AppTarget.COMMON], "Mixed Dialog", "/dialog-mixed", multipleWindows = false, dialog = true)
            class MixedDialogFragment : Fragment()

            @MainAppFragment(route = "/bad-main", dialog = true)
            class IllegalNamedMainDialogFragment : Fragment()

            @MainAppFragment(dialog = false)
            class IllegalFalseMainDialogFragment : Fragment()

            @MainAppFragment([AppTarget.COMMON], "Bad Pos", "/bad-pos", true, "", false, true)
            class IllegalPositionalMainDialogFragment : Fragment()
            """.trimIndent()
        )

        assertEquals(8, discovered.size)

        val namedMain = discovered.first { it.simpleName == "NamedMainFragment" }
        assertTrue(namedMain.isMainFragment)
        assertTrue(namedMain.multipleWindows)
        assertFalse(namedMain.dialog)
        assertFalse(namedMain.hasIllegalMainDialogFlag)

        val posMain = discovered.first { it.simpleName == "PositionalMainFragment" }
        assertTrue(posMain.isMainFragment)
        assertEquals("Positional Main", posMain.label)
        assertEquals("/main-pos", posMain.route)
        assertEquals("portrait", posMain.screenOrientation)
        assertTrue(posMain.multipleWindows)
        assertFalse(posMain.dialog)
        assertFalse(posMain.hasIllegalMainDialogFlag)

        val namedDialog = discovered.first { it.simpleName == "NamedDialogFragment" }
        assertFalse(namedDialog.isMainFragment)
        assertTrue(namedDialog.multipleWindows)
        assertTrue(namedDialog.dialog)
        assertFalse(namedDialog.hasIllegalMainDialogFlag)

        val posDialog = discovered.first { it.simpleName == "PositionalDialogFragment" }
        assertFalse(posDialog.isMainFragment)
        assertEquals("Positional Dialog", posDialog.label)
        assertEquals("/dialog-pos", posDialog.route)
        assertTrue(posDialog.multipleWindows)
        assertTrue(posDialog.dialog)
        assertFalse(posDialog.hasIllegalMainDialogFlag)

        val mixedDialog = discovered.first { it.simpleName == "MixedDialogFragment" }
        assertEquals("Mixed Dialog", mixedDialog.label)
        assertEquals("/dialog-mixed", mixedDialog.route)
        assertFalse(mixedDialog.multipleWindows)
        assertTrue(mixedDialog.dialog)

        for (badName in listOf(
            "IllegalNamedMainDialogFragment",
            "IllegalFalseMainDialogFragment",
            "IllegalPositionalMainDialogFragment"
        )) {
            val bad = discovered.first { it.simpleName == badName }
            assertTrue(bad.isMainFragment)
            assertFalse(bad.dialog, "$badName must never expose dialog = true")
            assertTrue(bad.hasIllegalMainDialogFlag, "$badName must flag hasIllegalMainDialogFlag = true")
        }
    }

    @Test
    fun `validateApp fails with actionable error when MainAppFragment declares dialog`() {
        val project = ProjectBuilder.builder().withName("illegal-main-dialog").build()
        project.group = "com.example.bad"
        project.pluginManager.apply("com.nexustools.genericui")
        project.extensions.getByType(GenericUiExtension::class.java).backends {
            all()
        }

        val commonDir = File(project.projectDir, "src/commonMain/kotlin/com/example/bad").apply { mkdirs() }
        File(commonDir, "BadMainFragment.kt").writeText(
            """
            package com.example.bad
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            @MainAppFragment(dialog = true)
            class BadMainFragment : Fragment()
            """.trimIndent()
        )

        val validateTask = project.tasks.getByName("genericuiValidateApp") as GenericUiValidateAppTask
        val validateEx = assertFailsWith<IllegalStateException> {
            validateTask.validateApp()
        }
        val msg = validateEx.message.orEmpty()
        assertTrue(msg.contains("com.example.bad.BadMainFragment"), "Error must name class: $msg")
        assertTrue(msg.contains("@MainAppFragment"), "Error must mention @MainAppFragment: $msg")
        assertTrue(msg.contains("@AppFragment"), "Error must explain @AppFragment supports dialog: $msg")

        val discovered = GenericUiFragmentScanner.scanProject(project.projectDir)
        assertFailsWith<IllegalStateException> {
            GenericUiMainFragmentResolver.resolveMainFragmentForBackend(discovered, GenericUiVariantScope.WINDOWS)
        }
        assertFailsWith<GradleException> {
            GenericUiGenerateAndroidActivitiesTask.resolveAndroidFragments(discovered)
        }
        assertFailsWith<GradleException> {
            GenericUiAndroidManifestXmlGenerator.generate(
                appId = "com.example.bad",
                appName = "Bad",
                appVersion = "1.0.0",
                model = GenericUiAndroidManifestModel(),
                fragments = discovered
            )
        }
    }

    @Test
    fun `codegen emits full FragmentMetadata, Theme GenericUI Dialog XMLs, and dialog launchMode AndroidManifest attributes`() {
        val project = ProjectBuilder.builder().withName("dialog-multiwin-app").build()
        project.group = "com.example.dialogapp"
        project.version = "1.0.0"
        project.pluginManager.apply("com.nexustools.genericui")

        val ext = project.extensions.getByType(GenericUiExtension::class.java)
        ext.app {
            id = "com.example.dialogapp"
            name = "DialogApp"
            version = "1.0.0"
            packageName = "com.example.dialogapp"
        }
        ext.backends {
            all()
        }
        ext.extensions {
            serviceWorker(true)
        }
        ext.android {
            manifest {
                activity("CustomThemeDialogFragment") {
                    theme = "@style/CustomDialogTheme"
                }
            }
        }

        val commonDir = File(project.projectDir, "src/commonMain/kotlin/com/example/dialogapp").apply { mkdirs() }
        File(commonDir, "WorkspaceMainFragment.kt").writeText(
            """
            package com.example.dialogapp
            import com.nexustools.genericui.component.fragment.Fragment
            import com.nexustools.genericui.component.fragment.MainAppFragment
            @MainAppFragment(label = "Workspace", route = "/workspace", multipleWindows = true)
            class WorkspaceMainFragment : Fragment()
            """.trimIndent()
        )
        File(commonDir, "InspectorDialogFragment.kt").writeText(
            """
            package com.example.dialogapp
            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            @AppFragment(label = "Inspector", route = "/inspector", multipleWindows = false, dialog = true)
            class InspectorDialogFragment : Fragment()
            """.trimIndent()
        )
        File(commonDir, "CustomThemeDialogFragment.kt").writeText(
            """
            package com.example.dialogapp
            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            @AppFragment(label = "Custom Dialog", route = "/custom-dialog", multipleWindows = true, dialog = true)
            class CustomThemeDialogFragment : Fragment()
            """.trimIndent()
        )
        File(commonDir, "SettingsPanelFragment.kt").writeText(
            """
            package com.example.dialogapp
            import com.nexustools.genericui.component.fragment.AppFragment
            import com.nexustools.genericui.component.fragment.Fragment
            @AppFragment(label = "Settings", route = "/settings", multipleWindows = false, dialog = false)
            class SettingsPanelFragment : Fragment()
            """.trimIndent()
        )
        File(commonDir, "DialogWorker.kt").writeText(
            """
            package com.example.dialogapp
            import com.nexustools.genericui.ext.serviceworker.MainServiceWorker
            import com.nexustools.genericui.ext.serviceworker.ServiceWorker
            @MainServiceWorker
            class DialogWorker : ServiceWorker()
            """.trimIndent()
        )

        // 1. Validate passes
        val validateTask = project.tasks.getByName("genericuiValidateApp") as GenericUiValidateAppTask
        validateTask.validateApp()

        // 2. Launchers & ServiceWorker codegen (Desktop + Web)
        val launchersTask = project.tasks.getByName("generateGenericUiLaunchers") as GenericUiGenerateLaunchersTask
        launchersTask.generateLaunchers()
        val launcherOut = launchersTask.outputDirectory.get().asFile

        val expectedWorkspaceMeta =
            "com.nexustools.genericui.component.fragment.FragmentMetadata(" +
                "fragmentId = \"com.example.dialogapp.WorkspaceMainFragment\", " +
                "route = \"/workspace\", label = \"Workspace\", isMainFragment = true, " +
                "multipleWindows = true, dialog = false)"
        val expectedInspectorMeta =
            "com.nexustools.genericui.component.fragment.FragmentMetadata(" +
                "fragmentId = \"com.example.dialogapp.InspectorDialogFragment\", " +
                "route = \"/inspector\", label = \"Inspector\", isMainFragment = false, " +
                "multipleWindows = false, dialog = true)"
        val expectedCustomMeta =
            "com.nexustools.genericui.component.fragment.FragmentMetadata(" +
                "fragmentId = \"com.example.dialogapp.CustomThemeDialogFragment\", " +
                "route = \"/custom-dialog\", label = \"Custom Dialog\", isMainFragment = false, " +
                "multipleWindows = true, dialog = true)"
        val expectedSettingsMeta =
            "com.nexustools.genericui.component.fragment.FragmentMetadata(" +
                "fragmentId = \"com.example.dialogapp.SettingsPanelFragment\", " +
                "route = \"/settings\", label = \"Settings\", isMainFragment = false, " +
                "multipleWindows = false, dialog = false)"

        for (target in listOf("windows", "macos", "gtk", "kde", "swing", "ios", "ipados", "web")) {
            val pascal = target.replaceFirstChar { it.uppercaseChar() }
            val descSrc = File(
                launcherOut,
                "$target/kotlin/com/example/dialogapp/generated/Generated${pascal}AppDescriptor.kt"
            ).readText(Charsets.UTF_8)
            assertTrue(descSrc.contains(expectedWorkspaceMeta), "$target descriptor missing WorkspaceMainFragment metadata")
            assertTrue(descSrc.contains(expectedInspectorMeta), "$target descriptor missing InspectorDialogFragment metadata")
            assertTrue(descSrc.contains(expectedCustomMeta), "$target descriptor missing CustomThemeDialogFragment metadata")
            assertTrue(descSrc.contains(expectedSettingsMeta), "$target descriptor missing SettingsPanelFragment metadata")
        }

        val expectedInspectorReg =
            "com.nexustools.genericui.component.fragment.FragmentRegistry.registerMetadata(" +
                "fragmentClass = com.example.dialogapp.InspectorDialogFragment::class, " +
                "fragmentId = \"com.example.dialogapp.InspectorDialogFragment\", " +
                "route = \"/inspector\", label = \"Inspector\", isMainFragment = false, " +
                "multipleWindows = false, dialog = true)"
        val expectedWorkspaceReg =
            "com.nexustools.genericui.component.fragment.FragmentRegistry.registerMetadata(" +
                "fragmentClass = com.example.dialogapp.WorkspaceMainFragment::class, " +
                "fragmentId = \"com.example.dialogapp.WorkspaceMainFragment\", " +
                "route = \"/workspace\", label = \"Workspace\", isMainFragment = true, " +
                "multipleWindows = true, dialog = false)"

        for ((dirName, fileName) in listOf(
            "windows" to "GeneratedWindowsServiceWorkerMain.kt",
            "macos" to "GeneratedMacosServiceWorkerMain.kt",
            "swing" to "GeneratedSwingServiceWorkerMain.kt"
        )) {
            val workerSrc = File(
                launcherOut,
                "$dirName/kotlin/com/example/dialogapp/generated/$fileName"
            ).readText(Charsets.UTF_8)
            assertTrue(workerSrc.contains(expectedWorkspaceReg), "$fileName missing WorkspaceMainFragment registration")
            assertTrue(workerSrc.contains(expectedInspectorReg), "$fileName missing InspectorDialogFragment registration")
            assertTrue(
                workerSrc.indexOf(expectedInspectorReg) < workerSrc.indexOf("runDesktopWorkerMain"),
                "$fileName must register metadata before runDesktopWorkerMain"
            )
        }

        val webMainSrc = File(
            launcherOut,
            "web/kotlin/com/example/dialogapp/generated/GeneratedWebLauncherMain.kt"
        ).readText(Charsets.UTF_8)
        assertTrue(webMainSrc.contains(expectedWorkspaceReg))
        assertTrue(webMainSrc.contains(expectedInspectorReg))
        assertTrue(
            webMainSrc.indexOf(expectedInspectorReg) < webMainSrc.indexOf("ServiceWorkerExtension.ensureStarted()")
        )

        // 3. Android Application + ServiceWorkerService
        val androidActivitiesTask = project.tasks.getByName("generateGenericUiAndroidActivities") as GenericUiGenerateAndroidActivitiesTask
        androidActivitiesTask.generate()
        val androidOut = androidActivitiesTask.outputDirectory.get().asFile
        val androidAppSrc = File(androidOut, "com/example/dialogapp/GeneratedAndroidApplication.kt").readText(Charsets.UTF_8)
        assertTrue(androidAppSrc.contains(expectedWorkspaceMeta))
        assertTrue(androidAppSrc.contains(expectedInspectorMeta))
        assertTrue(androidAppSrc.contains(expectedCustomMeta))
        assertTrue(androidAppSrc.contains(expectedSettingsMeta))

        val androidWorkerSrc = File(
            androidOut,
            "com/example/dialogapp/GeneratedAndroidServiceWorkerService.kt"
        ).readText(Charsets.UTF_8)
        assertTrue(androidWorkerSrc.contains(expectedWorkspaceReg))
        assertTrue(androidWorkerSrc.contains(expectedInspectorReg))
        assertTrue(
            androidWorkerSrc.indexOf(expectedInspectorReg) <
                androidWorkerSrc.indexOf("AndroidServiceWorkerController.onServiceCreated(")
        )

        // 4. AndroidManifest.xml dialog theme & singleTop launchMode
        val manifestTask = project.tasks.getByName("generateGenericUiAndroidManifest") as GenericUiGenerateAndroidManifestTask
        manifestTask.generate()
        val manifestXml = manifestTask.outputManifestFile.get().asFile.readText(Charsets.UTF_8)

        fun activityBlock(activityFqn: String): String {
            val marker = """android:name="$activityFqn""""
            val idx = manifestXml.indexOf(marker)
            assertTrue(idx >= 0, "Expected activity $activityFqn in AndroidManifest.xml:\n$manifestXml")
            val tagStart = manifestXml.lastIndexOf("<activity", idx)
            val openEnd = manifestXml.indexOf('>', idx)
            val tagEnd = if (openEnd > 0 && manifestXml[openEnd - 1] == '/') {
                openEnd + 1
            } else {
                val closeTag = manifestXml.indexOf("</activity>", idx)
                if (closeTag >= 0) closeTag + "</activity>".length else manifestXml.length
            }
            return manifestXml.substring(tagStart, tagEnd)
        }

        val workspaceBlock = activityBlock("com.example.dialogapp.WorkspaceMainFragmentActivity")
        assertFalse(workspaceBlock.contains("android:launchMode=\"singleTop\""), "multipleWindows=true main should not force singleTop:\n$workspaceBlock")
        assertFalse(workspaceBlock.contains("Theme.GenericUI.Dialog"), "main activity must not use dialog theme:\n$workspaceBlock")

        val inspectorBlock = activityBlock("com.example.dialogapp.InspectorDialogFragmentActivity")
        assertTrue(inspectorBlock.contains("""android:theme="@style/Theme.GenericUI.Dialog""""), "dialog=true activity must get Theme.GenericUI.Dialog:\n$inspectorBlock")
        assertTrue(inspectorBlock.contains("""android:launchMode="singleTop""""), "multipleWindows=false dialog activity must get launchMode=singleTop:\n$inspectorBlock")

        val customDialogBlock = activityBlock("com.example.dialogapp.CustomThemeDialogFragmentActivity")
        assertTrue(customDialogBlock.contains("""android:theme="@style/CustomDialogTheme""""), "explicit theme override must win:\n$customDialogBlock")
        assertFalse(customDialogBlock.contains("Theme.GenericUI.Dialog"), "explicit theme override must not be replaced by default dialog theme:\n$customDialogBlock")
        assertFalse(customDialogBlock.contains("android:launchMode=\"singleTop\""), "multipleWindows=true dialog activity should not force singleTop:\n$customDialogBlock")

        val settingsBlock = activityBlock("com.example.dialogapp.SettingsPanelFragmentActivity")
        assertTrue(settingsBlock.contains("""android:launchMode="singleTop""""), "multipleWindows=false activity must get launchMode=singleTop:\n$settingsBlock")
        assertFalse(settingsBlock.contains("Theme.GenericUI.Dialog"), "dialog=false activity must not get dialog theme:\n$settingsBlock")

        // 5. Generated Android themes.xml and values-night/themes.xml include Theme.GenericUI.Dialog
        val resTask = project.tasks.getByName("compileGenericUiResources") as GenericUiResourceCompilerTask
        resTask.compileResources()
        val androidResDir = File(resTask.backendOutputDirectory.get().asFile, "android/res")
        for (relPath in listOf("values/themes.xml", "values-night/themes.xml")) {
            val themeXml = File(androidResDir, relPath).readText(Charsets.UTF_8)
            assertTrue(
                themeXml.contains("""<style name="Theme.GenericUI.Dialog" parent="Theme.MaterialComponents.DayNight.Dialog">"""),
                "$relPath must define Theme.GenericUI.Dialog:\n$themeXml"
            )
            assertTrue(themeXml.contains("""<item name="windowNoTitle">true</item>"""), "$relPath missing windowNoTitle")
            assertTrue(themeXml.contains("""<item name="android:windowNoTitle">true</item>"""), "$relPath missing android:windowNoTitle")
            assertTrue(themeXml.contains("""<item name="android:windowIsFloating">true</item>"""), "$relPath missing android:windowIsFloating")
            assertTrue(themeXml.contains("""<item name="android:windowCloseOnTouchOutside">true</item>"""), "$relPath missing android:windowCloseOnTouchOutside")
        }
    }
}
