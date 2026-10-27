package com.nexustools.genericui.ide.generator

import com.nexustools.genericui.ide.model.GenericUiExtensionModule
import com.nexustools.genericui.ide.model.GenericUiProjectConfig
import com.nexustools.genericui.ide.model.GenericUiTargetBackend

/**
 * Generates `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and live
 * `genericui { ... }` DSL blocks based on the user's checkbox selections in IntelliJ IDEA.
 *
 * Projects applying `id("com.nexustools.genericui")` rely entirely on the `genericui { ... }`
 * DSL block (`application`, `backends`, `graphics`, `extensions`, `styles`, `sourceSets`)
 * without redundant `kotlin { ... }` target boilerplate.
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
        appendLine("plugins {")
        appendLine("    id(\"com.nexustools.genericui.settings\")")
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
        appendLine("kotlin.native.ignoreDisabledTargets=true")
        if (GenericUiTargetBackend.ANDROID in config.selectedBackends) {
            appendLine("android.useAndroidX=true")
            appendLine("android.nonTransitiveRClass=true")
        }
    }

    /**
     * Generates the `genericui { ... }` Gradle DSL block configuring `application`,
     * `backends`, `graphics`, `extensions`, and `styles`.
     *
     * `id("com.nexustools.genericui")` automatically configures the Kotlin Multiplatform
     * targets, the GenericUI sourceSet hierarchy (`common`, `desktopCommon`, `nativeDesktopCommon`,
     * `windowsMain`, `macMain`, `linuxCommon`, `gtkMain`, `qtMain`, `swingMain`, `mobileCommon`,
     * `androidMain`, `iosMain`, `webMain`), and Development/Production build target tasks.
     */
    fun generateGenericUiDslBlock(config: GenericUiProjectConfig): String = buildString {
        val backends = config.selectedBackends
        val extensions = config.resolvedExtensions()

        appendLine("genericui {")
        appendLine("    app {")
        appendLine("        id = \"${config.packageName}\"")
        appendLine("        name = \"${config.projectName}\"")
        appendLine("        version = \"1.0.0\"")
        appendLine("    }")
        appendLine("    application {")
        appendLine("        baseName = \"${config.projectName}\"")
        appendLine("    }")
        appendLine("    theme {")
        appendLine("        mode = \"${config.themeMode}\"")
        appendLine("    }")
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
        if (config.compileSass && config.globalStyleSheet.isNotBlank()) {
            appendLine("        globalStyleSheet = \"${config.globalStyleSheet}\"")
        }
        appendLine("        sourceDir = \"src/commonMain/resources/styles\"")
        appendLine("    }")
        append("}")
    }

    /**
     * Generates the complete `build.gradle.kts` file for a GenericUI project, relying entirely
     * on `id("com.nexustools.genericui")` and `genericui { ... }` without redundant `kotlin { ... }` boilerplate.
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
        appendLine(generateGenericUiDslBlock(config))
    }
}
