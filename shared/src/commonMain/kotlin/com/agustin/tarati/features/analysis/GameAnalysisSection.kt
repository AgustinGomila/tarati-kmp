package com.agustin.tarati.features.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.data.database.dto.GameDto
import com.agustin.tarati.core.domain.analysis.AnalysisCacheRepository
import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.GameAnalysis
import com.agustin.tarati.core.domain.analysis.MoveClassifier
import com.agustin.tarati.core.domain.analysis.MoveQuality
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.core.domain.game.play.GameState.Companion.parseBoardNotation
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.analysis_analyze_game
import com.agustin.tarati.shared.generated.resources.analysis_analyzing
import com.agustin.tarati.shared.generated.resources.analysis_quality_best
import com.agustin.tarati.shared.generated.resources.analysis_quality_blunder
import com.agustin.tarati.shared.generated.resources.analysis_quality_good
import com.agustin.tarati.shared.generated.resources.analysis_quality_inaccuracy
import com.agustin.tarati.shared.generated.resources.analysis_quality_mistake
import com.agustin.tarati.shared.generated.resources.analysis_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** Colores de calidad de jugada (verde=buenas … rojo=graves). Usados por la leyenda. */
private val BestColor = Color(0xFF2E7D32)
private val GoodColor = Color(0xFF9CCC65)
private val InaccuracyColor = Color(0xFFFBC02D)
private val MistakeColor = Color(0xFFF57C00)
private val BlunderColor = Color(0xFFD32F2F)

/** Color de cada categoría, para las etiquetas de la leyenda. */
private fun legendColorFor(quality: MoveQuality): Color = when (quality) {
    MoveQuality.BEST -> BestColor
    MoveQuality.GOOD -> GoodColor
    MoveQuality.INACCURACY -> InaccuracyColor
    MoveQuality.MISTAKE -> MistakeColor
    MoveQuality.BLUNDER -> BlunderColor
}

/**
 * Marcador (punto) sobre el gráfico: **solo** las jugadas notables (imprecisión/error/grave),
 * para no saturar la curva con un punto por ply. Las buenas (BEST/GOOD) se ven en la leyenda,
 * no como puntos.
 */
private fun markerColorFor(quality: MoveQuality): Color? = when (quality) {
    MoveQuality.BLUNDER -> BlunderColor
    MoveQuality.MISTAKE -> MistakeColor
    MoveQuality.INACCURACY -> InaccuracyColor
    else -> null
}

private sealed interface AnalysisUi {
    data object Idle : AnalysisUi
    data object Running : AnalysisUi
    data class Ready(val analysis: GameAnalysis) : AnalysisUi
}

/**
 * Sección del gráfico de evaluación en el detalle de partida: botón "Analizar", barra
 * de progreso durante el cómputo por-ply (depth 3) y el [EvalGraph] al terminar. El
 * resultado se cachea por [gameId] vía [AnalysisCacheRepository] (Room en Android/
 * Desktop, memoria en Web).
 *
 * El cómputo se delega a un [AnalysisRunner] inyectado (fuera del hilo de UI): hilos
 * reales en Desktop/Android; en Web, un runner basado en Web Worker lo reemplazará. El
 * progreso viaja por un [MutableStateFlow] — seguro de escribir desde el hilo de cómputo
 * y colectado en el hilo de UI — así el `onProgress` nunca toca estado Compose desde
 * otro hilo.
 *
 * @param currentMoveIndex índice 0-based del movimiento visualizado; el gráfico marca
 *        el punto `currentMoveIndex + 1` (el punto 0 es la posición inicial).
 * @param onMoveClick salta a un movimiento al tocar el gráfico.
 */
