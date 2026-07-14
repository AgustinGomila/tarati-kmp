package com.agustin.tarati.features.game6

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpMoveCell
import com.agustin.tarati.core.domain.game6.play.MpMoveList
import com.agustin.tarati.core.domain.game6.play.MpMoveRow
import com.agustin.tarati.core.domain.game6.play.PlayerMove
import com.agustin.tarati.core.domain.game6.play.Seat
import com.agustin.tarati.core.domain.game6.play.toPositionNotation
import com.agustin.tarati.core.domain.game6.rules.MpSetup
import com.agustin.tarati.services.clipboard.GameClipboardHelper
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.notifications.UIMessage
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.a_board_game_by_george_spencer_brown
import com.agustin.tarati.shared.generated.resources.about
import com.agustin.tarati.shared.generated.resources.about_tarati
import com.agustin.tarati.shared.generated.resources.achievements
import com.agustin.tarati.shared.generated.resources.board_position_copied_to_clipboard
import com.agustin.tarati.shared.generated.resources.copy_position
import com.agustin.tarati.shared.generated.resources.edit
import com.agustin.tarati.shared.generated.resources.game6_how_to_play
import com.agustin.tarati.shared.generated.resources.game6_player_n
import com.agustin.tarati.shared.generated.resources.game6_players
import com.agustin.tarati.shared.generated.resources.game6_turn
import com.agustin.tarati.shared.generated.resources.jump_to_current_position
import com.agustin.tarati.shared.generated.resources.move_controls
import com.agustin.tarati.shared.generated.resources.new_game
import com.agustin.tarati.shared.generated.resources.online_lobby
import com.agustin.tarati.shared.generated.resources.player_ai
import com.agustin.tarati.shared.generated.resources.player_human
import com.agustin.tarati.shared.generated.resources.redo
import com.agustin.tarati.shared.generated.resources.rotate_board
import com.agustin.tarati.shared.generated.resources.save_game
import com.agustin.tarati.shared.generated.resources.saved_games
import com.agustin.tarati.shared.generated.resources.settings
import com.agustin.tarati.shared.generated.resources.tarati
import com.agustin.tarati.shared.generated.resources.undo
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
fun MpSidebarHeader(
    onSettings: () -> Unit,
    onAchievements: () -> Unit,
    onHowToPlay: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = localizedString(Res.string.tarati),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onHowToPlay != null) {
                CircleIconButton(TaratiIcons.MenuBook, localizedString(Res.string.game6_how_to_play), onHowToPlay)
            }
            CircleIconButton(TaratiIcons.EmojiEvents, localizedString(Res.string.achievements), onAchievements)
            CircleIconButton(TaratiIcons.Settings, localizedString(Res.string.settings), onSettings)
        }
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────

@Composable
fun MpControls(
    onNewGame: () -> Unit,
    onRotate: () -> Unit,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
) {
    GameModeSwitch(current = GameMode.MULTI)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onNewGame,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
        ) {
            LocalizedText(Res.string.new_game, style = MaterialTheme.typography.bodyMedium)
        }
        // Editor del tablero 25 (D14): toggle resaltado cuando está activo. Rotación (perspectiva 60°).
        SquareToggleIcon(
            icon = TaratiIcons.SquareFoot,
            tooltip = localizedString(Res.string.edit),
            active = isEditing,
            onClick = onToggleEdit,
        )
        RotateIconButton(localizedString(Res.string.rotate_board), onRotate)
    }
}

/** Botón cuadrado de 46dp con estado activo/inactivo (resaltado en primary cuando [active]). */
@Composable
private fun SquareToggleIcon(icon: ImageVector, tooltip: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    TooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
    ) {
        Icon(icon, contentDescription = tooltip, tint = tint)
    }
}

/** Botón de rotación (perspectiva 60°): dibuja una flecha en Canvas, como el de Tarati. */
@Composable
private fun RotateIconButton(tooltip: String, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    TooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Canvas(Modifier.size(20.dp).semantics { contentDescription = tooltip }) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.42f
            val points = listOf(270f, 30f, 150f).map { a ->
                val rad = (a * PI / 180.0).toFloat()
                Offset(cx + r * cos(rad), cy + r * sin(rad))
            }
            drawPath(
                Path().apply {
                    moveTo(points[0].x, points[0].y)
                    lineTo(points[1].x, points[1].y)
                    lineTo(points[2].x, points[2].y)
                    close()
                },
                color = tint,
                style = Fill,
            )
        }
    }
}

// ── Player configuration (2–6 seats) ──────────────────────────────────────────

@Composable
fun MpPlayerConfig(
    config: MpConfig,
    onSetPlayerCount: (Int) -> Unit,
    onSetSeatIsAI: (index: Int, isAI: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = localizedString(Res.string.game6_players),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        for (index in 0 until MpSetup.MAX_PLAYERS) {
            val enabled = index < config.playerCount
            val alwaysOn = index < MpSetup.MIN_PLAYERS
            val isAI = config.seatIsAI.getOrElse(index) { true }
            MpSeatRow(
                index = index,
                enabled = enabled,
                alwaysOn = alwaysOn,
                isAI = isAI,
                onToggleEnabled = { checked ->
                    // Habilitar el asiento index → count = index+1; deshabilitar → count = index.
                    onSetPlayerCount(if (checked) index + 1 else index)
                },
                onToggleType = { onSetSeatIsAI(index, !isAI) },
            )
        }
    }
}

