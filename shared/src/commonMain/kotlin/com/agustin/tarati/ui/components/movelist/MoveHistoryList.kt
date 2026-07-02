package com.agustin.tarati.ui.components.movelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.core.domain.game.play.TurnGroup
import com.agustin.tarati.core.domain.game.play.groupByTurns
import com.agustin.tarati.core.domain.game.play.moveIndexToGroupIndex
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.a_board_game_by_george_spencer_brown
import com.agustin.tarati.shared.generated.resources.board_position_copied_to_clipboard
import com.agustin.tarati.shared.generated.resources.copy_position
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Lista de historial de movimientos centralizada, reutilizada por el Sidebar,
 * el FAB de la pantalla de juego y la pantalla de detalle de partida.
 *
 * ## Responsabilidad
 * Renderiza una cabecera con la notación de la posición actual ([MoveHistoryHeader])
 * seguida de la [LazyColumn] con filas de movimientos, y gestiona el auto-scroll
 * al movimiento activo. No incluye container (Card / Surface) ni botones de
 * navegación — cada caller los agrega según su contexto.
 *
 * ## Cabecera de notación
 * La notación de posición y la leyenda de George Spencer-Brown viven aquí (antes
 * estaban en el indicador de turno giratorio), de modo que aparecen en todas las
 * listas de movimientos de la app. La notación se deriva del estado resultante del
 * movimiento en [moveIndex]; en la posición inicial (`moveIndex < 0`) se usa la
 * posición estándar. La cabecera conserva el botón de copiado (al portapapeles, con
 * toast) y alterna a la leyenda GSB al tocar la notación.
 *
 * ## Navegación por clic
 * [onMoveClick] recibe el índice plano del movimiento seleccionado (base-0,
 * igual al índice en [StableHistoryList]). Pasar `null` desactiva el clic
 * y es el modo apropiado para contextos de solo lectura (detalle de partida).
 *
 * ## Índice plano vs. índice de fila
 * El historial se muestra agrupado en pares de turno (blancas | negras). El
 * índice plano se calcula a partir del índice de fila y la columna (0 = blancas,
 * 1 = negras). [TurnGroup] puede tener 1 o 2 movimientos internos (promoción
 * forzada + movimiento posterior), pero visualmente ocupa una sola celda.
 *
 * @param history       Historial estable de movimientos de la partida.
 * @param moveIndex     Índice plano del movimiento actualmente visualizado (-1 = inicio).
 * @param onMoveClick   Callback invocado con el índice plano al hacer clic en una celda.
 *                      `null` = modo solo lectura (sin interacción).
 * @param modifier      Modificador aplicado al contenedor ([Column]).
 */
