import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    android {
        namespace = "com.nexustools.genericui.jvm"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTest {}

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":system"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("jvmMain") {
            kotlin.srcDir("src/jvmSharedMain/kotlin")
        }
        getByName("androidMain") {
            kotlin.srcDir("src/jvmSharedMain/kotlin")
        }
        getByName("jvmTest") {
            kotlin.srcDir("src/jvmSharedTest/kotlin")
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }
        getByName("androidHostTest") {
            kotlin.srcDir("src/jvmSharedTest/kotlin")
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }
    }
}
