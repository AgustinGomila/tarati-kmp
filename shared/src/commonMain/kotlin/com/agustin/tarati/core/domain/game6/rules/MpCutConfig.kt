package com.agustin.tarati.core.domain.game6.rules

/**
 * Umbrales del **corte de partida por estancamiento** del juego multijugador (§2.6 del plan).
 *
 * El motor MP no tiene "tablas": ante un estancamiento, la partida se corta y se resuelve por mayoría
 * de piezas (ver [MpRules.resolveByCut]). Dos disparadores locales (headless):
 *
 * - **Triple repetición** ([repetitionLimit]): la misma posición (piezas + turno) reaparece ese nº de
 *   veces. Linaje: la regla de triple repetición del juego 1. Requiere historial → lo aplica [MpMatch].
 * - **N jugadas sin conversión** ([maxMovesWithoutCapture]): medias jugadas consecutivas sin captura.
 *   Linaje: la regla de 50 movimientos del juego 1 (en MP el "progreso" es una conversión, ya que no
 *   hay cob-move ni promoción que distinguir). Lo aplica [MpRules.applyMove] con este config.
 *
 * @property repetitionLimit repeticiones de una misma posición (piezas + turno) que disparan el corte.
 * @property maxMovesWithoutCapture medias jugadas sin ninguna conversión que disparan el corte.
 */
data class MpCutConfig(
    val repetitionLimit: Int = 3,
    val maxMovesWithoutCapture: Int = 100,
) {
    init {
        require(repetitionLimit >= 2) { "repetitionLimit debe ser ≥ 2 (fue $repetitionLimit)" }
        require(maxMovesWithoutCapture >= 1) {
            "maxMovesWithoutCapture debe ser ≥ 1 (fue $maxMovesWithoutCapture)"
        }
    }

    companion object {
        /**
         * Config por defecto: triple repetición y **100 medias jugadas sin conversión**. Los 100 plies
         * espejan la regla del juego 1 (50 movimientos por jugador en una partida de 2). Calibrable.
         */
        val Default: MpCutConfig = MpCutConfig()
    }
}