@Composable
fun MoveHistoryList(
    modifier: Modifier = Modifier,
    history: StableHistoryList,
    moveIndex: Int,
    onMoveClick: ((moveIndex: Int) -> Unit)? = null,
) {
    // Memoized to avoid O(N) recomputation on every recomposition.
    val moves = remember(history) { history.getMoves() }
    val groups = remember(history) { moves.groupByTurns().chunked(2) }
    val currentGroupIndex = remember(history, moveIndex) { moves.moveIndexToGroupIndex(moveIndex) }

    // Pre-compute flat start indices per row to avoid O(N²) work inside itemsIndexed.
    val flatStartIndices = remember(groups) {
        groups.runningFold(0) { acc, pair -> acc + pair.sumOf { it.moves.size } }
    }

    // Notación de la posición actualmente visualizada. Se deriva del estado
    // resultante del movimiento en moveIndex; en la posición inicial se usa la
    // posición estándar de arranque.
    val positionNotation = remember(history, moveIndex) {
        if (moveIndex in 0 until history.size) {
            history.getGameState(moveIndex).toPositionNotation()
        } else {
            initialGameState().toPositionNotation()
        }
    }

    val listState = rememberLazyListState()

    // Track previous groups.size to distinguish "new move added" from "user navigated".
    // Uses a plain array ref so writes don't trigger recomposition.
    val prevGroupsSizeRef = remember { intArrayOf(0) }

    LaunchedEffect(currentGroupIndex, groups.size) {
        if (groups.isEmpty()) return@LaunchedEffect
        val targetRow = (currentGroupIndex / 2)
            .coerceAtLeast(0)
            .coerceAtMost(groups.lastIndex)
        // Instant scroll when a new move was added (AI or player): avoids launching
        // a frame-by-frame scroll animation on every move during rapid AI play.
        // Animated scroll when the user navigates through existing history.
        val newMoveAdded = groups.size > prevGroupsSizeRef[0]
        prevGroupsSizeRef[0] = groups.size
        if (newMoveAdded) {
            listState.scrollToItem(targetRow)
        } else {
            listState.animateScrollToItem(targetRow)
        }
    }

    Column(modifier = modifier) {
        MoveHistoryHeader(
            positionNotation = positionNotation,
            moveIndex = moveIndex,
        )

        LazyColumn(
            state = listState,
            // fill = false: envuelve el contenido cuando hay pocas jugadas y se
            // limita (con scroll) cuando excede el espacio disponible, preservando
            // el comportamiento previo en todos los contenedores (Card acotada,
            // heightIn, fillMaxSize).
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            itemsIndexed(groups) { rowIndex, pair ->
                val flatIndexWhite = flatStartIndices[rowIndex]
                val white = pair.firstOrNull()
                val black = pair.getOrNull(1)
                val flatIndexBlack = flatIndexWhite + (white?.moves?.size ?: 0)

                MoveHistoryRow(
                    rowNumber = rowIndex + 1,
                    white = white,
                    black = black,
                    currentGroupIndex = currentGroupIndex,
                    rowIndex = rowIndex,
                    flatIndexWhite = flatIndexWhite,
                    flatIndexBlack = flatIndexBlack,
                    onMoveClick = onMoveClick,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                if (rowIndex < groups.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}

// ── Cabecera de notación ───────────────────────────────────────────────────────

/**
 * Cabecera con la notación de la posición actual y la leyenda de George
 * Spencer-Brown. Al tocar la notación alterna a la leyenda y viceversa. Mantiene
 * el botón de copiado, que envía la notación al portapapeles y muestra un toast.
 *
 * El copiado es autónomo (inyecta [GameClipboardHelper] y [UIMessageBus]) para
 * funcionar de forma idéntica en cualquier lista de movimientos de la app.
 */
@Composable
private fun MoveHistoryHeader(
    positionNotation: String,
    moveIndex: Int,
    modifier: Modifier = Modifier,
) {
    // Move number: moveIndex is 0-based index of the last recorded entry,
    // so the current move number is moveIndex + 1 (or 0 before any moves).
    val moveNumber = (moveIndex + 1).coerceAtLeast(0)

    var showGameState by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val copiedMessage = localizedString(Res.string.board_position_copied_to_clipboard)

    // koinInject requiere un grafo Koin activo; en Compose Preview no lo hay, así que
    // se omite la inyección (el botón de copiado queda inerte solo en previews).
    val inPreview = LocalInspectionMode.current
    val clipboardHelper = if (inPreview) null else koinInject<GameClipboardHelper>()
    val bus = if (inPreview) null else koinInject<UIMessageBus>()

    // Fondo tenue para distinguir la cabecera de notación de las filas de movimientos.
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showGameState) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "$moveNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = positionNotation,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showGameState = false },
                )
                Spacer(modifier = Modifier.width(8.dp))
                TooltipIconButton(
                    tooltip = localizedString(Res.string.copy_position),
                    onClick = {
                        val helper = clipboardHelper ?: return@TooltipIconButton
                        scope.launch {
                            val copied = helper.copyBoardPosition(positionNotation)
                            if (copied) {
                                bus?.toast(UIMessage.Toast(message = copiedMessage))
                            }
                        }
                    },
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        imageVector = TaratiIcons.ContentCopy,
                        contentDescription = localizedString(Res.string.copy_position),
                    )
                }
            }
        } else {
            Text(
                text = localizedString(Res.string.a_board_game_by_george_spencer_brown),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .clickable { showGameState = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }
}

// ── Row y celda ──────────────────────────────────────────────────────────────

@Composable
private fun MoveHistoryRow(
    rowNumber: Int,
    white: TurnGroup?,
    black: TurnGroup?,
    currentGroupIndex: Int,
    rowIndex: Int,
    flatIndexWhite: Int,
    flatIndexBlack: Int,
    onMoveClick: ((moveIndex: Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rowNumber.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        MoveCell(
            group = white,
            isCurrent = currentGroupIndex == rowIndex * 2,
            onClick = if (white != null && onMoveClick != null) {
                { onMoveClick(flatIndexWhite) }
            } else null,
            modifier = Modifier.weight(1f),
        )
        MoveCell(
            group = black,
            isCurrent = currentGroupIndex == rowIndex * 2 + 1,
            onClick = if (black != null && onMoveClick != null) {
                { onMoveClick(flatIndexBlack) }
            } else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MoveCell(
    group: TurnGroup?,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (group != null) {
            Text(
                text = group.notation,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
