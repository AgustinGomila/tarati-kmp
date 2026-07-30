package com.agustin.tarati.features.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.data.database.dto.MatchDto
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.parseBoardNotation
import com.agustin.tarati.core.domain.game.play.Move
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.back
import com.agustin.tarati.shared.generated.resources.game_information
import com.agustin.tarati.shared.generated.resources.go_to_begin
import com.agustin.tarati.shared.generated.resources.go_to_end
import com.agustin.tarati.shared.generated.resources.move_n_of_n
import com.agustin.tarati.shared.generated.resources.next
import com.agustin.tarati.shared.generated.resources.observations
import com.agustin.tarati.ui.components.TooltipIconButton
import com.agustin.tarati.ui.components.bottombar.rememberPanelTilt
import com.agustin.tarati.ui.components.library.ReplayBoardRenderer
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CreateCardBoard(
    modifier: Modifier,
    matchDto: MatchDto,
    currentMoveIndex: Int,
    onMoveIndexChange: (Int) -> Unit,
    topPanelExpanded: Boolean = false,
    bottomPanelExpanded: Boolean = false,
) {
    val moves = matchDto.game.moveHistory
    // Fallback defensivo: partidas guardadas antes de la versión 3 de la DB
    // tienen initialBoardPosition vacío. En ese caso se usa la posición
    // inicial estándar, que es correcta para cualquier partida no editada.
    val initialState = remember(matchDto.game.initialBoardPosition) {
        runCatching { parseBoardNotation(matchDto.game.initialBoardPosition) }
            .getOrElse { initialGameState() }
    }
    // Start at the final position; navigation uses initialState as the base.
    var currentBoardState by remember(
        matchDto.game.boardPosition,
        matchDto.game.moveHistory.size,
    ) { mutableStateOf(parseBoardNotation(matchDto.game.boardPosition)) }

    // ── Animación de avance de jugada (paridad con el detalle MP) ────────────
    // Al avanzar exactamente una jugada, la pieza que se mueve se desliza de origen a destino
    // ([ReplayBoardRenderer]). Cualquier otra transición (retroceso, salto, inicio/fin lejano) hace snap.
    var animPrevState by remember(matchDto.game.boardPosition, matchDto.game.moveHistory.size) {
        mutableStateOf<GameState?>(null)
    }
    var animMove by remember(matchDto.game.boardPosition, matchDto.game.moveHistory.size) {
        mutableStateOf<Move?>(null)
    }
    var animKey by remember(matchDto.game.boardPosition, matchDto.game.moveHistory.size) {
        mutableIntStateOf(0)
    }
    var prevMoveIndex by remember(matchDto.game.boardPosition, matchDto.game.moveHistory.size) {
        mutableIntStateOf(currentMoveIndex)
    }

    // Única fuente de verdad de la posición mostrada: reacciona a cualquier cambio de índice
    // (botones o click en la lista). Si el índice avanzó una jugada, arma la animación de deslizamiento.
    LaunchedEffect(currentMoveIndex) {
        val old = prevMoveIndex
        val newState = GameState.getBoardStateAtMove(moves, currentMoveIndex, initialState)
        val advancedMove = if (currentMoveIndex == old + 1) moves.getOrNull(currentMoveIndex) else null
        if (advancedMove != null && !advancedMove.isPromotion()) {
            animPrevState = currentBoardState // posición en `old`, antes de aplicar la jugada
            animMove = advancedMove
            animKey++
        } else {
            animPrevState = null
            animMove = null
        }
        currentBoardState = newState
        prevMoveIndex = currentMoveIndex
    }

    // ── Inclinación inercial al expandir/colapsar los paneles ────────────────
    // Helper compartido con el detalle MP: kick corto + spring inercial de vuelta a 0° por cada
    // cambio de panel, más un kick inicial de "aparición".
    val panelTilt = rememberPanelTilt(
        topPanelExpanded = topPanelExpanded,
        bottomPanelExpanded = bottomPanelExpanded,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationX = panelTilt.rotationX
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center,
    ) {
        val flippable = matchDto.header.observations.isNotEmpty()

        // Estado del flip: (rotaciónDestino, signoDir).
        // Signo = -1 → flip izquierda (tap derecho).
        // Signo = +1 → flip derecha (tap izquierdo).
        // Acumula ±180° en cada doble-tap.
        var flipState by remember { mutableStateOf(Pair(0f, 0f)) }
        val rotationY = remember { Animatable(0f) }

        // Animación de flip en 2 fases:
        //   1. Presión (110 ms, LinearEasing): el lado tocado se inclina levemente
        //      hacia el fondo — movimiento relativo a la posición actual.
        //      LinearEasing da sensación de masa física siendo empujada.
        //   2. Rebote + volteo (spring, DampingRatioMediumBouncy):
        //      el resorte parte desde la posición presionada y lanza la tarjeta
        //      hacia el target, con un pequeño rebote al llegar.
        LaunchedEffect(flipState) {
            val (target, sign) = flipState
            if (sign == 0f) return@LaunchedEffect
            // Fase 1: el lado tocado se hunde (dirección opuesta al flip)
            rotationY.animateTo(
                targetValue = rotationY.value - sign * 20f,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing),
            )
            // Pausa: el usuario percibe claramente la posición presionada
            // antes de que comience el volteo
            delay(60.milliseconds)
            // Fase 2: spring al target con rebote amortiguado al llegar
            rotationY.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessVeryLow,
                ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Botón Anterior
            if (currentMoveIndex > -1) {
                TooltipIconButton(
                    tooltip = stringResource(Res.string.back),
                    onClick = {
                        if (currentMoveIndex > -1) onMoveIndexChange(currentMoveIndex - 1)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        TaratiIcons.KeyboardArrowLeft,
                        contentDescription = stringResource(Res.string.back),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp)) // Espacio para mantener alineación
            }

            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .tiltEffect3D(
                            sensitivity = 0.3f,
                            maxTilt = 8f,
                            onDoubleTap = { tapXRatio ->
                                if (flippable) {
                                    // Tap derecho → flip hacia la izquierda (signo -1)
                                    // Tap izquierdo → flip hacia la derecha (signo +1)
                                    val sign = if (tapXRatio > 0.5f) -1f else +1f
                                    val newTarget = flipState.first + sign * 180f
                                    flipState = Pair(newTarget, sign)
                                }
                            },
                        ),
            ) {
                // Calcula el mayor Card posible que respete ambas dimensiones disponibles
                // y el aspect ratio 0.7 (width / height). Sin este cálculo, fillMaxHeight
                // en pantallas altas produce un Card más ancho que la pantalla.
                val maxCardHeight = maxHeight * 0.9f
                val maxCardWidth = maxWidth
                val cardHeight = minOf(maxCardHeight, maxCardWidth / 0.7f)
                val cardWidth = cardHeight * 0.7f

                Card(
                    modifier =
                        Modifier
                            .size(width = cardWidth, height = cardHeight)
                            .graphicsLayer {
                                this.rotationY = rotationY.value
                                cameraDistance = 12f * density
                            },
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val normalizedAngle = ((rotationY.value % 360f) + 360f) % 360f
                        if (normalizedAngle !in 90f..<270f) {
                            ReplayBoardRenderer(
                                modifier = Modifier.fillMaxSize(),
                                gameState = currentBoardState,
                                previousState = animPrevState,
                                lastMove = animMove,
                                animationKey = animKey,
                            )
                        } else {
                            BackOfCard(
                                modifier = Modifier.fillMaxSize(),
                                matchDto = matchDto,
                            )
                        }
                    }
                }
            }

            // Botón Siguiente
            if (currentMoveIndex < moves.lastIndex) {
                TooltipIconButton(
                    tooltip = stringResource(Res.string.next),
                    onClick = {
                        if (currentMoveIndex < moves.lastIndex) onMoveIndexChange(currentMoveIndex + 1)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        TaratiIcons.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.next),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp)) // Espacio para mantener alineación
            }
        }

        // Controles adicionales debajo del tablero
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Barra de progreso
            if (moves.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { if (moves.isEmpty()) 0f else (currentMoveIndex + 1).toFloat() / moves.size.toFloat() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Botón Inicio
                    TooltipIconButton(
                        tooltip = stringResource(Res.string.go_to_begin),
                        onClick = { onMoveIndexChange(0) },
                        enabled = currentMoveIndex > -1,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            TaratiIcons.SkipPrevious,
                            contentDescription = stringResource(Res.string.go_to_begin),
                            tint =
                                if (currentMoveIndex > -1) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // Contador
                    Text(
                        text = stringResource(Res.string.move_n_of_n, currentMoveIndex + 1, moves.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )

                    // Botón Fin
                    TooltipIconButton(
                        tooltip = stringResource(Res.string.go_to_end),
                        onClick = { onMoveIndexChange(moves.lastIndex) },
                        enabled = currentMoveIndex < moves.size - 1,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            TaratiIcons.SkipNext,
                            contentDescription = stringResource(Res.string.go_to_end),
                            tint =
                                if (currentMoveIndex < moves.size - 1) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackOfCard(
    modifier: Modifier,
    matchDto: MatchDto,
) {
    // Agregar otros campos visibles solo en la parte trasera de la tarjeta
    val backFields: MutableList<Pair<String, String>> = mutableListOf()
    backFields.add(localizedString(Res.string.observations) to matchDto.header.observations.take(200))

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Rotación compensatoria para que el texto se vea correctamente
                    rotationY = 180f
                }
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = localizedString(Res.string.game_information),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            backFields.forEach {
                Spacer(modifier = Modifier.height(16.dp))

                GameInfoItem(title = it.first, value = it.second)
            }
        }
    }
}

@Composable
fun GameInfoItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun Modifier.tiltEffect3D(
    sensitivity: Float = 0.5f,
    maxTilt: Float = 12f,
    onDoubleTap: (tapXRatio: Float) -> Unit = {},
): Modifier =
    composed {
        var rotationX by remember { mutableFloatStateOf(0f) }
        var rotationY by remember { mutableFloatStateOf(0f) }
        var isPressed by remember { mutableStateOf(false) }
        var tapCount by remember { mutableIntStateOf(0) }
        var lastTapTime by remember { mutableLongStateOf(0L) }

        // Animaciones
        val animatedRotationX by animateFloatAsState(
            targetValue = rotationX,
            animationSpec =
                if (isPressed) {
                    tween(durationMillis = 100)
                } else {
                    tween(durationMillis = 500, easing = FastOutSlowInEasing)
                },
            label = "rotationX",
        )

        val animatedRotationY by animateFloatAsState(
            targetValue = rotationY,
            animationSpec =
                if (isPressed) {
                    tween(durationMillis = 100)
                } else {
                    tween(durationMillis = 500, easing = FastOutSlowInEasing)
                },
            label = "rotationY",
        )

        this
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val currentTime = Clock.System.now().toEpochMilliseconds()

                        when (event.type) {
                            PointerEventType.Press -> {
                                // Detectar doble-tap manualmente
                                if (currentTime - lastTapTime < 300) {
                                    tapCount++
                                    if (tapCount == 2) {
                                        val tapX = event.changes.first().position.x
                                        val tapXRatio = tapX / size.width.toFloat()
                                        isPressed = true
                                        onDoubleTap(tapXRatio)
                                        tapCount = 0
                                        lastTapTime = 0
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }
                                } else {
                                    tapCount = 1
                                }
                                lastTapTime = currentTime

                                // Solo activar inclinación si no es doble-tap
                                if (tapCount < 2) {
                                    isPressed = true
                                }
                            }

                            PointerEventType.Move -> {
                                if (isPressed) {
                                    // Calcular el desplazamiento desde el punto inicial
                                    val change = event.changes.first()
                                    val rawOffset = change.position - change.previousPosition

                                    // Aplicar sensibilidad y límites
                                    val newRotationX =
                                        (rotationX + rawOffset.y * sensitivity * 0.1f)
                                            .coerceIn(-maxTilt, maxTilt)
                                    val newRotationY =
                                        (rotationY - rawOffset.x * sensitivity * 0.1f)
                                            .coerceIn(-maxTilt, maxTilt)

                                    rotationX = newRotationX
                                    rotationY = newRotationY
                                }
                            }

                            PointerEventType.Release -> {
                                if (isPressed) {
                                    isPressed = false
                                    rotationX = 0f
                                    rotationY = 0f
                                }
                            }

                            else -> {}
                        }

                        // Procesar todos los cambios
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .graphicsLayer {
                this.rotationX = animatedRotationX
                this.rotationY = animatedRotationY
                cameraDistance = 12f * density
                transformOrigin = TransformOrigin.Center
            }
    }