plugins {
    // Versiones centralizadas en gradle/libs.versions.toml (fuente única de verdad).

    // Android
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false

    // Kotlin
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    // Room & KSP
    alias(libs.plugins.room) apply false
    alias(libs.plugins.ksp) apply false

    alias(libs.plugins.kotlin.jvm)
}

// Forzar ws 8.20.1 para evitar CVE-2026-45736 (uninitialized memory disclosure en websocket.close).
// ws es dependencia directa que KGP inyecta en los sub-packages generados (build/wasm/packages/*/package.json).
// La resolución en el package.json raíz del workspace de yarn overridea todas las sub-declaraciones.
// Kotlin 2.x usa dos instancias de yarn independientes (JS y Wasm).
//
// Nota: the<T>() dentro de plugins.withType<P> { } resuelve en el scope del plugin, no del project —
// por eso se accede a la extensión explícitamente via rootProject.extensions.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.extensions.getByType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>()
        .resolution("ws", "8.20.1")
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    rootProject.extensions.getByType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension>()
        .resolution("ws", "8.20.1")
}