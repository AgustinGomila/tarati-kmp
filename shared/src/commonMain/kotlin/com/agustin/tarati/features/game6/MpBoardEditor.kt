package com.agustin.tarati.features.game6

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.cancel
import com.agustin.tarati.shared.generated.resources.clear_board
import com.agustin.tarati.shared.generated.resources.game6_edit_reset
import com.agustin.tarati.shared.generated.resources.piece
import com.agustin.tarati.shared.generated.resources.start
import com.agustin.tarati.shared.generated.resources.start_game
import com.agustin.tarati.shared.generated.resources.turn
import com.agustin.tarati.ui.theme.TaratiIcons

/**
 * Overlay de edición de posiciones del tablero `25` (D14), análogo al `EditControls` de single:
 * en disposiciones **anchas** (landscape/Expanded, [isLandscape]) los controles van a los **lados**
 * (selectores a la izquierda, acciones a la derecha) para no tapar el tablero; en **portrait** van
 * **arriba y abajo**. Se superpone al área de tablero mientras [MpLocalGameViewModel.isEditing] está
 * activo: los taps sobre el tablero colocan/quitan piezas (lo maneja el ViewModel).
 *
 * [canStart] refleja la validación libre (≥2 colores con piezas); habilita "Iniciar".
 */
@Composable
fun MpEditControls(
    state: MpGameState,
    editColor: PlayerColor,
    canStart: Boolean,
    isLandscape: Boolean,
    onCycleColor: () -> Unit,
    onCycleTurn: () -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectorsAlign = if (isLandscape) Alignment.CenterStart else Alignment.TopCenter
    val actionsAlign = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter

    Box(modifier = modifier.fillMaxSize()) {
        // Selectores: color a colocar + asiento en turno.
        Box(modifier = Modifier.align(selectorsAlign).padding(16.dp)) {
            EditGroup(isLandscape) {
                ColorSelector(
                    label = localizedString(Res.string.piece),
                    color = editColor,
                    onClick = onCycleColor,
                )
                ColorSelector(
                    label = localizedString(Res.string.turn),
                    color = state.currentSeat.color,
                    onClick = onCycleTurn,
                )
            }
        }

        // Acciones: cancelar / limpiar / reiniciar / iniciar.
        Box(modifier = Modifier.align(actionsAlign).padding(16.dp)) {
            EditGroup(isLandscape) {
                ActionFab(TaratiIcons.Close, localizedString(Res.string.cancel), onCancel)
                ActionFab(TaratiIcons.Delete, localizedString(Res.string.clear_board), onClear)
                ActionFab(TaratiIcons.Replay, localizedString(Res.string.game6_edit_reset), onReset)
                StartButton(canStart, onStart)
            }
        }
    }
}

/** Agrupa los controles en **columna** (landscape → lados) o en **fila** (portrait → arriba/abajo). */
@Composable
private fun EditGroup(isLandscape: Boolean, content: @Composable () -> Unit) {
    if (isLandscape) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun StartButton(canStart: Boolean, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        enabled = canStart,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (canStart) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            },
        ),
    ) {
        Text(localizedString(Res.string.start_game))
        Spacer(Modifier.width(8.dp))
        Icon(TaratiIcons.PlayArrow, contentDescription = localizedString(Res.string.start))
    }
}

/** Botón redondo (FAB) con un disco del [color] y una etiqueta debajo; al tocarlo, cicla. */
@Composable
private fun ColorSelector(
    label: String,
    color: PlayerColor,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            EditorColorDisc(color)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionFab(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(icon, contentDescription = tooltip, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Disco del color de jugador (relleno + borde de la paleta multijugador). */
@Composable
private fun EditorColorDisc(color: PlayerColor) {
    val fill = PlayerPalette.fill(color)
    val border = PlayerPalette.border(color)
    Canvas(Modifier.size(24.dp)) {
        val r = size.minDimension / 2f
        drawCircle(fill, radius = r, center = Offset(r, r))
        drawCircle(border, radius = r, center = Offset(r, r), style = Stroke(width = r * 0.18f))
    }
}
