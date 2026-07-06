package com.agustin.tarati.core.utils

/**
 * `true` en builds de desarrollo/debug.
 *
 * Se usa para habilitar atajos de desarrollo sin ownership (p. ej. desbloquear los cosméticos
 * premium — piezas/paletas — para probarlos). En **Android** refleja `BuildConfig.DEBUG` (debug vs
 * release). En Desktop/Web/iOS es `false` por defecto (los builds distribuidos son release).
 */
expect val isDebugBuild: Boolean
