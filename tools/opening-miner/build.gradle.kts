import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "com.agustin.tarati.tools"
version = "1.0.0"

application {
    mainClass.set("com.agustin.tarati.tools.openingminer.OpeningMinerKt")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // Motor de juego compartido (GameState, Move, replay, hashBoard). Se excluyen los
    // artefactos Android/Compose/Room que el tool JVM no necesita — mismo criterio que :server.
    implementation(project(":shared")) {
        exclude(group = "androidx.room", module = "room-runtime")
        exclude(group = "androidx.room", module = "room-ktx")
        exclude(group = "androidx.compose.runtime", module = "runtime")
        exclude(group = "androidx.compose.foundation", module = "foundation")
        exclude(group = "androidx.compose.material3", module = "material3")
        exclude(group = "androidx.compose.ui", module = "ui")
        exclude(group = "org.jetbrains.compose.components", module = "components-resources")
        exclude(group = "org.jetbrains.compose.navigation", module = "navigation")
    }

    // Testing
    testImplementation(libs.kotlin.test.junit)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
}

// Etapa 2: compila opening_stats.tsv -> book.tsv. Main distinto del `run` de la Etapa 1.
// Uso: ./gradlew :tools:opening-miner:compileBook --args="--input <stats.tsv> --output <book.tsv>"
tasks.register<JavaExec>("compileBook") {
    group = "application"
    description = "Compila opening_stats.tsv en el opening book (Wilson lower bound + poda)"
    mainClass.set("com.agustin.tarati.tools.openingminer.OpeningBookBuilderKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