@Composable
fun GameAnalysisSection(
    gameDto: GameDto,
    gameId: String,
    currentMoveIndex: Int,
    onMoveClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    analysisRepository: AnalysisCacheRepository = koinInject(),
    analysisRunner: AnalysisRunner = koinInject(),
) {
    val moves = gameDto.moveHistory
    if (moves.isEmpty()) return
    val scope = rememberCoroutineScope()
    val firstMoverWhite = remember(gameDto) {
        runCatching { parseBoardNotation(gameDto.initialBoardPosition).currentTurn == CobColor.WHITE }
            .getOrDefault(true)
    }

    var uiState by remember(gameId) { mutableStateOf<AnalysisUi>(AnalysisUi.Idle) }
    val progressFlow = remember(gameId) { MutableStateFlow(0f) }
    val progress by progressFlow.collectAsState()

    // Al abrir una partida (o cambiar de gameId), reusa el análisis cacheado si existe.
    LaunchedEffect(gameId) {
        if (uiState is AnalysisUi.Idle) {
            analysisRepository.get(gameId)?.let { uiState = AnalysisUi.Ready(it) }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = localizedString(Res.string.analysis_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        when (val state = uiState) {
            AnalysisUi.Idle -> Button(
                onClick = {
                    uiState = AnalysisUi.Running
                    progressFlow.value = 0f
                    scope.launch {
                        val result = analysisRunner.run(
                            initialBoardPosition = gameDto.initialBoardPosition,
                            moves = moves,
                            onProgress = { progressFlow.value = it },
                        )
                        analysisRepository.put(gameId, result)
                        uiState = AnalysisUi.Ready(result)
                    }
                },
            ) {
                Text(localizedString(Res.string.analysis_analyze_game))
            }

            AnalysisUi.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${localizedString(Res.string.analysis_analyzing)} ${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is AnalysisUi.Ready -> {
                val classifications = remember(state.analysis, firstMoverWhite) {
                    MoveClassifier.classify(state.analysis) { i -> (i % 2 == 0) == firstMoverWhite }
                }
                val markers = remember(classifications) {
                    classifications
                        .mapNotNull { c -> markerColorFor(c.quality)?.let { (c.moveIndex + 1) to it } }
                        .toMap()
                }
                val counts = remember(classifications) {
                    classifications.groupingBy { it.quality }.eachCount()
                }
                // Puntos de la serie (p = índice del movimiento p-1) resultantes de un
                // movimiento de Negras: misma convención de color que MoveClassifier.
                val blackMoveMarks = remember(state.analysis, firstMoverWhite) {
                    (1 until state.analysis.series.size)
                        .filter { p -> ((p - 1) % 2 == 0) != firstMoverWhite }
                        .toSet()
                }
                EvalGraph(
                    series = state.analysis.series.map { it.winProbWhite },
                    currentIndex = currentMoveIndex + 1,
                    onSelectIndex = { pointIndex -> onMoveClick((pointIndex - 1).coerceIn(0, moves.lastIndex)) },
                    markers = markers,
                    blackMoveMarks = blackMoveMarks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                MoveQualityLegend(counts)
            }
        }
    }
}

/**
 * Leyenda del gráfico: una entrada por categoría de calidad ([MoveQuality]) con su color y el
 * **conteo** de jugadas de la partida en esa categoría. Sirve de referencia de colores y de
 * resumen — así las jugadas buenas (BEST/GOOD), que no llevan punto en la curva, quedan visibles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveQualityLegend(counts: Map<MoveQuality, Int>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MoveQuality.entries.forEach { quality ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(legendColorFor(quality)),
                )
                Text(
                    text = "${qualityLabel(quality)} ${counts[quality] ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun qualityLabel(quality: MoveQuality): String = localizedString(
    when (quality) {
        MoveQuality.BEST -> Res.string.analysis_quality_best
        MoveQuality.GOOD -> Res.string.analysis_quality_good
        MoveQuality.INACCURACY -> Res.string.analysis_quality_inaccuracy
        MoveQuality.MISTAKE -> Res.string.analysis_quality_mistake
        MoveQuality.BLUNDER -> Res.string.analysis_quality_blunder
    },
)
