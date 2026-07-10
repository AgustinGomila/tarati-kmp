package com.agustin.tarati.features.online.ui


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.pieces.CobColor
import com.agustin.tarati.features.online.game.SpectatingState
import com.agustin.tarati.network.models.OnlineGame
import com.agustin.tarati.network.models.OnlineGameStatus
import com.agustin.tarati.services.localization.LocalizedText
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.accept
import com.agustin.tarati.shared.generated.resources.casual_info_card
import com.agustin.tarati.shared.generated.resources.confirm_offer_draw
import com.agustin.tarati.shared.generated.resources.confirm_resign
import com.agustin.tarati.shared.generated.resources.decline
import com.agustin.tarati.shared.generated.resources.draw_offer_received
import com.agustin.tarati.shared.generated.resources.draw_offer_sent
import com.agustin.tarati.shared.generated.resources.game_finished
import com.agustin.tarati.shared.generated.resources.offer_draw
import com.agustin.tarati.shared.generated.resources.opponent
import com.agustin.tarati.shared.generated.resources.opponent_disconnected
import com.agustin.tarati.shared.generated.resources.rated
import com.agustin.tarati.shared.generated.resources.rated_info_card
import com.agustin.tarati.shared.generated.resources.rating_nbr
import com.agustin.tarati.shared.generated.resources.resign
import com.agustin.tarati.shared.generated.resources.spectator_count
import com.agustin.tarati.shared.generated.resources.time_control
import com.agustin.tarati.shared.generated.resources.tournament_round_progress
import com.agustin.tarati.shared.generated.resources.your_color
import com.agustin.tarati.ui.components.ConfirmDialog
import com.agustin.tarati.ui.components.game.CobColorIndicator
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Barra de información y controles para partidas online.
 *
 * ## Acciones con confirmación
 *
 * Tanto "Resign" como "Offer Draw" abren un [ConfirmDialog] de confirmación antes
 * de ejecutar la acción. Los diálogos siguen el mismo estilo que [MatchmakingModal]
 * para consistencia visual. La barra nunca envía comandos directamente sin
 * confirmación del jugador.
 *
 * ## Estados del área de acciones
 *
 * ```
 * drawOfferFrom != null  → DrawOfferBanner:  [✓ Draw offer]  [Accept] [Decline]
 * pendingDrawSent        → DrawOfferSentRow: [✉ Draw offer sent…]     [Resign]
 * else                   → NormalActions:   [Offer Draw]              [Resign]
 * ```
 *
 * @param onlineGame        Partida online activa (null → barra invisible).
 * @param drawOfferFrom     UserId del jugador con oferta de tablas pendiente, o null.
 * @param pendingDrawSent   True cuando el jugador local envió una oferta y espera respuesta.
 * @param isPlayerTurn      True cuando es el turno del jugador local.
 * @param onResign          Callback ejecutado tras confirmar la resignación.
 * @param onOfferDraw       Callback ejecutado tras confirmar el ofrecimiento de tablas.
 * @param onAcceptDraw      Callback para aceptar la oferta de tablas recibida.
 * @param onDeclineDraw     Callback para rechazar la oferta de tablas recibida.
 * @param visible           Controla la visibilidad animada de la barra.
 */
