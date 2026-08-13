import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun getVersionProperties(): Properties {
    val versionFile = file("${project.rootDir}/version.properties")
    val versionProps = Properties()
    versionProps.load(versionFile.reader())
    return versionProps
}

val versionProps: Properties = getVersionProperties()

// Credenciales de firma: env var (CI) → keystore/signing.properties (local, gitignoreado) → "".
val signingProps: Properties = Properties().apply {
    val file = rootProject.file("keystore/signing.properties")
    if (file.exists()) file.reader().use { load(it) }
}

fun signingCredential(key: String): String =
    (System.getenv(key) ?: signingProps.getProperty(key) ?: "")
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")

android {
    namespace = "com.agustin.tarati"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "com.agustin.tarati"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/keystore.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = signingCredential("KEYSTORE_PASSWORD")
                keyAlias = signingCredential("KEY_ALIAS")
                keyPassword = signingCredential("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Solo asignar signingConfig si el keystore existe
            val keystoreFile = rootProject.file("keystore/keystore.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                debugSymbolLevel = "FULL"  // Genera símbolos
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmCompatibility.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmCompatibility.get())
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Mockear automáticamente clases de Android framework
            isReturnDefaultValues = true
            // Incluir recursos de Android en tests
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.jvmArgs(
                "-XX:+EnableDynamicAgentLoading",
                "-Xshare:off",
            )
        }
    }

}

// Con built-in Kotlin (AGP 9) la extensión kotlin{} la registra el propio AGP;
// jvmTarget explícito (default = android.compileOptions.targetCompatibility).
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// ═══════════════════════════════════════════════════════════════
// Exclusión de tests pesados - POR DEFECTO
// ═══════════════════════════════════════════════════════════════
val skipHeavyTests: Boolean = if (project.hasProperty("skipHeavyTests")) {
    project.property("skipHeavyTests").toString().toBoolean()
} else {
    true  // Por defecto EXCLUIR
}

// Tests pesados de AI/torneo. Se excluyen por defecto en la corrida completa (CI, `./gradlew test`),
// pero se **habilitan cuando se pide un test puntual**: tanto `--tests` por consola como el clic del
// gutter de la IDE (vía Tooling API) pueblan `filter.commandLineIncludePatterns` de la task, así que
// se levanta la exclusión en ese caso. `-PskipHeavyTests=false` fuerza correr todo siempre.
val heavyTestPatterns: Set<String> = setOf(
    "**/*RegressionTest*.class",
    "**/*RoundRobinTest*.class",
    "**/*DifficultyDiagnosticTest*.class",
    "**/*OpeningBookAbTest*.class",
    "**/*MpEngineStrengthTest*.class",
    "**/*SideToMoveAdvantageTest*.class",
    "**/*ChampionVarietyTest*.class",
    // Herramientas que juegan/replay partidas a depth 7 (CHAMPION) — lentas o manuales, no de aserción.
    "**/*VisualGameTest*.class",
    "**/*ChampionMatchTest*.class",
    "**/*PgnReplayTest*.class",
)

if (skipHeavyTests) {
    println("⏭️  Skipping heavy AI tests (lifted when a test filter is present)")
} else {
    println("🔬 Running ALL tests")
}

tasks.withType<Test>().configureEach {
    if (skipHeavyTests) {
        // La exclusión se levanta cuando hay un filtro de test explícito, detectado **en runtime** desde
        // `commandLineIncludePatterns`: lo pueblan tanto `--tests` por consola como el **gutter de la IDE**
        // (vía Tooling API, que NO pasa `--tests` por CLI). Eso usa la API interna `DefaultTestFilter`, que
        // no es serializable en el configuration cache; por eso se marca el task como incompatible: degrada
        // limpio (el resto del build sí cachea, este task no — su configuración es barata) en vez de fallar.
        // `-PskipHeavyTests=false` fuerza correr todo siempre.
        notCompatibleWithConfigurationCache(
            "La exclusión de tests pesados inspecciona el filtro de test en runtime (incluye el gutter de la IDE)"
        )
        exclude(heavyTestPatterns)
        doFirst {
            val test = this as Test
            val explicitFilter =
                (test.filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter)
                    .commandLineIncludePatterns
                    .isNotEmpty()
            if (explicitFilter) {
                test.setExcludes(test.excludes - heavyTestPatterns)
                test.logger.lifecycle("🔬 Explicit test filter — heavy AI test exclusion lifted")
            } else {
                test.logger.lifecycle("⏭️  SKIPPING heavy AI tests")
            }
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("androidApp/compose_stability.conf")
    )
}

dependencies {
// Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

// Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.compose.components.resources)
    implementation(libs.kotlinx.coroutines.play)

// Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

// Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

// DataStore
    implementation(libs.datastore)

// Google Services
    implementation(libs.play.services.games.v2)
    implementation(libs.billing)
    implementation(libs.integrity)
    implementation(project(":shared"))

// Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)

// Android Testing
    androidTestImplementation(libs.android.junit4)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.test.runner)

// Debug
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

// Ktor
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.okhttp)

// SLF4J provider para Android (elimina warning "No SLF4J providers were found")
    implementation(libs.slf4j.android)
}