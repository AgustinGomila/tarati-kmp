package com.agustin.tarati.core.utils

import com.agustin.tarati.shared.BuildConfig

/** Refleja el tipo de build de la librería compartida (debug vs release). */
actual val isDebugBuild: Boolean = BuildConfig.DEBUG
