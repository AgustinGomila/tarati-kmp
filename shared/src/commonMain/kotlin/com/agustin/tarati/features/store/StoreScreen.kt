package com.agustin.tarati.features.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.core.domain.game.play.GameState
import com.agustin.tarati.features.game6.GameMode
import com.agustin.tarati.features.game6.LocalGameModeController
import com.agustin.tarati.features.game6.StaticBoard25Renderer
import com.agustin.tarati.features.settings.ISettingsViewModel
import com.agustin.tarati.features.settings.PaletteSetting
import com.agustin.tarati.features.settings.PieceTypeSetting
import com.agustin.tarati.features.settings.SettingsViewModel
import com.agustin.tarati.network.models.SUPPORTER_PRODUCT_ID
import com.agustin.tarati.services.billing.EntitlementsRepository
import com.agustin.tarati.services.billing.OwnedProducts
import com.agustin.tarati.services.localization.LocalAppLanguage
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.store_title
import com.agustin.tarati.shared.generated.resources.store_unlock_all_cta
import com.agustin.tarati.shared.generated.resources.supporter_thanks
import com.agustin.tarati.ui.components.library.StaticBoardRenderer
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.setCurrentPalette
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.getBoardColors
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pantalla "Tienda" (C5): vitrina de cosméticos con preview en vivo del tablero.
 *
 * Reusa el [ISettingsViewModel] (mismo estado de paleta/pieza que Settings — comparten la
 * instancia para que seleccionar en cualquiera de los dos se refleje en el otro) y los
 * composables `PaletteSetting`/`PieceTypeSetting`. El gate de C4 ya está activo en esos
 * selectores: los premium muestran candado para no-supporters; tocarlos lleva al
 * `SupporterScreen` (Desktop/Web) o lanza la compra de Google Play (Android).
 */
@Composable
fun StoreScreen(
    settingsViewModel: ISettingsViewModel = koinViewModel<SettingsViewModel>(),
    entitlementsRepository: EntitlementsRepository = koinInject(),
    onNavigateToSupporter: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val currentLanguage = LocalAppLanguage.current
    key(currentLanguage) {
        StoreContent(settingsViewModel, entitlementsRepository, onNavigateToSupporter, onNavigateBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreContent(
    viewModel: ISettingsViewModel,
    entitlementsRepository: EntitlementsRepository,
    onNavigateToSupporter: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val settingsState by viewModel.settingsState.collectAsState()
    val allPalettesForSelector by viewModel.allPalettesForSelector.collectAsState()
    val lockedPalettes by viewModel.lockedPalettes.collectAsState()
    val rawPurchasedIds by viewModel.purchasedProductIds.collectAsState()
    val purchasedProductIds = remember(rawPurchasedIds) { OwnedProducts(rawPurchasedIds) }
    val entitlements by entitlementsRepository.entitlements.collectAsState()
    val isSupporter = SUPPORTER_PRODUCT_ID in entitlements

    val boardColors = getBoardColors()
    val previewState = remember { GameState.initialGameState() }

    // Tile bloqueado: en todas las plataformas lleva a la pantalla Supporter. Android compra
    // el tier `supporter` por Google Play; Desktop/Web por Polar. El supporter desbloquea todo.
    val onPurchase: (String) -> Unit = { onNavigateToSupporter() }

    Scaffold(
        topBar = {
            TaratiTopBar(
                title = localizedString(Res.string.store_title),
                navigationType = TopBarNavigationType.Back,
                onNavigationClick = onNavigateBack,
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Preview en vivo del tablero (refleja la paleta y el modo activo) ──
                val previewModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(16.dp)
                if (LocalGameModeController.current?.mode == GameMode.MULTI) {
                    StaticBoard25Renderer(modifier = previewModifier)
                } else {
                    StaticBoardRenderer(modifier = previewModifier, gameState = previewState)
                }

                // ── Banner supporter ──────────────────────────────────────────────
                if (isSupporter) {
                    Text(
                        text = localizedString(Res.string.supporter_thanks),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                } else {
                    Card(
                        onClick = onNavigateToSupporter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = TaratiIcons.Supporter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                            Text(
                                text = localizedString(Res.string.store_unlock_all_cta),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = TaratiIcons.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Selectores reusados (con el gate de C4 activo) ────────────────
                PaletteSetting(
                    paletteName = settingsState.palette,
                    availablePalettes = allPalettesForSelector,
                    lockedPalettes = lockedPalettes,
                    onPaletteSelected = { palette ->
                        viewModel.setPalette(palette)
                        setCurrentPalette(palette) // actualiza la paleta global que lee el preview
                    },
                    onPurchasePalette = onPurchase,
                )

                PieceTypeSetting(
                    selectedPieceTypeId = settingsState.pieceTypeId,
                    boardColors = boardColors,
                    onPieceTypeSelected = { pieceTypeId -> viewModel.setPieceType(pieceTypeId) },
                    purchasedProductIds = purchasedProductIds,
                    onPurchasePieceType = onPurchase,
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
