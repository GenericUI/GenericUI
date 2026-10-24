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
includeBuild("lib/NJS")

include(":common")
include(":desktop:shared")
include(":desktop:qt")
include(":desktop:gtk")
include(":desktop:windows")
include(":desktop:macos")
include(":desktop:swing")
include(":mobile:shared")
include(":mobile:android")
include(":mobile:ios")
include(":web")

// Extension Modules (Project-of-Projects with platform Gradle submodules + Single-Project extensions)
include(":extensions:gpu")
include(":extensions:gpu:common")
include(":extensions:gpu:desktop:shared")
include(":extensions:gpu:desktop:swing")
include(":extensions:gpu:mobile:shared")
include(":extensions:gpu:mobile:android")
include(":extensions:gpu:mobile:ios")
include(":extensions:gpu:web")

include(":extensions:canvas2d")
include(":extensions:canvas2d:common")
include(":extensions:canvas2d:desktop:shared")
include(":extensions:canvas2d:desktop:windows")
include(":extensions:canvas2d:desktop:macos")
include(":extensions:canvas2d:desktop:gtk")
include(":extensions:canvas2d:desktop:qt")
include(":extensions:canvas2d:desktop:swing")
include(":extensions:canvas2d:mobile:shared")
include(":extensions:canvas2d:mobile:android")
include(":extensions:canvas2d:mobile:ios")
include(":extensions:canvas2d:web")

include(":extensions:canvas3d")
include(":extensions:canvas3d:common")
include(":extensions:canvas3d:desktop:shared")
include(":extensions:canvas3d:desktop:windows")
include(":extensions:canvas3d:desktop:macos")
include(":extensions:canvas3d:desktop:gtk")
include(":extensions:canvas3d:desktop:qt")
include(":extensions:canvas3d:desktop:swing")
include(":extensions:canvas3d:mobile:shared")
include(":extensions:canvas3d:mobile:android")
include(":extensions:canvas3d:mobile:ios")
include(":extensions:canvas3d:web")

include(":extensions:media")
include(":extensions:media:common")
include(":extensions:media:desktop:shared")
include(":extensions:media:desktop:windows")
include(":extensions:media:desktop:macos")
include(":extensions:media:desktop:gtk")
include(":extensions:media:desktop:qt")
include(":extensions:media:desktop:swing")
include(":extensions:media:mobile:shared")
include(":extensions:media:mobile:android")
include(":extensions:media:mobile:ios")
include(":extensions:media:web")

include(":extensions:webview")
include(":extensions:webview:common")
include(":extensions:webview:desktop:shared")
include(":extensions:webview:desktop:windows")
include(":extensions:webview:desktop:macos")
include(":extensions:webview:desktop:gtk")
include(":extensions:webview:desktop:qt")
include(":extensions:webview:desktop:swing")
include(":extensions:webview:mobile:shared")
include(":extensions:webview:mobile:android")
include(":extensions:webview:mobile:ios")
include(":extensions:webview:web")

include(":extensions:imageprocessing")
include(":extensions:aichat")
include(":extensions:codeeditor")
include(":extensions:threejs")
include(":extensions:box2d")
include(":extensions:box3d")
include(":extensions:dsl")

// Custom GenericUI Gradle Plugin Module
include(":gradle-plugin")

// IntelliJ IDEA IDE Plugin Module for Creating & Managing GenericUI Projects
include(":idea-plugin")
