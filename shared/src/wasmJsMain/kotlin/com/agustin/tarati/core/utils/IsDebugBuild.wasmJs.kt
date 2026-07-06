@file:OptIn(ExperimentalWasmJsInterop::class)

package com.agustin.tarati.core.utils

/**
 * En web el bundle no tiene variante debug/release; se considera **debug** cuando corre en el
 * dev server (`localhost` / `127.0.0.1`), no en producción (`tarati.tech`). Permite probar los
 * cosméticos premium sin ownership durante el desarrollo, igual que `BuildConfig.DEBUG` en Android.
 */
@JsFun("() => { const h = window.location.hostname; return h === 'localhost' || h === '127.0.0.1'; }")
private external fun isLocalhost(): Boolean

actual val isDebugBuild: Boolean = isLocalhost()
