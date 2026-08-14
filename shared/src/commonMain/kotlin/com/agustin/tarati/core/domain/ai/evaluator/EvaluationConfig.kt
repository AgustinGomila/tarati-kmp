package com.agustin.tarati.core.domain.ai.evaluator

import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfig.Companion.withPersonality
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.defensive
import com.agustin.tarati.core.domain.ai.evaluator.EvaluationConfigBuilder.gambit
import com.agustin.tarati.core.domain.ai.services.Difficulty

/**
 * Full configuration for the AI engine.
 *
 * Parameters are grouped into five cohesive sub-objects:
 *  - [material]   — piece values and capture bonuses
 *  - [positional] — board control, mobility, territory, promotion
 *  - [tactical]   — quick threat and attack weights used in move ordering
 *  - [search]     — move-ordering heuristics within the search tree
 *  - [behavior]   — win thresholds, draw avoidance, stalling, difficulty handicaps
 *
 * Flat accessors are provided for all sub-object fields so that existing
 * consumers ([BoardEvaluator],
 * [MoveEvaluator],
 * [MinimaxStrategy], etc.)
 * continue to compile without modification.
 */
data class EvaluationConfig(
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val name: String = difficulty.name,
    val material: MaterialWeights = MaterialWeights(),
    val positional: PositionalWeights = PositionalWeights(),
    val tactical: TacticalWeights = TacticalWeights(),
    val search: SearchWeights = SearchWeights(),
    val behavior: BehaviorConfig = BehaviorConfig(),
    /**
     * Si el motor consulta el opening book antes de buscar (ver
     * [com.agustin.tarati.core.domain.ai.book.OpeningBook]). Gateable por dificultad;
     * actualmente **off en todos los niveles**. El book es una muleta que rinde más a la búsqueda
     * superficial (depth 5) que a la profunda (depth 7): con él activo, Champion no se separa de
     * Hard en el round-robin. La infra del book (miner, corpus, CI) queda intacta para reactivarlo
     * por-tier a futuro. Ver docs/internal/plans/engine_strength_plan.md.
     */
    val openingBookEnabled: Boolean = false,
    /**
     * Si la búsqueda resuelve las capturas pendientes en la hoja con una
     * [com.agustin.tarati.core.domain.ai.engine.MinimaxStrategy] quiescence search
     * antes de puntuar (ver [SearchWeights.quiescenceMaxPlies]). Gateable por dificultad;
     * **solo CHAMPION** lo activa. En Tarati casi toda jugada de contacto voltea piezas,
     * así que la profundidad fija evalúa rutinariamente posiciones a mitad de un intercambio
     * (horizon effect); la quiescence lo corrige y es donde la profundidad de CHAMPION (7) por
     * fin supera a HARD (5). Medida sin book (el contexto vigente tras gatear el book off):
     * CHAMPION-quiescence vs HARD-static ≈ 68 %. Ver docs/internal/plans/engine_strength_plan.md,
     * Fase B (reabierta).
     */
    val quiescenceEnabled: Boolean = false,
    /**
     * Cómo se rompen los empates de score en [com.agustin.tarati.core.domain.ai.engine.MinimaxStrategy]:
     *  - `false` (default) → **aleatorio** (reservoir sampling entre las jugadas de igual score). Da
     *    **variedad orgánica** sin costo de fuerza (todas las empatadas son igual de buenas para el motor):
     *    sorpresa y mejor experiencia de juego. Lo usan EASY/MEDIUM.
     *  - `true` → **keep-first determinista** (la primera del orden de `sortMoves`). Lo usan **HARD y CHAMPION**:
     *    fija el juego afilado (evita abrir con la jugada pasiva — OBS-1) y lo hace reproducible en las ramas
     *    profundas; la variedad partida a partida la aporta [rootSelection], no el reservoir.
     *
     * A diferencia de [BehaviorConfig.evalNoise] (que perturba el score y **debilita**), esto solo desempata
     * entre óptimos → variedad gratis.
     */
    val deterministicTiebreak: Boolean = false,
    /**
     * Política de **selección en la raíz** (ver [RootSelection]): variedad controlada partida a partida
     * aplicada solo al nodo raíz, sin tocar el valor de la búsqueda ni debilitar el juego profundo. La usan
     * **HARD y CHAMPION** para no jugar siempre la misma partida pese al desempate determinista: sesga hacia
     * la jugada afilada entre las cuasi-óptimas (esquiva OBS-1) y diversifica la apertura. Deshabilitada por
     * default (EASY/MEDIUM ya tienen variedad vía reservoir aleatorio).
     */
    val rootSelection: RootSelection = RootSelection(),
) {
    // ── MaterialWeights accessors ────────────────────────────────────────────
    val cobScore: Double get() = material.cobScore
    val rocScore: Double get() = material.rocScore
    val flipCobBonus: Double get() = material.flipCobBonus
    val flipRocBonus: Double get() = material.flipRocBonus
    val cobFlipMultiplier: Double get() = material.cobFlipMultiplier
    val rocFlipMultiplier: Double get() = material.rocFlipMultiplier

    // ── PositionalWeights accessors ──────────────────────────────────────────
    val controlCenterScore: Double get() = positional.controlCenterScore
    val controlCenterMultiplier: Double get() = positional.controlCenterMultiplier
    val mobilityScore: Double get() = positional.mobilityScore
    val mobilityImprovementMultiplier: Double get() = positional.mobilityImprovementMultiplier
    val domesticControlScore: Double get() = positional.domesticControlScore
    val opponentDomesticPressureScore: Double get() = positional.opponentDomesticPressureScore
    val upgradeScore: Double get() = positional.upgradeScore
    val upgradeBonusMultiplier: Double get() = positional.upgradeBonusMultiplier
    val forcedPromotionScore: Double get() = positional.forcedPromotionScore
    val isolationPenalty: Double get() = positional.isolationPenalty
    val conversionPotentialScore: Double get() = positional.conversionPotentialScore

    // ── SearchWeights accessors ──────────────────────────────────────────────
    val killerMoveBaseBonus: Double get() = search.killerMoveBaseBonus
    val historyHeuristicMultiplier: Double get() = search.historyHeuristicMultiplier
    val lateMoveReductionPenalty: Double get() = search.lateMoveReductionPenalty
    val lateMoveReductionDepth: Int get() = search.lateMoveReductionDepth
    val lmrBranchingThreshold: Int get() = search.lmrBranchingThreshold
    val lmrDepthReduction: Int get() = search.lmrDepthReduction
    val lmrMoveIndexThreshold: Int get() = search.lmrMoveIndexThreshold
    val quiescenceMaxPlies: Int get() = search.quiescenceMaxPlies
    val quiescenceMinCobFlips: Int get() = search.quiescenceMinCobFlips

    // ── TacticalWeights accessors ────────────────────────────────────────────
    val quickThreatWeight: Double get() = tactical.quickThreatWeight
    val quickAttackWeight: Double get() = tactical.quickAttackWeight

    // ── BehaviorConfig accessors ─────────────────────────────────────────────
    val winningScore: Double get() = behavior.winningScore
    val winningThreshold: Double get() = behavior.winningThreshold
    val winningPositionThreshold: Double get() = behavior.winningPositionThreshold
    val repetitionPenaltyMultiplier: Double get() = behavior.repetitionPenaltyMultiplier
    val immediateWinBonusMultiplier: Double get() = behavior.immediateWinBonusMultiplier
    val stallingPenalty: Double get() = behavior.stallingPenalty
    val stallingThreshold: Int get() = behavior.stallingThreshold
    val randomMoveChance: Double get() = behavior.randomMoveChance
    val evalNoise: Double get() = behavior.evalNoise

    companion object {
        val DEFAULT: EvaluationConfig = EvaluationConfig()

        // EASY: entiende solo conteo de piezas. Ignora flips, centro, movilidad y
        // territorio. Pierde porque no comprende el tablero, no por azar.
        val EASY: EvaluationConfig = EvaluationConfig(
            difficulty = Difficulty.EASY,
            material = MaterialWeights(
                rocScore = 150.0,
                flipCobBonus = 0.0,
                flipRocBonus = 0.0,
            ),
            positional = PositionalWeights(
                controlCenterScore = 0.0,
                mobilityScore = 0.0,
                domesticControlScore = 0.0,
                opponentDomesticPressureScore = 0.0,
                upgradeScore = 0.0,
                forcedPromotionScore = 0.0,
                isolationPenalty = 0.0,
            ),
            behavior = BehaviorConfig(
                evalNoise = 8.0,
            ),
        )

        // MEDIUM: comprende material completo y el valor de los flips.
        // No valora territorio ni amenazas posicionales avanzadas.
        val MEDIUM: EvaluationConfig = EvaluationConfig(
            difficulty = Difficulty.MEDIUM,
            material = MaterialWeights(
                cobScore = 190.0,
                rocScore = 360.0,
                flipCobBonus = 77.0,
                flipRocBonus = 100.0,
            ),
            positional = PositionalWeights(
                controlCenterScore = 20.0,
                mobilityScore = 8.0,
            ),
            behavior = BehaviorConfig(
                evalNoise = 4.0,
            ),
        )

        // HARD: comprende material, flips, centro y presión territorial.
        // No explota piezas aisladas ni oportunidades de promoción forzada.
        val HARD: EvaluationConfig = EvaluationConfig(
            difficulty = Difficulty.HARD,
            material = MaterialWeights(
                cobScore = 206.0,
                rocScore = 414.0,
                flipCobBonus = 77.0,
                flipRocBonus = 220.0,
            ),
            positional = PositionalWeights(
                controlCenterScore = 36.0,
                mobilityScore = 11.0,
                domesticControlScore = 35.0,
                opponentDomesticPressureScore = 45.0,
                upgradeScore = 160.0,
            ),
            // Variedad controlada en la raíz sin costo de fuerza (mismo mecanismo que CHAMPION): keep-first
            // determinista en las ramas profundas + selección en la raíz que sesga hacia la afilada entre las
            // cuasi-óptimas (esquiva OBS-1, a diferencia del reservoir uniforme) y diversifica la apertura.
            // Medio juego/final: epsilon=0 → solo empates exactos (costo cero). Apertura (primeros 8 plies):
            // epsilon=60 admite la 2ª mejor apertura (avanzar vs. costado) donde las posiciones están equilibradas.
            deterministicTiebreak = true,
            rootSelection = RootSelection(
                enabled = true,
                temperature = 0.75,
                openingPlies = 8,
                openingEpsilon = 60.0,
                openingTemperature = 0.9,
            ),
        )

        // CHAMPION: hereda la base calibrada de HARD y la extiende con conocimiento
        // táctico más profundo. Los pesos posicionales se escalan moderadamente sobre
        // HARD en lugar de introducir valores desconectados que distorsionen la búsqueda.
        // Único tier con el potencial de conversión de Rok activo: modela que un Rok móvil
        // domina un grupo enemigo ("1 Rok convierte todo"), donde el conteo de material es
        // ciego. Peso 40 (empírico: pico de fuerza vs HARD en juego diverso/off-book, sin
        // sobre-pesar el material; ver docs/internal/plans/engine_strength_plan.md, Fase A).
        val CHAMPION: EvaluationConfig = EvaluationConfig(
            difficulty = Difficulty.CHAMPION,
            material = MaterialWeights(
                cobScore = 216.0,
                rocScore = 454.0,
                flipCobBonus = 85.0,
                flipRocBonus = 240.0,
            ),
            positional = PositionalWeights(
                controlCenterScore = 42.0,
                mobilityScore = 14.0,
                domesticControlScore = 45.0,
                opponentDomesticPressureScore = 55.0,
                upgradeScore = 175.0,
                forcedPromotionScore = 175.0,
                isolationPenalty = 100.0,
                conversionPotentialScore = 40.0,
            ),
            behavior = BehaviorConfig(
                stallingPenalty = 8.0,
                stallingThreshold = 65,
            ),
            // Quiescence en la hoja — único tier que la activa. La profundidad 7 recién
            // paga cuando evalúa posiciones tranquilas (sin capturas pendientes); es el
            // diferenciador real frente a HARD (depth 5, sin quiescence).
            quiescenceEnabled = true,
            // Desempate determinista (keep-first) → juego afilado reproducible en las ramas profundas, sin
            // abrir con la jugada pasiva (OBS-1). Compartido con HARD; Easy/Medium usan el reservoir aleatorio.
            deterministicTiebreak = true,
            // Variedad controlada en la raíz: sin ella, dos CHAMPION juegan siempre la misma partida
            // (keep-first + evalNoise=0). Medio juego/final: epsilon=0 → solo empates exactos (costo cero);
            // temperature=0.75 sesga hacia la afilada dejando aparecer las otras óptimas. Apertura (primeros
            // 8 plies): epsilon=60 admite la 2ª mejor apertura (avanzar vs. costado, no solo el reflejo espejo)
            // donde las posiciones están equilibradas → variedad estratégica genuina a costo mínimo.
            rootSelection = RootSelection(
                enabled = true,
                temperature = 0.75,
                openingPlies = 8,
                openingEpsilon = 60.0,
                openingTemperature = 0.9,
            ),
        )

        /**
         * Applies [personality] on top of an existing [EvaluationConfig].
         *
         * The [personality] function receives this config and returns a modified version,
         * preserving the original [Difficulty] while layering the personality's weight
         * adjustments. Useful in tests and tournament runners to override the default
         * personality for a given difficulty:
         * ```
         * Difficulty.MEDIUM.withPersonality(::gambit)
         * EvaluationConfig.HARD.withPersonality(::aggressive)
         * ```
         */
        fun Difficulty.withPersonality(
            personality: (EvaluationConfig) -> EvaluationConfig,
        ): EvaluationConfig = personality(baseConfigFor(this))

        /**
         * Returns the raw base [EvaluationConfig] for [difficulty] without any personality
         * overlay. Intended for use by [withPersonality] and for callers that need the
         * unmodified weights (e.g. tournament runners applying a custom personality).
         */
        private fun baseConfigFor(difficulty: Difficulty): EvaluationConfig =
            when (difficulty) {
                Difficulty.EASY -> EASY
                Difficulty.MEDIUM -> MEDIUM
                Difficulty.HARD -> HARD
                Difficulty.CHAMPION -> CHAMPION
            }

        /**
         * Returns the [EvaluationConfig] for [difficulty] with its empirically the strongest
         * personality pre-applied.
         *
         * Personality selection is based on round-robin tournament results across the five
         * key personalities (baseline, defensive, gambit, balanced, positional):
         * - EASY    → no overlay — noise alone defines the skill floor.
         * - MEDIUM  → [defensive]: steady piece safety outperforms riskier styles at depth 3.
         * - HARD    → [gambit]: high-flip aggression exploits the deeper search at depth 5.
         * - CHAMPION → [gambit]: at depth 7 high-flip aggression produces the highest win rate.
         */
        fun getByDifficulty(difficulty: Difficulty): EvaluationConfig =
            when (difficulty) {
                Difficulty.EASY -> EASY
                Difficulty.MEDIUM -> defensive(MEDIUM)
                Difficulty.HARD -> gambit(HARD)
                Difficulty.CHAMPION -> gambit(CHAMPION)
            }
    }
}