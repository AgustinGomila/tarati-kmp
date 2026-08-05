package com.agustin.tarati.core.domain.game6.ai

import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.difficulty_champion
import com.agustin.tarati.shared.generated.resources.difficulty_easy
import com.agustin.tarati.shared.generated.resources.difficulty_hard
import com.agustin.tarati.shared.generated.resources.difficulty_medium
import org.jetbrains.compose.resources.StringResource

/**
 * Nombre de display localizado del tier de bot multijugador. Reutiliza los strings de dificultad del
 * juego 1 (`difficulty_*`) — el ladder es análogo (Fácil→Campeón).
 */
val MpBotLevel.displayNameRes: StringResource
    get() = when (this) {
        MpBotLevel.EASY -> Res.string.difficulty_easy
        MpBotLevel.MEDIUM -> Res.string.difficulty_medium
        MpBotLevel.HARD -> Res.string.difficulty_hard
        MpBotLevel.CHAMPION -> Res.string.difficulty_champion
    }
