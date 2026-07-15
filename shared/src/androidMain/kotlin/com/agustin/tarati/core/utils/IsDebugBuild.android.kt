package com.agustin.tarati.core.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import org.koin.core.context.GlobalContext

/**
 * Detección runtime del tipo de build vía [ApplicationInfo.FLAG_DEBUGGABLE] (equivale a
 * `BuildConfig.DEBUG` del APK instalado). Independiente de BuildConfig: el plugin Android
 * KMP library es single-variant y no genera BuildConfig por buildType.
 * El [Context] se resuelve desde Koin (`androidContext` registrado en TaratiApplication);
 * si Koin aún no arrancó (p. ej. previews), se asume build no-debug.
 */
private val debuggable: Boolean by lazy {
    runCatching {
        val context = GlobalContext.get().get<Context>()
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(false)
}

/** Refleja el tipo de build de la app instalada (debug vs release). */
actual val isDebugBuild: Boolean get() = debuggable
