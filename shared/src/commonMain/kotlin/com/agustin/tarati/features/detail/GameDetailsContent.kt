@file:OptIn(ExperimentalMaterial3Api::class)

package com.agustin.tarati.features.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.data.database.dto.GameDto
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.data.database.dto.toHistoryEntries
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.features.analysis.GameAnalysisSection
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.move_history
import com.agustin.tarati.shared.generated.resources.toggle_move_history
import com.agustin.tarati.shared.generated.resources.total_moves
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.movelist.MoveHistoryList
import com.agustin.tarati.ui.theme.TaratiIcons

/** Duration for expand/collapse panel transitions (ms). */
private const val EXPAND_DURATION_MS = 300

/** Duration for the fade associated with expand/collapse transitions (ms). */
private const val FADE_DURATION_MS = 250

@Composable
fun GameDetailsContent(
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    matchDto: MatchDto,
    onMatchDtoChange: (MatchDto) -> Unit = {},
) {
    BoxWithConstraints {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            // En landscape, siempre usar diseño horizontal (tanto en edición como en visualización)
            LandscapeLayout(
                modifier = modifier,
                isEditing = isEditing,
                matchDto = matchDto,
                onMatchDtoChange = onMatchDtoChange,
            )
        } else {
            PortraitLayout(
                modifier = modifier,
                isEditing = isEditing,
                matchDto = matchDto,
                onMatchDtoChange = onMatchDtoChange,
            )
        }
    }
}

