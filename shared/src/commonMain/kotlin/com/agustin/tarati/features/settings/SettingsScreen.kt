package com.agustin.tarati.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.appVersion
import com.agustin.tarati.core.utils.FeatureFlags
import com.agustin.tarati.features.online.auth.IAuthViewModel
import com.agustin.tarati.services.achievements.IAchievementsManager
import com.agustin.tarati.services.billing.OwnedProducts
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.services.pwa.pwaInstall
import com.agustin.tarati.services.pwa.pwaInstallAvailable
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.about
import com.agustin.tarati.shared.generated.resources.animate_effects
import com.agustin.tarati.shared.generated.resources.animations
import com.agustin.tarati.shared.generated.resources.app_version
import com.agustin.tarati.shared.generated.resources.appearance
import com.agustin.tarati.shared.generated.resources.auth_logout
import com.agustin.tarati.shared.generated.resources.board_display
import com.agustin.tarati.shared.generated.resources.board_edges
import com.agustin.tarati.shared.generated.resources.board_labels
import com.agustin.tarati.shared.generated.resources.board_perimeter
import com.agustin.tarati.shared.generated.resources.board_regions
import com.agustin.tarati.shared.generated.resources.board_vertices
import com.agustin.tarati.shared.generated.resources.gameplay
import com.agustin.tarati.shared.generated.resources.general
import com.agustin.tarati.shared.generated.resources.pre_moves
import com.agustin.tarati.shared.generated.resources.settings
import com.agustin.tarati.shared.generated.resources.settings_achievements
import com.agustin.tarati.shared.generated.resources.settings_install_app
import com.agustin.tarati.shared.generated.resources.settings_online
import com.agustin.tarati.shared.generated.resources.show_evaluation_bar
import com.agustin.tarati.shared.generated.resources.sound
import com.agustin.tarati.shared.generated.resources.sound_effects
import com.agustin.tarati.shared.generated.resources.store_title
import com.agustin.tarati.shared.generated.resources.supporter_title
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiIcons
import com.agustin.tarati.ui.theme.getBoardColors
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ISettingsViewModel = koinViewModel<SettingsViewModel>(),
    authViewModel: IAuthViewModel = koinInject(),
    events: SettingsEvents,
    onNavigateBack: () -> Unit = {},
    isGameActive: Boolean = false,
    onLogout: (() -> Unit)? = null,
    loggedInUsername: String? = null,
    onNavigateToOnlineSettings: (() -> Unit)? = null,
    onNavigateToAchievements: (() -> Unit)? = null,
    onNavigateToSupporter: (() -> Unit)? = null,
    onNavigateToStore: (() -> Unit)? = null,
) {
    val settingsState by viewModel.settingsState.collectAsState()

    // Prefetch del perfil online para que bio/visibility estén listos cuando el
    // usuario navegue a OnlineSettingsScreen. Se dispara al loguear o al abrir Settings.
    if (FeatureFlags.ONLINE_ENABLED) {
        androidx.compose.runtime.LaunchedEffect(loggedInUsername) {
            val isGuest = authViewModel.currentUser?.isGuest == true
            if (!loggedInUsername.isNullOrBlank() && !isGuest) authViewModel.fetchProfile()
        }
    }
    // Los colores de tablero se leen desde el CompositionLocal activo, igual que
    // en el resto de la app. Se pasan explícitamente al selector de piezas para
    // que el preview de las piezas use siempre la paleta activa en tiempo real.
    val boardColors = getBoardColors()
    val rawPurchasedIds by viewModel.purchasedProductIds.collectAsState()
    val purchasedProductIds = remember(rawPurchasedIds) { OwnedProducts(rawPurchasedIds) }
    val allPalettesForSelector by viewModel.allPalettesForSelector.collectAsState()
    val lockedPalettes by viewModel.lockedPalettes.collectAsState()

    Scaffold(
        topBar = {
            TaratiTopBar(
                title = localizedString(Res.string.settings),
                navigationType = TopBarNavigationType.Back,
                onNavigationClick = onNavigateBack,
            )
        },
    ) { padding ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                SettingsCategory(title = Res.string.general)
                UserNameSetting(
                    userName = settingsState.userName,
                    onUserNameChange = { name ->
                        viewModel.setUserName(name)
                        events.onUserNameChange(name)
                    },
                )
                LanguageSetting(
                    language = settingsState.language,
                    onLanguageChange = events::onLanguageChange,
                )

                SettingsCategory(title = Res.string.gameplay)
                TimeControlSetting(
                    mode = settingsState.timeControl,
                    isGameActive = isGameActive,
                    onModeSelected = { mode -> viewModel.setTimeControl(mode) },
                )
                ToggleSetting(
                    icon = TaratiIcons.Speed,
                    title = Res.string.pre_moves,
                    checked = settingsState.preMovesEnabled,
                    onCheckedChange = { enabled -> viewModel.setPreMovesEnabled(enabled) },
                )

                SettingsCategory(title = Res.string.appearance)
                ToggleSetting(
                    icon = TaratiIcons.Leaderboard,
                    title = Res.string.show_evaluation_bar,
                    checked = settingsState.showEvaluationBar,
                    onCheckedChange = { enabled -> viewModel.setShowEvaluationBar(enabled) },
                )
                ThemeSetting(
                    theme = settingsState.appTheme,
                    onThemeChange = events::onThemeChange,
                )
                if (onNavigateToStore != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToStore() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = TaratiIcons.CardGiftcard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = localizedString(Res.string.store_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = TaratiIcons.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PaletteSetting(
                    paletteName = settingsState.palette,
                    availablePalettes = allPalettesForSelector,
                    lockedPalettes = lockedPalettes,
                    onPaletteSelected = { palette ->
                        viewModel.setPalette(palette)
                        events.onPaletteChange(palette)
                    },
                    onPurchasePalette = { productId ->
                        // Gate supporter (C4): tocar un cosmético premium bloqueado lleva a la
                        // pantalla Supporter en todas las plataformas. Android compra el tier
                        // `supporter` por Google Play; Desktop/Web por Polar. El supporter
                        // desbloquea todo, así que el productId puntual solo se usa de fallback.
                        if (onNavigateToSupporter != null) {
                            onNavigateToSupporter()
                        } else {
                            viewModel.launchPurchaseFlow(productId)
                        }
                    },
                )
                PieceTypeSetting(
                    selectedPieceTypeId = settingsState.pieceTypeId,
                    boardColors = boardColors,
                    purchasedProductIds = purchasedProductIds,
                    onPieceTypeSelected = { pieceTypeId ->
                        viewModel.setPieceType(pieceTypeId)
                    },
                    onPurchasePieceType = { productId ->
                        if (onNavigateToSupporter != null) {
                            onNavigateToSupporter()
                        } else {
                            viewModel.launchPurchaseFlow(productId)
                        }
                    },
                )

                SettingsCategory(title = Res.string.board_display)
                ToggleSetting(
                    icon = TaratiIcons.Visibility,
                    title = Res.string.board_labels,
                    checked = settingsState.boardVisualState.labelsVisibles,
                    onCheckedChange = { visible ->
                        viewModel.setLabelsVisibility(visible)
                        events.onLabelsVisibilityChange(visible)
                    },
                )
                ToggleSetting(
                    icon = TaratiIcons.Visibility,
                    title = Res.string.board_vertices,
                    checked = settingsState.boardVisualState.verticesVisibles,
                    onCheckedChange = { visible ->
                        viewModel.setVerticesVisibility(visible)
                        events.onVerticesVisibilityChange(visible)
                    },
                )
                ToggleSetting(
                    icon = TaratiIcons.Visibility,
                    title = Res.string.board_edges,
                    checked = settingsState.boardVisualState.edgesVisibles,
                    onCheckedChange = { visible ->
                        viewModel.setEdgesVisibility(visible)
                        events.onEdgesVisibilityChange(visible)
                    },
                )
                ToggleSetting(
                    icon = TaratiIcons.Visibility,
                    title = Res.string.board_regions,
                    checked = settingsState.boardVisualState.regionsVisibles,
                    onCheckedChange = { visible ->
                        viewModel.setRegionsVisibility(visible)
                        events.onRegionsVisibilityChange(visible)
                    },
                )
                ToggleSetting(
                    icon = TaratiIcons.Visibility,
                    title = Res.string.board_perimeter,
                    checked = settingsState.boardVisualState.perimeterVisible,
                    onCheckedChange = { visible ->
                        viewModel.setPerimeterVisibility(visible)
                        events.onPerimeterVisibilityChange(visible)
                    },
                )

                SettingsCategory(title = Res.string.animations)
                ToggleSetting(
                    icon = TaratiIcons.Animation,
                    title = Res.string.animate_effects,
                    checked = settingsState.boardVisualState.animateEffects,
                    onCheckedChange = { animate ->
                        viewModel.setAnimateEffects(animate)
                        events.onAnimateEffectsChange(animate)
                    },
                )
                ConversionAnimationSetting(
                    style = settingsState.boardVisualState.conversionAnimationStyle,
                    onStyleSelected = { style ->
                        viewModel.setConversionAnimationStyle(style)
                    },
                )

                SettingsCategory(title = Res.string.sound)
                ToggleSetting(
                    icon = TaratiIcons.VolumeUp,
                    title = Res.string.sound_effects,
                    checked = settingsState.soundState.soundEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setSoundEnabled(enabled)
                        events.onSoundEnabledChange(enabled)
                    },
                )

                VolumeSetting(
                    volume = settingsState.soundState.soundVolume,
                    enabled = settingsState.soundState.soundEnabled,
                    onVolumeChange = { volume ->
                        viewModel.setSoundVolume(volume)
                        events.onSoundVolumeChange(volume)
                    },
                )

                if (onLogout != null) {
                    val isGuest = authViewModel.currentUser?.isGuest == true
                    SettingsCategory(title = Res.string.settings_online)
                    if (onNavigateToOnlineSettings != null && !loggedInUsername.isNullOrBlank() && !isGuest) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToOnlineSettings() }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = TaratiIcons.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = loggedInUsername,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = TaratiIcons.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        )
                    }
                    if (onNavigateToSupporter != null && !loggedInUsername.isNullOrBlank() && !isGuest) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToSupporter() }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = TaratiIcons.Supporter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = localizedString(Res.string.supporter_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = TaratiIcons.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLogout)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = TaratiIcons.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = localizedString(Res.string.auth_logout),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                AboutSection(onNavigateToAchievements = onNavigateToAchievements)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AboutSection(
    achievementsManager: IAchievementsManager = koinInject(),
    onNavigateToAchievements: (() -> Unit)? = null,
) {
    var canInstall by remember { mutableStateOf(pwaInstallAvailable()) }

    // Sondea disponibilidad del prompt PWA cada segundo mientras la pantalla está visible.
    // En Android/Desktop pwaInstallAvailable() siempre retorna false (no-op).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(1_000.milliseconds)
            canInstall = pwaInstallAvailable()
        }
    }

    SettingsCategory(title = Res.string.about)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                achievementsManager.showAchievementsUI { onNavigateToAchievements?.invoke() }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TaratiIcons.EmojiEvents,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = localizedString(Res.string.settings_achievements),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TaratiIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
    )

    SettingItem(
        icon = TaratiIcons.Info,
        title = "Tarati",
        subtitle = localizedString(Res.string.app_version).replace($$"%1$s", appVersion),
    )

    if (canInstall) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    pwaInstall()
                    canInstall = false
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TaratiIcons.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = localizedString(Res.string.settings_install_app),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = TaratiIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        )
    }
}
