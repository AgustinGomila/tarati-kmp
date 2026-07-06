@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// ─────────────────────────────────────────────────────────────────────────────
// appVersion generado desde version.properties — fuente única, incluye build number.
// Reemplaza los antiguos AppInfo.kt hardcodeados ("1.0.0") por plataforma.
// ─────────────────────────────────────────────────────────────────────────────
val generateAppVersion by tasks.registering {
    val versionFile = rootProject.file("version.properties")
    val outputDir = layout.buildDirectory.dir("generated/appVersion/kotlin")
    inputs.file(versionFile)
    outputs.dir(outputDir)
    doLast {
        val props = Properties().apply { versionFile.inputStream().use { load(it) } }
        val name = props.getProperty("versionName")
        val code = props.getProperty("versionCode")
        val pkgDir = outputDir.get().dir("com/agustin/tarati").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("AppVersion.kt").writeText(
            buildString {
                appendLine("package com.agustin.tarati")
                appendLine()
                appendLine("// GENERADO por la tarea Gradle :shared:generateAppVersion desde version.properties.")
                appendLine("// No editar a mano — se regenera en cada build.")
                appendLine("val appVersion: String = \"$name (build $code)\"")
            }
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    // Aplicar el template de jerarquía por defecto ANTES de los dependsOn custom.
    // Sin esta llamada explícita, Kotlin 2.x detecta los .dependsOn() manuales
    // y omite el template → iosMain queda desconectado.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // Fuente generada con appVersion (build number incluido).
        commonMain {
            kotlin.srcDir(generateAppVersion)
        }

        commonMain.dependencies {
            // Kotlin
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.atomicfu)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose.multiplatform)
            implementation(libs.koin.compose.viewmodel)

            // UUID
            implementation(libs.uuid)

            // Compose Multiplatform
            implementation(libs.androidx.compose.runtime)
            implementation(libs.androidx.compose.foundation)
            implementation(libs.androidx.compose.material3)
            implementation(libs.androidx.compose.ui)
            implementation(libs.androidx.compose.components.resources)
            implementation(libs.androidx.compose.navigation)

            // Ktor client WebSocket (multiplataforma — Android, Desktop, WASM)
            implementation(libs.ktor.client.websockets)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // roomMain: Room runtime para Android / iOS / JVM (sin wasmJs — no tiene artifact).
        // Fuentes Room en src/roomMain/kotlin/.
        val roomMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.androidx.room.runtime)
            }
        }

        androidMain.get().dependsOn(roomMain)
        iosMain.get().dependsOn(roomMain)
        jvmMain.get().dependsOn(roomMain)

        // skikoMain: implementación gráfica única basada en Skiko (org.jetbrains.skia),
        // compartida por Desktop (jvm), Web (wasmJs) e iOS (native). Android NO la usa
        // (su backend es android.graphics). Mismo patrón intermedio que roomMain.
        val skikoMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(skikoMain)
        wasmJsMain.get().dependsOn(skikoMain)
        iosMain.get().dependsOn(skikoMain)

        androidMain.dependencies {
            // Room KTX (solo en Android)
            implementation(libs.androidx.room.ktx)

            // UI Tooling para previews
            implementation(libs.androidx.compose.ui.tooling.preview)
        }

        // JVM (Desktop + Server)
        jvmMain.dependencies {
            // Ktor server — solo JVM
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.websockets)
        }

        // WASM (browser) — sin Room
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

android {
    namespace = "com.agustin.tarati.shared"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk
            .get()
            .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmCompatibility.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmCompatibility.get())
    }

    buildFeatures {
        // Genera com.agustin.tarati.shared.BuildConfig (DEBUG) para el actual de `isDebugBuild`.
        buildConfig = true
    }

    compose.resources {
        publicResClass = true
        packageOfResClass = "com.agustin.tarati.shared.generated.resources"
        generateResClass = always
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")
}

// Room Configuration
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room KSP para Android
    add("kspAndroid", libs.androidx.room.compiler)

    // Room KSP para iOS
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)

    // Room KSP para Desktop/JVM
    add("kspJvm", libs.androidx.room.compiler)

}