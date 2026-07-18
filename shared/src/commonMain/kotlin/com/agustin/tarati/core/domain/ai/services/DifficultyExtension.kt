package com.agustin.tarati.core.domain.ai.services

import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.difficulty_champion
import com.agustin.tarati.shared.generated.resources.difficulty_easy
import com.agustin.tarati.shared.generated.resources.difficulty_hard
import com.agustin.tarati.shared.generated.resources.difficulty_medium
import org.jetbrains.compose.resources.StringResource

/**
 * Extensiones de Difficulty con recursos de UI (Compose Resources).
 * Agregan los nombres de display localizados.
 */

val Difficulty.displayNameRes: StringResource
    get() = when (this) {
        Difficulty.EASY -> Res.string.difficulty_easy
        Difficulty.MEDIUM -> Res.string.difficulty_medium
        Difficulty.HARD -> Res.string.difficulty_hard
        Difficulty.CHAMPION -> Res.string.difficulty_champion
    }