@Composable
private fun MpSeatRow(
    index: Int,
    enabled: Boolean,
    alwaysOn: Boolean,
    isAI: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleType: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Caja de tamaño fijo → el checkbox ocupa el mismo footprint en todas las filas
        // (interactivo o no), manteniendo alineados el disco y la etiqueta.
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = enabled,
                onCheckedChange = if (alwaysOn) null else onToggleEnabled,
                enabled = !alwaysOn,
            )
        }
        MpColorDisc(PlayerColor.fromIndex(index))
        Text(
            text = localizedString(Res.string.game6_player_n, PlayerColor.fromIndex(index).letter.toString()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (enabled) {
            MpPlayerTypeChip(isAI = isAI, onToggle = onToggleType)
        }
    }
}

@Composable
private fun MpPlayerTypeChip(isAI: Boolean, onToggle: () -> Unit) {
    val bgColor = if (isAI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isAI) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (isAI) TaratiIcons.SmartToy else TaratiIcons.Person
    val label = if (isAI) localizedString(Res.string.player_ai) else localizedString(Res.string.player_human)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = FontWeight.Medium)
    }
}

// ── Status + moves ────────────────────────────────────────────────────────────

@Composable
fun MpMoveHistorySection(
    modifier: Modifier,
    state: MpGameState,
    history: List<PlayerMove>,
    onOnlineLobby: () -> Unit,
    nameByColor: Map<PlayerColor, String> = mpPlayerNames(),
    // Navegación del historial (undo/redo) — solo el juego local la provee; online pasa null → no se
    // muestran controles ni se resalta/navega el historial (el servidor es la autoridad).
    moveIndex: Int = -1,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    onMoveToIndex: ((Int) -> Unit)? = null,
    onMoveToCurrent: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MpStatusRow(state, nameByColor)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = localizedString(Res.string.move_controls).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Guardar/biblioteca aún no disponibles en MP local → deshabilitados. Online sí (lobby).
                DisabledSmallIcon(TaratiIcons.Save, localizedString(Res.string.save_game))
                DisabledSmallIcon(TaratiIcons.MenuBook, localizedString(Res.string.saved_games))
                SmallIconButton(TaratiIcons.Public, localizedString(Res.string.online_lobby), onOnlineLobby)
            }
        }

        // Undo/Redo (solo local) — mismos botones que single (NavigableHistoryList).
        if (onUndo != null && onRedo != null) {
            MpUndoRedoRow(
                canUndo = moveIndex >= 0,
                canRedo = moveIndex < history.size - 1,
                onUndo = onUndo,
                onRedo = onRedo,
            )
        }

        Card(modifier = Modifier.fillMaxWidth().weight(1f), elevation = CardDefaults.cardElevation(1.dp)) {
            MpPositionHeader(state)
            MpMoveGrid(
                modifier = Modifier.fillMaxWidth().weight(1f),
                seats = state.seats,
                history = history,
                currentPly = moveIndex,
                onCellClick = onMoveToIndex,
            )
        }

        // Salto a la posición actual (solo local, y solo si se está revisando el pasado).
        if (onMoveToCurrent != null && moveIndex < history.size - 1) {
            Button(
                onClick = onMoveToCurrent,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                LocalizedText(Res.string.jump_to_current_position)
            }
        }
    }
}

