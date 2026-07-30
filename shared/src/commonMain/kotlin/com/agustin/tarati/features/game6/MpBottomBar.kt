package com.agustin.tarati.features.game6

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.jump_to_current_position
import com.agustin.tarati.shared.generated.resources.move_history
import com.agustin.tarati.ui.components.bottombar.FabOrStrip

/**
 * FAB flotante de controles para el juego multijugador **local** en pantallas compactas (D12), análogo
 * al `BottomGameBar` de single: colapsado muestra un `＋`; expandido, la pastilla `◄ UNDO · REDO ► ·
 * HIST · ✕`; con el historial abierto, un panel con la grilla de movimientos por columnas.
 *
 * Reutiliza el núcleo interactivo de single (`FabOrStrip`/`ControlStrip`, ya genéricos) y aporta el
 * contenedor (backdrop + posicionamiento + panel de historial) con la lista MP ([MpMoveGrid]).
 */
@Composable
fun MpBottomBar(
    moves: List<PlayerMove>,
    seats: List<Seat>,
    moveIndex: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveToCurrent: () -> Unit,
    onMoveToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** `false` grisa undo/redo/saltar (online en curso); activo offline u online-terminada. */
    navigationEnabled: Boolean = true,
    // Notifican al host el estado del FAB / panel de historial → alimentan el cabeceo del tablero.
    onFabExpandedChange: (Boolean) -> Unit = {},
    onHistoryOpenChange: (Boolean) -> Unit = {},
) {
    val canUndo = navigationEnabled && moveIndex >= 0
    val canRedo = navigationEnabled && moveIndex < moves.size - 1

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var isHistoryOpen by rememberSaveable { mutableStateOf(false) }

    // Reset al iniciar nueva partida (historial vacío).
    LaunchedEffect(moves.isEmpty()) {
        if (moves.isEmpty()) {
            isExpanded = false
            isHistoryOpen = false
            onHistoryOpenChange(false)
            onFabExpandedChange(false)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {

        // Cosmética de single (BottomGameBar): en área ancha (landscape) el panel se acota a 320dp y
        // queda alineado a la derecha; en área angosta (portrait) ocupa el ancho con un margen inicial.
        val panelModifier = if (maxWidth > maxHeight) {
            Modifier.widthIn(max = 320.dp)
        } else {
            Modifier.fillMaxWidth().padding(start = 16.dp)
        }

        // Backdrop transparente: cierra el strip al tocar fuera, sin efecto visual.
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        isExpanded = false
                        isHistoryOpen = false
                        onHistoryOpenChange(false)
                        onFabExpandedChange(false)
                    },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth()
                .heightIn(max = maxHeight - 8.dp)
                .padding(bottom = 8.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                modifier = Modifier.weight(1f, fill = false),
                visible = isHistoryOpen && isExpanded,
                enter = expandVertically(tween(250), expandFrom = Alignment.Bottom) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom) + fadeOut(tween(150)),
            ) {
                MpMoveHistoryPanel(
                    modifier = panelModifier,
                    moves = moves,
                    seats = seats,
                    moveIndex = moveIndex,
                    canJumpToCurrent = canRedo,
                    onMoveToCurrent = {
                        onMoveToCurrent()
                        isHistoryOpen = false
                    },
                    onMoveToIndex = if (navigationEnabled) {
                        { idx -> onMoveToIndex(idx); isHistoryOpen = false }
                    } else null,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                FabOrStrip(
                    isExpanded = isExpanded,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    isHistoryOpen = isHistoryOpen,
                    onFabClick = {
                        isExpanded = true
                        onFabExpandedChange(true)
                    },
                    onUndoClick = onUndo,
                    onRedoClick = onRedo,
                    onHistoryToggle = {
                        val next = !isHistoryOpen
                        isHistoryOpen = next
                        onHistoryOpenChange(isExpanded && next)
                    },
                    onClose = {
                        isExpanded = false
                        isHistoryOpen = false
                        onHistoryOpenChange(false)
                        onFabExpandedChange(false)
                    },
                )
            }
        }
    }
}

/**
 * Tarjeta flotante con la grilla de movimientos MP + acceso rápido a la posición actual.
 *
 * [onMoveToIndex] `null` la vuelve **read-only** (celdas no clickeables): úsalo en la partida online,
 * donde el servidor es la autoridad del estado en vivo y la lista es solo para consulta.
 */
@Composable
private fun MpMoveHistoryPanel(
    modifier: Modifier,
    moves: List<PlayerMove>,
    seats: List<Seat>,
    moveIndex: Int,
    canJumpToCurrent: Boolean,
    onMoveToCurrent: () -> Unit,
    onMoveToIndex: ((Int) -> Unit)?,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = localizedString(Res.string.move_history).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                if (canJumpToCurrent) {
                    TextButton(
                        onClick = onMoveToCurrent,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        LocalizedText(
                            resource = Res.string.jump_to_current_position,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (moves.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "–",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    MpMoveGrid(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        seats = seats,
                        history = moves,
                        currentPly = moveIndex,
                        onCellClick = onMoveToIndex,
                    )
                }
            }
        }
    }
}

