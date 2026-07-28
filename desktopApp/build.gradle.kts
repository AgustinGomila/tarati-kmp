import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// ═══════════════════════════════════════════════════════════════════════════
// Versión desde properties de Gradle (pasadas por workflow con -P)
// ═══════════════════════════════════════════════════════════════════════════
val appVersionName = project.findProperty("versionName")?.toString() ?: "1.0.0"
val appVersionCode = project.findProperty("versionCode")?.toString() ?: "1"

// Versión del instalador nativo (MSI/DEB). Windows Installer solo hace upgrade
// in-place si la ProductVersion (major.minor.build) aumenta entre releases. El
// versionName es constante (marketing), así que el tercer campo (build) toma el
// versionCode, que crece de forma monótona en cada release. Deb también exige
// versión creciente para que apt/dpkg reconozcan la actualización.
val installerVersion: String = run {
    val parts = appVersionName.split(".")
    val major = parts.getOrElse(0) { "1" }
    val minor = parts.getOrElse(1) { "0" }
    "$major.$minor.$appVersionCode"
}

println("🖥️  Desktop Version: $appVersionName (build $appVersionCode) — installer $installerVersion")

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.components.resources)

    implementation(libs.koin.core)
    implementation(libs.koin.compose.multiplatform)
    implementation(libs.koin.compose.viewmodel)

    // Coroutines Swing - CRITICAL for Desktop Main dispatcher
    implementation(libs.kotlinx.coroutines.swing)

    // SQLite driver for Room on Desktop
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)

    // Ktor CIO engine for Desktop WebSocket/HTTP
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // SLF4J provider para Desktop (elimina warning "No SLF4J providers were found")
    implementation(libs.logback)

    // Decoder MP3 para javax.sound.sampled (SPI: JLayer + Tritonus) — usado por DesktopSoundService
    implementation(libs.mp3spi)
}

compose.desktop {
    application {
        mainClass = "com.agustin.tarati.desktop.MainKt"

        // JVM args para el run de desarrollo (./gradlew run)
        // Incluye --enable-native-access para silenciar warning de Skiko
        jvmArgs += listOf(
            "--enable-native-access=ALL-UNNAMED",
            "-Xmx2G",
            "-Dfile.encoding=UTF-8"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            packageName = "Tarati"
            packageVersion = installerVersion  // major.minor.versionCode — habilita upgrade in-place
            description = "Tarati - A strategic board game by George Spencer-Brown"
            copyright = "© 2026 Agustin Gomila. All rights reserved."
            vendor = "Agustin Gomila"

            // Módulos JDK requeridos
            modules("java.sql", "java.naming", "jdk.unsupported")

            // Windows
            windows {
                iconFile.set(project.file("src/main/resources/icons/tarati.ico"))

                // UUID estable — identifica el producto para upgrade in-place.
                // Sin esto, cada MSI genera un upgradeCode aleatorio y Windows
                // exige desinstalar la versión anterior antes de instalar la nueva.
                upgradeUuid = "7B3E2F81-D4C6-4A91-B85D-F2E6C3A70948"
                menuGroup = "Tarati"
                shortcut = true
                dirChooser = true
                perUserInstall = false
            }

            // macOS
            macOS {
                val iconFile = project.file("src/main/resources/icons/tarati.icns")
                if (iconFile.exists()) {
                    this.iconFile.set(iconFile)
                }

                bundleID = "com.agustin.tarati.desktop"
                appStore = false
                appCategory = "public.app-category.games"
            }

            // Linux
            linux {
                iconFile.set(project.file("../webApp/src/wasmJsMain/resources/icon-512x512.png"))

                appCategory = "Game"
                debMaintainer = "tarati.gameboard@gmail.com"
                menuGroup = "Games"
                appRelease = "1"
            }

            // Argumentos JVM para el instalable empaquetado
            jvmArgs += listOf(
                "--enable-native-access=ALL-UNNAMED",
                "-Xmx2G",
                "-Dfile.encoding=UTF-8"
            )
        }
    }
}