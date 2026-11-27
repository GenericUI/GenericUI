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
include(":system")
include(":jvm")
include(":apple")
include(":desktop")
include(":mobile")
include(":web")

// Categorized First-Party Extensions
// Graphics
include(":extensions:graphics:gpu")
include(":extensions:graphics:imageprocessing")
include(":extensions:graphics:svg")
include(":extensions:graphics:svg:dsl")
include(":extensions:graphics:svg:alien")
include(":extensions:graphics:canvas2d")
include(":extensions:graphics:canvas2d:dsl")
include(":extensions:graphics:canvas2d:alien")
include(":extensions:graphics:canvas3d")
include(":extensions:graphics:canvas3d:dsl")
include(":extensions:graphics:canvas3d:alien")
include(":extensions:graphics:threejs")
include(":extensions:graphics:threejs:dsl")

// Physics
include(":extensions:physics:box2d")
include(":extensions:physics:box2d:dsl")
include(":extensions:physics:box3d")
include(":extensions:physics:box3d:dsl")

// Game & Input
include(":extensions:game:gamepad")
include(":extensions:game:engine")
include(":extensions:game:engine:dsl")
include(":extensions:game:engine2d")
include(":extensions:game:engine2d:dsl")
include(":extensions:game:engine3d")
include(":extensions:game:engine3d:dsl")

// Media
include(":extensions:media:media")
include(":extensions:media:media:dsl")
include(":extensions:media:media:alien")
include(":extensions:media:mediacontrols")

// Data
include(":extensions:data:schema")
include(":extensions:data:schema:dsl")

// System
include(":extensions:system:ipc")
include(":extensions:system:serviceworker")

// UI
include(":extensions:ui:dsl")
include(":extensions:ui:overlay")
include(":extensions:ui:overlay:dsl")
include(":extensions:ui:alien")
include(":extensions:ui:alien:dsl")
include(":extensions:ui:webview")
include(":extensions:ui:webview:dsl")
include(":extensions:ui:codeeditor")
include(":extensions:ui:codeeditor:dsl")
include(":extensions:ui:aichat")
include(":extensions:ui:aichat:dsl")
include(":extensions:ui:pip")

// JestElectricRunner Multi-Backend UI Testing Suite
include(":tests")

// Custom GenericUI Gradle Plugin Module
include(":gradle-plugin")

// IntelliJ IDEA IDE Plugin Module for Creating & Managing GenericUI Projects
include(":idea-plugin")