/** Fila Undo/Redo del sidebar MP — misma disposición que single. */
@Composable
private fun MpUndoRedoRow(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(TaratiIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            LocalizedText(Res.string.undo)
        }
        OutlinedButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
        ) {
            LocalizedText(Res.string.redo)
            Spacer(Modifier.width(4.dp))
            Icon(TaratiIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Cabecera de notación de posición (FEN) ─────────────────────────────────────

/**
 * Cabecera con la FEN de la posición actual (§10 del plan). Al tocar la notación alterna a la
 * leyenda de George Spencer-Brown y viceversa; el botón de copiado envía la FEN al portapapeles
 * con un toast. Reproduce el `MoveHistoryHeader` de single, adaptado al estado `game6`.
 */
@Composable
private fun MpPositionHeader(state: MpGameState) {
    var showGameState by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val copiedMessage = localizedString(Res.string.board_position_copied_to_clipboard)

    val inPreview = LocalInspectionMode.current
    val clipboardHelper = if (inPreview) null else koinInject<GameClipboardHelper>()
    val bus = if (inPreview) null else koinInject<UIMessageBus>()

    val headerBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val notation = remember(state) { state.toPositionNotation() }

    Column(Modifier.fillMaxWidth()) {
        if (showGameState) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${state.moveCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = notation,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showGameState = false }
                )
                Spacer(Modifier.width(8.dp))
                TooltipIconButton(
                    tooltip = localizedString(Res.string.copy_position),
                    onClick = {
                        val helper = clipboardHelper ?: return@TooltipIconButton
                        scope.launch {
                            if (helper.copyBoardPosition(notation)) {
                                bus?.toast(UIMessage.Toast(message = copiedMessage))
                            }
                        }
                    },
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(TaratiIcons.ContentCopy, localizedString(Res.string.copy_position))
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

// ── Grilla de movimientos por columnas ─────────────────────────────────────────

private val MP_ROW_NUM_WIDTH = 24.dp

/**
 * Lista de movimientos con **una columna por jugador** (§D7 del plan): las jugadas de cada
 * jugador quedan centradas en su columna, el jugador retirado muestra `-`.
 *
 * Las columnas son de **ancho flexible** y se reparten el ancho del Sidebar → **entra siempre sin
 * scroll horizontal** (en web/desktop el scroll horizontal no responde al mouse). La tipografía se
 * achica al subir el nº de jugadores para que los tokens de hasta 7 caracteres (`D10-D11`) entren.
 * Scroll vertical con auto-scroll al final.
 */
@Composable
internal fun MpMoveGrid(
    modifier: Modifier,
    seats: List<Seat>,
    history: List<PlayerMove>,
    currentPly: Int = -1,
    onCellClick: ((ply: Int) -> Unit)? = null,
) {
    val colors = seats.map { it.color }
    val rows = remember(history, seats) { MpMoveList.build(history, seats) }
    val vScroll = rememberScrollState()

    // Auto-scroll al final al agregarse filas (paridad con single).
    LaunchedEffect(rows.size) { vScroll.scrollTo(vScroll.maxValue) }

    val cellStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = when {
            colors.size <= 4 -> 11.sp
            colors.size == 5 -> 10.sp
            else -> 8.sp
        },
    )

    // El header (discos de color por columna) queda **fijo** arriba: solo las filas de jugadas
    // scrollean, así la referencia de qué columna es cada jugador nunca se pierde de vista.
    Column(modifier = modifier.padding(8.dp)) {
        MpMoveGridHeader(colors)
        Column(modifier = Modifier.weight(1f).verticalScroll(vScroll)) {
            rows.forEach { MpMoveGridRow(it, cellStyle, currentPly, onCellClick) }
        }
    }
}

@Composable
private fun MpMoveGridHeader(colors: List<PlayerColor>) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(MP_ROW_NUM_WIDTH))
        colors.forEach { color ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MpColorDisc(color)
            }
        }
    }
}

@Composable
private fun MpMoveGridRow(
    row: MpMoveRow,
    cellStyle: TextStyle,
    currentPly: Int,
    onCellClick: ((ply: Int) -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${row.number}.",
            style = cellStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(MP_ROW_NUM_WIDTH),
        )
        row.cells.forEach { cell ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (cell) {
                    is MpMoveCell.Played -> {
                        val isCurrent = cell.ply == currentPly
                        val cellModifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (isCurrent) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                else Modifier,
                            )
                            .then(
                                if (onCellClick != null) Modifier.clickable { onCellClick(cell.ply) }
                                else Modifier,
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                        Text(
                            text = cell.move.name,
                            style = cellStyle,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = cellModifier,
                        )
                    }

                    MpMoveCell.Retired -> Text(
                        text = "-",
                        style = cellStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    MpMoveCell.Empty -> Unit
                }
            }
        }
    }
}

@Composable
private fun MpStatusRow(state: MpGameState, nameByColor: Map<PlayerColor, String>) {
    val result = state.result
    val text = if (result != null) mpResultMessage(result, nameByColor)
    else localizedString(Res.string.game6_turn, nameByColor[state.currentSeat.color] ?: "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MpColorDisc(state.currentSeat.color)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (result != null) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Footer ────────────────────────────────────────────────────────────────────

@Composable
fun MpAboutFooter(onAbout: () -> Unit) {
    TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
        Icon(TaratiIcons.Info, localizedString(Res.string.about), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(localizedString(Res.string.about_tarati), color = MaterialTheme.colorScheme.primary)
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun MpColorDisc(color: PlayerColor) {
    val fill = PlayerPalette.fill(color)
    val border = PlayerPalette.border(color)
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2f
        drawCircle(fill, radius = r, center = Offset(r, r))
        drawCircle(border, radius = r, center = Offset(r, r), style = Stroke(width = r * 0.18f))
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
) {
    TooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(icon, contentDescription = tooltip, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, tooltip: String, onClick: () -> Unit) {
    TooltipIconButton(
        tooltip = tooltip,
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            icon,
            contentDescription = tooltip,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DisabledSmallIcon(icon: ImageVector, tooltip: String) {
    TooltipIconButton(
        tooltip = tooltip,
        onClick = {},
        enabled = false,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            icon,
            contentDescription = tooltip,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
