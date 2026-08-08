package com.agustin.tarati.features.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.data.database.dto.GameDto
import com.agustin.tarati.core.domain.analysis.AnalysisCacheRepository
import com.agustin.tarati.core.domain.analysis.AnalysisRunner
import com.agustin.tarati.core.domain.analysis.GameAnalysis
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.analysis_analyze_game
import com.agustin.tarati.shared.generated.resources.analysis_analyzing
import com.agustin.tarati.shared.generated.resources.analysis_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

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

            is AnalysisUi.Ready -> EvalGraph(
                series = state.analysis.series.map { it.winProbWhite },
                currentIndex = currentMoveIndex + 1,
                onSelectIndex = { pointIndex -> onMoveClick((pointIndex - 1).coerceIn(0, moves.lastIndex)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
        }
    }
}
