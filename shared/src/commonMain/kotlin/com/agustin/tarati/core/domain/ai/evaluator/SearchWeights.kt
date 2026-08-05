package com.agustin.tarati.core.domain.ai.evaluator

/**
 * Weights that control move-ordering heuristics within the search tree.
 *
 * Used exclusively by [MoveEvaluator]: killer move bonus, history heuristic,
 * and late-move reduction parameters.
 */
data class SearchWeights(
    val killerMoveBaseBonus: Double = 50.0,
    val historyHeuristicMultiplier: Double = 0.1,
    val lateMoveReductionPenalty: Double = 10.0,
    val lateMoveReductionDepth: Int = 3,
    // ── Branching-factor LMR (rok high-mobility positions) ─────────────────
    // When a node has more than [lmrBranchingThreshold] moves (typical in
    // positions with many roks), moves ranked [lmrMoveIndexThreshold]+ are
    // searched at depth-[lmrDepthReduction]. Re-searched at full depth if
    // the result beats alpha. Never applied to capturing or winning moves.
    val lmrBranchingThreshold: Int = 10,
    val lmrDepthReduction: Int = 1,
    val lmrMoveIndexThreshold: Int = 3,
    // ── Quiescence search (leaf capture-resolution) ────────────────────────
    // At a depth-0 leaf, instead of scoring the (possibly mid-exchange) position
    // statically, extend the search over "noisy" moves — those that flip
    // [quiescenceMinCobFlips]+ cobs or any rok — until a quiet position or
    // [quiescenceMaxPlies] is reached. Bounds the horizon effect that a fixed-depth
    // search suffers in Tarati, where nearly every contact move flips pieces.
    // Gated by [EvaluationConfig.quiescenceEnabled]; only CHAMPION enables it.
    val quiescenceMaxPlies: Int = 4,
    val quiescenceMinCobFlips: Int = 1,
) {
    fun scaleKiller(factor: Double): SearchWeights = copy(
        killerMoveBaseBonus = killerMoveBaseBonus * factor,
    )

    fun scaleLmr(penalty: Double = 1.0, depth: Int = lateMoveReductionDepth): SearchWeights = copy(
        lateMoveReductionPenalty = lateMoveReductionPenalty * penalty,
        lateMoveReductionDepth = depth,
    )
}