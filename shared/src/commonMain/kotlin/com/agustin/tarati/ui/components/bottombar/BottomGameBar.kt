package com.agustin.tarati.ui.components.bottombar


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.agustin.tarati.core.domain.game.play.StableHistoryList
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.analysis_title
import com.agustin.tarati.shared.generated.resources.close
import com.agustin.tarati.shared.generated.resources.jump_to_current_position
import com.agustin.tarati.shared.generated.resources.move_controls
import com.agustin.tarati.shared.generated.resources.move_history
import com.agustin.tarati.shared.generated.resources.redo
import com.agustin.tarati.shared.generated.resources.undo
import com.agustin.tarati.ui.components.TooltipFilledTonalIconButton
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.movelist.MoveHistoryList
import com.agustin.tarati.ui.theme.TaratiIcons

/**
 * Barra de controles flotante de la pantalla de juego.
 *
 * ## Flujo de interacción
 * ```
 *  Colapsado:
 *                               [＋ FAB]
 *
 *  Expandido (el FAB se "extiende" hacia la izquierda):
 *  ┌───────────────────────────────────┐
 *  │  ◄ UNDO   REDO ►   HIST   ✕       │
 *  └───────────────────────────────────┘
 *
 *  Con historial abierto (panel emerge hacia arriba):
 *  ┌───────────────────────────────────┐
 *  │  1. A1→C1     C7→C8               │
 *  │  2. ...                           │
 *  └───────────────────────────────────┘
 *  ┌───────────────────────────────────┐
 *  │  ◄ UNDO   REDO ►   HIST   ✕       │
 *  └───────────────────────────────────┘
 * ```
 *
 * ## Cierre al tocar fuera
 * Cuando el strip está expandido, se renderiza un `Box` transparente detrás
 * del `Column` que intercepta cualquier toque fuera del strip y lo colapsa.
 * `indication = null` garantiza que el backdrop no muestre ripple ni ningún
 * efecto visual al ser presionado.
 *
 * @param history         Historial estable de movimientos de la partida actual.
 * @param moveIndex       Índice 0-based del movimiento actualmente visualizado.
 * @param onUndo          Retrocede un movimiento (incluye sincronización TaratiAI).
 * @param onRedo          Avanza un movimiento (incluye sincronización TaratiAI).
 * @param onMoveToCurrent Salta al estado más reciente de la partida.
 * @param modifier        Debe incluir `fillMaxSize()` para posicionamiento correcto.
 */
@Composable
fun BottomGameBar(
    history: StableHistoryList,
    moveIndex: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveToCurrent: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    onHistoryOpenChange: (Boolean) -> Unit = {},
    onFabExpandedChange: (Boolean) -> Unit = {},
    onMoveClick: ((moveIndex: Int) -> Unit)? = null,
    initialHistoryExpanded: Boolean = false,
    /** `false` grisa undo/redo/saltar (online en curso / espectador); activo offline o online-terminada. */
    navigationEnabled: Boolean = true,
    /** Acción del botón de análisis en el strip. Si es `null`, el botón no se muestra. */
    onAnalysis: (() -> Unit)? = null,
    onlineContent: @Composable (() -> Unit)? = null,
) {
    val moves = history.getMoves()
    val canUndo = navigationEnabled && moveIndex >= 0
    val canRedo = navigationEnabled && moveIndex < moves.size - 1

    // initialHistoryExpanded permite a los previews de Play Store mostrar el panel
    // abierto directamente sin afectar el comportamiento de producción (default false).
    var isExpanded by rememberSaveable { mutableStateOf(initialHistoryExpanded) }
    var isHistoryOpen by rememberSaveable { mutableStateOf(initialHistoryExpanded) }

    // Reset al iniciar nueva partida.
    LaunchedEffect(moves.isEmpty()) {
        if (moves.isEmpty()) {
            isExpanded = false
            isHistoryOpen = false
            onHistoryOpenChange(false)
            onFabExpandedChange(false)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {

        // ── Backdrop transparente ─────────────────────────────────────────────
        // Primer hijo del Box: queda detrás del Column en Z-order.
        // Solo existe cuando el strip está expandido; intercepta taps fuera de
        // él y colapsa el panel sin producir ningún efecto visual (indication = null).
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
                // heightIn(max) gives the Column a bounded height so that
                // weight(1f) on the history panel works correctly.
                .heightIn(max = maxHeight - 8.dp)
                .padding(bottom = 8.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Panel de historial (animación vertical, compatible con ColumnScope) ──
            // weight(1f, fill = false): FabOrStrip is measured first at its
            // natural height; the history panel receives the remaining space
            // as its max, so it can never grow enough to push the FAB off screen.
            AnimatedVisibility(
                modifier = Modifier.weight(1f, fill = false),
                visible = isHistoryOpen && isExpanded,
                enter = expandVertically(
                    animationSpec = tween(250),
                    expandFrom = Alignment.Bottom,
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(200),
                    shrinkTowards = Alignment.Bottom,
                ) + fadeOut(tween(150)),
            ) {
                MoveHistoryPanel(
                    modifier = if (isLandscape) {
                        Modifier.widthIn(max = 320.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                    },
                    history = history,
                    moveIndex = moveIndex,
                    canJumpToCurrent = canRedo,
                    onMoveToCurrent = {
                        onMoveToCurrent()
                        isHistoryOpen = false
                    },
                    onMoveClick = if (navigationEnabled && onMoveClick != null) {
                        { idx -> onMoveClick(idx); isHistoryOpen = false }
                    } else null,
                )
            }

            // ── FAB ↔ strip horizontal ────────────────────────────────────────
            // onlineContent fills available space with weight(1f); FAB sits fixed
            // on the right and is always visible. When the strip expands it overlaps
            // onlineContent naturally since the strip is wider.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isExpanded && onlineContent != null) {
                    Box(modifier = Modifier.weight(1f)) {
                        onlineContent()
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
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
                    onAnalysis = onAnalysis,
                )
            }
        }
    }
}

// ── FAB ↔ strip ───────────────────────────────────────────────────────────────

/**
 * Alterna entre el [FloatingActionButton] colapsado y el [ControlStrip] expandido
 * usando transiciones horizontales.
 *
 * Este composable existe exclusivamente para **romper la cadena de receptor
 * implícito** de `ColumnScope`: al declararlo fuera del bloque `Column { }`,
 * Kotlin resuelve la sobrecarga genérica de `AnimatedVisibility` que acepta
 * `expandHorizontally` / `shrinkHorizontally`, en lugar de la sobrecarga
 * `ColumnScope.AnimatedVisibility` que solo acepta transiciones verticales.
 *
 * La animación ancla ambas transiciones en `Alignment.End` (borde derecho),
 * produciendo la ilusión de que el FAB "se extiende" hacia la izquierda
 * al abrirse y "se encoge" de vuelta al cerrarse.
 */
@Composable
fun FabOrStrip(
    isExpanded: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isHistoryOpen: Boolean,
    onFabClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onHistoryToggle: () -> Unit,
    onClose: () -> Unit,
    onAnalysis: (() -> Unit)? = null,
) {
    Box(contentAlignment = Alignment.CenterEnd) {

        // FAB colapsado — sale hacia la derecha al expandir.
        AnimatedVisibility(
            visible = !isExpanded,
            enter = expandHorizontally(
                animationSpec = tween(200),
                expandFrom = Alignment.End,
            ) + fadeIn(tween(150)),
            exit = shrinkHorizontally(
                animationSpec = tween(200),
                shrinkTowards = Alignment.End,
            ) + fadeOut(tween(150)),
        ) {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = TaratiIcons.Add,
                    contentDescription = localizedString(Res.string.move_controls),
                )
            }
        }

        // Strip de controles — entra desde la derecha expandiéndose a la izquierda.
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(
                animationSpec = tween(250),
                expandFrom = Alignment.End,
            ) + fadeIn(tween(200)),
            exit = shrinkHorizontally(
                animationSpec = tween(200),
                shrinkTowards = Alignment.End,
            ) + fadeOut(tween(150)),
        ) {
            ControlStrip(
                canUndo = canUndo,
                canRedo = canRedo,
                isHistoryOpen = isHistoryOpen,
                onUndoClick = onUndoClick,
                onRedoClick = onRedoClick,
                onHistoryToggle = onHistoryToggle,
                onClose = onClose,
                onAnalysis = onAnalysis,
            )
        }
    }
}

