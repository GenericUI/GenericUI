package com.nexustools.genericui.plugin

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

/**
 * Gradle `Settings` plugin (`id("com.nexustools.genericui.settings")`) that allows consumer
 * `settings.gradle.kts` builds to automatically synthesize and include a companion
 * `:<project>-android-app` wrapper module under `build/genericui/android-app` when building
 * Android APKs alongside a Kotlin Multiplatform module.
 */
class GenericUiSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.settingsEvaluated {
            val enabledProp = settings.providers
                .gradleProperty(PROP_ANDROID_WRAPPER_ENABLED)
                .orNull
                ?.toBooleanStrictOrNull()
                ?: false

            if (enabledProp) {
                val rootName = settings.rootProject.name.ifBlank { "app" }
                val moduleName = settings.providers
                    .gradleProperty(PROP_ANDROID_WRAPPER_MODULE_NAME)
                    .orNull
                    ?.takeIf { it.isNotBlank() }
                    ?: "$rootName-android-app"
                val namespace = settings.providers
                    .gradleProperty(PROP_ANDROID_WRAPPER_NAMESPACE)
                    .orNull
                    ?.takeIf { it.isNotBlank() }
                    ?: "com.nexustools.${rootName.lowercase().replace(Regex("[^a-z0-9_]"), "")}"

                includeAndroidAppWrapper(
                    settings = settings,
                    hostProjectPath = ":",
                    appModuleName = moduleName,
                    namespace = namespace
                )
            }
        }
    }

    companion object {
        const val PLUGIN_ID: String = "com.nexustools.genericui.settings"
        const val PROP_ANDROID_WRAPPER_ENABLED: String = "genericui.android.appWrapper.enabled"
        const val PROP_ANDROID_WRAPPER_MODULE_NAME: String = "genericui.android.appWrapper.moduleName"
        const val PROP_ANDROID_WRAPPER_NAMESPACE: String = "genericui.android.appWrapper.namespace"

        /**
         * Synthesizes a companion Android application wrapper module directory under
         * `<rootDir>/build/genericui/android-app` and includes it in [settings] as `:[appModuleName]`.
         */
        fun includeAndroidAppWrapper(
            settings: Settings,
            hostProjectPath: String = ":",
            appModuleName: String = "${settings.rootProject.name.ifBlank { "app" }}-android-app",
            namespace: String = "com.nexustools.genericui.app",
            compileSdk: Int = 35,
            minSdk: Int = 24,
            targetSdk: Int = 35
        ): File {
            val wrapperDir = synthesizeAndroidAppModule(
                rootDir = settings.rootDir,
                hostProjectPath = hostProjectPath,
                appModuleName = appModuleName,
                namespace = namespace,
                compileSdk = compileSdk,
                minSdk = minSdk,
                targetSdk = targetSdk
            )
            val gradlePath = if (appModuleName.startsWith(":")) appModuleName else ":$appModuleName"
            settings.include(gradlePath)
            settings.project(gradlePath).projectDir = wrapperDir
            return wrapperDir
        }

        /**
         * Generates the companion Android application wrapper `build.gradle.kts` under
         * `<rootDir>/build/genericui/android-app` and returns the wrapper directory.
         */
        fun synthesizeAndroidAppModule(
            rootDir: File,
            hostProjectPath: String = ":",
            appModuleName: String = "android-app",
            namespace: String = "com.nexustools.genericui.app",
            compileSdk: Int = 35,
            minSdk: Int = 24,
            targetSdk: Int = 35
        ): File {
            val wrapperDir = File(rootDir, "build/genericui/android-app")
            wrapperDir.mkdirs()

            val normalizedHostPath = if (hostProjectPath.startsWith(":")) hostProjectPath else ":$hostProjectPath"
            val buildScriptFile = File(wrapperDir, "build.gradle.kts")
            buildScriptFile.writeText(
                renderAndroidAppWrapperBuildScript(
                    hostProjectPath = normalizedHostPath,
                    appModuleName = appModuleName.removePrefix(":"),
                    namespace = namespace,
                    compileSdk = compileSdk,
                    minSdk = minSdk,
                    targetSdk = targetSdk
                )
            )
            return wrapperDir
        }

        /**
         * Renders the Gradle Kotlin DSL `build.gradle.kts` content for the synthesized Android app wrapper module.
         */
        fun renderAndroidAppWrapperBuildScript(
            hostProjectPath: String = ":",
            appModuleName: String = "android-app",
            namespace: String = "com.nexustools.genericui.app",
            compileSdk: Int = 35,
            minSdk: Int = 24,
            targetSdk: Int = 35
        ): String = buildString {
            appendLine("// Auto-generated by GenericUiSettingsPlugin ($PLUGIN_ID) for module :$appModuleName")
            appendLine("plugins {")
            appendLine("    id(\"com.android.application\")")
            appendLine("    kotlin(\"android\")")
            appendLine("}")
            appendLine()
            appendLine("android {")
            appendLine("    namespace = \"$namespace\"")
            appendLine("    compileSdk = $compileSdk")
            appendLine("    defaultConfig {")
            appendLine("        applicationId = \"$namespace\"")
            appendLine("        minSdk = $minSdk")
            appendLine("        targetSdk = $targetSdk")
            appendLine("    }")
            appendLine("    sourceSets[\"main\"].manifest.srcFile(rootProject.layout.buildDirectory.file(\"generated/genericui/android-manifest/AndroidManifest.xml\"))")
            appendLine("    sourceSets[\"main\"].java.srcDir(rootProject.layout.buildDirectory.dir(\"generated/genericui/entrypoints/androidMain/kotlin\"))")
            appendLine("}")
            appendLine()
            appendLine("dependencies {")
            appendLine("    implementation(project(\"$hostProjectPath\"))")
            appendLine("}")
        }
    }
}