@Composable
fun OnlineGameBar(
    onlineGame: OnlineGame?,
    drawOfferFrom: String? = null,
    pendingDrawSent: Boolean = false,
    isPlayerTurn: Boolean = false,
    onResign: () -> Unit = {},
    onOfferDraw: () -> Unit = {},
    onAcceptDraw: () -> Unit = {},
    onDeclineDraw: () -> Unit = {},
    visible: Boolean = true,
    /** Cuando no null, activa el modo espectador: muestra ambos jugadores sin controles. */
    spectatingState: SpectatingState? = null,
    modifier: Modifier = Modifier,
) {
    // ── Estado local de confirmación ──────────────────────────────────────────
    var showResignDialog by remember { mutableStateOf(false) }
    var showOfferDrawDialog by remember { mutableStateOf(false) }

    // ── Diálogos de confirmación ──────────────────────────────────────────────

    if (showResignDialog) {
        ConfirmDialog(
            title = Res.string.resign,
            body = Res.string.confirm_resign,
            confirmLabel = Res.string.resign,
            destructive = true,
            onConfirm = onResign,
            onDismiss = { showResignDialog = false },
        )
    }

    if (showOfferDrawDialog) {
        ConfirmDialog(
            title = Res.string.offer_draw,
            body = Res.string.confirm_offer_draw,
            confirmLabel = Res.string.offer_draw,
            onConfirm = onOfferDraw,
            onDismiss = { showOfferDrawDialog = false },
        )
    }

    // ── Barra espectador ─────────────────────────────────────────────────────

    AnimatedVisibility(
        visible = visible && spectatingState != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        spectatingState?.let { s ->
            SpectatorGameBar(s)
        }
    }

    // ── Barra jugador ─────────────────────────────────────────────────────────

    AnimatedVisibility(
        visible = visible && onlineGame != null && spectatingState == null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        onlineGame?.let { game ->
            Surface(
                modifier = Modifier.wrapContentWidth(align = Alignment.Start),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── Fila 1: Oponente · Rating · Modalidad de tiempo ──────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = TaratiIcons.AccountCircle,
                                contentDescription = localizedString(Res.string.opponent),
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = game.opponentInfo.username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = TaratiIcons.EmojiEvents,
                                contentDescription = localizedString(Res.string.rating_nbr),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = game.opponentInfo.rating.toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = TaratiIcons.Timer,
                                contentDescription = localizedString(Res.string.time_control),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = game.timeControl.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    // ── Fila torneo (si aplica) ───────────────────────────────────
                    if (game.tournamentName != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = TaratiIcons.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            val roundSuffix = if (game.tournamentRound != null && game.tournamentRound > 0 &&
                                game.tournamentTotalRounds != null && game.tournamentTotalRounds > 0
                            ) {
                                " · " + localizedString(
                                    Res.string.tournament_round_progress,
                                    game.tournamentRound,
                                    game.tournamentTotalRounds,
                                )
                            } else ""
                            Text(
                                text = "${game.tournamentName}$roundSuffix",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }

                    // ── Fila 2: Tu color + rated  ·  Acciones ────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = localizedString(Res.string.your_color, game.yourColor.capitalize()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (game.isRated) {
                                LocalizedText(
                                    Res.string.rated,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.small,
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        if (game.status == OnlineGameStatus.InProgress) {
                            // Tres estados mutuamente excluyentes priorizados:
                            // 1. Oferta recibida del oponente
                            // 2. Oferta enviada por nosotros, esperando respuesta
                            // 3. Estado normal
                            val drawState: DrawActionState = when {
                                drawOfferFrom != null -> DrawActionState.OfferReceived
                                pendingDrawSent -> DrawActionState.OfferSent
                                else -> DrawActionState.Normal
                            }

                            AnimatedContent(
                                targetState = drawState,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "draw_action_state",
                            ) { state ->
                                when (state) {
                                    DrawActionState.OfferReceived -> DrawOfferBanner(
                                        onAccept = onAcceptDraw,
                                        onDecline = onDeclineDraw,
                                    )

                                    DrawActionState.OfferSent -> DrawOfferSentRow(
                                        onResignClick = { showResignDialog = true },
                                    )

                                    DrawActionState.Normal -> NormalActions(
                                        isPlayerTurn = isPlayerTurn,
                                        onOfferDrawClick = { showOfferDrawDialog = true },
                                        onResignClick = { showResignDialog = true },
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !game.opponentConnected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        OpponentDisconnectedBanner(
                            disconnectedAtMs = game.opponentDisconnectedAtMs,
                            gracePeriodSec = game.gracePeriodSec,
                        )
                    }

                    if (game.status != OnlineGameStatus.InProgress) {
                        Text(
                            text = when (game.status) {
                                is OnlineGameStatus.Finished -> localizedString(Res.string.game_finished)
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}

// ── Draw action states ────────────────────────────────────────────────────────

private enum class DrawActionState { Normal, OfferSent, OfferReceived }

// ── Spectator bar ─────────────────────────────────────────────────────────────

@Composable
private fun SpectatorGameBar(s: SpectatingState) {
    Surface(
        modifier = Modifier.wrapContentWidth(align = Alignment.Start),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Blancas
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(4.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = s.whitePlayer.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        CobColorIndicator(CobColor.WHITE, size = 16.dp)
                    }
                    Text(
                        text = "(${s.whitePlayer.rating})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }

            // Time control + rated
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.timeControlLabel.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            TaratiIcons.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = s.timeControlLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (s.isRated) {
                    LocalizedText(
                        Res.string.rated_info_card,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    LocalizedText(
                        Res.string.casual_info_card,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.spectatorCount > 0) {
                    Text(
                        text = localizedString(Res.string.spectator_count, s.spectatorCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // Negras
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CobColorIndicator(CobColor.BLACK, size = 16.dp)
                        Text(
                            text = s.blackPlayer.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = "(${s.blackPlayer.rating})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/** Banner inline para la oferta de tablas recibida. Sin diálogo: accept/decline son directos. */
@Composable
private fun DrawOfferBanner(onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TaratiIcons.Done,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        LocalizedText(
            Res.string.draw_offer_received,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onAccept,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            LocalizedText(Res.string.accept, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = onDecline,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            LocalizedText(Res.string.decline, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Indicador de oferta enviada. El botón "Offer Draw" desaparece y es reemplazado
 * por una etiqueta de espera; Resign permanece activo.
 */
@Composable
private fun DrawOfferSentRow(onResignClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TaratiIcons.Done,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        LocalizedText(
            Res.string.draw_offer_sent,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onResignClick) {
            LocalizedText(Res.string.resign, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Botones normales de acción. Los clics abren el diálogo de confirmación
 * correspondiente — no ejecutan la acción directamente.
 */
@Composable
private fun NormalActions(
    isPlayerTurn: Boolean,
    onOfferDrawClick: () -> Unit,
    onResignClick: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onOfferDrawClick, enabled = isPlayerTurn) {
            LocalizedText(
                Res.string.offer_draw,
                color = if (isPlayerTurn)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
        TextButton(onClick = onResignClick) {
            LocalizedText(Res.string.resign, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Banner de countdown mostrado mientras el oponente está desconectado.
 *
 * Calcula los segundos restantes del período de gracia a partir del timestamp
 * del cliente ([disconnectedAtMs]) y los actualiza cada segundo.
 * Cuando el servidor adjudique la victoria mostrará [GameEnded] y la partida terminará.
 */
@Composable
private fun OpponentDisconnectedBanner(
    disconnectedAtMs: Long?,
    gracePeriodSec: Int,
) {
    fun remaining(): Int {
        val atMs = disconnectedAtMs ?: return gracePeriodSec
        val elapsedSec = ((Clock.System.now().toEpochMilliseconds() - atMs) / 1000L).toInt()
        return maxOf(0, gracePeriodSec - elapsedSec)
    }

    var secondsLeft by remember(disconnectedAtMs) { mutableIntStateOf(remaining()) }

    LaunchedEffect(disconnectedAtMs) {
        while (secondsLeft > 0) {
            delay(1.seconds)
            secondsLeft = remaining()
        }
    }

    val warningColor = Color(0xFFF59E0B)  // amber-400

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(warningColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = TaratiIcons.Warning,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = warningColor,
        )
        Text(
            text = localizedString(Res.string.opponent_disconnected, "${secondsLeft}s"),
            style = MaterialTheme.typography.bodySmall,
            color = warningColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
