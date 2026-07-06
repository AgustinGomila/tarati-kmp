package com.agustin.tarati.features.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.core.domain.game.play.GameState.Companion.initialGameState
import com.agustin.tarati.features.game6.GameMode
import com.agustin.tarati.features.game6.LocalGameModeController
import com.agustin.tarati.features.game6.StaticBoard25Renderer
import com.agustin.tarati.services.billing.LockedPalettes
import com.agustin.tarati.ui.components.library.StaticBoardRenderer
import com.agustin.tarati.ui.theme.BoardPalette
import com.agustin.tarati.ui.theme.LocalBoardPalette
import com.agustin.tarati.ui.theme.PaletteList
import com.agustin.tarati.ui.theme.TaratiIcons
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// PaletteSelector
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Selector visual de paleta de colores.
 *
 * Muestra un [LazyRow] de [PaletteBoardTile]s. Cada tile renderiza un minitablero
 * con las piezas en la posición inicial, usando los colores reales de esa paleta,
 * independientemente de la paleta activa en el resto de la app.
 *
 * Cada paleta se renderiza con [CompositionLocalProvider] + [LocalBoardPalette],
 * siguiendo el mismo patrón que los previews aislados, para no modificar el
 * estado global de [PaletteManager].
 *
 * @param selectedPaletteName  Nombre de la paleta actualmente activa.
 * @param palettes             Paletas disponibles a mostrar.
 * @param onSelect             Callback al seleccionar una paleta.
 * @param lockedPalettes       Nombres de paletas bloqueadas (premium no compradas).
 *                             Los tiles bloqueados muestran un candado y no pueden
 *                             seleccionarse; la acción de compra se delega a [onPurchase].
 * @param onPurchase           Callback al pulsar un tile bloqueado. Recibe el nombre de la paleta.
 * @param tileSize             Tamaño del minitablero. Default 116.dp.
 */
@Composable
fun PaletteSelector(
    selectedPaletteName: String,
    palettes: PaletteList,
    onSelect: (BoardPalette) -> Unit,
    lockedPalettes: LockedPalettes = LockedPalettes.None,
    onPurchase: (paletteName: String) -> Unit = {},
    tileSize: Dp = 116.dp,
) {
    val initialState = remember { initialGameState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            // Habilitar scroll horizontal con drag del mouse (Desktop)
            // En Android, LazyRow ya soporta drag nativo con touch
            // detectDragGestures distingue automáticamente entre click y drag
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        // Invertir el signo para scroll natural (drag izq = scroll izq)
                        listState.scrollBy(-dragAmount.x)
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(palettes.items, key = { it.name }) { palette ->
            val isSelected = palette.name == selectedPaletteName
            // Gate supporter (C4): las paletas premium quedan bloqueadas salvo supporter / compra.
            val isLocked = palette.name in lockedPalettes

            PaletteBoardTile(
                palette = palette,
                isSelected = isSelected,
                isLocked = isLocked,
                tileSize = tileSize,
                initialState = initialState,
                onClick = {
                    if (isLocked) onPurchase(palette.name) else onSelect(palette)
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PaletteBoardTile
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tile individual: minitablero con piezas iniciales + etiqueta del nombre.
 *
 * El tablero se renderiza con los colores propios de [palette] mediante
 * [CompositionLocalProvider], sin afectar al estado global de la paleta activa.
 * El [StaticBoardRenderer] ya escala automáticamente vértices, aristas y piezas
 * al tamaño del canvas, por lo que [tileSize] controla el detalle visible.
 *
 * La etiqueta trunca nombres largos en una sola línea.
 */
@Composable
fun PaletteBoardTile(
    palette: BoardPalette,
    isSelected: Boolean,
    isLocked: Boolean = false,
    tileSize: Dp = 116.dp,
    initialState: GameState =
        remember { initialGameState() },
    onClick: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)

    val borderModifier = when {
        isSelected -> Modifier.border(width = 2.dp, color = accentColor, shape = shape)
        isLocked -> Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            shape = shape,
        )

        else -> Modifier
    }

    val labelStyle = when {
        isSelected -> MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )

        isLocked -> MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )

        else -> MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .then(borderModifier)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Renderizar el minitablero con la paleta propia de este tile, y con el tablero del **modo
            // activo** (single = tablero `30`, multi = tablero `25`). CompositionLocalProvider aísla el
            // cambio de paleta para que no afecte al resto del árbol de composición.
            CompositionLocalProvider(LocalBoardPalette provides palette) {
                val tileModifier = Modifier
                    .size(tileSize)
                    .then(if (isLocked) Modifier.alpha(0.35f) else Modifier)
                if (LocalGameModeController.current?.mode == GameMode.MULTI) {
                    StaticBoard25Renderer(modifier = tileModifier)
                } else {
                    StaticBoardRenderer(modifier = tileModifier, gameState = initialState)
                }
            }

            if (isLocked) {
                Icon(
                    imageVector = TaratiIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(tileSize * 0.35f),
                )
            }
        }

        Text(
            text = palette.name,
            style = labelStyle,
            maxLines = 1,
        )
    }
}