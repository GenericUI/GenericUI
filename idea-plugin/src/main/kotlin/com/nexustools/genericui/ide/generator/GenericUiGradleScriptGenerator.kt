package com.nexustools.genericui.ide.generator

import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiTargetBackend

/**
 * Generates `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and live
 * `genericui { ... }` DSL blocks based on the user's checkbox selections in IntelliJ IDEA.
 */
class GenericUiGradleScriptGenerator {

    /**
     * Generates `settings.gradle.kts` linking the `lib/GenericUI` composite build
     * both in `pluginManagement` (for `id("com.nexustools.genericui")`) and at the root level.
     */
    fun generateSettingsGradleKts(config: GenericUiProjectConfig): String = buildString {
        appendLine("rootProject.name = \"${config.projectName}\"")
        appendLine()
        appendLine("pluginManagement {")
        appendLine("    repositories {")
        appendLine("        google()")
        appendLine("        mavenCentral()")
        appendLine("        gradlePluginPortal()")
        appendLine("    }")
        appendLine("    includeBuild(\"lib/GenericUI\")")
        appendLine("}")
        appendLine()
        appendLine("dependencyResolutionManagement {")
        appendLine("    repositories {")
        appendLine("        google()")
        appendLine("        mavenCentral()")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("// Composite build link to GenericUI multiplatform UI framework")
        appendLine("includeBuild(\"lib/GenericUI\")")
    }

    /**
     * Generates `gradle.properties` for a GenericUI multiplatform project.
     */
    fun generateGradleProperties(config: GenericUiProjectConfig): String = buildString {
        appendLine("org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8")
        appendLine("kotlin.code.style=official")
        appendLine("kotlin.mpp.stability.nowarn=true")
        if (GenericUiTargetBackend.ANDROID in config.selectedBackends) {
            appendLine("android.useAndroidX=true")
            appendLine("android.nonTransitiveRClass=true")
        }
    }

    /**
     * Generates the `kotlin { ... }` multiplatform targets block matching the selected
     * [GenericUiTargetBackend] checkboxes.
     */
    fun generateKotlinMultiplatformBlock(config: GenericUiProjectConfig): String = buildString {
        val backends = config.selectedBackends
        appendLine("kotlin {")
        if (GenericUiTargetBackend.WINDOWS in backends) {
            appendLine("    mingwX64()")
        }
        if (GenericUiTargetBackend.MACOS in backends) {
            appendLine("    macosX64()")
            appendLine("    macosArm64()")
        }
        if (GenericUiTargetBackend.GTK in backends || GenericUiTargetBackend.QT in backends) {
            appendLine("    linuxX64()")
        }
        if (GenericUiTargetBackend.SWING in backends) {
            appendLine("    jvm(\"swing\")")
        }
        if (GenericUiTargetBackend.ANDROID in backends) {
            appendLine("    // Android target configured when Android Gradle Plugin is applied")
        }
        if (GenericUiTargetBackend.IOS in backends) {
            appendLine("    iosArm64()")
            appendLine("    iosSimulatorArm64()")
        }
        if (GenericUiTargetBackend.WEB in backends) {
            appendLine("    js(IR) {")
            appendLine("        browser()")
            appendLine("        binaries.executable()")
            appendLine("    }")
        }
        append("}")
    }

    /**
     * Generates the `genericui { ... }` Gradle DSL block matching the selected target backends,
     * Swing LWJGL OpenGL ES / Vulkan flags, extension modules, and SASS stylesheet configuration.
     */
    fun generateGenericUiDslBlock(config: GenericUiProjectConfig): String = buildString {
        val backends = config.selectedBackends
        val extensions = config.resolvedExtensions()

        appendLine("genericui {")
        appendLine("    backends {")
        when {
            config.isAllExceptSwingSelected() -> {
                appendLine("        allExceptSwing()")
            }
            config.isDesktopExceptSwingSelected() -> {
                appendLine("        allNativeDesktop()")
            }
            config.isAllBackendsSelected() && !config.swingUseLwjglGL && !config.swingUseVulkan -> {
                appendLine("        all()")
            }
            else -> {
                for (backend in GenericUiTargetBackend.entries) {
                    if (backend in backends) {
                        if (backend == GenericUiTargetBackend.SWING && (config.swingUseLwjglGL || config.swingUseVulkan)) {
                            appendLine("        swing {")
                            appendLine("            useLwjglGL = ${config.swingUseLwjglGL}")
                            appendLine("            useVulkan = ${config.swingUseVulkan}")
                            appendLine("        }")
                        } else {
                            appendLine("        ${backend.dslMethodName}()")
                        }
                    }
                }
            }
        }
        appendLine("    }")

        if (config.bundleAngleOnWindows || config.bundleMoltenVkOnApple || config.bundleShaderc) {
            appendLine("    graphics {")
            appendLine("        bundleAngleOnWindows = ${config.bundleAngleOnWindows}")
            appendLine("        bundleMoltenVkOnApple = ${config.bundleMoltenVkOnApple}")
            appendLine("        bundleShaderc = ${config.bundleShaderc}")
            appendLine("    }")
        }

        if (extensions.isNotEmpty()) {
            appendLine("    extensions {")
            for (ext in GenericUiExtensionModule.entries) {
                if (ext in extensions) {
                    appendLine("        ${ext.dslMethodName}()")
                }
            }
            appendLine("    }")
        }

        appendLine("    styles {")
        appendLine("        compileSass = ${config.compileSass}")
        appendLine("        sourceDir = \"src/commonMain/resources/styles\"")
        appendLine("    }")
        append("}")
    }

    /**
     * Generates the complete `build.gradle.kts` file for a GenericUI project.
     */
    fun generateBuildGradleKts(config: GenericUiProjectConfig): String = buildString {
        appendLine("plugins {")
        appendLine("    kotlin(\"multiplatform\")")
        appendLine("    id(\"com.nexustools.genericui\")")
        appendLine("}")
        appendLine()
        appendLine("group = \"${config.packageName}\"")
        appendLine("version = \"0.1.0-SNAPSHOT\"")
        appendLine()
        appendLine(generateKotlinMultiplatformBlock(config))
        appendLine()
        appendLine(generateGenericUiDslBlock(config))
    }
}
