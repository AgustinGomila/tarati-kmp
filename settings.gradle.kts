pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tarati"
include(":shared")

// Módulos que se incluyen solo si su directorio existe. Gradle 9.x falla en duro
// al incluir un proyecto cuyo directorio no existe (a diferencia de Gradle 8.x,
// que lo toleraba). Esto cubre dos escenarios:
//   - El build Docker del server copia únicamente `shared` y `server`, por lo que
//     los módulos cliente se omiten ahí.
//   - El repo público no incluye `server` (backend cerrado por ahora), por lo que
//     ese módulo se omite ahí.
listOf("server", "androidApp", "desktopApp", "webApp").forEach { module ->
    if (File(settingsDir, module).isDirectory) include(":$module")
}

// Tool interno de minería de aperturas (opening book). No se publica ni entra al
// build Docker del server, por eso se incluye solo si su directorio existe.
if (File(settingsDir, "tools/opening-miner").isDirectory) {
    include(":tools:opening-miner")
}