// ── Strip de controles ────────────────────────────────────────────────────────

/**
 * Pill horizontal con los controles de la partida:
 * `◄ UNDO | REDO ► | HIST | ✕`
 */
@Composable
fun ControlStrip(
    canUndo: Boolean,
    canRedo: Boolean,
    isHistoryOpen: Boolean,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onHistoryToggle: () -> Unit,
    onClose: () -> Unit,
    onAnalysis: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // UNDO
            OutlinedButton(
                onClick = onUndoClick,
                enabled = canUndo,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = TaratiIcons.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                LocalizedText(
                    resource = Res.string.undo,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            // REDO
            OutlinedButton(
                onClick = onRedoClick,
                enabled = canRedo,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                LocalizedText(
                    resource = Res.string.redo,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = TaratiIcons.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Toggle historial
            TooltipFilledTonalIconButton(
                tooltip = localizedString(Res.string.move_history),
                onClick = onHistoryToggle,
            ) {
                Icon(
                    imageVector = if (isHistoryOpen) TaratiIcons.KeyboardArrowDown
                    else TaratiIcons.MenuBook,
                    contentDescription = localizedString(Res.string.move_history),
                )
            }

            // Toggle análisis
            if (onAnalysis != null) {
                TooltipFilledTonalIconButton(
                    tooltip = localizedString(Res.string.analysis_title),
                    onClick = onAnalysis,
                ) {
                    Icon(
                        imageVector = TaratiIcons.Leaderboard,
                        contentDescription = localizedString(Res.string.analysis_title),
                    )
                }
            }

            // Cerrar (colapsar volviendo al FAB)
            TooltipIconButton(
                tooltip = localizedString(Res.string.close),
                onClick = onClose,
            ) {
                Icon(
                    imageVector = TaratiIcons.Close,
                    contentDescription = localizedString(Res.string.close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Panel de historial ────────────────────────────────────────────────────────

/**
 * Tarjeta flotante con la lista completa de movimientos de la partida.
 * Incluye auto-scroll al movimiento activo y acceso rápido al estado presente.
 */
@Composable
fun MoveHistoryPanel(
    modifier: Modifier = Modifier,
    history: StableHistoryList,
    moveIndex: Int,
    canJumpToCurrent: Boolean,
    onMoveToCurrent: () -> Unit,
    onMoveClick: ((moveIndex: Int) -> Unit)? = null,
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

            if (history.getMoves().isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    MoveHistoryList(
                        history = history,
                        moveIndex = moveIndex,
                        onMoveClick = onMoveClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}