@Composable
private fun LandscapeLayout(
    modifier: Modifier,
    isEditing: Boolean,
    matchDto: MatchDto,
    onMatchDtoChange: (MatchDto) -> Unit,
) {
    val scrollState = rememberScrollState()
    var currentMoveIndex by remember(
        matchDto.game.boardPosition,
        matchDto.game.moveHistory.size,
    ) { mutableIntStateOf(matchDto.game.moveHistory.lastIndex) }

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Columna izquierda: Información del juego con scroll para edición
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
            verticalArrangement = if (isEditing) Arrangement.Top else Arrangement.Center,
        ) {
            GameInfoCard(
                modifier = Modifier.fillMaxWidth(),
                isEditing = isEditing,
                header = matchDto.header,
            ) { newHeader ->
                onMatchDtoChange(matchDto.copy(header = newHeader))
            }

            if (!isEditing) {
                GameAnalysisSection(
                    gameDto = matchDto.game,
                    gameId = matchDto.id ?: matchDto.game.hashCode().toString(),
                    currentMoveIndex = currentMoveIndex,
                    onMoveClick = { currentMoveIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        }

        if (!isEditing) {
            // Centro: Tablero (solo en modo visualización)
            CreateCardBoard(
                modifier =
                    Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                matchDto = matchDto,
                currentMoveIndex = currentMoveIndex,
                onMoveIndexChange = { currentMoveIndex = it },
            )

            // Derecha: Historial de movimientos (solo en modo visualización)
            MoveHistoryCard(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                gameDto = matchDto.game,
                currentMoveIndex = currentMoveIndex,
                onMoveClick = { currentMoveIndex = it },
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    modifier: Modifier,
    isEditing: Boolean,
    matchDto: MatchDto,
    onMatchDtoChange: (MatchDto) -> Unit,
) {
    val scrollState = rememberScrollState()
    var isInfoExpanded by remember { mutableStateOf(false) }
    val inPreview = LocalInspectionMode.current
    var isMoveHistoryExpanded by remember { mutableStateOf(inPreview) }
    var currentMoveIndex by remember(
        matchDto.game.boardPosition,
        matchDto.game.moveHistory.size,
    ) { mutableIntStateOf(matchDto.game.moveHistory.lastIndex) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize(),
    ) {
        val availableHeight = maxHeight

        // Altura mínima del tablero: el tablero Card usa fillMaxHeight(0.9f) * aspectRatio(0.7f),
        // lo que significa que necesita al menos maxWidth * 0.75f de altura para mostrar un
        // tablero usable (~0.7f de ancho disponible × 0.9f de la altura del contenedor).
        val boardMinHeight = maxWidth * 0.75f

        // Altura estimada de GameInfoCard según su estado:
        //   Colapsado: padding(vertical=16dp)×2 + titleMedium (~20dp) ≈ 52dp
        //   Expandido: header + N filas de datos (~32dp c/u) + padding ≈ 160dp
        val infoCardHeight = if (isInfoExpanded) 160.dp else 52.dp

        // Gaps del Column (2 × 16dp) + padding inferior del panel (80dp).
        val fixedOverhead = 16.dp * 2 + 80.dp

        // Espacio disponible para la lista una vez garantizado el mínimo del tablero.
        val maxListHeight = (availableHeight
                - boardMinHeight
                - infoCardHeight
                - fixedOverhead)
            .coerceIn(
                // Mínimo: al menos 2 filas (~30dp/fila + 20dp padding ≈ 80dp)
                minimumValue = 80.dp,
                // Máximo: no más de 240dp (comportamiento original en pantallas grandes)
                maximumValue = 240.dp,
            )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GameInfoCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                isEditing = isEditing,
                header = matchDto.header,
                onExpandedChange = { isInfoExpanded = it },
            ) { newHeader ->
                onMatchDtoChange(matchDto.copy(header = newHeader))
            }

            if (!isEditing) {
                // Tablero ocupa la mayor parte del espacio
                CreateCardBoard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    matchDto = matchDto,
                    currentMoveIndex = currentMoveIndex,
                    onMoveIndexChange = { currentMoveIndex = it },
                    topPanelExpanded = isInfoExpanded,
                    bottomPanelExpanded = isMoveHistoryExpanded,
                )

                GameAnalysisSection(
                    gameDto = matchDto.game,
                    gameId = matchDto.id ?: matchDto.game.hashCode().toString(),
                    currentMoveIndex = currentMoveIndex,
                    onMoveClick = { currentMoveIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )

                // Movimientos colapsable en portrait con padding para evitar FAB
                CollapsibleMoveHistoryCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    gameDto = matchDto.game,
                    isExpanded = isMoveHistoryExpanded,
                    currentMoveIndex = currentMoveIndex,
                    onExpandedChange = { isMoveHistoryExpanded = it },
                    onMoveClick = { currentMoveIndex = it },
                    maxListHeight = maxListHeight,
                )
            }
        }
    }
}

/**
 * Panel inferior colapsable con la lista de movimientos de la partida.
 *
 * La apertura y el cierre del contenido se realizan con [AnimatedVisibility]
 * usando [expandVertically] + [fadeIn] / [shrinkVertically] + [fadeOut], lo que
 * evita el cambio brusco producido por el `if` simple anterior.
 *
 * El ícono del botón rota 180° con [animateFloatAsState] en lugar de hacer
 * swap entre [Icons.Filled.ExpandMore] e [Icons.Filled.ExpandLess], produciendo
 * una transición continua y coherente con la animación del contenido.
 *
 * @param gameDto         DTO de la partida cuyo historial se muestra.
 * @param isExpanded      Si el panel de movimientos está actualmente abierto.
 * @param onExpandedChange Callback invocado al pulsar el botón de expandir/colapsar.
 */
@Composable
fun CollapsibleMoveHistoryCard(
    modifier: Modifier = Modifier,
    gameDto: GameDto,
    isExpanded: Boolean = false,
    currentMoveIndex: Int = -1,
    onExpandedChange: (Boolean) -> Unit = {},
    onMoveClick: ((moveIndex: Int) -> Unit)? = null,
    /** Maximum height for the scrollable move list. Calculated by [PortraitLayout]
     *  based on available screen space to guarantee the board always has room. */
    maxListHeight: Dp = 240.dp,
) {
    val moves = gameDto.moveHistory

    // Ángulo de rotación del chevron: 0° = cerrado (ExpandMore), 180° = abierto (ExpandLess).
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = EXPAND_DURATION_MS, easing = FastOutSlowInEasing),
        label = "move_history_chevron_rotation",
    )

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // ── Header (siempre visible) ──────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = localizedString(Res.string.move_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = localizedString(Res.string.total_moves, moves.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )

                    TooltipIconButton(
                        tooltip = localizedString(Res.string.toggle_move_history),
                        onClick = { onExpandedChange(!isExpanded) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = TaratiIcons.ExpandMore,
                            contentDescription = localizedString(Res.string.toggle_move_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            }

            // ── Contenido expandible con transición animada ───────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(
                        durationMillis = EXPAND_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                    expandFrom = Alignment.Top,
                ) + fadeIn(
                    animationSpec = tween(durationMillis = FADE_DURATION_MS),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(
                        durationMillis = EXPAND_DURATION_MS - 50,
                        easing = FastOutSlowInEasing,
                    ),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(
                    animationSpec = tween(durationMillis = FADE_DURATION_MS - 50),
                ),
            ) {
                val history = remember(gameDto) {
                    StableHistoryList(gameDto.toHistoryEntries())
                }

                MoveHistoryList(
                    history = history,
                    moveIndex = currentMoveIndex,
                    onMoveClick = onMoveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                )
            }
        }
    }
}