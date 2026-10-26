rootProject.name = "GenericUI"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Symlinked NJS JavaScript & TypeScript Runtime + Native JIT Engine (Desktop Only; will become a git submodule)
if (gradle.parent == null) {
    includeBuild("lib/NJS")
}

include(":common")
include(":systemCommon")
include(":desktop:common")
include(":desktop:nativeCommon")
include(":desktop:linuxCommon")
include(":desktop:qt")
include(":desktop:gtk")
include(":desktop:windows")
include(":desktop:macos")
include(":desktop:swing")
include(":mobile:common")
include(":mobile:android")
include(":mobile:ios")
include(":web")

// Extension Modules (Project-of-Projects with platform Gradle submodules + Single-Project extensions)
include(":extensions:gpu")
include(":extensions:gpu:common")
include(":extensions:gpu:systemCommon")
include(":extensions:gpu:desktop:nativeCommon")
include(":extensions:gpu:desktop:swing")
include(":extensions:gpu:mobile:common")
include(":extensions:gpu:mobile:android")
include(":extensions:gpu:mobile:ios")
include(":extensions:gpu:web")

include(":extensions:imageprocessing")
include(":extensions:imageprocessing:common")
include(":extensions:imageprocessing:systemCommon")
include(":extensions:imageprocessing:web")

include(":extensions:canvas2d")
include(":extensions:canvas2d:common")
include(":extensions:canvas2d:systemCommon")
include(":extensions:canvas2d:desktop:nativeCommon")
include(":extensions:canvas2d:desktop:windows")
include(":extensions:canvas2d:desktop:macos")
include(":extensions:canvas2d:desktop:gtk")
include(":extensions:canvas2d:desktop:qt")
include(":extensions:canvas2d:desktop:swing")
include(":extensions:canvas2d:mobile:common")
include(":extensions:canvas2d:mobile:android")
include(":extensions:canvas2d:mobile:ios")
include(":extensions:canvas2d:web")

include(":extensions:canvas3d")
include(":extensions:canvas3d:common")
include(":extensions:canvas3d:systemCommon")
include(":extensions:canvas3d:desktop:nativeCommon")
include(":extensions:canvas3d:desktop:windows")
include(":extensions:canvas3d:desktop:macos")
include(":extensions:canvas3d:desktop:gtk")
include(":extensions:canvas3d:desktop:qt")
include(":extensions:canvas3d:desktop:swing")
include(":extensions:canvas3d:mobile:common")
include(":extensions:canvas3d:mobile:android")
include(":extensions:canvas3d:mobile:ios")
include(":extensions:canvas3d:web")

include(":extensions:media")
include(":extensions:media:common")
include(":extensions:media:systemCommon")
include(":extensions:media:desktop:nativeCommon")
include(":extensions:media:desktop:windows")
include(":extensions:media:desktop:macos")
include(":extensions:media:desktop:gtk")
include(":extensions:media:desktop:qt")
include(":extensions:media:desktop:swing")
include(":extensions:media:mobile:common")
include(":extensions:media:mobile:android")
include(":extensions:media:mobile:ios")
include(":extensions:media:web")

include(":extensions:mediacontrols")
include(":extensions:mediacontrols:common")
include(":extensions:mediacontrols:systemCommon")
include(":extensions:mediacontrols:desktop:nativeCommon")
include(":extensions:mediacontrols:desktop:windows")
include(":extensions:mediacontrols:desktop:macos")
include(":extensions:mediacontrols:desktop:gtk")
include(":extensions:mediacontrols:desktop:qt")
include(":extensions:mediacontrols:desktop:swing")
include(":extensions:mediacontrols:mobile:common")
include(":extensions:mediacontrols:mobile:android")
include(":extensions:mediacontrols:mobile:ios")
include(":extensions:mediacontrols:web")

include(":extensions:webview")
include(":extensions:webview:common")
include(":extensions:webview:systemCommon")
include(":extensions:webview:desktop:common")
include(":extensions:webview:desktop:nativeCommon")
include(":extensions:webview:desktop:windows")
include(":extensions:webview:desktop:macos")
include(":extensions:webview:desktop:gtk")
include(":extensions:webview:desktop:qt")
include(":extensions:webview:desktop:swing")
include(":extensions:webview:mobile:common")
include(":extensions:webview:mobile:android")
include(":extensions:webview:mobile:ios")
include(":extensions:webview:web")

include(":extensions:alien")
include(":extensions:alien:common")
include(":extensions:alien:systemCommon")
include(":extensions:alien:web")

fun includeIfExists(projectPath: String) {
    val relPath = projectPath.trimStart(':').replace(':', '/')
    if (java.io.File(settingsDir, relPath).isDirectory) {
        include(projectPath)
    }
}

include(":extensions:overlay")
includeIfExists(":extensions:overlay:dsl")
include(":extensions:aichat")
includeIfExists(":extensions:aichat:dsl")
include(":extensions:codeeditor")
includeIfExists(":extensions:codeeditor:dsl")
include(":extensions:threejs")
includeIfExists(":extensions:threejs:dsl")
include(":extensions:box2d")
includeIfExists(":extensions:box2d:dsl")
include(":extensions:box3d")
includeIfExists(":extensions:box3d:dsl")
includeIfExists(":extensions:canvas2d:dsl")
includeIfExists(":extensions:canvas3d:dsl")
includeIfExists(":extensions:media:dsl")
includeIfExists(":extensions:mediacontrols:dsl")
includeIfExists(":extensions:webview:dsl")
includeIfExists(":extensions:alien:dsl")
include(":extensions:dsl")

// JestElectricRunner Multi-Backend UI Testing Suite (Jest + Robolectric-style API with Native Impls)
include(":tests")
include(":tests:common")
include(":tests:desktop:nativeCommon")
include(":tests:desktop:windows")
include(":tests:desktop:macos")
include(":tests:desktop:gtk")
include(":tests:desktop:qt")
include(":tests:desktop:swing")
include(":tests:mobile:common")
include(":tests:mobile:android")
include(":tests:mobile:ios")
include(":tests:web")

// Custom GenericUI Gradle Plugin Module
include(":gradle-plugin")

// IntelliJ IDEA IDE Plugin Module for Creating & Managing GenericUI Projects
include(":idea-plugin")
