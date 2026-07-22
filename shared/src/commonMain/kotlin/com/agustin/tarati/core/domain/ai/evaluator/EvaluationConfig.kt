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
     * [com.agustin.tarati.core.domain.ai.book.OpeningBook]). Gateable; por defecto activo solo en
     * HARD y CHAMPION — EASY/MEDIUM conservan su juego más débil y variado.
     */
    val openingBookEnabled: Boolean = false,
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
            openingBookEnabled = true,
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
            openingBookEnabled = true,
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