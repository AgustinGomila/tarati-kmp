package com.agustin.tarati.core.domain.game.pieces

import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.black
import com.agustin.tarati.shared.generated.resources.white
import org.jetbrains.compose.resources.StringResource

/**
 * Extensiones de CobColor con recursos de UI (Compose Resources).
 */

/** Nombre localizado del color para mostrar en la UI. */
val CobColor.colorNameRes: StringResource
    get() = when (this) {
        CobColor.WHITE -> Res.string.white
        CobColor.BLACK -> Res.string.black
    }